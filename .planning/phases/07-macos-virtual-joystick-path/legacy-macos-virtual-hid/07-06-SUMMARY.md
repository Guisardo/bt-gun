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

duration: 5 min to checkpoint
completed: checkpoint-pending
---

# Phase 07 Plan 06: HIDDriverKit Fallback Checkpoint Summary

**CoreHID `corehid-runtime-blocked` selected a lab-only HIDDriverKit fallback, now stopped before System Extension activation.**

## Performance

- **Duration:** 5 min to checkpoint
- **Started:** 2026-06-10T17:12:00Z
- **Checkpointed:** 2026-06-10T17:17:28Z
- **Tasks:** 2 complete, 1 checkpoint pending
- **Files modified:** 8

## Accomplishments

- Consumed the Plan 07-05 CoreHID gate and selected the fallback branch.
- Created `docs/setup/macos-driverkit-fallback.md` with exact lab-only build, signing, activation, status, output probe, rollback, and log commands.
- Recorded `phase7-driverkit-fallback-selected-corehid-runtime-blocked` and `phase7-driverkit-approval-required` manifest rows.
- Added narrow DriverKit and System Extension source scaffolds for the fallback path.

## Task Commits

1. **Task 1: Consume CoreHID gate and choose fallback branch** - `34f28ba` (docs)
2. **Task 2: Minimal HIDDriverKit report bridge and host activation scaffold** - `afd63a4` (feat)
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

## Verification

- PASS Task 1 branch scan: `rg -n "corehid-pass|corehid-visibility-failed|corehid-output-failed|corehid-runtime-blocked|phase7-driverkit-fallback-skipped-corehid-pass|HIDDriverKit" docs/evidence/manifests/phase7-macos-virtual-hid.jsonl docs/setup/macos-driverkit-fallback.md .planning/phases/07-macos-virtual-joystick-path/07-06-SUMMARY.md`.
- PASS manifest parse: `jq -c . docs/evidence/manifests/phase7-macos-virtual-hid.jsonl`.
- PASS Task 2 static scaffold scan: `rg -n "IOHIDDevice|handleReport|setReport|getReport|OSSystemExtensionRequest|com.apple.developer.driverkit|driverkit.family.hid|0x1209|0xB707|0x01|0x02|LAN|HapticCommand|ControlServer" native/macos-hid-driverkit docs/setup/macos-driverkit-fallback.md`.
- PASS plist lint: `plutil -lint native/macos-hid-driverkit/BTGunHidDriver/Info.plist native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.entitlements`.
- PASS host scaffold syntax: `swiftc -parse native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.swift`.
- PASS Task 3 checkpoint scan: `rg -n "USER APPROVAL REQUIRED|systemextensionsctl|hidutil|ioreg|phase7-driverkit-system-extension-approved|phase7-driverkit-cli-enumeration|phase7-driverkit-output-probe|rollback" docs/setup/macos-driverkit-fallback.md docs/evidence/manifests/phase7-macos-virtual-hid.jsonl .planning/phases/07-macos-virtual-joystick-path/07-06-SUMMARY.md`.

## Deviations from Plan

None - plan executed exactly as written up to the approval checkpoint.

## Issues Encountered

- DriverKit proof is blocked by required user approval and possible Apple entitlement/provisioning availability.
- DESK-03 and DESK-06 remain pending; no macOS production support is claimed from this fallback scaffold.
- The DriverKit C++ source is a source scaffold only. Full compile/sign/activation proof requires a local Xcode DriverKit project and approved entitlements, so it is intentionally stopped at the checkpoint.

## User Setup Required

Task 3 requires explicit approval before running any DriverKit/System Extension activation or OS security-state commands. See `docs/setup/macos-driverkit-fallback.md`.

Exact approval target:

```bash
/private/tmp/btgun-driverkit-derived/Build/Products/Debug/BTGunHidHostApp.app/Contents/MacOS/BTGunHidHostApp activate
systemextensionsctl list
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|MaxInputReportSize|MaxOutputReportSize'
native/macos-hid-helper/.build/debug/BtGunMacosHidOutputProbe --strength 180 --duration-ms 120 --ttl-ms 500
```

Potential security-relaxed lab commands require separate explicit approval because they change OS security state:

```bash
csrutil disable
sudo systemextensionsctl developer on
sudo systemextensionsctl developer off
csrutil enable
```

## Next Phase Readiness

Not ready for Plan 07-07. Continue Plan 07-06 Task 3 only after explicit user approval or after the user reports the entitlement/provisioning blocker.

## Self-Check: PASSED

- Found `docs/setup/macos-driverkit-fallback.md`.
- Found `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.cpp`.
- Found `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.h`.
- Found `native/macos-hid-driverkit/BTGunHidDriver/Info.plist`.
- Found `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.swift`.
- Found `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.entitlements`.
- Found `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl`.
- Found task commits `34f28ba` and `afd63a4` in git history.
- Confirmed no System Extension activation, install, removal, rollback, reboot, SIP, developer-mode, or other OS security-state command was run.

---
*Phase: 07-macos-virtual-joystick-path*
*Checkpoint: 2026-06-10*
