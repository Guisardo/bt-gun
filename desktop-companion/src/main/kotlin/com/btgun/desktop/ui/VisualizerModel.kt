package com.btgun.desktop.ui

import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.diagnostics.DiagnosticDomain
import com.btgun.desktop.diagnostics.DiagnosticEvent
import com.btgun.desktop.diagnostics.DiagnosticStatus
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.security.SecretRedactor
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpReceivedInput

enum class VisualizerChecklistState {
    WAITING,
    OBSERVED,
    CONFIRMED,
    FAILED,
    UNSUPPORTED_DEFERRED,
}

enum class VisualizerChecklistSummary {
    PENDING,
    PASSING,
    NEEDS_ATTENTION,
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
    val observedSource: String? = null,
    val observedElapsedNanos: Long? = null,
    val confirmationLabel: String? = if (id.requiresUserConfirmation) "Confirm observed" else null,
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
    val lastRecenterLabel: String = "Last recenter: none",
    val lastRecenterElapsedNanos: Long? = null,
    val recenterState: String = "unavailable",
    val lastStatusObservedElapsedNanos: Long? = null,
)

data class VisualizerHapticStatus(
    val commandId: String?,
    val status: String,
    val detail: String,
    val observedElapsedNanos: Long?,
)

data class VisualizerDiagnosticBucket(
    val id: String,
    val label: String,
    val status: String = DiagnosticStatus.UNKNOWN.wireName,
    val reasonCode: String = "not_reported",
    val detail: String = "No diagnostic event reported",
    val attention: Boolean = false,
) {
    companion object {
        const val MAX_DETAIL_CHARS = 96
    }
}

data class VisualizerDiagnosticSummary(
    val buckets: List<VisualizerDiagnosticBucket> = DiagnosticDomain.entries.map { domain ->
        VisualizerDiagnosticBucket(
            id = domain.wireName,
            label = domain.label(),
        )
    },
) {
    fun bucket(id: String): VisualizerDiagnosticBucket =
        buckets.first { bucket -> bucket.id == id }

    fun withEvent(event: DiagnosticEvent): VisualizerDiagnosticSummary =
        copy(
            buckets = buckets.map { bucket ->
                if (bucket.id == event.domain.wireName) bucket.updatedBy(event) else bucket
            },
        )
}

data class VisualizerModel(
    val liveState: SemanticControllerState = SemanticControllerState(),
    val profileSummary: VisualizerProfileSummary = VisualizerProfileSummary(),
    val controlSessionState: ControlServerSessionState = ControlServerSessionState.STOPPED,
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
    val diagnosticSummary: VisualizerDiagnosticSummary = VisualizerDiagnosticSummary(),
) {
    fun row(id: VisualizerChecklistRowId): VisualizerChecklistRow =
        checklistRows.first { it.id == id }

    fun withProductEvent(event: VisualizerProductEvent): VisualizerModel =
        copy(productEvents = (listOf(event) + productEvents).take(MAX_PRODUCT_EVENTS))

    fun withAcceptedInput(input: UdpReceivedInput, observedElapsedNanos: Long): VisualizerModel {
        val state = UdpControllerStateAdapter.toState(input)
        val nextRawDebug = VisualizerRawDebugState(
            enabled = input.rawDebugEnabled,
            collapsed = !input.rawDebugEnabled,
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
                .markObserved(
                    id = VisualizerChecklistRowId.LAN_VISUALIZER_STREAM,
                    source = "authenticated LAN mapped UDP frame",
                    observedElapsedNanos = observedElapsedNanos,
                )
                .markObserved(
                    id = VisualizerChecklistRowId.LIVE_CONTROLS,
                    source = "live mapped controls and aim stream",
                    observedElapsedNanos = observedElapsedNanos,
                ),
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

    fun withControlSessionState(state: ControlServerSessionState): VisualizerModel =
        copy(
            controlSessionState = state,
            packetLifecycle = when (state) {
                ControlServerSessionState.AUTHENTICATED -> InputStreamLifecycleState.ACTIVE
                ControlServerSessionState.DEGRADED -> InputStreamLifecycleState.STALE
                ControlServerSessionState.DISCONNECTED,
                ControlServerSessionState.STOPPED,
                -> InputStreamLifecycleState.STOPPED
                else -> packetLifecycle
            },
        )

    fun withProfileMetadata(metadata: ProfileMetadata): VisualizerModel =
        copy(profileSummary = VisualizerProfileSummary.from(metadata))

    fun withInputRejection(reason: String): VisualizerModel =
        copy(
            rawDebug = rawDebug.copy(lastRejection = reason.take(80)),
        )

    fun withDiagnosticEvent(event: DiagnosticEvent): VisualizerModel =
        copy(diagnosticSummary = diagnosticSummary.withEvent(event))

    fun withVisualizerStatus(status: VisualizerStatus, observedElapsedNanos: Long): VisualizerModel {
        val recentered = status.recenterState == "recentered" && status.lastRecenterElapsedNanos != null
        return copy(
            recenter = VisualizerRecenterState(
                aimZeroLabel = "Aim zero: ${status.aimZeroLabel}",
                recenterInstruction = recenter.recenterInstruction,
                lastRecenterLabel = lastRecenterLabel(status),
                lastRecenterElapsedNanos = status.lastRecenterElapsedNanos,
                recenterState = status.recenterState,
                lastStatusObservedElapsedNanos = observedElapsedNanos,
            ),
            rawDebug = rawDebug.copy(
                enabled = status.rawDebugEnabled,
                collapsed = !status.rawDebugEnabled,
                provider = if (status.rawDebugEnabled) rawDebug.provider else null,
                yaw = if (status.rawDebugEnabled) rawDebug.yaw else null,
                pitch = if (status.rawDebugEnabled) rawDebug.pitch else null,
                roll = if (status.rawDebugEnabled) rawDebug.roll else null,
                rawAimX = if (status.rawDebugEnabled) rawDebug.rawAimX else null,
                rawAimY = if (status.rawDebugEnabled) rawDebug.rawAimY else null,
            ),
            checklistRows = checklistRows.markObservedIf(
                id = VisualizerChecklistRowId.RECENTER_AIM_ZERO,
                condition = recentered,
                source = "Android visualizer recenter status",
                observedElapsedNanos = observedElapsedNanos,
            ),
        ).withProductEvent(
            VisualizerProductEvent(
                type = "recenter_${status.recenterState}",
                sequence = status.statusSequence,
                ageSourceElapsedNanos = observedElapsedNanos,
            ),
        )
    }

    fun withMetrics(snapshot: VisualizerMetricSnapshot): VisualizerModel =
        copy(
            metrics = snapshot,
            checklistRows = checklistRows
                .markObservedIf(
                    id = VisualizerChecklistRowId.LATENCY_TARGET,
                    condition = snapshot.targetStatus == "pass",
                    source = "current-session latency sample under target",
                    observedElapsedNanos = null,
                )
                .markObservedIf(
                    id = VisualizerChecklistRowId.PACKET_LOSS,
                    condition = snapshot.packetExpected > 0L,
                    source = "current-session accepted sequence counters",
                    observedElapsedNanos = null,
                ),
        )

    fun withHapticResult(result: HapticResult): VisualizerModel =
        if (result.status == HapticResultStatus.STARTED) {
            copy(
                hapticStatus = VisualizerHapticStatus(
                    commandId = result.commandId,
                    status = "confirmed",
                    detail = VisualizerWindow.hapticResultStatusText(result.status),
                    observedElapsedNanos = result.observedElapsedNanos,
                ),
                checklistRows = checklistRows.markObservedAfterRetry(
                    id = VisualizerChecklistRowId.LAN_PHONE_HAPTIC,
                    source = "authenticated LAN phone haptic ack",
                    observedElapsedNanos = result.observedElapsedNanos,
                ),
            )
        } else {
            copy(
                hapticStatus = VisualizerHapticStatus(
                    commandId = result.commandId,
                    status = "failed",
                    detail = VisualizerWindow.hapticResultStatusText(result.status),
                    observedElapsedNanos = result.observedElapsedNanos,
                ),
                checklistRows = checklistRows.update(VisualizerChecklistRowId.LAN_PHONE_HAPTIC) { row ->
                    if (row.state == VisualizerChecklistState.CONFIRMED) row else row.copy(state = VisualizerChecklistState.FAILED)
                },
            )
        }.withProductEvent(
            VisualizerProductEvent(
                type = "haptic_${result.status.wireName}",
                sequence = null,
                ageSourceElapsedNanos = result.observedElapsedNanos,
            ),
        )

    fun withHapticSendResult(
        result: HapticSendResult,
        commandId: String?,
        observedElapsedNanos: Long,
    ): VisualizerModel {
        val nextStatus = when (result) {
            HapticSendResult.Sent -> VisualizerHapticStatus(
                commandId = commandId,
                status = "queued",
                detail = VisualizerWindow.hapticSendStatusText(result, commandId),
                observedElapsedNanos = observedElapsedNanos,
            )
            HapticSendResult.NoActiveSession -> VisualizerHapticStatus(
                commandId = null,
                status = "no-session",
                detail = VisualizerWindow.hapticSendStatusText(result, commandId),
                observedElapsedNanos = observedElapsedNanos,
            )
            is HapticSendResult.Rejected -> VisualizerHapticStatus(
                commandId = commandId,
                status = "failed",
                detail = VisualizerWindow.hapticSendStatusText(result, commandId),
                observedElapsedNanos = observedElapsedNanos,
            )
            is HapticSendResult.Failed -> VisualizerHapticStatus(
                commandId = commandId,
                status = "failed",
                detail = VisualizerWindow.hapticSendStatusText(result, commandId),
                observedElapsedNanos = observedElapsedNanos,
            )
        }
        return copy(hapticStatus = nextStatus)
    }

    fun withWindowsBackendDiagnostics(
        diagnostics: WindowsBackendRuntimeDiagnostics,
        observedElapsedNanos: Long,
    ): VisualizerModel {
        var rows = checklistRows
        if (
            diagnostics.lifecycleState == BackendLifecycleState.STARTED &&
            diagnostics.lastPublishResult == BackendPublishResult.Published &&
            diagnostics.lastSourceSequence != null
        ) {
            rows = rows.markObserved(
                id = VisualizerChecklistRowId.WINDOWS_VHF_INPUT,
                source = "Phase 6 Windows VHF backend published seq=${diagnostics.lastSourceSequence}",
                observedElapsedNanos = observedElapsedNanos,
            )
        }
        if (
            diagnostics.outputHapticCommandsRouted > 0L &&
            diagnostics.lastHapticSendResult == HapticSendResult.Sent
        ) {
            rows = rows.markObserved(
                id = VisualizerChecklistRowId.WINDOWS_VHF_HAPTIC,
                source = "Phase 6 Windows VHF output routed to phone haptic",
                observedElapsedNanos = observedElapsedNanos,
            )
        }
        return copy(checklistRows = rows)
    }

    fun withMacosBackendDiagnostics(
        diagnostics: MacosBackendRuntimeDiagnostics,
        observedElapsedNanos: Long,
    ): VisualizerModel {
        val source = if (diagnostics.outputHapticCommandsRouted > 0L) {
            "macOS HID haptic unsupported/deferred; LAN and Windows phone haptics remain available"
        } else {
            "macOS HID haptic unsupported/deferred by Phase 7 evidence"
        }
        return copy(
            checklistRows = checklistRows.markObserved(
                id = VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT,
                source = source,
                observedElapsedNanos = observedElapsedNanos,
            ),
        )
    }

    fun confirmRow(id: VisualizerChecklistRowId): VisualizerModel =
        copy(
            checklistRows = checklistRows.update(id) { row ->
                if (row.state == VisualizerChecklistState.OBSERVED) {
                    row.copy(
                        state = VisualizerChecklistState.CONFIRMED,
                        observedSource = row.observedSource ?: "user-confirmed observation",
                    )
                } else if (id == VisualizerChecklistRowId.MACOS_HID_INPUT && row.state == VisualizerChecklistState.WAITING) {
                    row.copy(
                        state = VisualizerChecklistState.CONFIRMED,
                        observedSource = "user-confirmed macOS Android HID input",
                    )
                } else {
                    row
                }
            },
        )

    fun confirmNextObservedRow(): VisualizerModel {
        val next = checklistRows.firstOrNull { row -> row.isConfirmableByUser() } ?: return this
        return confirmRow(next.id)
    }

    fun confirmLimitation(id: VisualizerChecklistRowId): VisualizerModel =
        if (id == VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT) {
            copy(
                checklistRows = checklistRows.update(id) { row ->
                    row.copy(
                        state = VisualizerChecklistState.UNSUPPORTED_DEFERRED,
                        observedSource = row.observedSource
                            ?: "Phase 7 macOS HID haptic unsupported/deferred evidence",
                        confirmationLabel = "Confirm limitation",
                    )
                },
            )
        } else {
            this
        }

    fun failRow(id: VisualizerChecklistRowId): VisualizerModel =
        copy(checklistRows = checklistRows.update(id) { row -> row.copy(state = VisualizerChecklistState.FAILED) })

    fun markUnsupportedDeferred(id: VisualizerChecklistRowId): VisualizerModel =
        copy(checklistRows = checklistRows.update(id) { row -> row.copy(state = VisualizerChecklistState.UNSUPPORTED_DEFERRED) })

    fun resetChecklist(): VisualizerModel =
        copy(checklistRows = defaultChecklistRows())

    fun checklistSummary(): VisualizerChecklistSummary =
        when {
            checklistRows.any { it.state == VisualizerChecklistState.FAILED } -> VisualizerChecklistSummary.NEEDS_ATTENTION
            checklistRows.all { it.isAcceptedForPass() } -> VisualizerChecklistSummary.PASSING
            else -> VisualizerChecklistSummary.PENDING
        }

    fun topSummaryLabel(): String =
        when (checklistSummary()) {
            VisualizerChecklistSummary.PENDING -> VisualizerWindow.topSummaryPending()
            VisualizerChecklistSummary.PASSING -> VisualizerWindow.topSummaryPassing()
            VisualizerChecklistSummary.NEEDS_ATTENTION -> VisualizerWindow.topSummaryFailed()
        }

    companion object {
        const val MAX_PRODUCT_EVENTS = 10

        fun initial(): VisualizerModel = VisualizerModel()

        fun defaultChecklistRows(): List<VisualizerChecklistRow> =
            VisualizerChecklistRowId.entries.map { id -> VisualizerChecklistRow(id = id) }
    }
}

private fun VisualizerChecklistRow.isAcceptedForPass(): Boolean =
    when (id) {
        VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT ->
            state == VisualizerChecklistState.UNSUPPORTED_DEFERRED
        else -> if (requiresUserConfirmation) {
            state == VisualizerChecklistState.CONFIRMED
        } else {
            state == VisualizerChecklistState.OBSERVED || state == VisualizerChecklistState.CONFIRMED
        }
    }

private fun VisualizerChecklistRow.isConfirmableByUser(): Boolean =
    requiresUserConfirmation &&
        (
            state == VisualizerChecklistState.OBSERVED ||
                (id == VisualizerChecklistRowId.MACOS_HID_INPUT && state == VisualizerChecklistState.WAITING)
            )

private fun lastRecenterLabel(status: VisualizerStatus): String {
    val last = status.lastRecenterElapsedNanos ?: return "Last recenter: none"
    val ageMillis = ((status.androidElapsedNanos - last).coerceAtLeast(0L)) / 1_000_000L
    return "Last recenter: $ageMillis ms ago"
}

private fun List<VisualizerChecklistRow>.markObserved(
    id: VisualizerChecklistRowId,
    source: String,
    observedElapsedNanos: Long?,
): List<VisualizerChecklistRow> =
    update(id) { row ->
        if (row.state == VisualizerChecklistState.WAITING) {
            row.copy(
                state = VisualizerChecklistState.OBSERVED,
                observedSource = source,
                observedElapsedNanos = observedElapsedNanos,
            )
        } else {
            row
        }
    }

private fun List<VisualizerChecklistRow>.markObservedAfterRetry(
    id: VisualizerChecklistRowId,
    source: String,
    observedElapsedNanos: Long?,
): List<VisualizerChecklistRow> =
    update(id) { row ->
        when (row.state) {
            VisualizerChecklistState.WAITING,
            VisualizerChecklistState.FAILED,
            -> row.copy(
                state = VisualizerChecklistState.OBSERVED,
                observedSource = source,
                observedElapsedNanos = observedElapsedNanos,
            )
            else -> row
        }
    }

private fun List<VisualizerChecklistRow>.markObservedIf(
    id: VisualizerChecklistRowId,
    condition: Boolean,
    source: String,
    observedElapsedNanos: Long?,
): List<VisualizerChecklistRow> =
    if (condition) {
        markObserved(
            id = id,
            source = source,
            observedElapsedNanos = observedElapsedNanos,
        )
    } else {
        this
    }

private fun List<VisualizerChecklistRow>.update(
    id: VisualizerChecklistRowId,
    transform: (VisualizerChecklistRow) -> VisualizerChecklistRow,
): List<VisualizerChecklistRow> =
    map { row -> if (row.id == id) transform(row) else row }

private fun VisualizerDiagnosticBucket.updatedBy(event: DiagnosticEvent): VisualizerDiagnosticBucket =
    copy(
        status = event.status.wireName,
        reasonCode = event.reasonCode,
        detail = event.safeVisualizerDetail(),
        attention = event.status in setOf(
            DiagnosticStatus.DEGRADED,
            DiagnosticStatus.BLOCKED,
            DiagnosticStatus.UNSUPPORTED,
        ),
    )

private fun DiagnosticEvent.safeVisualizerDetail(): String {
    val wireDetail = toWireMap()["detail"] as? String ?: detail
    return SecretRedactor.redact(wireDetail)
        .replace(VISUALIZER_UNSAFE_DETAIL_PATTERN, "<redacted>")
        .replace(VISUALIZER_REDACTED_VALUE_PATTERN, "\$1<redacted>")
        .take(VisualizerDiagnosticBucket.MAX_DETAIL_CHARS)
}

private fun DiagnosticDomain.label(): String =
    when (this) {
        DiagnosticDomain.GUN_BLE -> "Gun BLE"
        DiagnosticDomain.SENSOR_MOTION -> "Sensor motion"
        DiagnosticDomain.LAN_CONTROL_UDP -> "LAN/control UDP"
        DiagnosticDomain.PROFILE_MAPPING -> "Profile mapping"
        DiagnosticDomain.HID_BACKEND_HAPTICS -> "HID/backend haptics"
    }

private val VISUALIZER_UNSAFE_DETAIL_PATTERN = Regex(
    "(?i)(qr[_ -]?secret|pairing[_ -]?proof|stream[_ -]?key|hmac[_ -]?key|private[_ -]?key|bluetooth[_ -]?address|android[_ -]?id|raw[_ -]?screenshot|raw[_ -]?log|[0-9a-f]{2}(:[0-9a-f]{2}){5})",
)
private val VISUALIZER_REDACTED_VALUE_PATTERN = Regex("(<redacted>[=: ]*)[A-Za-z0-9_-]+")
