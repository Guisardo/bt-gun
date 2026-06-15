---
phase: 10-diagnostics-replay-and-v1-docs
plan: "01"
subsystem: testing
tags: [kotlin, gradle, replay, udp, visualizer, diagnostics]

requires:
  - phase: 09-visualizer-acceptance-path
    provides: visualizer model, checklist rows, latency metrics, and accepted Phase 9 UAT path
provides:
  - PERF-04 replay fixture corpus under fixtures/replay
  - full-chain desktop replay test through UDP receiver, mapped state, visualizer model, and metrics
  - sanitized Phase 10 replay provenance manifest
affects: [phase-10-diagnostics, replay-fixtures, desktop-companion-tests, v1-docs]

tech-stack:
  added: []
  patterns:
    - main-function Kotlin replay tests registered in desktop-companion Gradle test task
    - hex-only UDP replay fixtures with sanitized JSONL and expected visualizer JSON
    - evidence manifest rows linking replay fixtures to sanitized provenance

key-files:
  created:
    - fixtures/replay/README.md
    - fixtures/replay/udp-golden/mapped-session-001.hex
    - fixtures/replay/udp-golden/mapped-session-001.jsonl
    - fixtures/replay/expected/mapped-session-001-visualizer.json
    - docs/evidence/manifests/phase10-replay-fixtures.jsonl
    - desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt
  modified:
    - desktop-companion/build.gradle.kts
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Use a small hex-only replay corpus for the first PERF-04 fixture to keep diffs reviewable and avoid raw capture dependencies."
  - "Replay tests must route committed datagrams through UdpInputReceiver before UdpControllerStateAdapter and VisualizerModel assertions."
  - "Expected replay timing records UDP-estimated offset quality instead of direct Android-to-desktop monotonic subtraction."

patterns-established:
  - "Replay fixture contract: raw UDP hex + sanitized JSONL + expected visualizer JSON + provenance manifest."
  - "Fixture path resolution supports both repo-root and desktop-companion Gradle working directories."

requirements-completed: [PERF-04]

duration: 9 min
completed: 2026-06-15
---

# Phase 10 Plan 01: Replay Fixtures and Full-Chain Visualizer Replay Test Summary

**PERF-04 replay slice with authenticated UDP hex fixtures driving receiver guard, mapped controller state, visualizer rows, and latency/packet metrics.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-06-15T17:25:46Z
- **Completed:** 2026-06-15T17:34:34Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Added `mapped-session-001` replay corpus with two authenticated UDP datagrams, sanitized JSONL notes, and expected visualizer output.
- Added `ReplayFixtureTest.kt` to prove datagrams pass `UdpInputReceiver` before mapping and update `VisualizerModel`/`VisualizerMetrics`.
- Linked replay artifacts to sanitized Phase 4/Phase 9 provenance in `phase10-replay-fixtures.jsonl`.
- Marked `PERF-04` complete and advanced Phase 10 roadmap/state to 1/7 plans.

## Task Commits

1. **Task 1: RED full-chain replay fixture test** - `6a20f53` (test)
2. **Task 2: GREEN replay corpus and expected visualizer snapshot** - `6047aa6` (feat)

## Files Created/Modified

- `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt` - Full-chain replay assertions from fixture files through receiver, adapter, model, and metrics.
- `desktop-companion/build.gradle.kts` - Registers `com.btgun.desktop.replay.ReplayFixtureTestKt`.
- `fixtures/replay/README.md` - Fixture layout and redaction policy.
- `fixtures/replay/udp-golden/mapped-session-001.hex` - Two reviewable authenticated UDP datagrams.
- `fixtures/replay/udp-golden/mapped-session-001.jsonl` - Sanitized schema/provenance replay notes.
- `fixtures/replay/expected/mapped-session-001-visualizer.json` - Expected mapped state, metrics, checklist rows, and timing model.
- `docs/evidence/manifests/phase10-replay-fixtures.jsonl` - Sanitized replay fixture provenance manifest.
- `.planning/STATE.md` - Plan progress, metric, session, and Phase 10 decision update.
- `.planning/ROADMAP.md` - Phase 10 plan progress update.
- `.planning/REQUIREMENTS.md` - `PERF-04` marked complete.

## Decisions Made

- Use hex-only datagrams for the first replay corpus, with JSONL/JSON sidecars for readable expectations.
- Keep the replay test on the production path: `UdpInputReceiver` -> `UdpControllerStateAdapter` -> `VisualizerModel` -> `VisualizerMetrics`.
- Assert offset quality as `estimated`; no expected output relies on direct Android-to-desktop monotonic timestamp subtraction.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed JSON primitive compile issue**
- **Found during:** Task 1 RED verification
- **Issue:** Initial test used an unavailable `JsonPrimitive.content` import in this project dependency set.
- **Fix:** Switched expected-value reads to `contentOrNull` helpers.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt`
- **Verification:** RED run compiled and failed only for missing replay fixture; direct class output reported `missing replay hex fixture`.
- **Committed in:** `6a20f53`

**2. [Rule 3 - Blocking] Added fixture path resolver for Gradle working directory**
- **Found during:** Task 2 GREEN implementation
- **Issue:** Gradle executes main-function tests from `desktop-companion/`, while committed fixtures live at repo root.
- **Fix:** Added `repoFile` lookup that supports repo-root and `desktop-companion/` working directories.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt`
- **Verification:** Focused desktop replay/model/metrics tests passed through Gradle.
- **Committed in:** `6047aa6`

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking issue)
**Impact on plan:** Both fixes were required for the planned TDD gate and did not change scope.

## Issues Encountered

- Sandbox blocked Gradle file-lock/socket startup; the required Gradle commands passed when run with approved elevated execution.
- The RED Gradle failure hid the main-class assertion text, so the replay class was run directly to confirm the intended missing-fixture failure.

## Verification

- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ReplayFixture*' --tests '*UdpControllerStateAdapter*' --tests '*VisualizerMetrics*' --tests '*VisualizerModel*' --no-daemon --console=plain`
- PASS: `! rg -n "qr_secret|manual code|pairing_proof|stream key|HMAC key|private key|Bluetooth address|Android ID|serial|raw screenshot|raw log|[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}" fixtures/replay docs/evidence/manifests/phase10-replay-fixtures.jsonl`
- PASS: RED gate commit exists before GREEN gate commit.

## TDD Gate Compliance

- RED: `6a20f53 test(10-01): add failing replay fixture test`
- GREEN: `6047aa6 feat(10-01): add replay fixture corpus`
- REFACTOR: Not needed.

## Authentication Gates

None.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for `10-02-PLAN.md`. PERF-04 replay infrastructure is committed and can support later diagnostics/export docs without physical hardware.

## Self-Check: PASSED

- Files exist: all 7 task files found.
- Commits exist: `6a20f53` and `6047aa6` found in git log.
- No tracked deletions were introduced by task commits.
- Stub scan found no TODO/FIXME/placeholder patterns in changed replay artifacts.

---
*Phase: 10-diagnostics-replay-and-v1-docs*
*Completed: 2026-06-15*
