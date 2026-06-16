package com.btgun.host.hid

data class BtGunHidProfile(
    val id: String,
    val sdpName: String,
    val sdpDescription: String,
    val sdpProvider: String,
    val sdpSubclass: Byte,
    val descriptorBytes: ByteArray,
    val inputReportId: Int,
    val inputReportPayloadLengthBytes: Int,
    val outputReportId: Int,
    val outputReportVersion: Int,
    val outputReportPayloadLengthBytes: Int,
)

object BtGunHidProfiles {
    const val METADATA_KEY: String = "com.btgun.host.HID_PROFILE"
    const val CURRENT_USER_ID: String = "current_user"
    const val BORING_STANDARD_ID: String = "boring_standard"

    val CURRENT_USER: BtGunHidProfile = BtGunHidProfile(
        id = CURRENT_USER_ID,
        sdpName = "BT Gun Gamepad",
        sdpDescription = "BT Gun Android HID Gamepad",
        sdpProvider = "BT Gun",
        sdpSubclass = 0x02, // BluetoothHidDevice.SUBCLASS2_GAMEPAD
        descriptorBytes = BtGunHidDescriptor.DESCRIPTOR_BYTES,
        inputReportId = BtGunHidDescriptor.INPUT_REPORT_ID,
        inputReportPayloadLengthBytes = BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES,
        outputReportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
        outputReportVersion = BtGunHidDescriptor.OUTPUT_REPORT_VERSION,
        outputReportPayloadLengthBytes = BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES,
    )

    val BORING_STANDARD: BtGunHidProfile = BtGunHidProfile(
        id = BORING_STANDARD_ID,
        sdpName = "BT Gun Gamepad",
        sdpDescription = "BT Gun Diagnostic Gamepad",
        sdpProvider = "BT Gun",
        sdpSubclass = 0x02, // BluetoothHidDevice.SUBCLASS2_GAMEPAD
        descriptorBytes = BtGunHidDescriptor.BORING_STANDARD_DESCRIPTOR_BYTES,
        inputReportId = BtGunHidDescriptor.INPUT_REPORT_ID,
        inputReportPayloadLengthBytes = BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES,
        outputReportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
        outputReportVersion = BtGunHidDescriptor.OUTPUT_REPORT_VERSION,
        outputReportPayloadLengthBytes = BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES,
    )

    val ALL: List<BtGunHidProfile> = listOf(CURRENT_USER, BORING_STANDARD)

    fun resolve(id: String?): BtGunHidProfile {
        val normalized = id?.trim()?.lowercase()
        return ALL.firstOrNull { profile -> profile.id == normalized } ?: CURRENT_USER
    }
}
