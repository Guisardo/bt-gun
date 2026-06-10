package com.btgun.desktop.backend.macos

import com.btgun.desktop.backend.BackendCapabilityPresets
import com.btgun.desktop.backend.BackendCapabilities
import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.SimulatedOutputReport
import com.btgun.desktop.haptics.HapticCommand

fun main() {
    rejectsPublishBeforeStartAndSubmitsNoHelperBytes()
    publishAfterStartUsesPackerAndReportOnlyHelperBoundary()
    helperSubmitErrorRejectsPublishAndLeavesStateUnchanged()
    drainsQueuedHelperOutputReportsToMacosHapticCommands()
    simulatedOutputReportStaysMapperOnlyNotOsOriginProof()
    macosCoreHidCapabilitiesStayHonestBeforeAndAfterProofStatus()
}

private fun rejectsPublishBeforeStartAndSubmitsNoHelperBytes() {
    val helper = FakeMacosHidHelper()
    val backend = MacosVirtualControllerBackend(helper = helper)
    val result = backend.publish(SemanticControllerState(trigger = true, sourceSequence = 7L))

    expectTrue("publish before start rejected", result is BackendPublishResult.Rejected)
    expectContains("reject reason", (result as BackendPublishResult.Rejected).reason, "not started")
    expectEquals("no helper submissions", 0, helper.submitted.size)
    expectEquals("last publish result", result, backend.lastPublishResult)
    helper.expectNoForbiddenBoundaryMaterial()
}

private fun publishAfterStartUsesPackerAndReportOnlyHelperBoundary() {
    val helper = FakeMacosHidHelper()
    val backend = MacosVirtualControllerBackend(helper = helper)
    val state = SemanticControllerState(
        trigger = true,
        reload = true,
        x = true,
        b = true,
        stickX = 1111,
        stickY = -2222,
        aimX = 0.5f,
        aimY = -0.25f,
        stale = false,
        sourceSequence = 42L,
    )

    expectEquals("start result", BackendLifecycleResult.Started, backend.start())
    expectEquals("publish result", BackendPublishResult.Published, backend.publish(state))

    val expected = MacosHidReportPacker.packInputReport(state)
    expectEquals("one helper submission", 1, helper.submitted.size)
    expectByteArrayEquals("submitted report bytes", expected.bytes, helper.submitted.single().bytes)
    expectEquals("submitted stale metadata", expected.stale, helper.submitted.single().stale)
    expectEquals("submitted source sequence", expected.sourceSequence, helper.submitted.single().sourceSequence)
    expectEquals("current state", state, backend.currentState)
    expectEquals("last publish result", BackendPublishResult.Published, backend.lastPublishResult)
    helper.expectNoForbiddenBoundaryMaterial()
}

private fun helperSubmitErrorRejectsPublishAndLeavesStateUnchanged() {
    val helper = FakeMacosHidHelper()
    helper.nextSubmitResult = MacosHidHelperResult.Error("helper-offline")
    val backend = MacosVirtualControllerBackend(helper = helper)
    val state = SemanticControllerState(trigger = true, sourceSequence = 8L)

    backend.start()
    val result = backend.publish(state)

    expectTrue("helper error rejected", result is BackendPublishResult.Rejected)
    expectContains("helper error reason", (result as BackendPublishResult.Rejected).reason, "helper-offline")
    expectEquals("helper saw report", 1, helper.submitted.size)
    expectEquals("state unchanged after rejected submit", SemanticControllerState(), backend.currentState)
    expectEquals("last publish result", result, backend.lastPublishResult)
    helper.expectNoForbiddenBoundaryMaterial()
}

private fun drainsQueuedHelperOutputReportsToMacosHapticCommands() {
    val helper = FakeMacosHidHelper()
    helper.outputReports.add(outputReport(strength = 192, durationMs = 150, ttlMs = 600))
    helper.outputReports.add(outputReport(strength = 64, durationMs = 75, ttlMs = 250))
    helper.outputReports.add(ByteArray(MACOS_OUTPUT_REPORT_LENGTH_BYTES) { 0x7f })
    val backend = MacosVirtualControllerBackend(helper = helper)

    backend.start()
    val commands: List<HapticCommand> = backend.drainOutputHaptics(nowElapsedNanos = 1_000_000L)

    expectEquals("command count", 2, commands.size)
    expectEquals("first command id", "macos-output-report-1", commands[0].commandId)
    expectClose("first strength", 192.0 / 255.0, commands[0].strength)
    expectEquals("first duration", 150L, commands[0].durationMs)
    expectEquals("first ttl", 600L, commands[0].ttlMs)
    expectEquals("first pattern", null, commands[0].pattern)
    expectEquals("second command id", "macos-output-report-2", commands[1].commandId)
    expectClose("second strength", 64.0 / 255.0, commands[1].strength)
    expectEquals("second duration", 75L, commands[1].durationMs)
    expectEquals("second ttl", 250L, commands[1].ttlMs)
    helper.expectNoForbiddenBoundaryMaterial()
}

private fun simulatedOutputReportStaysMapperOnlyNotOsOriginProof() {
    val helper = FakeMacosHidHelper()
    val backend = MacosVirtualControllerBackend(helper = helper)
    val command: HapticCommand = requireNotNull(
        backend.simulateOutputReport(
            SimulatedOutputReport(strength = 0.75, durationMs = 120L, ttlMs = 500L),
        ),
    )

    expectEquals("simulated command id", "macos-output-report-simulated-1", command.commandId)
    expectEquals("no helper read for simulated output", 0, helper.readOutputCalls)
    expectEquals("pattern output unsupported", null, backend.simulateOutputReport(SimulatedOutputReport(0.5, 100L, 500L, "pulse")))
    val capabilities: BackendCapabilities = BackendCapabilityPresets.macosCoreHid(
        MacosHidHelperStatus(osVisible = true, setReportCallbackSeen = false),
    )
    expectEquals("simulated output does not prove output report", false, capabilities.haptics.outputReport)
    expectExplicitUnsupported(capabilities, feature = "output-report")
    helper.expectNoForbiddenBoundaryMaterial()
}

private fun macosCoreHidCapabilitiesStayHonestBeforeAndAfterProofStatus() {
    val blocked: BackendCapabilities = BackendCapabilityPresets.macosCoreHid(
        MacosHidHelperStatus(version = 1, deviceActive = true, osVisible = false, setReportCallbackSeen = false),
    )

    expectEquals("platform", "macos-corehid", blocked.platform)
    expectEquals("buttons", listOf("trigger", "reload", "x", "y", "a", "b"), blocked.buttons)
    expectEquals("axes", listOf("stickX", "stickY", "aimX", "aimY"), blocked.axes)
    expectEquals("haptic strength", true, blocked.haptics.strength)
    expectEquals("haptic duration", true, blocked.haptics.duration)
    expectEquals("haptic pattern", false, blocked.haptics.pattern)
    expectEquals("phone haptic", true, blocked.haptics.phoneHaptic)
    expectEquals("output report before proof", false, blocked.haptics.outputReport)
    expectExplicitUnsupported(blocked, feature = "os-visible-device")
    expectExplicitUnsupported(blocked, feature = "output-report")

    val proven: BackendCapabilities = BackendCapabilityPresets.macosCoreHid(
        MacosHidHelperStatus(version = 1, deviceActive = true, osVisible = true, setReportCallbackSeen = true),
    )

    expectEquals("platform after proof", "macos-corehid", proven.platform)
    expectEquals("output report after proof", true, proven.haptics.outputReport)
    expectTrue("os visible limitation cleared", proven.limitations.none { reason -> reason.feature == "os-visible-device" })
    expectTrue("output report limitation cleared", proven.haptics.unsupported.none { reason -> reason.feature == "output-report" })
    expectExplicitUnsupported(proven, feature = "pattern")
}

private class FakeMacosHidHelper : MacosHidHelperClient {
    val submitted = mutableListOf<MacosInputReport>()
    val outputReports = ArrayDeque<ByteArray>()
    val interactions = mutableListOf<String>()
    var nextSubmitResult: MacosHidHelperResult = MacosHidHelperResult.Ok
    var status: MacosHidHelperStatus = MacosHidHelperStatus()
    var readOutputCalls = 0
    var closed = false

    override fun submitInputReport(report: MacosInputReport): MacosHidHelperResult {
        submitted.add(report)
        interactions.add("SUBMIT_INPUT ${report.bytes.toHex()}")
        return nextSubmitResult
    }

    override fun readOutputReport(): ByteArray? {
        readOutputCalls += 1
        interactions.add("READ_OUTPUT")
        return outputReports.removeFirstOrNull()
    }

    override fun readStatus(): MacosHidHelperStatus {
        interactions.add("STATUS")
        return status
    }

    override fun close() {
        interactions.add("QUIT")
        closed = true
    }

    fun expectNoForbiddenBoundaryMaterial() {
        val joined = interactions.joinToString(" ")
        val forbidden = listOf(
            "LAN",
            "Pairing",
            "auth",
            "QR",
            "proof",
            "stream key",
            "HMAC key",
            "session id",
            "desktop mapping",
            "HapticCommand",
        )
        forbidden.forEach { token ->
            expectTrue("helper boundary excludes $token", !joined.contains(token, ignoreCase = true))
        }
    }
}

private fun expectExplicitUnsupported(capabilities: BackendCapabilities, feature: String) {
    val allUnsupported = capabilities.limitations + capabilities.haptics.unsupported
    expectTrue(
        "$feature unsupported reason explicit",
        allUnsupported.any {
            it.platform == "macos-corehid" &&
                it.feature == feature &&
                it.detail.isNotBlank()
        },
    )
}

private fun outputReport(
    strength: Int,
    durationMs: Int,
    ttlMs: Int,
): ByteArray =
    byteArrayOf(
        MACOS_OUTPUT_REPORT_ID.toByte(),
        MACOS_OUTPUT_REPORT_VERSION.toByte(),
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
