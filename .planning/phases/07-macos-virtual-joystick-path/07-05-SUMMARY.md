---
phase: 07-macos-virtual-joystick-path
plan: 05
status: complete
completed_at: 2026-06-11
commits: []
---

# 07-05 Summary: Live macOS Bluetooth HID Proof

## Result

DESK-03 is accepted for the current Android Bluetooth HID path. DESK-06 is closed as unsupported/deferred for macOS browser/GameController haptics.

Current phone proof now shows Android HID proxy/register/pairing works, macOS pairs to the phone, Android sees macOS as connected HID host, and macOS IOHID receives live input for trigger, reload, X/Y/A/B, stick X/Y, and aim X/Y. Browser retesting with gpadtester refined the HID report so A/B/X/Y are buttons 1-4, reload/trigger are buttons 7/8 (LT/RT labels), and aim uses browser axes 2/3 via HID usages Z/Rx. The CLI `GameController` API returned no visible controller, so the input pass is based on macOS IOHID fallback plus browser Gamepad API observations against the same gamepad-class HID device.

The PID/Physical Interface Device ForceFeedback experiment is removed from the active Android HID path. It paired at the Bluetooth layer but stopped the gamepad from becoming browser-visible on macOS. The stable descriptor remains an 8-button, 4-axis gamepad with a generic output report callback hook only.

## Evidence Added

- `phase7-android-hid-proxy`: pass.
- `phase7-android-hid-register-app`: pass.
- `phase7-android-hid-pairing-window`: pass.
- `phase7-macos-bluetooth-paired`: pass.
- `phase7-gamecontroller-input`: pass by macOS IOHID fallback.
- `phase7-gamecontroller-input` browser remap retest: pass; trigger/reload map to LT/RT labels and aim maps to right stick axes 2/3.
- `phase7-macos-output-unsupported`: unsupported.
- `phase7-macos-output-unsupported` haptics defer row: unsupported; PID ForceFeedback experiment rejected for active path.

## Verification

- `xcrun swiftc -typecheck tools/macos/BtGunGameControllerProbe.swift`: pass.
- `xcrun swiftc tools/macos/BtGunGameControllerProbe.swift -o /private/tmp/BtGunGameControllerProbe`: pass.
- Unsandboxed probe `phase7-20260610-macos-iohid-unsandboxed`: saw gamepad plus X/Y/A/B and stick X/Y.
- Unsandboxed probe `phase7-20260610-macos-iohid-trigger-aim`: saw reload, X/Y/A/B, stick X, aim X/Y.
- Unsandboxed probe `phase7-20260610-macos-trigger-retest`: saw trigger after Android calibration mode was cleared.
- Unsandboxed probe `phase7-20260611-browser-z-rx-retest`: saw aim X/Y on `iohid-axis-50/51` after browser slot tuning.
- gpadtester raw data saw 8 buttons and 4 axes, with right stick using browser `axes[2]` and `axes[3]`.
- gpadtester raw data after calibrated restore saw centered right stick values `axes[2] -0.0238` and `axes[3] -0.0030` while Android preview showed `Calibrated aim active`.
- Focused Android unit tests for HID descriptor/report packing, dashboard status, HID state, and service liveness: pass.
- Android logcat after macOS pairing: `hostConnection=CONNECTED`, `lastInputReport.result=SENT`, output callback still `NONE`.
- Android logcat during remap retest showed only transition logs, not per-report HID status spam.
- gpadtester reported `No Vibration` for the stable browser-visible descriptor.
- PID/ForceFeedback descriptor experiment restored no browser haptic capability and temporarily removed browser-visible controller enumeration; stable descriptor restored visibility.

## Follow-Up

- Decide whether IOHID fallback proof is acceptable for DESK-03 or whether a GUI Game Controller tester/app bundle must prove `GCController`.
- Keep macOS browser/GameController haptics deferred unless a later host-origin output path proves otherwise.
- After HID descriptor changes, forget/remove the old macOS Bluetooth pairing before pairing again because macOS can cache the old descriptor.
