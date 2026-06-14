# Android Bluetooth HID Gamepad Setup

Phase 7 primary macOS strategy is Android Bluetooth HID. The Android phone acts as a Bluetooth gamepad so macOS can see a normal gamepad-style controller without CoreHID or DriverKit virtual HID entitlement work. CoreHID and DriverKit evidence remains retained only as blocked/fallback evidence, including `corehid-runtime-blocked`.

This doc covers PACK-03 and PACK-06: phone compatibility, Android permission state, macOS pairing, descriptor/report shape, output-report behavior, evidence redaction, and Windows VHF fallback limits.

## Compatibility Gate

Use Android HID mode only when the phone can provide the platform `BluetoothProfile.HID_DEVICE` role.

Required states before proof:

| Gate | Pass state | Blocked state |
|------|------------|---------------|
| Bluetooth adapter | Enabled | Bluetooth off or unavailable |
| Runtime permission | `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE` granted on Android 12+ | Nearby Devices/Bluetooth permission missing |
| HID profile proxy | `HID_DEVICE` proxy available | Phone/OEM does not expose HID Device profile |
| App registration | `BluetoothHidDevice.registerApp` accepted and callback reports registered | Registration rejected or unregistered |
| Pairing mode | User starts pairing window from the app | No discoverable/connectable window active |
| Host connection | macOS connects as HID host | No host connected or host disconnected |

The normal user flow starts with **Start Bluetooth gamepad**. This action is separate from the BLE gun/LAN session path; it starts the HID role probe and registers the gamepad SDP record. After registration is active, tap **Open pairing window** to start the discoverable pairing countdown. **Stop Bluetooth gamepad** sends a neutral report and disconnects the current host, but keeps the HID app registered so an already-paired Mac can reconnect without forgetting the device. Full unregister happens only when the Android service closes. The app should show remaining pairing time and an explicit blocked reason instead of silently falling back.

If the current phone blocks HID proxy, registration, macOS pairing, or input proof, test an alternate Android phone before selecting the Windows VHF fallback.

## macOS Pairing Proof

1. Start the Android host app and connect the iPega gun if live controls are being tested.
2. Tap **Start Bluetooth gamepad**.
3. Wait for the HID proxy/registration row to show active or registered.
4. Tap **Open pairing window**.
5. Confirm the pairing-mode countdown is visible.
6. On macOS, open Bluetooth settings and pair the Android HID gamepad.
7. Verify Android reports host connection `CONNECTED`.
8. Open a Game Controller surface, browser gamepad tester, or the Phase 7 probe path.
9. Press trigger, reload, X/Y/A/B, move the physical stick, and move the phone for aim.
10. Record only sanitized evidence rows in `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`.

DESK-03 proof requires macOS Bluetooth HID input. Desktop companion LAN input does not count as DESK-03 proof.

## HID Report Contract

Android owns descriptor bytes and input packing. The shape remains a normal gamepad-style joystick, not a custom gun report.

| Item | Value |
|------|-------|
| Device kind | `gamepad_like_joystick` |
| Input report ID | `1` |
| Input payload length | 9 bytes |
| Output report ID | `2` |
| Output payload length | 8 bytes |
| Axis encoding | signed int16 little-endian |
| Axis range | `-32768..32767` |
| Input axes | X, Y, Rx, Ry |
| Browser mapping intent | left stick on axes 0/1, aim/right stick on axes 2/3 |
| Windows Game Controllers labels | stick on X/Y, aim on Rotation X/Rotation Y |

Input payload layout:

| Offset | Size | Meaning |
|--------|------|---------|
| 0 | 1 | Button bitfield |
| 1 | 2 | `stickX`, signed int16 little-endian |
| 3 | 2 | `stickY`, signed int16 little-endian, inverted from Android state |
| 5 | 2 | `aimX`, signed int16 little-endian |
| 7 | 2 | `aimY`, signed int16 little-endian, inverted from Android state |

Button bit order:

| Bit | Control |
|-----|---------|
| 0 | Trigger |
| 1 | Reload |
| 2 | X |
| 3 | Y |
| 4 | A |
| 5 | B |
| 6 | Padding |
| 7 | Padding |

Aim uses normalized/calibrated `aimX` and `aimY` when present. Raw aim is fallback only when normalized aim is unavailable. Stale input clears buttons and stick axes; aim stays center/default through the packer input state.

## Output Behavior

Android accepts host output through `BluetoothHidDevice.Callback` methods such as `onSetReport` and validates the bytes before vibrating the phone.

Output payload layout for report ID `2`:

| Offset | Size | Meaning |
|--------|------|---------|
| 0 | 1 | Version, currently `1` |
| 1 | 1 | Strength byte, `0..255` |
| 2 | 2 | Duration milliseconds, unsigned little-endian, `1..1000` |
| 4 | 2 | TTL milliseconds, unsigned little-endian, `1..2000` |
| 6 | 1 | Flags, must be `0` |
| 7 | 1 | Reserved, must be `0` |

Invalid report ID, length, version, duration, TTL, flags, or reserved bytes must not vibrate the phone. The app reports a validation error and keeps the status visible.

Current Phase 7 evidence records macOS browser/Game Controller output as unsupported/deferred for this Android HID path. `phase7-macos-output-unsupported` means no usable macOS host-origin output callback or haptic object was observed for the stable descriptor. LAN haptics and Windows VHF output-to-phone haptics remain valid fallback paths, but they do not satisfy DESK-06 HID-output proof for macOS.

## Evidence Rows

Expected manifest rows:

| Row | Purpose |
|-----|---------|
| `phase7-android-hid-proxy` | Current phone exposes HID proxy |
| `phase7-android-hid-register-app` | Android HID app registration passes |
| `phase7-android-hid-pairing-window` | Pairing-mode window opens |
| `phase7-macos-bluetooth-paired` | macOS pairs to Android over Bluetooth HID |
| `phase7-gamecontroller-input` | macOS-visible gamepad receives live controls/aim |
| `phase7-hid-output-callback` | Host-origin HID output reaches Android, if supported |
| `phase7-macos-output-unsupported` | macOS output was probed and not supported |
| `phase7-alternate-phone-tested` | Alternate phone tested after current phone blocks proof |
| `phase7-windows-vhf-fallback-selected` | Windows fallback selected only after current and alternate Android phone attempts fail |

Current phone DESK-03 pass means the alternate-phone row is not required. Do not add `phase7-windows-vhf-fallback-selected` unless current and alternate phones both fail Android HID/macOS proof.

## Redaction Rules

Committed setup docs and evidence rows must be sanitized.

Do not commit:

- Bluetooth MAC addresses.
- Phone serials, Android IDs, stable hardware identifiers, or account names.
- Personal device names or nearby-device names that identify people or hardware.
- Pairing credentials, manual pairing codes, transport secrets, cryptographic signing material, transcript secrets, or raw Bluetooth dumps.
- Screenshots or screenshot paths that may contain sensitive device names.

Use generic labels such as `current-phone-android-hid`, `macos-bluetooth-hid-current-phone`, and local capture IDs.

## Fallback Boundary

Android Bluetooth HID is the primary no-subscription macOS strategy for Phase 7. CoreHID and DriverKit remain blocked/fallback evidence only; do not revive them as the primary path without later entitlement-capable proof and explicit planning.

Windows VHF fallback is the completed Phase 6 OS-visible controller path. Select it only if Android Bluetooth HID cannot be proven after the current phone and an alternate Android phone are tested. Do not discard Windows work, and do not count desktop companion LAN input or LAN haptics as macOS DESK-03/DESK-06 Bluetooth HID proof.
