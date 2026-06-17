package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.transport.UdpReceivedInput
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory

data class WindowsBackendRuntimeConfig(
    val bridgePath: String,
    val outputDrainIntervalMillis: Long = 16L,
) {
    init {
        require(bridgePath.isNotBlank()) { "bridgePath must be nonblank" }
        require(outputDrainIntervalMillis > 0L) { "outputDrainIntervalMillis must be positive" }
    }
}

data class WindowsBackendRuntimeDiagnostics(
    val lifecycleState: BackendLifecycleState = BackendLifecycleState.STOPPED,
    val lastPublishResult: BackendPublishResult? = null,
    val lastBridgeStatus: WindowsDriverBridgeStatus? = null,
    val stale: Boolean = false,
    val lastSourceSequence: Long? = null,
    val lastHapticSendResult: HapticSendResult? = null,
    val outputHapticCommandsRouted: Long = 0L,
)

class WindowsBackendRuntime(
    val config: WindowsBackendRuntimeConfig,
    private val backend: WindowsVirtualControllerBackend = WindowsVirtualControllerBackend(
        bridge = WindowsDriverBridge(
            WindowsDriverBridgeConfig(command = listOf(config.bridgePath)),
        ),
    ),
    private val nowElapsedNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    var onDiagnosticsChanged: (WindowsBackendRuntimeDiagnostics) -> Unit = {}

    private val lock = Any()
    private val outputDrainExecutor = Executors.newSingleThreadExecutor(
        ThreadFactory { task ->
            Thread(task, "btgun-windows-output-drain").also { it.isDaemon = true }
        },
    )
    private var attachedServer: ControlServer? = null
    private var previousUdpCallback: ((UdpReceivedInput) -> Unit)? = null
    private var runtimeUdpCallback: ((UdpReceivedInput) -> Unit)? = null
    private var outputDrainRunning = false
    private var outputDrainFuture: Future<*>? = null
    private var diagnostics = WindowsBackendRuntimeDiagnostics()

    fun attach(controlServer: ControlServer) {
        val callback: (UdpReceivedInput) -> Unit = { input ->
            previousUdpCallback?.invoke(input)
            handleTrustedInput(input)
        }
        synchronized(lock) {
            require(attachedServer == null) { "WindowsBackendRuntime is already attached" }
            previousUdpCallback = controlServer.onUdpInputReceived
            runtimeUdpCallback = callback
            attachedServer = controlServer
        }
        backend.start()
        synchronized(lock) {
            updateDiagnosticsLocked(
                diagnostics.copy(
                    lifecycleState = backend.lifecycleState,
                    lastBridgeStatus = backend.lastBridgeStatus,
                ),
            )
        }
        controlServer.onUdpInputReceived = callback
        if (backend.lifecycleState == BackendLifecycleState.STARTED) {
            startOutputDrainLoop(controlServer)
        }
    }

    fun diagnostics(): WindowsBackendRuntimeDiagnostics =
        synchronized(lock) { diagnostics }

    override fun close() {
        stopOutputDrainLoop()
        val serverToRestore: ControlServer?
        val previous: ((UdpReceivedInput) -> Unit)?
        val runtimeCallback: ((UdpReceivedInput) -> Unit)?
        synchronized(lock) {
            serverToRestore = attachedServer
            previous = previousUdpCallback
            runtimeCallback = runtimeUdpCallback
            attachedServer = null
            previousUdpCallback = null
            runtimeUdpCallback = null
        }
        val server = serverToRestore
        if (server != null && server.onUdpInputReceived === runtimeCallback) {
            server.onUdpInputReceived = previous ?: {}
        }
        backend.stop("windows backend runtime closed")
        synchronized(lock) {
            updateDiagnosticsLocked(
                diagnostics.copy(
                    lifecycleState = backend.lifecycleState,
                    lastBridgeStatus = backend.lastBridgeStatus,
                ),
            )
        }
        outputDrainExecutor.shutdownNow()
    }

    private fun handleTrustedInput(input: UdpReceivedInput) {
        val state = UdpControllerStateAdapter.toState(input)
        val publishResult = backend.publish(state)
        val bridgeStatus = backend.lastBridgeStatus
        synchronized(lock) {
            updateDiagnosticsLocked(
                diagnostics.copy(
                    lifecycleState = backend.lifecycleState,
                    lastPublishResult = publishResult,
                    lastBridgeStatus = bridgeStatus,
                    stale = state.stale,
                    lastSourceSequence = bridgeStatus?.lastInputSequence?.takeIf { it > 0L }
                        ?: state.sourceSequence,
                ),
            )
        }
    }

    private fun startOutputDrainLoop(controlServer: ControlServer) {
        synchronized(lock) {
            if (outputDrainFuture?.isDone == false) return
            outputDrainRunning = true
            outputDrainFuture = outputDrainExecutor.submit {
                while (isOutputDrainRunning()) {
                    drainAndRouteOutput(controlServer)
                    try {
                        Thread.sleep(config.outputDrainIntervalMillis)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
    }

    private fun stopOutputDrainLoop() {
        val future: Future<*>?
        synchronized(lock) {
            outputDrainRunning = false
            future = outputDrainFuture
            outputDrainFuture = null
        }
        future?.cancel(true)
    }

    private fun isOutputDrainRunning(): Boolean =
        synchronized(lock) { outputDrainRunning }

    private fun drainAndRouteOutput(controlServer: ControlServer) {
        val commands = backend.drainOutputHaptics(nowElapsedNanos())
        if (commands.isEmpty()) return
        val hapticSendResults = commands.map { command ->
            controlServer.sendHapticCommand(command, nowElapsedNanos = nowElapsedNanos())
        }
        synchronized(lock) {
            updateDiagnosticsLocked(
                diagnostics.copy(
                    lifecycleState = backend.lifecycleState,
                    lastBridgeStatus = backend.lastBridgeStatus,
                    lastHapticSendResult = hapticSendResults.lastOrNull() ?: diagnostics.lastHapticSendResult,
                    outputHapticCommandsRouted = diagnostics.outputHapticCommandsRouted + hapticSendResults.size,
                ),
            )
        }
    }

    private fun updateDiagnosticsLocked(next: WindowsBackendRuntimeDiagnostics) {
        diagnostics = next
        onDiagnosticsChanged(next)
    }
}
