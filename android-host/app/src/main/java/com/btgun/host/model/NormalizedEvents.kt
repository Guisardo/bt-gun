package com.btgun.host.model

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

data class MotionSample(
    val provider: MotionProvider,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
) : LivePayload

data class StatusEvent(
    val name: String,
    val message: String? = null,
) : LivePayload

enum class MotionProvider {
    GAME_ROTATION_VECTOR,
    ROTATION_VECTOR,
    GYRO_GRAVITY,
    TILT_FALLBACK,
    UNAVAILABLE,
}
