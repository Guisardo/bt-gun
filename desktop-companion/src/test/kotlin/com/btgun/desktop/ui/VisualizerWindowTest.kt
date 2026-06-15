package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.diagnostics.DiagnosticDomain
import com.btgun.desktop.diagnostics.DiagnosticEvent
import com.btgun.desktop.diagnostics.DiagnosticStatus
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.transport.InputReplayRejectReason
import com.btgun.desktop.transport.InputStreamLifecycleState
import java.awt.Dimension
import java.io.File

fun main() {
    visualizerWindowExposesUiSpecCopyWithoutLaunchingSwing()
    visualizerWindowExposesFinalChecklistActionCopy()
    visualizerWindowExposesLifecycleHelpersWithoutOwningTransport()
    visualizerPanelsExposeRequiredGamepadHelpers()
    visualizerCrosshairHelpersClampAndInvertYAxis()
    visualizerStaleOverlayPreservesLastAimCopy()
    visualizerWindowSourceUsesEdtFriendlyRendering()
    visualizerHapticButtonRequiresAuthenticatedSession()
    visualizerHapticCommandUsesSafeShape()
    visualizerHapticStatusCopyMatchesUiSpec()
    visualizerWindowRendersRecenterAndRawDebugStatusLabels()
    visualizerWindowRendersAllDiagnosticBucketRows()
    visualizerDiagnosticLabelsShowReasonAndSanitizedDetail()
    visualizerDiagnosticHapticLimitDoesNotConfirmProof()
    visualizerCoordinatorAppliesVisualizerStatusWithoutClearingState()
    visualizerCoordinatorRefreshesMetricsFromVisualizerStatus()
    visualizerCoordinatorAppliesBackendDiagnostics()
    backendProofLabelsAreSanitized()
    manualProofGuideNamesRowsAndKeepsChecklistPrimary()
    visualizerWindowSourceExcludesForbiddenLabels()
    visualizerFactoryReusesExistingWindow()
    visualizerCoordinatorOpensOnceOnAuthenticatedSession()
    visualizerCoordinatorKeepsHapticAvailableWhenUdpStale()
    visualizerCoordinatorUserActionsSurviveLiveUpdates()
    visualizerCoordinatorAppliesLiveInputProfileMetricsAndRejections()
    visualizerCoordinatorPreservesChecklistAndContextOnDisconnect()
    mainWiresEventHubVisualizerFactoryAndPairingWindow()
    visualizerWindowCapsPackedHeightToScreen()
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

private fun visualizerWindowExposesFinalChecklistActionCopy() {
    expectEquals("top passing summary", "Phase 9 checks passing", VisualizerWindow.topSummaryPassing())
    expectEquals("top failed summary", "Phase 9 checks need attention", VisualizerWindow.topSummaryFailed())
    expectEquals("confirm observed copy", "Confirm observed", VisualizerWindow.confirmObservedLabel())
    expectEquals("confirm limitation copy", "Confirm limitation", VisualizerWindow.confirmLimitationLabel())
    expectEquals("reset copy", "Reset checklist", VisualizerWindow.resetChecklistLabel())
    expectEquals(
        "reset confirmation copy",
        "Reset checklist: Reset Phase 9 checklist progress for this session? Live input and pairing state are unchanged.",
        VisualizerWindow.resetChecklistConfirmationCopy(),
    )
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

private fun visualizerPanelsExposeRequiredGamepadHelpers() {
    expectEquals(
        "button indicator labels",
        listOf("Trigger", "Reload", "X", "Y", "A", "B"),
        VisualizerPanels.buttonIndicatorLabels(),
    )
    expectEquals("stick surface size", 200, VisualizerPanels.stickCrosshairSpec().sizePx)
    expectEquals("aim surface size", 220, VisualizerPanels.aimCrosshairSpec().sizePx)
    expectEquals(
        "active button labels",
        listOf("Trigger", "A"),
        VisualizerPanels.activeButtonLabels(
            com.btgun.desktop.backend.SemanticControllerState(trigger = true, a = true),
        ),
    )
}

private fun visualizerCrosshairHelpersClampAndInvertYAxis() {
    val stick = VisualizerPanels.stickCrosshairSpec()
    val topRight = stick.pointFor(x = 2.0f, y = 2.0f)
    val bottomLeft = stick.pointFor(x = -2.0f, y = -2.0f)
    val center = stick.pointFor(x = 0.0f, y = 0.0f)

    expectEquals("clamp positive x to right edge", stick.maxPlotPx, topRight.x)
    expectEquals("invert positive y to top edge", stick.minPlotPx, topRight.y)
    expectEquals("clamp negative x to left edge", stick.minPlotPx, bottomLeft.x)
    expectEquals("invert negative y to bottom edge", stick.maxPlotPx, bottomLeft.y)
    expectEquals("center x", stick.centerPx, center.x)
    expectEquals("center y", stick.centerPx, center.y)
}

private fun visualizerStaleOverlayPreservesLastAimCopy() {
    expectEquals("stale overlay copy", "stale", VisualizerPanels.staleOverlayText(stale = true, disconnected = false))
    expectEquals(
        "disconnected overlay copy",
        "disconnected",
        VisualizerPanels.staleOverlayText(stale = false, disconnected = true),
    )
    expectEquals("live overlay hidden", null, VisualizerPanels.staleOverlayText(stale = false, disconnected = false))
    expectTrue(
        "stale aim display preserves last accepted aim",
        VisualizerPanels.usesLastAcceptedAimWhenStale(
            currentAimX = 0.0f,
            currentAimY = 0.0f,
            lastAcceptedAimX = 0.35f,
            lastAcceptedAimY = -0.45f,
            stale = true,
        ),
    )
}

private fun visualizerWindowSourceUsesEdtFriendlyRendering() {
    val source = File("src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()
    expectContains("render through helper panel", source, "VisualizerPanels")
    expectContains("EDT repaint flow", source, "SwingUtilities.invokeLater")
    expectFalse("no blocking sleep in visualizer window", source.contains("Thread.sleep("))
    expectFalse("no blocking network io in visualizer window", source.contains("java.net."))
}

private fun visualizerHapticButtonRequiresAuthenticatedSession() {
    expectTrue("authenticated enables haptic", VisualizerWindow.hapticButtonEnabled(ControlServerSessionState.AUTHENTICATED))
    listOf(
        ControlServerSessionState.STARTED,
        ControlServerSessionState.ANDROID_CONNECTED,
        ControlServerSessionState.DEGRADED,
        ControlServerSessionState.DISCONNECTED,
        ControlServerSessionState.STOPPED,
        ControlServerSessionState.RATE_LIMITED,
    ).forEach { state ->
        expectFalse("non-active session disables haptic: $state", VisualizerWindow.hapticButtonEnabled(state))
    }
}

private fun visualizerHapticCommandUsesSafeShape() {
    val command = VisualizerWindow.visualizerHapticCommand(nowElapsedNanos = 123_456_789L)

    expectContains("visualizer-scoped id", command.commandId, "visualizer-haptic-")
    expectEquals("strength", 0.6, command.strength)
    expectEquals("duration", 80L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectEquals("pattern omitted", null, command.pattern)
    listOf("sid", "session", "secret", "hmac", "pairing").forEach { forbidden ->
        expectFalse("command id excludes $forbidden", command.commandId.contains(forbidden, ignoreCase = true))
    }
}

private fun visualizerHapticStatusCopyMatchesUiSpec() {
    expectEquals(
        "queued copy",
        "Phone haptic queued",
        VisualizerWindow.hapticSendStatusText(HapticSendResult.Sent, commandId = "visualizer-haptic-1"),
    )
    expectEquals(
        "no session copy",
        "No active Android session. Pair Android before running haptic proof.",
        VisualizerWindow.hapticSendStatusText(HapticSendResult.NoActiveSession, commandId = null),
    )
    expectEquals(
        "failed send copy",
        "Phone haptic failed. Check Android session and try again.",
        VisualizerWindow.hapticSendStatusText(HapticSendResult.Failed("socket closed"), commandId = "visualizer-haptic-1"),
    )
    expectEquals(
        "ack copy",
        "Phone haptic confirmed",
        VisualizerWindow.hapticResultStatusText(HapticResultStatus.STARTED),
    )
    expectEquals(
        "failed ack copy",
        "Phone haptic failed. Check Android session and try again.",
        VisualizerWindow.hapticResultStatusText(HapticResultStatus.PERMISSION_BLOCKED),
    )
}

private fun visualizerWindowRendersRecenterAndRawDebugStatusLabels() {
    val model = VisualizerModel.initial().withVisualizerStatus(
        status = VisualizerStatus(
            rawDebugEnabled = true,
            aimZeroState = "ready",
            recenterState = "recentered",
            lastRecenterElapsedNanos = 3_000_000_000L,
            androidElapsedNanos = 5_000_000_000L,
            statusSequence = 44L,
            recenterLabel = "recentered",
            aimZeroLabel = "ready",
        ),
        observedElapsedNanos = 6_000_000_000L,
    )

    expectEquals(
        "recenter labels",
        listOf(
            "Aim zero: ready",
            "Recenter: hold reload for 2000ms",
            "Last recenter: 2000 ms ago",
        ),
        VisualizerWindow.recenterStatusLabels(model),
    )
    expectTrue("raw debug on label visible", VisualizerPanels.rawDebugLabels(model.rawDebug).contains("Raw debug on"))
}

private fun visualizerWindowRendersAllDiagnosticBucketRows() {
    val labels = VisualizerWindow.diagnosticStatusLabels(VisualizerModel.initial().diagnosticSummary)
    val joined = labels.joinToString("\n")

    expectEquals("diagnostic row count", 5, labels.size)
    listOf("gun_ble", "sensor_motion", "lan_control_udp", "profile_mapping", "hid_backend_haptics").forEach { id ->
        expectContains("diagnostic row $id", joined, "$id: unknown reason=not_reported")
    }
}

private fun visualizerDiagnosticLabelsShowReasonAndSanitizedDetail() {
    val detail = "Control failed from " + "raw" + " log payload " + "x".repeat(180)
    val model = VisualizerModel.initial().withDiagnosticEvent(
        DiagnosticEvent(
            tsElapsed = 7_000_000L,
            domain = DiagnosticDomain.LAN_CONTROL_UDP,
            status = DiagnosticStatus.BLOCKED,
            reasonCode = "lan_control_udp.auth_failed",
            detail = detail,
        ),
    )
    val labels = VisualizerWindow.diagnosticStatusLabels(model.diagnosticSummary)
    val lan = labels.first { it.startsWith("lan_control_udp:") }

    expectContains("status visible", lan, "blocked")
    expectContains("reason visible", lan, "reason=lan_control_udp.auth_failed")
    expectTrue("label detail capped", lan.length < 180)
    expectFalse("raw detail hidden", lan.contains("raw" + " log", ignoreCase = true))
}

private fun visualizerDiagnosticHapticLimitDoesNotConfirmProof() {
    val model = VisualizerModel.initial().withDiagnosticEvent(
        DiagnosticEvent(
            tsElapsed = 8_000_000L,
            domain = DiagnosticDomain.HID_BACKEND_HAPTICS,
            status = DiagnosticStatus.UNSUPPORTED,
            reasonCode = "hid_backend_haptics.macos_output_deferred",
            detail = "macOS HID haptic unsupported/deferred",
        ),
    )
    val label = VisualizerWindow.diagnosticStatusLabels(model.diagnosticSummary)
        .first { it.startsWith("hid_backend_haptics:") }

    expectContains("unsupported haptic label", label, "unsupported")
    expectEquals("macOS haptic proof not accepted", VisualizerChecklistState.WAITING, model.row(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT).state)
    expectFalse("phone haptic proof not confirmed", model.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).state == VisualizerChecklistState.CONFIRMED)
}

private fun visualizerCoordinatorAppliesVisualizerStatusWithoutClearingState() {
    val existing = VisualizerModel.initial()
        .withHapticResult(
            HapticResult(
                commandId = "visualizer-haptic-1",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 5_000_000_000L,
            ),
        )
        .confirmRow(VisualizerChecklistRowId.LAN_PHONE_HAPTIC)
        .withAcceptedInput(
            acceptedInputForWindow(sequence = 9L, aimX = 0.25f, aimY = -0.5f),
            observedElapsedNanos = 6_000_000_000L,
        )

    val updated = VisualizerWindowCoordinator.modelForVisualizerStatus(
        model = existing,
        status = VisualizerStatus(
            rawDebugEnabled = true,
            aimZeroState = "ready",
            recenterState = "recentered",
            lastRecenterElapsedNanos = 3_000_000_000L,
            androidElapsedNanos = 5_000_000_000L,
            statusSequence = 44L,
            recenterLabel = "recentered",
            aimZeroLabel = "ready",
        ),
        observedElapsedNanos = 6_000_000_000L,
    )

    expectEquals("live trigger preserved", true, updated.liveState.trigger)
    expectEquals("live aim x preserved", 0.25f, updated.liveState.aimX)
    expectEquals("haptic confirmation preserved", VisualizerChecklistState.CONFIRMED, updated.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).state)
    expectEquals("recenter observed", VisualizerChecklistState.OBSERVED, updated.row(VisualizerChecklistRowId.RECENTER_AIM_ZERO).state)
}

private fun visualizerCoordinatorRefreshesMetricsFromVisualizerStatus() {
    val coordinator = VisualizerWindowCoordinator(
        windowFactory = VisualizerWindowFactory {
            object : VisualizerWindowHandle {
                override fun open() = Unit
                override fun applyModel(model: VisualizerModel) = Unit
            }
        },
    )
    coordinator.onUdpInputReceived(
        acceptedInputForWindow(sequence = 1L, aimX = 0.25f, aimY = -0.5f),
        observedElapsedNanos = 10_030_000_000L,
    )
    coordinator.onVisualizerStatusReceived(
        status = VisualizerStatus(
            rawDebugEnabled = false,
            aimZeroState = "ready",
            recenterState = "idle",
            androidElapsedNanos = 1_000_000_000L,
        ),
        observedElapsedNanos = 10_000_000_000L,
    )

    expectEquals("status offset rendered into model", VisualizerClockOffsetQuality.GOOD, coordinator.model.metrics.offsetQuality)
    expectEquals("status metrics preserve packet proof", VisualizerChecklistState.OBSERVED, coordinator.model.row(VisualizerChecklistRowId.PACKET_LOSS).state)
}

private fun visualizerCoordinatorAppliesBackendDiagnostics() {
    val updated = VisualizerWindowCoordinator.modelForWindowsBackendDiagnostics(
        model = VisualizerModel.initial(),
        diagnostics = WindowsBackendRuntimeDiagnostics(
            lifecycleState = BackendLifecycleState.STARTED,
            lastPublishResult = BackendPublishResult.Published,
            lastSourceSequence = 55L,
        ),
        observedElapsedNanos = 7_000_000L,
    )

    expectEquals(
        "Windows input observed from diagnostics",
        VisualizerChecklistState.OBSERVED,
        updated.row(VisualizerChecklistRowId.WINDOWS_VHF_INPUT).state,
    )
    expectEquals(
        "macOS input not inferred from Windows diagnostics",
        VisualizerChecklistState.WAITING,
        updated.row(VisualizerChecklistRowId.MACOS_HID_INPUT).state,
    )
}

private fun backendProofLabelsAreSanitized() {
    val labels = VisualizerWindow.backendProofLabels(
        windowsDiagnostics = WindowsBackendRuntimeDiagnostics(
            lifecycleState = BackendLifecycleState.STARTED,
            lastPublishResult = BackendPublishResult.Published,
            lastSourceSequence = 55L,
            outputHapticCommandsRouted = 1L,
        ),
        macosDiagnostics = MacosBackendRuntimeDiagnostics(),
    )
    val joined = labels.joinToString("\n")

    expectContains("Windows source label", joined, "Phase 6 Windows VHF")
    expectContains("macOS limitation label", joined, "macOS HID haptic unsupported/deferred")
    listOf(
        "raw" + " log",
        "raw" + " screenshot",
        "192.168.",
        "stream" + " secret",
        "HMAC" + " key",
        "private" + " key",
    ).forEach { forbidden ->
        expectFalse("backend proof label excludes $forbidden", joined.contains(forbidden, ignoreCase = true))
    }
}

private fun manualProofGuideNamesRowsAndKeepsChecklistPrimary() {
    val guide = File("../.planning/phases/09-visualizer-acceptance-path/09-MANUAL-PROOF.md")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()

    VisualizerChecklistRowId.entries.forEach { rowId ->
        expectContains("manual guide names ${rowId.wireId}", guide, rowId.wireId)
    }
    expectContains("manual guide names D-01", guide, "D-01")
    expectContains("manual guide names D-16", guide, "D-16")
    expectContains("manual guide makes checklist primary", guide, "guided `BT Gun Visualizer` checklist")
    expectContains("manual guide references Windows approval caveat", guide, "approval-gated Phase 6 checklist")
    listOf(
        "stream" + " secret",
        "HMAC" + " key",
        "private" + " key",
        "raw" + " log",
        "raw" + " screenshot",
        "device" + " serial",
        "generated evidence bundle " + "primary",
    ).forEach { forbidden ->
        expectFalse("manual guide excludes $forbidden", guide.contains(forbidden, ignoreCase = true))
    }
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
        "Generated evidence " + "bundle",
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

private fun visualizerCoordinatorKeepsHapticAvailableWhenUdpStale() {
    val coordinator = VisualizerWindowCoordinator(
        windowFactory = VisualizerWindowFactory {
            object : VisualizerWindowHandle {
                override fun open() = Unit
                override fun applyModel(model: VisualizerModel) = Unit
            }
        },
    )

    coordinator.onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
    coordinator.onUdpInputStateChanged(InputStreamLifecycleState.STALE)

    expectEquals("control session remains authenticated", ControlServerSessionState.AUTHENTICATED, coordinator.model.controlSessionState)
    expectEquals("udp lifecycle stale", InputStreamLifecycleState.STALE, coordinator.model.packetLifecycle)
    expectTrue("authenticated control enables haptic despite stale udp", VisualizerWindow.hapticButtonEnabled(coordinator.model.controlSessionState))
}

private fun visualizerCoordinatorUserActionsSurviveLiveUpdates() {
    val coordinator = VisualizerWindowCoordinator(
        windowFactory = VisualizerWindowFactory {
            object : VisualizerWindowHandle {
                override fun open() = Unit
                override fun applyModel(model: VisualizerModel) = Unit
            }
        },
    )

    coordinator.confirmNextObservedRow()
    coordinator.onUdpInputReceived(
        acceptedInputForWindow(sequence = 1L, aimX = 0.33f, aimY = -0.44f),
        observedElapsedNanos = 10_000_000L,
    )
    coordinator.recordHapticSendResult(
        result = HapticSendResult.Sent,
        commandId = "visualizer-haptic-1",
        observedElapsedNanos = 11_000_000L,
    )
    coordinator.onMacosBackendDiagnosticsChanged(
        diagnostics = MacosBackendRuntimeDiagnostics(),
        observedElapsedNanos = 11_500_000L,
    )
    coordinator.confirmMacosHapticLimitation()
    coordinator.onUdpInputReceived(
        acceptedInputForWindow(sequence = 2L, aimX = 0.34f, aimY = -0.45f),
        observedElapsedNanos = 12_000_000L,
    )

    expectEquals("macOS user confirmation survives live input", VisualizerChecklistState.CONFIRMED, coordinator.model.row(VisualizerChecklistRowId.MACOS_HID_INPUT).state)
    expectEquals("limitation confirmation survives live input", VisualizerChecklistState.UNSUPPORTED_DEFERRED, coordinator.model.row(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT).state)
    expectEquals("haptic send state stays on coordinator model", "queued", coordinator.model.hapticStatus.status)
}

private fun visualizerCoordinatorAppliesLiveInputProfileMetricsAndRejections() {
    val applied = mutableListOf<VisualizerModel>()
    val coordinator = VisualizerWindowCoordinator(
        windowFactory = VisualizerWindowFactory {
            object : VisualizerWindowHandle {
                override fun open() = Unit

                override fun applyModel(model: VisualizerModel) {
                    applied.add(model)
                }
            }
        },
    )
    coordinator.openVisualizer()

    coordinator.onProfileMetadataReceived(
        ProfileMetadata(
            profileId = "default_visualizer",
            displayName = "Default Visualizer",
            revision = 2L,
            source = "android",
            rawDebugEnabled = true,
        ),
    )
    coordinator.onUdpInputReceived(
        acceptedInputForWindow(sequence = 7L, aimX = 0.33f, aimY = -0.44f),
        observedElapsedNanos = 10_000_000L,
    )
    coordinator.onUdpInputRejected(InputReplayRejectReason.OLD_SEQUENCE)
    coordinator.onUdpInputStateChanged(InputStreamLifecycleState.STALE)

    val model = coordinator.model
    expectEquals("profile metadata applied", "Default Visualizer", model.profileSummary.displayName)
    expectEquals("live trigger applied", true, model.liveState.trigger)
    expectEquals("live aim x applied", 0.33f, model.liveState.aimX)
    expectEquals("latency target observed from metrics", VisualizerChecklistState.OBSERVED, model.row(VisualizerChecklistRowId.LATENCY_TARGET).state)
    expectEquals("packet lifecycle applied", InputStreamLifecycleState.STALE, model.packetLifecycle)
    expectEquals("rejection label applied", "old_sequence", model.rawDebug.lastRejection)
    expectTrue("window received model updates", applied.size >= 4)
}

private fun visualizerCoordinatorPreservesChecklistAndContextOnDisconnect() {
    val existing = VisualizerModel.initial()
        .withVisualizerStatus(
            status = VisualizerStatus(
                rawDebugEnabled = false,
                aimZeroState = "ready",
                recenterState = "recentered",
                lastRecenterElapsedNanos = 3_000_000_000L,
                androidElapsedNanos = 5_000_000_000L,
            ),
            observedElapsedNanos = 6_000_000_000L,
        )
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
        "onConfirmObserved = coordinator::confirmNextObservedRow",
        "onConfirmLimitation = coordinator::confirmMacosHapticLimitation",
        "onResetChecklist = coordinator::resetChecklist",
        "onHapticSendResult = coordinator::recordHapticSendResult",
        "onVisualizerStatusReceived = coordinator::onVisualizerStatusReceived",
        "onProfileMetadataReceived = coordinator::onProfileMetadataReceived",
        "onUdpInputReceived = coordinator::onUdpInputReceived",
        "onUdpInputRejected = coordinator::onUdpInputRejected",
        "onUdpInputStateChanged = coordinator::onUdpInputStateChanged",
        "onWindowsBackendDiagnosticsChanged = coordinator::onWindowsBackendDiagnosticsChanged",
        "onMacosBackendDiagnosticsChanged = coordinator::onMacosBackendDiagnosticsChanged",
    ).forEach { expected ->
        expectContains("main wiring contains $expected", source, expected)
    }
}

private fun visualizerWindowCapsPackedHeightToScreen() {
    val capped = DesktopWindowFit.constrainedFrameSize(
        packedSize = Dimension(1180, 1100),
        usableScreenSize = Dimension(1366, 768),
    )
    val source = File("src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()

    expectEquals("caps visualizer height below 768p screen", 720, capped.height)
    expectContains("visualizer content scrolls", source, "DesktopWindowFit.scrollableContent(content())")
    expectContains("visualizer frame fits screen", source, "DesktopWindowFit.fitToScreen(frame)")
}

private fun acceptedInputForWindow(
    sequence: Long,
    aimX: Float,
    aimY: Float,
): com.btgun.desktop.transport.UdpReceivedInput =
    com.btgun.desktop.transport.UdpReceivedInput(
        controlSessionId = "control-sid-1",
        streamSessionIdHex = "00112233445566778899aabbccddeeff",
        frameType = com.btgun.desktop.transport.UdpInputFrameType.SNAPSHOT,
        buttons = 0x01,
        pressedControls = setOf("trigger"),
        stickX = 12,
        stickY = -12,
        motion = com.btgun.desktop.transport.UdpReceivedMotion(
            provider = 2,
            capabilityFlags = 3,
            yaw = 1.0f,
            pitch = 2.0f,
            roll = 3.0f,
            rawAimX = aimX,
            rawAimY = aimY,
            sourceSensorElapsedNanos = 1_000_000L,
        ),
        mappedAim = com.btgun.desktop.transport.UdpReceivedMappedAim(aimX = aimX, aimY = aimY),
        mappedProductStream = true,
        rawDebugEnabled = false,
        captureElapsedNanos = 2_000_000L,
        sendElapsedNanos = 3_000_000L,
        receivedElapsedNanos = 4_000_000L,
        stale = false,
        lastAcceptedSequence = sequence,
    )

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
