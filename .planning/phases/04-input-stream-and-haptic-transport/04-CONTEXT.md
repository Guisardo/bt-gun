# Phase 4: Input Stream and Haptic Transport - Context

**Gathered:** 2026-06-08T17:37:53Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 builds the first real Android-to-desktop runtime transport after Phase 3 pairing. Android streams high-rate gun and motion input to the trusted desktop over authenticated UDP frames, while reliable control messages carry desktop-to-Android phone haptic commands and Android ack/fail responses. Desktop can validate, authenticate/decrypt, parse, and reject stale or replayed input frames.

This phase does not build virtual joystick backends, final desktop profiles, profile editing, visualizer UI, visualizer latency dashboards, OS driver output reports, direct desktop-to-gun Bluetooth, or physical gun motor rumble.

</domain>

<decisions>
## Implementation Decisions

### UDP Input Frames
- **D-01:** Phase 4 uses binary UDP input frames plus a debug decoder. JSON remains appropriate for control/debug docs, not the high-rate input stream.
- **D-02:** UDP input uses a hybrid model: 60 Hz state snapshots plus immediate control-edge frames for trigger, reload, X/Y/A/B, and stick/button changes.
- **D-03:** Snapshot frames carry current button bitmask, stick axes, raw normalized motion, provider/capability flags, session identity, sequence number, capture timestamp, and send timestamp.
- **D-04:** Motion payload is raw normalized motion only: provider, capability flags, raw aim/yaw/pitch/roll, and timestamps. Do not include Android preview aim as product mapping. Desktop profiles own final aim mapping.
- **D-05:** Edge frames are for faster button/control response, not authoritative long-term state. Snapshot frames remain the recovery source after loss.

### Phone Haptics
- **D-06:** Phase 4 turns the Phase 3 reserved haptic type into a pulse-first haptic command over the reliable control channel.
- **D-07:** Haptic command body includes command id, strength, duration, and TTL/play deadline. Optional pattern fields may be reserved for compatibility, but full pattern behavior is not required in Phase 4.
- **D-08:** Latest valid haptic command wins. Android cancels any active phone vibration before starting the new valid command.
- **D-09:** Android returns ack/fail after accepting the command and attempting to start vibration, not after the vibration duration ends.
- **D-10:** Haptic result status must distinguish at least `started`, `expired`, `unsupported`, `permission_blocked`, `failed`, and `cancelled`.
- **D-11:** If the haptic command is expired before Android can start it, Android must not vibrate the phone and must return `expired`.

### Recovery and Replay Handling
- **D-12:** Desktop accepts UDP input only for the expected trusted session and rejects wrong-session, duplicate-sequence, old-sequence, and age-expired frames.
- **D-13:** Input stream timeout clears active buttons/pressed controls only. Aim remains last-known rather than being zeroed, but downstream status should make the stream stale/timeout state visible.
- **D-14:** Active phone haptic is cancelled on session change. A short control-channel disconnect does not automatically cancel a currently playing pulse.
- **D-15:** UDP may continue for a brief grace window after reliable control disconnect, but new or changed sessions require fresh authenticated control before input is trusted again.

### the agent's Discretion
- Choose exact binary field layout, field widths, byte order, AEAD/nonce construction, replay-window size, frame age limit, snapshot jitter tolerance, and UDP grace duration during planning.
- Choose exact haptic strength scale, default duration caps, TTL defaults, command/result envelope names, and cancellation result details as long as the locked behavior above is preserved.
- Choose whether Phase 4 writes one shared protocol module or keeps mirrored Android/desktop codecs, provided tests prove both sides stay wire-compatible.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 4 goal, requirements, success criteria, dependencies, and later-phase boundaries.
- `.planning/REQUIREMENTS.md` - `ANDR-07`, `TRAN-04`, `TRAN-05`, `TRAN-07`, `TRAN-08`, `TRAN-09`, `DESK-01`, and `PERF-03`.
- `.planning/PROJECT.md` - LAN transport constraint, desktop-profile ownership of aim mapping, phone haptic v1 decision, and platform portability constraints.
- `.planning/STATE.md` - current state after Phase 3 and concerns about preserving secure-session boundaries.

### Research Context
- `.planning/research/STACK.md` - UDP plus reliable control-channel stack direction, binary packet preference, foreground active-session constraint, and authenticated-packet requirement.
- `.planning/research/ARCHITECTURE.md` - UDP/control split, Android normalized event boundary, desktop receiver boundary, and virtual-controller deferral.
- `.planning/research/PITFALLS.md` - UDP packet sizing, replay protection, stale haptics, TTL/ack, and timeout pitfalls.
- `.planning/research/FEATURES.md` - dependency notes for LAN pairing, UDP input, haptics, profiles, and visualizer.
- `.planning/research/SUMMARY.md` - milestone sequencing and transport/security rationale.

### Prior Phase Context
- `.planning/phases/03-lan-pairing-and-secure-session/03-CONTEXT.md` - authenticated WSS control channel, reserved haptic type, heartbeat/liveness, and Phase 4 boundary.
- `.planning/phases/02-android-host-live-input/02-CONTEXT.md` - Android foreground service ownership, separate gun/motion/status streams, and desktop-profile mapping boundary.
- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md` - v1 phone haptic decision and physical gun motor deferral.

### Protocol Docs
- `docs/protocol/lan-pairing-v1.md` - Phase 3 reliable-control envelope, trust/session semantics, diagnostics boundaries, and reserved haptic command rule that Phase 4 must evolve.

### Android Host Integration
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - live envelope shape, per-stream sequencing, gun state, and motion sample fields to serialize into transport frames.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - foreground session owner, gun/motion/recenter state, desktop control ownership, and current live-state update points.
- `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` - Android copy of the Phase 3 control envelope and reserved haptic type.
- `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` - WSS client, session-ready handling, heartbeat/liveness, diagnostics/profile parsing, and send gate.
- `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` - current local phone vibration wrapper to extend for desktop-origin commands.
- `android-host/app/src/main/java/com/btgun/host/session/DesktopLinkState.kt` - desktop link state labels and liveness states used by dashboard/status.
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - packet-stream placeholder and phone-haptic status surface to activate.

### Desktop Companion Integration
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` - desktop copy of the reliable-control envelope and reserved haptic type.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - authenticated WSS server, trusted-session gate, heartbeat, diagnostics, and control-envelope acceptance point.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/HeartbeatMonitor.kt` - existing connected/degraded/disconnected timing pattern.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` - desktop control-channel behavior tests to extend for haptic payload and UDP/session gates.
- `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt` - Android control-client behavior tests to extend for haptic ack/fail and reserved-type removal.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LiveEnvelope`, `StreamSequencer`, `GunInputState`, and `MotionSample` already provide timestamped normalized state for gun and motion input.
- `HostSessionService` already owns BLE gun state, motion capture, reload recenter, desktop control client lifecycle, and foreground-session visibility.
- Android and desktop both have mirrored `ControlEnvelope` codecs with strict type allowlists and reserved haptic rejection.
- `ControlServer` already gates trusted control messages behind pairing proof/manual auth and session id validation.
- `PhoneHaptics` already wraps one-shot Android phone vibration and status results for local testing.

### Established Patterns
- Android owns physical gun/motion capture; desktop owns final mapping and virtual-controller behavior.
- Product state is visible in the dashboard; debug/provenance remains explicit.
- Phase 3 control is reliable WSS over pinned desktop identity. Phase 4 should reuse that channel for haptics and diagnostics instead of inventing a second reliable path.
- Current protocol code is duplicated between Android and desktop. Phase 4 must either centralize or test wire compatibility carefully.
- Packet stream remains inactive in UI today, so Phase 4 should activate it without pulling in Phase 9 visualizer metrics.

### Integration Points
- Add Android transport code under the foreground `HostSessionService` boundary so background behavior does not bypass the visible active session.
- Add desktop UDP receiver/parser behind the existing trusted-session lifecycle and expose parsed normalized input without virtual joystick assumptions.
- Update both Android and desktop control envelopes so haptic command/result bodies are allowed only in Phase 4's defined shape.
- Extend protocol docs and tests before hardware/manual smoke so binary frames and haptic outcomes are replayable without a live gun.

</code_context>

<specifics>
## Specific Ideas

- Use hybrid UDP frames: 60 Hz snapshots plus immediate control edges.
- Keep motion payload raw and provider-aware. Do not let Android preview aim become final product mapping.
- Use a binary frame with debug decoder and fixture-style tests.
- Use pulse-first phone haptics, latest valid command wins, and ack/fail after vibration start attempt.
- Strictly reject stale/replayed UDP frames, but allow a brief UDP grace after control disconnect.
- On stream timeout, clear active buttons only; aim remains last-known and should be marked stale.

</specifics>

<deferred>
## Deferred Ideas

- Full haptic pattern playback remains deferred beyond Phase 4 unless planner can include it without risk to pulse-first behavior.
- Physical gun motor rumble remains deferred/v2.
- Virtual joystick backends remain Phase 5 through Phase 7.
- Desktop profile mapping and tuning remain Phase 8.
- Visualizer latency dashboards, packet-loss UI, and full replay diagnostics remain Phase 9 or Phase 10 as mapped.

</deferred>

---

*Phase: 4-Input Stream and Haptic Transport*
*Context gathered: 2026-06-08T17:37:53Z*
