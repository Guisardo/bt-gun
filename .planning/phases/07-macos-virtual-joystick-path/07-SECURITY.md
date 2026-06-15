---
phase: 07
slug: macos-virtual-joystick-path
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-11
---

# Phase 07 - Security

Per-phase security contract for the Android Bluetooth HID macOS path.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Android app to Android Bluetooth stack | Android app probes `HID_DEVICE`, registers a gamepad SDP record, opens pairing, and sends reports through platform Bluetooth APIs. | HID role state, SDP descriptor bytes, input reports, output callbacks. |
| Normalized Android state to HID report bytes | Gun controls and phone aim cross from internal normalized state into OS-parsed Bluetooth HID reports. | Button bits, signed axes, stale state, report IDs. |
| macOS Bluetooth host to Android HID app | macOS can connect as HID host and may send output reports back to Android. | Host connection state, GET/SET/interrupt reports, haptic output bytes. |
| User action to foreground HID lifecycle | HID advertising, pairing, stop, and close paths begin from explicit app/service actions. | Start/stop actions, pairing countdown, foreground service state. |
| Live proof to committed evidence | Manual probe rows and setup docs can overclaim unsupported behavior or leak local identifiers. | Manifest rows, probe output, setup docs, validation notes. |
| Phase 7 strategy docs to future implementation | Blocked CoreHID/DriverKit work and Windows VHF fallback must not be reframed as the primary macOS path. | Fallback decisions, security-state gates, setup instructions. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-07-01 | Spoofing | HID capability UI | mitigate | `AndroidHidCapability.evaluate` separates Bluetooth, permission, proxy, registration, and host connection states; only connected state is available. | closed |
| T-07-02 | Tampering | Evidence manifest | mitigate | Manifest schema row defines allowed Phase 7 record types and sanitized status values before live rows. | closed |
| T-07-03 | Repudiation | Main-style Android tests | mitigate | Gradle registers `AndroidHidCapabilityTestKt` and HID tests in the unit-test task closeout. | closed |
| T-07-04 | Information Disclosure | Evidence fields | mitigate | Manifest redaction policy forbids Bluetooth addresses, serials, account names, keys, pairing material, raw dumps, and sensitive screenshots. | closed |
| T-07-05 | Tampering | HID descriptor bytes | mitigate | Descriptor bytes and report IDs are pinned by `BtGunHidDescriptor` and golden descriptor tests. | closed |
| T-07-06 | Tampering | Input report packer | mitigate | `BtGunHidReportPacker` pins button order, little-endian axes, stale behavior, calibrated aim preference, raw fallback, and center fallback. | closed |
| T-07-07 | Information Disclosure | Android HID package | mitigate | Android HID source guard prevents imports from desktop companion internals. | closed |
| T-07-08 | Tampering | Output report mapper | mitigate | Output mapper rejects wrong ID, length, version, duration, TTL, flags, reserved byte, and blank command ID before haptics. | closed |
| T-07-09 | DoS | Phone haptics | mitigate | HID output uses the existing `DesktopHapticCommand` caps and invalid reports return validation/status errors without vibration. | closed |
| T-07-10 | Spoofing | HID host status | mitigate | `AndroidBluetoothHidGamepad.sendInput` requires proxy, registered app, and connected host before sending reports. | closed |
| T-07-11 | Repudiation | Output proof | mitigate | HID status model records callback kind, validation result, haptic result, report ID, payload length, and last input status separately. | closed |
| T-07-12 | Spoofing | Pairing window | mitigate | Start gamepad and open pairing window are explicit user/service actions, with permission blocks and visible pairing status. | closed |
| T-07-13 | Tampering | Service state fanout | mitigate | `HostSessionHidController.fanOutLiveInput` sends live state only through the connected HID driver and does not start LAN desktop control. | closed |
| T-07-14 | Information Disclosure | Dashboard | mitigate | Dashboard/status paths use sanitized host labels and blocked-state text rather than Bluetooth addresses or personal device identity. | closed |
| T-07-15 | Elevation of Privilege | HID lifecycle | mitigate | Stop, session stop, service destroy, close, unregister, and virtual cable unplug all clear HID host/session state. | closed |
| T-07-16 | Spoofing | macOS proof | mitigate | DESK-03 proof requires macOS Bluetooth HID input; manifest includes Bluetooth paired and IOHID/browser input rows, and docs forbid desktop LAN proof. | closed |
| T-07-17 | Repudiation | Human checkpoint | mitigate | Manifest rows, Phase 7 validation approval, and verification report record accepted live proof and limitation status. | closed |
| T-07-18 | Information Disclosure | Probe/evidence | mitigate | Probe output schema sets `sanitized: true`; probe emission sanitizes payloads before JSON output. | closed |
| T-07-19 | Tampering | Output proof | mitigate | DESK-06 does not count LAN/direct haptics; macOS HID output is recorded as unsupported after live no-callback/no-vibration evidence. | closed |
| T-07-20 | Repudiation | Fallback decision | mitigate | Docs require current-phone and alternate-phone Android HID failure before Windows VHF fallback selection; current phone passed so no fallback-selected row exists. | closed |
| T-07-21 | Information Disclosure | Docs/evidence | mitigate | Setup docs and evidence policy forbid MACs, serials, account names, screenshots, keys, pairing material, stable IDs, and raw dumps. | closed |
| T-07-22 | Tampering | Strategy docs | mitigate | Setup docs state Android Bluetooth HID is primary and CoreHID/DriverKit are retained blocked/fallback evidence only. | closed |
| T-07-23 | Elevation of Privilege | Legacy macOS security-state steps | mitigate | macOS docs require explicit later approval before SIP, system-extension developer mode, activation, install, removal, rollback, reboot, or other security-state changes. | closed |
| T-07-SC | Supply Chain | Package installs | accept | No npm/pip/cargo installs or new package-manager dependency changes were introduced for Phase 7. | closed |

## Key Evidence

- `android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt:44` and `:97` implement the HID role state gate and connected-only available state.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt:32` and `:62` implement button/stale packing and calibrated/raw/center aim selection.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt:16` through `:30` validate output report ID, length, version, range, flags, and reserved bytes before haptics.
- `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt:136`, `:188`, `:233`, `:262`, and `:292` cover send gating, registration callback state, host-origin output callbacks, validate-before-haptic, and virtual cable unplug cleanup.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt:106`, `:171`, `:344`, `:360`, `:429`, and `:459` cover permission/pairing gates, connected-host fanout, explicit service actions, destroy cleanup, session stop cleanup, and pairing action split.
- `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl:1` defines schema and redaction policy; `:5`, `:8`, and `:9` record live macOS pairing/input proof and unsupported/deferred output behavior.
- `docs/setup/android-bluetooth-hid-gamepad.md:3`, `:39`, `:99`, `:117`, `:123`, `:135`, and `:137` document the primary Android HID path, proof boundaries, output limitation, fallback gate, redaction rules, CoreHID/DriverKit fallback status, and LAN/VHF proof boundaries.
- `tools/macos/BtGunGameControllerProbe.swift:142`, `:216`, and `:501` provide the IOHID fallback proof path, haptic-output unsupported event, and sanitized JSON emission.
- `docs/setup/macos-virtual-hid.md:18`, `:129`, and `:194`, plus `docs/setup/macos-driverkit-fallback.md:81`, preserve redaction, CoreHID blocked status, and approval-gated security-state changes.

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-07-SC | T-07-SC | Phase 7 did not add package-manager dependencies or run npm/pip/cargo installs; future dependency changes require a new threat review. | Plan-time threat model / Codex secure-phase | 2026-06-11 |

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-11 | 24 | 24 | 0 | Codex secure-phase |

## Sign-Off

- [x] All threats have a disposition.
- [x] Accepted risks documented.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

**Approval:** verified 2026-06-11
