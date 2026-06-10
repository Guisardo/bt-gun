---
phase: 07
slug: macos-virtual-joystick-path
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-10
---

# Phase 07 — Validation Strategy

> Per-phase validation contract for macOS virtual joystick execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin/JVM tests through `desktop-companion/build.gradle.kts`, plus native macOS helper smoke/proof commands |
| **Config file** | `desktop-companion/build.gradle.kts`, `desktop-companion/settings.gradle.kts`, native helper build files created during Phase 07 |
| **Quick run command** | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*Macos*'` |
| **Full suite command** | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test` |
| **Estimated runtime** | ~60-180 seconds for Kotlin tests; live CoreHID/proof commands are hardware/manual gated |

---

## Sampling Rate

- **After every task commit:** Run `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*Macos*'` when macOS/backend code exists.
- **After every plan wave:** Run `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test` and the relevant `smokeDesktopBackendMacos*` task.
- **Before `$gsd-verify-work`:** Full desktop tests, CoreHID or fallback live smoke, CLI enumeration artifact, OS-output-to-phone-haptic proof, live Android/gun stream visual proof, and PACK-03 docs complete.
- **Max feedback latency:** 3 minutes for automated tests; manual/live proof recorded as explicit checkpoint evidence.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | PACK-03 | T-07-01 / T-07-02 | Local helper setup documents exact toolchain, signing, entitlement, and fallback gates without committing secrets | docs/static | `rg -n 'CoreHID|HIDVirtualDevice|com.apple.developer.hid.virtual.device|HIDDriverKit|system extension|hidutil|ioreg' docs/setup/macos-virtual-hid.md` | no W0 | pending |
| 07-01-02 | 01 | 1 | DESK-03 | T-07-01 / T-07-02 | Minimal native helper creates or fails closed before backend claims OS-visible support | native smoke | helper build plus `hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'` | no W0 | pending |
| 07-02-01 | 02 | 2 | DESK-03 | T-07-03 | Semantic state maps to descriptor-compatible macOS input report bytes with bounded axis/button values | unit | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*MacosHidReportPacker*'` | no W0 | pending |
| 07-02-02 | 02 | 2 | DESK-06 | T-07-04 / T-07-05 | Output report bytes validate report id, version, length, reserved bytes, duration, TTL, and strength before haptic command creation | unit | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*MacosOutputReportMapper*'` | no W0 | pending |
| 07-03-01 | 03 | 3 | DESK-03 | T-07-01 / T-07-03 | Backend lifecycle publishes only through the local helper boundary and reports capabilities honestly | integration | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*MacosVirtualControllerBackend*'` | no W0 | pending |
| 07-03-02 | 03 | 3 | DESK-06 | T-07-04 / T-07-05 | Simulated output remains mapper-only and cannot be counted as OS-origin proof | integration | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*Macos*'` | no W0 | pending |
| 07-04-01 | 04 | 4 | DESK-03 | T-07-03 | Runtime preserves existing UDP callback, applies stale behavior, and exposes diagnostics | integration | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*MacosBackendRuntime*'` | no W0 | pending |
| 07-04-02 | 04 | 4 | DESK-06 | T-07-04 / T-07-05 | OS/HID-origin output report routes to existing authenticated phone haptic command path | live/integration | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle smokeDesktopBackendMacosCoreHid -Pbtgun.smoke.haptic=true` | no W0 | pending |
| 07-05-01 | 05 | 5 | DESK-03 | T-07-01 / T-07-03 | Live Android/gun stream moves macOS-visible joystick axes/buttons and evidence is sanitized | manual/live | `hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'` plus user/agent visual confirmation | no W0 | pending |
| 07-05-02 | 05 | 5 | DESK-06 | T-07-04 / T-07-05 | Phase does not pass unless macOS-origin output/rumble reaches Android phone haptic or DriverKit fallback proof replaces failed CoreHID path | manual/live | `rg -n 'macos-output-report|phone-haptic|approved' docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` | no W0 | pending |

*Status: pending, green, red, flaky.*

---

## Wave 0 Requirements

- [ ] `docs/setup/macos-virtual-hid.md` — records CoreHID, entitlement, signing, CLI proof, and DriverKit fallback setup.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPackerTest.kt` — covers DESK-03 report packing.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapperTest.kt` — covers DESK-06 output report validation.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackendTest.kt` — covers lifecycle/capabilities before OS-visible support is claimed.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt` — covers callback preservation, stale behavior, diagnostics, and output haptic routing.
- [ ] Native helper minimal build/proof command — covers CoreHID or IOHIDUserDevice compile, signing, entitlement, launch, and enumeration.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| macOS UI/tester shows virtual joystick axes/buttons moving from live Android/gun stream | DESK-03 | Phase 07 pass requires user-visible macOS proof and live physical input | Pair Android/gun to desktop, run macOS backend, open selected macOS tester/UI, move gun controls/aim, capture sanitized evidence and user confirmation. |
| macOS-origin output/rumble causes Android phone haptic | DESK-06 | OS/HID set-report proof and phone vibration confirmation require target machine plus paired Android session | Run output probe or selected macOS tester, verify helper receives output report, Kotlin routes haptic command, Android phone vibrates, and manifest records sanitized result. |
| DriverKit fallback approval if CoreHID fails | DESK-03, DESK-06, PACK-03 | System extension approval and entitlement behavior depend on the local macOS/account environment | Only execute if CoreHID gate fails; record Xcode/signing/approval steps, System Settings prompts, and final pass/fail evidence. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies.
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify.
- [ ] Wave 0 covers all missing references.
- [ ] No watch-mode flags.
- [ ] Feedback latency < 3 minutes for automated checks.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** pending
