package com.btgun.host.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

data class PhoneHapticStatus(
    val code: String,
    val capability: String,
    val lastLocalTest: String,
) {
    companion object {
        fun available(): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "available",
                capability = "Phone haptic available",
                lastLocalTest = "not tested",
            )

        fun unavailable(reason: String = "device reports no vibrator"): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "unavailable",
                capability = "Phone haptic unavailable",
                lastLocalTest = "unavailable | $reason",
            )

        fun started(durationMs: Long): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "started",
                capability = "Phone haptic available",
                lastLocalTest = "started | local haptic ${durationMs}ms",
            )

        fun permissionBlocked(): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "permission_blocked",
                capability = "Phone haptic available",
                lastLocalTest = "permission_blocked | local haptic",
            )

        fun failed(error: String): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "failed",
                capability = "Phone haptic available",
                lastLocalTest = "failed | $error",
            )
    }
}

class PhoneHaptics(
    context: Context,
) : PhoneHapticActuator {
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun currentStatus(): PhoneHapticStatus {
        val localVibrator = vibrator ?: return PhoneHapticStatus.unavailable("missing vibrator service")
        return if (localVibrator.hasVibrator()) {
            PhoneHapticStatus.available()
        } else {
            PhoneHapticStatus.unavailable()
        }
    }

    fun test(durationMs: Long = LOCAL_TEST_DURATION_MS): PhoneHapticStatus {
        val localVibrator = vibrator ?: return PhoneHapticStatus.unavailable("missing vibrator service")
        if (!localVibrator.hasVibrator()) {
            return PhoneHapticStatus.unavailable()
        }

        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                localVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                localVibrator.vibrate(durationMs)
            }
            PhoneHapticStatus.started(durationMs)
        } catch (_: SecurityException) {
            PhoneHapticStatus.permissionBlocked()
        } catch (error: RuntimeException) {
            PhoneHapticStatus.failed(error.javaClass.simpleName)
        }
    }

    override fun pulse(durationMs: Long, strength: Double): PhoneHapticStartResult {
        val localVibrator = vibrator ?: return PhoneHapticStartResult.Unsupported
        if (!localVibrator.hasVibrator()) {
            return PhoneHapticStartResult.Unsupported
        }

        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                localVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, strength.toAmplitude()),
                )
            } else {
                @Suppress("DEPRECATION")
                localVibrator.vibrate(durationMs)
            }
            PhoneHapticStartResult.Started
        } catch (_: SecurityException) {
            PhoneHapticStartResult.PermissionBlocked
        } catch (error: RuntimeException) {
            PhoneHapticStartResult.Failed(error.javaClass.simpleName)
        }
    }

    override fun patternTimeline(timeline: List<HapticTimelinePulse>): PhoneHapticStartResult {
        val localVibrator = vibrator ?: return PhoneHapticStartResult.Unsupported
        if (!localVibrator.hasVibrator() || Build.VERSION.SDK_INT < 26) {
            return PhoneHapticStartResult.Unsupported
        }
        val sorted = timeline.sortedBy { it.atMs }
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        var cursor = 0L
        sorted.forEach { pulse ->
            val gap = (pulse.atMs - cursor).coerceAtLeast(0L)
            if (gap > 0L) {
                timings += gap
                amplitudes += 0
            }
            timings += pulse.durationMs
            amplitudes += pulse.strength.toWaveformAmplitude()
            cursor = (pulse.atMs + pulse.durationMs).coerceAtLeast(cursor)
        }
        return try {
            localVibrator.vibrate(
                VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1),
            )
            PhoneHapticStartResult.Started
        } catch (_: SecurityException) {
            PhoneHapticStartResult.PermissionBlocked
        } catch (error: RuntimeException) {
            PhoneHapticStartResult.Failed(error.javaClass.simpleName)
        }
    }

    override fun cancel(): HapticResultStatus {
        val localVibrator = vibrator ?: return HapticResultStatus.UNSUPPORTED
        return try {
            localVibrator.cancel()
            HapticResultStatus.CANCELLED
        } catch (_: SecurityException) {
            HapticResultStatus.PERMISSION_BLOCKED
        } catch (_: RuntimeException) {
            HapticResultStatus.FAILED
        }
    }

    companion object {
        const val LOCAL_TEST_DURATION_MS: Long = 1_000L
    }
}

private fun Double.toAmplitude(): Int =
    (this.coerceIn(0.0, 1.0) * 255.0)
        .toInt()
        .coerceIn(1, 255)

private fun Double.toWaveformAmplitude(): Int =
    (this.coerceIn(0.0, 1.0) * 255.0)
        .toInt()
        .coerceIn(0, 255)
