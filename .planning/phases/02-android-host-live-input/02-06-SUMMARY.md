---
phase: 02-android-host-live-input
plan: 06
subsystem: android-host-dashboard-validation
tags: [android, kotlin, dashboard, haptics, calibration, manual-validation]
requires:
  - phase: 02-03
    provides: Foreground HostSessionService, BLE adapter, connection state, and status events.
  - phase: 02-05
    provides: Reload-hold recenter state and status events.
provides:
  - Integrated native Android dashboard for permission, session, gun input, motion, aim graph, recenter, foreground, error, and phone haptic state.
  - Local-only phone vibration test.
  - Manual Phase 2 validation checklist and sanitized evidence manifest.
  - Post-validation hardening for disabled Bluetooth/location capability probes.
affects: [02-android-host-live-input, android-host, dashboard-state, manual-validation]
tech-stack:
  added: []
  patterns:
    - Platform Android Views only; no new UI dependency.
    - Runtime capability probes are centralized and fail closed.
    - Manual raw evidence stays under ignored .evidence paths; committed manifest remains sanitized.
key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt
    - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
    - android-host/app/src/main/java/com/btgun/host/ui/AimGraphView.kt
    - android-host/app/src/main/java/com/btgun/host/permissions/HostCapabilityProbe.kt
    - android-host/scripts/collect-phase2-host-evidence.sh
    - .planning/phases/02-android-host-live-input/02-MANUAL-VALIDATION.md
    - docs/evidence/manifests/phase2-host-live-input.jsonl
  modified:
    - android-host/app/src/main/java/com/btgun/host/MainActivity.kt
    - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
    - android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt
    - android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt
    - android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java
key-decisions:
  - "Keep Phase 3 Desktop link and Phase 4 Packet stream visible as inactive placeholders only."
  - "Phone haptic test remains local Android vibration only; desktop haptic commands remain Phase 4 scope."
  - "Disabled Bluetooth or location must surface as blocked state instead of crashing Activity, service, or BLE scan startup."
patterns-established:
  - "DashboardState.from(...) adapts service, BLE, permission, motion, recenter, calibration, and haptic state into product/debug UI fields."
  - "HostCapabilityProbe evaluates Bluetooth, location, sensor, vibration, and network capability with guarded platform calls."
requirements-completed: [ANDR-01, ANDR-02, ANDR-03, ANDR-04, ANDR-05, ANDR-06, ANDR-08]
duration: hardware-interactive
completed: 2026-06-07
---

# Phase 02 Plan 06: Dashboard and Manual Validation Summary

**Phase 2 is approved. The Android host app can connect the gun, show live controls and motion aim, recenter/calibrate aim, display the axis graph, and report local phone haptics and system-service blockers honestly.**

## Accomplishments

- Wired the native Android dashboard to `HostSessionService.latestState` with permission state, primary start/stop action, connection state, foreground-service state, current error, last gun event, active controls, motion provider/capabilities, preview/calibrated aim, recenter state, calibration state, axis graph, and phone haptic status.
- Added debug panels for BLE provenance, permission state, and GATT status while keeping them collapsed by default.
- Kept Desktop link and Packet stream as inactive Phase 3/4 placeholders; no LAN transport, desktop pairing, or desktop-origin haptic command path was added.
- Added local phone haptic testing through Android `Vibrator`/`VibrationEffect`.
- Added manual validation checklist and sanitized Phase 2 evidence manifest.
- Added crash hardening so disabled Bluetooth/location capability checks and BLE scanner startup report blocked/unavailable state instead of throwing.

## Manual Approval

- User sign-off received: `phase 2 approved` on 2026-06-07.
- Manual rows marked pass in `02-MANUAL-VALIDATION.md`.
- Evidence manifest rows marked `approved_manual` with sanitized pass status.

## Verification

- `node tools/phase1/validate-fixtures.mjs --full` - PASS.
- `JAVA_HOME=/Users/lucas.rancez/Library/Java/JavaVirtualMachines/temurin-17.0.12/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug assembleDebug` - PASS.
- USB install and launch on `SM_A750G` - PASS.
- Disabled-location device validation: app launch and service start path produced no `FATAL EXCEPTION` / `AndroidRuntime` crash; location restored afterward.
- ADB-driven Bluetooth-off validation was unavailable on the Samsung device because `cmd bluetooth_manager disable` returned `No shell command implementation`; the Bluetooth-off path is covered by guarded code and PermissionGate tests.

## Known Deferred Scope

- Android-to-desktop LAN pairing remains Phase 3.
- UDP input stream and desktop-origin haptic command/ack handling remain Phase 4.
- Physical gun motor rumble remains deferred; v1 feedback uses Android phone vibration.

## Next Phase Readiness

Phase 3 can build LAN pairing and secure session setup on top of the approved Android host dashboard and live input state.
