package com.btgun.diagnostic

import android.Manifest
import android.app.Activity
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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

class MainActivity : Activity() {
    private val schema = "btgun.android_diagnostic.v1"
    private val sessionId = "manual-phase1"
    private lateinit var output: TextView
    private var activeGatt: BluetoothGatt? = null
    private var gattCandidateDevice: BluetoothDevice? = null
    private var gattOperationInFlight = false
    private val pendingGattOperations = ArrayDeque<PendingGattOperation>()

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            logReport(
                "ble_scan",
                "callback_type" to callbackType,
                "device_name" to safeDeviceName(result.device),
                "device_address" to safeDeviceAddress(result.device),
                "rssi" to result.rssi,
                "service_uuids" to (result.scanRecord?.serviceUuids?.joinToString("|") ?: "")
            )
        }

        override fun onScanFailed(errorCode: Int) {
            logReport("ble_scan", "state" to "failed", "error_code" to errorCode)
        }
    }

    private val gattScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceUuids = result.scanRecord?.serviceUuids?.joinToString("|").orEmpty()
            val serviceMatch = result.scanRecord?.serviceUuids?.any { it.uuid == BLE_SERVICE_UUID } == true
            val name = safeDeviceName(result.device)
            val nameMatch = name.equals(ARGUN_DEVICE_NAME, ignoreCase = true)
            if (!serviceMatch && !nameMatch) {
                return
            }
            if (gattCandidateDevice != null) {
                return
            }
            gattCandidateDevice = result.device
            logReport(
                "ble_gatt_scan",
                "state" to "candidate_found",
                "callback_type" to callbackType,
                "device_name" to name,
                "device_address" to safeDeviceAddress(result.device),
                "rssi" to result.rssi,
                "service_uuids" to serviceUuids,
                "service_match" to serviceMatch,
                "name_match" to nameMatch,
                "clue_id" to "ARGUN2021-BLE-001"
            )
            stopGattScan()
            connectGatt(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            logReport("ble_gatt_scan", "state" to "failed", "error_code" to errorCode)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            logReport(
                "ble_gatt_connection",
                "status" to status,
                "new_state" to bluetoothProfileStateName(newState),
                "device_name" to safeDeviceName(gatt.device),
                "device_address" to safeDeviceAddress(gatt.device),
                "clue_id" to "ARGUN2021-BLE-001"
            )
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                    val started = gatt.discoverServices()
                    logReport("ble_gatt_discovery", "state" to "discover_services_requested", "started" to started)
                } catch (error: SecurityException) {
                    logReport("ble_gatt_discovery", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pendingGattOperations.clear()
                gattOperationInFlight = false
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logReport("ble_gatt_discovery", "state" to "services_discovered", "status" to status)
            inspectGattServices(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            logReport(
                "ble_gatt_descriptor_write",
                "status" to status,
                "service_uuid" to descriptor.characteristic?.service?.uuid,
                "characteristic_uuid" to descriptor.characteristic?.uuid,
                "descriptor_uuid" to descriptor.uuid
            )
            completeGattOperation("descriptor_write")
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            logCharacteristicPayload("ble_gatt_characteristic_read", characteristic, value, status)
            completeGattOperation("characteristic_read")
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            logCharacteristicPayload("ble_gatt_characteristic_read", characteristic, characteristic.value ?: ByteArray(0), status)
            completeGattOperation("characteristic_read")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            logCharacteristicPayload("ble_gatt_characteristic_changed", characteristic, value, BluetoothGatt.GATT_SUCCESS)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            logCharacteristicPayload("ble_gatt_characteristic_changed", characteristic, characteristic.value ?: ByteArray(0), BluetoothGatt.GATT_SUCCESS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button("Permission State") { reportPermissionState() })
            addView(button("InputDevice Scan") { scanInputDevices() })
            addView(button("BLE Scan Hook") { startBleScan() })
            addView(button("BLE GATT Discovery") { startBleGattDiscovery() })
            addView(button("BLE Characteristic Hook") { recordBleCharacteristicTargets() })
            addView(button("Classic Scan Hook") { scanClassicDevices() })
            addView(button("App Frame Marker") { recordAppObservedFrameMarker() })
            addView(button("Rumble Attempt Hook") { recordRumbleAttempt() })
            addView(button("Rumble Observed Marker") { recordRumbleObservedMarker() })
            addView(ScrollView(this@MainActivity).apply { addView(output) })
        }
        setContentView(controls)
        reportPermissionState()
        scanInputDevices()
        recordBleCharacteristicTargets()
        scanClassicDevices()
    }

    override fun onDestroy() {
        stopGattScan()
        closeActiveGatt("activity_destroy")
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        logKeyEvent("key_event", event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        logKeyEvent("key_event", event)
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        logMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun reportPermissionState() {
        logReport(
            "permission_state",
            "sdk_int" to Build.VERSION.SDK_INT,
            "bluetooth_enabled" to bluetoothEnabled(),
            "bluetooth_scan" to permissionStatus(Manifest.permission.BLUETOOTH_SCAN),
            "bluetooth_connect" to permissionStatus(Manifest.permission.BLUETOOTH_CONNECT),
            "bluetooth_legacy" to permissionStatus(Manifest.permission.BLUETOOTH),
            "bluetooth_admin" to permissionStatus(Manifest.permission.BLUETOOTH_ADMIN),
            "location_fine" to permissionStatus(Manifest.permission.ACCESS_FINE_LOCATION),
            "location_coarse" to permissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun scanInputDevices() {
        for (deviceId in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            logReport(
                "input_device_scan",
                "device_id" to device.id,
                "name" to device.name,
                "descriptor" to device.descriptor,
                "sources" to "0x${device.sources.toString(16)}",
                "keyboard_type" to device.keyboardType,
                "vendor_id" to if (Build.VERSION.SDK_INT >= 19) device.vendorId else -1,
                "product_id" to if (Build.VERSION.SDK_INT >= 19) device.productId else -1,
                "motion_ranges" to device.motionRanges.joinToString("|") { range ->
                    "axis=${range.axis},source=0x${range.source.toString(16)},min=${range.min},max=${range.max}"
                }
            )
        }
    }

    private fun startBleScan() {
        reportPermissionState()
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            logReport("ble_scan", "state" to "unavailable")
            return
        }
        if (!hasBluetoothScanPermission()) {
            logReport("ble_scan", "state" to "permission_blocked")
            return
        }
        logReport("ble_scan", "state" to "start", "clue_id" to "ARGUN2021-BLE-001")
        try {
            scanner.startScan(bleScanCallback)
        } catch (error: SecurityException) {
            logReport("ble_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
        }
    }

    private fun recordBleCharacteristicTargets() {
        for ((characteristicUuid, clueId, purpose) in BLE_CHARACTERISTIC_TARGETS) {
            logReport(
                "ble_characteristic",
                "state" to "candidate_recorded",
                "service_uuid" to BLE_SERVICE_UUID,
                "characteristic_uuid" to characteristicUuid,
                "properties" to purpose,
                "clue_id" to clueId
            )
        }
    }

    private fun startBleGattDiscovery() {
        reportPermissionState()
        recordBleCharacteristicTargets()
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            logReport("ble_gatt_scan", "state" to "unavailable")
            return
        }
        if (!hasBluetoothScanPermission() || !hasBluetoothConnectPermission()) {
            logReport(
                "ble_gatt_scan",
                "state" to "permission_blocked",
                "bluetooth_scan" to permissionStatus(Manifest.permission.BLUETOOTH_SCAN),
                "bluetooth_connect" to permissionStatus(Manifest.permission.BLUETOOTH_CONNECT)
            )
            return
        }
        stopGattScan()
        closeActiveGatt("restart_gatt_discovery")
        gattCandidateDevice = null
        pendingGattOperations.clear()
        gattOperationInFlight = false

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BLE_SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        logReport(
            "ble_gatt_scan",
            "state" to "start",
            "service_uuid" to BLE_SERVICE_UUID,
            "device_name_candidate" to ARGUN_DEVICE_NAME,
            "clue_id" to "ARGUN2021-BLE-001"
        )
        try {
            scanner.stopScan(bleScanCallback)
            scanner.startScan(filters, settings, gattScanCallback)
        } catch (error: SecurityException) {
            logReport("ble_gatt_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
        } catch (error: IllegalStateException) {
            logReport("ble_gatt_scan", "state" to "failed", "error" to error.javaClass.simpleName)
        }
    }

    private fun stopGattScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(gattScanCallback)
        } catch (_: SecurityException) {
            // Permission state is reported when starting the scan.
        } catch (_: IllegalStateException) {
            // Scanner may be unavailable while Bluetooth is changing state.
        }
    }

    private fun connectGatt(device: BluetoothDevice) {
        try {
            logReport(
                "ble_gatt_connection",
                "state" to "connect_requested",
                "device_name" to safeDeviceName(device),
                "device_address" to safeDeviceAddress(device),
                "transport" to "le",
                "clue_id" to "ARGUN2021-BLE-001"
            )
            activeGatt = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(this, false, gattCallback)
            }
        } catch (error: SecurityException) {
            logReport("ble_gatt_connection", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
        }
    }

    private fun closeActiveGatt(reason: String) {
        val gatt = activeGatt ?: return
        try {
            logReport("ble_gatt_connection", "state" to "closing", "reason" to reason, "device_address" to safeDeviceAddress(gatt.device))
            gatt.disconnect()
        } catch (_: SecurityException) {
            // Close below still releases app-side resources.
        } finally {
            gatt.close()
            activeGatt = null
        }
    }

    private fun inspectGattServices(gatt: BluetoothGatt) {
        val services = gatt.services.orEmpty()
        if (services.isEmpty()) {
            logReport("ble_gatt_service", "state" to "none")
            return
        }
        for (service in services) {
            logReport(
                "ble_gatt_service",
                "service_uuid" to service.uuid,
                "service_type" to service.type,
                "characteristic_count" to service.characteristics.size
            )
            for (characteristic in service.characteristics) {
                val clueId = clueIdForCharacteristic(characteristic.uuid)
                logReport(
                    "ble_gatt_characteristic",
                    "service_uuid" to service.uuid,
                    "characteristic_uuid" to characteristic.uuid,
                    "properties" to characteristicPropertiesText(characteristic.properties),
                    "descriptors" to characteristic.descriptors.joinToString("|") { it.uuid.toString() },
                    "clue_id" to clueId.orEmpty()
                )
                if (service.uuid == BLE_SERVICE_UUID && clueId != null) {
                    prepareTargetCharacteristic(gatt, characteristic, clueId)
                }
            }
        }
    }

    private fun prepareTargetCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        clueId: String
    ) {
        val properties = characteristic.properties
        val readable = properties has BluetoothGattCharacteristic.PROPERTY_READ
        val notifiable = properties has BluetoothGattCharacteristic.PROPERTY_NOTIFY
        val indicatable = properties has BluetoothGattCharacteristic.PROPERTY_INDICATE
        val writeLike = (properties has BluetoothGattCharacteristic.PROPERTY_WRITE) ||
            (properties has BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

        logReport(
            "ble_gatt_target",
            "state" to "matched_static_candidate",
            "service_uuid" to characteristic.service.uuid,
            "characteristic_uuid" to characteristic.uuid,
            "properties" to characteristicPropertiesText(properties),
            "readable" to readable,
            "notifiable" to notifiable,
            "indicatable" to indicatable,
            "write_like" to writeLike,
            "clue_id" to clueId
        )

        if (readable) {
            enqueueGattOperation("read:${characteristic.uuid}") {
                try {
                    gatt.readCharacteristic(characteristic)
                } catch (error: SecurityException) {
                    logReport("ble_gatt_operation", "state" to "permission_blocked", "operation" to "read", "error" to error.javaClass.simpleName)
                    false
                }
            }
        }

        if (notifiable || indicatable) {
            val notificationSet = try {
                gatt.setCharacteristicNotification(characteristic, true)
            } catch (error: SecurityException) {
                logReport("ble_gatt_notification", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
                false
            }
            logReport(
                "ble_gatt_notification",
                "state" to "set_characteristic_notification",
                "service_uuid" to characteristic.service.uuid,
                "characteristic_uuid" to characteristic.uuid,
                "result" to notificationSet,
                "clue_id" to clueId
            )
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                logReport(
                    "ble_gatt_notification",
                    "state" to "missing_cccd",
                    "service_uuid" to characteristic.service.uuid,
                    "characteristic_uuid" to characteristic.uuid,
                    "clue_id" to clueId
                )
            } else {
                val descriptorValue = if (indicatable) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                enqueueGattOperation("cccd:${characteristic.uuid}") {
                    try {
                        @Suppress("DEPRECATION")
                        descriptor.value = descriptorValue
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    } catch (error: SecurityException) {
                        logReport("ble_gatt_operation", "state" to "permission_blocked", "operation" to "cccd_write", "error" to error.javaClass.simpleName)
                        false
                    }
                }
            }
        }
    }

    private fun enqueueGattOperation(name: String, start: () -> Boolean) {
        pendingGattOperations.addLast(PendingGattOperation(name, start))
        drainGattOperations()
    }

    private fun drainGattOperations() {
        if (gattOperationInFlight || pendingGattOperations.isEmpty()) {
            return
        }
        val operation = pendingGattOperations.removeFirst()
        gattOperationInFlight = true
        val started = operation.start()
        logReport("ble_gatt_operation", "state" to if (started) "started" else "start_failed", "operation" to operation.name)
        if (!started) {
            gattOperationInFlight = false
            drainGattOperations()
        }
    }

    private fun completeGattOperation(callback: String) {
        gattOperationInFlight = false
        logReport("ble_gatt_operation", "state" to "completed", "callback" to callback)
        drainGattOperations()
    }

    private fun scanClassicDevices() {
        reportPermissionState()
        val bondedDevices = try {
            bluetoothAdapter?.bondedDevices.orEmpty()
        } catch (error: SecurityException) {
            logReport("classic_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
            return
        }
        if (bondedDevices.isEmpty()) {
            logReport("classic_scan", "state" to "no_bonded_devices")
            return
        }
        for (device in bondedDevices) {
            val uuids = try {
                device.uuids?.joinToString("|") { it.uuid.toString() }.orEmpty()
            } catch (error: SecurityException) {
                "permission_blocked"
            }
            logReport(
                "classic_scan",
                "device_name" to safeDeviceName(device),
                "device_address" to safeDeviceAddress(device),
                "bond_state" to safeBondState(device),
                "type" to safeDeviceType(device),
                "uuids" to uuids,
                "clue_id" to "ARCHER-BT-001"
            )
            logReport(
                "classic_socket_observation",
                "state" to "candidate_recorded",
                "uuid" to SPP_UUID.toString(),
                "channel" to 1,
                "device_address" to safeDeviceAddress(device),
                "clue_id" to "ARCHER-BT-001"
            )
        }
    }

    private fun recordAppObservedFrameMarker() {
        logReport(
            "app_observed_frame",
            "state" to "manual_marker",
            "source_path" to "ble_or_classic_callback",
            "payload_excerpt" to "manual_entry_required",
            "capture_ref" to "local://.evidence/phase1/app-logs/",
            "clue_id" to "ARCHER-INPUT-001"
        )
    }

    private fun recordRumbleAttempt() {
        logReport(
            "rumble_attempt",
            "state" to "manual_plan03_required",
            "ble_characteristic_candidate" to "0000fff5-0000-1000-8000-00805f9b34fb",
            "classic_uuid_candidate" to SPP_UUID.toString(),
            "clue_id" to "ARGUN2021-RUMBLE-001"
        )
        logReport(
            "rumble_failed",
            "state" to "no_payload_sent",
            "reason" to "Plan 03 must review hardware workflow before bounded output writes",
            "clue_id" to "ARGUN2021-RUMBLE-001"
        )
    }

    private fun recordRumbleObservedMarker() {
        logReport(
            "rumble_observed",
            "state" to "manual_marker",
            "command_ref" to "local://.evidence/phase1/app-logs/",
            "capture_ref" to "local://.evidence/phase1/hci/",
            "observer_note" to "Use only when physical motor activation is observed in Plan 03",
            "clue_id" to "ARGUN2021-RUMBLE-001"
        )
    }

    private fun logKeyEvent(report: String, event: KeyEvent) {
        logReport(
            report,
            "action" to keyActionName(event.action),
            "key_code" to event.keyCode,
            "scan_code" to event.scanCode,
            "repeat_count" to event.repeatCount,
            "source" to "0x${event.source.toString(16)}",
            "device_id" to event.deviceId,
            "event_time" to event.eventTime
        )
    }

    private fun logMotionEvent(event: MotionEvent) {
        val axes = MOTION_AXES.joinToString("|") { axis ->
            "${MotionEvent.axisToString(axis)}=${event.getAxisValue(axis)}"
        }
        logReport(
            "motion_event",
            "action" to motionActionName(event.actionMasked),
            "source" to "0x${event.source.toString(16)}",
            "device_id" to event.deviceId,
            "pointer_count" to event.pointerCount,
            "axes" to axes,
            "x_precision" to event.xPrecision,
            "y_precision" to event.yPrecision,
            "event_time" to event.eventTime
        )
    }

    private fun logReport(report: String, vararg fields: Pair<String, Any?>) {
        val allFields = linkedMapOf<String, Any?>(
            "schema" to schema,
            "report" to report,
            "ts_elapsed_ms" to SystemClock.elapsedRealtime(),
            "session_id" to sessionId
        )
        fields.forEach { (key, value) -> allFields[key] = value }
        val line = allFields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":${jsonValue(value)}"
        }
        android.util.Log.i("BtGunDiagnostic", line)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            appendOutput(line)
        } else {
            runOnUiThread { appendOutput(line) }
        }
    }

    private fun appendOutput(line: String) {
        output.append(line)
        output.append("\n")
    }

    private fun jsonValue(value: Any?): String =
        when (value) {
            null -> "null"
            is Number, is Boolean -> value.toString()
            else -> "\"${escape(value.toString())}\""
        }

    private fun escape(value: String): String =
        buildString {
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun permissionStatus(permission: String): String =
        if (Build.VERSION.SDK_INT < 23) {
            "granted_pre_runtime"
        } else if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "granted"
        } else {
            "denied"
        }

    private fun hasBluetoothScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun bluetoothEnabled(): String =
        try {
            when (bluetoothAdapter?.isEnabled) {
                true -> "true"
                false -> "false"
                null -> "unavailable"
            }
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeDeviceName(device: BluetoothDevice?): String =
        try {
            device?.name.orEmpty()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeDeviceAddress(device: BluetoothDevice?): String =
        try {
            device?.address.orEmpty()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeBondState(device: BluetoothDevice): String =
        try {
            device.bondState.toString()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeDeviceType(device: BluetoothDevice): String =
        try {
            device.type.toString()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun keyActionName(action: Int): String =
        when (action) {
            KeyEvent.ACTION_DOWN -> "down"
            KeyEvent.ACTION_UP -> "up"
            KeyEvent.ACTION_MULTIPLE -> "multiple"
            else -> "unknown_$action"
        }

    private fun motionActionName(action: Int): String =
        when (action) {
            MotionEvent.ACTION_DOWN -> "down"
            MotionEvent.ACTION_UP -> "up"
            MotionEvent.ACTION_MOVE -> "move"
            MotionEvent.ACTION_CANCEL -> "cancel"
            else -> "unknown_$action"
        }

    private fun bluetoothProfileStateName(state: Int): String =
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> "connected"
            BluetoothProfile.STATE_CONNECTING -> "connecting"
            BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
            BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
            else -> "unknown_$state"
        }

    private fun characteristicPropertiesText(properties: Int): String {
        val names = mutableListOf<String>()
        if (properties has BluetoothGattCharacteristic.PROPERTY_BROADCAST) names += "broadcast"
        if (properties has BluetoothGattCharacteristic.PROPERTY_READ) names += "read"
        if (properties has BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) names += "write_no_response"
        if (properties has BluetoothGattCharacteristic.PROPERTY_WRITE) names += "write"
        if (properties has BluetoothGattCharacteristic.PROPERTY_NOTIFY) names += "notify"
        if (properties has BluetoothGattCharacteristic.PROPERTY_INDICATE) names += "indicate"
        if (properties has BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) names += "signed_write"
        return if (names.isEmpty()) "none" else names.joinToString("|")
    }

    private infix fun Int.has(mask: Int): Boolean = (this and mask) != 0

    private fun clueIdForCharacteristic(uuid: UUID): String? =
        BLE_CHARACTERISTIC_TARGETS.firstOrNull { it.first == uuid }?.second

    private fun logCharacteristicPayload(
        report: String,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        logReport(
            report,
            "status" to status,
            "service_uuid" to characteristic.service?.uuid,
            "characteristic_uuid" to characteristic.uuid,
            "payload_len" to value.size,
            "payload_hex" to value.toHex(),
            "payload_base64" to Base64.encodeToString(value, Base64.NO_WRAP),
            "clue_id" to clueIdForCharacteristic(characteristic.uuid).orEmpty()
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class PendingGattOperation(val name: String, val start: () -> Boolean)

    companion object {
        private const val ARGUN_DEVICE_NAME = "ARGunGame"
        private val BLE_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val BLE_CHARACTERISTIC_TARGETS = arrayOf(
            Triple(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"), "ARGUN2021-BLE-001", "read_or_notify_candidate"),
            Triple(UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"), "ARCHER-BLE-001", "notify_candidate"),
            Triple(UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb"), "ARGUN2021-RUMBLE-001", "write_candidate")
        )
        private val MOTION_AXES = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS
        )
    }
}
