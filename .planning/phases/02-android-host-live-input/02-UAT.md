---
status: complete
phase: 02-android-host-live-input
source: [02-01-SUMMARY.md, 02-02-SUMMARY.md, 02-03-SUMMARY.md, 02-04-SUMMARY.md, 02-05-SUMMARY.md, 02-06-SUMMARY.md, 02-MANUAL-VALIDATION.md]
started: 2026-06-11T16:56:56.993Z
updated: 2026-06-11T17:31:44.196Z
---

## Current Test

[testing complete]

## Tests

### 1. User Flow - Open Host And Grant Permissions
expected: Install and open `com.btgun.host`. The app should show `BT Gun Host`, a permission gate when needed, and a `Grant permissions` action. After approving Bluetooth/Nearby Devices/location prompts, the gate should clear or show only honest unavailable/blocked hardware capability, with no silent scan/connect failure.
result: pass

### 2. User Flow - Start Live Gun Session
expected: With the iPega gun powered nearby, tap `Start live session`. The foreground notification should say `BT Gun Host running - live input active`, `Gun connection` should reach `connected`, and debug GATT status should mention `fff3_notifications_enabled`.
result: pass

### 3. User Flow - Live Gun Controls
expected: Press trigger, reload, stick left/right/up/down, X, Y, A, and B. `Last gun event` should update in place with product labels for trigger/reload/stick/button down/up states, while raw BLE bytes, clue ids, capture ids, and confidence stay hidden unless Debug provenance is opened.
result: pass

### 4. User Flow - Motion Aim Preview
expected: Move the phone/gun through yaw left/right and pitch up/down. `Motion provider` should show the selected provider or `tilt fallback`, capability flags should be visible, and the preview aim dot/numeric X/Y values should move with elapsed timestamp metadata.
result: pass

### 5. User Flow - Reload Hold Recenter
expected: Press reload briefly, then hold reload for two seconds, then release. The brief press should show only `reload down` and `reload up`; the hold should show countdown/recenter state, emit `recenter emitted` with baseline elapsed timestamp, and still show `reload up` on release.
result: pass

### 6. User Flow - Foreground Session Continuity
expected: Start a live session, switch apps or lock/unlock the screen, then return to the host app. The foreground notification should remain visible and the dashboard should show service running plus connected/reconnecting/error state honestly.
result: pass

### 7. User Flow - Local Phone Haptic
expected: Tap `Test phone vibration`. The phone should vibrate locally for about 1000 ms, and `Phone haptic` should show `started | local phone vibration 1000ms` or an honest unavailable/error state. No desktop-origin haptic command, ack/fail, TTL, or control-channel UI should appear.
result: pass

### 8. User Flow - Deferred Desktop Boundaries
expected: The dashboard should show Desktop link and Packet stream as inactive placeholders with Phase 3/4 pending copy. It should not imply that LAN pairing, UDP packets, desktop haptics, desktop profiles, or HID output are working in Phase 2.
result: pass

### 9. Technical Check - Phase 1 Fixture Gate
expected: Run `node tools/phase1/validate-fixtures.mjs --full`. The command should pass, proving the Android host parser still has valid Phase 1 fixture/provenance evidence.
result: pass

### 10. Technical Check - Android Host Build Gate
expected: Run `JAVA_HOME=/Users/lucas.rancez/Library/Java/JavaVirtualMachines/temurin-17.0.12/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug assembleDebug`. The command should pass.
result: pass

### 11. Coverage Check - Phase 2 Goal
expected: Starting from the Phase 2 goal, you should be able to trace ANDR-01, ANDR-02, ANDR-03, ANDR-04, ANDR-05, ANDR-06, and ANDR-08 to Android host code, manual validation rows, sanitized evidence manifest rows, and passing build/test evidence.
result: pass

## Summary

total: 11
passed: 11
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
