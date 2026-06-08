package com.btgun.desktop.ui

import com.btgun.desktop.transport.InputStreamLifecycleState

fun main() {
    pairingWindowExposesOnlyConciseTransportStateLabels()
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
