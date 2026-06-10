package com.btgun.host.hid

object BtGunHidDescriptor {
    const val INPUT_REPORT_ID: Int = 0x01
    const val OUTPUT_REPORT_ID: Int = 0x02
    const val OUTPUT_REPORT_VERSION: Int = 0x01
    const val INPUT_REPORT_PAYLOAD_LENGTH_BYTES: Int = 9
    const val OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES: Int = 8

    const val DEVICE_KIND: String = "gamepad_like_joystick"
    val BUTTONS: List<String> = listOf("trigger", "reload", "x", "y", "a", "b")
    val AXES: List<String> = listOf("stickX", "stickY", "aimX", "aimY")
    const val TRIGGER_KIND: String = "digital"
    const val BUTTON_COUNT: Int = 6
    const val AXIS_COUNT: Int = 4

    val DESCRIPTOR_BYTES: ByteArray = byteArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x05, // Usage (Game Pad)
        0xa1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), INPUT_REPORT_ID.toByte(), // Report ID
        0x05, 0x09, // Usage Page (Button)
        0x19, 0x01, // Usage Minimum (Button 1)
        0x29, BUTTON_COUNT.toByte(), // Usage Maximum (Button 6)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95.toByte(), BUTTON_COUNT.toByte(), // Report Count (6)
        0x75, 0x01, // Report Size (1)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x95.toByte(), 0x01, // Report Count (1)
        0x75, 0x02, // Report Size (2)
        0x81.toByte(), 0x03, // Input (Const,Var,Abs)
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x16, 0x00, 0x80.toByte(), // Logical Minimum (-32768)
        0x26, 0xff.toByte(), 0x7f, // Logical Maximum (32767)
        0x75, 0x10, // Report Size (16)
        0x95.toByte(), AXIS_COUNT.toByte(), // Report Count (4)
        0x09, 0x30, // Usage (X)
        0x09, 0x31, // Usage (Y)
        0x09, 0x33, // Usage (Rx)
        0x09, 0x34, // Usage (Ry)
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
