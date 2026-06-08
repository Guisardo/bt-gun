package com.btgun.host.transport

import com.btgun.host.model.GunEvent
import com.btgun.host.model.GunInputState
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.motion.MotionCapabilityFlags
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt

fun interface AndroidUdpDatagramSink {
    fun send(host: String, port: Int, payload: ByteArray): Boolean
}

enum class AndroidUdpInputSendResult {
    SENT,
    INACTIVE,
    FAILED,
}

class AndroidUdpSocketSink : AndroidUdpDatagramSink {
    override fun send(host: String, port: Int, payload: ByteArray): Boolean =
        runCatching {
            DatagramSocket().use { socket ->
                socket.send(
                    DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getByName(host),
                        port,
                    ),
                )
            }
        }.isSuccess
}

class AndroidUdpInputSender(
    private val datagramSink: AndroidUdpDatagramSink = AndroidUdpSocketSink(),
    private val elapsedRealtimeNanos: () -> Long,
    private val sequencer: InputStreamSequencer = InputStreamSequencer(),
) {
    private var activeConfig: InputStreamConfig? = null

    fun start(config: InputStreamConfig) {
        activeConfig = config
        sequencer.resetFor(config.streamSessionIdHex)
    }

    fun stop(reason: String) {
        activeConfig = null
    }

    fun sendSnapshot(
        state: GunInputState,
        motion: LiveEnvelope<MotionSample>?,
    ): AndroidUdpInputSendResult =
        sendFrame(
            type = UdpInputFrameType.SNAPSHOT,
            state = state,
            motion = motion,
            captureElapsedNanos = motion?.captureElapsedNanos ?: elapsedRealtimeNanos(),
            edgeBitmask = 0,
        )

    fun sendEdge(
        event: LiveEnvelope<GunEvent>,
        state: GunInputState,
        motion: LiveEnvelope<MotionSample>?,
    ): AndroidUdpInputSendResult =
        sendFrame(
            type = UdpInputFrameType.EDGE,
            state = state,
            motion = motion,
            captureElapsedNanos = event.captureElapsedNanos,
            edgeBitmask = EDGE_CONTROL_CHANGED,
        )

    private fun sendFrame(
        type: UdpInputFrameType,
        state: GunInputState,
        motion: LiveEnvelope<MotionSample>?,
        captureElapsedNanos: Long,
        edgeBitmask: Int,
    ): AndroidUdpInputSendResult {
        val config = activeConfig ?: return AndroidUdpInputSendResult.INACTIVE
        val payload = motion?.payload
        val sendElapsedNanos = elapsedRealtimeNanos()
        val frame = UdpInputFrame(
            type = type,
            streamSessionId = config.streamSessionIdHex,
            sequence = sequencer.next(),
            captureElapsedNanos = captureElapsedNanos,
            sendElapsedNanos = sendElapsedNanos.coerceAtLeast(captureElapsedNanos),
            buttonBitmask = buttonBitmaskFor(state) or edgeBitmask,
            stickX = state.stickAxisX.toInt16Axis(),
            stickY = state.stickAxisY.toInt16Axis(),
            motionProvider = payload?.provider?.toWireProvider() ?: PROVIDER_UNAVAILABLE,
            motionCapabilityFlags = payload?.capabilities?.toWireFlags() ?: 0,
            yaw = payload?.yaw ?: 0f,
            pitch = payload?.pitch ?: 0f,
            roll = payload?.roll ?: 0f,
            rawAimX = payload?.rawAimX ?: Float.NaN,
            rawAimY = payload?.rawAimY ?: Float.NaN,
            sourceSensorElapsedNanos = payload?.sourceSensorElapsedNanos ?: captureElapsedNanos,
        )
        val encoded = UdpInputFrameCodec.encode(frame, config)
        return if (datagramSink.send(config.udpHost, config.udpPort, encoded)) {
            AndroidUdpInputSendResult.SENT
        } else {
            AndroidUdpInputSendResult.FAILED
        }
    }

    private fun buttonBitmaskFor(state: GunInputState): Int {
        var bitmask = 0
        state.pressedControls.forEach { control ->
            bitmask = bitmask or when (control) {
                "trigger" -> BUTTON_TRIGGER
                "reload" -> BUTTON_RELOAD
                "button_x" -> BUTTON_X
                "button_y" -> BUTTON_Y
                "button_a" -> BUTTON_A
                "button_b" -> BUTTON_B
                else -> 0
            }
        }
        return bitmask
    }

    private fun Float.toInt16Axis(): Int =
        when {
            this <= -1f -> Short.MIN_VALUE.toInt()
            this >= 1f -> Short.MAX_VALUE.toInt()
            else -> (this * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        }

    private fun MotionProvider.toWireProvider(): Int =
        when (this) {
            MotionProvider.UNAVAILABLE -> PROVIDER_UNAVAILABLE
            MotionProvider.GAME_ROTATION_VECTOR -> PROVIDER_GAME_ROTATION_VECTOR
            MotionProvider.ROTATION_VECTOR -> PROVIDER_ROTATION_VECTOR
            MotionProvider.GYRO_GRAVITY -> PROVIDER_GYRO_GRAVITY
            MotionProvider.TILT_FALLBACK -> PROVIDER_TILT_FALLBACK
        }

    private fun MotionCapabilityFlags.toWireFlags(): Int {
        var flags = 0
        if (gameRotationVector) flags = flags or CAP_GAME_ROTATION_VECTOR
        if (rotationVector) flags = flags or CAP_ROTATION_VECTOR
        if (gyroscope) flags = flags or CAP_GYROSCOPE
        if (accelerometer) flags = flags or CAP_ACCELEROMETER
        if (gravity) flags = flags or CAP_GRAVITY
        if (tiltFallback) flags = flags or CAP_TILT_FALLBACK
        return flags
    }

    companion object {
        const val BUTTON_TRIGGER: Int = 1 shl 0
        const val BUTTON_RELOAD: Int = 1 shl 1
        const val BUTTON_X: Int = 1 shl 2
        const val BUTTON_Y: Int = 1 shl 3
        const val BUTTON_A: Int = 1 shl 4
        const val BUTTON_B: Int = 1 shl 5
        const val EDGE_CONTROL_CHANGED: Int = 1 shl 8

        const val PROVIDER_UNAVAILABLE: Int = 0
        const val PROVIDER_GAME_ROTATION_VECTOR: Int = 1
        const val PROVIDER_ROTATION_VECTOR: Int = 2
        const val PROVIDER_GYRO_GRAVITY: Int = 3
        const val PROVIDER_TILT_FALLBACK: Int = 4

        private const val CAP_GAME_ROTATION_VECTOR: Int = 1 shl 0
        private const val CAP_ROTATION_VECTOR: Int = 1 shl 1
        private const val CAP_GYROSCOPE: Int = 1 shl 2
        private const val CAP_ACCELEROMETER: Int = 1 shl 3
        private const val CAP_GRAVITY: Int = 1 shl 4
        private const val CAP_TILT_FALLBACK: Int = 1 shl 5
    }
}
