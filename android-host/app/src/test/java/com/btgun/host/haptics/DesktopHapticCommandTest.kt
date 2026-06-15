package com.btgun.host.haptics

fun main() {
    resultStatusesCoverAllPhaseFourOutcomes()
    expiredCommandDoesNotStartPhoneVibration()
    unsupportedPatternDoesNotStartPhoneVibration()
    validPulseStartsImmediately()
    secondValidCommandCancelsActivePulseBeforeStarting()
    zeroStrengthCommandCancelsWithoutStartingNewPulse()
    sessionChangeReportsCancelledResultOnlyForActivePulse()
    sessionChangeAfterPulseEndDoesNotReportCancelled()
    expiredReplacementDoesNotForgetActivePulse()
    permissionAndRuntimeFailuresMapToExplicitStatuses()
    invalidCommandReturnsFailedWithoutVibration()
}

private fun resultStatusesCoverAllPhaseFourOutcomes() {
    expectEquals(
        "status wires",
        listOf("started", "expired", "unsupported", "permission_blocked", "failed", "cancelled"),
        HapticResultStatus.entries.map { it.wireName },
    )
}

private fun expiredCommandDoesNotStartPhoneVibration() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-expired",
            strength = 0.5,
            durationMs = 80L,
            ttlMs = 10L,
        ),
        receivedElapsedNanos = 900_000_000L,
    )

    expectEquals("expired status", HapticResultStatus.EXPIRED, result.status)
    expectEquals("expired no pulse", emptyList<PhoneCall>(), phone.calls)
}

private fun unsupportedPatternDoesNotStartPhoneVibration() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-pattern",
            strength = 0.5,
            durationMs = 80L,
            ttlMs = 500L,
            pattern = "double",
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("pattern status", HapticResultStatus.UNSUPPORTED, result.status)
    expectEquals("pattern no pulse", emptyList<PhoneCall>(), phone.calls)
}

private fun validPulseStartsImmediately() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-start",
            strength = 0.75,
            durationMs = 120L,
            ttlMs = 500L,
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("started status", HapticResultStatus.STARTED, result.status)
    expectEquals("observed time", 1_000_000_000L, result.observedElapsedNanos)
    expectEquals("phone calls", listOf(PhoneCall.Pulse(120L, 0.75)), phone.calls)
}

private fun secondValidCommandCancelsActivePulseBeforeStarting() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    executor.handle(
        command = DesktopHapticCommand("cmd-first", strength = 0.25, durationMs = 300L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )
    val second = executor.handle(
        command = DesktopHapticCommand("cmd-second", strength = 1.0, durationMs = 90L, ttlMs = 500L),
        receivedElapsedNanos = 999_950_000L,
    )

    expectEquals("second status", HapticResultStatus.STARTED, second.status)
    expectEquals(
        "cancel before new pulse",
        listOf(
            PhoneCall.Pulse(300L, 0.25),
            PhoneCall.Cancel,
            PhoneCall.Pulse(90L, 1.0),
        ),
        phone.calls,
    )
}

private fun zeroStrengthCommandCancelsWithoutStartingNewPulse() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    executor.handle(
        command = DesktopHapticCommand("cmd-first", strength = 0.25, durationMs = 300L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )
    val stopped = executor.handle(
        command = DesktopHapticCommand("cmd-stop", strength = 0.0, durationMs = 1L, ttlMs = 500L),
        receivedElapsedNanos = 999_950_000L,
    )

    expectEquals("stop status", HapticResultStatus.CANCELLED, stopped.status)
    expectEquals(
        "stop cancels active without new pulse",
        listOf(
            PhoneCall.Pulse(300L, 0.25),
            PhoneCall.Cancel,
        ),
        phone.calls,
    )
}

private fun sessionChangeReportsCancelledResultOnlyForActivePulse() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    expectEquals("no active cancel result", null, executor.onSessionChanged("first-session"))
    executor.handle(
        command = DesktopHapticCommand("cmd-active", strength = 0.5, durationMs = 200L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )
    val cancelled = executor.onSessionChanged("next-session")

    expectEquals("cancel command", "cmd-active", cancelled?.commandId)
    expectEquals("cancel status", HapticResultStatus.CANCELLED, cancelled?.status)
    expectEquals("cancel detail", "phone pulse cancelled", cancelled?.detail)
    expectEquals(
        "pulse then cancel",
        listOf(PhoneCall.Pulse(200L, 0.5), PhoneCall.Cancel),
        phone.calls,
    )
}

private fun sessionChangeAfterPulseEndDoesNotReportCancelled() {
    val phone = RecordingPhoneHapticActuator()
    var now = 1_000_000_000L
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { now })

    executor.handle(
        command = DesktopHapticCommand("cmd-short", strength = 0.5, durationMs = 80L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )
    now += 81_000_000L
    val cancelled = executor.onSessionChanged("next-session")

    expectEquals("expired pulse has no cancel result", null, cancelled)
    expectEquals("completed pulse not cancelled", listOf(PhoneCall.Pulse(80L, 0.5)), phone.calls)
}

private fun expiredReplacementDoesNotForgetActivePulse() {
    val phone = RecordingPhoneHapticActuator()
    var now = 1_000_000_000L
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { now })

    executor.handle(
        command = DesktopHapticCommand("cmd-active", strength = 0.5, durationMs = 300L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )
    now += 50_000_000L
    val expired = executor.handle(
        command = DesktopHapticCommand("cmd-expired", strength = 1.0, durationMs = 80L, ttlMs = 1L),
        receivedElapsedNanos = 1_000_000_000L,
    )
    val cancelled = executor.onSessionChanged("next-session")

    expectEquals("replacement expired", HapticResultStatus.EXPIRED, expired.status)
    expectEquals("active command still cancellable", "cmd-active", cancelled?.commandId)
    expectEquals(
        "expired replacement does not cancel or forget active pulse",
        listOf(PhoneCall.Pulse(300L, 0.5), PhoneCall.Cancel),
        phone.calls,
    )
}

private fun permissionAndRuntimeFailuresMapToExplicitStatuses() {
    val permission = DesktopHapticCommandExecutor(
        phone = RecordingPhoneHapticActuator(startResult = PhoneHapticStartResult.PermissionBlocked),
        elapsedRealtimeNanos = { 1_000_000_000L },
    ).handle(
        command = DesktopHapticCommand("cmd-perm", strength = 0.5, durationMs = 80L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )
    val failed = DesktopHapticCommandExecutor(
        phone = RecordingPhoneHapticActuator(startResult = PhoneHapticStartResult.Failed("RuntimeException")),
        elapsedRealtimeNanos = { 1_000_000_000L },
    ).handle(
        command = DesktopHapticCommand("cmd-fail", strength = 0.5, durationMs = 80L, ttlMs = 500L),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("permission status", HapticResultStatus.PERMISSION_BLOCKED, permission.status)
    expectEquals("failed status", HapticResultStatus.FAILED, failed.status)
}

private fun invalidCommandReturnsFailedWithoutVibration() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "",
            strength = 0.5,
            durationMs = 80L,
            ttlMs = 500L,
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("invalid status", HapticResultStatus.FAILED, result.status)
    expectEquals("invalid no pulse", emptyList<PhoneCall>(), phone.calls)
}

private class RecordingPhoneHapticActuator(
    private val startResult: PhoneHapticStartResult = PhoneHapticStartResult.Started,
) : PhoneHapticActuator {
    val calls = mutableListOf<PhoneCall>()

    override fun pulse(durationMs: Long, strength: Double): PhoneHapticStartResult {
        calls += PhoneCall.Pulse(durationMs, strength)
        return startResult
    }

    override fun cancel(): HapticResultStatus {
        calls += PhoneCall.Cancel
        return HapticResultStatus.CANCELLED
    }
}

private sealed interface PhoneCall {
    data class Pulse(val durationMs: Long, val strength: Double) : PhoneCall
    data object Cancel : PhoneCall
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
