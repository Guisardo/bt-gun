package com.btgun.desktop.transport

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException

sealed interface UdpInputRuntimeStartResult {
    data object Started : UdpInputRuntimeStartResult
    data class Failed(val reason: String) : UdpInputRuntimeStartResult
}

interface UdpInputRuntime {
    val lifecycleState: InputStreamLifecycleState

    fun start(trustedSession: String, config: InputStreamConfig): UdpInputRuntimeStartResult
    fun onControlDisconnected(nowElapsedNanos: Long)
    fun stop(reason: String = "stopped")
}

class DesktopUdpInputRuntime(
    private val onInput: (UdpReceivedInput) -> Unit = {},
    private val onRejected: (InputReplayRejectReason) -> Unit = {},
    private val onStateChanged: (InputStreamLifecycleState) -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
    private val socketFactory: (InputStreamConfig) -> DatagramSocket = ::defaultSocket,
    private val threadFactory: (Runnable) -> Thread = { runnable ->
        Thread(runnable, "bt-gun-udp-input").apply { isDaemon = true }
    },
) : UdpInputRuntime {
    private val lock = Any()
    private val receiver = UdpInputReceiver(onInput = onInput)
    private var generation: Long = 0L
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    override val lifecycleState: InputStreamLifecycleState
        get() = synchronized(lock) { receiver.lifecycleState }

    override fun start(trustedSession: String, config: InputStreamConfig): UdpInputRuntimeStartResult {
        require(trustedSession.isNotBlank()) { "trustedSession must not be blank" }
        synchronized(lock) {
            stopLocked(reason = "replaced")
        }
        val newSocket = runCatching { socketFactory(config) }.getOrElse { error ->
            onStateChanged(InputStreamLifecycleState.STOPPED)
            return UdpInputRuntimeStartResult.Failed(errorSummary(error))
        }

        val newGeneration: Long
        synchronized(lock) {
            stopLocked(reason = "replaced")
            generation += 1L
            newGeneration = generation
            socket = newSocket
            receiver.start(trustedSession = trustedSession, config = config)
            thread = threadFactory(Runnable { receiveLoop(newSocket, newGeneration) }).also { it.start() }
        }
        onStateChanged(InputStreamLifecycleState.ACTIVE)
        return UdpInputRuntimeStartResult.Started
    }

    override fun onControlDisconnected(nowElapsedNanos: Long) {
        val state = synchronized(lock) {
            receiver.onControlDisconnected(nowElapsedNanos)
            receiver.lifecycleState
        }
        onStateChanged(state)
    }

    override fun stop(reason: String) {
        val state = synchronized(lock) {
            stopLocked(reason)
            receiver.lifecycleState
        }
        onStateChanged(state)
    }

    private fun receiveLoop(activeSocket: DatagramSocket, activeGeneration: Long) {
        val buffer = ByteArray(MAX_DATAGRAM_BYTES)
        while (isActive(activeGeneration, activeSocket)) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                activeSocket.receive(packet)
                val bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val result = synchronized(lock) {
                    if (!isActive(activeGeneration, activeSocket)) return@receiveLoop
                    receiver.handleDatagram(bytes, nanoTime())
                }
                if (result is UdpInputReceiverResult.Rejected) {
                    onRejected(result.reason)
                }
                emitState(activeGeneration, activeSocket)
            } catch (_: SocketTimeoutException) {
                synchronized(lock) {
                    if (!isActive(activeGeneration, activeSocket)) return@receiveLoop
                    receiver.refresh(nanoTime())
                    receiver.onStreamTimeout(nanoTime())
                }
                emitState(activeGeneration, activeSocket)
            } catch (_: SocketException) {
                return
            } catch (_: IOException) {
                onRejected(InputReplayRejectReason.MALFORMED)
            }
        }
    }

    private fun emitState(activeGeneration: Long, activeSocket: DatagramSocket) {
        val state = synchronized(lock) {
            if (!isActive(activeGeneration, activeSocket)) return
            receiver.lifecycleState
        }
        onStateChanged(state)
    }

    private fun isActive(activeGeneration: Long, activeSocket: DatagramSocket): Boolean =
        synchronized(lock) {
            generation == activeGeneration && socket === activeSocket && !activeSocket.isClosed
        }

    private fun stopLocked(reason: String) {
        generation += 1L
        socket?.close()
        socket = null
        thread = null
        receiver.stop(reason = reason)
    }

    private companion object {
        const val MAX_DATAGRAM_BYTES = 2048

        fun defaultSocket(config: InputStreamConfig): DatagramSocket =
            DatagramSocket(null).apply {
                soTimeout = config.streamTimeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                bind(InetSocketAddress(InetAddress.getByName(config.udpHost), config.udpPort))
            }

        fun errorSummary(error: Throwable): String =
            error.javaClass.simpleName.ifBlank { "error" } + error.message?.let { ": $it" }.orEmpty()
    }
}
