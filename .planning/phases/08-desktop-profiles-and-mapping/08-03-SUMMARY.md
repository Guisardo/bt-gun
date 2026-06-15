---
phase: 08-desktop-profiles-and-mapping
plan: 03
subsystem: android-profile-mapping
tags: [android, profiles, mapping, smoothing, tdd]

requires:
  - phase: 08-desktop-profiles-and-mapping
    provides: Android profile schema, defaults, validation, and store from Plan 02
provides:
  - Pure Android profile mapper from GunInputState plus MotionSample to mapped controller state
  - Latency-capped adaptive aim smoother with deterministic tau values and Low fallback
  - Button remap, stick pass-through, and physical recenter helper behavior
affects: [phase-08, android-host, profiles, hid, mapped-stream]

tech-stack:
  added: []
  patterns:
    - Pure profile math stays framework-free and unit-tested before service/UI wiring.
    - Adaptive smoothing reports mode, estimated filter lag, and fallback state.
    - Recenter is a physical control selection separate from virtual button remap.

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/profile/AdaptiveAimSmoother.kt
    - android-host/app/src/main/java/com/btgun/host/profile/ProfileMapper.kt
    - android-host/app/src/test/java/com/btgun/host/profile/AdaptiveAimSmootherTest.kt
    - android-host/app/src/test/java/com/btgun/host/profile/ProfileMapperTest.kt
  modified:
    - android-host/app/build.gradle.kts

key-decisions:
  - "Adaptive smoothing uses tau values Low=12ms, Balanced=24ms, High=40ms, with adaptive fast=8ms and jitter=18ms before latency fallback."
  - "Adaptive fallback reports smoothingMode=low and adaptiveFallback=true when added filter lag or aim latency headroom threatens the 50ms target."
  - "ProfileMapper publishes only v1 virtual button ids; unknown controls and axis-like names are ignored as product buttons."

patterns-established:
  - "ProfileMapper is a pure Kotlin stateful mapper that accepts injected AdaptiveAimSmoother."
  - "MappedControllerState carries aim axes, aim status, pressed virtual controls, stick axes, and recenter physical id."

requirements-completed: [PROF-02, PROF-03, PROF-04]

duration: 11min
completed: 2026-06-12
---

# Phase 08 Plan 03: Desktop Profiles and Mapping Summary

**Pure Android profile mapper with latency-capped smoothing, provider overrides, button remap, and physical recenter helper**

## Performance

- **Duration:** 11 min
- **Started:** 2026-06-12T16:21:58Z
- **Completed:** 2026-06-12T16:33:05Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Added `AdaptiveAimSmoother` with deterministic Low/Balanced/High tau values, adaptive fast/jitter behavior, finite/clamped output, monotonic timestamp handling, and Low fallback when latency budget is tight.
- Added `ProfileMapper` with provider-specific aim setting selection, calibrated/raw/tilt/center aim sources, sensitivity/inversion/dead-zone math, smoothing status, and mapped controller output.
- Added v1-only button remap, stick pass-through, `recenterPhysicalControl`, and `isRecenterPressed()` based on physical controls.
- Added focused TDD tests for all mapper/smoother acceptance criteria.

## Task Commits

Each TDD task used RED then GREEN commits:

1. **Task 1 RED: Adaptive smoother tests** - `397dcfc` (test)
2. **Task 1 GREEN: Adaptive smoother implementation** - `0a6ec6f` (feat)
3. **Task 2 RED: Profile mapper aim tests** - `83a6f1c` (test)
4. **Task 2 GREEN: Profile mapper aim implementation** - `bdca2ed` (feat)
5. **Task 3 RED: Profile button/recenter tests** - `47987b1` (test)
6. **Task 3 GREEN: Profile button/recenter implementation** - `ec67833` (feat)

No refactor commit was needed.

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/profile/AdaptiveAimSmoother.kt` - Pure timestamp-driven smoothing and latency fallback.
- `android-host/app/src/main/java/com/btgun/host/profile/ProfileMapper.kt` - Pure profile aim/button/stick/recenter mapper.
- `android-host/app/src/test/java/com/btgun/host/profile/AdaptiveAimSmootherTest.kt` - Smoothing mode, fallback, clamp, and monotonic tests.
- `android-host/app/src/test/java/com/btgun/host/profile/ProfileMapperTest.kt` - Provider override, aim math, smoothing status, remap, stick, and recenter tests.
- `android-host/app/build.gradle.kts` - Manual Android unit harness entries for new profile tests.

## Decisions Made

- Used adaptive fast tau `8ms` and jitter tau `18ms`; explicit Low/Balanced/High remain pinned at `12/24/40ms`.
- Applied adaptive Low fallback only for adaptive mode, preserving explicit user-selected Low/Balanced/High semantics while still reporting estimated lag.
- Kept mapper source labels stable: `calibrated`, `normalized`, `raw`, `tilt_fallback`, and `center`.
- Kept recenter physical id in mapped state for service wiring, while virtual reload remains normal mapped output.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Registered new main() tests in Android unit harness**
- **Found during:** Task 1 (adaptive smoother RED)
- **Issue:** Android host uses manual Kotlin `main()` tests invoked from `build.gradle.kts`; new profile tests would not execute unless added there.
- **Fix:** Added `AdaptiveAimSmootherTestKt` and `ProfileMapperTestKt` to the harness list.
- **Files modified:** `android-host/app/build.gradle.kts`
- **Verification:** Required Gradle command runs both focused test classes and passes.
- **Committed in:** `397dcfc`, `83a6f1c`

---

**Total deviations:** 1 auto-fixed (1 blocking).
**Impact on plan:** Required for TDD correctness; no product scope added.

## Issues Encountered

- Gradle cannot create its file-lock/socket service in the managed sandbox. The required Gradle commands were rerun unchanged with approved escalation and passed.

## Known Stubs

None. Stub scan hits were nullable state/test parameters only; no placeholder UI or unwired product data was added.

## Threat Flags

None. The plan threat model already covered mapper tampering (`T-08-05`) and smoother DoS/lag behavior (`T-08-06`).

## TDD Gate Compliance

- RED commits exist before GREEN commits for all three tasks.
- GREEN commits pass the focused test gates.
- No refactor commit was needed.

## User Setup Required

None - no external service configuration required.

## Verification

- RED Task 1: required Gradle command with `--tests '*AdaptiveAimSmoother*'` failed on missing `AdaptiveAimSmoother`.
- GREEN Task 1: same Gradle environment with `--tests '*AdaptiveAimSmoother*'` - PASS.
- RED Task 2: required Gradle command with `--tests '*ProfileMapper*' --tests '*AdaptiveAimSmoother*'` failed on missing `ProfileMapper` and mapped fields.
- GREEN Task 2: same Gradle environment with `--tests '*ProfileMapper*' --tests '*AdaptiveAimSmoother*'` - PASS.
- RED Task 3: same Gradle environment with `--tests '*ProfileMapper*'` failed on missing button/stick/recenter fields and helper.
- GREEN Task 3: same Gradle environment with `--tests '*ProfileMapper*'` - PASS.
- Overall required command: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*ProfileMapper*' --tests '*AdaptiveAimSmoother*' --no-daemon --console=plain` - PASS.
- Framework-boundary scan: `rg -n "android\\.|androidx\\.|SystemClock|Context|View" AdaptiveAimSmoother.kt ProfileMapper.kt` - PASS with no matches.

## Next Phase Readiness

Ready for `08-04-PLAN.md`: service/HID/LAN wiring can consume `MappedControllerState` without adding profile math to desktop.

## Self-Check: PASSED

- Summary file exists.
- Task commits exist: `397dcfc`, `0a6ec6f`, `83a6f1c`, `bdca2ed`, `47987b1`, `ec67833`.
- Key created files exist.
- No tracked files were deleted by task commits.
- Untracked `.codex/` remains untouched as requested.

---
*Phase: 08-desktop-profiles-and-mapping*
*Completed: 2026-06-12*
