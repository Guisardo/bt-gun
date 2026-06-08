package com.btgun.desktop.transport

sealed interface UdpInputReceiverResult {
    data class Accepted(val input: UdpReceivedInput) : UdpInputReceiverResult
    data class Rejected(val reason: InputReplayRejectReason) : UdpInputReceiverResult
    data object Stopped : UdpInputReceiverResult
}

class UdpInputReceiver(
    private val onInput: (UdpReceivedInput) -> Unit = {},
) {
    private var trustedControlSessionId: String? = null
    private var guard: InputReplayGuard? = null
    private var activeConfig: InputStreamConfig? = null
    private var controlDisconnectedAtNanos: Long? = null
    var lifecycleState: InputStreamLifecycleState = InputStreamLifecycleState.STOPPED
        private set
    var current: UdpReceivedInput? = null
        private set

    fun start(trustedSession: String, config: InputStreamConfig): UdpInputReceiver {
        trustedControlSessionId = trustedSession
        activeConfig = config
        controlDisconnectedAtNanos = null
        lifecycleState = InputStreamLifecycleState.ACTIVE
        guard = InputReplayGuard(
            trustedControlSessionId = trustedSession,
            config = config,
        )
        current = null
        return this
    }

    fun handleDatagram(bytes: ByteArray, receivedElapsedNanos: Long): UdpInputReceiverResult {
        val controlSessionId = trustedControlSessionId ?: return UdpInputReceiverResult.Stopped
        val activeGuard = guard ?: return UdpInputReceiverResult.Stopped
        if (controlGraceExpired(receivedElapsedNanos)) {
            lifecycleState = InputStreamLifecycleState.STALE
            return UdpInputReceiverResult.Rejected(InputReplayRejectReason.CONTROL_GRACE_EXPIRED)
        }
        return when (val decision = activeGuard.acceptDatagram(bytes, receivedElapsedNanos, controlSessionId)) {
            is InputReplayDecision.Accepted -> {
                current = decision.input
                if (controlDisconnectedAtNanos == null) {
                    lifecycleState = InputStreamLifecycleState.ACTIVE
                }
                onInput(decision.input)
                UdpInputReceiverResult.Accepted(decision.input)
            }
            is InputReplayDecision.Rejected -> UdpInputReceiverResult.Rejected(decision.reason)
        }
    }

    fun onTimeout(): UdpReceivedInput? {
        return onStreamTimeout(nowElapsedNanos = 0L)
    }

    fun onStreamTimeout(nowElapsedNanos: Long): UdpReceivedInput? {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        val activeGuard = guard ?: return current
        val stale = current?.let(activeGuard::onTimeout) ?: return null
        current = stale
        lifecycleState = InputStreamLifecycleState.STALE
        onInput(stale)
        return stale
    }

    fun onControlDisconnected(nowElapsedNanos: Long) {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        if (guard == null) {
            lifecycleState = InputStreamLifecycleState.STOPPED
            return
        }
        controlDisconnectedAtNanos = nowElapsedNanos
        lifecycleState = InputStreamLifecycleState.GRACE
    }

    fun onControlReconnected(config: InputStreamConfig): UdpInputReceiver {
        val session = trustedControlSessionId ?: return this
        return start(trustedSession = session, config = config)
    }

    fun stop(reason: String = "stopped") {
        require(reason.isNotBlank()) { "reason must not be blank" }
        trustedControlSessionId = null
        guard = null
        activeConfig = null
        controlDisconnectedAtNanos = null
        lifecycleState = InputStreamLifecycleState.STOPPED
    }

    private fun controlGraceExpired(nowElapsedNanos: Long): Boolean {
        val disconnectedAt = controlDisconnectedAtNanos ?: return false
        val config = activeConfig ?: return true
        return nowElapsedNanos - disconnectedAt > config.controlDisconnectGraceMs * NANOS_PER_MILLI
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
