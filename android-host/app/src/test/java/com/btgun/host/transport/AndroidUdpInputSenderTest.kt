package com.btgun.host.transport

import com.btgun.host.model.GunEvent
import com.btgun.host.model.GunInputState
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.StreamKind
import com.btgun.host.motion.MotionCapabilityFlags
import java.io.File

fun main() {
    sequencerResetsForEachStreamSession()
    senderDoesNotSendBeforeTrustedConfig()
    snapshotUsesCurrentStateMotionAndAuthenticatedConfig()
    snapshotUsesFreshCaptureTimeForOldMotionSample()
    edgeUsesSharedMonotonicSequenceAndImmediateSend()
    senderCloseClosesDatagramSink()
    senderSourceExcludesPreviewAimAndProductMapping()
}

private fun sequencerResetsForEachStreamSession() {
    val sequencer = InputStreamSequencer()

    sequencer.resetFor("00112233445566778899aabbccddeeff")
    expectEquals("first stream first seq", 1L, sequencer.next())
    expectEquals("first stream second seq", 2L, sequencer.next())
    sequencer.resetFor("ffeeddccbbaa99887766554433221100")

    expectEquals("new stream resets seq", 1L, sequencer.next())
}

private fun senderDoesNotSendBeforeTrustedConfig() {
    val sink = RecordingDatagramSink()
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { 1_000_000_000L },
    )

    val result = sender.sendSnapshot(
        state = GunInputState(pressedControls = setOf("trigger")),
        motion = motionEnvelope(),
    )

    expectEquals("inactive result", AndroidUdpInputSendResult.INACTIVE, result)
    expectEquals("no packets before start", 0, sink.datagrams.size)
}

private fun snapshotUsesCurrentStateMotionAndAuthenticatedConfig() {
    val sink = RecordingDatagramSink()
    var now = 1_111_111_222L
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { now },
    )

    sender.start(fixtureConfig())
    val result = sender.sendSnapshot(
        state = GunInputState(
            pressedControls = setOf("trigger", "button_a"),
            stickAxisX = 0.5f,
            stickAxisY = -1f,
        ),
        motion = motionEnvelope(
            captureElapsedNanos = 1_111_111_111L,
            emittedElapsedNanos = 1_111_111_120L,
            sourceSensorElapsedNanos = 1_111_111_000L,
            rawAimX = 0.125f,
            rawAimY = -0.25f,
            aimX = 99f,
            aimY = -99f,
        ),
    )

    val decoded = decodeSingle(sink)
    expectEquals("snapshot result", AndroidUdpInputSendResult.SENT, result)
    expectEquals("snapshot host", "192.168.1.44", sink.datagrams.single().host)
    expectEquals("snapshot port", 41731, sink.datagrams.single().port)
    expectEquals("snapshot type", UdpInputFrameType.SNAPSHOT, decoded.type)
    expectEquals("snapshot seq", 1L, decoded.sequence)
    expectEquals("snapshot capture", now, decoded.captureElapsedNanos)
    expectEquals("snapshot send", now, decoded.sendElapsedNanos)
    expectEquals("buttons", AndroidUdpInputSender.BUTTON_TRIGGER or AndroidUdpInputSender.BUTTON_A, decoded.buttonBitmask)
    expectEquals("stick x", 16_384, decoded.stickX)
    expectEquals("stick y", Short.MIN_VALUE.toInt(), decoded.stickY)
    expectEquals("provider", AndroidUdpInputSender.PROVIDER_ROTATION_VECTOR, decoded.motionProvider)
    expectEquals("capabilities", 0x07, decoded.motionCapabilityFlags)
    expectEquals("raw aim x", 0.125f, decoded.rawAimX)
    expectEquals("raw aim y", -0.25f, decoded.rawAimY)
    expectEquals("source sensor", 1_111_111_000L, decoded.sourceSensorElapsedNanos)
}

private fun snapshotUsesFreshCaptureTimeForOldMotionSample() {
    val sink = RecordingDatagramSink()
    val now = 5_000_000_000L
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { now },
    )

    sender.start(fixtureConfig())
    val result = sender.sendSnapshot(
        state = GunInputState(pressedControls = setOf("trigger")),
        motion = motionEnvelope(
            captureElapsedNanos = 4_000_000_000L,
            emittedElapsedNanos = 4_000_000_100L,
            sourceSensorElapsedNanos = 3_999_999_900L,
            rawAimX = 0.75f,
            rawAimY = -0.5f,
        ),
    )

    val decoded = decodeSingle(sink)
    expectEquals("old motion snapshot result", AndroidUdpInputSendResult.SENT, result)
    expectEquals("snapshot frame capture is fresh", now, decoded.captureElapsedNanos)
    expectEquals("snapshot frame send is fresh", now, decoded.sendElapsedNanos)
    expectEquals("old sensor timestamp preserved", 3_999_999_900L, decoded.sourceSensorElapsedNanos)
    expectEquals("raw aim x preserved", 0.75f, decoded.rawAimX)
    expectEquals("raw aim y preserved", -0.5f, decoded.rawAimY)
}

private fun edgeUsesSharedMonotonicSequenceAndImmediateSend() {
    val sink = RecordingDatagramSink()
    var now = 2_000_000_000L
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { now },
    )

    sender.start(fixtureConfig())
    sender.sendSnapshot(
        state = GunInputState(pressedControls = setOf("trigger")),
        motion = motionEnvelope(captureElapsedNanos = 1_900_000_000L, emittedElapsedNanos = 1_900_000_100L),
    )
    now = 2_000_000_100L
    val result = sender.sendEdge(
        event = gunEnvelope(
            captureElapsedNanos = 2_000_000_050L,
            emittedElapsedNanos = 2_000_000_060L,
            event = GunEvent(name = "trigger", pressed = true),
        ),
        state = GunInputState(pressedControls = setOf("trigger")),
        motion = motionEnvelope(captureElapsedNanos = 1_999_999_000L, emittedElapsedNanos = 1_999_999_100L),
    )

    val edge = decodePacket(sink.datagrams.last().payload)
    expectEquals("edge result", AndroidUdpInputSendResult.SENT, result)
    expectEquals("edge packet count", 2, sink.datagrams.size)
    expectEquals("edge type", UdpInputFrameType.EDGE, edge.type)
    expectEquals("edge shared seq", 2L, edge.sequence)
    expectEquals("edge capture from event", 2_000_000_050L, edge.captureElapsedNanos)
    expectEquals("edge immediate send", now, edge.sendElapsedNanos)
    expectEquals("edge bitmask", AndroidUdpInputSender.BUTTON_TRIGGER or AndroidUdpInputSender.EDGE_CONTROL_CHANGED, edge.buttonBitmask)
}

private fun senderCloseClosesDatagramSink() {
    val sink = CloseRecordingDatagramSink()
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { 3_000_000_000L },
    )

    sender.start(fixtureConfig())
    sender.close()

    expectEquals("sender stopped", InputStreamLifecycleState.STOPPED, sender.lifecycleState)
    expectTrue("sink closed", sink.closed)
}

private fun senderSourceExcludesPreviewAimAndProductMapping() {
    val source = File("app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt")
    val text = if (source.exists()) source.readText() else ""
    listOf("PreviewAim", "aimX", "aimY", "profile mapping", "profile_mapper", "virtual joystick", "physical gun motor").forEach { banned ->
        expectFalse("sender excludes $banned", text.contains(banned, ignoreCase = false))
    }
}

private fun decodeSingle(sink: RecordingDatagramSink): UdpInputFrame {
    expectEquals("packet count", 1, sink.datagrams.size)
    return decodePacket(sink.datagrams.single().payload)
}

private fun decodePacket(payload: ByteArray): UdpInputFrame {
    val decoded = UdpInputFrameCodec.authenticateAndDecode(payload, fixtureConfig())
    expectTrue("frame accepted", decoded is UdpInputFrameDecodeResult.Accepted)
    return (decoded as UdpInputFrameDecodeResult.Accepted).frame
}

private fun motionEnvelope(
    captureElapsedNanos: Long = 1_000_000_000L,
    emittedElapsedNanos: Long = 1_000_000_100L,
    sourceSensorElapsedNanos: Long = 999_999_900L,
    rawAimX: Float? = 0.25f,
    rawAimY: Float? = -0.5f,
    aimX: Float? = 42f,
    aimY: Float? = -42f,
): LiveEnvelope<MotionSample> =
    LiveEnvelope(
        stream = StreamKind.MOTION,
        seq = 7L,
        captureElapsedNanos = captureElapsedNanos,
        emittedElapsedNanos = emittedElapsedNanos,
        payload = MotionSample(
            provider = MotionProvider.ROTATION_VECTOR,
            providerName = MotionProvider.ROTATION_VECTOR.wireName,
            capabilities = MotionCapabilityFlags(
                gameRotationVector = true,
                rotationVector = true,
                gyroscope = true,
            ),
            sourceSensorElapsedNanos = sourceSensorElapsedNanos,
            yaw = 1.25f,
            pitch = -2.5f,
            roll = 0.75f,
            rawAimX = rawAimX,
            rawAimY = rawAimY,
            aimX = aimX,
            aimY = aimY,
        ),
    )

private fun gunEnvelope(
    captureElapsedNanos: Long,
    emittedElapsedNanos: Long,
    event: GunEvent,
): LiveEnvelope<GunEvent> =
    LiveEnvelope(
        stream = StreamKind.GUN,
        seq = 4L,
        captureElapsedNanos = captureElapsedNanos,
        emittedElapsedNanos = emittedElapsedNanos,
        payload = event,
    )

private open class RecordingDatagramSink : AndroidUdpDatagramSink {
    val datagrams = mutableListOf<RecordedDatagram>()

    override fun send(host: String, port: Int, payload: ByteArray): Boolean {
        datagrams += RecordedDatagram(host = host, port = port, payload = payload.copyOf())
        return true
    }
}

private class CloseRecordingDatagramSink : RecordingDatagramSink(), AutoCloseable {
    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
    }
}

private data class RecordedDatagram(
    val host: String,
    val port: Int,
    val payload: ByteArray,
)

private fun fixtureConfig(): InputStreamConfig =
    InputStreamConfig(
        streamSessionIdHex = "00112233445566778899aabbccddeeff",
        udpHost = "192.168.1.44",
        udpPort = 41731,
        hmacSha256KeyBase64Url = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA",
        snapshotHz = 60,
        frameAgeLimitMs = 150,
        streamTimeoutMs = 250,
        controlDisconnectGraceMs = 1500,
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

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError(label)
    }
}
