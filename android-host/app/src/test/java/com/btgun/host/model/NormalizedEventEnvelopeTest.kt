package com.btgun.host.model

fun main() {
    streamSequencerUsesIndependentCounters()
    liveEnvelopeUsesElapsedNanosOnlyAndRejectsImpossibleTiming()
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
