package com.btgun.desktop.pairing

import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.DesktopTlsIdentity
import com.btgun.desktop.security.FileDesktopIdentityStore
import com.btgun.desktop.security.SecretRedactor
import com.btgun.desktop.ui.PairingWindow
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import kotlin.io.path.createTempDirectory
import javax.crypto.spec.SecretKeySpec

fun main() {
    startPairingCreatesOneShortLivedSession()
    qrPayloadContainsEndpointIdentityNonceAndSecret()
    manualFallbackExposesEndpointCodeAndFingerprintSuffix()
    manualCodeAuthenticatesActiveSessionWithoutSidOrNonce()
    restartingPairingReplacesOneTimeMaterial()
    desktopIdentityStorePersistsFingerprint()
    desktopIdentityStoreRotatesLegacyPasswordStore()
    desktopIdentityStoreRotatesMismatchedKeyPair()
    qrRendererProducesMinimumSizedImage()
    localEndpointSelectorPrefersLanAdaptersOverVirtualAdapters()
    redactorHidesPairingSecrets()
    pairingWindowCopyCoversRequiredStatesAndVisibleFallbackOnly()
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
    expectEquals("fingerprint suffix", "99aabbcc", session.manualPayload.desktopSpkiSha256Suffix)
}

private fun manualCodeAuthenticatesActiveSessionWithoutSidOrNonce() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 25_000L)

    val result = registry.verifyManualCode(
        ManualPairingAttemptRequest(
            androidNonce = "aa".repeat(16),
            desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
            code = session.manualPayload.code,
        ),
        nowEpochMillis = 26_000L,
    )

    expectTrue("manual accepted", result is PairingAttemptResult.Accepted)
    expectEquals("manual trusted sid", session.sid, (result as PairingAttemptResult.Accepted).trustedSession.sid)
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

private fun localEndpointSelectorPrefersLanAdaptersOverVirtualAdapters() {
    val wifiPriority = LocalEndpointSelector.candidatePriority(
        name = "en0",
        displayName = "Wi-Fi",
        virtual = false,
        pointToPoint = false,
        multicast = true,
        address = ipv4("192.168.1.29"),
    )
    val ethernetPriority = LocalEndpointSelector.candidatePriority(
        name = "Ethernet",
        displayName = "Realtek PCIe GbE Family Controller",
        virtual = false,
        pointToPoint = false,
        multicast = true,
        address = ipv4("192.168.1.100"),
    )
    val zeroTierPriority = LocalEndpointSelector.candidatePriority(
        name = "ZeroTier One [a09acf02338a73b5]",
        displayName = "ZeroTier Virtual Port #2",
        virtual = false,
        pointToPoint = false,
        multicast = true,
        address = ipv4("192.168.196.101"),
    )
    val virtualBoxPriority = LocalEndpointSelector.candidatePriority(
        name = "Ethernet 2",
        displayName = "VirtualBox Host-Only Ethernet Adapter",
        virtual = false,
        pointToPoint = false,
        multicast = true,
        address = ipv4("192.168.56.1"),
    )
    val virtualPriority = LocalEndpointSelector.candidatePriority(
        name = "feth3790",
        displayName = "virtual bridge",
        virtual = false,
        pointToPoint = false,
        multicast = true,
        address = ipv4("172.28.0.101"),
    )
    val loopbackPriority = LocalEndpointSelector.candidatePriority(
        name = "lo0",
        displayName = "loopback",
        virtual = false,
        pointToPoint = false,
        multicast = false,
        address = ipv4("127.0.0.1"),
    )

    expectTrue("wifi priority", wifiPriority != null && wifiPriority > 0)
    expectTrue("ethernet priority", ethernetPriority != null && ethernetPriority > 0)
    expectEquals("zerotier rejected", null, zeroTierPriority)
    expectEquals("virtualbox rejected", null, virtualBoxPriority)
    expectEquals("virtual rejected", null, virtualPriority)
    expectEquals("loopback rejected", null, loopbackPriority)
}

private fun desktopIdentityStorePersistsFingerprint() {
    val path = createTempDirectory("btgun-desktop-identity-test").resolve("identity.p12")
    val store = FileDesktopIdentityStore(path)
    val first = store.loadOrCreateIdentity()
    val second = FileDesktopIdentityStore(path).loadOrCreateIdentity()

    expectEquals("fingerprint length", 64, first.desktopSpkiSha256.length)
    expectEquals("persisted fingerprint", first.desktopSpkiSha256, second.desktopSpkiSha256)
    expectTrue("sidecar password exists", Files.exists(path.resolveSibling("${path.fileName}.key")))
}

private fun desktopIdentityStoreRotatesLegacyPasswordStore() {
    val path = createTempDirectory("btgun-desktop-legacy-identity-test").resolve("identity.p12")
    val legacy = FileDesktopIdentityStore(path, "bt-gun-desktop-local-identity".toCharArray()).loadOrCreateIdentity()
    val rotated = FileDesktopIdentityStore(path).loadOrCreateIdentity()

    expectNotEquals("rotated fingerprint", legacy.desktopSpkiSha256, rotated.desktopSpkiSha256)
    expectTrue("legacy quarantined", Files.exists(path.resolveSibling("${path.fileName}.legacy-insecure")))
}

private fun desktopIdentityStoreRotatesMismatchedKeyPair() {
    val path = createTempDirectory("btgun-desktop-mismatched-identity-test").resolve("identity.p12")
    val password = "bt-gun-desktop-mismatched".toCharArray()
    val first = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val second = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val badFingerprint = FileDesktopIdentityStore.spkiSha256(first.public)
    writeIdentity(path, password, publicKeyBytes = first.public.encoded, privateKeyBytes = second.private.encoded)

    val rotated = FileDesktopIdentityStore(path, password).loadOrCreateIdentity()

    expectNotEquals("rotated mismatch fingerprint", badFingerprint, rotated.desktopSpkiSha256)
    expectTrue("mismatch quarantined", Files.exists(path.resolveSibling("${path.fileName}.legacy-insecure")))
    DesktopTlsIdentity.keyStoreFor(rotated, "192.168.50.25")
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

private fun pairingWindowCopyCoversRequiredStatesAndVisibleFallbackOnly() {
    val session = testRegistry().startPairing(nowEpochMillis = 60_000L)
    val manualHtml = PairingWindow.manualFallbackHtml(session.manualPayload)

    expectEquals("required states", REQUIRED_STATE_LABELS, PairingWindow.requiredStateLabels())
    expectEquals("qr size", 420, PairingWindow.QR_SIZE)
    expectEquals("endpoint text", "Endpoint: 192.168.50.25:41731", PairingWindow.endpointText(session.endpoint))
    expectEquals("countdown ceil seconds", "Expires in: 5s", PairingWindow.countdownText(65_000L, 60_001L))
    expectContains("manual endpoint", manualHtml, "192.168.50.25:41731")
    expectContains("manual port", manualHtml, "41731")
    expectContains("manual code", manualHtml, session.manualPayload.code)
    expectContains("fingerprint suffix", manualHtml, "99aabbcc")
    expectFalse("no challenge in manual copy", manualHtml.contains("Challenge", ignoreCase = true))
    expectFalse("no session id in manual copy", manualHtml.contains("Session id", ignoreCase = true))
    expectFalse("no qr secret in manual copy", manualHtml.contains(session.qrPayload.qrSecret))
    expectFalse("no full fingerprint in manual copy", manualHtml.contains(session.qrPayload.desktopSpkiSha256))
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

private fun writeIdentity(path: Path, password: CharArray, publicKeyBytes: ByteArray, privateKeyBytes: ByteArray) {
    val keyStore = KeyStore.getInstance("JCEKS").apply {
        load(null, password)
    }
    keyStore.setEntry(
        "btgun-desktop-public",
        KeyStore.SecretKeyEntry(SecretKeySpec(publicKeyBytes, "RAW")),
        KeyStore.PasswordProtection(password),
    )
    keyStore.setEntry(
        "btgun-desktop-private",
        KeyStore.SecretKeyEntry(SecretKeySpec(privateKeyBytes, "RAW")),
        KeyStore.PasswordProtection(password),
    )
    Files.createDirectories(path.parent)
    Files.newOutputStream(path).use { output -> keyStore.store(output, password) }
}

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

private fun ipv4(value: String): Inet4Address =
    InetAddress.getByName(value) as Inet4Address

private val REQUIRED_STATE_LABELS = listOf(
    "idle",
    "pairing ready",
    "android connected",
    "authenticated",
    "degraded",
    "disconnected",
    "expired",
    "rate limited",
)
