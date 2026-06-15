package com.btgun.desktop.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.lang.reflect.Modifier
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun main() {
    receiverParsesValidDatagramIntoRawInputOnly()
    receiverRejectsUntrustedReplayAndAcceptsClockSkewedDatagrams()
    receiverRejectsAuthenticatedMalformedFieldsBeforeApply()
    receiverSnapshotRepairsDroppedEdgeState()
    receiverTimeoutClearsActiveControlsOnly()
    receiverBoundaryDoesNotExposeMappedDesktopOutput()
}

private fun receiverParsesValidDatagramIntoRawInputOnly() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())

    val result = receiver.handleDatagram(
        UdpInputFrameCodec.encode(frame(sequence = 42L), fixtureConfig()),
        receivedElapsedNanos = 1_111_111_300L,
    )

    expectAccepted("valid datagram", result, sequence = 42L)
    val input = received.single()
    expectEquals("buttons", 0x23, input.buttons)
    expectEquals("stick x", 12_345, input.stickX)
    expectEquals("stick y", -12_345, input.stickY)
    expectEquals("provider", 2, input.motion.provider)
    expectEquals("capabilities", 0x07, input.motion.capabilityFlags)
    expectEquals("yaw", 1.25f, input.motion.yaw)
    expectEquals("pitch", -2.5f, input.motion.pitch)
    expectEquals("roll", 0.75f, input.motion.roll)
    expectEquals("raw aim x", 0.125f, input.motion.rawAimX)
    expectEquals("raw aim y", -0.25f, input.motion.rawAimY)
    expectTrue("fresh input", !input.stale)
}

private fun receiverRejectsUntrustedReplayAndAcceptsClockSkewedDatagrams() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())
    val valid = UdpInputFrameCodec.encode(frame(sequence = 50L), fixtureConfig())
    val wrongStream = UdpInputFrameCodec.encode(frame(sequence = 51L, streamSessionId = OTHER_STREAM_SESSION_ID_HEX), otherStreamConfig())
    val badMac = valid.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }
    val skewedUptime = UdpInputFrameCodec.encode(
        frame(sequence = 52L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_000_000_000L),
        fixtureConfig(),
    )
    val senderLocalStale = UdpInputFrameCodec.encode(
        frame(sequence = 53L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_151_000_001L),
        fixtureConfig(),
    )

    expectAccepted("valid", receiver.handleDatagram(valid, receivedElapsedNanos = 1_111_111_300L), sequence = 50L)
    expectRejected("duplicate", InputReplayRejectReason.DUPLICATE_SEQUENCE, receiver.handleDatagram(valid, 1_111_111_301L))
    expectRejected("old", InputReplayRejectReason.OLD_SEQUENCE, receiver.handleDatagram(UdpInputFrameCodec.encode(frame(sequence = 49L), fixtureConfig()), 1_111_111_302L))
    expectRejected("wrong stream", InputReplayRejectReason.WRONG_STREAM_SESSION, receiver.handleDatagram(wrongStream, 1_111_111_303L))
    expectRejected("bad mac", InputReplayRejectReason.BAD_HMAC, receiver.handleDatagram(badMac, 1_111_111_304L))
    expectAccepted("clock-skewed uptime", receiver.handleDatagram(skewedUptime, 900_000_000_000_000L), sequence = 52L)
    expectRejected("sender-local stale", InputReplayRejectReason.AGE_EXPIRED, receiver.handleDatagram(senderLocalStale, 900_000_000_000_100L))
    expectEquals("valid and skewed applied", 2, received.size)
}

private fun receiverRejectsAuthenticatedMalformedFieldsBeforeApply() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())

    expectRejected(
        "zero sequence malformed",
        InputReplayRejectReason.MALFORMED,
        receiver.handleDatagram(authenticatedLongMutation(UdpInputFrameCodec.OFFSET_SEQUENCE, 0L), 1_111_111_300L),
    )
    expectRejected(
        "negative capture malformed",
        InputReplayRejectReason.MALFORMED,
        receiver.handleDatagram(authenticatedLongMutation(UdpInputFrameCodec.OFFSET_CAPTURE_ELAPSED_NANOS, -1L), 1_111_111_301L),
    )
    expectRejected(
        "send before capture malformed",
        InputReplayRejectReason.MALFORMED,
        receiver.handleDatagram(
            authenticatedMutation { bytes ->
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                buffer.putLong(UdpInputFrameCodec.OFFSET_CAPTURE_ELAPSED_NANOS, 500L)
                buffer.putLong(UdpInputFrameCodec.OFFSET_SEND_ELAPSED_NANOS, 499L)
            },
            1_111_111_302L,
        ),
    )
    expectEquals("malformed fields not applied", emptyList<UdpReceivedInput>(), received)
}

private fun receiverSnapshotRepairsDroppedEdgeState() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())

    expectAccepted(
        "initial snapshot",
        receiver.handleDatagram(UdpInputFrameCodec.encode(frame(sequence = 60L, buttonBitmask = 0x00), fixtureConfig()), 1_111_111_300L),
        sequence = 60L,
    )
    expectAccepted(
        "repair snapshot",
        receiver.handleDatagram(UdpInputFrameCodec.encode(frame(sequence = 62L, buttonBitmask = 0x23), fixtureConfig()), 1_111_111_320L),
        sequence = 62L,
    )
    expectRejected(
        "late edge cannot overwrite snapshot",
        InputReplayRejectReason.OLD_SEQUENCE,
        receiver.handleDatagram(
            UdpInputFrameCodec.encode(frame(type = UdpInputFrameType.EDGE, sequence = 61L, buttonBitmask = 0x00), fixtureConfig()),
            1_111_111_330L,
        ),
    )

    expectEquals("latest buttons stay from snapshot", 0x23, receiver.current?.buttons)
    expectEquals("latest sequence stays from snapshot", 62L, receiver.current?.lastAcceptedSequence)
}

private fun receiverTimeoutClearsActiveControlsOnly() {
    val received = mutableListOf<UdpReceivedInput>()
    val receiver = UdpInputReceiver(onInput = received::add)
        .start(trustedSession = CONTROL_SESSION_ID, config = fixtureConfig())
    receiver.handleDatagram(UdpInputFrameCodec.encode(frame(sequence = 70L), fixtureConfig()), 1_111_111_300L)

    val timedOut = receiver.onTimeout()

    expectEquals("buttons cleared", 0, timedOut?.buttons)
    expectEquals("pressed controls cleared", emptySet<String>(), timedOut?.pressedControls)
    expectEquals("stick x cleared", 0, timedOut?.stickX)
    expectEquals("stick y cleared", 0, timedOut?.stickY)
    expectEquals("raw aim x preserved", 0.125f, timedOut?.motion?.rawAimX)
    expectEquals("raw aim y preserved", -0.25f, timedOut?.motion?.rawAimY)
    expectTrue("timeout stale", timedOut?.stale == true)
}

private fun receiverBoundaryDoesNotExposeMappedDesktopOutput() {
    val outputFields = dataFieldNames(UdpReceivedInput::class.java) + dataFieldNames(UdpReceivedMotion::class.java)
    listOf(
        "virtualJoystick",
        "virtualHid",
        "hidReport",
        "profileMapping",
        "profileMapper",
        "visualizerLatency",
        "productAimX",
        "productAimY",
        "aimX",
        "aimY",
    ).forEach { banned ->
        expectTrue("receiver output excludes $banned", banned !in outputFields)
    }
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
    captureElapsedNanos: Long = 1_111_111_111L,
    sendElapsedNanos: Long = 1_111_111_222L,
    buttonBitmask: Int = 0x23,
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
        rawAimX = 0.125f,
        rawAimY = -0.25f,
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

private fun authenticatedLongMutation(offset: Int, value: Long): ByteArray =
    authenticatedMutation { bytes ->
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putLong(offset, value)
    }

private fun authenticatedMutation(mutator: (ByteArray) -> Unit): ByteArray =
    UdpInputFrameCodec.encode(frame(sequence = 90L), fixtureConfig()).also { bytes ->
        mutator(bytes)
        hmac(bytes.copyOfRange(0, UdpInputFrameCodec.OFFSET_HMAC_TAG))
            .copyInto(bytes, UdpInputFrameCodec.OFFSET_HMAC_TAG)
    }

private fun hmac(input: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(Base64.getUrlDecoder().decode(HMAC_KEY_BASE64URL), "HmacSHA256"))
    return mac.doFinal(input)
}

private fun dataFieldNames(type: Class<*>): List<String> =
    type.declaredFields
        .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
        .map { it.name }

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
