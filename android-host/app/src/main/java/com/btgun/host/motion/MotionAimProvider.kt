package com.btgun.host.motion

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.view.Surface
import com.btgun.host.model.ElapsedNanosClock
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.StreamKind
import com.btgun.host.model.StreamSequencer

data class MotionCapabilityFlags(
    val gameRotationVector: Boolean = false,
    val rotationVector: Boolean = false,
    val gyroscope: Boolean = false,
    val accelerometer: Boolean = false,
    val gravity: Boolean = false,
    val tiltFallback: Boolean = false,
    val timestampSource: String = SENSOR_EVENT_TIMESTAMP_SOURCE,
) {
    companion object {
        const val SENSOR_EVENT_TIMESTAMP_SOURCE = "sensor_event_elapsed_nanos"
        const val UNAVAILABLE_TIMESTAMP_SOURCE = "unavailable"
    }
}

data class SelectedMotionProvider(
    val provider: MotionProvider,
    val providerName: String,
    val capabilities: MotionCapabilityFlags,
    val isAvailable: Boolean,
)

object MotionProviderSelection {
    fun choose(capabilities: MotionCapabilityFlags): SelectedMotionProvider {
        val provider = when {
            capabilities.gameRotationVector -> MotionProvider.GAME_ROTATION_VECTOR
            capabilities.rotationVector -> MotionProvider.ROTATION_VECTOR
            capabilities.gyroscope && (capabilities.gravity || capabilities.accelerometer) -> MotionProvider.GYRO_GRAVITY
            capabilities.gravity || capabilities.accelerometer -> MotionProvider.TILT_FALLBACK
            else -> MotionProvider.UNAVAILABLE
        }
        val isAvailable = provider != MotionProvider.UNAVAILABLE
        val selectedCapabilities = capabilities.copy(
            tiltFallback = provider == MotionProvider.TILT_FALLBACK,
            timestampSource = if (isAvailable) {
                MotionCapabilityFlags.SENSOR_EVENT_TIMESTAMP_SOURCE
            } else {
                MotionCapabilityFlags.UNAVAILABLE_TIMESTAMP_SOURCE
            },
        )

        return SelectedMotionProvider(
            provider = provider,
            providerName = provider.wireName,
            capabilities = selectedCapabilities,
            isAvailable = isAvailable,
        )
    }
}

class MotionAimProvider(
    private val sensorManager: SensorManager,
    private val clock: ElapsedNanosClock,
    private val sequencer: StreamSequencer = StreamSequencer(),
) {
    fun currentSelection(): SelectedMotionProvider =
        MotionProviderSelection.choose(detectCapabilities())

    fun envelopeForOrientation(
        selection: SelectedMotionProvider,
        sourceSensorElapsedNanos: Long,
        yaw: Float,
        pitch: Float,
        roll: Float,
    ): LiveEnvelope<MotionSample> {
        require(selection.isAvailable) { "motion provider unavailable" }
        val emittedElapsedNanos = clock.nowElapsedNanos()
        val captureElapsedNanos = sourceSensorElapsedNanos.coerceAtMost(emittedElapsedNanos)

        return LiveEnvelope(
            stream = StreamKind.MOTION,
            seq = sequencer.next(StreamKind.MOTION),
            captureElapsedNanos = captureElapsedNanos,
            emittedElapsedNanos = emittedElapsedNanos,
            payload = MotionSample(
                provider = selection.provider,
                providerName = selection.providerName,
                capabilities = selection.capabilities,
                sourceSensorElapsedNanos = sourceSensorElapsedNanos,
                yaw = yaw,
                pitch = pitch,
                roll = roll,
            ),
        )
    }

    fun unavailableSample(emittedElapsedNanos: Long = clock.nowElapsedNanos()): LiveEnvelope<MotionSample> {
        val selection = MotionProviderSelection.choose(detectCapabilities())
        require(!selection.isAvailable) { "motion provider is available" }

        return LiveEnvelope(
            stream = StreamKind.MOTION,
            seq = sequencer.next(StreamKind.MOTION),
            captureElapsedNanos = emittedElapsedNanos,
            emittedElapsedNanos = emittedElapsedNanos,
            payload = MotionSample(
                provider = MotionProvider.UNAVAILABLE,
                providerName = MotionProvider.UNAVAILABLE.wireName,
                capabilities = selection.capabilities,
                sourceSensorElapsedNanos = emittedElapsedNanos,
                yaw = 0f,
                pitch = 0f,
                roll = 0f,
            ),
        )
    }

    fun envelopeForSensorEvent(
        event: SensorEvent,
        orientation: OrientationAngles,
        selection: SelectedMotionProvider = currentSelection(),
    ): LiveEnvelope<MotionSample> =
        envelopeForOrientation(
            selection = selection,
            sourceSensorElapsedNanos = event.timestamp,
            yaw = orientation.yaw,
            pitch = orientation.pitch,
            roll = orientation.roll,
        )

    private fun detectCapabilities(): MotionCapabilityFlags =
        MotionCapabilityFlags(
            gameRotationVector = hasSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
            rotationVector = hasSensor(Sensor.TYPE_ROTATION_VECTOR),
            gyroscope = hasSensor(Sensor.TYPE_GYROSCOPE),
            accelerometer = hasSensor(Sensor.TYPE_ACCELEROMETER),
            gravity = hasSensor(Sensor.TYPE_GRAVITY),
        )

    private fun hasSensor(sensorType: Int): Boolean =
        sensorManager.getDefaultSensor(sensorType) != null
}

data class OrientationAngles(
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
)

data class DisplayRotationAxes(
    val x: Int,
    val y: Int,
)

object DisplayRotationRemap {
    fun axesFor(rotation: Int): DisplayRotationAxes? =
        when (rotation) {
            Surface.ROTATION_90 -> DisplayRotationAxes(
                x = SensorManager.AXIS_Y,
                y = SensorManager.AXIS_MINUS_X,
            )
            Surface.ROTATION_180 -> DisplayRotationAxes(
                x = SensorManager.AXIS_MINUS_X,
                y = SensorManager.AXIS_MINUS_Y,
            )
            Surface.ROTATION_270 -> DisplayRotationAxes(
                x = SensorManager.AXIS_MINUS_Y,
                y = SensorManager.AXIS_X,
            )
            else -> null
        }

    fun remapTiltXY(rotation: Int, x: Float, y: Float): Pair<Float, Float> =
        when (rotation) {
            Surface.ROTATION_90 -> y to -x
            Surface.ROTATION_180 -> -x to -y
            Surface.ROTATION_270 -> -y to x
            else -> x to y
        }
}
