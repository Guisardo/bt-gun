package com.btgun.desktop.ui

import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpReceivedInput

enum class VisualizerChecklistState {
    WAITING,
    OBSERVED,
    CONFIRMED,
    FAILED,
    UNSUPPORTED_DEFERRED,
}

enum class VisualizerChecklistRowId(
    val wireId: String,
    val label: String,
    val requiresUserConfirmation: Boolean,
) {
    LAN_VISUALIZER_STREAM("lan_visualizer_stream", "LAN visualizer stream", false),
    LIVE_CONTROLS("live_controls", "Live gun controls", false),
    RECENTER_AIM_ZERO("recenter_aim_zero", "Recenter and aim zero", true),
    MACOS_HID_INPUT("macos_hid_input", "macOS Android HID input", true),
    WINDOWS_VHF_INPUT("windows_vhf_input", "Windows VHF input", true),
    LAN_PHONE_HAPTIC("lan_phone_haptic", "LAN phone haptic", true),
    WINDOWS_VHF_HAPTIC("windows_vhf_haptic", "Windows VHF phone haptic", true),
    MACOS_HID_HAPTIC_LIMIT("macos_hid_haptic_limit", "macOS HID haptic limitation", true),
    LATENCY_TARGET("latency_target", "Latency under 50 ms", false),
    PACKET_LOSS("packet_loss", "Packet loss visible", false),
}

data class VisualizerChecklistRow(
    val id: VisualizerChecklistRowId,
    val label: String = id.label,
    val state: VisualizerChecklistState = VisualizerChecklistState.WAITING,
    val requiresUserConfirmation: Boolean = id.requiresUserConfirmation,
)

data class VisualizerProductEvent(
    val type: String,
    val sequence: Long?,
    val ageSourceElapsedNanos: Long,
)

data class VisualizerProfileSummary(
    val profileId: String = "unknown",
    val displayName: String = "unknown",
    val revision: Long? = null,
    val source: String = "unknown",
    val rawDebugEnabled: Boolean = false,
) {
    companion object {
        fun from(metadata: ProfileMetadata): VisualizerProfileSummary =
            VisualizerProfileSummary(
                profileId = metadata.profileId,
                displayName = metadata.displayName,
                revision = metadata.revision,
                source = metadata.source,
                rawDebugEnabled = metadata.rawDebugEnabled,
            )
    }
}

data class VisualizerRawDebugState(
    val enabled: Boolean = false,
    val collapsed: Boolean = true,
    val provider: Int? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val roll: Float? = null,
    val rawAimX: Float? = null,
    val rawAimY: Float? = null,
    val lastRejection: String? = null,
)

data class VisualizerRecenterState(
    val aimZeroLabel: String = "unavailable",
    val recenterInstruction: String = "Recenter: hold reload for 2000ms",
    val lastRecenterElapsedNanos: Long? = null,
)

data class VisualizerHapticStatus(
    val commandId: String?,
    val status: String,
    val detail: String,
    val observedElapsedNanos: Long?,
)

data class VisualizerModel(
    val liveState: SemanticControllerState = SemanticControllerState(),
    val profileSummary: VisualizerProfileSummary = VisualizerProfileSummary(),
    val packetLifecycle: InputStreamLifecycleState = InputStreamLifecycleState.STOPPED,
    val hapticStatus: VisualizerHapticStatus = VisualizerHapticStatus(
        commandId = null,
        status = "waiting",
        detail = "none",
        observedElapsedNanos = null,
    ),
    val metrics: VisualizerMetricSnapshot = VisualizerMetricSnapshot.empty(),
    val rawDebug: VisualizerRawDebugState = VisualizerRawDebugState(),
    val recenter: VisualizerRecenterState = VisualizerRecenterState(),
    val checklistRows: List<VisualizerChecklistRow> = defaultChecklistRows(),
    val productEvents: List<VisualizerProductEvent> = emptyList(),
    val lastAcceptedAimX: Float = 0.0f,
    val lastAcceptedAimY: Float = 0.0f,
) {
    fun row(id: VisualizerChecklistRowId): VisualizerChecklistRow =
        checklistRows.first { it.id == id }

    fun withProductEvent(event: VisualizerProductEvent): VisualizerModel =
        copy(productEvents = (listOf(event) + productEvents).take(MAX_PRODUCT_EVENTS))

    fun withAcceptedInput(input: UdpReceivedInput, observedElapsedNanos: Long): VisualizerModel {
        val state = UdpControllerStateAdapter.toState(input)
        val nextRawDebug = VisualizerRawDebugState(
            enabled = input.rawDebugEnabled,
            collapsed = true,
            provider = if (input.rawDebugEnabled) input.motion.provider else null,
            yaw = if (input.rawDebugEnabled) input.motion.yaw else null,
            pitch = if (input.rawDebugEnabled) input.motion.pitch else null,
            roll = if (input.rawDebugEnabled) input.motion.roll else null,
            rawAimX = if (input.rawDebugEnabled) input.motion.rawAimX else null,
            rawAimY = if (input.rawDebugEnabled) input.motion.rawAimY else null,
            lastRejection = rawDebug.lastRejection,
        )
        return copy(
            liveState = state,
            rawDebug = nextRawDebug,
            checklistRows = checklistRows
                .markObserved(VisualizerChecklistRowId.LAN_VISUALIZER_STREAM)
                .markObserved(VisualizerChecklistRowId.LIVE_CONTROLS),
            lastAcceptedAimX = state.aimX,
            lastAcceptedAimY = state.aimY,
        ).withProductEvent(
            VisualizerProductEvent(
                type = if (input.stale) "stale_input" else "input",
                sequence = input.lastAcceptedSequence,
                ageSourceElapsedNanos = observedElapsedNanos,
            ),
        )
    }

    fun withPacketLifecycle(state: InputStreamLifecycleState): VisualizerModel =
        copy(packetLifecycle = state)

    fun withProfileMetadata(metadata: ProfileMetadata): VisualizerModel =
        copy(profileSummary = VisualizerProfileSummary.from(metadata))

    fun withMetrics(snapshot: VisualizerMetricSnapshot): VisualizerModel =
        copy(
            metrics = snapshot,
            checklistRows = checklistRows
                .markObservedIf(VisualizerChecklistRowId.LATENCY_TARGET, snapshot.targetStatus == "pass")
                .markObserved(VisualizerChecklistRowId.PACKET_LOSS),
        )

    fun withHapticResult(result: HapticResult): VisualizerModel =
        copy(
            hapticStatus = VisualizerHapticStatus(
                commandId = result.commandId,
                status = result.status.wireName,
                detail = result.detail,
                observedElapsedNanos = result.observedElapsedNanos,
            ),
            checklistRows = checklistRows.markObserved(VisualizerChecklistRowId.LAN_PHONE_HAPTIC),
        ).withProductEvent(
            VisualizerProductEvent(
                type = "haptic_${result.status.wireName}",
                sequence = null,
                ageSourceElapsedNanos = result.observedElapsedNanos,
            ),
        )

    fun confirmRow(id: VisualizerChecklistRowId): VisualizerModel =
        copy(checklistRows = checklistRows.update(id) { row -> row.copy(state = VisualizerChecklistState.CONFIRMED) })

    fun failRow(id: VisualizerChecklistRowId): VisualizerModel =
        copy(checklistRows = checklistRows.update(id) { row -> row.copy(state = VisualizerChecklistState.FAILED) })

    fun markUnsupportedDeferred(id: VisualizerChecklistRowId): VisualizerModel =
        copy(checklistRows = checklistRows.update(id) { row -> row.copy(state = VisualizerChecklistState.UNSUPPORTED_DEFERRED) })

    companion object {
        const val MAX_PRODUCT_EVENTS = 10

        fun initial(): VisualizerModel = VisualizerModel()

        fun defaultChecklistRows(): List<VisualizerChecklistRow> =
            VisualizerChecklistRowId.entries.map { id -> VisualizerChecklistRow(id = id) }
    }
}

private fun List<VisualizerChecklistRow>.markObserved(id: VisualizerChecklistRowId): List<VisualizerChecklistRow> =
    update(id) { row ->
        if (row.state == VisualizerChecklistState.WAITING) {
            row.copy(state = VisualizerChecklistState.OBSERVED)
        } else {
            row
        }
    }

private fun List<VisualizerChecklistRow>.markObservedIf(
    id: VisualizerChecklistRowId,
    condition: Boolean,
): List<VisualizerChecklistRow> =
    if (condition) markObserved(id) else this

private fun List<VisualizerChecklistRow>.update(
    id: VisualizerChecklistRowId,
    transform: (VisualizerChecklistRow) -> VisualizerChecklistRow,
): List<VisualizerChecklistRow> =
    map { row -> if (row.id == id) transform(row) else row }
