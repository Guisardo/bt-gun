# Phase 9: Visualizer Acceptance Path - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 9 delivers the user-visible acceptance visualizer for the v1 MVP. It proves the authenticated LAN product stream, both OS-visible controller paths, recenter behavior, latency, packet loss, and phone haptic feedback before any commercial game support. It does not add game-specific presets, physical gun motor rumble, import/export, replay tooling, or Phase 10 documentation/replay systems.

</domain>

<decisions>
## Implementation Decisions

### Acceptance Path
- **D-01:** Phase 9 pass gate is a guided both-path acceptance flow. The visualizer must prove the LAN visualizer path plus macOS Android Bluetooth HID input plus Windows VHF input.
- **D-02:** Both OS-visible paths are required for Phase 9 acceptance: macOS through Android Bluetooth HID and Windows through the completed VHF path. Do not treat either one alone as sufficient.
- **D-03:** Haptic proof uses per-platform rows. The authenticated LAN visualizer haptic path must vibrate the Android phone. The Windows VHF output-to-phone haptic path must also pass. The macOS Bluetooth HID output/haptic row may pass as unsupported/deferred when it shows current evidence for the known limitation.
- **D-04:** Final Phase 9 acceptance is recorded as a guided manual checklist in the visualizer. Do not make a generated evidence bundle the primary pass artifact in this phase.

### Visualizer Surface
- **D-05:** Add a separate Swing `VisualizerWindow` instead of turning `PairingWindow` into the main visualizer.
- **D-06:** The visualizer auto-opens when Android reaches an authenticated control session.
- **D-07:** `PairingWindow` also provides a visible manual reopen button for the visualizer.
- **D-08:** The visualizer remains open across disconnects and shows stale or disconnected state instead of closing or clearing context.

### Live Display Shape
- **D-09:** The top of the visualizer shows the guided checklist and live gamepad panel together.
- **D-10:** The primary control display is gamepad-like: six button indicators, stick crosshair, aim crosshair, and stale overlay.
- **D-11:** Recenter and recent history show as a current recenter/aim-zero status row plus a short event strip with the last 10 product events.
- **D-12:** Raw debug state is visible as on/off status. Raw provider, yaw, pitch, roll, and raw aim values live in a collapsed debug drawer when Android raw debug is enabled.

### Metrics and Haptic Proof
- **D-13:** The headline latency metric is Android capture timestamp to Swing visualizer update/render, with the v1 target under 50 ms.
- **D-14:** Packet loss display is a simple current-session expected/missed counter and percent derived from accepted UDP sequence gaps.
- **D-15:** The visualizer haptic test flow includes an authenticated LAN phone pulse and then prompts or guides the Windows VHF output-to-phone haptic proof.
- **D-16:** Checklist rows pass through observed live state when possible plus user confirmation for physical, macOS, Windows, and haptic observations.

### the agent's Discretion
- Choose exact Swing component structure, layout managers, rendering cadence, update throttling, checklist row ids, and row labels as long as the decisions above stay true.
- Choose exact visual styling for indicators, crosshairs, stale overlays, and event strip within existing Swing constraints.
- Choose exact internal metrics classes and callback names, provided latency uses Android capture to visualizer update/render for the headline and packet loss uses accepted UDP sequence gaps.
- Choose whether supporting values such as capture-to-send, send-to-receive, receive-to-render, rejection reasons, and raw debug values appear in secondary diagnostics, but do not make them the primary acceptance metric.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 9 goal, requirements, success criteria, dependency on Phase 8, and Phase 10 boundary.
- `.planning/REQUIREMENTS.md` - `VIS-01` through `VIS-06`, `PERF-01`, `PERF-02`, and remaining acceptance criteria.
- `.planning/PROJECT.md` - v1 product intent, Android host plus desktop companion architecture, gamepad-style HID shape, and phone-haptic feedback decision.
- `.planning/STATE.md` - current project state after Phase 8 and carried decisions.

### Prior Phase Context
- `.planning/phases/08-desktop-profiles-and-mapping/08-CONTEXT.md` - Android-owned profiles, mapped product stream, raw debug toggle, and desktop read-only metadata.
- `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md` - Android Bluetooth HID as primary macOS input path, haptic limitation handling, and macOS proof expectations.
- `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md` - Windows VHF proof bar, `joy.cpl` visibility, live input, and output-to-phone haptic precedent.
- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md` - v1 backend descriptor, semantic controller state, backend capabilities, and haptic smoke boundaries.
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - authenticated UDP stream, stale behavior, haptic transport, and disconnect recovery.

### Protocol and Evidence
- `docs/protocol/lan-pairing-v1.md` - pairing/control channel, haptic command/result, mapped UDP frame fields, sequence handling, stream lifecycle, and current note that visualizer metrics belong to later phases.
- `docs/setup/android-bluetooth-hid-gamepad.md` - macOS Android HID setup, input proof, output unsupported/deferred behavior, and Windows fallback routing.
- `docs/windows/phase6-proof-checklist.md` - Windows VHF target proof checklist and user-confirmed evidence pattern.
- `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` - macOS Android HID input pass rows and haptic unsupported rows.
- `docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl` - Windows VHF pass evidence rows.

### Desktop Code
- `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` - desktop launch wiring for `PairingWindow`, `ControlServer`, and backend runtimes.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - existing Swing pairing/status window, profile metadata display, haptic button, and backend diagnostics.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` - current UI behavior tests and forbidden desktop profile controls.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - authenticated control session, UDP callbacks, profile metadata callback, rejected input callback, haptic result callback, and haptic send path.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt` - Android profile metadata consumed by desktop.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt` - UDP receive loop and lifecycle updates.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - accepted input, rejection, timeout, stale, and control grace behavior.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` - mapped aim, raw debug fields, timestamps, sequence, and stale state available to the visualizer.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` - semantic button, stick, aim, stale, and source sequence fields.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` - mapped UDP to semantic state conversion.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt` - Windows runtime diagnostics and output haptic routing.

### Android Code
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - profile mapping, HID and UDP fanout, recenter, haptic handling, and packet stream state.
- `android-host/app/src/main/java/com/btgun/host/profile/ProfileMapper.kt` - mapped controller state, aim status, smoothing status, and recenter physical control.
- `android-host/app/src/main/java/com/btgun/host/profile/ProfileModels.kt` - Default Visualizer profile and raw debug flag.
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - Android dashboard labels for profile, packet stream, recenter, HID, and haptic states.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` - Android Bluetooth HID mapped report packing.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PairingWindow` already creates a Swing `JFrame`, listens to `ControlServer` callbacks on the Swing event thread, shows packet stream/profile/haptic/backend diagnostics, and sends test haptics.
- `ControlServer` already exposes callbacks for session state, profile metadata, accepted UDP input, rejected UDP input, UDP lifecycle state, and haptic result.
- `UdpReceivedInput` already carries capture, send, receive, sequence, mapped aim, raw debug, and stale fields needed for Phase 9 metrics and display.
- `SemanticControllerState` and `UdpControllerStateAdapter` provide an existing mapped product state boundary for buttons, stick, aim, stale, and source sequence.
- `WindowsBackendRuntimeDiagnostics` and `MacosBackendRuntimeDiagnostics` already expose backend lifecycle, publish, stale, source sequence, and haptic routing status for checklist rows.

### Established Patterns
- Desktop UI is Kotlin/JVM Swing with updates routed through `SwingUtilities.invokeLater`.
- Desktop receives Android-mapped product input by default and must not reintroduce desktop profile editing.
- Stale stream behavior clears active buttons and stick while preserving last aim with `stale=true`.
- Sensitive pairing material, stream secrets, HMAC keys, and private key data must stay out of diagnostics and committed evidence.

### Integration Points
- Add `VisualizerWindow` under `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/`.
- Launch and manage the visualizer from `PairingWindow` or a small shared desktop UI coordinator after authenticated session state.
- Feed visualizer state from `ControlServer` callbacks and backend runtime diagnostics without adding networking/session ownership to the UI.
- Extend tests under `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/` and transport/backend tests as needed for metrics calculations.

</code_context>

<specifics>
## Specific Ideas

- The visualizer should feel like an acceptance harness, not a dense debug dashboard.
- The first screen should make pass/fail progress visible while still showing live controls immediately.
- Live gamepad controls should be visually scannable: buttons, stick crosshair, aim crosshair, stale overlay.
- Raw debug data is secondary and should not crowd the default view.
- Manual confirmation remains part of the pass gate because the phase includes physical gun feel, phone vibration, macOS visibility, and Windows visibility.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 9 scope.

</deferred>

---

*Phase: 9-Visualizer Acceptance Path*
*Context gathered: 2026-06-12*
