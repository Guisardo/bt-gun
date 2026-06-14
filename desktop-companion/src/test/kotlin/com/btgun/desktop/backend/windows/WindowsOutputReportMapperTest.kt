package com.btgun.desktop.backend.windows

fun main() {
    mapsValidOutputReportIdTwoToHapticCommand()
    mapsZeroStrengthOutputReportToCancelCommand()
    rejectsMalformedOutputReportsBeforeHapticCommand()
}

private fun mapsValidOutputReportIdTwoToHapticCommand() {
    val command = requireNotNull(
        WindowsOutputReportMapper.toHapticCommand(
            reportBytes = outputReport(strength = 255, durationMs = 120, ttlMs = 500),
            commandId = "windows-output-1",
        ),
    )

    expectEquals("report id constant", 0x02, WINDOWS_OUTPUT_REPORT_ID)
    expectEquals("report version constant", 0x01, WINDOWS_OUTPUT_REPORT_VERSION)
    expectEquals("report length constant", 9, WINDOWS_OUTPUT_REPORT_LENGTH_BYTES)
    expectEquals("command id", "windows-output-1", command.commandId)
    expectClose("strength", 1.0, command.strength)
    expectEquals("duration", 120L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectEquals("pattern", null, command.pattern)
}

private fun mapsZeroStrengthOutputReportToCancelCommand() {
    val command = requireNotNull(
        WindowsOutputReportMapper.toHapticCommand(
            reportBytes = outputReport(strength = 0, durationMs = 1, ttlMs = 500),
            commandId = "windows-output-stop",
        ),
    )

    expectEquals("command id", "windows-output-stop", command.commandId)
    expectClose("strength", 0.0, command.strength)
    expectEquals("duration", 1L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
}

private fun rejectsMalformedOutputReportsBeforeHapticCommand() {
    val valid = outputReport(strength = 128, durationMs = 120, ttlMs = 500)

    expectNull("bad report id", valid.copyWith(0, 0x01))
    expectNull("bad length", valid.copyOf(WINDOWS_OUTPUT_REPORT_LENGTH_BYTES - 1))
    expectNull("bad version", valid.copyWith(1, 0x02))
    expectNull("zero duration", outputReport(strength = 128, durationMs = 0, ttlMs = 500))
    expectNull("zero ttl", outputReport(strength = 128, durationMs = 120, ttlMs = 0))
    expectNull("oversized duration", outputReport(strength = 128, durationMs = 1001, ttlMs = 500))
    expectNull("oversized ttl", outputReport(strength = 128, durationMs = 120, ttlMs = 2001))
    expectNull("unsupported flags", valid.copyWith(7, 0x01))
    expectNull("reserved byte", valid.copyWith(8, 0x01))
}

private fun expectNull(label: String, reportBytes: ByteArray) {
    val command = WindowsOutputReportMapper.toHapticCommand(
        reportBytes = reportBytes,
        commandId = "bad-$label",
    )
    expectEquals(label, null, command)
}

private fun outputReport(
    strength: Int,
    durationMs: Int,
    ttlMs: Int,
): ByteArray =
    byteArrayOf(
        WINDOWS_OUTPUT_REPORT_ID.toByte(),
        WINDOWS_OUTPUT_REPORT_VERSION.toByte(),
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
