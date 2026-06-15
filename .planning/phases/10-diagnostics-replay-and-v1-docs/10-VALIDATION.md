---
phase: 10
slug: diagnostics-replay-and-v1-docs
status: complete
nyquist_compliant: true
wave_0_complete: true
updated: 2026-06-15
---

# Phase 10 - Validation Strategy

Final validation contract for replay, diagnostics, redaction, and v1 docs.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Desktop Kotlin/JVM executable main tests through Gradle `test`; Android JVM unit executable main tests through `testDebugUnitTest`; docs/source guards through desktop Gradle tests and shell scans. |
| **Config file** | `desktop-companion/build.gradle.kts`, `android-host/app/build.gradle.kts`. |
| **Quick run command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Replay*' --tests '*Diagnostic*' --tests '*Visualizer*' --tests '*Docs*' --no-daemon --console=plain` |
| **Android diagnostic command** | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk gradle -p android-host testDebugUnitTest --tests '*Diagnostic*' --tests '*DashboardState*' --tests '*VisualizerStatus*' --no-daemon --console=plain` |
| **Evidence safety command** | `node -e "const fs=require('fs'); for (const line of fs.readFileSync('docs/evidence/manifests/phase10-v1-closeout.jsonl','utf8').trim().split(/\\n/)) JSON.parse(line);"` plus the Phase 10 forbidden-pattern scan. |

---

## Artifact Map

| Area | Requirement | Status | Artifacts | Verification |
|------|-------------|--------|-----------|--------------|
| Replay fixtures and full-chain replay test | PERF-04 | green | `fixtures/replay/README.md`, `fixtures/replay/udp-golden/mapped-session-001.hex`, `fixtures/replay/udp-golden/mapped-session-001.jsonl`, `fixtures/replay/expected/mapped-session-001-visualizer.json`, `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt`, `docs/evidence/manifests/phase10-replay-fixtures.jsonl` | `10-01-SUMMARY.md` verification. |
| Android diagnostics | PERF-05 | green | `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticEvent.kt`, `DiagnosticReporter.kt`, Android diagnostics tests, `DashboardState.kt` diagnostic rows | `10-03-SUMMARY.md` verification. |
| Desktop diagnostics and visualizer rendering | PERF-05 | green | `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt`, `VisualizerModel.kt`, `VisualizerWindow.kt`, desktop diagnostics tests | `10-02-SUMMARY.md` and `10-04-SUMMARY.md` verification. |
| Sanitized diagnostic export | PERF-05, PACK-04 | green | `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt`, `SecretRedactor.kt`, `.gitignore`, `docs/evidence/manifests/phase10-diagnostic-export.jsonl` | `10-05-SUMMARY.md` verification. |
| Android build and device workflow docs | PACK-01 | green | `docs/setup/android-build-device-testing.md`, `docs/setup/android-bluetooth-hid-gamepad.md`, `desktop-companion/src/test/kotlin/com/btgun/desktop/docs/Phase10DocsGuardTest.kt` | `10-06-SUMMARY.md` docs guard. |
| LAN protocol and security docs | PACK-04 | green | `docs/protocol/lan-session-security-v1.md`, `docs/protocol/lan-pairing-v1.md`, `docs/protocol/input-stream-v1-fixtures.md`, `docs/diagnostics/replay-and-troubleshooting.md` | `10-06-SUMMARY.md` docs guard. |
| Known limits docs and v1 index | PACK-05 | green | `docs/limits/v1-compatibility-limits.md`, `docs/v1.md` | `10-06-SUMMARY.md` docs guard and Plan 10-07 index scan. |
| Phase closeout manifest | PERF-04, PERF-05, PACK-01, PACK-04, PACK-05 | green | `docs/evidence/manifests/phase10-v1-closeout.jsonl` | Plan 10-07 JSONL parse, coverage grep, and evidence safety scan. |

---

## Source Audit

| Source | Coverage | Status |
|--------|----------|--------|
| PERF-04 | Packet logs replay through parser, receiver guard, Android-mapped product state, visualizer model, latency, and packet-loss output. | green |
| PERF-05 | Android and desktop diagnostics distinguish `gun_ble`, `sensor_motion`, `lan_control_udp`, `profile_mapping`, and `hid_backend_haptics`; export path is sanitized. | green |
| PACK-01 | Android build/toolchain, install, permission, USB capture, real gun, Android HID, LAN, and blocker workflow documented. | green |
| PACK-04 | LAN protocol/security docs cover pairing, proof, authenticated control, replay guard, lifecycle, haptics, diagnostics, fixtures, and evidence safety. | green |
| PACK-05 | v1 known limits matrix and index document unsupported, fallback, and deferred rows with evidence and next proof. | green |

---

## Decision Coverage

| Decision | Status | Artifact refs |
|----------|--------|---------------|
| D-01 replay uses raw UDP fixture hex plus sanitized session snapshots | green | `fixtures/replay/udp-golden/mapped-session-001.hex`, `mapped-session-001.jsonl` |
| D-02 first replay corpus stays small | green | `fixtures/replay/README.md`, `phase10-replay-fixtures.jsonl` |
| D-03 replay proves decode/authentication through mapped state and visualizer output | green | `ReplayFixtureTest.kt`, `mapped-session-001-visualizer.json` |
| D-04 fixtures live under `fixtures/replay/` and link sanitized provenance | green | `fixtures/replay/`, `phase10-replay-fixtures.jsonl` |
| D-05 diagnostics use five locked domain buckets | green | Android and desktop `DiagnosticEvent.kt`, `docs/diagnostics/replay-and-troubleshooting.md` |
| D-06 diagnostic schema includes stable fields | green | Android and desktop diagnostic schema tests |
| D-07 diagnostics render live and export sanitized bundle data | green | `DashboardState.kt`, `VisualizerWindow.kt`, `DiagnosticExport.kt` |
| D-08 diagnostic statuses are fixed | green | Android and desktop diagnostic enum tests |
| D-09 committed diagnostics/replay stay sanitized | green | `SecretRedactor.kt`, `phase10-diagnostic-export.jsonl`, closeout scan |
| D-10 committed artifacts ban secrets and full identifiers | green | `SecretRedactor.kt`, docs guard, Plan 10-07 forbidden scan |
| D-11 suffix/truncated refs allowed after redaction | green | `DiagnosticSessionRefs`, `DiagnosticExport.kt`, manifest refs |
| D-12 export bundle is replay-ready and excludes local capture dumps by default | green | `DiagnosticExport.kt`, `phase10-diagnostic-export.jsonl`, `.gitignore` |
| D-13 docs split into focused docs plus v1 index | green | `docs/setup/android-build-device-testing.md`, `docs/protocol/lan-session-security-v1.md`, `docs/diagnostics/replay-and-troubleshooting.md`, `docs/limits/v1-compatibility-limits.md`, `docs/v1.md` |
| D-14 docs serve developer/operator workflows first | green | `docs/v1.md`, setup/troubleshooting docs |
| D-15 Android setup docs include local build, install, USB, gun, HID, and blocker workflow | green | `docs/setup/android-build-device-testing.md` |
| D-16 LAN protocol/security docs stay contract-level | green | `docs/protocol/lan-session-security-v1.md` |
| D-17 known limits use direct compatibility matrix statuses | green | `docs/limits/v1-compatibility-limits.md` |
| D-18 required unsupported/fallback/deferred limit rows are present | green | `docs/limits/v1-compatibility-limits.md`, `docs/v1.md` |
| D-19 macOS Android Bluetooth HID and Windows VHF are equal primary v1 OS-visible paths | green | `docs/v1.md`, `docs/limits/v1-compatibility-limits.md` |
| D-20 unsupported/deferred rows include evidence and next proof | green | `docs/limits/v1-compatibility-limits.md` |

---

## Closeout Rows

| Row ID | Status | Covers | Artifact |
|--------|--------|--------|----------|
| `phase10-replay` | pass | PERF-04, D-01 through D-04 | `docs/evidence/manifests/phase10-v1-closeout.jsonl` |
| `phase10-diagnostics` | pass | PERF-05, D-05 through D-08 | `docs/evidence/manifests/phase10-v1-closeout.jsonl` |
| `phase10-android-docs` | pass | PACK-01, D-13 through D-15 | `docs/evidence/manifests/phase10-v1-closeout.jsonl` |
| `phase10-lan-security-docs` | pass | PACK-04, D-16 | `docs/evidence/manifests/phase10-v1-closeout.jsonl` |
| `phase10-known-limits` | pass | PACK-05, D-17 through D-20 | `docs/evidence/manifests/phase10-v1-closeout.jsonl` |
| `phase10-redaction` | pass | PERF-05, PACK-04, D-09 through D-12 | `docs/evidence/manifests/phase10-v1-closeout.jsonl` |

---

## Manual-Only Verifications

| Behavior | Requirement | Status | Test Instructions |
|----------|-------------|--------|-------------------|
| Android real-device diagnostic/export capture | PERF-05, PACK-01 | manual gate, docs ready | With a device attached, follow `docs/setup/android-build-device-testing.md`, export sanitized diagnostics, then commit only safe summaries/manifests. |
| Windows VHF target status evidence | PACK-05 | existing evidence linked | Use `docs/windows/test-signing-and-install.md` and Phase 6 manifests; do not claim new Windows proof unless target checklist reruns. |
| macOS Android HID haptics limitation | PACK-05 | existing limitation linked | Use Phase 7 evidence; only change after host-origin output proof reaches Android without breaking input. |

---

## Validation Sign-Off

- [x] All plans have automated verify steps or explicit hardware/manual gates.
- [x] Sampling continuity preserved through replay, diagnostics, export, docs guard, and closeout scans.
- [x] Replay, diagnostics, redaction, docs, known-limits, and closeout manifest artifacts exist.
- [x] No watch-mode flags in verification commands.
- [x] Redaction/forbidden-pattern scan covers committed closeout docs/manifests.
- [x] `nyquist_compliant: true` remains set in frontmatter.

**Approval:** ready for `$gsd-verify-work`
