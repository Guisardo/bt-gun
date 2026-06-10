# Windows Virtual HID Strategy

Phase 6 uses a real Windows KMDF source driver with Microsoft VHF. vJoy, ViGEm, and the Phase 5 stub backend are not acceptable final pass paths for Windows.

## Architecture

```text
Android gun stream
  -> desktop companion session/security
  -> SemanticControllerState
  -> WindowsVirtualControllerBackend
  -> btgun-driver-bridge.exe
  -> IOCTL_BTGVJOY_SUBMIT_INPUT
  -> BtGunVJoy KMDF/VHF driver
  -> Windows HIDClass / joy.cpl / games
```

The driver is intentionally small. It owns VHF lifecycle, the `Root\BTGunVJoy` HID device, IOCTL validation, input report submission, and output-report queuing. It does not own LAN, pairing, replay protection, profile mapping, QR material, stream keys, or Android phone haptic transport.

## HID Shape

- Device: `BT Gun VJoy`
- Hardware id: `Root\BTGunVJoy`
- Top-level collection: regular gamepad-style virtual joystick
- Input report: report ID 1, 10 bytes
- Output report: report ID 2, 9 bytes

Report ID 1 layout:

| Byte(s) | Value |
|---------|-------|
| 0 | Report id `1` |
| 1 | Buttons: trigger, reload, X, Y, A, B |
| 2-3 | `stickX` signed int16 little-endian |
| 4-5 | `stickY` signed int16 little-endian |
| 6-7 | `aimX` signed int16 little-endian |
| 8-9 | `aimY` signed int16 little-endian |

Report ID 2 layout maps to v1 Android phone haptics:

| Byte(s) | Value |
|---------|-------|
| 0 | Report id `2` |
| 1 | Version `1` |
| 2 | Strength `0..255` |
| 3-4 | Duration milliseconds, uint16 little-endian |
| 5-6 | TTL milliseconds, uint16 little-endian |
| 7-8 | Reserved, must be zero |

The desktop companion converts valid output reports to `HapticCommand` and sends them through the authenticated control channel. Physical gun motor rumble remains deferred.

## Build and Package Path

GitHub Actions workflow `windows-driver` builds and packages the driver on a Windows runner:

1. Checkout repo with first-party `actions/checkout@v4`.
2. Install the WDK on the GitHub-hosted runner when needed, then locate MSBuild, Windows Kits, `inf2cat`, and `signtool`, preferring x64 tools when the kit provides them.
3. Build `windows/btgun-vjoy/driver/BtGunVJoy.vcxproj`.
4. Build `btgun-driver-bridge.exe` and `btgun-hid-output-sender.exe`.
5. Decode test certificate from `BTGUN_WINDOWS_TEST_CERT_PFX_BASE64`.
6. Sign with `BTGUN_WINDOWS_TEST_CERT_PASSWORD`.
7. Run `inf2cat` and `signtool`.
8. Upload artifact `btgun-vjoy-windows-x64-testsigned`.

Artifact contents must include:

- `BtGunVJoy.sys`
- `btgunvjoy.inf`
- `btgunvjoy.cat`
- `BtGunVJoyIoctl.h`
- `btgun-driver-bridge.exe`
- `btgun-hid-output-sender.exe`
- `Install-BtGunVJoy.ps1`
- `Rollback-BtGunVJoy.ps1`
- `build-metadata.json`

## Signing Rules

Phase 6 uses local test signing only. Production release signing, Partner Center submission, EV certificate flow, and installer distribution are outside Phase 6.

Private key material is never committed. The only configured secret names are `BTGUN_WINDOWS_TEST_CERT_PFX_BASE64` and `BTGUN_WINDOWS_TEST_CERT_PASSWORD`. Workflow logs must not print certificate bytes or passwords.

## Target Rules

Proof target `192.168.1.100` is install/proof only. Do not install WDK, Visual Studio, MSBuild, Git, or build tools there. Driver build/sign/package happens off target in GitHub Actions.

Every boot, test-signing, reboot, driver install, devnode creation, or removal operation requires explicit user approval before execution.

## Required Proof

Plan 06-06 must prove:

- `pnputil` shows the driver package/device.
- `Root\BTGunVJoy` is present.
- `joy.cpl` lists the virtual joystick.
- Live Android/gun input moves Windows-visible controls.
- A real HID output report reaches the driver and maps to Android phone vibration.

Try `joy.cpl` first for output. If it cannot send output for this descriptor, document that limitation and use `btgun-hid-output-sender.exe` as the fallback real HID output proof.
