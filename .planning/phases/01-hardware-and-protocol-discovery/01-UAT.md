---
status: complete
phase: 01-hardware-and-protocol-discovery
source: [01-01-SUMMARY.md, 01-02-SUMMARY.md, 01-03-SUMMARY.md, 01-04-SUMMARY.md, 01-05-SUMMARY.md]
started: 2026-06-11T16:32:17.620Z
updated: 2026-06-11T16:45:53.893Z
---

## Current Test

[testing complete]

## Tests

### 1. User Flow - Evidence Entry Point
expected: Open the Phase 1 evidence docs and summaries. You should be able to tell, without hidden setup knowledge, that Phase 1 verified the real iPega protocol path enough for Android-host implementation: local refs were inventoried, physical BLE/GATT evidence was captured, normalized fixtures exist, and phone vibration is the v1 feedback path.
result: pass

### 2. User Flow - Static Reference Inventory
expected: Open `docs/protocol/ipega-phase1-inventory.md` and `docs/protocol/ipega-phase1-clues.md`. You should see every local APK/XAPK reference accounted for, invalid/deferred archives called out, and static Bluetooth/control/haptic clues treated as hypotheses until linked to physical evidence.
result: pass

### 3. User Flow - Physical Protocol Evidence
expected: Open `docs/protocol/ipega-phase1-hardware.md`. You should see that the gun did not appear as a standard Android InputDevice, direct Android Settings pairing failed, BLE service `fff0` was discovered, `fff1`/`fff3`/`fff5` GATT characteristics were found, and `fff3` notifications carried control evidence.
result: pass

### 4. User Flow - Normalized Control Evidence
expected: Open `fixtures/ipega/normalized/handshake.jsonl`, `trigger.jsonl`, `reload.jsonl`, `joystick.jsonl`, and `buttons-xyab.jsonl`. You should see replayable normalized rows for handshake, trigger, reload, joystick directions, and X/Y/A/B candidates, each tied back to capture evidence.
result: pass

### 5. User Flow - Feedback Decision
expected: Open `docs/protocol/ipega-phase1-haptics.md` and `fixtures/ipega/normalized/haptics.jsonl`. You should see Android phone vibration documented as the v1 feedback proof and physical gun motor rumble explicitly deferred, without claiming unsupported gun-motor success.
result: pass

### 6. Technical Check - Fixture Validator Self Test
expected: Run `node tools/phase1/validate-fixtures.mjs --self-test`. The command should pass, proving the validator's parser and assertion logic still work.
result: pass

### 7. Technical Check - Fixture Validator Quick Gate
expected: Run `node tools/phase1/validate-fixtures.mjs --quick`. The command should pass against committed Phase 1 docs, manifest, and fixture shape.
result: pass

### 8. Technical Check - Fixture Validator Full Gate
expected: Run `node tools/phase1/validate-fixtures.mjs --full`. The command should pass and reject any Phase 1 protocol/control/haptic claim that lacks static clue, capture id, raw/app-log ref, and normalized fixture linkage.
result: pass

### 9. Coverage Check - Phase 1 Goal
expected: Starting from the Phase 1 goal, you should be able to trace DISC-01 through DISC-07 to committed docs, manifest rows, normalized fixtures, and validator coverage. The evidence should be enough for the Android host to target the BLE/GATT input path and phone-vibration feedback path.
result: pass

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
