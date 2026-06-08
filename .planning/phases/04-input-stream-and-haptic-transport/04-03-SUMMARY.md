---
phase: 04-input-stream-and-haptic-transport
plan: 03
subsystem: transport
tags: [kotlin, desktop, udp, hmac, replay-guard, tdd]

requires:
  - phase: 04-input-stream-and-haptic-transport
    provides: fixed UDP input frame codec and Android trusted UDP sender from Plans 04-01 and 04-02
provides:
  - Trusted desktop input_stream_config control envelope after authenticated session acceptance
  - Fresh per-session UDP stream id and HMAC key generation
  - Desktop UDP datagram authentication, replay rejection, and raw input parsing
  - Timeout recovery state that clears active controls while preserving last raw motion
affects: [desktop-receiver, input-stream, haptic-transport, phase-05-backend-contract]

tech-stack:
  added: []
  patterns:
    - Proof-gated control creates fresh stream config for UDP trust
    - Receiver validates HMAC/session/age/sequence before applying input
    - Raw received input state stays separate from later virtual joystick/profile mapping

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputReceiverTest.kt
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt

key-decisions:
  - "Desktop stream trust starts only after authenticated control-session acceptance."
  - "InputReplayGuard tracks one highest accepted sequence per stream session and rejects late edge frames after newer snapshots."
  - "UdpReceivedInput exposes raw motion/provider/axis fields only; desktop profile and virtual joystick mapping remain deferred."

patterns-established:
  - "ControlServer emits input_stream_config immediately after session_ready and before initial diagnostics/profile metadata."
  - "UDP receiver tests use the Plan 04-01 frame codec to prove real HMAC and fixture-compatible parsing."
  - "Timeout recovery preserves raw aim/motion while marking the state stale and clearing active controls."

requirements-completed:
  - TRAN-04
  - TRAN-05
  - DESK-01
  - PERF-03

duration: 44 min
completed: 2026-06-08
---

# Phase 04 Plan 03: Desktop Authenticated UDP Receiver Summary

**Proof-gated desktop UDP stream config plus replay-safe raw input receiver for Android frames**

## Performance

- **Duration:** 44 min across RED and resumed GREEN execution
- **Started:** 2026-06-08T18:43:31Z
- **Completed:** 2026-06-08T19:27:00Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added desktop RED tests for trusted stream config emission, replay rejection, wrong stream/session rejection, bad HMAC rejection, age expiry, late edge rejection, timeout stale state, and raw-output boundary.
- Added `ControlMessageType.INPUT_STREAM_CONFIG` and proof-gated `ControlServer` stream config emission with fresh 16-byte stream id and 32-byte HMAC key material.
- Implemented `InputReplayGuard`, `UdpInputReceiver`, and `UdpReceivedInput` so desktop datagrams authenticate/parse through the 04-01 codec before any state apply.
- Preserved raw motion/provider/aim fields for later backend/profile work without adding virtual joystick, HID, visualizer, or profile mapping behavior.

## Task Commits

1. **Task 1: RED - add desktop receiver and replay guard tests** - `407e3ce` (test)
2. **Task 2: GREEN - implement authenticated desktop receiver and stream config send path** - `0f2a01a` (feat)

## Files Created/Modified

- `desktop-companion/build.gradle.kts` - Registers desktop receiver and replay guard main-style tests.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` - Extends control-channel tests for `input_stream_config` allowlist and fresh trusted config body.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt` - Covers accepted sequence, duplicate/old/wrong-session/wrong-stream/bad-MAC/malformed/age-expired rejection, late edge rejection, and timeout stale state.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputReceiverTest.kt` - Covers receiver parsing, reject-before-apply behavior, snapshot repair, timeout behavior, and raw-output field boundary.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` - Adds `input_stream_config` control type to the strict allowlist.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - Emits fresh stream config after trusted session authentication and before initial metadata.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt` - Tracks highest sequence and rejects wrong/stale/replayed datagrams before apply.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - Starts/stops trusted receiver state and exposes accepted/stale raw input callbacks.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` - Defines raw received input and motion state for later desktop backend work.

## Decisions Made

- Desktop stream id/key generation uses fresh random bytes per config envelope and never derives UDP stream trust from QR/manual pairing material.
- Replay handling uses one monotonic sequence across snapshot and edge frames, matching the Android sender from Plan 04-02.
- Timeout clears buttons, pressed controls, and stick axes while keeping last raw motion/aim fields and `stale=true`.

## Deviations from Plan

None - plan executed as written after resuming the interrupted GREEN state.

## Issues Encountered

- Prior executor disconnected after the RED commit and partial GREEN files. Resume treated `407e3ce` as complete, preserved existing worktree changes, and continued Task 2 without reverting.
- Sandboxed Gradle startup failed with `java.net.SocketException: Operation not permitted`; unsandboxed Gradle with the documented Phase 04 workaround passed.
- The exact focused Gradle command returned `BUILD SUCCESSFUL` but all tasks were up-to-date, so `--rerun-tasks` was also run to prove real test execution.

## Verification

- Exact focused command: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*InputReplayGuard*' --tests '*UdpInputReceiver*' --tests '*ControlChannel*'` - passed.
- Executed focused command: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --rerun-tasks --tests '*InputReplayGuard*' --tests '*UdpInputReceiver*' --tests '*ControlChannel*'` - passed.
- Boundary grep: `! rg -n "VirtualHID|virtual joystick|HID|profile mapping|ProfileMapper|visualizer latency|packet loss dashboard|aimX|aimY|qr_secret|manual code|HMAC key" desktop-companion/src/main/kotlin/com/btgun/desktop/transport desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - passed.
- Secret/diagnostic scan found no logging of stream key, QR secret, manual code, proof, or HMAC key material. The stream config body intentionally carries `streamSessionIdHex` and `hmacSha256KeyBase64Url` to the authenticated Android control client.
- TDD gate check: RED commit `407e3ce` exists and GREEN commit `0f2a01a` follows it.

## TDD Gate Compliance

- RED commit exists: `407e3ce`
- GREEN commit exists after RED: `0f2a01a`
- Refactor commit: not needed

## Known Stubs

None. Nullable receiver/control fields are active lifecycle state, not placeholder or mock data.

## Threat Flags

None. New stream-config, UDP datagram, replay, and timeout surfaces are covered by plan threats T-04-09 through T-04-13 and verified by focused tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04-04 can add reliable desktop-to-Android haptic commands using the same trusted control channel. Later Phase 5 backend work can consume `UdpReceivedInput` without inheriting profile, HID, or visualizer assumptions.

## Self-Check: PASSED

- Summary file exists.
- `InputReplayGuard.kt`, `UdpInputReceiver.kt`, and `UdpReceivedInput.kt` exist.
- Task commits `407e3ce` and `0f2a01a` exist in git history.
- No tracked file deletions occurred in task commits.

---
*Phase: 04-input-stream-and-haptic-transport*
*Completed: 2026-06-08*
