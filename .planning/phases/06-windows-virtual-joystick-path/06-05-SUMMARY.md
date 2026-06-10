---
phase: 06-windows-virtual-joystick-path
plan: 05
status: blocked
subsystem: windows-driver-ci-packaging
tags: [windows, kmdf, vhf, github-actions, test-signing, packaging]
requires:
  - phase: 06-windows-virtual-joystick-path
    provides: KMDF/VHF driver source, INF, IOCTL ABI, and helper tools from Plan 06-02
provides:
  - Windows driver CI workflow source
  - Driver package collection script
  - Approval-gated Windows install and rollback scripts
  - Windows VHF strategy and test-signing setup docs
affects: [phase-06, windows-driver-ci, windows-target-proof, plan-06-06]
tech-stack:
  added: []
  patterns: [GitHub Actions Windows driver packaging, transient test certificate signing, approval-gated target scripts]
key-files:
  created:
    - .github/workflows/windows-driver.yml
    - windows/btgun-vjoy/package/Package-BtGunVJoy.ps1
    - windows/btgun-vjoy/package/Install-BtGunVJoy.ps1
    - windows/btgun-vjoy/package/Rollback-BtGunVJoy.ps1
    - docs/windows/virtual-hid-strategy.md
    - docs/windows/test-signing-and-install.md
  modified: []
key-decisions:
  - "GitHub Actions workflow uses only first-party checkout/upload actions and runner-local MSBuild/Windows Kits discovery."
  - "Target scripts require explicit approval switches before test-signing, certificate import, install, devnode, rollback, or test-signing reversal operations."
  - "Plan 06-05 is blocked before CI artifact proof because GitHub CLI is not authenticated in this executor."
patterns-established:
  - "Test certificate private material enters only through GitHub secrets and is removed from the runner after signing."
  - "Artifact packaging records file hashes and excludes PFX/P12/key/id_rsa material."
  - "Windows target docs treat joy.cpl and real HID output report proof as mandatory later gates."
requirements-completed: []
duration: 4 min
started: 2026-06-10T00:48:17Z
completed: null
blocked_at: 2026-06-10T00:52:14Z
---

# Phase 06 Plan 05: Windows Driver CI Packaging Checkpoint

**Windows driver workflow, package scripts, and setup docs are created, but CI artifact validation is blocked by missing GitHub CLI authentication.**

## Performance

- **Duration:** 4 min before checkpoint
- **Started:** 2026-06-10T00:48:17Z
- **Blocked:** 2026-06-10T00:52:14Z
- **Tasks completed:** 2/3
- **Files modified:** 6

## Accomplishments

- Added `windows-driver` GitHub Actions workflow for Windows runner build/sign/package.
- Added `Package-BtGunVJoy.ps1` to collect signed driver package files, helper executables, public IOCTL header, install/rollback scripts, and `build-metadata.json`.
- Added approval-gated install and rollback scripts for target-side test-signing, `pnputil`, devnode, rollback, and test-signing reversal operations.
- Added Windows VHF/KMDF strategy and test-signing/install docs with `joy.cpl`, real HID output report proof, rollback, redaction, and no-target-toolchain rules.

## Task Commits

1. **Task 1: GitHub Actions build/sign/package workflow** - `33d99f3` (feat)
2. **Task 2: Install and rollback scripts with approval boundaries** - `89f5d99` (feat)
3. **Task 3: Windows VHF strategy docs and CI artifact validation** - blocked before task commit

## Files Created/Modified

- `.github/workflows/windows-driver.yml` - Windows GitHub Actions workflow for MSBuild/WDK discovery, driver/helper build, `inf2cat`, `signtool`, package validation, and artifact upload.
- `windows/btgun-vjoy/package/Package-BtGunVJoy.ps1` - Artifact collector with hash metadata and private-key material rejection.
- `windows/btgun-vjoy/package/Install-BtGunVJoy.ps1` - Target install script with approval gates and no target build-tool installs.
- `windows/btgun-vjoy/package/Rollback-BtGunVJoy.ps1` - Target rollback script with approval gates and no auto-reboot.
- `docs/windows/virtual-hid-strategy.md` - VHF/KMDF, report IDs, output haptic path, CI packaging, target proof strategy.
- `docs/windows/test-signing-and-install.md` - Test-signing, install, `pnputil`, `joy.cpl`, real output report proof, rollback, and redaction rules.

## Verification

- PASS: `rg -n "windows-driver|BTGUN_WINDOWS_TEST_CERT_PFX_BASE64|BTGUN_WINDOWS_TEST_CERT_PASSWORD|btgun-vjoy-windows-x64-testsigned|BtGunVJoy.vcxproj|inf2cat|signtool" .github/workflows/windows-driver.yml windows/btgun-vjoy/package/Package-BtGunVJoy.ps1`
- PASS: `rg -n "BtGunVJoy\\.sys|btgunvjoy\\.inf|btgunvjoy\\.cat|BtGunVJoyIoctl\\.h|btgun-driver-bridge\\.exe|btgun-hid-output-sender\\.exe|Install-BtGunVJoy\\.ps1|Rollback-BtGunVJoy\\.ps1|build-metadata\\.json" .github/workflows/windows-driver.yml windows/btgun-vjoy/package/Package-BtGunVJoy.ps1`
- PASS: `rg -n "USER APPROVAL REQUIRED|bcdedit|pnputil|Root\\\\BTGunVJoy|Rollback|testsigning" windows/btgun-vjoy/package/Install-BtGunVJoy.ps1 windows/btgun-vjoy/package/Rollback-BtGunVJoy.ps1`
- PASS: `rg -n "VHF|KMDF|testsigning|GitHub Actions|BTGUN_WINDOWS_TEST_CERT_PFX_BASE64|joy.cpl|pnputil|rollback|Root\\\\BTGunVJoy|real HID output" docs/windows/virtual-hid-strategy.md docs/windows/test-signing-and-install.md`
- BLOCKED: `gh auth status` returned `You are not logged into any GitHub hosts. To log in, run: gh auth login`

## Deviations from Plan

None - plan stopped at the required GitHub CLI authentication gate before CI artifact proof.

## Issues Encountered

- **Blocking auth gate:** GitHub CLI is not authenticated, so the required workflow trigger/watch/download/artifact assertions could not run.
- **Plan state:** Plan 06-05 is not complete. Do not feed Plan 06-06 until a continuation authenticates `gh`, triggers `windows-driver.yml`, watches the run, downloads `btgun-vjoy-windows-x64-testsigned`, and passes artifact assertions.

## Authentication Gates

### GitHub CLI

- **Found during:** Task 3
- **Command:** `gh auth status`
- **Result:** Not logged into any GitHub hosts.
- **Required user action:** Authenticate GitHub CLI for repo `Guisardo/bt-gun` with permission to run workflows and download artifacts.
- **Resume verification:** `gh auth status`

## Known Stubs

None. No placeholder, TODO, empty mock data, or unwired docs were added.

## Threat Flags

None. CI signing, artifact packaging, target install approval, and redaction surfaces are planned in the Plan 05 threat model.

## User Setup Required

GitHub CLI authentication is required before continuing Task 3:

```bash
gh auth login
gh auth status
```

Required repository secrets must exist before the workflow can pass:

- `BTGUN_WINDOWS_TEST_CERT_PFX_BASE64`
- `BTGUN_WINDOWS_TEST_CERT_PASSWORD`

## Next Phase Readiness

Blocked. Plan 06-06 must not start until Plan 06-05 CI artifact validation passes and this checkpoint is replaced by a complete summary.

## Self-Check: PASSED

- Found created files on disk: `.github/workflows/windows-driver.yml`, package scripts, install/rollback scripts, and docs.
- Found task commits `33d99f3` and `89f5d99` in git history.
- Found `.planning/phases/06-windows-virtual-joystick-path/06-05-SUMMARY.md` on disk after writing.

---
*Phase: 06-windows-virtual-joystick-path*
*Blocked: 2026-06-10*
