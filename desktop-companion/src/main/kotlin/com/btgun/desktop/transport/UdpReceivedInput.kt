package com.btgun.desktop.transport

data class UdpReceivedMotion(
    val provider: Int,
    val capabilityFlags: Int,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val rawAimX: Float,
    val rawAimY: Float,
    val sourceSensorElapsedNanos: Long,
)

data class UdpReceivedMappedAim(
    val aimX: Float,
    val aimY: Float,
)

data class UdpReceivedInput(
    val controlSessionId: String,
    val streamSessionIdHex: String,
    val frameType: UdpInputFrameType,
    val buttons: Int,
    val pressedControls: Set<String>,
    val stickX: Int,
    val stickY: Int,
    val motion: UdpReceivedMotion,
    val mappedAim: UdpReceivedMappedAim = UdpReceivedMappedAim(motion.rawAimX, motion.rawAimY),
    val mappedProductStream: Boolean = true,
    val rawDebugEnabled: Boolean = false,
    val captureElapsedNanos: Long,
    val sendElapsedNanos: Long,
    val receivedElapsedNanos: Long,
    val stale: Boolean,
    val lastAcceptedSequence: Long,
)

internal fun UdpInputFrame.toReceivedInput(
    controlSessionId: String,
    receivedElapsedNanos: Long,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = controlSessionId,
        streamSessionIdHex = streamSessionId,
        frameType = type,
        buttons = buttonBitmask,
        pressedControls = pressedControlsFrom(buttonBitmask),
        stickX = stickX,
        stickY = stickY,
        motion = UdpReceivedMotion(
            provider = motionProvider,
            capabilityFlags = motionCapabilityFlags,
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            rawAimX = rawAimX,
            rawAimY = rawAimY,
            sourceSensorElapsedNanos = sourceSensorElapsedNanos,
        ),
        mappedAim = UdpReceivedMappedAim(
            aimX = productAimX,
            aimY = productAimY,
        ),
        mappedProductStream = mappedProductStream,
        rawDebugEnabled = rawDebugEnabled,
        captureElapsedNanos = captureElapsedNanos,
        sendElapsedNanos = sendElapsedNanos,
        receivedElapsedNanos = receivedElapsedNanos,
        stale = false,
        lastAcceptedSequence = sequence,
    )

private fun pressedControlsFrom(buttons: Int): Set<String> =
    buildSet {
        addIfPressed(buttons, 0, "jp_button_b1", "a")
        addIfPressed(buttons, 1, "jp_button_b2", "b")
        addIfPressed(buttons, 2, "jp_button_b3", "x")
        addIfPressed(buttons, 3, "jp_button_b4", "y")
        addIfPressed(buttons, 4, "jp_button_l1")
        addIfPressed(buttons, 5, "jp_button_r1")
        addIfPressed(buttons, 6, "jp_button_l2", "reload")
        addIfPressed(buttons, 7, "jp_button_r2", "trigger")
        addIfPressed(buttons, 8, "jp_button_s1")
        addIfPressed(buttons, 9, "jp_button_s2")
        addIfPressed(buttons, 10, "jp_button_l3")
        addIfPressed(buttons, 11, "jp_button_r3")
        addIfPressed(buttons, 12, "jp_button_du")
        addIfPressed(buttons, 13, "jp_button_dd")
        addIfPressed(buttons, 14, "jp_button_dl")
        addIfPressed(buttons, 15, "jp_button_dr")
        addIfPressed(buttons, 16, "jp_button_a1")
        addIfPressed(buttons, 17, "jp_button_a2")
        addIfPressed(buttons, 18, "jp_button_a3")
        addIfPressed(buttons, 19, "jp_button_a4")
        addIfPressed(buttons, 20, "jp_button_l4")
        addIfPressed(buttons, 21, "jp_button_r4")
    }

private fun MutableSet<String>.addIfPressed(buttons: Int, bitIndex: Int, vararg labels: String) {
    if (buttons and (1 shl bitIndex) == 0) return
    labels.forEach(::add)
}
