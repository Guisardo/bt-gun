package com.btgun.desktop.backend.macos

import com.btgun.desktop.backend.SemanticControllerState

fun main() {
    packsButtonsAndAxesIntoReportIdOne()
    clampsStickAndAimAxesBeforeSignedInt16Encoding()
    staleInputClearsButtonsAndStickButKeepsAim()
}

private fun packsButtonsAndAxesIntoReportIdOne() {
    val report = MacosHidReportPacker.packInputReport(
        SemanticControllerState(
            trigger = true,
            reload = true,
            x = true,
            y = false,
            a = true,
            b = true,
            stickX = 12345,
            stickY = -12345,
            aimX = 0.5f,
            aimY = -0.25f,
            stale = false,
            sourceSequence = 42L,
        ),
    )

    expectEquals("report id constant", 0x01, MACOS_INPUT_REPORT_ID)
    expectEquals("report length constant", 10, MACOS_INPUT_REPORT_LENGTH_BYTES)
    expectEquals("report id", MACOS_INPUT_REPORT_ID.toByte(), report.bytes[0])
    expectEquals("byte length", MACOS_INPUT_REPORT_LENGTH_BYTES, report.bytes.size)
    expectEquals("button bits", 0b0011_0111.toByte(), report.bytes[1])
    expectEquals("stickX", 12345, report.bytes.readInt16Le(2))
    expectEquals("stickY inverted for macOS HID", 12345, report.bytes.readInt16Le(4))
    expectEquals("aimX", 16384, report.bytes.readInt16Le(6))
    expectEquals("aimY", -8192, report.bytes.readInt16Le(8))
    expectEquals("stale metadata", false, report.stale)
    expectEquals("source sequence", 42L, report.sourceSequence)
}

private fun clampsStickAndAimAxesBeforeSignedInt16Encoding() {
    val report = MacosHidReportPacker.packInputReport(
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
    val report = MacosHidReportPacker.packInputReport(
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
