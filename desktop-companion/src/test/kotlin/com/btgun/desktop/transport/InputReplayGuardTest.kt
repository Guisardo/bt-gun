package com.btgun.desktop.transport

fun main() {
    replayGuardAcceptsFirstValidFrameAndRejectsReplayCases()
    replayGuardRejectsWrongSessionMalformedBadMacAgeExpiredAndAcceptsClockSkewedDatagrams()
    replayGuardAcceptsCompactFrameThroughMuxAndUsesSendTimestampFreshness()
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
    val guard = InputReplayGuard(
        trustedControlSessionId = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )
    val compact = UdpInputFrameCodec.encodeCompact(
        frame(
            sequence = 40L,
            captureElapsedNanos = 5_000_000_000L,
            sendElapsedNanos = 5_000_100_000L,
            buttonBitmask = 0x101,
        ),
        fixtureConfig(),
    )
    val staleBySendElapsed = UdpInputFrameCodec.encodeCompact(
        frame(
            sequence = 41L,
            captureElapsedNanos = 5_000_000_000L,
            sendElapsedNanos = 5_151_000_001L,
        ),
        fixtureConfig(),
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

private const val CONTROL_SESSION_ID = "control-sid-1"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val OTHER_STREAM_SESSION_ID_HEX = "ffeeddccbbaa99887766554433221100"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
