# Requirements: Bluetooth Gun Driver

**Defined:** 2026-06-06
**Core Value:** Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive motion aiming and v1 phone haptic feedback.

## User Stories

- As a user with the discontinued iPega AR gun, I can connect the gun to an Android phone and use the phone itself as a wireless Bluetooth HID gamepad for a desktop computer.
- As a desktop user, I can see the gun stream as a normal gamepad-style joystick device through Android Bluetooth HID on macOS Apple Silicon, with the completed Windows virtual joystick path retained as fallback.
- As a player, I can aim with Android phone motion sensors, press the gun controls, recenter aim, and feel phone haptic feedback from the desktop.
- As a developer/debugger, I can inspect connection state, packet timing, mapped axes, button states, and haptic results in a simple joystick visualizer.

## v1 Requirements

### Discovery

- [x] **DISC-01**: Developer can inventory every local reference APK/XAPK under `docs/refs/` with package name, target SDK, permissions, app type, and validity status.
- [x] **DISC-02**: Developer can run an Android diagnostic that reports whether the iPega gun appears as a standard Android input device.
- [x] **DISC-03**: Developer can run an Android diagnostic that reports visible Bluetooth Classic and BLE services for the physical iPega gun.
- [x] **DISC-04**: Developer can capture and store raw Bluetooth traffic or app-observed frames for trigger, reload, joystick, X/Y/A/B, and haptic/deferred motor tests.
- [x] **DISC-05**: Developer can map every physical gun control to a normalized event with down/up or axis semantics.
- [x] **DISC-06**: Developer can verify Android phone vibration as the v1 feedback path and document physical gun motor rumble as deferred.
- [x] **DISC-07**: Developer can save raw and normalized protocol fixtures for regression tests without requiring the physical gun.

### Android Host

- [x] **ANDR-01**: User can grant required Android Bluetooth, nearby device, sensor, and LAN permissions from the Android host app.
- [x] **ANDR-02**: User can connect the Android host app to the physical iPega gun.
- [x] **ANDR-03**: Android host app emits normalized events for trigger, reload, joystick, X/Y/A/B, and connection state.
- [x] **ANDR-04**: Android host app samples motion aim data with monotonic capture timestamps, using rotation-vector/game-rotation-vector, gyroscope, accelerometer, and gravity providers as available.
- [x] **ANDR-05**: Android host app merges gun input and motion sensor data into ordered normalized input samples with sensor provider and capability metadata.
- [x] **ANDR-06**: Holding reload for two seconds recenters motion aim without preventing normal reload press/release events.
- [x] **ANDR-07**: Android host app can receive a haptic command from desktop and vibrate the Android phone.
- [x] **ANDR-08**: Android host app shows active session status for gun connection, desktop link, packet stream, and haptic feedback.
- [x] **ANDR-09**: Android host app can register or advertise as a Bluetooth HID gamepad using the phone's supported HID peripheral role, with a clear blocked state when the device/OEM does not support the role.
- [x] **ANDR-10**: Android host app maps normalized gun controls and Android motion aim into regular gamepad-style HID input reports without requiring a macOS virtual HID driver.
- [x] **ANDR-11**: Android host app can receive Bluetooth HID output or rumble reports from the desktop OS and map valid output to v1 phone haptics, or report the platform limitation clearly.

### LAN Session

Retained for diagnostics, pairing/control experiments, and Windows virtual-controller fallback. The primary no-subscription macOS v1 path is Android-to-desktop Bluetooth HID, not LAN-to-macOS virtual HID.

- [x] **TRAN-01**: Desktop companion can create a local pairing session and display a QR code plus pairing-code fallback.
- [x] **TRAN-02**: Android host app can pair to the desktop companion using QR code or pairing code without manual IP entry in the normal path.
- [x] **TRAN-03**: Pairing creates an authenticated local session with a short-lived one-time secret and replay protection.
- [x] **TRAN-04**: Android host app streams high-rate input and motion samples to desktop using versioned UDP input frames.
- [x] **TRAN-05**: UDP input frames include sequence number, session id, capture timestamp, send timestamp, button bitmask, axes, motion payload, and motion provider/capability flags.
- [x] **TRAN-06**: Android and desktop maintain a reliable control channel for pairing state, heartbeat, diagnostics, profile metadata, and haptic commands.
- [x] **TRAN-07**: Desktop can send a haptic command with command id, strength, duration, expiry/TTL, and optional pattern.
- [x] **TRAN-08**: Android host app returns haptic acknowledgement or failure status to desktop.
- [x] **TRAN-09**: Desktop and Android can recover cleanly from LAN disconnect without playing stale haptic commands.

### Desktop / OS-visible Controller

- [x] **DESK-01**: Desktop companion can receive, validate, decrypt/authenticate, and parse normalized Android input frames.
- [x] **DESK-02**: Desktop companion can expose a regular gamepad-style virtual joystick on Windows 11 x64.
- [x] **DESK-03**: macOS Apple Silicon can see the Android phone as a regular Bluetooth HID gamepad-style joystick for the gun stream, avoiding paid Apple virtual HID entitlements in the primary v1 path.
- [x] **DESK-04**: Virtual joystick descriptor exposes trigger, reload, joystick axes, X/Y/A/B buttons, and aim axes.
- [x] **DESK-05**: Windows virtual joystick path can receive desktop rumble/output requests and map them to v1 phone haptic commands.
- [x] **DESK-06**: macOS Bluetooth HID gamepad path can receive OS output or rumble reports and route them to Android phone haptics, or clearly report the platform limitation while preserving v1 phone haptic support.
- [x] **DESK-07**: Desktop companion exposes backend capability flags for buttons, axes, haptic feedback, output reports, and platform limitations.
- [x] **DESK-08**: Developer can run a fake-input virtual controller smoke test on both Windows and macOS before using the real Android stream.

### Profiles

- [x] **PROF-01**: Android stores local profiles for gun controls, motion aim, and active-profile revision; desktop only displays active Android profile metadata.
- [x] **PROF-02**: Android profile editor configures motion aim mapping to joystick axes, including provider-specific tuning for calibrated/fused rotation, gyro/raw aim, and accelerometer/gravity tilt fallback.
- [x] **PROF-03**: Android profile settings include sensitivity, inversion, dead zone, and smoothing for shared aim behavior and provider overrides.
- [x] **PROF-04**: Android limited remap covers trigger, reload, X, Y, A, and B while stick axes and aim axes remain semantic.
- [x] **PROF-05**: Android profile changes apply at runtime without Android rebuilds or any desktop editor.
- [x] **PROF-06**: Android ships an immutable `Default Visualizer` profile that works immediately after pairing; desktop only displays active Android profile id, name, revision, source, and mapped-stream state.

### Visualizer

- [x] **VIS-01**: User can open a simple joystick visualizer that connects to the desktop companion pipeline.
- [x] **VIS-02**: Visualizer displays trigger, reload, joystick, X/Y/A/B, and aim axes in real time.
- [x] **VIS-03**: Visualizer displays recenter events and current aim-zero state.
- [x] **VIS-04**: Visualizer displays Android connection, desktop virtual controller, packet stream, and haptic status.
- [x] **VIS-05**: Visualizer includes a haptic test control that vibrates the Android phone and shows ack/fail result.
- [x] **VIS-06**: Visualizer displays latency and packet loss metrics for the current session.

### Performance and Reliability

- [x] **PERF-01**: End-to-end input path can be measured from Android capture timestamp to desktop visualizer update.
- [x] **PERF-02**: v1 visualizer path targets under 50 ms end-to-end latency during normal local Wi-Fi testing.
- [x] **PERF-03**: Desktop drops stale or replayed UDP input frames instead of applying old aim/control data.
- [x] **PERF-04**: Packet logs can be replayed in tests to verify parser, profile mapping, and visualizer output.
- [x] **PERF-05**: Android and desktop expose enough diagnostic logs to distinguish gun, sensor, LAN, profile, and virtual-driver failures.

### Packaging and Documentation

- [x] **PACK-01**: Repository documents the selected Android build toolchain and device testing workflow.
- [x] **PACK-02**: Repository documents the selected Windows virtual HID strategy, driver signing requirements, and development setup.
- [x] **PACK-03**: Repository documents the selected macOS strategy: Android Bluetooth HID gamepad as the primary no-subscription path, with CoreHID/DriverKit virtual HID work retained only as blocked/fallback evidence unless entitlement-capable proof exists.
- [x] **PACK-04**: Repository documents the LAN session protocol, packet schemas, pairing flow, and security model.
- [x] **PACK-05**: Repository documents known limitations, including unsupported direct desktop-to-gun Bluetooth, Android Bluetooth HID device compatibility risk, and lack of game-specific presets in v1.
- [x] **PACK-06**: Repository documents Android Bluetooth HID gamepad setup, phone compatibility checks, macOS/Windows pairing flow, HID report descriptors, output-report behavior, and fallback routing to the completed Windows virtual joystick path.

## Acceptance Criteria

- [ ] A physical iPega gun can connect to the Android host app.
- [ ] Android host app can show live gun controls and motion sensor samples, including which motion provider is active.
- [ ] Desktop companion can pair to Android by QR or pairing code.
- [ ] Windows 11 x64 sees an OS-visible virtual gamepad-style joystick.
- [x] macOS Apple Silicon sees the Android phone as an OS-visible Bluetooth HID gamepad-style joystick.
- [x] If Android Bluetooth HID gamepad mode is blocked on the phone or macOS, the project can fall back to the completed Windows virtual joystick path without discarding that work.
- [ ] Visualizer shows live controls, mapped aim axes, recenter state, packet timing, and packet loss.
- [ ] Visualizer haptic test vibrates the Android phone and displays ack/fail.
- [ ] Normal local Wi-Fi visualizer path targets under 50 ms end-to-end latency.
- [ ] Reference APK/protocol findings are backed by local evidence or physical-device captures.

## v2 Requirements

### Desktop Bluetooth

- **BT2-01**: Desktop can connect directly to the physical iPega gun without Android as host.

### Additional Transports

- **TR2-01**: Android and desktop can communicate over Wi-Fi Direct.
- **TR2-02**: Android and desktop can communicate over non-HID Bluetooth/BLE transport when LAN is unavailable.

### Game Profiles

- **GP2-01**: User can select built-in profiles for specific games or emulators.
- **GP2-02**: User can import/export desktop mapping profiles.

### Multi-Device

- **MD2-01**: Desktop can pair and distinguish multiple gun devices.
- **MD2-02**: Visualizer can display multiple simultaneous gun streams.

### Physical Gun Motor Rumble

- **RUMBLE2-01**: Android host can identify and activate the physical iPega gun vibration motor.
- **RUMBLE2-02**: Desktop rumble commands can target the physical gun motor when the command path is proven.

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Direct desktop-to-gun Bluetooth in v1 | Android host preserves the phone motion-sensor role and reduces platform-specific gun Bluetooth work. Android-to-desktop Bluetooth HID is now the primary macOS path. |
| Cloud or internet relay | Local LAN keeps latency and security manageable. |
| Wired Android-to-desktop transport | User requested wireless. |
| Custom gun-specific HID descriptor | Regular gamepad/joystick HID has better compatibility for v1. |
| Physical gun motor rumble | Deferred because no verified physical gun motor command path exists; v1 uses confirmed Android phone vibration instead. |
| First-class game integrations | Visualizer validates the core pipeline first. |
| Multi-gun support | Single-device identity and timing must work first. |
| Replacing the original APK apps | Goal is a new host/desktop driver stack, not patching the old apps. |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DISC-01 | Phase 1 | Complete |
| DISC-02 | Phase 1 | Complete |
| DISC-03 | Phase 1 | Complete |
| DISC-04 | Phase 1 | Complete |
| DISC-05 | Phase 1 | Complete |
| DISC-06 | Phase 1 | Complete |
| DISC-07 | Phase 1 | Complete |
| ANDR-01 | Phase 2 | Complete |
| ANDR-02 | Phase 2 | Complete |
| ANDR-03 | Phase 2 | Complete |
| ANDR-04 | Phase 2 | Complete |
| ANDR-05 | Phase 2 | Complete |
| ANDR-06 | Phase 2 | Complete |
| ANDR-07 | Phase 4 | Complete |
| ANDR-08 | Phase 2 | Complete |
| ANDR-09 | Phase 7 | Complete |
| ANDR-10 | Phase 7 | Complete |
| ANDR-11 | Phase 7 | Complete |
| TRAN-01 | Phase 3 | Complete |
| TRAN-02 | Phase 3 | Complete |
| TRAN-03 | Phase 3 | Complete |
| TRAN-04 | Phase 4 | Complete |
| TRAN-05 | Phase 4 | Complete |
| TRAN-06 | Phase 3 | Complete |
| TRAN-07 | Phase 4 | Complete |
| TRAN-08 | Phase 4 | Complete |
| TRAN-09 | Phase 4 | Complete |
| DESK-01 | Phase 4 | Complete |
| DESK-02 | Phase 6 | Complete |
| DESK-03 | Phase 7 | Complete |
| DESK-04 | Phase 5 | Complete |
| DESK-05 | Phase 6 | Complete |
| DESK-06 | Phase 7 | Complete |
| DESK-07 | Phase 5 | Complete |
| DESK-08 | Phase 5 | Complete |
| PROF-01 | Phase 8 | Complete |
| PROF-02 | Phase 8 | Complete |
| PROF-03 | Phase 8 | Complete |
| PROF-04 | Phase 8 | Complete |
| PROF-05 | Phase 8 | Complete |
| PROF-06 | Phase 8 | Complete |
| VIS-01 | Phase 9 | Complete |
| VIS-02 | Phase 9 | Complete |
| VIS-03 | Phase 9 | Complete |
| VIS-04 | Phase 9 | Complete |
| VIS-05 | Phase 9 | Complete |
| VIS-06 | Phase 9 | Complete |
| PERF-01 | Phase 9 | Complete |
| PERF-02 | Phase 9 | Complete |
| PERF-03 | Phase 4 | Complete |
| PERF-04 | Phase 10 | Complete |
| PERF-05 | Phase 10 | Complete |
| PACK-01 | Phase 10 | Complete |
| PACK-02 | Phase 6 | Complete |
| PACK-03 | Phase 7 | Complete |
| PACK-04 | Phase 10 | Complete |
| PACK-05 | Phase 10 | Complete |
| PACK-06 | Phase 7 | Complete |
| BT2-01 | v2 | Deferred |
| TR2-01 | v2 | Deferred |
| TR2-02 | v2 | Deferred |
| GP2-01 | v2 | Deferred |
| GP2-02 | v2 | Deferred |
| MD2-01 | v2 | Deferred |
| MD2-02 | v2 | Deferred |
| RUMBLE2-01 | v2 | Deferred |
| RUMBLE2-02 | v2 | Deferred |

**Coverage:**

- v1 requirements: 58 total
- Mapped to phases: 58
- Unmapped: 0
- v2 requirements: 9 total
- Deferred/mapped: 9

---
*Requirements defined: 2026-06-06*
*Last updated: 2026-06-15 after Phase 10 Plan 10-06 v1 docs guard closeout*
