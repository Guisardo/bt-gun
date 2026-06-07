package com.btgun.desktop.control

import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSecurityState
import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.PairingProof
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun main() {
    envelopeCodecAcceptsOnlyVersionOneAndKnownTypes()
    envelopeCodecRejectsUnsupportedVersionUnknownTypeAndOversizedText()
    reservedHapticCommandAllowsEmptyBodyOnly()
    controlServerRejectsControlEnvelopeBeforeProof()
    controlServerAcceptsKnownEnvelopeAfterProof()
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
}

private fun envelopeCodecRejectsUnsupportedVersionUnknownTypeAndOversizedText() {
    val unsupportedVersion = """{"v":2,"type":"session_ready","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""
    val unknownType = """{"v":1,"type":"profile_update","msgId":"m-1","sessionId":"sid-1","seq":1,"sentElapsedNanos":10,"body":{}}"""

    expectRejected("unsupported version", ControlEnvelopeError.UNSUPPORTED_VERSION, ControlEnvelopeCodec.decode(unsupportedVersion))
    expectRejected("unknown type", ControlEnvelopeError.UNKNOWN_TYPE, ControlEnvelopeCodec.decode(unknownType))
    expectRejected("oversized", ControlEnvelopeError.OVERSIZED, ControlEnvelopeCodec.decode(unknownType, maxBytes = 12))
}

private fun reservedHapticCommandAllowsEmptyBodyOnly() {
    val reservedOnly = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope(ControlMessageType.RESERVED_HAPTIC_COMMAND)))
    val withExecutionShape = ControlEnvelopeCodec.decode(
        ControlEnvelopeCodec.encode(
            envelope(
                ControlMessageType.RESERVED_HAPTIC_COMMAND,
                body = JsonObject(
                    mapOf(
                        "command" to JsonPrimitive("pulse"),
                        "ttl_ms" to JsonPrimitive(500),
                    ),
                ),
            ),
        ),
    )

    expectTrue("reserved type accepted with empty body", reservedOnly is ControlDecodeResult.Accepted)
    expectRejected("reserved haptic body", ControlEnvelopeError.RESERVED_HAPTIC_BODY, withExecutionShape)
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

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
