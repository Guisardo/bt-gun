package com.btgun.host.transport

import android.util.Log
import com.btgun.host.model.GunEvent
import com.btgun.host.model.GunInputState
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.motion.MotionCapabilityFlags
import com.btgun.host.profile.MappedControllerState
import com.btgun.host.profile.VirtualButton
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.ArrayDeque
import kotlin.math.roundToInt

fun interface AndroidUdpDatagramSink {
    fun send(host: String, port: Int, payload: ByteArray): Boolean
}

enum class AndroidUdpDatagramPriority {
    SNAPSHOT,
    EDGE,
}

enum class AndroidUdpInputSendResult {
    SENT,
    INACTIVE,
    FAILED,
}

class AndroidUdpSocketSink : AndroidUdpDatagramSink, AutoCloseable {
    private val lock = Object()
    private val queue = ArrayDeque<QueuedDatagram>()
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    private var closed: Boolean = false
    private var lastFailureLogNanos: Long = 0L
    private var cachedHost: String? = null
    private var cachedAddress: InetAddress? = null

    override fun send(host: String, port: Int, payload: ByteArray): Boolean {
        val queued = QueuedDatagram(
            host = host,
            port = port,
            payload = payload.copyOf(),
            priority = priorityFor(payload),
        )
        synchronized(lock) {
            if (closed) return false
            enqueueLocked(queued)
            ensureWorkerLocked()
            lock.notifyAll()
            return true
        }
    }

    private fun enqueueLocked(queued: QueuedDatagram) {
        if (queued.priority == AndroidUdpDatagramPriority.SNAPSHOT) {
            dropAllSnapshotsLocked()
        }
        while (queue.size >= MAX_QUEUED_DATAGRAMS) {
            if (!dropOldestSnapshotLocked()) {
                queue.removeFirst()
            }
        }
        queue.addLast(queued)
    }

    private fun dropAllSnapshotsLocked() {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().priority == AndroidUdpDatagramPriority.SNAPSHOT) {
                iterator.remove()
            }
        }
    }

    private fun dropOldestSnapshotLocked(): Boolean {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().priority == AndroidUdpDatagramPriority.SNAPSHOT) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    private fun ensureWorkerLocked() {
        if (worker?.isAlive == true) return
        worker = Thread(::workerLoop, "bt-gun-udp-sender").apply {
            isDaemon = true
            start()
        }
    }

    private fun workerLoop() {
        while (true) {
            val queued = synchronized(lock) {
                while (!closed && queue.isEmpty()) {
                    lock.wait()
                }
                if (closed && queue.isEmpty()) return
                queue.removeFirst()
            }
            sendOnWorker(queued)
        }
    }

    private fun sendOnWorker(queued: QueuedDatagram) {
        val activeSocket = synchronized(lock) {
            if (closed) return
            socket ?: DatagramSocket().also { socket = it }
        }
        runCatching {
            activeSocket.send(
                DatagramPacket(
                    queued.payload,
                    queued.payload.size,
                    addressFor(queued.host),
                    queued.port,
                ),
            )
        }.onFailure { error ->
            closeSocket(activeSocket)
            logFailure(error)
        }
    }

    private fun addressFor(host: String): InetAddress {
        synchronized(lock) {
            if (cachedHost == host) {
                cachedAddress?.let { return it }
            }
        }
        val resolved = InetAddress.getByName(host)
        synchronized(lock) {
            cachedHost = host
            cachedAddress = resolved
        }
        return resolved
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            queue.clear()
            socket?.close()
            socket = null
            lock.notifyAll()
        }
    }

    private fun closeSocket(activeSocket: DatagramSocket) {
        synchronized(lock) {
            if (socket === activeSocket) {
                socket = null
            }
        }
        activeSocket.close()
    }

    private fun logFailure(error: Throwable) {
        val now = System.nanoTime()
        if (now - lastFailureLogNanos < FAILURE_LOG_INTERVAL_NANOS) return
        lastFailureLogNanos = now
        Log.w(TAG, "UDP input send failed", error)
    }

    companion object {
        const val TAG = "BtGunUdp"
        const val MAX_QUEUED_DATAGRAMS = 3
        const val FAILURE_LOG_INTERVAL_NANOS = 1_000_000_000L
    }
}

private data class QueuedDatagram(
    val host: String,
    val port: Int,
    val payload: ByteArray,
    val priority: AndroidUdpDatagramPriority,
)

internal fun priorityFor(payload: ByteArray): AndroidUdpDatagramPriority =
    if (
        payload.size > UDP_FRAME_TYPE_OFFSET &&
        (payload[UDP_FRAME_TYPE_OFFSET].toInt() and 0xff) == UdpInputFrameType.EDGE.wireValue
    ) {
        AndroidUdpDatagramPriority.EDGE
    } else {
        AndroidUdpDatagramPriority.SNAPSHOT
    }

private const val UDP_FRAME_TYPE_OFFSET = 5

class AndroidUdpInputSender(
    private val datagramSink: AndroidUdpDatagramSink = AndroidUdpSocketSink(),
    private val elapsedRealtimeNanos: () -> Long,
    private val sequencer: InputStreamSequencer = InputStreamSequencer(),
) : AutoCloseable {
    private var activeConfig: InputStreamConfig? = null
    private var controlDisconnectedAtNanos: Long? = null
    var lifecycleState: InputStreamLifecycleState = InputStreamLifecycleState.STOPPED
        private set

    fun start(config: InputStreamConfig) {
        activeConfig = config
        controlDisconnectedAtNanos = null
        lifecycleState = InputStreamLifecycleState.ACTIVE
        sequencer.resetFor(config.streamSessionIdHex)
    }

    fun stop(reason: String) {
        require(reason.isNotBlank()) { "reason must not be blank" }
        activeConfig = null
        controlDisconnectedAtNanos = null
        lifecycleState = InputStreamLifecycleState.STOPPED
    }

    override fun close() {
        stop("closed")
        (datagramSink as? AutoCloseable)?.close()
    }

    fun onControlDisconnected(nowElapsedNanos: Long) {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        if (activeConfig == null) {
            lifecycleState = InputStreamLifecycleState.STOPPED
            return
        }
        controlDisconnectedAtNanos = nowElapsedNanos
        lifecycleState = InputStreamLifecycleState.GRACE
    }

    fun onControlReconnected(config: InputStreamConfig) {
        start(config)
    }

    fun onSessionChanged(newSessionId: String) {
        require(newSessionId.isNotBlank()) { "newSessionId must not be blank" }
        stop("control session changed")
    }

    fun sendSnapshot(
        state: GunInputState,
        motion: LiveEnvelope<MotionSample>?,
    ): AndroidUdpInputSendResult =
        sendFrame(
            type = UdpInputFrameType.SNAPSHOT,
            state = state,
            motion = motion,
            captureElapsedNanos = elapsedRealtimeNanos(),
            edgeBitmask = 0,
        )

    fun sendSnapshot(
        mappedState: MappedControllerState,
        motion: LiveEnvelope<MotionSample>?,
        rawDebugEnabled: Boolean,
    ): AndroidUdpInputSendResult =
        sendMappedFrame(
            type = UdpInputFrameType.SNAPSHOT,
            mappedState = mappedState,
            motion = motion,
            rawDebugEnabled = rawDebugEnabled,
            captureElapsedNanos = elapsedRealtimeNanos(),
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

    fun sendEdge(
        event: LiveEnvelope<GunEvent>,
        mappedState: MappedControllerState,
        motion: LiveEnvelope<MotionSample>?,
        rawDebugEnabled: Boolean,
    ): AndroidUdpInputSendResult =
        sendMappedFrame(
            type = UdpInputFrameType.EDGE,
            mappedState = mappedState,
            motion = motion,
            rawDebugEnabled = rawDebugEnabled,
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
        val sendElapsedNanos = elapsedRealtimeNanos()
        if (!canSendAt(config, sendElapsedNanos)) {
            return AndroidUdpInputSendResult.INACTIVE
        }
        val payload = motion?.payload
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
        val encoded = frame.encodeFor(config)
        return if (datagramSink.send(config.udpHost, config.udpPort, encoded)) {
            AndroidUdpInputSendResult.SENT
        } else {
            AndroidUdpInputSendResult.FAILED
        }
    }

    private fun sendMappedFrame(
        type: UdpInputFrameType,
        mappedState: MappedControllerState,
        motion: LiveEnvelope<MotionSample>?,
        rawDebugEnabled: Boolean,
        captureElapsedNanos: Long,
        edgeBitmask: Int,
    ): AndroidUdpInputSendResult {
        val config = activeConfig ?: return AndroidUdpInputSendResult.INACTIVE
        val sendElapsedNanos = elapsedRealtimeNanos()
        if (!canSendAt(config, sendElapsedNanos)) {
            return AndroidUdpInputSendResult.INACTIVE
        }
        val payload = motion?.payload
        val rawFlags = if (rawDebugEnabled) UdpInputFrame.FLAG_RAW_DEBUG_EXTRAS else 0
        val frame = UdpInputFrame(
            type = type,
            streamSessionId = config.streamSessionIdHex,
            sequence = sequencer.next(),
            captureElapsedNanos = captureElapsedNanos,
            sendElapsedNanos = sendElapsedNanos.coerceAtLeast(captureElapsedNanos),
            buttonBitmask = buttonBitmaskFor(mappedState.pressedVirtualControls) or edgeBitmask,
            stickX = mappedState.stickAxisX.toInt16Axis(),
            stickY = mappedState.stickAxisY.toInt16Axis(),
            motionProvider = if (rawDebugEnabled) payload?.provider?.toWireProvider() ?: PROVIDER_UNAVAILABLE else PROVIDER_UNAVAILABLE,
            motionCapabilityFlags = if (rawDebugEnabled) payload?.capabilities?.toWireFlags() ?: 0 else 0,
            yaw = mappedState.aimAxisX,
            pitch = mappedState.aimAxisY,
            roll = if (rawDebugEnabled) payload?.roll ?: Float.NaN else Float.NaN,
            rawAimX = if (rawDebugEnabled) payload?.rawAimX ?: Float.NaN else Float.NaN,
            rawAimY = if (rawDebugEnabled) payload?.rawAimY ?: Float.NaN else Float.NaN,
            sourceSensorElapsedNanos = if (rawDebugEnabled) payload?.sourceSensorElapsedNanos ?: 0L else 0L,
            streamFlags = UdpInputFrame.FLAG_MAPPED_PRODUCT_STREAM or rawFlags,
            productAimX = mappedState.aimAxisX,
            productAimY = mappedState.aimAxisY,
            rawRoll = if (rawDebugEnabled) payload?.roll ?: Float.NaN else Float.NaN,
        )
        val encoded = frame.encodeFor(config)
        return if (datagramSink.send(config.udpHost, config.udpPort, encoded)) {
            AndroidUdpInputSendResult.SENT
        } else {
            AndroidUdpInputSendResult.FAILED
        }
    }

    private fun canSendAt(config: InputStreamConfig, nowElapsedNanos: Long): Boolean {
        val disconnectedAt = controlDisconnectedAtNanos ?: return lifecycleState == InputStreamLifecycleState.ACTIVE
        val graceNanos = config.controlDisconnectGraceMs * NANOS_PER_MILLI
        return if (nowElapsedNanos - disconnectedAt <= graceNanos) {
            lifecycleState = InputStreamLifecycleState.GRACE
            true
        } else {
            activeConfig = null
            controlDisconnectedAtNanos = null
            lifecycleState = InputStreamLifecycleState.STALE
            false
        }
    }

    private fun UdpInputFrame.encodeFor(config: InputStreamConfig): ByteArray =
        when (config.frameFormat) {
            InputFrameFormat.V1 -> UdpInputFrameCodec.encode(this, config)
            InputFrameFormat.COMPACT_V2 -> UdpInputFrameCodec.encodeCompact(this, config)
        }

    private fun buttonBitmaskFor(state: GunInputState): Int {
        var bitmask = 0
        state.pressedControls.forEach { control ->
            bitmask = bitmask or bitForControl(control)
        }
        return bitmask
    }

    private fun buttonBitmaskFor(pressedControls: Set<String>): Int {
        var bitmask = 0
        pressedControls.forEach { control ->
            bitmask = bitmask or bitForControl(control)
        }
        return bitmask
    }

    private fun bitForControl(control: String): Int =
        VirtualButton.fromId(control)?.bitMask ?: 0

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
        const val BUTTON_A: Int = 1 shl 0
        const val BUTTON_B: Int = 1 shl 1
        const val BUTTON_X: Int = 1 shl 2
        const val BUTTON_Y: Int = 1 shl 3
        const val BUTTON_RELOAD: Int = 1 shl 6
        const val BUTTON_TRIGGER: Int = 1 shl 7
        const val EDGE_CONTROL_CHANGED: Int = 1 shl 30

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
        private const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
