---
phase: 06-windows-virtual-joystick-path
plan: 06
status: in_progress
subsystem: windows-target-proof
tags: [windows, vhf, kmdf, joystick, evidence, approval-gate]
requires:
  - phase: 06-windows-virtual-joystick-path
    provides: CI-built test-signed Windows driver package from Plan 06-05
provides:
  - Phase 6 Windows proof checklist
  - Pending sanitized evidence manifest scaffold
  - Read-only CLI evidence collector
  - Evidence redaction helper
  - Passing CLI/PnP/HID proof for target host 192.168.1.100
  - Passing joy.cpl visual proof for target host 192.168.1.100
  - Remaining manual proof gates for live input and phone haptic
affects: [phase-06, windows-target-proof, DESK-02, DESK-05, PACK-02]
tech-stack:
  added: []
  patterns: [approval-gated target proof, sanitized evidence manifest, read-only Windows evidence collection]
key-files:
  created:
    - docs/windows/phase6-proof-checklist.md
    - docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl
    - windows/btgun-vjoy/tools/proof/Collect-BtGunVJoyEvidence.ps1
    - windows/btgun-vjoy/tools/proof/Redact-BtGunVJoyEvidence.ps1
  modified:
    - .planning/phases/06-windows-virtual-joystick-path/06-06-SUMMARY.md
key-decisions:
  - "Stop before any Windows target boot, signing, reboot, install, devnode, rollback, GUI proof, or haptic proof command."
  - "Use CI artifact run 27252079810 / artifact 7526172777 for proof tooling; do not install build tools on 192.168.1.100."
  - "VHF source-driver package must install Vhf as a LowerFilters entry and use a higher DriverVer than older staged packages so PnP selects the fixed INF."
requirements-completed: []
duration: hardware-interactive checkpoint
completed: null
---

# Phase 06 Plan 06: Windows Target Proof Checkpoint

**CLI/PnP/HID and joy.cpl target proof are passing; final Phase 6 proof is blocked on live input and haptic confirmation.**

## Progress

- **Tasks complete:** 2/3 plus Task 3 CLI and joy.cpl sub-proof
- **Current task:** Task 3, final live joystick and haptic proof
- **Status:** blocked on live Android/gun input and phone haptic proof gates
- **Target:** `192.168.1.100`
- **Final proof tooling artifact:** GitHub Actions run `27252079810`, artifact `7526172777`, local download `/private/tmp/btgun-phase6-ci-artifact-download-proof.XjHtDR`
- **Installed driver package:** `oem44.inf`, `DriverVer=06/10/2026,0.6.2.1`
- **Target evidence path:** `D:\Users\Lucas\btgun-phase6-target-proof.XjHtDR\evidence\phase6-pnp-hid-cli`
- **Target commands executed:** approved test-signing enable, reboot, cert import, driver install, devnode verify/scan, read-only CLI/PnP/HID checks, ASCII bridge probe, packaged proof collector.

## Task Commits

1. **Task 1: Proof checklist, collectors, and sanitized manifest scaffold** - `5acd445` (feat)
2. **Target install fix: attach VHF lower filter** - `200827a` (fix)
3. **Target install fix: bump VHF package version** - `f55d8a4` (fix)
4. **Artifact packaging fix: include proof scripts** - `2a0e882` (fix)

## Verification Completed

- PASS: `rg -n "phase6-pnp-hid-cli|phase6-joy-cpl-visible|phase6-live-android-gun-input|phase6-hid-output-report-phone-haptic|phase6-redaction-scan|joy.cpl|btgun-hid-output-sender|USER APPROVAL REQUIRED" docs/windows/phase6-proof-checklist.md docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl windows/btgun-vjoy/tools/proof`
- PASS: JSONL manifest parsed successfully with Node.
- PASS: GitHub Actions `windows-driver.yml` run `27252079810` completed successfully for clean branch commit `3224399`; artifact `7526172777` contains driver, helper tools, install scripts, and proof scripts.
- PASS: Target driver package `oem44.inf` is selected and started; `LowerFilters` is `{Vhf}` and PnP problem is `CM_PROB_NONE`.
- PASS: ASCII bridge probe returned `STATUS` with `driverStarted=true`, `vhfStarted=true`, and accepted `SUBMIT_INPUT`.
- PASS: Packaged proof collector wrote read-only evidence under `D:\Users\Lucas\btgun-phase6-target-proof.XjHtDR\evidence\phase6-pnp-hid-cli`.
- PASS: User-provided visual confirmation shows `control joy.cpl` listing the VHF virtual HID game controller as `Aceptar`, with properties axes and buttons visible.
- NOT RUN: PowerShell script parse/execution. `pwsh`/`powershell` is not installed on this macOS executor.

## Approval Command List

These commands require explicit approval before execution on `192.168.1.100`.

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

Read-only preflight/proof commands for the approved session:

```powershell
bcdedit /enum
pnputil /enum-drivers | findstr /i btgun
pnputil /enum-devices /connected | findstr /i "BT Gun Root\BTGunVJoy"
Get-PnpDevice -PresentOnly | Where-Object { $_.FriendlyName -like "*BT Gun*" -or $_.InstanceId -like "*BTGUNVJOY*" }
.\tools\proof\Collect-BtGunVJoyEvidence.ps1 -OutputDirectory .\evidence\phase6
.\tools\proof\Redact-BtGunVJoyEvidence.ps1 -InputPath .\evidence\phase6 -OutputPath .\evidence\phase6-redacted
```

## Guardrails

- Do not use `gh`.
- Do not install WDK, Visual Studio, MSBuild, Git, `signtool`, `inf2cat`, `devcon`, or build tools on `192.168.1.100`.
- Do not run `bcdedit`, reboot, certificate import, driver install, devnode creation, rollback/removal, `control joy.cpl`, or haptic output proof before approval.
- Final input proof must use a live Android/gun stream. Replay/fake input is not accepted.
- Final haptic proof must use a real Windows HID output report and user phone-vibration confirmation.

## Awaiting

Manual proof gates only:

- Live Android/gun input proof through the real Windows backend.
- Real HID output-to-phone-haptic proof, using `joy.cpl` first and fallback sender only if the `joy.cpl` limitation is documented.

## Self-Check: CHECKPOINT

- Found Task 1 commit `5acd445`.
- Found target fix commits `200827a`, `f55d8a4`, and `2a0e882`.
- Found created proof checklist, manifest scaffold, collector, and redactor files.
- Confirmed GitHub Actions run `27252079810` succeeded and artifact `7526172777` includes `tools/proof/*`.
- Confirmed target PnP/HID CLI proof passes with `oem44.inf`, `LowerFilters={Vhf}`, `CM_PROB_NONE`, and bridge `STATUS`/`SUBMIT_INPUT` success.
- Confirmed joy.cpl visual proof passes from user-provided target view.
