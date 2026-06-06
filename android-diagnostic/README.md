# Android Diagnostic Module

Diagnostic-only Phase 1 validation tooling for the iPega AR gun. This module exists to test Android visibility of the physical gun before production Android host architecture is chosen.

## Scope

- Enumerate Android `InputDevice` entries and record whether the gun appears as a standard controller.
- Record `KeyEvent` and `MotionEvent` observations while pressing trigger, reload, joystick, X/Y/A/B, and any visible axes.
- Scan BLE services/characteristics and Bluetooth Classic bonded devices, UUIDs, and socket candidates.
- Emit structured diagnostic log lines that Plan 03 can turn into capture manifests and normalized fixtures.
- Record permission state and unavailable/denied states explicitly so scan absence is not treated as protocol proof.

## Out Of Scope

- No production Android host architecture.
- No LAN transport, QR pairing, UDP/TCP session, or desktop link behavior.
- No desktop HID, virtual controller, profiles, aim mapping, or visualizer behavior.
- No claim that `DISC-02` or `DISC-03` is complete without Plan 03 physical hardware evidence.

## Evidence Rule

Static clues from `docs/protocol/ipega-phase1-clues.md` are hypotheses. A finding is verified only after Plan 03 links:

1. static `clue_id`
2. physical hardware capture or app-observed frame
3. normalized fixture row

## Build Boundary

Gradle files are scaffolding only. Do not build, install, or download Gradle/plugin dependencies until Plan 03 performs human dependency review.

## Dependency Review Gate

Do not run Gradle build/install commands until a human reviews these generated coordinates:

- Root plugin: `com.android.application` version `8.7.3`
- Root plugin: `org.jetbrains.kotlin.android` version `2.0.21`
- Repositories: `google()`, `mavenCentral()`, `gradlePluginPortal()`
- App package: `com.btgun.diagnostic`
- SDKs: `compileSdk = 35`, `minSdk = 23`, `targetSdk = 35`

Plan 03 Task 2 is the blocking checkpoint for this review and for physical capture. No dependency download or Android install is part of Task 1.

## Physical Capture Workflow

Use `android-diagnostic/scripts/collect-phase1-captures.sh` after dependency review and after the diagnostic APK is installed by an approved path. The helper is package-free: it checks for `adb`, verifies exactly one attached USB-debug Android device, creates ignored evidence directories under `.evidence/phase1/`, records logcat while the hardware action happens, and writes local pointer metadata.

Ignored raw output targets:

- `.evidence/phase1/raw/`
- `.evidence/phase1/hci/`
- `.evidence/phase1/app-logs/`

Capture sequence:

1. Power the iPega gun and make it pairable or paired with the Android device.
2. Connect the Android device over USB with debugging enabled.
3. Review dependency coordinates above before any Gradle build/install.
4. Run one capture per action, using the capture ids listed in `docs/protocol/ipega-phase1-hardware.md`.
5. Press or move only the named control during that capture window.
6. Update `docs/protocol/ipega-phase1-hardware.md` and `docs/evidence/manifests/phase1-captures.jsonl` with the observed outcome.

Example:

```bash
android-diagnostic/scripts/collect-phase1-captures.sh \
  --capture-id trigger-001 \
  --action "trigger down/up" \
  --clue-id ARGUN2021-CONTROL-001 \
  --package com.btgun.diagnostic
```

Use `--bugreport` or `BTGUN_COLLECT_BUGREPORT=1` only when Bluetooth HCI snoop logging is enabled on the Android device. The bugreport stays ignored under `.evidence/phase1/hci/`; commit only sanitized manifest pointers.

## Capture Targets

Required Plan 03 hardware actions:

- `input-device-scan-001` - Android `InputDevice`, `KeyEvent`, and `MotionEvent` visibility.
- `ble-scan-001` - BLE service and characteristic observations for `fff0`, `fff1`, `fff3`, and `fff5` candidates.
- `classic-scan-001` - Classic bonded device, SPP UUID, and channel-1 observations.
- `trigger-001` - Trigger down/up.
- `reload-001` - Reload down/up.
- `joystick-001` - Stick X/Y axes across neutral and extremes.
- `button-x-001`, `button-y-001`, `button-a-001`, `button-b-001` - Face button down/up.
- `rumble-ble-fff5-001` and `rumble-classic-spp-001` - Bounded clue-led output attempts only after input path evidence exists.

Rows may record explicit denied, unavailable, timeout, no-device, no-frame, or no-motor outcomes. Do not mark any finding verified until the static clue, physical capture, and normalized fixture all exist.
