---
phase: 01-hardware-and-protocol-discovery
plan: 01
subsystem: protocol-discovery
tags: [apk, xapk, bluetooth, ble, jsonl, reverse-engineering]

requires: []
provides:
  - DISC-01 local APK/XAPK inventory
  - Static Bluetooth/control/rumble clue index
  - Evidence manifest and normalized JSONL fixture contract
  - Package-free Phase 1 validator
affects: [hardware-capture, android-diagnostic, normalized-fixtures, rumble-proof]

tech-stack:
  added: [node-mjs, apktool, jadx, unzip]
  patterns:
    - Static clues stay hypotheses until linked to hardware capture and normalized fixtures.
    - Large raw capture and decompile output stays under ignored .evidence/phase1 paths.

key-files:
  created:
    - .gitignore
    - docs/evidence/manifests/phase1-captures.jsonl
    - fixtures/ipega/normalized/README.md
    - tools/phase1/validate-fixtures.mjs
    - docs/protocol/ipega-phase1-inventory.md
    - docs/protocol/ipega-phase1-clues.md
  modified: []

key-decisions:
  - "Use fff0/fff1/fff3/fff5 and SPP UUID clues only as hardware-test hypotheses until captures and fixtures exist."
  - "Treat ARGunPro_1.0.19_apkcombo.com.xapk as invalid because the local file is 0 bytes."

patterns-established:
  - "Evidence chain: clue_id -> capture_id -> normalized fixture row."
  - "Validator modes: self-test for parser logic, quick for committed docs, full for clue/capture/fixture linkage status."

requirements-completed: [DISC-01]

duration: 10 min
completed: 2026-06-06
---

# Phase 01 Plan 01: Static APK/XAPK Inventory and Evidence Scaffold Summary

**Static iPega reference inventory with BLE/SPP/control hypotheses, ignored evidence paths, JSONL schema docs, and package-free validation.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-06T04:18:38Z
- **Completed:** 2026-06-06T04:28:38Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added ignored local evidence paths for raw captures, HCI logs, decompile output, app logs, and diagnostic build output.
- Created committed capture manifest scaffold and normalized fixture README with required `btgun.ipega.normalized.v1` fields.
- Added `tools/phase1/validate-fixtures.mjs` with `--self-test`, `--quick`, and `--full` modes and no package deps.
- Inventoried every local ref archive under `docs/refs/`, including tool provenance and invalid 0-byte ARGunPro status.
- Built stable static clue ids for BLE `fff0`/`fff1`/`fff3`/`fff5`, Classic SPP, input bridge, and rumble candidate paths.

## Task Commits

1. **Task 1: Create evidence storage contract and validator** - `f260960` (feat)
2. **Task 2: Inventory every local APK/XAPK reference** - `54b0764` (docs)
3. **Task 3: Build static decompile clue index** - `5767ade` (docs)

## Files Created/Modified

- `.gitignore` - Ignores Phase 1 raw/decompile/app-log evidence and Android diagnostic build output.
- `docs/evidence/manifests/phase1-captures.jsonl` - Parseable capture manifest scaffold.
- `fixtures/ipega/normalized/README.md` - Normalized JSONL schema and linkage rules.
- `tools/phase1/validate-fixtures.mjs` - Package-free manifest, fixture, inventory, and clue validator.
- `docs/protocol/ipega-phase1-inventory.md` - Complete local APK/XAPK inventory for DISC-01.
- `docs/protocol/ipega-phase1-clues.md` - Static clue index with stable ids and planned hardware tests.

## Decisions Made

- Static clue rows remain `unverified` until a hardware capture and normalized fixture exist.
- `ARGunPro_1.0.19_apkcombo.com.xapk` remains invalid/deferred because the local file is 0 bytes.
- `ARGun Library_1.0.1_apkcombo.com.apk` stays secondary because its manifest has no Bluetooth permissions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed extracted WorldsAR APK file permissions**
- **Found during:** Task 2 (Inventory every local APK/XAPK reference)
- **Issue:** `unzip` extracted `com.lenze.armagic.apk` with unreadable `----------` mode, blocking apktool decode.
- **Fix:** Ran `chmod 644 .evidence/phase1/decompile/extracted/com.lenze.armagic.apk` on ignored generated evidence output.
- **Files modified:** Ignored local file under `.evidence/phase1/decompile/extracted/`.
- **Verification:** `apktool d` decoded WorldsAR successfully afterward.
- **Committed in:** Not committed; ignored generated evidence only.

---

**Total deviations:** 1 auto-fixed (Rule 3).
**Impact on plan:** No scope change. Fix only made ignored generated evidence readable for static inspection.

## Issues Encountered

None.

## Known Stubs

- `docs/evidence/manifests/phase1-captures.jsonl:1` - Intentional scaffold row only. Hardware capture rows are Plan 03 work; Plan 01 must not invent capture facts.

## Verification

- `node tools/phase1/validate-fixtures.mjs --self-test` - PASS
- `node tools/phase1/validate-fixtures.mjs --quick` - PASS
- `node tools/phase1/validate-fixtures.mjs --full` - PASS
- `git log --oneline --grep="(01-01)"` - found all 3 task commits

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 02. Later hardware work can use `clue_id` values from `docs/protocol/ipega-phase1-clues.md`, capture rows in `docs/evidence/manifests/phase1-captures.jsonl`, and normalized fixture rows under `fixtures/ipega/normalized/`.

## Self-Check: PASSED

- Created files exist on disk.
- Task commits `f260960`, `54b0764`, and `5767ade` exist in git history.
- Validator self-test, quick, and full gates pass.

---
*Phase: 01-hardware-and-protocol-discovery*
*Completed: 2026-06-06*
