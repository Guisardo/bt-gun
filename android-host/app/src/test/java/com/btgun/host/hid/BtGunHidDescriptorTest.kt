package com.btgun.host.hid

fun main() {
    descriptorBytesArePinned()
    descriptorMirrorsBtGunV1Semantics()
    reportConstantsMatchBluetoothHidContract()
    descriptorUsesStandardGamepadPagesOnly()
}

private fun descriptorBytesArePinned() {
    val expected = byteArrayOf(
        0x05, 0x01,
        0x09, 0x05,
        0xa1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x06,
        0x15, 0x00,
        0x25, 0x01,
        0x95.toByte(), 0x06,
        0x75, 0x01,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x01,
        0x75, 0x02,
        0x81.toByte(), 0x03,
        0x05, 0x01,
        0x16, 0x00, 0x80.toByte(),
        0x26, 0xff.toByte(), 0x7f,
        0x75, 0x10,
        0x95.toByte(), 0x04,
        0x09, 0x30,
        0x09, 0x31,
        0x09, 0x33,
        0x09, 0x34,
        0x81.toByte(), 0x02,
        0x85.toByte(), 0x02,
        0x05, 0x01,
        0x09, 0x00,
        0x15, 0x00,
        0x26, 0xff.toByte(), 0x00,
        0x75, 0x08,
        0x95.toByte(), 0x08,
        0x91.toByte(), 0x02,
        0xc0.toByte(),
    )

    expectByteArray("descriptor", expected, BtGunHidDescriptor.DESCRIPTOR_BYTES)
}

private fun descriptorMirrorsBtGunV1Semantics() {
    expectEquals("device kind", "gamepad_like_joystick", BtGunHidDescriptor.DEVICE_KIND)
    expectEquals("buttons", listOf("trigger", "reload", "x", "y", "a", "b"), BtGunHidDescriptor.BUTTONS)
    expectEquals("axes", listOf("stickX", "stickY", "aimX", "aimY"), BtGunHidDescriptor.AXES)
    expectEquals("trigger kind", "digital", BtGunHidDescriptor.TRIGGER_KIND)
    expectEquals("button count", 6, BtGunHidDescriptor.BUTTON_COUNT)
    expectEquals("axis count", 4, BtGunHidDescriptor.AXIS_COUNT)
}

private fun reportConstantsMatchBluetoothHidContract() {
    expectEquals("input report id", 0x01, BtGunHidDescriptor.INPUT_REPORT_ID)
    expectEquals("output report id", 0x02, BtGunHidDescriptor.OUTPUT_REPORT_ID)
    expectEquals("output report version", 0x01, BtGunHidDescriptor.OUTPUT_REPORT_VERSION)
    expectEquals("input payload length", 9, BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES)
    expectEquals("output payload length", 8, BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES)
}

private fun descriptorUsesStandardGamepadPagesOnly() {
    val descriptor = BtGunHidDescriptor.DESCRIPTOR_BYTES
    expectTrue("uses generic desktop page", descriptor.containsSubsequence(0x05, 0x01))
    expectTrue("uses gamepad usage", descriptor.containsSubsequence(0x09, 0x05))
    expectTrue("uses button page", descriptor.containsSubsequence(0x05, 0x09))
    expectFalse("no vendor usage page", descriptor.containsSubsequence(0x06, 0x00, 0xff))
    expectFalse("no gun usage page", descriptor.containsSubsequence(0x05, 0x05))
}

private fun ByteArray.containsSubsequence(vararg expected: Int): Boolean =
    indices.any { start ->
        expected.indices.all { offset -> start + offset < size && this[start + offset] == expected[offset].toByte() }
    }

private fun expectByteArray(label: String, expected: ByteArray, actual: ByteArray) {
    if (!expected.contentEquals(actual)) {
        throw AssertionError("$label expected <${expected.toHex()}> but was <${actual.toHex()}>")
    }
}

private fun ByteArray.toHex(): String =
    joinToString(" ") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun expectTrue(label: String, actual: Boolean) {
    expectEquals(label, true, actual)
}

private fun expectFalse(label: String, actual: Boolean) {
    expectEquals(label, false, actual)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
