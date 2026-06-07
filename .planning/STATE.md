---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 03-01-PLAN.md
last_updated: "2026-06-07T18:45:30Z"
last_activity: 2026-06-07 -- Phase 03 Plan 01 desktop pairing session complete
progress:
  total_phases: 10
  completed_phases: 2
  total_plans: 19
  completed_plans: 12
  percent: 20
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-07)

**Core value:** Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive motion aiming and v1 phone haptic feedback.
**Current focus:** Phase 03 — lan-pairing-and-secure-session

## Current Position

Phase: 03 (lan-pairing-and-secure-session) — EXECUTING
Plan: 2 of 8
Status: Executing Phase 03
Last activity: 2026-06-07 -- Phase 03 Plan 01 desktop pairing session complete

Progress: [██░░░░░░░░] 20% by phase; Phase 01 and Phase 02 are complete; Phase 03 has 8 planned execution prompts.

## Performance Metrics

**Velocity:**

- Total plans completed: 11
- Average duration: not tracked for hardware-interactive plans
- Total execution time: not tracked after Plan 02

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 5 | not tracked | not tracked |
| 02 | 6 | hardware-interactive | hardware-interactive |

**Recent Trend:**

- Last 5 plans: P02, P04, P03, P05, P06 complete
- Trend: Phase 03 Wave 0 desktop harness started; Android pairing parser next

*Updated after each plan completion*
| Phase 01 P01 | 10 min | 3 tasks | 6 files |
| Phase 01 P02 | 4 min | 2 tasks | 8 files |
| Phase 01 P03 | not tracked | hardware checkpoint | capture evidence |
| Phase 01 P04 | not tracked | fixture normalization | normalized JSONL |
| Phase 01 P05 | not tracked | haptic proof | final evidence gate |
| Phase 02 P01 | 10 min | 3 tasks | 10 files |
| Phase 02 P02 | 9 min | 3 tasks | 4 files |
| Phase 02 P04 | 5 min | 3 tasks | 5 files |
| Phase 02 P03 | 7 min | 3 tasks | 6 files |
| Phase 02 P05 | 13 min | 3 tasks | 4 files |
| Phase 02 P06 | hardware-interactive | dashboard/manual validation | approved |
| Phase 03 P01 | 9 min | 3 tasks | 17 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Phase 1]: Reduce highest uncertainty first by testing real iPega hardware and protocol evidence.
- [Phase 9]: First user-visible acceptance path is the simple joystick visualizer, not commercial game support.
- [Phase 01]: Use fff0/fff1/fff3/fff5 and SPP UUID clues only as hardware-test hypotheses until captures and fixtures exist. — Plan 01 static analysis cannot satisfy the Phase 1 evidence rule alone.
- [Phase 01]: Treat ARGunPro_1.0.19_apkcombo.com.xapk as invalid because the local file is 0 bytes. — D-05 says reacquire only if strongest valid refs block protocol discovery.
- [Phase 01 resolved]: DISC-02 and DISC-03 closed after Plan 03 physical hardware evidence. — Diagnostics plus normalized fixtures now satisfy the D-06 and D-07 evidence rule.
- [Phase 01 resolved]: Plan 02 Gradle no-build note is historical. — Subsequent hardware plans completed discovery evidence, so it is not an active closeout blocker.
- [Phase 01]: Manual marker reports are hooks for Plan 03 evidence tagging, not proof of app-observed frames or physical motor activation. — Prevents tooling-only rows from satisfying Phase 1 evidence rule.
- [v1]: Use Android phone vibration for haptic feedback; defer physical gun motor rumble. — No verified physical gun motor command path exists, while `phone-vibrate-001` confirmed Android phone haptics.
- [Phase 01]: Physical input path is BLE GATT `fff0` with `fff3` notifications. — Normalized fixtures now cover trigger, reload, digital stick directions, X/Y/A/B, handshake, and phone haptics.
- [Phase 02]: Use a strict `fff3` fixture whitelist; unknown bytes become `UnknownBlePayload` with status/debug envelope only. — Prevents arbitrary BLE bytes from becoming product controls.
- [Phase 02]: Keep candidate control confidence in parser provenance rather than flattening noisy evidence into product UI fields. — Preserves Phase 1 evidence quality for debug mode.
- [Phase 02]: Motion provider selection is pure and preview aim is Android-local calibration only. — Desktop profile/HID mapping remains deferred to later desktop profile phases.
- [Phase 02]: Foreground HostSessionService owns the active BLE connection and IpegaBleGunAdapter accepts only ARGunGame/fff0 before parsing fff3 notifications. — Keeps BLE lifecycle visible, bounded, and scoped before LAN/control phases.
- [Phase 02]: Reload-hold recenter is a pure elapsed-nanos state machine. — Reload down/up remain gun events, while recenter emits a separate status event after a two-second hold.
- [Phase 02 approved]: Android host live input is approved for physical-device use. — Permission gate, BLE connection, controls, motion/aim graph, recenter/calibration, foreground behavior, and local phone haptic rows passed manual sign-off on 2026-06-07.
- [Phase 02]: Disabled Bluetooth/location must surface as blocked/unavailable capability state instead of crashing. — Activity, service, and BLE scan startup use guarded capability probes.
- [Phase 03]: Desktop companion starts as an isolated Kotlin/JVM Swing surface. — Keeps Wave 0 portable across macOS Apple Silicon and Windows 11 x64 before OS driver work.
- [Phase 03]: Desktop pairing material is one active short-lived session with QR as normal path and visible 6-digit manual fallback. — Matches D-01/D-02 while keeping Android QR/manual parsing for Plan 03-02.
- [Phase 03]: Desktop identity is anchored by SPKI SHA-256 fingerprint from local Java KeyStore-backed key material. — Pairing payloads expose fingerprint only; one-time secrets and manual codes are not durable metadata.

### Pending Todos

- Continue Phase 03 with Plan 03-02: Android QR/manual parser, trusted desktop store, and desktop-link dashboard state.

### Blockers/Concerns

- [Phase 3]: Pairing must preserve Phase 2 live-session boundaries and keep desktop haptic commands out of pairing scope until Phase 4.
- [Phase 7]: macOS virtual HID/output path may depend on entitlement and OS support.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v1 feedback | Physical gun motor rumble | deferred; use Android phone vibration in v1 | 2026-06-06 |

## Session Continuity

Last session: 2026-06-07T18:45:30Z
Stopped at: Completed 03-01-PLAN.md
Resume file: .planning/phases/03-lan-pairing-and-secure-session/03-02-PLAN.md
