package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.BackendCapabilityPresets
import com.btgun.desktop.backend.BackendCapabilities
import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.SimulatedOutputReport
import com.btgun.desktop.haptics.HapticCommand

fun main() {
    rejectsPublishBeforeStartAndRecordsLastResult()
    d13FixedMappingPublishUsesPlanOnePackerAndBridgeBoundary()
    submitErrorRejectsPublishAndRecordsLastResult()
    d16RealOutputReportBytesDrainToPhoneHapticCommand()
    windowsVhfCapabilitiesDeclareRealOutputReportPath()
}

private fun rejectsPublishBeforeStartAndRecordsLastResult() {
    val bridge = FakeWindowsDriverBridge()
    val backend = WindowsVirtualControllerBackend(bridge = bridge)
    val result = backend.publish(SemanticControllerState(trigger = true, sourceSequence = 7L))

    expectTrue("publish before start rejected", result is BackendPublishResult.Rejected)
    expectContains("reject reason", (result as BackendPublishResult.Rejected).reason, "not started")
    expectEquals("no bridge submissions", 0, bridge.submitted.size)
    expectEquals("last publish result", result, backend.lastPublishResult)
}

private fun d13FixedMappingPublishUsesPlanOnePackerAndBridgeBoundary() {
    val bridge = FakeWindowsDriverBridge()
    val backend = WindowsVirtualControllerBackend(bridge = bridge)
    val state = SemanticControllerState(
        trigger = true,
        reload = true,
        x = true,
        a = true,
        stickX = 12345,
        stickY = -12345,
        aimX = 0.5f,
        aimY = -0.25f,
        stale = false,
        sourceSequence = 42L,
    )

    expectEquals("start result", BackendLifecycleResult.Started, backend.start())
    expectEquals("publish result", BackendPublishResult.Published, backend.publish(state))

    val expected = WindowsHidReportPacker.packInputReport(state)
    expectEquals("one bridge submission", 1, bridge.submitted.size)
    expectByteArrayEquals("submitted report bytes", expected.bytes, bridge.submitted.single().bytes)
    expectEquals("submitted stale metadata", expected.stale, bridge.submitted.single().stale)
    expectEquals("submitted source sequence", expected.sourceSequence, bridge.submitted.single().sourceSequence)
    expectEquals("current state", state, backend.currentState)
    expectEquals("last publish result", BackendPublishResult.Published, backend.lastPublishResult)
}

private fun submitErrorRejectsPublishAndRecordsLastResult() {
    val bridge = FakeWindowsDriverBridge()
    bridge.nextSubmitResult = WindowsDriverBridgeResult.Error("driver offline")
    val backend = WindowsVirtualControllerBackend(bridge = bridge)
    val state = SemanticControllerState(trigger = true, sourceSequence = 8L)

    backend.start()
    val result = backend.publish(state)

    expectTrue("submit error rejected", result is BackendPublishResult.Rejected)
    expectContains("submit error reason", (result as BackendPublishResult.Rejected).reason, "driver offline")
    expectEquals("bridge saw report", 1, bridge.submitted.size)
    expectEquals("state unchanged after rejected submit", SemanticControllerState(), backend.currentState)
    expectEquals("last publish result", result, backend.lastPublishResult)
}

private fun d16RealOutputReportBytesDrainToPhoneHapticCommand() {
    val bridge = FakeWindowsDriverBridge()
    bridge.outputReports.add(outputReport(strength = 192, durationMs = 150, ttlMs = 600))
    bridge.outputReports.add(outputReport(strength = 64, durationMs = 75, ttlMs = 250))
    val backend = WindowsVirtualControllerBackend(bridge = bridge)

    backend.start()
    val commands: List<HapticCommand> = backend.drainOutputHaptics(nowElapsedNanos = 1_000_000L)

    expectEquals("command count", 2, commands.size)
    expectEquals("first command id", "windows-output-report-1", commands[0].commandId)
    expectClose("first strength", 192.0 / 255.0, commands[0].strength)
    expectEquals("first duration", 150L, commands[0].durationMs)
    expectEquals("first ttl", 600L, commands[0].ttlMs)
    expectEquals("first pattern", null, commands[0].pattern)
    expectEquals("second command id", "windows-output-report-2", commands[1].commandId)
    expectClose("second strength", 64.0 / 255.0, commands[1].strength)
    expectEquals("second duration", 75L, commands[1].durationMs)
    expectEquals("second ttl", 250L, commands[1].ttlMs)
}

private fun windowsVhfCapabilitiesDeclareRealOutputReportPath() {
    val capabilities: BackendCapabilities = BackendCapabilityPresets.windowsVhf()

    expectEquals("platform", "windows-vhf", capabilities.platform)
    expectEquals("buttons", listOf("trigger", "reload", "x", "y", "a", "b"), capabilities.buttons)
    expectEquals("axes", listOf("stickX", "stickY", "aimX", "aimY"), capabilities.axes)
    expectEquals("haptic strength", true, capabilities.haptics.strength)
    expectEquals("haptic duration", true, capabilities.haptics.duration)
    expectEquals("haptic pattern", false, capabilities.haptics.pattern)
    expectEquals("phone haptic", true, capabilities.haptics.phoneHaptic)
    expectEquals("output report", true, capabilities.haptics.outputReport)
    expectTrue(
        "pattern limitation explicit",
        capabilities.haptics.unsupported.any {
            it.platform == "windows-vhf" &&
                it.feature == "pattern" &&
                it.detail.contains("pattern", ignoreCase = true)
        },
    )
    expectTrue(
        "Chrome Gamepad API vibration not marked unsupported",
        capabilities.haptics.unsupported.none { it.feature == "chrome-gamepad-api-vibration" },
    )
    val allDetails = (capabilities.limitations + capabilities.haptics.unsupported).joinToString(" ") { it.detail }
    expectTrue("not Phase 5 stub behavior", !allDetails.contains("stub", ignoreCase = true))
    expectTrue("no fake device limitation", capabilities.limitations.none { it.feature == "os-visible-device" })
}

private class FakeWindowsDriverBridge : WindowsDriverBridgeClient {
    val submitted = mutableListOf<WindowsInputReport>()
    val outputReports = ArrayDeque<ByteArray>()
    var nextSubmitResult: WindowsDriverBridgeResult = WindowsDriverBridgeResult.Ok
    var status: WindowsDriverBridgeStatus = WindowsDriverBridgeStatus()
    var closed = false

    override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult {
        submitted.add(report)
        return nextSubmitResult
    }

    override fun readOutputReport(): ByteArray? =
        outputReports.removeFirstOrNull()

    override fun readStatus(): WindowsDriverBridgeStatus =
        status

    override fun close() {
        closed = true
    }
}

private fun outputReport(
    strength: Int,
    durationMs: Int,
    ttlMs: Int,
): ByteArray =
    byteArrayOf(
        WINDOWS_OUTPUT_REPORT_ID.toByte(),
        WINDOWS_OUTPUT_REPORT_VERSION.toByte(),
        strength.toByte(),
        (durationMs and 0xff).toByte(),
        ((durationMs ushr 8) and 0xff).toByte(),
        (ttlMs and 0xff).toByte(),
        ((ttlMs ushr 8) and 0xff).toByte(),
        0,
        0,
    )

private fun expectByteArrayEquals(label: String, expected: ByteArray, actual: ByteArray) {
    if (!expected.contentEquals(actual)) {
        throw AssertionError("$label expected <${expected.toHex()}> but was <${actual.toHex()}>")
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}

private fun expectContains(label: String, text: String, expected: String) {
    if (!text.contains(expected, ignoreCase = true)) {
        throw AssertionError("$label expected <$text> to contain <$expected>")
    }
}

private fun expectClose(label: String, expected: Double, actual: Double, tolerance: Double = 0.000001) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
