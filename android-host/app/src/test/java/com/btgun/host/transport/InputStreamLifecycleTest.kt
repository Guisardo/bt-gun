package com.btgun.host.transport

import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.DesktopHapticCommandExecutor
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.haptics.PhoneHapticActuator
import com.btgun.host.haptics.PhoneHapticStartResult
import com.btgun.host.model.GunInputState
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.StreamKind
import com.btgun.host.motion.MotionCapabilityFlags

fun main() {
    senderKeepsUdpAliveOnlyDuringControlDisconnectGrace()
    senderStopsImmediatelyWhenSessionChanges()
    hapticSessionChangeCancelsActivePulseButShortDisconnectDoesNot()
    latestValidAndExpiredHapticsStillBehaveAfterRecovery()
}

private fun senderKeepsUdpAliveOnlyDuringControlDisconnectGrace() {
    val sink = LifecycleRecordingDatagramSink()
    var now = 1_000_000_000L
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { now },
    )
    sender.start(fixtureConfig())

    sender.onControlDisconnected(nowElapsedNanos = now)
    expectEquals("sender state enters grace", InputStreamLifecycleState.GRACE, sender.lifecycleState)

    now += 1_499_000_000L
    expectEquals(
        "unchanged session sends during grace",
        AndroidUdpInputSendResult.SENT,
        sender.sendSnapshot(GunInputState(pressedControls = setOf("trigger")), motionEnvelope()),
    )

    now += 2_000_000L
    expectEquals(
        "unchanged session stops after grace",
        AndroidUdpInputSendResult.INACTIVE,
        sender.sendSnapshot(GunInputState(pressedControls = setOf("reload")), motionEnvelope()),
    )
    expectEquals("sender state stale after grace", InputStreamLifecycleState.STALE, sender.lifecycleState)
    expectEquals("only grace packet sent", 1, sink.datagrams.size)
}

private fun senderStopsImmediatelyWhenSessionChanges() {
    val sink = LifecycleRecordingDatagramSink()
    val sender = AndroidUdpInputSender(
        datagramSink = sink,
        elapsedRealtimeNanos = { 2_000_000_000L },
    )
    sender.start(fixtureConfig())
    expectEquals(
        "active send works",
        AndroidUdpInputSendResult.SENT,
        sender.sendSnapshot(GunInputState(pressedControls = setOf("trigger")), motionEnvelope()),
    )

    sender.onSessionChanged(newSessionId = "new-control-session")

    expectEquals("session change stops sender", InputStreamLifecycleState.STOPPED, sender.lifecycleState)
    expectEquals(
        "changed session needs fresh config",
        AndroidUdpInputSendResult.INACTIVE,
        sender.sendSnapshot(GunInputState(pressedControls = setOf("button_a")), motionEnvelope()),
    )
    expectEquals("old session only sent once", 1, sink.datagrams.size)
}

private fun hapticSessionChangeCancelsActivePulseButShortDisconnectDoesNot() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 3_000_000_000L })

    executor.handle(
        command = DesktopHapticCommand("cmd-active", strength = 0.5, durationMs = 300L, ttlMs = 500L),
        receivedElapsedNanos = 2_999_900_000L,
    )
    executor.onControlDisconnected(nowElapsedNanos = 3_000_100_000L)
    expectEquals("short disconnect does not cancel", listOf(PhoneCall.Pulse(300L, 0.5)), phone.calls)

    val cancelled = executor.onSessionChanged(newSessionId = "new-session")

    expectEquals("session change reports cancelled", HapticResultStatus.CANCELLED, cancelled)
    expectEquals(
        "session change cancels active pulse",
        listOf(PhoneCall.Pulse(300L, 0.5), PhoneCall.Cancel),
        phone.calls,
    )
}

private fun latestValidAndExpiredHapticsStillBehaveAfterRecovery() {
    val phone = RecordingPhoneHapticActuator()
    var now = 4_000_000_000L
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { now })

    val first = executor.handle(
        command = DesktopHapticCommand("cmd-first", strength = 0.25, durationMs = 200L, ttlMs = 500L),
        receivedElapsedNanos = 3_999_900_000L,
    )
    executor.onSessionChanged(newSessionId = "next-session")
    val second = executor.handle(
        command = DesktopHapticCommand("cmd-second", strength = 1.0, durationMs = 80L, ttlMs = 500L),
        receivedElapsedNanos = 3_999_950_000L,
    )
    now = 4_001_000_001L
    val expired = executor.handle(
        command = DesktopHapticCommand("cmd-expired", strength = 1.0, durationMs = 80L, ttlMs = 1L),
        receivedElapsedNanos = 4_000_000_000L,
    )

    expectEquals("first started", HapticResultStatus.STARTED, first.status)
    expectEquals("second starts after recovery", HapticResultStatus.STARTED, second.status)
    expectEquals("expired still rejected", HapticResultStatus.EXPIRED, expired.status)
    expectEquals(
        "expired no extra pulse",
        listOf(
            PhoneCall.Pulse(200L, 0.25),
            PhoneCall.Cancel,
            PhoneCall.Pulse(80L, 1.0),
        ),
        phone.calls,
    )
}

private fun motionEnvelope(): LiveEnvelope<MotionSample> =
    LiveEnvelope(
        stream = StreamKind.MOTION,
        seq = 1L,
        captureElapsedNanos = 900_000_000L,
        emittedElapsedNanos = 900_000_100L,
        payload = MotionSample(
            provider = MotionProvider.ROTATION_VECTOR,
            providerName = MotionProvider.ROTATION_VECTOR.wireName,
            capabilities = MotionCapabilityFlags(rotationVector = true, gyroscope = true),
            sourceSensorElapsedNanos = 899_999_999L,
            yaw = 1.25f,
            pitch = -2.5f,
            roll = 0.75f,
            rawAimX = 0.125f,
            rawAimY = -0.25f,
        ),
    )

private class LifecycleRecordingDatagramSink : AndroidUdpDatagramSink {
    val datagrams = mutableListOf<LifecycleRecordedDatagram>()

    override fun send(host: String, port: Int, payload: ByteArray): Boolean {
        datagrams += LifecycleRecordedDatagram(host = host, port = port, payload = payload.copyOf())
        return true
    }
}

private data class LifecycleRecordedDatagram(
    val host: String,
    val port: Int,
    val payload: ByteArray,
)

private class RecordingPhoneHapticActuator : PhoneHapticActuator {
    val calls = mutableListOf<PhoneCall>()

    override fun pulse(durationMs: Long, strength: Double): PhoneHapticStartResult {
        calls += PhoneCall.Pulse(durationMs, strength)
        return PhoneHapticStartResult.Started
    }

    override fun cancel(): HapticResultStatus {
        calls += PhoneCall.Cancel
        return HapticResultStatus.CANCELLED
    }
}

private sealed interface PhoneCall {
    data class Pulse(val durationMs: Long, val strength: Double) : PhoneCall
    data object Cancel : PhoneCall
}

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
