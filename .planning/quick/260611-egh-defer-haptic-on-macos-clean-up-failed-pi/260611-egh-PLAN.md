---
quick_id: 260611-egh
status: complete
created: 2026-06-11
---

# Quick Task 260611-egh: defer haptic on macOS and clean failed PID experiment

## Objective

Defer macOS browser/GameController haptics for the Android Bluetooth HID path and remove the failed PID ForceFeedback experiment from active code.

## Tasks

1. Remove PID/Physical Interface Device descriptor constants, experimental descriptor bytes, handler code, and tests.
2. Preserve the stable 8-button/4-axis Bluetooth HID gamepad descriptor and generic output callback mapper.
3. Update Phase 7 evidence/state so macOS haptics are marked unsupported/deferred and Plan 07-06 can proceed.

## Verification

- Focused Android HID/unit checks.
- Static search that PID/ForceFeedback code is gone from production/test HID code except the descriptor exclusion assertion.
- Manifest/state docs updated with the defer decision.
