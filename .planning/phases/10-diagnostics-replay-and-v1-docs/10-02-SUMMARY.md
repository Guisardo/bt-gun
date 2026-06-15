---
phase: 10-diagnostics-replay-and-v1-docs
plan: "02"
subsystem: desktop-diagnostics
tags: [kotlin, diagnostics, control, redaction, tdd]

requires:
  - phase: 10-diagnostics-replay-and-v1-docs
    provides: PERF-04 replay fixtures and Android diagnostic vocabulary slice
provides:
  - Desktop diagnostic event schema with fixed domain and status vocabulary
  - Desktop control diagnostics adapter for LAN/control UDP diagnostic events
  - Schema validation tests for wire names, reason codes, safe detail, and redacted refs
affects: [phase-10-diagnostics, desktop-companion, control-diagnostics, perf-05, pack-04]

tech-stack:
  added: []
  patterns:
    - main-function desktop diagnostics tests registered in Gradle
    - fixed diagnostic enums with wire names
    - diagnostic validation before rendering/export consumption

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticEventTest.kt
  modified:
    - desktop-companion/build.gradle.kts
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Desktop diagnostics keep ControlDiagnostics source-compatible by adding a toDiagnosticEvent adapter extension instead of changing the existing data class shape."
  - "DiagnosticEvent wire output uses stable lower-snake field names while Kotlin properties stay idiomatic camelCase."
  - "Global PERF-05 and PACK-04 remain open until later Phase 10 visualizer/export/docs plans complete their slices."

patterns-established:
  - "Desktop DiagnosticEvent schema: fixed domain/status enums, lower-snake reason-code validation, sanitized detail/context, and suffix-only session refs."

requirements-completed: [PERF-05, PACK-04]

duration: 5 min
completed: 2026-06-15
---

# Phase 10 Plan 02: Desktop Diagnostic Event Schema and Control Adapter Summary

**Desktop diagnostic schema for LAN/control UDP events with fixed machine-readable domains, statuses, reason codes, and redacted session references.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-15T18:42:13Z
- **Completed:** 2026-06-15T18:46:55Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added RED tests for locked desktop diagnostic domain/status wire values, required schema fields, validation failures, safe session refs, and control adapter behavior.
- Implemented `DiagnosticEvent`, `DiagnosticDomain`, `DiagnosticStatus`, and `DiagnosticSessionRefs`.
- Added `ControlDiagnostics.toDiagnosticEvent(tsElapsed)` for `lan_control_udp` status/reason-code events without changing existing callers.
- Verified focused desktop diagnostics/control tests and the forbidden-source scan.

## Task Commits

1. **Task 1: RED desktop DiagnosticEvent schema tests** - `285df7f` (test)
2. **Task 2: GREEN desktop DiagnosticEvent schema and control adapter** - `4fec31c` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt` - Desktop diagnostic schema, validation, safe wire-map helpers, and `ControlDiagnostics` adapter.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticEventTest.kt` - Main-function schema and adapter tests.
- `desktop-companion/build.gradle.kts` - Registers `com.btgun.desktop.diagnostics.DiagnosticEventTestKt`.
- `.planning/STATE.md` - Phase 10 progress, metric, session, and decision update.
- `.planning/ROADMAP.md` - Phase 10 plan progress update.

## Decisions Made

- Kept `ControlDiagnostics` source-compatible and added a diagnostic adapter extension in the diagnostics package.
- Used stable lower-snake wire keys and reason codes for export/rendering consumers.
- Left global `PERF-05` and `PACK-04` pending in `REQUIREMENTS.md`; this plan provides the desktop schema/control basis, while Plans 10-04 through 10-07 own visualizer, export, docs, and validation closeout.

## Deviations from Plan

None - plan executed exactly as written.

**Total deviations:** 0 auto-fixed.
**Impact on plan:** No scope changes.

## Issues Encountered

- Sandbox execution blocked Gradle file-lock/socket startup. The same focused Gradle command passed with approved elevated execution.

## Verification

- PASS: RED run failed on missing planned symbols: `DiagnosticDomain`, `DiagnosticStatus`, `DiagnosticEvent`, `DiagnosticSessionRefs`, `validationErrors`, `toWireMap`, and `toDiagnosticEvent`.
- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*DiagnosticEvent*' --tests '*ControlChannel*' --no-daemon --console=plain`
- PASS: `rg -n "qr_secret|manual code|pairing_proof|stream key|HMAC key|private key|Bluetooth address|Android ID|serial" desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics` returned no matches.

## TDD Gate Compliance

- RED: `285df7f test(10-02): add failing desktop diagnostic schema tests`
- GREEN: `4fec31c feat(10-02): implement desktop diagnostic events`
- REFACTOR: Not needed.

## Authentication Gates

None.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for `10-04-PLAN.md`. Desktop diagnostic events now expose stable schema values for the visualizer rendering, export, and documentation plans.

## Self-Check: PASSED

- Files exist: both task-created diagnostic files found.
- Commits exist: `285df7f` and `4fec31c` found in git log.
- No tracked deletions were introduced by task commits.
- Stub scan found no TODO/FIXME/placeholder patterns in changed diagnostic files.

---
*Phase: 10-diagnostics-replay-and-v1-docs*
*Completed: 2026-06-15*
