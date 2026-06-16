package com.btgun.host.hid

import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.profile.MappedAimStatus
import com.btgun.host.profile.MappedControllerState
import java.io.File

fun main() {
    packsButtonsAndAxesIntoPinnedInputPayload()
    packsMappedButtonsAndAxesIntoPinnedInputPayload()
    packsExtendedJoypadDestinationsIntoPinnedInputPayload()
    usesNormalizedAimBeforeRawAim()
    clampsAxesAndFallsBackToRawAim()
    centersAimWhenMotionIsMissing()
    staleReportClearsButtonsAndStickButKeepsSelectedAim()
    staleMappedReportClearsButtonsAndStickButKeepsMappedAim()
    neutralReportClearsButtonsStickAndAim()
    descriptorBytesRemainPinnedForMappedState()
    currentUserProfileRemainsDefault()
    boringStandardNeutralReportUsesHatNeutral()
    boringStandardMapsButtonsHatAndMasksUnsupportedButtons()
    boringStandardOppositeDpadDirectionsCancelToNeutralHat()
    hidPackageDoesNotDependOnDesktopModules()
}

private fun packsButtonsAndAxesIntoPinnedInputPayload() {
    val report = BtGunHidReportPacker.packInputReport(
        state = GunInputState(
            pressedControls = setOf("trigger", "reload", "button_x", "button_a"),
            stickAxisX = 0.5f,
            stickAxisY = -0.25f,
        ),
        motion = motion(aimX = 0.25f, aimY = -0.5f, rawAimX = 11.25f, rawAimY = 22.5f, aimCalibrated = true),
        stale = false,
    )

    expectEquals("report id", BtGunHidDescriptor.INPUT_REPORT_ID, report.reportId)
    expectEquals("payload length", BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES, report.bytes.size)
    expectByteArray(
        "golden input payload",
        byteArrayOf(
            0xc5.toByte(), 0x00, 0x00,
            0x00, 0x40,
            0x00, 0x20,
            0x00, 0x20,
            0x00, 0x40,
        ),
        report.bytes,
    )
    expectEquals("button bits", 0x00c5, report.bytes.readButtonBits())
    expectEquals("stickX", 16_384, report.bytes.readInt16Le(3))
    expectEquals("stickY inverted for HID", 8_192, report.bytes.readInt16Le(5))
    expectEquals("aimX calibrated", 8_192, report.bytes.readInt16Le(7))
    expectEquals("aimY calibrated inverted for HID", 16_384, report.bytes.readInt16Le(9))
    expectEquals("aim source", "calibrated", report.aimSource)
    expectEquals("stale metadata", false, report.stale)
}

private fun packsMappedButtonsAndAxesIntoPinnedInputPayload() {
    val report = BtGunHidReportPacker.packInputReport(
        mappedState = mappedState(
            pressedVirtualControls = setOf("jp_button_r2", "jp_button_l2", "jp_button_b3", "jp_button_b1"),
            stickAxisX = 0.5f,
            stickAxisY = -0.25f,
            aimAxisX = 0.25f,
            aimAxisY = -0.5f,
            aimSource = "mapped_profile",
        ),
        stale = false,
    )

    expectEquals("mapped report id", BtGunHidDescriptor.INPUT_REPORT_ID, report.reportId)
    expectEquals("mapped payload length", BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES, report.bytes.size)
    expectByteArray(
        "mapped golden payload",
        byteArrayOf(
            0xc5.toByte(), 0x00, 0x00,
            0x00, 0x40,
            0x00, 0x20,
            0x00, 0x20,
            0x00, 0x40,
        ),
        report.bytes,
    )
    expectEquals("mapped aim source", "mapped_profile", report.aimSource)
}

private fun packsExtendedJoypadDestinationsIntoPinnedInputPayload() {
    val report = BtGunHidReportPacker.packInputReport(
        mappedState = mappedState(
            pressedVirtualControls = setOf("jp_button_a1", "jp_button_r4"),
        ),
        stale = false,
    )

    expectEquals("extended button bits", (1 shl 16) or (1 shl 21), report.bytes.readButtonBits())
}

private fun usesNormalizedAimBeforeRawAim() {
    val report = BtGunHidReportPacker.packInputReport(
        state = GunInputState(),
        motion = motion(aimX = -0.25f, aimY = 0.75f, rawAimX = 1f, rawAimY = -1f),
        stale = false,
    )

    expectEquals("normalized aimX preferred", -8_192, report.bytes.readInt16Le(7))
    expectEquals("normalized aimY preferred and inverted for HID", -24_576, report.bytes.readInt16Le(9))
    expectEquals("aim source", "normalized", report.aimSource)
}

private fun clampsAxesAndFallsBackToRawAim() {
    val report = BtGunHidReportPacker.packInputReport(
        state = GunInputState(
            pressedControls = setOf("button_y", "button_b", "unknown"),
            stickAxisX = 2.0f,
            stickAxisY = -2.0f,
        ),
        motion = motion(aimX = null, aimY = null, rawAimX = 1.5f, rawAimY = -1.5f),
        stale = false,
    )

    expectEquals("button bits ignore unknown", 0x000a, report.bytes.readButtonBits())
    expectEquals("stickX clamp", 32_767, report.bytes.readInt16Le(3))
    expectEquals("stickY inverted clamp", 32_767, report.bytes.readInt16Le(5))
    expectEquals("raw aimX clamp", 32_767, report.bytes.readInt16Le(7))
    expectEquals("raw aimY clamp inverted for HID", 32_767, report.bytes.readInt16Le(9))
    expectEquals("aim source", "raw", report.aimSource)
}

private fun centersAimWhenMotionIsMissing() {
    val report = BtGunHidReportPacker.packInputReport(
        state = GunInputState(pressedControls = setOf("trigger"), stickAxisX = -1f, stickAxisY = 1f),
        motion = null,
        stale = false,
    )

    expectEquals("button bits", 0x0080, report.bytes.readButtonBits())
    expectEquals("stickX min", -32_768, report.bytes.readInt16Le(3))
    expectEquals("stickY inverted min", -32_768, report.bytes.readInt16Le(5))
    expectEquals("aimX center", 0, report.bytes.readInt16Le(7))
    expectEquals("aimY center", 0, report.bytes.readInt16Le(9))
    expectEquals("aim source", "center", report.aimSource)
}

private fun staleReportClearsButtonsAndStickButKeepsSelectedAim() {
    val report = BtGunHidReportPacker.packInputReport(
        state = GunInputState(
            pressedControls = setOf("trigger", "reload", "button_x", "button_y", "button_a", "button_b"),
            stickAxisX = 0.75f,
            stickAxisY = -0.75f,
        ),
        motion = motion(aimX = -0.125f, aimY = 0.5f, rawAimX = 1f, rawAimY = -1f, aimCalibrated = true),
        stale = true,
    )

    expectEquals("stale buttons clear", 0, report.bytes.readButtonBits())
    expectEquals("stale stickX clear", 0, report.bytes.readInt16Le(3))
    expectEquals("stale stickY clear", 0, report.bytes.readInt16Le(5))
    expectEquals("stale aimX preserved", -4_096, report.bytes.readInt16Le(7))
    expectEquals("stale aimY preserved inverted for HID", -16_384, report.bytes.readInt16Le(9))
    expectEquals("aim source", "calibrated", report.aimSource)
    expectEquals("stale metadata", true, report.stale)
}

private fun staleMappedReportClearsButtonsAndStickButKeepsMappedAim() {
    val report = BtGunHidReportPacker.packInputReport(
        mappedState = mappedState(
            pressedVirtualControls = setOf(
                "jp_button_r2",
                "jp_button_l2",
                "jp_button_b3",
                "jp_button_b4",
                "jp_button_b1",
                "jp_button_b2",
            ),
            stickAxisX = 0.75f,
            stickAxisY = -0.75f,
            aimAxisX = -0.125f,
            aimAxisY = 0.5f,
            aimSource = "mapped_profile",
        ),
        stale = true,
    )

    expectEquals("mapped stale buttons clear", 0, report.bytes.readButtonBits())
    expectEquals("mapped stale stickX clear", 0, report.bytes.readInt16Le(3))
    expectEquals("mapped stale stickY clear", 0, report.bytes.readInt16Le(5))
    expectEquals("mapped stale aimX preserved", -4_096, report.bytes.readInt16Le(7))
    expectEquals("mapped stale aimY preserved inverted for HID", -16_384, report.bytes.readInt16Le(9))
    expectEquals("mapped stale source", "mapped_profile", report.aimSource)
    expectEquals("mapped stale metadata", true, report.stale)
}

private fun neutralReportClearsButtonsStickAndAim() {
    val report = BtGunHidReportPacker.neutralInputReport()

    expectEquals("neutral report id", BtGunHidDescriptor.INPUT_REPORT_ID, report.reportId)
    expectByteArray("neutral payload", ByteArray(BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES), report.bytes)
    expectEquals("neutral aim source", "neutral", report.aimSource)
    expectEquals("neutral stale metadata", true, report.stale)
}

private fun descriptorBytesRemainPinnedForMappedState() {
    expectEquals("descriptor payload length", 11, BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES)
    expectByteArray(
        "descriptor bytes pinned",
        byteArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xa1.toByte(), 0x01, 0x85.toByte(), 0x01,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x16, 0x15, 0x00, 0x25, 0x01,
            0x95.toByte(), 0x16, 0x75, 0x01, 0x81.toByte(), 0x02, 0x95.toByte(), 0x02,
            0x75, 0x01, 0x81.toByte(), 0x03, 0x05, 0x01, 0x16, 0x00, 0x80.toByte(),
            0x26, 0xff.toByte(), 0x7f, 0x75, 0x10, 0x95.toByte(), 0x04, 0x09, 0x30,
            0x09, 0x31, 0x09, 0x33, 0x09, 0x34, 0x81.toByte(), 0x02, 0x85.toByte(),
            0x02, 0x05, 0x01, 0x09, 0x00, 0x15, 0x00, 0x26, 0xff.toByte(), 0x00,
            0x75, 0x08, 0x95.toByte(), 0x08, 0x91.toByte(), 0x02, 0xc0.toByte(),
        ),
        BtGunHidDescriptor.DESCRIPTOR_BYTES,
    )
}

private fun currentUserProfileRemainsDefault() {
    val report = BtGunHidReportPacker.packInputReport(
        mappedState = mappedState(pressedVirtualControls = setOf("jp_button_a1")),
        stale = false,
    )

    expectEquals("default profile", BtGunHidProfiles.CURRENT_USER.id, report.profile.id)
    expectEquals("default keeps extended button", 1 shl 16, report.bytes.readButtonBits())
}

private fun boringStandardNeutralReportUsesHatNeutral() {
    val report = BtGunHidReportPacker.neutralInputReport(BtGunHidProfiles.BORING_STANDARD)

    expectEquals("boring profile", BtGunHidProfiles.BORING_STANDARD.id, report.profile.id)
    expectEquals("neutral button bits", 0, report.bytes.readButtonBits16())
    expectEquals("neutral hat", 8, report.bytes.readHat())
    expectEquals("neutral stickX", 0, report.bytes.readInt16Le(3))
    expectEquals("neutral stickY", 0, report.bytes.readInt16Le(5))
    expectEquals("neutral aimX", 0, report.bytes.readInt16Le(7))
    expectEquals("neutral aimY", 0, report.bytes.readInt16Le(9))
}

private fun boringStandardMapsButtonsHatAndMasksUnsupportedButtons() {
    val report = BtGunHidReportPacker.packInputReport(
        mappedState = mappedState(
            pressedVirtualControls = setOf(
                "jp_button_b1",
                "jp_button_r2",
                "jp_button_s2",
                "jp_button_du",
                "jp_button_dr",
                "jp_button_a1",
                "jp_button_r4",
            ),
            stickAxisX = 0.5f,
            stickAxisY = -0.25f,
            aimAxisX = 0.25f,
            aimAxisY = -0.5f,
        ),
        stale = false,
        profile = BtGunHidProfiles.BORING_STANDARD,
    )

    expectByteArray(
        "boring golden payload",
        byteArrayOf(
            0x81.toByte(), 0x02, 0x01,
            0x00, 0x40,
            0x00, 0x20,
            0x00, 0x20,
            0x00, 0x40,
        ),
        report.bytes,
    )
    expectEquals("boring button bits", (1 shl 0) or (1 shl 7) or (1 shl 9), report.bytes.readButtonBits16())
    expectEquals("boring hat up-right", 1, report.bytes.readHat())
}

private fun boringStandardOppositeDpadDirectionsCancelToNeutralHat() {
    val report = BtGunHidReportPacker.packInputReport(
        mappedState = mappedState(
            pressedVirtualControls = setOf(
                "jp_button_du",
                "jp_button_dd",
                "jp_button_dl",
                "jp_button_dr",
            ),
        ),
        stale = false,
        profile = BtGunHidProfiles.BORING_STANDARD,
    )

    expectEquals("opposite dpad neutral", 8, report.bytes.readHat())
}

private fun hidPackageDoesNotDependOnDesktopModules() {
    val roots = listOf(
        sourceDir("hid"),
        File("app/src/test/java/com/btgun/host/hid"),
    )
    val banned = listOf("com.btgun." + "desktop", "Macos" + "Hid", "Windows" + "Hid")
    roots.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }.forEach { file ->
        val text = file.readText()
        banned.forEach { token ->
            expectFalse("${file.path} excludes $token", text.contains(token))
        }
    }
}

private fun motion(
    aimX: Float?,
    aimY: Float?,
    rawAimX: Float?,
    rawAimY: Float?,
    aimCalibrated: Boolean = false,
): MotionSample =
    MotionSample(
        provider = MotionProvider.ROTATION_VECTOR,
        sourceSensorElapsedNanos = 1_000L,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        rawAimX = rawAimX,
        rawAimY = rawAimY,
        aimX = aimX,
        aimY = aimY,
        aimCalibrated = aimCalibrated,
    )

private fun mappedState(
    pressedVirtualControls: Set<String> = emptySet(),
    stickAxisX: Float = 0f,
    stickAxisY: Float = 0f,
    aimAxisX: Float = 0f,
    aimAxisY: Float = 0f,
    aimSource: String = "mapped_profile",
): MappedControllerState =
    MappedControllerState(
        aimAxisX = aimAxisX,
        aimAxisY = aimAxisY,
        aimStatus = MappedAimStatus(
            aimSource = aimSource,
            providerName = "rotation_vector",
            smoothingMode = "off",
            estimatedFilterLagMillis = 0,
            adaptiveFallback = false,
        ),
        pressedVirtualControls = pressedVirtualControls,
        stickAxisX = stickAxisX,
        stickAxisY = stickAxisY,
        recenterPhysicalControl = "reload",
    )

private fun ByteArray.readInt16Le(offset: Int): Int {
    val unsigned = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    return if ((unsigned and 0x8000) != 0) unsigned - 0x10000 else unsigned
}

private fun ByteArray.readButtonBits(): Int =
    (this[0].toInt() and 0xff) or
        ((this[1].toInt() and 0xff) shl 8) or
        ((this[2].toInt() and 0xff) shl 16)

private fun ByteArray.readButtonBits16(): Int =
    (this[0].toInt() and 0xff) or
        ((this[1].toInt() and 0xff) shl 8)

private fun ByteArray.readHat(): Int =
    this[2].toInt() and 0x0f

private fun expectByteArray(label: String, expected: ByteArray, actual: ByteArray) {
    if (!expected.contentEquals(actual)) {
        throw AssertionError("$label expected <${expected.toHex()}> but was <${actual.toHex()}>")
    }
}

private fun ByteArray.toHex(): String =
    joinToString(" ") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun expectFalse(label: String, actual: Boolean) {
    expectEquals(label, false, actual)
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun sourceDir(relative: String): File {
    val direct = File("src/main/java/com/btgun/host/$relative")
    if (direct.exists()) return direct
    return File("runtime/src/main/java/com/btgun/host/$relative")
}
