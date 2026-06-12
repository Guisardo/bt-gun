# Phase 8: Desktop Profiles and Mapping - Context

**Gathered:** 2026-06-12T01:02:10Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 8 delivers configurable profiles for aim feel and button mapping, but ownership is intentionally rerouted from desktop to Android. Android stores, edits, validates, and applies profiles for the live gun stream. The desktop companion no longer provides profile editing; it shows active Android profile metadata/revision, consumes the minimal mapped stream, and remains the Windows LAN fallback publisher plus diagnostics surface.

This phase must update stale "desktop profiles" wording in planning/docs before implementation or as the first plan task. It must not build Phase 9 visualizer UI, game-specific presets, profile import/export, direct desktop-to-gun Bluetooth, physical gun motor rumble, or new macOS virtual HID work.

</domain>

<decisions>
## Implementation Decisions

### Profile Ownership and Stream Shape
- **D-01:** Android owns all profiles. Android stores and applies profiles for gun controls and motion aim; this replaces the prior desktop-owned profile direction.
- **D-02:** Android sends the minimal mapped product stream by default.
- **D-03:** Android exposes an in-app debug option/button that enables sending raw motion/provider data in addition to the mapped stream.
- **D-04:** The raw-data debug toggle is Android-session controlled. Desktop cannot request raw extras by itself in Phase 8.
- **D-05:** Remove desktop profile editing from Phase 8. Desktop only displays active Android profile id/name/revision and mapped-stream status.

### Aim Feel Model
- **D-06:** The built-in default uses a responsive balanced feel: low smoothing, small dead zone, sensitivity `1.0`, and inversion off.
- **D-07:** Normal profile editing includes shared aim settings, and the advanced editor surfaces provider-specific overrides for calibrated/fused rotation, gyro/raw aim, and accelerometer/gravity tilt fallback.
- **D-08:** Smoothing uses a latency-capped adaptive filter.
- **D-09:** Adaptive smoothing must report or bound added filter lag. If the filter threatens the latency target or feels laggy, the implementation must auto-fallback to a Low smoothing behavior.

### Button Remap Rules
- **D-10:** Phase 8 supports limited remapping: physical trigger, reload, X, Y, A, and B can map among the v1 virtual button outputs.
- **D-11:** Stick axes and aim axes stay semantic. Do not allow arbitrary physical-button-to-axis or axis-to-button remaps in Phase 8.
- **D-12:** The hold-to-recenter gesture can move from physical reload to another physical button.
- **D-13:** Each saved profile must keep exactly one hold-to-recenter physical button.
- **D-14:** Android validates profiles and blocks save for missing required controls, invalid duplicate mappings, or missing recenter control. Do not silently auto-repair invalid mappings.

### Profile Storage and Android UI
- **D-15:** Android ships an immutable built-in Default Visualizer profile.
- **D-16:** Users can create local Android profiles by duplicating a profile, then rename, edit, select, and delete user-created profiles.
- **D-17:** Profile persistence is local Android storage in Phase 8. Reset restores default-derived settings.
- **D-18:** Import/export is deferred.
- **D-19:** The Android profile editor is advanced: it should expose full per-provider overrides in the main profile editing flow, not hide them behind a future phase.

### the agent's Discretion
- Choose exact Android storage API, schema versioning shape, profile ids, revision semantics, validation error labels, UI layout, adaptive smoothing algorithm, filter constants, default dead-zone value, and raw debug payload fields, as long as the decisions above and v1 HID shape remain true.
- Choose exact desktop read-only metadata display and mapped-stream diagnostics, provided desktop profile editing is not reintroduced.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition and Reroute Context
- `.planning/ROADMAP.md` - Phase 8 still names desktop profiles; planner must reconcile this with the Android-owned profile decision in this context.
- `.planning/REQUIREMENTS.md` - `PROF-01` through `PROF-06` are still desktop-worded; planner must update requirements or explicitly plan the wording correction before code work.
- `.planning/PROJECT.md` - prior key decision says desktop stores aim profiles; Phase 8 context supersedes that after the Android HID reroute.
- `.planning/STATE.md` - current focus and accumulated Phase 7 Android Bluetooth HID decisions.

### Prior Phase Context
- `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md` - Android Bluetooth HID is the primary macOS input path, Android owns HID descriptor/report packing, and desktop is diagnostics/fallback for this path.
- `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md` - Windows VHF remains the OS-visible fallback path and currently consumes semantic controller state.
- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md` - locked v1 backend descriptor, semantic controller state, capability flags, and profile/visualizer boundaries.
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - UDP input stream, raw motion/provider payload, haptic transport, and stale-stream behavior.

### Architecture and Protocol
- `.planning/research/ARCHITECTURE.md` - historical desktop-owned profile pattern and boundaries that Phase 8 now intentionally reroutes.
- `.planning/research/STACK.md` - Android native, SensorManager, LAN transport, Windows VHF, and visualizer sequencing.
- `.planning/research/PITFALLS.md` - "profiles baked into Android" warning; planner must address why Phase 7 Android HID changes that tradeoff.
- `docs/protocol/lan-pairing-v1.md` - current minimal `profile_metadata` and raw UDP motion payload contract.
- `docs/setup/android-bluetooth-hid-gamepad.md` - Android HID report setup, current browser mapping intent, and stable input descriptor constraints.

### Android Code
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - `GunInputState`, `MotionSample`, raw aim, calibrated aim, provider, and calibration metadata.
- `android-host/app/src/main/java/com/btgun/host/motion/AimCalibration.kt` - existing calibration model and normalized aim mapping foundations.
- `android-host/app/src/main/java/com/btgun/host/motion/PreviewAimMapper.kt` - Android-local preview aim behavior and fallback raw aim computation.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` - Android HID report packing path that must consume mapped profile output for Bluetooth HID.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt` - locked Android HID descriptor shape to preserve.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - foreground service owner for gun input, motion, recenter, HID mode, LAN diagnostics, and haptics.
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - Android dashboard state surface to extend with profile state, validation errors, and debug raw toggle.

### Desktop Code
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt` - current minimal metadata model to keep read-only.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - current default profile metadata envelope and control/UDP ownership.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` - semantic mapped state published to desktop backends.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` - current raw UDP to semantic mapping; Phase 8 must adapt this boundary to Android-mapped input.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` - locked v1 buttons/axes descriptor.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - current desktop status UI that should show active Android profile metadata, not editing controls.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AimCalibration` and `PreviewAimMapper`: Android already has calibration and preview-aim primitives that can feed the profile mapper.
- `BtGunHidReportPacker`: Android HID output already exists and should receive mapped profile state for the primary macOS path.
- `ProfileMetadata`: desktop already has a minimal profile id/name/revision concept suitable for read-only display.
- `SemanticControllerState` and `btGunV1Descriptor`: desktop backend contracts remain useful as the mapped-state target shape.
- `ControlServer`: existing control channel can carry profile metadata and stream configuration, but Phase 8 should avoid making desktop the profile authority.

### Established Patterns
- Hardware/platform-specific details stay behind adapters and expose normalized status.
- Android must show honest blocked/invalid state instead of silently guessing capability or fixing bad mappings.
- Secret and raw debug data stay off by default.
- v1 exposes one regular gamepad-like joystick shape with six buttons and four axes.
- Replay/fake paths are useful for tests, but visualizer/live acceptance remains Phase 9.

### Integration Points
- Add Android profile model, storage, validator, mapper, and UI under the Android host app.
- Insert Android profile mapping before Android Bluetooth HID report packing and before any LAN mapped stream output.
- Extend Android session/dashboard state with active profile, validation errors, recenter control, adaptive smoothing status, and debug raw-data toggle.
- Update desktop companion to display Android profile metadata/revision and mapped-stream diagnostics only.
- Update LAN protocol/docs/tests so minimal mapped input is the default product stream and raw provider/motion data is debug-only.

</code_context>

<specifics>
## Specific Ideas

- The user explicitly chose Android-owned profiles for all paths.
- The product stream should be minimal by default; raw data is available only when the Android debug option/button is enabled.
- The Android editor should be advanced enough to expose provider-specific overrides in the main profile flow.
- Recenter is user-movable but mandatory: exactly one physical button must hold-to-recenter in every saved profile.

</specifics>

<deferred>
## Deferred Ideas

- Profile import/export is deferred to a later game-profile or v2 phase.
- Game-specific preset browser remains out of Phase 8.
- Phase 9 owns visualizer UI, latency dashboard, packet-loss dashboard, recenter display, and haptic test controls.
- Phase 10 owns broader replay diagnostics and v1 documentation.
- Direct desktop-to-gun Bluetooth remains v2/deferred.
- Physical gun motor rumble remains v2/deferred.

</deferred>

---

*Phase: 8-Desktop Profiles and Mapping*
*Context gathered: 2026-06-12T01:02:10Z*
