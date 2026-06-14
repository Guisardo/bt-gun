package com.btgun.host.profile

fun main() {
    defaultVisualizerIsValid()
    virtualOutputsUseJoypadGamepadDestinationOrder()
    validationReturnsUiSpecLabels()
    validationBlocksUnsupportedAxisMappings()
    validationBlocksInvalidAimSettings()
}

private fun defaultVisualizerIsValid() {
    expectEquals("default errors", emptyList<String>(), ProfileValidator.validate(defaultProfile()).labels())
}

private fun virtualOutputsUseJoypadGamepadDestinationOrder() {
    expectEquals(
        "destination labels",
        listOf(
            "B1 - south face (A / Cross)",
            "B2 - east face (B / Circle)",
            "B3 - west face (X / Square)",
            "B4 - north face (Y / Triangle)",
            "L1 - left shoulder",
            "R1 - right shoulder",
            "L2 - left trigger",
            "R2 - right trigger",
            "S1 - back / select",
            "S2 - start",
            "L3 - left stick click",
            "R3 - right stick click",
            "DU - d-pad up",
            "DD - d-pad down",
            "DL - d-pad left",
            "DR - d-pad right",
            "A1 - guide / home",
            "A2 - capture / touchpad",
            "A3 - mute",
            "A4 - auxiliary 4",
            "L4 - left paddle",
            "R4 - right paddle",
        ),
        VirtualButton.destinationOptions.map { output -> output.destinationLabel },
    )
    expectEquals("legacy trigger decodes", VirtualButton.R2, VirtualButton.fromDestination("trigger"))
    expectEquals("legacy reload decodes", VirtualButton.L2, VirtualButton.fromDestination("reload"))
    expectEquals("destination label decodes", VirtualButton.B1, VirtualButton.fromDestination("B1 - south face (A / Cross)"))
    expectEquals("short label decodes", VirtualButton.L1, VirtualButton.fromDestination("L1"))
    expectEquals("function buttons excluded", false, VirtualButton.destinationOptions.any { output -> output.id == "jp_button_f1" })
}

private fun validationReturnsUiSpecLabels() {
    expectLabels(
        "blank name",
        listOf("Name required"),
        defaultProfile().copy(displayName = "  "),
    )
    expectContains(
        "missing physical mapping",
        ProfileValidator.validate(
            defaultProfile().copy(buttonMapping = defaultProfile().buttonMapping - PhysicalButton.TRIGGER),
        ).labels(),
        "Missing button mapping",
    )
    expectContains(
        "duplicate output",
        ProfileValidator.validate(
            defaultProfile().copy(
                buttonMapping = defaultProfile().buttonMapping + (PhysicalButton.RELOAD to VirtualButton.R2),
            ),
        ).labels(),
        "Duplicate output",
    )
    expectLabels(
        "missing recenter",
        listOf("Recenter required"),
        defaultProfile().copy(recenterPhysicalControl = null),
    )
}

private fun validationBlocksUnsupportedAxisMappings() {
    expectLabels(
        "unsupported mapping",
        listOf("Unsupported axis mapping"),
        defaultProfile().copy(unsupportedMappings = listOf("stickX->trigger")),
    )
}

private fun validationBlocksInvalidAimSettings() {
    listOf(
        defaultProfile().copy(aim = defaultProfile().aim.copy(sensitivity = 0f)),
        defaultProfile().copy(aim = defaultProfile().aim.copy(deadZone = 0.6f)),
        defaultProfile().copy(aim = defaultProfile().aim.copy(sensitivity = Float.NaN)),
        defaultProfile().copy(
            providerOverrides = mapOf(
                AimProviderKey.CALIBRATED_FUSED_ROTATION to ProviderAimOverrides(
                    useSharedSettings = false,
                    settings = defaultProfile().aim.copy(deadZone = Float.POSITIVE_INFINITY),
                ),
            ),
        ),
    ).forEachIndexed { index, profile ->
        expectContains("invalid aim $index", ProfileValidator.validate(profile).labels(), "Invalid aim settings")
    }
}

private fun defaultProfile(): BtGunProfile =
    BtGunProfile.defaultVisualizer()

private fun List<ProfileValidationError>.labels(): List<String> =
    map { error -> error.label }

private fun expectLabels(label: String, expected: List<String>, profile: BtGunProfile) {
    expectEquals(label, expected, ProfileValidator.validate(profile).labels())
}

private fun expectContains(label: String, actual: List<String>, expected: String) {
    if (expected !in actual) {
        throw AssertionError("$label expected <$actual> to contain <$expected>")
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
