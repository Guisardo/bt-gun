package com.btgun.desktop.backend

import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult

fun main() {
    snapshotFixtureReplaysThroughReceiverBeforeMapping()
    edgeFixtureMapsSemanticControlsAndNeutralizesNanAim()
    staleReceiverInputClearsButtonsAndStickBeforeMapping()
}

private fun snapshotFixtureReplaysThroughReceiverBeforeMapping() {
    val state = acceptFixture(GOLDEN_SNAPSHOT_FRAME_HEX).toSemanticState()

    expectEquals("trigger", true, state.trigger)
    expectEquals("reload", true, state.reload)
    expectEquals("x", false, state.x)
    expectEquals("y", false, state.y)
    expectEquals("a", false, state.a)
    expectEquals("b", true, state.b)
    expectEquals("stickX", 12345, state.stickX)
    expectEquals("stickY", -12345, state.stickY)
    expectEquals("aimX", 0.125f, state.aimX)
    expectEquals("aimY", -0.25f, state.aimY)
    expectEquals("stale", false, state.stale)
    expectEquals("sourceSequence", 42L, state.sourceSequence)
}

private fun edgeFixtureMapsSemanticControlsAndNeutralizesNanAim() {
    val state = acceptFixture(GOLDEN_EDGE_FRAME_HEX).toSemanticState()

    expectEquals("trigger", true, state.trigger)
    expectEquals("reload", false, state.reload)
    expectEquals("x", true, state.x)
    expectEquals("y", false, state.y)
    expectEquals("a", false, state.a)
    expectEquals("b", false, state.b)
    expectEquals("stickX", -32768, state.stickX)
    expectEquals("stickY", 32767, state.stickY)
    expectEquals("aimX neutral", 0.0f, state.aimX)
    expectEquals("aimY neutral", 0.0f, state.aimY)
    expectEquals("sourceSequence", 43L, state.sourceSequence)
}

private fun staleReceiverInputClearsButtonsAndStickBeforeMapping() {
    val receiver = startedReceiver()
    val accepted = receiver.handleDatagram(
        bytes = GOLDEN_SNAPSHOT_FRAME_HEX.hexToBytes(),
        receivedElapsedNanos = RECEIVED_ELAPSED_NANOS,
    )
    expectTrue("accepted before stale", accepted is UdpInputReceiverResult.Accepted)

    val stale = receiver.onStreamTimeout(nowElapsedNanos = RECEIVED_ELAPSED_NANOS + 1_000_000L)
        ?: throw AssertionError("stale input expected")
    val state = stale.toSemanticState()

    expectEquals("trigger stale", false, state.trigger)
    expectEquals("reload stale", false, state.reload)
    expectEquals("x stale", false, state.x)
    expectEquals("y stale", false, state.y)
    expectEquals("a stale", false, state.a)
    expectEquals("b stale", false, state.b)
    expectEquals("stickX stale", 0, state.stickX)
    expectEquals("stickY stale", 0, state.stickY)
    expectEquals("stale flag", true, state.stale)
    expectEquals("stale sourceSequence", 42L, state.sourceSequence)
}

private fun acceptFixture(hex: String): UdpInputReceiverResult.Accepted {
    val result = startedReceiver().handleDatagram(
        bytes = hex.hexToBytes(),
        receivedElapsedNanos = RECEIVED_ELAPSED_NANOS,
    )
    if (result !is UdpInputReceiverResult.Accepted) {
        throw AssertionError("fixture must be accepted by UdpInputReceiver before adapter, got $result")
    }
    return result
}

private fun UdpInputReceiverResult.Accepted.toSemanticState(): SemanticControllerState =
    UdpControllerStateAdapter.toState(input)

private fun UdpReceivedInput.toSemanticState(): SemanticControllerState =
    UdpControllerStateAdapter.toState(this)

private fun startedReceiver(): UdpInputReceiver =
    UdpInputReceiver().start(
        trustedSession = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )

private fun fixtureConfig(): InputStreamConfig =
    InputStreamConfig(
        streamSessionIdHex = STREAM_SESSION_ID_HEX,
        udpHost = "127.0.0.1",
        udpPort = 41234,
        hmacSha256KeyBase64Url = HMAC_KEY_BASE64URL,
        snapshotHz = 60,
        frameAgeLimitMs = 150,
        streamTimeoutMs = 250,
        controlDisconnectGraceMs = 1500,
    )

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError("$label expected true")
    }
}

private const val CONTROL_SESSION_ID = "control-sid-1"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val RECEIVED_ELAPSED_NANOS = 1_111_111_333L

private const val GOLDEN_SNAPSHOT_FRAME_HEX =
    "425447490101000000112233445566778899aabbccddeeff000000000000002a00000000423a35c700000000423a3636000000233039cfc7020700003fa00000c02000003f4000003e000000be80000000000000423a3558ad0f94e008b50a045111a7bbb25688c2f1d399a8de4b3b8f2e325c0f63fb7d5f"

private const val GOLDEN_EDGE_FRAME_HEX =
    "425447490102000000112233445566778899aabbccddeeff000000000000002b00000000423a36a500000000423a37140000010180007fff03030000bf8000003f000000400000007fc000007fc0000000000000423a36843b9a10ccf01f62a02db4cc6065db9d133b1f4e20e1b4f8c74579b672755e8d24"
