package com.btgun.desktop.control

import com.btgun.desktop.pairing.PairingAttemptResult
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.pairing.TrustedPairingSession
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

class ControlServer(
    private val registry: PairingSessionRegistry,
    private val maxMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
) {
    private var stopServer: (() -> Unit)? = null

    fun start(port: Int, host: String = "0.0.0.0"): ControlServer {
        val server = embeddedServer(Netty, host = host, port = port) {
            install(WebSockets) {
                maxFrameSize = maxMessageBytes.toLong()
                masking = false
            }
            routing {
                webSocket("/control") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val decoded = ControlEnvelopeCodec.decode(frame.readText(), maxBytes = maxMessageBytes)
                            if (decoded is ControlDecodeResult.Rejected) {
                                break
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
        stopServer = {
            server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        }
        return this
    }

    fun stop() {
        stopServer?.invoke()
        stopServer = null
    }

    fun handleAuthenticatedSocket(
        proofRequest: PairingProofRequest?,
        text: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ControlServerResult {
        if (proofRequest == null) {
            return ControlServerResult.RejectedPreAuth
        }
        val proof = registry.verifyProof(proofRequest, nowEpochMillis)
        if (proof !is PairingAttemptResult.Accepted) {
            return ControlServerResult.RejectedProof(proof)
        }
        return handleTrustedEnvelope(proof.trustedSession, text)
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

    companion object {
        const val DEFAULT_MAX_MESSAGE_BYTES = 16 * 1024
    }
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
