package com.btgun.host.camera

fun main() {
    supportedHardwareZoomClampsAndLabels()
    unsupportedHardwareZoomFallsBackToNoop()
}

private fun supportedHardwareZoomClampsAndLabels() {
    val initial = CameraZoomModel.stateFor(4f)
    expectEquals("supported", true, initial.hardwareSupported)
    expectEquals("label", "1.0x", initial.label)

    val zoomed = CameraZoomModel.setZoom(initial, 2.5f)
    expectEquals("zoom", 2.5f, zoomed.currentZoom)
    expectEquals("zoom label", "2.5x", zoomed.label)

    val clamped = CameraZoomModel.step(zoomed, 10f)
    expectEquals("max clamp", 4f, clamped.currentZoom)
}

private fun unsupportedHardwareZoomFallsBackToNoop() {
    val initial = CameraZoomModel.stateFor(null)
    expectEquals("unsupported", false, initial.hardwareSupported)

    val zoomed = CameraZoomModel.setZoom(initial, 3f)
    expectEquals("noop zoom", 1f, zoomed.currentZoom)
    expectEquals("noop label", "1.0x", zoomed.label)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
