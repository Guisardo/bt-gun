package com.btgun.host.play

enum class PlayMode {
    NONE,
    LAN,
    BLUETOOTH_HID,
}

data class PlayModeTransition(
    val previous: PlayMode,
    val next: PlayMode,
    val stopPreviousFirst: Boolean,
)

class PlayModeController(initialMode: PlayMode = PlayMode.NONE) {
    var mode: PlayMode = initialMode
        private set

    val canSendLan: Boolean
        get() = mode == PlayMode.LAN

    val canSendBluetoothHid: Boolean
        get() = mode == PlayMode.BLUETOOTH_HID

    fun switchTo(next: PlayMode): PlayModeTransition {
        val previous = mode
        val transition = PlayModeTransition(
            previous = previous,
            next = next,
            stopPreviousFirst = previous != PlayMode.NONE && previous != next,
        )
        mode = next
        return transition
    }

    fun stop(): PlayModeTransition =
        switchTo(PlayMode.NONE)
}
