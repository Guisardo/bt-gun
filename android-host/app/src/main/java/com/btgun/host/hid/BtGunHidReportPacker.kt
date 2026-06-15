package com.btgun.host.hid

import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionSample
import com.btgun.host.profile.MappedControllerState
import com.btgun.host.profile.VirtualButton
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

        report.writeButtonBits(if (stale) 0 else state.faceButtonBits())
        report.writeInt16Le(offset = 3, value = if (stale) 0 else state.stickAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 5, value = if (stale) 0 else (-state.stickAxisY).toSignedInt16Axis())
        report.writeInt16Le(offset = 7, value = aim.x.toSignedInt16Axis())
        report.writeInt16Le(offset = 9, value = (-aim.y).toSignedInt16Axis())

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

        report.writeButtonBits(if (stale) 0 else faceButtonBits(mappedState.pressedVirtualControls))
        report.writeInt16Le(offset = 3, value = if (stale) 0 else mappedState.stickAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 5, value = if (stale) 0 else (-mappedState.stickAxisY).toSignedInt16Axis())
        report.writeInt16Le(offset = 7, value = mappedState.aimAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 9, value = (-mappedState.aimAxisY).toSignedInt16Axis())

        return BtGunHidInputReport(
            reportId = BtGunHidDescriptor.INPUT_REPORT_ID,
            bytes = report,
            stale = stale,
            aimSource = mappedState.aimStatus.aimSource,
        )
    }

    private fun GunInputState.faceButtonBits(): Int =
        faceButtonBits(pressedControls)

    private fun faceButtonBits(pressedControls: Set<String>): Int =
        VirtualButton.bitmaskForControlIds(pressedControls)

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

    private fun ByteArray.writeButtonBits(bits: Int) {
        this[0] = (bits and 0xff).toByte()
        this[1] = ((bits ushr 8) and 0xff).toByte()
        this[2] = ((bits ushr 16) and 0xff).toByte()
    }

    private data class SelectedAim(
        val x: Float,
        val y: Float,
        val source: String,
    )
}
