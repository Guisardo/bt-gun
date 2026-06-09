package com.btgun.host.session

import com.btgun.host.transport.InputStreamConfig
import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.HapticResult
import com.btgun.host.haptics.HapticResultStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

fun main() {
    envelopeCodecMirrorsDesktopAllowlist()
    envelopeCodecRejectsVersionUnknownTypeAndOversizedText()
    envelopeCodecRejectsOverflowedVersion()
    clientBuildsPinnedWssRequestAndTrustMismatchResult()
    qrPayloadBuildsControlRequestAndProofHeaders()
    manualPayloadBuildsCodeAuthWithoutSidOrChallenge()
    manualAuthLearnsSessionIdFromSessionReady()
    sessionReadyInitializesHeartbeatFreshness()
    trustMismatchMovesToTrustProblemWithoutOpeningSocket()
    clientSendRejectsInvalidEnvelopeBeforeSocketWrite()
    desktopLinkHeartbeatMapsLivenessStates()
    hardSocketCloseStaysDisconnectedDuringLivenessRefresh()
    livenessCoordinatorStartsOnlyAfterAuthStopsOnTimeoutAndIgnoresStaleClients()
    clientPublishesPreAuthCloseAsFailure()
    clientPublishesFullFailureReason()
    clientRespondsToHeartbeatAndAppliesLiveMetadata()
    clientCloseStopsSocketAndDisconnectsLinkState()
    clientUpdatesLinkStateFromHeartbeatDiagnosticsAndErrors()
    profileMetadataModelContainsOnlyRequiredFields()
    controlEnvelopeAllowsHeartbeatDiagnosticsAndProfileTypes()
    controlEnvelopeAllowsInputStreamConfigType()
    clientRejectsInputStreamConfigBeforeSessionReady()
    clientRejectsQrInputStreamConfigBeforeSessionReady()
    clientRejectsInputStreamConfigForMismatchedSession()
    clientPublishesTrustedInputStreamConfigAfterSessionReady()
    controlEnvelopeAllowsHapticCommandAndResultTypes()
    clientRejectsHapticCommandBeforeSessionReady()
    clientRejectsQrHapticCommandBeforeSessionReady()
    clientRejectsHapticCommandForMismatchedSession()
    clientHandlesTrustedHapticCommandAndSendsResult()
    clientPreservesHapticResultDetailsFromCallback()
    clientCanSendCancellationHapticResultBeforeClose()
}

private fun envelopeCodecMirrorsDesktopAllowlist() {
    val envelope = envelope(ControlMessageType.SESSION_READY)

    val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope))

    expectTrue("decoded accepted", decoded is ControlDecodeResult.Accepted)
    expectEquals("type", ControlMessageType.SESSION_READY, (decoded as ControlDecodeResult.Accepted).envelope.type)
    expectEquals("pairing wire name", "pairing_state", ControlMessageType.PAIRING_STATE.wireName)
    expectEquals("ready wire name", "session_ready", ControlMessageType.SESSION_READY.wireName)
    expectEquals("reserved haptic name", "reserved_haptic_command", ControlMessageType.RESERVED_HAPTIC_COMMAND.wireName)
    expectEquals("haptic result name", "haptic_result", ControlMessageType.HAPTIC_RESULT.wireName)
}

private fun envelopeCodecRejectsVersionUnknownTypeAndOversizedText() {
    val unsupportedVersion = """{"v":2,"type":"session_ready","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""
    val unknownType = """{"v":1,"type":"profile_update","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""

    expectRejected("version", ControlEnvelopeError.UNSUPPORTED_VERSION, ControlEnvelopeCodec.decode(unsupportedVersion))
    expectRejected("type", ControlEnvelopeError.UNKNOWN_TYPE, ControlEnvelopeCodec.decode(unknownType))
    expectRejected("size", ControlEnvelopeError.OVERSIZED, ControlEnvelopeCodec.decode(unknownType, maxBytes = 8))
}

private fun envelopeCodecRejectsOverflowedVersion() {
    val overflowVersion = """{"v":4294967297,"type":"session_ready","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""

    expectRejected("overflow version", ControlEnvelopeError.INVALID_FIELD, ControlEnvelopeCodec.decode(overflowVersion))
}

private fun clientBuildsPinnedWssRequestAndTrustMismatchResult() {
    val openedRequests = mutableListOf<Request>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { request, _: WebSocketListener ->
            openedRequests += request
            FakeSocket()
        },
    )

    val result = client.connect(proofRequest())

    expectTrue("connecting", result is DesktopControlConnectResult.Connecting)
    expectEquals("url", "https://192.168.50.25:41731/control", openedRequests.single().url.toString())
    expectEquals("fingerprint header", FINGERPRINT, openedRequests.single().header("X-BT-Gun-Desktop-Fingerprint"))
    expectTrue("pin present", client.certificatePin().startsWith("sha256/"))
    expectEquals(
        "trust mismatch",
        DesktopControlConnectResult.TrustMismatch(expected = FINGERPRINT, presented = OTHER_FINGERPRINT),
        client.verifyPresentedFingerprint(OTHER_FINGERPRINT),
    )
}

private fun qrPayloadBuildsControlRequestAndProofHeaders() {
    val request = DesktopControlConnectionRequest.fromQrPayload(
        payload = PairingPayloadV1(
            sid = "session-001",
            host = "192.168.1.44",
            port = 44383,
            expiresAtEpochMillis = 1_700_000_120_000L,
            desktopSpkiSha256 = FINGERPRINT,
            desktopNonce = "00".repeat(16),
            qrSecret = "abcdefghijklmnopqrstuvwxyzABCDEF",
        ),
        androidNonce = "11".repeat(16),
    )
    val openedRequests = mutableListOf<Request>()
    val client = DesktopControlClient(
        config = request.config,
        socketFactory = { openedRequest, _: WebSocketListener ->
            openedRequests += openedRequest
            FakeSocket()
        },
    )

    val authRequest = request.authRequest as ControlProofRequest
    val result = client.connect(authRequest)

    expectTrue("qr connecting", result is DesktopControlConnectResult.Connecting)
    expectEquals("qr url", "https://192.168.1.44:44383/control", openedRequests.single().url.toString())
    expectEquals("qr session header", "session-001", openedRequests.single().header("X-BT-Gun-Session"))
    expectEquals("qr nonce header", "11".repeat(16), openedRequests.single().header("X-BT-Gun-Android-Nonce"))
    expectEquals("qr proof header", authRequest.proofHex, openedRequests.single().header("X-BT-Gun-Pairing-Proof"))
    expectEquals("qr manual code header", null, openedRequests.single().header("X-BT-Gun-Manual-Code"))
    expectEquals("trusted host", "192.168.1.44", request.trustedMetadata(1L).lastHost)
    expectEquals("trusted fingerprint", FINGERPRINT, request.trustedMetadata(1L).fingerprintSha256)
}

private fun manualPayloadBuildsCodeAuthWithoutSidOrChallenge() {
    val request = DesktopControlConnectionRequest.fromManualPayload(
        payload = ManualPairingPayload(
            host = "192.168.1.44",
            port = 44383,
            code = "123456",
            desktopSpkiSha256Suffix = FINGERPRINT.takeLast(8),
        ),
        trustedDesktop = TrustedDesktopMetadata(
            fingerprintSha256 = FINGERPRINT,
            displayName = "BT Gun Desktop",
            lastHost = "192.168.1.44",
            lastPort = 44383,
            lastSeenEpochMillis = 10L,
        ),
        androidNonce = "11".repeat(16),
    )
    val authRequest = request.authRequest as ManualCodeAuthRequest
    val openedRequests = mutableListOf<Request>()
    val client = DesktopControlClient(
        config = request.config,
        socketFactory = { openedRequest, _: WebSocketListener ->
            openedRequests += openedRequest
            FakeSocket()
        },
    )

    val result = client.connect(authRequest)

    expectTrue("manual connecting", result is DesktopControlConnectResult.Connecting)
    expectEquals("manual url", "wss://192.168.1.44:44383/control", request.config.url)
    expectEquals("manual expected sid", null, authRequest.expectedSessionId)
    expectEquals("manual code", "123456", authRequest.code)
    expectEquals("manual code header", "123456", openedRequests.single().header("X-BT-Gun-Manual-Code"))
    expectEquals("manual session header", null, openedRequests.single().header("X-BT-Gun-Session"))
    expectEquals("manual proof header", null, openedRequests.single().header("X-BT-Gun-Pairing-Proof"))
}

private fun manualAuthLearnsSessionIdFromSessionReady() {
    val socket = FakeSocket()
    var listener: WebSocketListener? = null
    var authenticated = 0
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = ManualCodeAuthRequest(
            androidNonce = "11".repeat(16),
            desktopSpkiSha256 = FINGERPRINT,
            code = "123456",
        ),
        onAuthenticated = { authenticated += 1 },
    )
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "manual-sid-001"))
    listener?.onMessage(NOOP_WEB_SOCKET, envelopeText(ControlMessageType.HEARTBEAT_PING, sessionId = "manual-sid-001"))

    val pong = ControlEnvelopeCodec.decode(socket.sent.single()) as ControlDecodeResult.Accepted
    expectEquals("manual authenticated once", 1, authenticated)
    expectEquals("manual ready phase", DesktopLinkPhase.CONNECTED, client.currentLinkState().phase)
    expectEquals("manual pong sid", "manual-sid-001", pong.envelope.sessionId)
}

private fun sessionReadyInitializesHeartbeatFreshness() {
    var listener: WebSocketListener? = null
    var now = 5_000_000_000L
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { now },
    )

    client.connect(proofRequest())
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope())

    expectEquals("ready freshness phase", DesktopLinkPhase.CONNECTED, client.currentLinkState().phase)
    expectEquals("ready freshness age", 0L, client.currentLinkState().heartbeatAgeMillis)

    now = 5_900_000_000L
    val fresh = client.refreshLiveness(nowElapsedNanos = now)

    expectEquals("ready remains fresh", DesktopLinkPhase.CONNECTED, fresh.phase)
    expectEquals("ready freshness advances", 900L, fresh.heartbeatAgeMillis)
}

private fun trustMismatchMovesToTrustProblemWithoutOpeningSocket() {
    var opened = false
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, _ ->
            opened = true
            FakeSocket()
        },
    )

    val result = client.connect(proofRequest(desktopSpkiSha256 = OTHER_FINGERPRINT))

    expectEquals("mismatch result", DesktopControlConnectResult.TrustMismatch(FINGERPRINT, OTHER_FINGERPRINT), result)
    expectFalse("no socket opened", opened)
    expectEquals("trust problem phase", DesktopLinkPhase.TRUST_PROBLEM, client.currentLinkState().phase)
    expectEquals("trust error", "desktop fingerprint mismatch", client.currentLinkState().lastControlError)
}

private fun clientSendRejectsInvalidEnvelopeBeforeSocketWrite() {
    val socket = FakeSocket()
    var listener: WebSocketListener? = null
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 128,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )
    val connection = client.connect(proofRequest())
    expectTrue("connecting", connection is DesktopControlConnectResult.Connecting)
    expectEquals("pre-auth send blocked", DesktopControlSendResult.NotConnected, client.send(envelope(ControlMessageType.PAIRING_STATE)))
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope())

    val valid = client.send(envelope(ControlMessageType.PAIRING_STATE))
    val invalid = client.send(
        envelope(
            ControlMessageType.DIAGNOSTICS,
            body = JsonObject(mapOf("lastControlError" to JsonPrimitive("x".repeat(256)))),
        ),
    )

    expectEquals("valid sent", DesktopControlSendResult.Sent, valid)
    expectEquals("invalid rejected", DesktopControlSendResult.Rejected(ControlEnvelopeError.OVERSIZED), invalid)
    expectEquals("one socket send", 1, socket.sent.size)
}

private fun desktopLinkHeartbeatMapsLivenessStates() {
    val client = clientWithFakeSocket()
    val connection = client.connect(proofRequest())
    expectTrue("connecting", connection is DesktopControlConnectResult.Connecting)
    client.markReadyForTest()

    client.observeHeartbeatPong(nowElapsedNanos = 1_000_000_000L)
    expectEquals("fresh link", DesktopLinkPhase.CONNECTED, client.currentLinkState().phase)

    client.refreshLiveness(nowElapsedNanos = 2_500_000_001L)
    expectEquals("stale link", DesktopLinkPhase.DEGRADED, client.currentLinkState().phase)

    client.refreshLiveness(nowElapsedNanos = 4_000_000_001L)
    expectEquals("missing link", DesktopLinkPhase.DISCONNECTED, client.currentLinkState().phase)
}

private fun hardSocketCloseStaysDisconnectedDuringLivenessRefresh() {
    var listener: WebSocketListener? = null
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(proofRequest())
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope())
    listener?.onClosed(NOOP_WEB_SOCKET, 1001, "wifi link closed")
    val refreshed = client.refreshLiveness(nowElapsedNanos = 1_200_000_000L)

    expectEquals("hard close phase", DesktopLinkPhase.DISCONNECTED, refreshed.phase)
    expectEquals("hard close error", "wifi link closed", refreshed.lastControlError)
}

private fun livenessCoordinatorStartsOnlyAfterAuthStopsOnTimeoutAndIgnoresStaleClients() {
    val staleClient = clientWithFakeSocket()
    staleClient.connect(proofRequest())
    staleClient.markReadyForTest()
    val newClient = clientWithFakeSocket()
    newClient.connect(proofRequest())
    newClient.markReadyForTest()
    val currentState = DesktopLinkState(
        phase = DesktopLinkPhase.CONNECTED,
        desktopDisplayName = "BT Gun Desktop",
        fingerprintSuffix = "11223344",
        profileDisplayName = "Default",
        profileRevision = 2L,
    )
    val coordinator = DesktopLivenessCoordinator()

    val beforeAuth = coordinator.refresh(
        client = staleClient,
        currentState = currentState,
        nowElapsedNanos = 2_500_000_001L,
    )
    expectEquals("inactive phase unchanged", DesktopLinkPhase.CONNECTED, beforeAuth.linkState.phase)
    expectFalse("inactive no polling", beforeAuth.shouldContinuePolling)
    expectFalse("inactive no close", beforeAuth.shouldCloseClient)

    coordinator.start(staleClient)
    val degraded = coordinator.refresh(
        client = staleClient,
        currentState = currentState,
        nowElapsedNanos = 2_500_000_001L,
    )
    expectEquals("coordinator stale", DesktopLinkPhase.DEGRADED, degraded.linkState.phase)
    expectEquals("coordinator profile kept", "Default", degraded.linkState.profileDisplayName)
    expectEquals("coordinator suffix kept", "11223344", degraded.linkState.fingerprintSuffix)
    expectTrue("coordinator keeps polling", degraded.shouldContinuePolling)
    expectFalse("coordinator degraded no close", degraded.shouldCloseClient)

    coordinator.start(newClient)
    val staleIgnored = coordinator.refresh(
        client = staleClient,
        currentState = currentState,
        nowElapsedNanos = 4_000_000_001L,
    )
    expectEquals("stale client ignored", DesktopLinkPhase.CONNECTED, staleIgnored.linkState.phase)
    expectFalse("stale client no clear", staleIgnored.shouldClearClient)

    val expired = coordinator.refresh(
        client = newClient,
        currentState = currentState,
        nowElapsedNanos = 4_000_000_001L,
    )
    expectEquals("coordinator expired", DesktopLinkPhase.DISCONNECTED, expired.linkState.phase)
    expectEquals("coordinator timeout error", DesktopLivenessCoordinator.DEFAULT_TIMEOUT_ERROR, expired.linkState.lastControlError)
    expectFalse("coordinator stop polling", expired.shouldContinuePolling)
    expectTrue("coordinator clear client", expired.shouldClearClient)
    expectTrue("coordinator close timed out client", expired.shouldCloseClient)

    val afterTimeout = coordinator.refresh(
        client = newClient,
        currentState = currentState,
        nowElapsedNanos = 4_100_000_000L,
    )
    expectEquals("coordinator stopped after timeout", DesktopLinkPhase.CONNECTED, afterTimeout.linkState.phase)
    expectFalse("coordinator no polling after timeout", afterTimeout.shouldContinuePolling)
}

private fun clientPublishesPreAuthCloseAsFailure() {
    var listener: WebSocketListener? = null
    val stateChanges = mutableListOf<DesktopLinkState>()
    val failures = mutableListOf<String>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
    )

    client.connect(
        authRequest = proofRequest(),
        onConnectionFailure = failures::add,
        onLinkStateChanged = stateChanges::add,
    )
    listener?.onClosed(NOOP_WEB_SOCKET, 1008, "pairing proof rejected")

    expectEquals("failure reason", listOf("pairing proof rejected"), failures)
    expectEquals("close state", DesktopLinkPhase.DISCONNECTED, stateChanges.last().phase)
    expectEquals("client close error", "pairing proof rejected", client.currentLinkState().lastControlError)
}

private fun clientPublishesFullFailureReason() {
    var listener: WebSocketListener? = null
    val failures = mutableListOf<String>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
    )

    client.connect(
        authRequest = proofRequest(),
        onConnectionFailure = failures::add,
    )
    listener?.onFailure(NOOP_WEB_SOCKET, javax.net.ssl.SSLPeerUnverifiedException("Hostname 192.168.50.25 not verified"), null)

    expectEquals(
        "failure reason",
        listOf("SSLPeerUnverifiedException: Hostname 192.168.50.25 not verified"),
        failures,
    )
    expectEquals("failure state", DesktopLinkPhase.DISCONNECTED, client.currentLinkState().phase)
    expectEquals("failure error", failures.single(), client.currentLinkState().lastControlError)
}

private fun clientRespondsToHeartbeatAndAppliesLiveMetadata() {
    val socket = FakeSocket()
    var listener: WebSocketListener? = null
    val linkStates = mutableListOf<DesktopLinkState>()
    val profiles = mutableListOf<ProfileMetadata>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = proofRequest(),
        onLinkStateChanged = linkStates::add,
        onProfileMetadataReceived = profiles::add,
    )
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope())
    listener?.onMessage(NOOP_WEB_SOCKET, envelopeText(ControlMessageType.HEARTBEAT_PING))
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(
            type = ControlMessageType.DIAGNOSTICS,
            body = JsonObject(
                mapOf(
                    "sessionState" to JsonPrimitive("degraded"),
                    "desktopIdentitySuffix" to JsonPrimitive("11223344"),
                    "heartbeatAgeMillis" to JsonPrimitive(250L),
                    "lastControlError" to JsonPrimitive("none"),
                ),
            ),
        ),
    )
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(
            type = ControlMessageType.PROFILE_METADATA,
            body = JsonObject(
                mapOf(
                    "profileId" to JsonPrimitive("default"),
                    "displayName" to JsonPrimitive("Default"),
                    "revision" to JsonPrimitive(2L),
                ),
            ),
        ),
    )

    val pong = ControlEnvelopeCodec.decode(socket.sent.single()) as ControlDecodeResult.Accepted
    expectEquals("pong type", ControlMessageType.HEARTBEAT_PONG, pong.envelope.type)
    expectEquals("diagnostic state", DesktopLinkPhase.DEGRADED, linkStates.last().phase)
    expectEquals("profile callback", ProfileMetadata("default", "Default", 2L), profiles.single())
}


private fun clientCloseStopsSocketAndDisconnectsLinkState() {
    val socket = FakeSocket()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, _ -> socket },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )
    client.connect(proofRequest())
    client.markReadyForTest()

    client.close()

    expectTrue("socket closed", socket.closed)
    expectEquals("link disconnected", DesktopLinkPhase.DISCONNECTED, client.currentLinkState().phase)
    expectEquals("send after close", DesktopControlSendResult.NotConnected, client.send(envelope(ControlMessageType.PAIRING_STATE)))
}

private fun clientUpdatesLinkStateFromHeartbeatDiagnosticsAndErrors() {
    val client = clientWithFakeSocket()
    client.connect(proofRequest())
    client.markReadyForTest()
    client.observeHeartbeatPing(nowElapsedNanos = 1_000_000_000L)
    client.applyDiagnostics(
        ControlDiagnostics(
            sessionState = "connected",
            desktopIdentitySuffix = "11223344",
            heartbeatAgeMillis = 250L,
            lastControlError = "none",
        ),
    )

    expectEquals("suffix", "11223344", client.currentLinkState().fingerprintSuffix)
    expectEquals("heartbeat age", 250L, client.currentLinkState().heartbeatAgeMillis)
    expectEquals("diagnostic error", "none", client.currentLinkState().lastControlError)

    client.recordControlError("decode_error")

    expectEquals("recorded error", "decode_error", client.currentLinkState().lastControlError)
    expectEquals(
        "diagnostic fields",
        listOf("sessionState", "desktopIdentitySuffix", "heartbeatAgeMillis", "lastControlError"),
        dataFieldNames(ControlDiagnostics::class.java),
    )
}

private fun profileMetadataModelContainsOnlyRequiredFields() {
    val profile = ProfileMetadata(
        profileId = "default",
        displayName = "Default profile",
        revision = 1L,
    )

    expectEquals("profile display", "Default profile", profile.displayName)
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
    ).forEach { type ->
        val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope(type)))
        expectTrue("${type.wireName} accepted", decoded is ControlDecodeResult.Accepted)
    }
}

private fun controlEnvelopeAllowsInputStreamConfigType() {
    expectEquals("input config wire name", "input_stream_config", ControlMessageType.INPUT_STREAM_CONFIG.wireName)

    val decoded = ControlEnvelopeCodec.decode(
        ControlEnvelopeCodec.encode(envelope(ControlMessageType.INPUT_STREAM_CONFIG, body = inputStreamConfigBody())),
    )

    expectTrue("input stream config accepted", decoded is ControlDecodeResult.Accepted)
}

private fun clientRejectsInputStreamConfigBeforeSessionReady() {
    var listener: WebSocketListener? = null
    val configs = mutableListOf<InputStreamConfig>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = ManualCodeAuthRequest(
            androidNonce = "11".repeat(16),
            desktopSpkiSha256 = FINGERPRINT,
            code = "123456",
        ),
        onInputStreamConfigReceived = configs::add,
    )
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.INPUT_STREAM_CONFIG, sessionId = "manual-sid-001", body = inputStreamConfigBody()),
    )

    expectEquals("pre-ready config ignored", emptyList<InputStreamConfig>(), configs)
    expectEquals("pre-ready error", "session not ready", client.currentLinkState().lastControlError)
}

private fun clientRejectsQrInputStreamConfigBeforeSessionReady() {
    var listener: WebSocketListener? = null
    val configs = mutableListOf<InputStreamConfig>()
    val socket = FakeSocket()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = proofRequest(),
        onInputStreamConfigReceived = configs::add,
    )
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.INPUT_STREAM_CONFIG, sessionId = "sid-1", body = inputStreamConfigBody()),
    )

    expectEquals("qr pre-ready config ignored", emptyList<InputStreamConfig>(), configs)
    expectEquals("qr pre-ready send blocked", DesktopControlSendResult.NotConnected, client.send(envelope(ControlMessageType.PAIRING_STATE)))
    expectEquals("qr pre-ready socket no response", emptyList<String>(), socket.sent)
    expectEquals("qr pre-ready error", "session not ready", client.currentLinkState().lastControlError)
}

private fun clientRejectsInputStreamConfigForMismatchedSession() {
    var listener: WebSocketListener? = null
    val configs = mutableListOf<InputStreamConfig>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(authRequest = proofRequest(), onInputStreamConfigReceived = configs::add)
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "sid-1"))
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.INPUT_STREAM_CONFIG, sessionId = "sid-2", body = inputStreamConfigBody()),
    )

    expectEquals("mismatch config ignored", emptyList<InputStreamConfig>(), configs)
    expectEquals("mismatch error", "session mismatch", client.currentLinkState().lastControlError)
}

private fun clientPublishesTrustedInputStreamConfigAfterSessionReady() {
    var listener: WebSocketListener? = null
    val configs = mutableListOf<InputStreamConfig>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(authRequest = proofRequest(), onInputStreamConfigReceived = configs::add)
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "sid-1"))
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.INPUT_STREAM_CONFIG, sessionId = "sid-1", body = inputStreamConfigBody()),
    )

    expectEquals("trusted config callback", listOf(fixtureConfig()), configs)
}

private fun controlEnvelopeAllowsHapticCommandAndResultTypes() {
    expectEquals("haptic command wire name", "reserved_haptic_command", ControlMessageType.RESERVED_HAPTIC_COMMAND.wireName)
    expectEquals("haptic result wire name", "haptic_result", ControlMessageType.HAPTIC_RESULT.wireName)

    val commandDecoded = ControlEnvelopeCodec.decode(
        ControlEnvelopeCodec.encode(
            envelope(
                ControlMessageType.RESERVED_HAPTIC_COMMAND,
                body = hapticCommandBody(),
            ),
        ),
    )
    val resultDecoded = ControlEnvelopeCodec.decode(
        ControlEnvelopeCodec.encode(
            envelope(
                ControlMessageType.HAPTIC_RESULT,
                body = JsonObject(
                    mapOf(
                        "commandId" to JsonPrimitive("cmd-001"),
                        "status" to JsonPrimitive(HapticResultStatus.STARTED.wireName),
                        "detail" to JsonPrimitive("phone pulse started"),
                        "observedElapsedNanos" to JsonPrimitive(1_050_000_000L),
                    ),
                ),
            ),
        ),
    )

    expectTrue("haptic command accepted", commandDecoded is ControlDecodeResult.Accepted)
    expectTrue("haptic result accepted", resultDecoded is ControlDecodeResult.Accepted)
}

private fun clientRejectsHapticCommandBeforeSessionReady() {
    var listener: WebSocketListener? = null
    val handled = mutableListOf<DesktopHapticCommand>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = ManualCodeAuthRequest(
            androidNonce = "11".repeat(16),
            desktopSpkiSha256 = FINGERPRINT,
            code = "123456",
        ),
        onHapticCommandReceived = { command, _ ->
            handled += command
            hapticResult(command, HapticResultStatus.STARTED, "phone pulse started")
        },
    )
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.RESERVED_HAPTIC_COMMAND, sessionId = "manual-sid-001", body = hapticCommandBody()),
    )

    expectEquals("pre-ready haptic ignored", emptyList<DesktopHapticCommand>(), handled)
    expectEquals("pre-ready haptic error", "session not ready", client.currentLinkState().lastControlError)
}

private fun clientRejectsQrHapticCommandBeforeSessionReady() {
    var listener: WebSocketListener? = null
    val handled = mutableListOf<DesktopHapticCommand>()
    val socket = FakeSocket()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = proofRequest(),
        onHapticCommandReceived = { command, _ ->
            handled += command
            hapticResult(command, HapticResultStatus.STARTED, "phone pulse started")
        },
    )
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.RESERVED_HAPTIC_COMMAND, sessionId = "sid-1", body = hapticCommandBody()),
    )

    expectEquals("qr pre-ready haptic ignored", emptyList<DesktopHapticCommand>(), handled)
    expectEquals("qr pre-ready haptic no result", emptyList<String>(), socket.sent)
    expectEquals("qr pre-ready haptic error", "session not ready", client.currentLinkState().lastControlError)
}

private fun clientRejectsHapticCommandForMismatchedSession() {
    var listener: WebSocketListener? = null
    val handled = mutableListOf<DesktopHapticCommand>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 1024,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            FakeSocket()
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    client.connect(
        authRequest = proofRequest(),
        onHapticCommandReceived = { command, _ ->
            handled += command
            hapticResult(command, HapticResultStatus.STARTED, "phone pulse started")
        },
    )
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "sid-1"))
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.RESERVED_HAPTIC_COMMAND, sessionId = "sid-2", body = hapticCommandBody()),
    )

    expectEquals("mismatch haptic ignored", emptyList<DesktopHapticCommand>(), handled)
    expectEquals("mismatch haptic error", "session mismatch", client.currentLinkState().lastControlError)
}

private fun clientHandlesTrustedHapticCommandAndSendsResult() {
    val socket = FakeSocket()
    var listener: WebSocketListener? = null
    val handled = mutableListOf<DesktopHapticCommand>()
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 2048,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_050_000_000L },
    )

    client.connect(
        authRequest = proofRequest(),
        onHapticCommandReceived = { command, receivedElapsedNanos ->
            handled += command
            expectEquals("received time", 1_050_000_000L, receivedElapsedNanos)
            hapticResult(command, HapticResultStatus.STARTED, "phone pulse started")
        },
    )
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "sid-1"))
    listener?.onMessage(
        NOOP_WEB_SOCKET,
        envelopeText(ControlMessageType.RESERVED_HAPTIC_COMMAND, sessionId = "sid-1", body = hapticCommandBody()),
    )

    expectEquals("haptic handled", listOf(fixtureHapticCommand()), handled)
    val result = ControlEnvelopeCodec.decode(socket.sent.single()) as ControlDecodeResult.Accepted
    expectEquals("result type", ControlMessageType.HAPTIC_RESULT, result.envelope.type)
    expectEquals("result status", HapticResultStatus.STARTED.wireName, result.envelope.body["status"]?.jsonPrimitive?.content)
    expectEquals("result detail", "phone pulse started", result.envelope.body["detail"]?.jsonPrimitive?.content)
}

private fun clientPreservesHapticResultDetailsFromCallback() {
    listOf(
        HapticResultStatus.PERMISSION_BLOCKED to "vibrate permission blocked",
        HapticResultStatus.FAILED to "IllegalStateException",
        HapticResultStatus.EXPIRED to "haptic command expired",
    ).forEach { (status, detail) ->
        val socket = FakeSocket()
        var listener: WebSocketListener? = null
        val client = DesktopControlClient(
            config = DesktopControlClientConfig(
                url = "wss://192.168.50.25:41731/control",
                expectedDesktopSpkiSha256 = FINGERPRINT,
                maxMessageBytes = 2048,
            ),
            socketFactory = { _, socketListener ->
                listener = socketListener
                socket
            },
            elapsedRealtimeNanos = { 1_050_000_000L },
        )

        client.connect(
            authRequest = proofRequest(),
            onHapticCommandReceived = { command, receivedElapsedNanos ->
                expectEquals("${status.wireName} received time", 1_050_000_000L, receivedElapsedNanos)
                hapticResult(command, status, detail, observedElapsedNanos = 1_060_000_000L)
            },
        )
        listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "sid-1"))
        listener?.onMessage(
            NOOP_WEB_SOCKET,
            envelopeText(ControlMessageType.RESERVED_HAPTIC_COMMAND, sessionId = "sid-1", body = hapticCommandBody()),
        )

        val result = ControlEnvelopeCodec.decode(socket.sent.single()) as ControlDecodeResult.Accepted
        expectEquals("${status.wireName} result status", status.wireName, result.envelope.body["status"]?.jsonPrimitive?.content)
        expectEquals("${status.wireName} result detail", detail, result.envelope.body["detail"]?.jsonPrimitive?.content)
    }
}

private fun clientCanSendCancellationHapticResultBeforeClose() {
    val socket = FakeSocket()
    var listener: WebSocketListener? = null
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 2048,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            socket
        },
        elapsedRealtimeNanos = { 1_050_000_000L },
    )
    val result = HapticResult(
        commandId = "cmd-cancel",
        status = HapticResultStatus.CANCELLED,
        detail = "phone pulse cancelled",
        observedElapsedNanos = 1_050_000_000L,
    )

    client.connect(authRequest = proofRequest())
    expectEquals("pre-ready result blocked", DesktopControlSendResult.NotConnected, client.sendHapticResult(result))
    listener?.onMessage(NOOP_WEB_SOCKET, readyEnvelope(sessionId = "sid-1"))

    expectEquals("cancel result sent", DesktopControlSendResult.Sent, client.sendHapticResult(result))
    val envelope = ControlEnvelopeCodec.decode(socket.sent.single()) as ControlDecodeResult.Accepted
    expectEquals("result type", ControlMessageType.HAPTIC_RESULT, envelope.envelope.type)
    expectEquals("result command", "cmd-cancel", envelope.envelope.body["commandId"]?.jsonPrimitive?.content)
    expectEquals("result status", HapticResultStatus.CANCELLED.wireName, envelope.envelope.body["status"]?.jsonPrimitive?.content)
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

private fun readyEnvelope(sessionId: String = "sid-1"): String =
    ControlEnvelopeCodec.encode(envelope(ControlMessageType.SESSION_READY, sessionId = sessionId))

private fun envelopeText(
    type: ControlMessageType,
    sessionId: String = "sid-1",
    body: JsonObject = JsonObject(emptyMap()),
): String =
    ControlEnvelopeCodec.encode(envelope(type = type, sessionId = sessionId, body = body))

private fun proofRequest(desktopSpkiSha256: String = FINGERPRINT): ControlProofRequest =
    ControlProofRequest(
        sid = "sid-1",
        androidNonce = "aa".repeat(16),
        desktopSpkiSha256 = desktopSpkiSha256,
        proofHex = "bb".repeat(32),
    )

private fun inputStreamConfigBody(): JsonObject =
    JsonObject(
        mapOf(
            "streamSessionIdHex" to JsonPrimitive(STREAM_SESSION_ID_HEX),
            "udpHost" to JsonPrimitive("192.168.1.44"),
            "udpPort" to JsonPrimitive(41731),
            "hmacSha256KeyBase64Url" to JsonPrimitive(HMAC_KEY_BASE64URL),
            "snapshotHz" to JsonPrimitive(60),
            "frameAgeLimitMs" to JsonPrimitive(150L),
            "streamTimeoutMs" to JsonPrimitive(250L),
            "controlDisconnectGraceMs" to JsonPrimitive(1500L),
        ),
    )

private fun hapticCommandBody(): JsonObject =
    DesktopHapticCommand(
        commandId = "cmd-001",
        strength = 0.75,
        durationMs = 120L,
        ttlMs = 500L,
    ).toJsonBody()

private fun fixtureHapticCommand(): DesktopHapticCommand =
    DesktopHapticCommand(
        commandId = "cmd-001",
        strength = 0.75,
        durationMs = 120L,
        ttlMs = 500L,
    )

private fun hapticResult(
    command: DesktopHapticCommand,
    status: HapticResultStatus,
    detail: String,
    observedElapsedNanos: Long = 1_050_000_000L,
): HapticResult =
    HapticResult(
        commandId = command.commandId,
        status = status,
        detail = detail,
        observedElapsedNanos = observedElapsedNanos,
    )

private fun fixtureConfig(): InputStreamConfig =
    InputStreamConfig(
        streamSessionIdHex = STREAM_SESSION_ID_HEX,
        udpHost = "192.168.1.44",
        udpPort = 41731,
        hmacSha256KeyBase64Url = HMAC_KEY_BASE64URL,
        snapshotHz = 60,
        frameAgeLimitMs = 150,
        streamTimeoutMs = 250,
        controlDisconnectGraceMs = 1500,
    )

private fun clientWithFakeSocket(): DesktopControlClient {
    var listener: WebSocketListener? = null
    return DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, socketListener ->
            listener = socketListener
            TestSocket(FakeSocket(), { listener })
        },
        elapsedRealtimeNanos = { 1_000_000_000L },
    )
}

private fun DesktopControlClient.markReadyForTest() {
    (this.currentTestSocket())?.listener()?.onMessage(NOOP_WEB_SOCKET, readyEnvelope())
}

private fun DesktopControlClient.currentTestSocket(): TestSocket? {
    val field = DesktopControlClient::class.java.getDeclaredField("socket")
    field.isAccessible = true
    return field.get(this) as? TestSocket
}

private class TestSocket(
    private val delegate: FakeSocket,
    val listener: () -> WebSocketListener?,
) : DesktopControlSocket {
    override fun send(text: String): Boolean = delegate.send(text)

    override fun close() {
        delegate.close()
    }
}

private class FakeSocket : DesktopControlSocket {
    val sent = mutableListOf<String>()
    var closed = false

    override fun send(text: String): Boolean {
        sent += text
        return true
    }

    override fun close() {
        closed = true
    }
}

private val NOOP_WEB_SOCKET = object : WebSocket {
    override fun request(): Request = Request.Builder().url("wss://192.168.50.25:41731/control").build()
    override fun queueSize(): Long = 0L
    override fun send(text: String): Boolean = true
    override fun send(bytes: ByteString): Boolean = true
    override fun close(code: Int, reason: String?): Boolean = true
    override fun cancel() = Unit
}

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
    expectTrue(label, !condition)
}

private fun dataFieldNames(type: Class<*>): List<String> =
    type.declaredFields
        .filterNot { it.isSynthetic }
        .map { it.name }

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
private const val OTHER_FINGERPRINT = "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
