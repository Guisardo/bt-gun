package com.btgun.host

import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.session.DesktopLivenessCoordinator
import com.btgun.host.session.DesktopLivenessUpdate
import com.btgun.host.transport.InputStreamLifecycleState

fun main() {
    heartbeatTimeoutClearSchedulesUdpDisconnectGrace()
    controlDisconnectWithoutUdpStreamStaysStopped()
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

private fun controlDisconnectWithoutUdpStreamStaysStopped() {
    expectEquals(
        "no sender stays stopped",
        InputStreamLifecycleState.STOPPED,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = false,
            hasConfig = false,
            controlDisconnectGraceMs = null,
        ),
    )
    expectEquals(
        "missing config stays stopped",
        InputStreamLifecycleState.STOPPED,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = false,
            controlDisconnectGraceMs = null,
        ),
    )
    expectEquals(
        "zero grace marks active stream stale",
        InputStreamLifecycleState.STALE,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = true,
            controlDisconnectGraceMs = 0L,
        ),
    )
    expectEquals(
        "positive grace enters grace",
        InputStreamLifecycleState.GRACE,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = true,
            controlDisconnectGraceMs = 1_500L,
        ),
    )
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
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
