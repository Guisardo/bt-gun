package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.transport.UdpReceivedInput

data class WindowsBackendRuntimeConfig(
    val bridgePath: String,
) {
    init {
        require(bridgePath.isNotBlank()) { "bridgePath must be nonblank" }
    }
}

data class WindowsBackendRuntimeDiagnostics(
    val lifecycleState: BackendLifecycleState = BackendLifecycleState.STOPPED,
    val lastPublishResult: BackendPublishResult? = null,
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
    private var attachedServer: ControlServer? = null
    private var previousUdpCallback: ((UdpReceivedInput) -> Unit)? = null
    private var runtimeUdpCallback: ((UdpReceivedInput) -> Unit)? = null
    private var diagnostics = WindowsBackendRuntimeDiagnostics()

    fun attach(controlServer: ControlServer) {
        synchronized(lock) {
            require(attachedServer == null) { "WindowsBackendRuntime is already attached" }
            previousUdpCallback = controlServer.onUdpInputReceived
            val callback: (UdpReceivedInput) -> Unit = { input ->
                previousUdpCallback?.invoke(input)
                handleTrustedInput(controlServer, input)
            }
            runtimeUdpCallback = callback
            attachedServer = controlServer
            backend.start()
            updateDiagnosticsLocked(diagnostics.copy(lifecycleState = backend.lifecycleState))
            controlServer.onUdpInputReceived = callback
        }
    }

    fun diagnostics(): WindowsBackendRuntimeDiagnostics =
        synchronized(lock) { diagnostics }

    override fun close() {
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
            updateDiagnosticsLocked(diagnostics.copy(lifecycleState = backend.lifecycleState))
        }
    }

    private fun handleTrustedInput(controlServer: ControlServer, input: UdpReceivedInput) {
        val state = UdpControllerStateAdapter.toState(input)
        val publishResult = backend.publish(state)
        val hapticSendResults = backend.drainOutputHaptics(nowElapsedNanos()).map { command ->
            controlServer.sendHapticCommand(command, nowElapsedNanos = nowElapsedNanos())
        }
        synchronized(lock) {
            updateDiagnosticsLocked(
                diagnostics.copy(
                    lifecycleState = backend.lifecycleState,
                    lastPublishResult = publishResult,
                    stale = state.stale,
                    lastSourceSequence = state.sourceSequence,
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
