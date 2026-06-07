package com.btgun.host.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import okhttp3.WebSocketListener

fun main() {
    envelopeCodecMirrorsDesktopAllowlist()
    envelopeCodecRejectsVersionUnknownTypeOversizedAndReservedHapticBody()
    clientBuildsPinnedWssRequestAndTrustMismatchResult()
    qrPayloadBuildsControlRequestAndProofHeaders()
    trustMismatchMovesToTrustProblemWithoutOpeningSocket()
    clientSendRejectsInvalidEnvelopeBeforeSocketWrite()
    desktopLinkHeartbeatMapsLivenessStates()
    clientCloseStopsSocketAndDisconnectsLinkState()
    clientUpdatesLinkStateFromHeartbeatDiagnosticsAndErrors()
    profileMetadataModelContainsOnlyRequiredFields()
    controlEnvelopeAllowsHeartbeatDiagnosticsAndProfileTypes()
}

private fun envelopeCodecMirrorsDesktopAllowlist() {
    val envelope = envelope(ControlMessageType.SESSION_READY)

    val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope))

    expectTrue("decoded accepted", decoded is ControlDecodeResult.Accepted)
    expectEquals("type", ControlMessageType.SESSION_READY, (decoded as ControlDecodeResult.Accepted).envelope.type)
    expectEquals("pairing wire name", "pairing_state", ControlMessageType.PAIRING_STATE.wireName)
    expectEquals("ready wire name", "session_ready", ControlMessageType.SESSION_READY.wireName)
    expectEquals("reserved haptic name", "reserved_haptic_command", ControlMessageType.RESERVED_HAPTIC_COMMAND.wireName)
}

private fun envelopeCodecRejectsVersionUnknownTypeOversizedAndReservedHapticBody() {
    val unsupportedVersion = """{"v":2,"type":"session_ready","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""
    val unknownType = """{"v":1,"type":"profile_update","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""
    val reservedBody = ControlEnvelopeCodec.encode(
        envelope(
            ControlMessageType.RESERVED_HAPTIC_COMMAND,
            body = JsonObject(mapOf("command" to JsonPrimitive("pulse"))),
        ),
    )

    expectRejected("version", ControlEnvelopeError.UNSUPPORTED_VERSION, ControlEnvelopeCodec.decode(unsupportedVersion))
    expectRejected("type", ControlEnvelopeError.UNKNOWN_TYPE, ControlEnvelopeCodec.decode(unknownType))
    expectRejected("size", ControlEnvelopeError.OVERSIZED, ControlEnvelopeCodec.decode(unknownType, maxBytes = 8))
    expectRejected("reserved haptic", ControlEnvelopeError.RESERVED_HAPTIC_BODY, ControlEnvelopeCodec.decode(reservedBody))
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

    expectTrue("connected", result is DesktopControlConnectResult.Connected)
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

    val result = client.connect(request.proofRequest)

    expectTrue("qr connect", result is DesktopControlConnectResult.Connected)
    expectEquals("qr url", "https://192.168.1.44:44383/control", openedRequests.single().url.toString())
    expectEquals("qr session header", "session-001", openedRequests.single().header("X-BT-Gun-Session"))
    expectEquals("qr nonce header", "11".repeat(16), openedRequests.single().header("X-BT-Gun-Android-Nonce"))
    expectEquals("qr proof header", request.proofRequest.proofHex, openedRequests.single().header("X-BT-Gun-Pairing-Proof"))
    expectEquals("trusted host", "192.168.1.44", request.trustedMetadata(1L).lastHost)
    expectEquals("trusted fingerprint", FINGERPRINT, request.trustedMetadata(1L).fingerprintSha256)
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
    val client = DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 128,
        ),
        socketFactory = { _, _ -> socket },
    )
    val connection = client.connect(proofRequest())
    expectTrue("connected", connection is DesktopControlConnectResult.Connected)

    val valid = client.send(envelope(ControlMessageType.PAIRING_STATE))
    val invalid = client.send(
        envelope(
            ControlMessageType.RESERVED_HAPTIC_COMMAND,
            body = JsonObject(mapOf("command" to JsonPrimitive("pulse"))),
        ),
    )

    expectEquals("valid sent", DesktopControlSendResult.Sent, valid)
    expectEquals("invalid rejected", DesktopControlSendResult.Rejected(ControlEnvelopeError.RESERVED_HAPTIC_BODY), invalid)
    expectEquals("one socket send", 1, socket.sent.size)
}

private fun desktopLinkHeartbeatMapsLivenessStates() {
    val client = clientWithFakeSocket()
    val connection = client.connect(proofRequest())
    expectTrue("connected", connection is DesktopControlConnectResult.Connected)

    client.observeHeartbeatPong(nowElapsedNanos = 1_000_000_000L)
    expectEquals("fresh link", DesktopLinkPhase.CONNECTED, client.currentLinkState().phase)

    client.refreshLiveness(nowElapsedNanos = 2_500_000_001L)
    expectEquals("stale link", DesktopLinkPhase.DEGRADED, client.currentLinkState().phase)

    client.refreshLiveness(nowElapsedNanos = 4_000_000_001L)
    expectEquals("missing link", DesktopLinkPhase.DISCONNECTED, client.currentLinkState().phase)
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
    )
    client.connect(proofRequest())

    client.close()

    expectTrue("socket closed", socket.closed)
    expectEquals("link disconnected", DesktopLinkPhase.DISCONNECTED, client.currentLinkState().phase)
    expectEquals("send after close", DesktopControlSendResult.NotConnected, client.send(envelope(ControlMessageType.PAIRING_STATE)))
}

private fun clientUpdatesLinkStateFromHeartbeatDiagnosticsAndErrors() {
    val client = clientWithFakeSocket()
    client.connect(proofRequest())
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

private fun envelope(
    type: ControlMessageType,
    body: JsonObject = JsonObject(emptyMap()),
): ControlEnvelope =
    ControlEnvelope(
        v = 1,
        type = type,
        msgId = "msg-1",
        sessionId = "sid-1",
        seq = 1L,
        sentElapsedNanos = 10L,
        body = body,
    )

private fun proofRequest(desktopSpkiSha256: String = FINGERPRINT): ControlProofRequest =
    ControlProofRequest(
        sid = "sid-1",
        androidNonce = "aa".repeat(16),
        desktopSpkiSha256 = desktopSpkiSha256,
        proofHex = "bb".repeat(32),
    )

private fun clientWithFakeSocket(): DesktopControlClient =
    DesktopControlClient(
        config = DesktopControlClientConfig(
            url = "wss://192.168.50.25:41731/control",
            expectedDesktopSpkiSha256 = FINGERPRINT,
            maxMessageBytes = 512,
        ),
        socketFactory = { _, _ -> FakeSocket() },
    )

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
