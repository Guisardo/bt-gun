---
quick_id: 260616-ofd
slug: fix-android-bluetooth-hid-sdp-subclass-f
status: complete
completed: 2026-06-16
commit: pending
---

# Quick Task 260616-ofd Summary

Fixed the Android Bluetooth HID SDP subclass from `0x05` to `0x02`, matching `BluetoothHidDevice.SUBCLASS2_GAMEPAD`.

## Root Cause

The input report descriptor was a Generic Desktop Game Pad and browser Gamepad API could parse it, but the SDP subclass advertised `0x05`. Android SDK defines `0x05` as `SUBCLASS2_DIGITIZER_TABLET`; `SUBCLASS2_GAMEPAD` is `0x02`. Steam apps may filter on SDP/device class before accepting a controller, so browser success did not prove Steam compatibility.

## Changes

- Changed the Android HID gamepad SDP subclass to `0x02`.
- Added a regression assertion for the SDP subclass in the HID state test.
- Updated setup and compatibility docs so Steam app detection is not claimed from browser/GameController proof alone.
- Added a sanitized evidence row for the 2026-06-16 Steam detection blocker.

## Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*AndroidBluetoothHidGamepadState*' --no-daemon --console=plain` passed.
- `git diff --check` passed.

## Retest Needed

Install the rebuilt user app, forget the old Bluetooth HID pairing on macOS, re-pair the phone, then test Steam app detection again. If Steam still misses it, capture Steam Input/device visibility separately from browser Gamepad API.
