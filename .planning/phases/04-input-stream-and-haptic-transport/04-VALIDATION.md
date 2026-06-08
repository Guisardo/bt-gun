---
phase: 04
slug: input-stream-and-haptic-transport
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 04 - Validation Strategy

Per-phase validation contract for feedback sampling during execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | Existing Kotlin/JVM and Android unit-test tasks with hand-run `main()` test classes. |
| Config file | `android-host/app/build.gradle.kts`, `desktop-companion/build.gradle.kts` |
| Quick run command | `gradle test` from `desktop-companion/` after local Gradle startup is repaired. |
| Full suite command | `gradle test` from `android-host/` and `gradle test` from `desktop-companion/` after local Gradle startup is repaired. |
| Estimated runtime | Unknown until Gradle startup issue is repaired. |

## Sampling Rate

- After every task commit: run the smallest changed-module test command once Gradle starts.
- After every plan wave: run `gradle test` in both `android-host/` and `desktop-companion/`.
- Before `$gsd-verify-work`: full Android and desktop test suites must be green, or the blocker must be documented.
- Max feedback latency: one task; no implementation task may mark done without either a passing targeted test or a documented Gradle blocker.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-W0-01 | TBD | 0 | TRAN-05 | T-04-01 | Android and desktop codecs agree on exact binary frame bytes and reject malformed version/length/type. | codec fixture | `gradle test --tests '*UdpInputFrameCodecTest*'` in both modules | no, W0 | pending |
| 04-W0-02 | TBD | 0 | PERF-03, DESK-01 | T-04-02 | Desktop rejects bad MAC, wrong stream id, duplicate sequence, old sequence, and age-expired frames before applying input. | unit | `gradle test --tests '*InputReplayGuardTest*'` from `desktop-companion/` | no, W0 | pending |
| 04-W0-03 | TBD | 0 | ANDR-07, TRAN-08 | T-04-03 | Android haptic command handling enforces TTL, strength/duration bounds, latest-valid-wins cancellation, and result statuses. | unit | `gradle test --tests '*DesktopHapticCommandTest*'` from `android-host/` | no, W0 | pending |
| 04-W0-04 | TBD | 0 | TRAN-04 | T-04-04 | Android sends snapshot frames and immediate edge frames only after trusted stream config. | unit + fake socket | `gradle test --tests '*AndroidUdpInputSenderTest*'` from `android-host/` | no, W0 | pending |
| 04-W0-05 | TBD | 0 | TRAN-07 | T-04-05 | Desktop haptic command body includes command id, strength, duration, TTL, and optional pattern fields. | unit | `gradle test --tests '*HapticCommandCodecTest*'` from `desktop-companion/` | no, W0 | pending |
| 04-W0-06 | TBD | 0 | TRAN-09 | T-04-06 | Session change cancels active phone haptic and stale UDP sessions cannot apply old input after reconnect. | state-machine unit | `gradle test --tests '*InputStreamLifecycleTest*'` in both modules | no, W0 | pending |
| 04-W0-07 | TBD | 0 | TRAN-04, DESK-01 | T-04-07 | Local Gradle startup is repaired or a documented workaround exists before any automated test command is trusted. | tooling check | `gradle --version` and targeted `gradle test` commands | no, W0 | pending |

Status: pending, green, red, flaky.

## Wave 0 Requirements

- [ ] `docs/protocol/lan-pairing-v1.md` documents `input_stream_config`, UDP binary frame schema, haptic command, and haptic result.
- [ ] `android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt` exists and validates golden frame encode/decode.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt` exists and validates the same golden bytes.
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt` covers duplicate, old, wrong-session, bad-MAC, and age-expired rejection.
- [ ] `android-host/app/src/test/java/com/btgun/host/haptics/DesktopHapticCommandTest.kt` covers TTL, result statuses, duration/strength bounds, and latest-valid-wins cancellation.
- [ ] Gradle startup is repaired or an execution-time blocker/workaround is documented before automated validation is considered runnable.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android phone vibrates from a desktop-origin haptic command over trusted LAN control. | ANDR-07, TRAN-07, TRAN-08 | Unit tests can verify command handling, but real vibrator hardware and LAN control need device confirmation. | Pair Android and desktop, send one valid haptic command from desktop, confirm one phone pulse and `started` result, then send an expired command and confirm no pulse plus `expired` result. |
| LAN disconnect/reconnect does not apply stale UDP input or play stale haptics. | TRAN-09, PERF-03 | Real network disconnect timing is device/environment dependent. | Start stream, press and hold one control, interrupt LAN/control channel, confirm pressed controls clear while aim remains last-known/stale, reconnect with new session, confirm old UDP frames and old haptic commands are rejected. |

## Validation Sign-Off

- [ ] All tasks have automated verify commands or Wave 0 dependencies.
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify.
- [ ] Wave 0 covers all missing references.
- [ ] No watch-mode flags.
- [ ] Feedback latency is bounded to one task.
- [ ] `nyquist_compliant: true` set in frontmatter after Wave 0 coverage exists and passes.

Approval: pending
