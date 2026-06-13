---
phase: 09-visualizer-acceptance-path
plan: "02"
subsystem: ui
tags: [kotlin, swing, desktop, visualizer, tdd]

requires:
  - phase: 09-visualizer-acceptance-path
    provides: Plan 09-01 desktop event hub, visualizer metrics, and model contracts
provides:
  - Separate Swing VisualizerWindow shell with UI-SPEC copy
  - PairingWindow manual Open visualizer action
  - Authenticated-session auto-open coordinator using DesktopUiEventHub fanout
affects: [visualizer-window, pairing-window, desktop-launch, phase-09]

tech-stack:
  added: []
  patterns:
    - Executable Kotlin RED/GREEN tests for Swing helper behavior
    - VisualizerWindowFactory reuses one visualizer instance across manual and authenticated opens
    - PairingWindow consumes DesktopUiEventHub when injected, preserving callback fanout

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt

key-decisions:
  - "Use VisualizerWindowFactory plus VisualizerWindowCoordinator so authenticated sessions and manual reopen share one visualizer instance."
  - "Route PairingWindow UI callbacks through DesktopUiEventHub when Main injects one, preserving backend and visualizer fanout."
  - "Keep PairingWindow as pairing/status UI; visualizer-only checklist and gamepad labels stay in VisualizerWindow."

patterns-established:
  - "VisualizerWindow exposes UI copy/lifecycle helpers for headless executable tests."
  - "Visualizer close/dispose does not stop ControlServer or backend runtimes."
  - "Authenticated auto-open sets visualizer lifecycle state without clearing checklist or last aim context on disconnect."

requirements-completed: [VIS-01, VIS-04]

duration: 14 min
completed: 2026-06-13
---

# Phase 09 Plan 02: Visualizer Opening Path Summary

**Separate Swing visualizer shell with manual PairingWindow reopen and authenticated-session auto-open reuse.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-06-13T02:03:41Z
- **Completed:** 2026-06-13T02:17:06Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added `VisualizerWindow` as a separate Swing shell with required Phase 9 title, empty state, pending summary, checklist/live gamepad/metrics/event sections, and close behavior that disposes only the visualizer UI.
- Added an always-visible `Open visualizer` action to `PairingWindow` through an injected opener seam while preserving existing pairing, haptic smoke, and read-only Android profile diagnostics.
- Added `VisualizerWindowFactory` and `VisualizerWindowCoordinator` so authenticated control sessions auto-open one reusable visualizer window and disconnect/degraded events preserve checklist and last aim context.
- Wired `Main.kt` with one shared `ControlServer`, `DesktopUiEventHub`, visualizer factory/coordinator, backend runtime launch values, and `PairingWindow`.

## Task Commits

1. **Task 1 RED: visualizer window shell tests** - `b5caf06` (test)
2. **Task 1 GREEN: visualizer window shell** - `792a64c` (feat)
3. **Task 2 RED: visualizer reopen action tests** - `5072eb9` (test)
4. **Task 2 GREEN: PairingWindow reopen action** - `9b3035c` (feat)
5. **Task 3 RED: authenticated auto-open tests** - `efc9090` (test)
6. **Task 3 GREEN: auto-open wiring** - `ba27a9c` (feat)

## Files Created/Modified

- `desktop-companion/build.gradle.kts` - Registered `VisualizerWindowTestKt` in executable desktop tests.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` - Wires event hub, visualizer factory/coordinator, and PairingWindow opener seam.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Adds manual `Open visualizer` button and optional event-hub listener path.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt` - Adds visualizer shell, helper copy, close behavior, factory, and coordinator.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` - Covers manual reopen action and PairingWindow visualizer-label exclusion.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt` - Covers UI-SPEC copy, lifecycle helpers, reusable factory, auto-open coordinator, disconnect preservation, and Main wiring.

## Decisions Made

- Used a small factory/coordinator instead of putting authenticated-session logic in `PairingWindow`, keeping pairing/status UI separate from visualizer lifecycle.
- Kept `PairingWindow` backward-compatible by using direct `ControlServer` callbacks when no `DesktopUiEventHub` is injected.
- Verified forbidden UI labels through quoted-label source scans so method names like `invokeLater` cannot trigger false positives for the forbidden `OK` label.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed false-positive forbidden-label scan**
- **Found during:** Task 1 (Create VisualizerWindow shell and UI helper tests)
- **Issue:** The initial forbidden-label source scan checked `OK` as a raw case-insensitive substring, which matched `invokeLater`.
- **Fix:** Updated the test to scan quoted UI labels, preserving the UI-SPEC guard without false positives.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt`
- **Verification:** Task 1 focused Gradle command passed after the fix.
- **Committed in:** `792a64c`

---

**Total deviations:** 1 auto-fixed (1 bug).
**Impact on plan:** Test correctness fix only; no scope expansion.

## Issues Encountered

- Gradle cannot start inside the restricted sandbox because its local file-lock socket raises `java.net.SocketException: Operation not permitted`. Focused Gradle verification was rerun with approved escalation and passed.

## Known Stubs

None. Nullable runtime handles, disabled backend diagnostics, and `unknown`/`none` UI text are existing or intentional disconnected-state values, not placeholder data flows.

## Threat Flags

None. Changes add local Swing UI lifecycle and callback fanout consumption only; no new network endpoint, auth path, file access pattern, schema, HID publisher, or haptic transport was introduced.

## User Setup Required

None - no external service configuration required.

## Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*VisualizerWindow*' --tests '*VisualizerModel*' --no-daemon --console=plain` - PASS
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingWindow*' --tests '*VisualizerWindow*' --no-daemon --console=plain` - PASS
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*VisualizerWindow*' --tests '*PairingWindow*' --tests '*DesktopUiEventHub*' --no-daemon --console=plain` - PASS

## TDD Gate Compliance

- RED commits present for all three tasks.
- GREEN commits present after each matching RED commit.
- No refactor commit was needed.

## Self-Check: PASSED

- Created files exist on disk: `VisualizerWindow.kt` and `VisualizerWindowTest.kt`.
- Required commits exist in git history: `b5caf06`, `792a64c`, `5072eb9`, `9b3035c`, `efc9090`, `ba27a9c`.
- Plan-level focused desktop verification passed after all tasks.

## Next Phase Readiness

Ready for Plan 09-03. Later visualizer work can render live panels and haptic actions using the separate `VisualizerWindow`, reusable opener, and shared event hub without redefining launch or PairingWindow ownership.

---
*Phase: 09-visualizer-acceptance-path*
*Completed: 2026-06-13*
