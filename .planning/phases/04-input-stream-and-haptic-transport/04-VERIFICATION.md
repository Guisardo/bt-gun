---
phase: 04-input-stream-and-haptic-transport
verified: 2026-06-08T23:49:10Z
status: gaps_found
score: 2/5 roadmap must-haves verified
overrides_applied: 0
gaps:
  - truth: "Desktop can safely receive high-rate Android UDP input from the live runtime and reject stale/replayed/malformed/wrong-session frames before apply."
    status: failed
    reason: "The receiver/parser exists and passes tests, but it is not wired into desktop runtime. Repo-wide search shows UdpInputReceiver is only constructed in tests; production desktop code has no DatagramSocket/DatagramChannel receive loop and ControlServer only emits input_stream_config."
    artifacts:
      - path: "desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt"
        issue: "Substantive receiver class exists, but no production caller feeds live UDP datagrams into handleDatagram."
      - path: "desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt"
        issue: "Creates fresh stream config, but does not start or own a UDP receiver/listener for that config."
    missing:
      - "Add a desktop UDP socket/listener owned by the trusted control session or companion runtime."
      - "Start UdpInputReceiver with the fresh input_stream_config and pass every datagram through handleDatagram before any state update."
      - "Stop/restart receiver state on control disconnect, reconnect, and session change."
  - truth: "Desktop can send haptic commands over the active reliable WSS control channel and receive acknowledgements."
    status: failed
    reason: "Desktop haptic command/result models and envelope builder exist, and Android can handle a trusted command, but ControlServer has no live send API, active socket/session handle, queue, CLI, UI, or harness that sends reserved_haptic_command over the open WSS connection."
    artifacts:
      - path: "desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt"
        issue: "hapticCommandEnvelopeFor builds an envelope, but the runtime websocket path only sends session_ready, input_stream_config, diagnostics/profile metadata, and heartbeat."
      - path: "desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt"
        issue: "Command body model exists, but it is not wired to an active desktop command source."
    missing:
      - "Expose a runtime sendHapticCommand path that targets the authenticated active WSS session."
      - "Record/route haptic_result acknowledgements back to desktop state or a testable callback."
      - "Provide at least a smoke harness or companion command surface for issuing Phase 4 phone haptic commands before visualizer work."
---

# Phase 4: Input Stream and Haptic Transport Verification Report

**Phase Goal:** Desktop can safely receive high-rate Android input while haptic commands travel back to the Android phone with acknowledgements.
**Verified:** 2026-06-08T23:49:10Z
**Status:** gaps_found
**Re-verification:** No - initial verification.

## User Flow Coverage

Mode is `mvp`, but ROADMAP goal is not user-story formatted (`As a ..., I want ..., so that ...`). `gsd-tools` was unavailable, so verification used roadmap success criteria plus PLAN must-haves.

| Step | Expected | Evidence | Status |
|---|---|---|---|
| Android receives trusted stream config | UDP starts only after `session_ready` and `input_stream_config` | `DesktopControlClient.kt:381-435`, `HostSessionService.kt:509-515`, `HostSessionService.kt:627-641` | VERIFIED |
| Android streams input | Snapshot/edge frames include sequence, session id, timestamps, buttons, axes, raw motion/provider flags | `AndroidUdpInputSender.kt:83-140`, `UdpInputFrameCodec.kt:87-131`, focused Android tests passed | VERIFIED |
| Desktop receives live UDP | Runtime desktop listener accepts UDP datagrams and feeds receiver before apply | No production `UdpInputReceiver` construction or desktop UDP receive loop; `rg` finds only tests | FAILED |
| Desktop sends haptic command | Active WSS session sends `reserved_haptic_command` to Android | `hapticCommandEnvelopeFor` exists, but no live send API/source in `ControlServer` | FAILED |
| Android haptic ack/fail | Android validates trusted command, starts phone pulse, returns `haptic_result` | `DesktopControlClient.kt:439-452`, `DesktopHapticCommand.kt:101-128`, focused Android tests passed | VERIFIED |
| Recovery | Old UDP input and stale haptics do not apply after disconnect/reconnect | Component tests pass, but live UDP/haptic paths are not wired, so runtime recovery is not end-to-end | FAILED |

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | Android streams versioned UDP input frames with sequence number, session id, timestamps, buttons, axes, motion payload, and provider/capability flags. | VERIFIED | `AndroidUdpInputSender.kt:121-140` builds fixed frames from live gun/motion state; `HostSessionService.kt:141-145` schedules snapshots and `HostSessionService.kt:1054-1090` sends edge frames. |
| 2 | Desktop can validate/authenticate, parse, and reject stale or replayed Android input frames from the live runtime. | FAILED | `InputReplayGuard.kt:27-68` and `UdpInputReceiver.kt:34-50` validate byte arrays, but production desktop code never constructs `UdpInputReceiver` or binds a UDP socket. |
| 3 | Desktop can send haptic commands with command id, strength, duration, TTL, and optional pattern. | FAILED | `HapticCommand.kt:11-34` and `ControlServer.kt:255-268` build command envelopes, but no active WSS send path exists. |
| 4 | Android vibrates phone for desktop haptic commands and returns ack/failure status. | VERIFIED | Android accepts trusted haptic commands after session gates and sends `haptic_result` in `DesktopControlClient.kt:439-452`; executor maps started/expired/unsupported/permission/failed in `DesktopHapticCommand.kt:101-128`. |
| 5 | Android and desktop recover from LAN disconnect without applying old input or playing stale haptics. | FAILED | Sender/receiver lifecycle classes have tests, but missing live desktop UDP receive and haptic send wiring prevents end-to-end runtime recovery. |

**Score:** 2/5 roadmap truths verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt` | Android binary UDP frame encoder/debug decoder | VERIFIED | Fixed 120-byte `BTGI` v1 frame with full HMAC tag and malformed-field rejection. |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt` | Desktop authenticator/parser/debug decoder | VERIFIED | Mirrored codec validates length, magic, version, type, stream id, HMAC, and field invariants. |
| `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` | Trusted-config-gated sender | VERIFIED | No send before `start(config)`; snapshots/edges share sequence and obey control-disconnect grace. |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` | Live desktop UDP receiver | ORPHANED | Substantive parser/receiver, but production desktop runtime never feeds it datagrams. |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | Fresh stream config and haptic command transport | PARTIAL | Fresh config sent after authentication; no UDP receiver ownership and no live haptic command send API. |
| `android-host/app/src/main/java/com/btgun/host/haptics/DesktopHapticCommand.kt` | Android command validation/execution/result mapping | VERIFIED | TTL, pattern unsupported, latest-valid-wins, explicit status mapping. |
| `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` | Phone pulse/cancel actuator | VERIFIED | Uses Android vibrator pulse/cancel with permission/runtime failure statuses. |
| `.planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md` | Physical smoke guide | PRESENT | Guide exists, but live desktop gaps block meaningful smoke execution. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `DesktopControlClient.kt` | `AndroidUdpInputSender.kt` | `onInputStreamConfigReceived` after `session_ready` | WIRED | Session gate at `DesktopControlClient.kt:381-399`; callback starts UDP at `HostSessionService.kt:509-515`. |
| `HostSessionService.kt` | normalized gun/motion state | snapshots and edge frames | WIRED | `sendUdpSnapshot` uses `gunInputState` and `lastMotionSample`; `handleGunEvent` calls `sendUdpEdge`. |
| `ControlServer.kt` | `UdpInputReceiver.kt` | authenticated session creates fresh config and live receiver | NOT_WIRED | Fresh config exists, but no production `UdpInputReceiver` construction or datagram loop. |
| `ControlServer.kt` | `DesktopControlClient.kt` | `reserved_haptic_command` over active WSS | NOT_WIRED | Envelope builder exists, but runtime websocket path does not send haptic commands. |
| `DesktopControlClient.kt` | `DesktopHapticCommand.kt` | authenticated command callback | WIRED | Trusted haptic command callback returns `HapticResult` and sends `haptic_result`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `AndroidUdpInputSender` | `UdpInputFrame` | `HostSessionService.currentState.gunInputState` + `lastMotionSample` | Yes | FLOWING |
| `UdpInputReceiver` | `UdpReceivedInput.current` | `handleDatagram(bytes, receivedElapsedNanos)` | No live source | HOLLOW - class only, no production datagram source |
| `DesktopHapticCommandExecutor` | `HapticResult` | trusted Android WSS command callback | Yes when command arrives | FLOWING on Android side |
| `ControlServer.hapticCommandEnvelopeFor` | `ControlEnvelope` | caller-provided `HapticCommand` | No live command source | HOLLOW - builder only |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Desktop codec/receiver/replay/haptic/control tests | `gradle -p desktop-companion test --tests '*UdpInputFrameCodecTest*' --tests '*UdpInputReceiverTest*' --tests '*InputReplayGuardTest*' --tests '*InputStreamLifecycleTest*' --tests '*HapticCommandCodecTest*' --tests '*ControlChannelTest*'` | Initial sandbox run failed with Gradle `SocketException: Operation not permitted`; escalated rerun passed in 4s. | PASS |
| Android codec/sender/lifecycle/haptic/control tests | `gradle -p android-host testDebugUnitTest --tests '*UdpInputFrameCodecTest*' --tests '*AndroidUdpInputSenderTest*' --tests '*InputStreamLifecycleTest*' --tests '*DesktopControlClientTest*' --tests '*DesktopHapticCommandTest*' --tests '*HostSessionServiceLivenessTest*'` | Initial sandbox run failed with Gradle `SocketException: Operation not permitted`; escalated rerun passed in 5s. | PASS |
| Production UDP receiver wiring | `rg -n "UdpInputReceiver|DatagramSocket|DatagramChannel|handleDatagram" desktop-companion/src/main/kotlin` | Only production receiver class definition plus endpoint-selection DatagramSocket; no runtime listener or caller. | FAIL |
| Production haptic send wiring | `rg -n "hapticCommandEnvelopeFor|sendHaptic|reserved_haptic_command" desktop-companion/src/main/kotlin` | Envelope builder only; no active send method/source. | FAIL |

### Probe Execution

| Probe | Command | Result | Status |
|---|---|---|---|
| Conventional probes | `find scripts -path '*/tests/probe-*.sh' -type f` | No probes found. | SKIPPED |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| ANDR-07 | 04-04, 04-05 | Android can receive desktop haptic command and vibrate phone | PARTIAL | Android handler/executor verified; desktop cannot yet send live command. |
| TRAN-04 | 04-02, 04-03 | Android streams high-rate input/motion over versioned UDP | PARTIAL | Android sends datagrams; desktop runtime cannot receive live datagrams. |
| TRAN-05 | 04-01, 04-02, 04-03 | UDP frame fields include seq/session/timestamps/buttons/axes/motion/provider flags | VERIFIED | Codec docs/tests and sender field mapping verified. |
| TRAN-07 | 04-04 | Desktop can send haptic command with command id, strength, duration, TTL, optional pattern | FAILED | Command model/envelope exists; no active WSS send path. |
| TRAN-08 | 04-04 | Android returns haptic ack/failure status | PARTIAL | Android returns result when command is injected; no live desktop send/ack loop. |
| TRAN-09 | 04-05 | Clean recovery from LAN disconnect without stale haptics | FAILED | Component lifecycle tests pass, but live UDP/haptic transport wiring missing. |
| DESK-01 | 04-01, 04-03, 04-05 | Desktop can receive, validate/authenticate, parse normalized Android input frames | FAILED | Parser/receiver class verified; live receive path missing. |
| PERF-03 | 04-01, 04-03, 04-05 | Desktop drops stale/replayed UDP instead of applying old input | PARTIAL | Replay guard drops duplicate/old/wrong/bad frames when called; no runtime UDP caller. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| None | - | Debt markers / placeholders / console-only impl | INFO | No unreferenced `TBD`, `FIXME`, or `XXX` blockers found in modified Phase 4 source. Null/default state matches existing lifecycle patterns and is not a stub by itself. |

### Human Verification Required After Gap Closure

1. **Physical input stream smoke**
   - Test: Follow `04-MANUAL-SMOKE.md` input stream section with Android phone, physical gun, and desktop companion.
   - Expected: desktop receiver state updates trigger/reload/X/Y/A/B/stick/raw motion live.
   - Why human: requires real Android device, LAN, and physical controls.

2. **Disconnect/reconnect smoke**
   - Test: Interrupt LAN/control during pressed input, wait past grace, reconnect with fresh config, replay old UDP if available.
   - Expected: grace then stale, active controls clear, aim/motion preserved as stale, old stream rejected.
   - Why human: requires live network interruption and end-to-end desktop state.

3. **Phone haptic smoke**
   - Test: Send valid, expired, and session-change haptic commands from desktop.
   - Expected: valid pulses once and returns `started`; expired returns `expired` without pulse; session change cancels active pulse; short disconnect does not cancel.
   - Why human: requires physical Android vibrator and live desktop command source after missing send path is added.

### Gaps Summary

Automated component tests are green, CR fixes are present, and the code review status is clean. However, goal-backward verification fails at runtime wiring:

- Desktop has no production UDP receive loop that feeds `UdpInputReceiver`, so Android can send frames but desktop companion cannot actually receive high-rate live input.
- Desktop has no active WSS haptic send API/source, so haptic commands cannot travel from desktop to Android in the running companion.

These are blockers for the Phase 4 goal and for requirements `TRAN-07`, `DESK-01`, and `TRAN-09`. Later phases consume this transport; they do not explicitly defer building these runtime transport links.

---

_Verified: 2026-06-08T23:49:10Z_
_Verifier: the agent (gsd-verifier)_
