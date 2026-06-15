package com.btgun.host.profile

import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import kotlin.math.abs

data class MappedAimStatus(
    val aimSource: String,
    val providerName: String,
    val smoothingMode: String,
    val estimatedFilterLagMillis: Int,
    val adaptiveFallback: Boolean,
)

data class MappedControllerState(
    val aimAxisX: Float,
    val aimAxisY: Float,
    val aimStatus: MappedAimStatus,
    val pressedVirtualControls: Set<String>,
    val stickAxisX: Float,
    val stickAxisY: Float,
    val recenterPhysicalControl: String?,
)

class ProfileMapper(
    private val smoother: AdaptiveAimSmoother = AdaptiveAimSmoother(),
) {
    fun map(
        profile: BtGunProfile,
        gunInputState: GunInputState,
        motionSample: MotionSample?,
        nowElapsedNanos: Long,
    ): MappedControllerState {
        val selected = selectAim(profile, motionSample)
        if (selected.source == AIM_SOURCE_CENTER) {
            smoother.reset()
            return MappedControllerState(
                aimAxisX = 0f,
                aimAxisY = 0f,
                aimStatus = MappedAimStatus(
                    aimSource = AIM_SOURCE_CENTER,
                    providerName = motionSample?.providerName ?: "none",
                    smoothingMode = SmoothingMode.OFF.id,
                    estimatedFilterLagMillis = 0,
                    adaptiveFallback = false,
                ),
                pressedVirtualControls = profile.mapPressedVirtualControls(gunInputState),
                stickAxisX = gunInputState.stickAxisX.finiteAxis(),
                stickAxisY = gunInputState.stickAxisY.finiteAxis(),
                recenterPhysicalControl = profile.recenterPhysicalControl?.id,
            )
        }

        val adjustedX = selected.x
            .applySensitivity(selected.settings.sensitivity)
            .applyInversion(selected.settings.invertX)
            .applyDeadZone(selected.settings.deadZone)
            .finiteClamped()
        val adjustedY = selected.y
            .applySensitivity(selected.settings.sensitivity)
            .applyInversion(selected.settings.invertY)
            .applyDeadZone(selected.settings.deadZone)
            .finiteClamped()
        val smoothed = smoother.smooth(
            x = adjustedX,
            y = adjustedY,
            mode = selected.settings.smoothing,
            sampleElapsedNanos = nowElapsedNanos,
            aimLatencyMillis = motionSample?.aimLatencyMillis,
        )

        return MappedControllerState(
            aimAxisX = smoothed.x,
            aimAxisY = smoothed.y,
            aimStatus = MappedAimStatus(
                aimSource = selected.source,
                providerName = motionSample?.providerName ?: "none",
                smoothingMode = smoothed.modeLabel,
                estimatedFilterLagMillis = smoothed.estimatedFilterLagMillis,
                adaptiveFallback = smoothed.adaptiveFallback,
            ),
            pressedVirtualControls = profile.mapPressedVirtualControls(gunInputState),
            stickAxisX = gunInputState.stickAxisX.finiteAxis(),
            stickAxisY = gunInputState.stickAxisY.finiteAxis(),
            recenterPhysicalControl = profile.recenterPhysicalControl?.id,
        )
    }

    fun isRecenterPressed(profile: BtGunProfile, gunInputState: GunInputState): Boolean {
        val recenter = profile.recenterPhysicalControl ?: return false
        return recenter.id in gunInputState.pressedControls
    }

    private fun selectAim(profile: BtGunProfile, motionSample: MotionSample?): SelectedMappedAim {
        if (motionSample == null || motionSample.provider == MotionProvider.UNAVAILABLE) {
            return SelectedMappedAim.center(profile.aim)
        }

        val providerKey = motionSample.provider.toAimProviderKey()
        val settings = profile.settingsFor(providerKey)
        return when (providerKey) {
            AimProviderKey.CALIBRATED_FUSED_ROTATION -> motionSample.selectCalibratedAim(settings)
            AimProviderKey.GYRO_RAW_AIM -> motionSample.selectGyroAim(
                settings = settings,
                preferRaw = profile.usesOverride(providerKey),
            )
            AimProviderKey.TILT_FALLBACK -> motionSample.selectTiltAim(settings)
        }
    }

    private fun BtGunProfile.settingsFor(providerKey: AimProviderKey): AimMappingSettings {
        val override = providerOverrides[providerKey]
        return if (override != null && !override.useSharedSettings) {
            override.settings
        } else {
            aim
        }
    }

    private fun BtGunProfile.usesOverride(providerKey: AimProviderKey): Boolean =
        providerOverrides[providerKey]?.useSharedSettings == false

    private fun MotionProvider.toAimProviderKey(): AimProviderKey =
        when (this) {
            MotionProvider.GAME_ROTATION_VECTOR,
            MotionProvider.ROTATION_VECTOR,
            -> AimProviderKey.CALIBRATED_FUSED_ROTATION
            MotionProvider.GYRO_GRAVITY -> AimProviderKey.GYRO_RAW_AIM
            MotionProvider.TILT_FALLBACK -> AimProviderKey.TILT_FALLBACK
            MotionProvider.UNAVAILABLE -> AimProviderKey.CALIBRATED_FUSED_ROTATION
        }

    private fun MotionSample.selectCalibratedAim(settings: AimMappingSettings): SelectedMappedAim =
        when {
            aimX != null && aimY != null -> SelectedMappedAim(
                x = aimX,
                y = aimY,
                source = if (aimCalibrated) "calibrated" else "normalized",
                settings = settings,
            )
            rawAimX != null && rawAimY != null -> SelectedMappedAim(rawAimX, rawAimY, "raw", settings)
            else -> SelectedMappedAim.center(settings)
        }

    private fun MotionSample.selectGyroAim(
        settings: AimMappingSettings,
        preferRaw: Boolean,
    ): SelectedMappedAim =
        when {
            preferRaw && rawAimX != null && rawAimY != null -> {
                SelectedMappedAim(rawAimX, rawAimY, "raw", settings)
            }
            aimX != null && aimY != null -> {
                SelectedMappedAim(aimX, aimY, "normalized", settings)
            }
            rawAimX != null && rawAimY != null -> {
                SelectedMappedAim(rawAimX, rawAimY, "raw", settings)
            }
            else -> SelectedMappedAim.center(settings)
        }

    private fun MotionSample.selectTiltAim(settings: AimMappingSettings): SelectedMappedAim =
        when {
            aimX != null && aimY != null -> SelectedMappedAim(aimX, aimY, "tilt_fallback", settings)
            rawAimX != null && rawAimY != null -> SelectedMappedAim(rawAimX, rawAimY, "tilt_fallback", settings)
            else -> SelectedMappedAim.center(settings)
        }

    private fun BtGunProfile.mapPressedVirtualControls(gunInputState: GunInputState): Set<String> =
        gunInputState.pressedControls.mapNotNull { controlId ->
            val physical = PhysicalButton.fromId(controlId)
            val virtual = physical?.let { button -> buttonMapping[button] }
            virtual?.id
        }.toSet()

    private fun Float.applySensitivity(sensitivity: Float): Float =
        finiteClamped() * sensitivity

    private fun Float.applyInversion(invert: Boolean): Float =
        if (invert) -this else this

    private fun Float.applyDeadZone(deadZone: Float): Float =
        if (abs(this) < deadZone) 0f else this

    private fun Float.finiteClamped(): Float =
        if (isFinite()) coerceIn(-1.0f, 1.0f) else 0.0f

    private fun Float.finiteAxis(): Float =
        if (isFinite()) coerceIn(-1.0f, 1.0f) else 0.0f

    private data class SelectedMappedAim(
        val x: Float,
        val y: Float,
        val source: String,
        val settings: AimMappingSettings,
    ) {
        companion object {
            fun center(settings: AimMappingSettings): SelectedMappedAim =
                SelectedMappedAim(0f, 0f, AIM_SOURCE_CENTER, settings)
        }
    }

    companion object {
        private const val AIM_SOURCE_CENTER = "center"
    }
}
