package com.btgun.host.haptics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

fun main() {
    resultStatusesCoverAllPhaseFourOutcomes()
    sharedMatrixHapticRowsProduceExpectedStatuses()
    expiredCommandDoesNotStartPhoneVibration()
    unsupportedPatternDoesNotStartPhoneVibration()
    timelineCommandValidatesAndStartsPatternWhenSupported()
    unsupportedTimelineFallsBackToPulse()
    zeroStrengthTimelineFallbackCancelsWithoutPulse()
    invalidTimelineFailsWithoutVibration()
    overlappingTimelineFailsWithoutVibration()
    nonNullPatternIsUnsupportedBeforeTimelineOrPulse()
    validPulseStartsImmediately()
    secondValidCommandCancelsActivePulseBeforeStarting()
    zeroStrengthCommandCancelsWithoutStartingNewPulse()
    sessionChangeReportsCancelledResultOnlyForActivePulse()
    sessionChangeAfterPulseEndDoesNotReportCancelled()
    expiredReplacementDoesNotForgetActivePulse()
    permissionAndRuntimeFailuresMapToExplicitStatuses()
    invalidCommandReturnsFailedWithoutVibration()
}

private fun sharedMatrixHapticRowsProduceExpectedStatuses() {
    val rows = replayMatrixHapticRows()
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    expectEquals(
        "haptic matrix categories",
        setOf("haptic_invalid_body", "haptic_unsupported_pattern", "haptic_invalid_timeline", "haptic_overlapping_timeline"),
        rows.map { row -> row.stringField("category") }.toSet(),
    )
    rows.forEach { row ->
        val command = DesktopHapticCommand.fromJsonBody(row["body"]?.jsonObject ?: error("missing body"))
            ?: error("matrix command did not parse: ${row.stringField("case_id")}")
        val expected = HapticResultStatus.fromWireName(row.stringField("expected_status"))
            ?: error("unknown expected status")
        val result = executor.handle(command, receivedElapsedNanos = 999_900_000L)

        expectEquals(row.stringField("case_id"), expected, result.status)
    }
}

private fun timelineCommandValidatesAndStartsPatternWhenSupported() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })
    val timeline = listOf(
        HapticTimelinePulse(atMs = 0L, durationMs = 40L, strength = 0.5),
        HapticTimelinePulse(atMs = 100L, durationMs = 60L, strength = 1.0),
    )

    val parsed = DesktopHapticCommand.fromJsonBody(
        DesktopHapticCommand(
            commandId = "cmd-timeline",
            strength = 0.5,
            durationMs = 1L,
            ttlMs = 500L,
            patternTimeline = timeline,
        ).toJsonBody(),
    )
    val result = executor.handle(requireNotNull(parsed), receivedElapsedNanos = 999_900_000L)

    expectEquals("timeline status", HapticResultStatus.STARTED, result.status)
    expectEquals("timeline call", listOf(PhoneCall.Pattern(timeline)), phone.calls)
}

private fun unsupportedTimelineFallsBackToPulse() {
    val phone = RecordingPhoneHapticActuator(patternResult = PhoneHapticStartResult.Unsupported)
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })
    val timeline = listOf(HapticTimelinePulse(atMs = 0L, durationMs = 40L, strength = 0.5))

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-timeline-fallback",
            strength = 0.6,
            durationMs = 90L,
            ttlMs = 500L,
            patternTimeline = timeline,
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("fallback status", HapticResultStatus.STARTED, result.status)
    expectEquals("fallback calls", listOf(PhoneCall.Pattern(timeline), PhoneCall.Pulse(90L, 0.6)), phone.calls)
}

private fun zeroStrengthTimelineFallbackCancelsWithoutPulse() {
    val phone = RecordingPhoneHapticActuator(patternResult = PhoneHapticStartResult.Unsupported)
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-timeline-zero",
            strength = 0.0,
            durationMs = 90L,
            ttlMs = 500L,
            patternTimeline = listOf(HapticTimelinePulse(atMs = 0L, durationMs = 40L, strength = 0.5)),
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("zero timeline status", HapticResultStatus.CANCELLED, result.status)
    expectEquals("zero timeline cancels before timeline fallback", listOf(PhoneCall.Cancel), phone.calls)
}

private fun invalidTimelineFailsWithoutVibration() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-bad-timeline",
            strength = 0.5,
            durationMs = 1L,
            ttlMs = 500L,
            patternTimeline = listOf(HapticTimelinePulse(atMs = 1_950L, durationMs = 100L, strength = 0.5)),
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("bad timeline status", HapticResultStatus.FAILED, result.status)
    expectEquals("bad timeline no calls", emptyList<PhoneCall>(), phone.calls)
}

private fun overlappingTimelineFailsWithoutVibration() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-overlap",
            strength = 0.5,
            durationMs = 1L,
            ttlMs = 500L,
            patternTimeline = listOf(
                HapticTimelinePulse(atMs = 0L, durationMs = 100L, strength = 0.5),
                HapticTimelinePulse(atMs = 50L, durationMs = 100L, strength = 0.5),
            ),
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("overlap status", HapticResultStatus.FAILED, result.status)
    expectEquals("overlap no calls", emptyList<PhoneCall>(), phone.calls)
}

private fun nonNullPatternIsUnsupportedBeforeTimelineOrPulse() {
    val phone = RecordingPhoneHapticActuator()
    val executor = DesktopHapticCommandExecutor(phone = phone, elapsedRealtimeNanos = { 1_000_000_000L })

    val result = executor.handle(
        command = DesktopHapticCommand(
            commandId = "cmd-pattern-priority",
            strength = 0.7,
            durationMs = 90L,
            ttlMs = 500L,
            pattern = "double",
            patternTimeline = listOf(HapticTimelinePulse(atMs = 0L, durationMs = 40L, strength = 1.0)),
        ),
        receivedElapsedNanos = 999_900_000L,
    )

    expectEquals("pattern priority status", HapticResultStatus.UNSUPPORTED, result.status)
    expectEquals("pattern priority no calls", emptyList<PhoneCall>(), phone.calls)
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
    private val patternResult: PhoneHapticStartResult = startResult,
) : PhoneHapticActuator {
    val calls = mutableListOf<PhoneCall>()

    override fun pulse(durationMs: Long, strength: Double): PhoneHapticStartResult {
        calls += PhoneCall.Pulse(durationMs, strength)
        return startResult
    }

    override fun patternTimeline(timeline: List<HapticTimelinePulse>): PhoneHapticStartResult {
        calls += PhoneCall.Pattern(timeline)
        return patternResult
    }

    override fun cancel(): HapticResultStatus {
        calls += PhoneCall.Cancel
        return HapticResultStatus.CANCELLED
    }
}

private sealed interface PhoneCall {
    data class Pulse(val durationMs: Long, val strength: Double) : PhoneCall
    data class Pattern(val timeline: List<HapticTimelinePulse>) : PhoneCall
    data object Cancel : PhoneCall
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun replayMatrixHapticRows(): List<JsonObject> {
    val file = repoFile("fixtures/replay/udp-golden/input-stream-v1-v2-matrix.jsonl")
    if (!file.exists()) {
        throw AssertionError("missing shared replay matrix fixture: ${file.path}")
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { Json.parseToJsonElement(it).jsonObject }
        .filter { row -> row.stringField("record_type") == "haptic" }
}

private fun repoFile(path: String): File =
    listOf(File(path), File("../$path"), File("../../$path"))
        .firstOrNull { it.exists() }
        ?: File(path)

private fun JsonObject.stringField(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull
        ?: throw AssertionError("missing string field $name")
