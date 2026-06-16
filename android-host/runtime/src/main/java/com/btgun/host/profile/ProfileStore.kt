package com.btgun.host.profile

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val PROFILE_STORE_DEFAULT_ID = "default_visualizer"

interface ProfilePreferences {
    fun loadProfiles(): String?
    fun saveProfiles(value: String)
}

sealed interface ProfileStoreLoadResult {
    val document: ProfileDocument

    data class Loaded(override val document: ProfileDocument) : ProfileStoreLoadResult
    data class Defaulted(override val document: ProfileDocument, val reason: String) : ProfileStoreLoadResult
    data class Rejected(override val document: ProfileDocument, val reason: String) : ProfileStoreLoadResult
}

sealed interface SaveProfileResult {
    data class Saved(val document: ProfileDocument) : SaveProfileResult
    data class Rejected(
        val reason: String,
        val errors: List<ProfileValidationError> = emptyList(),
        val document: ProfileDocument,
    ) : SaveProfileResult
}

class ProfileStore {
    private val preferences: ProfilePreferences
    private val idFactory: () -> String
    @Suppress("unused")
    private val nowEpochMillis: () -> Long

    constructor(context: Context) : this(
        SharedPreferencesProfilePreferences(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    constructor(
        preferences: ProfilePreferences,
        idFactory: () -> String = { "profile_${UUID.randomUUID()}" },
        nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    ) {
        this.preferences = preferences
        this.idFactory = idFactory
        this.nowEpochMillis = nowEpochMillis
    }

    fun load(): ProfileStoreLoadResult {
        val raw = preferences.loadProfiles()
        if (raw.isNullOrBlank()) {
            return ProfileStoreLoadResult.Defaulted(ProfileDocument.defaults(), "empty")
        }
        val decoded = ProfileDocumentCodec.decode(raw)
            ?: return ProfileStoreLoadResult.Rejected(ProfileDocument.defaults(), "malformed_json")
        if (decoded.profiles.any { profile -> ProfileValidator.validate(profile).isNotEmpty() }) {
            return ProfileStoreLoadResult.Rejected(ProfileDocument.defaults(), "invalid_document")
        }
        return ProfileStoreLoadResult.Loaded(decoded.withSafeActiveProfile())
    }

    fun duplicateProfile(profileId: String): SaveProfileResult {
        val document = load().document
        val source = document.profiles.firstOrNull { profile -> profile.profileId == profileId }
            ?: return SaveProfileResult.Rejected("profile_not_found", document = document)
        val copy = source.copy(
            profileId = idFactory(),
            displayName = "${source.displayName} Copy",
            revision = 1L,
            builtIn = false,
        )
        return persist(
            document.copy(
                documentRevision = document.documentRevision + 1L,
                profiles = document.profiles + copy,
            ),
        )
    }

    fun renameProfile(profileId: String, displayName: String): SaveProfileResult {
        val document = load().document
        val existing = document.profiles.firstOrNull { profile -> profile.profileId == profileId }
            ?: return SaveProfileResult.Rejected("profile_not_found", document = document)
        return saveProfile(existing.copy(displayName = displayName))
    }

    fun saveProfile(profile: BtGunProfile): SaveProfileResult {
        val document = load().document
        val existing = document.profiles.firstOrNull { candidate -> candidate.profileId == profile.profileId }
            ?: return SaveProfileResult.Rejected("profile_not_found", document = document)
        if (existing.builtIn || profile.builtIn) {
            return SaveProfileResult.Rejected("built_in_immutable", document = document)
        }
        val errors = ProfileValidator.validate(profile)
        if (errors.isNotEmpty()) {
            return SaveProfileResult.Rejected("validation", errors = errors, document = document)
        }
        val nextProfile = profile.copy(
            builtIn = false,
            revision = existing.revision + 1L,
        )
        return persist(
            document.copy(
                documentRevision = document.documentRevision + 1L,
                profiles = document.profiles.map { candidate ->
                    if (candidate.profileId == profile.profileId) nextProfile else candidate
                },
            ),
        )
    }

    fun selectProfile(profileId: String): SaveProfileResult {
        val document = load().document
        if (document.profiles.none { profile -> profile.profileId == profileId }) {
            return SaveProfileResult.Rejected("profile_not_found", document = document)
        }
        return persist(
            document.copy(
                activeProfileId = profileId,
                documentRevision = document.documentRevision + 1L,
            ),
        )
    }

    fun migrateActiveProfileCalibration(encodedCalibration: String?): SaveProfileResult {
        val encoded = encodedCalibration?.takeIf { it.isNotBlank() }
            ?: return SaveProfileResult.Rejected("calibration_empty", document = load().document)
        val document = load().document
        val active = document.activeProfile()
        if (!active.aimCalibration.isNullOrBlank()) {
            return SaveProfileResult.Rejected("calibration_already_present", document = document)
        }
        return persist(
            document.copy(
                documentRevision = document.documentRevision + 1L,
                profiles = document.profiles.map { profile ->
                    if (profile.profileId == active.profileId) {
                        profile.copy(
                            revision = profile.revision + 1L,
                            aimCalibration = encoded,
                        )
                    } else {
                        profile
                    }
                },
            ),
        )
    }

    fun saveActiveProfileCalibration(encodedCalibration: String): SaveProfileResult {
        if (encodedCalibration.isBlank()) {
            return SaveProfileResult.Rejected("calibration_empty", document = load().document)
        }
        val document = load().document
        val active = document.activeProfile()
        return persist(
            document.copy(
                documentRevision = document.documentRevision + 1L,
                profiles = document.profiles.map { profile ->
                    if (profile.profileId == active.profileId) {
                        profile.copy(
                            revision = profile.revision + 1L,
                            aimCalibration = encodedCalibration,
                        )
                    } else {
                        profile
                    }
                },
            ),
        )
    }

    fun deleteProfile(profileId: String): SaveProfileResult {
        val document = load().document
        val existing = document.profiles.firstOrNull { profile -> profile.profileId == profileId }
            ?: return SaveProfileResult.Rejected("profile_not_found", document = document)
        if (existing.builtIn || existing.profileId == PROFILE_STORE_DEFAULT_ID) {
            return SaveProfileResult.Rejected("built_in_immutable", document = document)
        }
        val remaining = document.profiles.filterNot { profile -> profile.profileId == profileId }
        val activeProfileId = if (document.activeProfileId == profileId) {
            PROFILE_STORE_DEFAULT_ID
        } else {
            document.activeProfileId
        }
        return persist(
            document.copy(
                activeProfileId = activeProfileId,
                documentRevision = document.documentRevision + 1L,
                profiles = remaining,
            ).withSafeActiveProfile(),
        )
    }

    fun reset(): SaveProfileResult =
        persist(ProfileDocument.defaults())

    private fun persist(document: ProfileDocument): SaveProfileResult {
        preferences.saveProfiles(ProfileDocumentCodec.encode(document))
        return SaveProfileResult.Saved(document)
    }

    private fun ProfileDocument.withSafeActiveProfile(): ProfileDocument =
        if (profiles.any { profile -> profile.profileId == activeProfileId }) {
            this
        } else {
            copy(activeProfileId = PROFILE_STORE_DEFAULT_ID)
        }

    private class SharedPreferencesProfilePreferences(
        private val sharedPreferences: SharedPreferences,
    ) : ProfilePreferences {
        override fun loadProfiles(): String? =
            sharedPreferences.getString(KEY_PROFILES, null)

        override fun saveProfiles(value: String) {
            sharedPreferences.edit()
                .putString(KEY_PROFILES, value)
                .apply()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "bt_gun_profiles"
        private const val KEY_PROFILES = "profiles_v1"
    }
}

private object ProfileDocumentCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(document: ProfileDocument): String =
        json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            buildJsonObject {
                put("schemaVersion", document.schemaVersion)
                put("activeProfileId", document.activeProfileId)
                put("documentRevision", document.documentRevision)
                put(
                    "profiles",
                    buildJsonArray {
                        document.profiles.forEach { profile -> add(profile.encode()) }
                    },
                )
            },
        )

    fun decode(value: String): ProfileDocument? {
        val root = runCatching { json.parseToJsonElement(value).jsonObject }.getOrNull() ?: return null
        val schemaVersion = root.intField("schemaVersion") ?: return null
        if (schemaVersion !in 1..2) {
            return null
        }
        val profiles = (root["profiles"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.decodeProfile() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        if (profiles.none { profile -> profile.profileId == PROFILE_STORE_DEFAULT_ID && profile.builtIn }) {
            return null
        }
        return ProfileDocument(
            schemaVersion = 2,
            activeProfileId = root.stringField("activeProfileId") ?: PROFILE_STORE_DEFAULT_ID,
            documentRevision = root.longField("documentRevision")?.takeIf { it > 0L } ?: 1L,
            profiles = profiles,
        )
    }

    private fun BtGunProfile.encode(): JsonObject =
        buildJsonObject {
            put("profileId", profileId)
            put("displayName", displayName)
            put("revision", revision)
            put("builtIn", builtIn)
            put("rawDebugEnabled", rawDebugEnabled)
            put("recenterPhysicalControl", recenterPhysicalControl?.id.orEmpty())
            put("aimCalibration", aimCalibration ?: "")
            put("aim", aim.encode())
            put(
                "providerOverrides",
                buildJsonObject {
                    providerOverrides.forEach { (provider, override) -> put(provider.id, override.encode()) }
                },
            )
            put(
                "buttonMapping",
                buildJsonObject {
                    buttonMapping.forEach { (physical, virtual) -> put(physical.id, virtual.id) }
                },
            )
            put(
                "softControlMapping",
                buildJsonObject {
                    softControlMapping.forEach { (soft, virtual) -> put(soft.id, virtual.id) }
                },
            )
            put(
                "unsupportedMappings",
                buildJsonArray {
                    unsupportedMappings.forEach { value -> add(value) }
                },
            )
        }

    private fun JsonObject.decodeProfile(): BtGunProfile? {
        val profileId = stringField("profileId")?.takeIf { it.isNotBlank() } ?: return null
        val displayName = stringField("displayName") ?: return null
        val revision = longField("revision")?.takeIf { it > 0L } ?: return null
        val aim = (get("aim") as? JsonObject)?.decodeAimSettings() ?: return null
        val buttonMapping = (get("buttonMapping") as? JsonObject)?.decodeButtonMapping() ?: return null
        val softControlMapping = (get("softControlMapping") as? JsonObject)?.decodeSoftControlMapping()
            ?: BtGunProfile.defaultSoftControlMapping()
        val recenter = stringField("recenterPhysicalControl")
            ?.takeIf { it.isNotBlank() }
            ?.let(PhysicalButton::fromId)
        val unsupported = (get("unsupportedMappings") as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return BtGunProfile(
            profileId = profileId,
            displayName = displayName,
            revision = revision,
            builtIn = boolField("builtIn") ?: return null,
            aim = aim,
            providerOverrides = decodeProviderOverrides(get("providerOverrides") as? JsonObject),
            buttonMapping = buttonMapping,
            softControlMapping = softControlMapping,
            recenterPhysicalControl = recenter,
            aimCalibration = stringField("aimCalibration")?.takeIf { it.isNotBlank() },
            rawDebugEnabled = boolField("rawDebugEnabled") ?: false,
            unsupportedMappings = unsupported,
        )
    }

    private fun AimMappingSettings.encode(): JsonObject =
        buildJsonObject {
            put("sensitivity", sensitivity)
            put("invertX", invertX)
            put("invertY", invertY)
            put("deadZone", deadZone)
            put("smoothing", smoothing.id)
        }

    private fun JsonObject.decodeAimSettings(): AimMappingSettings? {
        val sensitivity = floatField("sensitivity") ?: return null
        val invertX = boolField("invertX") ?: return null
        val invertY = boolField("invertY") ?: return null
        val deadZone = floatField("deadZone") ?: return null
        val smoothing = stringField("smoothing")?.let(SmoothingMode::fromId) ?: return null
        return AimMappingSettings(
            sensitivity = sensitivity,
            invertX = invertX,
            invertY = invertY,
            deadZone = deadZone,
            smoothing = smoothing,
        )
    }

    private fun ProviderAimOverrides.encode(): JsonObject =
        buildJsonObject {
            put("useSharedSettings", useSharedSettings)
            put("settings", settings.encode())
        }

    private fun JsonObject.decodeProviderOverride(): ProviderAimOverrides? {
        val useSharedSettings = boolField("useSharedSettings") ?: return null
        val settings = (get("settings") as? JsonObject)?.decodeAimSettings() ?: return null
        return ProviderAimOverrides(
            useSharedSettings = useSharedSettings,
            settings = settings,
        )
    }

    private fun decodeProviderOverrides(value: JsonObject?): Map<AimProviderKey, ProviderAimOverrides> {
        val defaults = BtGunProfile.defaultProviderOverrides().toMutableMap()
        value?.forEach { (key, element) ->
            val provider = AimProviderKey.fromId(key) ?: return@forEach
            val override = (element as? JsonObject)?.decodeProviderOverride() ?: return@forEach
            defaults[provider] = override
        }
        return defaults
    }

    private fun JsonObject.decodeButtonMapping(): Map<PhysicalButton, VirtualButton>? {
        val mapping = mutableMapOf<PhysicalButton, VirtualButton>()
        forEach { (key, element) ->
            val physical = PhysicalButton.fromId(key) ?: return null
            val virtual = (element as? JsonPrimitive)?.contentOrNull?.let(VirtualButton::fromId) ?: return null
            mapping[physical] = virtual
        }
        return mapping
    }

    private fun JsonObject.decodeSoftControlMapping(): Map<SoftControl, VirtualButton>? {
        val mapping = BtGunProfile.defaultSoftControlMapping().toMutableMap()
        forEach { (key, element) ->
            val soft = SoftControl.fromId(key) ?: return null
            val virtual = (element as? JsonPrimitive)?.contentOrNull?.let(VirtualButton::fromId) ?: return null
            mapping[soft] = virtual
        }
        return mapping
    }

    private fun JsonObject.stringField(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolField(name: String): Boolean? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.intField(name: String): Int? =
        longField(name)?.takeIf { value -> value in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun JsonObject.longField(name: String): Long? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull

    private fun JsonObject.floatField(name: String): Float? =
        (get(name) as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
}
