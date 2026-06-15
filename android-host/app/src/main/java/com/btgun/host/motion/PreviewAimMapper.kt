package com.btgun.host.motion

import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample

data class AimBaseline(
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val elapsedNanos: Long,
)

data class PreviewAim(
    val x: Float,
    val y: Float,
    val padEnabled: Boolean,
    val statusLabel: String,
    val baselineElapsedNanos: Long,
    val rawX: Float = x,
    val rawY: Float = y,
    val calibrated: Boolean = false,
    val latencyMillis: Long? = null,
)

class PreviewAimMapper(
    private val baseline: AimBaseline,
) {
    fun map(sample: LiveEnvelope<MotionSample>): PreviewAim {
        val motion = sample.payload
        if (motion.provider == MotionProvider.UNAVAILABLE) {
            return PreviewAim(
                x = 0f,
                y = 0f,
                padEnabled = false,
                statusLabel = "Motion unavailable",
                baselineElapsedNanos = baseline.elapsedNanos,
            )
        }

        val fallbackRaw = RawAimPoint(
            xDegrees = shortestAngleDelta(motion.yaw, baseline.yaw),
            yDegrees = shortestAngleDelta(motion.pitch, baseline.pitch),
        )
        val fallback = fallbackAim(fallbackRaw)
        return PreviewAim(
            x = motion.aimX ?: fallback.x,
            y = motion.aimY ?: fallback.y,
            padEnabled = true,
            statusLabel = if (motion.aimCalibrated) "Calibrated aim" else "Uncalibrated preview",
            baselineElapsedNanos = baseline.elapsedNanos,
            rawX = motion.rawAimX ?: fallbackRaw.xDegrees,
            rawY = motion.rawAimY ?: fallbackRaw.yDegrees,
            calibrated = motion.aimCalibrated,
            latencyMillis = motion.aimLatencyMillis,
        )
    }

    private fun shortestAngleDelta(current: Float, baseline: Float): Float {
        var delta = (current - baseline) % 360f
        if (delta > 180f) {
            delta -= 360f
        } else if (delta < -180f) {
            delta += 360f
        }
        return delta
    }
}
