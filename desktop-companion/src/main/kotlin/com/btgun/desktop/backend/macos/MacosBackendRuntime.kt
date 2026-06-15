package com.btgun.desktop.backend.macos

import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.transport.UdpReceivedInput

data class MacosBackendRuntimeConfig(
    val helperPath: String = "",
    val helperCommand: List<String> = if (helperPath.isNotBlank()) listOf(helperPath) else emptyList(),
) {
    init {
        require(helperPath.isNotBlank() || helperCommand.isNotEmpty()) {
            "helperPath must be nonblank or helperCommand must not be empty"
        }
        require(helperCommand.all { it.isNotBlank() }) { "helperCommand entries must be nonblank" }
    }
}

data class MacosBackendRuntimeDiagnostics(
    val lifecycleState: BackendLifecycleState = BackendLifecycleState.STOPPED,
    val lastPublishResult: BackendPublishResult? = null,
    val stale: Boolean = false,
    val lastSourceSequence: Long? = null,
    val lastHapticSendResult: HapticSendResult? = null,
    val outputHapticCommandsRouted: Long = 0L,
    val helperStatus: MacosHidHelperStatus = MacosHidHelperStatus(),
)

class MacosBackendRuntime(
    val config: MacosBackendRuntimeConfig,
    private val backend: MacosVirtualControllerBackend = MacosVirtualControllerBackend(
        helper = MacosHidHelper(
            MacosHidHelperConfig(command = config.helperCommand),
        ),
    ),
    private val nowElapsedNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    var onDiagnosticsChanged: (MacosBackendRuntimeDiagnostics) -> Unit = {}

    private val lock = Any()
    private var attachedServer: ControlServer? = null
    private var previousUdpCallback: ((UdpReceivedInput) -> Unit)? = null
    private var runtimeUdpCallback: ((UdpReceivedInput) -> Unit)? = null
    private var diagnostics = MacosBackendRuntimeDiagnostics()

    fun attach(controlServer: ControlServer) {
        synchronized(lock) {
            require(attachedServer == null) { "MacosBackendRuntime is already attached" }
            previousUdpCallback = controlServer.onUdpInputReceived
            val callback: (UdpReceivedInput) -> Unit = { input ->
                previousUdpCallback?.invoke(input)
                handleTrustedInput(controlServer, input)
            }
            runtimeUdpCallback = callback
            attachedServer = controlServer
            backend.start()
            updateDiagnosticsLocked(
                diagnostics.copy(
                    lifecycleState = backend.lifecycleState,
                    helperStatus = backend.helperStatus(),
                ),
            )
            controlServer.onUdpInputReceived = callback
        }
    }

    fun diagnostics(): MacosBackendRuntimeDiagnostics =
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
        backend.stop("macos backend runtime closed")
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
                    helperStatus = backend.helperStatus(),
                ),
            )
        }
    }

    private fun updateDiagnosticsLocked(next: MacosBackendRuntimeDiagnostics) {
        diagnostics = next
        onDiagnosticsChanged(next)
    }
}
