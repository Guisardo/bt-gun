---
quick_id: 260616-hid
slug: diagnostic-only-boring-standard-hid
status: complete
created: 2026-06-16
---

# Quick Task 260616-hid: Diagnostic-Only Boring Standard HID

## Goal

Add a boring-standard Bluetooth HID profile for the debug host app only, so Steam/SDL detection can be retested without changing the user-facing Gamepad Extension app.

## Tasks

1. Add profile-aware HID descriptor and input packing.
   - Keep `current_user` as the default 22-button user-facing profile.
   - Add `boring_standard` with 12 buttons, hat switch, X/Y/Z/Rx axes, and profile-specific neutral report bytes.

2. Select diagnostic profile only from the debug host.
   - Add `com.btgun.host.HID_PROFILE=boring_standard` to `android-host/app`.
   - Leave `android-host/user-app` without metadata so it defaults to `current_user`.
   - Resolve metadata in `HostSessionService` and pass the selected profile into the HID driver.

3. Update focused tests and docs.
   - Cover descriptor pins, report packing, neutral GET_REPORT/stop/disconnect/unplug bytes, metadata defaulting, and fanout dedupe.
   - Document both HID profiles and the Steam retest boundary.

## Verification

- `gradle -p android-host testDebugUnitTest --tests '*BtGunHidDescriptor*' --tests '*BtGunHidReportPacker*' --tests '*AndroidBluetoothHidGamepadState*' --tests '*HostSessionServiceLiveness*' --no-daemon --console=plain`
- `gradle -p android-host :app:assembleDebug --no-daemon --console=plain`
