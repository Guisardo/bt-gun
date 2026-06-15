package com.btgun.desktop.diagnostics

import com.btgun.desktop.security.SecretRedactor
import java.io.File

data class DiagnosticExportBundle(
    val bundleId: String,
    val createdElapsed: Long,
    val diagnostics: List<DiagnosticEvent>,
    val replayRefs: List<String>,
    val appVersions: Map<String, String>,
    val capabilityStatuses: Map<String, String>,
    val manifestRef: String,
    val rawEvidencePaths: List<String> = emptyList(),
    val includeRawEvidence: Boolean = false,
)

data class DiagnosticExportManifest(
    val schema: String,
    val bundleId: String,
    val createdElapsed: Long,
    val diagnosticsJsonl: String,
    val replayRefs: List<String>,
    val appVersions: Map<String, String>,
    val capabilityStatuses: Map<String, String>,
    val manifestRef: String,
    val rawIncluded: Boolean,
) {
    fun toWireMap(): Map<String, Any> =
        linkedMapOf(
            "schema" to schema,
            "bundle_id" to bundleId,
            "created_elapsed" to createdElapsed,
            "diagnostics_jsonl" to diagnosticsJsonl,
            "replay_refs" to replayRefs,
            "app_versions" to appVersions,
            "capability_statuses" to capabilityStatuses,
            "manifest_ref" to manifestRef,
            "raw_included" to rawIncluded,
        )
}

data class DiagnosticExportResult(
    val outputDir: File,
    val diagnosticsJsonl: File,
    val manifestFile: File,
    val manifest: DiagnosticExportManifest,
)

class DiagnosticExportWriter(
    private val outputRoot: File,
) {
    fun write(bundle: DiagnosticExportBundle): DiagnosticExportResult {
        require(bundle.createdElapsed >= 0L) { "created_elapsed must be non-negative" }
        bundle.diagnostics.forEachIndexed { index, event ->
            require(event.detail.length <= MAX_EVENT_DETAIL_CHARS) { "diagnostic detail at index $index exceeds $MAX_EVENT_DETAIL_CHARS chars" }
            val errors = event.validationErrors().filterNot { it in REDACTABLE_EVENT_ERRORS }
            require(errors.isEmpty()) { "diagnostic event at index $index is invalid: ${errors.joinToString()}" }
        }

        val safeBundleId = safeToken(bundle.bundleId).ifBlank { "diagnostic-export" }
        val outputDir = outputRoot.resolve(safeBundleId)
        outputDir.mkdirs()

        val diagnosticsFile = outputDir.resolve("diagnostics.jsonl")
        val manifestFile = outputDir.resolve("manifest.json")
        val rawIncluded = bundle.includeRawEvidence && bundle.rawEvidencePaths.isNotEmpty()
        val manifest = DiagnosticExportManifest(
            schema = EXPORT_SCHEMA,
            bundleId = safeBundleId,
            createdElapsed = bundle.createdElapsed,
            diagnosticsJsonl = diagnosticsFile.name,
            replayRefs = bundle.replayRefs.map(::safePathRef),
            appVersions = bundle.appVersions.safeMap(),
            capabilityStatuses = bundle.capabilityStatuses.safeMap(),
            manifestRef = safePathRef(bundle.manifestRef),
            rawIncluded = rawIncluded,
        )

        diagnosticsFile.writeText(
            bundle.diagnostics.joinToString(separator = "\n", postfix = "\n") { event ->
                jsonValue(event.toWireMap().sanitizeWireMap())
            },
        )
        manifestFile.writeText(jsonValue(manifest.toWireMap()) + "\n")

        return DiagnosticExportResult(
            outputDir = outputDir,
            diagnosticsJsonl = diagnosticsFile,
            manifestFile = manifestFile,
            manifest = manifest,
        )
    }
}

private const val EXPORT_SCHEMA = "btgun.diagnostic_export.v1"
private const val MAX_EVENT_DETAIL_CHARS = 512
private const val MAX_TEXT_CHARS = 240
private val REDACTABLE_EVENT_ERRORS = setOf("unsafe detail", "unsafe context", "unsafe session_refs")

private val SAFE_TOKEN_PATTERN = Regex("[^A-Za-z0-9._-]+")
private val RAW_REF_PATTERN = Regex("(?i)\\.evidence/[^\\s\"']+")
private val MAC_PATTERN = Regex("(?i)\\b[0-9a-f]{2}(:[0-9a-f]{2}){5}\\b")

private fun Map<String, String>.safeMap(): Map<String, String> =
    entries.associateTo(linkedMapOf()) { (key, value) ->
        safeToken(key).take(MAX_TEXT_CHARS) to safeText(value)
    }

private fun Map<String, Any>.sanitizeWireMap(): Map<String, Any> =
    entries.associateTo(linkedMapOf()) { (key, value) ->
        key to when (value) {
            is String -> safeText(value)
            is Map<*, *> -> value.entries.associateTo(linkedMapOf<String, String>()) { (nestedKey, nestedValue) ->
                safeToken(nestedKey.toString()) to safeText(nestedValue.toString())
            }
            else -> value
        }
    }

private fun safePathRef(value: String): String =
    safeText(value).replace(RAW_REF_PATTERN, "<redacted-raw-evidence>")

private fun safeText(value: String): String =
    SecretRedactor.redact(value)
        .replace(RAW_REF_PATTERN, "<redacted-raw-evidence>")
        .replace(MAC_PATTERN, "<redacted-bluetooth-address>")
        .take(MAX_TEXT_CHARS)

private fun safeToken(value: String): String =
    safeText(value.trim())
        .replace(SAFE_TOKEN_PATTERN, "-")
        .trim('-')
        .take(MAX_TEXT_CHARS)

private fun jsonValue(value: Any?): String =
    when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is String -> "\"" + value.escapeJson() + "\""
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, nestedValue) ->
            jsonValue(key.toString()) + ":" + jsonValue(nestedValue)
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
        else -> jsonValue(value.toString())
    }

private fun String.escapeJson(): String =
    buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
