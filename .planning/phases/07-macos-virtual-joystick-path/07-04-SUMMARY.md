---
phase: 07-macos-virtual-joystick-path
plan: 04
subsystem: android
tags: [android, bluetooth-hid, gamepad, foreground-service, dashboard, haptics, tdd]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: Android Bluetooth HID capability, descriptor/report packer, adapter seam, and output mapper from Plans 07-01 through 07-03
  - phase: 04-input-stream-and-haptic-transport
    provides: Phone haptic executor and LAN diagnostic/fallback haptic path
provides:
  - Explicit foreground-service actions for starting, stopping, and pairing Android Bluetooth HID gamepad mode
  - Service-owned HID lifecycle and live gun/motion input fanout gated by connected HID host state
  - Dashboard HID proof fields for role, registration, pairing countdown, host connection, input reports, output callbacks, validation, haptic result, and fallback status
  - MainActivity controls for Bluetooth gamepad start, stop, and user-triggered pairing window
affects: [android-host, phase-07, bluetooth-hid, macos-gamepad-path, phone-haptics]

tech-stack:
  added: []
  patterns:
    - Foreground HostSessionService owns Android Bluetooth HID mode separately from LAN desktop diagnostics
    - Dashboard HID proof state is formatted separately from local/LAN phone haptics
    - Pairing discoverability is requested only from explicit user action

key-files:
  created:
    - .planning/phases/07-macos-virtual-joystick-path/07-04-SUMMARY.md
  modified:
    - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
    - android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt
    - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
    - android-host/app/src/main/java/com/btgun/host/MainActivity.kt
    - android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt
    - android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt

key-decisions:
  - "Android HID mode starts only through explicit service actions; live BLE/LAN session startup remains separate."
  - "HID input fanout sends only while the Bluetooth HID adapter reports a connected host."
  - "HID output callback, validation, and phone haptic result are dashboarded as HID proof fields, not LAN haptic proof."
  - "Android discoverability is requested only by the explicit pairing-window action."

patterns-established:
  - "HostSessionHidController: small testable service bridge around AndroidBluetoothHidGamepad lifecycle and fanout."
  - "DashboardHidGamepad: first-class proof model for Android HID role/status/output fields."

requirements-completed: [ANDR-09, ANDR-10, ANDR-11]

duration: 12min
completed: 2026-06-10
---

# Phase 07 Plan 04: Android HID Service and Dashboard Integration Summary

**Foreground-service Bluetooth HID controls with connected-host input fanout and honest dashboard output proof**

## Performance

- **Duration:** 12 min
- **Started:** 2026-06-10T23:17:33Z
- **Completed:** 2026-06-10T23:29:37Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added explicit `HostSessionService` actions for Bluetooth gamepad start, stop, and pairing-window mode.
- Wired service-owned HID lifecycle, async status propagation, stop/close cleanup, and connected-host-only live input fanout from gun and motion updates.
- Added dashboard HID proof fields for blocked states, registration, pairing countdown, host connection, last input report, output callback, validation, phone haptic result, and fallback status.
- Added Activity controls for Start Bluetooth gamepad, Stop Bluetooth gamepad, and Open pairing window.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: HID service control tests** - `abcfe8d` (test)
2. **Task 1 GREEN: HID service lifecycle and input fanout** - `def2e33` (feat)
3. **Task 2 RED: HID dashboard proof tests** - `a27eb32` (test)
4. **Task 2 GREEN: HID dashboard proof state** - `5be5e0d` (feat)
5. **Task 3: Activity controls and pairing-window trigger** - `bf4d3e0` (feat)

_Note: Tasks 1 and 2 followed TDD with RED and GREEN commits. No refactor commit was needed._

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - Adds HID service actions, lifecycle bridge, foreground start path, stop/close cleanup, haptic callback handler, and input fanout.
- `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt` - Adds status-change callback and user-triggered discoverability request for pairing mode.
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - Adds `DashboardHidGamepad` model and HID-specific formatter fields.
- `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` - Adds Bluetooth gamepad controls and HID status rows.
- `android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt` - Adds service-level HID action, stop/close, fanout, and output-status tests.
- `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` - Adds dashboard HID blocked-state and proof-field tests.

## Decisions Made

- Android HID start/stop is separate from `ACTION_START_SESSION`; LAN desktop control and UDP diagnostics are not started by Bluetooth gamepad mode.
- HID input reports are sent from the foreground service only when the adapter status says a host is connected.
- HID output proof uses adapter callback/validation/haptic status. LAN haptics remain visible only as diagnostics/fallback.
- No manifest permission change was needed; existing Bluetooth connect/scan and foreground connected-device permissions cover this plan.

## TDD Gate Compliance

- **Task 1 RED:** `abcfe8d` added failing tests for missing HID service actions, controller, stop/close behavior, connected-only fanout, and output haptic status.
- **Task 1 GREEN:** `def2e33` added service lifecycle/fanout implementation; focused service and adapter tests passed.
- **Task 2 RED:** `a27eb32` added failing tests for missing dashboard HID model/fields.
- **Task 2 GREEN:** `5be5e0d` added dashboard model/formatters; focused dashboard tests passed.
- **REFACTOR:** Not needed.

## Verification

- **PASS:** `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*HostSessionServiceLiveness*' --tests '*AndroidBluetoothHidGamepadState*' --tests '*DashboardState*' --tests '*PermissionGate*' --no-daemon --console=plain`
- **PASS:** `rg -n 'CoreHID|HIDDriverKit|MacosHid|desktop companion.*input path|ProfileMapper|sensitivity|dead zone|smoothing' android-host/app/src/main/java/com/btgun/host` returned no matches.
- **PASS:** `rg -n 'ACTION_START_BLUETOOTH_GAMEPAD|ACTION_STOP_BLUETOOTH_GAMEPAD|ACTION_START_HID_PAIRING_WINDOW' android-host/app/src/main/java/com/btgun/host/MainActivity.kt android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` confirmed Activity uses service constants by name.
- **PASS:** Stub scan found no TODO/FIXME/placeholder UI stubs in changed files. Null/default-value matches are existing optional state fields or test fixtures, not unimplemented UI data sources.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added async HID status propagation from adapter to service**
- **Found during:** Task 1 (HID service lifecycle)
- **Issue:** Without a status-change callback, service/dashboard state would not reliably reflect asynchronous HID proxy, registration, host, or output callbacks.
- **Fix:** Added an `onStatusChanged` callback to `AndroidBluetoothHidGamepad` and wired it into `HostSessionService`.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`, `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`
- **Verification:** Focused service, adapter, dashboard, and permission tests passed.
- **Committed in:** `def2e33`

**2. [Rule 2 - Missing Critical] Made pairing window request Android discoverability**
- **Found during:** Task 3 (Activity controls)
- **Issue:** The prior adapter seam only recorded pairing-window intent; the explicit pairing action needed to trigger Android's discoverability UI instead of reporting a fake open window.
- **Fix:** `AndroidBtGunHidProfileConnector.openPairingWindow` now starts `BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE` with the requested duration, only from the explicit pairing action.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`
- **Verification:** Focused plan-level Android tests passed; source guard found no scope creep.
- **Committed in:** `bf4d3e0`

---

**Total deviations:** 2 auto-fixed (Rule 2: 2).
**Impact on plan:** Both fixes were required for honest live status and explicit user-controlled pairing. No profile, visualizer, LAN input, CoreHID, or DriverKit scope was added.

## Issues Encountered

- Sandbox-blocked Gradle startup reported `java.net.SocketException: Operation not permitted`; required Gradle gates were rerun outside the sandbox with approval.
- One dashboard formatter assertion failed during GREEN; direct class execution identified separator mismatch, and the formatter was corrected before commit.

## Known Stubs

None.

## Threat Flags

None. New HID lifecycle, discoverability, input fanout, and output haptic paths are covered by the plan threat model (`T-07-12` through `T-07-15`).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 07-05. Android app now exposes explicit Bluetooth HID gamepad controls and status, but live macOS pairing/Game Controller proof and HID output behavior still require real hardware/user verification.

## Self-Check: PASSED

- Files exist: `HostSessionService.kt`, `AndroidBluetoothHidGamepad.kt`, `DashboardState.kt`, `MainActivity.kt`, `HostSessionServiceLivenessTest.kt`, `DashboardStateTest.kt`, and this summary.
- Task commits exist: `abcfe8d`, `def2e33`, `a27eb32`, `5be5e0d`, `bf4d3e0`.
- Plan-level Gradle tests and source scope guard passed after final changes.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
