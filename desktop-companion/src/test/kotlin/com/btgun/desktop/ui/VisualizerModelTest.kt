package com.btgun.desktop.ui

import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpReceivedMappedAim
import com.btgun.desktop.transport.UdpReceivedMotion

fun main() {
    eventStripKeepsExactlyTenNewestProductEvents()
    eventStripLabelsIncludeSequenceAndAge()
    rawDebugDrawerStartsCollapsedAndShowsWhitelistedFieldsOnlyWhenEnabled()
    observedLanStreamDoesNotConfirmManualProofRows()
    modelLabelsExcludeDesktopProfileControlsAndSecretFields()
    staleInputPreservesLastAcceptedAimContext()
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
