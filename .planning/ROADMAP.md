# Roadmap: Bluetooth Gun Driver

## Overview

v1 moves from real iPega hardware discovery to a simple end-to-end joystick visualizer. The order reduces the highest uncertainty first: prove the physical gun input protocol, phone motion-aim path, and phone haptic fallback, then lock transport/control contracts, then validate an OS-visible controller path. As of 2026-06-10, the primary no-subscription macOS path is the Android phone acting as a Bluetooth HID gamepad. The completed Windows virtual joystick work remains the fallback desktop path if Android Bluetooth HID gamepad integration is blocked.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Hardware and Protocol Discovery** - Prove how the real iPega gun exposes input and verify phone vibration as the v1 feedback path.
- [x] **Phase 2: Android Host Live Input** - Android can connect to the gun, read controls and motion sensors, and expose live session status.
- [x] **Phase 3: LAN Pairing and Secure Session** - Android and desktop can establish an authenticated local session by QR or pairing code. (completed 2026-06-08)
- [x] **Phase 4: Input Stream and Haptic Transport** - Versioned UDP input and reliable control messages carry input, diagnostics, and phone haptic commands safely. (physical smoke plan added 2026-06-09) (completed 2026-06-09)
- [x] **Phase 5: Desktop Backend Contract and Smoke Harness** - Shared desktop backend contract and fake-input smoke tests work before real OS driver work. (completed 2026-06-09)
- [x] **Phase 6: Windows Virtual Joystick Path** - Windows 11 x64 exposes the gun stream as a regular gamepad-style joystick with output-to-phone-haptic forwarding, retained as fallback if Android Bluetooth HID gamepad integration is blocked. (completed 2026-06-10)
- [x] **Phase 7: Android Bluetooth HID Gamepad Path** - Android phone exposes the gun stream directly to macOS as a Bluetooth HID gamepad, with output-report-to-phone-haptic proof or clear compatibility limits. (completed 2026-06-11)
- [ ] **Phase 8: Desktop Profiles and Mapping** - Users configure Android-owned aim and button profiles that apply at runtime; desktop only displays active Android profile metadata.
- [ ] **Phase 9: Visualizer Acceptance Path** - The simple visualizer proves controls, aim, recentering, latency, packet loss, and phone haptic round trip.
- [ ] **Phase 10: Diagnostics, Replay, and v1 Docs** - Replay tests, diagnostic logs, setup docs, protocol docs, and known limits make the MVP repeatable.

## Phase Details

### Phase 1: Hardware and Protocol Discovery

**Goal:** As a developer, I want to verify the real iPega hardware protocol and capture enough build evidence, so that the Android host can target the correct gun input and feedback path.
**Mode:** mvp
**Depends on:** Nothing (first phase)
**Requirements:** DISC-01, DISC-02, DISC-03, DISC-04, DISC-05, DISC-06, DISC-07
**Success Criteria** (what must be TRUE):

  1. Developer can inventory every local reference APK/XAPK with package identity, SDK, permissions, type, and validity.
  2. Developer can run Android diagnostics that show whether the gun is a standard input device and which Bluetooth Classic/BLE services are visible.
  3. Developer can capture trigger, reload, joystick, X/Y/A/B, and haptic/deferred motor evidence from real hardware or app-observed frames.
  4. Developer can map every physical gun control to saved normalized fixtures and record phone vibration as the v1 feedback path.

**Plans:** 5/5 plans executed
Plans:

- [x] 01-01-PLAN.md — Static APK/XAPK inventory, evidence scaffold, and JSONL validator.
- [x] 01-02-PLAN.md — Throwaway Android diagnostic module and behavior spec.
- [x] 01-03-PLAN.md — Physical hardware capture workflow and human verification checkpoint.
- [x] 01-04-PLAN.md — Normalized handshake/control fixtures and no-hardware validation.
- [x] 01-05-PLAN.md — Phone haptic fallback, deferred gun motor note, and final evidence gate.

### Phase 2: Android Host Live Input

**Goal:** As a user, I want to use the Android host app to connect the gun, see live control and motion-aim state, and recenter aim, so that I can prove the phone can read and normalize the gun stream.
**Mode:** mvp
**Depends on:** Phase 1
**Requirements:** ANDR-01, ANDR-02, ANDR-03, ANDR-04, ANDR-05, ANDR-06, ANDR-08
**Success Criteria** (what must be TRUE):

  1. User can grant Bluetooth, nearby device, sensor, and LAN permissions from the Android app.
  2. User can connect the Android host app to the physical iPega gun.
  3. Android app emits ordered normalized samples for gun controls, connection state, and motion aim data with monotonic timestamps and active provider metadata.
  4. Motion aim prefers fused rotation-vector/game-rotation-vector or gyro+accelerometer data when available, and exposes an explicit accelerometer/gravity tilt fallback when no gyroscope is present.
  5. Holding reload for two seconds recenters motion aim while normal reload press and release events still appear.
  6. Android app shows active gun connection, desktop link, packet stream, motion provider, and haptic status.

**Plans:** 6/6 plans executed
Plans:

- [x] 02-01-PLAN.md — Production Android host scaffold, permission gate, and live envelope contracts.
- [x] 02-02-PLAN.md — Fixture-backed packet parser and normalized gun events.
- [x] 02-03-PLAN.md — BLE adapter and foreground session service.
- [x] 02-04-PLAN.md — Motion provider selection and preview aim contracts.
- [x] 02-05-PLAN.md — Reload-hold recenter state machine.
- [x] 02-06-PLAN.md — Dashboard shell, inactive placeholders, and manual validation evidence.

**UI hint**: yes

### Phase 3: LAN Pairing and Secure Session

**Goal:** User can pair Android and desktop locally without manual IP entry and get an authenticated session.
**Mode:** mvp
**Depends on:** Phase 2
**Requirements:** TRAN-01, TRAN-02, TRAN-03, TRAN-06
**Success Criteria** (what must be TRUE):

  1. Desktop companion can create a local pairing session and display a QR code plus pairing-code fallback.
  2. User can pair Android to desktop from the normal path without manual IP entry.
  3. Pairing creates an authenticated local session using a short-lived one-time secret with replay protection.
  4. Android and desktop maintain a reliable control channel for pairing state, heartbeat, diagnostics, profile metadata, and haptic commands.

**Plans:** 8/8 plans complete
Plans:

**Wave 0**

- [x] 03-01-PLAN.md — Desktop pairing session, QR/manual fallback, and Wave 0 desktop test harness.

**Wave 1** *(blocked on Wave 0 completion)*

- [x] 03-02-PLAN.md — Android QR/manual parser, trusted desktop store, and desktop-link dashboard state.

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 03-03-PLAN.md — Authenticated pairing proof, replay/rate-limit defenses, and fail-closed trust anchor.

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 03-04-PLAN.md — Reliable WSS control-channel core, envelope allowlist, and proof-gated client/server.

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 03-05-PLAN.md — Heartbeat/liveness, diagnostics, and minimal profile metadata implementation.

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 03-06-PLAN.md — Desktop companion launch and pairing/control window lifecycle.

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 03-07-PLAN.md — Android QR/manual/trusted-desktop wiring and service ownership.

**Wave 7** *(blocked on Wave 6 completion)*

- [x] 03-08-PLAN.md — Protocol finalization, manual smoke guide, and Phase 4 boundary gates.

**UI hint**: yes

### Phase 4: Input Stream and Haptic Transport

**Goal:** As a desktop user, I want to receive live Android gun input and send phone haptic commands, so that I can verify the transport works before virtual joystick work.
**Mode:** mvp
**Depends on:** Phase 3
**Requirements:** ANDR-07, TRAN-04, TRAN-05, TRAN-07, TRAN-08, TRAN-09, DESK-01, PERF-03
**Success Criteria** (what must be TRUE):

  1. Android streams versioned UDP input frames with sequence number, session id, timestamps, buttons, axes, motion payload, and motion provider/capability flags.
  2. Desktop can validate, decrypt or authenticate, parse, and reject stale or replayed Android input frames.
  3. Desktop can send haptic commands with command id, strength, duration, TTL, and optional pattern.
  4. Android vibrates the phone for desktop haptic commands and returns ack or failure status.
  5. Android and desktop recover from LAN disconnect without applying old input or playing stale haptics.

**Plans:** 6/6 plans complete
Plans:
**Wave 1**

- [x] 04-01-PLAN.md — Wire contract foundation.
- [x] 04-02-PLAN.md — Android trusted config UDP sender.
- [x] 04-03-PLAN.md — Desktop authenticated UDP receiver.

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 04-04-PLAN.md — Reliable phone haptic transport.

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 04-05-PLAN.md — Input stream recovery.

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 04-06-PLAN.md — Physical LAN smoke evidence and UAT readiness.

### Phase 5: Desktop Backend Contract and Smoke Harness

**Goal:** Developer can validate shared virtual-controller behavior on both desktop targets before using the real Android stream.
**Mode:** mvp
**Depends on:** Phase 4
**Requirements:** DESK-04, DESK-07, DESK-08
**Success Criteria** (what must be TRUE):

  1. Developer can run fake-input virtual controller smoke tests on Windows and macOS.
  2. Virtual joystick descriptor includes trigger, reload, joystick axes, X/Y/A/B buttons, and aim axes.
  3. Desktop companion reports backend capability flags for buttons, axes, haptics, output reports, and platform limitations.

**Plans:** 5/5 plans complete
Plans:
**Wave 1**

- [x] 05-01-PLAN.md — Semantic controller state and gamepad-like descriptor contract.

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 05-02-PLAN.md — Structured backend capabilities and stub backend lifecycle.

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 05-03-PLAN.md — UDP receiver handoff to semantic controller state.

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 05-04-PLAN.md — macOS and Windows fake-input smoke commands with JUnit-style XML.

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 05-05-PLAN.md — Simulated output-report haptic smoke and cross-platform evidence gate.

### Phase 6: Windows Virtual Joystick Path

**Goal:** Windows 11 x64 can see and use the streamed gun as a regular gamepad-style joystick with output-to-phone-haptic forwarding, and this completed path remains the desktop fallback if Android Bluetooth HID gamepad mode is blocked.
**Mode:** mvp
**Depends on:** Phase 5
**Requirements:** DESK-02, DESK-05, PACK-02
**Success Criteria** (what must be TRUE):

  1. Windows 11 x64 sees an OS-visible regular gamepad-style virtual joystick.
  2. Windows virtual joystick receives desktop rumble or output requests and maps them to v1 phone haptic commands.
  3. Repository documents the selected Windows virtual HID strategy, driver signing requirements, and development setup.

**Plans:** 6/6 plans complete
Plans:

**Wave 1**

- [x] 06-01-PLAN.md — Windows HID report packing and output haptic mapping.

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 06-02-PLAN.md — VHF/KMDF driver, IOCTL ABI, INF, and helper tools.

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 06-03-PLAN.md — Windows desktop backend bridge and capabilities.
- [x] 06-05-PLAN.md — GitHub Actions build/sign/package workflow and Windows setup docs.

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 06-04-PLAN.md — Live companion runtime wiring and Windows VHF smoke entrypoint.

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 06-06-PLAN.md — Approval-gated Windows target proof with `joy.cpl`, live input, and real output haptic.

### Phase 7: Android Bluetooth HID Gamepad Path

**Goal:** Android phone can act as a Bluetooth HID gamepad for the gun stream so macOS Apple Silicon sees a normal OS-visible gamepad-style joystick without paid Apple virtual HID entitlements.
**Mode:** mvp
**Depends on:** Phase 2, Phase 5, with Phase 6 retained as fallback
**Requirements:** ANDR-09, ANDR-10, ANDR-11, DESK-03, DESK-06, PACK-03, PACK-06
**Success Criteria** (what must be TRUE):

  1. Android host app detects whether the phone can act as a Bluetooth HID gamepad peripheral and reports a clear blocked state when unsupported.
  2. Android host app exposes normalized gun controls and Android motion aim as a regular gamepad-style Bluetooth HID report.
  3. macOS Apple Silicon pairs to the Android phone and sees an OS-visible gamepad-style joystick without CoreHID/DriverKit virtual HID entitlement work.
  4. Bluetooth HID output or rumble reports route to Android phone haptics when supported, or the app reports the limitation clearly.
  5. Repository documents Android Bluetooth HID setup, macOS pairing, output-report behavior, compatibility risks, and the fallback to the completed Windows virtual joystick path.

**Plans:** 6/6 plans complete
Plans:

**Reroute note (2026-06-10):** CoreHID and DriverKit virtual HID paths require Apple entitlement/signing or local security relaxation and do not satisfy the no-subscription primary path. Legacy work is retained only as evidence/fallback scaffolding. The active Phase 7 plan set below is Android Bluetooth HID gamepad work.

**Haptic defer note (2026-06-11):** macOS browser/GameController haptics over the Android Bluetooth HID path are unsupported/deferred after live `No Vibration` evidence and a failed PID ForceFeedback descriptor experiment. Stable gamepad input remains the active macOS path; phone haptics remain available through LAN/Windows VHF fallback paths.

**Wave 1**

- [x] 07-01-PLAN.md — Gradle startup gate, Android HID capability/status model, and sanitized evidence scaffold.

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 07-02-PLAN.md — Android Bluetooth HID descriptor and deterministic input report packer.

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 07-03-PLAN.md — Android `BluetoothHidDevice` adapter seam and strict output-report mapper.

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 07-04-PLAN.md — HostSessionService, dashboard, and Activity integration for explicit Bluetooth gamepad mode.

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 07-05-PLAN.md — Live Android phone plus macOS Bluetooth/Game Controller proof checkpoints.

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 07-06-PLAN.md — Android Bluetooth HID setup docs, redaction, alternate-phone gate, and Windows VHF fallback decision.

### Phase 8: Desktop Profiles and Mapping

**Goal:** User can configure Android-owned profiles that map gun controls and motion aim into virtual joystick behavior, while desktop remains a read-only mapped-stream diagnostics surface.
**Mode:** mvp
**Depends on:** Phase 7 Android Bluetooth HID gamepad proof or explicit Windows-fallback decision
**Requirements:** PROF-01, PROF-02, PROF-03, PROF-04, PROF-05, PROF-06
**Success Criteria** (what must be TRUE):

  1. Android stores local profiles and desktop only displays active Android profile metadata.
  2. Android profile editor configures motion axes, sensitivity, inversion, dead zone, smoothing, and provider-specific behavior per profile.
  3. Android remaps trigger, reload, X, Y, A, and B while stick axes and aim axes remain semantic.
  4. Android profile changes apply at runtime without Android rebuilds or a desktop editor.
  5. Immutable `Default Visualizer` works immediately after pairing and desktop shows read-only mapped-stream status.

**Plans:** 2/7 plans executed

Cross-cutting constraints:

- Android owns Phase 8 profile storage, editing, validation, and runtime application; desktop remains read-only for active Android profile metadata.
- Android sends a mapped product stream by default; raw provider/motion extras are Android-session debug only.
- Stick axes and aim axes stay semantic; Phase 8 remaps only trigger, reload, X, Y, A, and B.

Plans:

**Wave 0**

- [x] 08-01-PLAN.md — Correct stale profile ownership wording before code work.

**Wave 1** *(blocked on Wave 0 completion)*

- [x] 08-02-PLAN.md — Android profile schema, store, validation, and immutable Default Visualizer.

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 08-03-PLAN.md — Pure Android profile mapper and latency-capped adaptive smoother.

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 08-04-PLAN.md — Runtime active-profile wiring into HID, LAN mapped stream, and control metadata.

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 08-05-PLAN.md — Android profile management UI and dashboard profile rows.
- [ ] 08-06-PLAN.md — Desktop read-only active Android profile metadata and mapped-stream diagnostics.

**Wave 5** *(blocked on Wave 4 completion)*

- [ ] 08-07-PLAN.md — Full validation, USB Android screenshots, screenshot cleanup, and sanitized evidence manifest.

**UI hint**: yes

### Phase 9: Visualizer Acceptance Path

**Goal:** User can prove the full MVP path in a simple joystick visualizer before any commercial game support.
**Mode:** mvp
**Depends on:** Phase 8
**Requirements:** VIS-01, VIS-02, VIS-03, VIS-04, VIS-05, VIS-06, PERF-01, PERF-02
**Success Criteria** (what must be TRUE):

  1. User can open a simple joystick visualizer connected to the desktop companion pipeline.
  2. Visualizer displays trigger, reload, joystick, X/Y/A/B, mapped aim axes, recenter events, and current aim-zero state in real time.
  3. Visualizer displays Android connection, desktop virtual controller, packet stream, haptic status, latency, and packet loss.
  4. User can press a haptic test control that vibrates the Android phone and shows ack or fail result.
  5. Visualizer path measures Android capture timestamp to desktop update and targets under 50 ms on normal local Wi-Fi.

**Plans:** TBD
**UI hint**: yes

### Phase 10: Diagnostics, Replay, and v1 Docs

**Goal:** Developer can repeat, diagnose, and document the v1 MVP without depending on hidden setup knowledge.
**Mode:** mvp
**Depends on:** Phase 9
**Requirements:** PERF-04, PERF-05, PACK-01, PACK-04, PACK-05
**Success Criteria** (what must be TRUE):

  1. Developer can replay packet logs in tests to verify parser, profile mapping, and visualizer output.
  2. Android and desktop diagnostics distinguish gun, sensor, LAN, profile, and virtual-driver failures.
  3. Repository documents Android build tooling, device testing workflow, LAN protocol schemas, pairing flow, and security model.
  4. Repository documents v1 limitations, including no direct desktop Bluetooth, deferred physical gun motor rumble, and no game-specific presets.

**Plans:** TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Hardware and Protocol Discovery | 5/5 | Complete | 2026-06-06 |
| 2. Android Host Live Input | 6/6 | Complete | 2026-06-07 |
| 3. LAN Pairing and Secure Session | 8/8 | Complete   | 2026-06-08 |
| 4. Input Stream and Haptic Transport | 6/6 | Complete    | 2026-06-09 |
| 5. Desktop Backend Contract and Smoke Harness | 5/5 | Complete | 2026-06-09 |
| 6. Windows Virtual Joystick Path | 6/6 | Complete | 2026-06-10 |
| 7. Android Bluetooth HID Gamepad Path | 6/6 | Complete    | 2026-06-11 |
| 8. Desktop Profiles and Mapping | 2/7 | In Progress|  |
| 9. Visualizer Acceptance Path | 0/TBD | Not started | - |
| 10. Diagnostics, Replay, and v1 Docs | 0/TBD | Not started | - |
