package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.SemanticControllerState
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    headerConstantsStaySyncedWithKotlinWindowsAbi()
    packsButtonsAndAxesIntoReportIdOne()
    clampsAimAxesBeforeSignedInt16Encoding()
    staleInputClearsButtonsAndStickButKeepsAim()
}

private fun headerConstantsStaySyncedWithKotlinWindowsAbi() {
    val header = Files.readString(repoRoot().resolve("windows/btgun-vjoy/include/BtGunVJoyIoctl.h"))
    val driverDevice = Files.readString(repoRoot().resolve("windows/btgun-vjoy/driver/BtGunVJoyDevice.c"))
    val inf = Files.readString(repoRoot().resolve("windows/btgun-vjoy/package/btgunvjoy.inf"))
    val packageScript = Files.readString(repoRoot().resolve("windows/btgun-vjoy/package/Package-BtGunVJoy.ps1"))

    expectContains("hardware id", header, "#define BTGVJOY_HARDWARE_ID_A \"Root\\\\BTGunVJoy\"")
    expectContains("device name", header, "#define BTGVJOY_DEVICE_NAME_A \"BT Gun VJoy\"")
    expectContains("vendor id", header, "#define BTGVJOY_VENDOR_ID 0x18D1u")
    expectContains("product id", header, "#define BTGVJOY_PRODUCT_ID 0x9400u")
    expectContains("version number", header, "#define BTGVJOY_VERSION_NUMBER 0x0603u")
    expectContains("abi version", header, "#define BTGVJOY_ABI_VERSION 1u")
    expectContains("input report id", header, "#define BTGVJOY_INPUT_REPORT_ID ${WINDOWS_INPUT_REPORT_ID}u")
    expectContains(
        "input report length",
        header,
        "#define BTGVJOY_INPUT_REPORT_LENGTH_BYTES ${WINDOWS_INPUT_REPORT_LENGTH_BYTES}u",
    )
    expectContains("output report id", header, "#define BTGVJOY_OUTPUT_REPORT_ID ${WINDOWS_OUTPUT_REPORT_ID}u")
    expectContains("output report version", header, "#define BTGVJOY_OUTPUT_REPORT_VERSION ${WINDOWS_OUTPUT_REPORT_VERSION}u")
    expectContains(
        "output report length",
        header,
        "#define BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES ${WINDOWS_OUTPUT_REPORT_LENGTH_BYTES}u",
    )
    expectContains("chrome haptic report id", header, "#define BTGVJOY_CHROME_HAPTIC_REPORT_ID 5u")
    expectContains("chrome haptic report length", header, "#define BTGVJOY_CHROME_HAPTIC_REPORT_LENGTH_BYTES 5u")
    expectContains("chrome haptic payload length", header, "#define BTGVJOY_CHROME_HAPTIC_PAYLOAD_LENGTH_BYTES 4u")
    expectContains("chrome haptic duration", header, "#define BTGVJOY_CHROME_HAPTIC_DURATION_MS 1000u")
    expectContains("chrome haptic ttl", header, "#define BTGVJOY_CHROME_HAPTIC_TTL_MS 2000u")
    expectContains("ioctl device type", header, "#define FILE_DEVICE_BT_GUN_VJOY 0x8000u")
    expectContains("submit ioctl function", header, "CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x801, METHOD_BUFFERED, FILE_WRITE_DATA)")
    expectContains("read output ioctl function", header, "CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x802, METHOD_BUFFERED, FILE_READ_DATA)")
    expectContains("status ioctl function", header, "CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x803, METHOD_BUFFERED, FILE_READ_DATA)")
    expectContains("input source sequence field", header, "BTGVJOY_UINT64 SourceSequence;")
    expectContains("output sequence field", header, "BTGVJOY_UINT64 OutputSequence;")
    expectContains("status last input sequence field", header, "BTGVJOY_UINT64 LastInputSequence;")
    expectContains("status last output sequence field", header, "BTGVJOY_UINT64 LastOutputSequence;")
    expectContains("descriptor report id 1", driverDevice, "0x85, 0x01")
    expectContains("descriptor report id 2", driverDevice, "0x85, 0x02")
    expectContains("descriptor report id 5", driverDevice, "0x85, 0x05")
    expectContains("vhf vendor id", driverDevice, "vhfConfig.VendorID = BTGVJOY_VENDOR_ID;")
    expectContains("vhf product id", driverDevice, "vhfConfig.ProductID = BTGVJOY_PRODUCT_ID;")
    expectContains("vhf version", driverDevice, "vhfConfig.VersionNumber = BTGVJOY_VERSION_NUMBER;")
    expectContains("inf version pending proof", inf, "DriverVer=06/14/2026,0.6.3.0")
    expectContains("inf hardware id", inf, "%BtGunVJoy.DeviceDesc%=BtGunVJoy_Install, Root\\BTGunVJoy")
    expectContains("inf direct input identity", inf, "VID_18D1&PID_9400")
    expectContains("package vendor id", packageScript, "vendorId = \"VID_18D1\"")
    expectContains("package product id", packageScript, "productId = \"PID_9400\"")
    expectContains("package version number", packageScript, "versionNumber = \"0x0603\"")
    expectContains("package report id 1", packageScript, "input = 1")
    expectContains("package report id 2", packageScript, "nativeOutput = 2")
    expectContains("package report id 5", packageScript, "chromeHapticOutput = 5")
}

private fun packsButtonsAndAxesIntoReportIdOne() {
    val report = WindowsHidReportPacker.packInputReport(
        SemanticControllerState(
            trigger = true,
            reload = true,
            x = true,
            y = false,
            a = true,
            b = false,
            stickX = 12345,
            stickY = -12345,
            aimX = 0.5f,
            aimY = -0.25f,
            stale = false,
            sourceSequence = 42L,
        ),
    )

    expectEquals("report id constant", 0x01, WINDOWS_INPUT_REPORT_ID)
    expectEquals("report length constant", 10, WINDOWS_INPUT_REPORT_LENGTH_BYTES)
    expectEquals("report id", WINDOWS_INPUT_REPORT_ID.toByte(), report.bytes[0])
    expectEquals("byte length", WINDOWS_INPUT_REPORT_LENGTH_BYTES, report.bytes.size)
    expectEquals("button bits", 0b0001_0111.toByte(), report.bytes[1])
    expectEquals("stickX", 12345, report.bytes.readInt16Le(2))
    expectEquals("stickY inverted for Windows HID", 12345, report.bytes.readInt16Le(4))
    expectEquals("aimX", 16384, report.bytes.readInt16Le(6))
    expectEquals("aimY", -8192, report.bytes.readInt16Le(8))
    expectEquals("stale metadata", false, report.stale)
    expectEquals("source sequence", 42L, report.sourceSequence)
}

private fun clampsAimAxesBeforeSignedInt16Encoding() {
    val report = WindowsHidReportPacker.packInputReport(
        SemanticControllerState(
            stickX = Int.MAX_VALUE,
            stickY = Int.MIN_VALUE,
            aimX = 2.0f,
            aimY = -2.0f,
        ),
    )

    expectEquals("stickX clamp", 32767, report.bytes.readInt16Le(2))
    expectEquals("stickY inverted clamp", 32767, report.bytes.readInt16Le(4))
    expectEquals("aimX clamp", 32767, report.bytes.readInt16Le(6))
    expectEquals("aimY clamp", -32768, report.bytes.readInt16Le(8))
}

private fun staleInputClearsButtonsAndStickButKeepsAim() {
    val report = WindowsHidReportPacker.packInputReport(
        SemanticControllerState(
            trigger = true,
            reload = true,
            x = true,
            y = true,
            a = true,
            b = true,
            stickX = 2222,
            stickY = -3333,
            aimX = 0.125f,
            aimY = -0.5f,
            stale = true,
            sourceSequence = 77L,
        ),
    )

    expectEquals("stale button bits clear", 0.toByte(), report.bytes[1])
    expectEquals("stale stickX clear", 0, report.bytes.readInt16Le(2))
    expectEquals("stale stickY clear", 0, report.bytes.readInt16Le(4))
    expectEquals("stale aimX preserved", 4096, report.bytes.readInt16Le(6))
    expectEquals("stale aimY preserved", -16384, report.bytes.readInt16Le(8))
    expectEquals("stale metadata", true, report.stale)
    expectEquals("stale source sequence", 77L, report.sourceSequence)
}

private fun ByteArray.readInt16Le(offset: Int): Int {
    val unsigned = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    return if ((unsigned and 0x8000) != 0) unsigned - 0x10000 else unsigned
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectContains(label: String, text: String, expected: String) {
    if (!text.contains(expected)) {
        throw AssertionError("$label expected header to contain <$expected>")
    }
}

private fun repoRoot(): Path {
    var current = Path.of("").toAbsolutePath()
    while (true) {
        if (Files.isRegularFile(current.resolve("windows/btgun-vjoy/include/BtGunVJoyIoctl.h"))) {
            return current
        }
        current = current.parent ?: throw IllegalStateException("repo root not found")
    }
}
