# Roadmap: Bluetooth Gun Driver

## Overview

v1 moves from real iPega hardware discovery to a simple end-to-end joystick visualizer. The order reduces the highest uncertainty first: prove the physical gun input protocol, phone motion-aim path, and phone haptic fallback, then lock the Android-to-desktop session contract, then validate Windows and macOS virtual joystick feasibility, and only then complete the first user-visible acceptance path through profiles, visualizer diagnostics, recentering, latency, and phone haptics.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Hardware and Protocol Discovery** - Prove how the real iPega gun exposes input and verify phone vibration as the v1 feedback path.
- [x] **Phase 2: Android Host Live Input** - Android can connect to the gun, read controls and motion sensors, and expose live session status.
- [x] **Phase 3: LAN Pairing and Secure Session** - Android and desktop can establish an authenticated local session by QR or pairing code. (completed 2026-06-08)
- [ ] **Phase 4: Input Stream and Haptic Transport** - Versioned UDP input and reliable control messages carry input, diagnostics, and phone haptic commands safely.
- [ ] **Phase 5: Desktop Backend Contract and Smoke Harness** - Shared desktop backend contract and fake-input smoke tests work before real OS driver work.
- [ ] **Phase 6: Windows Virtual Joystick Path** - Windows 11 x64 exposes the gun stream as a regular gamepad-style joystick with output-to-phone-haptic forwarding.
- [ ] **Phase 7: macOS Virtual Joystick Path** - macOS Apple Silicon exposes the gun stream as a regular gamepad-style joystick and reports output limits clearly.
- [ ] **Phase 8: Desktop Profiles and Mapping** - Users can configure aim and button mapping on desktop without Android rebuilds.
- [ ] **Phase 9: Visualizer Acceptance Path** - The simple visualizer proves controls, aim, recentering, latency, packet loss, and phone haptic round trip.
- [ ] **Phase 10: Diagnostics, Replay, and v1 Docs** - Replay tests, diagnostic logs, setup docs, protocol docs, and known limits make the MVP repeatable.

## Phase Details

### Phase 1: Hardware and Protocol Discovery

**Goal:** Developer can verify the real iPega hardware protocol and capture enough evidence to build against it.
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

**Goal:** User can use the Android host app to connect the gun, see live control and motion-aim state, and recenter aim.
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

**Goal:** Desktop can safely receive high-rate Android input while haptic commands travel back to the Android phone with acknowledgements.
**Mode:** mvp
**Depends on:** Phase 3
**Requirements:** ANDR-07, TRAN-04, TRAN-05, TRAN-07, TRAN-08, TRAN-09, DESK-01, PERF-03
**Success Criteria** (what must be TRUE):

  1. Android streams versioned UDP input frames with sequence number, session id, timestamps, buttons, axes, motion payload, and motion provider/capability flags.
  2. Desktop can validate, decrypt or authenticate, parse, and reject stale or replayed Android input frames.
  3. Desktop can send haptic commands with command id, strength, duration, TTL, and optional pattern.
  4. Android vibrates the phone for desktop haptic commands and returns ack or failure status.
  5. Android and desktop recover from LAN disconnect without applying old input or playing stale haptics.

**Plans:** 4/5 plans executed

### Phase 5: Desktop Backend Contract and Smoke Harness

**Goal:** Developer can validate shared virtual-controller behavior on both desktop targets before using the real Android stream.
**Mode:** mvp
**Depends on:** Phase 4
**Requirements:** DESK-04, DESK-07, DESK-08
**Success Criteria** (what must be TRUE):

  1. Developer can run fake-input virtual controller smoke tests on Windows and macOS.
  2. Virtual joystick descriptor includes trigger, reload, joystick axes, X/Y/A/B buttons, and aim axes.
  3. Desktop companion reports backend capability flags for buttons, axes, haptics, output reports, and platform limitations.

**Plans:** TBD

### Phase 6: Windows Virtual Joystick Path

**Goal:** Windows 11 x64 can see and use the streamed gun as a regular gamepad-style joystick with output-to-phone-haptic forwarding.
**Mode:** mvp
**Depends on:** Phase 5
**Requirements:** DESK-02, DESK-05, PACK-02
**Success Criteria** (what must be TRUE):

  1. Windows 11 x64 sees an OS-visible regular gamepad-style virtual joystick.
  2. Windows virtual joystick receives desktop rumble or output requests and maps them to v1 phone haptic commands.
  3. Repository documents the selected Windows virtual HID strategy, driver signing requirements, and development setup.

**Plans:** TBD

### Phase 7: macOS Virtual Joystick Path

**Goal:** macOS Apple Silicon can see and use the streamed gun as a regular gamepad-style joystick with honest output capability reporting.
**Mode:** mvp
**Depends on:** Phase 5
**Requirements:** DESK-03, DESK-06, PACK-03
**Success Criteria** (what must be TRUE):

  1. macOS Apple Silicon sees an OS-visible regular gamepad-style virtual joystick.
  2. macOS virtual joystick receives desktop rumble or output requests where supported, or clearly reports the platform output limitation while preserving v1 phone haptic support.
  3. Repository documents the selected macOS virtual HID strategy, entitlement requirements, and development setup.

**Plans:** TBD

### Phase 8: Desktop Profiles and Mapping

**Goal:** User can configure desktop-side profiles that map gun controls and motion aim into virtual joystick behavior.
**Mode:** mvp
**Depends on:** Phase 7
**Requirements:** PROF-01, PROF-02, PROF-03, PROF-04, PROF-05, PROF-06
**Success Criteria** (what must be TRUE):

  1. User can create, store, and select desktop aim mapping profiles locally.
  2. User can configure motion axes, sensitivity, inversion, dead zone, smoothing, and provider-specific behavior per profile.
  3. User can map trigger, reload, joystick, and X/Y/A/B to virtual joystick controls per profile.
  4. Profile changes apply on desktop without rebuilding the Android app.
  5. A default visualizer profile works immediately after pairing.

**Plans:** TBD
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
| 4. Input Stream and Haptic Transport | 4/5 | In Progress|  |
| 5. Desktop Backend Contract and Smoke Harness | 0/TBD | Not started | - |
| 6. Windows Virtual Joystick Path | 0/TBD | Not started | - |
| 7. macOS Virtual Joystick Path | 0/TBD | Not started | - |
| 8. Desktop Profiles and Mapping | 0/TBD | Not started | - |
| 9. Visualizer Acceptance Path | 0/TBD | Not started | - |
| 10. Diagnostics, Replay, and v1 Docs | 0/TBD | Not started | - |
