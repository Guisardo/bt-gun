---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 07-05-PLAN.md
last_updated: "2026-06-10T17:06:46Z"
last_activity: 2026-06-10 -- Phase 07 Plan 05 recorded CoreHID runtime block and mandatory DriverKit fallback gate
progress:
  total_phases: 10
  completed_phases: 6
  total_plans: 43
  completed_plans: 41
  percent: 95
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-09)

**Core value:** Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive motion aiming and v1 phone haptic feedback.
**Current focus:** Phase 07 — macos-virtual-joystick-path

## Current Position

Phase: 07 (macos-virtual-joystick-path) — EXECUTING
Plan: 6 of 7
Next phase: 07 (macOS Virtual Joystick Path) execution
Status: Plan 07-05 complete; executing Phase 07
Last activity: 2026-06-10 -- Phase 07 Plan 05 recorded CoreHID runtime block and mandatory DriverKit fallback gate

Progress: [█████████░] 41/43 planned plans complete. Phase 06 has 6/6 plans complete; Phase 07 has 5/7 plans executed.

## Performance Metrics

**Velocity:**

- Total plans completed: 40
- Average duration: not tracked for hardware-interactive plans
- Total execution time: not tracked after Plan 02

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 5 | not tracked | not tracked |
| 02 | 6 | hardware-interactive | hardware-interactive |
| 04 | 6 | - | - |
| 05 | 5 | hardware-interactive | hardware-interactive |
| 06 | 6 | hardware-interactive | hardware-interactive |
| 07 | 7 | active | Plans 01-05 complete; remaining plans pending |

**Recent Trend:**

- Recent completed execution plans: 06-P02, 06-P03, 06-P05, 06-P04, 06-P06, 07-P01, 07-P02, 07-P03, 07-P04, and 07-P05 complete.
- Trend: Phase 07 plan sequence covers CoreHID feasibility, report packing, helper/backend/runtime wiring, OS-output proof, conditional DriverKit fallback, and final live macOS proof.

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
| Phase 04 P06 | hardware-interactive; closeout 2 min | 2 tasks | 4 files |
| Phase 05 P01 | 14 min | 2 tasks | 4 files |
| Phase 05 P02 | 10min | 2 tasks | 5 files |
| Phase 05 P03 | 6min | 2 tasks | 4 files |
| Phase 05 P04 | 10min | 2 tasks | 6 files |
| Phase 05 P05 | 2h 10m | 3 tasks | 11 files |
| Phase 06 P01 | 12 min | 2 tasks | 5 files |
| Phase 06 P02 | 8 min | 3 tasks | 10 files |
| Phase 06 P03 | 6min | 2 tasks | 5 files |
| Phase 06 P04 | 11 min | 3 tasks | 6 files |
| Phase 06 P05 | hardware-interactive | CI build/sign/package | approved |
| Phase 06 P06 | hardware-interactive | Windows target proof | approved |
| Phase 07 P01 | checkpointed; closeout complete | 3 tasks | 6 files |
| Phase 07 P02 | 5 min | 2 TDD tasks | 5 files |
| Phase 07 P03 | 10 min | 3 tasks | 7 files |
| Phase 07 P04 | 8 min | 3 tasks | 7 files |
| Phase 07 P05 | 12 min | 3 tasks | 7 files |

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
- [Phase 04]: Treat the user's 2026-06-09 Phase 4 approval as pass status for every planned physical-smoke capture id.
- [Phase 04]: Do not invent or commit raw log text, device identifiers, pairing material, stream secrets, proof values, keys, or screenshots.
- [Phase 05]: BT Gun v1 backend descriptor is fixed to one gamepad_like_joystick with trigger/reload/X/Y/A/B, stickX/stickY/aimX/aimY, and digital trigger. — Locks DESK-04 before platform-specific adapters.
- [Phase 05]: SemanticControllerState stays immutable and semantic-only with no platform HID/report fields. — Keeps Windows and macOS adapter details deferred to later phases.
- [Phase 05]: Stub capabilities use explicit macos-stub/windows-stub ids and unsupported reasons for OS-visible device/output-report limits. — Keeps DESK-07 honest before platform adapters.
- [Phase 05]: Stub backend publish state is synchronized and records current state plus last publish result. — Future adapters must preserve observable lifecycle/publish semantics.
- [Phase 05]: UDP handoff tests pass authenticated fixture bytes through UdpInputReceiver before semantic state mapping.
- [Phase 05]: UdpControllerStateAdapter maps only pressedControls, stick axes, rawAimX/rawAimY, stale, and lastAcceptedSequence.
- [Phase 05]: Platform smoke commands use distinct macos-stub/windows-stub JavaExec entrypoints and write separate JUnit-style XML artifacts.
- [Phase 05]: Smoke XML records only case names, pass/fail status, and timing; fixture bytes and stream authentication material stay out of artifacts.
- [Phase 05]: Simulated backend output reports route to phone haptics only through authenticated ControlServer haptic commands.
- [Phase 05]: Headless haptic smoke writes a scannable QR PNG artifact and keeps pairing/control secrets out of committed evidence.
- [Phase 05]: Android trusted-desktop conflict detection is endpoint-scoped for unknown fingerprints so macOS and Windows desktop identities can both be trusted.
- [Phase 03 approved]: Physical Android plus desktop manual smoke completed. — User confirmed during the 2026-06-09 quick repair, so no Phase 03 manual smoke todo remains.
- [Phase 06]: Windows input report ID 1 uses byte 0 report id, byte 1 trigger/reload/X/Y/A/B bits, then stickX/stickY/aimX/aimY signed int16 little-endian axes.
- [Phase 06]: Windows output report ID 2 version 1 maps one strength byte plus duration/TTL uint16 little-endian fields to a pattern-null HapticCommand after reserved bytes validate as zero.
- [Phase 06]: Windows driver ABI uses METHOD_BUFFERED IOCTLs with FILE_WRITE_DATA for input submission and FILE_READ_DATA for output/status reads. — Matches Plan 02 user-mode to kernel tampering mitigation.
- [Phase 06]: KMDF/VHF kernel code stays a HID report bridge only; LAN/session/security and haptic transport stay in user mode. — Preserves D-11 and D-12 boundaries while reducing kernel attack surface.
- [Phase 06]: Local macOS execution records the WDK/MSBuild build gate as source-only blocked; Plan 05 CI must validate build/sign/package before target use. — The executor has no Windows WDK/MSBuild toolchain, and Plan 02 build_gate allows source-only closeout.
- [Phase 06]: WindowsVirtualControllerBackend publishes semantic state only through WindowsHidReportPacker and WindowsDriverBridge.submitInputReport.
- [Phase 06]: Windows output report bytes drain from the helper bridge and map through WindowsOutputReportMapper with windows-output-report-* command ids.
- [Phase 06]: windows-vhf capabilities declare real output-report and phone-haptic support while keeping pattern output unsupported.
- [Phase 06]: WindowsBackendRuntime attaches only to ControlServer.onUdpInputReceived, preserving existing callbacks and keeping LAN/session/auth ownership in ControlServer.
- [Phase 06]: The real windows-vhf smoke command requires a Plan 05 btgun-driver-bridge.exe artifact path and never falls back to Phase 5 stubs.
- [Phase 06]: Desktop launch enables the real Windows backend only when btgun.windows.driver.enabled=true and an explicit bridge path is provided.
- [Phase 06 approved]: Windows virtual joystick path is accepted for Phase 7 handoff. — User confirmed Phase 6 approval on 2026-06-10 after the 0.6.2.2 VHF identity/package update, latest CI artifact install, joy.cpl axis verification, and target validation.
- [Phase 07]: Self-signed, ad-hoc, and named local signing did not satisfy the restricted CoreHID virtual HID entitlement on normal macOS. — Plan 07-01 remains a CoreHID-first proof but records `corehid-runtime-blocked`.
- [Phase 07]: No USB bridge is available, so the no-subscription lab route is local-development-only HIDDriverKit/system-extension fallback exploration. — Do not claim DESK-03 or DESK-06 production support until later proof rows pass.
- [Phase 07]: SIP changes, system extension developer mode, install, activation, removal, rollback, reboot, or other OS security-state changes require explicit later approval. — Plan 07-01 documented future commands/risks only and did not run them.
- [Phase 07]: macOS report ID 1 and output report ID 2 byte contracts now mirror the Windows shape in pure Kotlin. — Plan 07-02 covers deterministic report packing and mapper validation only; OS-visible joystick and OS-origin output proof remain pending.
- [Phase 07]: MacosVirtualControllerBackend publishes only packed report bytes through the helper. — LAN/session/security/profile/haptic transport ownership stays outside native code.
- [Phase 07]: macos-corehid capabilities claim output-report support only after both OS-visible and set-report callback proof status are true. — Simulated output remains mapper-only and cannot satisfy DESK-06 proof.
- [Phase 07]: Native Swift helper remains a CoreHID byte bridge with HELLO/SUBMIT_INPUT/READ_OUTPUT/STATUS/QUIT only. — No DriverKit activation or OS security-state command was run in Plan 07-03.
- [Phase 07]: MacosBackendRuntime attaches only to the trusted ControlServer UDP callback chain and preserves prior callbacks. — LAN/session/security/UDP validation stay in ControlServer and UdpControllerStateAdapter.
- [Phase 07]: macOS backend launch is disabled by default and requires explicit btgun.macos.hid.helper.path. — Missing helper path fails closed with visible startup diagnostic.
- [Phase 07]: macOS helper-origin output reports route only through authenticated ControlServer.sendHapticCommand. — No direct haptic bypass or Android session ownership was added.
- [Phase 07]: CoreHID real smoke and separate IOHIDDeviceSetReport probe recorded corehid-runtime-blocked on normal macOS. — Helper built and signed but was killed before enumeration; Plan 07-06 DriverKit fallback is mandatory unless entitlement-capable CoreHID proof replaces the gate.

### Pending Todos

None.

### Blockers/Concerns

- [Phase 7]: Production macOS virtual HID/output support remains unproven; Plan 07-05 recorded `corehid-runtime-blocked`, so Plan 07-06 must execute the selected local-dev-only DriverKit fallback branch unless entitlement-capable CoreHID proof replaces the gate.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260609-pnq | repair stale Phase 5 roadmap/validation status | 2026-06-09 | this commit | [260609-pnq-repair-stale-phase-5-roadmap-validation-](./quick/260609-pnq-repair-stale-phase-5-roadmap-validation-/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v1 feedback | Physical gun motor rumble | deferred; use Android phone vibration in v1 | 2026-06-06 |

## Session Continuity

Last session: 2026-06-10T17:06:46Z
Stopped at: Completed 07-05-PLAN.md
Resume file: None
