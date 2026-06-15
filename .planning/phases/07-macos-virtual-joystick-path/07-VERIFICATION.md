---
phase: 07-macos-virtual-joystick-path
verified: 2026-06-11T14:39:36Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 7: Android Bluetooth HID Gamepad Path Verification Report

**Phase Goal:** User with discontinued iPega AR gun can use Android phone as a Bluetooth HID gamepad for macOS Apple Silicon, so macOS sees a normal controller without paid Apple virtual HID entitlements while completed Windows VHF path remains fallback.
**Verified:** 2026-06-11T14:39:36Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Android host app detects whether the phone can act as a Bluetooth HID gamepad peripheral and reports a clear blocked state when unsupported. | VERIFIED | `AndroidHidCapability.kt` models Bluetooth off, missing connect/advertise permission, not probed, proxy unavailable, registration failed, no host, disconnected, and connected states. `PermissionGate.kt` includes `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE`; focused Gradle suite passed. |
| 2 | Android host app exposes normalized gun controls and Android motion aim as a regular gamepad-style Bluetooth HID report. | VERIFIED | `BtGunHidDescriptor.kt` declares Game Pad usage, report ID 1 input, report ID 2 output, 8 buttons, and 4 axes. `BtGunHidReportPacker.kt` packs `GunInputState` plus `MotionSample` into little-endian signed axes with normalized/calibrated aim first and raw aim fallback. `HostSessionService.kt` fans live state into `AndroidBluetoothHidGamepad.sendInput`. |
| 3 | macOS Apple Silicon pairs to the Android phone and sees an OS-visible gamepad-style joystick without CoreHID/DriverKit virtual HID entitlement work. | VERIFIED | Manifest rows include current-phone HID proxy pass, register-app pass, pairing-window pass, macOS Bluetooth paired pass, IOHID/GameController input pass, and browser-visible 8-button/4-axis input pass. Docs forbid desktop companion LAN input as DESK-03 proof. |
| 4 | Bluetooth HID output or rumble reports route to Android phone haptics when supported, or the app reports the limitation clearly. | VERIFIED | `AndroidBluetoothHidGamepad.kt` handles `onSetReport` and `onInterruptData`, validates through `BtGunHidOutputReportMapper.kt`, sends valid commands to `DesktopHapticCommandExecutor`, and records output callback/validation/haptic status. Manifest records macOS output as unsupported/deferred after live no-haptics/no-callback evidence, which satisfies the requirement's explicit limitation branch. |
| 5 | Repository documents Android Bluetooth HID setup, macOS pairing, output-report behavior, compatibility risks, and fallback to completed Windows virtual joystick path. | VERIFIED | `docs/setup/android-bluetooth-hid-gamepad.md` documents HID_DEVICE compatibility, permission states, Start Bluetooth gamepad, pairing window, macOS pairing proof, report layout, output behavior, redaction, CoreHID/DriverKit blocked evidence, and Windows VHF fallback boundaries. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt` | HID role status model and blocked labels | VERIFIED | 131 lines; substantive capability state machine; covered by focused tests. |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt` | Bluetooth gamepad descriptor/report constants | VERIFIED | Uses Generic Desktop/Game Pad usage, Button page, report IDs 1/2, 8 buttons, 4 axes. |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` | HID input report packing from normalized input/motion | VERIFIED | Packs button bits, stick axes, aim axes, stale behavior, aim source metadata. |
| `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt` | BluetoothHidDevice adapter/proxy/callback seam | VERIFIED | Registers app, sends reports only when proxy/registration/host are ready, handles output callbacks, ignores stale callback generations. |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt` | Strict output report to phone haptic command mapper | VERIFIED | Rejects wrong ID/version/length/range/flags/reserved bytes; valid report creates `DesktopHapticCommand`. |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | Explicit HID service actions and live input fanout | VERIFIED | Defines start/stop/pairing-window actions, permission blocks, foreground HID mode, live input fanout, output-to-phone haptic handling. |
| `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | HID dashboard state and limitation reporting | VERIFIED | Shows role, registration, pairing, host connection, last input, output callback, validation, haptic result, and fallback text. |
| `tools/macos/BtGunGameControllerProbe.swift` | macOS sanitized controller/input/output probe | VERIFIED | 721 lines; `xcrun swiftc -typecheck` passed after approved cache-write rerun. |
| `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | Sanitized live proof rows | VERIFIED | JSONL parses with `jq`; contains required current-phone pass rows and unsupported haptics rows. |
| `docs/setup/android-bluetooth-hid-gamepad.md` | Setup, pairing, report, output, fallback docs | VERIFIED | Required topic grep passed; docs state Android HID primary, CoreHID/DriverKit blocked, Windows VHF fallback. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HostCapabilityProbe.kt` | `PermissionGate.kt` / `AndroidHidCapability.kt` | `bluetoothHid` permission and role fields | WIRED | Permission gate exposes HID role separately from BLE/LAN session readiness. |
| `BtGunHidReportPacker.kt` | `NormalizedEvents.kt` | `GunInputState` and `MotionSample` | WIRED | Packer imports app model types and no desktop production module. |
| `AndroidBluetoothHidGamepad.kt` | `BtGunHidReportPacker.kt` | `packInputReport` then `sendReport` | WIRED | `sendInput` packs payload and calls `BluetoothHidDevice.sendReport` through proxy. |
| `AndroidBluetoothHidGamepad.kt` | `BtGunHidOutputReportMapper.kt` | `onSetReport` / `onInterruptData` | WIRED | Output callbacks validate bytes, report errors for invalid input, and call haptic handler for valid command. |
| `HostSessionService.kt` | `AndroidBluetoothHidGamepad.kt` | service actions and live input fanout | WIRED | Explicit start/stop/pairing actions own HID lifecycle; gun/motion updates fan out only when host connected. |
| `DashboardState.kt` | `BtGunHidStatus.kt` | status formatter | WIRED | Dashboard displays HID proof/limitation fields separately from LAN diagnostics. |
| `docs/setup/android-bluetooth-hid-gamepad.md` | `phase7-android-bluetooth-hid.jsonl` | proof row names | WIRED | Docs name manifest row contract and fallback decision rules. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `HostSessionService.kt` | `gunInputState`, `lastMotionSample` | Existing BLE gun/session and motion pipeline | Yes - live state passed to `hidSessionController.fanOutLiveInput` then `sendInput`. | FLOWING |
| `BtGunHidReportPacker.kt` | `pressedControls`, `stickAxisX/Y`, `aimX/Y`, `rawAimX/Y` | `GunInputState` and `MotionSample` | Yes - report bytes derive from state/motion, not hardcoded fixtures. | FLOWING |
| `AndroidBluetoothHidGamepad.kt` | output callback payload | Android `BluetoothHidDevice.Callback` | Yes when host sends output; otherwise status remains visible and limitation evidence records unsupported path. | FLOWING / LIMITATION REPORTED |
| `DashboardState.kt` | `hidGamepadStatus`, `bluetoothHidRole` | `HostSessionState` and `PermissionGateState` | Yes - formatter reads service state and capability gate. | FLOWING |
| `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | proof rows | Live Android/macOS observations and probe evidence refs | Yes - current-phone and macOS rows are concrete pass/unsupported JSONL records. | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Focused Android HID unit coverage | `gradle -p android-host testDebugUnitTest --tests '*AndroidBluetoothHidGamepadState*' --tests '*AndroidHidCapability*' --tests '*PermissionGate*' --tests '*HostSessionServiceLiveness*' --tests '*BtGunHidDescriptor*' --tests '*BtGunHidReportPacker*' --tests '*BtGunHidOutputReportMapper*' --tests '*DashboardState*' --no-daemon --console=plain` with required env | Initial sandbox run failed on Gradle socket permission; approved rerun passed, BUILD SUCCESSFUL in 14s. | PASS |
| macOS probe compiles | `xcrun swiftc -typecheck tools/macos/BtGunGameControllerProbe.swift` | Initial sandbox run failed on clang module cache permission; approved rerun passed. | PASS |
| Evidence manifest parses | `jq -c . docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | All 9 JSONL rows parsed. | PASS |
| Setup docs cover required topics | `rg -n 'Android Bluetooth HID|HID_DEVICE|Start Bluetooth gamepad|pairing-mode|Game Controller|report ID|onSetReport|unsupported|Windows VHF fallback|corehid-runtime-blocked|redaction' docs/setup/android-bluetooth-hid-gamepad.md` | Required topics found. | PASS |
| Sensitive-value redaction | `! rg -n '([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}|qr_secret|stream key|HMAC key|private key|proof value|manual_code=|pairing_secret|android_id|deviceId|serialNumber|screenshot_path' ...` | No matches. | PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| Conventional shell probes | `find scripts -path '*/tests/probe-*.sh' -type f` | No `probe-*.sh` files found. | SKIP |
| macOS Game Controller probe source | `xcrun swiftc -typecheck tools/macos/BtGunGameControllerProbe.swift` | Typecheck passed on approved rerun. | PASS |
| Live macOS Bluetooth/GameController/IOHID proof | Manifest rows `phase7-macos-bluetooth-paired`, `phase7-gamecontroller-input`, `phase7-macos-output-unsupported` | Current-phone input proof passed; macOS haptics unsupported/deferred recorded. | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ANDR-09 | 07-01, 07-03, 07-04, 07-06 | Android HID peripheral role with clear blocked state | SATISFIED | Capability model, permission gate, adapter proxy/register states, current-phone proxy/register pass rows. |
| ANDR-10 | 07-02, 07-04 | Normalized gun controls and motion aim to gamepad HID reports | SATISFIED | Descriptor/packer code, service fanout, current-phone input rows. |
| ANDR-11 | 07-03, 07-04, 07-05 | Receive HID output or report limitation clearly | SATISFIED | Output mapper/callback code plus macOS unsupported/deferred evidence and dashboard fallback text. |
| DESK-03 | 07-05, 07-06 | macOS sees Android phone as Bluetooth HID gamepad without paid virtual HID entitlement | SATISFIED | Manifest records macOS Bluetooth paired and GameController/IOHID/browser input pass; docs keep CoreHID/DriverKit as blocked/fallback only. |
| DESK-06 | 07-03, 07-05, 07-06 | macOS output/rumble to phone haptics or clear limitation while preserving phone haptics | SATISFIED | Stable macOS haptic limitation recorded; LAN/Windows VHF phone-haptic fallback preserved and not falsely counted as HID output proof. |
| PACK-03 | 07-06 | Selected macOS strategy documented | SATISFIED | Setup doc names Android Bluetooth HID primary and CoreHID/DriverKit blocked evidence only. |
| PACK-06 | 07-01, 07-06 | Android HID setup, compatibility, pairing, descriptors, output, fallback docs | SATISFIED | Setup doc covers all required topics; manifest schema and redaction policy present. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `HostSessionService.kt` | 134 | `return null` | INFO | This is the successful "not blocked" branch from `hidStartBlockedStatusFor`, not an empty implementation. |
| `DashboardState.kt` / `MainActivity.kt` | multiple | `placeholders` identifier | INFO | Existing dashboard placeholder model, not user-visible unfinished Phase 7 work. |

No `TBD`, `FIXME`, or `XXX` blocker markers found in Phase 7 implementation artifacts.

### Human Verification Required

None outstanding. The manual Phase 7 checks were already closed by sanitized manifest evidence: current-phone HID proxy/register/pairing pass rows, macOS Bluetooth paired row, live input rows, and macOS output unsupported/deferred rows.

### Gaps Summary

No blocking gaps found. macOS browser/GameController haptics through Android Bluetooth HID remain unsupported/deferred, but this is explicitly allowed by ANDR-11 and DESK-06 when the limitation is clearly reported and v1 phone-haptic fallback paths remain available.

---

_Verified: 2026-06-11T14:39:36Z_
_Verifier: the agent (gsd-verifier)_
