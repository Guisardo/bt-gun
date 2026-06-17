package com.btgun.host.play

fun main() {
    startsInNoneAndGatesOutputs()
    switchingModesRequiresPreviousStop()
    outputGateCanPrepareThenOpenSelectedMode()
}

private fun startsInNoneAndGatesOutputs() {
    val controller = PlayModeController()

    expectEquals("mode", PlayMode.NONE, controller.mode)
    expectEquals("lan off", false, controller.canSendLan)
    expectEquals("hid off", false, controller.canSendBluetoothHid)

    controller.switchTo(PlayMode.LAN)
    expectEquals("lan mode", PlayMode.LAN, controller.mode)
    expectEquals("lan on", true, controller.canSendLan)
    expectEquals("hid still off", false, controller.canSendBluetoothHid)
}

private fun switchingModesRequiresPreviousStop() {
    val controller = PlayModeController()

    val lan = controller.switchTo(PlayMode.LAN)
    expectEquals("none to lan no prior stop", false, lan.stopPreviousFirst)

    val hid = controller.switchTo(PlayMode.BLUETOOTH_HID)
    expectEquals("switch from lan", PlayMode.LAN, hid.previous)
    expectEquals("switch to hid", PlayMode.BLUETOOTH_HID, hid.next)
    expectEquals("lan stopped first", true, hid.stopPreviousFirst)
    expectEquals("lan gate closed", false, controller.canSendLan)
    expectEquals("hid gate open", true, controller.canSendBluetoothHid)

    val stopped = controller.stop()
    expectEquals("stop previous", PlayMode.BLUETOOTH_HID, stopped.previous)
    expectEquals("stopped", PlayMode.NONE, controller.mode)
}

private fun outputGateCanPrepareThenOpenSelectedMode() {
    val controller = PlayModeController()

    controller.setOutputGateOpen(false)
    controller.switchTo(PlayMode.LAN)
    expectEquals("lan prepared closed", false, controller.canSendLan)

    controller.setOutputGateOpen(true)
    expectEquals("lan play ready open", true, controller.canSendLan)

    controller.setOutputGateOpen(false)
    controller.switchTo(PlayMode.BLUETOOTH_HID)
    expectEquals("hid prepared closed", false, controller.canSendBluetoothHid)

    controller.setOutputGateOpen(true)
    expectEquals("hid play ready open", true, controller.canSendBluetoothHid)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
