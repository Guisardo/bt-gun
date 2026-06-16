package com.btgun.host.hid

import com.btgun.host.haptics.DesktopHapticCommand

sealed interface BtGunHidOutputReportResult {
    data class Valid(val command: DesktopHapticCommand) : BtGunHidOutputReportResult
    data class Invalid(val reason: String) : BtGunHidOutputReportResult
}

object BtGunHidOutputReportMapper {
    fun toHapticCommand(
        reportId: Int,
        payload: ByteArray,
        commandId: String,
    ): BtGunHidOutputReportResult {
        if (commandId.isBlank()) return invalid("blank command id")
        if (reportId != BtGunHidDescriptor.OUTPUT_REPORT_ID) return invalid("unexpected output report id")
        if (payload.size != BtGunHidDescriptor.OUTPUT_REPORT_PAYLOAD_LENGTH_BYTES) return invalid("unexpected output payload length")
        if (payload[0].toUnsignedInt() != BtGunHidDescriptor.OUTPUT_REPORT_VERSION) return invalid("unexpected output report version")

        val strength = payload[1].toUnsignedInt() / 255.0
        val durationMs = payload.readUInt16Le(offset = 2).toLong()
        val ttlMs = payload.readUInt16Le(offset = 4).toLong()
        val flags = payload[6].toUnsignedInt()
        val reserved = payload[7].toUnsignedInt()

        if (durationMs !in 1L..MAX_DURATION_MS) return invalid("invalid durationMs")
        if (ttlMs !in 1L..MAX_TTL_MS) return invalid("invalid ttlMs")
        if (flags != 0) return invalid("unsupported flags")
        if (reserved != 0) return invalid("reserved byte must be zero")

        return BtGunHidOutputReportResult.Valid(
            DesktopHapticCommand(
                commandId = commandId,
                strength = strength,
                durationMs = durationMs,
                ttlMs = ttlMs,
                pattern = null,
            ),
        )
    }

    private fun invalid(reason: String): BtGunHidOutputReportResult.Invalid =
        BtGunHidOutputReportResult.Invalid(reason)

    private fun ByteArray.readUInt16Le(offset: Int): Int =
        this[offset].toUnsignedInt() or (this[offset + 1].toUnsignedInt() shl 8)

    private fun Byte.toUnsignedInt(): Int =
        toInt() and 0xff

    private const val MAX_DURATION_MS = 1_000L
    private const val MAX_TTL_MS = 2_000L
}
