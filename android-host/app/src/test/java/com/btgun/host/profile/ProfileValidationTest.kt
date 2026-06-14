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
        ),
        VirtualButton.requiredOutputs.map { output -> output.destinationLabel },
    )
    expectEquals("legacy id still decodes", VirtualButton.TRIGGER, VirtualButton.fromDestination("trigger"))
    expectEquals("destination label decodes", VirtualButton.BUTTON_A, VirtualButton.fromDestination("B1 - south face (A / Cross)"))
    expectEquals("short label decodes", VirtualButton.RELOAD, VirtualButton.fromDestination("L1"))
}

private fun validationReturnsUiSpecLabels() {
    expectLabels(
        "blank name",
        listOf("Name required"),
        defaultProfile().copy(displayName = "  "),
    )
    VirtualButton.requiredOutputs.forEach { output ->
        val mapping = defaultProfile().buttonMapping.filterValues { mapped -> mapped != output }
        expectContains(
            "missing ${output.id}",
            ProfileValidator.validate(defaultProfile().copy(buttonMapping = mapping)).labels(),
            "Missing ${output.validationName} output",
        )
    }
    expectContains(
        "duplicate output",
        ProfileValidator.validate(
            defaultProfile().copy(
                buttonMapping = defaultProfile().buttonMapping + (PhysicalButton.RELOAD to VirtualButton.TRIGGER),
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
