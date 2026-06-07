---
phase: 01-hardware-and-protocol-discovery
plan: 04
subsystem: normalized-fixtures
tags: [jsonl, fixtures, protocol, controls, handshake, validation]

requires:
  - phase: 01-03
    provides: Physical BLE/GATT and control capture evidence.
provides:
  - Normalized handshake fixtures
  - Normalized trigger, reload, joystick, and X/Y/A/B fixtures
  - Capture manifest linkage from hardware evidence to replay fixtures
  - Validator coverage for handshake/control replay without hardware
affects: [protocol-discovery, android-host, regression-fixtures, hardware-capture]

tech-stack:
  added: []
  patterns:
    - Each normalized row links `raw_ref`, `clue_id`, and `capture_id`.
    - Candidate semantics stay explicit when capture quality is noisy.
    - `--full` enforces control/handshake coverage but Plan 05 owns haptic completion.

key-files:
  created:
    - fixtures/ipega/normalized/handshake.jsonl
    - fixtures/ipega/normalized/trigger.jsonl
    - fixtures/ipega/normalized/reload.jsonl
    - fixtures/ipega/normalized/joystick.jsonl
    - fixtures/ipega/normalized/buttons-xyab.jsonl
  modified:
    - fixtures/ipega/normalized/README.md
    - docs/evidence/manifests/phase1-captures.jsonl
    - tools/phase1/validate-fixtures.mjs

key-decisions:
  - "Normalize app-level BLE GATT as the first replayable protocol path."
  - "Represent joystick as four digital direction button events."
  - "Keep trigger and X/Y/A/B semantics as candidates where evidence windows were imperfect."
  - "Keep haptic fixture and final DISC-06 proof in Plan 05 scope."

patterns-established:
  - "Fixture rows use schema btgun.ipega.normalized.v1."
  - "Manifest rows point to normalized fixture files for each capture_id."
  - "Validator rejects fixture rows without matching manifest raw refs."

requirements-completed: [DISC-04, DISC-05]
requirements-partially-covered: [DISC-07]

duration: not recorded
completed: 2026-06-06
---

# Phase 01 Plan 04: Normalized Control Fixtures Summary

**Replayable JSONL fixtures for BLE handshake and physical control mappings.**

## Accomplishments

- Added normalized handshake fixture rows for BLE scan, GATT connect/service discovery, `fff1`, `fff3`, and `fff5`.
- Added trigger fixture rows for candidate down/up semantics: `ARGun KeyPressed` and zero frame on `fff3`.
- Added reload fixture rows for `B8DOWN` and `B8UP`.
- Added joystick fixture rows as digital direction events: `stick_left`, `stick_right`, `stick_up`, and `stick_down`.
- Added X/Y/A/B fixture rows for `BA`, `B3`, `B2`, and `B9` down/up candidates.
- Updated validator coverage so fixture rows must parse as JSONL and link back to manifest rows by `capture_id` and `raw_ref`.
- Preserved Plan 05 boundary: no physical gun motor success is claimed by control fixtures.

## Normalized Handshake and Control Fixtures

- `fixtures/ipega/normalized/handshake.jsonl` - `ble_scan`, `ble_gatt`, `fff1`, `fff3`, and `fff5` observed rows.
- `fixtures/ipega/normalized/trigger.jsonl` - trigger candidate down/up from `trigger-001`.
- `fixtures/ipega/normalized/reload.jsonl` - reload down/up from `reload-001`.
- `fixtures/ipega/normalized/joystick.jsonl` - digital stick directions from `joystick-001`.
- `fixtures/ipega/normalized/buttons-xyab.jsonl` - X/Y/A/B down/up candidates from button captures.

## Task Commits

- None found in current git history for `(01-04)` at summary time.

## Files Created/Modified

- `fixtures/ipega/normalized/handshake.jsonl` - BLE advertisement, GATT connect, and characteristic observation rows.
- `fixtures/ipega/normalized/trigger.jsonl` - Trigger candidate normalized button events.
- `fixtures/ipega/normalized/reload.jsonl` - Reload normalized button events.
- `fixtures/ipega/normalized/joystick.jsonl` - Joystick normalized digital direction events.
- `fixtures/ipega/normalized/buttons-xyab.jsonl` - X/Y/A/B normalized button events.
- `fixtures/ipega/normalized/README.md` - Fixture schema and proof-chain rules.
- `docs/evidence/manifests/phase1-captures.jsonl` - Capture rows linked to fixture paths.
- `tools/phase1/validate-fixtures.mjs` - Fixture parsing, raw-ref linkage, and coverage checks.

## Decisions Made

- BLE `fff3` notification payloads are the normalized input source for current fixtures.
- `fff5` is normalized only as a discovered read|write characteristic; no output command or motor success is inferred.
- Joystick directions are modeled as buttons because hardware evidence showed discrete `B4`/`B5`/`B6`/`B7` events.
- No verified control claim can rely on static clues alone.

## Deviations from Plan

- Haptic fixture coverage appears in the current validator, but Plan 05 owns DISC-06 completion and final haptic proof.
- No `(01-04)` task commits were present in the current history; fixture work appears in the dirty/untracked workspace state.

## Issues Encountered

- Trigger up semantics are candidate-only because the observed release/idle frame is all zeros.
- X/Y/A/B mapping depends partly on sequence capture plus separate/noisy per-button windows.
- A-up uses `button-a-up-noisy-001` until a cleaner A-only recapture exists.

## Known Deferred Work

- Clean per-button recaptures can improve confidence for noisy X/Y/A/B evidence.
- Future Android host code must preserve candidate/verified status rather than flattening provenance.
- Physical gun motor command discovery remains outside Plan 04.

## Verification

- `node tools/phase1/validate-fixtures.mjs --quick` - PASS
- `node tools/phase1/validate-fixtures.mjs --full` - PASS after current haptic fixture coverage existed

## Next Phase Readiness

Ready for Plan 05 and downstream host planning. Handshake/control behavior can now be replayed without the physical gun through normalized JSONL fixtures linked to capture evidence.

## Self-Check: PASSED

- Handshake/control fixtures exist and parse.
- Fixture rows link to capture manifest rows.
- Control semantics remain factual where capture evidence is imperfect.

---
*Phase: 01-hardware-and-protocol-discovery*
*Completed: 2026-06-06*
