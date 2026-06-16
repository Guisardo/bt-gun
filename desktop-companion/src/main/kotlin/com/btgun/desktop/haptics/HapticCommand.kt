package com.btgun.desktop.haptics

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
    init {
        require(atMs >= 0L) { "atMs must be non-negative" }
        require(durationMs in 1L..2_000L) { "durationMs must be in 1..2000" }
        require(strength in 0.0..1.0) { "strength must be in 0.0..1.0" }
        require(atMs + durationMs <= 2_000L) { "timeline pulse exceeds 2000ms" }
    }

    fun toJsonBody(): JsonObject =
        buildJsonObject {
            put("atMs", JsonPrimitive(atMs))
            put("durationMs", JsonPrimitive(durationMs))
            put("strength", JsonPrimitive(strength))
        }
}

data class HapticCommand(
    val commandId: String,
    val strength: Double,
    val durationMs: Long,
    val ttlMs: Long,
    val pattern: String? = null,
    val patternTimeline: List<HapticTimelinePulse> = emptyList(),
) {
    init {
        require(commandId.isNotBlank()) { "commandId must be nonblank" }
        require(strength in 0.0..1.0) { "strength must be in 0.0..1.0" }
        require(durationMs in 1L..1_000L) { "durationMs must be in 1..1000" }
        require(ttlMs in 1L..2_000L) { "ttlMs must be in 1..2000" }
        require(patternTimeline.size <= 8) { "patternTimeline max is 8 pulses" }
        require(patternTimeline.sumOf { it.durationMs } <= 2_000L) { "patternTimeline total duration must be <=2000ms" }
    }

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

    companion object {
        fun fromJsonBody(body: JsonObject): HapticCommand? =
            runCatching {
                HapticCommand(
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
    init {
        require(commandId.isNotBlank()) { "commandId must be nonblank" }
        require(observedElapsedNanos >= 0L) { "observedElapsedNanos must be non-negative" }
    }

    fun toJsonBody(): JsonObject =
        JsonObject(
            mapOf(
                "commandId" to JsonPrimitive(commandId),
                "status" to JsonPrimitive(status.wireName),
                "detail" to JsonPrimitive(detail),
                "observedElapsedNanos" to JsonPrimitive(observedElapsedNanos),
            ),
        )

    companion object {
        fun fromJsonBody(body: JsonObject): HapticResult? =
            runCatching {
                HapticResult(
                    commandId = body.stringField("commandId") ?: return null,
                    status = HapticResultStatus.fromWireName(body.stringField("status") ?: return null) ?: return null,
                    detail = body.stringField("detail") ?: return null,
                    observedElapsedNanos = body.longField("observedElapsedNanos") ?: return null,
                )
            }.getOrNull()
    }
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
