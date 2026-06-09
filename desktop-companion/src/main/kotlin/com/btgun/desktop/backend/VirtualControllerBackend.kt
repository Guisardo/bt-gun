package com.btgun.desktop.backend

enum class BackendLifecycleState {
    STOPPED,
    STARTED,
}

sealed interface BackendLifecycleResult {
    data object Started : BackendLifecycleResult
    data class Stopped(val reason: String) : BackendLifecycleResult {
        init {
            require(reason.isNotBlank()) { "reason must be nonblank" }
        }
    }
}

sealed interface BackendPublishResult {
    data object Published : BackendPublishResult
    data class Rejected(val reason: String) : BackendPublishResult {
        init {
            require(reason.isNotBlank()) { "reason must be nonblank" }
        }
    }
}

interface VirtualControllerBackend {
    val descriptor: VirtualControllerDescriptor
    val capabilities: BackendCapabilities
    val lifecycleState: BackendLifecycleState
    val currentState: SemanticControllerState
    val lastPublishResult: BackendPublishResult?

    fun start(): BackendLifecycleResult
    fun publish(state: SemanticControllerState): BackendPublishResult
    fun simulateOutputReport(report: SimulatedOutputReport): com.btgun.desktop.haptics.HapticCommand?
    fun stop(reason: String = "stopped"): BackendLifecycleResult
}
