---
phase: "02"
slug: android-host-live-input
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-06
---

# Phase 02 - Validation Strategy

> Per-phase validation contract for Android host live-input execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android Gradle local unit tests plus existing Node fixture validator |
| **Config file** | `android-host/app/build.gradle.kts` to be created in Wave 0 |
| **Quick run command** | `node tools/phase1/validate-fixtures.mjs --full && ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` |
| **Full suite command** | `node tools/phase1/validate-fixtures.mjs --full && ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug` |
| **Estimated runtime** | ~60 seconds after Gradle cache warmup |

---

## Sampling Rate

- **After every task commit:** Run the focused unit test named by the task plus `node tools/phase1/validate-fixtures.mjs --full` when parser or fixture mappings change.
- **After every plan wave:** Run the full suite command.
- **Before `$gsd-verify-work`:** Full suite must be green and manual physical-device checks must be recorded.
- **Max feedback latency:** 90 seconds for automated checks after cache warmup.

---

## Per-Requirement Verification Map

| Req ID | Behavior | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|--------|----------|------------|-----------------|-----------|-------------------|-------------|--------|
| ANDR-01 | Permission gate computes Bluetooth scan/connect, legacy location, LAN placeholder, vibration, and sensor capability states by SDK. | T-02-01 | Missing permission or capability is surfaced as blocked state; no silent scan/connect failure. | unit + manual | `gradle -p android-host testDebugUnitTest --tests '*PermissionGate*'` | no - Wave 0 | pending |
| ANDR-02 | BLE adapter scans `ARGunGame`/`fff0`, connects, discovers GATT, enables `fff3` notifications, and reports reconnect/errors. | T-02-02 | Unknown devices are ignored; reconnect is bounded and visible. | unit + manual hardware | `gradle -p android-host testDebugUnitTest --tests '*BleGunAdapter*'` | no - Wave 0 | pending |
| ANDR-03 | Parser maps Phase 1 fixture payloads to trigger, reload, stick, X/Y/A/B, and connection/status events with optional provenance. | T-02-03 | Unknown `fff3` payloads become debug/status events, not product controls. | unit | `gradle -p android-host testDebugUnitTest --tests '*IpegaPacketParser*'` | no - Wave 0 | pending |
| ANDR-04 | Motion provider selection chooses game rotation vector, rotation vector, gyro plus gravity/accelerometer, then tilt fallback, with monotonic timestamps. | T-02-04 | Provider capability flags are explicit; unavailable motion does not fake aim. | unit + manual device | `gradle -p android-host testDebugUnitTest --tests '*MotionProviderSelection*'` | no - Wave 0 | pending |
| ANDR-05 | Gun, motion, and status samples use common envelopes with stream, per-stream seq, capture elapsed nanos, emitted elapsed nanos, provider metadata, and capability metadata. | T-02-05 | Stream ordering uses monotonic time, not wall-clock time. | unit | `gradle -p android-host testDebugUnitTest --tests '*NormalizedEventEnvelope*'` | no - Wave 0 | pending |
| ANDR-06 | Reload hold emits reload down immediately, recenter after 2 seconds while still held, and reload up on release. | T-02-06 | Recenter does not consume or suppress normal reload semantics. | unit + manual hardware | `gradle -p android-host testDebugUnitTest --tests '*ReloadHoldRecenter*'` | no - Wave 0 | pending |
| ANDR-08 | Dashboard state exposes gun connection, inactive desktop link, inactive packet stream, motion provider, recenter state, foreground service, error line, and phone haptic local-test status. | T-02-07 | Desktop/LAN/haptic features not built in Phase 2 remain explicit placeholders, not simulated success. | unit + manual UI | `gradle -p android-host testDebugUnitTest --tests '*DashboardState*'` | no - Wave 0 | pending |

---

## Wave 0 Requirements

- [ ] `android-host/settings.gradle.kts` and `android-host/build.gradle.kts` reuse approved Android Gradle Plugin and Kotlin versions from `android-diagnostic/`.
- [ ] `android-host/app/build.gradle.kts` exposes `testDebugUnitTest` and `lintDebug`.
- [ ] `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt` covers Phase 1 normalized fixtures.
- [ ] `android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt` covers hold/release timing.
- [ ] `android-host/app/src/test/java/com/btgun/host/motion/MotionProviderSelectionTest.kt` covers provider fallback order.
- [ ] `android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt` covers per-stream seq and timestamps.
- [ ] `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` covers Phase 2 dashboard placeholders and visible state.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android permission grant flow | ANDR-01 | Runtime permission UI and OEM settings behavior need real device. | Install host app, start session, grant Bluetooth/location/network/vibration-related requests, confirm dashboard shows unblocked state. |
| Physical iPega BLE connect | ANDR-02 | Requires real gun advertisement and GATT behavior. | Power gun, start live session, confirm scan finds `ARGunGame`, connects to `fff0`, subscribes to `fff3`, and shows connected state. |
| Physical controls | ANDR-03 | Fixture tests prove parser, but real BLE timing/noise needs device. | Press trigger, reload, stick directions, X/Y/A/B; verify product events and optional provenance. |
| Motion preview orientation | ANDR-04, ANDR-05 | Physical phone mounting orientation determines preview transform quality. | Move phone/gun through yaw/pitch directions, confirm aim dot direction and provider label; record fallback behavior if gyro unavailable. |
| Reload recenter gesture | ANDR-06 | Timing and UI feedback need end-to-end device feel. | Press reload briefly, verify reload down/up only; hold for 2 seconds, verify recenter emitted and reload up still appears on release. |
| Foreground service survival | ANDR-02, ANDR-08 | Background/screen-change behavior cannot be fully proven in JVM tests. | Start live session, switch apps or lock/unlock screen, confirm foreground notification and reconnection/status behavior. |
| Local phone haptic test | ANDR-08 | Physical vibration capability/result needs phone hardware. | Tap `Test phone vibration`, confirm phone vibrates and haptic status updates; do not test desktop-origin haptics in Phase 2. |

---

## Validation Sign-Off

- [x] All Phase 2 requirements have automated or manual verification coverage.
- [x] Wave 0 defines missing test infrastructure before feature implementation.
- [x] No 3 consecutive implementation tasks should lack automated verification once Wave 0 exists.
- [x] No watch-mode flags in validation commands.
- [x] Feedback latency target is under 90 seconds after Gradle cache warmup.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** draft 2026-06-06
