package com.btgun.host.model

fun main() {
    streamSequencerUsesIndependentCounters()
    liveEnvelopeUsesElapsedNanosOnlyAndRejectsImpossibleTiming()
    gunInputStateTracksSimultaneousPressedControls()
    gunInputStateTracksCompositeStickAxis()
}

private fun streamSequencerUsesIndependentCounters() {
    val sequencer = StreamSequencer()

    expectEquals("gun seq 1", 1L, sequencer.next(StreamKind.GUN))
    expectEquals("gun seq 2", 2L, sequencer.next(StreamKind.GUN))
    expectEquals("motion seq 1", 1L, sequencer.next(StreamKind.MOTION))
    expectEquals("status seq 1", 1L, sequencer.next(StreamKind.STATUS))
    expectEquals("motion seq 2", 2L, sequencer.next(StreamKind.MOTION))
}

private fun liveEnvelopeUsesElapsedNanosOnlyAndRejectsImpossibleTiming() {
    val envelope = LiveEnvelope(
        stream = StreamKind.GUN,
        seq = 1L,
        captureElapsedNanos = 100L,
        emittedElapsedNanos = 150L,
        payload = GunEvent(name = "reload", pressed = true),
    )

    expectEquals("stream", StreamKind.GUN, envelope.stream)
    expectEquals("capture elapsed", 100L, envelope.captureElapsedNanos)
    expectEquals("emitted elapsed", 150L, envelope.emittedElapsedNanos)
    expectTrue(
        "no wall-clock fields",
        LiveEnvelope::class.java.declaredFields.none { field -> field.name.contains("wall", ignoreCase = true) },
    )

    expectThrows("emitted before capture rejected") {
        LiveEnvelope(
            stream = StreamKind.STATUS,
            seq = 1L,
            captureElapsedNanos = 200L,
            emittedElapsedNanos = 199L,
            payload = StatusEvent(name = "bad_time"),
        )
    }
}

private fun gunInputStateTracksSimultaneousPressedControls() {
    val state = GunInputState()
        .apply(GunEvent(name = "button_y", pressed = true))
        .apply(GunEvent(name = "trigger", pressed = true))
        .apply(GunEvent(name = "button_x", pressed = true))
        .apply(GunEvent(name = "button_y", pressed = false))

    expectEquals("pressed set", setOf("trigger", "button_x"), state.pressedControls)
    expectEquals("display order", listOf("trigger", "button_x"), state.activeControls())
}

private fun gunInputStateTracksCompositeStickAxis() {
    val diagonal = GunInputState()
        .apply(GunEvent(name = "stick", axisX = 1f, axisY = -1f))

    expectEquals("stick x", 1f, diagonal.stickAxisX)
    expectEquals("stick y", -1f, diagonal.stickAxisY)
    expectEquals("stick active", listOf("stick"), diagonal.activeControls())

    val neutral = diagonal.apply(GunEvent(name = "stick", axisX = 0f, axisY = 0f))
    expectEquals("stick neutral x", 0f, neutral.stickAxisX)
    expectEquals("stick neutral y", 0f, neutral.stickAxisY)
    expectEquals("stick hidden when neutral", emptyList<String>(), neutral.activeControls())
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}

private fun expectThrows(label: String, block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalArgumentException) {
        return
    }
    throw AssertionError("$label expected IllegalArgumentException")
}
