# Bluetooth Gun Driver

## What This Is

Bluetooth Gun Driver is a clean replacement host/driver stack for a discontinued iPega AR gun joystick. An Android app connects to the physical gun over Bluetooth, reads gun controls plus Android device motion sensors, and forwards a normalized input stream over a wireless LAN link to desktop companion drivers for macOS on Apple Silicon and Windows 11 x64.

The desktop side exposes the stream as a regular gamepad-style gun controller so games and tools can see joystick axes, buttons, trigger, reload, and v1 haptic feedback without depending on the original discontinued Android apps. The first validation target is a simple joystick visualizer that proves end-to-end input, configurable aim mapping, recentering, and phone-vibration feedback.

## Core Value

Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive motion aiming and v1 phone haptic feedback. Physical gun motor rumble is deferred.

## Requirements

### Validated

- [x] Phase 1 verified the physical iPega BLE/GATT input path and normalized fixtures for trigger, reload, joystick, X/Y/A/B, and phone haptics.
- [x] Phase 2 Android host app can connect to the physical iPega gun over Bluetooth.
- [x] Phase 2 Android host app can read gun trigger, reload, joystick, and X/Y/A/B buttons.
- [x] Phase 2 Android host app can read Android motion sensor data for aiming, using gyro, accelerometer/gravity, and rotation-vector providers as available.
- [x] Phase 2 Android host app shows live session status, aim graph/calibration state, foreground-service state, current errors, and local phone haptic status.
- [x] Phase 2 holding reload for two seconds recenters motion aim without suppressing normal reload behavior.
- [x] Phase 3 desktop companion can pair with the Android host via QR or pairing code over a proof-gated secure local session.
- [x] Phase 4 Android host app can stream normalized gun input and motion samples to a desktop companion over Wi-Fi/LAN.
- [x] Phase 4 desktop-to-Android haptic messages can vibrate the Android phone for v1 feedback.
- [x] Phase 6 Windows VHF path exposes the BT Gun stream as a regular Windows gamepad-style joystick.
- [x] Phase 7 Android Bluetooth HID path lets macOS Apple Silicon pair to the Android phone as a normal gamepad-style controller without paid Apple virtual HID entitlements.
- [x] Phase 7 documents macOS Bluetooth HID output/haptics as unsupported/deferred for the stable Android HID path while preserving LAN and Windows VHF phone-haptic fallback routes.
- [x] Phase 8 Android stores local profiles that map motion aim into joystick axes, with desktop read-only metadata in v1.
- [x] Phase 9 visualizer proves buttons, axes, aim mapping, recentering, packet loss, and phone haptics before game-specific support.
- [x] Phase 9 visualizer path records end-to-end input latency under the 50 ms target during UAT.

### Active

- [ ] Reverse-engineering of the discontinued reference APK/XAPK apps and the iPega Bluetooth protocol is allowed when standard Android controller APIs are insufficient.

### Out of Scope

- Direct desktop-to-gun Bluetooth support - v1 uses Android as the gun host because the device was designed around Android pairing and phone motion aiming.
- First-class game-specific integrations - v1 validates against a simple joystick visualizer before targeting real games.
- Wired Android-to-desktop transport - the product goal is wireless; Wi-Fi/LAN is the v1 transport.
- Custom desktop HID gun report - v1 should expose a regular joystick/gamepad-compatible device shape for broader compatibility.
- Cloud relay or internet pairing - v1 targets local LAN pairing for latency and simplicity.

## Context

The physical device is a discontinued iPega AR gun controller documented at `https://ipega.hk/gamehandle/53-100.html`. The original device can only be linked over Bluetooth to the discontinued Android apps archived under `docs/refs/`:

- `ARGun2021.apk`
- `AR Cher_20200905_Apkpure.xapk`
- `ARGunPro_1.0.19_apkcombo.com.xapk`
- `ARGun Library_1.0.1_apkcombo.com.apk`
- `WorldsAR_14.0_apkcombo.com.xapk`

The hardware has a trigger, reload button, physical joystick, classic X/Y/A/B buttons, and a vibration motor. It was intended to be mounted with or connected to an Android device so Android device motion sensors supply aiming data for AR gun apps.

The desired architecture is split into three pieces:

- Android gun server app: owns Bluetooth connection to the iPega gun, reads Android motion sensors, normalizes events, handles recenter gesture, and plays v1 phone haptic feedback from desktop haptic commands.
- Wireless transport: local Wi-Fi/LAN in v1, paired by QR or pairing code, designed so other wireless transports can be added later if needed.
- Desktop companion/driver: receives normalized stream, applies desktop-hosted profiles, exposes a virtual gamepad-style gun, and sends haptic commands back to Android.

Both desktop targets matter for v1: Windows 11 x64 and macOS on M3/Apple Silicon. The first acceptance harness is a simple joystick visualizer rather than a specific commercial game.

## Constraints

- **Desktop support**: Windows 11 x64 and macOS Apple Silicon are both v1 targets - the protocol and virtual-controller model must avoid platform-specific assumptions.
- **Transport**: Android-to-desktop v1 transport is Wi-Fi/LAN with QR or pairing code - this keeps pairing/debugging simpler while preserving wireless use.
- **Latency**: End-to-end target is under 50 ms for visualizer input path - design must support timestamping and latency measurement early.
- **HID shape**: Desktop exposes a regular gamepad-style gun controller - avoids custom HID compatibility risk in v1.
- **Aim mapping**: Android profile editor configures sensitivity, inversion, dead zone, smoothing, provider-specific tuning, and limited button remap; desktop only displays active Android profile metadata and mapped-stream diagnostics.
- **Sensor fallback**: Prefer Android fused rotation-vector or game-rotation-vector data when available; use gyro plus accelerometer/gravity when useful for stability; support an explicit accelerometer/gravity tilt fallback when no gyroscope is available.
- **Calibration**: Holding reload for two seconds recenters motion aim - this gesture must not break normal reload behavior.
- **Reverse engineering**: Reference APK/XAPK and Bluetooth protocol reverse-engineering is allowed - useful if standard Android controller APIs do not reveal full input behavior. Physical gun motor rumble is no longer a v1 blocker.
- **Hardware availability**: Physical iPega gun is available for testing now - implementation should include real-device diagnostic tooling early.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Use Android as gun host | Original device was designed for Android Bluetooth pairing and phone-based motion aiming. | - Validated in Phase 2 |
| Support Windows and macOS in v1 | Both Windows 11 x64 and macOS M3 are required target desktops. | - Windows validated in Phase 6; macOS no-subscription path validated through Android Bluetooth HID in Phase 7 |
| Expose a gamepad-style gun HID shape | Regular joystick/gamepad compatibility is safer than a custom gun HID report. | - Validated in Phase 6 Windows VHF and Phase 7 Android Bluetooth HID |
| Use Wi-Fi/LAN transport for v1 | Simpler local pairing, debugging, and latency measurement than desktop Bluetooth/BLE. | - Validated in Phase 4 |
| Store aim profiles on Android | Phase 8 context supersedes the old desktop-local mapping decision after the Android Bluetooth HID reroute; Android owns profile storage, editing, validation, and application. | - Validated in Phase 8 |
| Support accelerometer-aware motion aim | Fused rotation sensors can improve stability when gyro and accelerometer data are available, and accelerometer/gravity tilt can keep a limited aiming mode working on devices without a gyroscope. | - Validated in Phase 2 |
| Pair by QR or pairing code | Reduces manual IP setup while keeping local-only networking. | - Validated in Phase 3 |
| Validate first with a joystick visualizer | Proves the input and phone-haptic pipeline before game-specific work. | - Validated in Phase 9 |
| Defer physical gun motor rumble | BLE `fff5` is only a read/write candidate; no verified physical gun motor command path exists. Android phone vibration is confirmed and good enough for v1 feedback. | - Validated in Phase 1; still deferred after Phase 4 |
| Use Android Bluetooth HID as the no-subscription macOS path | CoreHID/DriverKit virtual HID requires restricted entitlement/signing or local security relaxation; Android HID pairs as a normal gamepad. | - Validated in Phase 7; macOS HID haptics unsupported/deferred |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `$gsd-transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `$gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check - still the right priority?
3. Audit Out of Scope - reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-15 after Phase 9 visualizer acceptance verification*
