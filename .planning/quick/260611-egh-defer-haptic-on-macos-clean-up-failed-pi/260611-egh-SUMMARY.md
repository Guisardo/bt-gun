---
quick_id: 260611-egh
status: complete
completed_at: 2026-06-11
commits: []
---

# Quick Task 260611-egh Summary

## Result

macOS browser/GameController haptics for Android Bluetooth HID are deferred. The failed PID ForceFeedback experiment is removed from active Android HID code and tests.

## Changes

- Removed PID descriptor constants, experimental descriptor bytes, PID set/get handling, and PID haptic routing tests.
- Kept the stable gamepad descriptor: 8 buttons, X/Y/Z/Rx axes, and generic output report ID 2.
- Added sanitized evidence/state notes that PID broke browser-visible gamepad enumeration and did not unlock vibration.
- Advanced Phase 7 state to Plan 07-06 next.

## Verification

- Pass: `gradle -p android-host :app:testDebugUnitTest --tests '*BtGunHidDescriptor*' --tests '*BtGunHidReportPacker*' --tests '*AndroidBluetoothHidGamepadState*' --tests '*DashboardState*'`.
- Pass: no `BtGunHidPid`, `EXPERIMENTAL_PID`, or `PID_` symbols remain in Android production/test HID code.
- Pass: `git diff --check`.
