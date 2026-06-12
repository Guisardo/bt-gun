package com.btgun.host.profile

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class SmoothingResult(
    val x: Float,
    val y: Float,
    val modeLabel: String,
    val estimatedFilterLagMillis: Int,
    val adaptiveFallback: Boolean,
)

class AdaptiveAimSmoother {
    private var state: FilterState? = null

    fun smooth(
        x: Float,
        y: Float,
        mode: SmoothingMode,
        sampleElapsedNanos: Long,
        aimLatencyMillis: Long?,
    ): SmoothingResult {
        val targetX = x.finiteClamped()
        val targetY = y.finiteClamped()

        if (mode == SmoothingMode.OFF) {
            state = FilterState(targetX, targetY, targetX, targetY, sampleElapsedNanos)
            return SmoothingResult(
                x = targetX,
                y = targetY,
                modeLabel = SmoothingMode.OFF.id,
                estimatedFilterLagMillis = 0,
                adaptiveFallback = false,
            )
        }

        val previous = state
        if (previous == null) {
            state = FilterState(targetX, targetY, targetX, targetY, sampleElapsedNanos)
            return SmoothingResult(
                x = targetX,
                y = targetY,
                modeLabel = mode.id,
                estimatedFilterLagMillis = 0,
                adaptiveFallback = false,
            )
        }

        val elapsedNanos = sampleElapsedNanos - previous.elapsedNanos
        if (elapsedNanos <= 0L) {
            return SmoothingResult(
                x = previous.filteredX,
                y = previous.filteredY,
                modeLabel = mode.id,
                estimatedFilterLagMillis = 0,
                adaptiveFallback = false,
            )
        }

        val selectedTauMillis = when (mode) {
            SmoothingMode.OFF -> 0
            SmoothingMode.LOW -> LOW_TAU_MILLIS
            SmoothingMode.BALANCED -> BALANCED_TAU_MILLIS
            SmoothingMode.HIGH -> HIGH_TAU_MILLIS
            SmoothingMode.ADAPTIVE -> adaptiveTauMillis(previous, targetX, targetY)
        }
        val selectedLag = estimateLagMillis(selectedTauMillis, elapsedNanos)
        val shouldFallback = mode == SmoothingMode.ADAPTIVE &&
            (selectedLag > MAX_ADDED_FILTER_LAG_MS || hasTightLatencyHeadroom(aimLatencyMillis))
        val tauMillis = if (shouldFallback) LOW_TAU_MILLIS else selectedTauMillis
        val alpha = elapsedNanos.alphaFor(tauMillis)
        val filteredX = lerp(previous.filteredX, targetX, alpha).finiteClamped()
        val filteredY = lerp(previous.filteredY, targetY, alpha).finiteClamped()

        state = FilterState(filteredX, filteredY, targetX, targetY, sampleElapsedNanos)
        return SmoothingResult(
            x = filteredX,
            y = filteredY,
            modeLabel = if (shouldFallback) SmoothingMode.LOW.id else mode.id,
            estimatedFilterLagMillis = estimateLagMillis(tauMillis, elapsedNanos)
                .coerceAtMost(if (shouldFallback) MAX_ADDED_FILTER_LAG_MS else Int.MAX_VALUE),
            adaptiveFallback = shouldFallback,
        )
    }

    fun reset() {
        state = null
    }

    private fun adaptiveTauMillis(previous: FilterState, targetX: Float, targetY: Float): Int {
        val delta = max(abs(targetX - previous.rawX), abs(targetY - previous.rawY))
        return when {
            delta >= FAST_MOTION_DELTA -> FAST_ADAPTIVE_TAU_MILLIS
            delta <= JITTER_MOTION_DELTA -> JITTER_ADAPTIVE_TAU_MILLIS
            else -> LOW_TAU_MILLIS
        }
    }

    private fun hasTightLatencyHeadroom(aimLatencyMillis: Long?): Boolean =
        aimLatencyMillis != null &&
            LATENCY_TARGET_MILLIS - aimLatencyMillis < MIN_LATENCY_HEADROOM_MILLIS

    private fun estimateLagMillis(tauMillis: Int, elapsedNanos: Long): Int {
        if (tauMillis <= 0) return 0
        val elapsedMillis = elapsedNanos / NANOS_PER_MILLI_FLOAT
        val alpha = elapsedMillis / (tauMillis + elapsedMillis)
        return (tauMillis * (1f - alpha)).roundToInt().coerceAtLeast(0)
    }

    private fun Long.alphaFor(tauMillis: Int): Float {
        if (tauMillis <= 0) return 1f
        val elapsedMillis = this / NANOS_PER_MILLI_FLOAT
        return elapsedMillis / (tauMillis + elapsedMillis)
    }

    private fun lerp(start: Float, end: Float, alpha: Float): Float =
        start + (end - start) * alpha

    private fun Float.finiteClamped(): Float =
        if (isFinite()) coerceIn(-1.0f, 1.0f) else 0.0f

    private data class FilterState(
        val filteredX: Float,
        val filteredY: Float,
        val rawX: Float,
        val rawY: Float,
        val elapsedNanos: Long,
    )

    companion object {
        const val LOW_TAU_MILLIS: Int = 12
        const val BALANCED_TAU_MILLIS: Int = 24
        const val HIGH_TAU_MILLIS: Int = 40
        const val MAX_ADDED_FILTER_LAG_MS: Int = 12

        private const val FAST_ADAPTIVE_TAU_MILLIS: Int = 8
        private const val JITTER_ADAPTIVE_TAU_MILLIS: Int = 18
        private const val FAST_MOTION_DELTA: Float = 0.25f
        private const val JITTER_MOTION_DELTA: Float = 0.05f
        private const val LATENCY_TARGET_MILLIS: Long = 50
        private const val MIN_LATENCY_HEADROOM_MILLIS: Long = 20
        private const val NANOS_PER_MILLI_FLOAT: Float = 1_000_000f
    }
}
