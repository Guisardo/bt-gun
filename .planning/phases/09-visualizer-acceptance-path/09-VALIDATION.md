---
phase: 09
slug: visualizer-acceptance-path
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-12
---

# Phase 09 - Validation Strategy

> Per-phase validation contract for the Swing visualizer acceptance path.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Desktop Kotlin/JVM executable tests through `gradle -p desktop-companion test`; Android unit tests through `gradle -p android-host testDebugUnitTest` when Android visualizer-status diagnostics are added. |
| **Config file** | `desktop-companion/build.gradle.kts`, `android-host/app/build.gradle.kts` |
| **Quick run command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Visualizer*' --tests '*PairingWindow*' --tests '*ControlChannel*' --tests '*UdpControllerStateAdapter*' --no-daemon --console=plain` |
| **Full suite command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --no-daemon --console=plain` plus `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*Visualizer*' --tests '*HostSessionService*' --no-daemon --console=plain` if Android status diagnostics change. |
| **Estimated runtime** | Focused desktop tests under 90 seconds; full desktop plus Android focused tests under 4 minutes on local machine. |

---

## Sampling Rate

- **After every task commit:** Run the quick desktop command or narrower touched `--tests '*Visualizer*'` subset.
- **After every plan wave:** Run full desktop suite; add Android focused tests when Android status diagnostics or haptic/status payloads change.
- **Before `$gsd-verify-work`:** Full desktop suite green, required Android focused tests green, and guided manual checklist rows completed.
- **Max feedback latency:** 4 minutes for automated feedback; real Android/macOS/Windows proof rows are manual and hardware-bound.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 09-W0-01 | TBD | 0 | VIS-01, VIS-02, VIS-04 | T-09-01 / T-09-05 | UI event fanout does not clobber pairing window or backend callbacks. | unit | `gradle -p desktop-companion test --tests '*DesktopUiEventHub*' --tests '*PairingWindow*'` | W0 missing | pending |
| 09-W0-02 | TBD | 0 | VIS-06, PERF-01, PERF-02 | T-09-02 / T-09-03 | Metrics use accepted UDP sequence gaps and estimated Android-to-desktop monotonic offset, not direct clock-origin subtraction. | unit | `gradle -p desktop-companion test --tests '*VisualizerMetrics*'` | W0 missing | pending |
| 09-W0-03 | TBD | 0 | VIS-02, VIS-03, VIS-04, VIS-05 | T-09-01 / T-09-04 | Model separates observed live state from user-confirmed checklist rows and never exposes secrets. | unit | `gradle -p desktop-companion test --tests '*VisualizerModel*'` | W0 missing | pending |
| 09-W0-04 | TBD | 0 | VIS-01, VIS-05 | T-09-04 | LAN haptic button is enabled only for authenticated active session and displays ack/fail from trusted result. | unit/UI model | `gradle -p desktop-companion test --tests '*VisualizerWindow*' --tests '*ControlChannel*'` | W0 missing | pending |
| 09-W0-05 | TBD | 0 | VIS-03, VIS-04 | T-09-01 / T-09-06 | Android visualizer status diagnostics are sanitized and contain recenter/aim-zero state without pairing or stream secrets. | unit | `gradle -p android-host testDebugUnitTest --tests '*Visualizer*' --tests '*HostSessionService*'` | W0 missing | pending |
| 09-MAN-01 | TBD | final | VIS-01..VIS-06, PERF-01, PERF-02 | T-09-02 / T-09-03 / T-09-04 | Final pass requires guided manual proof for LAN visualizer, macOS Android HID input, Windows VHF input, LAN haptic, Windows output-to-phone haptic, and macOS haptic unsupported/deferred row. | manual | Visualizer guided checklist plus full automated suites | Manual | pending |

*Status: pending, green, red, flaky.*

---

## Wave 0 Requirements

- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/DesktopUiEventHubTest.kt` - PairingWindow, VisualizerWindow, and backend runtimes all receive UDP/session/haptic events without callback clobber.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerMetricsTest.kt` - sequence-gap packet loss, session reset, offset latency math, and `<50 ms` target labels.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerModelTest.kt` - live gamepad state, stale overlay, raw-debug drawer state, haptic statuses, recenter/aim-zero rows, and checklist row transitions.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt` - auto-open/manual reopen seams, LAN haptic action gating, labels/actions/helpers, and forbidden desktop profile controls.
- [ ] Android status diagnostics tests if planner adds `VisualizerStatus.kt`: recenter emitted, aim-zero/baseline state, raw debug flag, time-sync fields if any, and no secrets.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| LAN visualizer acceptance | VIS-01..VIS-06, PERF-01, PERF-02 | Requires live Android phone, iPega gun, LAN session, and human observation of physical controls. | Pair Android to desktop, reach authenticated session, confirm visualizer auto-opens, press trigger/reload/X/Y/A/B, move stick/aim, hold recenter, verify metrics and stale/disconnect states. |
| macOS Android Bluetooth HID input | VIS-04 | Requires macOS Bluetooth host visibility and live Android HID path. | Start Android Bluetooth HID gamepad mode, pair macOS, confirm input moves in host/gamepad probe, mark guided checklist row with sanitized notes. |
| Windows VHF input and output-to-phone haptic | VIS-04, VIS-05 | Requires Windows 11 x64 target with VHF driver path and phone vibration observation. | Run Windows VHF proof using Phase 6 checklist, confirm virtual controller input, trigger output haptic to phone, mark guided checklist rows. |
| macOS Bluetooth HID haptic unsupported/deferred row | VIS-05 | Current Phase 7 evidence allows unsupported/deferred output row when limitation is visible. | Show current unsupported/deferred evidence in visualizer row and confirm it does not block LAN or Windows haptic proof. |

---

## Validation Sign-Off

- [ ] All planned tasks have `<automated>` verify or Wave 0 dependencies.
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify.
- [ ] Wave 0 covers all MISSING references.
- [ ] No watch-mode flags.
- [ ] Feedback latency target is under 4 minutes for automated gates.
- [ ] `nyquist_compliant: true` set in frontmatter after Wave 0 tests exist and pass.

**Approval:** pending Phase 09 planning.
