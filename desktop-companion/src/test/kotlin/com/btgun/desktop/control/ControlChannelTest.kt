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
import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpInputRuntime
import com.btgun.desktop.transport.UdpInputRuntimeStartResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.booleanOrNull
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
    controlServerAcceptsVisualizerStatusFromTrustedDiagnostics()
    controlServerIgnoresMalformedVisualizerStatusDiagnostics()
    visualizerStatusBodyIsSanitized()
    heartbeatMonitorTransitionsConnectedDegradedDisconnected()
    heartbeatPingAndPongRefreshLiveness()
    diagnosticsPayloadContainsOnlyControlFields()
    profileMetadataContainsAndroidOwnershipFields()
    controlServerAcceptsAndroidProfileMetadataCallback()
    controlServerIgnoresIncompleteProfileMetadata()
    controlServerDoesNotOwnDefaultProfileMetadata()
    controlEnvelopeAllowsHeartbeatDiagnosticsAndProfileTypes()
    controlEnvelopeAllowsHapticResultBody()
    controlServerBuildsTrustedHapticCommandEnvelope()
    controlServerSendsHapticCommandOnlyForActiveSession()
    controlServerAcceptsOnlyPendingActiveHapticResult()
    controlServerRejectsHapticsAfterLivenessDisconnect()
    controlServerAcceptsStartedAndThenCancelledForActiveHaptic()
    controlServerKeepsOnlyLatestStartedHapticCancellable()
    controlServerKeepsActiveHapticWhenReplacementFails()
    controlServerAcceptsActiveHapticCancelFailureStatuses()
    controlServerSendsFreshInputStreamConfigAfterTrustedSession()
    controlServerStartsUdpRuntimeWithAdvertisedInputStreamConfig()
    controlServerReportsUdpStartFailureBeforeStreamConfig()
    controlServerUsesPairingEndpointForInputStreamConfigWhenConstructedBeforePairing()
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

private fun controlServerAcceptsVisualizerStatusFromTrustedDiagnostics() = runBlocking {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 1024)
    val accepted = mutableListOf<ControlEnvelope>()
    val statuses = mutableListOf<VisualizerStatus>()
    server.onControlEnvelopeAccepted = accepted::add
    server.onVisualizerStatusReceived = statuses::add

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.DIAGNOSTICS,
            sessionId = "sid-1",
            body = JsonObject(
                mapOf(
                    "visualizerStatus" to JsonObject(
                        mapOf(
                            "rawDebugEnabled" to JsonPrimitive(true),
                            "aimZeroState" to JsonPrimitive("ready"),
                            "recenterState" to JsonPrimitive("recentered"),
                            "lastRecenterElapsedNanos" to JsonPrimitive(1_900_000_000L),
                            "androidElapsedNanos" to JsonPrimitive(2_000_000_000L),
                            "captureElapsedNanos" to JsonPrimitive(1_950_000_000L),
                            "sendElapsedNanos" to JsonPrimitive(1_960_000_000L),
                            "statusSequence" to JsonPrimitive(7L),
                            "recenterLabel" to JsonPrimitive("recentered"),
                            "aimZeroLabel" to JsonPrimitive("ready"),
                        ),
                    ),
                ),
            ),
        ),
        heartbeat = HeartbeatMonitor(),
        nowElapsedNanos = 3_000_000_000L,
    )

    expectEquals("generic diagnostics accepted", ControlMessageType.DIAGNOSTICS, accepted.single().type)
    expectEquals(
        "visualizer status callback",
        VisualizerStatus(
            controlSessionId = "sid-1",
            rawDebugEnabled = true,
            aimZeroState = "ready",
            recenterState = "recentered",
            lastRecenterElapsedNanos = 1_900_000_000L,
            androidElapsedNanos = 2_000_000_000L,
            captureElapsedNanos = 1_950_000_000L,
            sendElapsedNanos = 1_960_000_000L,
            statusSequence = 7L,
            recenterLabel = "recentered",
            aimZeroLabel = "ready",
        ),
        statuses.single(),
    )
}

private fun controlServerIgnoresMalformedVisualizerStatusDiagnostics() = runBlocking {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 1024)
    val accepted = mutableListOf<ControlEnvelope>()
    val statuses = mutableListOf<VisualizerStatus>()
    server.onControlEnvelopeAccepted = accepted::add
    server.onVisualizerStatusReceived = statuses::add

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.DIAGNOSTICS,
            sessionId = "sid-1",
            body = JsonObject(
                mapOf(
                    "visualizerStatus" to JsonObject(
                        mapOf(
                            "rawDebugEnabled" to JsonPrimitive(false),
                            "aimZeroState" to JsonPrimitive("ready"),
                            "recenterState" to JsonPrimitive("idle"),
                            "androidElapsedNanos" to JsonPrimitive(-1L),
                        ),
                    ),
                ),
            ),
        ),
        heartbeat = HeartbeatMonitor(),
        nowElapsedNanos = 3_000_000_000L,
    )

    expectEquals("malformed diagnostics still accepted generically", ControlMessageType.DIAGNOSTICS, accepted.single().type)
    expectEquals("malformed status ignored", emptyList<VisualizerStatus>(), statuses)
}

private fun visualizerStatusBodyIsSanitized() {
    val valid = visualizerStatusFromJsonBody(
        JsonObject(
            mapOf(
                "visualizerStatus" to JsonObject(
                    mapOf(
                        "rawDebugEnabled" to JsonPrimitive(false),
                        "aimZeroState" to JsonPrimitive("pending"),
                        "recenterState" to JsonPrimitive("idle"),
                        "androidElapsedNanos" to JsonPrimitive(2_000_000_000L),
                    ),
                ),
            ),
        ),
    )
    val invalidElapsed = visualizerStatusFromJsonBody(
        JsonObject(
            mapOf(
                "visualizerStatus" to JsonObject(
                    mapOf(
                        "rawDebugEnabled" to JsonPrimitive(false),
                        "aimZeroState" to JsonPrimitive("pending"),
                        "recenterState" to JsonPrimitive("idle"),
                        "androidElapsedNanos" to JsonPrimitive(-1L),
                    ),
                ),
            ),
        ),
    )
    val invalidSource = visualizerStatusFromJsonBody(
        JsonObject(
            mapOf(
                "visualizerStatus" to JsonObject(
                    mapOf(
                        "rawDebugEnabled" to JsonPrimitive(false),
                        "aimZeroState" to JsonPrimitive("pending"),
                        "recenterState" to JsonPrimitive("idle"),
                        "androidElapsedNanos" to JsonPrimitive(2_000_000_000L),
                        "source" to JsonPrimitive("desktop"),
                    ),
                ),
            ),
        ),
    )
    val secretLikeField = visualizerStatusFromJsonBody(
        JsonObject(
            mapOf(
                "visualizerStatus" to JsonObject(
                    mapOf(
                        "rawDebugEnabled" to JsonPrimitive(false),
                        "aimZeroState" to JsonPrimitive("pending"),
                        "recenterState" to JsonPrimitive("idle"),
                        "androidElapsedNanos" to JsonPrimitive(2_000_000_000L),
                        "pairingSecret" to JsonPrimitive("nope"),
                    ),
                ),
            ),
        ),
    )

    expectEquals("valid status parsed", "pending", valid?.aimZeroState)
    expectEquals("negative elapsed rejected", null, invalidElapsed)
    expectEquals("invalid source rejected", null, invalidSource)
    expectEquals("secret-like field rejected", null, secretLikeField)
    expectEquals(
        "visualizer status fields",
        listOf(
            "controlSessionId",
            "rawDebugEnabled",
            "aimZeroState",
            "recenterState",
            "lastRecenterElapsedNanos",
            "androidElapsedNanos",
            "captureElapsedNanos",
            "sendElapsedNanos",
            "statusSequence",
            "recenterLabel",
            "aimZeroLabel",
        ),
        dataFieldNames(VisualizerStatus::class.java),
    )
    listOf("secret", "key", "hmac", "pairing", "proof", "deviceId").forEach { forbidden ->
        expectFalse("status field excludes $forbidden", dataFieldNames(VisualizerStatus::class.java).any { it.contains(forbidden, ignoreCase = true) })
    }
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

private fun profileMetadataContainsAndroidOwnershipFields() {
    val profile = ProfileMetadata(
        profileId = "default_visualizer",
        displayName = "Default Visualizer",
        revision = 1L,
        source = "android",
        rawDebugEnabled = true,
    )

    expectEquals("profile id", "default_visualizer", profile.profileId)
    expectEquals("source", "android", profile.source)
    expectEquals("raw debug", true, profile.rawDebugEnabled)
    expectEquals(
        "profile fields",
        listOf("profileId", "displayName", "revision", "source", "rawDebugEnabled"),
        dataFieldNames(ProfileMetadata::class.java),
    )
}

private fun controlServerAcceptsAndroidProfileMetadataCallback() = runBlocking {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 512)
    val received = mutableListOf<ProfileMetadata>()
    server.onProfileMetadataReceived = received::add

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.PROFILE_METADATA,
            sessionId = "sid-1",
            body = JsonObject(
                mapOf(
                    "profileId" to JsonPrimitive("default_visualizer"),
                    "displayName" to JsonPrimitive("Default Visualizer"),
                    "revision" to JsonPrimitive(7L),
                    "source" to JsonPrimitive("android"),
                    "rawDebugEnabled" to JsonPrimitive(true),
                ),
            ),
        ),
        heartbeat = HeartbeatMonitor(),
        nowElapsedNanos = 1_000_000_000L,
    )

    expectEquals(
        "android metadata callback",
        ProfileMetadata(
            profileId = "default_visualizer",
            displayName = "Default Visualizer",
            revision = 7L,
            source = "android",
            rawDebugEnabled = true,
        ),
        received.single(),
    )
}

private fun controlServerIgnoresIncompleteProfileMetadata() = runBlocking {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 512)
    val received = mutableListOf<ProfileMetadata>()
    server.onProfileMetadataReceived = received::add

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.PROFILE_METADATA,
            sessionId = "sid-1",
            body = JsonObject(
                mapOf(
                    "profileId" to JsonPrimitive("default_visualizer"),
                    "displayName" to JsonPrimitive("Default Visualizer"),
                    "revision" to JsonPrimitive(7L),
                ),
            ),
        ),
        heartbeat = HeartbeatMonitor(),
        nowElapsedNanos = 1_000_000_000L,
    )

    expectEquals("missing source ignored", emptyList<ProfileMetadata>(), received)
}

private fun controlServerDoesNotOwnDefaultProfileMetadata() {
    val source = java.io.File("src/main/kotlin/com/btgun/desktop/control/ControlServer.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()

    listOf("desktop-profile-metadata", "DEFAULT_PROFILE_ID", "DEFAULT_PROFILE_NAME").forEach { forbidden ->
        expectFalse("desktop default profile authority absent: $forbidden", source.contains(forbidden))
    }
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

private fun controlServerBuildsTrustedHapticCommandEnvelope() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val trusted = server.authenticate(proofRequestFor(session, "cc".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)

    val envelope = server.hapticCommandEnvelopeFor(
        trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession,
        command = HapticCommand(
            commandId = "cmd-001",
            strength = 0.75,
            durationMs = 120L,
            ttlMs = 500L,
        ),
        nowElapsedNanos = 3_000_000_000L,
    )

    expectEquals("haptic command type", ControlMessageType.RESERVED_HAPTIC_COMMAND, envelope.type)
    expectEquals("trusted sid", session.sid, envelope.sessionId)
    expectEquals("command id", "cmd-001", envelope.body.stringField("commandId"))
    expectEquals("strength", "0.75", envelope.body.stringField("strength"))
    expectTrue("command encodes", ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope)) is ControlDecodeResult.Accepted)
}

private fun controlServerSendsHapticCommandOnlyForActiveSession() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val command = HapticCommand(
        commandId = "cmd-001",
        strength = 0.5,
        durationMs = 80L,
        ttlMs = 500L,
    )

    expectEquals("pre-auth haptic blocked", HapticSendResult.NoActiveSession, server.sendHapticCommand(command))

    val trusted = server.authenticate(proofRequestFor(session, "ee".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    server.registerActiveControlSessionForTest((trusted as ControlAuthenticationResult.Accepted).trustedSession, outbound)

    val send = server.sendHapticCommand(command, nowElapsedNanos = 3_000_000_000L)
    val sentEnvelope = outbound.tryReceive().getOrNull()

    expectEquals("active haptic sent", HapticSendResult.Sent, send)
    expectEquals("haptic type", ControlMessageType.RESERVED_HAPTIC_COMMAND, sentEnvelope?.type)
    expectEquals("haptic command id", "cmd-001", sentEnvelope?.body?.stringField("commandId"))
}

private fun controlServerAcceptsOnlyPendingActiveHapticResult() = runBlocking {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val accepted = mutableListOf<ControlEnvelope>()
    val parsed = mutableListOf<HapticResult>()
    server.onControlEnvelopeAccepted = accepted::add
    server.onHapticResultReceived = parsed::add
    val trusted = server.authenticate(proofRequestFor(session, "ef".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    server.registerActiveControlSessionForTest(trustedSession, outbound, token = "active-token")

    server.handleAcceptedEnvelope(
        envelope = envelope(ControlMessageType.HAPTIC_RESULT, sessionId = trustedSession.sid, body = JsonObject(emptyMap())),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = "active-token",
    )
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult(
                commandId = "unknown",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 1_050_000_000L,
            ).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = "active-token",
    )
    server.sendHapticCommand(HapticCommand("cmd-stale", strength = 0.5, durationMs = 80L, ttlMs = 500L))
    outbound.tryReceive().getOrNull()
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult(
                commandId = "cmd-stale",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 1_050_000_000L,
            ).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = "stale-token",
    )
    server.sendHapticCommand(HapticCommand("cmd-001", strength = 0.5, durationMs = 80L, ttlMs = 500L))
    outbound.tryReceive().getOrNull()

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult(
                commandId = "cmd-001",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 1_050_000_000L,
            ).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = "active-token",
    )

    expectEquals("one parsed haptic result", 1, parsed.size)
    expectEquals("parsed command", "cmd-001", parsed.single().commandId)
    expectEquals("haptic callback", ControlMessageType.HAPTIC_RESULT, accepted.single().type)
}

private fun controlServerRejectsHapticsAfterLivenessDisconnect() = runBlocking {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val runtime = FakeUdpRuntime()
    val server = ControlServer(registry = registry, maxMessageBytes = 1024, udpRuntime = runtime)
    val trusted = server.authenticate(proofRequestFor(session, "f0".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    val token = server.registerActiveControlSessionForTest((trusted as ControlAuthenticationResult.Accepted).trustedSession, outbound)

    server.updateSessionState(
        state = LivenessState.DISCONNECTED,
        controlSessionToken = token,
        nowElapsedNanos = 4_000_000_001L,
    )

    expectEquals(
        "stale session cannot send haptic",
        HapticSendResult.NoActiveSession,
        server.sendHapticCommand(HapticCommand("cmd-after-disconnect", 0.5, 80L, 500L)),
    )
    expectEquals("udp disconnect signaled once", 1, runtime.disconnects)
}

private fun controlServerAcceptsStartedAndThenCancelledForActiveHaptic() = runBlocking {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val parsed = mutableListOf<HapticResult>()
    server.onHapticResultReceived = parsed::add
    val trusted = server.authenticate(proofRequestFor(session, "f1".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    val token = server.registerActiveControlSessionForTest(trustedSession, outbound)
    server.sendHapticCommand(HapticCommand("cmd-active", 0.5, 80L, 500L))
    outbound.tryReceive().getOrNull()

    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-active", HapticResultStatus.STARTED, "phone pulse started", 10L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-active", HapticResultStatus.CANCELLED, "phone pulse cancelled", 20L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )

    expectEquals("started then cancelled", listOf(HapticResultStatus.STARTED, HapticResultStatus.CANCELLED), parsed.map { it.status })
}

private fun controlServerKeepsOnlyLatestStartedHapticCancellable() = runBlocking {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val parsed = mutableListOf<HapticResult>()
    server.onHapticResultReceived = parsed::add
    val trusted = server.authenticate(proofRequestFor(session, "f2".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    val token = server.registerActiveControlSessionForTest(trustedSession, outbound)

    listOf("cmd-old", "cmd-new").forEach { commandId ->
        server.sendHapticCommand(HapticCommand(commandId, 0.5, 80L, 500L))
        outbound.tryReceive().getOrNull()
        server.handleAcceptedEnvelope(
            envelope = envelope(
                ControlMessageType.HAPTIC_RESULT,
                sessionId = trustedSession.sid,
                body = HapticResult(commandId, HapticResultStatus.STARTED, "phone pulse started", 10L).toJsonBody(),
            ),
            heartbeat = HeartbeatMonitor(),
            controlSessionToken = token,
        )
    }
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-old", HapticResultStatus.CANCELLED, "old cancel", 20L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-new", HapticResultStatus.CANCELLED, "new cancel", 30L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )

    expectEquals(
        "old cancel ignored, latest cancel accepted",
        listOf("cmd-old", "cmd-new", "cmd-new"),
        parsed.map { it.commandId },
    )
}

private fun controlServerKeepsActiveHapticWhenReplacementFails() = runBlocking {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val parsed = mutableListOf<HapticResult>()
    server.onHapticResultReceived = parsed::add
    val trusted = server.authenticate(proofRequestFor(session, "f4".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    val token = server.registerActiveControlSessionForTest(trustedSession, outbound)

    server.sendHapticCommand(HapticCommand("cmd-active", 0.5, 300L, 500L))
    outbound.tryReceive().getOrNull()
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-active", HapticResultStatus.STARTED, "phone pulse started", 10L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )
    server.sendHapticCommand(HapticCommand("cmd-expired", 0.5, 80L, 1L))
    outbound.tryReceive().getOrNull()
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-expired", HapticResultStatus.EXPIRED, "haptic command expired", 20L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-active", HapticResultStatus.CANCELLED, "phone pulse cancelled", 30L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )

    expectEquals(
        "failed replacement leaves previous active command cancellable",
        listOf("cmd-active", "cmd-expired", "cmd-active"),
        parsed.map { it.commandId },
    )
}

private fun controlServerAcceptsActiveHapticCancelFailureStatuses() = runBlocking {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val parsed = mutableListOf<HapticResult>()
    server.onHapticResultReceived = parsed::add
    val trusted = server.authenticate(proofRequestFor(session, "f3".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    val token = server.registerActiveControlSessionForTest(trustedSession, outbound)
    server.sendHapticCommand(HapticCommand("cmd-active", 0.5, 80L, 500L))
    outbound.tryReceive().getOrNull()
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-active", HapticResultStatus.STARTED, "phone pulse started", 10L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )
    server.handleAcceptedEnvelope(
        envelope = envelope(
            ControlMessageType.HAPTIC_RESULT,
            sessionId = trustedSession.sid,
            body = HapticResult("cmd-active", HapticResultStatus.PERMISSION_BLOCKED, "vibrate permission blocked", 20L).toJsonBody(),
        ),
        heartbeat = HeartbeatMonitor(),
        controlSessionToken = token,
    )

    expectEquals("started then cancel failure", listOf(HapticResultStatus.STARTED, HapticResultStatus.PERMISSION_BLOCKED), parsed.map { it.status })
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
    expectEquals("compact frame format", "compact_v2", first.body.stringField("frameFormat"))
    expectEquals("compact capability", true, first.body["capabilities"]?.jsonObject?.get("compactUdpV2")?.jsonPrimitive?.booleanOrNull)
    expectEquals("stream id hex length", 32, requireNotNull(first.body.stringField("streamSessionIdHex")).length)
    expectTrue("stream id fresh", first.body.stringField("streamSessionIdHex") != second.body.stringField("streamSessionIdHex"))
    expectTrue("stream key fresh", first.body.stringField("hmacSha256KeyBase64Url") != second.body.stringField("hmacSha256KeyBase64Url"))
    expectTrue("config encodes", ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(first)) is ControlDecodeResult.Accepted)
    listOf("qrSecret", "qr_secret", "manualCode", "manual code", "pairingProof", "proof").forEach { secret ->
        expectTrue("config body excludes $secret", first.body.toString().contains(secret, ignoreCase = true).not())
    }
}

private fun controlServerStartsUdpRuntimeWithAdvertisedInputStreamConfig() {
    val runtime = FakeUdpRuntime()
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(
        registry = registry,
        maxMessageBytes = 2048,
        streamSecretFactory = incrementalSecretFactory(),
        udpRuntime = runtime,
    )
    val trusted = server.authenticate(proofRequestFor(session, "cd".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)

    val started = server.startInputStreamForTrustedSession(
        trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession,
        nowElapsedNanos = 3_000_000_000L,
    )

    expectTrue("stream started", started is ControlInputStreamStartResult.Started)
    val result = started as ControlInputStreamStartResult.Started
    expectEquals("one runtime start", 1, runtime.starts.size)
    expectEquals("runtime config", result.config, runtime.starts.single().config)
    expectEquals("advertised stream", result.config.streamSessionIdHex, result.envelope.body.stringField("streamSessionIdHex"))
    expectEquals("advertised host", result.config.udpHost, result.envelope.body.stringField("udpHost"))
    expectEquals("advertised port", result.config.udpPort.toLong(), result.envelope.body.longField("udpPort"))
    expectEquals("advertised format", result.config.frameFormat.wireName, result.envelope.body.stringField("frameFormat"))
}

private fun controlServerReportsUdpStartFailureBeforeStreamConfig() {
    val runtime = FakeUdpRuntime(startResult = UdpInputRuntimeStartResult.Failed("bind failed"))
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 2048, udpRuntime = runtime)
    val trusted = server.authenticate(proofRequestFor(session, "ce".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)

    val started = server.startInputStreamForTrustedSession(
        trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession,
        nowElapsedNanos = 3_000_000_000L,
    )

    expectTrue("stream failed", started is ControlInputStreamStartResult.Failed)
    expectEquals("failure reason", "bind failed", (started as ControlInputStreamStartResult.Failed).reason)
    expectEquals("one attempted start", 1, runtime.starts.size)
}

private fun controlServerUsesPairingEndpointForInputStreamConfigWhenConstructedBeforePairing() {
    val registry = testRegistry()
    val server = ControlServer(
        registry = registry,
        maxMessageBytes = 2048,
        streamSecretFactory = incrementalSecretFactory(),
    )
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val trusted = server.authenticate(proofRequestFor(session, "dd".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)

    val envelope = server.inputStreamConfigEnvelopeFor(
        trustedSession = (trusted as ControlAuthenticationResult.Accepted).trustedSession,
        nowElapsedNanos = 3_000_000_000L,
    )

    expectEquals("pairing endpoint host", session.endpoint.host, envelope.body.stringField("udpHost"))
    expectEquals("pairing endpoint port", session.endpoint.port.toLong(), envelope.body.longField("udpPort"))
    expectTrue("not loopback fallback", envelope.body.stringField("udpHost") != ControlServer.DEFAULT_UDP_HOST)
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

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
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

private class FakeUdpRuntime(
    private val startResult: UdpInputRuntimeStartResult = UdpInputRuntimeStartResult.Started,
) : UdpInputRuntime {
    data class Start(val trustedSession: String, val config: InputStreamConfig)

    val starts = mutableListOf<Start>()
    var disconnects = 0
        private set
    var stops = 0
        private set

    override val lifecycleState: InputStreamLifecycleState = InputStreamLifecycleState.STOPPED

    override fun start(trustedSession: String, config: InputStreamConfig): UdpInputRuntimeStartResult {
        starts.add(Start(trustedSession, config))
        return startResult
    }

    override fun onControlDisconnected(nowElapsedNanos: Long) {
        disconnects += 1
    }

    override fun stop(reason: String) {
        stops += 1
    }
}

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
