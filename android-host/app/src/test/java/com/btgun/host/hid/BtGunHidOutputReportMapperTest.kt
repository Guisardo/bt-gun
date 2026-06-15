package com.btgun.host.hid

fun main() {
    mapsValidOutputReportToDesktopHapticCommand()
    rejectsMalformedOutputReportsBeforeHapticCommand()
    neverAcceptsPatternOutputFromHidBytes()
}

private fun mapsValidOutputReportToDesktopHapticCommand() {
    val result = BtGunHidOutputReportMapper.toHapticCommand(
        reportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
        payload = outputPayload(strength = 255, durationMs = 120, ttlMs = 500),
        commandId = "hid-output-1",
    )
    val command = expectValid("valid output report", result)

    expectEquals("output report id", 0x02, BtGunHidDescriptor.OUTPUT_REPORT_ID)
    expectEquals("output report version", 0x01, BtGunHidDescriptor.OUTPUT_REPORT_VERSION)
    expectEquals("output payload length", 8, BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES)
    expectEquals("command id", "hid-output-1", command.commandId)
    expectClose("strength", 1.0, command.strength)
    expectEquals("duration", 120L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectEquals("pattern", null, command.pattern)
}

private fun rejectsMalformedOutputReportsBeforeHapticCommand() {
    val valid = outputPayload(strength = 128, durationMs = 120, ttlMs = 500)

    expectInvalid("bad report id", BtGunHidOutputReportMapper.toHapticCommand(0x01, valid, "bad-id"))
    expectInvalid(
        "bad length",
        BtGunHidOutputReportMapper.toHapticCommand(
            BtGunHidDescriptor.OUTPUT_REPORT_ID,
            valid.copyOf(BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES - 1),
            "bad-length",
        ),
    )
    expectInvalid(
        "bad version",
        BtGunHidOutputReportMapper.toHapticCommand(
            BtGunHidDescriptor.OUTPUT_REPORT_ID,
            valid.copyWith(0, 0x02),
            "bad-version",
        ),
    )
    expectInvalid("zero duration", mapPayload(outputPayload(strength = 128, durationMs = 0, ttlMs = 500), "zero-duration"))
    expectInvalid("zero ttl", mapPayload(outputPayload(strength = 128, durationMs = 120, ttlMs = 0), "zero-ttl"))
    expectInvalid(
        "oversized duration",
        mapPayload(outputPayload(strength = 128, durationMs = 1001, ttlMs = 500), "oversized-duration"),
    )
    expectInvalid(
        "oversized ttl",
        mapPayload(outputPayload(strength = 128, durationMs = 120, ttlMs = 2001), "oversized-ttl"),
    )
    expectInvalid("unsupported flags", mapPayload(valid.copyWith(6, 0x01), "unsupported-flags"))
    expectInvalid("reserved byte", mapPayload(valid.copyWith(7, 0x01), "reserved-byte"))
    expectInvalid("blank command id", mapPayload(valid, ""))
}

private fun neverAcceptsPatternOutputFromHidBytes() {
    val command = expectValid(
        "valid output report",
        BtGunHidOutputReportMapper.toHapticCommand(
            reportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
            payload = outputPayload(strength = 64, durationMs = 40, ttlMs = 100),
            commandId = "hid-output-pattern-check",
        ),
    )

    expectEquals("hid output bytes cannot request pattern", null, command.pattern)
}

private fun mapPayload(payload: ByteArray, commandId: String): BtGunHidOutputReportResult =
    BtGunHidOutputReportMapper.toHapticCommand(
        reportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
        payload = payload,
        commandId = commandId,
    )

private fun outputPayload(
    strength: Int,
    durationMs: Int,
    ttlMs: Int,
): ByteArray =
    byteArrayOf(
        BtGunHidDescriptor.OUTPUT_REPORT_VERSION.toByte(),
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

private fun expectValid(label: String, result: BtGunHidOutputReportResult): com.btgun.host.haptics.DesktopHapticCommand =
    when (result) {
        is BtGunHidOutputReportResult.Valid -> result.command
        is BtGunHidOutputReportResult.Invalid -> throw AssertionError("$label expected valid but was ${result.reason}")
    }

private fun expectInvalid(label: String, result: BtGunHidOutputReportResult) {
    when (result) {
        is BtGunHidOutputReportResult.Valid -> throw AssertionError("$label expected invalid but was valid")
        is BtGunHidOutputReportResult.Invalid -> expectFalse("$label reason not blank", result.reason.isBlank())
    }
}

private fun expectFalse(label: String, actual: Boolean) {
    expectEquals(label, false, actual)
}

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
