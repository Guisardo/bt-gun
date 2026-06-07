package com.btgun.desktop.pairing

import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.SecretRedactor

fun main() {
    startPairingCreatesOneShortLivedSession()
    qrPayloadContainsEndpointIdentityNonceAndSecret()
    manualFallbackExposesEndpointCodeAndFingerprintSuffix()
    restartingPairingReplacesOneTimeMaterial()
    qrRendererProducesMinimumSizedImage()
    redactorHidesPairingSecrets()
}

private fun startPairingCreatesOneShortLivedSession() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)

    expectEquals("active session", session, registry.activeSession)
    expectEquals("protocol version", 1, session.qrPayload.v)
    expectEquals("endpoint host", "192.168.50.25", session.qrPayload.host)
    expectEquals("endpoint port", 41731, session.qrPayload.port)
    expectTrue("ttl minimum", session.expiresAtEpochMillis - 1_000L >= 120_000L)
    expectTrue("ttl maximum", session.expiresAtEpochMillis - 1_000L <= 300_000L)
    expectTrue("session id present", session.sid.isNotBlank())
}

private fun qrPayloadContainsEndpointIdentityNonceAndSecret() {
    val session = testRegistry().startPairing(nowEpochMillis = 10_000L)
    val payload = session.qrPayload
    val uri = payload.toPairingUri()

    expectContains("scheme", uri, "btgun://pair?")
    expectContains("sid", uri, "sid=${payload.sid}")
    expectContains("host", uri, "host=192.168.50.25")
    expectContains("port", uri, "port=41731")
    expectContains("expiry", uri, "expires_at_epoch_millis=${payload.expiresAtEpochMillis}")
    expectContains("fingerprint", uri, "desktop_spki_sha256=${payload.desktopSpkiSha256}")
    expectContains("nonce", uri, "desktop_nonce=${payload.desktopNonce}")
    expectContains("qr secret", uri, "qr_secret=${payload.qrSecret}")
    expectTrue("desktop nonce entropy", payload.desktopNonce.length >= 32)
    expectTrue("qr secret entropy", payload.qrSecret.length >= 32)
}

private fun manualFallbackExposesEndpointCodeAndFingerprintSuffix() {
    val session = testRegistry().startPairing(nowEpochMillis = 20_000L)

    expectEquals("manual host", "192.168.50.25", session.manualPayload.host)
    expectEquals("manual port", 41731, session.manualPayload.port)
    expectEquals("manual code length", 6, session.manualPayload.code.length)
    expectTrue("manual code digits", session.manualPayload.code.all { it.isDigit() })
    expectEquals("manual sid binding", session.sid, session.manualPayload.sid)
    expectEquals("fingerprint suffix", "99aabbcc", session.manualPayload.desktopSpkiSha256Suffix)
}

private fun restartingPairingReplacesOneTimeMaterial() {
    val registry = testRegistry()
    val first = registry.startPairing(nowEpochMillis = 30_000L)
    val second = registry.startPairing(nowEpochMillis = 31_000L)

    expectEquals("second active", second, registry.activeSession)
    expectNotEquals("new sid", first.sid, second.sid)
    expectNotEquals("new qr secret", first.qrPayload.qrSecret, second.qrPayload.qrSecret)
    expectNotEquals("new manual code", first.manualPayload.code, second.manualPayload.code)
}

private fun qrRendererProducesMinimumSizedImage() {
    val payload = testRegistry().startPairing(nowEpochMillis = 40_000L).qrPayload
    val image = QrCodeRenderer.render(payload.toPairingUri(), size = 240)

    expectEquals("qr width", 240, image.width)
    expectEquals("qr height", 240, image.height)
}

private fun redactorHidesPairingSecrets() {
    val session = testRegistry().startPairing(nowEpochMillis = 50_000L)
    val redacted = SecretRedactor.redact(
        "qr_secret=${session.qrPayload.qrSecret} code=${session.manualPayload.code} proof=abcdef0123456789",
    )

    expectFalse("no qr secret", redacted.contains(session.qrPayload.qrSecret))
    expectFalse("no manual code", redacted.contains(session.manualPayload.code))
    expectFalse("no proof", redacted.contains("abcdef0123456789"))
}

private fun testRegistry(): PairingSessionRegistry =
    PairingSessionRegistry(
        endpointSelector = LocalEndpointSelector.fixed(
            host = "192.168.50.25",
            port = 41731,
        ),
        identityStore = object : DesktopIdentityStore {
            override fun loadOrCreateIdentity(): DesktopIdentity =
                DesktopIdentity(
                    desktopSpkiSha256 = "00112233445566778899aabbccddeeff00112233445566778899aabbcc",
                )
        },
    )

private fun expectEquals(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$name expected <$expected> but was <$actual>")
    }
}

private fun expectNotEquals(name: String, first: Any?, second: Any?) {
    if (first == second) {
        throw AssertionError("$name expected values to differ but both were <$first>")
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

private fun expectContains(name: String, value: String, needle: String) {
    if (!value.contains(needle)) {
        throw AssertionError("$name expected <$value> to contain <$needle>")
    }
}
