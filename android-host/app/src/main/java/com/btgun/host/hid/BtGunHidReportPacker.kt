package com.btgun.host.hid

import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionSample
import com.btgun.host.profile.MappedControllerState
import kotlin.math.roundToInt

data class BtGunHidInputReport(
    val reportId: Int,
    val bytes: ByteArray,
    val stale: Boolean,
    val aimSource: String,
) {
    init {
        require(reportId == BtGunHidDescriptor.INPUT_REPORT_ID) {
            "BT Gun HID input report id must be ${BtGunHidDescriptor.INPUT_REPORT_ID}"
        }
        require(bytes.size == BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES) {
            "BT Gun HID input payload must be ${BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES} bytes"
        }
    }
}

object BtGunHidReportPacker {
    fun neutralInputReport(): BtGunHidInputReport =
        BtGunHidInputReport(
            reportId = BtGunHidDescriptor.INPUT_REPORT_ID,
            bytes = ByteArray(BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES),
            stale = true,
            aimSource = "neutral",
        )

    fun packInputReport(
        state: GunInputState,
        motion: MotionSample?,
        stale: Boolean,
    ): BtGunHidInputReport {
        val report = ByteArray(BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES)
        val aim = motion.selectAim()

        report[0] = if (stale) 0 else state.faceButtonBits().toByte()
        report.writeInt16Le(offset = 1, value = if (stale) 0 else state.stickAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 3, value = if (stale) 0 else (-state.stickAxisY).toSignedInt16Axis())
        report.writeInt16Le(offset = 5, value = aim.x.toSignedInt16Axis())
        report.writeInt16Le(offset = 7, value = (-aim.y).toSignedInt16Axis())

        return BtGunHidInputReport(
            reportId = BtGunHidDescriptor.INPUT_REPORT_ID,
            bytes = report,
            stale = stale,
            aimSource = aim.source,
        )
    }

    fun packInputReport(
        mappedState: MappedControllerState,
        stale: Boolean,
    ): BtGunHidInputReport {
        val report = ByteArray(BtGunHidDescriptor.INPUT_REPORT_PAYLOAD_LENGTH_BYTES)

        report[0] = if (stale) 0 else faceButtonBits(mappedState.pressedVirtualControls).toByte()
        report.writeInt16Le(offset = 1, value = if (stale) 0 else mappedState.stickAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 3, value = if (stale) 0 else (-mappedState.stickAxisY).toSignedInt16Axis())
        report.writeInt16Le(offset = 5, value = mappedState.aimAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 7, value = (-mappedState.aimAxisY).toSignedInt16Axis())

        return BtGunHidInputReport(
            reportId = BtGunHidDescriptor.INPUT_REPORT_ID,
            bytes = report,
            stale = stale,
            aimSource = mappedState.aimStatus.aimSource,
        )
    }

    private fun GunInputState.faceButtonBits(): Int =
        faceButtonBits(pressedControls)

    private fun faceButtonBits(pressedControls: Set<String>): Int {
        var bits = 0
        pressedControls.forEach { control ->
            bits = bits or when (control) {
                "trigger" -> 1 shl 0
                "reload" -> 1 shl 1
                "button_x" -> 1 shl 2
                "button_y" -> 1 shl 3
                "button_a" -> 1 shl 4
                "button_b" -> 1 shl 5
                else -> 0
            }
        }
        return bits
    }

    private fun MotionSample?.selectAim(): SelectedAim =
        when {
            this?.aimX != null && aimY != null -> {
                val source = if (aimCalibrated) "calibrated" else "normalized"
                SelectedAim(aimX, aimY, source)
            }
            this?.rawAimX != null && rawAimY != null -> {
                SelectedAim(rawAimX, rawAimY, "raw")
            }
            else -> SelectedAim(0f, 0f, "center")
        }

    private fun Float.toSignedInt16Axis(): Int {
        if (isNaN()) return 0
        val bounded = coerceIn(-1.0f, 1.0f)
        return when {
            bounded >= 1.0f -> Short.MAX_VALUE.toInt()
            bounded <= -1.0f -> Short.MIN_VALUE.toInt()
            bounded < 0.0f -> (bounded * -Short.MIN_VALUE.toInt()).roundToInt()
            else -> (bounded * Short.MAX_VALUE.toInt()).roundToInt()
        }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    private fun ByteArray.writeInt16Le(offset: Int, value: Int) {
        val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        this[offset] = (clamped and 0xff).toByte()
        this[offset + 1] = ((clamped ushr 8) and 0xff).toByte()
    }

    private data class SelectedAim(
        val x: Float,
        val y: Float,
        val source: String,
    )
}
