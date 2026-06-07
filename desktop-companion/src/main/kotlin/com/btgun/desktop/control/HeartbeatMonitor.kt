package com.btgun.desktop.control

enum class LivenessState {
    CONNECTED,
    DEGRADED,
    DISCONNECTED,
}

class HeartbeatMonitor(
    private val connectedTimeoutNanos: Long = DEFAULT_CONNECTED_TIMEOUT_NANOS,
    private val disconnectedTimeoutNanos: Long = DEFAULT_DISCONNECTED_TIMEOUT_NANOS,
) {
    private var lastObservedElapsedNanos: Long? = null

    init {
        require(connectedTimeoutNanos > 0L) { "connectedTimeoutNanos must be positive" }
        require(disconnectedTimeoutNanos > connectedTimeoutNanos) {
            "disconnectedTimeoutNanos must be greater than connectedTimeoutNanos"
        }
    }

    fun observePing(nowElapsedNanos: Long) {
        observeHeartbeat(nowElapsedNanos)
    }

    fun observePong(nowElapsedNanos: Long) {
        observeHeartbeat(nowElapsedNanos)
    }

    fun stateAt(nowElapsedNanos: Long): LivenessState {
        val age = ageNanosAt(nowElapsedNanos) ?: return LivenessState.DISCONNECTED
        return when {
            age <= connectedTimeoutNanos -> LivenessState.CONNECTED
            age <= disconnectedTimeoutNanos -> LivenessState.DEGRADED
            else -> LivenessState.DISCONNECTED
        }
    }

    fun heartbeatAgeMillisAt(nowElapsedNanos: Long): Long? =
        ageNanosAt(nowElapsedNanos)?.div(NANOS_PER_MILLI)

    private fun observeHeartbeat(nowElapsedNanos: Long) {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        lastObservedElapsedNanos = nowElapsedNanos
    }

    private fun ageNanosAt(nowElapsedNanos: Long): Long? =
        lastObservedElapsedNanos?.let { (nowElapsedNanos - it).coerceAtLeast(0L) }

    companion object {
        const val DEFAULT_CONNECTED_TIMEOUT_NANOS = 1_000_000_000L
        const val DEFAULT_DISCONNECTED_TIMEOUT_NANOS = 3_000_000_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
