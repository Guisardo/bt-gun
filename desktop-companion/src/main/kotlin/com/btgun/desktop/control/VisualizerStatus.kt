package com.btgun.desktop.control

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class VisualizerStatus(
    val rawDebugEnabled: Boolean,
    val aimZeroState: String,
    val recenterState: String,
    val lastRecenterElapsedNanos: Long? = null,
    val androidElapsedNanos: Long,
    val captureElapsedNanos: Long? = null,
    val sendElapsedNanos: Long? = null,
    val statusSequence: Long? = null,
    val recenterLabel: String = STATE_UNAVAILABLE,
    val aimZeroLabel: String = STATE_UNAVAILABLE,
)

internal fun visualizerStatusFromJsonBody(body: JsonObject): VisualizerStatus? {
    val statusBody = body[VISUALIZER_STATUS_BODY_KEY] as? JsonObject ?: return null
    if (statusBody.keys.any { it !in allowedFields }) {
        return null
    }
    val source = statusBody.stringField("source")
    if (source != null && source != SOURCE_ANDROID) {
        return null
    }
    val rawDebugEnabled = statusBody.booleanField("rawDebugEnabled") ?: return null
    val aimZeroState = statusBody.stringField("aimZeroState")?.takeIf { it in allowedAimZeroStates } ?: return null
    val recenterState = statusBody.stringField("recenterState")?.takeIf { it in allowedRecenterStates } ?: return null
    val androidElapsedNanos = statusBody.nonNegativeLongField("androidElapsedNanos") ?: return null
    if (optionalElapsedFields.any { statusBody.hasInvalidOptionalNonNegativeLong(it) }) {
        return null
    }
    return VisualizerStatus(
        rawDebugEnabled = rawDebugEnabled,
        aimZeroState = aimZeroState,
        recenterState = recenterState,
        lastRecenterElapsedNanos = statusBody.optionalNonNegativeLongField("lastRecenterElapsedNanos"),
        androidElapsedNanos = androidElapsedNanos,
        captureElapsedNanos = statusBody.optionalNonNegativeLongField("captureElapsedNanos"),
        sendElapsedNanos = statusBody.optionalNonNegativeLongField("sendElapsedNanos"),
        statusSequence = statusBody.optionalNonNegativeLongField("statusSequence"),
        recenterLabel = statusBody.sanitizedLabelField("recenterLabel") ?: recenterState,
        aimZeroLabel = statusBody.sanitizedLabelField("aimZeroLabel") ?: aimZeroState,
    )
}

private fun JsonObject.stringField(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.booleanField(name: String): Boolean? =
    (get(name) as? JsonPrimitive)?.jsonPrimitive?.booleanOrNull

private fun JsonObject.nonNegativeLongField(name: String): Long? =
    (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull?.takeIf { it >= 0L }

private fun JsonObject.optionalNonNegativeLongField(name: String): Long? =
    if (name in this) nonNegativeLongField(name) else null

private fun JsonObject.hasInvalidOptionalNonNegativeLong(name: String): Boolean =
    name in this && nonNegativeLongField(name) == null

private fun JsonObject.sanitizedLabelField(name: String): String? =
    stringField(name)
        ?.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' || it == ':' || it == '/' }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(MAX_LABEL_CHARS)

internal const val VISUALIZER_STATUS_BODY_KEY: String = "visualizerStatus"
internal const val STATE_UNAVAILABLE: String = "unavailable"
private const val SOURCE_ANDROID: String = "android"
private const val MAX_LABEL_CHARS = 48
private val allowedAimZeroStates = setOf("ready", "recentered", "pending", STATE_UNAVAILABLE)
private val allowedRecenterStates = setOf("idle", "held", "recentered", STATE_UNAVAILABLE)
private val optionalElapsedFields = setOf(
    "lastRecenterElapsedNanos",
    "captureElapsedNanos",
    "sendElapsedNanos",
    "statusSequence",
)
private val allowedFields = setOf(
    "rawDebugEnabled",
    "aimZeroState",
    "recenterState",
    "lastRecenterElapsedNanos",
    "androidElapsedNanos",
    "captureElapsedNanos",
    "sendElapsedNanos",
    "statusSequence",
    "recenterLabel",
    "aimZeroLabel",
    "source",
)
