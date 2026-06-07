---
phase: 02-android-host-live-input
plan: 04
subsystem: android-host-motion
tags: [android, kotlin, sensormanager, motion, preview-aim, tdd]
requires:
  - phase: 02-android-host-live-input
    provides: Android host scaffold, live envelopes, stream sequencing, and normalized motion contracts
provides:
  - Pure motion provider selection order with explicit capability metadata
  - SensorManager-backed motion provider shell using elapsed-nanos sensor timestamps
  - Preview-only aim mapper with unavailable-state handling
  - Motion sample metadata for provider name, capabilities, and source sensor elapsed nanos
affects: [02-android-host-live-input, phase-04-input-stream, phase-08-desktop-profiles]
tech-stack:
  added: []
  patterns:
    - Pure provider selection remains unit-testable without SensorManager
    - Android SensorManager probing stays behind MotionAimProvider
    - Preview aim is local calibration output only; desktop profile mapping remains deferred
key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/motion/MotionAimProvider.kt
    - android-host/app/src/main/java/com/btgun/host/motion/PreviewAimMapper.kt
    - android-host/app/src/test/java/com/btgun/host/motion/MotionProviderSelectionTest.kt
  modified:
    - android-host/app/build.gradle.kts
    - android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt
key-decisions:
  - "Use provider order game rotation vector, rotation vector, gyro plus gravity/accelerometer, tilt fallback, then unavailable."
  - "Keep Android preview aim as bounded local calibration output only, with no desktop profile or HID mapping fields."
  - "Use platform SensorManager only; no sensor-fusion dependency was added."
patterns-established:
  - "MotionProviderSelection.choose(capabilities) returns selected provider, exact wire name, availability, and derived capability flags."
  - "MotionSample carries provider metadata and source sensor elapsed nanos inside the common LiveEnvelope motion stream."
  - "PreviewAimMapper maps baseline-relative orientation deltas into bounded [-1, 1] preview values and disables the pad when motion is unavailable."
requirements-completed: [ANDR-04, ANDR-05]
duration: 5min
completed: 2026-06-07
---

# Phase 02 Plan 04: Motion Provider Selection and Preview Aim Summary

**TDD-covered Android motion provider selection with honest capability metadata and preview-only aim calibration output.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-07T00:26:15Z
- **Completed:** 2026-06-07T00:31:35Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Added RED tests for provider fallback order, capability flags, timestamp source, preview bounds, unavailable state, and absence of desktop profile/HID fields.
- Implemented pure `MotionProviderSelection.choose(...)` plus `MotionCapabilityFlags` and exact provider wire names: `game_rotation_vector`, `rotation_vector`, `gyro_gravity`, `tilt_fallback`, `unavailable`.
- Added `MotionAimProvider` SensorManager shell that probes game rotation vector, rotation vector, gyroscope, accelerometer, and gravity sensors, and emits `LiveEnvelope<MotionSample>` on the motion stream.
- Added `PreviewAimMapper` with baseline-relative bounded x/y values and disabled-pad `Motion unavailable` behavior for unavailable providers.
- Extended `MotionSample` with provider name, capability metadata, and source sensor elapsed nanos.

## Task Commits

1. **Task 1: RED motion provider and preview tests** - `17bc9bb` (test)
2. **Task 2: GREEN provider selection and motion sample envelope** - `fd6cbf6` (feat)
3. **Task 3: REFACTOR motion boundary and full quick gate** - no commit; boundary already separated and verification passed

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/motion/MotionAimProvider.kt` - Pure provider selection, capability flags, SensorManager capability probing, and motion envelope emission.
- `android-host/app/src/main/java/com/btgun/host/motion/PreviewAimMapper.kt` - Local preview/calibration aim mapping with unavailable state.
- `android-host/app/src/test/java/com/btgun/host/motion/MotionProviderSelectionTest.kt` - JVM-pure RED/GREEN tests for provider order, metadata, preview bounds, unavailable behavior, and desktop mapping boundary.
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - Motion sample metadata fields and exact provider wire names.
- `android-host/app/build.gradle.kts` - Registers the motion test main in the existing no-dependency test harness.

## Decisions Made

- Provider selection stays pure and independent from Android framework types so unit tests cover fallback behavior without mocking `SensorManager`.
- Android motion preview stays preview/calibration only; no sensitivity, dead-zone, desktop profile id, Windows/macOS, or HID mapping fields were added.
- Unavailable motion emits explicit unavailable metadata and disabled preview state instead of fake aim values.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Registered motion test in custom JVM harness**
- **Found during:** Task 1 (RED motion provider and preview tests)
- **Issue:** Existing `testDebugUnitTest` uses a manual `main()` test harness; adding only the test file would compile it but not execute it.
- **Fix:** Added `com.btgun.host.motion.MotionProviderSelectionTestKt` to the Gradle harness list.
- **Files modified:** `android-host/app/build.gradle.kts`
- **Verification:** RED failed on missing motion classes, then GREEN/full gates executed the motion test main.
- **Committed in:** `17bc9bb`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required for correctness of the existing no-dependency test harness; no scope expansion or new package dependency.

## Issues Encountered

- Gradle cannot run inside the sandbox because native file-lock socket setup is blocked (`Operation not permitted`). Verification was rerun with approved escalation.
- Android Gradle verification used local JDK 17, matching prior Phase 02 plan evidence.
- Task 3 required no refactor commit because selection, Android sensor probing, and preview math were already separated after GREEN.

## Known Stubs

None. Nullable defaults in shared event contracts are optional payload fields, not UI or mock-data stubs.

## Threat Flags

None. Android sensor input and preview-to-desktop mapping boundaries were already covered by the plan threat model.

## User Setup Required

None - no external service configuration required. Android Gradle verification should use JDK 17.

## Verification

- RED: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*MotionProviderSelection*'` - FAIL as expected on unresolved `MotionAimProvider`/`PreviewAimMapper` behavior.
- GREEN focused: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*MotionProviderSelection*'` - PASS.
- Quick fixture gate: `node tools/phase1/validate-fixtures.mjs --full` - PASS.
- Full unit gate: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` - PASS.
- Full lint gate: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug` - PASS.

## TDD Gate Compliance

- RED commit exists before GREEN: `17bc9bb`
- GREEN implementation commit exists after RED: `fd6cbf6`
- REFACTOR commit not needed because no behavior-preserving cleanup was required after GREEN.

## Next Phase Readiness

Plan 02-05 can use motion baseline timestamps for reload-hold recenter. Plan 02-06 can surface `MotionProviderSelection`, `MotionSample` metadata, and `PreviewAim` without inventing desktop profile mapping.

## Self-Check: PASSED

- Found `.planning/phases/02-android-host-live-input/02-04-SUMMARY.md`
- Found `android-host/app/src/main/java/com/btgun/host/motion/MotionAimProvider.kt`
- Found `android-host/app/src/main/java/com/btgun/host/motion/PreviewAimMapper.kt`
- Found task commits `17bc9bb` and `fd6cbf6`

---
*Phase: 02-android-host-live-input*
*Completed: 2026-06-07*
