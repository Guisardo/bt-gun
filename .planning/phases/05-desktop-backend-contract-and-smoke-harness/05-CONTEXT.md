# Phase 5: Desktop Backend Contract and Smoke Harness - Context

**Gathered:** 2026-06-09T17:25:09Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 creates the shared desktop backend contract and smoke harness that future Windows and macOS virtual joystick implementations must satisfy. It defines a platform-neutral normalized controller backend, a first UDP-to-controller-state adapter, a regular gamepad-like descriptor contract, structured backend capabilities, and platform stub smoke commands that must run on both macOS and Windows before real OS-visible virtual HID work starts.

This phase does not build production Windows VHF/KMDF driver code, production macOS CoreHID/HIDDriverKit code, desktop profile editing, full aim tuning, final visualizer UI, game-specific presets, direct desktop-to-gun Bluetooth, or physical gun motor rumble.

</domain>

<decisions>
## Implementation Decisions

### Backend Contract
- **D-01:** Phase 5 uses a normalized controller backend contract. It consumes stable semantic controller state and hides Windows/macOS implementation details behind later platform adapters.
- **D-02:** Phase 5 includes a small adapter from `UdpReceivedInput` to normalized controller state so the Phase 4 receiver handoff is proven now. Do not add configurable profile mapping in Phase 5.
- **D-03:** Controller state uses named semantic controls: `trigger`, `reload`, `x`, `y`, `a`, `b`, `stickX`, `stickY`, `aimX`, and `aimY`. Backend adapters decide HID report packing later.
- **D-04:** The backend surface exposes current controller state, backend capabilities, lifecycle, and the last publish/error result.

### Fake Smoke Harness
- **D-05:** Phase 5 uses separate macOS and Windows platform stub commands. These stubs do not need to create OS-visible virtual HID devices, but they must catch platform command wiring and backend contract assumptions before Phase 6/7.
- **D-06:** The smoke harness feeds replayed UDP fixtures through the receiver path rather than only publishing direct semantic frames.
- **D-07:** Phase 5 is not accepted unless smoke commands run on both macOS and Windows.
- **D-08:** Smoke evidence should be emitted as JUnit-style test output.

### Gamepad Descriptor Shape
- **D-09:** The v1 controller is one regular gamepad-like joystick device, not a custom gun HID report, mouse-like aim device, or split multi-device shape.
- **D-10:** The v1 axes are physical stick X/Y plus motion aim X/Y. Do not expose raw yaw, pitch, roll, or Android-local preview aim as HID axes in Phase 5.
- **D-11:** The v1 buttons are trigger, reload, X, Y, A, and B. Physical stick direction buttons should not duplicate the stick axes in the descriptor contract.
- **D-12:** Trigger is digital only in v1 because current iPega evidence is binary. Do not invent an analog trigger axis.

### Capability Flags
- **D-13:** Backends expose a structured capability object, not only a bitmask or freeform diagnostic string.
- **D-14:** Unsupported features must include explicit unsupported reasons with platform and detail fields so platform limits are visible to later planning and diagnostics.
- **D-15:** Haptic capabilities use a detailed effect matrix with strength, duration, pattern, phone-haptic, and output-report support limits. This avoids conflating v1 phone haptics with future OS output-report support.
- **D-16:** Required invariant tests must prove the capability object matches the descriptor contract: six buttons, four axes, digital trigger, and declared haptic/output support.

### Haptic and Output Boundary
- **D-17:** Phase 5 smoke sends a real phone haptic command through the existing desktop-to-Android control path.
- **D-18:** Platform stubs simulate a future HID output report and verify it routes to a phone haptic command.
- **D-19:** Android absence fails Phase 5 haptic smoke. The pass path requires a paired Android session.
- **D-20:** Haptic smoke evidence requires human confirmation that the phone vibrated. Sending a command alone is not enough.

### the agent's Discretion
- Choose exact Kotlin package names, interface/class names, immutable state representation, axis numeric ranges, fixture filenames, JUnit XML schema details, and stub command names during planning.
- Choose how to store cross-platform smoke artifacts, provided both macOS and Windows runs are distinguishable and auditable.
- Choose whether the first UDP replay fixture is generated from existing codec helpers or checked in as a small sanitized binary/JSON fixture.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 5 goal, requirements, success criteria, dependencies, and later-phase boundaries.
- `.planning/REQUIREMENTS.md` - `DESK-04`, `DESK-07`, `DESK-08`, and later Windows/macOS/profile/visualizer boundaries.
- `.planning/PROJECT.md` - gamepad-style HID shape, desktop platform constraints, desktop-owned profile mapping, and phone haptic v1 decision.
- `.planning/STATE.md` - current state after Phase 4 and active desktop backend focus.

### Research Context
- `.planning/research/STACK.md` - Windows VHF direction, macOS CoreHID/HIDDriverKit direction, and virtual joystick/visualizer sequencing.
- `.planning/research/ARCHITECTURE.md` - virtual HID backend boundary, profile mapper boundary, input flow, haptic flow, and anti-patterns.

### Prior Phase Context
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - UDP receiver boundary, raw motion-only input, haptic transport, stream lifecycle, and virtual joystick deferral.
- `.planning/phases/03-lan-pairing-and-secure-session/03-CONTEXT.md` - desktop companion scaffold, trusted control channel, heartbeat, diagnostics, and reserved haptic history.
- `.planning/phases/02-android-host-live-input/02-CONTEXT.md` - normalized gun/motion events, desktop-owned final aim mapping, and recenter semantics.

### Protocol and Evidence
- `docs/protocol/lan-pairing-v1.md` - control envelopes, UDP input frames, input stream lifecycle, and phone haptic command/result contract.
- `docs/protocol/input-stream-v1-fixtures.md` - Phase 4 input stream fixture contract and examples.
- `docs/evidence/manifests/phase4-input-haptic-transport.jsonl` - accepted Phase 4 input and haptic physical smoke evidence.

### Desktop Companion Integration
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - desktop control/session hub, UDP input callbacks, input stream config, and haptic command path.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` - reliable control envelope codec and type allowlist.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt` - existing compact diagnostics model.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt` - minimal default profile metadata surface; do not expand into full Phase 8 profile behavior.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` - receiver output that Phase 5 adapts to normalized controller state.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt` - injectable UDP runtime seam and receiver loop.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - trusted UDP receiver lifecycle and stale-state behavior.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt` - replay/stale rejection gate.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt` - phone haptic command/result model.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` - existing fake UDP runtime and control/haptic tests.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt` - loopback UDP runtime smoke pattern.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - current Swing haptic smoke command and desktop UI diagnostics.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UdpReceivedInput` already exposes trusted receiver output with buttons, pressed controls, stick axes, raw motion, timestamps, stale state, and sequence.
- `DesktopUdpInputRuntime` and `UdpInputReceiver` already provide an injectable receiver path for replay or loopback smoke.
- `ControlServer` already exposes `onUdpInputReceived`, `onUdpInputRejected`, `onUdpInputStateChanged`, `sendHapticCommand`, and `onHapticResultReceived` seams.
- `HapticCommand` and `HapticResult` already model desktop-to-Android phone haptic command/result payloads.
- `ControlChannelTest` already has `FakeUdpRuntime`, useful as a precedent for harness doubles.

### Established Patterns
- Desktop companion is Kotlin/JVM with a Swing shell and plain Kotlin main-function tests executed from Gradle.
- Protocol code mirrors Android and desktop where needed, with fixture-backed compatibility tests instead of a shared module.
- Android sends raw normalized motion/provider/capability data; desktop owns final aim mapping.
- Existing diagnostics avoid secret material and keep later profile/visualizer behavior out of earlier phases.

### Integration Points
- Add backend contract under the desktop companion source tree behind the current `ControlServer` UDP input callback.
- Add the `UdpReceivedInput` to controller-state adapter without adding Phase 8 profile tuning.
- Add platform stub commands or tests that can run independently on macOS and Windows and emit JUnit-style results.
- Add capability and descriptor invariant tests before any platform OS adapter work.
- Route simulated output-report haptic smoke through the existing control/haptic path and require live Android validation for pass status.

</code_context>

<specifics>
## Specific Ideas

- The first backend contract should be semantic and named, not raw HID bytes.
- UDP replay is preferred over direct semantic-only input because Phase 5 must prove the Phase 4 receiver handoff.
- The gamepad contract is intentionally small: six buttons, four axes, digital trigger.
- Capability reporting must be rich enough to describe platform limits and haptic effect support before macOS/Windows implementation diverges.
- Phase 5 haptic smoke is intentionally strict: it must reach a paired Android phone and be physically observed.

</specifics>

<deferred>
## Deferred Ideas

- Production Windows virtual HID implementation remains Phase 6.
- Production macOS virtual HID implementation remains Phase 7.
- Profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Packet replay diagnostics beyond the Phase 5 smoke harness remain Phase 10.
- Physical gun motor rumble remains v2/deferred.

</deferred>

---

*Phase: 5-Desktop Backend Contract and Smoke Harness*
*Context gathered: 2026-06-09T17:25:09Z*
