package com.btgun.host.profile

fun main() {
    defaultDocumentContainsImmutableDefaultVisualizer()
    emptyStoreLoadsDefaultDocument()
    malformedJsonLoadsDefaultAndReportsRejected()
    storePersistsDuplicateRenameEditSelectDeleteAndReset()
    storeRejectsBuiltInMutationAndInvalidProfiles()
    storeMigratesActiveProfileCalibration()
}

private fun defaultDocumentContainsImmutableDefaultVisualizer() {
    val document = ProfileDocument.defaults()
    val profile = document.profiles.single()

    expectEquals("schema version", 2, document.schemaVersion)
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
            PhysicalButton.TRIGGER to VirtualButton.R2,
            PhysicalButton.RELOAD to VirtualButton.L2,
            PhysicalButton.BUTTON_X to VirtualButton.B3,
            PhysicalButton.BUTTON_Y to VirtualButton.B4,
            PhysicalButton.BUTTON_A to VirtualButton.B1,
            PhysicalButton.BUTTON_B to VirtualButton.B2,
        ),
        profile.buttonMapping,
    )
    expectEquals("recenter", PhysicalButton.RELOAD, profile.recenterPhysicalControl)
    expectEquals(
        "soft controls",
        mapOf(
            SoftControl.BACK to VirtualButton.S1,
            SoftControl.HOME to VirtualButton.A1,
            SoftControl.SELECT to VirtualButton.S2,
        ),
        profile.softControlMapping,
    )
    expectEquals("profile calibration empty", null, profile.aimCalibration)
    expectEquals("raw debug off", false, profile.rawDebugEnabled)
}

private fun emptyStoreLoadsDefaultDocument() {
    val store = ProfileStore(InMemoryProfilePreferences(), idFactory = deterministicIds(), nowEpochMillis = { 100L })

    val result = store.load()

    expectTrue("defaulted result", result is ProfileStoreLoadResult.Defaulted)
    expectEquals("empty default document", ProfileDocument.defaults(), result.document)
}

private fun malformedJsonLoadsDefaultAndReportsRejected() {
    val preferences = InMemoryProfilePreferences("{not-json")
    val store = ProfileStore(preferences, idFactory = deterministicIds(), nowEpochMillis = { 100L })

    val result = store.load()

    expectTrue("malformed rejected result", result is ProfileStoreLoadResult.Rejected)
    expectEquals("malformed default document", ProfileDocument.defaults(), result.document)
    expectEquals("malformed raw preserved", "{not-json", preferences.rawValue())
}

private fun storePersistsDuplicateRenameEditSelectDeleteAndReset() {
    var now = 100L
    val preferences = InMemoryProfilePreferences()
    val store = ProfileStore(preferences, idFactory = deterministicIds(), nowEpochMillis = { now })

    val duplicated = store.duplicateProfile(DEFAULT_VISUALIZER_PROFILE_ID).saved().document
    val copy = duplicated.profiles.first { profile -> !profile.builtIn }
    expectEquals("copy id", "profile_1", copy.profileId)
    expectEquals("copy name", "Default Visualizer Copy", copy.displayName)
    expectEquals("copy builtIn", false, copy.builtIn)
    expectEquals("copy revision", 1L, copy.revision)
    expectEquals("duplicate document revision", 2L, duplicated.documentRevision)

    now = 200L
    val renamed = store.renameProfile(copy.profileId, "Arcade Aim").saved().document
    val renamedCopy = renamed.profile(copy.profileId)
    expectEquals("renamed copy", "Arcade Aim", renamedCopy.displayName)
    expectEquals("renamed profile revision", 2L, renamedCopy.revision)
    expectEquals("renamed document revision", 3L, renamed.documentRevision)

    val edited = store.saveProfile(
        renamedCopy.copy(
            aim = renamedCopy.aim.copy(sensitivity = 1.25f),
            recenterPhysicalControl = PhysicalButton.BUTTON_A,
        ),
    ).saved().document
    val editedCopy = edited.profile(copy.profileId)
    expectEquals("edited sensitivity", 1.25f, editedCopy.aim.sensitivity)
    expectEquals("edited recenter", PhysicalButton.BUTTON_A, editedCopy.recenterPhysicalControl)
    expectEquals("edited profile revision", 3L, editedCopy.revision)
    expectEquals("edited document revision", 4L, edited.documentRevision)

    val selected = store.selectProfile(copy.profileId).saved().document
    expectEquals("selected profile", copy.profileId, selected.activeProfileId)
    expectEquals("selected document revision", 5L, selected.documentRevision)
    expectEquals("select does not edit profile revision", 3L, selected.profile(copy.profileId).revision)

    val deleted = store.deleteProfile(copy.profileId).saved().document
    expectEquals("delete fallback active", DEFAULT_VISUALIZER_PROFILE_ID, deleted.activeProfileId)
    expectEquals("delete removes user profile", listOf(DEFAULT_VISUALIZER_PROFILE_ID), deleted.profiles.map { it.profileId })
    expectEquals("delete document revision", 6L, deleted.documentRevision)

    val reloaded = store.load()
    expectTrue("persisted load", reloaded is ProfileStoreLoadResult.Loaded)
    expectEquals("persisted deleted document", deleted, reloaded.document)

    val reset = store.reset().saved().document
    expectEquals("reset defaults", ProfileDocument.defaults(), reset)
    expectEquals("reset saved JSON", reset, store.load().document)
}

private fun storeRejectsBuiltInMutationAndInvalidProfiles() {
    val store = ProfileStore(InMemoryProfilePreferences(), idFactory = deterministicIds(), nowEpochMillis = { 100L })
    val builtIn = ProfileDocument.defaults().activeProfile()

    expectRejected("edit built-in", store.saveProfile(builtIn.copy(displayName = "Renamed Default")))
    expectRejected("delete built-in", store.deleteProfile(DEFAULT_VISUALIZER_PROFILE_ID))

    val copy = store.duplicateProfile(DEFAULT_VISUALIZER_PROFILE_ID).saved().document.profiles.first { !it.builtIn }
    val rejected = store.saveProfile(copy.copy(displayName = " ")).rejected()
    expectEquals("invalid label", listOf("Name required"), rejected.errors.map { it.label })
}

private fun storeMigratesActiveProfileCalibration() {
    val store = ProfileStore(InMemoryProfilePreferences(), idFactory = deterministicIds(), nowEpochMillis = { 100L })
    val copy = store.duplicateProfile(DEFAULT_VISUALIZER_PROFILE_ID).saved().document.profiles.first { !it.builtIn }
    store.selectProfile(copy.profileId).saved()

    val migrated = store.migrateActiveProfileCalibration("v1|provider|100|0,0|10,0|0,-10|10,-10").saved().document
    val active = migrated.activeProfile()

    expectEquals("migrated active", copy.profileId, active.profileId)
    expectEquals("migrated calibration", "v1|provider|100|0,0|10,0|0,-10|10,-10", active.aimCalibration)
    expectEquals("migrated revision", 2L, active.revision)

    val duplicate = store.migrateActiveProfileCalibration("new-value").rejected()
    expectEquals("skip duplicate migration", "calibration_already_present", duplicate.reason)
}

private fun ProfileDocument.profile(profileId: String): BtGunProfile =
    profiles.first { profile -> profile.profileId == profileId }

private fun SaveProfileResult.saved(): SaveProfileResult.Saved =
    this as? SaveProfileResult.Saved ?: throw AssertionError("expected saved but was <$this>")

private fun SaveProfileResult.rejected(): SaveProfileResult.Rejected =
    this as? SaveProfileResult.Rejected ?: throw AssertionError("expected rejected but was <$this>")

private fun expectRejected(label: String, result: SaveProfileResult) {
    if (result !is SaveProfileResult.Rejected) {
        throw AssertionError("$label expected rejected but was <$result>")
    }
}

private class InMemoryProfilePreferences(initialValue: String? = null) : ProfilePreferences {
    private var value: String? = initialValue

    override fun loadProfiles(): String? = value

    override fun saveProfiles(value: String) {
        this.value = value
    }

    fun rawValue(): String = value.orEmpty()
}

private fun deterministicIds(): () -> String {
    var next = 1
    return { "profile_${next++}" }
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
