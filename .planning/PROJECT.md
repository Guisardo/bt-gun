# Bluetooth Gun Driver

## What This Is

Bluetooth Gun Driver is a clean replacement host/driver stack for a discontinued iPega AR gun joystick. An Android app connects to the physical gun over Bluetooth, reads gun controls plus the Android device gyroscope, and forwards a normalized input stream over a wireless LAN link to desktop companion drivers for macOS on Apple Silicon and Windows 11 x64.

The desktop side exposes the stream as a regular gamepad-style gun controller so games and tools can see joystick axes, buttons, trigger, reload, and rumble without depending on the original discontinued Android apps. The first validation target is a simple joystick visualizer that proves end-to-end input, configurable aim mapping, recentering, and rumble round-trip behavior.

## Core Value

Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive gyro aiming and bidirectional rumble.

## Requirements

### Validated

(None yet - ship to validate)

### Active

- [ ] Android host app can connect to the physical iPega gun over Bluetooth.
- [ ] Android host app can read gun trigger, reload, joystick, X/Y/A/B buttons, and vibration capability.
- [ ] Android host app can read Android gyroscope data for aiming.
- [ ] Android host app can stream normalized gun input and gyro samples to a desktop companion over Wi-Fi/LAN.
- [ ] Desktop companion can pair with the Android host via QR or pairing code.
- [ ] Desktop companion can expose a regular gamepad-style gun controller on both Windows 11 x64 and macOS Apple Silicon.
- [ ] Desktop profiles can map gyro aim into joystick axes, with configurable aim mapping in v1.
- [ ] Holding reload for two seconds recenters gyro aim.
- [ ] Desktop-to-Android rumble messages can vibrate the physical gun.
- [ ] End-to-end input latency targets under 50 ms for the v1 visualizer path.
- [ ] A simple joystick visualizer can prove buttons, axes, aim mapping, recentering, and rumble.
- [ ] Reverse-engineering of the discontinued reference APK/XAPK apps and the iPega Bluetooth protocol is allowed when standard Android controller APIs are insufficient.

### Out of Scope

- Direct desktop-to-gun Bluetooth support - v1 uses Android as the gun host because the device was designed around Android pairing and phone gyro aiming.
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

The hardware has a trigger, reload button, physical joystick, classic X/Y/A/B buttons, and a vibration motor. It was intended to be mounted with or connected to an Android device so the Android device gyroscope supplies aiming data for AR gun apps.

The desired architecture is split into three pieces:

- Android gun server app: owns Bluetooth connection to the iPega gun, reads Android gyro, normalizes events, handles recenter gesture, and relays rumble commands back to the gun.
- Wireless transport: local Wi-Fi/LAN in v1, paired by QR or pairing code, designed so other wireless transports can be added later if needed.
- Desktop companion/driver: receives normalized stream, applies desktop-hosted profiles, exposes a virtual gamepad-style gun, and sends rumble commands back to Android.

Both desktop targets matter for v1: Windows 11 x64 and macOS on M3/Apple Silicon. The first acceptance harness is a simple joystick visualizer rather than a specific commercial game.

## Constraints

- **Desktop support**: Windows 11 x64 and macOS Apple Silicon are both v1 targets - the protocol and virtual-controller model must avoid platform-specific assumptions.
- **Transport**: Android-to-desktop v1 transport is Wi-Fi/LAN with QR or pairing code - this keeps pairing/debugging simpler while preserving wireless use.
- **Latency**: End-to-end target is under 50 ms for visualizer input path - design must support timestamping and latency measurement early.
- **HID shape**: Desktop exposes a regular gamepad-style gun controller - avoids custom HID compatibility risk in v1.
- **Aim mapping**: Aim mapping is configurable by desktop-side profiles - Android should send normalized gyro/raw aim data without baking in one desktop mapping.
- **Calibration**: Holding reload for two seconds recenters gyro aim - this gesture must not break normal reload behavior.
- **Reverse engineering**: Reference APK/XAPK and Bluetooth protocol reverse-engineering is allowed - useful if standard Android controller APIs do not reveal full input or rumble behavior.
- **Hardware availability**: Physical iPega gun is available for testing now - implementation should include real-device diagnostic tooling early.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Use Android as gun host | Original device was designed for Android Bluetooth pairing and Android gyro aiming. | - Pending |
| Support Windows and macOS in v1 | Both Windows 11 x64 and macOS M3 are required target desktops. | - Pending |
| Expose a gamepad-style gun HID shape | Regular joystick/gamepad compatibility is safer than a custom gun HID report. | - Pending |
| Use Wi-Fi/LAN transport for v1 | Simpler local pairing, debugging, and latency measurement than desktop Bluetooth/BLE. | - Pending |
| Store aim profiles on desktop | Desktop owns target platform mapping and virtual controller behavior. | - Pending |
| Pair by QR or pairing code | Reduces manual IP setup while keeping local-only networking. | - Pending |
| Validate first with a joystick visualizer | Proves the input/rumble pipeline before game-specific work. | - Pending |
| Require rumble round-trip in v1 | Vibration motor is part of the hardware experience and validates bidirectional transport. | - Pending |

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
*Last updated: 2026-06-06 after initialization*
