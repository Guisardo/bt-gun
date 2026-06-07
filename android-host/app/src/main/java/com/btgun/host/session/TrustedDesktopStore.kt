package com.btgun.host.session

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

data class TrustedDesktopMetadata(
    val fingerprintSha256: String,
    val displayName: String,
    val lastHost: String,
    val lastPort: Int,
    val lastSeenEpochMillis: Long,
) {
    init {
        require(fingerprintSha256.matches(FINGERPRINT_REGEX)) { "fingerprintSha256 must be SHA-256 hex" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(lastHost.isNotBlank()) { "lastHost must not be blank" }
        require(lastPort in PORT_RANGE) { "lastPort must be between 1 and 65535" }
        require(lastSeenEpochMillis >= 0L) { "lastSeenEpochMillis must be non-negative" }
    }
}

sealed interface TrustValidationResult {
    data class Trusted(val metadata: TrustedDesktopMetadata) : TrustValidationResult
    data class FirstTrust(
        val fingerprintSha256: String,
        val displayName: String,
        val host: String,
        val port: Int,
    ) : TrustValidationResult

    data class Mismatch(
        val stored: TrustedDesktopMetadata,
        val presentedFingerprintSha256: String,
        val displayName: String,
        val host: String,
        val port: Int,
    ) : TrustValidationResult

    data object Missing : TrustValidationResult
}

interface TrustedDesktopPreferences {
    fun loadTrustedDesktops(): String?
    fun saveTrustedDesktops(value: String)
}

class TrustedDesktopStore {
    private val preferences: TrustedDesktopPreferences

    constructor(context: Context) : this(
        SharedPreferencesTrustedDesktopPreferences(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    constructor(preferences: TrustedDesktopPreferences) {
        this.preferences = preferences
    }

    fun saveTrustedDesktop(metadata: TrustedDesktopMetadata) {
        val next = loadTrustedDesktops()
            .filterNot { it.fingerprintSha256 == metadata.fingerprintSha256 }
            .plus(metadata)
            .sortedBy { it.displayName.lowercase(Locale.US) }
        preferences.saveTrustedDesktops(TrustedDesktopCodec.encode(next))
    }

    fun loadTrustedDesktops(): List<TrustedDesktopMetadata> =
        TrustedDesktopCodec.decode(preferences.loadTrustedDesktops())

    fun validateIdentity(
        fingerprintSha256: String,
        displayName: String,
        host: String,
        port: Int,
    ): TrustValidationResult {
        val normalized = fingerprintSha256.lowercase(Locale.US)
        if (!normalized.matches(FINGERPRINT_REGEX)) {
            return TrustValidationResult.Missing
        }
        val trusted = loadTrustedDesktops()
        trusted.firstOrNull { it.fingerprintSha256 == normalized }?.let { metadata ->
            return TrustValidationResult.Trusted(metadata)
        }
        val conflicting = trusted.firstOrNull { desktop ->
            desktop.displayName == displayName || (desktop.lastHost == host && desktop.lastPort == port)
        }
        return if (conflicting != null) {
            TrustValidationResult.Mismatch(
                stored = conflicting,
                presentedFingerprintSha256 = normalized,
                displayName = displayName,
                host = host,
                port = port,
            )
        } else {
            TrustValidationResult.FirstTrust(
                fingerprintSha256 = normalized,
                displayName = displayName,
                host = host,
                port = port,
            )
        }
    }

    private class SharedPreferencesTrustedDesktopPreferences(
        private val sharedPreferences: SharedPreferences,
    ) : TrustedDesktopPreferences {
        override fun loadTrustedDesktops(): String? =
            sharedPreferences.getString(KEY_TRUSTED_DESKTOPS, null)

        override fun saveTrustedDesktops(value: String) {
            sharedPreferences.edit()
                .putString(KEY_TRUSTED_DESKTOPS, value)
                .apply()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "bt_gun_trusted_desktops"
        private const val KEY_TRUSTED_DESKTOPS = "trusted_desktops"
    }
}

private object TrustedDesktopCodec {
    fun encode(desktops: List<TrustedDesktopMetadata>): String =
        desktops.joinToString("\n") { desktop ->
            listOf(
                desktop.fingerprintSha256,
                encodeField(desktop.displayName),
                encodeField(desktop.lastHost),
                desktop.lastPort.toString(),
                desktop.lastSeenEpochMillis.toString(),
            ).joinToString("|")
        }

    fun decode(value: String?): List<TrustedDesktopMetadata> =
        value.orEmpty()
            .lineSequence()
            .mapNotNull(::decodeRow)
            .toList()

    private fun decodeRow(row: String): TrustedDesktopMetadata? {
        val fields = row.split("|")
        if (fields.size != 5) {
            return null
        }
        val fingerprint = fields[0].lowercase(Locale.US)
        val displayName = decodeField(fields[1]).takeIf { it.isNotBlank() } ?: return null
        val host = decodeField(fields[2]).takeIf { it.isNotBlank() } ?: return null
        val port = fields[3].toIntOrNull()?.takeIf { it in PORT_RANGE } ?: return null
        val lastSeen = fields[4].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        if (!fingerprint.matches(FINGERPRINT_REGEX)) {
            return null
        }
        return TrustedDesktopMetadata(
            fingerprintSha256 = fingerprint,
            displayName = displayName,
            lastHost = host,
            lastPort = port,
            lastSeenEpochMillis = lastSeen,
        )
    }

    private fun encodeField(value: String): String =
        value
            .replace("%", "%25")
            .replace("|", "%7C")
            .replace("\n", "%0A")

    private fun decodeField(value: String): String =
        value
            .replace("%0A", "\n")
            .replace("%7C", "|")
            .replace("%25", "%")
}

private val PORT_RANGE = 1..65_535
private val FINGERPRINT_REGEX = Regex("[0-9a-f]{64}")
