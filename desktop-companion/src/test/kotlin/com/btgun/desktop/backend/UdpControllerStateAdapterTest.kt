package com.btgun.desktop.backend

import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputFrame
import com.btgun.desktop.transport.UdpInputFrameCodec
import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult

fun main() {
    snapshotFixtureReplaysThroughReceiverBeforeMapping()
    edgeFixtureMapsSemanticControlsAndNeutralizesNanAim()
    virtualButtonIdsMapWithoutLegacyAliases()
    legacyUnmappedFrameIsIncompatibleWithProductPath()
    edgeFlagDoesNotBecomeButtonThree()
    staleReceiverInputClearsButtonsAndStickBeforeMapping()
}

private fun snapshotFixtureReplaysThroughReceiverBeforeMapping() {
    val state = acceptFrame(productFrame(sequence = 42L, buttonBitmask = BUTTON_R2 or BUTTON_L2 or BUTTON_B2))
        .toSemanticState()

    expectEquals("trigger", true, state.trigger)
    expectEquals("reload", true, state.reload)
    expectEquals("x", false, state.x)
    expectEquals("y", false, state.y)
    expectEquals("a", false, state.a)
    expectEquals("b", true, state.b)
    expectEquals("stickX", 12345, state.stickX)
    expectEquals("stickY", -12345, state.stickY)
    expectEquals("aimX", 0.375f, state.aimX)
    expectEquals("aimY", -0.625f, state.aimY)
    expectEquals("stale", false, state.stale)
    expectEquals("sourceSequence", 42L, state.sourceSequence)
}

private fun edgeFixtureMapsSemanticControlsAndNeutralizesNanAim() {
    val state = acceptFrame(
        productFrame(
            type = UdpInputFrameType.EDGE,
            sequence = 43L,
            buttonBitmask = BUTTON_R2 or EDGE_CONTROL_CHANGED,
            stickX = Short.MIN_VALUE.toInt(),
            stickY = Short.MAX_VALUE.toInt(),
            productAimX = -0.5f,
            productAimY = 0.25f,
        ),
    ).toSemanticState()

    expectEquals("trigger", true, state.trigger)
    expectEquals("reload", false, state.reload)
    expectEquals("x", false, state.x)
    expectEquals("y", false, state.y)
    expectEquals("a", false, state.a)
    expectEquals("b", false, state.b)
    expectEquals("stickX", -32768, state.stickX)
    expectEquals("stickY", 32767, state.stickY)
    expectEquals("aimX mapped", -0.5f, state.aimX)
    expectEquals("aimY mapped", 0.25f, state.aimY)
    expectEquals("sourceSequence", 43L, state.sourceSequence)
}

private fun virtualButtonIdsMapWithoutLegacyAliases() {
    val state = UdpControllerStateAdapter.toState(
        receivedInput(
            pressedControls = setOf(
                "jp_button_r2",
                "jp_button_l2",
                "jp_button_b3",
                "jp_button_b4",
                "jp_button_b1",
                "jp_button_b2",
            ),
        ),
    )

    expectEquals("trigger virtual id", true, state.trigger)
    expectEquals("reload virtual id", true, state.reload)
    expectEquals("x virtual id", true, state.x)
    expectEquals("y virtual id", true, state.y)
    expectEquals("a virtual id", true, state.a)
    expectEquals("b virtual id", true, state.b)
}

private fun legacyUnmappedFrameIsIncompatibleWithProductPath() {
    val accepted = acceptFrame(productFrame(sequence = 42L, buttonBitmask = 0x23, streamFlags = 0))
    val input = accepted.input
    val state = accepted.toSemanticState()

    expectEquals("mapped stream absent", false, input.mappedProductStream)
    expectEquals("raw debug absent", false, input.rawDebugEnabled)
    expectEquals("trigger ignored", false, state.trigger)
    expectEquals("reload ignored", false, state.reload)
    expectEquals("stickX neutral", 0, state.stickX)
    expectEquals("stickY neutral", 0, state.stickY)
    expectEquals("aimX neutral", 0.0f, state.aimX)
    expectEquals("aimY neutral", 0.0f, state.aimY)
    expectEquals("incompatible marked stale", true, state.stale)
}

private fun edgeFlagDoesNotBecomeButtonThree() {
    val input = acceptFrame(
        productFrame(
            type = UdpInputFrameType.EDGE,
            sequence = 43L,
            buttonBitmask = BUTTON_R2 or EDGE_CONTROL_CHANGED,
        ),
    ).input

    expectEquals("edge flag present", true, input.buttons and EDGE_CONTROL_CHANGED != 0)
    expectEquals("edge flag ignored for controls", setOf("jp_button_r2", "trigger"), input.pressedControls)
}

private fun staleReceiverInputClearsButtonsAndStickBeforeMapping() {
    val receiver = startedReceiver()
    val accepted = receiver.handleDatagram(
        bytes = UdpInputFrameCodec.encode(
            productFrame(sequence = 42L, buttonBitmask = BUTTON_R2 or BUTTON_L2 or BUTTON_B2),
            fixtureConfig(),
        ),
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

private fun acceptFrame(frame: UdpInputFrame): UdpInputReceiverResult.Accepted {
    val result = startedReceiver().handleDatagram(
        bytes = UdpInputFrameCodec.encode(frame, fixtureConfig()),
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

private fun receivedInput(
    pressedControls: Set<String>,
    mappedProductStream: Boolean = true,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = CONTROL_SESSION_ID,
        streamSessionIdHex = STREAM_SESSION_ID_HEX,
        frameType = UdpInputFrameType.SNAPSHOT,
        buttons = 0,
        pressedControls = pressedControls,
        stickX = 0,
        stickY = 0,
        motion = com.btgun.desktop.transport.UdpReceivedMotion(
            provider = 0,
            capabilityFlags = 0,
            yaw = 0.0f,
            pitch = 0.0f,
            roll = 0.0f,
            rawAimX = Float.NaN,
            rawAimY = Float.NaN,
            sourceSensorElapsedNanos = 0L,
        ),
        mappedProductStream = mappedProductStream,
        captureElapsedNanos = 1L,
        sendElapsedNanos = 1L,
        receivedElapsedNanos = 1L,
        stale = false,
        lastAcceptedSequence = 1L,
    )

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

private fun productFrame(
    type: UdpInputFrameType = UdpInputFrameType.SNAPSHOT,
    sequence: Long,
    buttonBitmask: Int,
    stickX: Int = 12_345,
    stickY: Int = -12_345,
    productAimX: Float = 0.375f,
    productAimY: Float = -0.625f,
    streamFlags: Int = UdpInputFrame.FLAG_MAPPED_PRODUCT_STREAM or UdpInputFrame.FLAG_RAW_DEBUG_EXTRAS,
): UdpInputFrame =
    UdpInputFrame(
        type = type,
        streamSessionId = STREAM_SESSION_ID_HEX,
        sequence = sequence,
        captureElapsedNanos = 1_111_111_111L,
        sendElapsedNanos = 1_111_111_222L,
        buttonBitmask = buttonBitmask,
        stickX = stickX,
        stickY = stickY,
        motionProvider = 2,
        motionCapabilityFlags = 0x07,
        yaw = productAimX,
        pitch = productAimY,
        roll = 0.75f,
        rawAimX = 0.125f,
        rawAimY = -0.25f,
        sourceSensorElapsedNanos = 1_111_111_000L,
        streamFlags = streamFlags,
        productAimX = productAimX,
        productAimY = productAimY,
        rawRoll = 0.75f,
    )

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
private const val BUTTON_B2 = 1 shl 1
private const val BUTTON_L2 = 1 shl 6
private const val BUTTON_R2 = 1 shl 7
private const val EDGE_CONTROL_CHANGED = 1 shl 30
