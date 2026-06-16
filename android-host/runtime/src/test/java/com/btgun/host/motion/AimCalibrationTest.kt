package com.btgun.host.motion

import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample

fun main() {
    skewedCalibrationMapsCornersCenterAndInterior()
    invertedAxesMapToTargetMarks()
    rawAimTrackerUnwrapsAcrossAngleBoundary()
    fallbackAimInvertsPitchForGraphY()
    degenerateCalibrationRejectsBadPoints()
    centeredCalibrationSurvivesOriginRecenter()
    calibrationCodecRoundTripsAndIgnoresBadPayloads()
    sessionCapturesTriggerOrderAndPreservesLastGoodOnFailure()
}

private fun skewedCalibrationMapsCornersCenterAndInterior() {
    val captured = listOf(
        capture(AimCalibrationMark.TOP_LEFT, -24f, 17f),
        capture(AimCalibrationMark.TOP_RIGHT, 31f, 19f),
        capture(AimCalibrationMark.BOTTOM_LEFT, -27f, -22f),
        capture(AimCalibrationMark.BOTTOM_RIGHT, 29f, -24f),
    )

    val result = AimCalibrationSolver.buildFromCaptured("game_rotation_vector", captured, 123L).valid()
    AimCalibrationMark.captureOrder.forEach { mark ->
        val centerRelative = captured.first { point -> point.mark == mark }.rawPoint - result.rawCenter
        expectNear("${mark.wireName} x", mark.target.x, result.calibration.map(centerRelative).x)
        expectNear("${mark.wireName} y", mark.target.y, result.calibration.map(centerRelative).y)
    }
    expectNear("center x", 0f, result.calibration.map(RawAimPoint(0f, 0f)).x)
    expectNear("center y", 0f, result.calibration.map(RawAimPoint(0f, 0f)).y)

    val target = NormalizedAimPoint(0.25f, -0.4f)
    val rawInterior = result.calibration.homography.inverse()?.project(target) ?: throw AssertionError("missing inverse")
    val mapped = result.calibration.map(RawAimPoint(rawInterior.x, rawInterior.y))
    expectNear("interior x", target.x, mapped.x)
    expectNear("interior y", target.y, mapped.y)
}

private fun invertedAxesMapToTargetMarks() {
    val result = AimCalibrationSolver.buildFromCaptured(
        providerName = "rotation_vector",
        capturedPoints = listOf(
            capture(AimCalibrationMark.TOP_LEFT, 20f, -10f),
            capture(AimCalibrationMark.TOP_RIGHT, -20f, -10f),
            capture(AimCalibrationMark.BOTTOM_LEFT, 20f, 10f),
            capture(AimCalibrationMark.BOTTOM_RIGHT, -20f, 10f),
        ),
        createdAtEpochMillis = 456L,
    ).valid()

    expectNear("inverted left x", -1f, result.calibration.map(RawAimPoint(20f, -10f) - result.rawCenter).x)
    expectNear("inverted right x", 1f, result.calibration.map(RawAimPoint(-20f, -10f) - result.rawCenter).x)
}

private fun rawAimTrackerUnwrapsAcrossAngleBoundary() {
    val tracker = RawAimTracker()
    val first = tracker.track(motion(yaw = 179f, pitch = 5f))
    val second = tracker.track(motion(yaw = -179f, pitch = 6f))
    val third = tracker.track(motion(yaw = 178f, pitch = 7f))

    expectNear("first yaw", 179f, first.xDegrees)
    expectNear("forward unwrap", 181f, second.xDegrees)
    expectNear("backward unwrap", 178f, third.xDegrees)
}

private fun fallbackAimInvertsPitchForGraphY() {
    expectNear("positive pitch moves graph down", -1f, fallbackAim(RawAimPoint(0f, 45f)).y)
    expectNear("negative pitch moves graph up", 1f, fallbackAim(RawAimPoint(0f, -45f)).y)
}

private fun degenerateCalibrationRejectsBadPoints() {
    val duplicate = AimCalibrationSolver.buildFromCaptured(
        "game_rotation_vector",
        listOf(
            capture(AimCalibrationMark.TOP_LEFT, 0f, 0f),
            capture(AimCalibrationMark.TOP_RIGHT, 1f, 1f),
            capture(AimCalibrationMark.BOTTOM_LEFT, -20f, -20f),
            capture(AimCalibrationMark.BOTTOM_RIGHT, 20f, -20f),
        ),
        1L,
    )
    expectInvalid("duplicate", duplicate)

    val crossed = AimCalibrationSolver.buildFromCaptured(
        "game_rotation_vector",
        listOf(
            capture(AimCalibrationMark.TOP_LEFT, -20f, 20f),
            capture(AimCalibrationMark.TOP_RIGHT, -20f, -20f),
            capture(AimCalibrationMark.BOTTOM_LEFT, 20f, -20f),
            capture(AimCalibrationMark.BOTTOM_RIGHT, 20f, 20f),
        ),
        1L,
    )
    expectInvalid("crossed", crossed)

    val small = AimCalibrationSolver.buildFromCaptured(
        "game_rotation_vector",
        listOf(
            capture(AimCalibrationMark.TOP_LEFT, -2f, 2f),
            capture(AimCalibrationMark.TOP_RIGHT, 2f, 2f),
            capture(AimCalibrationMark.BOTTOM_LEFT, -2f, -2f),
            capture(AimCalibrationMark.BOTTOM_RIGHT, 2f, -2f),
        ),
        1L,
    )
    expectInvalid("small area", small)
}

private fun centeredCalibrationSurvivesOriginRecenter() {
    val calibration = AimCalibrationSolver.buildFromCentered(
        providerName = "game_rotation_vector",
        centeredPoints = mapOf(
            AimCalibrationMark.TOP_LEFT to RawAimPoint(-25f, 20f),
            AimCalibrationMark.TOP_RIGHT to RawAimPoint(25f, 20f),
            AimCalibrationMark.BOTTOM_LEFT to RawAimPoint(-25f, -20f),
            AimCalibrationMark.BOTTOM_RIGHT to RawAimPoint(25f, -20f),
        ),
        createdAtEpochMillis = 123L,
    ).valid().calibration

    val firstOrigin = RawAimPoint(100f, 50f)
    val secondOrigin = RawAimPoint(-30f, 80f)
    val physicalRelative = RawAimPoint(25f, 20f)
    val firstMapped = calibration.map(firstOrigin + physicalRelative - firstOrigin)
    val secondMapped = calibration.map(secondOrigin + physicalRelative - secondOrigin)
    expectNear("recenter preserves x", firstMapped.x, secondMapped.x)
    expectNear("recenter preserves y", firstMapped.y, secondMapped.y)
}

private fun calibrationCodecRoundTripsAndIgnoresBadPayloads() {
    val calibration = AimCalibrationSolver.buildFromCentered(
        providerName = "game_rotation_vector",
        centeredPoints = mapOf(
            AimCalibrationMark.TOP_LEFT to RawAimPoint(-20f, 20f),
            AimCalibrationMark.TOP_RIGHT to RawAimPoint(20f, 20f),
            AimCalibrationMark.BOTTOM_LEFT to RawAimPoint(-20f, -20f),
            AimCalibrationMark.BOTTOM_RIGHT to RawAimPoint(20f, -20f),
        ),
        createdAtEpochMillis = 999L,
    ).valid().calibration

    val decoded = AimCalibrationCodec.decode(AimCalibrationCodec.encode(calibration)) ?: throw AssertionError("decode")
    expectEquals("provider", calibration.providerName, decoded.providerName)
    expectNear("round-trip map", 1f, decoded.map(RawAimPoint(20f, 20f)).x)

    expectEquals("old version ignored", null, AimCalibrationCodec.decode("v0|bad"))
    expectEquals("corrupt ignored", null, AimCalibrationCodec.decode("v1|provider|oops|1,2|3,4|5,6|7,8"))
    expectEquals(
        "invalid stored ignored",
        null,
        AimCalibrationCodec.decode("v1|provider|1|0,0|0,0|0,0|0,0"),
    )
}

private fun sessionCapturesTriggerOrderAndPreservesLastGoodOnFailure() {
    val session = AimCalibrationSession()
    val good = AimCalibrationSolver.buildFromCentered(
        providerName = "game_rotation_vector",
        centeredPoints = mapOf(
            AimCalibrationMark.TOP_LEFT to RawAimPoint(-20f, 20f),
            AimCalibrationMark.TOP_RIGHT to RawAimPoint(20f, 20f),
            AimCalibrationMark.BOTTOM_LEFT to RawAimPoint(-20f, -20f),
            AimCalibrationMark.BOTTOM_RIGHT to RawAimPoint(20f, -20f),
        ),
        createdAtEpochMillis = 1L,
    ).valid().calibration
    session.setActiveCalibration(good)
    session.start()

    session.capture("game_rotation_vector", RawAimPoint(0f, 0f), 1L, 1L)
    expectEquals("next mark", AimCalibrationMark.TOP_RIGHT, session.state.activeMark)
    session.capture("game_rotation_vector", RawAimPoint(0f, 0f), 2L, 1L)
    session.capture("game_rotation_vector", RawAimPoint(0f, 0f), 3L, 1L)
    val failed = session.capture("game_rotation_vector", RawAimPoint(0f, 0f), 4L, 1L)

    expectTrue("failed outcome", failed is AimCalibrationCaptureOutcome.Failed)
    expectEquals("restart top left", AimCalibrationMark.TOP_LEFT, session.state.activeMark)
    expectEquals("last good kept", good, session.state.activeCalibration)
    expectEquals("bad captures cleared", 0, session.state.capturedPoints.size)
}

private fun capture(mark: AimCalibrationMark, x: Float, y: Float): CapturedAimPoint =
    CapturedAimPoint(mark, RawAimPoint(x, y), elapsedRealtimeNanos = 10L)

private fun motion(yaw: Float, pitch: Float): MotionSample =
    MotionSample(
        provider = MotionProvider.GAME_ROTATION_VECTOR,
        sourceSensorElapsedNanos = 1L,
        yaw = yaw,
        pitch = pitch,
        roll = 0f,
    )

private fun AimCalibrationBuildResult.valid(): AimCalibrationBuildResult.Valid =
    this as? AimCalibrationBuildResult.Valid ?: throw AssertionError("expected valid but was $this")

private fun expectInvalid(label: String, result: AimCalibrationBuildResult) {
    if (result !is AimCalibrationBuildResult.Invalid) {
        throw AssertionError("$label expected invalid but was <$result>")
    }
}

private fun expectNear(label: String, expected: Float, actual: Float, tolerance: Float = 0.05f) {
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
