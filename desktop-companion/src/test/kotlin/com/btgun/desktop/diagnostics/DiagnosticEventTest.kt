package com.btgun.desktop.diagnostics

import com.btgun.desktop.control.ControlDiagnostics

fun main() {
    diagnosticDomainsUseLockedWireNames()
    diagnosticStatusesUseLockedWireNames()
    diagnosticEventExposesRequiredWireFields()
    diagnosticEventValidationRejectsUnsafeValues()
    diagnosticEventRejectsWrongDomainReasonCode()
    sessionRefsExposeRedactedFieldsOnly()
    sessionRefsTruncateLongHexIdentifiers()
    controlDiagnosticsProducesLanControlDiagnosticEvent()
}

private fun diagnosticDomainsUseLockedWireNames() {
    expectEquals(
        "domain wire names",
        listOf("gun_ble", "sensor_motion", "lan_control_udp", "profile_mapping", "hid_backend_haptics"),
        DiagnosticDomain.entries.map { it.wireName },
    )
}

private fun diagnosticStatusesUseLockedWireNames() {
    expectEquals(
        "status wire names",
        listOf("ok", "degraded", "blocked", "unsupported", "unknown"),
        DiagnosticStatus.entries.map { it.wireName },
    )
}

private fun diagnosticEventExposesRequiredWireFields() {
    val event = validEvent()

    expectEquals(
        "required fields",
        listOf("schema", "ts_elapsed", "domain", "status", "reason_code", "detail", "session_refs", "context"),
        event.toWireMap().keys.toList(),
    )
    expectEquals("schema", "btgun.diagnostics.v1", event.toWireMap()["schema"])
    expectEquals("domain", "lan_control_udp", event.toWireMap()["domain"])
    expectEquals("status", "degraded", event.toWireMap()["status"])
    expectEquals("valid event", emptyList<String>(), event.validationErrors())
}

private fun diagnosticEventValidationRejectsUnsafeValues() {
    val invalid = listOf(
        validEvent(tsElapsed = -1L),
        validEvent(reasonCode = "lan_control_udp.heartbeat degraded"),
        validEvent(detail = "stream_key=abcdefghijklmnopqrstuvwxyzABCDEF"),
    )

    expectEquals(
        "validation errors",
        listOf("negative ts_elapsed", "invalid reason_code", "unsafe detail"),
        invalid.map { it.validationErrors().single() },
    )
}

private fun diagnosticEventRejectsWrongDomainReasonCode() {
    val event = validEvent(reasonCode = "profile_mapping.mapped")

    expectEquals("domain mismatch", listOf("reason_code domain mismatch"), event.validationErrors())
}

private fun sessionRefsExposeRedactedFieldsOnly() {
    val refs = DiagnosticSessionRefs(
        controlSessionRef = "sid-suffix-11223344",
        streamSessionRef = "stream-suffix-aabbccdd",
        desktopIdentitySuffix = "desktop-77889900",
    )

    expectEquals(
        "session refs",
        mapOf(
            "control_session_ref" to "sid-suffix-11223344",
            "stream_session_ref" to "stream-suffix-aabbccdd",
            "desktop_identity_suffix" to "desktop-77889900",
        ),
        refs.toWireMap(),
    )
    expectFalse("no raw session id", refs.toWireMap().values.any { it.length > 32 })
}

private fun sessionRefsTruncateLongHexIdentifiers() {
    val refs = DiagnosticSessionRefs(
        streamSessionRef = "00112233445566778899aabbccddeeff",
    )

    expectEquals("stream ref suffix only", "suffix-ccddeeff", refs.toWireMap()["stream_session_ref"])
    expectEquals("safe refs validate", emptyList<String>(), refs.validationErrors())
}

private fun controlDiagnosticsProducesLanControlDiagnosticEvent() {
    val diagnostics = ControlDiagnostics(
        sessionState = "degraded",
        desktopIdentitySuffix = "11223344",
        heartbeatAgeMillis = 2_400L,
        lastControlError = "heartbeat timeout",
    )

    val event = diagnostics.toDiagnosticEvent(tsElapsed = 10_000L)

    expectEquals("domain", DiagnosticDomain.LAN_CONTROL_UDP, event.domain)
    expectEquals("status", DiagnosticStatus.DEGRADED, event.status)
    expectEquals("reason", "lan_control_udp.heartbeat_degraded", event.reasonCode)
    expectEquals("desktop suffix", "11223344", event.sessionRefs.desktopIdentitySuffix)
    expectEquals("valid control event", emptyList<String>(), event.validationErrors())
}

private fun validEvent(
    tsElapsed: Long = 10_000L,
    reasonCode: String = "lan_control_udp.heartbeat_degraded",
    detail: String = "heartbeat age 2400ms",
): DiagnosticEvent =
    DiagnosticEvent(
        tsElapsed = tsElapsed,
        domain = DiagnosticDomain.LAN_CONTROL_UDP,
        status = DiagnosticStatus.DEGRADED,
        reasonCode = reasonCode,
        detail = detail,
        sessionRefs = DiagnosticSessionRefs(
            controlSessionRef = "sid-suffix-11223344",
            streamSessionRef = "stream-suffix-aabbccdd",
            desktopIdentitySuffix = "desktop-77889900",
        ),
        context = mapOf("source" to "desktop_control"),
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError("$label expected false")
    }
}
