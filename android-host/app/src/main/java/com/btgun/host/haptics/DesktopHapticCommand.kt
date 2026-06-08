package com.btgun.host.haptics

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class DesktopHapticCommand(
    val commandId: String,
    val strength: Double,
    val durationMs: Long,
    val ttlMs: Long,
    val pattern: String? = null,
) {
    fun toJsonBody(): JsonObject =
        JsonObject(
            mapOf(
                "commandId" to JsonPrimitive(commandId),
                "strength" to JsonPrimitive(strength),
                "durationMs" to JsonPrimitive(durationMs),
                "ttlMs" to JsonPrimitive(ttlMs),
                "pattern" to (pattern?.let(::JsonPrimitive) ?: JsonNull),
            ),
        )

    fun validationError(): String? =
        when {
            commandId.isBlank() -> "invalid commandId"
            strength !in 0.0..1.0 -> "invalid strength"
            durationMs !in 1L..1_000L -> "invalid durationMs"
            ttlMs !in 1L..2_000L -> "invalid ttlMs"
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
    fun cancel(): HapticResultStatus
}

class DesktopHapticCommandExecutor(
    private val phone: PhoneHapticActuator,
    private val elapsedRealtimeNanos: () -> Long,
) {
    private var activeCommandId: String? = null

    fun handle(command: DesktopHapticCommand, receivedElapsedNanos: Long): HapticResult {
        val now = elapsedRealtimeNanos()
        command.validationError()?.let { error ->
            return result(command.commandId.ifBlank { "invalid" }, HapticResultStatus.FAILED, error, now)
        }
        if (now - receivedElapsedNanos > command.ttlMs * NANOS_PER_MILLI) {
            return result(command.commandId, HapticResultStatus.EXPIRED, "haptic command expired", now)
        }
        if (command.pattern != null) {
            return result(command.commandId, HapticResultStatus.UNSUPPORTED, "haptic pattern playback unsupported", now)
        }
        if (activeCommandId != null) {
            phone.cancel()
            activeCommandId = null
        }

        return when (val start = phone.pulse(command.durationMs, command.strength)) {
            PhoneHapticStartResult.Started -> {
                activeCommandId = command.commandId
                result(command.commandId, HapticResultStatus.STARTED, "phone pulse started", elapsedRealtimeNanos())
            }
            PhoneHapticStartResult.Unsupported ->
                result(command.commandId, HapticResultStatus.UNSUPPORTED, "phone haptic unsupported", elapsedRealtimeNanos())
            PhoneHapticStartResult.PermissionBlocked ->
                result(command.commandId, HapticResultStatus.PERMISSION_BLOCKED, "vibrate permission blocked", elapsedRealtimeNanos())
            is PhoneHapticStartResult.Failed ->
                result(command.commandId, HapticResultStatus.FAILED, start.detail, elapsedRealtimeNanos())
        }
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
