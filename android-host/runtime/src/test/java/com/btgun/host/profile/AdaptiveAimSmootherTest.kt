package com.btgun.host.profile

fun main() {
    offReturnsCurrentAimWithoutLag()
    deterministicModesUsePinnedTauValues()
    adaptiveUsesFastAndJitterTau()
    adaptiveFallsBackToLowWhenLatencyBudgetIsThreatened()
    outputIsFiniteClampedAndMonotonic()
}

private fun offReturnsCurrentAimWithoutLag() {
    val smoother = AdaptiveAimSmoother()

    val result = smoother.smooth(
        x = 0.75f,
        y = -0.5f,
        mode = SmoothingMode.OFF,
        sampleElapsedNanos = 1_000_000L,
        aimLatencyMillis = null,
    )

    expectNear("off x", 0.75f, result.x)
    expectNear("off y", -0.5f, result.y)
    expectEquals("off mode", "off", result.modeLabel)
    expectEquals("off lag", 0, result.estimatedFilterLagMillis)
    expectEquals("off fallback", false, result.adaptiveFallback)
}

private fun deterministicModesUsePinnedTauValues() {
    expectTau("low tau", SmoothingMode.LOW, expected = 0.5f)
    expectTau("balanced tau", SmoothingMode.BALANCED, expected = 1f / 3f)
    expectTau("high tau", SmoothingMode.HIGH, expected = 12f / 52f)
}

private fun adaptiveUsesFastAndJitterTau() {
    val fast = AdaptiveAimSmoother()
    fast.smooth(0f, 0f, SmoothingMode.ADAPTIVE, 0L, aimLatencyMillis = 4)
    val fastResult = fast.smooth(1f, 0f, SmoothingMode.ADAPTIVE, 16_000_000L, aimLatencyMillis = 4)

    val jitter = AdaptiveAimSmoother()
    jitter.smooth(0f, 0f, SmoothingMode.ADAPTIVE, 0L, aimLatencyMillis = 4)
    val jitterResult = jitter.smooth(0.02f, 0f, SmoothingMode.ADAPTIVE, 16_000_000L, aimLatencyMillis = 4)

    expectEquals("adaptive fast mode", "adaptive", fastResult.modeLabel)
    expectEquals("adaptive jitter mode", "adaptive", jitterResult.modeLabel)
    expectTrue("fast follows target more", fastResult.x > 0.65f)
    expectTrue("jitter damped more than fast tau would", jitterResult.x < 0.014f)
    expectTrue("jitter has larger estimated lag", jitterResult.estimatedFilterLagMillis > fastResult.estimatedFilterLagMillis)
}

private fun adaptiveFallsBackToLowWhenLatencyBudgetIsThreatened() {
    val smoother = AdaptiveAimSmoother()
    smoother.smooth(0f, 0f, SmoothingMode.ADAPTIVE, 0L, aimLatencyMillis = 4)

    val result = smoother.smooth(0.02f, 0f, SmoothingMode.ADAPTIVE, 4_000_000L, aimLatencyMillis = 35)

    expectEquals("fallback mode", "low", result.modeLabel)
    expectEquals("fallback flag", true, result.adaptiveFallback)
    expectTrue("fallback lag capped", result.estimatedFilterLagMillis <= AdaptiveAimSmoother.MAX_ADDED_FILTER_LAG_MS)
}

private fun outputIsFiniteClampedAndMonotonic() {
    val off = AdaptiveAimSmoother().smooth(
        x = Float.NaN,
        y = -2f,
        mode = SmoothingMode.OFF,
        sampleElapsedNanos = 1L,
        aimLatencyMillis = null,
    )
    expectEquals("nan becomes finite", true, off.x.isFinite())
    expectNear("clamped y", -1f, off.y)

    val smoother = AdaptiveAimSmoother()
    smoother.smooth(0f, 0f, SmoothingMode.LOW, 1_000_000L, aimLatencyMillis = null)
    val current = smoother.smooth(1f, 0f, SmoothingMode.LOW, 13_000_000L, aimLatencyMillis = null)
    val older = smoother.smooth(-1f, 0f, SmoothingMode.LOW, 12_000_000L, aimLatencyMillis = null)

    expectNear("older timestamp holds filtered x", current.x, older.x)
    expectEquals("older timestamp finite", true, older.x.isFinite())
}

private fun expectTau(label: String, mode: SmoothingMode, expected: Float) {
    val smoother = AdaptiveAimSmoother()
    smoother.smooth(0f, 0f, mode, 0L, aimLatencyMillis = null)

    val result = smoother.smooth(1f, 0f, mode, 12_000_000L, aimLatencyMillis = null)

    expectNear(label, expected, result.x, tolerance = 0.0005f)
    expectEquals("$label fallback", false, result.adaptiveFallback)
}

private fun expectNear(label: String, expected: Float, actual: Float, tolerance: Float = 0.0001f) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
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
