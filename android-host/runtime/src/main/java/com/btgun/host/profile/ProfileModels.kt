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
    val bitIndex: Int,
    private val legacyIds: Set<String> = emptySet(),
) {
    B1("jp_button_b1", "B1", "B1 - south face (A / Cross)", 0, setOf("button_a", "a")),
    B2("jp_button_b2", "B2", "B2 - east face (B / Circle)", 1, setOf("button_b", "b")),
    B3("jp_button_b3", "B3", "B3 - west face (X / Square)", 2, setOf("button_x", "x")),
    B4("jp_button_b4", "B4", "B4 - north face (Y / Triangle)", 3, setOf("button_y", "y")),
    L1("jp_button_l1", "L1", "L1 - left shoulder", 4),
    R1("jp_button_r1", "R1", "R1 - right shoulder", 5),
    L2("jp_button_l2", "L2", "L2 - left trigger", 6, setOf("reload")),
    R2("jp_button_r2", "R2", "R2 - right trigger", 7, setOf("trigger")),
    S1("jp_button_s1", "S1", "S1 - back / select", 8),
    S2("jp_button_s2", "S2", "S2 - start", 9),
    L3("jp_button_l3", "L3", "L3 - left stick click", 10),
    R3("jp_button_r3", "R3", "R3 - right stick click", 11),
    DU("jp_button_du", "DU", "DU - d-pad up", 12),
    DD("jp_button_dd", "DD", "DD - d-pad down", 13),
    DL("jp_button_dl", "DL", "DL - d-pad left", 14),
    DR("jp_button_dr", "DR", "DR - d-pad right", 15),
    A1("jp_button_a1", "A1", "A1 - guide / home", 16),
    A2("jp_button_a2", "A2", "A2 - capture / touchpad", 17),
    A3("jp_button_a3", "A3", "A3 - mute", 18),
    A4("jp_button_a4", "A4", "A4 - auxiliary 4", 19),
    L4("jp_button_l4", "L4", "L4 - left paddle", 20),
    R4("jp_button_r4", "R4", "R4 - right paddle", 21);

    val bitMask: Int
        get() = 1 shl bitIndex

    companion object {
        val destinationOptions: List<VirtualButton> = entries

        fun fromId(id: String): VirtualButton? =
            entries.firstOrNull { button -> button.matches(id) }

        fun fromDestination(value: String): VirtualButton? {
            val normalized = value.trim()
            return entries.firstOrNull { button -> button.matches(normalized) }
        }

        fun bitmaskForControlIds(controlIds: Set<String>): Int {
            var bits = 0
            controlIds.forEach { controlId ->
                bits = bits or (fromId(controlId)?.bitMask ?: 0)
            }
            return bits
        }
    }

    private fun matches(value: String): Boolean =
        id == value || destinationLabel == value || validationName == value || value in legacyIds
}

enum class SoftControl(val id: String, val label: String) {
    BACK("back", "Back"),
    HOME("home", "Home"),
    SELECT("select", "Select");

    companion object {
        val defaultOrder: List<SoftControl> = entries

        fun fromId(id: String): SoftControl? =
            entries.firstOrNull { control -> control.id == id }
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
    val softControlMapping: Map<SoftControl, VirtualButton> = defaultSoftControlMapping(),
    val recenterPhysicalControl: PhysicalButton?,
    val aimCalibration: String? = null,
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
                softControlMapping = defaultSoftControlMapping(),
                recenterPhysicalControl = PhysicalButton.RELOAD,
            )

        fun defaultButtonMapping(): Map<PhysicalButton, VirtualButton> =
            mapOf(
                PhysicalButton.TRIGGER to VirtualButton.R2,
                PhysicalButton.RELOAD to VirtualButton.L2,
                PhysicalButton.BUTTON_X to VirtualButton.B3,
                PhysicalButton.BUTTON_Y to VirtualButton.B4,
                PhysicalButton.BUTTON_A to VirtualButton.B1,
                PhysicalButton.BUTTON_B to VirtualButton.B2,
            )

        fun defaultProviderOverrides(): Map<AimProviderKey, ProviderAimOverrides> =
            AimProviderKey.defaultOrder.associateWith { ProviderAimOverrides() }

        fun defaultSoftControlMapping(): Map<SoftControl, VirtualButton> =
            mapOf(
                SoftControl.BACK to VirtualButton.S1,
                SoftControl.HOME to VirtualButton.A1,
                SoftControl.SELECT to VirtualButton.S2,
            )
    }
}

data class ProfileDocument(
    val schemaVersion: Int = 2,
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
