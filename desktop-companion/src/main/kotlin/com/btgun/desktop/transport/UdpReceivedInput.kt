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
        if (buttons and 0x01 != 0) add("trigger")
        if (buttons and 0x02 != 0) add("reload")
        if (buttons and 0x04 != 0) add("x")
        if (buttons and 0x08 != 0) add("y")
        if (buttons and 0x10 != 0) add("a")
        if (buttons and 0x20 != 0) add("b")
    }
