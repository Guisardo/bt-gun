# Phase 2: Android Host Live Input - Context

**Gathered:** 2026-06-06T21:58:03Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 builds the Android host app live-input path. It connects to the physical iPega gun over the verified BLE `fff0`/`fff3` path, captures gun controls and Android motion data, emits separate live gun/motion/status streams with comparable timestamps, supports reload-hold recenter, and shows an operational Android session dashboard. It does not build LAN pairing, UDP/TCP transport, desktop virtual controllers, desktop profiles, or desktop haptic commands.

</domain>

<decisions>
## Implementation Decisions

### Gun Connection Flow
- **D-01:** Use auto-connect as the normal path after Bluetooth and permission checks pass: scan for `ARGunGame` advertising service `fff0`, connect immediately, and show progress.
- **D-02:** Auto-reconnect while the active session is running. Show connection state and last error instead of silently retrying.
- **D-03:** Use a guided first-run permission gate for Bluetooth, location, and sensor permissions. Explain missing state only when blocked.
- **D-04:** Own the live BLE connection in a visible foreground service so it can survive screen changes and backgrounding.

### Normalized Live Sample Shape
- **D-05:** Keep separate internal streams for gun, motion, and status in Phase 2. Merge into transport frames in later LAN phases.
- **D-06:** Use a common envelope across streams: `stream`, per-stream `seq`, `capture_elapsed_nanos`, and `emitted_elapsed_nanos`.
- **D-07:** Product events are default. A debug-provenance toggle exposes Phase 1 details such as raw ASCII/hex, BLE characteristic, `clue_id`, `capture_id`, and semantic confidence.
- **D-08:** Preserve physical stick direction events as `stick_left`, `stick_right`, `stick_up`, and `stick_down`, and derive X/Y axes for local preview.

### Motion Aim and Recenter
- **D-09:** Select motion provider automatically: game rotation vector first, then rotation vector, then gyro plus gravity/accelerometer, then gravity/accelerometer tilt fallback.
- **D-10:** Android applies local mapped aim in Phase 2 for preview/calibration only. Final desktop/game aim mapping still belongs to desktop profiles.
- **D-11:** Reload down/up events always emit. If reload remains held for two seconds, also emit a `recenter` event without consuming normal reload semantics.
- **D-12:** Show immediate recenter feedback with the current zero/baseline timestamp and brief recenter status.

### Android Status Surface
- **D-13:** Main session screen is an operational dashboard showing connection state, last gun event, motion provider, preview aim, recenter state, foreground-service status, and current error line.
- **D-14:** Put raw BLE/provenance, permission state, and GATT status behind expandable debug panels.
- **D-15:** Show inactive placeholder cards for desktop link and packet stream: "not built yet" / "pending Phase 3 or Phase 4".
- **D-16:** Haptic status in Phase 2 means phone vibrator capability and last local test. Desktop-origin haptic commands wait for Phase 4.

### the agent's Discretion
- Choose exact Android module/package split, UI toolkit, foreground-service notification copy, stream class names, and local preview math during planning.
- Choose exact debug panel layout, as long as product events stay primary and provenance remains available.
- Choose exact sample rates and buffering strategy during planning, as long as monotonic capture/emitted timestamps remain present.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` — Phase 2 goal, requirements, success criteria, and later-phase boundaries.
- `.planning/REQUIREMENTS.md` — `ANDR-01` through `ANDR-06` and `ANDR-08`; `ANDR-07` deferred to Phase 4.
- `.planning/PROJECT.md` — Android host responsibility, desktop-profile ownership of final aim mapping, haptic fallback, and v1 constraints.
- `.planning/STATE.md` — current project state, Phase 1 decisions, and Phase 2 concerns.

### Research Context
- `.planning/research/STACK.md` — Android/Kotlin, Bluetooth APIs, SensorManager, foreground active-session model, and timestamp constraints.
- `.planning/research/ARCHITECTURE.md` — Android gun adapter, sensor capture boundary, normalized pipeline, and recenter flow.
- `.planning/research/PITFALLS.md` — BLE permission pitfalls, sensor timestamp/provider pitfalls, recenter feedback, and haptic boundaries.
- `.planning/research/FEATURES.md` — Android host feature priorities and deferred transport/desktop scope.
- `.planning/research/SUMMARY.md` — v1 architecture summary and Android host implications.

### Phase 1 Evidence
- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md` — locked Phase 1 evidence rules and downstream boundary notes.
- `docs/protocol/ipega-phase1-clues.md` — static clue ids for BLE, input, Classic fallback, and haptics.
- `docs/protocol/ipega-phase1-hardware.md` — physical GATT observations, `fff3` control mappings, no standard `InputDevice`, and phone vibration proof.
- `docs/protocol/ipega-phase1-haptics.md` — v1 phone haptic decision and deferred physical motor status.
- `docs/evidence/manifests/phase1-captures.jsonl` — committed capture manifest pointers tying evidence to fixtures.
- `fixtures/ipega/normalized/README.md` — normalized fixture schema and evidence-link rules.
- `fixtures/ipega/normalized/handshake.jsonl` — BLE advertisement, GATT connect, `fff1`, `fff3`, and `fff5` fixture rows.
- `fixtures/ipega/normalized/trigger.jsonl` — trigger down/up candidate events.
- `fixtures/ipega/normalized/reload.jsonl` — reload down/up events used for recenter gesture design.
- `fixtures/ipega/normalized/joystick.jsonl` — digital stick direction mappings.
- `fixtures/ipega/normalized/buttons-xyab.jsonl` — X/Y/A/B mappings.
- `fixtures/ipega/normalized/haptics.jsonl` — phone haptic fixture.

### Existing Android Diagnostic
- `android-diagnostic/README.md` — diagnostic scope, build boundary, capture workflow, and dependency review gate.
- `android-diagnostic/SPEC.md` — diagnostic app out-of-scope boundary; do not treat it as production host architecture.
- `android-diagnostic/app/src/main/AndroidManifest.xml` — current Bluetooth/location/vibrate permissions and BLE feature declaration.
- `android-diagnostic/app/build.gradle.kts` — current Android app module coordinates, SDKs, and Kotlin/JVM settings.
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` — reusable BLE scan/connect/GATT notification patterns, serialized GATT operations, permission checks, phone vibration proof, and diagnostic JSON logging.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` has working BLE scan, `connectGatt`, service discovery, target characteristic inspection, notification subscription, serialized GATT operation queue, permission-state reporting, and phone vibration calls.
- `android-diagnostic/app/src/main/AndroidManifest.xml` has the relevant legacy Bluetooth, Android 12+ Bluetooth, location, and vibration permission surface to carry forward.
- `fixtures/ipega/normalized/*.jsonl` provide expected control semantics for Phase 2 parser tests without requiring physical hardware.
- `tools/phase1/validate-fixtures.mjs` shows the existing evidence-link validation style for fixture-backed regression checks.

### Established Patterns
- Hardware-specific BLE details must stay inside the Android gun adapter and convert to normalized events before leaving that boundary.
- Phase 1 provenance remains valuable in debug mode, but product UI should not make raw BLE bytes the default surface.
- Large raw captures stay ignored; committed docs and fixtures preserve sanitized, reproducible pointers.
- Android owns motion capture and recenter state; desktop profiles own final game/platform aim mapping.

### Integration Points
- New production Android host code should either reuse diagnostic BLE logic carefully or move it behind a production adapter boundary.
- Motion sensor capture is currently planned but not implemented; Phase 2 must introduce SensorManager provider selection and sample emission.
- Foreground service is a new production responsibility; the diagnostic Activity-owned connection is not enough for Phase 2.

</code_context>

<specifics>
## Specific Ideas

- User chose auto-connect and auto-reconnect rather than a manual device picker or diagnostic-first product flow.
- User chose foreground-service ownership for the gun connection.
- User chose separate streams rather than one unified ordered stream for Phase 2.
- User chose Android-side local mapped aim now; capture this as preview/calibration mapping only so it does not conflict with desktop-owned final profiles.
- User wants desktop link and packet stream represented as inactive placeholders instead of hidden or faked.

</specifics>

<deferred>
## Deferred Ideas

- LAN pairing and authenticated desktop session remain Phase 3.
- UDP input frame transport, reliable control channel, and desktop-origin phone haptic commands remain Phase 4.
- Final desktop aim profiles and game/platform mapping remain Phase 8.
- Physical gun motor rumble remains deferred/v2 unless later evidence proves a command path.

</deferred>

---

*Phase: 2-Android Host Live Input*
*Context gathered: 2026-06-06T21:58:03Z*
