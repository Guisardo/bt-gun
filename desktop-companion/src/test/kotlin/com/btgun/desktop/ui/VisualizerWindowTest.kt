package com.btgun.desktop.ui

import java.io.File

fun main() {
    visualizerWindowExposesUiSpecCopyWithoutLaunchingSwing()
    visualizerWindowExposesLifecycleHelpersWithoutOwningTransport()
    visualizerWindowSourceExcludesForbiddenLabels()
}

private fun visualizerWindowExposesUiSpecCopyWithoutLaunchingSwing() {
    expectEquals("window title", "BT Gun Visualizer", VisualizerWindow.windowTitle())
    expectEquals("empty state heading", "Waiting for authenticated session", VisualizerWindow.emptyStateHeading())
    expectEquals("top pending summary", "Phase 9 checks pending", VisualizerWindow.topSummaryPending())
    expectEquals(
        "required sections",
        listOf(
            "Acceptance checklist",
            "Live gamepad",
            "Latency and packet loss",
            "Recent product events",
        ),
        VisualizerWindow.requiredSectionLabels(),
    )
    expectEquals("haptic action", "Run phone haptic test", VisualizerWindow.hapticButtonLabel())
}

private fun visualizerWindowExposesLifecycleHelpersWithoutOwningTransport() {
    val disconnected = VisualizerWindow.summaryFor(VisualizerDisplayState.DISCONNECTED)
    expectContains("disconnected summary", disconnected, "disconnected")
    expectContains("disconnected preserves checklist", disconnected, "preserve the checklist")

    val close = VisualizerWindow.closeBehavior()
    expectTrue("close disposes visualizer", close.disposeVisualizerUi)
    expectFalse("close does not stop control server", close.stopControlServer)
    expectFalse("close does not stop backends", close.stopBackendRuntimes)
    expectTrue(
        "open method exposed",
        VisualizerWindow::class.java.methods.any { method -> method.name == "open" },
    )
}

private fun visualizerWindowSourceExcludesForbiddenLabels() {
    val source = File("src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()
    val forbidden = listOf(
        "Submit",
        "OK",
        "Cancel",
        "Save",
        "Click Here",
        "Edit desktop " + "profile",
        "Desktop profile " + "editor",
        "generated evidence " + "bundle pass",
    )

    forbidden.forEach { label ->
        expectFalse("forbidden visualizer label absent: $label", source.contains(label, ignoreCase = true))
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectContains(label: String, actual: String, expectedPart: String) {
    if (!actual.contains(expectedPart, ignoreCase = true)) {
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
