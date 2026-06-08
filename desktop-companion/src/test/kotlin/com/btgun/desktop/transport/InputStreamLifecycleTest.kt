package com.btgun.desktop.transport

fun main() {
    receiverAllowsUnchangedSessionOnlyDuringControlDisconnectGrace()
    receiverRejectsOldUdpFramesAfterFreshReconnect()
    streamTimeoutUsesLifecyclePathAndPreservesAimAsStale()
}

private fun receiverAllowsUnchangedSessionOnlyDuringControlDisconnectGrace() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())
    expectAccepted(
        "initial accepted",
        receiver.handleDatagram(UdpInputFrameCodec.encode(frame(sequence = 1L), fixtureConfig()), 1_000_000_050L),
        sequence = 1L,
    )

    receiver.onControlDisconnected(nowElapsedNanos = 1_000_000_100L)
    expectEquals("receiver enters grace", InputStreamLifecycleState.GRACE, receiver.lifecycleState)

    expectAccepted(
        "unchanged session accepted during grace",
        receiver.handleDatagram(
            UdpInputFrameCodec.encode(frame(sequence = 2L, captureElapsedNanos = 2_499_000_000L, sendElapsedNanos = 2_499_000_000L), fixtureConfig()),
            2_499_000_000L,
        ),
        sequence = 2L,
    )
    expectRejected(
        "unchanged session rejected after grace",
        InputReplayRejectReason.CONTROL_GRACE_EXPIRED,
        receiver.handleDatagram(
            UdpInputFrameCodec.encode(frame(sequence = 3L, captureElapsedNanos = 2_501_000_000L, sendElapsedNanos = 2_501_000_000L), fixtureConfig()),
            2_501_000_000L,
        ),
    )
    expectEquals("receiver stale after grace", InputStreamLifecycleState.STALE, receiver.lifecycleState)
}

private fun receiverRejectsOldUdpFramesAfterFreshReconnect() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())
    val oldDatagram = UdpInputFrameCodec.encode(frame(sequence = 10L), fixtureConfig())
    expectAccepted("old stream initially accepted", receiver.handleDatagram(oldDatagram, 1_000_000_050L), sequence = 10L)

    receiver.onControlDisconnected(nowElapsedNanos = 1_100_000_000L)
    receiver.onControlReconnected(config = newFixtureConfig())
    expectEquals("receiver active after fresh config", InputStreamLifecycleState.ACTIVE, receiver.lifecycleState)

    expectRejected("old stream rejected after reconnect", InputReplayRejectReason.WRONG_STREAM_SESSION, receiver.handleDatagram(oldDatagram, 1_200_000_000L))
    expectAccepted(
        "new stream accepted after fresh auth",
        receiver.handleDatagram(
            UdpInputFrameCodec.encode(
                frame(
                    sequence = 1L,
                    streamSessionId = NEW_STREAM_SESSION_ID_HEX,
                    captureElapsedNanos = 1_200_000_001L,
                    sendElapsedNanos = 1_200_000_001L,
                ),
                newFixtureConfig(),
            ),
            1_200_000_001L,
        ),
        sequence = 1L,
    )
}

private fun streamTimeoutUsesLifecyclePathAndPreservesAimAsStale() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())
    expectAccepted(
        "valid input before timeout",
        receiver.handleDatagram(
            UdpInputFrameCodec.encode(frame(sequence = 20L, buttonBitmask = 0x23, rawAimX = 0.125f, rawAimY = -0.25f), fixtureConfig()),
            1_000_000_050L,
        ),
        sequence = 20L,
    )

    val stale = receiver.onStreamTimeout(nowElapsedNanos = 1_300_000_000L)

    expectEquals("timeout state stale", InputStreamLifecycleState.STALE, receiver.lifecycleState)
    expectEquals("buttons cleared", 0, stale?.buttons)
    expectEquals("pressed controls cleared", emptySet<String>(), stale?.pressedControls)
    expectEquals("raw aim x preserved", 0.125f, stale?.motion?.rawAimX)
    expectEquals("raw aim y preserved", -0.25f, stale?.motion?.rawAimY)
    expectEquals("stale surfaced to callback", true, received.last().stale)
}

private fun expectAccepted(label: String, actual: UdpInputReceiverResult, sequence: Long) {
    expectTrue(label, actual is UdpInputReceiverResult.Accepted)
    expectEquals("$label sequence", sequence, (actual as UdpInputReceiverResult.Accepted).input.lastAcceptedSequence)
}

private fun expectRejected(label: String, expected: InputReplayRejectReason, actual: UdpInputReceiverResult) {
    expectTrue(label, actual is UdpInputReceiverResult.Rejected)
    expectEquals(label, expected, (actual as UdpInputReceiverResult.Rejected).reason)
}

private fun frame(
    type: UdpInputFrameType = UdpInputFrameType.SNAPSHOT,
    streamSessionId: String = STREAM_SESSION_ID_HEX,
    sequence: Long,
    captureElapsedNanos: Long = 1_000_000_000L,
    sendElapsedNanos: Long = 1_000_000_000L,
    buttonBitmask: Int = 0x23,
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
        stickX = 12_345,
        stickY = -12_345,
        motionProvider = 2,
        motionCapabilityFlags = 0x07,
        yaw = 1.25f,
        pitch = -2.5f,
        roll = 0.75f,
        rawAimX = rawAimX,
        rawAimY = rawAimY,
        sourceSensorElapsedNanos = 999_999_999L,
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

private fun newFixtureConfig(): InputStreamConfig =
    fixtureConfig().copy(
        streamSessionIdHex = NEW_STREAM_SESSION_ID_HEX,
        hmacSha256KeyBase64Url = NEW_HMAC_KEY_BASE64URL,
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

private const val CONTROL_SESSION_ID = "control-sid-1"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val NEW_STREAM_SESSION_ID_HEX = "ffeeddccbbaa99887766554433221100"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val NEW_HMAC_KEY_BASE64URL = "ESNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
