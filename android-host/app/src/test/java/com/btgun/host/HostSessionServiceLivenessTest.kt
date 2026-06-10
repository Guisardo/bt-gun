package com.btgun.host

import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.session.DesktopLivenessCoordinator
import com.btgun.host.session.DesktopLivenessUpdate
import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.HapticResult
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.hid.BtGunHidDescriptor
import com.btgun.host.hid.BtGunHidHostConnectionState
import com.btgun.host.hid.BtGunHidInputSendResult
import com.btgun.host.hid.BtGunHidOutputCallbackKind
import com.btgun.host.hid.BtGunHidOutputValidationState
import com.btgun.host.hid.BtGunHidReportTypes
import com.btgun.host.hid.BtGunHidStatus
import com.btgun.host.transport.InputStreamLifecycleState
import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample

fun main() {
    heartbeatTimeoutClearSchedulesUdpDisconnectGrace()
    controlDisconnectWithoutUdpStreamStaysStopped()
    bluetoothGamepadActionConstantsAreExplicit()
    bluetoothGamepadStartDoesNotStartLanDesktopControl()
    bluetoothGamepadStopSessionAndDestroyCloseHidMode()
    liveInputFanoutOnlySendsWhenHidHostConnected()
    hidOutputCallbackRoutesThroughPhoneHapticExecutorStatus()
}

private fun heartbeatTimeoutClearSchedulesUdpDisconnectGrace() {
    val timeoutUpdate = DesktopLivenessUpdate(
        linkState = DesktopLinkState(
            phase = DesktopLinkPhase.DISCONNECTED,
            lastControlError = DesktopLivenessCoordinator.DEFAULT_TIMEOUT_ERROR,
        ),
        shouldContinuePolling = false,
        shouldClearClient = true,
        shouldCloseClient = true,
    )

    val action = hostDesktopLivenessActionFor(timeoutUpdate)

    expectTrue("timeout schedules udp grace", action.shouldScheduleUdpDisconnectGrace)
    expectTrue("timeout clears client", action.shouldClearClient)
    expectTrue("timeout cancels liveness tick", action.shouldCancelLivenessTick)
    expectTrue("timeout closes client", action.shouldCloseClient)
    expectFalse("timeout stops polling", action.shouldContinuePolling)
}

private fun controlDisconnectWithoutUdpStreamStaysStopped() {
    expectEquals(
        "no sender stays stopped",
        InputStreamLifecycleState.STOPPED,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = false,
            hasConfig = false,
            controlDisconnectGraceMs = null,
        ),
    )
    expectEquals(
        "missing config stays stopped",
        InputStreamLifecycleState.STOPPED,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = false,
            controlDisconnectGraceMs = null,
        ),
    )
    expectEquals(
        "zero grace marks active stream stale",
        InputStreamLifecycleState.STALE,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = true,
            controlDisconnectGraceMs = 0L,
        ),
    )
    expectEquals(
        "positive grace enters grace",
        InputStreamLifecycleState.GRACE,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = true,
            controlDisconnectGraceMs = 1_500L,
        ),
    )
}

private fun bluetoothGamepadActionConstantsAreExplicit() {
    expectEquals(
        "start hid action",
        "com.btgun.host.action.START_BLUETOOTH_GAMEPAD",
        HostSessionService.ACTION_START_BLUETOOTH_GAMEPAD,
    )
    expectEquals(
        "stop hid action",
        "com.btgun.host.action.STOP_BLUETOOTH_GAMEPAD",
        HostSessionService.ACTION_STOP_BLUETOOTH_GAMEPAD,
    )
    expectEquals(
        "pairing hid action",
        "com.btgun.host.action.START_HID_PAIRING_WINDOW",
        HostSessionService.ACTION_START_HID_PAIRING_WINDOW,
    )
}

private fun bluetoothGamepadStartDoesNotStartLanDesktopControl() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })
    val before = HostSessionState(
        desktopLinkState = DesktopLinkState(phase = DesktopLinkPhase.DISCONNECTED),
        packetStreamState = InputStreamLifecycleState.STOPPED,
    )

    val after = controller.startBluetoothGamepad(before)

    expectEquals("hid started", 1, driver.startCount)
    expectEquals("desktop unchanged", before.desktopLinkState, after.desktopLinkState)
    expectEquals("packet unchanged", InputStreamLifecycleState.STOPPED, after.packetStreamState)
}

private fun bluetoothGamepadStopSessionAndDestroyCloseHidMode() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })

    controller.startBluetoothGamepad(HostSessionState())
    controller.stopBluetoothGamepad(HostSessionState())
    controller.startBluetoothGamepad(HostSessionState())
    controller.close()

    expectEquals("stop called for explicit stop and close", 2, driver.stopCount)
    expectEquals("close called once", 1, driver.closeCount)
}

private fun liveInputFanoutOnlySendsWhenHidHostConnected() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })
    controller.startBluetoothGamepad(HostSessionState())
    val inputState = GunInputState(pressedControls = setOf("trigger"), stickAxisX = 0.25f, stickAxisY = -0.5f)
    val motion = MotionSample(
        provider = MotionProvider.ROTATION_VECTOR,
        sourceSensorElapsedNanos = 2_000L,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        aimX = 0.5f,
        aimY = -0.25f,
    )

    controller.fanOutLiveInput(HostSessionState(gunInputState = inputState, lastMotionSample = null))
    driver.status = driver.status.copy(hostConnection = BtGunHidHostConnectionState.CONNECTED)
    controller.fanOutLiveInput(HostSessionState(gunInputState = inputState, lastMotionSample = motionEnvelope(motion)))

    expectEquals("only connected send", 1, driver.sentInputs.size)
    expectEquals("sent state", inputState, driver.sentInputs.single().state)
    expectEquals("sent motion", motion, driver.sentInputs.single().motion)
}

private fun hidOutputCallbackRoutesThroughPhoneHapticExecutorStatus() {
    val haptics = mutableListOf<DesktopHapticCommand>()
    val driver = RecordingHostHidGamepadDriver(
        onOutput = { command ->
            haptics += command
            HapticResult(command.commandId, HapticResultStatus.STARTED, "phone pulse started", 1_000L)
        },
    )
    val controller = HostSessionHidController(driverFactory = { driver })
    controller.startBluetoothGamepad(HostSessionState())

    driver.simulateSetReport(
        reportType = BtGunHidReportTypes.OUTPUT,
        reportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
        payload = outputPayload(strength = 128, durationMs = 75, ttlMs = 500),
    )
    val state = controller.refreshStatus(HostSessionState())

    expectEquals("haptic routed", 1, haptics.size)
    expectEquals("callback status", BtGunHidOutputCallbackKind.SET_REPORT, state.hidGamepadStatus.lastOutputCallback.kind)
    expectEquals("validation status", BtGunHidOutputValidationState.VALID, state.hidGamepadStatus.lastOutputValidation.state)
    expectEquals("haptic result", HapticResultStatus.STARTED, state.hidGamepadStatus.lastHapticResult?.status)
}

private fun motionEnvelope(payload: MotionSample) =
    com.btgun.host.model.LiveEnvelope(
        stream = com.btgun.host.model.StreamKind.MOTION,
        seq = 1L,
        captureElapsedNanos = 2_000L,
        emittedElapsedNanos = 2_000L,
        payload = payload,
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

private class RecordingHostHidGamepadDriver(
    private val onOutput: (DesktopHapticCommand) -> HapticResult? = { null },
) : HostHidGamepadDriver {
    override var status: BtGunHidStatus = BtGunHidStatus()
    var startCount = 0
    var stopCount = 0
    var closeCount = 0
    val sentInputs = mutableListOf<SentInput>()

    override fun startGamepadMode() {
        startCount += 1
    }

    override fun stopGamepadMode() {
        stopCount += 1
    }

    override fun openPairingWindow(durationSeconds: Int): Boolean =
        true

    override fun sendInput(state: GunInputState, motion: MotionSample?, stale: Boolean): BtGunHidInputSendResult {
        sentInputs += SentInput(state, motion, stale)
        return BtGunHidInputSendResult.SENT
    }

    fun simulateSetReport(reportType: Int, reportId: Int, payload: ByteArray) {
        val command = com.btgun.host.hid.BtGunHidOutputReportMapper
            .toHapticCommand(reportId, payload, "hid-output-test")
        if (command is com.btgun.host.hid.BtGunHidOutputReportResult.Valid) {
            val result = onOutput(command.command)
            status = status.copy(
                lastOutputCallback = com.btgun.host.hid.BtGunHidOutputCallbackStatus(
                    kind = BtGunHidOutputCallbackKind.SET_REPORT,
                    reportType = reportType,
                    reportId = reportId,
                    payloadLength = payload.size,
                ),
                lastOutputValidation = com.btgun.host.hid.BtGunHidOutputValidationStatus(
                    state = BtGunHidOutputValidationState.VALID,
                    detail = "valid output report",
                ),
                lastHapticResult = result,
            )
        }
    }

    override fun close() {
        closeCount += 1
    }
}

private data class SentInput(
    val state: GunInputState,
    val motion: MotionSample?,
    val stale: Boolean,
)

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError("$label expected true")
    }
}

private fun expectFalse(label: String, actual: Boolean) {
    if (actual) {
        throw AssertionError("$label expected false")
    }
}
