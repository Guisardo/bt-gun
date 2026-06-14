---
quick_id: 260614-4qb
slug: recover-android-gun-connection-stuck-aft
status: complete
completed: 2026-06-14
commit: b3644d6
---

# Quick Task 260614-4qb Summary

Recovered the Android gun connection and installed a patched APK.

## Root Cause

The app initially left `HostSessionService` running after the UI closed without Stop session. After the app retry loop was stopped, the gun was still discoverable but Android's BLE/GATT stack repeatedly failed direct LE connects with `status=133`. A full phone reboot cleared the stuck stack; Bluetooth toggle alone did not.

## Changes

- Added Stop session action and app-open intent to the foreground notification.
- Added `onTaskRemoved` cleanup for active foreground and HID sessions.
- Added Start-session guards so repeated Start does not double-start active sessions or crash.
- Added failed-foreground restart handling so Start can recover from `ERROR` while the foreground service is still present.
- Avoided `MotionAimProvider.unavailableSample()` crash when Android reports a motion provider but sensor listener registration fails.
- Added liveness tests for notification, task removal, duplicate Start, and failed-session restart behavior.

## Verification

- `git diff --check` passed.
- Focused `HostSessionServiceLivenessTest` passed.
- Full Android host `testDebugUnitTest` passed.
- Debug APK assembled and installed successfully.
- Post-reboot physical retest connected to `ARGunGame`: GATT `status=0`, ACL connected, service discovery started, and `fff3` notification setup reached.

## Notes

If `status=133` repeats after an abrupt app/device interruption, force-stop the app first. If Bluetooth toggle does not clear it on this Samsung phone, reboot the phone; reboot was the step that cleared the lower-level GATT state in this run.
