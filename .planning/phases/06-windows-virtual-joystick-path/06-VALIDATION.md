---
phase: 06
slug: windows-virtual-joystick-path
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-09
---

# Phase 06 - Validation Strategy

Per-phase validation contract for feedback sampling during execution.

Revision note: Wave 0 validation coverage executed and passed through automated checks, CI artifact proof, Windows target proof, and user approval on 2026-06-10.

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | Plain Kotlin `main()` test classes through Gradle for desktop companion logic; Windows driver build/sign/package checks through CI; manual Windows target proof for OS-visible HID behavior. |
| Config file | `desktop-companion/build.gradle.kts`, future `.github/workflows/windows-driver.yml`, future `windows/btgun-vjoy/driver/BtGunVJoy.vcxproj` |
| Quick run command | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain` |
| Full suite command | Quick run plus Windows driver CI workflow plus Phase 6 Windows proof checklist. |
| Estimated runtime | ~30-90 seconds for local companion tests; CI and Windows target proof runtime unknown until driver workflow exists. |

## Sampling Rate

- After every companion task commit: run `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain`.
- After every driver/package task commit: run the Windows driver CI build/sign/package workflow or document why it cannot run locally.
- After every plan wave: run all available local companion tests plus the latest driver package build check.
- Before target install: get explicit user approval for any `bcdedit`, boot signing, reboot, driver install, or rollback action on `192.168.1.100`.
- Before `$gsd-verify-work`: PnP/HID CLI evidence, `joy.cpl` visual evidence, live Android/gun input movement, real HID output report evidence, user phone-vibration confirmation, and sanitized evidence manifest must exist.
- Max feedback latency: one task for local code paths; one wave for CI driver package checks; manual gates cannot be skipped.

## Per-Task Verification Map

| Validation ID | Plan/Task Coverage | Execution Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Planned Artifact | Status |
|---------------|--------------------|----------------|-------------|------------|-----------------|-----------|-------------------|------------------|--------|
| 06-W0-01 | 06-01 Task 1, 06-01 Task 2 | 1 | DESK-02 | T-06-01 | Semantic state packs to report ID 1 with six buttons, four axes, bounded values, and stale button clearing. | unit | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain` | `WindowsHidReportPacker.kt`, `WindowsHidReportPackerTest.kt` | green |
| 06-W0-02 | 06-01 Task 1, 06-01 Task 2 | 1 | DESK-05 | T-06-02 | Report ID 2 maps only valid, bounded output bytes to `HapticCommand` and rejects bad id, length, version, duration, TTL, and strength. | unit | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain` | `WindowsOutputReportMapper.kt`, `WindowsOutputReportMapperTest.kt` | green |
| 06-W0-03 | 06-02 Task 2, 06-05 Task 1, 06-05 Task 3 | 2/3 | DESK-02, PACK-02 | T-06-04, T-06-16 | KMDF/VHF driver package builds and artifact contains `.sys`, `.inf`, `.cat`, public IOCTL header, helper tools, install scripts, and build metadata. | CI build/artifact | GitHub API-triggered `windows-driver.yml` run and artifact assertions from 06-05 Task 3 | `.github/workflows/windows-driver.yml`, `windows/btgun-vjoy/driver/BtGunVJoy.vcxproj`, CI artifact | green |
| 06-W0-04 | 06-02 Task 1, 06-02 Task 2, 06-05 Task 3 | 2/3 | DESK-02 | T-06-04, T-06-05 | Driver IOCTL path validates buffer size, version, report id, and access before submitting input to VHF. | driver source review plus WDK/CI build | CI artifact gate on `windows-driver.yml` | `BtGunVJoyIoctl.h`, `BtGunVJoyQueue.c`, CI artifact | green |
| 06-W0-05 | 06-06 Task 2, 06-06 Task 3 | 5 | DESK-02 | T-06-19, T-06-20 | Windows target shows PnP/HID device and `joy.cpl` lists the virtual joystick after approval-gated install. | manual gate plus CLI | `pnputil`, `Get-PnpDevice`, `control joy.cpl` per 06-06 Task 3 | `phase6-pnp-hid-cli`, `phase6-joy-cpl-visible` manifest rows | green |
| 06-W0-06 | 06-04 Task 1, 06-04 Task 2, 06-06 Task 3 | 4/5 | DESK-02 | T-06-11, T-06-20 | Live paired Android/gun input moves Windows-visible buttons and axes; replay/fake input is not accepted as final proof. | manual live smoke | Windows backend run with `btgun.windows.driver.enabled=true` plus live paired Android/gun | `phase6-live-android-gun-input` manifest row | green |
| 06-W0-07 | 06-02 Task 2, 06-02 Task 3, 06-04 Task 1, 06-04 Task 2, 06-06 Task 3 | 2/4/5 | DESK-05 | T-06-05, T-06-13, T-06-21 | Real Windows HID output report reaches the driver callback and routes to authenticated Android phone haptic. | manual live smoke | `joy.cpl` attempt, then `btgun-hid-output-sender.exe --strength 192 --duration-ms 120 --ttl-ms 500` fallback only if documented | `phase6-hid-output-report-phone-haptic` manifest row | green |
| 06-W0-08 | 06-05 Task 2, 06-05 Task 3, 06-06 Task 1 | 3/5 | PACK-02 | T-06-17, T-06-18, T-06-22 | Docs describe VHF strategy, test signing, install, proof, rollback, and no private key material. | docs review/redaction scan | `rg -n "VHF|testsigning|joy.cpl|pnputil|rollback" docs/windows windows .github/workflows` plus redaction scan in 06-06 Task 3 | `docs/windows/test-signing-and-install.md`, `docs/windows/phase6-proof-checklist.md` | green |

Status values: coverage planned; execution pending; green; red; flaky.

## Wave 0 Requirements

- [x] 06-W0-01 / 06-01 Task 2: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt` - DESK-02 fixed Phase 5 semantic state to HID report ID 1.
- [x] 06-W0-01 / 06-01 Task 1: `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPackerTest.kt` - report bytes, axis bounds, button bits, and stale behavior.
- [x] 06-W0-02 / 06-01 Task 2: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt` - DESK-05 output report ID 2 to phone haptic command.
- [x] 06-W0-02 / 06-01 Task 1: `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapperTest.kt` - valid output mapping and malformed output rejection.
- [x] 06-W0-03 and 06-W0-04 / 06-02 Task 2 plus 06-05 Task 3: `windows/btgun-vjoy/driver/` - KMDF/VHF source driver with minimal report bridge behavior and CI artifact build validation.
- [x] 06-W0-03 / 06-02 Task 2: `windows/btgun-vjoy/package/btgunvjoy.inf` - driver package metadata for the Windows virtual joystick path.
- [x] 06-W0-03 / 06-05 Task 1 and Task 3: `.github/workflows/windows-driver.yml` - CI build/sign/package artifact workflow without committed private key material and with downloaded artifact assertions.
- [x] 06-W0-08 / 06-05 Task 2 and Task 3: `docs/windows/test-signing-and-install.md` - explicit approval, test-signing, install, proof, and rollback steps.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Boot/signing or reboot changes on `192.168.1.100`. | PACK-02 | Locked D-03 requires explicit user approval before every such action. | Present the exact command and effect, wait for approval, then run only the approved command and document reversal. |
| Windows Game Controllers lists the virtual joystick. | DESK-02 | `joy.cpl` is a GUI proof surface and is mandatory for final Phase 6 pass. | Install the CI-built package on `192.168.1.100`, run `control joy.cpl`, capture agent visual evidence, and get user confirmation. |
| Live Android/gun stream moves Windows-visible controls. | DESK-02 | Final proof must use real paired hardware and cannot rely on replay/fake state. | Pair Android/gun to desktop, start Windows backend, move trigger/buttons/axes, and capture CLI plus `joy.cpl` evidence. |
| Real HID output report causes Android phone vibration. | DESK-05 | Unit tests can validate bytes, but final proof needs Windows HID output and physical phone vibration. | Try `joy.cpl` first; if it cannot send output, record that limitation, run the fallback HID output sender, and get user phone-vibration confirmation. |

## Validation Sign-Off

- [x] All tasks have automated verify commands or Wave 0 dependencies.
- [x] Sampling continuity: no 3 consecutive tasks without automated verify.
- [x] Wave 0 covers all missing references.
- [x] No watch-mode flags.
- [x] Feedback latency target is bounded for local code paths and manual gates are explicit.
- [x] `nyquist_compliant: true` set in frontmatter after Wave 0 coverage exists and passes.

Approval: approved 2026-06-10 after Phase 06 Windows target proof and user sign-off.
