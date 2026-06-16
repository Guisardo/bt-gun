package com.btgun.host.recenter

import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.StatusEvent
import com.btgun.host.model.StreamKind

fun main() {
    reloadDownEmitsImmediatelyAndHoldEmitsOneRecenter()
    sameHoldDoesNotEmitCalibrationStartAfterRecenter()
    earlyReloadReleaseDoesNotEmitRecenter()
    extraLongHoldDoesNotEmitCalibrationStart()
    lateTickCanEmitOnlyRecenter()
    secondHoldCanEmitAfterPreviousRelease()
    duplicateReloadDownDoesNotResetHoldTimers()
    repeatedTicksAfterRecenterDoNotDuplicate()
    eventOrderPreservesReloadSemantics()
}

private fun reloadDownEmitsImmediatelyAndHoldEmitsOneRecenter() {
    val recenter = ReloadHoldRecenter()

    val down = recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("reload down")
    expectEquals("down stream", StreamKind.GUN, down.stream)
    expectEquals("down name", "reload", down.payload.name)
    expectEquals("down pressed", true, down.payload.pressed)
    expectEquals("down capture", 1_000_000_000L, down.captureElapsedNanos)

    expectEmpty("tick before threshold", recenter.onTick(nowElapsedNanos = 2_999_999_999L))

    val emitted = recenter.onTick(nowElapsedNanos = 3_000_000_000L).singleStatus("recenter threshold")
    expectEquals("recenter stream", StreamKind.STATUS, emitted.stream)
    expectEquals("recenter event name", "recenter", emitted.payload.name)
    expectEquals("recenter label", "recenter emitted", emitted.payload.message)
    expectEquals("recenter capture", 3_000_000_000L, emitted.captureElapsedNanos)
    expectEquals("recenter baseline", 3_000_000_000L, emitted.payload.recenterEvent.baselineElapsedNanos)
    expectEquals("recenter status label", "recenter emitted", emitted.payload.recenterEvent.statusLabel)

    val up = recenter.onReload(pressed = false, nowElapsedNanos = 3_100_000_000L).singleGun("reload up")
    expectEquals("up name", "reload", up.payload.name)
    expectEquals("up pressed", false, up.payload.pressed)
    expectEquals("up capture", 3_100_000_000L, up.captureElapsedNanos)
}

private fun sameHoldDoesNotEmitCalibrationStartAfterRecenter() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("down")
    recenter.onTick(nowElapsedNanos = 3_000_000_000L).singleStatus("recenter")

    expectEmpty("no calibration from extra-long hold", recenter.onTick(nowElapsedNanos = 11_000_000_000L))
}

private fun earlyReloadReleaseDoesNotEmitRecenter() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("early down")
    expectEmpty("early tick", recenter.onTick(nowElapsedNanos = 2_500_000_000L))

    val up = recenter.onReload(pressed = false, nowElapsedNanos = 2_700_000_000L).singleGun("early up")
    expectEquals("early up emitted", false, up.payload.pressed)
    expectEmpty("post-release tick cannot recenter", recenter.onTick(nowElapsedNanos = 3_000_000_000L))
}

private fun extraLongHoldDoesNotEmitCalibrationStart() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("down")
    recenter.onTick(nowElapsedNanos = 3_000_000_000L).singleStatus("recenter")

    expectEmpty("ten second tick has no calibration", recenter.onTick(nowElapsedNanos = 11_000_000_000L))
    val up = recenter.onReload(pressed = false, nowElapsedNanos = 12_000_000_000L).singleGun("up")
    expectEquals("up still emitted", false, up.payload.pressed)
}

private fun lateTickCanEmitOnlyRecenter() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("down")
    val emitted = recenter.onTick(nowElapsedNanos = 11_000_000_000L)
    expectEquals("late tick emits recenter only", 1, emitted.size)
    expectEquals("late first", "recenter", emitted[0].payload.name)
    recenter.onReload(pressed = false, nowElapsedNanos = 11_100_000_000L).singleGun("up")

    expectEmpty("post-release duplicate recenter", recenter.onTick(nowElapsedNanos = 12_000_000_000L))
}

private fun secondHoldCanEmitAfterPreviousRelease() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("first down")
    recenter.onTick(nowElapsedNanos = 3_000_000_000L).singleStatus("first recenter")
    recenter.onReload(pressed = false, nowElapsedNanos = 3_100_000_000L).singleGun("first up")

    recenter.onReload(pressed = true, nowElapsedNanos = 4_000_000_000L).singleGun("second down")
    val second = recenter.onTick(nowElapsedNanos = 6_000_000_000L).singleStatus("second recenter")
    expectEquals("second baseline", 6_000_000_000L, second.payload.recenterEvent.baselineElapsedNanos)
    expectEquals("second label", "recenter emitted", second.payload.recenterEvent.statusLabel)
}

private fun duplicateReloadDownDoesNotResetHoldTimers() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("down")
    recenter.onReload(pressed = true, nowElapsedNanos = 2_000_000_000L).singleGun("duplicate down")

    val emitted = recenter.onTick(nowElapsedNanos = 3_000_000_000L).singleStatus("timer from first down")
    expectEquals("duplicate did not reset baseline", 3_000_000_000L, emitted.payload.recenterEvent.baselineElapsedNanos)
}

private fun repeatedTicksAfterRecenterDoNotDuplicate() {
    val recenter = ReloadHoldRecenter()

    recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).singleGun("down")
    recenter.onTick(nowElapsedNanos = 3_000_000_000L).singleStatus("first threshold")

    expectEmpty("duplicate same tick", recenter.onTick(nowElapsedNanos = 3_000_000_000L))
    expectEmpty("duplicate later tick", recenter.onTick(nowElapsedNanos = 5_000_000_000L))
    expectEmpty("extra-long hold still no event", recenter.onTick(nowElapsedNanos = 11_000_000_000L))
}

private fun eventOrderPreservesReloadSemantics() {
    val recenter = ReloadHoldRecenter()

    val events = buildList {
        addAll(recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L).map { it.payload.nameWithState() })
        addAll(recenter.onTick(nowElapsedNanos = 3_000_000_000L).map { it.payload.message.orEmpty() })
        addAll(recenter.onReload(pressed = false, nowElapsedNanos = 3_100_000_000L).map { it.payload.nameWithState() })
    }

    expectEquals("event order", listOf("reload down", "recenter emitted", "reload up"), events)
}

private fun List<LiveEnvelope<GunEvent>>.singleGun(label: String): LiveEnvelope<GunEvent> {
    expectEquals("$label event count", 1, size)
    return single()
}

private fun List<LiveEnvelope<StatusEvent>>.singleStatus(label: String): LiveEnvelope<StatusEvent> {
    expectEquals("$label event count", 1, size)
    return single()
}

private fun GunEvent.nameWithState(): String =
    "$name ${if (pressed == true) "down" else "up"}"

private fun expectEmpty(label: String, values: List<Any>) {
    if (values.isNotEmpty()) {
        throw AssertionError("$label expected empty but was <$values>")
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
