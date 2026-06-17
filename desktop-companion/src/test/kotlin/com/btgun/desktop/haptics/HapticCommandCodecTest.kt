package com.btgun.desktop.haptics

import com.btgun.desktop.control.ControlDecodeResult
import com.btgun.desktop.control.ControlEnvelope
import com.btgun.desktop.control.ControlEnvelopeCodec
import com.btgun.desktop.control.ControlMessageType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

fun main() {
    hapticCommandBodyUsesReservedWireNameWithPulseFields()
    hapticCommandBodyAllowsTimelineShape()
    sharedMatrixHapticRowsDecodeThroughReservedEnvelope()
    hapticResultBodyUsesExplicitResultStatusWireNames()
    hapticResultStatusesCoverAllPhaseFourOutcomes()
}

private fun hapticCommandBodyAllowsTimelineShape() {
    val command = HapticCommand(
        commandId = "cmd-pattern",
        strength = 0.5,
        durationMs = 1L,
        ttlMs = 500L,
        patternTimeline = listOf(
            HapticTimelinePulse(atMs = 0L, durationMs = 40L, strength = 0.4),
            HapticTimelinePulse(atMs = 100L, durationMs = 60L, strength = 1.0),
        ),
    )
    val envelope = envelope(
        type = ControlMessageType.RESERVED_HAPTIC_COMMAND,
        body = command.toJsonBody(),
    )

    val decoded = ControlEnvelopeCodec.decode(ControlEnvelopeCodec.encode(envelope))

    expectTrue("timeline command body accepted", decoded is ControlDecodeResult.Accepted)
    val body = (decoded as ControlDecodeResult.Accepted).envelope.body
    val timeline = body["patternTimeline"]?.jsonArray ?: error("missing patternTimeline")
    expectEquals("timeline size", 2, timeline.size)
    expectEquals("timeline first at", "0", timeline[0].jsonObject["atMs"]?.jsonPrimitive?.content)
    expectEquals("timeline round trip", command, HapticCommand.fromJsonBody(body))
}

private fun sharedMatrixHapticRowsDecodeThroughReservedEnvelope() {
    val rows = replayMatrixHapticRows()

    expectEquals(
        "haptic matrix categories",
        setOf("haptic_invalid_body", "haptic_unsupported_pattern", "haptic_invalid_timeline", "haptic_overlapping_timeline"),
        rows.map { row -> row.stringField("category") }.toSet(),
    )
    rows.forEach { row ->
        val body = row["body"]?.jsonObject ?: error("missing body")
        val decoded = ControlEnvelopeCodec.decode(
            ControlEnvelopeCodec.encode(
                envelope(ControlMessageType.RESERVED_HAPTIC_COMMAND, body),
            ),
        )

        expectTrue("${row.stringField("case_id")} envelope accepted", decoded is ControlDecodeResult.Accepted)
        if (row.stringField("expected_status") == "unsupported") {
            expectEquals("unsupported command parses", "cmd-pattern", HapticCommand.fromJsonBody(body)?.commandId)
        }
    }
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

private fun replayMatrixHapticRows(): List<JsonObject> {
    val file = repoFile("fixtures/replay/udp-golden/input-stream-v1-v2-matrix.jsonl")
    if (!file.exists()) {
        throw AssertionError("missing shared replay matrix fixture: ${file.path}")
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { Json.parseToJsonElement(it).jsonObject }
        .filter { row -> row.stringField("record_type") == "haptic" }
}

private fun repoFile(path: String): File =
    listOf(File(path), File("../$path"), File("../../$path"))
        .firstOrNull { it.exists() }
        ?: File(path)

private fun JsonObject.stringField(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw AssertionError("missing string field $name")
