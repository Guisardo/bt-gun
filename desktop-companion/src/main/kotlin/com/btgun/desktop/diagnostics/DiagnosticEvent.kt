package com.btgun.desktop.diagnostics

import com.btgun.desktop.control.ControlDiagnostics
import com.btgun.desktop.security.SecretRedactor

enum class DiagnosticDomain(val wireName: String) {
    GUN_BLE("gun_ble"),
    SENSOR_MOTION("sensor_motion"),
    LAN_CONTROL_UDP("lan_control_udp"),
    PROFILE_MAPPING("profile_mapping"),
    HID_BACKEND_HAPTICS("hid_backend_haptics"),
}

enum class DiagnosticStatus(val wireName: String) {
    OK("ok"),
    DEGRADED("degraded"),
    BLOCKED("blocked"),
    UNSUPPORTED("unsupported"),
    UNKNOWN("unknown"),
}

data class DiagnosticSessionRefs(
    val controlSessionRef: String? = null,
    val streamSessionRef: String? = null,
    val desktopIdentitySuffix: String? = null,
) {
    fun toWireMap(): Map<String, String> =
        linkedMapOf<String, String>().apply {
            safeRef(controlSessionRef)?.let { put("control_session_ref", it) }
            safeRef(streamSessionRef)?.let { put("stream_session_ref", it) }
            safeRef(desktopIdentitySuffix)?.let { put("desktop_identity_suffix", it) }
        }

    fun validationErrors(): List<String> =
        toWireMap().values
            .filter { it.length > MAX_REF_CHARS || containsUnsafeText(it) }
            .map { "unsafe session_refs" }
            .distinct()
}

data class DiagnosticEvent(
    val schema: String = SCHEMA,
    val tsElapsed: Long,
    val domain: DiagnosticDomain,
    val status: DiagnosticStatus,
    val reasonCode: String,
    val detail: String,
    val sessionRefs: DiagnosticSessionRefs = DiagnosticSessionRefs(),
    val context: Map<String, String> = emptyMap(),
) {
    fun toWireMap(): Map<String, Any> =
        linkedMapOf(
            "schema" to schema,
            "ts_elapsed" to tsElapsed,
            "domain" to domain.wireName,
            "status" to status.wireName,
            "reason_code" to reasonCode,
            "detail" to safeDetail(),
            "session_refs" to sessionRefs.toWireMap(),
            "context" to safeContext(),
        )

    fun validationErrors(): List<String> =
        buildList {
            if (schema != SCHEMA) add("invalid schema")
            if (tsElapsed < 0L) add("negative ts_elapsed")
            if (!REASON_CODE_PATTERN.matches(reasonCode)) add("invalid reason_code")
            if (containsUnsafeText(detail)) add("unsafe detail")
            addAll(sessionRefs.validationErrors())
            if (safeContext().size != context.size || context.any { containsUnsafeText(it.key) || containsUnsafeText(it.value) }) {
                add("unsafe context")
            }
        }

    private fun safeDetail(): String =
        SecretRedactor.redact(detail)
            .replace(UNSAFE_TEXT_PATTERN, "<redacted>")
            .take(MAX_DETAIL_CHARS)

    private fun safeContext(): Map<String, String> =
        context.entries
            .filter { (key, value) ->
                key.length <= MAX_CONTEXT_CHARS &&
                    value.length <= MAX_CONTEXT_CHARS &&
                    REASON_CODE_PART_PATTERN.matches(key) &&
                    !containsUnsafeText(key) &&
                    !containsUnsafeText(value)
            }
            .associateTo(linkedMapOf()) { (key, value) ->
                key to SecretRedactor.redact(value).replace(UNSAFE_TEXT_PATTERN, "<redacted>").take(MAX_CONTEXT_CHARS)
            }
}

fun ControlDiagnostics.toDiagnosticEvent(tsElapsed: Long): DiagnosticEvent {
    val normalizedState = sessionState.lowercase()
    val normalizedError = lastControlError?.takeIf { it.isNotBlank() }
    val status = when {
        normalizedState in setOf("connected", "authenticated", "started", "active") && normalizedError == null -> DiagnosticStatus.OK
        normalizedState in setOf("degraded", "rate_limited") || heartbeatAgeMillis != null && heartbeatAgeMillis > 1_000L -> DiagnosticStatus.DEGRADED
        normalizedState in setOf("disconnected", "stopped", "blocked") -> DiagnosticStatus.BLOCKED
        normalizedState.isBlank() -> DiagnosticStatus.UNKNOWN
        else -> DiagnosticStatus.UNKNOWN
    }
    val reason = when (status) {
        DiagnosticStatus.OK -> "lan_control_udp.heartbeat_ok"
        DiagnosticStatus.DEGRADED -> "lan_control_udp.heartbeat_degraded"
        DiagnosticStatus.BLOCKED -> "lan_control_udp.session_blocked"
        DiagnosticStatus.UNSUPPORTED -> "lan_control_udp.unsupported"
        DiagnosticStatus.UNKNOWN -> "lan_control_udp.state_unknown"
    }
    val detail = buildList {
        add("session_state=$normalizedState")
        heartbeatAgeMillis?.let { add("heartbeat_age_ms=$it") }
        normalizedError?.let { add("last_error=${SecretRedactor.redact(it)}") }
    }.joinToString(" ")

    return DiagnosticEvent(
        tsElapsed = tsElapsed,
        domain = DiagnosticDomain.LAN_CONTROL_UDP,
        status = status,
        reasonCode = reason,
        detail = detail,
        sessionRefs = DiagnosticSessionRefs(desktopIdentitySuffix = safeRef(desktopIdentitySuffix)),
        context = mapOf("source" to "desktop_control"),
    )
}

private const val SCHEMA = "btgun.diagnostics.v1"
private const val MAX_DETAIL_CHARS = 240
private const val MAX_CONTEXT_CHARS = 80
private const val MAX_REF_CHARS = 32

private val REASON_CODE_PART_PATTERN = Regex("[a-z][a-z0-9_]*")
private val REASON_CODE_PATTERN = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
private val UNSAFE_TEXT_PATTERN = Regex(
    "(?i)(qr[_ -]?secret|pairing[_ -]?proof|stream[_ -]?key|hmac[_ -]?key|private[_ -]?key|bluetooth[_ -]?address|android[_ -]?id|raw[_ -]?screenshot|raw[_ -]?log|[0-9a-f]{2}(:[0-9a-f]{2}){5})",
)

private fun safeRef(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { SecretRedactor.redact(it).replace(UNSAFE_TEXT_PATTERN, "<redacted>").takeLast(MAX_REF_CHARS) }

private fun containsUnsafeText(value: String): Boolean =
    UNSAFE_TEXT_PATTERN.containsMatchIn(value) || SecretRedactor.redact(value) != value
