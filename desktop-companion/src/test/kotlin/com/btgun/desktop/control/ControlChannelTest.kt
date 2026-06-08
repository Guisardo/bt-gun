package com.btgun.desktop.control

import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.ManualPairingAttemptRequest
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSecurityState
import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.PairingProof
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun main() {
    envelopeCodecAcceptsOnlyVersionOneAndKnownTypes()
    envelopeCodecRejectsUnsupportedVersionUnknownTypeAndOversizedText()
    envelopeCodecRejectsOverflowedVersion()
    reservedHapticCommandAllowsPhaseFourCommandBody()
    controlServerRejectsControlEnvelopeBeforeProof()
    controlServerAcceptsKnownEnvelopeAfterProof()
    controlServerAuthenticatesManualCodeWithoutSidOrNonce()
    controlServerRetainsTrustedSessionForMultipleEnvelopes()
    controlServerRespondsToHeartbeatAndSurfacesTrustedMetadata()
    heartbeatMonitorTransitionsConnectedDegradedDisconnected()
    heartbeatPingAndPongRefreshLiveness()
    diagnosticsPayloadContainsOnlyControlFields()
    profileMetadataContainsOnlyRequiredFields()
    controlEnvelopeAllowsHeartbeatDiagnosticsAndProfileTypes()
    controlEnvelopeAllowsHapticResultBody()
    controlServerAcceptsHapticResultAfterProof()
    controlServerSendsFreshInputStreamConfigAfterTrustedSession()
}

private fun envelopeCodecAcceptsOnlyVersionOneAndKnownTypes() {
    val envelope = envelope(ControlMessageType.SESSION_READY)

    val encoded = ControlEnvelopeCodec.encode(envelope)
    val decoded = ControlEnvelopeCodec.decode(encoded)

    expectTrue("decoded accepted", decoded is ControlDecodeResult.Accepted)
    expectEquals("round trip type", ControlMessageType.SESSION_READY, (decoded as ControlDecodeResult.Accepted).envelope.type)
    expectEquals("pairing wire name", "pairing_state", ControlMessageType.PAIRING_STATE.wireName)
    expectEquals("ready wire name", "session_ready", ControlMessageType.SESSION_READY.wireName)
    expectEquals("reserved haptic name", "reserved_haptic_command", ControlMessageType.RESERVED_HAPTIC_COMMAND.wireName)
    expectEquals("haptic result name", "haptic_result", ControlMessageType.HAPTIC_RESULT.wireName)
    expectEquals("stream config wire name", "input_stream_config", ControlMessageType.INPUT_STREAM_CONFIG.wireName)
}

private fun envelopeCodecRejectsUnsupportedVersionUnknownTypeAndOversizedText() {
    val unsupportedVersion = """{"v":2,"type":"session_ready","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""
    val unknownType = """{"v":1,"type":"profile_update","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""

    expectRejected("unsupported version", ControlEnvelopeError.UNSUPPORTED_VERSION, ControlEnvelopeCodec.decode(unsupportedVersion))
    expectRejected("unknown type", ControlEnvelopeError.UNKNOWN_TYPE, ControlEnvelopeCodec.decode(unknownType))
    expectRejected("oversized", ControlEnvelopeError.OVERSIZED, ControlEnvelopeCodec.decode(unknownType, maxBytes = 12))
}

private fun envelopeCodecRejectsOverflowedVersion() {
    val overflowVersion = """{"v":4294967297,"type":"session_ready","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""

    expectRejected("overflow version", ControlEnvelopeError.INVALID_FIELD, ControlEnvelopeCodec.decode(overflowVersion))
}

private fun reservedHapticCommandAllowsPhaseFourCommandBody() {
    val withExecutionShape = ControlEnvelopeCodec.decode(
        ControlEnvelopeCodec.encode(
            envelope(
                ControlMessageType.RESERVED_HAPTIC_COMMAND,
                body = HapticCommand(
                    commandId = "cmd-001",
                    strength = 0.5,
                    durationMs = 80L,
                    ttlMs = 500L,
                ).toJsonBody(),
            ),
        ),
    )

    expectTrue("reserved haptic command body accepted", withExecutionShape is ControlDecodeResult.Accepted)
}

private fun controlServerRejectsControlEnvelopeBeforeProof() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 512)
    val raw = ControlEnvelopeCodec.encode(envelope(ControlMessageType.PAIRING_STATE, sessionId = session.sid))

    val result = server.handleAuthenticatedSocket(proofRequest = null, text = raw, nowEpochMillis = 2_000L)

    expectEquals("pre auth rejected", ControlServerResult.RejectedPreAuth, result)
    expectEquals("state still pending", PairingSecurityState.PENDING, registry.securityState(session.sid, nowEpochMillis = 2_000L))
}

private fun controlServerAcceptsKnownEnvelopeAfterProof() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 512)
    val androidNonce = "aa".repeat(16)
    val proofRequest = proofRequestFor(session, androidNonce)
    val raw = ControlEnvelopeCodec.encode(envelope(ControlMessageType.SESSION_READY, sessionId = session.sid))

    val result = server.handleAuthenticatedSocket(proofRequest = proofRequest, text = raw, nowEpochMillis = 2_000L)

    expectTrue("accepted", result is ControlServerResult.Accepted)
    expectEquals("accepted type", ControlMessageType.SESSION_READY, (result as ControlServerResult.Accepted).envelope.type)
    expectEquals("trusted sid", session.sid, result.trustedSession.sid)
}

private fun controlServerAuthenticatesManualCodeWithoutSidOrNonce() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 512)

    val trusted = server.authenticate(
        ManualPairingAttemptRequest(
            androidNonce = "aa".repeat(16),
            desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
            code = session.manualPayload.code,
        ),
        nowEpochMillis = 2_000L,
    )

    expectTrue("manual authenticated", trusted is ControlAuthenticationResult.Accepted)
    expectEquals("manual trusted sid", session.sid, (trusted as ControlAuthenticationResult.Accepted).trustedSession.sid)
}

private fun controlServerRetainsTrustedSessionForMultipleEnvelopes() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 512)
    val trusted = server.authenticate(proofRequestFor(session, "bb".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession

    val first = server.handleTrustedEnvelope(
        trustedSession,
        ControlEnvelopeCodec.encode(envelope(ControlMessageType.HEARTBEAT_PING, sessionId = session.sid)),
    )
    val second = server.handleTrustedEnvelope(
        trustedSession,
        ControlEnvelopeCodec.encode(envelope(ControlMessageType.DIAGNOSTICS, sessionId = session.sid)),
    )

    expectTrue("first accepted", first is ControlServerResult.Accepted)
    expectTrue("second accepted", second is ControlServerResult.Accepted)
}

private fun controlServerRespondsToHeartbeatAndSurfacesTrustedMetadata() = runBlocking {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 512)
    val states = mutableListOf<ControlServerSessionState>()
    val accepted = mutableListOf<ControlEnvelope>()
    val sent = mutableListOf<ControlEnvelope>()
    val heartbeat = HeartbeatMonitor()
    server.onSessionStateChanged = states::add
    server.onControlEnvelopeAccepted = accepted::add

    server.handleAcceptedEnvelope(
        envelope = envelope(ControlMessageType.HEARTBEAT_PING, sessionId = "sid-1"),
        heartbeat = heartbeat,
        nowElapsedNanos = 1_000_000_000L,
        sendEnvelope = sent::add,
    )
    server.handleAcceptedEnvelope(
        envelope = envelope(ControlMessageType.DIAGNOSTICS, sessionId = "sid-1"),
        heartbeat = heartbeat,
        nowElapsedNanos = 1_100_000_000L,
        sendEnvelope = sent::add,
    )

    expectEquals("pong emitted", ControlMessageType.HEARTBEAT_PONG, sent.single().type)
    expectEquals("liveness state", ControlServerSessionState.AUTHENTICATED, states.single())
    expectEquals("metadata callback", ControlMessageType.DIAGNOSTICS, accepted.single().type)
}

private fun heartbeatMonitorTransitionsConnectedDegradedDisconnected() {
    val monitor = HeartbeatMonitor(
        connectedTimeoutNanos = 1_000_000_000L,
        disconnectedTimeoutNanos = 3_000_000_000L,
    )

    expectEquals("before heartbeat disconnected", LivenessState.DISCONNECTED, monitor.stateAt(500_000_000L))
    monitor.observePing(nowElapsedNanos = 1_000_000_000L)

    expectEquals("fresh ping connected", LivenessState.CONNECTED, monitor.stateAt(1_500_000_000L))
    expectEquals("stale ping degraded", LivenessState.DEGRADED, monitor.stateAt(2_500_000_001L))
    expectEquals("missing ping disconnected", LivenessState.DISCONNECTED, monitor.stateAt(4_000_000_001L))
}

private fun heartbeatPingAndPongRefreshLiveness() {
    val monitor = HeartbeatMonitor(
        connectedTimeoutNanos = 1_000_000_000L,
        disconnectedTimeoutNanos = 3_000_000_000L,
    )

    monitor.observePing(nowElapsedNanos = 1_000_000_000L)
    expectEquals("ping becomes degraded", LivenessState.DEGRADED, monitor.stateAt(2_500_000_001L))
    monitor.observePong(nowElapsedNanos = 2_600_000_000L)

    expectEquals("pong refreshes connected state", LivenessState.CONNECTED, monitor.stateAt(3_000_000_000L))
    expectEquals("heartbeat age millis", 400L, monitor.heartbeatAgeMillisAt(3_000_000_000L))
}

private fun diagnosticsPayloadContainsOnlyControlFields() {
    val diagnostics = ControlDiagnostics(
        sessionState = "connected",
        desktopIdentitySuffix = "11223344",
        heartbeatAgeMillis = 400L,
        lastControlError = "none",
    )

    expectEquals("session state", "connected", diagnostics.sessionState)
    expectEquals(
        "diagnostic fields",
        listOf("sessionState", "desktopIdentitySuffix", "heartbeatAgeMillis", "lastControlError"),
        dataFieldNames(ControlDiagnostics::class.java),
    )
}

private fun profileMetadataContainsOnlyRequiredFields() {
    val profile = ProfileMetadata(
        profileId = "default",
        displayName = "Default profile",
        revision = 1L,
    )

    expectEquals("profile id", "default", profile.profileId)
    expectEquals(
        "profile fields",
        listOf("profileId", "displayName", "revision"),
        dataFieldNames(ProfileMetadata::class.java),
    )
}

private fun controlEnvelopeAllowsHeartbeatDiagnosticsAndProfileTypes() {
    expectEquals("heartbeat ping wire name", "heartbeat_ping", ControlMessageType.HEARTBEAT_PING.wireName)
    expectEquals("heartbeat pong wire name", "heartbeat_pong", ControlMessageType.HEARTBEAT_PONG.wireName)
    expectEquals("diagnostics wire name", "diagnostics", ControlMessageType.DIAGNOSTICS.wireName)
    expectEquals("profile metadata wire name", "profile_metadata", ControlMessageType.PROFILE_METADATA.wireName)

    listOf(
        ControlMessageType.HEARTBEAT_PING,
        ControlMessageType.HEARTBEAT_PONG,
        ControlMessageType.DIAGNOSTICS,
        ControlMessageType.PROFILE_METADATA,
        ControlMessageType.INPUT_STREAM_CONFIG,
        ControlMessageType.HAPTIC_RESULT,
    ).forEach { type ->
        val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope(type)))
        expectTrue("${type.wireName} accepted", decoded is ControlDecodeResult.Accepted)
    }
}

private fun controlEnvelopeAllowsHapticResultBody() {
    val decoded = ControlEnvelopeCodec.decode(
        ControlEnvelopeCodec.encode(
            envelope(
                ControlMessageType.HAPTIC_RESULT,
                body = HapticResult(
                    commandId = "cmd-001",
                    status = HapticResultStatus.STARTED,
                    detail = "phone pulse started",
                    observedElapsedNanos = 1_050_000_000L,
                ).toJsonBody(),
            ),
        ),
    )

    expectTrue("haptic result body accepted", decoded is ControlDecodeResult.Accepted)
}

private fun controlServerAcceptsHapticResultAfterProof() = runBlocking {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 1024)
    val accepted = mutableListOf<ControlEnvelope>()
    server.onControlEnvelopeAccepted = accepted::add

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            body = HapticResult(
                commandId = "cmd-001",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 1_050_000_000L,
            ).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
    )

    expectEquals("haptic callback", ControlMessageType.HAPTIC_RESULT, accepted.single().type)
}

private fun controlServerSendsFreshInputStreamConfigAfterTrustedSession() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(
        registry = registry,
        maxMessageBytes = 2048,
        udpHost = "192.168.50.25",
        udpPort = 41731,
        streamSecretFactory = incrementalSecretFactory(),
    )
    val trusted = server.authenticate(proofRequestFor(session, "cc".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession

    val first = server.inputStreamConfigEnvelopeFor(trustedSession, nowElapsedNanos = 3_000_000_000L)
    val second = server.inputStreamConfigEnvelopeFor(trustedSession, nowElapsedNanos = 3_000_000_100L)

    expectEquals("config type", ControlMessageType.INPUT_STREAM_CONFIG, first.type)
    expectEquals("trusted sid", session.sid, first.sessionId)
    expectEquals("host", "192.168.50.25", first.body.stringField("udpHost"))
    expectEquals("port", 41731L, first.body.longField("udpPort"))
    expectEquals("snapshot hz", 60L, first.body.longField("snapshotHz"))
    expectEquals("age limit", 150L, first.body.longField("frameAgeLimitMs"))
    expectEquals("timeout", 250L, first.body.longField("streamTimeoutMs"))
    expectEquals("control grace", 1500L, first.body.longField("controlDisconnectGraceMs"))
    expectEquals("stream id hex length", 32, requireNotNull(first.body.stringField("streamSessionIdHex")).length)
    expectTrue("stream id fresh", first.body.stringField("streamSessionIdHex") != second.body.stringField("streamSessionIdHex"))
    expectTrue("stream key fresh", first.body.stringField("hmacSha256KeyBase64Url") != second.body.stringField("hmacSha256KeyBase64Url"))
    expectTrue("config encodes", ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(first)) is ControlDecodeResult.Accepted)
    listOf("qrSecret", "qr_secret", "manualCode", "manual code", "pairingProof", "proof").forEach { secret ->
        expectTrue("config body excludes $secret", first.body.toString().contains(secret, ignoreCase = true).not())
    }
}

private fun envelope(
    type: ControlMessageType,
    sessionId: String = "sid-1",
    body: JsonObject = JsonObject(emptyMap()),
): ControlEnvelope =
    ControlEnvelope(
        v = 1,
        type = type,
        msgId = "msg-1",
        sessionId = sessionId,
        seq = 1L,
        sentElapsedNanos = 10L,
        body = body,
    )

private fun proofRequestFor(session: PairingSession, androidNonce: String): PairingProofRequest =
    PairingProofRequest(
        sid = session.sid,
        androidNonce = androidNonce,
        desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
        proofHex = PairingProof.create(
            sid = session.sid,
            desktopNonce = session.qrPayload.desktopNonce,
            androidNonce = androidNonce,
            desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
            oneTimeMaterial = session.qrPayload.qrSecret,
        ),
    )

private fun testRegistry(): PairingSessionRegistry =
    PairingSessionRegistry(
        endpointSelector = LocalEndpointSelector.fixed(host = "192.168.50.25", port = 41731),
        identityStore = object : DesktopIdentityStore {
            override fun loadOrCreateIdentity(): DesktopIdentity =
                DesktopIdentity(desktopSpkiSha256 = FINGERPRINT)
        },
    )

private fun expectRejected(label: String, expected: ControlEnvelopeError, actual: ControlDecodeResult) {
    expectTrue(label, actual is ControlDecodeResult.Rejected)
    expectEquals(label, expected, (actual as ControlDecodeResult.Rejected).error)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}

private fun dataFieldNames(type: Class<*>): List<String> =
    type.declaredFields
        .filterNot { it.isSynthetic }
        .map { it.name }

private fun JsonObject.stringField(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull

private fun JsonObject.longField(name: String): Long? =
    get(name)?.jsonPrimitive?.contentOrNull?.toLongOrNull()

private fun incrementalSecretFactory(): () -> ByteArray {
    var next = 1
    return {
        ByteArray(32) { (next + it).toByte() }.also { next += 32 }
    }
}

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
