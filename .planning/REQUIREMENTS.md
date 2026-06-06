# Requirements: Bluetooth Gun Driver

**Defined:** 2026-06-06
**Core Value:** Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive gyro aiming and bidirectional rumble.

## User Stories

- As a user with the discontinued iPega AR gun, I can connect the gun to an Android phone and use it as a wireless controller for a desktop computer.
- As a desktop user, I can see the gun as a normal gamepad-style joystick device on Windows 11 x64 and macOS Apple Silicon.
- As a player, I can aim with the Android phone gyro, press the gun controls, recenter aim, and feel rumble feedback from the desktop.
- As a developer/debugger, I can inspect connection state, packet timing, mapped axes, button states, and rumble results in a simple joystick visualizer.

## v1 Requirements

### Discovery

- [x] **DISC-01**: Developer can inventory every local reference APK/XAPK under `docs/refs/` with package name, target SDK, permissions, app type, and validity status.
- [ ] **DISC-02**: Developer can run an Android diagnostic that reports whether the iPega gun appears as a standard Android input device.
- [ ] **DISC-03**: Developer can run an Android diagnostic that reports visible Bluetooth Classic and BLE services for the physical iPega gun.
- [ ] **DISC-04**: Developer can capture and store raw Bluetooth traffic or app-observed frames for trigger, reload, joystick, X/Y/A/B, and rumble tests.
- [ ] **DISC-05**: Developer can map every physical gun control to a normalized event with down/up or axis semantics.
- [ ] **DISC-06**: Developer can identify and verify the command path that makes the physical gun vibration motor activate.
- [ ] **DISC-07**: Developer can save raw and normalized protocol fixtures for regression tests without requiring the physical gun.

### Android Host

- [ ] **ANDR-01**: User can grant required Android Bluetooth, nearby device, sensor, and LAN permissions from the Android host app.
- [ ] **ANDR-02**: User can connect the Android host app to the physical iPega gun.
- [ ] **ANDR-03**: Android host app emits normalized events for trigger, reload, joystick, X/Y/A/B, and connection state.
- [ ] **ANDR-04**: Android host app samples gyro or rotation-vector data with monotonic capture timestamps.
- [ ] **ANDR-05**: Android host app merges gun input and gyro data into ordered normalized input samples.
- [ ] **ANDR-06**: Holding reload for two seconds recenters gyro aim without preventing normal reload press/release events.
- [ ] **ANDR-07**: Android host app can receive a rumble command from desktop and forward it to the physical gun.
- [ ] **ANDR-08**: Android host app shows active session status for gun connection, desktop link, packet stream, and rumble.

### LAN Session

- [ ] **TRAN-01**: Desktop companion can create a local pairing session and display a QR code plus pairing-code fallback.
- [ ] **TRAN-02**: Android host app can pair to the desktop companion using QR code or pairing code without manual IP entry in the normal path.
- [ ] **TRAN-03**: Pairing creates an authenticated local session with a short-lived one-time secret and replay protection.
- [ ] **TRAN-04**: Android host app streams high-rate input and gyro samples to desktop using versioned UDP input frames.
- [ ] **TRAN-05**: UDP input frames include sequence number, session id, capture timestamp, send timestamp, button bitmask, axes, and gyro payload.
- [ ] **TRAN-06**: Android and desktop maintain a reliable control channel for pairing state, heartbeat, diagnostics, profile metadata, and rumble commands.
- [ ] **TRAN-07**: Desktop can send a rumble command with command id, strength, duration, expiry/TTL, and optional pattern.
- [ ] **TRAN-08**: Android host app returns rumble acknowledgement or failure status to desktop.
- [ ] **TRAN-09**: Desktop and Android can recover cleanly from LAN disconnect without playing stale rumble commands.

### Desktop Virtual Controller

- [ ] **DESK-01**: Desktop companion can receive, validate, decrypt/authenticate, and parse normalized Android input frames.
- [ ] **DESK-02**: Desktop companion can expose a regular gamepad-style virtual joystick on Windows 11 x64.
- [ ] **DESK-03**: Desktop companion can expose a regular gamepad-style virtual joystick on macOS Apple Silicon.
- [ ] **DESK-04**: Virtual joystick descriptor exposes trigger, reload, joystick axes, X/Y/A/B buttons, and aim axes.
- [ ] **DESK-05**: Windows virtual joystick path can receive desktop rumble/output requests and forward them to the control channel.
- [ ] **DESK-06**: macOS virtual joystick path can receive desktop rumble/output requests or clearly reports the platform limitation.
- [ ] **DESK-07**: Desktop companion exposes backend capability flags for buttons, axes, rumble, output reports, and platform limitations.
- [ ] **DESK-08**: Developer can run a fake-input virtual controller smoke test on both Windows and macOS before using the real Android stream.

### Profiles

- [ ] **PROF-01**: Desktop companion stores aim mapping profiles locally on the desktop.
- [ ] **PROF-02**: User can configure gyro aim mapping to joystick axes per desktop profile.
- [ ] **PROF-03**: User can configure sensitivity, inversion, dead zone, and smoothing per aim profile.
- [ ] **PROF-04**: User can map trigger, reload, joystick, and X/Y/A/B to virtual joystick controls per profile.
- [ ] **PROF-05**: Desktop companion applies profile changes without requiring Android app rebuilds.
- [ ] **PROF-06**: Desktop companion stores a default visualizer profile that works immediately after pairing.

### Visualizer

- [ ] **VIS-01**: User can open a simple joystick visualizer that connects to the desktop companion pipeline.
- [ ] **VIS-02**: Visualizer displays trigger, reload, joystick, X/Y/A/B, and aim axes in real time.
- [ ] **VIS-03**: Visualizer displays recenter events and current aim-zero state.
- [ ] **VIS-04**: Visualizer displays Android connection, desktop virtual controller, packet stream, and rumble status.
- [ ] **VIS-05**: Visualizer includes a rumble test control that causes the physical gun motor to vibrate and shows ack/fail result.
- [ ] **VIS-06**: Visualizer displays latency and packet loss metrics for the current session.

### Performance and Reliability

- [ ] **PERF-01**: End-to-end input path can be measured from Android capture timestamp to desktop visualizer update.
- [ ] **PERF-02**: v1 visualizer path targets under 50 ms end-to-end latency during normal local Wi-Fi testing.
- [ ] **PERF-03**: Desktop drops stale or replayed UDP input frames instead of applying old aim/control data.
- [ ] **PERF-04**: Packet logs can be replayed in tests to verify parser, profile mapping, and visualizer output.
- [ ] **PERF-05**: Android and desktop expose enough diagnostic logs to distinguish gun, sensor, LAN, profile, and virtual-driver failures.

### Packaging and Documentation

- [ ] **PACK-01**: Repository documents the selected Android build toolchain and device testing workflow.
- [ ] **PACK-02**: Repository documents the selected Windows virtual HID strategy, driver signing requirements, and development setup.
- [ ] **PACK-03**: Repository documents the selected macOS virtual HID strategy, entitlement requirements, and development setup.
- [ ] **PACK-04**: Repository documents the LAN session protocol, packet schemas, pairing flow, and security model.
- [ ] **PACK-05**: Repository documents known limitations, including unsupported direct desktop Bluetooth and lack of game-specific presets in v1.

## Acceptance Criteria

- [ ] A physical iPega gun can connect to the Android host app.
- [ ] Android host app can show live gun controls and gyro samples.
- [ ] Desktop companion can pair to Android by QR or pairing code.
- [ ] Windows 11 x64 sees an OS-visible virtual gamepad-style joystick.
- [ ] macOS Apple Silicon sees an OS-visible virtual gamepad-style joystick.
- [ ] Visualizer shows live controls, mapped aim axes, recenter state, packet timing, and packet loss.
- [ ] Visualizer rumble test vibrates the physical gun and displays ack/fail.
- [ ] Normal local Wi-Fi visualizer path targets under 50 ms end-to-end latency.
- [ ] Reference APK/protocol findings are backed by local evidence or physical-device captures.

## v2 Requirements

### Desktop Bluetooth

- **BT2-01**: Desktop can connect directly to the physical iPega gun without Android as host.

### Additional Transports

- **TR2-01**: Android and desktop can communicate over Wi-Fi Direct.
- **TR2-02**: Android and desktop can communicate over Bluetooth/BLE transport when LAN is unavailable.

### Game Profiles

- **GP2-01**: User can select built-in profiles for specific games or emulators.
- **GP2-02**: User can import/export desktop mapping profiles.

### Multi-Device

- **MD2-01**: Desktop can pair and distinguish multiple gun devices.
- **MD2-02**: Visualizer can display multiple simultaneous gun streams.

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Direct desktop-to-gun Bluetooth in v1 | Android host preserves phone gyro role and reduces platform-specific Bluetooth work. |
| Cloud or internet relay | Local LAN keeps latency and security manageable. |
| Wired Android-to-desktop transport | User requested wireless. |
| Custom gun-specific HID descriptor | Regular gamepad/joystick HID has better compatibility for v1. |
| First-class game integrations | Visualizer validates the core pipeline first. |
| Multi-gun support | Single-device identity and timing must work first. |
| Replacing the original APK apps | Goal is a new host/desktop driver stack, not patching the old apps. |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DISC-01 | Phase 1 | Complete |
| DISC-02 | Phase 1 | Pending |
| DISC-03 | Phase 1 | Pending |
| DISC-04 | Phase 1 | Pending |
| DISC-05 | Phase 1 | Pending |
| DISC-06 | Phase 1 | Pending |
| DISC-07 | Phase 1 | Pending |
| ANDR-01 | Phase 2 | Pending |
| ANDR-02 | Phase 2 | Pending |
| ANDR-03 | Phase 2 | Pending |
| ANDR-04 | Phase 2 | Pending |
| ANDR-05 | Phase 2 | Pending |
| ANDR-06 | Phase 2 | Pending |
| ANDR-07 | Phase 4 | Pending |
| ANDR-08 | Phase 2 | Pending |
| TRAN-01 | Phase 3 | Pending |
| TRAN-02 | Phase 3 | Pending |
| TRAN-03 | Phase 3 | Pending |
| TRAN-04 | Phase 4 | Pending |
| TRAN-05 | Phase 4 | Pending |
| TRAN-06 | Phase 3 | Pending |
| TRAN-07 | Phase 4 | Pending |
| TRAN-08 | Phase 4 | Pending |
| TRAN-09 | Phase 4 | Pending |
| DESK-01 | Phase 4 | Pending |
| DESK-02 | Phase 6 | Pending |
| DESK-03 | Phase 7 | Pending |
| DESK-04 | Phase 5 | Pending |
| DESK-05 | Phase 6 | Pending |
| DESK-06 | Phase 7 | Pending |
| DESK-07 | Phase 5 | Pending |
| DESK-08 | Phase 5 | Pending |
| PROF-01 | Phase 8 | Pending |
| PROF-02 | Phase 8 | Pending |
| PROF-03 | Phase 8 | Pending |
| PROF-04 | Phase 8 | Pending |
| PROF-05 | Phase 8 | Pending |
| PROF-06 | Phase 8 | Pending |
| VIS-01 | Phase 9 | Pending |
| VIS-02 | Phase 9 | Pending |
| VIS-03 | Phase 9 | Pending |
| VIS-04 | Phase 9 | Pending |
| VIS-05 | Phase 9 | Pending |
| VIS-06 | Phase 9 | Pending |
| PERF-01 | Phase 9 | Pending |
| PERF-02 | Phase 9 | Pending |
| PERF-03 | Phase 4 | Pending |
| PERF-04 | Phase 10 | Pending |
| PERF-05 | Phase 10 | Pending |
| PACK-01 | Phase 10 | Pending |
| PACK-02 | Phase 6 | Pending |
| PACK-03 | Phase 7 | Pending |
| PACK-04 | Phase 10 | Pending |
| PACK-05 | Phase 10 | Pending |

**Coverage:**

- v1 requirements: 54 total
- Mapped to phases: 54
- Unmapped: 0

---
*Requirements defined: 2026-06-06*
*Last updated: 2026-06-06 after initialization*
