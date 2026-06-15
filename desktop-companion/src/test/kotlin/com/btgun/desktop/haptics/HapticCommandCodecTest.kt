package com.btgun.desktop.haptics

import com.btgun.desktop.control.ControlDecodeResult
import com.btgun.desktop.control.ControlEnvelope
import com.btgun.desktop.control.ControlEnvelopeCodec
import com.btgun.desktop.control.ControlMessageType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

fun main() {
    hapticCommandBodyUsesReservedWireNameWithPulseFields()
    hapticResultBodyUsesExplicitResultStatusWireNames()
    hapticResultStatusesCoverAllPhaseFourOutcomes()
}

private fun hapticCommandBodyUsesReservedWireNameWithPulseFields() {
    val command = HapticCommand(
        commandId = "cmd-001",
        strength = 0.75,
        durationMs = 120L,
        ttlMs = 500L,
        pattern = null,
    )
    val envelope = envelope(
        type = ControlMessageType.RESERVED_HAPTIC_COMMAND,
        body = command.toJsonBody(),
    )

    val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope))

    expectTrue("haptic command body accepted", decoded is ControlDecodeResult.Accepted)
    val body = (decoded as ControlDecodeResult.Accepted).envelope.body
    expectEquals("command id", "cmd-001", body["commandId"]?.jsonPrimitive?.content)
    expectEquals("strength", "0.75", body["strength"]?.jsonPrimitive?.content)
    expectEquals("duration", "120", body["durationMs"]?.jsonPrimitive?.content)
    expectEquals("ttl", "500", body["ttlMs"]?.jsonPrimitive?.content)
    expectEquals("pattern", JsonNull, body["pattern"])
    expectEquals("round trip", command, HapticCommand.fromJsonBody(body))
}

private fun hapticResultBodyUsesExplicitResultStatusWireNames() {
    val result = HapticResult(
        commandId = "cmd-001",
        status = HapticResultStatus.STARTED,
        detail = "phone pulse started",
        observedElapsedNanos = 1_050_000_000L,
    )
    val envelope = envelope(
        type = ControlMessageType.HAPTIC_RESULT,
        body = result.toJsonBody(),
    )

    val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope))

    expectTrue("haptic result accepted", decoded is ControlDecodeResult.Accepted)
    val body = (decoded as ControlDecodeResult.Accepted).envelope.body
    expectEquals("status wire", "started", body["status"]?.jsonPrimitive?.content)
    expectEquals("result round trip", result, HapticResult.fromJsonBody(body))
}

private fun hapticResultStatusesCoverAllPhaseFourOutcomes() {
    expectEquals(
        "status wires",
        listOf("started", "expired", "unsupported", "permission_blocked", "failed", "cancelled"),
        HapticResultStatus.entries.map { it.wireName },
    )
}

private fun envelope(
    type: ControlMessageType,
    body: kotlinx.serialization.json.JsonObject,
): ControlEnvelope =
    ControlEnvelope(
        v = 1,
        type = type,
        msgId = "msg-1",
        sessionId = "sid-1",
        seq = 1L,
        sentElapsedNanos = 1_000L,
        body = body,
    )

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
