---
phase: 07
slug: android-bluetooth-hid-gamepad-path
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-10
updated: 2026-06-11
---

# Phase 07 - Validation Strategy

Per-phase validation contract after the Android Bluetooth HID gamepad reroute. CoreHID and DriverKit rows are retained only through historical summaries and fallback docs; they are not the Phase 7 primary-path validation target.

Final Phase 7 result: Android Bluetooth HID input proof passed on the current phone, so the alternate-phone fallback gate was not required. macOS browser/GameController haptics are unsupported/deferred for this path after live no-output/no-vibration evidence. Windows VHF remains the completed fallback path, but no Windows fallback-selection row is needed because DESK-03 input proof passed.

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | Android/JVM main-function tests from existing Gradle `test` tasks |
| Config file | `android-host/app/build.gradle.kts`; `desktop-companion/build.gradle.kts` only when fallback docs/status touch desktop companion |
| Quick run command | `cd android-host && gradle test` after local Gradle startup repair |
| Full suite command | `cd android-host && gradle test && cd ../desktop-companion && gradle test` after local Gradle startup repair |
| Estimated runtime | hardware-independent tests under 2 min after Gradle repair; live Bluetooth proof is manual |

## Sampling Rate

- **After every task commit:** run the plan's Android unit/static checks, or record the local Gradle blocker if `libnative-platform.dylib` still prevents startup.
- **After every plan wave:** run Android tests plus docs/static evidence checks touched by that wave.
- **Before `$gsd-verify-work`:** Android HID unit tests green, live macOS Bluetooth/Game Controller proof captured, output-report proof or unsupported row captured, and redaction scan clean.
- **Max feedback latency:** one task commit without automated proof unless the task is explicitly manual/live.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | ANDR-09 | T-07-01 / T-07-02 | Local Gradle startup either works or blocker is recorded before test-dependent work claims pass | tooling | `cd android-host && gradle test` | `07-01-SUMMARY.md` | ✅ green |
| 07-01-02 | 01 | 1 | ANDR-09 | T-07-01 / T-07-05 | Capability gate distinguishes Bluetooth off, missing `BLUETOOTH_CONNECT`, HID proxy unavailable, registration failed, no host connected, and host disconnected | unit | `cd android-host && gradle test` | `AndroidHidCapability.kt` | ✅ green |
| 07-02-01 | 02 | 2 | ANDR-10 | T-07-03 | HID descriptor exposes eight browser-compatible buttons and four axes matching BT Gun v1 semantics | unit/golden | `cd android-host && gradle test` | `BtGunHidDescriptor.kt` | ✅ green |
| 07-02-02 | 02 | 2 | ANDR-10 | T-07-03 | Input report packer pins button bit order, little-endian signed axes, calibrated aim preference, raw aim fallback, and stale center behavior | unit/golden | `cd android-host && gradle test` | `BtGunHidReportPacker.kt` | ✅ green |
| 07-03-01 | 03 | 3 | ANDR-09, ANDR-10 | T-07-02 / T-07-06 | HID adapter registers/unregisters explicitly and sends reports only when registered with a connected host | unit/fake adapter | `cd android-host && gradle test` | `AndroidBluetoothHidGamepad.kt` | ✅ green |
| 07-03-02 | 03 | 3 | ANDR-11 | T-07-04 | Invalid host output reports trigger error/status and do not vibrate the phone | unit/fake adapter | `cd android-host && gradle test` | `BtGunHidOutputReportMapper.kt` | ✅ green |
| 07-04-01 | 04 | 4 | ANDR-09, ANDR-10 | T-07-02 / T-07-06 | `HostSessionService` starts/stops HID mode by explicit action and fans out live gun/motion updates without changing LAN diagnostics | unit/static | `cd android-host && gradle test` | `HostSessionService.kt` | ✅ green |
| 07-04-02 | 04 | 4 | ANDR-09, ANDR-11 | T-07-05 | Dashboard exposes HID role, registration, pairing window, host connection, last input report, output callback/result, and fallback status | unit | `cd android-host && gradle test` | `DashboardState.kt` | ✅ green |
| 07-05-01 | 05 | 5 | DESK-03 | T-07-07 / T-07-08 | macOS pairs to Android phone over Bluetooth and sees a gamepad/controller surface without desktop companion in the input path | manual/live | `rg -n 'phase7-macos-bluetooth-paired|phase7-gamecontroller-input' docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | ✅ green |
| 07-05-02 | 05 | 5 | DESK-06, ANDR-11 | T-07-04 / T-07-09 | HID output callback result is captured as phone haptic pass or honest macOS unsupported/deferred result after live probe | manual/live | `rg -n 'phase7-hid-output-callback|phase7-macos-output-unsupported' docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | ✅ green |
| 07-06-01 | 06 | 6 | PACK-03, PACK-06 | T-07-10 | Docs name Android Bluetooth HID as primary, CoreHID/DriverKit as retained fallback evidence, and Windows VHF as fallback decision path | docs/static | `rg -n 'Android Bluetooth HID|HID_DEVICE|registerApp|sendReport|onSetReport|Windows VHF fallback|corehid-runtime-blocked' docs/setup/android-bluetooth-hid-gamepad.md` | `docs/setup/android-bluetooth-hid-gamepad.md` | ✅ green |
| 07-06-02 | 06 | 6 | PACK-06 | T-07-10 | Evidence manifest and redaction scan exclude Bluetooth MACs, serials, account names, screenshots, keys, pairing material, and device ids | docs/static | `! rg -n '([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}|qr_secret|stream key|HMAC key|private key|device[_ -]?id|serial|screenshot_path' docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl docs/setup/android-bluetooth-hid-gamepad.md` | `phase7-android-bluetooth-hid.jsonl` | ✅ green |

*Status: ⬜ pending | ✅ green | ❌ red | ⚠ flaky*

## Wave 0 Requirements

- [x] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidDescriptorTest.kt` - descriptor bytes and semantic parity.
- [x] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt` - input report golden vectors.
- [x] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidOutputReportMapperTest.kt` - host output report validation.
- [x] `android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt` - adapter status transitions with fake proxy/callback seams.
- [x] Extend `android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java` for HID capability rows.
- [x] Extend `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` for HID dashboard fields.
- [x] `docs/setup/android-bluetooth-hid-gamepad.md` - setup, compatibility, pairing, report, output behavior, and fallback docs.
- [x] `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` - sanitized proof rows.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| HID profile support on the current phone | ANDR-09 | OEM Bluetooth HID Device support must be observed on real hardware | Start Android host, tap Start Bluetooth gamepad, record proxy/registration status in sanitized manifest row `phase7-android-hid-proxy`. |
| Alternate-phone check before fallback | ANDR-09, DESK-03 | One phone failure cannot prove the primary path impossible | If current phone lacks HID role or macOS pairing fails, repeat proxy/register/pairing proof on an alternate Android phone before marking fallback. |
| macOS Bluetooth pairing and controller visibility | DESK-03 | macOS Bluetooth UI/Game Controller surface is host OS behavior | Pair Android phone from macOS, open tester/Game Controller surface, confirm `BT Gun` gamepad/controller is visible without desktop companion in input path. |
| Live physical gun and phone motion drive controller input | DESK-03 | Requires physical iPega gun and macOS-visible controller surface | Press trigger/reload/X/Y/A/B, move stick, move phone aim, and record sanitized tester evidence plus user confirmation row `phase7-gamecontroller-input`. |
| HID output or unsupported behavior | DESK-06, ANDR-11 | macOS may or may not send generic output/rumble to this descriptor | Trigger host output probe if available; otherwise record no callback seen with Android status, unsupported reason, and preserved LAN/Windows haptic fallback. |

## Validation Sign-Off

- [x] All Android HID logic tasks have automated unit/static verification or a recorded local Gradle blocker.
- [x] No three consecutive implementation tasks omit automated verification.
- [x] Wave 0 covers all missing test/evidence/doc scaffolds.
- [x] No watch-mode flags.
- [x] Live DESK-03 proof uses Bluetooth HID/Game Controller path, not desktop companion LAN input.
- [x] DESK-06 pass requires host-origin HID output callback and phone vibration; unsupported/deferred status requires live no-callback/no-usable-output evidence.
- [x] Redaction scan passes for evidence/docs.
- [x] `nyquist_compliant: true` remains set in frontmatter.

**Approval:** complete for Phase 7 Android Bluetooth HID input proof. DESK-06 macOS browser/GameController haptics remain unsupported/deferred with explicit evidence.
