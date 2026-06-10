---
quick_id: 260610-m2r
slug: update-requirements-and-roadmap-to-use-a
status: planned
created: 2026-06-10T18:53:43.550Z
---

# Quick Task: Android Bluetooth HID Gamepad Reroute

## Objective

Update requirements and roadmap so the primary no-subscription macOS path is the Android phone acting as a Bluetooth HID gamepad. Preserve completed Windows virtual joystick work as the fallback desktop path when macOS Bluetooth HID gamepad integration is blocked.

## Scope

- Update `.planning/REQUIREMENTS.md`.
- Update `.planning/ROADMAP.md`.
- Update `.planning/STATE.md` with the quick task and decision context.

## Acceptance

- Requirements name Android Bluetooth HID gamepad behavior and trace it to Phase 7.
- Roadmap Phase 7 is rerouted from macOS virtual HID driver work to Android Bluetooth HID gamepad proof.
- Windows VHF work remains complete and explicitly retained as fallback.
- No source code or OS security-state changes.
