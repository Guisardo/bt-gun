---
phase: 03
slug: lan-pairing-and-secure-session
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-07
---

# Phase 03 - Validation Strategy

Per-phase validation contract for feedback sampling during Phase 03 execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | Android Kotlin/Java unit tests through Gradle `testDebugUnitTest`; desktop JVM test harness not created yet. |
| Config file | `android-host/app/build.gradle.kts`; `desktop-companion/build.gradle.kts` must be created in Wave 0. |
| Quick run command | `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DashboardState*'` |
| Full suite command | `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest`; after Wave 0 also run `gradle -p desktop-companion test`. |
| Estimated runtime | Android focused tests under 30 seconds; full Android plus desktop suite TBD after desktop harness exists. |

## Sampling Rate

- After every task commit: run focused unit tests for changed Android or desktop module.
- After every plan wave: run full Android `testDebugUnitTest` plus desktop `test` once Wave 0 creates the desktop harness.
- Before `$gsd-verify-work`: Android full suite and desktop full suite must be green.
- Max feedback latency: under 60 seconds for focused checks after Wave 0.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-W0-01 | TBD | 0 | TRAN-01 | T-03-01 / T-03-02 | Desktop pairing session creates QR payload, 6-digit manual code, TTL, best IPv4 endpoint, and QR image without persisting one-time secrets. | unit | `gradle -p desktop-companion test --tests '*PairingSession*'` | no - W0 | pending |
| 03-W0-02 | TBD | 0 | TRAN-02 | T-03-05 / T-03-06 | Android parses QR/manual payloads, rejects stale or unreachable endpoint state clearly, and does not broaden normal pairing into LAN discovery. | unit + manual device QR smoke | `gradle -p android-host testDebugUnitTest --tests '*PairingPayload*'` | no - W0 | pending |
| 03-W0-03 | TBD | 0 | TRAN-03 | T-03-01 / T-03-02 / T-03-03 / T-03-04 | Pairing proof rejects expired, reused, wrong-code, wrong-fingerprint, and replayed-nonce attempts before trusted control messages are accepted. | unit + local integration | `gradle -p desktop-companion test --tests '*PairingSecurity*'` | no - W0 | pending |
| 03-W0-04 | TBD | 0 | TRAN-06 | T-03-04 / T-03-05 | WSS control channel sends session-ready, heartbeat ping/pong, diagnostics, minimal profile metadata, and a reserved haptic envelope type only. | unit + local integration | `gradle -p desktop-companion test --tests '*ControlChannel*'` plus Android control client tests | no - W0 | pending |

## Wave 0 Requirements

- [ ] `desktop-companion/build.gradle.kts` - JVM desktop test harness and dependency verification gates.
- [ ] `desktop-companion/src/test/kotlin/.../PairingSessionTest.kt` - pairing session TTL, manual code, endpoint, QR payload tests.
- [ ] `desktop-companion/src/test/kotlin/.../PairingSecurityTest.kt` - wrong-code, replay, single-use, rate-limit, fingerprint mismatch tests.
- [ ] `desktop-companion/src/test/kotlin/.../ControlChannelTest.kt` - envelope allowlist, heartbeat, diagnostics, profile metadata, and haptic reservation tests.
- [ ] `android-host/app/src/test/java/com/btgun/host/session/PairingPayloadTest.kt` - QR/manual parser and stale endpoint error tests.
- [ ] `android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt` - persisted fingerprint trust and mismatch behavior tests.
- [ ] `docs/protocol/lan-pairing-v1.md` - pairing URI, proof transcript, control envelope, and reserved haptic type contract.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android QR scan path pairs with the local desktop companion. | TRAN-02 | Camera/Google Code Scanner behavior and device Play services availability require a physical Android device. | Start desktop pairing session, scan QR from Android, confirm trusted desktop fingerprint prompt/state, and verify session reaches connected. |
| Manual fallback is visible and usable without scanning. | TRAN-01, TRAN-02 | User-facing fallback layout and entry errors require visual/device confirmation. | Start desktop pairing session, use Android manual entry for IP/port/code, confirm clear errors for wrong code/stale endpoint, then confirm success with correct code. |
| Heartbeat degrades and disconnects visibly. | TRAN-06 | Network interruption behavior needs real or simulated LAN disruption. | Pair Android and desktop, interrupt desktop or LAN, verify Android and desktop state move connected -> degraded -> disconnected without activating Packet stream. |

## Threat Model References

| Threat Ref | Threat | Required Mitigation |
|------------|--------|---------------------|
| T-03-01 | Rogue desktop impersonates trusted host. | Persist and pin desktop public-key fingerprint; fail closed on mismatch. |
| T-03-02 | Pairing code brute force. | 6-digit code only with short TTL, per-session attempt limit, and backoff or lockout. |
| T-03-03 | Replay of pair request. | Nonces, session id, HMAC transcript, single-use session material. |
| T-03-04 | Control message injection before auth. | No trusted control handling until WSS is established and pair proof passes. |
| T-03-05 | Oversized or unknown WebSocket message. | Set max frame/message size and reject unknown envelope versions/types. |
| T-03-06 | Secret leakage in logs or QR screenshots. | Redact QR secret, manual code, and proof; show only fingerprint suffix and session state in diagnostics. |

## Validation Sign-Off

- [ ] All tasks have automated verification or Wave 0 dependencies.
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify.
- [ ] Wave 0 covers all missing test references.
- [ ] No watch-mode flags.
- [ ] Feedback latency under 60 seconds after Wave 0.
- [ ] `nyquist_compliant: true` set in frontmatter after Wave 0 test harness exists and all mapped checks are green.

**Approval:** pending
