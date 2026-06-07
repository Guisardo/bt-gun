package com.btgun.desktop.security

import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.PairingAttemptResult
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSecurityState
import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry

fun main() {
    correctQrProofAcceptsExactlyOnce()
    expiredSessionRejectsBeforeTrustedState()
    wrongManualCodeAndQrSecretReject()
    replayedAndroidNonceRejects()
    fingerprintMismatchRejectsBeforeTrustedState()
    exhaustedAttemptsLockSession()
    redactorHidesProofMaterialAndPrivateKeyMarkers()
}

private fun correctQrProofAcceptsExactlyOnce() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val androidNonce = "aa".repeat(16)
    val proof = proofFor(session, androidNonce, session.qrPayload.qrSecret)
    val request = requestFor(session, androidNonce, proof)

    val accepted = registry.verifyProof(request, nowEpochMillis = 2_000L)

    expectTrue("proof accepted", accepted is PairingAttemptResult.Accepted)
    expectEquals("trusted sid", session.sid, (accepted as PairingAttemptResult.Accepted).trustedSession.sid)

    val replay = registry.verifyProof(request, nowEpochMillis = 2_001L)
    expectEquals("single use rejects", PairingAttemptResult.RejectedReplay, replay)
    expectNoTrustedControlState(replay)
}

private fun expiredSessionRejectsBeforeTrustedState() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val androidNonce = "bb".repeat(16)
    val result = registry.verifyProof(
        requestFor(session, androidNonce, proofFor(session, androidNonce, session.qrPayload.qrSecret)),
        nowEpochMillis = session.expiresAtEpochMillis,
    )

    expectEquals("expired", PairingAttemptResult.RejectedExpired, result)
    expectNoTrustedControlState(result)
}

private fun wrongManualCodeAndQrSecretReject() {
    val registry = testRegistry(maxFailedAttempts = 4)
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val wrongQrNonce = "cc".repeat(16)
    val wrongCodeNonce = "dd".repeat(16)

    val wrongQr = registry.verifyProof(
        requestFor(session, wrongQrNonce, proofFor(session, wrongQrNonce, "wrong-qr-secret-material-0000000000")),
        nowEpochMillis = 2_000L,
    )
    val wrongCode = registry.verifyProof(
        requestFor(session, wrongCodeNonce, proofFor(session, wrongCodeNonce, "000000")),
        nowEpochMillis = 2_001L,
    )

    expectEquals("wrong qr secret", PairingAttemptResult.RejectedWrongProof, wrongQr)
    expectEquals("wrong manual code", PairingAttemptResult.RejectedWrongProof, wrongCode)
    expectNoTrustedControlState(wrongQr)
    expectNoTrustedControlState(wrongCode)
}

private fun replayedAndroidNonceRejects() {
    val registry = testRegistry(maxFailedAttempts = 4)
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val androidNonce = "ee".repeat(16)
    val wrong = registry.verifyProof(
        requestFor(session, androidNonce, proofFor(session, androidNonce, "wrong-qr-secret-material-1111111111")),
        nowEpochMillis = 2_000L,
    )
    val replay = registry.verifyProof(
        requestFor(session, androidNonce, proofFor(session, androidNonce, session.qrPayload.qrSecret)),
        nowEpochMillis = 2_001L,
    )

    expectEquals("first attempt wrong proof", PairingAttemptResult.RejectedWrongProof, wrong)
    expectEquals("nonce replay", PairingAttemptResult.RejectedReplay, replay)
    expectNoTrustedControlState(replay)
}

private fun fingerprintMismatchRejectsBeforeTrustedState() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val androidNonce = "ff".repeat(16)
    val proof = PairingProof.create(
        sid = session.sid,
        desktopNonce = session.qrPayload.desktopNonce,
        androidNonce = androidNonce,
        desktopSpkiSha256 = OTHER_FINGERPRINT,
        oneTimeMaterial = session.qrPayload.qrSecret,
    )

    val result = registry.verifyProof(
        PairingProofRequest(
            sid = session.sid,
            androidNonce = androidNonce,
            desktopSpkiSha256 = OTHER_FINGERPRINT,
            proofHex = proof,
        ),
        nowEpochMillis = 2_000L,
    )

    expectEquals("fingerprint mismatch", PairingAttemptResult.RejectedFingerprintMismatch, result)
    expectNoTrustedControlState(result)
}

private fun exhaustedAttemptsLockSession() {
    val registry = testRegistry(maxFailedAttempts = 2)
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val first = registry.verifyProof(
        requestFor(session, "11".repeat(16), proofFor(session, "11".repeat(16), "wrong-qr-secret-material-2222222222")),
        nowEpochMillis = 2_000L,
    )
    val second = registry.verifyProof(
        requestFor(session, "22".repeat(16), proofFor(session, "22".repeat(16), "wrong-qr-secret-material-3333333333")),
        nowEpochMillis = 2_001L,
    )
    val locked = registry.verifyProof(
        requestFor(session, "33".repeat(16), proofFor(session, "33".repeat(16), session.manualPayload.code)),
        nowEpochMillis = 2_002L,
    )

    expectEquals("first wrong proof", PairingAttemptResult.RejectedWrongProof, first)
    expectEquals("limit reached", PairingAttemptResult.RejectedRateLimited, second)
    expectEquals("state locked", PairingSecurityState.RATE_LIMITED, registry.securityState(session.sid, nowEpochMillis = 2_002L))
    expectEquals("correct proof still locked", PairingAttemptResult.RejectedRateLimited, locked)
    expectNoTrustedControlState(locked)
}

private fun redactorHidesProofMaterialAndPrivateKeyMarkers() {
    val redacted = SecretRedactor.redact(
        "qr_secret=abcdefghijklmnopqrstuvwxyzABCDEF code=123456 proof=abcdef0123456789 " +
            "pairing_proof=nonce-abcdef0123456789 private_key=-----BEGIN PRIVATE KEY-----abc",
    )

    expectFalse("no qr secret", redacted.contains("abcdefghijklmnopqrstuvwxyzABCDEF"))
    expectFalse("no manual code", redacted.contains("123456"))
    expectFalse("no proof", redacted.contains("abcdef0123456789"))
    expectFalse("no private key marker", redacted.contains("BEGIN PRIVATE KEY"))
}

private fun proofFor(session: PairingSession, androidNonce: String, material: String): String =
    PairingProof.create(
        sid = session.sid,
        desktopNonce = session.qrPayload.desktopNonce,
        androidNonce = androidNonce,
        desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
        oneTimeMaterial = material,
    )

private fun requestFor(session: PairingSession, androidNonce: String, proof: String): PairingProofRequest =
    PairingProofRequest(
        sid = session.sid,
        androidNonce = androidNonce,
        desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
        proofHex = proof,
    )

private fun testRegistry(maxFailedAttempts: Int = 3): PairingSessionRegistry =
    PairingSessionRegistry(
        endpointSelector = LocalEndpointSelector.fixed(
            host = "192.168.50.25",
            port = 41731,
        ),
        identityStore = object : DesktopIdentityStore {
            override fun loadOrCreateIdentity(): DesktopIdentity =
                DesktopIdentity(desktopSpkiSha256 = FINGERPRINT)
        },
        maxFailedAttempts = maxFailedAttempts,
    )

private fun expectNoTrustedControlState(result: PairingAttemptResult) {
    if (result is PairingAttemptResult.Accepted) {
        throw AssertionError("rejected proof must not return trusted control state")
    }
}

private fun expectEquals(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$name expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(name: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError("$name expected true")
    }
}

private fun expectFalse(name: String, condition: Boolean) {
    if (condition) {
        throw AssertionError("$name expected false")
    }
}

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
private const val OTHER_FINGERPRINT = "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100"
