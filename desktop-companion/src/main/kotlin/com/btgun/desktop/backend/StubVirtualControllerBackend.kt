package com.btgun.desktop.backend

class StubVirtualControllerBackend(
    private val platform: StubPlatform,
    override val descriptor: VirtualControllerDescriptor = btGunV1Descriptor,
) : VirtualControllerBackend {
    private val lock = Any()
    private var state: SemanticControllerState = SemanticControllerState()
    private var lifecycle: BackendLifecycleState = BackendLifecycleState.STOPPED
    private var lastResult: BackendPublishResult? = null

    init {
        requireBtGunV1Invariant(descriptor)
    }

    override val capabilities: BackendCapabilities =
        when (platform) {
            StubPlatform.MACOS -> BackendCapabilityPresets.macosStub()
            StubPlatform.WINDOWS -> BackendCapabilityPresets.windowsStub()
        }

    override val lifecycleState: BackendLifecycleState
        get() = synchronized(lock) { lifecycle }

    override val currentState: SemanticControllerState
        get() = synchronized(lock) { state }

    override val lastPublishResult: BackendPublishResult?
        get() = synchronized(lock) { lastResult }

    override fun start(): BackendLifecycleResult =
        synchronized(lock) {
            lifecycle = BackendLifecycleState.STARTED
            BackendLifecycleResult.Started
        }

    override fun publish(state: SemanticControllerState): BackendPublishResult =
        synchronized(lock) {
            val result = if (lifecycle == BackendLifecycleState.STARTED) {
                this.state = state
                BackendPublishResult.Published
            } else {
                BackendPublishResult.Rejected("backend not started")
            }
            lastResult = result
            result
        }

    override fun stop(reason: String): BackendLifecycleResult =
        synchronized(lock) {
            lifecycle = BackendLifecycleState.STOPPED
            BackendLifecycleResult.Stopped(reason)
        }

    enum class StubPlatform {
        MACOS,
        WINDOWS,
    }

    companion object {
        fun macos(): StubVirtualControllerBackend =
            StubVirtualControllerBackend(StubPlatform.MACOS)

        fun windows(): StubVirtualControllerBackend =
            StubVirtualControllerBackend(StubPlatform.WINDOWS)
    }
}
