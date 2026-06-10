---
phase: 07-macos-virtual-joystick-path
plan: 02
subsystem: android
tags: [android, bluetooth-hid, gamepad, report-packer, tdd]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: Android Bluetooth HID capability model and schema-only evidence manifest from Plan 07-01
  - phase: 05-desktop-backend-contract-and-smoke-harness
    provides: Locked BT Gun v1 gamepad-like joystick contract
  - phase: 06-windows-virtual-joystick-path
    provides: Prior report byte order and completed Windows fallback path
provides:
  - Android-owned Bluetooth HID gamepad descriptor bytes for report ID 1 input and report ID 2 output
  - Deterministic input report packer from GunInputState plus MotionSample
  - Golden tests for descriptor bytes, report constants, button bits, int16 axes, aim source preference, and stale behavior
affects: [android-host, phase-07, bluetooth-hid, macos-gamepad-path]

tech-stack:
  added: []
  patterns:
    - Android HID package owns report bytes without production imports from desktop companion modules
    - Bluetooth HID input payloads omit the report ID and carry it as metadata for sendReport(reportId, data)
    - Packer tests pin calibrated aim first, raw aim fallback second, and center only when motion aim is missing

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt
    - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt
    - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidDescriptorTest.kt
    - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt
  modified:
    - android-host/app/build.gradle.kts

key-decisions:
  - "Android HID descriptor and report packing stay Android-owned; tests copy semantic parity values instead of importing desktop production code."
  - "Input reports use payload bytes only, with report ID 1 carried separately for Android BluetoothHidDevice.sendReport."
  - "Stick Y is inverted in the HID payload, matching the existing v1 HID axis convention."

patterns-established:
  - "Descriptor golden test: pin exact HID descriptor bytes plus semantic parity with the BT Gun v1 contract."
  - "Report packer golden test: pin button bit order, little-endian signed int16 axes, aim fallback, and stale behavior."
  - "Android HID source guard: scan hid main/test package for desktop companion dependency tokens."

requirements-completed: [ANDR-10]

duration: 19min
completed: 2026-06-10
---

# Phase 07 Plan 02: Android Bluetooth HID Descriptor and Report Packer Summary

**Android-owned Bluetooth HID gamepad descriptor and deterministic input report packer for normalized gun controls and phone motion aim**

## Performance

- **Duration:** 19 min
- **Started:** 2026-06-10T22:37:13Z
- **Completed:** 2026-06-10T22:55:59Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added `BtGunHidDescriptor` with exact descriptor bytes, report ID constants, payload lengths, six-button/four-axis semantic metadata, and no custom gun usage page.
- Added `BtGunHidReportPacker` to turn `GunInputState` plus latest `MotionSample` into a stable Bluetooth HID input payload.
- Pinned button bit order: trigger, reload, X, Y, A, B as bits 0 through 5.
- Pinned little-endian signed int16 encoding for stickX, inverted stickY, aimX, and aimY.
- Pinned aim source order: calibrated `aimX`/`aimY`, then raw aim fallback, then centered aim when motion is absent.
- Added a source guard proving the Android HID package does not import desktop companion production modules.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Pin Android HID descriptor bytes** - `75ff266` (test)
2. **Task 1 GREEN: Implement Android HID descriptor** - `b32c5d8` (feat)
3. **Task 2 RED: Pin Android HID input report packer vectors** - `71c06ed` (test)
4. **Task 2 GREEN: Implement Android HID report packer** - `dc37f33` (feat)

_Note: Both plan tasks followed TDD with separate RED and GREEN commits. No refactor commit was needed._

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt` - Android-owned HID descriptor bytes and report constants.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` - Pure packer from normalized gun state and motion sample to Bluetooth HID input payload.
- `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidDescriptorTest.kt` - Golden descriptor bytes, report constants, semantic parity, and standard usage-page checks.
- `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt` - Golden payload vectors for buttons, axes, aim selection, stale behavior, and source guard.
- `android-host/app/build.gradle.kts` - Registers descriptor and packer main-function tests in the existing Android test runner list.

## Decisions Made

- Android owns the HID descriptor and packer. The Android code does not import desktop companion production classes.
- The report packer returns a payload byte array without the report ID byte because Android `BluetoothHidDevice.sendReport` accepts the report ID separately.
- Stale reports clear buttons and stick axes but keep selected aim values, matching the existing v1 HID stale behavior.

## TDD Gate Compliance

- **Task 1 RED:** `75ff266` added descriptor golden tests and failed on unresolved `BtGunHidDescriptor`.
- **Task 1 GREEN:** `b32c5d8` added descriptor constants and exact bytes; focused descriptor tests passed.
- **Task 2 RED:** `71c06ed` added packer golden tests and failed on unresolved `BtGunHidReportPacker`.
- **Task 2 GREEN:** `dc37f33` added packer implementation; focused descriptor and packer tests passed.
- **REFACTOR:** Not needed.

## Verification

- **PASS:** `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*BtGunHidDescriptor*' --tests '*BtGunHidReportPacker*' --no-daemon --console=plain`
- **PASS:** `rg -n "com.btgun.desktop|MacosHid|WindowsHid" android-host/app/src/main/java/com/btgun/host/hid android-host/app/src/test/java/com/btgun/host/hid` returned no matches.
- **PASS:** Stub scan found no TODO/FIXME/placeholder/empty UI stub patterns in files created or modified by this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Avoided source-guard self-match in the packer test**
- **Found during:** Task 2 GREEN
- **Issue:** The source-guard test originally contained one literal forbidden desktop-token string, causing the guard command to match the test itself.
- **Fix:** Built banned tokens by runtime string concatenation while preserving the same guard behavior.
- **Files modified:** `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt`
- **Verification:** Source guard returned no matches and focused Gradle tests passed.
- **Committed in:** `dc37f33`

---

**Total deviations:** 1 auto-fixed (Rule 1).
**Impact on plan:** No scope expansion. The fix keeps the planned Android HID package dependency guard enforceable.

## Issues Encountered

- Sandbox-blocked Gradle startup reported `java.net.SocketException: Operation not permitted`; reran required Gradle gates outside the sandbox with approval.
- Gradle's manual main-function runner hides assertion stderr from `providers.exec`; source-guard grep identified the failing self-match.

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 07-03. Android now has pinned descriptor and input payload bytes for the later Bluetooth HID adapter to register and send, while output-report execution remains pending in later plans.

## Self-Check: PASSED

- Created files exist: `BtGunHidDescriptor.kt`, `BtGunHidReportPacker.kt`, `BtGunHidDescriptorTest.kt`, `BtGunHidReportPackerTest.kt`, and this summary.
- Task commits exist: `75ff266`, `b32c5d8`, `71c06ed`, `dc37f33`.
- Focused Gradle tests and Android HID source guard passed after final changes.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
