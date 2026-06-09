package com.btgun.desktop.backend

fun main() {
    descriptorMatchesBtGunV1Contract()
    semanticControllerStateStartsNeutral()
}

private fun descriptorMatchesBtGunV1Contract() {
    val descriptor = btGunV1Descriptor

    requireBtGunV1Invariant(descriptor)
    expectEquals("device kind", "gamepad_like_joystick", descriptor.deviceKind)
    expectEquals("buttons", listOf("trigger", "reload", "x", "y", "a", "b"), descriptor.buttons)
    expectEquals("axes", listOf("stickX", "stickY", "aimX", "aimY"), descriptor.axes)
    expectEquals("trigger kind", "digital", descriptor.triggerKind)
}

private fun semanticControllerStateStartsNeutral() {
    val state = SemanticControllerState()

    expectEquals("trigger default", false, state.trigger)
    expectEquals("reload default", false, state.reload)
    expectEquals("x default", false, state.x)
    expectEquals("y default", false, state.y)
    expectEquals("a default", false, state.a)
    expectEquals("b default", false, state.b)
    expectEquals("stickX default", 0, state.stickX)
    expectEquals("stickY default", 0, state.stickY)
    expectEquals("aimX default", 0.0f, state.aimX)
    expectEquals("aimY default", 0.0f, state.aimY)
    expectEquals("stale default", false, state.stale)
    expectEquals("sourceSequence default", null, state.sourceSequence)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
