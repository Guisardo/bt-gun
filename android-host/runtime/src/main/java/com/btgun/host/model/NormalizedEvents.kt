package com.btgun.host.model

import com.btgun.host.motion.MotionCapabilityFlags

data class LiveEnvelope<T>(
    val stream: StreamKind,
    val seq: Long,
    val captureElapsedNanos: Long,
    val emittedElapsedNanos: Long,
    val payload: T,
    val provenance: Provenance? = null,
) {
    init {
        require(seq > 0L) { "seq must start at 1" }
        require(captureElapsedNanos >= 0L) { "captureElapsedNanos must be non-negative" }
        require(emittedElapsedNanos >= captureElapsedNanos) {
            "emittedElapsedNanos must be greater than or equal to captureElapsedNanos"
        }
    }
}

enum class StreamKind(val wireName: String) {
    GUN("gun"),
    MOTION("motion"),
    STATUS("status"),
}

class StreamSequencer {
    private val nextByStream = mutableMapOf<StreamKind, Long>()

    fun next(stream: StreamKind): Long {
        val next = nextByStream[stream] ?: 1L
        nextByStream[stream] = next + 1L
        return next
    }
}

fun interface ElapsedNanosClock {
    fun nowElapsedNanos(): Long
}

sealed interface LivePayload

data class GunEvent(
    val name: String,
    val pressed: Boolean? = null,
    val axisX: Float? = null,
    val axisY: Float? = null,
) : LivePayload

data class GunInputState(
    val pressedControls: Set<String> = emptySet(),
    val stickAxisX: Float = 0f,
    val stickAxisY: Float = 0f,
) {
    fun apply(event: GunEvent): GunInputState =
        if (event.axisX != null || event.axisY != null) {
            copy(
                stickAxisX = event.axisX ?: stickAxisX,
                stickAxisY = event.axisY ?: stickAxisY,
            )
        } else {
            when (event.pressed) {
                true -> copy(pressedControls = pressedControls + event.name)
                false -> copy(pressedControls = pressedControls - event.name)
                null -> this
            }
        }

    fun activeControls(): List<String> {
        val ordered = CONTROL_DISPLAY_ORDER.filter { control -> control in pressedControls }
        val extra = (pressedControls - CONTROL_DISPLAY_ORDER.toSet()).sorted()
        val stick = if (stickAxisX != 0f || stickAxisY != 0f) listOf("stick") else emptyList()
        return ordered + stick + extra
    }

    companion object {
        private val CONTROL_DISPLAY_ORDER = listOf(
            "trigger",
            "reload",
            "button_x",
            "button_y",
            "button_a",
            "button_b",
        )
    }
}

data class MotionSample(
    val provider: MotionProvider,
    val providerName: String = provider.wireName,
    val capabilities: MotionCapabilityFlags = MotionCapabilityFlags(),
    val sourceSensorElapsedNanos: Long,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val rawAimX: Float? = null,
    val rawAimY: Float? = null,
    val aimX: Float? = null,
    val aimY: Float? = null,
    val aimCalibrated: Boolean = false,
    val aimCalibrationProvider: String? = null,
    val aimLatencyMillis: Long? = null,
) : LivePayload

data class StatusEvent(
    val name: String,
    val message: String? = null,
    val baselineElapsedNanos: Long? = null,
    val statusLabel: String? = null,
) : LivePayload

enum class MotionProvider {
    GAME_ROTATION_VECTOR,
    ROTATION_VECTOR,
    GYRO_GRAVITY,
    TILT_FALLBACK,
    UNAVAILABLE;

    val wireName: String
        get() = when (this) {
            GAME_ROTATION_VECTOR -> "game_rotation_vector"
            ROTATION_VECTOR -> "rotation_vector"
            GYRO_GRAVITY -> "gyro_gravity"
            TILT_FALLBACK -> "tilt_fallback"
            UNAVAILABLE -> "unavailable"
        }
}
