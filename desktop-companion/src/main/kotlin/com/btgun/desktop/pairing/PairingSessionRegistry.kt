package com.btgun.desktop.pairing

import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.PairingProof
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

data class PairingProofRequest(
    val sid: String,
    val androidNonce: String,
    val desktopSpkiSha256: String,
    val proofHex: String,
)

data class TrustedPairingSession(
    val sid: String,
    val desktopSpkiSha256: String,
    val acceptedAtEpochMillis: Long,
)

sealed interface PairingAttemptResult {
    data class Accepted(val trustedSession: TrustedPairingSession) : PairingAttemptResult
    data object RejectedExpired : PairingAttemptResult
    data object RejectedWrongProof : PairingAttemptResult
    data object RejectedReplay : PairingAttemptResult
    data object RejectedRateLimited : PairingAttemptResult
    data object RejectedFingerprintMismatch : PairingAttemptResult
}

enum class PairingSecurityState {
    PENDING,
    RATE_LIMITED,
    ACCEPTED,
    EXPIRED,
    MISSING,
}

class PairingSessionRegistry(
    private val endpointSelector: LocalEndpointSelector = LocalEndpointSelector(),
    private val identityStore: DesktopIdentityStore = DesktopIdentityStore.default(),
    private val random: SecureRandom = SecureRandom(),
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxFailedAttempts: Int = DEFAULT_MAX_FAILED_ATTEMPTS,
) {
    var activeSession: PairingSession? = null
        private set
    private val failedAttemptsBySid = mutableMapOf<String, Int>()
    private val usedAndroidNoncesBySid = mutableMapOf<String, MutableSet<String>>()
    private val consumedSids = mutableSetOf<String>()
    private val rateLimitedSids = mutableSetOf<String>()

    init {
        require(ttlMillis in MIN_TTL_MILLIS..MAX_TTL_MILLIS) {
            "ttlMillis must be between $MIN_TTL_MILLIS and $MAX_TTL_MILLIS"
        }
        require(maxFailedAttempts > 0) { "maxFailedAttempts must be positive" }
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
            desktopNonce = nonce,
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
            failedAttemptsBySid.remove(session.sid)
            usedAndroidNoncesBySid.remove(session.sid)
            consumedSids.remove(session.sid)
            rateLimitedSids.remove(session.sid)
        }
    }

    fun verifyProof(request: PairingProofRequest, nowEpochMillis: Long = System.currentTimeMillis()): PairingAttemptResult {
        if (consumedSids.contains(request.sid)) {
            return PairingAttemptResult.RejectedReplay
        }
        val session = activeSession?.takeIf { it.sid == request.sid } ?: return PairingAttemptResult.RejectedExpired
        if (session.isExpired(nowEpochMillis)) {
            return PairingAttemptResult.RejectedExpired
        }
        if (rateLimitedSids.contains(session.sid)) {
            return PairingAttemptResult.RejectedRateLimited
        }
        if (request.desktopSpkiSha256 != session.qrPayload.desktopSpkiSha256) {
            return PairingAttemptResult.RejectedFingerprintMismatch
        }
        if (usedAndroidNoncesBySid.getOrPut(session.sid) { mutableSetOf() }.add(request.androidNonce).not()) {
            return PairingAttemptResult.RejectedReplay
        }

        val proofMatches = listOf(session.qrPayload.qrSecret, session.manualPayload.code).any { material ->
            PairingProof.verify(
                proofHex = request.proofHex,
                sid = session.sid,
                desktopNonce = session.qrPayload.desktopNonce,
                androidNonce = request.androidNonce,
                desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
                oneTimeMaterial = material,
            )
        }

        if (!proofMatches) {
            return recordFailedAttempt(session.sid)
        }

        return consumeSession(session, nowEpochMillis)
    }

    fun desktopIdentity() = identityStore.loadOrCreateIdentity()

    fun consumeSession(session: PairingSession, nowEpochMillis: Long): PairingAttemptResult.Accepted {
        consumedSids += session.sid
        activeSession = null
        return PairingAttemptResult.Accepted(
            TrustedPairingSession(
                sid = session.sid,
                desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
                acceptedAtEpochMillis = nowEpochMillis,
            ),
        )
    }

    fun recordFailedAttempt(sid: String): PairingAttemptResult {
        val next = (failedAttemptsBySid[sid] ?: 0) + 1
        failedAttemptsBySid[sid] = next
        return if (next >= maxFailedAttempts) {
            rateLimitedSids += sid
            PairingAttemptResult.RejectedRateLimited
        } else {
            PairingAttemptResult.RejectedWrongProof
        }
    }

    fun securityState(sid: String, nowEpochMillis: Long = System.currentTimeMillis()): PairingSecurityState {
        val session = activeSession
        return when {
            rateLimitedSids.contains(sid) -> PairingSecurityState.RATE_LIMITED
            consumedSids.contains(sid) -> PairingSecurityState.ACCEPTED
            session == null || session.sid != sid -> PairingSecurityState.MISSING
            session.isExpired(nowEpochMillis) -> PairingSecurityState.EXPIRED
            else -> PairingSecurityState.PENDING
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
        const val DEFAULT_MAX_FAILED_ATTEMPTS = 5
    }
}
