package com.btgun.host.profile

import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample

fun main() {
    calibratedFusedProvidersUseCalibratedAimAndOverrides()
    gyroRawOverrideUsesRawAim()
    tiltFallbackUsesTiltOverride()
    appliesSensitivityInversionDeadZoneBeforeSmoothing()
    unavailableProviderCentersAim()
    reportsSmoothingStatusAndClampsFiniteOutput()
    remapsPhysicalButtonsToVirtualOutputsOnly()
    stickAxesPassThroughWithoutAxisRemap()
    recenterPhysicalControlDoesNotSuppressVirtualReload()
    softControlsMergeWithPhysicalControls()
}

private fun calibratedFusedProvidersUseCalibratedAimAndOverrides() {
    val profile = defaultProfile().copy(
        providerOverrides = defaultProfile().providerOverrides + (
            AimProviderKey.CALIBRATED_FUSED_ROTATION to ProviderAimOverrides(
                useSharedSettings = false,
                settings = defaultProfile().aim.copy(sensitivity = 2f, smoothing = SmoothingMode.OFF),
            )
        ),
    )

    val mapped = ProfileMapper().map(
        profile = profile,
        gunInputState = GunInputState(),
        motionSample = motion(
            provider = MotionProvider.GAME_ROTATION_VECTOR,
            aimX = 0.25f,
            aimY = -0.125f,
            rawAimX = 0.9f,
            rawAimY = 0.9f,
            aimCalibrated = true,
        ),
        nowElapsedNanos = 1_000_000L,
    )

    expectNear("calibrated x", 0.5f, mapped.aimAxisX)
    expectNear("calibrated y", -0.25f, mapped.aimAxisY)
    expectEquals("calibrated source", "calibrated", mapped.aimStatus.aimSource)
    expectEquals("provider name", "game_rotation_vector", mapped.aimStatus.providerName)
}

private fun gyroRawOverrideUsesRawAim() {
    val profile = defaultProfile().copy(
        providerOverrides = defaultProfile().providerOverrides + (
            AimProviderKey.GYRO_RAW_AIM to ProviderAimOverrides(
                useSharedSettings = false,
                settings = defaultProfile().aim.copy(sensitivity = 1.5f, smoothing = SmoothingMode.OFF),
            )
        ),
    )

    val mapped = ProfileMapper().map(
        profile = profile,
        gunInputState = GunInputState(),
        motionSample = motion(
            provider = MotionProvider.GYRO_GRAVITY,
            aimX = 0.1f,
            aimY = 0.1f,
            rawAimX = -0.4f,
            rawAimY = 0.25f,
        ),
        nowElapsedNanos = 1_000_000L,
    )

    expectNear("raw x", -0.6f, mapped.aimAxisX)
    expectNear("raw y", 0.375f, mapped.aimAxisY)
    expectEquals("raw source", "raw", mapped.aimStatus.aimSource)
}

private fun tiltFallbackUsesTiltOverride() {
    val profile = defaultProfile().copy(
        providerOverrides = defaultProfile().providerOverrides + (
            AimProviderKey.TILT_FALLBACK to ProviderAimOverrides(
                useSharedSettings = false,
                settings = defaultProfile().aim.copy(sensitivity = 2f, smoothing = SmoothingMode.OFF),
            )
        ),
    )

    val mapped = ProfileMapper().map(
        profile = profile,
        gunInputState = GunInputState(),
        motionSample = motion(
            provider = MotionProvider.TILT_FALLBACK,
            aimX = 0.2f,
            aimY = -0.2f,
            rawAimX = 0.8f,
            rawAimY = 0.8f,
        ),
        nowElapsedNanos = 1_000_000L,
    )

    expectNear("tilt x", 0.4f, mapped.aimAxisX)
    expectNear("tilt y", -0.4f, mapped.aimAxisY)
    expectEquals("tilt source", "tilt_fallback", mapped.aimStatus.aimSource)
}

private fun appliesSensitivityInversionDeadZoneBeforeSmoothing() {
    val profile = defaultProfile().copy(
        aim = defaultProfile().aim.copy(
            sensitivity = 2f,
            invertX = true,
            invertY = true,
            deadZone = 0.25f,
            smoothing = SmoothingMode.OFF,
        ),
    )

    val mapped = ProfileMapper().map(
        profile = profile,
        gunInputState = GunInputState(),
        motionSample = motion(
            provider = MotionProvider.ROTATION_VECTOR,
            aimX = 0.25f,
            aimY = 0.1f,
        ),
        nowElapsedNanos = 1_000_000L,
    )

    expectNear("ordered x", -0.5f, mapped.aimAxisX)
    expectNear("ordered y deadzone", 0f, mapped.aimAxisY)
}

private fun unavailableProviderCentersAim() {
    val mapped = ProfileMapper().map(
        profile = defaultProfile(),
        gunInputState = GunInputState(),
        motionSample = motion(provider = MotionProvider.UNAVAILABLE, aimX = 1f, aimY = 1f),
        nowElapsedNanos = 1_000_000L,
    )

    expectNear("center x", 0f, mapped.aimAxisX)
    expectNear("center y", 0f, mapped.aimAxisY)
    expectEquals("center source", "center", mapped.aimStatus.aimSource)
}

private fun reportsSmoothingStatusAndClampsFiniteOutput() {
    val mapper = ProfileMapper()
    val profile = defaultProfile().copy(
        aim = defaultProfile().aim.copy(smoothing = SmoothingMode.BALANCED),
    )
    mapper.map(profile, GunInputState(), motion(provider = MotionProvider.ROTATION_VECTOR, aimX = 0f, aimY = 0f), 0L)

    val mapped = mapper.map(
        profile = profile,
        gunInputState = GunInputState(),
        motionSample = motion(
            provider = MotionProvider.ROTATION_VECTOR,
            aimX = 2f,
            aimY = Float.NaN,
        ),
        nowElapsedNanos = 12_000_000L,
    )

    expectNear("balanced smoothed x", 1f / 3f, mapped.aimAxisX, tolerance = 0.0005f)
    expectEquals("finite y", true, mapped.aimAxisY.isFinite())
    expectEquals("smoothing mode", "balanced", mapped.aimStatus.smoothingMode)
    expectEquals("fallback false", false, mapped.aimStatus.adaptiveFallback)
    expectTrue("lag reported", mapped.aimStatus.estimatedFilterLagMillis > 0)
}

private fun remapsPhysicalButtonsToVirtualOutputsOnly() {
    val profile = defaultProfile().copy(
        buttonMapping = mapOf(
            PhysicalButton.TRIGGER to VirtualButton.B3,
            PhysicalButton.RELOAD to VirtualButton.R2,
            PhysicalButton.BUTTON_X to VirtualButton.B4,
            PhysicalButton.BUTTON_Y to VirtualButton.B1,
            PhysicalButton.BUTTON_A to VirtualButton.L2,
            PhysicalButton.BUTTON_B to VirtualButton.B2,
        ),
    )

    val mapped = ProfileMapper().map(
        profile = profile,
        gunInputState = GunInputState(
            pressedControls = setOf("trigger", "button_a", "unknown", "stick_axis_x"),
        ),
        motionSample = motion(provider = MotionProvider.ROTATION_VECTOR, aimX = 0.1f, aimY = 0.1f),
        nowElapsedNanos = 1_000_000L,
    )

    expectEquals("remapped controls", setOf("jp_button_b3", "jp_button_l2"), mapped.pressedVirtualControls)
    expectEquals("no axis published as button", false, "stick_axis_x" in mapped.pressedVirtualControls)
}

private fun stickAxesPassThroughWithoutAxisRemap() {
    val mapped = ProfileMapper().map(
        profile = defaultProfile(),
        gunInputState = GunInputState(
            pressedControls = setOf("trigger"),
            stickAxisX = 0.45f,
            stickAxisY = -0.75f,
        ),
        motionSample = motion(provider = MotionProvider.ROTATION_VECTOR, aimX = 0.2f, aimY = -0.2f),
        nowElapsedNanos = 1_000_000L,
    )

    expectNear("stick x pass-through", 0.45f, mapped.stickAxisX)
    expectNear("stick y pass-through", -0.75f, mapped.stickAxisY)
    expectNear("aim x still from motion", 0.2f, mapped.aimAxisX)
    expectNear("aim y still from motion", -0.2f, mapped.aimAxisY)
}

private fun recenterPhysicalControlDoesNotSuppressVirtualReload() {
    val profile = defaultProfile().copy(
        recenterPhysicalControl = PhysicalButton.BUTTON_A,
        buttonMapping = defaultProfile().buttonMapping + (PhysicalButton.BUTTON_A to VirtualButton.L2),
    )
    val rawState = GunInputState(pressedControls = setOf("button_a"))
    val mapper = ProfileMapper()

    val mapped = mapper.map(
        profile = profile,
        gunInputState = rawState,
        motionSample = motion(provider = MotionProvider.ROTATION_VECTOR, aimX = 0f, aimY = 0f),
        nowElapsedNanos = 1_000_000L,
    )

    expectEquals("recenter control exposed", "button_a", mapped.recenterPhysicalControl)
    expectEquals("recenter physical pressed", true, mapper.isRecenterPressed(profile, rawState))
    expectEquals("virtual reload preserved", true, "jp_button_l2" in mapped.pressedVirtualControls)
}

private fun softControlsMergeWithPhysicalControls() {
    val profile = defaultProfile().copy(
        softControlMapping = mapOf(
            SoftControl.BACK to VirtualButton.S1,
            SoftControl.HOME to VirtualButton.A1,
            SoftControl.SELECT to VirtualButton.S2,
        ),
    )

    val mapped = ProfileMapper().map(
        profile = profile,
        gunInputState = GunInputState(pressedControls = setOf("trigger")),
        motionSample = motion(provider = MotionProvider.ROTATION_VECTOR, aimX = 0f, aimY = 0f),
        nowElapsedNanos = 1_000_000L,
        softPressedControls = setOf(SoftControl.BACK, SoftControl.SELECT),
    )

    expectEquals(
        "soft plus physical controls",
        setOf("jp_button_r2", "jp_button_s1", "jp_button_s2"),
        mapped.pressedVirtualControls,
    )
}

private fun motion(
    provider: MotionProvider,
    aimX: Float? = null,
    aimY: Float? = null,
    rawAimX: Float? = null,
    rawAimY: Float? = null,
    aimCalibrated: Boolean = false,
): MotionSample =
    MotionSample(
        provider = provider,
        sourceSensorElapsedNanos = 1_000L,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        rawAimX = rawAimX,
        rawAimY = rawAimY,
        aimX = aimX,
        aimY = aimY,
        aimCalibrated = aimCalibrated,
        aimLatencyMillis = 4,
    )

private fun defaultProfile(): BtGunProfile =
    BtGunProfile.defaultVisualizer()

private fun expectNear(label: String, expected: Float, actual: Float, tolerance: Float = 0.0001f) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}
