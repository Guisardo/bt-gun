# Phase 6 Windows Proof Checklist

Target: `192.168.1.100` only. Use Administrator PowerShell with the CI artifact `btgun-vjoy-windows-x64-testsigned` from GitHub Actions run `27247835704`, artifact `7524720768`. Local artifact copy: `/private/tmp/btgun-phase6-ci-artifact-download`.

Do not install Git, Visual Studio, MSBuild, WDK, `signtool`, `inf2cat`, `devcon`, or build tools on the target.

## Approval Gate

USER APPROVAL REQUIRED before any command below runs on `192.168.1.100`.

| Command | Expected effect | Reversal path |
|---------|-----------------|---------------|
| `.\scripts\Install-BtGunVJoy.ps1 -ApproveTestSigning` | Runs `bcdedit /set testsigning on`; may require reboot. | `.\scripts\Rollback-BtGunVJoy.ps1 -ApproveTestSigningOff`; reboot only after separate approval if Windows requires it. |
| `Restart-Computer -Force` | Reboots target after approved test-signing change if Windows requires it. | No direct reversal; reconnect after boot, then disable test signing with approved rollback if proof ends. |
| `.\scripts\Install-BtGunVJoy.ps1 -PublicCertificatePath .\certs\btgun-test-signing.cer -ApproveCertificateImport` | Imports public test certificate into `LocalMachine\Root` and `LocalMachine\TrustedPublisher`. | Remove the imported certificate by thumbprint from both stores after separate approval. |
| `.\scripts\Install-BtGunVJoy.ps1 -ApproveDriverInstall` | Runs `pnputil /add-driver <artifact>\driver\btgunvjoy.inf /install`. | `.\scripts\Rollback-BtGunVJoy.ps1 -ApproveDriverDelete`. |
| `.\scripts\Install-BtGunVJoy.ps1 -ApproveDevnode` | Creates or verifies `Root\BTGunVJoy` with packaged `btgun-devnode.exe` if needed. | `.\scripts\Rollback-BtGunVJoy.ps1 -ApproveDeviceRemoval`. |
| `control joy.cpl` | Opens Windows Game Controllers GUI to prove `BT Gun VJoy` is visible. | Close the window; no persistent target change. |
| `.\tools\btgun-hid-output-sender.exe --strength 192 --duration-ms 120 --ttl-ms 500` | Sends a real HID output report only if `joy.cpl` cannot initiate output/rumble; expected Android phone haptic via driver callback. | Wait for duration/TTL to expire; stop process if it hangs. |
| `.\scripts\Rollback-BtGunVJoy.ps1 -ApproveDeviceRemoval` | Removes `ROOT\BTGUNVJOY\*` device instance. | Re-run approved install/devnode command. |
| `.\scripts\Rollback-BtGunVJoy.ps1 -ApproveDriverDelete` | Deletes published `btgunvjoy.inf` package with `pnputil /delete-driver /uninstall /force`. | Re-run approved driver install. |
| `.\scripts\Rollback-BtGunVJoy.ps1 -ApproveTestSigningOff` | Runs `bcdedit /set testsigning off`; may require reboot. | `.\scripts\Install-BtGunVJoy.ps1 -ApproveTestSigning`. |

Preflight/read-only commands for the approved session:

```powershell
bcdedit /enum
pnputil /enum-drivers | findstr /i btgun
pnputil /enum-devices /connected | findstr /i "BT Gun Root\BTGunVJoy"
Get-PnpDevice -PresentOnly | Where-Object { $_.FriendlyName -like "*BT Gun*" -or $_.InstanceId -like "*BTGUNVJOY*" }
```

## Required Proof Rows

### phase6-pnp-hid-cli

- Run the proof collector after approved install.
- Required evidence: `pnputil` driver listing, connected device listing, `Get-PnpDevice`, HID/game-controller class enumeration, and package metadata/hash refs.
- Must show `BT Gun VJoy` or `Root\BTGunVJoy`.

### phase6-joy-cpl-visible

- Run `control joy.cpl`.
- Agent must capture GUI evidence that Windows Game Controllers lists `BT Gun VJoy`.
- User must confirm the visual result.
- CLI/PnP evidence alone does not pass D-08.

### phase6-live-android-gun-input

- Start the desktop companion with the real Windows backend enabled and the CI artifact bridge path.
- Pair the Android app and physical iPega gun live.
- Move trigger, reload, X/Y/A/B, stick, and aim.
- Confirm Windows-visible axes/buttons move.
- Replay, fixture, fake state, smoke tests, or Phase 5 stub output cannot satisfy final D-09 proof.

### phase6-hid-output-report-phone-haptic

- Try `joy.cpl` output/rumble first.
- If `joy.cpl` cannot send output for this descriptor, document that limitation.
- Then run fallback:

```powershell
.\tools\btgun-hid-output-sender.exe --strength 192 --duration-ms 120 --ttl-ms 500
```

- Required evidence: real Windows HID output report drained by the bridge, mapped to `ControlServer.sendHapticCommand`, and user confirmation that the Android phone physically vibrated.
- Phase 5 simulated output report proof is not enough for D-16.

### phase6-redaction-scan

- Run `Redact-BtGunVJoyEvidence.ps1` before updating the committed manifest.
- Redact private keys, certificate passwords, QR/manual pairing secrets, proof values, stream keys, HMAC/session keys, Bluetooth addresses, device identifiers, screenshots, raw logs, and local account paths.
- Manifest rows must contain sanitized status, command category, artifact refs, and user-confirmation status only.

## Collector Commands

Run after approval and install proof setup:

```powershell
.\tools\proof\Collect-BtGunVJoyEvidence.ps1 -OutputDirectory .\evidence\phase6
.\tools\proof\Redact-BtGunVJoyEvidence.ps1 -InputPath .\evidence\phase6 -OutputPath .\evidence\phase6-redacted
```

The collector is read-only. It uses PowerShell, `bcdedit`, `pnputil`, `Get-PnpDevice`, `Get-CimInstance`, and file hashing. It does not require WDK, Visual Studio, MSBuild, Git, `signtool`, `inf2cat`, or `devcon`.
