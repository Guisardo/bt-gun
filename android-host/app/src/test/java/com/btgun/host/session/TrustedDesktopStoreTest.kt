package com.btgun.host.session

fun main() {
    trustedStoreSavesNonSecretDesktopMetadataByFingerprint()
    trustedStoreValidatesIdentityByFingerprintNotNameOrHost()
    trustedStoreIgnoresCorruptRows()
}

private fun trustedStoreSavesNonSecretDesktopMetadataByFingerprint() {
    val preferences = InMemoryTrustedDesktopPreferences()
    val store = TrustedDesktopStore(preferences)
    val metadata = TrustedDesktopMetadata(
        fingerprintSha256 = FINGERPRINT,
        displayName = "BT Gun Desktop",
        lastHost = "192.168.1.44",
        lastPort = 44383,
        lastSeenEpochMillis = 1_700_000_000_000L,
    )

    store.saveTrustedDesktop(metadata)

    expectEquals("load saved", listOf(metadata), store.loadTrustedDesktops())
    val raw = preferences.rawValue()
    expectContains("stores fingerprint", raw, FINGERPRINT)
    expectContains("stores display name", raw, "BT Gun Desktop")
    expectContains("stores host", raw, "192.168.1.44")
    listOf("qr_secret", "qrSecret", "manual_code", "manualCode", "proof", "privateKey", "123456").forEach { forbidden ->
        expectFalse("does not store $forbidden", raw.contains(forbidden, ignoreCase = true))
    }
}

private fun trustedStoreValidatesIdentityByFingerprintNotNameOrHost() {
    val store = TrustedDesktopStore(InMemoryTrustedDesktopPreferences())
    store.saveTrustedDesktop(
        TrustedDesktopMetadata(
            fingerprintSha256 = FINGERPRINT,
            displayName = "Desk A",
            lastHost = "192.168.1.44",
            lastPort = 44383,
            lastSeenEpochMillis = 1L,
        ),
    )

    expectEquals(
        "matching fingerprint",
        TrustedDesktopIdentityResult.TRUSTED,
        store.validateIdentity(fingerprintSha256 = FINGERPRINT, displayName = "Renamed", host = "10.0.0.9", port = 44444),
    )
    expectEquals(
        "changed fingerprint",
        TrustedDesktopIdentityResult.FINGERPRINT_MISMATCH,
        store.validateIdentity(fingerprintSha256 = OTHER_FINGERPRINT, displayName = "Desk A", host = "192.168.1.44", port = 44383),
    )
    expectEquals(
        "unknown fingerprint",
        TrustedDesktopIdentityResult.UNKNOWN,
        store.validateIdentity(fingerprintSha256 = OTHER_FINGERPRINT, displayName = "Desk B", host = "10.0.0.9", port = 44383),
    )
}

private fun trustedStoreIgnoresCorruptRows() {
    val preferences = InMemoryTrustedDesktopPreferences()
    preferences.saveTrustedDesktops("not|enough\n$FINGERPRINT|Desktop|bad-port|host|1000")
    val store = TrustedDesktopStore(preferences)

    expectEquals("corrupt rows ignored", emptyList<TrustedDesktopMetadata>(), store.loadTrustedDesktops())
}

private class InMemoryTrustedDesktopPreferences : TrustedDesktopPreferences {
    private var value: String? = null

    override fun loadTrustedDesktops(): String? = value

    override fun saveTrustedDesktops(value: String) {
        this.value = value
    }

    fun rawValue(): String = value.orEmpty()
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError(label)
    }
}

private fun expectContains(label: String, actual: String, expectedPart: String) {
    if (!actual.contains(expectedPart)) {
        throw AssertionError("$label expected <$actual> to contain <$expectedPart>")
    }
}

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
private const val OTHER_FINGERPRINT = "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100"
