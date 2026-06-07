---
phase: 01-hardware-and-protocol-discovery
plan: 03
subsystem: hardware-capture
tags: [android, adb, bluetooth, ble, gatt, hardware-evidence]

requires:
  - phase: 01-02
    provides: Android diagnostic contract and skeleton for hardware observation.
provides:
  - Physical iPega capture workflow
  - Human-reviewed diagnostic build/install evidence
  - Standard Android InputDevice negative evidence
  - BLE advertisement, GATT, and control notification evidence
  - Classic pairing/SPP unavailable evidence
affects: [protocol-discovery, normalized-fixtures, android-diagnostic, rumble-proof]

tech-stack:
  added: [adb-capture-helper, android-diagnostic-apk]
  patterns:
    - Raw logcat, HCI, and dumpsys output stays under ignored .evidence/phase1 paths.
    - Committed evidence uses sanitized manifest pointers and explicit unavailable states.
    - Physical captures remain candidates until normalized fixtures link them.

key-files:
  created:
    - android-diagnostic/scripts/collect-phase1-captures.sh
    - docs/protocol/ipega-phase1-hardware.md
  modified:
    - android-diagnostic/README.md
    - android-diagnostic/app/src/main/AndroidManifest.xml
    - android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt
    - docs/evidence/manifests/phase1-captures.jsonl
    - tools/phase1/validate-fixtures.mjs

key-decisions:
  - "Direct Android Settings pairing is not a usable proof path for this gun."
  - "The physical gun does not appear as a standard Android InputDevice in captured scans."
  - "The first proven input path is app-level BLE GATT service fff0 with fff3 notifications."
  - "Physical gun motor rumble remains deferred; phone vibration proof belongs to Plan 05."

patterns-established:
  - "Capture rows store clue_id, capture_id, action, raw/app-log ref, status, and normalized fixture target."
  - "Control observations are candidate mappings until Plan 04 fixture normalization."

requirements-completed: [DISC-02, DISC-03]
requirements-partially-covered: [DISC-04]

duration: not recorded
completed: 2026-06-06
---

# Phase 01 Plan 03: Hardware Capture Workflow Summary

**Physical iPega capture evidence for InputDevice, BLE/GATT, Classic, controls, and deferred motor paths.**

## Accomplishments

- Added the Phase 1 hardware capture workflow and evidence rules for ignored raw logs, sanitized manifest rows, and honest unavailable states.
- Recorded human checkpoint evidence: Android phone connected over USB, gun visible to Android, direct Settings pairing failed, and dependency coordinates were reviewed before build/install.
- Built and installed `com.btgun.diagnostic` after dependency approval and Gradle cache/JVM fixes.
- Captured standard Android input scans showing no external gun/gamepad-like `InputDevice`, `KeyEvent`, or `MotionEvent`.
- Captured BLE advertisement evidence for `ARGunGame` service `0000fff0-0000-1000-8000-00805f9b34fb`.
- Captured app-level GATT connect/service discovery without OS pairing: `fff1` read|notify, `fff3` read|notify, and `fff5` read|write.
- Captured physical control notifications on `fff3` for trigger, reload, joystick directions, and X/Y/A/B candidates.
- Captured Classic unavailable evidence: `ARGunGame` was not bonded and no SPP/socket bytes were observed.

## Physical Capture Evidence

- `input-device-scan-001` - no standard Android gun/gamepad `InputDevice`.
- `ble-scan-001` - `ARGunGame` advertises BLE service `fff0`.
- `ble-gatt-discovery-001` - GATT service discovery found `fff1`, `fff3`, and `fff5`.
- `trigger-001` - trigger candidate: `ARGun KeyPressed` followed by zero frame on `fff3`.
- `reload-001` - reload candidate: `B8DOWN` and `B8UP` on `fff3`.
- `joystick-001` - digital stick candidates: left `B6`, right `B4`, up `B5`, down `B7`.
- `button-x-001`, `button-y-001`, `button-a-001`, `button-b-001` - X/Y/A/B candidates: `BA`, `B3`, `B2`, `B9`.
- `rumble-ble-fff5-001` and `rumble-classic-spp-001` - physical motor research stays deferred/pending.

## Task Commits

1. **Task 1: Create hardware capture workflow** - `d07e60b` (feat)
2. **Checkpoint: Record direct pairing status** - `49bc852` (docs)
3. **Checkpoint: Record no-build Bluetooth evidence** - `aa2375a` (docs)
4. **Checkpoint: Capture diagnostic BLE evidence** - `6a67726` (fix)
5. **Checkpoint: Add BLE GATT discovery capture** - `c1fa7e2` (feat)
6. **Checkpoint: Map trigger candidate** - `292b7ed` (docs)
7. **Checkpoint: Map reload candidate** - `4688fe4` (docs)
8. **Checkpoint: Map joystick candidate** - `1a5f45e` (docs)
9. **Checkpoint: Map X/Y/A/B candidates** - `70d1b44` (docs)

## Files Created/Modified

- `android-diagnostic/scripts/collect-phase1-captures.sh` - ADB/logcat capture helper for ignored evidence output.
- `docs/protocol/ipega-phase1-hardware.md` - Physical capture notes, dependency review, GATT/control evidence, and capture checklist.
- `docs/evidence/manifests/phase1-captures.jsonl` - Sanitized capture rows and normalized fixture targets.
- `android-diagnostic/README.md` - Physical capture workflow and build/install checkpoint notes.
- `android-diagnostic/app/src/main/AndroidManifest.xml` - Diagnostic permission/build compatibility updates.
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` - Diagnostic BLE/GATT capture hooks.
- `tools/phase1/validate-fixtures.mjs` - Manifest/fixture validation updates.

## Decisions Made

- Treat BLE `fff0`/`fff3` as the proven physical input path for Phase 1 fixtures.
- Treat joystick output as digital directions, not analog axes, unless later captures prove analog data.
- Keep X/Y/A/B as candidate mappings because some per-button windows were noisy or empty.
- Keep `fff5` as read|write only; do not claim physical gun motor success.

## Deviations from Plan

- Diagnostic build/install was performed after human dependency approval; Plan 02 had intentionally stopped before build/install.
- GATT discovery became necessary because direct OS pairing and no-build ADB evidence could not expose control frames.
- Physical motor testing stayed deferred; no gun motor success evidence was captured.

## Issues Encountered

- Direct Android Settings pairing failed and returned to unbonded state.
- Default Gradle cache initialization failed; a temporary Gradle user home was used.
- Android SDK 29 required legacy Bluetooth/location permission handling instead of Android 12 runtime nearby-device permissions.

## Known Deferred Work

- Plan 04 must normalize trigger, reload, joystick, X/Y/A/B, and handshake evidence into replayable fixtures.
- Plan 05 owns v1 phone vibration proof and final haptic evidence gate.
- Physical gun motor command discovery remains deferred research.

## Verification

- `node tools/phase1/validate-fixtures.mjs --quick` - PASS
- `node tools/phase1/validate-fixtures.mjs --full` - PASS after later fixture/haptic coverage existed

## Next Phase Readiness

Ready for Plan 04. Physical captures now provide concrete `capture_id`, `raw_ref`, `clue_id`, and expected fixture targets for normalized handshake/control replay.

## Self-Check: PASSED

- Physical capture evidence is documented.
- Static-only protocol claims were not marked verified.
- Raw evidence paths remain ignored and committed rows are sanitized.

---
*Phase: 01-hardware-and-protocol-discovery*
*Completed: 2026-06-06*
