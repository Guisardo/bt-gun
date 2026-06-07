package com.btgun.host.session

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.Locale

data class DesktopControlClientConfig(
    val url: String,
    val expectedDesktopSpkiSha256: String,
    val maxMessageBytes: Int = 16 * 1024,
    val connectedTimeoutNanos: Long = 1_000_000_000L,
    val disconnectedTimeoutNanos: Long = 3_000_000_000L,
)

data class ControlProofRequest(
    val sid: String,
    val androidNonce: String,
    val desktopSpkiSha256: String,
    val proofHex: String,
)

data class ControlDiagnostics(
    val sessionState: String,
    val desktopIdentitySuffix: String,
    val heartbeatAgeMillis: Long?,
    val lastControlError: String?,
)

data class ProfileMetadata(
    val profileId: String,
    val displayName: String,
    val revision: Long,
)

data class DesktopControlConnectionRequest(
    val config: DesktopControlClientConfig,
    val proofRequest: ControlProofRequest,
    val displayName: String,
    val host: String,
    val port: Int,
) {
    fun trustedMetadata(nowEpochMillis: Long): TrustedDesktopMetadata =
        TrustedDesktopMetadata(
            fingerprintSha256 = config.expectedDesktopSpkiSha256.lowercase(Locale.US),
            displayName = displayName,
            lastHost = host,
            lastPort = port,
            lastSeenEpochMillis = nowEpochMillis,
        )

    companion object {
        fun fromQrPayload(
            payload: PairingPayloadV1,
            androidNonce: String,
            displayName: String = DEFAULT_DESKTOP_DISPLAY_NAME,
        ): DesktopControlConnectionRequest {
            val fingerprint = payload.desktopSpkiSha256.lowercase(Locale.US)
            return DesktopControlConnectionRequest(
                config = DesktopControlClientConfig(
                    url = "wss://${payload.host}:${payload.port}/control",
                    expectedDesktopSpkiSha256 = fingerprint,
                ),
                proofRequest = ControlProofRequest(
                    sid = payload.sid,
                    androidNonce = androidNonce,
                    desktopSpkiSha256 = fingerprint,
                    proofHex = PairingProof.create(
                        sid = payload.sid,
                        desktopNonce = payload.desktopNonce,
                        androidNonce = androidNonce,
                        desktopSpkiSha256 = fingerprint,
                        oneTimeMaterial = payload.qrSecret,
                    ),
                ),
                displayName = displayName,
                host = payload.host,
                port = payload.port,
            )
        }

        fun fromTrustedDesktop(
            metadata: TrustedDesktopMetadata,
            androidNonce: String,
        ): DesktopControlConnectionRequest {
            val fingerprint = metadata.fingerprintSha256.lowercase(Locale.US)
            val sid = "trusted-${fingerprint.takeLast(8)}"
            return DesktopControlConnectionRequest(
                config = DesktopControlClientConfig(
                    url = "wss://${metadata.lastHost}:${metadata.lastPort}/control",
                    expectedDesktopSpkiSha256 = fingerprint,
                ),
                proofRequest = ControlProofRequest(
                    sid = sid,
                    androidNonce = androidNonce,
                    desktopSpkiSha256 = fingerprint,
                    proofHex = PairingProof.create(
                        sid = sid,
                        desktopNonce = fingerprint.take(32),
                        androidNonce = androidNonce,
                        desktopSpkiSha256 = fingerprint,
                        oneTimeMaterial = fingerprint,
                    ),
                ),
                displayName = metadata.displayName,
                host = metadata.lastHost,
                port = metadata.lastPort,
            )
        }

        const val DEFAULT_DESKTOP_DISPLAY_NAME: String = "BT Gun Desktop"
    }
}

interface DesktopControlSocket {
    fun send(text: String): Boolean
    fun close()
}

class DesktopControlClient(
    private val config: DesktopControlClientConfig,
    private val socketFactory: (Request, WebSocketListener) -> DesktopControlSocket = ::defaultSocket,
) {
    private var socket: DesktopControlSocket? = null
    private var lastHeartbeatElapsedNanos: Long? = null
    private var linkState: DesktopLinkState = DesktopLinkState()

    fun connect(proofRequest: ControlProofRequest): DesktopControlConnectResult {
        val trust = verifyPresentedFingerprint(proofRequest.desktopSpkiSha256)
        if (trust is DesktopControlConnectResult.TrustMismatch) {
            linkState = linkState.copy(
                phase = DesktopLinkPhase.TRUST_PROBLEM,
                lastControlError = "desktop fingerprint mismatch",
            )
            return trust
        }
        val request = Request.Builder()
            .url(config.url)
            .header("X-BT-Gun-Desktop-Fingerprint", config.expectedDesktopSpkiSha256)
            .header("X-BT-Gun-Session", proofRequest.sid)
            .header("X-BT-Gun-Android-Nonce", proofRequest.androidNonce)
            .header("X-BT-Gun-Pairing-Proof", proofRequest.proofHex)
            .build()

        socket = socketFactory(request, object : WebSocketListener() {})
        linkState = linkState.copy(
            phase = DesktopLinkPhase.CONNECTED,
            fingerprintSuffix = config.expectedDesktopSpkiSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
        )
        return DesktopControlConnectResult.Connected
    }

    fun send(envelope: ControlEnvelope): DesktopControlSendResult {
        val encoded = ControlEnvelopeCodec.encode(envelope)
        return when (val decoded = ControlEnvelopeCodec.decode(encoded, maxBytes = Int.MAX_VALUE)) {
            is ControlDecodeResult.Rejected -> DesktopControlSendResult.Rejected(decoded.error)
            is ControlDecodeResult.Accepted -> {
                if (encoded.toByteArray(Charsets.UTF_8).size > config.maxMessageBytes) {
                    return DesktopControlSendResult.Rejected(ControlEnvelopeError.OVERSIZED)
                }
                val activeSocket = socket ?: return DesktopControlSendResult.NotConnected
                if (activeSocket.send(encoded)) {
                    DesktopControlSendResult.Sent
                } else {
                    DesktopControlSendResult.Failed("socket rejected message")
                }
            }
        }
    }

    fun close() {
        socket?.close()
        socket = null
        linkState = linkState.copy(phase = DesktopLinkPhase.DISCONNECTED)
    }

    fun currentLinkState(): DesktopLinkState = linkState

    fun observeHeartbeatPing(nowElapsedNanos: Long) {
        observeHeartbeat(nowElapsedNanos)
    }

    fun observeHeartbeatPong(nowElapsedNanos: Long) {
        observeHeartbeat(nowElapsedNanos)
    }

    fun refreshLiveness(nowElapsedNanos: Long): DesktopLinkState {
        linkState = linkState.copy(
            phase = livenessPhase(nowElapsedNanos),
            heartbeatAgeMillis = heartbeatAgeMillisAt(nowElapsedNanos),
        )
        return linkState
    }

    fun applyDiagnostics(diagnostics: ControlDiagnostics) {
        linkState = linkState.copy(
            phase = diagnostics.sessionState.toDesktopLinkPhase(),
            fingerprintSuffix = diagnostics.desktopIdentitySuffix,
            heartbeatAgeMillis = diagnostics.heartbeatAgeMillis,
            lastControlError = diagnostics.lastControlError,
        )
    }

    fun recordControlError(error: String) {
        linkState = linkState.copy(lastControlError = error)
    }

    fun verifyPresentedFingerprint(presented: String): DesktopControlConnectResult =
        if (presented.equals(config.expectedDesktopSpkiSha256, ignoreCase = true)) {
            DesktopControlConnectResult.Connected
        } else {
            DesktopControlConnectResult.TrustMismatch(
                expected = config.expectedDesktopSpkiSha256,
                presented = presented,
            )
        }

    fun certificatePin(): String =
        "sha256/${config.expectedDesktopSpkiSha256.hexToBytes().toByteString().base64()}"

    private fun observeHeartbeat(nowElapsedNanos: Long) {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        lastHeartbeatElapsedNanos = nowElapsedNanos
        linkState = linkState.copy(
            phase = DesktopLinkPhase.CONNECTED,
            heartbeatAgeMillis = heartbeatAgeMillisAt(nowElapsedNanos),
        )
    }

    private fun livenessPhase(nowElapsedNanos: Long): DesktopLinkPhase {
        val age = ageNanosAt(nowElapsedNanos) ?: return DesktopLinkPhase.DISCONNECTED
        return when {
            age <= config.connectedTimeoutNanos -> DesktopLinkPhase.CONNECTED
            age <= config.disconnectedTimeoutNanos -> DesktopLinkPhase.DEGRADED
            else -> DesktopLinkPhase.DISCONNECTED
        }
    }

    private fun heartbeatAgeMillisAt(nowElapsedNanos: Long): Long? =
        ageNanosAt(nowElapsedNanos)?.div(NANOS_PER_MILLI)

    private fun ageNanosAt(nowElapsedNanos: Long): Long? =
        lastHeartbeatElapsedNanos?.let { (nowElapsedNanos - it).coerceAtLeast(0L) }

    private companion object {
        private const val FINGERPRINT_SUFFIX_LENGTH = 8
        private const val NANOS_PER_MILLI = 1_000_000L

        fun defaultSocket(request: Request, listener: WebSocketListener): DesktopControlSocket {
            val host = request.url.host
            val pin = request.header("X-BT-Gun-Desktop-Fingerprint")
                ?: throw IllegalArgumentException("missing expected desktop fingerprint")
            val client = OkHttpClient.Builder()
                .certificatePinner(
                    CertificatePinner.Builder()
                        .add(host, "sha256/${pin.hexToBytes().toByteString().base64()}")
                        .build(),
                )
                .build()
            return OkHttpDesktopControlSocket(client.newWebSocket(request, listener))
        }
    }
}

private fun String.toDesktopLinkPhase(): DesktopLinkPhase =
    when (this) {
        DesktopLinkPhase.CONNECTED.wireName -> DesktopLinkPhase.CONNECTED
        DesktopLinkPhase.DEGRADED.wireName -> DesktopLinkPhase.DEGRADED
        DesktopLinkPhase.DISCONNECTED.wireName -> DesktopLinkPhase.DISCONNECTED
        DesktopLinkPhase.TRUST_PROBLEM.wireName -> DesktopLinkPhase.TRUST_PROBLEM
        DesktopLinkPhase.PAIRING_PROOF.wireName -> DesktopLinkPhase.PAIRING_PROOF
        DesktopLinkPhase.CONNECTING.wireName -> DesktopLinkPhase.CONNECTING
        DesktopLinkPhase.SCANNING_QR.wireName -> DesktopLinkPhase.SCANNING_QR
        else -> DesktopLinkPhase.IDLE
    }

sealed interface DesktopControlConnectResult {
    data object Connected : DesktopControlConnectResult
    data class TrustMismatch(val expected: String, val presented: String) : DesktopControlConnectResult
}

sealed interface DesktopControlSendResult {
    data object Sent : DesktopControlSendResult
    data object NotConnected : DesktopControlSendResult
    data class Rejected(val error: ControlEnvelopeError) : DesktopControlSendResult
    data class Failed(val reason: String) : DesktopControlSendResult
}

private class OkHttpDesktopControlSocket(
    private val webSocket: WebSocket,
) : DesktopControlSocket {
    override fun send(text: String): Boolean = webSocket.send(text)

    override fun close() {
        webSocket.close(1000, "client closed")
    }
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && matches(Regex("[0-9a-fA-F]+"))) {
        "fingerprint must be hex"
    }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
