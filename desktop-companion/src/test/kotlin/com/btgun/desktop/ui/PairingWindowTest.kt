package com.btgun.desktop.ui

import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.transport.InputStreamLifecycleState

fun main() {
    pairingWindowExposesOnlyConciseTransportStateLabels()
    pairingWindowExposesHapticSmokeStateWithoutLaunchingSwing()
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
