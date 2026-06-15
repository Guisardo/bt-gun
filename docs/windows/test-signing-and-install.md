# Windows Test Signing and Install

Use only the CI artifact `btgun-vjoy-windows-x64-testsigned`. Do not build on `192.168.1.100`.

Chrome Gamepad API haptics require package version `0.6.3.0` or newer. The updated VHF device uses `VID_18D1&PID_9400` and exposes Stadia-compatible haptic output report `0x05`; the native BT Gun haptic proof path remains report ID `0x02`.

## Inputs

- Artifact from GitHub Actions workflow `windows-driver`.
- Public test certificate, if target trust import is needed.
- User approval before each boot/signing/reboot/install/removal operation.

Required GitHub secrets:

- `BTGUN_WINDOWS_TEST_CERT_PFX_BASE64`
- `BTGUN_WINDOWS_TEST_CERT_PASSWORD`

Do not record private keys, certificate passwords, QR secrets, proof values, stream keys, device ids, or screenshots in committed docs or artifacts.

## Install Gate

Open Administrator PowerShell in the extracted artifact. Check current state first:

```powershell
bcdedit /enum
pnputil /enum-drivers | findstr /i btgun
pnputil /enum-devices /connected | findstr /i "BT Gun Root\BTGunVJoy"
```

Before enabling test signing:

```powershell
# USER APPROVAL REQUIRED
.\scripts\Install-BtGunVJoy.ps1 -ApproveTestSigning
```

If Windows requires reboot, stop and ask for approval. The script does not auto-reboot.

If a public test certificate is provided:

```powershell
# USER APPROVAL REQUIRED
.\scripts\Install-BtGunVJoy.ps1 -PublicCertificatePath .\certs\btgun-test-signing.cer -ApproveCertificateImport
```

Install the driver package:

```powershell
# USER APPROVAL REQUIRED
.\scripts\Install-BtGunVJoy.ps1 -ApproveDriverInstall
```

If the root devnode is not created by install, use the packaged `btgun-devnode.exe` through the approval-gated install script:

```powershell
# USER APPROVAL REQUIRED
.\scripts\Install-BtGunVJoy.ps1 -ApproveDevnode
```

No step installs WDK, Visual Studio, MSBuild, Git, or build tools on the target.

## Proof Sequence

Run these after install:

```powershell
pnputil /enum-drivers | findstr /i btgun
pnputil /enum-devices /connected | findstr /i "BT Gun Root\BTGunVJoy"
control joy.cpl
```

`joy.cpl` must show the regular gamepad-style virtual joystick before Phase 6 Windows visibility passes. Stub, vJoy, ViGEm, replay-only, or fake-input-only proof is not enough.

For input proof, run the desktop companion with the Windows VHF backend and a live paired Android/gun stream. Move trigger, reload, X/Y/A/B, stick, and aim. Capture sanitized evidence only.

For real HID output proof, try `joy.cpl` first. If it cannot send output for this descriptor, document that and run:

```powershell
.\tools\btgun-hid-output-sender.exe --strength 192 --duration-ms 120 --ttl-ms 500
```

Then verify the desktop bridge drains report ID 2 and Android phone vibration occurs through the authenticated haptic path.

Chrome standard Gamepad API vibration pages should expose vibration after the updated package is installed, the old `VID_1209&PID_B706` device is removed, and Chrome reconnects to `VID_18D1&PID_9400`. If Chrome still reports `No Vibration`, capture the installed identity and Chrome state, then use the native HID output sender or LAN visualizer haptic test as fallback proof.

## Rollback

Rollback also needs approval:

```powershell
# USER APPROVAL REQUIRED
.\scripts\Rollback-BtGunVJoy.ps1 -ApproveDeviceRemoval

# USER APPROVAL REQUIRED
.\scripts\Rollback-BtGunVJoy.ps1 -ApproveDriverDelete

# USER APPROVAL REQUIRED
.\scripts\Rollback-BtGunVJoy.ps1 -ApproveTestSigningOff
```

If disabling test signing requires reboot, stop and ask for approval. The rollback script does not auto-reboot.

Rollback proof:

```powershell
pnputil /enum-devices /instanceid "ROOT\BTGUNVJOY\*"
pnputil /enum-drivers | findstr /i btgun
bcdedit /enum
```
