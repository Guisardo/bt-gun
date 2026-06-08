package com.btgun.host

import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.session.DesktopLivenessCoordinator
import com.btgun.host.session.DesktopLivenessUpdate

fun main() {
    heartbeatTimeoutClearSchedulesUdpDisconnectGrace()
}

private fun heartbeatTimeoutClearSchedulesUdpDisconnectGrace() {
    val timeoutUpdate = DesktopLivenessUpdate(
        linkState = DesktopLinkState(
            phase = DesktopLinkPhase.DISCONNECTED,
            lastControlError = DesktopLivenessCoordinator.DEFAULT_TIMEOUT_ERROR,
        ),
        shouldContinuePolling = false,
        shouldClearClient = true,
        shouldCloseClient = true,
    )

    val action = hostDesktopLivenessActionFor(timeoutUpdate)

    expectTrue("timeout schedules udp grace", action.shouldScheduleUdpDisconnectGrace)
    expectTrue("timeout clears client", action.shouldClearClient)
    expectTrue("timeout cancels liveness tick", action.shouldCancelLivenessTick)
    expectTrue("timeout closes client", action.shouldCloseClient)
    expectFalse("timeout stops polling", action.shouldContinuePolling)
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError("$label expected true")
    }
}

private fun expectFalse(label: String, actual: Boolean) {
    if (actual) {
        throw AssertionError("$label expected false")
    }
}
