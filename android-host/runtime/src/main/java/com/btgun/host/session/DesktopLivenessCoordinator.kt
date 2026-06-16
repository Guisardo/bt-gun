package com.btgun.host.session

data class DesktopLivenessUpdate(
    val linkState: DesktopLinkState,
    val shouldContinuePolling: Boolean,
    val shouldClearClient: Boolean,
    val shouldCloseClient: Boolean,
)

class DesktopLivenessCoordinator(
    private val timeoutError: String = DEFAULT_TIMEOUT_ERROR,
) {
    private var activeClient: DesktopControlClient? = null

    fun start(client: DesktopControlClient) {
        activeClient = client
    }

    fun stop(client: DesktopControlClient? = null) {
        if (client == null || activeClient === client) {
            activeClient = null
        }
    }

    fun isActiveClient(client: DesktopControlClient): Boolean =
        activeClient === client

    fun refresh(
        client: DesktopControlClient,
        currentState: DesktopLinkState,
        nowElapsedNanos: Long,
    ): DesktopLivenessUpdate {
        if (activeClient !== client) {
            return DesktopLivenessUpdate(
                linkState = currentState,
                shouldContinuePolling = false,
                shouldClearClient = false,
                shouldCloseClient = false,
            )
        }

        val before = client.currentLinkState()
        val refreshed = client.refreshLiveness(nowElapsedNanos)
        val timedOut = before.phase != DesktopLinkPhase.DISCONNECTED &&
            refreshed.phase == DesktopLinkPhase.DISCONNECTED
        val disconnected = refreshed.phase == DesktopLinkPhase.DISCONNECTED
        val merged = currentState.copy(
            phase = refreshed.phase,
            desktopDisplayName = currentState.desktopDisplayName ?: refreshed.desktopDisplayName,
            fingerprintSuffix = currentState.fingerprintSuffix ?: refreshed.fingerprintSuffix,
            heartbeatAgeMillis = refreshed.heartbeatAgeMillis,
            lastControlError = when {
                timedOut -> timeoutError
                refreshed.lastControlError != null -> refreshed.lastControlError
                else -> currentState.lastControlError
            },
            profileDisplayName = currentState.profileDisplayName ?: refreshed.profileDisplayName,
            profileRevision = currentState.profileRevision ?: refreshed.profileRevision,
        )

        if (disconnected) {
            activeClient = null
        }

        return DesktopLivenessUpdate(
            linkState = merged,
            shouldContinuePolling = !disconnected,
            shouldClearClient = disconnected,
            shouldCloseClient = timedOut,
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_ERROR: String = "Desktop heartbeat timed out"
    }
}
