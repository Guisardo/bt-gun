package com.btgun.desktop.transport

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

fun main() {
    runtimeReceivesLoopbackDatagramAndSurfacesInput()
    runtimeRejectsMalformedDatagramsAndStops()
    runtimeTimeoutMarksCurrentInputStale()
    runtimeRestartsOnSamePort()
}

private fun runtimeReceivesLoopbackDatagramAndSurfacesInput() {
    val received = mutableListOf<UdpReceivedInput>()
    val states = mutableListOf<InputStreamLifecycleState>()
    val config = fixtureConfig(udpPort = freeUdpPort(), streamTimeoutMs = 250L)
    val runtime = DesktopUdpInputRuntime(
        onInput = received::add,
        onStateChanged = states::add,
    )

    expectEquals("start", UdpInputRuntimeStartResult.Started, runtime.start(CONTROL_SESSION_ID, config))
    sendUdp(config.udpPort, UdpInputFrameCodec.encode(frame(sequence = 1L), config))

    waitUntil("input received") { received.isNotEmpty() }
    expectEquals("sequence", 1L, received.single().lastAcceptedSequence)
    expectEquals("trigger pressed", true, "trigger" in received.single().pressedControls)
    expectEquals("active state", InputStreamLifecycleState.ACTIVE, states.last())
    runtime.stop()
}

private fun runtimeRejectsMalformedDatagramsAndStops() {
    val rejected = mutableListOf<InputReplayRejectReason>()
    val states = mutableListOf<InputStreamLifecycleState>()
    val config = fixtureConfig(udpPort = freeUdpPort(), streamTimeoutMs = 250L)
    val runtime = DesktopUdpInputRuntime(
        onRejected = rejected::add,
        onStateChanged = states::add,
    )

    expectEquals("start", UdpInputRuntimeStartResult.Started, runtime.start(CONTROL_SESSION_ID, config))
    sendUdp(config.udpPort, byteArrayOf(1, 2, 3))

    waitUntil("malformed rejected") { rejected.contains(InputReplayRejectReason.MALFORMED) }
    runtime.stop(reason = "test done")
    expectEquals("stopped state", InputStreamLifecycleState.STOPPED, runtime.lifecycleState)
    expectEquals("stopped callback", InputStreamLifecycleState.STOPPED, states.last())
}

private fun runtimeTimeoutMarksCurrentInputStale() {
    val received = mutableListOf<UdpReceivedInput>()
    val config = fixtureConfig(udpPort = freeUdpPort(), streamTimeoutMs = 25L)
    val runtime = DesktopUdpInputRuntime(onInput = received::add)

    expectEquals("start", UdpInputRuntimeStartResult.Started, runtime.start(CONTROL_SESSION_ID, config))
    sendUdp(config.udpPort, UdpInputFrameCodec.encode(frame(sequence = 1L), config))

    waitUntil("input received") { received.isNotEmpty() }
    waitUntil("stale input surfaced") { received.any { it.stale } }
    expectEquals("buttons cleared", 0, received.last().buttons)
    expectEquals("stale state", InputStreamLifecycleState.STALE, runtime.lifecycleState)
    runtime.stop()
}

private fun runtimeRestartsOnSamePort() {
    val received = mutableListOf<UdpReceivedInput>()
    val config = fixtureConfig(udpPort = freeUdpPort(), streamTimeoutMs = 250L)
    val runtime = DesktopUdpInputRuntime(onInput = received::add)

    expectEquals("first start", UdpInputRuntimeStartResult.Started, runtime.start(CONTROL_SESSION_ID, config))
    expectEquals("second start same port", UdpInputRuntimeStartResult.Started, runtime.start(CONTROL_SESSION_ID, config))
    sendUdp(config.udpPort, UdpInputFrameCodec.encode(frame(sequence = 2L), config))

    waitUntil("restart input received") { received.isNotEmpty() }
    expectEquals("restart sequence", 2L, received.single().lastAcceptedSequence)
    runtime.stop()
}

private fun sendUdp(port: Int, bytes: ByteArray) {
    DatagramSocket().use { socket ->
        val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), port)
        socket.send(packet)
    }
}

private fun freeUdpPort(): Int =
    DatagramSocket(0).use { it.localPort }

private fun waitUntil(label: String, condition: () -> Boolean) {
    repeat(80) {
        if (condition()) return
        Thread.sleep(25L)
    }
    throw AssertionError("timed out waiting for $label")
}

private fun frame(sequence: Long): UdpInputFrame =
    UdpInputFrame(
        type = UdpInputFrameType.SNAPSHOT,
        streamSessionId = STREAM_SESSION_ID_HEX,
        sequence = sequence,
        captureElapsedNanos = 1_000_000_000L,
        sendElapsedNanos = 1_000_000_000L,
        buttonBitmask = 0x01,
        stickX = 0,
        stickY = 0,
        motionProvider = 2,
        motionCapabilityFlags = 0x07,
        yaw = 0.0f,
        pitch = 0.0f,
        roll = 0.0f,
        rawAimX = 0.0f,
        rawAimY = 0.0f,
        sourceSensorElapsedNanos = 999_999_999L,
    )

private fun fixtureConfig(udpPort: Int, streamTimeoutMs: Long): InputStreamConfig =
    InputStreamConfig(
        streamSessionIdHex = STREAM_SESSION_ID_HEX,
        udpHost = "127.0.0.1",
        udpPort = udpPort,
        hmacSha256KeyBase64Url = HMAC_KEY_BASE64URL,
        snapshotHz = 60,
        frameAgeLimitMs = 150,
        streamTimeoutMs = streamTimeoutMs,
        controlDisconnectGraceMs = 1500,
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private const val CONTROL_SESSION_ID = "control-sid-1"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
