package com.btgun.host.hid

import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.HapticResult
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample

fun main() {
    doesNotSendBeforeProxyRegistrationAndHostConnection()
    startRequestsHidDeviceProxyAndRegistersGamepadSdp()
    callbacksUpdateStatusAndHandleValidOutputReports()
    invalidOutputReportCallsReportErrorAndSkipsHaptics()
    pairingWindowSecurityExceptionSurfacesBlockedStatus()
    staleProxyCallbacksAfterStopDoNotRegisterHidMode()
    olderProxyCallbacksDoNotOverrideNewStart()
    stopDisconnectAndUnplugSendNeutralReleaseReport()
    stopKeepsRegistrationForPairedMacosReconnect()
    unregisterAndCloseAreIdempotent()
}

private fun doesNotSendBeforeProxyRegistrationAndHostConnection() {
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })

    expectEquals("no proxy send", BtGunHidInputSendResult.NO_PROXY, gamepad.sendInput(state(), motion(), stale = false))
    gamepad.startGamepadMode()
    expectEquals("pending proxy send", BtGunHidInputSendResult.NO_PROXY, gamepad.sendInput(state(), motion(), stale = false))
    connector.callback.onProxyAvailable(connector.proxy)
    expectEquals("registered command requested", 1, connector.proxy.registeredSettings.size)
    expectEquals("registered callback captured", true, connector.proxy.callback != null)
    expectEquals("before app registered", BtGunHidInputSendResult.NOT_REGISTERED, gamepad.sendInput(state(), motion(), stale = false))
    connector.proxy.callback?.onAppStatusChanged(null, registered = true)
    expectEquals("no host send", BtGunHidInputSendResult.NO_HOST, gamepad.sendInput(state(), motion(), stale = false))
    connector.proxy.callback?.onConnectionStateChanged(FakeHost, BtGunHidConnectionStates.CONNECTED)

    val result = gamepad.sendInput(state(), motion(), stale = false)

    expectEquals("connected send", BtGunHidInputSendResult.SENT, result)
    expectEquals("sent count", 1, connector.proxy.sentReports.size)
    expectEquals("sent report id", BtGunHidDescriptor.INPUT_REPORT_ID, connector.proxy.sentReports.single().reportId)
    expectEquals("sent payload length", BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES, connector.proxy.sentReports.single().payload.size)
    expectEquals("status last input", BtGunHidInputSendResult.SENT, gamepad.status.lastInputReport.result)
}

private fun startRequestsHidDeviceProxyAndRegistersGamepadSdp() {
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })

    gamepad.startGamepadMode()
    connector.callback.onProxyAvailable(connector.proxy)
    val pairingOpened = gamepad.openPairingWindow(durationSeconds = 120)

    val settings = connector.proxy.registeredSettings.single()
    expectEquals("proxy requests", 1, connector.requestCount)
    expectEquals("status proxy available", BtGunHidProxyState.AVAILABLE, gamepad.status.proxy)
    expectEquals("sdp name", "BT Gun Gamepad", settings.name)
    expectEquals("sdp description", "BT Gun Android HID Gamepad", settings.description)
    expectEquals("sdp provider", "BT Gun", settings.provider)
    expectByteArray("descriptor bytes", BtGunHidDescriptor.DESCRIPTOR_BYTES, settings.descriptors)
    expectEquals("pairing opened", true, pairingOpened)
    expectEquals("pairing duration", 120, connector.pairingDurations.single())
}

private fun callbacksUpdateStatusAndHandleValidOutputReports() {
    val haptics = mutableListOf<DesktopHapticCommand>()
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(
        connector = connector,
        hapticHandler = { command ->
            haptics += command
            HapticResult(command.commandId, HapticResultStatus.STARTED, "phone pulse started", 1_000L)
        },
        commandIdFactory = { "hid-output-command" },
    )
    startRegisteredAndConnected(gamepad, connector)

    connector.proxy.callback?.onGetReport(FakeHost, BtGunHidReportTypes.INPUT, BtGunHidDescriptor.INPUT_REPORT_ID, bufferSize = 64)
    connector.proxy.callback?.onSetReport(
        FakeHost,
        BtGunHidReportTypes.OUTPUT,
        BtGunHidDescriptor.OUTPUT_REPORT_ID,
        outputPayload(strength = 128, durationMs = 75, ttlMs = 500),
    )
    connector.proxy.callback?.onInterruptData(
        FakeHost,
        BtGunHidDescriptor.OUTPUT_REPORT_ID,
        outputPayload(strength = 64, durationMs = 50, ttlMs = 300),
    )

    expectEquals("latest output callback", BtGunHidOutputCallbackKind.INTERRUPT_DATA, gamepad.status.lastOutputCallback.kind)
    expectEquals("reply report count", 1, connector.proxy.replyReports.size)
    expectEquals("set plus interrupt haptics", 2, haptics.size)
    expectEquals("set command id", "hid-output-command", haptics.first().commandId)
    expectEquals("validation valid", BtGunHidOutputValidationState.VALID, gamepad.status.lastOutputValidation.state)
    expectEquals("haptic started", HapticResultStatus.STARTED, gamepad.status.lastHapticResult?.status)

    connector.proxy.callback?.onConnectionStateChanged(FakeHost, BtGunHidConnectionStates.DISCONNECTED)
    expectEquals("host disconnected", BtGunHidHostConnectionState.DISCONNECTED, gamepad.status.hostConnection)
    connector.proxy.callback?.onConnectionStateChanged(FakeHost, BtGunHidConnectionStates.CONNECTED)
    connector.proxy.callback?.onVirtualCableUnplug(FakeHost)
    expectEquals("virtual cable unplug clears host", BtGunHidHostConnectionState.DISCONNECTED, gamepad.status.hostConnection)
}

private fun invalidOutputReportCallsReportErrorAndSkipsHaptics() {
    val haptics = mutableListOf<DesktopHapticCommand>()
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(
        connector = connector,
        hapticHandler = { command ->
            haptics += command
            null
        },
    )
    startRegisteredAndConnected(gamepad, connector)

    connector.proxy.callback?.onSetReport(
        FakeHost,
        BtGunHidReportTypes.OUTPUT,
        BtGunHidDescriptor.OUTPUT_REPORT_ID,
        outputPayload(strength = 128, durationMs = 0, ttlMs = 500),
    )

    expectEquals("no haptics on invalid", 0, haptics.size)
    expectEquals("report error count", 1, connector.proxy.reportErrors.size)
    expectEquals("validation invalid", BtGunHidOutputValidationState.INVALID, gamepad.status.lastOutputValidation.state)
    expectEquals("callback set report", BtGunHidOutputCallbackKind.SET_REPORT, gamepad.status.lastOutputCallback.kind)
}

private fun pairingWindowSecurityExceptionSurfacesBlockedStatus() {
    val connector = FakeHidProfileConnector().also {
        it.openPairingError = SecurityException("missing advertise")
    }
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })

    val opened = gamepad.openPairingWindow(durationSeconds = 120)

    expectEquals("pairing blocked", false, opened)
    expectEquals("pairing status closed", false, gamepad.status.pairingWindow.open)
    expectEquals("pairing security detail", "pairing window blocked: SecurityException", gamepad.status.pairingWindow.detail)
    expectEquals("unsupported reason", "pairing window blocked: SecurityException", gamepad.status.unsupportedReason)
}

private fun staleProxyCallbacksAfterStopDoNotRegisterHidMode() {
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })

    gamepad.startGamepadMode()
    val staleCallback = connector.callback
    gamepad.stopGamepadMode()
    staleCallback.onProxyAvailable(connector.proxy)

    expectEquals("stale callback did not register", 0, connector.proxy.registeredSettings.size)
    expectEquals("stale proxy unregistered", 1, connector.proxy.unregisterCount)
    expectEquals("stale status remains stopped", BtGunHidProxyState.NOT_REQUESTED, gamepad.status.proxy)
    expectEquals("stale send has no proxy", BtGunHidInputSendResult.NO_PROXY, gamepad.sendInput(state(), motion(), stale = false))
}

private fun olderProxyCallbacksDoNotOverrideNewStart() {
    val connector = FakeHidProfileConnector()
    val staleProxy = FakeHidDeviceProxy()
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })

    gamepad.startGamepadMode()
    val firstCallback = connector.callback
    gamepad.stopGamepadMode()
    gamepad.startGamepadMode()
    val currentCallback = connector.callback
    firstCallback.onProxyAvailable(staleProxy)
    currentCallback.onProxyAvailable(connector.proxy)

    expectEquals("old proxy not registered", 0, staleProxy.registeredSettings.size)
    expectEquals("old proxy unregistered", 1, staleProxy.unregisterCount)
    expectEquals("current proxy registered once", 1, connector.proxy.registeredSettings.size)
    expectEquals("current status available", BtGunHidProxyState.AVAILABLE, gamepad.status.proxy)
}

private fun stopDisconnectAndUnplugSendNeutralReleaseReport() {
    val stopConnector = FakeHidProfileConnector()
    val stopGamepad = AndroidBluetoothHidGamepad(connector = stopConnector, hapticHandler = { null })
    startRegisteredAndConnected(stopGamepad, stopConnector)
    stopGamepad.sendInput(state(), motion(), stale = false)
    expectEquals("stop active report is pressed", true, stopConnector.proxy.sentReports.first().payload.any { it != 0.toByte() })

    stopGamepad.stopGamepadMode()

    expectEquals("stop sent pressed plus neutral", 2, stopConnector.proxy.sentReports.size)
    expectNeutralReport("stop neutral report", stopConnector.proxy.sentReports.last())
    expectEquals("stop disconnected host", 1, stopConnector.proxy.disconnectedHosts.size)

    val disconnectConnector = FakeHidProfileConnector()
    val disconnectGamepad = AndroidBluetoothHidGamepad(connector = disconnectConnector, hapticHandler = { null })
    startRegisteredAndConnected(disconnectGamepad, disconnectConnector)
    disconnectGamepad.sendInput(state(), motion(), stale = false)

    disconnectConnector.proxy.callback?.onConnectionStateChanged(FakeHost, BtGunHidConnectionStates.DISCONNECTED)

    expectEquals("disconnect sent pressed plus neutral", 2, disconnectConnector.proxy.sentReports.size)
    expectNeutralReport("disconnect neutral report", disconnectConnector.proxy.sentReports.last())

    val unplugConnector = FakeHidProfileConnector()
    val unplugGamepad = AndroidBluetoothHidGamepad(connector = unplugConnector, hapticHandler = { null })
    startRegisteredAndConnected(unplugGamepad, unplugConnector)
    unplugGamepad.sendInput(state(), motion(), stale = false)

    unplugConnector.proxy.callback?.onVirtualCableUnplug(FakeHost)

    expectEquals("unplug sent pressed plus neutral", 2, unplugConnector.proxy.sentReports.size)
    expectNeutralReport("unplug neutral report", unplugConnector.proxy.sentReports.last())
}

private fun stopKeepsRegistrationForPairedMacosReconnect() {
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })
    startRegisteredAndConnected(gamepad, connector)

    gamepad.stopGamepadMode()

    expectEquals("stop does not unregister registered HID app", 0, connector.proxy.unregisterCount)
    expectEquals("stop disconnects current host", listOf(FakeHost), connector.proxy.disconnectedHosts)
    expectEquals("stop keeps registration status", BtGunHidRegistrationState.REGISTERED, gamepad.status.registration)
    expectEquals("stop clears host status", BtGunHidHostConnectionState.NOT_CONNECTED, gamepad.status.hostConnection)

    gamepad.startGamepadMode()
    connector.proxy.callback?.onConnectionStateChanged(FakeHost, BtGunHidConnectionStates.CONNECTED)
    val result = gamepad.sendInput(state(), motion(), stale = false)

    expectEquals("restart reuses existing registration", 1, connector.proxy.registeredSettings.size)
    expectEquals("restart accepts reconnect", BtGunHidHostConnectionState.CONNECTED, gamepad.status.hostConnection)
    expectEquals("restart sends after reconnect", BtGunHidInputSendResult.SENT, result)
}

private fun unregisterAndCloseAreIdempotent() {
    val connector = FakeHidProfileConnector()
    val gamepad = AndroidBluetoothHidGamepad(connector = connector, hapticHandler = { null })
    startRegisteredAndConnected(gamepad, connector)

    gamepad.stopGamepadMode()
    gamepad.stopGamepadMode()
    gamepad.close()
    gamepad.close()

    expectEquals("unregister once", 1, connector.proxy.unregisterCount)
    expectEquals("disconnect on explicit stop only", 1, connector.proxy.disconnectedHosts.size)
    expectEquals("connector closed once", 1, connector.closeCount)
    expectEquals("status unregistered", BtGunHidRegistrationState.NOT_REGISTERED, gamepad.status.registration)
    expectEquals("send after close no proxy", BtGunHidInputSendResult.NO_PROXY, gamepad.sendInput(state(), motion(), stale = false))
}

private fun expectNeutralReport(label: String, report: SentReport) {
    expectEquals("$label id", BtGunHidDescriptor.INPUT_REPORT_ID, report.reportId)
    expectByteArray("$label payload", ByteArray(BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES), report.payload)
}

private fun startRegisteredAndConnected(
    gamepad: AndroidBluetoothHidGamepad,
    connector: FakeHidProfileConnector,
) {
    gamepad.startGamepadMode()
    connector.callback.onProxyAvailable(connector.proxy)
    connector.proxy.callback?.onAppStatusChanged(null, registered = true)
    connector.proxy.callback?.onConnectionStateChanged(FakeHost, BtGunHidConnectionStates.CONNECTED)
}

private fun state(): GunInputState =
    GunInputState(pressedControls = setOf("trigger", "button_a"), stickAxisX = 0.25f, stickAxisY = -0.5f)

private fun motion(): MotionSample =
    MotionSample(
        provider = MotionProvider.ROTATION_VECTOR,
        sourceSensorElapsedNanos = 1_000L,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        aimX = 0.5f,
        aimY = -0.25f,
    )

private fun outputPayload(strength: Int, durationMs: Int, ttlMs: Int): ByteArray =
    byteArrayOf(
        BtGunHidDescriptor.OUTPUT_REPORT_VERSION.toByte(),
        strength.toByte(),
        (durationMs and 0xff).toByte(),
        ((durationMs ushr 8) and 0xff).toByte(),
        (ttlMs and 0xff).toByte(),
        ((ttlMs ushr 8) and 0xff).toByte(),
        0,
        0,
    )

private object FakeHost : BtGunHidHost {
    override val label: String = "fake-host"
}

private class FakeHidProfileConnector : BtGunHidProfileConnector {
    val proxy = FakeHidDeviceProxy()
    private val callbacks = mutableListOf<BtGunHidProfileCallback>()
    val callback: BtGunHidProfileCallback
        get() = callbacks.last()
    var requestCount = 0
    var closeCount = 0
    val pairingDurations = mutableListOf<Int>()
    var openPairingError: RuntimeException? = null

    override fun requestHidDeviceProxy(callback: BtGunHidProfileCallback) {
        requestCount += 1
        callbacks += callback
    }

    override fun openPairingWindow(durationSeconds: Int): Boolean {
        openPairingError?.let { throw it }
        pairingDurations += durationSeconds
        return true
    }

    override fun close() {
        closeCount += 1
    }
}

private class FakeHidDeviceProxy : BtGunHidDeviceProxy {
    val registeredSettings = mutableListOf<BtGunHidSdpSettings>()
    var callback: BtGunHidDeviceCallback? = null
    val sentReports = mutableListOf<SentReport>()
    val replyReports = mutableListOf<ReplyReport>()
    val reportErrors = mutableListOf<ReportError>()
    val disconnectedHosts = mutableListOf<BtGunHidHost>()
    var unregisterCount = 0

    override fun registerApp(settings: BtGunHidSdpSettings, callback: BtGunHidDeviceCallback): Boolean {
        registeredSettings += settings
        this.callback = callback
        return true
    }

    override fun unregisterApp(): Boolean {
        unregisterCount += 1
        return true
    }

    override fun disconnect(host: BtGunHidHost): Boolean {
        disconnectedHosts += host
        return true
    }

    override fun sendReport(host: BtGunHidHost, reportId: Int, payload: ByteArray): Boolean {
        sentReports += SentReport(host, reportId, payload.copyOf())
        return true
    }

    override fun replyReport(host: BtGunHidHost, reportType: Int, reportId: Int, payload: ByteArray): Boolean {
        replyReports += ReplyReport(host, reportType, reportId, payload.copyOf())
        return true
    }

    override fun reportError(host: BtGunHidHost, error: Byte): Boolean {
        reportErrors += ReportError(host, error)
        return true
    }
}

private data class SentReport(val host: BtGunHidHost, val reportId: Int, val payload: ByteArray)
private data class ReplyReport(val host: BtGunHidHost, val reportType: Int, val reportId: Int, val payload: ByteArray)
private data class ReportError(val host: BtGunHidHost, val error: Byte)

private fun expectByteArray(label: String, expected: ByteArray, actual: ByteArray) {
    if (!expected.contentEquals(actual)) {
        throw AssertionError("$label expected <${expected.toHex()}> but was <${actual.toHex()}>")
    }
}

private fun ByteArray.toHex(): String =
    joinToString(" ") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
