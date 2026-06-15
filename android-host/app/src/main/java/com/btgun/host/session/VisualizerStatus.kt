package com.btgun.host.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
) {
    fun toJsonBody(): JsonObject =
        buildJsonObject {
            put("rawDebugEnabled", rawDebugEnabled)
            put("aimZeroState", aimZeroState.sanitizedState())
            put("recenterState", recenterState.sanitizedState())
            lastRecenterElapsedNanos?.takeIf { it >= 0L }?.let { put("lastRecenterElapsedNanos", it) }
            put("androidElapsedNanos", androidElapsedNanos.coerceAtLeast(0L))
            captureElapsedNanos?.takeIf { it >= 0L }?.let { put("captureElapsedNanos", it) }
            sendElapsedNanos?.takeIf { it >= 0L }?.let { put("sendElapsedNanos", it) }
            statusSequence?.takeIf { it >= 0L }?.let { put("statusSequence", it) }
            put("recenterLabel", recenterLabel.sanitizedLabel())
            put("aimZeroLabel", aimZeroLabel.sanitizedLabel())
        }

    companion object {
        const val BODY_KEY: String = "visualizerStatus"
        const val STATE_UNAVAILABLE: String = "unavailable"
        const val AIM_ZERO_READY: String = "ready"
        const val AIM_ZERO_PENDING: String = "pending"
        const val AIM_ZERO_UNAVAILABLE: String = STATE_UNAVAILABLE
        const val RECENTER_IDLE: String = "idle"
        const val RECENTER_HELD: String = "held"
        const val RECENTERED: String = "recentered"
        const val RECENTER_UNAVAILABLE: String = STATE_UNAVAILABLE
        private const val MAX_LABEL_CHARS = 48
        private val safeStatePattern = Regex("^[a-z][a-z0-9_]{0,31}$")

        fun body(status: VisualizerStatus): JsonObject =
            buildJsonObject {
                put(BODY_KEY, status.toJsonBody())
            }

        private fun String.sanitizedState(): String {
            val value = trim()
            return if (safeStatePattern.matches(value)) value else STATE_UNAVAILABLE
        }

        private fun String.sanitizedLabel(): String {
            val normalized = trim()
                .filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' || it == ':' || it == '/' }
                .replace(Regex("\\s+"), " ")
                .ifBlank { STATE_UNAVAILABLE }
            return normalized.take(MAX_LABEL_CHARS)
        }
    }
}
