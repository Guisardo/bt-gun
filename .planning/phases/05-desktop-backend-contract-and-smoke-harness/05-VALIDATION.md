---
phase: 05
slug: desktop-backend-contract-and-smoke-harness
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-09
---

# Phase 05 - Validation Strategy

Per-phase validation contract for feedback sampling during execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Plain Kotlin `main()` test classes registered in Gradle `Test.doLast`; no JUnit dependency. |
| **Config file** | `desktop-companion/build.gradle.kts` |
| **Quick run command** | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test` |
| **Full suite command** | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test smokeDesktopBackendMacosStub smokeDesktopBackendWindowsStub` |
| **Estimated runtime** | ~30-90 seconds locally; Windows stub requires a real Windows run for final phase evidence. |

## Sampling Rate

- **After every task commit:** Run `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test`
- **After every plan wave:** Run `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test smokeDesktopBackendMacosStub` on macOS; run `smokeDesktopBackendWindowsStub` on Windows before phase close.
- **Before `$gsd-verify-work`:** Gradle test suite, macOS stub XML, Windows stub XML, and haptic human-confirmed manifest row must exist.
- **Max feedback latency:** 90 seconds for local automated checks.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | DESK-04 | T-05-01 | Descriptor exposes only six v1 buttons, four v1 axes, and digital trigger. | unit/contract | `gradle test` | no - Wave 0 | pending |
| 05-02-01 | 02 | 2 | DESK-07 | T-05-02 | Capabilities include platform/detail reasons for unsupported output/haptic features. | unit/contract | `gradle test` | no - Wave 0 | pending |
| 05-03-01 | 03 | 3 | DESK-08 | T-05-03 | UDP fixture replay goes through authenticated receiver path before backend publish. | integration | `gradle test` | no - Wave 0 | pending |
| 05-04-01 | 04 | 4 | DESK-08 | T-05-04 | macOS stub emits JUnit-style XML without claiming OS-visible HID support. | smoke | `gradle smokeDesktopBackendMacosStub` | no - Wave 0 | pending |
| 05-04-02 | 04 | 4 | DESK-08 | T-05-04 | Windows stub emits JUnit-style XML without claiming OS-visible HID support. | smoke/human-run | `gradle smokeDesktopBackendWindowsStub` | no - Wave 0 | pending |
| 05-05-01 | 05 | 5 | DESK-07, DESK-08 | T-05-05 | Simulated output report maps to existing authenticated phone haptic command path only. | smoke/manual | `gradle smokeDesktopBackendMacosStub -Pbtgun.smoke.haptic=true` and Windows equivalent | no - Wave 0 | pending |

*Status: pending / green / red / flaky*

## Wave 0 Requirements

- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` - DESK-04 named semantic state.
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` - DESK-04 descriptor invariants.
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - DESK-07 structured capabilities and unsupported reasons.
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` - DESK-08 Phase 4 receiver handoff.
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt` - DESK-08 JUnit-style smoke artifacts.
- [ ] Gradle tasks `smokeDesktopBackendMacosStub` and `smokeDesktopBackendWindowsStub`.
- [ ] `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl` - cross-platform and haptic evidence manifest.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Windows 11 x64 stub command runs and emits XML. | DESK-08 | Current host is macOS; Phase 5 requires Windows evidence. | Run `GRADLE_USER_HOME=%TEMP%\\bt-gun-gradle gradle smokeDesktopBackendWindowsStub` from `desktop-companion` on Windows 11 x64 and save/record the XML artifact path. |
| Phone haptic is physically felt during simulated output-report smoke. | DESK-07, DESK-08 | D-19/D-20 require paired Android and human confirmation. | Pair Android, run platform smoke with `-Pbtgun.smoke.haptic=true`, confirm phone vibration, and append a non-secret manifest row. |

## Validation Sign-Off

- [x] All tasks have automated verify commands or Wave 0 dependencies.
- [x] Sampling continuity: no 3 consecutive tasks without automated verify.
- [x] Wave 0 covers all missing references.
- [x] No watch-mode flags.
- [x] Feedback latency target is under 90 seconds for local automated checks.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** pending
