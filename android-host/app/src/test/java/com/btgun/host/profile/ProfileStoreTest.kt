package com.btgun.host.profile

fun main() {
    defaultDocumentContainsImmutableDefaultVisualizer()
}

private fun defaultDocumentContainsImmutableDefaultVisualizer() {
    val document = ProfileDocument.defaults()
    val profile = document.profiles.single()

    expectEquals("schema version", 1, document.schemaVersion)
    expectEquals("active profile", DEFAULT_VISUALIZER_PROFILE_ID, document.activeProfileId)
    expectEquals("profile id", DEFAULT_VISUALIZER_PROFILE_ID, profile.profileId)
    expectEquals("profile name", "Default Visualizer", profile.displayName)
    expectEquals("revision", 1L, profile.revision)
    expectEquals("built in", true, profile.builtIn)
    expectEquals("active profile object", profile, document.activeProfile())

    expectEquals(
        "default aim",
        AimMappingSettings(
            sensitivity = 1.0f,
            invertX = false,
            invertY = false,
            deadZone = 0.03f,
            smoothing = SmoothingMode.LOW,
        ),
        profile.aim,
    )
    expectEquals(
        "provider overrides",
        mapOf(
            AimProviderKey.CALIBRATED_FUSED_ROTATION to ProviderAimOverrides(),
            AimProviderKey.GYRO_RAW_AIM to ProviderAimOverrides(),
            AimProviderKey.TILT_FALLBACK to ProviderAimOverrides(),
        ),
        profile.providerOverrides,
    )
    expectEquals(
        "button mapping",
        mapOf(
            PhysicalButton.TRIGGER to VirtualButton.TRIGGER,
            PhysicalButton.RELOAD to VirtualButton.RELOAD,
            PhysicalButton.BUTTON_X to VirtualButton.BUTTON_X,
            PhysicalButton.BUTTON_Y to VirtualButton.BUTTON_Y,
            PhysicalButton.BUTTON_A to VirtualButton.BUTTON_A,
            PhysicalButton.BUTTON_B to VirtualButton.BUTTON_B,
        ),
        profile.buttonMapping,
    )
    expectEquals("recenter", PhysicalButton.RELOAD, profile.recenterPhysicalControl)
    expectEquals("raw debug off", false, profile.rawDebugEnabled)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
