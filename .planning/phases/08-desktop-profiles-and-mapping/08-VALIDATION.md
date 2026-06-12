---
phase: 08
slug: desktop-profiles-and-mapping
status: ready-for-usb-profile-ui-evidence
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-12
---

# Phase 08 - Validation Strategy

> Per-phase validation contract for Android-owned profiles and mapped-stream output.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android unit tests through `gradle -p android-host testDebugUnitTest`; desktop Kotlin/JVM tests through `gradle -p desktop-companion test` |
| **Config file** | `android-host/app/build.gradle.kts`, `desktop-companion/build.gradle.kts` |
| **Quick run command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*Profile*' --tests '*BtGunHidReportPacker*' --tests '*AndroidUdpInputSender*' --tests '*DashboardState*' --no-daemon --console=plain` |
| **Full suite command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --no-daemon --console=plain && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --no-daemon --console=plain` |
| **Estimated runtime** | Focused Android tests under 90 seconds; full Android plus desktop under 4 minutes on local machine |

---

## Sampling Rate

- **After every task commit:** Run the quick Android command or a narrower `--tests` subset named in the task.
- **After every plan wave:** Run Android focused tests plus any touched desktop tests.
- **Before `$gsd-verify-work`:** Full Android and desktop suites green.
- **Max feedback latency:** 4 minutes for automated feedback; USB screenshot evidence is manual/hardware-bound.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | PROF-01..PROF-06 | T-08-01 / T-08-02 | Docs do not authorize desktop profile editing or raw-default stream | docs/source | `rg -n "Android-owned profiles|Default Visualizer|raw debug" .planning docs` and forbidden-label negative `rg` | W0 | green |
| 08-02-01 | 02 | 1 | PROF-01, PROF-04, PROF-06 | T-08-03 / T-08-04 | Profile decode/save never crashes service and invalid profiles are rejected | unit | `gradle -p android-host testDebugUnitTest --tests '*ProfileStore*' --tests '*ProfileValidation*'` | W0 | pending |
| 08-03-01 | 03 | 2 | PROF-02, PROF-03, PROF-04 | T-08-05 | Mapper applies profile math with bounded smoothing lag | unit | `gradle -p android-host testDebugUnitTest --tests '*ProfileMapper*' --tests '*AdaptiveAimSmoother*'` | W0 | pending |
| 08-04-01 | 04 | 3 | PROF-02, PROF-03, PROF-04, PROF-05, PROF-06 | T-08-06 / T-08-07 | HID and LAN consume one Android-mapped state; raw debug is Android-controlled | unit/service | `gradle -p android-host testDebugUnitTest --tests '*HostSessionService*' --tests '*BtGunHidReportPacker*' --tests '*AndroidUdpInputSender*'` | W0 | pending |
| 08-05-01 | 05 | 4 | PROF-01..PROF-06 | T-08-08 / T-08-09 | UI blocks invalid save, shows Android authority, no desktop-owned copy | unit/device | `gradle -p android-host testDebugUnitTest --tests '*DashboardState*' --tests '*Profile*'` | W0 | pending |
| 08-06-01 | 06 | 5 | PROF-05, PROF-06 | T-08-10 / T-08-11 | Desktop only displays Android profile metadata and mapped-stream diagnostics | unit | `gradle -p desktop-companion test --tests '*PairingWindow*' --tests '*ControlChannel*' --tests '*UdpControllerStateAdapter*'` | W0 | pending |
| 08-07-01 | 07 | 6 | PROF-01..PROF-06 | T-08-12 / T-08-13 | Evidence is sanitized; screenshots are ignored and stale files deleted | full + manual | Full suite, forbidden-label `rg`, redaction `rg`, USB screenshot capture | W0 | pending |

*Status: pending, green, red, flaky.*

---

## Wave 0 Requirements

- [ ] `android-host/app/src/test/java/com/btgun/host/profile/ProfileValidationTest.kt` - stubs and cases for PROF-01, PROF-04.
- [ ] `android-host/app/src/test/java/com/btgun/host/profile/ProfileStoreTest.kt` - stubs and cases for PROF-01, PROF-06.
- [ ] `android-host/app/src/test/java/com/btgun/host/profile/ProfileMapperTest.kt` - stubs and cases for PROF-02, PROF-03, PROF-04.
- [ ] `android-host/app/src/test/java/com/btgun/host/profile/AdaptiveAimSmootherTest.kt` - stubs and cases for PROF-03 latency fallback.
- [ ] Extend `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt` for mapped-state input.
- [ ] Extend `android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt` for mapped default stream and raw debug toggle.
- [ ] Extend `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` for read-only active Android profile metadata.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android profile UI screenshot evidence | PROF-01..PROF-06 | Requires connected USB Android device and visual layout check | Capture `phase8-dashboard-profile-rows`, `phase8-profile-list-default`, `phase8-profile-editor-provider-overrides`, `phase8-validation-blocked-save`, and `phase8-raw-debug-toggle` under ignored `.evidence/phase8/android-profile-ui/<run-id>/`; remove stale unused screenshots before closeout |
| Runtime profile changes affect live HID/LAN behavior | PROF-05 | Unit tests prove seams; final feel/path proof uses real session later | During Phase 8 closeout, select a user profile and confirm dashboard metadata/revision changes without reinstall or rebuild |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies.
- [x] Sampling continuity: no 3 consecutive tasks without automated verify.
- [x] Wave 0 covers all MISSING references.
- [x] No watch-mode flags.
- [x] Feedback latency target is under 4 minutes for automated gates.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** Wave 0 docs correction complete; ready for profile test stubs.

## Plan 08-07 Execution Status

- Automated Android suite: green.
- Automated desktop suite: green.
- Forbidden desktop edit/raw-request/storage label guard: green.
- Raw debug default guard: green.
- USB Android profile UI screenshot evidence: pending human verification checkpoint.
