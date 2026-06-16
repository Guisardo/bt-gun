package com.btgun.host.hid

object BtGunHidDescriptor {
    const val INPUT_REPORT_ID: Int = 0x01
    const val OUTPUT_REPORT_ID: Int = 0x02
    const val OUTPUT_REPORT_VERSION: Int = 0x01
    const val INPUT_REPORT_PAYLOAD_LENGTH_BYTES: Int = 11
    const val OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES: Int = 8

    const val DEVICE_KIND: String = "gamepad_like_joystick"
    val BUTTONS: List<String> = listOf(
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
    )
    val AXES: List<String> = listOf("stickX", "stickY", "aimX", "aimY")
    const val TRIGGER_KIND: String = "digital_button_usages"
    const val BUTTON_COUNT: Int = 22
    const val BUTTON_PADDING_COUNT: Int = 2
    const val AXIS_COUNT: Int = 4
    const val BORING_STANDARD_BUTTON_COUNT: Int = 12
    const val BORING_STANDARD_BUTTON_PADDING_COUNT: Int = 4
    const val BORING_STANDARD_HAT_NEUTRAL: Int = 8

    val DESCRIPTOR_BYTES: ByteArray = byteArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x05, // Usage (Game Pad)
        0xa1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), INPUT_REPORT_ID.toByte(), // Report ID
        0x05, 0x09, // Usage Page (Button)
        0x19, 0x01, // Usage Minimum (Button 1)
        0x29, BUTTON_COUNT.toByte(), // Usage Maximum (Button 22)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95.toByte(), BUTTON_COUNT.toByte(), // Report Count (22)
        0x75, 0x01, // Report Size (1)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x95.toByte(), BUTTON_PADDING_COUNT.toByte(), // Report Count (2)
        0x75, 0x01, // Report Size (1)
        0x81.toByte(), 0x03, // Input (Const,Var,Abs) padding
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x16, 0x00, 0x80.toByte(), // Logical Minimum (-32768)
        0x26, 0xff.toByte(), 0x7f, // Logical Maximum (32767)
        0x75, 0x10, // Report Size (16)
        0x95.toByte(), AXIS_COUNT.toByte(), // Report Count (4)
        0x09, 0x30, // Usage (X)
        0x09, 0x31, // Usage (Y)
        0x09, 0x33, // Usage (Rx / aim X)
        0x09, 0x34, // Usage (Ry / aim Y)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x85.toByte(), OUTPUT_REPORT_ID.toByte(), // Report ID
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x00, // Usage (Undefined generic output bytes)
        0x15, 0x00, // Logical Minimum (0)
        0x26, 0xff.toByte(), 0x00, // Logical Maximum (255)
        0x75, 0x08, // Report Size (8)
        0x95.toByte(), OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES.toByte(), // Report Count (8)
        0x91.toByte(), 0x02, // Output (Data,Var,Abs)
        0xc0.toByte(), // End Collection
    )

    val BORING_STANDARD_DESCRIPTOR_BYTES: ByteArray = byteArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x05, // Usage (Game Pad)
        0xa1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), INPUT_REPORT_ID.toByte(), // Report ID
        0x05, 0x09, // Usage Page (Button)
        0x19, 0x01, // Usage Minimum (Button 1)
        0x29, BORING_STANDARD_BUTTON_COUNT.toByte(), // Usage Maximum (Button 12)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95.toByte(), BORING_STANDARD_BUTTON_COUNT.toByte(), // Report Count (12)
        0x75, 0x01, // Report Size (1)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x95.toByte(), BORING_STANDARD_BUTTON_PADDING_COUNT.toByte(), // Report Count (4)
        0x75, 0x01, // Report Size (1)
        0x81.toByte(), 0x03, // Input (Const,Var,Abs) padding
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x39, // Usage (Hat switch)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x07, // Logical Maximum (7)
        0x35, 0x00, // Physical Minimum (0)
        0x46, 0x3b, 0x01, // Physical Maximum (315)
        0x65, 0x14, // Unit (degrees)
        0x75, 0x04, // Report Size (4)
        0x95.toByte(), 0x01, // Report Count (1)
        0x81.toByte(), 0x42, // Input (Data,Var,Abs,Null)
        0x65, 0x00, // Unit (None)
        0x95.toByte(), 0x01, // Report Count (1)
        0x75, 0x04, // Report Size (4)
        0x81.toByte(), 0x03, // Input (Const,Var,Abs) padding
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x16, 0x00, 0x80.toByte(), // Logical Minimum (-32768)
        0x26, 0xff.toByte(), 0x7f, // Logical Maximum (32767)
        0x75, 0x10, // Report Size (16)
        0x95.toByte(), AXIS_COUNT.toByte(), // Report Count (4)
        0x09, 0x30, // Usage (X)
        0x09, 0x31, // Usage (Y)
        0x09, 0x32, // Usage (Z / aim X)
        0x09, 0x33, // Usage (Rx / aim Y)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x85.toByte(), OUTPUT_REPORT_ID.toByte(), // Report ID
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x00, // Usage (Undefined generic output bytes)
        0x15, 0x00, // Logical Minimum (0)
        0x26, 0xff.toByte(), 0x00, // Logical Maximum (255)
        0x75, 0x08, // Report Size (8)
        0x95.toByte(), OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES.toByte(), // Report Count (8)
        0x91.toByte(), 0x02, // Output (Data,Var,Abs)
        0xc0.toByte(), // End Collection
    )
}
