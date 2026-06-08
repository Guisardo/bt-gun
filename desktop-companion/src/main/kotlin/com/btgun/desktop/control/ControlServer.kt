package com.btgun.desktop.control

import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.pairing.LocalEndpoint
import com.btgun.desktop.pairing.PairingAttemptResult
import com.btgun.desktop.pairing.ManualPairingAttemptRequest
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.pairing.TrustedPairingSession
import com.btgun.desktop.security.DesktopTlsIdentity
import java.security.SecureRandom
import java.util.Base64
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ControlServer(
    private val registry: PairingSessionRegistry,
    private val maxMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
    private val udpHost: String? = null,
    private val udpPort: Int? = null,
    private val streamSecretFactory: () -> ByteArray = {
        ByteArray(STREAM_SECRET_BYTES).also(secureRandom::nextBytes)
    },
) {
    var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}
    var onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {}

    private var stopServer: (() -> Unit)? = null
    private var activeUdpHost: String = DEFAULT_UDP_HOST
    private var activeUdpPort: Int = DEFAULT_UDP_PORT

    fun start(port: Int, host: String = "0.0.0.0"): ControlServer {
        stop()
        val endpoint = registry.activeSession?.endpoint
        updateActiveUdpEndpoint(endpoint = endpoint, fallbackHost = host, fallbackPort = port)
        val certificateHost = endpoint?.host ?: host
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
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "pairing authentication rejected"))
                            return@webSocket
                        }
                        onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
                        sendSessionReady(trusted)
                        sendInputStreamConfig(trusted)
                        sendInitialMetadata(trusted)
                        val heartbeat = HeartbeatMonitor()
                        heartbeat.observePong(System.nanoTime())
                        val livenessJob = launch {
                            while (isActive) {
                                delay(LIVENESS_POLL_MILLIS)
                                val now = System.nanoTime()
                                sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PING, trusted.sid))
                                updateSessionState(heartbeat.stateAt(now))
                            }
                        }
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    when (val result = handleTrustedEnvelope(trusted, frame.readText())) {
                                        is ControlServerResult.Accepted -> handleAcceptedEnvelope(
                                            envelope = result.envelope,
                                            heartbeat = heartbeat,
                                            sendEnvelope = { envelope -> sendEnvelope(envelope) },
                                        )
                                        is ControlServerResult.RejectedEnvelope -> {
                                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, result.error.name))
                                            break
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        } finally {
                            livenessJob.cancel()
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
    ): ControlAuthenticationResult {
        val pendingEndpoint = registry.activeSession?.takeIf { it.sid == proofRequest.sid }?.endpoint
        return when (val proof = registry.verifyProof(proofRequest, nowEpochMillis)) {
            is PairingAttemptResult.Accepted -> {
                updateActiveUdpEndpoint(endpoint = pendingEndpoint)
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
    }

    fun authenticate(
        manualRequest: ManualPairingAttemptRequest,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ControlAuthenticationResult {
        val pendingEndpoint = registry.activeSession?.endpoint
        return when (val proof = registry.verifyManualCode(manualRequest, nowEpochMillis)) {
            is PairingAttemptResult.Accepted -> {
                updateActiveUdpEndpoint(endpoint = pendingEndpoint)
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

    suspend fun handleAcceptedEnvelope(
        envelope: ControlEnvelope,
        heartbeat: HeartbeatMonitor,
        nowElapsedNanos: Long = System.nanoTime(),
        sendEnvelope: suspend (ControlEnvelope) -> Unit = {},
    ) {
        when (envelope.type) {
            ControlMessageType.HEARTBEAT_PING -> {
                heartbeat.observePing(nowElapsedNanos)
                updateSessionState(heartbeat.stateAt(nowElapsedNanos))
                sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PONG, envelope.sessionId))
            }
            ControlMessageType.HEARTBEAT_PONG -> {
                heartbeat.observePong(nowElapsedNanos)
                updateSessionState(heartbeat.stateAt(nowElapsedNanos))
            }
            ControlMessageType.DIAGNOSTICS,
            ControlMessageType.PROFILE_METADATA,
            ControlMessageType.PAIRING_STATE,
            ControlMessageType.SESSION_READY,
            ControlMessageType.INPUT_STREAM_CONFIG,
            ControlMessageType.HAPTIC_RESULT,
            -> onControlEnvelopeAccepted(envelope)
            ControlMessageType.RESERVED_HAPTIC_COMMAND -> Unit
        }
    }

    fun inputStreamConfigEnvelopeFor(
        trustedSession: TrustedPairingSession,
        nowElapsedNanos: Long = System.nanoTime(),
    ): ControlEnvelope {
        val config = freshInputStreamConfig()
        return ControlEnvelope(
            v = 1,
            type = ControlMessageType.INPUT_STREAM_CONFIG,
            msgId = "desktop-input-stream-config",
            sessionId = trustedSession.sid,
            seq = 0L,
            sentElapsedNanos = nowElapsedNanos,
            body = JsonObject(
                mapOf(
                    "streamSessionIdHex" to JsonPrimitive(config.streamSessionIdHex),
                    "udpHost" to JsonPrimitive(config.udpHost),
                    "udpPort" to JsonPrimitive(config.udpPort),
                    "hmacSha256KeyBase64Url" to JsonPrimitive(config.hmacSha256KeyBase64Url),
                    "snapshotHz" to JsonPrimitive(config.snapshotHz),
                    "frameAgeLimitMs" to JsonPrimitive(config.frameAgeLimitMs),
                    "streamTimeoutMs" to JsonPrimitive(config.streamTimeoutMs),
                    "controlDisconnectGraceMs" to JsonPrimitive(config.controlDisconnectGraceMs),
                ),
            ),
        )
    }

    fun hapticCommandEnvelopeFor(
        trustedSession: TrustedPairingSession,
        command: HapticCommand,
        nowElapsedNanos: Long = System.nanoTime(),
    ): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.RESERVED_HAPTIC_COMMAND,
            msgId = "desktop-haptic-command-${command.commandId}",
            sessionId = trustedSession.sid,
            seq = 0L,
            sentElapsedNanos = nowElapsedNanos,
            body = command.toJsonBody(),
        )

    private fun authenticate(headers: io.ktor.http.Headers, nowEpochMillis: Long): TrustedPairingSession? {
        val androidNonce = headers[HEADER_ANDROID_NONCE] ?: return null
        val desktopFingerprint = headers[HEADER_DESKTOP_FINGERPRINT] ?: return null
        val manualCode = headers[HEADER_MANUAL_CODE]
        val result = if (manualCode != null) {
            authenticate(
                ManualPairingAttemptRequest(
                    androidNonce = androidNonce,
                    desktopSpkiSha256 = desktopFingerprint,
                    code = manualCode,
                ),
                nowEpochMillis,
            )
        } else {
            authenticate(
                PairingProofRequest(
                    sid = headers[HEADER_SESSION] ?: return null,
                    androidNonce = androidNonce,
                    desktopSpkiSha256 = desktopFingerprint,
                    proofHex = headers[HEADER_PAIRING_PROOF] ?: return null,
                ),
                nowEpochMillis,
            )
        }
        return (result as? ControlAuthenticationResult.Accepted)?.trustedSession
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendSessionReady(
        trustedSession: TrustedPairingSession,
    ) = sendEnvelope(
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.SESSION_READY,
            msgId = "desktop-session-ready",
            sessionId = trustedSession.sid,
            seq = 0L,
            sentElapsedNanos = System.nanoTime(),
        )
    )

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendInputStreamConfig(
        trustedSession: TrustedPairingSession,
    ) = sendEnvelope(inputStreamConfigEnvelopeFor(trustedSession))

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendInitialMetadata(
        trustedSession: TrustedPairingSession,
    ) {
        sendEnvelope(
            ControlEnvelope(
                v = 1,
                type = ControlMessageType.DIAGNOSTICS,
                msgId = "desktop-diagnostics",
                sessionId = trustedSession.sid,
                seq = 0L,
                sentElapsedNanos = System.nanoTime(),
                body = JsonObject(
                    mapOf(
                        "sessionState" to JsonPrimitive(DESKTOP_SESSION_CONNECTED),
                        "desktopIdentitySuffix" to JsonPrimitive(trustedSession.desktopSpkiSha256.takeLast(8)),
                        "heartbeatAgeMillis" to JsonPrimitive(0L),
                        "lastControlError" to JsonPrimitive("none"),
                    ),
                ),
            ),
        )
        sendEnvelope(
            ControlEnvelope(
                v = 1,
                type = ControlMessageType.PROFILE_METADATA,
                msgId = "desktop-profile-metadata",
                sessionId = trustedSession.sid,
                seq = 0L,
                sentElapsedNanos = System.nanoTime(),
                body = JsonObject(
                    mapOf(
                        "profileId" to JsonPrimitive(DEFAULT_PROFILE_ID),
                        "displayName" to JsonPrimitive(DEFAULT_PROFILE_NAME),
                        "revision" to JsonPrimitive(DEFAULT_PROFILE_REVISION),
                    ),
                ),
            ),
        )
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendEnvelope(envelope: ControlEnvelope) {
        send(Frame.Text(ControlEnvelopeCodec.encode(envelope)))
    }

    private fun heartbeatEnvelope(type: ControlMessageType, sessionId: String): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = type,
            msgId = "desktop-${type.wireName}",
            sessionId = sessionId,
            seq = 0L,
            sentElapsedNanos = System.nanoTime(),
        )

    private fun updateSessionState(state: LivenessState) {
        onSessionStateChanged(
            when (state) {
                LivenessState.CONNECTED -> ControlServerSessionState.AUTHENTICATED
                LivenessState.DEGRADED -> ControlServerSessionState.DEGRADED
                LivenessState.DISCONNECTED -> ControlServerSessionState.DISCONNECTED
            },
        )
    }

    private fun freshInputStreamConfig(): InputStreamConfig =
        InputStreamConfig(
            streamSessionIdHex = streamSecretFactory().copyOf(STREAM_ID_BYTES).toHex(),
            udpHost = activeUdpHost,
            udpPort = activeUdpPort,
            hmacSha256KeyBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(
                streamSecretFactory().copyOf(STREAM_SECRET_BYTES),
            ),
            snapshotHz = DEFAULT_SNAPSHOT_HZ,
            frameAgeLimitMs = DEFAULT_FRAME_AGE_LIMIT_MILLIS,
            streamTimeoutMs = DEFAULT_STREAM_TIMEOUT_MILLIS,
            controlDisconnectGraceMs = DEFAULT_CONTROL_DISCONNECT_GRACE_MILLIS,
        )

    private fun updateActiveUdpEndpoint(
        endpoint: LocalEndpoint?,
        fallbackHost: String = DEFAULT_UDP_HOST,
        fallbackPort: Int = DEFAULT_UDP_PORT,
    ) {
        activeUdpHost = udpHost ?: endpoint?.host ?: fallbackHost
        activeUdpPort = udpPort ?: endpoint?.port ?: fallbackPort
    }

    companion object {
        const val DEFAULT_MAX_MESSAGE_BYTES = 16 * 1024
        const val DEFAULT_UDP_HOST = "127.0.0.1"
        const val DEFAULT_UDP_PORT = 41731
        private const val LIVENESS_POLL_MILLIS = 500L
        private const val STREAM_ID_BYTES = 16
        private const val STREAM_SECRET_BYTES = 32
        private const val DEFAULT_SNAPSHOT_HZ = 60
        private const val DEFAULT_FRAME_AGE_LIMIT_MILLIS = 150L
        private const val DEFAULT_STREAM_TIMEOUT_MILLIS = 250L
        private const val DEFAULT_CONTROL_DISCONNECT_GRACE_MILLIS = 1500L
        private const val DEFAULT_PROFILE_ID = "default"
        private const val DEFAULT_PROFILE_NAME = "Default profile"
        private const val DEFAULT_PROFILE_REVISION = 1L
        private const val DESKTOP_SESSION_CONNECTED = "connected"
        const val HEADER_DESKTOP_FINGERPRINT = "X-BT-Gun-Desktop-Fingerprint"
        const val HEADER_SESSION = "X-BT-Gun-Session"
        const val HEADER_ANDROID_NONCE = "X-BT-Gun-Android-Nonce"
        const val HEADER_PAIRING_PROOF = "X-BT-Gun-Pairing-Proof"
        const val HEADER_MANUAL_CODE = "X-BT-Gun-Manual-Code"
        private val secureRandom = SecureRandom()
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
