package com.btgun.host.session

import android.os.SystemClock
import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.HapticResult
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.transport.InputFrameFormat
import com.btgun.host.transport.InputStreamConfig
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
    override val androidNonce: String,
    override val desktopSpkiSha256: String,
    val proofHex: String,
) : DesktopControlAuthRequest {
    override val expectedSessionId: String = sid
}

data class ManualCodeAuthRequest(
    override val androidNonce: String,
    override val desktopSpkiSha256: String,
    val code: String,
) : DesktopControlAuthRequest {
    override val expectedSessionId: String? = null
}

sealed interface DesktopControlAuthRequest {
    val androidNonce: String
    val desktopSpkiSha256: String
    val expectedSessionId: String?
}

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
    val source: String = "android",
    val rawDebugEnabled: Boolean = false,
)

data class DesktopControlConnectionRequest(
    val config: DesktopControlClientConfig,
    val authRequest: DesktopControlAuthRequest,
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
                authRequest = ControlProofRequest(
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
                authRequest = ManualCodeAuthRequest(
                    androidNonce = androidNonce,
                    desktopSpkiSha256 = fingerprint,
                    code = payload.code,
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
    private val elapsedRealtimeNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() },
) {
    private var socket: DesktopControlSocket? = null
    private var lastHeartbeatElapsedNanos: Long? = null
    private var linkState: DesktopLinkState = DesktopLinkState()
    private var authenticated: Boolean = false
    private var sessionReady: Boolean = false
    private var expectedSessionId: String? = null
    private var trustedSessionId: String? = null
    private var negotiatedInputFrameFormats: List<InputFrameFormat> = listOf(InputFrameFormat.V1)
    private val inboundSequences = ControlSequenceTracker()
    private val outboundSequences = ControlSequenceGenerator()

    fun connect(
        authRequest: DesktopControlAuthRequest,
        onAuthenticated: () -> Unit = {},
        onConnectionFailure: (String) -> Unit = {},
        onLinkStateChanged: (DesktopLinkState) -> Unit = {},
        onProfileMetadataReceived: (ProfileMetadata) -> Unit = {},
        onInputStreamConfigReceived: (InputStreamConfig) -> Unit = {},
        onHapticCommandReceived: (DesktopHapticCommand, Long) -> HapticResult = { command, observedElapsedNanos ->
            HapticResult(
                commandId = command.commandId,
                status = HapticResultStatus.UNSUPPORTED,
                detail = HapticResultStatus.UNSUPPORTED.wireName,
                observedElapsedNanos = observedElapsedNanos,
            )
        },
    ): DesktopControlConnectResult {
        val trust = verifyPresentedFingerprint(authRequest.desktopSpkiSha256)
        if (trust is DesktopControlConnectResult.TrustMismatch) {
            linkState = linkState.copy(
                phase = DesktopLinkPhase.TRUST_PROBLEM,
                lastControlError = "desktop fingerprint mismatch",
            )
            return trust
        }
        authenticated = false
        sessionReady = false
        expectedSessionId = authRequest.expectedSessionId
        trustedSessionId = null
        negotiatedInputFrameFormats = listOf(InputFrameFormat.V1)
        inboundSequences.reset()
        outboundSequences.reset()
        lastHeartbeatElapsedNanos = null
        val requestBuilder = Request.Builder()
            .url(config.url)
            .header("X-BT-Gun-Desktop-Fingerprint", config.expectedDesktopSpkiSha256)
            .header("X-BT-Gun-Android-Nonce", authRequest.androidNonce)
        when (authRequest) {
            is ControlProofRequest -> requestBuilder
                .header("X-BT-Gun-Session", authRequest.sid)
                .header("X-BT-Gun-Pairing-Proof", authRequest.proofHex)
            is ManualCodeAuthRequest -> requestBuilder
                .header("X-BT-Gun-Manual-Code", authRequest.code)
        }
        val request = requestBuilder.build()

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
                            sendEnvelope = { envelope -> socket?.send(ControlEnvelopeCodec.encode(envelope)) == true },
                            onLinkStateChanged = onLinkStateChanged,
                            onProfileMetadataReceived = onProfileMetadataReceived,
                            onInputStreamConfigReceived = onInputStreamConfigReceived,
                            onHapticCommandReceived = onHapticCommandReceived,
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
                    sessionReady = false
                    expectedSessionId = null
                    trustedSessionId = null
                    negotiatedInputFrameFormats = listOf(InputFrameFormat.V1)
                    inboundSequences.reset()
                    outboundSequences.reset()
                    lastHeartbeatElapsedNanos = null
                    val reason = failureReason(t, response)
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
                    sessionReady = false
                    expectedSessionId = null
                    trustedSessionId = null
                    negotiatedInputFrameFormats = listOf(InputFrameFormat.V1)
                    inboundSequences.reset()
                    outboundSequences.reset()
                    lastHeartbeatElapsedNanos = null
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

    private fun failureReason(error: Throwable, response: Response?): String {
        val type = error.javaClass.simpleName.ifBlank { "control channel failed" }
        val message = error.message
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_FAILURE_MESSAGE_CHARS)
        val httpStatus = response?.let { " http=${it.code}" }
        return listOfNotNull(
            type + (message?.let { ": $it" } ?: ""),
            httpStatus,
        ).joinToString(separator = "")
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

    fun sendHapticResult(result: HapticResult): DesktopControlSendResult {
        val sessionId = trustedSessionId ?: return DesktopControlSendResult.NotConnected
        return send(resultEnvelope(sessionId, result))
    }

    fun sendProfileMetadata(profile: ProfileMetadata): DesktopControlSendResult {
        val sessionId = trustedSessionId ?: return DesktopControlSendResult.NotConnected
        return send(profileMetadataEnvelope(sessionId, profile))
    }

    fun sendVisualizerStatus(status: VisualizerStatus): DesktopControlSendResult {
        val sessionId = trustedSessionId ?: return DesktopControlSendResult.NotConnected
        return send(visualizerStatusEnvelope(sessionId, status))
    }

    fun close() {
        socket?.close()
        socket = null
        authenticated = false
        sessionReady = false
        expectedSessionId = null
        trustedSessionId = null
        negotiatedInputFrameFormats = listOf(InputFrameFormat.V1)
        inboundSequences.reset()
        outboundSequences.reset()
        lastHeartbeatElapsedNanos = null
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
        if (!authenticated || socket == null) {
            linkState = linkState.copy(
                phase = DesktopLinkPhase.DISCONNECTED,
                heartbeatAgeMillis = heartbeatAgeMillisAt(nowElapsedNanos),
            )
            return linkState
        }
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
        sendEnvelope: (ControlEnvelope) -> Boolean,
        onLinkStateChanged: (DesktopLinkState) -> Unit,
        onProfileMetadataReceived: (ProfileMetadata) -> Unit,
        onInputStreamConfigReceived: (InputStreamConfig) -> Unit,
        onHapticCommandReceived: (DesktopHapticCommand, Long) -> HapticResult,
    ): Boolean =
        when (val decoded = ControlEnvelopeCodec.decode(text, maxBytes = config.maxMessageBytes)) {
            is ControlDecodeResult.Rejected -> {
                recordControlError(decoded.error.name.lowercase(Locale.US))
                false
            }
            is ControlDecodeResult.Accepted -> {
                if (!sessionReady) {
                    val expected = expectedSessionId
                    when {
                        decoded.envelope.type != ControlMessageType.SESSION_READY -> {
                            recordControlError("session not ready")
                            false
                        }
                        expected != null && decoded.envelope.sessionId != expected -> {
                            recordControlError("session mismatch")
                            false
                        }
                        !inboundSequences.accept(decoded.envelope) -> {
                            recordControlError("control sequence out of order")
                            false
                        }
                        else -> {
                            trustedSessionId = decoded.envelope.sessionId
                            sessionReady = true
                            negotiatedInputFrameFormats = negotiatedInputFrameFormats(decoded.envelope.body)
                            observeHeartbeat(elapsedRealtimeNanos())
                            if (supportsInputStreamCapabilities(decoded.envelope.body)) {
                                sendEnvelope(inputStreamCapabilitiesEnvelope(decoded.envelope.sessionId))
                            }
                            true
                        }
                    }
                } else if (decoded.envelope.sessionId != trustedSessionId) {
                    recordControlError("session mismatch")
                    false
                } else if (!inboundSequences.accept(decoded.envelope)) {
                    recordControlError("control sequence out of order")
                    false
                } else {
                    when (decoded.envelope.type) {
                        ControlMessageType.SESSION_READY -> {
                            observeHeartbeat(elapsedRealtimeNanos())
                            true
                        }
                        ControlMessageType.HEARTBEAT_PING -> {
                            observeHeartbeat(elapsedRealtimeNanos())
                            sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PONG, decoded.envelope.sessionId))
                            onLinkStateChanged(linkState)
                            false
                        }
                        ControlMessageType.HEARTBEAT_PONG -> {
                            observeHeartbeat(elapsedRealtimeNanos())
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
                        ControlMessageType.INPUT_STREAM_CONFIG -> {
                            val streamConfig = decoded.envelope.body.toInputStreamConfig()
                            if (streamConfig == null) {
                                recordControlError("invalid input stream config")
                            } else if (!negotiatedInputFrameFormats.contains(streamConfig.frameFormat)) {
                                recordControlError("unsupported input stream format")
                            } else {
                                onInputStreamConfigReceived(streamConfig)
                            }
                            false
                        }
                        ControlMessageType.RESERVED_HAPTIC_COMMAND -> {
                            val observedElapsedNanos = elapsedRealtimeNanos()
                            val command = DesktopHapticCommand.fromJsonBody(decoded.envelope.body)
                            val result = if (command == null) {
                                HapticResult(
                                    commandId = decoded.envelope.body.stringField("commandId")?.ifBlank { "invalid" } ?: "invalid",
                                    status = HapticResultStatus.FAILED,
                                    detail = "invalid haptic command",
                                    observedElapsedNanos = observedElapsedNanos,
                                )
                            } else {
                                onHapticCommandReceived(command, observedElapsedNanos)
                            }
                            send(resultEnvelope(decoded.envelope.sessionId, result))
                            false
                        }
                        ControlMessageType.INPUT_STREAM_CAPABILITIES -> false
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
            seq = nextOutboundSeq(sessionId),
            sentElapsedNanos = elapsedRealtimeNanos(),
        )

    private fun resultEnvelope(sessionId: String, result: HapticResult): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.HAPTIC_RESULT,
            msgId = "android-haptic-result-${result.commandId}",
            sessionId = sessionId,
            seq = nextOutboundSeq(sessionId),
            sentElapsedNanos = elapsedRealtimeNanos(),
            body = result.toJsonBody(),
        )

    private fun profileMetadataEnvelope(sessionId: String, profile: ProfileMetadata): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.PROFILE_METADATA,
            msgId = "android-profile-${profile.profileId}-${profile.revision}",
            sessionId = sessionId,
            seq = nextOutboundSeq(sessionId),
            sentElapsedNanos = elapsedRealtimeNanos(),
            body = JsonObject(
                mapOf(
                    "profileId" to JsonPrimitive(profile.profileId),
                    "displayName" to JsonPrimitive(profile.displayName),
                    "revision" to JsonPrimitive(profile.revision),
                    "source" to JsonPrimitive(profile.source),
                    "rawDebugEnabled" to JsonPrimitive(profile.rawDebugEnabled),
                ),
            ),
        )

    private fun visualizerStatusEnvelope(sessionId: String, status: VisualizerStatus): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.DIAGNOSTICS,
            msgId = "android-visualizer-status-${status.statusSequence ?: elapsedRealtimeNanos()}",
            sessionId = sessionId,
            seq = nextOutboundSeq(sessionId),
            sentElapsedNanos = elapsedRealtimeNanos(),
            body = VisualizerStatus.body(status),
        )

    private fun inputStreamCapabilitiesEnvelope(sessionId: String): ControlEnvelope =
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.INPUT_STREAM_CAPABILITIES,
            msgId = "android-input-stream-capabilities",
            sessionId = sessionId,
            seq = nextOutboundSeq(sessionId),
            sentElapsedNanos = elapsedRealtimeNanos(),
            body = JsonObject(
                mapOf(
                    "supportedInputFrameFormats" to buildJsonArray {
                        SUPPORTED_INPUT_FRAME_FORMATS.forEach { format -> add(format.wireName) }
                    },
                ),
            ),
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
            source = stringField("source") ?: "android",
            rawDebugEnabled = boolField("rawDebugEnabled") ?: false,
        )
    }

    private fun JsonObject.toInputStreamConfig(): InputStreamConfig? =
        runCatching {
            InputStreamConfig(
                streamSessionIdHex = stringField("streamSessionIdHex") ?: return null,
                udpHost = stringField("udpHost") ?: return null,
                udpPort = intField("udpPort") ?: return null,
                hmacSha256KeyBase64Url = stringField("hmacSha256KeyBase64Url") ?: return null,
                snapshotHz = intField("snapshotHz") ?: return null,
                frameAgeLimitMs = longField("frameAgeLimitMs") ?: return null,
                streamTimeoutMs = longField("streamTimeoutMs") ?: return null,
                controlDisconnectGraceMs = longField("controlDisconnectGraceMs") ?: return null,
                frameFormat = inputFrameFormatField("frameFormat") ?: return null,
            )
        }.getOrNull()

    private fun negotiatedInputFrameFormats(body: JsonObject): List<InputFrameFormat> {
        val desktopFormats = runCatching {
            body["controlCapabilities"]
                ?.jsonObjectOrNull()
                ?.get("supportedInputFrameFormats")
                ?.jsonArrayOrNull()
                ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull?.let(InputFrameFormat::fromWireName) }
                ?.distinct()
                .orEmpty()
        }.getOrDefault(emptyList())
        if (desktopFormats.isEmpty()) {
            return listOf(InputFrameFormat.V1)
        }
        return SUPPORTED_INPUT_FRAME_FORMATS.filter { format -> desktopFormats.contains(format) }
            .ifEmpty { listOf(InputFrameFormat.V1) }
    }

    private fun supportsInputStreamCapabilities(body: JsonObject): Boolean =
        body["controlCapabilities"]
            ?.jsonObjectOrNull()
            ?.get("supportedInputFrameFormats")
            ?.jsonArrayOrNull() != null

    private fun nextOutboundSeq(sessionId: String): Long =
        outboundSequences.next(sessionId)

    private fun JsonObject.stringField(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.inputFrameFormatField(name: String): InputFrameFormat? {
        val value = get(name) ?: return InputFrameFormat.V1
        if (value is JsonNull) return InputFrameFormat.V1
        val wireName = (value as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: return null
        return InputFrameFormat.fromWireName(wireName)
    }

    private fun JsonObject.longField(name: String): Long? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull

    private fun JsonObject.boolField(name: String): Boolean? =
        (get(name) as? JsonPrimitive)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.intField(name: String): Int? =
        longField(name)
            ?.takeIf { value -> value in Int.MIN_VALUE..Int.MAX_VALUE }
            ?.toInt()

    private fun JsonObject.nullableLongField(name: String): Long? =
        if (containsKey(name)) longField(name) else null

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull() =
        runCatching { jsonArray }.getOrNull()

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
        private const val MAX_FAILURE_MESSAGE_CHARS = 160
        private val SUPPORTED_INPUT_FRAME_FORMATS = listOf(
            InputFrameFormat.COMPACT_V2,
            InputFrameFormat.V1,
        )

        fun defaultSocket(request: Request, listener: WebSocketListener): DesktopControlSocket {
            val pin = request.header("X-BT-Gun-Desktop-Fingerprint")
                ?: throw IllegalArgumentException("missing expected desktop fingerprint")
            val trustManager = PinnedSpkiTrustManager(pin)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
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
