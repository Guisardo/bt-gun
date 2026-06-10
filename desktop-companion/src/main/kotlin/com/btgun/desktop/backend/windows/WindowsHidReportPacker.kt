package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.SemanticControllerState
import kotlin.math.roundToInt

const val WINDOWS_INPUT_REPORT_ID = 0x01
const val WINDOWS_INPUT_REPORT_LENGTH_BYTES = 10

data class WindowsInputReport(
    val bytes: ByteArray,
    val stale: Boolean,
    val sourceSequence: Long?,
) {
    init {
        require(bytes.size == WINDOWS_INPUT_REPORT_LENGTH_BYTES) {
            "Windows input report must be $WINDOWS_INPUT_REPORT_LENGTH_BYTES bytes"
        }
        require(bytes[0] == WINDOWS_INPUT_REPORT_ID.toByte()) {
            "Windows input report id must be $WINDOWS_INPUT_REPORT_ID"
        }
    }
}

object WindowsHidReportPacker {
    fun packInputReport(state: SemanticControllerState): WindowsInputReport {
        val report = ByteArray(WINDOWS_INPUT_REPORT_LENGTH_BYTES)
        report[0] = WINDOWS_INPUT_REPORT_ID.toByte()
        report[1] = if (state.stale) 0 else state.buttonBits().toByte()
        report.writeInt16Le(offset = 2, value = if (state.stale) 0 else state.stickX.clampSignedInt16())
        report.writeInt16Le(offset = 4, value = if (state.stale) 0 else state.stickY.clampSignedInt16())
        report.writeInt16Le(offset = 6, value = state.aimX.toSignedInt16Axis())
        report.writeInt16Le(offset = 8, value = state.aimY.toSignedInt16Axis())

        return WindowsInputReport(
            bytes = report,
            stale = state.stale,
            sourceSequence = state.sourceSequence,
        )
    }

    private fun SemanticControllerState.buttonBits(): Int {
        var bits = 0
        if (trigger) bits = bits or (1 shl 0)
        if (reload) bits = bits or (1 shl 1)
        if (x) bits = bits or (1 shl 2)
        if (y) bits = bits or (1 shl 3)
        if (a) bits = bits or (1 shl 4)
        if (b) bits = bits or (1 shl 5)
        return bits
    }

    private fun Int.clampSignedInt16(): Int =
        coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    private fun Float.toSignedInt16Axis(): Int {
        if (isNaN()) return 0
        val bounded = coerceIn(-1.0f, 1.0f)
        return when {
            bounded >= 1.0f -> Short.MAX_VALUE.toInt()
            bounded <= -1.0f -> Short.MIN_VALUE.toInt()
            bounded < 0.0f -> (bounded * -Short.MIN_VALUE.toInt()).roundToInt()
            else -> (bounded * Short.MAX_VALUE.toInt()).roundToInt()
        }.clampSignedInt16()
    }

    private fun ByteArray.writeInt16Le(offset: Int, value: Int) {
        val clamped = value.clampSignedInt16()
        this[offset] = (clamped and 0xff).toByte()
        this[offset + 1] = ((clamped ushr 8) and 0xff).toByte()
    }
}
