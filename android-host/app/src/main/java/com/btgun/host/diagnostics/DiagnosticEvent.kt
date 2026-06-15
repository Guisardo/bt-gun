package com.btgun.host.diagnostics

enum class AndroidDiagnosticDomain(val wireName: String) {
    GUN_BLE("gun_ble"),
    SENSOR_MOTION("sensor_motion"),
    LAN_CONTROL_UDP("lan_control_udp"),
    PROFILE_MAPPING("profile_mapping"),
    HID_BACKEND_HAPTICS("hid_backend_haptics"),
}

enum class AndroidDiagnosticStatus(val wireName: String) {
    OK("ok"),
    DEGRADED("degraded"),
    BLOCKED("blocked"),
    UNSUPPORTED("unsupported"),
    UNKNOWN("unknown"),
}

data class AndroidDiagnosticEvent(
    val tsElapsedNanos: Long,
    val domain: AndroidDiagnosticDomain,
    val status: AndroidDiagnosticStatus,
    val reasonCode: String,
    val detail: String,
    val sessionRefs: Map<String, String> = emptyMap(),
    val context: Map<String, String> = emptyMap(),
) {
    init {
        require(tsElapsedNanos >= 0L) { "tsElapsedNanos must be non-negative" }
        require(reasonCodePattern.matches(reasonCode)) { "reasonCode must be lowercase dotted snake case" }
        require(reasonCode.startsWith(domain.wireName + ".")) { "reasonCode must match domain family" }
    }

    fun toJsonBody(): Map<String, Any> =
        linkedMapOf(
            "schema" to SCHEMA,
            "ts_elapsed" to tsElapsedNanos,
            "domain" to domain.wireName,
            "status" to status.wireName,
            "reason_code" to reasonCode,
            "detail" to DiagnosticSanitizer.redact(detail).take(MAX_DETAIL_CHARS),
            "session_refs" to sessionRefs.redactedMap(),
            "context" to context.redactedMap(),
        )

    private fun Map<String, String>.redactedMap(): Map<String, String> =
        entries
            .sortedBy { it.key }
            .associate { (key, value) ->
                val safeKey = DiagnosticSanitizer.safeKey(key)
                val safeValue = if (DiagnosticSanitizer.isSensitiveKey(key)) {
                    REDACTED
                } else {
                    DiagnosticSanitizer.redact(value)
                }
                safeKey to safeValue.take(MAX_DETAIL_CHARS)
            }

    companion object {
        const val SCHEMA: String = "btgun.android.diagnostics.v1"
        private const val MAX_DETAIL_CHARS = 96
        private const val REDACTED = "<redacted>"
        private val reasonCodePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    }
}

data class AndroidDiagnosticSnapshot(
    val events: List<AndroidDiagnosticEvent>,
) {
    fun toDashboardDiagnostics(): DashboardDiagnostics =
        DashboardDiagnostics(
            rows = events.map { event ->
                DashboardDiagnosticRow(
                    domain = event.domain.wireName,
                    status = event.status.wireName,
                    reasonCode = event.reasonCode,
                    detail = DiagnosticSanitizer.redact(event.detail).take(96),
                )
            },
        )
}

data class DashboardDiagnostics(
    val rows: List<DashboardDiagnosticRow>,
) {
    companion object {
        fun empty(): DashboardDiagnostics =
            DashboardDiagnostics(emptyList())
    }
}

data class DashboardDiagnosticRow(
    val domain: String,
    val status: String,
    val reasonCode: String,
    val detail: String,
)

object DiagnosticSanitizer {
    private val btAddrPattern = Regex("[0-9A-Fa-f]{2}" + "(:[0-9A-Fa-f]{2}){5}")
    private val longHexPattern = Regex("\\b[0-9A-Fa-f]{12,}\\b")
    private val deviceIdPattern = Regex("\\b(SN|S" + "erial)[\\s:=_-]*[A-Za-z0-9-]{6,}\\b", RegexOption.IGNORE_CASE)
    private val keyValuePatterns = sensitiveTokens().map { token ->
        Regex("(?i)($token[\\s:=_-]+)[A-Za-z0-9_./+-]{4,}")
    }
    private val safeKeyPattern = Regex("[^A-Za-z0-9_.-]")

    fun redact(value: String): String {
        val tokenRedacted = keyValuePatterns.fold(value) { current, pattern ->
            pattern.replace(current) { match -> match.groupValues[1] + "<redacted>" }
        }
        return tokenRedacted
            .replace(btAddrPattern, "<redacted-bt-id>")
            .replace(deviceIdPattern, "$1=<redacted>")
            .replace(longHexPattern, "<redacted-id>")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "unavailable" }
    }

    fun safeKey(key: String): String =
        key.trim()
            .replace(safeKeyPattern, "_")
            .take(48)
            .ifBlank { "field" }

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace(Regex("[^a-z0-9]"), "")
        return sensitiveTokens().any { token -> normalized.contains(token.replace(Regex("[^a-z0-9]"), "")) } ||
            normalized.contains("secret") ||
            normalized.contains("proof") ||
            normalized.contains("key") ||
            normalized.contains("androidid") ||
            normalized.contains("deviceid") ||
            normalized.contains("serial")
    }

    private fun sensitiveTokens(): List<String> =
        listOf(
            "qr[\\s_-]*secret",
            "pairing[\\s_-]*proof",
            "stream[\\s_-]*key",
            "hmac[\\s_-]*key",
            "private[\\s_-]*key",
            "android[\\s_-]*id",
        )
}
