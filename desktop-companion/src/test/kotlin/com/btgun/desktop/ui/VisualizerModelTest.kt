package com.btgun.desktop.ui

import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.diagnostics.DiagnosticDomain
import com.btgun.desktop.diagnostics.DiagnosticEvent
import com.btgun.desktop.diagnostics.DiagnosticSessionRefs
import com.btgun.desktop.diagnostics.DiagnosticStatus
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpReceivedMappedAim
import com.btgun.desktop.transport.UdpReceivedMotion
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus

fun main() {
    diagnosticSummaryStartsWithFiveUnknownBuckets()
    diagnosticEventUpdatesOnlyMatchingBucket()
    diagnosticAttentionDoesNotConfirmProofRows()
    diagnosticDetailIsCappedAndRedacted()
    finalChecklistCannotPassUntilRequiredRowsReachAcceptedStates()
    macosAndWindowsInputRowsAreIndependentAndBothRequired()
    manualConfirmationRequiresObservedPrerequisitesExceptMacosInput()
    resetChecklistClearsProofOnlyAndPreservesLiveSessionState()
    windowsBackendDiagnosticsObserveInputButStillRequireConfirmation()
    windowsHapticDiagnosticsRequireRoutedOutputAndUserConfirmation()
    macosHidHapticLimitationRequiresLimitationConfirmation()
    eventStripKeepsExactlyTenNewestProductEvents()
    eventStripLabelsIncludeSequenceAndAge()
    rawDebugDrawerStartsCollapsedAndShowsWhitelistedFieldsOnlyWhenEnabled()
    visualizerStatusUpdatesRecenterAimZeroAndRawDebugWithoutConfirming()
    idleVisualizerStatusDoesNotObserveRecenterProof()
    hapticAckObservesLanRowButDoesNotConfirmPhoneVibration()
    hapticRetryRecoversFailedLanPhoneHapticRow()
    observedLanStreamDoesNotConfirmManualProofRows()
    modelLabelsExcludeDesktopProfileControlsAndSecretFields()
    staleInputPreservesLastAcceptedAimContext()
}

private fun diagnosticSummaryStartsWithFiveUnknownBuckets() {
    val summary = VisualizerModel.initial().diagnosticSummary
    val expectedIds = listOf(
        "gun_ble",
        "sensor_motion",
        "lan_control_udp",
        "profile_mapping",
        "hid_backend_haptics",
    )

    expectEquals("diagnostic ids", expectedIds, summary.buckets.map { it.id })
    summary.buckets.forEach { bucket ->
        expectEquals("initial status ${bucket.id}", "unknown", bucket.status)
        expectEquals("initial reason ${bucket.id}", "not_reported", bucket.reasonCode)
        expectEquals("initial detail ${bucket.id}", "No diagnostic event reported", bucket.detail)
        expectFalse("unknown not attention ${bucket.id}", bucket.attention)
    }
}

private fun diagnosticEventUpdatesOnlyMatchingBucket() {
    val event = DiagnosticEvent(
        tsElapsed = 1_000_000L,
        domain = DiagnosticDomain.SENSOR_MOTION,
        status = DiagnosticStatus.DEGRADED,
        reasonCode = "sensor_motion.provider_fallback",
        detail = "gyro unavailable; gravity tilt active",
        sessionRefs = DiagnosticSessionRefs(streamSessionRef = "00112233445566778899aabbccddeeff"),
        context = mapOf("provider" to "gravity"),
    )
    val model = VisualizerModel.initial().withDiagnosticEvent(event)

    expectEquals("updated status", "degraded", model.diagnosticSummary.bucket("sensor_motion").status)
    expectEquals("updated reason", "sensor_motion.provider_fallback", model.diagnosticSummary.bucket("sensor_motion").reasonCode)
    expectContains("updated detail", model.diagnosticSummary.bucket("sensor_motion").detail, "gravity tilt")
    expectTrue("degraded needs attention", model.diagnosticSummary.bucket("sensor_motion").attention)

    listOf("gun_ble", "lan_control_udp", "profile_mapping", "hid_backend_haptics").forEach { id ->
        expectEquals("other bucket unchanged $id", "unknown", model.diagnosticSummary.bucket(id).status)
    }
}

private fun diagnosticAttentionDoesNotConfirmProofRows() {
    val blocked = DiagnosticEvent(
        tsElapsed = 2_000_000L,
        domain = DiagnosticDomain.GUN_BLE,
        status = DiagnosticStatus.BLOCKED,
        reasonCode = "gun_ble.permission_blocked",
        detail = "nearby devices permission blocked",
    )
    val unsupported = DiagnosticEvent(
        tsElapsed = 3_000_000L,
        domain = DiagnosticDomain.HID_BACKEND_HAPTICS,
        status = DiagnosticStatus.UNSUPPORTED,
        reasonCode = "hid_backend_haptics.macos_output_deferred",
        detail = "macOS HID haptic unsupported/deferred; LAN haptic remains available",
    )
    val model = VisualizerModel.initial()
        .withDiagnosticEvent(blocked)
        .withDiagnosticEvent(unsupported)

    expectTrue("blocked bucket attention", model.diagnosticSummary.bucket("gun_ble").attention)
    expectTrue("unsupported bucket attention", model.diagnosticSummary.bucket("hid_backend_haptics").attention)
    listOf(
        VisualizerChecklistRowId.RECENTER_AIM_ZERO,
        VisualizerChecklistRowId.MACOS_HID_INPUT,
        VisualizerChecklistRowId.WINDOWS_VHF_INPUT,
        VisualizerChecklistRowId.LAN_PHONE_HAPTIC,
        VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC,
        VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT,
    ).forEach { rowId ->
        expectFalse("diagnostic does not confirm ${rowId.wireId}", model.row(rowId).state == VisualizerChecklistState.CONFIRMED)
    }
}

private fun diagnosticDetailIsCappedAndRedacted() {
    val noisyDetail = "Failure used " + "stream" + " key=abcdef and " + "private" + " key marker " + "x".repeat(260)
    val model = VisualizerModel.initial().withDiagnosticEvent(
        DiagnosticEvent(
            tsElapsed = 4_000_000L,
            domain = DiagnosticDomain.LAN_CONTROL_UDP,
            status = DiagnosticStatus.BLOCKED,
            reasonCode = "lan_control_udp.auth_failed",
            detail = noisyDetail,
        ),
    )
    val detail = model.diagnosticSummary.bucket("lan_control_udp").detail

    expectTrue("detail capped", detail.length <= VisualizerDiagnosticBucket.MAX_DETAIL_CHARS)
    listOf("stream" + " key", "private" + " key", "abcdef").forEach { forbidden ->
        expectFalse("detail redacts $forbidden", detail.contains(forbidden, ignoreCase = true))
    }
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
                packetExpected = 1L,
            ),
        )
        .withWindowsBackendDiagnostics(
            diagnostics = WindowsBackendRuntimeDiagnostics(
                lifecycleState = BackendLifecycleState.STARTED,
                lastPublishResult = BackendPublishResult.Published,
                lastSourceSequence = 1L,
                lastHapticSendResult = HapticSendResult.Sent,
                outputHapticCommandsRouted = 1L,
            ),
            observedElapsedNanos = 4_000_000L,
        )
        .withHapticResult(
            HapticResult(
                commandId = "visualizer-haptic-1",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 5_000_000L,
            ),
        )
        .withMacosBackendDiagnostics(
            diagnostics = MacosBackendRuntimeDiagnostics(),
            observedElapsedNanos = 6_000_000L,
        )

    expectEquals("observed still pending", VisualizerChecklistSummary.PENDING, observed.checklistSummary())

    val almost = observed
        .confirmRow(VisualizerChecklistRowId.RECENTER_AIM_ZERO)
        .confirmRow(VisualizerChecklistRowId.MACOS_HID_INPUT)
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_INPUT)
        .confirmRow(VisualizerChecklistRowId.LAN_PHONE_HAPTIC)
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC)

    expectEquals("macOS limitation still needed", VisualizerChecklistSummary.PENDING, almost.checklistSummary())
    val unobservedLimit = almost.copy(
        checklistRows = almost.checklistRows.updateForTest(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT) { row ->
            row.copy(state = VisualizerChecklistState.WAITING, observedSource = null)
        },
    )
    expectEquals(
        "unobserved limitation cannot be confirmed",
        VisualizerChecklistState.WAITING,
        unobservedLimit.confirmLimitation(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT)
            .row(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT).state,
    )

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
    expectEquals("Windows row still needs observation", VisualizerChecklistState.WAITING, onlyWindows.row(VisualizerChecklistRowId.WINDOWS_VHF_INPUT).state)
    expectEquals("macOS row still waiting", VisualizerChecklistState.WAITING, onlyWindows.row(VisualizerChecklistRowId.MACOS_HID_INPUT).state)
    expectEquals("other OS path insufficient", VisualizerChecklistSummary.PENDING, onlyWindows.checklistSummary())

    val confirmedWindows = VisualizerModel.initial()
        .withWindowsBackendDiagnostics(
            diagnostics = WindowsBackendRuntimeDiagnostics(
                lifecycleState = BackendLifecycleState.STARTED,
                lastPublishResult = BackendPublishResult.Published,
                lastSourceSequence = 22L,
            ),
            observedElapsedNanos = 3_000_000L,
        )
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_INPUT)
    expectEquals("Windows row confirms after observation", VisualizerChecklistState.CONFIRMED, confirmedWindows.row(VisualizerChecklistRowId.WINDOWS_VHF_INPUT).state)
}

private fun manualConfirmationRequiresObservedPrerequisitesExceptMacosInput() {
    val unobserved = VisualizerModel.initial()
        .confirmRow(VisualizerChecklistRowId.RECENTER_AIM_ZERO)
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_INPUT)
        .confirmRow(VisualizerChecklistRowId.LAN_PHONE_HAPTIC)
        .confirmRow(VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC)

    listOf(
        VisualizerChecklistRowId.RECENTER_AIM_ZERO,
        VisualizerChecklistRowId.WINDOWS_VHF_INPUT,
        VisualizerChecklistRowId.LAN_PHONE_HAPTIC,
        VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC,
    ).forEach { rowId ->
        expectEquals("unobserved row remains waiting: ${rowId.wireId}", VisualizerChecklistState.WAITING, unobserved.row(rowId).state)
    }
    expectEquals(
        "guided confirm can accept external macOS proof",
        VisualizerChecklistState.CONFIRMED,
        VisualizerModel.initial().confirmNextObservedRow().row(VisualizerChecklistRowId.MACOS_HID_INPUT).state,
    )
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
    listOf("secret", "pairing", "hmac", "private" + " key", "device id", "stream" + " key").forEach { forbidden ->
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

private fun idleVisualizerStatusDoesNotObserveRecenterProof() {
    val model = VisualizerModel.initial().withVisualizerStatus(
        status = VisualizerStatus(
            rawDebugEnabled = false,
            aimZeroState = "ready",
            recenterState = "idle",
            lastRecenterElapsedNanos = null,
            androidElapsedNanos = 5_000_000_000L,
            statusSequence = 45L,
            recenterLabel = "idle",
            aimZeroLabel = "ready",
        ),
        observedElapsedNanos = 6_000_000_000L,
    )

    expectEquals("idle status updates aim zero label", "Aim zero: ready", model.recenter.aimZeroLabel)
    expectEquals("idle status does not observe recenter row", VisualizerChecklistState.WAITING, model.row(VisualizerChecklistRowId.RECENTER_AIM_ZERO).state)
    expectEquals("idle status event recorded", "recenter_idle", model.productEvents.first().type)
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

private fun hapticRetryRecoversFailedLanPhoneHapticRow() {
    val failed = VisualizerModel.initial().withHapticResult(
        HapticResult(
            commandId = "visualizer-haptic-123",
            status = HapticResultStatus.PERMISSION_BLOCKED,
            detail = "permission blocked",
            observedElapsedNanos = 10_000_000L,
        ),
    )
    val recovered = failed.withHapticResult(
        HapticResult(
            commandId = "visualizer-haptic-124",
            status = HapticResultStatus.STARTED,
            detail = "phone pulse started",
            observedElapsedNanos = 11_000_000L,
        ),
    )
    val confirmed = recovered.confirmRow(VisualizerChecklistRowId.LAN_PHONE_HAPTIC)

    expectEquals("failed haptic marks row failed", VisualizerChecklistState.FAILED, failed.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).state)
    expectEquals("successful retry recovers row", VisualizerChecklistState.OBSERVED, recovered.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).state)
    expectEquals("recovered row can be confirmed", VisualizerChecklistState.CONFIRMED, confirmed.row(VisualizerChecklistRowId.LAN_PHONE_HAPTIC).state)
    expectEquals("recovered checklist no longer failed", VisualizerChecklistSummary.PENDING, confirmed.checklistSummary())
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
    listOf("desktop profile", "profile editor", "save profile", "hmac", "secret", "private" + " key", "pairing material")
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

private fun List<VisualizerChecklistRow>.updateForTest(
    id: VisualizerChecklistRowId,
    transform: (VisualizerChecklistRow) -> VisualizerChecklistRow,
): List<VisualizerChecklistRow> =
    map { row -> if (row.id == id) transform(row) else row }
