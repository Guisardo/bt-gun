package com.btgun.host.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import com.btgun.host.ReconnectPolicy
import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.StatusEvent
import com.btgun.host.model.StreamKind
import com.btgun.host.model.StreamSequencer
import java.util.UUID

@SuppressLint("MissingPermission")
class IpegaBleGunAdapter(
    private val context: Context,
    private val listener: Listener,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val parser: IpegaPacketParser = IpegaPacketParser(),
) {
    interface Listener {
        fun onConnectionState(state: BleGunConnectionState)
        fun onGunEvent(envelope: LiveEnvelope<GunEvent>)
        fun onStatus(envelope: LiveEnvelope<StatusEvent>)
    }

    private val statusSequencer = StreamSequencer()
    private val operationQueue = GattOperationQueue { event ->
        emitStatus(
            name = "gatt_operation",
            message = listOfNotNull(event.state, event.operationName, event.callbackName, event.status?.toString())
                .joinToString(":"),
        )
    }
    private var active = false
    private var reconnectAttempts = 0
    private var activeGatt: BluetoothGatt? = null
    private var candidateDevice: BluetoothDevice? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid.toString() }.toSet()
            val name = safeDeviceName(result.device)
            if (!matchesTargetAdvertisement(name, serviceUuids)) {
                return
            }
            if (candidateDevice != null) {
                return
            }

            candidateDevice = result.device
            emitStatus("ble_scan", "candidate_found:$ARGUN_DEVICE_NAME")
            stopScan()
            connectGatt(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            handleError("ble scan failed code=$errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    activeGatt = gatt
                    emitConnection(BleGunConnectionPhase.CONNECTING, "gatt_connected status=$status")
                    try {
                        val started = gatt.discoverServices()
                        emitStatus("ble_gatt_discovery", "discover_services_requested:$started")
                        if (!started) {
                            handleError("discover services failed to start")
                        }
                    } catch (error: SecurityException) {
                        handleError("discover services permission blocked:${error.javaClass.simpleName}")
                    } catch (error: IllegalStateException) {
                        handleError("discover services failed:${error.javaClass.simpleName}")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    parser.reset()
                    operationQueue.clear()
                    closeGatt(gatt)
                    if (active) {
                        scheduleReconnect("gatt disconnected status=$status")
                    } else {
                        emitConnection(BleGunConnectionPhase.STOPPED, "gatt disconnected after stop")
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleError("gatt discovery failed status=$status")
                return
            }
            emitStatus("ble_gatt_discovery", "services_discovered")
            subscribeFff3(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            operationQueue.completeDescriptorWrite(status)
            if (descriptor.uuid == CCCD_UUID_VALUE &&
                descriptor.characteristic?.uuid == FFF3_CHARACTERISTIC_UUID_VALUE &&
                status == BluetoothGatt.GATT_SUCCESS
            ) {
                reconnectAttempts = 0
                emitConnection(BleGunConnectionPhase.CONNECTED, "fff3_notifications_enabled")
                emitStatus("ble_gatt_notification", "fff3_cccd_enabled")
            } else if (descriptor.characteristic?.uuid == FFF3_CHARACTERISTIC_UUID_VALUE) {
                handleError("fff3 cccd write failed status=$status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            operationQueue.completeCharacteristicRead(status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            operationQueue.completeCharacteristicRead(status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            operationQueue.completeCharacteristicWrite(status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            routeCharacteristicChanged(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            routeCharacteristicChanged(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }
    }

    fun startSession() {
        active = true
        reconnectAttempts = 0
        candidateDevice = null
        parser.reset()
        startScan()
    }

    fun stopSession() {
        active = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        operationQueue.clear()
        activeGatt?.let { gatt ->
            try {
                gatt.disconnect()
            } catch (_: SecurityException) {
                // Permission loss is reported on active operations; close still releases app state.
            } finally {
                closeGatt(gatt)
            }
        }
        activeGatt = null
        candidateDevice = null
        parser.reset()
        emitConnection(BleGunConnectionPhase.STOPPED, "session_stopped")
    }

    private fun startScan() {
        if (!active) {
            return
        }
        val scanner = try {
            bluetoothAdapter?.bluetoothLeScanner
        } catch (error: SecurityException) {
            handleError("ble scanner permission blocked:${error.javaClass.simpleName}")
            return
        } catch (error: IllegalStateException) {
            handleError("ble scanner unavailable:${error.javaClass.simpleName}")
            return
        }
        if (scanner == null) {
            handleError("ble scanner unavailable")
            return
        }

        candidateDevice = null
        emitConnection(BleGunConnectionPhase.SCANNING, "scan_started")
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLE_SERVICE_UUID_VALUE))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (error: SecurityException) {
            handleError("ble scan permission blocked:${error.javaClass.simpleName}")
        } catch (error: IllegalStateException) {
            handleError("ble scan failed:${error.javaClass.simpleName}")
        }
    }

    private fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Permission loss is surfaced by active start/connect operations.
        } catch (_: IllegalStateException) {
            // Bluetooth may already be turning off.
        }
    }

    private fun connectGatt(device: BluetoothDevice) {
        if (!active) {
            return
        }
        emitConnection(BleGunConnectionPhase.CONNECTING, "connect_requested:$ARGUN_DEVICE_NAME")
        try {
            activeGatt = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, gattCallback)
            }
        } catch (error: SecurityException) {
            handleError("gatt connect permission blocked:${error.javaClass.simpleName}")
        } catch (error: IllegalStateException) {
            handleError("gatt connect failed:${error.javaClass.simpleName}")
        }
    }

    private fun subscribeFff3(gatt: BluetoothGatt) {
        val service = gatt.getService(BLE_SERVICE_UUID_VALUE)
        if (service == null) {
            handleError("missing fff0 service")
            return
        }
        val characteristic = service.getCharacteristic(FFF3_CHARACTERISTIC_UUID_VALUE)
        if (characteristic == null) {
            handleError("missing fff3 characteristic")
            return
        }

        val canNotify = characteristic.properties has BluetoothGattCharacteristic.PROPERTY_NOTIFY
        if (!canNotify) {
            handleError("fff3 missing notify property")
            return
        }

        val notificationSet = try {
            gatt.setCharacteristicNotification(characteristic, true)
        } catch (error: SecurityException) {
            handleError("fff3 notification permission blocked:${error.javaClass.simpleName}")
            false
        } catch (error: IllegalStateException) {
            handleError("fff3 notification failed:${error.javaClass.simpleName}")
            false
        }
        if (!notificationSet) {
            handleError("fff3 setCharacteristicNotification returned false")
            return
        }

        val descriptor = characteristic.getDescriptor(CCCD_UUID_VALUE)
        if (descriptor == null) {
            handleError("fff3 missing cccd descriptor")
            return
        }

        operationQueue.enqueue("write_cccd:fff3") {
            try {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            } catch (error: SecurityException) {
                handleError("fff3 cccd permission blocked:${error.javaClass.simpleName}")
                false
            } catch (error: IllegalStateException) {
                handleError("fff3 cccd write failed:${error.javaClass.simpleName}")
                false
            }
        }
    }

    private fun routeCharacteristicChanged(characteristicUuid: UUID, value: ByteArray) {
        if (characteristicUuid != FFF3_CHARACTERISTIC_UUID_VALUE) {
            emitStatus("ble_gatt_notification", "ignored_characteristic:$characteristicUuid")
            return
        }

        val now = SystemClock.elapsedRealtimeNanos()
        when (val parsed = parser.parseFff3(value, captureElapsedNanos = now, emittedElapsedNanos = now)) {
            is ParsedGunPacket.Event -> listener.onGunEvent(parsed.envelope)
            is UnknownBlePayload -> listener.onStatus(parsed.envelope)
        }
    }

    private fun scheduleReconnect(error: String) {
        val decision = reconnectPolicy.onDisconnect(
            activeSession = active,
            completedAttempts = reconnectAttempts,
            error = error,
        )
        reconnectAttempts = decision.nextAttempt
        emitConnection(
            phase = when (decision.state.phase.wireName) {
                "reconnecting" -> BleGunConnectionPhase.RECONNECTING
                "stopped" -> BleGunConnectionPhase.STOPPED
                else -> BleGunConnectionPhase.ERROR
            },
            message = error,
        )
        if (!decision.shouldReconnect || decision.delayMillis == null) {
            return
        }
        handler.postDelayed({
            if (active) {
                startScan()
            }
        }, decision.delayMillis)
    }

    private fun handleError(message: String) {
        emitConnection(BleGunConnectionPhase.ERROR, message)
        emitStatus("ble_error", message)
    }

    private fun emitConnection(phase: BleGunConnectionPhase, message: String? = null) {
        listener.onConnectionState(
            BleGunConnectionState(
                phase = phase,
                reconnectAttempt = reconnectAttempts,
                lastError = message,
            ),
        )
    }

    private fun emitStatus(name: String, message: String? = null) {
        val now = SystemClock.elapsedRealtimeNanos()
        listener.onStatus(
            LiveEnvelope(
                stream = StreamKind.STATUS,
                seq = statusSequencer.next(StreamKind.STATUS),
                captureElapsedNanos = now,
                emittedElapsedNanos = now,
                payload = StatusEvent(name = name, message = message),
            ),
        )
    }

    private fun closeGatt(gatt: BluetoothGatt) {
        if (activeGatt == gatt) {
            activeGatt = null
        }
        gatt.close()
    }

    private fun safeDeviceName(device: BluetoothDevice): String? =
        try {
            device.name
        } catch (_: SecurityException) {
            null
        }

    companion object {
        const val ARGUN_DEVICE_NAME: String = "ARGunGame"
        const val BLE_SERVICE_UUID: String = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val FFF3_CHARACTERISTIC_UUID: String = "0000fff3-0000-1000-8000-00805f9b34fb"
        const val CCCD_UUID: String = "00002902-0000-1000-8000-00805f9b34fb"
        private const val ENABLE_NOTIFICATION_VALUE_HEX: String = "0100"
        private val BLE_SERVICE_UUID_VALUE: UUID = UUID.fromString(BLE_SERVICE_UUID)
        private val FFF3_CHARACTERISTIC_UUID_VALUE: UUID = UUID.fromString(FFF3_CHARACTERISTIC_UUID)
        private val CCCD_UUID_VALUE: UUID = UUID.fromString(CCCD_UUID)

        fun scanFilterSpec(): BleScanFilterSpec =
            BleScanFilterSpec(
                deviceName = ARGUN_DEVICE_NAME,
                serviceUuid = BLE_SERVICE_UUID,
                scanMode = "low_latency",
            )

        fun notificationSubscriptionSpec(): NotificationSubscriptionSpec =
            NotificationSubscriptionSpec(
                serviceUuid = BLE_SERVICE_UUID,
                characteristicUuid = FFF3_CHARACTERISTIC_UUID,
                descriptorUuid = CCCD_UUID,
                enableNotificationValueHex = ENABLE_NOTIFICATION_VALUE_HEX,
            )

        fun matchesTargetAdvertisement(deviceName: String?, advertisedServiceUuids: Set<String>): Boolean {
            val serviceMatch = advertisedServiceUuids.any { uuid ->
                uuid.equals(BLE_SERVICE_UUID, ignoreCase = true)
            }
            val nameMatch = deviceName.equals(ARGUN_DEVICE_NAME, ignoreCase = true)
            return serviceMatch && nameMatch
        }
    }
}

data class BleScanFilterSpec(
    val deviceName: String,
    val serviceUuid: String,
    val scanMode: String,
)

data class NotificationSubscriptionSpec(
    val serviceUuid: String,
    val characteristicUuid: String,
    val descriptorUuid: String,
    val enableNotificationValueHex: String,
)

data class BleGunConnectionState(
    val phase: BleGunConnectionPhase = BleGunConnectionPhase.IDLE,
    val reconnectAttempt: Int = 0,
    val lastError: String? = null,
)

enum class BleGunConnectionPhase {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    STOPPED,
    ERROR,
}

private infix fun Int.has(flag: Int): Boolean = (this and flag) == flag
