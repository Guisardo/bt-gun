package com.btgun.desktop.ui

import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpReceivedMappedAim
import com.btgun.desktop.transport.UdpReceivedMotion
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus

fun main() {
    finalChecklistCannotPassUntilRequiredRowsReachAcceptedStates()
    macosAndWindowsInputRowsAreIndependentAndBothRequired()
    resetChecklistClearsProofOnlyAndPreservesLiveSessionState()
    windowsBackendDiagnosticsObserveInputButStillRequireConfirmation()
    windowsHapticDiagnosticsRequireRoutedOutputAndUserConfirmation()
    macosHidHapticLimitationRequiresLimitationConfirmation()
    eventStripKeepsExactlyTenNewestProductEvents()
    eventStripLabelsIncludeSequenceAndAge()
    rawDebugDrawerStartsCollapsedAndShowsWhitelistedFieldsOnlyWhenEnabled()
    visualizerStatusUpdatesRecenterAimZeroAndRawDebugWithoutConfirming()
    hapticAckObservesLanRowButDoesNotConfirmPhoneVibration()
    observedLanStreamDoesNotConfirmManualProofRows()
    modelLabelsExcludeDesktopProfileControlsAndSecretFields()
    staleInputPreservesLastAcceptedAimContext()
}

private fun finalChecklistCannotPassUntilRequiredRowsReachAcceptedStates() {
    val observed = VisualizerModel.initial()
        .withAcceptedInput(acceptedInput(sequence = 1L), observedElapsedNanos = 2_000_000L)
        .withVisualizerStatus(
            status = VisualizerStatus(
                rawDebugEnabled = false,
                aimZeroState = "ready",
                recenterState = "recentered",
                lastRecenterElapsedNanos = 1_000_000L,
                androidElapsedNanos = 2_000_000L,
                statusSequence = 2L,
                recenterLabel = "recentered",
                aimZeroLabel = "ready",
            ),
            observedElapsedNanos = 3_000_000L,
        )
        .withMetrics(
            VisualizerMetricSnapshot.empty().copy(
                targetStatus = "pass",
            ),
        )

    expectEquals("observed still pending", VisualizerChecklistSummary.PENDING, observed.checklistSummary())

    val almost = observed
        .confirmRow(VisualizerChecklistRowId.RECENTER_AIM_ZERO)
        .confirmRow(VisualizerChecklistRowId.MACOS_HID_INPUT)
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_INPUT)
        .confirmRow(VisualizerChecklistRowId.LAN_PHONE_HAPTIC)
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC)

    expectEquals("macOS limitation still needed", VisualizerChecklistSummary.PENDING, almost.checklistSummary())

    val passing = almost.confirmLimitation(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT)
    expectEquals("all required proof accepted", VisualizerChecklistSummary.PASSING, passing.checklistSummary())
    expectEquals("passing label", VisualizerWindow.topSummaryPassing(), passing.topSummaryLabel())
}

private fun macosAndWindowsInputRowsAreIndependentAndBothRequired() {
    val onlyMacos = VisualizerModel.initial()
        .confirmRow(VisualizerChecklistRowId.MACOS_HID_INPUT)
    expectEquals("macOS row confirmed", VisualizerChecklistState.CONFIRMED, onlyMacos.row(VisualizerChecklistRowId.MACOS_HID_INPUT).state)
    expectEquals("Windows row still waiting", VisualizerChecklistState.WAITING, onlyMacos.row(VisualizerChecklistRowId.WINDOWS_VHF_INPUT).state)
    expectEquals("one OS path insufficient", VisualizerChecklistSummary.PENDING, onlyMacos.checklistSummary())

    val onlyWindows = VisualizerModel.initial()
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_INPUT)
    expectEquals("Windows row confirmed", VisualizerChecklistState.CONFIRMED, onlyWindows.row(VisualizerChecklistRowId.WINDOWS_VHF_INPUT).state)
    expectEquals("macOS row still waiting", VisualizerChecklistState.WAITING, onlyWindows.row(VisualizerChecklistRowId.MACOS_HID_INPUT).state)
    expectEquals("other OS path insufficient", VisualizerChecklistSummary.PENDING, onlyWindows.checklistSummary())
}

private fun resetChecklistClearsProofOnlyAndPreservesLiveSessionState() {
    val live = VisualizerModel.initial()
        .withPacketLifecycle(com.btgun.desktop.transport.InputStreamLifecycleState.ACTIVE)
        .withAcceptedInput(acceptedInput(sequence = 7L, aimX = 0.5f, aimY = -0.25f), observedElapsedNanos = 8_000_000L)
        .confirmRow(VisualizerChecklistRowId.MACOS_HID_INPUT)
        .confirmLimitation(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT)

    val reset = live.resetChecklist()

    expectEquals("live lifecycle preserved", live.packetLifecycle, reset.packetLifecycle)
    expectEquals("live trigger preserved", live.liveState.trigger, reset.liveState.trigger)
    expectEquals("live aim preserved", live.liveState.aimX, reset.liveState.aimX)
    expectEquals("events preserved", live.productEvents, reset.productEvents)
    VisualizerChecklistRowId.entries.forEach { rowId ->
        expectEquals("row reset ${rowId.wireId}", VisualizerChecklistState.WAITING, reset.row(rowId).state)
        expectEquals("row observed source cleared ${rowId.wireId}", null, reset.row(rowId).observedSource)
    }
}

private fun windowsBackendDiagnosticsObserveInputButStillRequireConfirmation() {
    val model = VisualizerModel.initial().withWindowsBackendDiagnostics(
        diagnostics = WindowsBackendRuntimeDiagnostics(
            lifecycleState = BackendLifecycleState.STARTED,
            lastPublishResult = BackendPublishResult.Published,
            stale = false,
            lastSourceSequence = 42L,
        ),
        observedElapsedNanos = 9_000_000L,
    )

    val row = model.row(VisualizerChecklistRowId.WINDOWS_VHF_INPUT)
    expectEquals("Windows row observed", VisualizerChecklistState.OBSERVED, row.state)
    expectEquals("Windows source label", "Phase 6 Windows VHF backend published seq=42", row.observedSource)
    expectTrue("Windows input still needs user confirmation", row.requiresUserConfirmation)
    expectEquals("Windows observed is not enough", VisualizerChecklistSummary.PENDING, model.checklistSummary())
}

private fun windowsHapticDiagnosticsRequireRoutedOutputAndUserConfirmation() {
    val observed = VisualizerModel.initial().withWindowsBackendDiagnostics(
        diagnostics = WindowsBackendRuntimeDiagnostics(
            lifecycleState = BackendLifecycleState.STARTED,
            lastPublishResult = BackendPublishResult.Published,
            lastHapticSendResult = HapticSendResult.Sent,
            outputHapticCommandsRouted = 1L,
        ),
        observedElapsedNanos = 10_000_000L,
    )

    expectEquals(
        "Windows haptic observed",
        VisualizerChecklistState.OBSERVED,
        observed.row(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC).state,
    )
    expectTrue("Windows haptic still requires phone vibration confirmation", observed.row(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC).requiresUserConfirmation)

    val confirmed = observed.confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC)
    expectEquals(
        "Windows haptic confirmed",
        VisualizerChecklistState.CONFIRMED,
        confirmed.row(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC).state,
    )
}

private fun macosHidHapticLimitationRequiresLimitationConfirmation() {
    val evidence = VisualizerModel.initial().withMacosBackendDiagnostics(
        diagnostics = MacosBackendRuntimeDiagnostics(),
        observedElapsedNanos = 11_000_000L,
    )

    expectEquals(
        "macOS haptic row waits for limitation confirmation",
        VisualizerChecklistState.OBSERVED,
        evidence.row(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT).state,
    )
    expectContains(
        "macOS haptic source shows deferred evidence",
        evidence.row(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT).observedSource.orEmpty(),
        "unsupported/deferred",
    )

    val accepted = evidence.confirmLimitation(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT)
    expectEquals(
        "macOS haptic accepted only as limitation",
        VisualizerChecklistState.UNSUPPORTED_DEFERRED,
        accepted.row(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT).state,
    )
    expectFalse(
        "macOS HID input not inferred from virtual helper diagnostics",
        accepted.row(VisualizerChecklistRowId.MACOS_HID_INPUT).state == VisualizerChecklistState.CONFIRMED,
    )
}

private fun eventStripKeepsExactlyTenNewestProductEvents() {
    val model = (1L..12L).fold(VisualizerModel.initial()) { current, sequence ->
        current.withProductEvent(
            VisualizerProductEvent(
                type = "input",
                sequence = sequence,
                ageSourceElapsedNanos = 1_000_000L * sequence,
            ),
        )
    }

    expectEquals("event strip size", 10, model.productEvents.size)
    expectEquals("newest first sequence", 12L, model.productEvents.first().sequence)
    expectEquals("oldest retained sequence", 3L, model.productEvents.last().sequence)
    expectEquals("event type retained", "input", model.productEvents.first().type)
    expectEquals("age source retained", 12_000_000L, model.productEvents.first().ageSourceElapsedNanos)
}

private fun eventStripLabelsIncludeSequenceAndAge() {
    val model = (1L..12L).fold(VisualizerModel.initial()) { current, sequence ->
        current.withProductEvent(
            VisualizerProductEvent(
                type = "input",
                sequence = sequence,
                ageSourceElapsedNanos = sequence * 1_000_000L,
            ),
        )
    }

    val labels = VisualizerPanels.eventStripLabels(
        events = model.productEvents,
        nowElapsedNanos = 15_000_000L,
    )

    expectEquals("event slot count", 10, labels.size)
    expectEquals("newest event label", "input seq=12 age=3 ms", labels.first())
    expectEquals("oldest retained event label", "input seq=3 age=12 ms", labels.last())
}

private fun rawDebugDrawerStartsCollapsedAndShowsWhitelistedFieldsOnlyWhenEnabled() {
    val off = VisualizerPanels.rawDebugLabels(VisualizerRawDebugState(enabled = false))
    expectEquals("raw drawer off", listOf("Raw debug off"), off)

    val on = VisualizerPanels.rawDebugLabels(
        VisualizerRawDebugState(
            enabled = true,
            collapsed = false,
            provider = 2,
            yaw = 1.25f,
            pitch = -2.5f,
            roll = 3.75f,
            rawAimX = 0.4f,
            rawAimY = -0.6f,
            lastRejection = "old_sequence",
        ),
    )
    expectEquals(
        "raw drawer on labels",
        listOf(
            "Raw debug on",
            "Provider: 2",
            "Yaw: 1.25 | Pitch: -2.50 | Roll: 3.75",
            "Raw aim: x=0.40 y=-0.60",
            "Last rejection: old_sequence",
        ),
        on,
    )
    listOf("secret", "pairing", "hmac", "private key", "device id", "stream key").forEach { forbidden ->
        expectFalse("raw drawer excludes $forbidden", on.joinToString("\n").contains(forbidden, ignoreCase = true))
    }
}

private fun visualizerStatusUpdatesRecenterAimZeroAndRawDebugWithoutConfirming() {
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

    expectEquals("aim zero label", "Aim zero: ready", model.recenter.aimZeroLabel)
    expectEquals("recenter instruction", "Recenter: hold reload for 2000ms", model.recenter.recenterInstruction)
    expectEquals("last recenter label", "Last recenter: 2000 ms ago", model.recenter.lastRecenterLabel)
    expectEquals("raw debug enabled", true, model.rawDebug.enabled)
    expectEquals("raw debug expanded by enabled status", false, model.rawDebug.collapsed)
    expectEquals(
        "recenter row observed",
        VisualizerChecklistState.OBSERVED,
        model.row(VisualizerChecklistRowId.RECENTER_AIM_ZERO).state,
    )
    expectTrue("recenter still needs user confirmation", model.row(VisualizerChecklistRowId.RECENTER_AIM_ZERO).requiresUserConfirmation)
    expectFalse("recenter not auto-confirmed", model.row(VisualizerChecklistRowId.RECENTER_AIM_ZERO).state == VisualizerChecklistState.CONFIRMED)
    expectEquals("status event type", "recenter_recentered", model.productEvents.first().type)
    expectEquals("status event sequence", 44L, model.productEvents.first().sequence)
}

private fun hapticAckObservesLanRowButDoesNotConfirmPhoneVibration() {
    val model = VisualizerModel.initial().withHapticResult(
        HapticResult(
            commandId = "visualizer-haptic-123",
            status = HapticResultStatus.STARTED,
            detail = "phone pulse started",
            observedElapsedNanos = 10_000_000L,
        ),
    )

    expectEquals("haptic status copy", "Phone haptic confirmed", model.hapticStatus.detail)
    expectEquals(
        "lan haptic observed",
        VisualizerChecklistState.OBSERVED,
        model.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).state,
    )
    expectTrue("lan haptic still needs user confirmation", model.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).requiresUserConfirmation)
}

private fun observedLanStreamDoesNotConfirmManualProofRows() {
    val model = VisualizerModel.initial()
        .withAcceptedInput(acceptedInput(sequence = 1L), observedElapsedNanos = 2_000_000L)

    expectEquals(
        "lan stream observed",
        VisualizerChecklistState.OBSERVED,
        model.row(VisualizerChecklistRowId.LAN_VISUALIZER_STREAM).state,
    )
    listOf(
        VisualizerChecklistRowId.RECENTER_AIM_ZERO,
        VisualizerChecklistRowId.MACOS_HID_INPUT,
        VisualizerChecklistRowId.WINDOWS_VHF_INPUT,
        VisualizerChecklistRowId.LAN_PHONE_HAPTIC,
        VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC,
        VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT,
    ).forEach { rowId ->
        expectTrue("manual row requires confirmation: ${rowId.wireId}", model.row(rowId).requiresUserConfirmation)
        expectFalse("manual row not auto-confirmed: ${rowId.wireId}", model.row(rowId).state == VisualizerChecklistState.CONFIRMED)
    }
}

private fun modelLabelsExcludeDesktopProfileControlsAndSecretFields() {
    val labels = VisualizerModel.defaultChecklistRows().joinToString(separator = "\n") { row ->
        "${row.id.wireId} ${row.label}"
    }
    val requiredIds = setOf(
        "lan_visualizer_stream",
        "live_controls",
        "recenter_aim_zero",
        "macos_hid_input",
        "windows_vhf_input",
        "lan_phone_haptic",
        "windows_vhf_haptic",
        "macos_hid_haptic_limit",
        "latency_target",
        "packet_loss",
    )

    expectEquals("required ids", requiredIds, VisualizerChecklistRowId.entries.map { it.wireId }.toSet())
    listOf("desktop profile", "profile editor", "save profile", "hmac", "secret", "private key", "pairing material")
        .forEach { forbidden ->
            expectFalse("forbidden label absent: $forbidden", labels.contains(forbidden, ignoreCase = true))
        }
}

private fun staleInputPreservesLastAcceptedAimContext() {
    val live = VisualizerModel.initial()
        .withAcceptedInput(
            acceptedInput(sequence = 1L, aimX = 0.5f, aimY = -0.25f, stale = false),
            observedElapsedNanos = 2_000_000L,
        )
    val stale = live.withAcceptedInput(
        acceptedInput(sequence = 2L, aimX = 0.5f, aimY = -0.25f, stale = true),
        observedElapsedNanos = 3_000_000L,
    )

    expectEquals("stale display", true, stale.liveState.stale)
    expectEquals("buttons cleared by stale frame", false, stale.liveState.trigger)
    expectEquals("last aim x preserved", 0.5f, stale.liveState.aimX)
    expectEquals("last aim y preserved", -0.25f, stale.liveState.aimY)
    expectEquals("last accepted aim x context", 0.5f, stale.lastAcceptedAimX)
    expectEquals("last accepted aim y context", -0.25f, stale.lastAcceptedAimY)
}

private fun acceptedInput(
    sequence: Long,
    aimX: Float = 0.0f,
    aimY: Float = 0.0f,
    stale: Boolean = false,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = "control-sid-1",
        streamSessionIdHex = "00112233445566778899aabbccddeeff",
        frameType = UdpInputFrameType.SNAPSHOT,
        buttons = if (stale) 0 else 0x01,
        pressedControls = if (stale) emptySet() else setOf("trigger"),
        stickX = if (stale) 0 else 12,
        stickY = if (stale) 0 else -12,
        motion = UdpReceivedMotion(
            provider = 2,
            capabilityFlags = 3,
            yaw = 1.0f,
            pitch = 2.0f,
            roll = 3.0f,
            rawAimX = aimX,
            rawAimY = aimY,
            sourceSensorElapsedNanos = 1_000_000L,
        ),
        mappedAim = UdpReceivedMappedAim(aimX = aimX, aimY = aimY),
        mappedProductStream = true,
        rawDebugEnabled = false,
        captureElapsedNanos = 2_000_000L,
        sendElapsedNanos = 3_000_000L,
        receivedElapsedNanos = 4_000_000L,
        stale = stale,
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
