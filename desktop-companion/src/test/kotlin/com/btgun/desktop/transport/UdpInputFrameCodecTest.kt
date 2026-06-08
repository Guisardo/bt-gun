package com.btgun.desktop.transport

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun main() {
    codecConstantsMatchWireContract()
    goldenSnapshotAndEdgeFramesRoundTrip()
    decoderRejectsMalformedOrUntrustedFrames()
    decoderRejectsAuthenticatedMalformedFields()
    debugDecoderRedactsSecrets()
    sourceContractExcludesPreviewAimAndJsonUdp()
}

private fun codecConstantsMatchWireContract() {
    expectEquals("frame size", 120, UdpInputFrameCodec.FRAME_SIZE)
    expectEquals("tag size", 32, UdpInputFrameCodec.TAG_SIZE)
    expectEquals("magic", "BTGI", UdpInputFrameCodec.MAGIC)
    expectEquals("version", 1, UdpInputFrameCodec.VERSION)
    expectEquals("snapshot type", 1, UdpInputFrameType.SNAPSHOT.wireValue)
    expectEquals("edge type", 2, UdpInputFrameType.EDGE.wireValue)
    expectEquals("sequence offset", 24, UdpInputFrameCodec.OFFSET_SEQUENCE)
    expectEquals("capture offset", 32, UdpInputFrameCodec.OFFSET_CAPTURE_ELAPSED_NANOS)
    expectEquals("send offset", 40, UdpInputFrameCodec.OFFSET_SEND_ELAPSED_NANOS)
    expectEquals("buttons offset", 48, UdpInputFrameCodec.OFFSET_BUTTON_BITMASK)
    expectEquals("stick x offset", 52, UdpInputFrameCodec.OFFSET_STICK_X)
    expectEquals("stick y offset", 54, UdpInputFrameCodec.OFFSET_STICK_Y)
    expectEquals("provider offset", 56, UdpInputFrameCodec.OFFSET_MOTION_PROVIDER)
    expectEquals("capability offset", 57, UdpInputFrameCodec.OFFSET_MOTION_CAPABILITY_FLAGS)
    expectEquals("yaw offset", 60, UdpInputFrameCodec.OFFSET_YAW)
    expectEquals("pitch offset", 64, UdpInputFrameCodec.OFFSET_PITCH)
    expectEquals("roll offset", 68, UdpInputFrameCodec.OFFSET_ROLL)
    expectEquals("raw aim x offset", 72, UdpInputFrameCodec.OFFSET_RAW_AIM_X)
    expectEquals("raw aim y offset", 76, UdpInputFrameCodec.OFFSET_RAW_AIM_Y)
    expectEquals("sensor timestamp offset", 80, UdpInputFrameCodec.OFFSET_SOURCE_SENSOR_ELAPSED_NANOS)
    expectEquals("tag offset", 88, UdpInputFrameCodec.OFFSET_HMAC_TAG)
}

private fun goldenSnapshotAndEdgeFramesRoundTrip() {
    val config = fixtureConfig()
    val snapshot = UdpInputFrame(
        type = UdpInputFrameType.SNAPSHOT,
        streamSessionId = STREAM_SESSION_ID_HEX,
        sequence = 42L,
        captureElapsedNanos = 1_111_111_111L,
        sendElapsedNanos = 1_111_111_222L,
        buttonBitmask = 0x00000023,
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
    val edge = UdpInputFrame(
        type = UdpInputFrameType.EDGE,
        streamSessionId = STREAM_SESSION_ID_HEX,
        sequence = 43L,
        captureElapsedNanos = 1_111_111_333L,
        sendElapsedNanos = 1_111_111_444L,
        buttonBitmask = 0x00000101,
        stickX = -32_768,
        stickY = 32_767,
        motionProvider = 3,
        motionCapabilityFlags = 0x03,
        yaw = -1.0f,
        pitch = 0.5f,
        roll = 2.0f,
        rawAimX = Float.NaN,
        rawAimY = Float.NaN,
        sourceSensorElapsedNanos = 1_111_111_300L,
    )

    expectEquals("snapshot hex", GOLDEN_SNAPSHOT_FRAME_HEX, UdpInputFrameCodec.encode(snapshot, config).toHex())
    expectEquals("edge hex", GOLDEN_EDGE_FRAME_HEX, UdpInputFrameCodec.encode(edge, config).toHex())

    val decodedSnapshot = UdpInputFrameCodec.authenticateAndDecode(GOLDEN_SNAPSHOT_FRAME_HEX.hexToBytes(), config)
    val decodedEdge = UdpInputFrameCodec.authenticateAndDecode(GOLDEN_EDGE_FRAME_HEX.hexToBytes(), config)

    expectTrue("snapshot accepted", decodedSnapshot is UdpInputFrameDecodeResult.Accepted)
    expectTrue("edge accepted", decodedEdge is UdpInputFrameDecodeResult.Accepted)
    expectEquals("snapshot frame", snapshot, (decodedSnapshot as UdpInputFrameDecodeResult.Accepted).frame)
    expectEquals("edge frame type", UdpInputFrameType.EDGE, (decodedEdge as UdpInputFrameDecodeResult.Accepted).frame.type)
    expectTrue("edge raw aim x missing", decodedEdge.frame.rawAimX.isNaN())
    expectTrue("edge raw aim y missing", decodedEdge.frame.rawAimY.isNaN())
}

private fun decoderRejectsMalformedOrUntrustedFrames() {
    val config = fixtureConfig()

    expectRejected("bad length", UdpInputFrameRejectReason.INVALID_LENGTH, UdpInputFrameCodec.authenticateAndDecode(ByteArray(119), config))
    expectRejected("bad magic", UdpInputFrameRejectReason.BAD_MAGIC, UdpInputFrameCodec.authenticateAndDecode(mutated(0, 'X'.code), config))
    expectRejected("bad version", UdpInputFrameRejectReason.UNSUPPORTED_VERSION, UdpInputFrameCodec.authenticateAndDecode(mutated(4, 2), config))
    expectRejected("bad type", UdpInputFrameRejectReason.UNKNOWN_TYPE, UdpInputFrameCodec.authenticateAndDecode(mutated(5, 9), config))
    expectRejected("wrong stream", UdpInputFrameRejectReason.WRONG_STREAM_SESSION, UdpInputFrameCodec.authenticateAndDecode(mutated(8, 0xff), config))
    expectRejected("bad hmac", UdpInputFrameRejectReason.BAD_HMAC, UdpInputFrameCodec.authenticateAndDecode(mutated(119, 0x00), config))
}

private fun decoderRejectsAuthenticatedMalformedFields() {
    val config = fixtureConfig()

    expectRejected(
        "zero sequence",
        UdpInputFrameRejectReason.MALFORMED_FIELD,
        UdpInputFrameCodec.authenticateAndDecode(authenticatedLongMutation(UdpInputFrameCodec.OFFSET_SEQUENCE, 0L), config),
    )
    expectRejected(
        "negative capture timestamp",
        UdpInputFrameRejectReason.MALFORMED_FIELD,
        UdpInputFrameCodec.authenticateAndDecode(authenticatedLongMutation(UdpInputFrameCodec.OFFSET_CAPTURE_ELAPSED_NANOS, -1L), config),
    )
    expectRejected(
        "send before capture",
        UdpInputFrameRejectReason.MALFORMED_FIELD,
        UdpInputFrameCodec.authenticateAndDecode(
            authenticatedMutation { bytes ->
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                buffer.putLong(UdpInputFrameCodec.OFFSET_CAPTURE_ELAPSED_NANOS, 500L)
                buffer.putLong(UdpInputFrameCodec.OFFSET_SEND_ELAPSED_NANOS, 499L)
            },
            config,
        ),
    )
}

private fun debugDecoderRedactsSecrets() {
    val config = fixtureConfig()
    val summary = UdpInputFrameCodec.debugDecode(GOLDEN_SNAPSHOT_FRAME_HEX.hexToBytes(), config).toString()

    expectContains("debug type", summary, "SNAPSHOT")
    expectContains("debug sequence", summary, "sequence=42")
    expectContains("debug provider", summary, "motionProvider=2")
    listOf("qr_secret", "manual code", "proof", HMAC_KEY_BASE64URL, HMAC_KEY_HEX).forEach { secret ->
        expectFalse("debug redacts $secret", summary.contains(secret, ignoreCase = true))
    }
}

private fun sourceContractExcludesPreviewAimAndJsonUdp() {
    val source = File("src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt")
    val text = if (source.exists()) source.readText() else ""
    listOf("PreviewAim", "aimX", "aimY", "profile mapping", "profile_mapper", "json udp").forEach { banned ->
        expectFalse("codec excludes $banned", text.contains(banned, ignoreCase = false))
    }
}

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

private fun expectRejected(label: String, expected: UdpInputFrameRejectReason, actual: UdpInputFrameDecodeResult) {
    expectTrue(label, actual is UdpInputFrameDecodeResult.Rejected)
    expectEquals(label, expected, (actual as UdpInputFrameDecodeResult.Rejected).reason)
}

private fun mutated(offset: Int, value: Int): ByteArray =
    GOLDEN_SNAPSHOT_FRAME_HEX.hexToBytes().also { it[offset] = value.toByte() }

private fun authenticatedLongMutation(offset: Int, value: Long): ByteArray =
    authenticatedMutation { bytes ->
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putLong(offset, value)
    }

private fun authenticatedMutation(mutator: (ByteArray) -> Unit): ByteArray =
    GOLDEN_SNAPSHOT_FRAME_HEX.hexToBytes().also { bytes ->
        mutator(bytes)
        hmac(bytes.copyOfRange(0, UdpInputFrameCodec.OFFSET_HMAC_TAG))
            .copyInto(bytes, UdpInputFrameCodec.OFFSET_HMAC_TAG)
    }

private fun hmac(input: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(Base64.getUrlDecoder().decode(HMAC_KEY_BASE64URL), "HmacSHA256"))
    return mac.doFinal(input)
}

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

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

private fun expectContains(label: String, actual: String, expectedPart: String) {
    if (!actual.contains(expectedPart)) {
        throw AssertionError("$label expected <$actual> to contain <$expectedPart>")
    }
}

private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_HEX = "0123456789abcdeffedcba98765432100123456789abcdeffedcba9876543210"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val GOLDEN_SNAPSHOT_FRAME_HEX = "425447490101000000112233445566778899aabbccddeeff000000000000002a00000000423a35c700000000423a3636000000233039cfc7020700003fa00000c02000003f4000003e000000be80000000000000423a3558ad0f94e008b50a045111a7bbb25688c2f1d399a8de4b3b8f2e325c0f63fb7d5f"
private const val GOLDEN_EDGE_FRAME_HEX = "425447490102000000112233445566778899aabbccddeeff000000000000002b00000000423a36a500000000423a37140000010180007fff03030000bf8000003f000000400000007fc000007fc0000000000000423a36843b9a10ccf01f62a02db4cc6065db9d133b1f4e20e1b4f8c74579b672755e8d24"
