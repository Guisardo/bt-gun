package com.btgun.desktop.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

fun main() {
    replayGuardAcceptsFirstValidFrameAndRejectsReplayCases()
    replayGuardRejectsWrongSessionMalformedBadMacAgeExpiredAndAcceptsClockSkewedDatagrams()
    replayGuardAcceptsCompactFrameThroughMuxAndUsesSendTimestampFreshness()
    replayGuardConsumesSharedFixtureMatrix()
    timeoutClearsControlsButPreservesRawMotion()
}

private fun replayGuardAcceptsFirstValidFrameAndRejectsReplayCases() {
    val guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )
    val firstFrame = frame(sequence = 10L, buttonBitmask = 0x23, rawAimX = 0.125f, rawAimY = -0.25f)
    val nextSnapshot = frame(
        type = UdpInputFrameType.SNAPSHOT,
        sequence = 12L,
        buttonBitmask = 0x01,
        rawAimX = 0.5f,
        rawAimY = -0.75f,
    )
    val lateEdge = frame(type = UdpInputFrameType.EDGE, sequence = 11L, buttonBitmask = 0x3f)

    val first = guard.accept(firstFrame, receivedElapsedNanos = 1_111_111_300L, controlSessionId = CONTROL_SESSION_ID)
    val duplicate = guard.accept(firstFrame, receivedElapsedNanos = 1_111_111_301L, controlSessionId = CONTROL_SESSION_ID)
    val snapshot = guard.accept(nextSnapshot, receivedElapsedNanos = 1_111_111_350L, controlSessionId = CONTROL_SESSION_ID)
    val old = guard.accept(frame(sequence = 9L), receivedElapsedNanos = 1_111_111_351L, controlSessionId = CONTROL_SESSION_ID)
    val late = guard.accept(lateEdge, receivedElapsedNanos = 1_111_111_352L, controlSessionId = CONTROL_SESSION_ID)

    expectAccepted("first accepted", first, sequence = 10L, buttonBitmask = 0x23)
    expectRejected("duplicate rejected", InputReplayRejectReason.DUPLICATE_SEQUENCE, duplicate)
    expectAccepted("newer snapshot accepted", snapshot, sequence = 12L, buttonBitmask = 0x01)
    expectRejected("old rejected", InputReplayRejectReason.OLD_SEQUENCE, old)
    expectRejected("late edge rejected", InputReplayRejectReason.OLD_SEQUENCE, late)
    expectEquals("authoritative snapshot remains current", 0x01, guard.current?.buttons)
    expectEquals("snapshot raw aim x remains current", 0.5f, guard.current?.motion?.rawAimX)
    expectEquals("snapshot raw aim y remains current", -0.75f, guard.current?.motion?.rawAimY)
}

private fun replayGuardAcceptsCompactFrameThroughMuxAndUsesSendTimestampFreshness() {
    val config = fixtureConfig().copy(frameFormat = InputFrameFormat.COMPACT_V2)
    val guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = config,
    )
    val compact = UdpInputFrameCodec.encodeCompact(
        frame(
            sequence = 40L,
            captureElapsedNanos = 5_000_000_000L,
            sendElapsedNanos = 5_000_100_000L,
            buttonBitmask = 0x101,
        ),
        config,
    )
    val staleBySendElapsed = UdpInputFrameCodec.encodeCompact(
        frame(
            sequence = 41L,
            captureElapsedNanos = 5_000_000_000L,
            sendElapsedNanos = 5_151_000_001L,
        ),
        config,
    )

    expectAccepted(
        "compact accepted through mux",
        guard.acceptDatagram(compact, receivedElapsedNanos = 900_000_000_000_000L, controlSessionId = CONTROL_SESSION_ID),
        sequence = 40L,
        buttonBitmask = 0x101,
    )
    expectRejected(
        "compact stale uses send timestamp",
        InputReplayRejectReason.AGE_EXPIRED,
        guard.acceptDatagram(staleBySendElapsed, receivedElapsedNanos = 900_000_000_000_100L, controlSessionId = CONTROL_SESSION_ID),
    )
}

private fun replayGuardConsumesSharedFixtureMatrix() {
    val v1Guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )
    val v2Guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = fixtureConfig().copy(frameFormat = InputFrameFormat.COMPACT_V2),
    )
    val cases = replayGuardMatrixDatagrams()
    val expectedCaseIds = listOf(
        "v1-good-snapshot",
        "v1-duplicate-seq",
        "v2-good-snapshot",
        "v2-old-seq",
        "v1-stale-age",
        "v2-stale-age",
        "v1-bad-hmac",
        "v2-bad-hmac",
        "v1-wrong-stream",
        "v2-wrong-stream",
        "unexpected-format",
    )

    expectEquals("matrix replay case ids", expectedCaseIds, cases.map { it.caseId })
    cases.forEach { case ->
        val guard = if (case.frameFormat == "compact_v2") v2Guard else v1Guard
        val decision = guard.acceptDatagram(
            bytes = hexToBytes(case.hex),
            receivedElapsedNanos = MATRIX_RECEIVED_ELAPSED_NANOS + case.replayOrder,
            controlSessionId = CONTROL_SESSION_ID,
        )
        if (case.expectedReplay == "ACCEPTED") {
            expectMatrixAccepted(case.caseId, decision, sequence = requireNotNull(case.sequence))
        } else {
            expectRejected(case.caseId, InputReplayRejectReason.valueOf(case.expectedReplay), decision)
        }
    }
}

private fun replayGuardRejectsWrongSessionMalformedBadMacAgeExpiredAndAcceptsClockSkewedDatagrams() {
    val guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )

    val validBytes = UdpInputFrameCodec.encode(frame(sequence = 20L), fixtureConfig())
    val wrongStream = UdpInputFrameCodec.encode(frame(sequence = 21L, streamSessionId = OTHER_STREAM_SESSION_ID_HEX), otherStreamConfig())
    val badMac = validBytes.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }
    val malformed = validBytes.copyOfRange(0, validBytes.lastIndex)
    val skewedUptime = UdpInputFrameCodec.encode(
        frame(sequence = 22L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_000_001_000L),
        fixtureConfig(),
    )
    val senderLocalStale = UdpInputFrameCodec.encode(
        frame(sequence = 23L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_151_000_001L),
        fixtureConfig(),
    )

    expectRejected(
        "wrong control session rejected",
        InputReplayRejectReason.WRONG_CONTROL_SESSION,
        guard.accept(frame(sequence = 19L), receivedElapsedNanos = 1_111_111_300L, controlSessionId = "other-session"),
    )
    expectRejected(
        "wrong stream rejected",
        InputReplayRejectReason.WRONG_STREAM_SESSION,
        guard.acceptDatagram(wrongStream, receivedElapsedNanos = 1_111_111_300L, controlSessionId = CONTROL_SESSION_ID),
    )
    expectRejected(
        "bad mac rejected",
        InputReplayRejectReason.BAD_HMAC,
        guard.acceptDatagram(badMac, receivedElapsedNanos = 1_111_111_300L, controlSessionId = CONTROL_SESSION_ID),
    )
    expectRejected(
        "malformed rejected",
        InputReplayRejectReason.MALFORMED,
        guard.acceptDatagram(malformed, receivedElapsedNanos = 1_111_111_300L, controlSessionId = CONTROL_SESSION_ID),
    )
    expectAccepted(
        "clock-skewed uptime accepted",
        guard.acceptDatagram(skewedUptime, receivedElapsedNanos = 900_000_000_000_000L, controlSessionId = CONTROL_SESSION_ID),
        sequence = 22L,
        buttonBitmask = 0x23,
    )
    expectRejected(
        "sender-local stale frame rejected",
        InputReplayRejectReason.AGE_EXPIRED,
        guard.acceptDatagram(senderLocalStale, receivedElapsedNanos = 900_000_000_000_100L, controlSessionId = CONTROL_SESSION_ID),
    )
}

private fun timeoutClearsControlsButPreservesRawMotion() {
    val guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )
    val accepted = guard.accept(
        frame(sequence = 30L, buttonBitmask = 0x23, stickX = 12_345, stickY = -12_345, rawAimX = 0.125f, rawAimY = -0.25f),
        receivedElapsedNanos = 1_111_111_300L,
        controlSessionId = CONTROL_SESSION_ID,
    )
    expectAccepted("accepted before timeout", accepted, sequence = 30L, buttonBitmask = 0x23)

    val timedOut = guard.onTimeout(requireNotNull(guard.current))

    expectEquals("buttons cleared", 0, timedOut.buttons)
    expectEquals("pressed controls cleared", emptySet<String>(), timedOut.pressedControls)
    expectEquals("stick x cleared", 0, timedOut.stickX)
    expectEquals("stick y cleared", 0, timedOut.stickY)
    expectEquals("raw aim x preserved", 0.125f, timedOut.motion.rawAimX)
    expectEquals("raw aim y preserved", -0.25f, timedOut.motion.rawAimY)
    expectEquals("yaw preserved", 1.25f, timedOut.motion.yaw)
    expectEquals("pitch preserved", -2.5f, timedOut.motion.pitch)
    expectEquals("roll preserved", 0.75f, timedOut.motion.roll)
    expectTrue("timeout marks stale", timedOut.stale)
    expectEquals("sequence preserved", 30L, timedOut.lastAcceptedSequence)
}

private fun expectAccepted(label: String, actual: InputReplayDecision, sequence: Long, buttonBitmask: Int) {
    expectTrue(label, actual is InputReplayDecision.Accepted)
    val input = (actual as InputReplayDecision.Accepted).input
    expectEquals("$label sequence", sequence, input.lastAcceptedSequence)
    expectEquals("$label button bitmask", buttonBitmask, input.buttons)
    expectTrue("$label not stale", !input.stale)
}

private fun expectMatrixAccepted(label: String, actual: InputReplayDecision, sequence: Long) {
    expectTrue(label, actual is InputReplayDecision.Accepted)
    val input = (actual as InputReplayDecision.Accepted).input
    expectEquals("$label sequence", sequence, input.lastAcceptedSequence)
    expectTrue("$label not stale", !input.stale)
}

private fun expectRejected(label: String, expected: InputReplayRejectReason, actual: InputReplayDecision) {
    expectTrue(label, actual is InputReplayDecision.Rejected)
    expectEquals(label, expected, (actual as InputReplayDecision.Rejected).reason)
}

private fun frame(
    type: UdpInputFrameType = UdpInputFrameType.SNAPSHOT,
    streamSessionId: String = STREAM_SESSION_ID_HEX,
    sequence: Long,
    captureElapsedNanos: Long = 1_111_111_111L,
    sendElapsedNanos: Long = 1_111_111_222L,
    buttonBitmask: Int = 0x23,
    stickX: Int = 12_345,
    stickY: Int = -12_345,
    rawAimX: Float = 0.125f,
    rawAimY: Float = -0.25f,
): UdpInputFrame =
    UdpInputFrame(
        type = type,
        streamSessionId = streamSessionId,
        sequence = sequence,
        captureElapsedNanos = captureElapsedNanos,
        sendElapsedNanos = sendElapsedNanos,
        buttonBitmask = buttonBitmask,
        stickX = stickX,
        stickY = stickY,
        motionProvider = 2,
        motionCapabilityFlags = 0x07,
        yaw = 1.25f,
        pitch = -2.5f,
        roll = 0.75f,
        rawAimX = rawAimX,
        rawAimY = rawAimY,
        sourceSensorElapsedNanos = 1_111_111_000L,
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

private fun otherStreamConfig(): InputStreamConfig =
    fixtureConfig().copy(streamSessionIdHex = OTHER_STREAM_SESSION_ID_HEX)

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

private data class ReplayGuardMatrixCase(
    val caseId: String,
    val frameFormat: String,
    val replayOrder: Long,
    val expectedReplay: String,
    val hex: String,
    val sequence: Long?,
)

private fun replayGuardMatrixDatagrams(): List<ReplayGuardMatrixCase> {
    val file = replayGuardRepoFile("fixtures/replay/udp-golden/input-stream-v1-v2-matrix.jsonl")
    if (!file.exists()) {
        throw AssertionError("missing shared replay matrix fixture: ${file.path}")
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { Json.parseToJsonElement(it).jsonObject }
        .filter { it.guardStringField("record_type") == "datagram" }
        .map { row ->
            ReplayGuardMatrixCase(
                caseId = row.guardStringField("case_id"),
                frameFormat = row.guardStringField("frame_format"),
                replayOrder = row.guardLongField("replay_order"),
                expectedReplay = row.guardStringField("expected_replay"),
                hex = row.guardStringField("hex"),
                sequence = row.guardLongFieldOrNull("sequence"),
            )
        }
        .sortedBy { it.replayOrder }
}

private fun JsonObject.guardStringField(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw AssertionError("missing string field $name")

private fun JsonObject.guardLongField(name: String): Long =
    guardStringField(name).toLong()

private fun JsonObject.guardLongFieldOrNull(name: String): Long? =
    this[name]?.jsonPrimitive?.contentOrNull?.toLong()

private fun replayGuardRepoFile(path: String): File =
    listOf(File(path), File("../$path"), File("../../$path"))
        .firstOrNull { it.exists() }
        ?: File(path)

private fun hexToBytes(value: String): ByteArray =
    value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private const val CONTROL_SESSION_ID = "control-sid-1"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val OTHER_STREAM_SESSION_ID_HEX = "ffeeddccbbaa99887766554433221100"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val MATRIX_RECEIVED_ELAPSED_NANOS = 900_000_000_000_000L
