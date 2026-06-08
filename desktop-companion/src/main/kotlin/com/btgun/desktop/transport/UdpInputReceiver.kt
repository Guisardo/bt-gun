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
    var current: UdpReceivedInput? = null
        private set

    fun start(trustedSession: String, config: InputStreamConfig): UdpInputReceiver {
        trustedControlSessionId = trustedSession
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
        return when (val decision = activeGuard.acceptDatagram(bytes, receivedElapsedNanos, controlSessionId)) {
            is InputReplayDecision.Accepted -> {
                current = decision.input
                onInput(decision.input)
                UdpInputReceiverResult.Accepted(decision.input)
            }
            is InputReplayDecision.Rejected -> UdpInputReceiverResult.Rejected(decision.reason)
        }
    }

    fun onTimeout(): UdpReceivedInput? {
        val activeGuard = guard ?: return current
        val stale = current?.let(activeGuard::onTimeout) ?: return null
        current = stale
        onInput(stale)
        return stale
    }

    fun stop(reason: String = "stopped") {
        require(reason.isNotBlank()) { "reason must not be blank" }
        trustedControlSessionId = null
        guard = null
    }
}
