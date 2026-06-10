---
phase: 07-macos-virtual-joystick-path
plan: 03
subsystem: android
tags: [android, bluetooth-hid, gamepad, output-report, haptics, tdd]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: Android Bluetooth HID capability model and descriptor/report packer from Plans 07-01 and 07-02
  - phase: 04-input-stream-and-haptic-transport
    provides: Bounded DesktopHapticCommand and phone haptic execution model
  - phase: 06-windows-virtual-joystick-path
    provides: Output report validation precedent and Windows fallback path
provides:
  - Strict Android Bluetooth HID output-report mapper for report ID 2 payloads
  - BluetoothHidDevice adapter seam for explicit proxy/register/send/callback lifecycle
  - Status model distinguishing proxy, registration, host connection, input send, output callback, validation, and haptic result
affects: [android-host, phase-07, bluetooth-hid, phone-haptics, macos-gamepad-path]

tech-stack:
  added: []
  patterns:
    - Android HID callbacks route host-origin output bytes through a typed validation result before phone haptics
    - BluetoothHidDevice calls are hidden behind injectable proxy/connector interfaces for unit tests
    - HID adapter starts only by explicit startGamepadMode and sends input only after registered plus connected host

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt
    - android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt
    - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidStatus.kt
    - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidOutputReportMapperTest.kt
    - android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt
  modified:
    - android-host/app/build.gradle.kts

key-decisions:
  - "Android HID output reports use report ID 2 as callback metadata and an 8-byte payload containing version, strength, duration, TTL, flags, and reserved bytes."
  - "AndroidBluetoothHidGamepad hides platform BluetoothHidDevice calls behind injectable connector/proxy interfaces so lifecycle and callback behavior are testable without live Bluetooth."
  - "DESK-06 is not claimed live: this plan adds Android callback/status foundation only; macOS output-report proof remains a later live evidence gate."

patterns-established:
  - "Output mapper result: valid command or invalid reason, never nullable ambiguity."
  - "Adapter lifecycle: explicit startGamepadMode, optional openPairingWindow, sendInput gated by proxy/register/host, idempotent stop/close."
  - "Callback status: output callback seen, validation result, and haptic result are recorded separately for later evidence rows."

requirements-completed: []
requirements-addressed: [ANDR-09, ANDR-11, DESK-06]

duration: 10min
completed: 2026-06-10
---

# Phase 07 Plan 03: Android Bluetooth HID Adapter and Output Mapper Summary

**Android Bluetooth HID adapter seam with strict host-output validation before phone haptics**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-10T23:01:13Z
- **Completed:** 2026-06-10T23:10:51Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added `BtGunHidOutputReportMapper` with typed valid/invalid results for report ID 2 output payloads.
- Added `AndroidBluetoothHidGamepad` with explicit proxy request, app registration, pairing-window hook, input send gating, callback handling, and idempotent stop/close.
- Added `BtGunHidStatus` so later dashboard/evidence work can distinguish registration, host connection, input send, callback seen, validation, haptic result, and unsupported reason.
- Added focused TDD tests for malformed output reports, no-send-before-ready lifecycle, fake HID proxy callbacks, invalid-output `reportError`, and haptic routing.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: HID output report mapper tests** - `cd23ad6` (test)
2. **Task 1 GREEN: HID output report mapper** - `18d500e` (feat)
3. **Task 2 RED: Bluetooth HID adapter seam tests** - `bb9d654` (test)
4. **Task 2 GREEN: Bluetooth HID adapter seam** - `1b6378f` (feat)

_Note: Both tasks followed TDD with RED and GREEN commits. No refactor commit was needed._

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt` - Strict HID output payload validation and `DesktopHapticCommand` mapping.
- `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt` - Injectable BluetoothHidDevice adapter/proxy/callback seam plus Android wrapper.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidStatus.kt` - HID proxy, registration, host, input, callback, validation, and haptic status model.
- `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidOutputReportMapperTest.kt` - RED/GREEN mapper matrix for valid and malformed output reports.
- `android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt` - RED/GREEN adapter lifecycle and callback tests with fake proxy.
- `android-host/app/build.gradle.kts` - Registers mapper and adapter main-function tests.

## Decisions Made

- Android output mapper consumes report ID separately from the 8-byte payload, matching Android `BluetoothHidDevice.Callback` shape and Plan 07-02 input-report precedent.
- Invalid host-origin output reports are rejected before haptic execution and surface a reason plus `reportError`.
- Live macOS output-report support is not claimed. This plan only creates Android-side callback/status foundation for later proof.

## TDD Gate Compliance

- **Task 1 RED:** `cd23ad6` added mapper tests and failed on unresolved `BtGunHidOutputReportMapper` / result types.
- **Task 1 GREEN:** `18d500e` added strict mapper implementation; focused mapper tests passed.
- **Task 2 RED:** `bb9d654` added adapter seam tests and failed on unresolved adapter/status/proxy seam symbols.
- **Task 2 GREEN:** `1b6378f` added adapter/status implementation; focused adapter and mapper tests passed.
- **REFACTOR:** Not needed.

## Verification

- **PASS:** `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*AndroidBluetoothHidGamepadState*' --tests '*BtGunHidOutputReportMapper*' --no-daemon --console=plain`
- **PASS:** `rg -n "com\.btgun\.desktop|MacosHid|WindowsHid" android-host/app/src/main/java/com/btgun/host/hid` returned no matches.
- **PASS:** Stub scan found no TODO/FIXME/placeholder/empty UI stub patterns in files created or modified by this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected adapter test expectations for pending proxy and latest callback**
- **Found during:** Task 2 GREEN
- **Issue:** The RED test expected `NOT_REGISTERED` after start but before proxy availability, and expected `GET_REPORT` after later interrupt data. Both contradicted the adapter lifecycle/status model.
- **Fix:** Updated the test to expect `NO_PROXY` while the proxy request is still pending and `INTERRUPT_DATA` as the latest callback after interrupt data.
- **Files modified:** `android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt`
- **Verification:** Focused adapter and mapper Gradle tests passed.
- **Committed in:** `1b6378f`

**2. [Rule 3 - Blocking] Fixed Kotlin expression-body compile errors**
- **Found during:** Task 2 GREEN
- **Issue:** Android wrapper methods used Elvis `return false` inside expression-bodied functions, which Kotlin rejects.
- **Fix:** Converted `sendReport`, `replyReport`, and `reportError` wrapper methods to block bodies.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`
- **Verification:** Focused adapter and mapper Gradle tests passed.
- **Committed in:** `1b6378f`

---

**Total deviations:** 2 auto-fixed (Rule 1: 1, Rule 3: 1).
**Impact on plan:** No scope expansion. Fixes kept tests aligned with the planned lifecycle and unblocked compilation.

## Issues Encountered

- Sandbox-blocked Gradle startup reported `java.net.SocketException: Operation not permitted`; required Gradle gates were rerun outside the sandbox with approval.
- Gradle main-function runner hid one assertion failure, so the built test class was run directly once to identify the bad RED expectation.

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 07-04. Android now has a tested HID adapter seam and strict host-output validation, but live macOS Bluetooth pairing and output-report behavior remain unproven and must not be claimed from this plan.

## Self-Check: PASSED

- Created files exist: `BtGunHidOutputReportMapper.kt`, `AndroidBluetoothHidGamepad.kt`, `BtGunHidStatus.kt`, `BtGunHidOutputReportMapperTest.kt`, `AndroidBluetoothHidGamepadStateTest.kt`, and this summary.
- Task commits exist: `cd23ad6`, `18d500e`, `bb9d654`, `1b6378f`.
- Focused Gradle tests and Android HID production source guard passed after final changes.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
