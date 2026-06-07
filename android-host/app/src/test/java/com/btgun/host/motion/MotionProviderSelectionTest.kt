package com.btgun.host.motion

import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.StreamKind

fun main() {
    providerSelectionPrefersGameRotationVector()
    providerSelectionFallsBackToRotationVector()
    providerSelectionUsesGyroGravityBeforeTiltFallback()
    providerSelectionUsesTiltFallbackWithoutGyroscope()
    providerSelectionReportsUnavailableWithoutFakeAim()
    providerSelectionReportsCapabilityFlagsAndTimestampSource()
    motionSampleEnvelopeCarriesProviderAndCapabilityMetadata()
    previewMapperCentersBaselineAndBoundsDeltas()
    previewMapperDisablesPadWhenMotionUnavailable()
    previewMapperDoesNotExposeDesktopProfileMapping()
}

private fun providerSelectionPrefersGameRotationVector() {
    val selection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gameRotationVector = true,
            rotationVector = true,
            gyroscope = true,
            accelerometer = true,
            gravity = true,
        ),
    )

    expectEquals("game rotation provider", "game_rotation_vector", selection.providerName)
    expectEquals("game rotation enum", MotionProvider.GAME_ROTATION_VECTOR, selection.provider)
}

private fun providerSelectionFallsBackToRotationVector() {
    val selection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gameRotationVector = false,
            rotationVector = true,
            gyroscope = true,
            accelerometer = true,
            gravity = false,
        ),
    )

    expectEquals("rotation provider", "rotation_vector", selection.providerName)
    expectEquals("rotation enum", MotionProvider.ROTATION_VECTOR, selection.provider)
}

private fun providerSelectionUsesGyroGravityBeforeTiltFallback() {
    val gravitySelection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gyroscope = true,
            gravity = true,
        ),
    )
    expectEquals("gyro plus gravity provider", "gyro_gravity", gravitySelection.providerName)
    expectEquals("gyro plus gravity enum", MotionProvider.GYRO_GRAVITY, gravitySelection.provider)

    val accelerometerSelection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gyroscope = true,
            accelerometer = true,
        ),
    )
    expectEquals("gyro plus accelerometer provider", "gyro_gravity", accelerometerSelection.providerName)
}

private fun providerSelectionUsesTiltFallbackWithoutGyroscope() {
    val gravitySelection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gyroscope = false,
            gravity = true,
        ),
    )
    expectEquals("gravity tilt provider", "tilt_fallback", gravitySelection.providerName)
    expectEquals("gravity tilt enum", MotionProvider.TILT_FALLBACK, gravitySelection.provider)
    expectTrue("tilt fallback capability flag", gravitySelection.capabilities.tiltFallback)

    val accelerometerSelection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gyroscope = false,
            accelerometer = true,
        ),
    )
    expectEquals("accelerometer tilt provider", "tilt_fallback", accelerometerSelection.providerName)
}

private fun providerSelectionReportsUnavailableWithoutFakeAim() {
    val selection = MotionProviderSelection.choose(MotionCapabilityFlags())

    expectEquals("unavailable provider", "unavailable", selection.providerName)
    expectEquals("unavailable enum", MotionProvider.UNAVAILABLE, selection.provider)
    expectFalse("motion unavailable", selection.isAvailable)
    expectFalse("tilt fallback unavailable", selection.capabilities.tiltFallback)
}

private fun providerSelectionReportsCapabilityFlagsAndTimestampSource() {
    val selection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gameRotationVector = false,
            rotationVector = false,
            gyroscope = true,
            accelerometer = false,
            gravity = true,
        ),
    )

    expectFalse("game rotation flag", selection.capabilities.gameRotationVector)
    expectFalse("rotation flag", selection.capabilities.rotationVector)
    expectTrue("gyroscope flag", selection.capabilities.gyroscope)
    expectFalse("accelerometer flag", selection.capabilities.accelerometer)
    expectTrue("gravity flag", selection.capabilities.gravity)
    expectTrue("tilt flag exists", MotionCapabilityFlags::class.java.declaredFields.any { it.name == "tiltFallback" })
    expectEquals("timestamp source", "sensor_event_elapsed_nanos", selection.capabilities.timestampSource)
}

private fun motionSampleEnvelopeCarriesProviderAndCapabilityMetadata() {
    val selection = MotionProviderSelection.choose(
        MotionCapabilityFlags(
            gameRotationVector = true,
            rotationVector = true,
            gyroscope = true,
            accelerometer = true,
            gravity = true,
        ),
    )
    val envelope = LiveEnvelope(
        stream = StreamKind.MOTION,
        seq = 1L,
        captureElapsedNanos = 500L,
        emittedElapsedNanos = 700L,
        payload = MotionSample(
            provider = selection.provider,
            providerName = selection.providerName,
            capabilities = selection.capabilities,
            sourceSensorElapsedNanos = 500L,
            yaw = 1f,
            pitch = 2f,
            roll = 3f,
        ),
    )

    expectEquals("motion stream", StreamKind.MOTION, envelope.stream)
    expectEquals("motion capture elapsed", 500L, envelope.captureElapsedNanos)
    expectEquals("motion emitted elapsed", 700L, envelope.emittedElapsedNanos)
    expectEquals("motion provider name", "game_rotation_vector", envelope.payload.providerName)
    expectTrue("motion capability metadata", envelope.payload.capabilities.gameRotationVector)
    expectEquals("motion source sensor elapsed", 500L, envelope.payload.sourceSensorElapsedNanos)
}

private fun previewMapperCentersBaselineAndBoundsDeltas() {
    val mapper = PreviewAimMapper(AimBaseline(yaw = 10f, pitch = -5f, roll = 2f, elapsedNanos = 100L))
    val centered = mapper.map(
        sample = sample(
            provider = MotionProvider.GAME_ROTATION_VECTOR,
            yaw = 10f,
            pitch = -5f,
            roll = 2f,
        ),
    )
    expectNear("center x", 0f, centered.x)
    expectNear("center y", 0f, centered.y)
    expectTrue("center pad enabled", centered.padEnabled)

    val moved = mapper.map(
        sample = sample(
            provider = MotionProvider.GAME_ROTATION_VECTOR,
            yaw = 80f,
            pitch = -95f,
            roll = 2f,
        ),
    )
    expectTrue("moved x bounded", moved.x in -1f..1f)
    expectTrue("moved y bounded", moved.y in -1f..1f)
}

private fun previewMapperDisablesPadWhenMotionUnavailable() {
    val mapper = PreviewAimMapper(AimBaseline(yaw = 0f, pitch = 0f, roll = 0f, elapsedNanos = 1L))
    val preview = mapper.map(
        sample = sample(
            provider = MotionProvider.UNAVAILABLE,
            yaw = 45f,
            pitch = 45f,
            roll = 45f,
        ),
    )

    expectEquals("unavailable x", 0f, preview.x)
    expectEquals("unavailable y", 0f, preview.y)
    expectFalse("unavailable pad disabled", preview.padEnabled)
    expectEquals("unavailable label", "Motion unavailable", preview.statusLabel)
}

private fun previewMapperDoesNotExposeDesktopProfileMapping() {
    val forbiddenTokens = listOf("sensitivity", "dead", "windows", "mac", "hid", "profile")
    val exportedNames = listOf(
        PreviewAim::class.java.declaredFields.map { it.name },
        AimBaseline::class.java.declaredFields.map { it.name },
        PreviewAimMapper::class.java.declaredFields.map { it.name },
        MotionCapabilityFlags::class.java.declaredFields.map { it.name },
        MotionProviderSelection::class.java.declaredFields.map { it.name },
    ).flatten().joinToString(" ").lowercase()

    forbiddenTokens.forEach { token ->
        expectFalse("no desktop profile field token $token", exportedNames.contains(token))
    }
}

private fun sample(
    provider: MotionProvider,
    yaw: Float,
    pitch: Float,
    roll: Float,
): LiveEnvelope<MotionSample> =
    LiveEnvelope(
        stream = StreamKind.MOTION,
        seq = 1L,
        captureElapsedNanos = 100L,
        emittedElapsedNanos = 120L,
        payload = MotionSample(
            provider = provider,
            sourceSensorElapsedNanos = 100L,
            yaw = yaw,
            pitch = pitch,
            roll = roll,
        ),
    )

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

private fun expectFalse(label: String, condition: Boolean) {
    expectTrue(label, !condition)
}

private fun expectNear(label: String, expected: Float, actual: Float) {
    if (kotlin.math.abs(expected - actual) > 0.001f) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
