package com.btgun.host.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class ControlEnvelope(
    val v: Int,
    val type: ControlMessageType,
    val msgId: String,
    val sessionId: String,
    val seq: Long,
    val sentElapsedNanos: Long,
    val body: JsonObject = JsonObject(emptyMap()),
)

enum class ControlMessageType(val wireName: String) {
    PAIRING_STATE("pairing_state"),
    SESSION_READY("session_ready"),
    RESERVED_HAPTIC_COMMAND("reserved_haptic_command");

    companion object {
        fun fromWireName(wireName: String): ControlMessageType? =
            entries.firstOrNull { it.wireName == wireName }
    }
}

sealed interface ControlDecodeResult {
    data class Accepted(val envelope: ControlEnvelope) : ControlDecodeResult
    data class Rejected(val error: ControlEnvelopeError, val detail: String? = null) : ControlDecodeResult
}

enum class ControlEnvelopeError {
    OVERSIZED,
    MALFORMED,
    UNSUPPORTED_VERSION,
    UNKNOWN_TYPE,
    RESERVED_HAPTIC_BODY,
    INVALID_FIELD,
}

object ControlEnvelopeCodec {
    private const val VERSION = 1
    private const val DEFAULT_MAX_BYTES = 16 * 1024
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(envelope: ControlEnvelope): String =
        json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("v", envelope.v)
                put("type", envelope.type.wireName)
                put("msgId", envelope.msgId)
                put("sessionId", envelope.sessionId)
                put("seq", envelope.seq)
                put("sentElapsedNanos", envelope.sentElapsedNanos)
                put("body", envelope.body)
            },
        )

    fun decode(text: String, maxBytes: Int = DEFAULT_MAX_BYTES): ControlDecodeResult {
        if (text.toByteArray(Charsets.UTF_8).size > maxBytes) {
            return ControlDecodeResult.Rejected(ControlEnvelopeError.OVERSIZED)
        }
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            return ControlDecodeResult.Rejected(ControlEnvelopeError.MALFORMED, it.message)
        }

        val version = root.intField("v") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "v")
        if (version != VERSION) {
            return ControlDecodeResult.Rejected(ControlEnvelopeError.UNSUPPORTED_VERSION)
        }
        val typeWireName = root.stringField("type") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "type")
        val type = ControlMessageType.fromWireName(typeWireName)
            ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.UNKNOWN_TYPE)
        val body = root["body"] as? JsonObject ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "body")
        if (type == ControlMessageType.RESERVED_HAPTIC_COMMAND && body.isNotEmpty()) {
            return ControlDecodeResult.Rejected(ControlEnvelopeError.RESERVED_HAPTIC_BODY)
        }

        return ControlDecodeResult.Accepted(
            ControlEnvelope(
                v = version,
                type = type,
                msgId = root.stringField("msgId") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "msgId"),
                sessionId = root.stringField("sessionId") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "sessionId"),
                seq = root.longField("seq") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "seq"),
                sentElapsedNanos = root.longField("sentElapsedNanos")
                    ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "sentElapsedNanos"),
                body = body,
            ),
        )
    }

    private fun JsonObject.stringField(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.intField(name: String): Int? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull?.toInt()

    private fun JsonObject.longField(name: String): Long? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull
}
