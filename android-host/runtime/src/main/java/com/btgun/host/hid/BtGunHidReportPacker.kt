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
    val profile: BtGunHidProfile = BtGunHidProfiles.CURRENT_USER,
) {
    init {
        require(reportId == profile.inputReportId) {
            "BT Gun HID input report id must be ${profile.inputReportId}"
        }
        require(bytes.size == profile.inputReportPayloadLengthBytes) {
            "BT Gun HID input payload must be ${profile.inputReportPayloadLengthBytes} bytes"
        }
    }
}

object BtGunHidReportPacker {
    fun neutralInputReport(profile: BtGunHidProfile = BtGunHidProfiles.CURRENT_USER): BtGunHidInputReport {
        val report = ByteArray(profile.inputReportPayloadLengthBytes)
        if (profile.id == BtGunHidProfiles.BORING_STANDARD_ID) {
            report[2] = BtGunHidDescriptor.BORING_STANDARD_HAT_NEUTRAL.toByte()
        }
        return BtGunHidInputReport(
            reportId = profile.inputReportId,
            bytes = report,
            stale = true,
            aimSource = "neutral",
            profile = profile,
        )
    }

    fun packInputReport(
        state: GunInputState,
        motion: MotionSample?,
        stale: Boolean,
        profile: BtGunHidProfile = BtGunHidProfiles.CURRENT_USER,
    ): BtGunHidInputReport {
        val aim = motion.selectAim()
        return packControlsAndAxes(
            pressedControls = state.pressedControls,
            stickAxisX = if (stale) 0f else state.stickAxisX,
            stickAxisY = if (stale) 0f else state.stickAxisY,
            aimAxisX = aim.x,
            aimAxisY = aim.y,
            stale = stale,
            aimSource = aim.source,
            profile = profile,
        )
    }

    fun packInputReport(
        mappedState: MappedControllerState,
        stale: Boolean,
        profile: BtGunHidProfile = BtGunHidProfiles.CURRENT_USER,
    ): BtGunHidInputReport =
        packControlsAndAxes(
            pressedControls = mappedState.pressedVirtualControls,
            stickAxisX = if (stale) 0f else mappedState.stickAxisX,
            stickAxisY = if (stale) 0f else mappedState.stickAxisY,
            aimAxisX = mappedState.aimAxisX,
            aimAxisY = mappedState.aimAxisY,
            stale = stale,
            aimSource = mappedState.aimStatus.aimSource,
            profile = profile,
        )

    private fun packControlsAndAxes(
        pressedControls: Set<String>,
        stickAxisX: Float,
        stickAxisY: Float,
        aimAxisX: Float,
        aimAxisY: Float,
        stale: Boolean,
        aimSource: String,
        profile: BtGunHidProfile,
    ): BtGunHidInputReport {
        val report = ByteArray(profile.inputReportPayloadLengthBytes)
        if (profile.id == BtGunHidProfiles.BORING_STANDARD_ID) {
            report.writeButtonBits16(if (stale) 0 else boringStandardButtonBits(pressedControls))
            report[2] = if (stale) {
                BtGunHidDescriptor.BORING_STANDARD_HAT_NEUTRAL.toByte()
            } else {
                boringStandardHat(pressedControls).toByte()
            }
        } else {
            report.writeButtonBits24(if (stale) 0 else currentUserButtonBits(pressedControls))
        }
        report.writeInt16Le(offset = 3, value = stickAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 5, value = (-stickAxisY).toSignedInt16Axis())
        report.writeInt16Le(offset = 7, value = aimAxisX.toSignedInt16Axis())
        report.writeInt16Le(offset = 9, value = (-aimAxisY).toSignedInt16Axis())

        return BtGunHidInputReport(
            reportId = profile.inputReportId,
            bytes = report,
            stale = stale,
            aimSource = aimSource,
            profile = profile,
        )
    }

    private fun currentUserButtonBits(pressedControls: Set<String>): Int =
        VirtualButton.bitmaskForControlIds(pressedControls)

    private fun boringStandardButtonBits(pressedControls: Set<String>): Int {
        var bits = 0
        pressedControls.forEach { controlId ->
            val button = VirtualButton.fromId(controlId) ?: return@forEach
            if (button.bitIndex in 0 until BtGunHidDescriptor.BORING_STANDARD_BUTTON_COUNT) {
                bits = bits or button.bitMask
            }
        }
        return bits
    }

    private fun boringStandardHat(pressedControls: Set<String>): Int {
        val buttons = pressedControls.mapNotNull(VirtualButton::fromId).toSet()
        val vertical = when {
            VirtualButton.DU in buttons && VirtualButton.DD !in buttons -> -1
            VirtualButton.DD in buttons && VirtualButton.DU !in buttons -> 1
            else -> 0
        }
        val horizontal = when {
            VirtualButton.DL in buttons && VirtualButton.DR !in buttons -> -1
            VirtualButton.DR in buttons && VirtualButton.DL !in buttons -> 1
            else -> 0
        }
        return when {
            vertical == -1 && horizontal == 0 -> 0
            vertical == -1 && horizontal == 1 -> 1
            vertical == 0 && horizontal == 1 -> 2
            vertical == 1 && horizontal == 1 -> 3
            vertical == 1 && horizontal == 0 -> 4
            vertical == 1 && horizontal == -1 -> 5
            vertical == 0 && horizontal == -1 -> 6
            vertical == -1 && horizontal == -1 -> 7
            else -> BtGunHidDescriptor.BORING_STANDARD_HAT_NEUTRAL
        }
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

    private fun ByteArray.writeButtonBits16(bits: Int) {
        this[0] = (bits and 0xff).toByte()
        this[1] = ((bits ushr 8) and 0xff).toByte()
    }

    private fun ByteArray.writeButtonBits24(bits: Int) {
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
