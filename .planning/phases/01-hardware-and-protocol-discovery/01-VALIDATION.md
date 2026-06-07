---
phase: 01
slug: hardware-and-protocol-discovery
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-06
---

# Phase 01 - Validation Strategy

Per-phase validation contract for feedback sampling during execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Repo-local JSONL/schema/coverage validation plus manual Android hardware checklist |
| **Config file** | none - Wave 1 creates validator helper and fixture directories |
| **Quick run command** | `node tools/phase1/validate-fixtures.mjs --quick` |
| **Full suite command** | `node tools/phase1/validate-fixtures.mjs --full` |
| **Estimated runtime** | <10 seconds for committed docs/fixtures; manual hardware capture outside automated runtime |

## Sampling Rate

- **After every task commit:** Run `node tools/phase1/validate-fixtures.mjs --quick` once the helper exists.
- **After every plan wave:** Run `node tools/phase1/validate-fixtures.mjs --full`.
- **Before `$gsd-verify-work`:** Full suite must pass and manual hardware checklist rows must be complete.
- **Max feedback latency:** 10 seconds for automated fixture checks.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | DISC-01 | T-01-01 | APK/XAPK metadata derived without executing vendor code | static inventory | `node tools/phase1/validate-fixtures.mjs --quick` | yes | passed |
| 01-01-02 | 01 | 1 | DISC-01 | T-01-02 | Generated decompile output ignored; committed clue index has provenance | static clue validation | `node tools/phase1/validate-fixtures.mjs --quick` | yes | passed |
| 01-02-01 | 02 | 2 | DISC-02, DISC-03 | T-02-01 | Diagnostic requests Bluetooth permissions explicitly and records denied/unavailable states | source/doc validation | `node tools/phase1/validate-fixtures.mjs --quick` | yes | passed |
| 01-02-02 | 02 | 2 | DISC-02, DISC-03, DISC-04 | T-02-02 | Hardware evidence manifests avoid committing raw capture blobs | fixture validation | `node tools/phase1/validate-fixtures.mjs --full` | yes | passed |
| 01-03-01 | 03 | 3 | DISC-04, DISC-05, DISC-07 | T-03-01 | Normalized fixtures preserve raw refs and clue ids for replay | fixture validation | `node tools/phase1/validate-fixtures.mjs --full` | yes | passed |
| 01-03-02 | 03 | 3 | DISC-04, DISC-06, DISC-07 | T-03-02 | v1 haptic evidence requires observed phone vibration; physical gun motor rows remain deferred | fixture validation | `node tools/phase1/validate-fixtures.mjs --full` | yes | passed |
| 01-03-03 | 03 | 3 | DISC-01, DISC-02, DISC-03, DISC-04, DISC-05, DISC-06, DISC-07 | T-03-03 | Final evidence gate rejects static-only findings | coverage validation | `node tools/phase1/validate-fixtures.mjs --full` | yes | passed |

## Wave 0 Requirements

Existing infrastructure does not cover phase requirements. Plan 01 must create:

- [x] `tools/phase1/validate-fixtures.mjs` - validates committed docs, capture manifest rows, JSONL parseability, control coverage, and static+hardware+fixture linkage.
- [x] `docs/protocol/ipega-phase1-inventory.md` - inventory target for DISC-01.
- [x] `docs/protocol/ipega-phase1-clues.md` - static clue index target.
- [x] `docs/evidence/manifests/phase1-captures.jsonl` - capture pointer manifest.
- [x] `fixtures/ipega/normalized/README.md` - fixture schema and coverage index.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Physical gun appears or does not appear as Android input device | DISC-02 | Requires real Android device and iPega hardware | Run the diagnostic on device, record `InputDevice` ids/sources and any `KeyEvent`/`MotionEvent` logs in `docs/protocol/ipega-phase1-hardware.md`. |
| BLE and Classic service visibility | DISC-03 | Requires real Bluetooth scan against physical gun | Run diagnostic BLE/Classic scan, save service/socket observations in capture manifest rows `ble_scan` and `classic_scan`. |
| Raw control/app-observed frame capture | DISC-04 | Requires pressing physical trigger/reload/stick/X/Y/A/B | For each action, save ignored raw capture path, committed manifest row, static clue id, and expected interpretation. |
| Phone vibration feedback | DISC-06 | Requires Android log plus human phone-vibration confirmation | Run diagnostic `Phone Vibrate 1s`; record app log ref, observed phone vibration, and ack/status. Physical gun motor attempts may be documented as deferred, but final v1 verification must not block on motor activation. |

## Validation Sign-Off

- [x] All tasks have `<automated>` verify commands after Plan 01 creates `tools/phase1/validate-fixtures.mjs`.
- [x] Sampling continuity: no 3 consecutive implementation tasks without automated verify.
- [x] Wave 1 creates missing validation infrastructure before dependent plans run.
- [x] No watch-mode flags.
- [x] Feedback latency <10 seconds for automated checks.
- [x] Manual hardware checks are explicitly listed; v1 haptic feedback requires phone vibration confirmation, while physical gun motor rumble is deferred.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** complete
