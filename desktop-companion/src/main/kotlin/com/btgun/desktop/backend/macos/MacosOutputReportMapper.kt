package com.btgun.desktop.backend.macos

import com.btgun.desktop.haptics.HapticCommand

const val MACOS_OUTPUT_REPORT_ID = 0x02
const val MACOS_OUTPUT_REPORT_VERSION = 0x01
const val MACOS_OUTPUT_REPORT_LENGTH_BYTES = 9

object MacosOutputReportMapper {
    fun toHapticCommand(reportBytes: ByteArray, commandId: String): HapticCommand? {
        if (commandId.isBlank()) return null
        if (reportBytes.size != MACOS_OUTPUT_REPORT_LENGTH_BYTES) return null
        if (reportBytes[0].toUnsignedInt() != MACOS_OUTPUT_REPORT_ID) return null
        if (reportBytes[1].toUnsignedInt() != MACOS_OUTPUT_REPORT_VERSION) return null

        val strength = reportBytes[2].toUnsignedInt() / 255.0
        val durationMs = reportBytes.readUInt16Le(offset = 3).toLong()
        val ttlMs = reportBytes.readUInt16Le(offset = 5).toLong()
        val flags = reportBytes[7].toUnsignedInt()
        val reserved = reportBytes[8].toUnsignedInt()

        if (durationMs !in 1L..1_000L) return null
        if (ttlMs !in 1L..2_000L) return null
        if (flags != 0 || reserved != 0) return null

        return HapticCommand(
            commandId = commandId,
            strength = strength,
            durationMs = durationMs,
            ttlMs = ttlMs,
            pattern = null,
        )
    }

    private fun ByteArray.readUInt16Le(offset: Int): Int =
        this[offset].toUnsignedInt() or (this[offset + 1].toUnsignedInt() shl 8)

    private fun Byte.toUnsignedInt(): Int =
        toInt() and 0xff
}
