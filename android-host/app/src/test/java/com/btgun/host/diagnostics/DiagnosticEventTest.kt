package com.btgun.host.diagnostics

import kotlinx.serialization.json.jsonPrimitive

fun main() {
    androidDiagnosticEventJsonUsesStableSchemaAndWireNames()
    androidDiagnosticEventRejectsOrRedactsUnsafeValues()
}

private fun androidDiagnosticEventJsonUsesStableSchemaAndWireNames() {
    val event = AndroidDiagnosticEvent(
        tsElapsedNanos = 12_345L,
        domain = AndroidDiagnosticDomain.GUN_BLE,
        status = AndroidDiagnosticStatus.BLOCKED,
        reasonCode = "gun_ble.permission_blocked",
        detail = "Bluetooth connect permission blocked",
        sessionRefs = mapOf("gun_session" to "session-a1b2"),
        context = mapOf("gatt_state" to "scan_blocked"),
    )

    val body = event.toJsonBody()

    expectEquals(
        "stable diagnostic fields",
        listOf("schema", "ts_elapsed", "domain", "status", "reason_code", "detail", "session_refs", "context"),
        body.keys.toList(),
    )
    expectEquals("schema", "btgun.android.diagnostics.v1", body["schema"]?.jsonPrimitive?.content)
    expectEquals("elapsed", "12345", body["ts_elapsed"]?.jsonPrimitive?.content)
    expectEquals("domain", "gun_ble", body["domain"]?.jsonPrimitive?.content)
    expectEquals("status", "blocked", body["status"]?.jsonPrimitive?.content)
    expectEquals("reason", "gun_ble.permission_blocked", body["reason_code"]?.jsonPrimitive?.content)
    expectEquals("detail", "Bluetooth connect permission blocked", body["detail"]?.jsonPrimitive?.content)
    expectContains("session refs", body["session_refs"].toString(), "session-a1b2")
    expectContains("context", body["context"].toString(), "scan_blocked")

    expectEquals(
        "domain values",
        listOf("gun_ble", "sensor_motion", "lan_control_udp", "profile_mapping", "hid_backend_haptics"),
        AndroidDiagnosticDomain.entries.map { it.wireName },
    )
    expectEquals(
        "status values",
        listOf("ok", "degraded", "blocked", "unsupported", "unknown"),
        AndroidDiagnosticStatus.entries.map { it.wireName },
    )
}

private fun androidDiagnosticEventRejectsOrRedactsUnsafeValues() {
    expectThrows("negative timestamp") {
        AndroidDiagnosticEvent(
            tsElapsedNanos = -1L,
            domain = AndroidDiagnosticDomain.SENSOR_MOTION,
            status = AndroidDiagnosticStatus.UNKNOWN,
            reasonCode = "sensor_motion.unavailable",
            detail = "no sample",
        )
    }
    expectThrows("invalid reason code") {
        AndroidDiagnosticEvent(
            tsElapsedNanos = 1L,
            domain = AndroidDiagnosticDomain.PROFILE_MAPPING,
            status = AndroidDiagnosticStatus.DEGRADED,
            reasonCode = "Profile Mapping Has Spaces",
            detail = "fallback",
        )
    }

    val encoded = AndroidDiagnosticEvent(
        tsElapsedNanos = 2L,
        domain = AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
        status = AndroidDiagnosticStatus.UNSUPPORTED,
        reasonCode = "hid_backend_haptics.output_unsupported",
        detail = "Bluetooth address AA:BB:CC:DD:EE:FF serial SN-123456789 Android ID 1234567890abcdef",
        sessionRefs = mapOf(
            "pairing_proof" to "abc123secret",
            "stream_key" to "feedface",
            "device" to "AA:BB:CC:DD:EE:FF",
        ),
        context = mapOf(
            "qr_secret" to "secret-value",
            "hmac_key" to "hmac-value",
            "safe_hint" to "macOS output callback unsupported",
        ),
    ).toJsonBody().toString()

    listOf(
        "AA:BB:CC:DD:EE:FF",
        "SN-123456789",
        "1234567890abcdef",
        "abc123secret",
        "feedface",
        "secret-value",
        "hmac-value",
    ).forEach { forbidden ->
        expectFalse("redacts $forbidden", encoded.contains(forbidden, ignoreCase = true))
    }
    expectContains("keeps useful sanitized context", encoded, "macOS output callback unsupported")
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectContains(label: String, text: String, token: String) {
    if (!text.contains(token)) {
        throw AssertionError("$label expected <$text> to contain <$token>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError(label)
    }
}

private fun expectThrows(label: String, block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalArgumentException) {
        return
    }
    throw AssertionError("$label expected IllegalArgumentException")
}
