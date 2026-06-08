---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: verifying
stopped_at: Completed 04-05-PLAN.md
last_updated: "2026-06-08T20:27:54.959Z"
last_activity: 2026-06-08 -- Phase 04 Plan 05 completed; phase ready for verification
progress:
  total_phases: 10
  completed_phases: 4
  total_plans: 24
  completed_plans: 24
  percent: 40
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-07)

**Core value:** Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive motion aiming and v1 phone haptic feedback.
**Current focus:** Phase 04 — input-stream-and-haptic-transport verification

## Current Position

Phase: 04 (input-stream-and-haptic-transport) — READY FOR VERIFICATION
Plan: 5 of 5
Status: Phase complete — ready for verification
Last activity: 2026-06-08 -- Phase 04 Plan 05 completed; phase ready for verification

Progress: [██████████] 100% by plan for Phases 01-04. Phase 04 has 5/5 plans executed and is ready for verification.

## Performance Metrics

**Velocity:**

- Total plans completed: 19
- Average duration: not tracked for hardware-interactive plans
- Total execution time: not tracked after Plan 02

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 5 | not tracked | not tracked |
| 02 | 6 | hardware-interactive | hardware-interactive |

**Recent Trend:**

- Last 5 completed execution plans: 03-P04, 03-P05, 03-P06, 03-P07, 03-P08 complete
- Trend: Phase 03 secure LAN pairing/control foundation complete; Phase 04 input/haptic transport plans are ready to execute.

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
| Phase 03 P02 | 8 min | 3 tasks | 9 files |
| Phase 03 P03 | 19 min | 3 tasks | 9 files |
| Phase 03 P04 | 35 min | 2 tasks | 13 files |
| Phase 03 P05 | 18 min | 2 tasks | 8 files |
| Phase 03 P06 | 5 min | 2 tasks | 4 files |
| Phase 03 P07 | 8min | 2 tasks | 4 files |
| Phase 03 P08 | 4 min | 2 tasks | 2 files |
| Phase 04 P01 | 7 min | 2 tasks | 11 files |
| Phase 04 P02 | 7 min | 2 tasks | 8 files |
| Phase 04 P03 | 44 min | 2 tasks | 8 files |
| Phase 04 P04 | 23 min | 2 tasks | 15 files |
| Phase 04 P05 | 15m 32s | 2 tasks | 17 files |

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
- [Phase 03]: Android pairing entry stores trusted desktop metadata by SPKI SHA-256 fingerprint and keeps packet stream inactive. — QR/manual parsing and dashboard state are ready for proof/control-channel plans without adding Phase 4 transport behavior.
- [Phase 03]: Pairing proof uses a versioned HMAC transcript and consumes accepted sessions. — Replay, wrong proof, expiry, rate-limit, and fingerprint mismatch all fail before trusted control state exists.
- [Phase 03]: Android trust validation reports first-trust, trusted, missing, or mismatch without silent fingerprint overwrite. — Display name and endpoint remain metadata only.
- [Phase 03]: Control channel uses versioned JSON envelopes over WSS with proof-gated desktop handling. — Haptic support stays reserved type only until Phase 4.
- [Phase 03]: Approved Ktor/OkHttp deps compile under the current Kotlin 2.0.21 plugin with metadata-version skip. — No unapproved dependency coordinates were added.
- [Phase 03]: Heartbeat/liveness is monotonic elapsed-time state with connected, degraded, and disconnected views. — Diagnostics and profile metadata remain minimal and Phase 4 haptic execution stays out of scope.
- [Phase 03]: Desktop launch constructs identity/registry/server dependencies once and injects them into the Swing pairing window. — Keeps launch thin and protocol logic inside pairing/control classes.
- [Phase 03]: HostSessionService owns Android desktop control client creation, trusted metadata persistence, and socket shutdown. — Keeps QR/manual/trusted reconnect inside the foreground service boundary.
- [Phase 03]: Trusted desktop reconnect requires an explicit Android tap and stored fingerprint metadata. — No silent primary auto-reconnect is introduced.
- [Phase 03]: Protocol docs define `reserved_haptic_command` as an empty-body Phase 3 type only. — Phase 4 owns haptic command body shape and execution behavior.
- [Phase 03]: Manual smoke guide is the physical-device validation bridge for QR/manual pairing, trust mismatch, heartbeat degradation, trusted reconnect, and inactive packet stream.
- [Phase 04]: Use mirrored Android and desktop codecs with golden fixtures rather than a shared module for Plan 04-01. — Matches existing mirrored protocol modules and keeps wire compatibility enforced by tests.
- [Phase 04]: Use fixed 120-byte big-endian UDP frames authenticated with full HMAC-SHA256 tags. — Keeps parser deterministic, portable, and fixture-friendly without adding dependencies.
- [Phase 04]: Plain Gradle startup remains blocked locally; Phase 04 validation uses Homebrew JDK 17 plus GRADLE_USER_HOME under /private/tmp. — This avoids native-platform startup failure and supports trusted automated test results.
- [Phase 04]: Start Android UDP only from trusted WSS input_stream_config, never from QR/manual material. — QR/manual material pairs the session; authenticated WSS delivers fresh stream key material.
- [Phase 04]: Use one monotonic sequence across Android snapshot and edge UDP frames per stream session. — Receiver replay handling can use a single increasing sequence across mixed frame types.
- [Phase 04]: Keep Android UDP payload raw-motion-only while local preview aim remains dashboard state. — Desktop profiles own product aim mapping; UDP carries provider/capability/yaw/pitch/roll/raw aim only.
- [Phase 04]: Desktop stream trust starts only after authenticated control-session acceptance. — QR/manual pairing material remains separate from fresh UDP stream key material.
- [Phase 04]: InputReplayGuard tracks one highest accepted sequence per stream session and rejects late edge frames after newer snapshots. — Android sender uses one monotonic sequence across snapshot and edge frames.
- [Phase 04]: UdpReceivedInput exposes raw motion/provider/axis fields only; desktop profile and virtual joystick mapping remain deferred. — Phase 04 receiver boundary must not add profile mapping, HID, visualizer, or product aim behavior.
- [Phase 04]: Haptic commands stay on authenticated WSS control using reserved_haptic_command, with haptic_result responses. — No UDP haptic protocol was added.
- [Phase 04]: Android haptic results return immediately after validation and phone vibration start attempt. — Results do not wait for pulse duration.
- [Phase 04]: Non-null haptic patterns return unsupported and physical gun motor rumble remains deferred. — Phase 4 implements phone pulse only.
- [Phase 04]: Use active/grace/stale/stopped as the shared packet stream lifecycle labels. — Plan 04-05 needs concise endpoint-local state for Android dashboard and desktop diagnostics without adding later visualizer metrics.
- [Phase 04]: Allow unchanged-session UDP only during controlDisconnectGraceMs; require fresh stream config after reconnect or session change. — This fails closed across LAN disconnects and prevents old frames from applying after trust changes.
- [Phase 04]: Cancel phone haptic on trusted session change, not on short reliable-control disconnect. — Session change invalidates command ownership; short disconnect alone should not interrupt an already valid pulse.

### Pending Todos

- Run Phase 03 manual smoke on a physical Android device and desktop companion when ready.
- Execute Phase 4 Plan 05.

### Blockers/Concerns

- [Phase 4]: Preserve Phase 3 secure-session boundaries while adding fast input transport and haptic command execution.
- [Phase 7]: macOS virtual HID/output path may depend on entitlement and OS support.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v1 feedback | Physical gun motor rumble | deferred; use Android phone vibration in v1 | 2026-06-06 |

## Session Continuity

Last session: 2026-06-08T20:27:54.951Z
Stopped at: Completed 04-05-PLAN.md
Resume file: None
