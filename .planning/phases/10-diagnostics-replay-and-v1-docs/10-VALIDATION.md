---
phase: 10
slug: diagnostics-replay-and-v1-docs
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-15
---

# Phase 10 - Validation Strategy

Per-phase validation contract for replay, diagnostics, redaction, and v1 docs.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Desktop Kotlin/JVM executable main tests through Gradle `test`; Android JVM unit executable main tests through `testDebugUnitTest`. |
| **Config file** | `desktop-companion/build.gradle.kts`, `android-host/app/build.gradle.kts`. |
| **Quick run command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Replay*' --tests '*Diagnostic*' --tests '*Visualizer*' --tests '*Udp*' --no-daemon --console=plain` |
| **Full suite command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --no-daemon --console=plain` plus `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --no-daemon --console=plain`. |
| **Estimated runtime** | Quick desktop slice: under 60 seconds after tests exist. Full desktop plus Android unit suite: project-dependent. |

---

## Sampling Rate

- **After every task commit:** Run the narrow Gradle command for the touched side plus a focused redaction/forbidden-pattern check.
- **After every plan wave:** Run the full desktop suite; run Android unit suite when Android diagnostics/status/dashboard code changes.
- **Before `$gsd-verify-work`:** Full desktop suite, Android relevant suite, docs/source scan, replay fixture scan, and redaction scan must be green or explicitly hardware/manual-gated.
- **Max feedback latency:** No three consecutive implementation tasks should lack an automated source, unit, replay, or docs assertion.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 10-replay-01 | TBD | 1 | PERF-04 | T-10-01 | Replay raw datagrams through auth/decode and receiver guard before visualizer assertions. | integration | `gradle -p desktop-companion test --tests '*ReplayFixture*' --tests '*UdpControllerStateAdapter*' --tests '*VisualizerMetrics*' --tests '*VisualizerModel*'` | no - Wave 0 | pending |
| 10-diagnostics-android-01 | TBD | 1 | PERF-05 | T-10-02 | Android diagnostics emit fixed domains/statuses with sanitized detail and no raw identifiers. | unit | `gradle -p android-host testDebugUnitTest --tests '*Diagnostic*' --tests '*DashboardState*' --tests '*VisualizerStatus*'` | no - Wave 0 | pending |
| 10-diagnostics-desktop-01 | TBD | 1 | PERF-05 | T-10-02 | Desktop diagnostics emit fixed domains/statuses and visualizer/export uses sanitized fields. | unit/integration | `gradle -p desktop-companion test --tests '*Diagnostic*' --tests '*ControlChannel*' --tests '*VisualizerModel*'` | no - Wave 0 | pending |
| 10-redaction-01 | TBD | 1 | PERF-05, PACK-04 | T-10-01 | Export and committed fixtures redact pairing codes, proof values, stream keys, HMAC material, private keys, Bluetooth addresses, serials, Android IDs, screenshots, and raw logs. | unit/source scan | `gradle -p desktop-companion test --tests '*Redactor*' --tests '*DiagnosticExport*'` or equivalent Phase 10 source-scan test. | partial - extend existing redactor tests | pending |
| 10-docs-android-01 | TBD | 2 | PACK-01 | T-10-03 | Android workflow doc names toolchain, env, install, permissions, USB/logcat, real-gun steps, Android HID mode, blockers. | docs/source scan | `gradle -p desktop-companion test --tests '*Docs*'` or equivalent docs guard. | no - Wave 0 | pending |
| 10-docs-lan-01 | TBD | 2 | PACK-04 | T-10-03 | LAN protocol/security docs include schemas, pairing, authentication, replay rules, lifecycle, haptics, diagnostics, fixture refs. | docs/source scan | `gradle -p desktop-companion test --tests '*Docs*'` or equivalent docs guard. | partial docs exist | pending |
| 10-docs-limits-01 | TBD | 2 | PACK-05 | T-10-03 | Known limits matrix includes required supported/unsupported/fallback/deferred rows, evidence pointer, and next proof needed. | docs/source scan | `gradle -p desktop-companion test --tests '*Docs*'` or equivalent docs guard. | no - Wave 0 | pending |

*Status: pending, green, red, flaky*

---

## Wave 0 Requirements

- [ ] `fixtures/replay/README.md` and first `fixtures/replay/udp-golden/*` files for PERF-04.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt` or equivalent full-chain replay test.
- [ ] Desktop diagnostics schema tests for domain, status, reason code, visualizer rendering, and redacted export.
- [ ] Android diagnostics schema tests for gun BLE, sensor motion, profile mapping, HID/backend/haptics, dashboard/status rendering, and redaction.
- [ ] Redaction/forbidden-pattern scan for committed fixtures, manifests, exports, and docs.
- [ ] Docs guard or manual checklist for PACK-01, PACK-04, and PACK-05.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android real-device diagnostic/export capture | PERF-05, PACK-01 | No Android device is attached in the current environment. | With a device attached, run the documented install/logcat workflow, connect the gun, start Android HID or LAN mode as applicable, export sanitized diagnostics, and verify no raw logs/screenshots or full identifiers are committed. |
| Windows VHF target status evidence | PACK-05 | Windows target execution is not available on this macOS workspace. | Link Phase 6 evidence rows and do not claim new Windows proof unless target checklist is rerun on Windows. |
| macOS Android HID haptics limitation | PACK-05 | Prior live evidence says unsupported/deferred; Phase 10 should document it, not retest unless hardware path changes. | Link Phase 7 evidence rows and name next proof required to change status. |

---

## Validation Sign-Off

- [ ] All plans have automated verify steps or explicit hardware/manual gates.
- [ ] Sampling continuity: no three consecutive tasks without automated verify.
- [ ] Wave 0 creates missing replay, diagnostics, redaction, and docs guard infrastructure.
- [ ] No watch-mode flags in verification commands.
- [ ] Redaction/forbidden-pattern scan covers every committed replay/export/doc artifact.
- [ ] `nyquist_compliant: true` remains set in frontmatter.

**Approval:** pending
