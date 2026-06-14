package com.btgun.desktop.backend

import com.btgun.desktop.transport.UdpReceivedInput

object UdpControllerStateAdapter {
    fun toState(input: UdpReceivedInput): SemanticControllerState =
        if (!input.mappedProductStream) {
            SemanticControllerState(
                stale = true,
                sourceSequence = input.lastAcceptedSequence,
            )
        } else {
            SemanticControllerState(
                trigger = input.hasPressed("trigger", "jp_button_r2"),
                reload = input.hasPressed("reload", "jp_button_l2"),
                x = input.hasPressed("x", "jp_button_b3"),
                y = input.hasPressed("y", "jp_button_b4"),
                a = input.hasPressed("a", "jp_button_b1"),
                b = input.hasPressed("b", "jp_button_b2"),
                stickX = input.stickX,
                stickY = input.stickY,
                aimX = input.mappedAim.aimX.neutralIfNaN(),
                aimY = input.mappedAim.aimY.neutralIfNaN(),
                stale = input.stale,
                sourceSequence = input.lastAcceptedSequence,
            )
        }

    private fun Float.neutralIfNaN(): Float =
        if (isNaN()) 0.0f else this

    private fun UdpReceivedInput.hasPressed(vararg labels: String): Boolean =
        labels.any { label -> label in pressedControls }
}
