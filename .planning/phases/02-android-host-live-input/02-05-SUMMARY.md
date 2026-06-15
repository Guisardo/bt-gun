---
phase: 02-android-host-live-input
plan: 05
subsystem: android-host-recenter
tags: [android, kotlin, recenter, reload, tdd, elapsed-nanos]
requires:
  - phase: 02-02
    provides: Fixture-backed reload down/up product gun events and common live envelope contracts.
  - phase: 02-04
    provides: Preview aim baseline timestamp contract for local calibration feedback.
provides:
  - Pure reload-hold recenter state machine with deterministic elapsed-nanos input.
  - Recenter status event carrying baseline elapsed timestamp and `recenter emitted` label.
  - Regression coverage proving reload down/up remain normal gun events around recenter.
affects: [02-android-host-live-input, android-host, recenter, dashboard-state]
tech-stack:
  added: []
  patterns:
    - Recenter logic is pure Kotlin; no sleeps, timers, handlers, or Android framework dependencies.
    - Recenter emits on the status stream while reload down/up stay on the gun stream.
key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt
    - android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt
  modified:
    - android-host/app/build.gradle.kts
    - android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt
key-decisions:
  - "Use a pure elapsed-nanos state machine for reload-hold recenter instead of timers or scheduled work."
  - "Keep recenter as a status-stream event with baseline elapsed timestamp so reload gun events are never consumed."
  - "Expose recenter metadata through optional StatusEvent fields plus a RecenterEvent view for Plan 02-06 dashboard wiring."
patterns-established:
  - "ReloadHoldRecenter.onReload(...) always emits one reload GunEvent and resets duplicate recenter state on release."
  - "ReloadHoldRecenter.onTick(...) emits one recenter StatusEvent only after a held reload reaches 2_000_000_000 ns."
requirements-completed: [ANDR-03, ANDR-05, ANDR-06]
duration: 13min
completed: 2026-06-07
---

# Phase 02 Plan 05: Reload-Hold Recenter Summary

**Reload hold now recenters preview aim after two seconds without suppressing normal reload down/up product events.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-06-07T00:36:00Z
- **Completed:** 2026-06-07T00:49:25Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- Added RED tests for reload down, no early recenter, exactly one threshold recenter, reload up after recenter, second-hold reset, and event ordering.
- Implemented `ReloadHoldRecenter` with deterministic elapsed-nanos inputs and no timer/sleep dependency.
- Added recenter status metadata: `baselineElapsedNanos`, `statusLabel`, and `RecenterEvent` view.
- Preserved separate stream semantics: reload uses `StreamKind.GUN`; recenter uses `StreamKind.STATUS`.

## Task Commits

1. **Task 1: RED reload-hold recenter tests** - `660918e` (test)
2. **Task 2: GREEN reload hold state machine** - `992984b` (feat)
3. **Task 3: REFACTOR event contracts and quick gate** - no commit; implementation already kept event order clear and state pure

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt` - Pure reload hold state machine, exported `RecenterEvent`, `ReloadHoldState`, threshold constant, and status event conversion.
- `android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt` - Deterministic JVM tests for hold threshold, early release, duplicate suppression, repeat holds, and order.
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - Optional recenter metadata fields on status events.
- `android-host/app/build.gradle.kts` - Registers the recenter test main in the existing no-dependency unit harness.

## Decisions Made

- Used `onReload(pressed, nowElapsedNanos)` and `onTick(nowElapsedNanos)` so service/UI code can drive recenter without blocking BLE callbacks.
- Stored recenter baseline as the threshold elapsed timestamp, matching the UI contract for visible zero/baseline feedback.
- Kept desktop profile, HID, LAN transport, and physical gun motor behavior out of scope.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Registered recenter test in custom JVM harness**
- **Found during:** Task 1 (RED reload-hold recenter tests)
- **Issue:** Existing `testDebugUnitTest` uses an explicit main-class harness; a new test file would compile but not run unless registered.
- **Fix:** Added `com.btgun.host.recenter.ReloadHoldRecenterTestKt` to `android-host/app/build.gradle.kts`.
- **Files modified:** `android-host/app/build.gradle.kts`
- **Verification:** RED failed on missing `ReloadHoldRecenter`; GREEN and full gates executed the recenter test main.
- **Committed in:** `660918e`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required for the TDD gate to test intended behavior. No scope creep into UI, BLE parsing, LAN transport, desktop mapping, or haptics.

## Issues Encountered

- Sandbox-blocked Gradle file-lock sockets produced `java.net.SocketException: Operation not permitted`; Gradle verification was rerun with approved escalation.
- Android Gradle initially picked OpenJDK 26 and failed Android jlink; verification was rerun with local JDK 17, matching prior Phase 02 plan evidence.
- `.planning/REQUIREMENTS.md` already had unrelated pre-existing dirty edits; it was not staged in this plan closeout.

## Known Stubs

None. Nullable status/event fields are optional model contracts, not UI stubs or mock data.

## Threat Flags

None. The reload-to-recenter trust boundary was planned and covered by deterministic tests for early release, duplicate suppression, and reload event preservation.

## User Setup Required

None - no external service configuration required. Android Gradle verification should use JDK 17.

## Verification

- RED: `ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*ReloadHoldRecenter*'` - FAIL as expected on unresolved `ReloadHoldRecenter` and recenter metadata.
- GREEN focused: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*ReloadHoldRecenter*'` - PASS.
- Quick fixture gate: `node tools/phase1/validate-fixtures.mjs --full` - PASS.
- Full unit gate: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` - PASS.
- Full lint gate: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug` - PASS.

## TDD Gate Compliance

- RED commit exists before GREEN: `660918e`
- GREEN implementation commit exists after RED: `992984b`
- REFACTOR commit not needed because no behavior-preserving cleanup was required after GREEN.

## Next Phase Readiness

Plan 02-06 can wire `ReloadHoldRecenter` into dashboard/session state and display `recenter emitted` plus baseline elapsed timestamp without inventing timer-driven logic.

## Self-Check: PASSED

- Found `.planning/phases/02-android-host-live-input/02-05-SUMMARY.md`
- Found `android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt`
- Found `android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt`
- Found task commits `660918e` and `992984b`

---
*Phase: 02-android-host-live-input*
*Completed: 2026-06-07*
