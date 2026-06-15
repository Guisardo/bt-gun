---
phase: 01-hardware-and-protocol-discovery
plan: 02
subsystem: android-diagnostic
tags: [android, bluetooth, ble, inputdevice, diagnostic, kotlin]

requires:
  - phase: 01-01
    provides: Static clue ids, evidence rules, fixture validator, and ignored evidence paths.
provides:
  - Throwaway Android diagnostic module contract
  - Required structured report names for input, BLE, Classic, app frames, and rumble observations
  - Diagnostic Android skeleton with Bluetooth permission reporting and scan hooks
  - Partial tooling coverage for DISC-02 and DISC-03 before physical hardware evidence
affects: [hardware-capture, android-host, protocol-fixtures, rumble-proof]

tech-stack:
  added: [android-gradle-plugin-metadata, kotlin-android-metadata, android-platform-apis]
  patterns:
    - Diagnostic app emits structured JSON-style log rows by report name.
    - Permission denied/unavailable states are logged explicitly before scans.
    - Diagnostic module remains throwaway Phase 1 tooling, not production Android host architecture.

key-files:
  created:
    - android-diagnostic/README.md
    - android-diagnostic/SPEC.md
    - android-diagnostic/settings.gradle.kts
    - android-diagnostic/build.gradle.kts
    - android-diagnostic/app/build.gradle.kts
    - android-diagnostic/app/src/main/AndroidManifest.xml
    - android-diagnostic/app/src/main/res/values/styles.xml
    - android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt
  modified: []

key-decisions:
  - "Keep DISC-02 and DISC-03 partial only; Plan 03 must provide physical hardware evidence before requirements are complete."
  - "Add Gradle/plugin metadata but do not build, install, or download dependencies during Plan 02."
  - "Use manual marker reports for app-observed frames and rumble observations so Plan 03 can tag hardware evidence without false proof."

patterns-established:
  - "Report contract: SPEC report names must also appear in MainActivity logging hooks."
  - "Bluetooth permission failures are diagnostic observations, not protocol conclusions."

requirements-completed: []
requirements-partially-covered: [DISC-02, DISC-03]

duration: 4 min
completed: 2026-06-06
---

# Phase 01 Plan 02: Android Diagnostic Module Summary

**Throwaway Android diagnostic contract and skeleton for InputDevice, BLE, Classic, app-frame, and rumble evidence collection.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-06-06T04:33:52Z
- **Completed:** 2026-06-06T04:37:40Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Created `android-diagnostic/README.md` and `SPEC.md` with diagnostic-only boundary, report contract, clue targets, and Plan 03 hardware proof deferral.
- Added a minimal Android diagnostic module scaffold without running Gradle build/install or downloading dependencies.
- Declared legacy Bluetooth, Android 12+ nearby-device, and location fallback permissions.
- Added `MainActivity.kt` hooks for `InputDevice`, `KeyEvent`, `MotionEvent`, BLE scan/characteristic targets, Classic bonded-device/SPP observations, app-frame markers, and rumble attempt/observation markers.
- Preserved Phase 1 evidence rule: `DISC-02` and `DISC-03` are only partially covered until physical captures exist.

## Task Commits

1. **Task 1: Define diagnostic-only module contract** - `1e9228d` (docs)
2. **Task 2: Create diagnostic Android app skeleton** - `acf4040` (feat)

## Files Created/Modified

- `android-diagnostic/README.md` - Diagnostic-only boundary, excluded production scope, evidence rule, and no-build boundary.
- `android-diagnostic/SPEC.md` - Required report names and behavior contract for input, BLE, Classic, app frames, and rumble states.
- `android-diagnostic/settings.gradle.kts` - Throwaway Gradle project settings.
- `android-diagnostic/build.gradle.kts` - Android/Kotlin plugin metadata for later human-reviewed build.
- `android-diagnostic/app/build.gradle.kts` - Diagnostic app namespace and SDK metadata.
- `android-diagnostic/app/src/main/AndroidManifest.xml` - Bluetooth, nearby-device, location fallback, BLE feature, and diagnostic activity declarations.
- `android-diagnostic/app/src/main/res/values/styles.xml` - Minimal platform UI style for the diagnostic activity.
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` - Structured diagnostic report hooks.

## Decisions Made

- `DISC-02` and `DISC-03` remain pending in requirements because this plan creates tooling only; Plan 03 must record real hardware evidence.
- Gradle/plugin coordinates are metadata only in this plan. No build/install command was run.
- Manual marker reports (`app_observed_frame`, `rumble_observed`) exist so Plan 03 can tag evidence intentionally; they do not verify hardware behavior by themselves.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added minimal style resource**
- **Found during:** Task 2 (Create diagnostic Android app skeleton)
- **Issue:** The manifest needed a concrete activity theme while the plan did not list any resource file.
- **Fix:** Added `android-diagnostic/app/src/main/res/values/styles.xml` using a platform Material no-action-bar theme.
- **Files modified:** `android-diagnostic/app/src/main/res/values/styles.xml`
- **Verification:** File exists; manifest references `@style/AppTheme`.
- **Committed in:** `acf4040`

---

**Total deviations:** 1 auto-fixed (Rule 3).
**Impact on plan:** No scope change. Supporting resource only; diagnostic module remains throwaway.

## Issues Encountered

None.

## Known Stubs

- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt:200` - `app_observed_frame` manual marker only. Real app-observed payload evidence is Plan 03 hardware work.
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt:211` - `rumble_attempt` logs no payload sent until Plan 03 human hardware workflow reviews bounded output writes.
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt:227` - `rumble_observed` manual marker only. Physical motor observation is Plan 03 hardware work.

## Verification

- `node tools/phase1/validate-fixtures.mjs --quick` - PASS
- `rg -n "input_device_scan|key_event|motion_event|ble_scan|ble_characteristic|classic_scan|classic_socket_observation|app_observed_frame|rumble_attempt|rumble_observed|rumble_failed|permission_state" android-diagnostic/SPEC.md android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` - PASS; all report names present in spec and source.
- File existence check for required diagnostic artifacts - PASS.
- `git log --oneline --grep="(01-02)"` - found task commits `1e9228d` and `acf4040`.
- Gradle build/install was intentionally not run.

## User Setup Required

None for Plan 02. Plan 03 must review Gradle/plugin dependency legitimacy before any diagnostic build/install.

## Next Phase Readiness

Ready for Plan 03. The diagnostic contract and skeleton can guide physical hardware checks for standard Android input visibility, BLE/Classic visibility, app-observed frames, and rumble attempts.

## Self-Check: PASSED

- Created files exist on disk.
- Task commits `1e9228d` and `acf4040` exist in git history.
- Validator quick gate passes.
- `DISC-02` and `DISC-03` were not marked complete.

---
*Phase: 01-hardware-and-protocol-discovery*
*Completed: 2026-06-06*
