---
quick_id: 260616-hid
slug: diagnostic-only-boring-standard-hid
status: complete
completed: 2026-06-16
commit: pending
---

# Quick Task 260616-hid Summary

Implemented a diagnostic-only boring-standard HID profile for the debug host app while keeping the user-facing app on the default current-user profile.

## Changes

- Added explicit HID profiles: `current_user` and `boring_standard`.
- Added 12-button + hat + X/Y/Z/Rx diagnostic descriptor.
- Made input packing, neutral reports, GET_REPORT fallback, SDP settings, and fanout dedupe profile-aware.
- Added debug-host manifest metadata to select `boring_standard`; user app keeps default profile.
- Updated Steam compatibility docs and evidence notes.

## Verification

- Focused HID descriptor, packer, gamepad state, and host liveness tests passed.
- Full Android debug unit tests passed.
- Debug host and user app APK assembly passed.
- Debug host APK installed successfully on USB device `3200337647aea561`.
- Remaining live gate: forget/re-pair the diagnostic Bluetooth HID and retest Steam Controller Settings.
