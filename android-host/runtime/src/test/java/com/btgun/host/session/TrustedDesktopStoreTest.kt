package com.btgun.host.session

fun main() {
    trustedStoreSavesNonSecretDesktopMetadataByFingerprint()
    trustedStoreValidatesIdentityByFingerprintNotNameOrHost()
    trustedStoreReturnsFirstTrustMissingAndMismatchWithoutOverwrite()
    pairingProofUsesStableTranscriptAndConstantVerification()
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
        TrustValidationResult.Trusted(
            TrustedDesktopMetadata(
                fingerprintSha256 = FINGERPRINT,
                displayName = "Desk A",
                lastHost = "192.168.1.44",
                lastPort = 44383,
                lastSeenEpochMillis = 1L,
            ),
        ),
        store.validateIdentity(fingerprintSha256 = FINGERPRINT, displayName = "Renamed", host = "10.0.0.9", port = 44444),
    )

    val changed = store.validateIdentity(fingerprintSha256 = OTHER_FINGERPRINT, displayName = "Desk A", host = "192.168.1.44", port = 44383)
    expectTrue("changed fingerprint mismatch", changed is TrustValidationResult.Mismatch)
    expectEquals("stored fingerprint preserved", FINGERPRINT, (changed as TrustValidationResult.Mismatch).stored.fingerprintSha256)

    expectTrue(
        "unknown fingerprint first trust",
        store.validateIdentity(fingerprintSha256 = OTHER_FINGERPRINT, displayName = "Desk B", host = "10.0.0.9", port = 44383)
            is TrustValidationResult.FirstTrust,
    )
    expectTrue(
        "same display name different endpoint first trust",
        store.validateIdentity(fingerprintSha256 = OTHER_FINGERPRINT, displayName = "Desk A", host = "10.0.0.9", port = 44383)
            is TrustValidationResult.FirstTrust,
    )
}

private fun trustedStoreReturnsFirstTrustMissingAndMismatchWithoutOverwrite() {
    val store = TrustedDesktopStore(InMemoryTrustedDesktopPreferences())

    expectEquals(
        "missing fingerprint",
        TrustValidationResult.Missing,
        store.validateIdentity(fingerprintSha256 = "not-a-fingerprint", displayName = "Desk A", host = "192.168.1.44", port = 44383),
    )

    val firstTrust = store.validateIdentity(
        fingerprintSha256 = FINGERPRINT,
        displayName = "Desk A",
        host = "192.168.1.44",
        port = 44383,
    )
    expectTrue("first trust", firstTrust is TrustValidationResult.FirstTrust)
    expectEquals("first trust does not save", emptyList<TrustedDesktopMetadata>(), store.loadTrustedDesktops())

    store.saveTrustedDesktop(
        TrustedDesktopMetadata(
            fingerprintSha256 = FINGERPRINT,
            displayName = "Desk A",
            lastHost = "192.168.1.44",
            lastPort = 44383,
            lastSeenEpochMillis = 1L,
        ),
    )
    val mismatch = store.validateIdentity(
        fingerprintSha256 = OTHER_FINGERPRINT,
        displayName = "Desk A",
        host = "192.168.1.44",
        port = 44383,
    )

    expectTrue("mismatch", mismatch is TrustValidationResult.Mismatch)
    expectEquals("mismatch presented", OTHER_FINGERPRINT, (mismatch as TrustValidationResult.Mismatch).presentedFingerprintSha256)
    expectEquals("mismatch does not overwrite", listOf(FINGERPRINT), store.loadTrustedDesktops().map { it.fingerprintSha256 })
}

private fun pairingProofUsesStableTranscriptAndConstantVerification() {
    val transcript = PairingProof.transcript(
        sid = "sid-1",
        desktopNonce = "00".repeat(16),
        androidNonce = "11".repeat(16),
        desktopSpkiSha256 = FINGERPRINT,
        oneTimeMaterial = "123456",
    )
    expectEquals(
        "transcript fields",
        "btgun-pair-v1\nsid=sid-1\ndesktop_nonce=${"00".repeat(16)}\nandroid_nonce=${"11".repeat(16)}\n" +
            "desktop_spki_sha256=$FINGERPRINT\none_time_material=123456",
        transcript,
    )
    val proof = PairingProof.create(
        sid = "sid-1",
        desktopNonce = "00".repeat(16),
        androidNonce = "11".repeat(16),
        desktopSpkiSha256 = FINGERPRINT,
        oneTimeMaterial = "123456",
    )

    expectTrue(
        "proof verifies",
        PairingProof.verify(
            proofHex = proof,
            sid = "sid-1",
            desktopNonce = "00".repeat(16),
            androidNonce = "11".repeat(16),
            desktopSpkiSha256 = FINGERPRINT,
            oneTimeMaterial = "123456",
        ),
    )
    expectFalse(
        "wrong material fails",
        PairingProof.verify(
            proofHex = proof,
            sid = "sid-1",
            desktopNonce = "00".repeat(16),
            androidNonce = "11".repeat(16),
            desktopSpkiSha256 = FINGERPRINT,
            oneTimeMaterial = "000000",
        ),
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

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
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
