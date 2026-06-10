package com.btgun.desktop.ui

import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.macos.MacosHidHelperStatus
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.transport.InputStreamLifecycleState

fun main() {
    pairingWindowExposesOnlyConciseTransportStateLabels()
    pairingWindowExposesHapticSmokeStateWithoutLaunchingSwing()
    pairingWindowFormatsMacosBackendDiagnostics()
}

private fun pairingWindowExposesOnlyConciseTransportStateLabels() {
    expectEquals(
        "transport labels",
        listOf("active", "grace", "stale", "stopped"),
        PairingWindow.requiredTransportStateLabels(),
    )

    val diagnostics = PairingWindow.transportDiagnosticsHtml(InputStreamLifecycleState.STALE)
    expectContains("stale visible", diagnostics, "Packet stream: stale")
    listOf("latency", "packet loss", "virtual joystick", "profile mapping", "HID").forEach { forbidden ->
        expectFalse("no later-phase label $forbidden", diagnostics.contains(forbidden, ignoreCase = true))
    }
}

private fun pairingWindowExposesHapticSmokeStateWithoutLaunchingSwing() {
    val command = PairingWindow.smokeHapticCommand("cmd-ui")

    expectEquals("command id", "cmd-ui", command.commandId)
    expectEquals("strength", 0.6, command.strength)
    expectEquals("duration", 80L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectTrue("auth enables haptic", PairingWindow.hapticButtonEnabled(DesktopSessionUiState.AUTHENTICATED, serverAuthenticated = true))
    expectFalse("registry alone does not enable haptic", PairingWindow.hapticButtonEnabled(DesktopSessionUiState.AUTHENTICATED, serverAuthenticated = false))
    expectFalse("disconnect disables haptic", PairingWindow.hapticButtonEnabled(DesktopSessionUiState.DISCONNECTED))
    expectContains(
        "haptic result status",
        PairingWindow.hapticStatusText(
            HapticResult(
                commandId = "cmd-ui",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 10L,
            ),
        ),
        "started: phone pulse started",
    )
}

private fun pairingWindowFormatsMacosBackendDiagnostics() {
    expectEquals(
        "disabled macos status",
        "disabled",
        PairingWindow.macosBackendStatusText(diagnostics = null, startupDiagnostic = "disabled"),
    )

    val status = PairingWindow.macosBackendStatusText(
        diagnostics = MacosBackendRuntimeDiagnostics(
            lifecycleState = BackendLifecycleState.STARTED,
            lastPublishResult = BackendPublishResult.Published,
            stale = true,
            lastSourceSequence = 9L,
            lastHapticSendResult = HapticSendResult.Sent,
            outputHapticCommandsRouted = 2L,
            helperStatus = MacosHidHelperStatus(
                deviceActive = true,
                osVisible = false,
                setReportCallbackSeen = true,
                inputReportsSubmitted = 3L,
                outputReportsQueued = 1L,
                malformedInputReports = 0L,
                malformedOutputReports = 0L,
            ),
        ),
        startupDiagnostic = "enabled",
    )

    listOf(
        "lifecycle=started",
        "lastPublish=published",
        "stale=true",
        "lastHapticSend=sent",
        "routed=2",
        "helper=active=true",
        "visible=false",
        "setReport=true",
    ).forEach { expected ->
        expectContains("macos status contains $expected", status, expected)
    }
    listOf("QR", "proof", "stream key", "HMAC", "private key", "raw packet", "screenshot").forEach { forbidden ->
        expectFalse("macos status excludes $forbidden", status.contains(forbidden, ignoreCase = true))
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectContains(label: String, actual: String, expectedPart: String) {
    if (!actual.contains(expectedPart)) {
        throw AssertionError("$label expected <$actual> to contain <$expectedPart>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError(label)
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}
