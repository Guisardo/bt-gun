---
phase: 01
slug: hardware-and-protocol-discovery
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-11
---

# Phase 01 - Security

Per-phase security contract for hardware/protocol discovery evidence, Android diagnostic tooling, normalized fixtures, and haptic proof.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Reference archives to static tools | Untrusted APK/XAPK bytes are inspected by local reverse-engineering tools. | APK/XAPK metadata, manifests, decompile output, static strings. |
| Static clues to committed docs | Decompiled observations become project evidence and can bias later implementation. | Clue ids, hypotheses, status labels, planned hardware tests. |
| Raw device evidence to repo artifacts | Logcat, HCI, bugreport, dumpsys, and app logs become sanitized manifest/docs rows. | Capture ids, raw refs, device observations, expected interpretations. |
| Gradle metadata to diagnostic build | Diagnostic scaffolding may download and execute Android/Gradle build dependencies if built. | Plugin coordinates, repositories, app id, SDK metadata, build output. |
| Physical gun and Android device to diagnostic module | Unknown Bluetooth behavior and Android permission state enter diagnostic code and logs. | BLE advertisements, GATT characteristics, notifications, permission states, unavailable states. |
| Raw captures to normalized fixtures | Hardware bytes or app-observed frames become semantic controls for downstream phases. | `raw_ref`, `clue_id`, `capture_id`, fixture semantics, candidate status. |
| Haptic observation to v1 feedback proof | Human-observed phone vibration becomes the v1 haptic decision. | Android `Vibrator` state, duration, human confirmation, deferred motor status. |
| Phase 1 evidence to downstream implementation | Docs and fixtures inform Android host, transport, and no-hardware regression work. | Protocol path, control map, haptic path, validator gates. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-01-01 | Denial of Service | `docs/refs` archive parsing | mitigate | Inventory uses `apktool`, `jadx`, `unzip`, and strings inspection only; `docs/protocol/ipega-phase1-inventory.md` states no vendor code executed. | closed |
| T-01-02 | Information Disclosure | `.evidence/phase1` raw and decompile output | mitigate | `.gitignore` excludes raw, HCI, decompile, and app-log evidence; committed docs and manifest keep sanitized pointers only. | closed |
| T-01-03 | Tampering | Static clue verification status | mitigate | `tools/phase1/validate-fixtures.mjs --full` rejects verified static clue lines without clue, capture, and fixture linkage. | closed |
| T-01-04 | Tampering | Diagnostic report contract | mitigate | `android-diagnostic/SPEC.md` lists required report names and `MainActivity.kt` emits structured report hooks for permission, input, BLE, Classic, rumble, and phone vibration states. | closed |
| T-01-05 | Denial of Service | Android Bluetooth permissions | mitigate | Diagnostic spec and source emit `permission_state`, `permission_blocked`, `unavailable`, and failed scan states rather than treating absence as protocol proof. | closed |
| T-01-06 | Elevation of Privilege | Diagnostic app scope | mitigate | `android-diagnostic/README.md` and `SPEC.md` mark the module diagnostic-only and exclude production Android host, LAN, desktop HID, profiles, and visualizer behavior. | closed |
| T-01-07 | Spoofing | Bluetooth scan result identity | mitigate | Capture manifest rows record device name/address policy, service ids, capture/action context, clue id, and expected interpretation; docs do not infer protocol from name alone. | closed |
| T-01-08 | Information Disclosure | logcat, bugreport, HCI, and app-observed captures | mitigate | Raw captures stay under ignored `.evidence/phase1/`; manifest rows use `local://` pointers and docs note sanitized address prefixes. | closed |
| T-01-09 | Denial of Service | Android permission or device unavailable state | mitigate | Hardware docs and diagnostic code record denied permissions, unavailable hardware, no-device, no-frame, timeout, and no-motor as explicit outcomes. | closed |
| T-01-10 | Tampering | Normalized fixture semantics | mitigate | Fixture schema requires `raw_ref`, `clue_id`, `capture_id`, explicit control/kind/phase/value fields, and candidate semantics where evidence is imperfect. | closed |
| T-01-11 | Repudiation | Hardware action provenance | mitigate | Capture manifest stores origin, action, expected interpretation, raw/app-log refs, capture ids, and normalized fixture paths for each evidence item. | closed |
| T-01-12 | Information Disclosure | Raw capture refs in fixtures | mitigate | Fixtures commit only compact JSONL rows with `local://.evidence/phase1/...` refs; `.gitignore` keeps raw evidence out of git. | closed |
| T-01-13 | Spoofing | Haptic success claim | mitigate | `docs/protocol/ipega-phase1-haptics.md` and `haptics.jsonl` require Android `phone_vibrate` state plus human phone-vibration confirmation, and separate deferred gun motor status. | closed |
| T-01-14 | Tampering | Final verification status | mitigate | Full validator requires required fixture files/events and rejects rows lacking known clue ids or capture manifest linkage to raw refs and normalized fixtures. | closed |
| T-01-15 | Repudiation | Haptic command provenance | mitigate | Haptic docs, manifest row `phone-vibrate-001`, and normalized fixture store API path, requested duration, raw ref, clue id, capture id, action, and outcome. | closed |
| T-01-SC | Tampering | Gradle, npm, pip, cargo, or package installs | mitigate | Phase 1 records no package-manager installs outside the approved Gradle diagnostic build; dependency coordinates and repositories were human-approved before build/install. | closed |

## Key Evidence

- `.gitignore` excludes `.evidence/phase1/raw/`, `.evidence/phase1/hci/`, `.evidence/phase1/decompile/`, `.evidence/phase1/app-logs/`, and diagnostic build output.
- `docs/protocol/ipega-phase1-inventory.md` inventories all local refs, labels the 0-byte ARGunPro archive invalid/deferred, records tool provenance, and states static inspection only.
- `docs/protocol/ipega-phase1-clues.md` keeps static rows as hypotheses unless linked to hardware capture and normalized fixtures.
- `android-diagnostic/README.md` defines diagnostic-only scope, out-of-scope production behavior, dependency review gate, ignored evidence paths, and capture workflow.
- `android-diagnostic/SPEC.md` requires explicit permission, input, BLE, Classic, frame, rumble, and failed/unavailable reports.
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` emits permission, scan, GATT, Classic, rumble, phone vibration, and failure-state reports.
- `docs/protocol/ipega-phase1-hardware.md` records dependency approval, direct pairing failure, no standard InputDevice, BLE `fff0`, GATT `fff1`/`fff3`/`fff5`, `fff3` control captures, Classic unavailable evidence, and sanitized address policy.
- `docs/evidence/manifests/phase1-captures.jsonl` links each captured or deferred evidence row to `capture_id`, `clue_id`, action, origin, raw/app-log ref, normalized fixture path, and expected interpretation.
- `fixtures/ipega/normalized/*.jsonl` contains replayable handshake, trigger, reload, joystick, X/Y/A/B, and haptic rows with clue/capture/raw-ref linkage.
- `fixtures/ipega/normalized/README.md` documents required fixture fields and says verified status needs static clue, hardware capture, and normalized fixture linkage.
- `docs/protocol/ipega-phase1-haptics.md` documents Android phone vibration as the v1 feedback proof and keeps physical gun motor command discovery deferred.
- `tools/phase1/validate-fixtures.mjs --self-test`, `--quick`, and `--full` pass and enforce manifest, fixture, clue, raw-ref, and full-coverage linkage.

## Accepted Risks Log

No accepted risks.

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-11 | 16 | 16 | 0 | Codex secure-phase |

## Sign-Off

- [x] All threats have a disposition.
- [x] Accepted risks documented.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

**Approval:** verified 2026-06-11
