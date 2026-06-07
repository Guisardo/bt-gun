# Phase 3: LAN Pairing and Secure Session - Context

**Gathered:** 2026-06-07T16:28:16Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 builds the local LAN pairing and reliable session foundation between the Android host app and the first desktop companion surface. It lets the desktop create a pairing session, lets Android pair by QR code without normal-path manual IP entry, creates an authenticated encrypted session from short-lived one-time material, remembers trusted desktop identity, and maintains a reliable control channel for session state, heartbeat, diagnostics, profile metadata, and reserved future command envelopes.

This phase does not build high-rate UDP input streaming, input frame schemas, desktop parsing of input frames, phone haptic execution, virtual joystick backends, final desktop profiles, or visualizer latency/packet-loss metrics. Phase 4 owns input stream and haptic transport execution.

</domain>

<decisions>
## Implementation Decisions

### Pairing Entry Flow
- **D-01:** The normal path is desktop-initiated pairing. Desktop starts a local pairing session and shows a QR code; Android scans that QR to pair.
- **D-02:** QR fallback should be visible manual entry of endpoint details plus pairing code. This is a fallback/debug path, not the primary product path.
- **D-03:** After successful pairing, Android should remember a trusted desktop and let the user choose it later, with reauthentication when required. Do not force QR pairing every session, and do not silently auto-reconnect as the primary behavior.
- **D-04:** QR content should include endpoint information plus one-time authentication material: at minimum protocol version, session id or equivalent, endpoint host/port, expiry, and secret/key-agreement material. Exact schema is planner discretion.

### Discovery and Addressing
- **D-05:** Android should find the desktop from QR-provided endpoint data in the normal path. LAN service discovery is not required for v1 normal pairing.
- **D-06:** Desktop should advertise its best active LAN IPv4 address and port in the QR code and manual fallback UI.
- **D-07:** The Android pairing screen should expose an "Enter manually" style fallback next to QR scan. Full manual mode is not first-class, but the fallback must be discoverable.
- **D-08:** If the QR endpoint is stale or unreachable, Android should show a clear error and allow rescan or manual edit. Do not silently broaden into LAN discovery.

### Session Security
- **D-09:** Pairing must establish an authenticated encrypted local session before any trusted control messages are accepted.
- **D-10:** One-time pairing material should be short-lived, roughly 2-5 minutes. Long-lived pairing windows are not acceptable.
- **D-11:** Manual fallback uses a 6-digit pairing code with short TTL, rate limiting, and binding to the desktop pairing session.
- **D-12:** Android should remember desktop public key or fingerprint as the durable trust anchor. Desktop name and IP are display/addressing metadata, not security identity.
- **D-13:** Reconnect must detect changed or impersonated desktop identity and surface it as an explicit trust problem.

### Reliable Control Channel
- **D-14:** Phase 3 should establish a WebSocket-style reliable bidirectional control channel inside the authenticated encrypted session. Raw TCP framing is allowed only if planning proves it materially simpler without losing debuggability.
- **D-15:** Phase 3 control messages cover pairing state, session lifecycle, heartbeat/liveness, pairing/control diagnostics, and minimal profile metadata required by `TRAN-06`.
- **D-16:** Haptic command support in Phase 3 is envelope reservation only. Define a generic control message envelope and reserve the haptic command type for Phase 4. Do not define the full haptic payload schema, do not vibrate the Android phone, and do not return execution ack/fail semantics in Phase 3.
- **D-17:** Heartbeat/liveness should be bidirectional with timeout states. Android and desktop UI should distinguish connected, degraded, and disconnected session state.
- **D-18:** Phase 3 diagnostics should show pairing/control status only: session state, desktop identity, heartbeat age, and last control error. Packet loss, jitter, high-rate frame metrics, and visualizer latency belong to later phases.

### the agent's Discretion
- Choose exact QR payload format, URI scheme, serialization, key agreement, authenticated encryption mechanism, WebSocket library, control message names, and timeout intervals during planning.
- Choose exact desktop scaffold shape and UI toolkit for the first companion pairing surface, as long as it stays portable to Windows 11 x64 and macOS Apple Silicon.
- Choose minimal profile metadata needed to satisfy `TRAN-06`; full profile storage, mapping, and editing remain Phase 8.
- Choose exact Android storage mechanism for trusted desktop identity, provided the durable trust anchor is a desktop public key or fingerprint.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 3 goal, requirements, success criteria, dependencies, and later-phase boundaries.
- `.planning/REQUIREMENTS.md` - `TRAN-01`, `TRAN-02`, `TRAN-03`, `TRAN-06`; Phase 4 boundaries for `TRAN-04`, `TRAN-05`, `TRAN-07`, `TRAN-08`, `TRAN-09`, `DESK-01`, and `PERF-03`.
- `.planning/PROJECT.md` - v1 architecture, LAN transport constraint, QR/code pairing decision, desktop target constraints, and phone-haptic fallback.
- `.planning/STATE.md` - current state, Phase 2 approval, and Phase 3 concern that haptic commands must stay out of execution scope until Phase 4.

### Research Context
- `.planning/research/STACK.md` - local LAN transport direction, reliable control channel role, Android/Kotlin constraints, and desktop portability constraints.
- `.planning/research/ARCHITECTURE.md` - Android normalized event pipeline, UDP/control-channel split, desktop boundary, and virtual-controller deferral.
- `.planning/research/PITFALLS.md` - security, latency, and phase-boundary pitfalls for LAN/control work.
- `.planning/research/FEATURES.md` - feature dependency notes for pairing, transport, haptics, profiles, and visualizer.
- `.planning/research/SUMMARY.md` - milestone architecture and sequencing context.

### Prior Phase Context
- `.planning/phases/02-android-host-live-input/02-CONTEXT.md` - foreground Android session ownership, desktop-link placeholder, local-only phone haptic boundary, and Phase 4 packet-stream deferral.
- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md` - hardware evidence rules and v1 phone-haptic decision.

### Android Host Integration
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - existing `Desktop link` and `Packet stream` placeholders, local-only phone haptic surface, and dashboard state pattern.
- `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` - current Android dashboard wiring and visible placeholder rows where Phase 3 pairing state can connect.
- `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt` - existing LAN capability probe and permission-gate state shape.
- `android-host/app/src/main/AndroidManifest.xml` - current `INTERNET` and `ACCESS_NETWORK_STATE` permissions.
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - existing stream envelope conventions and monotonic timestamp style; use as style reference, not as final control protocol.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - foreground live-session owner that Phase 3 must not bypass.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DashboardState` already models inactive desktop and packet surfaces. Phase 3 can activate the desktop-link surface while keeping packet stream inactive until Phase 4.
- `MainActivity` already renders dashboard rows and actions imperatively. Phase 3 can add QR scan/manual pairing actions using the same lightweight Android UI style unless planning chooses a larger UI refactor.
- `PermissionGate` already probes LAN network availability, and the manifest already declares network permissions.
- `HostSessionService` is the foreground live-session owner. Pairing/session code must preserve that active-session boundary instead of creating an unrelated hidden network session.

### Established Patterns
- Product UI shows current state and errors directly, with debug/provenance details behind explicit panels.
- Android-local haptics are allowed only for local test status until Phase 4.
- Phase 2 kept gun, motion, and status streams separate. Phase 3 should not prematurely merge them into high-rate transport frames.
- No desktop source tree exists yet; Phase 3 may need to create the first desktop companion pairing scaffold.

### Integration Points
- Android dashboard `Desktop link` should move from "Pending Phase 3" placeholder to real pairing/session state.
- `Packet stream` must remain inactive or clearly pending Phase 4.
- Trusted desktop identity, QR scan/manual entry, endpoint reachability, WebSocket session state, and heartbeat status need testable non-hardware paths.
- Desktop pairing scaffold must be portable enough that later Windows and macOS driver phases do not inherit platform-specific protocol assumptions.

</code_context>

<specifics>
## Specific Ideas

- User prefers QR as the normal path but wants a visible manual fallback.
- Manual fallback should use IP/port plus a 6-digit code, not hidden-only debug tooling.
- Android should remember trusted desktops, anchored by public key or fingerprint.
- QR should carry endpoint plus one-time secret material rather than relying on discovery.
- Haptic work in this phase is only reserved envelope/type space. Full haptic payload, Android vibration, and ack/fail execution semantics remain Phase 4.

</specifics>

<deferred>
## Deferred Ideas

- High-rate UDP input frames, packet schemas, replay/stale-input rejection, and desktop input parsing remain Phase 4.
- Phone haptic command payloads, execution, TTL handling, and ack/fail semantics remain Phase 4.
- Full profile storage, profile editing, and aim/button mapping remain Phase 8.
- Packet-loss, jitter, frame-rate, and visualizer latency metrics remain Phase 9 or Phase 10 as mapped in requirements.

</deferred>

---

*Phase: 3-LAN Pairing and Secure Session*
*Context gathered: 2026-06-07T16:28:16Z*
