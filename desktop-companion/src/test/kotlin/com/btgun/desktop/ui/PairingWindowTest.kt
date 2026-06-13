package com.btgun.desktop.ui

import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.macos.MacosHidHelperStatus
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.transport.InputStreamLifecycleState
import java.io.File

fun main() {
    pairingWindowExposesOnlyConciseTransportStateLabels()
    pairingWindowExposesHapticSmokeStateWithoutLaunchingSwing()
    pairingWindowFormatsMacosBackendDiagnostics()
    pairingWindowFormatsReadOnlyAndroidProfileDiagnostics()
    pairingWindowFreshProfileDiagnosticsAreNeutral()
    pairingWindowExposesVisualizerReopenActionWithoutLaunchingSwing()
    pairingWindowDoesNotRenderVisualizerOnlyPanels()
    pairingWindowForbiddenDesktopProfileControlsAbsent()
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

private fun pairingWindowFormatsReadOnlyAndroidProfileDiagnostics() {
    val html = PairingWindow.profileDiagnosticsHtml(
        profile = ProfileMetadata(
            profileId = "default_visualizer",
            displayName = "Default Visualizer",
            revision = 7L,
            source = "android",
            rawDebugEnabled = true,
        ),
        packetState = InputStreamLifecycleState.ACTIVE,
        mappedProductStream = true,
        rawDebugEnabled = true,
        lastProfileUpdateElapsedNanos = 1_000_000_000L,
        nowElapsedNanos = 3_500_000_000L,
    )

    expectContains("active profile label", html, "Active Android profile")
    expectContains("active profile value", html, "Default Visualizer | id=default_visualizer | rev=7")
    expectContains("profile source", html, "Profile source")
    expectContains("profile source value", html, "android")
    expectContains("mapped stream label", html, "Mapped stream")
    expectContains("mapped stream value", html, "active | mapped=true | raw_debug=on")
    expectContains("last profile update", html, "Last profile update")
    expectContains("elapsed profile update", html, "2s ago")
}

private fun pairingWindowFreshProfileDiagnosticsAreNeutral() {
    val html = PairingWindow.freshProfileDiagnosticsHtml(nowElapsedNanos = 2_000_000_000L)

    expectContains("fresh active profile", html, "Active Android profile:</b> unknown")
    expectContains("fresh profile source", html, "Profile source:</b> unknown")
    expectContains("fresh mapped stream", html, "stopped | mapped=false | raw_debug=off")
    expectContains("fresh profile update", html, "Last profile update:</b> none")
}

private fun pairingWindowExposesVisualizerReopenActionWithoutLaunchingSwing() {
    var opens = 0
    val button = PairingWindow.createVisualizerOpenButton { opens += 1 }

    expectEquals("visualizer action label", "Open visualizer", PairingWindow.visualizerButtonLabel())
    expectEquals("button text", "Open visualizer", button.text)

    button.doClick()

    expectEquals("opener invoked once", 1, opens)
}

private fun pairingWindowDoesNotRenderVisualizerOnlyPanels() {
    val source = File("src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()
    val visualizerOnlyLabels = listOf(
        "Acceptance checklist",
        "Live gamepad",
        "Latency and packet loss",
        "Recent product events",
        "Run phone haptic test",
    )

    visualizerOnlyLabels.forEach { label ->
        val quotedLabel = Regex(""""${Regex.escape(label)}"""", RegexOption.IGNORE_CASE)
        expectFalse("pairing window excludes visualizer-only label: $label", quotedLabel.containsMatchIn(source))
    }
}

private fun pairingWindowForbiddenDesktopProfileControlsAbsent() {
    val source = File("src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()
    val forbidden = listOf(
        "Edit desktop " + "profile",
        "Desktop profile " + "editor",
        "Request raw " + "stream",
        "Save " + "profile",
        "Duplicate " + "profile",
        "Hold-to-" + "recenter",
    )

    forbidden.forEach { label ->
        expectFalse("forbidden desktop control absent: $label", source.contains(label, ignoreCase = true))
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
