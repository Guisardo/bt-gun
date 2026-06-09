package com.btgun.desktop.control

import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.pairing.LocalEndpoint
import com.btgun.desktop.pairing.PairingAttemptResult
import com.btgun.desktop.pairing.ManualPairingAttemptRequest
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.pairing.TrustedPairingSession
import com.btgun.desktop.security.DesktopTlsIdentity
import com.btgun.desktop.transport.DesktopUdpInputRuntime
import com.btgun.desktop.transport.InputReplayRejectReason
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpInputRuntime
import com.btgun.desktop.transport.UdpInputRuntimeStartResult
import com.btgun.desktop.transport.UdpReceivedInput
import java.security.SecureRandom
import java.util.UUID
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
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
    udpRuntime: UdpInputRuntime? = null,
) {
    var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}
    var onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {}
    var onUdpInputReceived: (UdpReceivedInput) -> Unit = {}
    var onUdpInputRejected: (InputReplayRejectReason) -> Unit = {}
    var onUdpInputStateChanged: (InputStreamLifecycleState) -> Unit = {}
    var onHapticResultReceived: (HapticResult) -> Unit = {}

    private var stopServer: (() -> Unit)? = null
    private var activeUdpHost: String = DEFAULT_UDP_HOST
    private var activeUdpPort: Int = DEFAULT_UDP_PORT
    private val stateLock = Any()
    private val udpRuntime: UdpInputRuntime = udpRuntime ?: DesktopUdpInputRuntime(
        onInput = { input -> onUdpInputReceived(input) },
        onRejected = { reason -> onUdpInputRejected(reason) },
        onStateChanged = { state -> onUdpInputStateChanged(state) },
    )
    private var activeControlSession: ActiveControlSession? = null
    private var controlDisconnectSignaledToken: String? = null
    private val pendingHapticCommandIds = mutableSetOf<String>()
    private var activeStartedHapticCommandId: String? = null

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
                        val streamStart = startInputStreamForTrustedSession(trusted)
                        if (streamStart is ControlInputStreamStartResult.Failed) {
                            onSessionStateChanged(ControlServerSessionState.DISCONNECTED)
                            close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, streamStart.reason))
                            return@webSocket
                        }
                        val streamConfig = (streamStart as ControlInputStreamStartResult.Started).config
                        val token = UUID.randomUUID().toString()
                        val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
                        val closeSignal = Channel<Unit>(capacity = 1)
                        val outboundJob = launch {
                            for (envelope in outbound) {
                                sendEnvelope(envelope)
                            }
                        }
                        val closeJob = launch {
                            closeSignal.receive()
                            close(CloseReason(CloseReason.Codes.NORMAL, "session replaced"))
                        }
                        registerActiveControlSession(
                            ActiveControlSession(
                                token = token,
                                trustedSession = trusted,
                                outbound = outbound,
                                closeSignal = closeSignal,
                            ),
                        )
                        sendSessionReady(trusted)
                        sendInputStreamConfig(trusted, streamConfig)
                        sendInitialMetadata(trusted)
                        onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
                        val heartbeat = HeartbeatMonitor()
                        heartbeat.observePong(System.nanoTime())
                        val livenessJob = launch {
                            while (isActive) {
                                delay(LIVENESS_POLL_MILLIS)
                                val now = System.nanoTime()
                                sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PING, trusted.sid))
                                updateSessionState(heartbeat.stateAt(now), controlSessionToken = token, nowElapsedNanos = now)
                            }
                        }
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    when (val result = handleTrustedEnvelope(trusted, frame.readText())) {
                                        is ControlServerResult.Accepted -> handleAcceptedEnvelope(
                                            envelope = result.envelope,
                                            heartbeat = heartbeat,
                                            controlSessionToken = token,
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
                            outboundJob.cancel()
                            closeJob.cancel()
                            clearActiveControlSession(token, nowElapsedNanos = System.nanoTime())
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
        clearAllActiveControlSessions()
        udpRuntime.stop(reason = "server stopped")
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
        controlSessionToken: String? = null,
        sendEnvelope: suspend (ControlEnvelope) -> Unit = {},
    ) {
        when (envelope.type) {
            ControlMessageType.HEARTBEAT_PING -> {
                heartbeat.observePing(nowElapsedNanos)
                updateSessionState(heartbeat.stateAt(nowElapsedNanos), controlSessionToken, nowElapsedNanos)
                sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PONG, envelope.sessionId))
            }
            ControlMessageType.HEARTBEAT_PONG -> {
                heartbeat.observePong(nowElapsedNanos)
                updateSessionState(heartbeat.stateAt(nowElapsedNanos), controlSessionToken, nowElapsedNanos)
            }
            ControlMessageType.DIAGNOSTICS,
            ControlMessageType.PROFILE_METADATA,
            ControlMessageType.PAIRING_STATE,
            ControlMessageType.SESSION_READY,
            ControlMessageType.INPUT_STREAM_CONFIG,
            -> onControlEnvelopeAccepted(envelope)
            ControlMessageType.HAPTIC_RESULT -> handleHapticResult(envelope, controlSessionToken)
            ControlMessageType.RESERVED_HAPTIC_COMMAND -> Unit
        }
    }

    fun inputStreamConfigEnvelopeFor(
        trustedSession: TrustedPairingSession,
        nowElapsedNanos: Long = System.nanoTime(),
    ): ControlEnvelope {
        val config = freshInputStreamConfig()
        return inputStreamConfigEnvelopeFor(trustedSession, config, nowElapsedNanos)
    }

    internal fun startInputStreamForTrustedSession(
        trustedSession: TrustedPairingSession,
        nowElapsedNanos: Long = System.nanoTime(),
    ): ControlInputStreamStartResult {
        val config = freshInputStreamConfig()
        return when (val started = udpRuntime.start(trustedSession = trustedSession.sid, config = config)) {
            UdpInputRuntimeStartResult.Started ->
                ControlInputStreamStartResult.Started(
                    config = config,
                    envelope = inputStreamConfigEnvelopeFor(trustedSession, config, nowElapsedNanos),
                )
            is UdpInputRuntimeStartResult.Failed -> ControlInputStreamStartResult.Failed(started.reason)
        }
    }

    private fun inputStreamConfigEnvelopeFor(
        trustedSession: TrustedPairingSession,
        config: InputStreamConfig,
        nowElapsedNanos: Long,
    ): ControlEnvelope =
        ControlEnvelope(
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

    fun sendHapticCommand(
        command: HapticCommand,
        nowElapsedNanos: Long = System.nanoTime(),
    ): HapticSendResult {
        val active = synchronized(stateLock) { activeControlSession }
            ?: return HapticSendResult.NoActiveSession
        val envelope = hapticCommandEnvelopeFor(active.trustedSession, command, nowElapsedNanos)
        val encoded = ControlEnvelopeCodec.encode(envelope)
        when (val decoded = ControlEnvelopeCodec.decode(encoded, maxBytes = Int.MAX_VALUE)) {
            is ControlDecodeResult.Rejected -> return HapticSendResult.Rejected(decoded.error)
            is ControlDecodeResult.Accepted -> Unit
        }
        if (encoded.toByteArray(Charsets.UTF_8).size > maxMessageBytes) {
            return HapticSendResult.Rejected(ControlEnvelopeError.OVERSIZED)
        }
        val stillActive = synchronized(stateLock) {
            if (activeControlSession?.token == active.token) {
                pendingHapticCommandIds.add(command.commandId)
                activeStartedHapticCommandId = null
                true
            } else {
                false
            }
        }
        if (!stillActive) {
            return HapticSendResult.NoActiveSession
        }
        return if (active.outbound.trySend(envelope).isSuccess) {
            HapticSendResult.Sent
        } else {
            synchronized(stateLock) {
                pendingHapticCommandIds.remove(command.commandId)
            }
            HapticSendResult.Failed("active control socket rejected haptic command")
        }
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
        config: InputStreamConfig,
    ) = sendEnvelope(inputStreamConfigEnvelopeFor(trustedSession, config, System.nanoTime()))

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

    internal fun updateSessionState(
        state: LivenessState,
        controlSessionToken: String? = null,
        nowElapsedNanos: Long = System.nanoTime(),
    ) {
        if (state == LivenessState.DISCONNECTED && controlSessionToken != null) {
            signalControlDisconnected(controlSessionToken, nowElapsedNanos)
        }
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

    internal fun registerActiveControlSessionForTest(
        trustedSession: TrustedPairingSession,
        outbound: SendChannel<ControlEnvelope>,
        token: String = "test-control-session",
    ): String {
        registerActiveControlSession(
            ActiveControlSession(
                token = token,
                trustedSession = trustedSession,
                outbound = outbound,
                closeSignal = Channel(capacity = 1),
            ),
        )
        return token
    }

    private fun registerActiveControlSession(session: ActiveControlSession) {
        val previous = synchronized(stateLock) {
            val old = activeControlSession
            activeControlSession = session
            controlDisconnectSignaledToken = null
            pendingHapticCommandIds.clear()
            activeStartedHapticCommandId = null
            old
        }
        previous?.outbound?.close()
        previous?.closeSignal?.trySend(Unit)
    }

    private fun clearActiveControlSession(token: String, nowElapsedNanos: Long) {
        val shouldDisconnect = synchronized(stateLock) {
            val active = activeControlSession
            if (active?.token != token) {
                false
            } else {
                val alreadySignaled = controlDisconnectSignaledToken == token
                activeControlSession = null
                controlDisconnectSignaledToken = null
                pendingHapticCommandIds.clear()
                activeStartedHapticCommandId = null
                active.outbound.close()
                active.closeSignal.trySend(Unit)
                !alreadySignaled
            }
        }
        if (shouldDisconnect) {
            udpRuntime.onControlDisconnected(nowElapsedNanos)
            onSessionStateChanged(ControlServerSessionState.DISCONNECTED)
        }
    }

    private fun clearAllActiveControlSessions() {
        val previous = synchronized(stateLock) {
            val active = activeControlSession
            activeControlSession = null
            controlDisconnectSignaledToken = null
            pendingHapticCommandIds.clear()
            activeStartedHapticCommandId = null
            active
        }
        previous?.outbound?.close()
        previous?.closeSignal?.trySend(Unit)
    }

    private fun handleHapticResult(envelope: ControlEnvelope, controlSessionToken: String?) {
        val result = HapticResult.fromJsonBody(envelope.body) ?: return
        val shouldAccept = synchronized(stateLock) {
            val active = activeControlSession
            active != null &&
                active.trustedSession.sid == envelope.sessionId &&
                (controlSessionToken == null || active.token == controlSessionToken) &&
                when (result.status) {
                    HapticResultStatus.STARTED -> {
                        pendingHapticCommandIds.remove(result.commandId).also { accepted ->
                            if (accepted) {
                                activeStartedHapticCommandId = result.commandId
                            }
                        }
                    }
                    HapticResultStatus.CANCELLED -> {
                        val accepted = activeStartedHapticCommandId == result.commandId || pendingHapticCommandIds.remove(result.commandId)
                        if (accepted && activeStartedHapticCommandId == result.commandId) {
                            activeStartedHapticCommandId = null
                        }
                        accepted
                    }
                    else -> {
                        val accepted = pendingHapticCommandIds.remove(result.commandId) || activeStartedHapticCommandId == result.commandId
                        if (accepted && activeStartedHapticCommandId == result.commandId) {
                            activeStartedHapticCommandId = null
                        }
                        accepted
                    }
                }
        }
        if (!shouldAccept) {
            return
        }
        onHapticResultReceived(result)
        onControlEnvelopeAccepted(envelope)
    }

    private fun signalControlDisconnected(token: String, nowElapsedNanos: Long) {
        val shouldSignal = synchronized(stateLock) {
            val active = activeControlSession
            if (active?.token == token && controlDisconnectSignaledToken != token) {
                controlDisconnectSignaledToken = token
                true
            } else {
                false
            }
        }
        if (shouldSignal) {
            udpRuntime.onControlDisconnected(nowElapsedNanos)
            clearActiveControlSession(token, nowElapsedNanos)
        }
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

sealed interface HapticSendResult {
    data object Sent : HapticSendResult
    data object NoActiveSession : HapticSendResult
    data class Rejected(val error: ControlEnvelopeError) : HapticSendResult
    data class Failed(val reason: String) : HapticSendResult
}

sealed interface ControlInputStreamStartResult {
    data class Started(
        val config: InputStreamConfig,
        val envelope: ControlEnvelope,
    ) : ControlInputStreamStartResult

    data class Failed(val reason: String) : ControlInputStreamStartResult
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

private data class ActiveControlSession(
    val token: String,
    val trustedSession: TrustedPairingSession,
    val outbound: SendChannel<ControlEnvelope>,
    val closeSignal: SendChannel<Unit>,
)
