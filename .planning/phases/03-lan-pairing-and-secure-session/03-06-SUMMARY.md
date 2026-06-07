---
phase: 03-lan-pairing-and-secure-session
plan: 06
subsystem: desktop-pairing-ui
tags: [kotlin, swing, desktop-companion, pairing, control-channel]

requires:
  - phase: 03-lan-pairing-and-secure-session
    provides: Pairing session registry, QR/manual payloads, proof-gated control server, heartbeat diagnostics from Plans 03-01 through 03-05
provides:
  - Runnable desktop companion entry point for `gradle -p desktop-companion run`
  - Swing pairing/control window with start/restart, QR, manual fallback, endpoint, countdown, and session state rendering
  - Control server lifecycle callbacks surfaced to the pairing window without platform driver assumptions
affects: [03-lan-pairing-and-secure-session, desktop-companion, phase-03-plan-07]

tech-stack:
  added: []
  patterns: [thin Swing launcher, injected registry/server dependencies, EDT-safe server state rendering, source-level UI state tests]

key-files:
  created:
    - .planning/phases/03-lan-pairing-and-secure-session/03-06-SUMMARY.md
  modified:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/pairing/PairingSessionRegistryTest.kt
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Desktop launch constructs `DesktopIdentityStore`, `PairingSessionRegistry`, and `ControlServer` once, then injects them into `PairingWindow`."
  - "PairingWindow owns visible lifecycle state while protocol validation remains in PairingSessionRegistry and ControlServer."
  - "Manual fallback displays endpoint, port, 6-digit code, and fingerprint suffix only; diagnostics are routed through SecretRedactor."

patterns-established:
  - "Desktop Swing entry points stay thin and inject protocol/control dependencies instead of duplicating protocol logic."
  - "ControlServer lifecycle emits UI-safe state transitions while authenticated handling remains proof-gated."

requirements-completed: [TRAN-01, TRAN-03, TRAN-06]

duration: 5min
completed: 2026-06-07T23:21:40Z
---

# Phase 03 Plan 06: Desktop Companion Launch and Pairing Window Summary

**Runnable desktop companion launch with Swing pairing window lifecycle, QR/manual fallback, countdown, and proof-gated control server state display.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-07T23:17:06Z
- **Completed:** 2026-06-07T23:21:40Z
- **Tasks:** 2 completed
- **Files modified:** 4 code/test files, plus planning metadata

## Accomplishments

- Wired `Main.kt` so `gradle -p desktop-companion run` launches `PairingWindow` with a desktop identity store, pairing registry, and control server.
- Expanded `PairingWindow` into the visible desktop pairing/control surface: start/restart action, QR image at 260x260, endpoint, port, 6-digit manual code, fingerprint suffix, expiry countdown, and required session states.
- Added control-server lifecycle callbacks for desktop UI state updates while preserving proof-gated trusted envelope handling.
- Added desktop test coverage for required UI state labels, QR size, countdown copy, and manual fallback secret redaction boundaries.

## Task Commits

1. **Task 1: Wire desktop companion launch entry** - `56e0aeb` (feat)
2. **Task 2: Wire desktop pairing window lifecycle and state rendering** - `ab4067f` (feat)

## Verification

- Focused: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingSession*' --tests '*ControlChannel*'` passed.
- Wave closeout: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test` passed.
- Boundary grep: `rg -n "virtual joystick|HID backend|UDP input|packet loss|jitter|visualizer latency|haptic strength|haptic duration|haptic ack|haptic fail|phone vibration" desktop-companion/src` returned no matches.
- Source assertions passed for `mainClass.set("com.btgun.desktop.MainKt")`, `Main.kt` referencing `PairingWindow`, and `PairingWindow.kt` rendering endpoint, port, 6-digit code, fingerprint suffix, QR size, countdown, and required state labels.

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` - desktop launch now creates identity, pairing registry, control server, and pairing window.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Swing pairing/control window renders QR/manual fallback, countdown, diagnostics, and session states.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - emits lifecycle state changes for UI without changing proof-gated trusted envelope handling.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/pairing/PairingSessionRegistryTest.kt` - covers desktop UI state labels, QR size, countdown, and manual fallback secret boundaries.

## Decisions Made

- Desktop launch constructs identity/registry/server dependencies once and injects them into the UI.
- PairingWindow starts and stops the control server around active pairing windows and window close.
- ControlServer reports pre-auth connection, authenticated, disconnected, degraded, and rate-limited lifecycle states to UI code, but trusted envelope acceptance still requires pairing proof.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- Sandboxed Gradle could not create local file-lock sockets, so required Gradle commands were rerun with approved escalation and normal Gradle behavior.
- Sandboxed git index writes were blocked, so required `git add` and `git commit` operations were rerun with approved escalation and normal hooks.

## Auth Gates

None.

## Known Stubs

None. Nullable Swing/control state fields represent inactive or absent runtime state, not placeholder data.

## Threat Flags

None. The changed trust-boundary surface matches the plan threat model: UI displays endpoint/code/fingerprint suffix only, diagnostics go through `SecretRedactor`, and trusted control handling remains proof-gated.

## Next Phase Readiness

Plan 03-07 can wire Android QR/manual/trusted-desktop flows against a runnable desktop companion surface. UDP input streaming, OS driver/HID work, packet-loss/jitter metrics, visualizer latency, haptic payloads, haptic execution, and phone feedback behavior remain out of scope for later phases.

## Self-Check: PASSED

- Found summary path: `.planning/phases/03-lan-pairing-and-secure-session/03-06-SUMMARY.md`.
- Found task commits `56e0aeb` and `ab4067f` in git log.
- Full desktop test suite passed.
- Boundary grep returned no forbidden Phase 4/driver/visualizer/phone-feedback behavior.

---
*Phase: 03-lan-pairing-and-secure-session*
*Completed: 2026-06-07*
