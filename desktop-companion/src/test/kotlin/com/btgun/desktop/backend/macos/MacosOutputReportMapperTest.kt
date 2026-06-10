package com.btgun.desktop.backend.macos

import com.btgun.desktop.haptics.HapticCommand

fun main() {
    mapsValidOutputReportIdTwoToHapticCommand()
    rejectsMalformedOutputReportsBeforeHapticCommand()
    rejectsBlankCommandId()
    mapperOnlyProofIsNotOsOriginOutputProof()
}

private fun mapsValidOutputReportIdTwoToHapticCommand() {
    val command: HapticCommand = requireNotNull(
        MacosOutputReportMapper.toHapticCommand(
            reportBytes = outputReport(strength = 255, durationMs = 120, ttlMs = 500),
            commandId = "macos-output-1",
        ),
    )

    expectEquals("report id constant", 0x02, MACOS_OUTPUT_REPORT_ID)
    expectEquals("report version constant", 0x01, MACOS_OUTPUT_REPORT_VERSION)
    expectEquals("report length constant", 9, MACOS_OUTPUT_REPORT_LENGTH_BYTES)
    expectEquals("command id", "macos-output-1", command.commandId)
    expectClose("strength", 1.0, command.strength)
    expectEquals("duration", 120L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectEquals("pattern", null, command.pattern)
}

private fun rejectsMalformedOutputReportsBeforeHapticCommand() {
    val valid = outputReport(strength = 128, durationMs = 120, ttlMs = 500)

    expectNull("bad report id", valid.copyWith(0, 0x01), commandId = "bad-report-id")
    expectNull("bad length", valid.copyOf(MACOS_OUTPUT_REPORT_LENGTH_BYTES - 1), commandId = "bad-length")
    expectNull("bad version", valid.copyWith(1, 0x02), commandId = "bad-version")
    expectNull("zero duration", outputReport(strength = 128, durationMs = 0, ttlMs = 500), commandId = "zero-duration")
    expectNull("zero ttl", outputReport(strength = 128, durationMs = 120, ttlMs = 0), commandId = "zero-ttl")
    expectNull("oversized duration", outputReport(strength = 128, durationMs = 1001, ttlMs = 500), commandId = "oversized-duration")
    expectNull("oversized ttl", outputReport(strength = 128, durationMs = 120, ttlMs = 2001), commandId = "oversized-ttl")
    expectNull("unsupported flags", valid.copyWith(7, 0x01), commandId = "unsupported-flags")
    expectNull("reserved byte", valid.copyWith(8, 0x01), commandId = "reserved-byte")
}

private fun rejectsBlankCommandId() {
    expectNull(
        label = "blank command id",
        reportBytes = outputReport(strength = 128, durationMs = 120, ttlMs = 500),
        commandId = " ",
    )
}

private fun mapperOnlyProofIsNotOsOriginOutputProof() {
    val command: HapticCommand = requireNotNull(
        MacosOutputReportMapper.toHapticCommand(
            reportBytes = outputReport(strength = 200, durationMs = 250, ttlMs = 750),
            commandId = "macos-mapper-only-proof-not-os-origin",
        ),
    )

    expectEquals("D-07/D-08 mapper-only proof note", "macos-mapper-only-proof-not-os-origin", command.commandId)
    expectEquals("mapper creates phone haptic only", null, command.pattern)
}

private fun expectNull(label: String, reportBytes: ByteArray, commandId: String) {
    val command = MacosOutputReportMapper.toHapticCommand(
        reportBytes = reportBytes,
        commandId = commandId,
    )
    expectEquals(label, null, command)
}

private fun outputReport(
    strength: Int,
    durationMs: Int,
    ttlMs: Int,
): ByteArray =
    byteArrayOf(
        MACOS_OUTPUT_REPORT_ID.toByte(),
        MACOS_OUTPUT_REPORT_VERSION.toByte(),
        strength.toByte(),
        (durationMs and 0xff).toByte(),
        ((durationMs ushr 8) and 0xff).toByte(),
        (ttlMs and 0xff).toByte(),
        ((ttlMs ushr 8) and 0xff).toByte(),
        0,
        0,
    )

private fun ByteArray.copyWith(index: Int, value: Int): ByteArray =
    copyOf().also { it[index] = value.toByte() }

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectClose(label: String, expected: Double, actual: Double, tolerance: Double = 0.000001) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
