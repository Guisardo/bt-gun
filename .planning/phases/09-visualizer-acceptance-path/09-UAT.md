---
status: testing
phase: 09-visualizer-acceptance-path
source: [09-VERIFICATION.md]
started: 2026-06-13T04:44:25Z
updated: 2026-06-13T04:44:25Z
---

## Current Test

number: 1
name: LAN visualizer stream and live controls
expected: |
  Pair Android to desktop; BT Gun Visualizer opens or can be opened; lan_visualizer_stream and live_controls rows become observed while trigger, reload, joystick, X/Y/A/B, and mapped aim move in real time.
awaiting: user response

## Tests

### 1. LAN visualizer stream and live controls
expected: Pair Android to desktop; BT Gun Visualizer opens or can be opened; lan_visualizer_stream and live_controls rows become observed while trigger, reload, joystick, X/Y/A/B, and mapped aim move in real time.
result: [pending]

### 2. Recenter and aim-zero
expected: Hold reload for 2000 ms; visualizer shows recenter/aim-zero update, recenter_aim_zero becomes observed, then Confirm observed records user proof.
result: [pending]

### 3. LAN phone haptic
expected: Press Run phone haptic test; Android phone vibrates, haptic ack/fail appears, lan_phone_haptic becomes observed, then Confirm observed records vibration proof.
result: [pending]

### 4. macOS Android Bluetooth HID input
expected: macOS sees Android as OS-visible Bluetooth HID gamepad and receives live gun controls/aim; macos_hid_input is confirmed by user.
result: [pending]

### 5. Windows VHF input and output-to-phone haptic
expected: On approved Windows target, joy.cpl/controller view moves from live gun stream and VHF output haptic routes to Android phone vibration; windows_vhf_input and windows_vhf_haptic are confirmed by user.
result: [pending]

### 6. Latency target and packet loss visibility
expected: During normal local Wi-Fi live input, latency_target becomes observed with headline latency under 50 ms and packet_loss shows current-session expected/missed/percent.
result: [pending]

### 7. macOS HID haptic limitation
expected: Visualizer shows macOS HID haptic unsupported/deferred limitation; user uses Confirm limitation for macos_hid_haptic_limit.
result: [pending]

## Summary

total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps
