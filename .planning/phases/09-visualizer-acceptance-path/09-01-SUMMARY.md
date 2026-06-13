---
phase: 09-visualizer-acceptance-path
plan: "01"
subsystem: ui
tags: [kotlin, swing, desktop, visualizer, metrics, tdd]

requires:
  - phase: 08-desktop-profiles-and-mapping
    provides: Android-owned mapped product stream and read-only profile metadata
  - phase: 04-input-stream-and-haptic-transport
    provides: authenticated accepted UDP input and haptic result callbacks
provides:
  - Desktop UI event fanout hub preserving ControlServer callbacks
  - Offset-aware visualizer latency and current-session packet-loss metrics
  - Immutable visualizer model, checklist row ids, and product event strip contracts
affects: [visualizer-window, phase-09, desktop-ui, acceptance-checklist]

tech-stack:
  added: []
  patterns:
    - TDD executable Kotlin test mains registered in desktop Gradle
    - ControlServer callback fanout through a small AutoCloseable hub
    - Immutable model snapshots for later Swing rendering

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/DesktopUiEventHubTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerMetricsTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerModelTest.kt
  modified:
    - desktop-companion/build.gradle.kts

key-decisions:
  - "Use a DesktopUiEventHub fanout layer before VisualizerWindow so later UI listeners do not clobber PairingWindow or backend callbacks."
  - "Compute visualizer latency only through an explicit Android-to-desktop clock offset estimate; direct capture-to-render subtraction remains invalid."
  - "Checklist rows distinguish observed live state from user-confirmed pass state for physical, OS-visible, and haptic proofs."

patterns-established:
  - "DesktopUiEventHub attaches once to ControlServer callbacks and restores previous callbacks on close when it still owns the callback."
  - "VisualizerMetrics tracks current-session packet loss from accepted sequence gaps and resets on control or stream session change."
  - "VisualizerModel keeps last accepted aim context while stale display state is shown."

requirements-completed: [VIS-01, VIS-02, VIS-04, VIS-06, PERF-01, PERF-02]

duration: 13 min
completed: 2026-06-13
---

# Phase 09 Plan 01: Visualizer Acceptance Contracts Summary

**Desktop visualizer foundation with callback fanout, offset-aware metrics, and immutable checklist/model contracts.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-06-13T01:44:37Z
- **Completed:** 2026-06-13T01:58:14Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Added `DesktopUiEventHub` so `ControlServer` session, control, profile, accepted UDP, rejected UDP, stream lifecycle, and haptic result callbacks can fan out to multiple desktop UI/backend consumers without callback replacement.
- Added `VisualizerMetrics` with current-session expected/missed packet-loss counters, offset-aware latency labels, capture-to-send, receive-to-render, sample age, and the `<50 ms` target status.
- Added `VisualizerModel` and checklist contracts for required Phase 9 row ids, user-confirmed proof semantics, raw-debug state, haptic state, recenter placeholders, and last-10 product events.

## Task Commits

1. **Task 1 RED: desktop UI event fanout tests** - `a1ee510` (test)
2. **Task 1 GREEN: desktop UI event fanout hub** - `55a84cf` (feat)
3. **Task 2 RED: visualizer metrics tests** - `e4c68d9` (test)
4. **Task 2 GREEN: visualizer metrics** - `fa2f903` (feat)
5. **Task 3 RED: visualizer model tests** - `f039032` (test)
6. **Task 3 GREEN: visualizer model contracts** - `9d5bb37` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt` - Event fanout hub with listener registration and close-time callback restoration.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt` - Latency, clock-offset quality, packet loss, and metric label calculations.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` - Immutable live-state, checklist, haptic, raw-debug, recenter, and event-strip model.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/DesktopUiEventHubTest.kt` - Executable tests for listener fanout, callback restoration, and hub scope.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerMetricsTest.kt` - Executable tests for sequence gaps, accepted-only counting, clock-offset latency, and session resets.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerModelTest.kt` - Executable tests for event strip size/order, checklist confirmation separation, forbidden labels, and stale aim preservation.
- `desktop-companion/build.gradle.kts` - Registered the three new executable test mains.

## Decisions Made

- Used a list-backed listener registry for `DesktopUiEventHub`; independent listener instances with equivalent function shapes must both receive callbacks.
- Kept metrics input limited to accepted `UdpReceivedInput`, leaving rejected frame reasons out of packet-loss counters.
- Kept checklist labels sanitized and acceptance-focused; no desktop profile editing or secret-like fields are exposed by model labels.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed listener deduplication in event hub**
- **Found during:** Task 1 (Add desktop UI event fanout without clobbering callbacks)
- **Issue:** Initial listener storage used a set, which collapsed two independently registered listeners with equivalent function-reference data.
- **Fix:** Changed listener storage to a list so every registered consumer receives callbacks.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt`
- **Verification:** Required Task 1 Gradle command passed after the fix.
- **Committed in:** `55a84cf`

---

**Total deviations:** 1 auto-fixed (1 bug).
**Impact on plan:** Correctness fix only; no scope expansion.

## Issues Encountered

- Gradle could not start inside the restricted sandbox because its local file-lock socket raised `java.net.SocketException: Operation not permitted`. The focused Gradle commands were rerun with approved escalation and passed.

## Known Stubs

None. Optional nullable fields in the metrics/model snapshots represent unavailable runtime data, not placeholder UI data.

## Threat Flags

None. New code consumes existing trusted callbacks and accepted UDP input; it adds no new network endpoints, auth paths, file access patterns, schema changes, HID publishing, or haptic execution path.

## User Setup Required

None - no external service configuration required.

## Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*DesktopUiEventHub*' --tests '*PairingWindow*' --tests '*WindowsBackendRuntime*' --tests '*MacosBackendRuntime*' --no-daemon --console=plain` - PASS
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*VisualizerMetrics*' --no-daemon --console=plain` - PASS
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*VisualizerModel*' --tests '*UdpControllerStateAdapter*' --no-daemon --console=plain` - PASS
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*DesktopUiEventHub*' --tests '*VisualizerMetrics*' --tests '*VisualizerModel*' --tests '*PairingWindow*' --tests '*UdpControllerStateAdapter*' --no-daemon --console=plain` - PASS

## TDD Gate Compliance

- RED commits present for all three tasks.
- GREEN commits present after each matching RED commit.
- No refactor commit was needed.

## Self-Check: PASSED

- Created files exist on disk: `DesktopUiEventHub.kt`, `VisualizerMetrics.kt`, `VisualizerModel.kt`, and all three executable test files.
- Required commits exist in git history: `a1ee510`, `55a84cf`, `e4c68d9`, `fa2f903`, `f039032`, `9d5bb37`.
- Plan-level focused desktop verification passed after all tasks.

## Next Phase Readiness

Ready for Plan 09-02. Later UI work can render the visualizer without redefining callback ownership, metrics math, packet-loss semantics, or checklist confirmation rules.

---
*Phase: 09-visualizer-acceptance-path*
*Completed: 2026-06-13*
