---
quick_id: 260616-ofd
slug: fix-android-bluetooth-hid-sdp-subclass-f
status: complete
created: 2026-06-16
---

# Quick Task 260616-ofd: Fix Android Bluetooth HID SDP Subclass For Steam App Detection

## Goal

Address the user-app Bluetooth HID finding: browser Gamepad API detects the gamepad, but Steam apps do not.

## Tasks

1. Inspect the Android HID SDP and descriptor path.
   - Files: `android-host/runtime/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`, `android-host/runtime/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt`.
   - Done: Found the SDP subclass set to `0x05`, while Android SDK `BluetoothHidDevice.SUBCLASS2_GAMEPAD` is `0x02`.

2. Correct the SDP subclass and pin it in tests.
   - Files: `AndroidBluetoothHidGamepad.kt`, `AndroidBluetoothHidGamepadStateTest.kt`.
   - Done: SDP registration now uses gamepad subclass `0x02`, and the state test asserts it.

3. Document the compatibility boundary.
   - Files: `docs/setup/android-bluetooth-hid-gamepad.md`, `docs/limits/v1-compatibility-limits.md`, `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`.
   - Done: Browser/GameController input remains supported; Steam app detection is tracked separately until fresh rebuilt-app proof passes.

## Verification

- Focused Android runtime test for `AndroidBluetoothHidGamepadStateTestKt`.
- `git diff --check`.
