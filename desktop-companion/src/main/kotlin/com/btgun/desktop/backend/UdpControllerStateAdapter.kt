package com.btgun.desktop.backend

import com.btgun.desktop.transport.UdpReceivedInput

object UdpControllerStateAdapter {
    fun toState(input: UdpReceivedInput): SemanticControllerState =
        SemanticControllerState(
            trigger = "trigger" in input.pressedControls,
            reload = "reload" in input.pressedControls,
            x = "x" in input.pressedControls,
            y = "y" in input.pressedControls,
            a = "a" in input.pressedControls,
            b = "b" in input.pressedControls,
            stickX = input.stickX,
            stickY = input.stickY,
            aimX = input.motion.rawAimX.neutralIfNaN(),
            aimY = input.motion.rawAimY.neutralIfNaN(),
            stale = input.stale,
            sourceSequence = input.lastAcceptedSequence,
        )

    private fun Float.neutralIfNaN(): Float =
        if (isNaN()) 0.0f else this
}
