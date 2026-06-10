---
phase: 07-macos-virtual-joystick-path
plan: 06
subsystem: macos-virtual-hid
tags: [macos, driverkit, hiddriverkit, system-extension, checkpoint, pack-03]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: Plan 07-05 CoreHID gate with `corehid-runtime-blocked`
provides:
  - Lab-only HIDDriverKit fallback branch selection for `corehid-runtime-blocked`
  - Approval-gated DriverKit setup and proof commands
affects: [phase-07, driverkit-fallback, desk-03, desk-06, pack-03]

tech-stack:
  added: [HIDDriverKit source scaffold, SystemExtensions host scaffold]
  patterns:
    - DriverKit fallback starts only after CoreHID non-pass evidence
    - System Extension activation requires explicit user approval before any OS security-state change

key-files:
  created:
    - docs/setup/macos-driverkit-fallback.md
    - native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.cpp
    - native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.h
    - native/macos-hid-driverkit/BTGunHidDriver/Info.plist
    - native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.swift
    - native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.entitlements
    - .planning/phases/07-macos-virtual-joystick-path/07-06-SUMMARY.md
  modified:
    - docs/evidence/manifests/phase7-macos-virtual-hid.jsonl

key-decisions:
  - "Plan 07-06 selected the local-dev-only HIDDriverKit fallback because Plan 07-05 recorded `corehid-runtime-blocked`."
  - "No DriverKit activation, install, removal, rollback, reboot, SIP, developer-mode, or other OS security-state command can run before explicit user approval."

patterns-established:
  - "DriverKit provider remains a HID report byte bridge; Kotlin keeps LAN/session/security/UDP mapping/haptic transport ownership."

requirements-completed: []

duration: checkpoint
completed: 2026-06-10
---

# Phase 07 Plan 06: HIDDriverKit Fallback Checkpoint Summary

**CoreHID `corehid-runtime-blocked` selected a lab-only HIDDriverKit fallback, now stopped before System Extension activation.**

## Performance

- **Duration:** checkpoint in progress
- **Started:** 2026-06-10T17:12:00Z
- **Completed:** checkpoint pending user approval
- **Tasks:** 2 complete, 1 checkpoint pending
- **Files modified:** 8

## Accomplishments

- Consumed the Plan 07-05 CoreHID gate and selected the fallback branch.
- Created `docs/setup/macos-driverkit-fallback.md` with exact lab-only build, signing, activation, status, output probe, rollback, and log commands.
- Recorded `phase7-driverkit-fallback-selected-corehid-runtime-blocked` and `phase7-driverkit-approval-required` manifest rows.
- Added narrow DriverKit and System Extension source scaffolds for the fallback path.

## Task Commits

1. **Task 1: Consume CoreHID gate and choose fallback branch** - pending commit
2. **Task 2: Minimal HIDDriverKit report bridge and host activation scaffold** - pending commit
3. **Task 3: DriverKit entitlement, approval, and proof gate** - checkpoint pending

## Files Created/Modified

- `docs/setup/macos-driverkit-fallback.md` - Lab-only DriverKit fallback commands, risks, rollback, and proof rows.
- `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` - Adds fallback-selection and approval-required rows.
- `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.cpp` - Minimal `IOHIDDevice` report bridge source.
- `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.h` - DriverKit report bridge interface.
- `native/macos-hid-driverkit/BTGunHidDriver/Info.plist` - DriverKit personality scaffold.
- `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.swift` - `OSSystemExtensionRequest` activation/deactivation host scaffold.
- `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.entitlements` - System Extension and DriverKit entitlement scaffold.

## Decisions Made

- Use DriverKit fallback only because `corehid-runtime-blocked` is recorded.
- Treat fallback as local-development-only and lab-only until approval and proof rows pass.
- Stop before any System Extension activation, install, rollback, entitlement/provisioning use, SIP, developer-mode, reboot, or other OS security-state change.

## Deviations from Plan

None - plan executed exactly as written up to the approval checkpoint.

## Issues Encountered

- DriverKit proof is blocked by required user approval and possible Apple entitlement/provisioning availability.
- DESK-03 and DESK-06 remain pending; no macOS production support is claimed from this fallback scaffold.

## User Setup Required

Task 3 requires explicit approval before running any DriverKit/System Extension activation or OS security-state commands. See `docs/setup/macos-driverkit-fallback.md`.

## Next Phase Readiness

Not ready for Plan 07-07. Continue Plan 07-06 Task 3 only after explicit user approval or after the user reports the entitlement/provisioning blocker.

## Self-Check: PENDING

Self-check will be finalized when the checkpoint is approved or closed as blocked.

---
*Phase: 07-macos-virtual-joystick-path*
*Checkpoint: 2026-06-10*
