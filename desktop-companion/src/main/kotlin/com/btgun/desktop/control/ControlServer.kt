package com.btgun.desktop.control

import com.btgun.desktop.pairing.PairingAttemptResult
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.pairing.TrustedPairingSession
import com.btgun.desktop.security.DesktopTlsIdentity
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send

class ControlServer(
    private val registry: PairingSessionRegistry,
    private val maxMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
) {
    var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}

    private var stopServer: (() -> Unit)? = null

    fun start(port: Int, host: String = "0.0.0.0"): ControlServer {
        stop()
        val certificateHost = registry.activeSession?.endpoint?.host ?: host
        val tls = DesktopTlsIdentity.keyStoreFor(registry.desktopIdentity(), certificateHost)
        val server = embeddedServer(
            Netty,
            applicationEnvironment {},
            {
                sslConnector(
                    keyStore = tls.keyStore,
                    keyAlias = tls.keyAlias,
                    keyStorePassword = { tls.password },
                    privateKeyPassword = { tls.password },
                ) {
                    this.host = host
                    this.port = port
                }
            },
            {
                install(WebSockets) {
                    maxFrameSize = maxMessageBytes.toLong()
                    masking = false
                }
                routing {
                    webSocket("/control") {
                        onSessionStateChanged(ControlServerSessionState.ANDROID_CONNECTED)
                        val trusted = authenticate(headers = call.request.headers, nowEpochMillis = System.currentTimeMillis())
                        if (trusted == null) {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "pairing proof rejected"))
                            return@webSocket
                        }
                        onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
                        sendSessionReady(trusted)
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val result = handleTrustedEnvelope(trusted, frame.readText())
                                    if (result is ControlServerResult.RejectedEnvelope) {
                                        break
                                    }
                                }
                            }
                        } finally {
                            onSessionStateChanged(ControlServerSessionState.DISCONNECTED)
                        }
                    }
                }
            },
        ).start(wait = false)
        stopServer = {
            server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        }
        onSessionStateChanged(ControlServerSessionState.STARTED)
        return this
    }

    fun stop() {
        stopServer?.invoke()
        stopServer = null
        onSessionStateChanged(ControlServerSessionState.STOPPED)
    }

    fun handleAuthenticatedSocket(
        proofRequest: PairingProofRequest?,
        text: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ControlServerResult {
        if (proofRequest == null) {
            return ControlServerResult.RejectedPreAuth
        }
        return when (val auth = authenticate(proofRequest, nowEpochMillis)) {
            is ControlAuthenticationResult.Accepted -> handleTrustedEnvelope(auth.trustedSession, text)
            is ControlAuthenticationResult.Rejected -> ControlServerResult.RejectedProof(auth.reason)
        }
    }

    fun authenticate(
        proofRequest: PairingProofRequest,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ControlAuthenticationResult =
        when (val proof = registry.verifyProof(proofRequest, nowEpochMillis)) {
            is PairingAttemptResult.Accepted -> {
                onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
                ControlAuthenticationResult.Accepted(proof.trustedSession)
            }
            else -> {
                if (proof is PairingAttemptResult.RejectedRateLimited) {
                    onSessionStateChanged(ControlServerSessionState.RATE_LIMITED)
                }
                ControlAuthenticationResult.Rejected(proof)
            }
        }

    fun handleTrustedEnvelope(trustedSession: TrustedPairingSession, text: String): ControlServerResult =
        when (val decoded = ControlEnvelopeCodec.decode(text, maxBytes = maxMessageBytes)) {
            is ControlDecodeResult.Rejected -> ControlServerResult.RejectedEnvelope(decoded.error)
            is ControlDecodeResult.Accepted -> {
                if (decoded.envelope.sessionId != trustedSession.sid) {
                    ControlServerResult.RejectedEnvelope(ControlEnvelopeError.INVALID_FIELD)
                } else {
                    ControlServerResult.Accepted(decoded.envelope, trustedSession)
                }
            }
        }

    private fun authenticate(headers: io.ktor.http.Headers, nowEpochMillis: Long): TrustedPairingSession? {
        val request = PairingProofRequest(
            sid = headers[HEADER_SESSION] ?: return null,
            androidNonce = headers[HEADER_ANDROID_NONCE] ?: return null,
            desktopSpkiSha256 = headers[HEADER_DESKTOP_FINGERPRINT] ?: return null,
            proofHex = headers[HEADER_PAIRING_PROOF] ?: return null,
        )
        return (authenticate(request, nowEpochMillis) as? ControlAuthenticationResult.Accepted)?.trustedSession
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendSessionReady(
        trustedSession: TrustedPairingSession,
    ) {
        send(
            Frame.Text(
                ControlEnvelopeCodec.encode(
                    ControlEnvelope(
                        v = 1,
                        type = ControlMessageType.SESSION_READY,
                        msgId = "desktop-session-ready",
                        sessionId = trustedSession.sid,
                        seq = 0L,
                        sentElapsedNanos = System.nanoTime(),
                    ),
                ),
            ),
        )
    }

    companion object {
        const val DEFAULT_MAX_MESSAGE_BYTES = 16 * 1024
        const val HEADER_DESKTOP_FINGERPRINT = "X-BT-Gun-Desktop-Fingerprint"
        const val HEADER_SESSION = "X-BT-Gun-Session"
        const val HEADER_ANDROID_NONCE = "X-BT-Gun-Android-Nonce"
        const val HEADER_PAIRING_PROOF = "X-BT-Gun-Pairing-Proof"
    }
}

enum class ControlServerSessionState {
    STARTED,
    STOPPED,
    ANDROID_CONNECTED,
    AUTHENTICATED,
    DEGRADED,
    DISCONNECTED,
    RATE_LIMITED,
}

sealed interface ControlServerResult {
    data object RejectedPreAuth : ControlServerResult
    data class RejectedProof(val reason: PairingAttemptResult) : ControlServerResult
    data class RejectedEnvelope(val error: ControlEnvelopeError) : ControlServerResult
    data class Accepted(
        val envelope: ControlEnvelope,
        val trustedSession: TrustedPairingSession,
    ) : ControlServerResult
}

sealed interface ControlAuthenticationResult {
    data class Accepted(val trustedSession: TrustedPairingSession) : ControlAuthenticationResult
    data class Rejected(val reason: PairingAttemptResult) : ControlAuthenticationResult
}
