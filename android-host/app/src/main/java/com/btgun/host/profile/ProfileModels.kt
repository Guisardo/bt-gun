package com.btgun.host.profile

const val DEFAULT_VISUALIZER_PROFILE_ID = "default_visualizer"

enum class PhysicalButton(val id: String, val eventLabel: String) {
    TRIGGER("trigger", "Gun trigger"),
    RELOAD("reload", "Gun reload"),
    BUTTON_X("button_x", "Gun X"),
    BUTTON_Y("button_y", "Gun Y"),
    BUTTON_A("button_a", "Gun A"),
    BUTTON_B("button_b", "Gun B");

    companion object {
        val defaultOrder: List<PhysicalButton> = entries

        fun fromId(id: String): PhysicalButton? =
            entries.firstOrNull { button -> button.id == id }
    }
}

enum class VirtualButton(
    val id: String,
    val validationName: String,
    val destinationLabel: String,
) {
    TRIGGER("trigger", "R1", "R1 - right shoulder"),
    RELOAD("reload", "L1", "L1 - left shoulder"),
    BUTTON_X("button_x", "B3", "B3 - west face (X / Square)"),
    BUTTON_Y("button_y", "B4", "B4 - north face (Y / Triangle)"),
    BUTTON_A("button_a", "B1", "B1 - south face (A / Cross)"),
    BUTTON_B("button_b", "B2", "B2 - east face (B / Circle)");

    companion object {
        val requiredOutputs: List<VirtualButton> = listOf(
            BUTTON_A,
            BUTTON_B,
            BUTTON_X,
            BUTTON_Y,
            RELOAD,
            TRIGGER,
        )

        fun fromId(id: String): VirtualButton? =
            entries.firstOrNull { button -> button.id == id }

        fun fromDestination(value: String): VirtualButton? {
            val normalized = value.trim()
            return entries.firstOrNull { button ->
                button.id == normalized ||
                    button.destinationLabel == normalized ||
                    button.validationName == normalized
            }
        }
    }
}

enum class SmoothingMode(val id: String) {
    OFF("off"),
    LOW("low"),
    BALANCED("balanced"),
    HIGH("high"),
    ADAPTIVE("adaptive");

    companion object {
        fun fromId(id: String): SmoothingMode? =
            entries.firstOrNull { mode -> mode.id == id }
    }
}

enum class AimProviderKey(val id: String) {
    CALIBRATED_FUSED_ROTATION("calibrated_fused_rotation"),
    GYRO_RAW_AIM("gyro_raw_aim"),
    TILT_FALLBACK("tilt_fallback");

    companion object {
        val defaultOrder: List<AimProviderKey> = entries

        fun fromId(id: String): AimProviderKey? =
            entries.firstOrNull { provider -> provider.id == id }
    }
}

data class AimMappingSettings(
    val sensitivity: Float = DEFAULT_SENSITIVITY,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val deadZone: Float = DEFAULT_DEAD_ZONE,
    val smoothing: SmoothingMode = SmoothingMode.LOW,
) {
    companion object {
        const val DEFAULT_SENSITIVITY: Float = 1.0f
        const val DEFAULT_DEAD_ZONE: Float = 0.03f

        fun defaults(): AimMappingSettings = AimMappingSettings()
    }
}

data class ProviderAimOverrides(
    val useSharedSettings: Boolean = true,
    val settings: AimMappingSettings = AimMappingSettings.defaults(),
)

data class BtGunProfile(
    val profileId: String,
    val displayName: String,
    val revision: Long,
    val builtIn: Boolean,
    val aim: AimMappingSettings,
    val providerOverrides: Map<AimProviderKey, ProviderAimOverrides>,
    val buttonMapping: Map<PhysicalButton, VirtualButton>,
    val recenterPhysicalControl: PhysicalButton?,
    val rawDebugEnabled: Boolean = false,
    val unsupportedMappings: List<String> = emptyList(),
) {
    companion object {
        fun defaultVisualizer(): BtGunProfile =
            BtGunProfile(
                profileId = DEFAULT_VISUALIZER_PROFILE_ID,
                displayName = "Default Visualizer",
                revision = 1L,
                builtIn = true,
                aim = AimMappingSettings.defaults(),
                providerOverrides = defaultProviderOverrides(),
                buttonMapping = defaultButtonMapping(),
                recenterPhysicalControl = PhysicalButton.RELOAD,
            )

        fun defaultButtonMapping(): Map<PhysicalButton, VirtualButton> =
            mapOf(
                PhysicalButton.TRIGGER to VirtualButton.TRIGGER,
                PhysicalButton.RELOAD to VirtualButton.RELOAD,
                PhysicalButton.BUTTON_X to VirtualButton.BUTTON_X,
                PhysicalButton.BUTTON_Y to VirtualButton.BUTTON_Y,
                PhysicalButton.BUTTON_A to VirtualButton.BUTTON_A,
                PhysicalButton.BUTTON_B to VirtualButton.BUTTON_B,
            )

        fun defaultProviderOverrides(): Map<AimProviderKey, ProviderAimOverrides> =
            AimProviderKey.defaultOrder.associateWith { ProviderAimOverrides() }
    }
}

data class ProfileDocument(
    val schemaVersion: Int = 1,
    val activeProfileId: String = DEFAULT_VISUALIZER_PROFILE_ID,
    val documentRevision: Long = 1L,
    val profiles: List<BtGunProfile> = listOf(BtGunProfile.defaultVisualizer()),
) {
    fun activeProfile(): BtGunProfile =
        profiles.firstOrNull { profile -> profile.profileId == activeProfileId }
            ?: profiles.first { profile -> profile.profileId == DEFAULT_VISUALIZER_PROFILE_ID }

    companion object {
        fun defaults(): ProfileDocument = ProfileDocument()
    }
}
