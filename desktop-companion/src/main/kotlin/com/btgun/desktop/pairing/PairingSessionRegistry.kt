package com.btgun.desktop.pairing

import com.btgun.desktop.security.DesktopIdentityStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class PairingSession(
    val sid: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val endpoint: LocalEndpoint,
    val qrPayload: PairingPayloadV1,
    val manualPayload: ManualPairingPayload,
) {
    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis
}

class PairingSessionRegistry(
    private val endpointSelector: LocalEndpointSelector = LocalEndpointSelector(),
    private val identityStore: DesktopIdentityStore = DesktopIdentityStore.default(),
    private val random: SecureRandom = SecureRandom(),
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    var activeSession: PairingSession? = null
        private set

    init {
        require(ttlMillis in MIN_TTL_MILLIS..MAX_TTL_MILLIS) {
            "ttlMillis must be between $MIN_TTL_MILLIS and $MAX_TTL_MILLIS"
        }
    }

    fun startPairing(nowEpochMillis: Long = System.currentTimeMillis()): PairingSession {
        val endpoint = endpointSelector.bestActiveIpv4()
        val identity = identityStore.loadOrCreateIdentity()
        val sid = UUID.randomUUID().toString()
        val expiresAt = nowEpochMillis + ttlMillis
        val nonce = randomHex(byteCount = 24)
        val qrSecret = randomSecret()
        val manualCode = nextManualCode(previousCode = activeSession?.manualPayload?.code)

        val qrPayload = PairingPayloadV1(
            sid = sid,
            host = endpoint.host,
            port = endpoint.port,
            expiresAtEpochMillis = expiresAt,
            desktopSpkiSha256 = identity.desktopSpkiSha256,
            desktopNonce = nonce,
            qrSecret = qrSecret,
        )
        val manualPayload = ManualPairingPayload(
            host = endpoint.host,
            port = endpoint.port,
            code = manualCode,
            desktopSpkiSha256Suffix = identity.desktopSpkiSha256.takeLast(8),
            sid = sid,
        )

        return PairingSession(
            sid = sid,
            createdAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = expiresAt,
            endpoint = endpoint,
            qrPayload = qrPayload,
            manualPayload = manualPayload,
        ).also { session ->
            activeSession = session
        }
    }

    fun expire(nowEpochMillis: Long = System.currentTimeMillis()) {
        if (activeSession?.isExpired(nowEpochMillis) == true) {
            activeSession = null
        }
    }

    fun reject() {
        activeSession = null
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun randomSecret(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun nextManualCode(previousCode: String?): String {
        repeat(8) {
            val code = random.nextInt(1_000_000).toString().padStart(6, '0')
            if (code != previousCode) {
                return code
            }
        }

        val next = ((previousCode?.toIntOrNull() ?: 0) + 1) % 1_000_000
        return next.toString().padStart(6, '0')
    }

    companion object {
        const val MIN_TTL_MILLIS = 120_000L
        const val MAX_TTL_MILLIS = 300_000L
        const val DEFAULT_TTL_MILLIS = 180_000L
    }
}
