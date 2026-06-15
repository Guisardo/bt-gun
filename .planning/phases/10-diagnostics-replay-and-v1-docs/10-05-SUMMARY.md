---
phase: 10-diagnostics-replay-and-v1-docs
plan: "05"
subsystem: diagnostics-export
tags: [kotlin, diagnostics, redaction, replay, evidence]

requires:
  - phase: 10-diagnostics-replay-and-v1-docs
    provides: replay fixtures, desktop diagnostic schema, Android diagnostics, and visualizer diagnostic buckets
provides:
  - Sanitized desktop diagnostic export bundle writer
  - Expanded central redaction for stream, HMAC, device identifier, and raw evidence categories
  - Phase 10 diagnostic export manifest row with replay refs and default raw exclusion
affects: [phase-10-diagnostics, desktop-companion, redaction, replay-fixtures, pack-04]

tech-stack:
  added: []
  patterns:
    - main-function Kotlin export tests registered in desktop-companion Gradle test task
    - desktop-owned DiagnosticExportWriter persists sanitized diagnostics JSONL plus manifest metadata
    - raw local evidence stays under ignored .evidence/phase10 paths and is excluded by default

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticExportTest.kt
    - docs/evidence/manifests/phase10-diagnostic-export.jsonl
    - .planning/phases/10-diagnostics-replay-and-v1-docs/10-05-SUMMARY.md
  modified:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt
    - desktop-companion/build.gradle.kts
    - .gitignore
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Diagnostic export is desktop-owned for v1 and writes sanitized diagnostics JSONL plus manifest metadata, not raw local evidence."
  - "SecretRedactor remains the central gate for pairing, stream, HMAC, device identifier, and raw evidence text before export persistence."
  - "Raw Phase 10 evidence belongs under ignored .evidence/phase10 paths; committed bundles carry replay refs and sanitized manifest pointers."

patterns-established:
  - "DiagnosticExportBundle -> DiagnosticExportWriter -> DiagnosticExportResult writes diagnostics.jsonl and manifest.json under a sanitized bundle id."
  - "Export manifests include replay_refs, app_versions, capability_statuses, manifest_ref, and raw_included=false by default."

requirements-completed: [PERF-05, PACK-04]

duration: 10 min
completed: 2026-06-15
---

# Phase 10 Plan 05: Sanitized Diagnostic Export and Redaction Gate Summary

**Replay-ready diagnostic export bundles now persist sanitized diagnostics JSONL, replay references, app/capability metadata, and manifest pointers while excluding raw evidence by default.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-15T19:17:13Z
- **Completed:** 2026-06-15T19:27:14Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Added RED export tests for bundle contents, replay refs, manifest pointer, default raw exclusion, and noisy detail rejection.
- Implemented `DiagnosticExportBundle`, `DiagnosticExportWriter`, `DiagnosticExportManifest`, and `DiagnosticExportResult`.
- Expanded `SecretRedactor` for stream/HMAC material, full device identifiers, full Bluetooth-style addresses, and raw evidence markers.
- Added sanitized `phase10-diagnostic-export.jsonl` manifest rows and ignored `.evidence/phase10/` raw sources.

## Task Commits

1. **Task 1: RED redaction and export tests** - `502510f` (test)
2. **Task 2: GREEN sanitized export writer and ignored raw boundary** - `2716cfd` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt` - Desktop-owned sanitized export writer and manifest/result models.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticExportTest.kt` - Export contract, redaction-at-write, raw-exclusion, and noise-guard tests.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt` - Central redaction rules expanded beyond pairing material.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt` - Central redactor expectations now cover diagnostic/export categories and preserve truncated suffixes.
- `desktop-companion/build.gradle.kts` - Registers `com.btgun.desktop.diagnostics.DiagnosticExportTestKt`.
- `docs/evidence/manifests/phase10-diagnostic-export.jsonl` - Sanitized export schema/contract manifest.
- `.gitignore` - Ignores raw `.evidence/phase10/` evidence sources.

## Decisions Made

- Diagnostic export stays in the desktop companion for this first v1 export path because replay fixtures, desktop diagnostics, and visualizer output are already desktop-adjacent.
- Export output is intentionally small: `diagnostics.jsonl` and `manifest.json` with replay refs, app/build versions, capability statuses, and a manifest pointer.
- Raw evidence paths can be named as local sources but are not copied by default; committed rows use sanitized refs only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added redaction-at-write acceptance**
- **Found during:** Task 2 (GREEN sanitized export writer and ignored raw boundary)
- **Issue:** The first GREEN implementation rejected redaction-capable diagnostic events instead of proving unsafe text is redacted before persistence.
- **Fix:** Added an export assertion for redaction before persistence and allowed redaction-handled event warnings while still rejecting invalid schema/timestamp/reason-code failures.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt`, `desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticExportTest.kt`
- **Verification:** Focused Gradle tests and forbidden-pattern scan passed.
- **Committed in:** `2716cfd`

**Total deviations:** 1 auto-fixed (1 missing critical).
**Impact on plan:** Required to satisfy the redaction threat model. No scope expansion beyond the planned export/redaction gate.

## Issues Encountered

- Restricted sandbox blocked Gradle file-lock socket startup. Required Gradle verification passed with approved elevated execution.
- One GREEN assertion initially expected `key=value` formatting while manifest JSON emits `"key":"value"`; the assertion was corrected before commit.

## Verification

- PASS RED: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Redactor*' --tests '*DiagnosticExport*' --no-daemon --console=plain` failed on missing `DiagnosticExportWriter` and `DiagnosticExportBundle`.
- PASS GREEN: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Redactor*' --tests '*DiagnosticExport*' --tests '*ReplayFixture*' --no-daemon --console=plain`
- PASS: `! rg -n "qr_secret|manual code|pairing_proof|stream key|HMAC key|private key|Bluetooth address|Android ID|serial|raw screenshot|raw log|[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}" desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics docs/evidence/manifests/phase10-diagnostic-export.jsonl`
- PASS: TDD gate commits exist in order: `502510f` then `2716cfd`.

## TDD Gate Compliance

- RED: `502510f test(10-05): add failing diagnostic export tests`
- GREEN: `2716cfd feat(10-05): add sanitized diagnostic export writer`
- REFACTOR: Not needed.

## Authentication Gates

None.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for `10-06-PLAN.md`. Export/redaction artifacts now provide the sanitized issue-bundle contract that the Android setup, LAN security, replay troubleshooting, and known-limits docs can reference. `PACK-04` documentation remains finalized by the docs/index plans even though this plan covers its export/security artifact slice.

## Self-Check: PASSED

- Files exist: `DiagnosticExport.kt`, `DiagnosticExportTest.kt`, and `phase10-diagnostic-export.jsonl` found.
- Commits exist: `502510f` and `2716cfd` found in git log.
- No tracked deletions were introduced by task commits.
- Stub scan found no TODO/FIXME/placeholder patterns in changed export/redaction artifacts.

---
*Phase: 10-diagnostics-replay-and-v1-docs*
*Completed: 2026-06-15*
