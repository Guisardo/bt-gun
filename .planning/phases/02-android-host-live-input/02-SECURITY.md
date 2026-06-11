---
phase: 02
slug: android-host-live-input
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-11
---

# Phase 02 - Security

Per-phase security contract for Android host live input: permission gate, BLE parser/adapter, foreground session, motion preview, reload-hold recenter, dashboard state, local phone haptics, and sanitized manual evidence.

Scope note: this audit verifies the Phase 2 plan-time threat register against Phase 2 artifacts and behavior. Later Phase 3+ transport, desktop pairing, haptic command, HID, and profile additions in the current branch are outside this Phase 2 security disposition unless explicitly listed here.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Android permission model to host session | Runtime Bluetooth, location, sensor, vibration, and network capability state gates active session startup. | Permission booleans, unavailable states, blocked labels, service start action. |
| Physical gun BLE to parser | `ARGunGame` BLE advertisements and `fff3` notifications become product gun events or debug/status events. | BLE service UUIDs, characteristic bytes, parsed controls, provenance. |
| Parser provenance to dashboard | Raw BLE/debug provenance can be surfaced for diagnosis but must stay out of product mode. | Raw ASCII/hex, clue ids, capture ids, semantic confidence, characteristic UUIDs. |
| Android service lifecycle to BLE work | Foreground service owns scan/connect/reconnect and error surfacing. | Notification state, connection state, reconnect attempts, last errors. |
| Android sensors to motion preview | Sensor capabilities and samples become preview aim data and metadata. | Provider names, capability flags, elapsed-nanos timestamps, preview x/y. |
| Reload control to recenter state | Reload down/up product events also drive a two-second hold recenter state machine. | Reload pressed state, elapsed nanos, recenter baseline, status labels. |
| Dashboard and manual evidence to repo docs | Live UI/manual validation state becomes committed sanitized proof rows. | Capture ids, pass/fail rows, raw evidence pointers, expected interpretations. |
| Android phone haptic to Phase 2 UI | Local phone vibration test is displayed without implying desktop haptic/control support. | Local vibrator capability, 1000 ms test state, unavailable/error state. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01 | Elevation of Privilege | `PermissionGate`, manifest | mitigate | `PermissionGate.evaluate` blocks BLE scan/connect/advertise, legacy location, vibration, LAN, and sensor capability gaps before session start; tests cover blocked states. | closed |
| T-02-02 | Information Disclosure | `Provenance` | mitigate | Provenance is optional debug metadata; dashboard product mode hides raw values by default and debug panels are explicit. | closed |
| T-02-03 | Tampering | Future desktop surfaces | mitigate | Phase 2 dashboard/manual UAT requires desktop link and packet stream to remain explicit pending placeholders, not simulated working LAN/control behavior. | closed |
| T-02-04 | Tampering | Package/dependency surface | mitigate | Phase 2 used approved Android Gradle/Kotlin coordinates and no new BLE/sensor/UI/haptic helper dependency in its phase-owned plans. | closed |
| T-02-05 | Tampering | `IpegaPacketParser.parseFff3` | mitigate | Parser uses a strict Phase 1 fixture whitelist; unknown `fff3` bytes produce `UnknownBlePayload` status/debug output only. | closed |
| T-02-06 | Information Disclosure | `Provenance` | mitigate | Raw ASCII/hex, characteristic UUID, clue id, capture id, and confidence are preserved for debug but hidden from product event display unless debug is opened. | closed |
| T-02-07 | Repudiation | Fixture parser tests | mitigate | Parser tests assert clue id, capture id, raw ASCII/hex, characteristic UUID, elapsed timestamps, and sequence behavior for known controls. | closed |
| T-02-08 | Spoofing | Connection/status event mapping | mitigate | Parser cannot mark arbitrary bytes as connection state; BLE adapter/service own connection status and route unknown payloads to status only. | closed |
| T-02-09 | Elevation of Privilege | `HostSessionService` BLE start | mitigate | Service start and UI start paths recheck permission gate and convert `SecurityException`/`IllegalStateException` into visible blocked/error state. | closed |
| T-02-10 | Denial of Service | `ReconnectPolicy` | mitigate | Reconnect attempts/backoff are bounded, active-session only, stop suppresses reconnect, and dashboard/manual rows require visible reconnect/error state. | closed |
| T-02-11 | Tampering | `IpegaBleGunAdapter` | mitigate | Adapter accepts only `ARGunGame` advertising `fff0`, subscribes `fff3`, and routes only `fff3` notification bytes through the parser whitelist. | closed |
| T-02-12 | Elevation of Privilege | Foreground service policy | mitigate | Manifest declares a non-exported connected-device foreground service and manual validation verifies active notification before long-running BLE session proof. | closed |
| T-02-13 | Scope/Safety | `fff5`/haptics | mitigate | Phase 2 evidence keeps desktop-origin haptics, `fff5` writes, ack/fail/TTL, and control-channel UI out of scope; local phone haptic test only. | closed |
| T-02-14 | Tampering | `MotionProviderSelection` | mitigate | Provider selection is derived only from capability flags, falls back through explicit provider order, and emits unavailable instead of fake aim. | closed |
| T-02-15 | Information Disclosure | Motion metadata | accept | Provider/capability metadata is non-PII and needed for debugging; raw debug remains collapsed by default. | closed |
| T-02-16 | Denial of Service | Sensor sampling | mitigate | Motion unavailable fallback exists; service/dashboard wiring exposes provider state, and Phase 2 manual validation covers motion behavior without fake values. | closed |
| T-02-17 | Scope/Safety | `PreviewAimMapper` | mitigate | Tests reject desktop profile/HID/sensitivity/dead-zone fields; Android aim remains preview/calibration only. | closed |
| T-02-18 | Tampering | `ReloadHoldRecenter` | mitigate | Deterministic tests require a two-second hold, reject early-release recenter, and suppress duplicate recenter while held. | closed |
| T-02-19 | Denial of Service | Reload hold handling | mitigate | Recenter logic is pure elapsed-nanos state, no sleeps/timers/blocking work, so BLE callbacks can remain responsive. | closed |
| T-02-20 | Repudiation | Recenter status | mitigate | Recenter emits a status event with `recenter emitted` label and baseline elapsed timestamp. | closed |
| T-02-21 | Scope/Safety | Reload semantics | mitigate | Tests and manual validation require reload down/up product events to remain visible even when recenter fires. | closed |
| T-02-22 | Information Disclosure | Debug panels/evidence | mitigate | Product mode hides raw BLE/provenance; raw Phase 2 evidence stays under ignored `.evidence/phase2`; committed manifest rows are sanitized. | closed |
| T-02-23 | Elevation of Privilege | Permission/start session UI | mitigate | Main activity evaluates `HostCapabilityProbe` before service actions and surfaces blocked state/errors instead of blind service startup. | closed |
| T-02-24 | Denial of Service | Foreground/reconnect dashboard | mitigate | Reconnect is bounded/visible from Phase 02-03; manual validation verifies foreground notification survives app switch/lock and reports state honestly. | closed |
| T-02-25 | Scope/Safety | `PhoneHaptics` | mitigate | Phase 2 exposes local phone vibration only and manual/UAT checks reject desktop-origin command, ack/fail, TTL, or control-channel UI. | closed |
| T-02-26 | Tampering | Manual evidence manifest | mitigate | Stable capture ids, expected interpretations, sanitized pass rows, and ignored raw paths are recorded in manual validation and evidence manifest. | closed |
| T-02-SC | Tampering | Package installs | mitigate | Phase 2 summaries record no new BLE/sensor/UI/haptic helper packages; any later dependency surface belongs to later phase security review. | closed |

## Key Evidence

- `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt` and `HostCapabilityProbe.kt` evaluate blocked/unavailable runtime capability state.
- `android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java` covers Android 12+ Bluetooth permissions, legacy scan/location, motion, vibration, LAN, and optional provenance.
- `android-host/app/src/main/java/com/btgun/host/ble/IpegaPacketParser.kt` implements the `fff3` whitelist and `UnknownBlePayload` fallback.
- `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt` verifies known controls, raw/provenance linkage, and unknown payload rejection.
- `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt` scans `ARGunGame`/`fff0`, enables `fff3`, catches platform exceptions, and routes parsed/unknown packets separately.
- `android-host/app/src/test/java/com/btgun/host/ble/IpegaBleGunAdapterTest.kt` covers scan matching, notification subscription, stopped reconnect behavior, and serialized GATT queue ordering.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` owns foreground session state, start/stop actions, permission/error handling, and haptic/local-session boundaries.
- `android-host/app/src/main/java/com/btgun/host/motion/MotionAimProvider.kt` and `PreviewAimMapper.kt` implement provider fallback, capability metadata, unavailable state, and preview-only aim.
- `android-host/app/src/test/java/com/btgun/host/motion/MotionProviderSelectionTest.kt` verifies provider order, elapsed-nanos motion envelope, disabled unavailable preview, and absence of desktop mapping fields.
- `android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt` implements pure elapsed-nanos reload-hold recenter state.
- `android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt` verifies two-second threshold, early release, duplicate suppression, repeat holds, and event order `reload down`, `recenter emitted`, `reload up`.
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` and `DashboardStateTest.kt` keep raw provenance in debug panels, preserve inactive Phase 2 boundaries, and show local phone haptic state.
- `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` reports local vibrator available/unavailable/permission-blocked/started states for phone-only haptic testing.
- `.planning/phases/02-android-host-live-input/02-MANUAL-VALIDATION.md` records user-approved permission, BLE connect, controls, motion, recenter, foreground, and phone haptic rows.
- `docs/evidence/manifests/phase2-host-live-input.jsonl` records sanitized approved-manual evidence rows with stable capture ids and ignored raw paths.
- `.planning/phases/02-android-host-live-input/02-UAT.md` records 11/11 UAT pass and no gaps.

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-02-15 | T-02-15 | Motion provider/capability metadata contains no personal identifiers and is needed to verify provider fallback; raw debug remains collapsed by default. | Plan-time threat model / Codex secure-phase | 2026-06-11 |

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-11 | 27 | 27 | 0 | Codex secure-phase |

## Sign-Off

- [x] All threats have a disposition.
- [x] Accepted risks documented.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

**Approval:** verified 2026-06-11
