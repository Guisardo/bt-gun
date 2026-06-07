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
                capability = "Phone vibration available",
                lastLocalTest = "not tested",
            )

        fun unavailable(reason: String = "device reports no vibrator"): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "unavailable",
                capability = "Phone vibration unavailable",
                lastLocalTest = "unavailable | $reason",
            )

        fun started(durationMs: Long): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "started",
                capability = "Phone vibration available",
                lastLocalTest = "started | local phone vibration ${durationMs}ms",
            )

        fun permissionBlocked(): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "permission_blocked",
                capability = "Phone vibration available",
                lastLocalTest = "permission_blocked | local phone vibration",
            )

        fun failed(error: String): PhoneHapticStatus =
            PhoneHapticStatus(
                code = "failed",
                capability = "Phone vibration available",
                lastLocalTest = "failed | $error",
            )
    }
}

class PhoneHaptics(
    context: Context,
) {
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

    companion object {
        const val LOCAL_TEST_DURATION_MS: Long = 1_000L
    }
}
