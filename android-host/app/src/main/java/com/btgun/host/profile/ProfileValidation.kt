package com.btgun.host.profile

enum class ProfileValidationError(val label: String) {
    NAME_REQUIRED("Name required"),
    MISSING_TRIGGER_OUTPUT("Missing trigger output"),
    MISSING_RELOAD_OUTPUT("Missing reload output"),
    MISSING_X_OUTPUT("Missing X output"),
    MISSING_Y_OUTPUT("Missing Y output"),
    MISSING_A_OUTPUT("Missing A output"),
    MISSING_B_OUTPUT("Missing B output"),
    DUPLICATE_OUTPUT("Duplicate output"),
    RECENTER_REQUIRED("Recenter required"),
    UNSUPPORTED_AXIS_MAPPING("Unsupported axis mapping"),
    INVALID_AIM_SETTINGS("Invalid aim settings"),
}

object ProfileValidator {
    fun validate(profile: BtGunProfile): List<ProfileValidationError> {
        val errors = mutableListOf<ProfileValidationError>()

        if (profile.displayName.isBlank()) {
            errors += ProfileValidationError.NAME_REQUIRED
        }

        if (profile.unsupportedMappings.isNotEmpty()) {
            errors += ProfileValidationError.UNSUPPORTED_AXIS_MAPPING
        }

        if (profile.recenterPhysicalControl == null) {
            errors += ProfileValidationError.RECENTER_REQUIRED
        }

        val mappedOutputs = profile.buttonMapping.values
        VirtualButton.requiredOutputs.forEach { output ->
            if (output !in mappedOutputs) {
                errors += missingOutputError(output)
            }
        }
        if (mappedOutputs.groupingBy { output -> output }.eachCount().any { (_, count) -> count > 1 }) {
            errors += ProfileValidationError.DUPLICATE_OUTPUT
        }

        if (!profile.aim.isValid() || profile.providerOverrides.values.any { override -> !override.isValid() }) {
            errors += ProfileValidationError.INVALID_AIM_SETTINGS
        }

        return errors.distinct()
    }

    private fun missingOutputError(output: VirtualButton): ProfileValidationError =
        when (output) {
            VirtualButton.TRIGGER -> ProfileValidationError.MISSING_TRIGGER_OUTPUT
            VirtualButton.RELOAD -> ProfileValidationError.MISSING_RELOAD_OUTPUT
            VirtualButton.BUTTON_X -> ProfileValidationError.MISSING_X_OUTPUT
            VirtualButton.BUTTON_Y -> ProfileValidationError.MISSING_Y_OUTPUT
            VirtualButton.BUTTON_A -> ProfileValidationError.MISSING_A_OUTPUT
            VirtualButton.BUTTON_B -> ProfileValidationError.MISSING_B_OUTPUT
        }

    private fun ProviderAimOverrides.isValid(): Boolean =
        useSharedSettings || settings.isValid()

    private fun AimMappingSettings.isValid(): Boolean =
        sensitivity.isFinite() &&
            sensitivity > 0f &&
            deadZone.isFinite() &&
            deadZone in 0.0f..0.5f
}
