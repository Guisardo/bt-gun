package com.btgun.host.haptics

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class HapticTimelinePulse(
    val atMs: Long,
    val durationMs: Long,
    val strength: Double,
) {
    fun toJsonBody(): JsonObject =
        buildJsonObject {
            put("atMs", JsonPrimitive(atMs))
            put("durationMs", JsonPrimitive(durationMs))
            put("strength", JsonPrimitive(strength))
        }
}

data class DesktopHapticCommand(
    val commandId: String,
    val strength: Double,
    val durationMs: Long,
    val ttlMs: Long,
    val pattern: String? = null,
    val patternTimeline: List<HapticTimelinePulse> = emptyList(),
) {
    fun toJsonBody(): JsonObject =
        JsonObject(
            mapOf(
                "commandId" to JsonPrimitive(commandId),
                "strength" to JsonPrimitive(strength),
                "durationMs" to JsonPrimitive(durationMs),
                "ttlMs" to JsonPrimitive(ttlMs),
                "pattern" to (pattern?.let(::JsonPrimitive) ?: JsonNull),
                "patternTimeline" to buildJsonArray {
                    patternTimeline.forEach { pulse -> add(pulse.toJsonBody()) }
                },
            ),
        )

    fun validationError(): String? =
        when {
            commandId.isBlank() -> "invalid commandId"
            strength !in 0.0..1.0 -> "invalid strength"
            durationMs !in 1L..1_000L -> "invalid durationMs"
            ttlMs !in 1L..2_000L -> "invalid ttlMs"
            patternTimeline.size > 8 -> "invalid patternTimeline"
            patternTimeline.sumOf { it.durationMs } > 2_000L -> "invalid patternTimeline"
            patternTimeline.any { pulse ->
                pulse.atMs < 0L ||
                    pulse.durationMs !in 1L..2_000L ||
                    pulse.strength !in 0.0..1.0 ||
                    pulse.atMs + pulse.durationMs > 2_000L
            } -> "invalid patternTimeline"
            patternTimeline.hasOverlaps() -> "invalid patternTimeline"
            else -> null
        }

    companion object {
        fun fromJsonBody(body: JsonObject): DesktopHapticCommand? =
            runCatching {
                DesktopHapticCommand(
                    commandId = body.stringField("commandId") ?: return null,
                    strength = body.doubleField("strength") ?: return null,
                    durationMs = body.longField("durationMs") ?: return null,
                    ttlMs = body.longField("ttlMs") ?: return null,
                    pattern = body.nullableStringField("pattern"),
                    patternTimeline = body.timelineField() ?: return null,
                )
            }.getOrNull()
    }
}

data class HapticResult(
    val commandId: String,
    val status: HapticResultStatus,
    val detail: String,
    val observedElapsedNanos: Long,
) {
    fun toJsonBody(): JsonObject =
        JsonObject(
            mapOf(
                "commandId" to JsonPrimitive(commandId),
                "status" to JsonPrimitive(status.wireName),
                "detail" to JsonPrimitive(detail),
                "observedElapsedNanos" to JsonPrimitive(observedElapsedNanos),
            ),
        )
}

enum class HapticResultStatus(val wireName: String) {
    STARTED("started"),
    EXPIRED("expired"),
    UNSUPPORTED("unsupported"),
    PERMISSION_BLOCKED("permission_blocked"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromWireName(wireName: String): HapticResultStatus? =
            entries.firstOrNull { it.wireName == wireName }
    }
}

sealed interface PhoneHapticStartResult {
    data object Started : PhoneHapticStartResult
    data object Unsupported : PhoneHapticStartResult
    data object PermissionBlocked : PhoneHapticStartResult
    data class Failed(val detail: String) : PhoneHapticStartResult
}

interface PhoneHapticActuator {
    fun pulse(durationMs: Long, strength: Double): PhoneHapticStartResult
    fun patternTimeline(timeline: List<HapticTimelinePulse>): PhoneHapticStartResult =
        PhoneHapticStartResult.Unsupported
    fun cancel(): HapticResultStatus
}

class DesktopHapticCommandExecutor(
    private val phone: PhoneHapticActuator,
    private val elapsedRealtimeNanos: () -> Long,
) {
    private data class ActiveHaptic(
        val commandId: String,
        val endsAtNanos: Long,
    )

    private var active: ActiveHaptic? = null

    fun handle(command: DesktopHapticCommand, receivedElapsedNanos: Long): HapticResult {
        val now = elapsedRealtimeNanos()
        clearExpired(now)
        command.validationError()?.let { error ->
            return result(command.commandId.ifBlank { "invalid" }, HapticResultStatus.FAILED, error, now)
        }
        if (now - receivedElapsedNanos > command.ttlMs * NANOS_PER_MILLI) {
            return result(command.commandId, HapticResultStatus.EXPIRED, "haptic command expired", now)
        }
        if (command.pattern != null) {
            return result(command.commandId, HapticResultStatus.UNSUPPORTED, "haptic pattern playback unsupported", now)
        }
        if (command.strength <= 0.0) {
            active = null
            val status = phone.cancel()
            return result(command.commandId, status, cancelDetail(status), elapsedRealtimeNanos())
        }
        if (command.patternTimeline.isNotEmpty()) {
            if (active != null) {
                phone.cancel()
                active = null
            }
            return when (val start = phone.patternTimeline(command.patternTimeline)) {
                PhoneHapticStartResult.Started -> {
                    val observed = elapsedRealtimeNanos()
                    active = ActiveHaptic(
                        commandId = command.commandId,
                        endsAtNanos = observed + command.patternTimeline.maxOf { it.atMs + it.durationMs } * NANOS_PER_MILLI,
                    )
                    result(command.commandId, HapticResultStatus.STARTED, "phone haptic timeline started", observed)
                }
                PhoneHapticStartResult.Unsupported ->
                    startPulse(command)
                PhoneHapticStartResult.PermissionBlocked ->
                    result(command.commandId, HapticResultStatus.PERMISSION_BLOCKED, "vibrate permission blocked", elapsedRealtimeNanos())
                is PhoneHapticStartResult.Failed ->
                    result(command.commandId, HapticResultStatus.FAILED, start.detail, elapsedRealtimeNanos())
            }
        }
        if (active != null) {
            phone.cancel()
            active = null
        }

        return startPulse(command)
    }

    private fun startPulse(command: DesktopHapticCommand): HapticResult {
        return when (val start = phone.pulse(command.durationMs, command.strength)) {
            PhoneHapticStartResult.Started -> {
                val observed = elapsedRealtimeNanos()
                active = ActiveHaptic(
                    commandId = command.commandId,
                    endsAtNanos = observed + command.durationMs * NANOS_PER_MILLI,
                )
                result(command.commandId, HapticResultStatus.STARTED, "phone pulse started", observed)
            }
            PhoneHapticStartResult.Unsupported ->
                result(command.commandId, HapticResultStatus.UNSUPPORTED, "phone haptic unsupported", elapsedRealtimeNanos())
            PhoneHapticStartResult.PermissionBlocked ->
                result(command.commandId, HapticResultStatus.PERMISSION_BLOCKED, "vibrate permission blocked", elapsedRealtimeNanos())
            is PhoneHapticStartResult.Failed ->
                result(command.commandId, HapticResultStatus.FAILED, start.detail, elapsedRealtimeNanos())
        }
    }

    fun onControlDisconnected(nowElapsedNanos: Long) {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
    }

    fun onSessionChanged(newSessionId: String): HapticResult? {
        require(newSessionId.isNotBlank()) { "newSessionId must not be blank" }
        val now = elapsedRealtimeNanos()
        clearExpired(now)
        val commandId = active?.commandId ?: return null
        active = null
        val status = phone.cancel()
        return result(commandId, status, cancelDetail(status), elapsedRealtimeNanos())
    }

    private fun clearExpired(nowElapsedNanos: Long) {
        val activeHaptic = active ?: return
        if (nowElapsedNanos >= activeHaptic.endsAtNanos) {
            active = null
        }
    }

    private fun cancelDetail(status: HapticResultStatus): String =
        when (status) {
            HapticResultStatus.CANCELLED -> "phone pulse cancelled"
            HapticResultStatus.UNSUPPORTED -> "phone haptic unsupported"
            HapticResultStatus.PERMISSION_BLOCKED -> "vibrate permission blocked"
            HapticResultStatus.FAILED -> "phone haptic cancel failed"
            HapticResultStatus.STARTED,
            HapticResultStatus.EXPIRED,
            -> status.wireName
        }

    private fun result(
        commandId: String,
        status: HapticResultStatus,
        detail: String,
        observedElapsedNanos: Long,
    ): HapticResult =
        HapticResult(
            commandId = commandId,
            status = status,
            detail = detail,
            observedElapsedNanos = observedElapsedNanos,
        )

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

private fun JsonObject.stringField(name: String): String? =
    get(name)?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.nullableStringField(name: String): String? {
    val value = get(name) ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.takeIf { it.isString }?.contentOrNull
}

private fun JsonObject.doubleField(name: String): Double? =
    get(name)?.jsonPrimitive?.doubleOrNull

private fun JsonObject.longField(name: String): Long? =
    get(name)?.jsonPrimitive?.longOrNull

private fun JsonObject.timelineField(): List<HapticTimelinePulse>? {
    val value = get("patternTimeline") ?: return emptyList()
    return runCatching {
        val array = value.jsonArray
        array.map { element ->
            val item = element.jsonObject
            HapticTimelinePulse(
                atMs = item.longField("atMs") ?: return null,
                durationMs = item.longField("durationMs") ?: return null,
                strength = item.doubleField("strength") ?: return null,
            )
        }
    }.getOrNull()
}

internal fun List<HapticTimelinePulse>.hasOverlaps(): Boolean {
    var previousEnd = 0L
    sortedBy { it.atMs }.forEachIndexed { index, pulse ->
        if (index > 0 && pulse.atMs < previousEnd) {
            return true
        }
        previousEnd = pulse.atMs + pulse.durationMs
    }
    return false
}
