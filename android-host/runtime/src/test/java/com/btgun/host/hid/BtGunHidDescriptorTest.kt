package com.btgun.host.hid

fun main() {
    descriptorBytesArePinned()
    boringStandardDescriptorBytesArePinned()
    descriptorMirrorsBtGunV1Semantics()
    boringStandardDescriptorMirrorsDiagnosticSemantics()
    reportConstantsMatchBluetoothHidContract()
    defaultDescriptorUsesStandardGamepadPagesOnly()
    hidProfileResolverDefaultsToUserFacingProfile()
}

private fun descriptorBytesArePinned() {
    val expected = byteArrayOf(
        0x05, 0x01,
        0x09, 0x05,
        0xa1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x16,
        0x15, 0x00,
        0x25, 0x01,
        0x95.toByte(), 0x16,
        0x75, 0x01,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x02,
        0x75, 0x01,
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

    expectByteArray("gamepad descriptor", expected, BtGunHidDescriptor.DESCRIPTOR_BYTES)
}

private fun descriptorMirrorsBtGunV1Semantics() {
    expectEquals("device kind", "gamepad_like_joystick", BtGunHidDescriptor.DEVICE_KIND)
    expectEquals(
        "buttons",
        listOf(
            "jp_button_b1",
            "jp_button_b2",
            "jp_button_b3",
            "jp_button_b4",
            "jp_button_l1",
            "jp_button_r1",
            "jp_button_l2",
            "jp_button_r2",
            "jp_button_s1",
            "jp_button_s2",
            "jp_button_l3",
            "jp_button_r3",
            "jp_button_du",
            "jp_button_dd",
            "jp_button_dl",
            "jp_button_dr",
            "jp_button_a1",
            "jp_button_a2",
            "jp_button_a3",
            "jp_button_a4",
            "jp_button_l4",
            "jp_button_r4",
        ),
        BtGunHidDescriptor.BUTTONS,
    )
    expectEquals("axes", listOf("stickX", "stickY", "aimX", "aimY"), BtGunHidDescriptor.AXES)
    expectEquals("trigger kind", "digital_button_usages", BtGunHidDescriptor.TRIGGER_KIND)
    expectEquals("button count", 22, BtGunHidDescriptor.BUTTON_COUNT)
    expectEquals("button padding count", 2, BtGunHidDescriptor.BUTTON_PADDING_COUNT)
    expectEquals("axis count", 4, BtGunHidDescriptor.AXIS_COUNT)
    expectTrue("aim uses rotation pair", BtGunHidDescriptor.DESCRIPTOR_BYTES.containsSubsequence(0x09, 0x33, 0x09, 0x34))
    expectFalse("aim does not use Z axis", BtGunHidDescriptor.DESCRIPTOR_BYTES.containsSubsequence(0x09, 0x32, 0x09, 0x33))
}

private fun boringStandardDescriptorBytesArePinned() {
    val expected = byteArrayOf(
        0x05, 0x01,
        0x09, 0x05,
        0xa1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x0c,
        0x15, 0x00,
        0x25, 0x01,
        0x95.toByte(), 0x0c,
        0x75, 0x01,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x04,
        0x75, 0x01,
        0x81.toByte(), 0x03,
        0x05, 0x01,
        0x09, 0x39,
        0x15, 0x00,
        0x25, 0x07,
        0x35, 0x00,
        0x46, 0x3b, 0x01,
        0x65, 0x14,
        0x75, 0x04,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x42,
        0x65, 0x00,
        0x95.toByte(), 0x01,
        0x75, 0x04,
        0x81.toByte(), 0x03,
        0x05, 0x01,
        0x16, 0x00, 0x80.toByte(),
        0x26, 0xff.toByte(), 0x7f,
        0x75, 0x10,
        0x95.toByte(), 0x04,
        0x09, 0x30,
        0x09, 0x31,
        0x09, 0x32,
        0x09, 0x33,
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

    expectByteArray("boring descriptor", expected, BtGunHidDescriptor.BORING_STANDARD_DESCRIPTOR_BYTES)
}

private fun boringStandardDescriptorMirrorsDiagnosticSemantics() {
    expectEquals("boring profile id", "boring_standard", BtGunHidProfiles.BORING_STANDARD.id)
    expectEquals("boring button count", 12, BtGunHidDescriptor.BORING_STANDARD_BUTTON_COUNT)
    expectEquals("boring hat neutral", 8, BtGunHidDescriptor.BORING_STANDARD_HAT_NEUTRAL)
    expectTrue(
        "boring descriptor uses hat switch",
        BtGunHidDescriptor.BORING_STANDARD_DESCRIPTOR_BYTES.containsSubsequence(0x09, 0x39),
    )
    expectTrue(
        "boring aim uses Z/Rx",
        BtGunHidDescriptor.BORING_STANDARD_DESCRIPTOR_BYTES.containsSubsequence(0x09, 0x32, 0x09, 0x33),
    )
    expectFalse(
        "boring aim does not use Rx/Ry pair",
        BtGunHidDescriptor.BORING_STANDARD_DESCRIPTOR_BYTES.containsSubsequence(0x09, 0x33, 0x09, 0x34),
    )
}

private fun reportConstantsMatchBluetoothHidContract() {
    expectEquals("input report id", 0x01, BtGunHidDescriptor.INPUT_REPORT_ID)
    expectEquals("output report id", 0x02, BtGunHidDescriptor.OUTPUT_REPORT_ID)
    expectEquals("output report version", 0x01, BtGunHidDescriptor.OUTPUT_REPORT_VERSION)
    expectEquals("input payload length", 11, BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES)
    expectEquals("output payload length", 8, BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES)
}

private fun defaultDescriptorUsesStandardGamepadPagesOnly() {
    val descriptor = BtGunHidDescriptor.DESCRIPTOR_BYTES
    expectTrue("uses generic desktop page", descriptor.containsSubsequence(0x05, 0x01))
    expectTrue("uses gamepad usage", descriptor.containsSubsequence(0x09, 0x05))
    expectTrue("uses button page", descriptor.containsSubsequence(0x05, 0x09))
    expectFalse("no pid force feedback page by default", descriptor.containsSubsequence(0x05, 0x0f))
    expectFalse("no vendor usage page", descriptor.containsSubsequence(0x06, 0x00, 0xff))
    expectFalse("no gun usage page", descriptor.containsSubsequence(0x05, 0x05))
}

private fun hidProfileResolverDefaultsToUserFacingProfile() {
    expectEquals("missing profile defaults", BtGunHidProfiles.CURRENT_USER, BtGunHidProfiles.resolve(null))
    expectEquals("blank profile defaults", BtGunHidProfiles.CURRENT_USER, BtGunHidProfiles.resolve(" "))
    expectEquals(
        "diagnostic profile resolves",
        BtGunHidProfiles.BORING_STANDARD,
        BtGunHidProfiles.resolve("boring_standard"),
    )
    expectEquals(
        "diagnostic profile trims and lowercases",
        BtGunHidProfiles.BORING_STANDARD,
        BtGunHidProfiles.resolve(" BORING_STANDARD "),
    )
    expectEquals("unknown profile defaults", BtGunHidProfiles.CURRENT_USER, BtGunHidProfiles.resolve("unknown"))
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
