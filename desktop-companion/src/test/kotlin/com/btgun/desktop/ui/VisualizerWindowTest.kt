package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.transport.InputStreamLifecycleState
import java.io.File

fun main() {
    visualizerWindowExposesUiSpecCopyWithoutLaunchingSwing()
    visualizerWindowExposesLifecycleHelpersWithoutOwningTransport()
    visualizerWindowSourceExcludesForbiddenLabels()
    visualizerFactoryReusesExistingWindow()
    visualizerCoordinatorOpensOnceOnAuthenticatedSession()
    visualizerCoordinatorPreservesChecklistAndContextOnDisconnect()
    mainWiresEventHubVisualizerFactoryAndPairingWindow()
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
        val quotedLabel = Regex(""""${Regex.escape(label)}"""", RegexOption.IGNORE_CASE)
        expectFalse("forbidden visualizer label absent: $label", quotedLabel.containsMatchIn(source))
    }
}

private fun visualizerFactoryReusesExistingWindow() {
    var created = 0
    var opened = 0
    val factory = VisualizerWindowFactory {
        created += 1
        object : VisualizerWindowHandle {
            override fun open() {
                opened += 1
            }

            override fun applyModel(model: VisualizerModel) = Unit
        }
    }

    factory.open()
    factory.open()

    expectEquals("one visualizer instance", 1, created)
    expectEquals("open reuses existing visualizer", 2, opened)
}

private fun visualizerCoordinatorOpensOnceOnAuthenticatedSession() {
    var opened = 0
    val applied = mutableListOf<InputStreamLifecycleState>()
    val coordinator = VisualizerWindowCoordinator(
        windowFactory = VisualizerWindowFactory {
            object : VisualizerWindowHandle {
                override fun open() {
                    opened += 1
                }

                override fun applyModel(model: VisualizerModel) {
                    applied.add(model.packetLifecycle)
                }
            }
        },
    )

    coordinator.onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
    coordinator.onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
    coordinator.onSessionStateChanged(ControlServerSessionState.DEGRADED)
    coordinator.onSessionStateChanged(ControlServerSessionState.DISCONNECTED)

    expectEquals("authenticated opens once", 1, opened)
    expectEquals(
        "session states applied",
        listOf(
            InputStreamLifecycleState.ACTIVE,
            InputStreamLifecycleState.ACTIVE,
            InputStreamLifecycleState.STALE,
            InputStreamLifecycleState.STOPPED,
        ),
        applied,
    )
}

private fun visualizerCoordinatorPreservesChecklistAndContextOnDisconnect() {
    val existing = VisualizerModel.initial()
        .confirmRow(VisualizerChecklistRowId.RECENTER_AIM_ZERO)
        .copy(lastAcceptedAimX = 0.4f, lastAcceptedAimY = -0.2f)

    val disconnected = VisualizerWindowCoordinator.modelForSessionState(
        model = existing,
        sessionState = ControlServerSessionState.DISCONNECTED,
    )

    expectEquals(
        "recenter confirmation preserved",
        VisualizerChecklistState.CONFIRMED,
        disconnected.row(VisualizerChecklistRowId.RECENTER_AIM_ZERO).state,
    )
    expectEquals("last aim x preserved", 0.4f, disconnected.lastAcceptedAimX)
    expectEquals("last aim y preserved", -0.2f, disconnected.lastAcceptedAimY)
    expectEquals("disconnect updates lifecycle", InputStreamLifecycleState.STOPPED, disconnected.packetLifecycle)
}

private fun mainWiresEventHubVisualizerFactoryAndPairingWindow() {
    val source = File("src/main/kotlin/com/btgun/desktop/Main.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()

    listOf(
        "DesktopUiEventHub(controlServer).attach()",
        "VisualizerWindowFactory",
        "VisualizerWindowCoordinator",
        "PairingWindow(",
        "eventHub = eventHub",
        "openVisualizer = coordinator::openVisualizer",
    ).forEach { expected ->
        expectContains("main wiring contains $expected", source, expected)
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
