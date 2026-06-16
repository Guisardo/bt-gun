package com.btgun.host.profile

enum class ProfileValidationError(val label: String) {
    NAME_REQUIRED("Name required"),
    MISSING_BUTTON_MAPPING("Missing button mapping"),
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

        if (PhysicalButton.defaultOrder.any { physical -> physical !in profile.buttonMapping }) {
            errors += ProfileValidationError.MISSING_BUTTON_MAPPING
        }
        if (SoftControl.defaultOrder.any { soft -> soft !in profile.softControlMapping }) {
            errors += ProfileValidationError.MISSING_BUTTON_MAPPING
        }
        val mappedOutputs = profile.buttonMapping.values + profile.softControlMapping.values
        if (hasDuplicateOutput(mappedOutputs)) {
            errors += ProfileValidationError.DUPLICATE_OUTPUT
        }

        if (!profile.aim.isValid() || profile.providerOverrides.values.any { override -> !override.isValid() }) {
            errors += ProfileValidationError.INVALID_AIM_SETTINGS
        }

        return errors.distinct()
    }

    private fun hasDuplicateOutput(outputs: Collection<VirtualButton>): Boolean {
        val seen = mutableSetOf<VirtualButton>()
        outputs.forEach { output ->
            if (!seen.add(output)) {
                return true
            }
        }
        return false
    }

    private fun ProviderAimOverrides.isValid(): Boolean =
        useSharedSettings || settings.isValid()

    private fun AimMappingSettings.isValid(): Boolean =
        sensitivity.isFinite() &&
            sensitivity > 0f &&
            deadZone.isFinite() &&
            deadZone in 0.0f..0.5f
}
