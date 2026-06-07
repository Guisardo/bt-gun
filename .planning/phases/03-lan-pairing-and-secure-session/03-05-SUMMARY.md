---
phase: 03-lan-pairing-and-secure-session
plan: 05
subsystem: reliable-control-channel-semantics
tags: [kotlin, android, desktop, websocket, heartbeat, diagnostics, profile-metadata]

requires:
  - phase: 03-lan-pairing-and-secure-session
    provides: Versioned proof-gated WSS control envelope core from Plan 03-04
provides:
  - Bidirectional heartbeat/liveness state with connected, degraded, and disconnected states
  - Minimal diagnostics model limited to session state, desktop identity suffix, heartbeat age, and last control error
  - Minimal profile metadata model limited to profileId, displayName, and revision
  - Expanded control envelope allowlist for heartbeat, diagnostics, profile metadata, and reserved haptic type
  - Android desktop-link state updates from heartbeat, diagnostics, and control errors
affects: [03-lan-pairing-and-secure-session, desktop-companion, android-host-session]

tech-stack:
  added: []
  patterns: [main-style TDD tests, monotonic elapsed-nanos liveness, minimal metadata payloads, reserved haptic boundary]

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/HeartbeatMonitor.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt
  modified:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt
    - android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt
    - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
    - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Heartbeat/liveness uses monotonic elapsed-nanos observations and exposes connected, degraded, and disconnected state."
  - "Diagnostics stay limited to session state, desktop identity suffix, heartbeat age, and last control error."
  - "Profile metadata stays limited to profileId, displayName, and revision; full profile mapping remains Phase 8."
  - "Haptic support remains a reserved empty-body envelope type with no payload, execution result, or phone feedback behavior."

patterns-established:
  - "HeartbeatMonitor: observe ping or pong, compute liveness at an injected elapsed-nanos timestamp."
  - "Control metadata models: keep Phase 3 payloads intentionally narrow and field-count tested."

requirements-completed: [TRAN-06]

duration: 18min
completed: 2026-06-07T23:14:18Z
---

# Phase 03 Plan 05: Heartbeat, Diagnostics, and Profile Metadata Summary

**Control channel heartbeat/liveness, minimal diagnostics, and minimal profile metadata without Phase 4 input or haptic execution behavior.**

## Performance

- **Duration:** 18 min
- **Started:** 2026-06-07T22:56:00Z
- **Completed:** 2026-06-07T23:14:18Z
- **Tasks:** 2 completed
- **Files modified:** 8 code/test files, plus planning metadata

## Accomplishments

- Added RED tests first for heartbeat state transitions, bidirectional ping/pong refresh, diagnostics field limits, profile metadata field limits, expanded control message types, and reserved haptic body rejection.
- Implemented `HeartbeatMonitor` with deterministic elapsed-nanos inputs and connected/degraded/disconnected thresholds.
- Added minimal `ControlDiagnostics` and `ProfileMetadata` models and expanded desktop/Android `ControlMessageType` allowlists for heartbeat, diagnostics, and profile metadata.
- Wired Android `DesktopControlClient` to keep `DesktopLinkState` fresh from heartbeat observations, diagnostics, trust mismatch, close, and last control error.
- Preserved Phase 4 boundaries: no UDP input parsing, packet timing metrics, haptic payload fields, execution result semantics, or phone feedback behavior.

## Task Commits

1. **Task 1: RED - add heartbeat, diagnostics, and profile metadata tests** - `fa61820` (test)
2. **Task 2: GREEN - implement heartbeat/liveness, diagnostics, and profile metadata** - `5c3a52d` (feat)

## Verification

- RED desktop: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ControlChannel*'` failed on missing `HeartbeatMonitor`, `LivenessState`, `ControlDiagnostics`, `ProfileMetadata`, and heartbeat/diagnostics/profile enum values.
- RED Android: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DesktopControlClient*'` failed on missing heartbeat link-state methods, `ControlDiagnostics`, `ProfileMetadata`, and heartbeat/diagnostics/profile enum values.
- Focused desktop GREEN: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ControlChannel*'` passed.
- Focused Android GREEN: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DesktopControlClient*' --tests '*DashboardState*'` passed.
- Wave closeout desktop: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test` passed.
- Wave closeout Android: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` passed.
- Boundary grep: `rg -n "packet loss|jitter|frame rate|visualizer latency|input stream metric|haptic strength|haptic duration|haptic ttl|haptic ack|haptic fail|phone vibration" desktop-companion/src android-host/app/src docs/protocol/lan-pairing-v1.md` returned no matches.

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/HeartbeatMonitor.kt` - bidirectional heartbeat/liveness state machine with elapsed-nanos thresholds.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt` - diagnostics payload with only the four allowed Phase 3 fields.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt` - profile metadata payload with only the three allowed Phase 3 fields.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` - adds heartbeat, diagnostics, and profile metadata control message types.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` - RED/GREEN coverage for liveness, diagnostics, profile metadata, and haptic reservation.
- `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` - mirrors expanded control message type allowlist.
- `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` - updates visible link state from heartbeat, diagnostics, close, trust mismatch, and last control error.
- `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt` - Android control client coverage for heartbeat mapping, diagnostics, profile metadata, and control type allowlist.

## Decisions Made

- Use `observePing` and `observePong` as equivalent freshness signals so either direction refreshes liveness.
- Keep diagnostics/profile metadata field-count tested through reflection to prevent accidental Phase 4/profile-mapping creep.
- Keep Android metadata helper models local to the session package for now instead of creating a shared module before the control protocol stabilizes.

## Deviations from Plan

None - product scope executed as written.

## Issues Encountered

- Sandboxed Gradle still could not create local file-lock sockets, so required Gradle commands were rerun with approved escalation and normal Gradle behavior.
- `gsd-tools` was not on PATH. The local shim at `~/.codex/gsd-core/bin/gsd-tools.cjs` handled plan advance, session update, roadmap update, and requirement completion; metric/decision helper calls rejected the expected positional argument shape, so the small state/roadmap deltas were patched directly.

## Auth Gates

None.

## Known Stubs

None. Nullable heartbeat age and last control error represent absent state, not placeholder UI data.

## TDD Gate Compliance

- RED commit exists before GREEN: `fa61820`
- GREEN commit exists after RED: `5c3a52d`
- Refactor commit: none needed.

## Threat Flags

None. The changed trust-boundary surface matches the plan threat model: liveness state is timeout-bounded, diagnostics exclude secrets and later transport metrics, and profile metadata excludes mappings.

## Next Phase Readiness

Plan 03-06 can use the expanded control message allowlist, `HeartbeatMonitor`, `ControlDiagnostics`, `ProfileMetadata`, and Android link-state hooks when wiring the desktop companion launch and pairing/control window lifecycle. UDP input frames, packet metrics, haptic command payloads, haptic execution, and execution result semantics remain Phase 4 scope.

## Self-Check: PASSED

- Found created desktop control files: `HeartbeatMonitor.kt`, `ControlDiagnostics.kt`, and `ProfileMetadata.kt`.
- Found task commits `fa61820` and `5c3a52d` in git log.
- Focused and full desktop/Android unit suites passed.
- Boundary grep returned no forbidden Phase 4 or phone feedback execution terms.

---
*Phase: 03-lan-pairing-and-secure-session*
*Completed: 2026-06-07*
