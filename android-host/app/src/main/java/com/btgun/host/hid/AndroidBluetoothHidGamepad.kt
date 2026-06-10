package com.btgun.host.hid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.HapticResult
import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionSample
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

interface BtGunHidHost {
    val label: String
}

data class BtGunHidSdpSettings(
    val name: String,
    val description: String,
    val provider: String,
    val subclass: Byte,
    val descriptors: ByteArray,
)

interface BtGunHidProfileCallback {
    fun onProxyAvailable(proxy: BtGunHidDeviceProxy)
    fun onProxyUnavailable(reason: String)
}

interface BtGunHidProfileConnector : AutoCloseable {
    fun requestHidDeviceProxy(callback: BtGunHidProfileCallback)
    fun openPairingWindow(durationSeconds: Int): Boolean
    override fun close()
}

interface BtGunHidDeviceCallback {
    fun onAppStatusChanged(host: BtGunHidHost?, registered: Boolean)
    fun onConnectionStateChanged(host: BtGunHidHost, state: Int)
    fun onGetReport(host: BtGunHidHost, reportType: Int, reportId: Int, bufferSize: Int)
    fun onSetReport(host: BtGunHidHost, reportType: Int, reportId: Int, payload: ByteArray)
    fun onInterruptData(host: BtGunHidHost, reportId: Int, payload: ByteArray)
    fun onVirtualCableUnplug(host: BtGunHidHost)
}

interface BtGunHidDeviceProxy {
    fun registerApp(settings: BtGunHidSdpSettings, callback: BtGunHidDeviceCallback): Boolean
    fun unregisterApp(): Boolean
    fun sendReport(host: BtGunHidHost, reportId: Int, payload: ByteArray): Boolean
    fun replyReport(host: BtGunHidHost, reportType: Int, reportId: Int, payload: ByteArray): Boolean
    fun reportError(host: BtGunHidHost, error: Byte): Boolean
}

class AndroidBluetoothHidGamepad(
    private val connector: BtGunHidProfileConnector,
    private val hapticHandler: (DesktopHapticCommand) -> HapticResult?,
    private val commandIdFactory: () -> String = DefaultCommandIdFactory(),
    private val onStatusChanged: (BtGunHidStatus) -> Unit = {},
) : AutoCloseable {
    private var proxy: BtGunHidDeviceProxy? = null
    private var callback: BtGunHidDeviceCallback? = null
    private var connectedHost: BtGunHidHost? = null
    private var lastInputReport: BtGunHidInputReport? = null
    private var started = false
    private var closed = false
    private var registered = false

    var status: BtGunHidStatus = BtGunHidStatus()
        private set(value) {
            field = value
            onStatusChanged(value)
        }

    fun startGamepadMode() {
        if (closed || started) return
        started = true
        status = status.copy(proxy = BtGunHidProxyState.REQUESTING, registration = BtGunHidRegistrationState.NOT_REGISTERED)
        connector.requestHidDeviceProxy(ProfileCallback())
    }

    fun stopGamepadMode() {
        val activeProxy = proxy
        if (registered && activeProxy != null) {
            activeProxy.unregisterApp()
        }
        registered = false
        proxy = null
        callback = null
        connectedHost = null
        started = false
        status = status.copy(
            proxy = if (closed) BtGunHidProxyState.CLOSED else BtGunHidProxyState.NOT_REQUESTED,
            registration = BtGunHidRegistrationState.NOT_REGISTERED,
            hostConnection = BtGunHidHostConnectionState.NOT_CONNECTED,
        )
    }

    fun openPairingWindow(durationSeconds: Int): Boolean {
        require(durationSeconds > 0) { "durationSeconds must be positive" }
        val opened = connector.openPairingWindow(durationSeconds)
        status = status.copy(
            pairingWindow = BtGunHidPairingWindowStatus(
                open = opened,
                durationSeconds = durationSeconds,
                detail = if (opened) "pairing window open" else "pairing window failed",
            ),
        )
        return opened
    }

    fun sendInput(
        state: GunInputState,
        motion: MotionSample?,
        stale: Boolean,
    ): BtGunHidInputSendResult {
        val activeProxy = proxy ?: return recordInputResult(BtGunHidInputSendResult.NO_PROXY)
        if (!registered) return recordInputResult(BtGunHidInputSendResult.NOT_REGISTERED)
        val host = connectedHost ?: return recordInputResult(BtGunHidInputSendResult.NO_HOST)

        val report = BtGunHidReportPacker.packInputReport(state = state, motion = motion, stale = stale)
        lastInputReport = report
        val result = if (activeProxy.sendReport(host, report.reportId, report.bytes)) {
            BtGunHidInputSendResult.SENT
        } else {
            BtGunHidInputSendResult.FAILED
        }
        return recordInputResult(result, report)
    }

    override fun close() {
        if (closed) return
        stopGamepadMode()
        connector.close()
        closed = true
        status = status.copy(proxy = BtGunHidProxyState.CLOSED)
    }

    private fun onProxyAvailable(newProxy: BtGunHidDeviceProxy) {
        proxy = newProxy
        status = status.copy(proxy = BtGunHidProxyState.AVAILABLE, registration = BtGunHidRegistrationState.REGISTERING)
        val newCallback = DeviceCallback()
        callback = newCallback
        val accepted = newProxy.registerApp(gamepadSdpSettings(), newCallback)
        if (!accepted) {
            registered = false
            status = status.copy(registration = BtGunHidRegistrationState.FAILED)
        }
    }

    private fun onProxyUnavailable(reason: String) {
        proxy = null
        registered = false
        status = status.copy(
            proxy = BtGunHidProxyState.UNAVAILABLE,
            registration = BtGunHidRegistrationState.FAILED,
            unsupportedReason = reason,
        )
    }

    private fun onAppStatusChanged(registered: Boolean) {
        this.registered = registered
        if (!registered) {
            connectedHost = null
        }
        status = status.copy(
            registration = if (registered) BtGunHidRegistrationState.REGISTERED else BtGunHidRegistrationState.FAILED,
            hostConnection = if (registered) status.hostConnection else BtGunHidHostConnectionState.NOT_CONNECTED,
        )
    }

    private fun onConnectionStateChanged(host: BtGunHidHost, state: Int) {
        if (state == BtGunHidConnectionStates.CONNECTED) {
            connectedHost = host
            status = status.copy(hostConnection = BtGunHidHostConnectionState.CONNECTED)
        } else {
            if (connectedHost == host) {
                connectedHost = null
            }
            status = status.copy(hostConnection = BtGunHidHostConnectionState.DISCONNECTED)
        }
    }

    private fun onGetReport(host: BtGunHidHost, reportType: Int, reportId: Int, bufferSize: Int) {
        status = status.copy(
            lastOutputCallback = BtGunHidOutputCallbackStatus(
                kind = BtGunHidOutputCallbackKind.GET_REPORT,
                reportType = reportType,
                reportId = reportId,
                payloadLength = bufferSize,
            ),
        )
        val payload = if (reportType == BtGunHidReportTypes.INPUT && reportId == BtGunHidDescriptor.INPUT_REPORT_ID) {
            lastInputReport?.bytes ?: ByteArray(BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES)
        } else {
            proxy?.reportError(host, BtGunHidErrorResponses.UNSUPPORTED_REQUEST)
            return
        }
        proxy?.replyReport(host, reportType, reportId, payload)
    }

    private fun onOutputReport(
        host: BtGunHidHost,
        kind: BtGunHidOutputCallbackKind,
        reportType: Int?,
        reportId: Int,
        payload: ByteArray,
    ) {
        status = status.copy(
            lastOutputCallback = BtGunHidOutputCallbackStatus(
                kind = kind,
                reportType = reportType,
                reportId = reportId,
                payloadLength = payload.size,
            ),
        )
        if (reportType != null && reportType != BtGunHidReportTypes.OUTPUT) {
            proxy?.reportError(host, BtGunHidErrorResponses.UNSUPPORTED_REQUEST)
            status = status.copy(
                lastOutputValidation = BtGunHidOutputValidationStatus(
                    state = BtGunHidOutputValidationState.UNSUPPORTED,
                    detail = "unsupported report type",
                ),
            )
            return
        }

        when (val result = BtGunHidOutputReportMapper.toHapticCommand(reportId, payload, commandIdFactory())) {
            is BtGunHidOutputReportResult.Valid -> {
                val hapticResult = hapticHandler(result.command)
                status = status.copy(
                    lastOutputValidation = BtGunHidOutputValidationStatus(
                        state = BtGunHidOutputValidationState.VALID,
                        detail = "valid output report",
                    ),
                    lastHapticResult = hapticResult,
                )
            }
            is BtGunHidOutputReportResult.Invalid -> {
                proxy?.reportError(host, errorForInvalid(result.reason))
                status = status.copy(
                    lastOutputValidation = BtGunHidOutputValidationStatus(
                        state = BtGunHidOutputValidationState.INVALID,
                        detail = result.reason,
                    ),
                )
            }
        }
    }

    private fun errorForInvalid(reason: String): Byte =
        if (reason.contains("report id")) {
            BtGunHidErrorResponses.INVALID_REPORT_ID
        } else {
            BtGunHidErrorResponses.INVALID_PARAMETER
        }

    private fun onVirtualCableUnplug() {
        connectedHost = null
        status = status.copy(
            hostConnection = BtGunHidHostConnectionState.DISCONNECTED,
            lastOutputCallback = BtGunHidOutputCallbackStatus(kind = BtGunHidOutputCallbackKind.VIRTUAL_CABLE_UNPLUG),
        )
    }

    private fun recordInputResult(
        result: BtGunHidInputSendResult,
        report: BtGunHidInputReport? = null,
    ): BtGunHidInputSendResult {
        status = status.copy(
            lastInputReport = BtGunHidInputReportStatus(
                result = result,
                reportId = report?.reportId,
                payloadLength = report?.bytes?.size ?: 0,
                aimSource = report?.aimSource,
                stale = report?.stale ?: false,
            ),
        )
        return result
    }

    private inner class ProfileCallback : BtGunHidProfileCallback {
        override fun onProxyAvailable(proxy: BtGunHidDeviceProxy) {
            this@AndroidBluetoothHidGamepad.onProxyAvailable(proxy)
        }

        override fun onProxyUnavailable(reason: String) {
            this@AndroidBluetoothHidGamepad.onProxyUnavailable(reason)
        }
    }

    private inner class DeviceCallback : BtGunHidDeviceCallback {
        override fun onAppStatusChanged(host: BtGunHidHost?, registered: Boolean) {
            this@AndroidBluetoothHidGamepad.onAppStatusChanged(registered)
        }

        override fun onConnectionStateChanged(host: BtGunHidHost, state: Int) {
            this@AndroidBluetoothHidGamepad.onConnectionStateChanged(host, state)
        }

        override fun onGetReport(host: BtGunHidHost, reportType: Int, reportId: Int, bufferSize: Int) {
            this@AndroidBluetoothHidGamepad.onGetReport(host, reportType, reportId, bufferSize)
        }

        override fun onSetReport(host: BtGunHidHost, reportType: Int, reportId: Int, payload: ByteArray) {
            onOutputReport(host, BtGunHidOutputCallbackKind.SET_REPORT, reportType, reportId, payload)
        }

        override fun onInterruptData(host: BtGunHidHost, reportId: Int, payload: ByteArray) {
            onOutputReport(host, BtGunHidOutputCallbackKind.INTERRUPT_DATA, null, reportId, payload)
        }

        override fun onVirtualCableUnplug(host: BtGunHidHost) {
            this@AndroidBluetoothHidGamepad.onVirtualCableUnplug()
        }
    }
}

fun gamepadSdpSettings(): BtGunHidSdpSettings =
    BtGunHidSdpSettings(
        name = "BT Gun Gamepad",
        description = "BT Gun Android HID Gamepad",
        provider = "BT Gun",
        subclass = BT_GUN_HID_GAMEPAD_SUBCLASS,
        descriptors = BtGunHidDescriptor.DESCRIPTOR_BYTES.copyOf(),
    )

private class DefaultCommandIdFactory : () -> String {
    private val next = AtomicInteger(1)

    override fun invoke(): String =
        "android-hid-output-${next.getAndIncrement()}"
}

private const val BT_GUN_HID_GAMEPAD_SUBCLASS: Byte = 0x05

class AndroidBtGunHidProfileConnector(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val executor: Executor,
) : BtGunHidProfileConnector {
    private var listener: BluetoothProfile.ServiceListener? = null
    private var closed = false

    override fun requestHidDeviceProxy(callback: BtGunHidProfileCallback) {
        if (closed) {
            callback.onProxyUnavailable("connector closed")
            return
        }
        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                    callback.onProxyAvailable(AndroidBtGunHidDeviceProxy(proxy, executor))
                } else {
                    callback.onProxyUnavailable("HID_DEVICE proxy unavailable")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    callback.onProxyUnavailable("HID_DEVICE proxy disconnected")
                }
            }
        }
        listener = serviceListener
        if (!adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)) {
            callback.onProxyUnavailable("HID_DEVICE proxy request rejected")
        }
    }

    override fun openPairingWindow(durationSeconds: Int): Boolean {
        if (durationSeconds <= 0 || closed) return false
        return runCatching {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    override fun close() {
        if (closed) return
        closed = true
        val serviceListener = listener ?: return
        adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, null)
        listener = null
    }
}

private class AndroidBtGunHidDeviceProxy(
    private val hidDevice: BluetoothHidDevice,
    private val executor: Executor,
) : BtGunHidDeviceProxy {
    override fun registerApp(settings: BtGunHidSdpSettings, callback: BtGunHidDeviceCallback): Boolean =
        hidDevice.registerApp(
            BluetoothHidDeviceAppSdpSettings(
                settings.name,
                settings.description,
                settings.provider,
                settings.subclass,
                settings.descriptors.copyOf(),
            ),
            null,
            null,
            executor,
            AndroidCallback(callback),
        )

    override fun unregisterApp(): Boolean =
        hidDevice.unregisterApp()

    override fun sendReport(host: BtGunHidHost, reportId: Int, payload: ByteArray): Boolean {
        val device = host.bluetoothDeviceOrNull() ?: return false
        return hidDevice.sendReport(device, reportId, payload.copyOf())
    }

    override fun replyReport(host: BtGunHidHost, reportType: Int, reportId: Int, payload: ByteArray): Boolean {
        val device = host.bluetoothDeviceOrNull() ?: return false
        return hidDevice.replyReport(device, reportType.toByte(), reportId.toByte(), payload.copyOf())
    }

    override fun reportError(host: BtGunHidHost, error: Byte): Boolean {
        val device = host.bluetoothDeviceOrNull() ?: return false
        return hidDevice.reportError(device, error)
    }
}

private data class AndroidBtGunHidHost(
    val device: BluetoothDevice,
) : BtGunHidHost {
    override val label: String = "bluetooth-host"
}

private class AndroidCallback(
    private val callback: BtGunHidDeviceCallback,
) : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        callback.onAppStatusChanged(pluggedDevice?.let(::AndroidBtGunHidHost), registered)
    }

    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        callback.onConnectionStateChanged(AndroidBtGunHidHost(device), state)
    }

    override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
        callback.onGetReport(AndroidBtGunHidHost(device), type.toUnsignedInt(), id.toUnsignedInt(), bufferSize)
    }

    override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
        callback.onSetReport(AndroidBtGunHidHost(device), type.toUnsignedInt(), id.toUnsignedInt(), data.copyOf())
    }

    override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
        callback.onInterruptData(AndroidBtGunHidHost(device), reportId.toUnsignedInt(), data.copyOf())
    }

    override fun onVirtualCableUnplug(device: BluetoothDevice) {
        callback.onVirtualCableUnplug(AndroidBtGunHidHost(device))
    }
}

private fun BtGunHidHost.bluetoothDeviceOrNull(): BluetoothDevice? =
    (this as? AndroidBtGunHidHost)?.device

private fun Byte.toUnsignedInt(): Int =
    toInt() and 0xff
