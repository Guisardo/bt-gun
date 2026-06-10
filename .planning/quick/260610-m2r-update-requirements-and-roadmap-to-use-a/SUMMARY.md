---
quick_id: 260610-m2r
slug: update-requirements-and-roadmap-to-use-a
status: complete
completed: 2026-06-10T18:53:43.550Z
files:
  - .planning/REQUIREMENTS.md
  - .planning/ROADMAP.md
  - .planning/STATE.md
---

# Quick Task Summary: Android Bluetooth HID Gamepad Reroute

## Result

Requirements and roadmap now make Android phone Bluetooth HID gamepad mode the primary no-subscription macOS path. Completed Windows VHF work remains the fallback desktop path if phone HID peripheral support or macOS pairing/output behavior blocks the new route.

## Changes

- Added Android Bluetooth HID gamepad requirements `ANDR-09`, `ANDR-10`, and `ANDR-11`.
- Reframed `DESK-03` and `DESK-06` around macOS seeing the Android phone as a Bluetooth HID gamepad.
- Added `PACK-06` for Android HID setup, compatibility checks, pairing, descriptors, output reports, and fallback routing.
- Rerouted Roadmap Phase 7 from macOS virtual HID driver work to Android Bluetooth HID gamepad proof.
- Preserved completed Phase 6 Windows virtual joystick work as fallback.
- Recorded the reroute in `STATE.md`.

## Verification

- Requirements traceability includes all new requirements.
- Roadmap Phase 7 names the Android Bluetooth HID gamepad path and identifies old CoreHID/DriverKit work as retained evidence/fallback scaffolding.
- No source code or OS security-state changes made.
