---
phase: 10-diagnostics-replay-and-v1-docs
reviewed: 2026-06-15T20:01:22Z
depth: standard
files_reviewed: 33
files_reviewed_list:
  - .gitignore
  - android-host/app/build.gradle.kts
  - android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticEvent.kt
  - android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticReporter.kt
  - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
  - android-host/app/src/test/java/com/btgun/host/diagnostics/DiagnosticEventTest.kt
  - android-host/app/src/test/java/com/btgun/host/diagnostics/DiagnosticReporterTest.kt
  - desktop-companion/build.gradle.kts
  - desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticEventTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticExportTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/docs/Phase10DocsGuardTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerModelTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt
  - docs/diagnostics/replay-and-troubleshooting.md
  - docs/evidence/manifests/phase10-diagnostic-export.jsonl
  - docs/evidence/manifests/phase10-replay-fixtures.jsonl
  - docs/evidence/manifests/phase10-v1-closeout.jsonl
  - docs/limits/v1-compatibility-limits.md
  - docs/protocol/lan-session-security-v1.md
  - docs/setup/android-build-device-testing.md
  - docs/v1.md
  - fixtures/replay/README.md
  - fixtures/replay/expected/mapped-session-001-visualizer.json
  - fixtures/replay/udp-golden/mapped-session-001.hex
  - fixtures/replay/udp-golden/mapped-session-001.jsonl
findings:
  critical: 5
  warning: 3
  info: 0
  total: 8
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-15T20:01:22Z
**Depth:** standard
**Files Reviewed:** 33
**Status:** issues_found

## Summary

Reviewed Phase 10 source, tests, docs, manifests, and replay fixtures for diagnostics/replay contracts, redaction, doc guards, and acceptance-regression risk. Main concern: diagnostic export/redaction paths still leak sensitive material in several edge cases, and the visualizer can accept the macOS haptic limitation without observed evidence.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: [BLOCKER] Diagnostic export bundle id can escape the output root

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt:61`
**Issue:** `safeToken()` allows `.` characters and only trims `-`, so a bundle id of `..` remains `..`. `outputRoot.resolve(safeBundleId)` then points at the parent directory, and the writer creates/writes `diagnostics.jsonl` and `manifest.json` outside the requested export root. This is path traversal/write-outside-root risk in a shareable diagnostic artifact writer.
**Fix:**
```kotlin
val safeBundleId = safeToken(bundle.bundleId)
    .takeUnless { it == "." || it == ".." || it.isBlank() }
    ?: "diagnostic-export"
val outputDir = outputRoot.resolve(safeBundleId).canonicalFile
require(outputDir.path.startsWith(outputRoot.canonicalFile.path + File.separator)) {
    "bundle id resolves outside output root"
}
require(outputDir.mkdirs() || outputDir.isDirectory) { "failed to create export directory" }
```

### CR-02: [BLOCKER] Desktop redaction can preserve secret values after replacing only the label

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt:9`
**Issue:** `qr_secret`, `code`, and `proof` rules are case-sensitive and exact-form only. When a diagnostic detail contains a variant such as `QR_SECRET=actualSecret` or `Proof: actualSecret`, `SecretRedactor` misses it, then `DiagnosticEvent.safeDetail()` replaces only the unsafe token name, leaving `<redacted>=actualSecret`. `DiagnosticExportWriter` explicitly tolerates redactable validation errors and persists `event.toWireMap()`, so the value can land in `diagnostics.jsonl`.
**Fix:**
```kotlin
Regex("(?i)\\b(qr[_ -]?secret\\s*[=:]\\s*)\\S+") to "$1<redacted>",
Regex("(?i)\\b(code\\s*[=:]\\s*)\\d{6}\\b") to "$1<redacted>",
Regex("(?i)\\b((?:pairing[_ -]?)?proof\\s*[=:]\\s*)\\S+") to "$1<redacted>",
```
Also make `UNSAFE_TEXT_PATTERN` include the value when used as fallback redaction, not just the field label.

### CR-03: [BLOCKER] Full stream/session identifiers pass through desktop diagnostic session refs

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt:139`
**Issue:** `safeRef()` keeps the last 32 characters and the unsafe-text pattern does not recognize long hex identifiers. A 16-byte stream id such as `00112233445566778899aabbccddeeff` is exactly 32 chars, so it is exported intact as `stream_session_ref`. The Phase 10 redaction policy allows suffix refs, not full stream/session identifiers.
**Fix:**
```kotlin
private val LONG_HEX_PATTERN = Regex("(?i)\\b[0-9a-f]{16,}\\b")

private fun safeRef(value: String?): String? =
    value?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { SecretRedactor.redact(it).replace(LONG_HEX_PATTERN) { m -> "suffix-" + m.value.takeLast(8) } }
        ?.replace(UNSAFE_TEXT_PATTERN, "<redacted>")
        ?.takeLast(16)
```

### CR-04: [BLOCKER] Android diagnostic map redaction misses Android ID keys

**File:** `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticEvent.kt:127`
**Issue:** `isSensitiveKey()` compares regex source strings as literals (`android[\\s_-]*id`) after only removing the literal `\s*`, so keys like `android_id` or `android-id` do not match unless they contain `secret`, `proof`, or `key`. A context/session ref with key `android_id` and value `pixel-123456` can be emitted because value redaction only catches MAC-like ids, long hex, and serial-shaped values.
**Fix:**
```kotlin
fun isSensitiveKey(key: String): Boolean {
    val normalized = key.lowercase().replace(Regex("[^a-z0-9]"), "")
    return normalized.contains("secret") ||
        normalized.contains("proof") ||
        normalized.contains("key") ||
        normalized.contains("androidid") ||
        normalized.contains("deviceid") ||
        normalized.contains("serial")
}
```
Add tests for `android_id`, `android-id`, and `Android ID` map keys with non-hex values.

### CR-05: [BLOCKER] Visualizer can accept macOS haptic limitation without observed evidence

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt:433`
**Issue:** `confirmLimitation(MACOS_HID_HAPTIC_LIMIT)` sets the row to `UNSUPPORTED_DEFERRED` regardless of current state. Because `isAcceptedForPass()` treats that state as passing, the checklist can pass after a user clicks "Confirm limitation" even if no macOS backend diagnostic ever marked the limitation row observed.
**Fix:**
```kotlin
fun confirmLimitation(id: VisualizerChecklistRowId): VisualizerModel =
    if (id == VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT) {
        copy(checklistRows = checklistRows.update(id) { row ->
            if (row.state == VisualizerChecklistState.OBSERVED) {
                row.copy(
                    state = VisualizerChecklistState.UNSUPPORTED_DEFERRED,
                    observedSource = row.observedSource
                        ?: "Phase 7 macOS HID haptic unsupported/deferred evidence",
                    confirmationLabel = "Confirm limitation",
                )
            } else {
                row
            }
        })
    } else {
        this
    }
```
Disable the UI action until that row is observed.

## Warnings

### WR-01: [WARNING] Desktop diagnostics allow reason codes from the wrong domain

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt:63`
**Issue:** Validation checks only the reason-code shape, not that `reasonCode` starts with `domain.wireName + "."`. A `LAN_CONTROL_UDP` event can carry `profile_mapping.mapped`, which breaks bucket routing semantics and diverges from Android diagnostics, which enforce the domain family.
**Fix:** Add `if (!reasonCode.startsWith(domain.wireName + ".")) add("reason_code domain mismatch")` and a regression test.

### WR-02: [WARNING] JSON escaping leaves control characters unescaped

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt:148`
**Issue:** `escapeJson()` handles quotes, backslash, newline, carriage return, and tab, but leaves other JSON-forbidden control characters (`\u0000` through `\u001F`) raw. A diagnostic detail containing backspace, form-feed, or NUL can produce malformed `diagnostics.jsonl` or `manifest.json`.
**Fix:** Escape every character below U+0020:
```kotlin
else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
```

### WR-03: [WARNING] Phase 10 doc guard does not scan all Phase 10 docs/manifests/fixtures

**File:** `desktop-companion/src/test/kotlin/com/btgun/desktop/docs/Phase10DocsGuardTest.kt:16`
**Issue:** `docsExcludeForbiddenEvidenceTerms()` scans only `requiredDocs`, which excludes `docs/v1.md`, `docs/evidence/manifests/phase10-*.jsonl`, and `fixtures/replay/*` even though those files are part of this Phase 10 review scope and carry redaction-policy claims. A forbidden literal can regress into those artifacts without this guard failing.
**Fix:** Expand the guarded file list to include the v1 index, Phase 10 manifests, replay README, replay JSONL, expected JSON, and hex fixture comments; keep binary/raw evidence paths ignored.

---

_Reviewed: 2026-06-15T20:01:22Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
