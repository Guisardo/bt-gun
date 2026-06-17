package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.BackendCapabilityPresets
import com.btgun.desktop.backend.BackendCapabilities
import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.SimulatedOutputReport
import com.btgun.desktop.haptics.HapticCommand
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main() {
    driverBridgeSubmitUsesExplicitSourceSequenceProtocol()
    driverBridgeReadOutputNoOutputIsNull()
    driverBridgeTimeoutPoisonsHelperAndRestartsNextRequest()
    rejectsPublishBeforeStartAndRecordsLastResult()
    startupReadinessCheckRejectsMissingDriver()
    d13FixedMappingPublishUsesPlanOnePackerAndBridgeBoundary()
    publishObservesBridgeSourceSequenceStatus()
    publishDoesNotHoldBackendLockDuringBridgeIo()
    submitErrorRejectsPublishAndRecordsLastResult()
    d16RealOutputReportBytesDrainToPhoneHapticCommandWithTickLimit()
    windowsVhfCapabilitiesDeclareRealOutputReportPath()
}

private fun driverBridgeSubmitUsesExplicitSourceSequenceProtocol() {
    val shell = shellOrSkip() ?: return
    val tempDir = Files.createTempDirectory("btgun-driver-bridge-protocol")
    val capture = tempDir.resolve("command.txt")
    val bridge = WindowsDriverBridge(
        WindowsDriverBridgeConfig(
            command = listOf(
                shell.toString(),
                "-c",
                "IFS= read -r line; printf '%s\\n' \"\$line\" > \"\$0\"; printf 'OK\\n'",
                capture.toString(),
            ),
            requestTimeoutMillis = 1_000L,
        ),
    )

    try {
        val result = bridge.submitInputReport(
            WindowsInputReport(
                bytes = byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                stale = false,
                sourceSequence = 987_654_321L,
            ),
        )

        expectEquals("submit result", WindowsDriverBridgeResult.Ok, result)
        expectEquals(
            "submit command includes source sequence",
            "SUBMIT_INPUT 987654321 01000000000000000000",
            Files.readString(capture).trim(),
        )
    } finally {
        bridge.close()
    }
}

private fun driverBridgeReadOutputNoOutputIsNull() {
    val shell = shellOrSkip() ?: return
    val bridge = WindowsDriverBridge(
        WindowsDriverBridgeConfig(
            command = listOf(
                shell.toString(),
                "-c",
                "IFS= read -r line; if [ \"\$line\" = READ_OUTPUT ]; then printf 'NO_OUTPUT\\n'; else printf 'ERR invalid\\n'; fi",
            ),
            requestTimeoutMillis = 1_000L,
        ),
    )

    try {
        expectEquals("no output", null, bridge.readOutputReport())
    } finally {
        bridge.close()
    }
}

private fun driverBridgeTimeoutPoisonsHelperAndRestartsNextRequest() {
    val shell = shellOrSkip() ?: return
    val tempDir = Files.createTempDirectory("btgun-driver-bridge-timeout")
    val counter = tempDir.resolve("counter.txt")
    val statusJson = "STATUS {\"driverStarted\":true,\"vhfStarted\":true,\"queueDepth\":0,\"lastInputSequence\":77}"
    val bridge = WindowsDriverBridge(
        WindowsDriverBridgeConfig(
            command = listOf(
                shell.toString(),
                "-c",
                "count=0; if [ -f \"\$0\" ]; then count=\$(cat \"\$0\"); fi; " +
                    "count=\$((count + 1)); printf '%s\\n' \"\$count\" > \"\$0\"; " +
                    "IFS= read -r line; if [ \"\$count\" = \"1\" ]; then sleep 5; else printf '%s\\n' '$statusJson'; fi",
                counter.toString(),
            ),
            requestTimeoutMillis = 100L,
        ),
    )

    try {
        val timedOut = bridge.readStatus()
        val restarted = bridge.readStatus()

        expectEquals("timed out status not ready", false, timedOut.driverStarted)
        expectEquals("restarted status ready", true, restarted.driverStarted)
        expectEquals("restarted source sequence", 77L, restarted.lastInputSequence)
        expectEquals("helper restarted", "2", Files.readString(counter).trim())
    } finally {
        bridge.close()
    }
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

private fun startupReadinessCheckRejectsMissingDriver() {
    val bridge = FakeWindowsDriverBridge()
    bridge.status = WindowsDriverBridgeStatus(driverStarted = true, vhfStarted = false)
    val backend = WindowsVirtualControllerBackend(bridge = bridge)

    val start = backend.start()
    val publish = backend.publish(SemanticControllerState(trigger = true, sourceSequence = 7L))

    expectTrue("start reports stopped", start is BackendLifecycleResult.Stopped)
    expectContains("readiness reason", (start as BackendLifecycleResult.Stopped).reason, "not ready")
    expectEquals("lifecycle remains stopped", com.btgun.desktop.backend.BackendLifecycleState.STOPPED, backend.lifecycleState)
    expectTrue("publish rejected after failed readiness", publish is BackendPublishResult.Rejected)
    expectEquals("no bridge submissions", 0, bridge.submitted.size)
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

private fun publishObservesBridgeSourceSequenceStatus() {
    val bridge = FakeWindowsDriverBridge()
    val backend = WindowsVirtualControllerBackend(bridge = bridge)

    backend.start()
    backend.publish(SemanticControllerState(trigger = true, sourceSequence = 123L))

    expectEquals("bridge status source sequence", 123L, backend.lastBridgeStatus?.lastInputSequence)
}

private fun publishDoesNotHoldBackendLockDuringBridgeIo() {
    val bridge = BlockingSubmitWindowsDriverBridge()
    val backend = WindowsVirtualControllerBackend(bridge = bridge)
    backend.start()

    val publishThread = thread(start = true) {
        backend.publish(SemanticControllerState(trigger = true, sourceSequence = 9L))
    }
    expectTrue("submit entered", bridge.submitEntered.await(1, TimeUnit.SECONDS))

    val stateReadCompleted = CountDownLatch(1)
    thread(start = true) {
        backend.currentState
        stateReadCompleted.countDown()
    }

    expectTrue("current state not blocked by submit", stateReadCompleted.await(200, TimeUnit.MILLISECONDS))
    bridge.releaseSubmit.countDown()
    publishThread.join(1_000)
    expectTrue("publish thread completed", !publishThread.isAlive)
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

private fun d16RealOutputReportBytesDrainToPhoneHapticCommandWithTickLimit() {
    val bridge = FakeWindowsDriverBridge()
    repeat(17) { index ->
        bridge.outputReports.add(outputReport(strength = 64 + index, durationMs = 75, ttlMs = 250))
    }
    val backend = WindowsVirtualControllerBackend(bridge = bridge)

    backend.start()
    val commands: List<HapticCommand> = backend.drainOutputHaptics(nowElapsedNanos = 1_000_000L)
    val secondTick: List<HapticCommand> = backend.drainOutputHaptics(nowElapsedNanos = 1_000_001L)

    expectEquals("command count", 16, commands.size)
    expectEquals("first command id", "windows-output-report-1", commands[0].commandId)
    expectClose("first strength", 64.0 / 255.0, commands[0].strength)
    expectEquals("first duration", 75L, commands[0].durationMs)
    expectEquals("first ttl", 250L, commands[0].ttlMs)
    expectEquals("first pattern", null, commands[0].pattern)
    expectEquals("second tick count", 1, secondTick.size)
    expectEquals("second tick command id", "windows-output-report-17", secondTick.single().commandId)
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
    var status: WindowsDriverBridgeStatus = WindowsDriverBridgeStatus(driverStarted = true, vhfStarted = true)
    var closed = false

    override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult {
        submitted.add(report)
        return nextSubmitResult
    }

    override fun readOutputReport(): ByteArray? =
        outputReports.removeFirstOrNull()

    override fun readStatus(): WindowsDriverBridgeStatus =
        status.copy(lastInputSequence = submitted.lastOrNull()?.sourceSequence ?: status.lastInputSequence)

    override fun close() {
        closed = true
    }
}

private class BlockingSubmitWindowsDriverBridge : WindowsDriverBridgeClient {
    val submitEntered = CountDownLatch(1)
    val releaseSubmit = CountDownLatch(1)

    override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult {
        submitEntered.countDown()
        releaseSubmit.await(1, TimeUnit.SECONDS)
        return WindowsDriverBridgeResult.Ok
    }

    override fun readOutputReport(): ByteArray? =
        null

    override fun readStatus(): WindowsDriverBridgeStatus =
        WindowsDriverBridgeStatus(driverStarted = true, vhfStarted = true, lastInputSequence = 9L)

    override fun close() = Unit
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

private fun shellOrSkip(): Path? {
    val shell = Path.of("/bin/sh")
    if (!Files.isExecutable(shell)) {
        println("Skipping WindowsDriverBridge process protocol tests: /bin/sh unavailable")
        return null
    }
    return shell
}
