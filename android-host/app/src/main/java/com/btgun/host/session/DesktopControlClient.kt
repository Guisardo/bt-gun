package com.btgun.host.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.Locale
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

        fun fromManualPayload(
            payload: ManualPairingPayload,
            trustedDesktop: TrustedDesktopMetadata,
            androidNonce: String,
        ): DesktopControlConnectionRequest {
            val fingerprint = trustedDesktop.fingerprintSha256.lowercase(Locale.US)
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
                        oneTimeMaterial = payload.code,
                    ),
                ),
                displayName = trustedDesktop.displayName,
                host = payload.host,
                port = payload.port,
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
    private var authenticated: Boolean = false

    fun connect(
        proofRequest: ControlProofRequest,
        onAuthenticated: () -> Unit = {},
        onConnectionFailure: (String) -> Unit = {},
        onLinkStateChanged: (DesktopLinkState) -> Unit = {},
        onProfileMetadataReceived: (ProfileMetadata) -> Unit = {},
    ): DesktopControlConnectResult {
        val trust = verifyPresentedFingerprint(proofRequest.desktopSpkiSha256)
        if (trust is DesktopControlConnectResult.TrustMismatch) {
            linkState = linkState.copy(
                phase = DesktopLinkPhase.TRUST_PROBLEM,
                lastControlError = "desktop fingerprint mismatch",
            )
            return trust
        }
        authenticated = false
        val request = Request.Builder()
            .url(config.url)
            .header("X-BT-Gun-Desktop-Fingerprint", config.expectedDesktopSpkiSha256)
            .header("X-BT-Gun-Session", proofRequest.sid)
            .header("X-BT-Gun-Android-Nonce", proofRequest.androidNonce)
            .header("X-BT-Gun-Pairing-Proof", proofRequest.proofHex)
            .build()

        socket = socketFactory(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    linkState = linkState.copy(phase = DesktopLinkPhase.PAIRING_PROOF)
                    onLinkStateChanged(linkState)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (
                        handleServerEnvelope(
                            text = text,
                            expectedSessionId = proofRequest.sid,
                            sendEnvelope = { envelope -> socket?.send(ControlEnvelopeCodec.encode(envelope)) == true },
                            onLinkStateChanged = onLinkStateChanged,
                            onProfileMetadataReceived = onProfileMetadataReceived,
                        )
                    ) {
                        if (!authenticated) {
                            authenticated = true
                            onAuthenticated()
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    authenticated = false
                    val reason = t.javaClass.simpleName.ifBlank { "control channel failed" }
                    linkState = linkState.copy(
                        phase = DesktopLinkPhase.DISCONNECTED,
                        lastControlError = reason,
                    )
                    onLinkStateChanged(linkState)
                    onConnectionFailure(reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    val wasAuthenticated = authenticated
                    authenticated = false
                    val closeReason = reason.ifBlank { "control channel closed" }
                    linkState = linkState.copy(
                        phase = DesktopLinkPhase.DISCONNECTED,
                        lastControlError = closeReason,
                    )
                    onLinkStateChanged(linkState)
                    if (!wasAuthenticated) {
                        onConnectionFailure(closeReason)
                    }
                }
            },
        )
        linkState = linkState.copy(
            phase = DesktopLinkPhase.CONNECTING,
            fingerprintSuffix = config.expectedDesktopSpkiSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
        )
        return DesktopControlConnectResult.Connecting
    }

    fun send(envelope: ControlEnvelope): DesktopControlSendResult {
        val encoded = ControlEnvelopeCodec.encode(envelope)
        return when (val decoded = ControlEnvelopeCodec.decode(encoded, maxBytes = Int.MAX_VALUE)) {
            is ControlDecodeResult.Rejected -> DesktopControlSendResult.Rejected(decoded.error)
            is ControlDecodeResult.Accepted -> {
                if (encoded.toByteArray(Charsets.UTF_8).size > config.maxMessageBytes) {
                    return DesktopControlSendResult.Rejected(ControlEnvelopeError.OVERSIZED)
                }
                if (!authenticated) {
                    return DesktopControlSendResult.NotConnected
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
        authenticated = false
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

    private fun handleServerEnvelope(
        text: String,
        expectedSessionId: String,
        sendEnvelope: (ControlEnvelope) -> Boolean,
        onLinkStateChanged: (DesktopLinkState) -> Unit,
        onProfileMetadataReceived: (ProfileMetadata) -> Unit,
    ): Boolean =
        when (val decoded = ControlEnvelopeCodec.decode(text, maxBytes = config.maxMessageBytes)) {
            is ControlDecodeResult.Rejected -> {
                recordControlError(decoded.error.name.lowercase(Locale.US))
                false
            }
            is ControlDecodeResult.Accepted -> {
                if (decoded.envelope.sessionId != expectedSessionId) {
                    recordControlError("session mismatch")
                    false
                } else {
                    when (decoded.envelope.type) {
                        ControlMessageType.SESSION_READY -> {
                            linkState = linkState.copy(phase = DesktopLinkPhase.CONNECTED)
                            true
                        }
                        ControlMessageType.HEARTBEAT_PING -> {
                            observeHeartbeat(System.nanoTime())
                            sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PONG, expectedSessionId))
                            onLinkStateChanged(linkState)
                            false
                        }
                        ControlMessageType.HEARTBEAT_PONG -> {
                            observeHeartbeat(System.nanoTime())
                            onLinkStateChanged(linkState)
                            false
                        }
                        ControlMessageType.DIAGNOSTICS -> {
                            decoded.envelope.body.toDiagnostics()?.let { diagnostics ->
                                applyDiagnostics(diagnostics)
                                onLinkStateChanged(linkState)
                            }
                            false
                        }
                        ControlMessageType.PROFILE_METADATA -> {
                            decoded.envelope.body.toProfileMetadata()?.let(onProfileMetadataReceived)
                            false
                        }
                        else -> false
                    }
                }
            }
        }

    private fun heartbeatEnvelope(type: ControlMessageType, sessionId: String): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = type,
            msgId = "android-${type.wireName}",
            sessionId = sessionId,
            seq = 0L,
            sentElapsedNanos = System.nanoTime(),
        )

    private fun JsonObject.toDiagnostics(): ControlDiagnostics? {
        val sessionState = stringField("sessionState") ?: return null
        val desktopIdentitySuffix = stringField("desktopIdentitySuffix") ?: return null
        return ControlDiagnostics(
            sessionState = sessionState,
            desktopIdentitySuffix = desktopIdentitySuffix,
            heartbeatAgeMillis = nullableLongField("heartbeatAgeMillis"),
            lastControlError = stringField("lastControlError"),
        )
    }

    private fun JsonObject.toProfileMetadata(): ProfileMetadata? {
        val profileId = stringField("profileId") ?: return null
        val displayName = stringField("displayName") ?: return null
        val revision = longField("revision") ?: return null
        return ProfileMetadata(
            profileId = profileId,
            displayName = displayName,
            revision = revision,
        )
    }

    private fun JsonObject.stringField(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.longField(name: String): Long? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull

    private fun JsonObject.nullableLongField(name: String): Long? =
        if (containsKey(name)) longField(name) else null

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
            val trustManager = PinnedSpkiTrustManager(pin)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
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
    data object Connecting : DesktopControlConnectResult
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

private class PinnedSpkiTrustManager(
    expectedDesktopSpkiSha256: String,
) : X509TrustManager {
    private val expected = expectedDesktopSpkiSha256.lowercase(Locale.US)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("client certificates are not accepted")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("missing desktop certificate")
        val presented = MessageDigest.getInstance("SHA-256")
            .digest(leaf.publicKey.encoded)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        if (presented != expected) {
            throw CertificateException("desktop SPKI fingerprint mismatch")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && matches(Regex("[0-9a-fA-F]+"))) {
        "fingerprint must be hex"
    }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
