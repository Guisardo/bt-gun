package com.btgun.host.motion

import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import kotlin.math.max
import kotlin.math.min

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

        return PreviewAim(
            x = clampUnit((motion.yaw - baseline.yaw) / PREVIEW_DEGREES_TO_EDGE),
            y = clampUnit((motion.pitch - baseline.pitch) / PREVIEW_DEGREES_TO_EDGE),
            padEnabled = true,
            statusLabel = "Preview calibration",
            baselineElapsedNanos = baseline.elapsedNanos,
        )
    }

    private fun clampUnit(value: Float): Float =
        max(-1f, min(1f, value))

    private companion object {
        const val PREVIEW_DEGREES_TO_EDGE = 45f
    }
}
