---
phase: 06-windows-virtual-joystick-path
plan: 05
status: complete
subsystem: windows-driver-ci-packaging
tags: [windows, kmdf, vhf, github-actions, test-signing, packaging]
requires:
  - phase: 06-windows-virtual-joystick-path
    provides: KMDF/VHF driver source, INF, IOCTL ABI, and helper tools from Plan 06-02
provides:
  - Windows driver CI workflow source
  - Test-signed Windows driver artifact from GitHub Actions
  - Driver package collection script
  - Approval-gated Windows install and rollback scripts
  - Windows VHF strategy and test-signing setup docs
affects: [phase-06, windows-driver-ci, windows-target-proof, plan-06-06]
tech-stack:
  added: []
  patterns: [GitHub Actions Windows driver packaging, WDK MSBuild toolset discovery, transient test certificate signing, approval-gated target scripts]
key-files:
  created:
    - .github/workflows/windows-driver.yml
    - windows/btgun-vjoy/package/Package-BtGunVJoy.ps1
    - windows/btgun-vjoy/package/Install-BtGunVJoy.ps1
    - windows/btgun-vjoy/package/Rollback-BtGunVJoy.ps1
    - docs/windows/virtual-hid-strategy.md
    - docs/windows/test-signing-and-install.md
  modified:
    - windows/btgun-vjoy/driver/BtGunVJoy.vcxproj
    - windows/btgun-vjoy/driver/BtGunVJoy.h
    - windows/btgun-vjoy/driver/BtGunVJoyDevice.c
    - windows/btgun-vjoy/include/BtGunVJoyIoctl.h
    - windows/btgun-vjoy/tools/hid-output-sender/HidOutputSender.cpp
key-decisions:
  - "GitHub Actions workflow uses first-party checkout/upload actions, runner-local MSBuild/WDK discovery, WDK Visual Studio toolset validation, and explicit SHA256 signing."
  - "Target scripts require explicit approval switches before test-signing, certificate import, install, devnode, rollback, or test-signing reversal operations."
  - "CI artifact validation used the GitHub REST API with github-guisardo credentials instead of GitHub CLI."
patterns-established:
  - "Test certificate private material enters only through GitHub secrets and is removed from the runner after signing."
  - "Artifact packaging records file hashes and excludes PFX/P12/key/id_rsa material."
  - "Windows target docs treat joy.cpl and real HID output report proof as mandatory later gates."
requirements-completed: [PACK-02]
duration: 68 min
started: 2026-06-10T00:48:17Z
completed: 2026-06-10T01:55:30Z
---

# Phase 06 Plan 05: Windows Driver CI Packaging Summary

**Windows driver CI now builds, test-signs, packages, uploads, downloads, and validates the VHF/KMDF driver artifact without installing toolchains on the Windows proof target.**

## Performance

- **Duration:** 68 min including checkpoint recovery and CI hardening
- **Started:** 2026-06-10T00:48:17Z
- **Completed:** 2026-06-10T01:55:30Z
- **Tasks completed:** 3/3
- **Files modified:** 11

## Accomplishments

- Added `windows-driver` GitHub Actions workflow for Windows runner WDK/MSBuild discovery, driver/helper build, `inf2cat`, SHA256 `signtool`, package validation, and artifact upload.
- Added `Package-BtGunVJoy.ps1` to collect signed driver package files, helper executables, public IOCTL header, install/rollback scripts, and `build-metadata.json`.
- Added approval-gated install and rollback scripts for target-side test-signing, `pnputil`, devnode, rollback, and test-signing reversal operations.
- Added Windows VHF/KMDF strategy and test-signing/install docs with `joy.cpl`, real HID output report proof, rollback, redaction, and no-target-toolchain rules.
- Fixed CI-exposed driver/helper build issues: WDK toolset validation, kernel-safe IOCTL header, VHF descriptor length type, disabled MSBuild auto-sign, helper batch execution, and HID sender GUID definition.
- Triggered and validated successful GitHub Actions run `27249711691` on branch `codex/phase6-windows-ci-artifact`, artifact `7525354356`, including packaged `btgun-devnode.exe`.

## Task Commits

1. **Task 1: GitHub Actions build/sign/package workflow** - `33d99f3` (feat)
2. **Task 2: Install and rollback scripts with approval boundaries** - `89f5d99` (feat)
3. **Task 3: CI artifact validation and hardening** - `dc5b477`, `528208b`, `91cd26d`, `cdd8fdd`, `ded2aa0`, `327d607`, `5273243`, `6bdb97b`, `a878117`, `da0229d` (fix)

## Files Created/Modified

- `.github/workflows/windows-driver.yml` - Windows GitHub Actions workflow for WDK/MSBuild toolchain validation, driver/helper build, test signing, package validation, and artifact upload.
- `windows/btgun-vjoy/package/Package-BtGunVJoy.ps1` - Artifact collector with hash metadata and private-key material rejection.
- `windows/btgun-vjoy/package/Install-BtGunVJoy.ps1` - Target install script with approval gates and no target build-tool installs.
- `windows/btgun-vjoy/package/Rollback-BtGunVJoy.ps1` - Target rollback script with approval gates and no auto-reboot.
- `docs/windows/virtual-hid-strategy.md` - VHF/KMDF, report IDs, output haptic path, CI packaging, target proof strategy.
- `docs/windows/test-signing-and-install.md` - Test-signing, install, `pnputil`, `joy.cpl`, real output report proof, rollback, and redaction rules.
- `windows/btgun-vjoy/driver/BtGunVJoy.vcxproj` - Disables MSBuild auto-sign so workflow signs explicitly.
- `windows/btgun-vjoy/driver/BtGunVJoy.h` and `BtGunVJoyDevice.c` - VHF compile fixes.
- `windows/btgun-vjoy/include/BtGunVJoyIoctl.h` - Kernel/user-mode safe shared ABI header.
- `windows/btgun-vjoy/tools/hid-output-sender/HidOutputSender.cpp` - HID interface GUID definition for helper link.
- `windows/btgun-vjoy/tools/devnode/Devnode.cpp` - SetupAPI helper that creates or verifies the `Root\BTGunVJoy` devnode from the packaged artifact.

## Verification

- PASS: `rg -n "windows-driver|BTGUN_WINDOWS_TEST_CERT_PFX_BASE64|BTGUN_WINDOWS_TEST_CERT_PASSWORD|btgun-vjoy-windows-x64-testsigned|BtGunVJoy.vcxproj|inf2cat|signtool" .github/workflows/windows-driver.yml windows/btgun-vjoy/package/Package-BtGunVJoy.ps1`
- PASS: `rg -n "BtGunVJoy\\.sys|btgunvjoy\\.inf|btgunvjoy\\.cat|BtGunVJoyIoctl\\.h|btgun-driver-bridge\\.exe|btgun-hid-output-sender\\.exe|btgun-devnode\\.exe|Install-BtGunVJoy\\.ps1|Rollback-BtGunVJoy\\.ps1|build-metadata\\.json" .github/workflows/windows-driver.yml windows/btgun-vjoy/package/Package-BtGunVJoy.ps1`
- PASS: `rg -n "USER APPROVAL REQUIRED|bcdedit|pnputil|Root\\\\BTGunVJoy|Rollback|testsigning" windows/btgun-vjoy/package/Install-BtGunVJoy.ps1 windows/btgun-vjoy/package/Rollback-BtGunVJoy.ps1`
- PASS: `rg -n "VHF|KMDF|testsigning|GitHub Actions|BTGUN_WINDOWS_TEST_CERT_PFX_BASE64|joy.cpl|pnputil|rollback|Root\\\\BTGunVJoy|real HID output" docs/windows/virtual-hid-strategy.md docs/windows/test-signing-and-install.md`
- PASS: GitHub REST API `workflow_dispatch` for `windows-driver.yml` on `codex/phase6-windows-ci-artifact`.
- PASS: GitHub Actions run `27249711691` completed `success`.
- PASS: GitHub REST API downloaded artifact `btgun-vjoy-windows-x64-testsigned` to `/private/tmp/btgun-phase6-ci-artifact-download-devnode`.
- PASS: artifact assertion found `.sys`, `.inf`, `.cat`, `BtGunVJoyIoctl.h`, `btgun-driver-bridge.exe`, `btgun-hid-output-sender.exe`, `btgun-devnode.exe`, install/rollback scripts, and `build-metadata.json`.
- PASS: artifact assertion found no `.pfx`, `.p12`, `.key`, or `id_rsa` private key material.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced unavailable GitHub CLI flow with GitHub REST API**
- **Found during:** Task 3 checkpoint continuation
- **Issue:** `gh auth status` was not authenticated, and the user requested GitHub API instead of `gh`.
- **Fix:** Used `github-guisardo` 1Password token with GitHub REST API for workflow dispatch, run polling, log download, artifact listing, and artifact download.
- **Files modified:** `.planning/phases/06-windows-virtual-joystick-path/06-05-SUMMARY.md`
- **Verification:** Run `27249711691` passed and artifact `7525354356` downloaded/validated.
- **Committed in:** pending summary commit.

**2. [Rule 3 - Blocking] CI runner had WDK headers but MSBuild was not using driver toolset**
- **Found during:** GitHub Actions run logs
- **Issue:** Early runs compiled with user-mode `cl` flags and could not find `ntddk.h`.
- **Fix:** Workflow now validates WDK headers, `vhfkm.lib`, and `WindowsKernelModeDriver10.0` MSBuild toolset, installs WDK VSIX integration when missing, and pins the discovered kit version.
- **Verification:** Later CI logs show `Building 'BtGunVJoy' with toolset 'WindowsKernelModeDriver10.0'` and driver build success.
- **Committed in:** `91cd26d`, `cdd8fdd`

**3. [Rule 3 - Blocking] Real WDK compile surfaced driver/helper portability issues**
- **Found during:** GitHub Actions run logs
- **Issue:** Shared IOCTL header pulled user-mode headers into kernel compile, `hidport.h` conflicted with VHF, descriptor length used a wider type, MSBuild auto-sign used an invalid default digest flow, helper batch commands did not run, and HID sender missed GUID definition.
- **Fix:** Made shared header kernel-safe, removed conflicting `hidport.h`, changed descriptor length to `USHORT`, disabled MSBuild auto-sign, wrote helper commands to a `.cmd` file, and added `initguid.h` for the HID sender.
- **Verification:** Final CI run passed all build/sign/package/upload steps.
- **Committed in:** `ded2aa0`, `327d607`, `5273243`, `6bdb97b`, `a878117`, `da0229d`

---

**Total deviations:** 3 auto-fixed
**Impact on plan:** No product scope change. Plan 05 now has stronger CI proof than originally available because the workflow was executed on GitHub and the artifact was downloaded and structurally verified.

## Issues Encountered

- GitHub CLI was unavailable, but GitHub REST API was available through `github-guisardo`.
- The GitHub-hosted Windows runner had SDK signing tools but needed explicit WDK toolset/header/lib validation to avoid false positives.
- No private key material was committed or printed. Repository secrets were configured by API for the required test certificate values.

## Known Stubs

None. CI produces a real test-signed KMDF/VHF `.sys`, `.inf`, `.cat`, helper executables, scripts, public header, and metadata.

## Threat Flags

None unresolved. CI signing, artifact packaging, target install approval, and redaction surfaces are covered by the plan threat model and validated by workflow assertions.

## User Setup Required

None for Plan 05. Plan 06-06 still requires explicit user approval before any Windows target boot, test-signing, reboot, driver install, devnode creation, rollback, or GUI proof action.

## Next Phase Readiness

Ready. Plan 06-04 can wire companion runtime haptic routing, and Plan 06-06 can later consume the downloaded artifact for approval-gated Windows target proof.

## Self-Check: PASSED

- Found created workflow, package scripts, install/rollback scripts, docs, and CI-hardening driver/helper changes on disk.
- Found successful GitHub Actions run `27249711691`.
- Found downloaded artifact at `/private/tmp/btgun-phase6-ci-artifact-download-devnode`.
- Artifact contents matched the required Plan 05 file set and excluded private key material.
- Found `.planning/phases/06-windows-virtual-joystick-path/06-05-SUMMARY.md` on disk after replacing checkpoint.

---
*Phase: 06-windows-virtual-joystick-path*
*Completed: 2026-06-10*
