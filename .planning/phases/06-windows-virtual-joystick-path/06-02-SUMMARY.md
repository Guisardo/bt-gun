---
phase: 06-windows-virtual-joystick-path
plan: 02
subsystem: windows-driver
tags: [windows, kmdf, vhf, hid, ioctl, inf, haptics]
requires:
  - phase: 06-windows-virtual-joystick-path
    provides: Windows report ID 1 and output report ID 2 constants from Plan 01
provides:
  - Public BT Gun VJoy IOCTL ABI and report structs
  - KMDF/VHF source driver with buffered IOCTL submission and output report queue
  - Root\BTGunVJoy INF metadata
  - User-mode driver bridge and HID output sender source
affects: [phase-06, windows-driver-ci, windows-target-proof, windows-backend-bridge]
tech-stack:
  added: []
  patterns: [KMDF VHF source driver, METHOD_BUFFERED IOCTL bridge, bounded HID output queue, source-only WDK build gate]
key-files:
  created:
    - windows/btgun-vjoy/include/BtGunVJoyIoctl.h
    - windows/btgun-vjoy/driver/BtGunVJoy.h
    - windows/btgun-vjoy/driver/BtGunVJoy.c
    - windows/btgun-vjoy/driver/BtGunVJoyDevice.c
    - windows/btgun-vjoy/driver/BtGunVJoyQueue.c
    - windows/btgun-vjoy/driver/BtGunVJoy.sln
    - windows/btgun-vjoy/driver/BtGunVJoy.vcxproj
    - windows/btgun-vjoy/package/btgunvjoy.inf
    - windows/btgun-vjoy/tools/driver-bridge/DriverBridge.cpp
    - windows/btgun-vjoy/tools/hid-output-sender/HidOutputSender.cpp
  modified: []
key-decisions:
  - "Windows driver ABI uses METHOD_BUFFERED IOCTLs with FILE_WRITE_DATA for input submission and FILE_READ_DATA for output/status reads."
  - "KMDF/VHF kernel code stays a HID report bridge only; LAN/session/security and haptic transport stay in user mode."
  - "Local macOS execution records the WDK/MSBuild build gate as source-only blocked; Plan 05 CI must validate build/sign/package before target use."
patterns-established:
  - "Report ID 1 input bytes are accepted only after size, ABI version, report ID, and button-mask validation."
  - "Report ID 2 output bytes are queued in a fixed-capacity kernel ring and read by user mode through IOCTL_BTGVJOY_READ_OUTPUT."
requirements-completed: [DESK-02, DESK-05, PACK-02]
duration: 8 min
completed: 2026-06-10
---

# Phase 06 Plan 02: Windows VHF Driver Source Summary

**KMDF/VHF virtual HID driver source with public IOCTL ABI, root-device INF, and user-mode report helper tools.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-10T00:21:50Z
- **Completed:** 2026-06-10T00:29:43Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Added `BtGunVJoyIoctl.h` with `GUID_DEVINTERFACE_BTGUNVJOY`, report IDs/lengths, packed ABI structs, and `METHOD_BUFFERED` IOCTLs with read/write access bits.
- Added KMDF/VHF source driver files that create the device interface, start VHF, submit validated report ID 1 input through `VhfReadReportSubmit`, and queue report ID 2 output from `EvtVhfAsyncOperationWriteReport`.
- Added `btgunvjoy.inf`, WDK project files, `DriverBridge.cpp`, and `HidOutputSender.cpp` for later CI package/build and Windows target proof.

## Task Commits

1. **Task 1: Public IOCTL ABI and HID descriptor** - `4f87f5b` (feat)
2. **Task 2: KMDF/VHF bridge driver source and INF** - `54f7f44` (feat)
3. **Task 3: User-mode helper tools for IOCTL and fallback output** - `7669809` (feat)

## Files Created/Modified

- `windows/btgun-vjoy/include/BtGunVJoyIoctl.h` - Public report IDs, lengths, IOCTL codes, GUID, and packed ABI structs.
- `windows/btgun-vjoy/driver/BtGunVJoy.h` - Driver context, VHF state, queue state, and function declarations.
- `windows/btgun-vjoy/driver/BtGunVJoy.c` - `DriverEntry` and device-add dispatch.
- `windows/btgun-vjoy/driver/BtGunVJoyDevice.c` - HID gamepad descriptor, device interface creation, VHF create/start, cleanup.
- `windows/btgun-vjoy/driver/BtGunVJoyQueue.c` - IOCTL dispatch, report validation, VHF input submit, bounded output queue, status read.
- `windows/btgun-vjoy/driver/BtGunVJoy.sln` - Visual Studio solution for the driver.
- `windows/btgun-vjoy/driver/BtGunVJoy.vcxproj` - WDK KMDF driver project with VHF library dependency.
- `windows/btgun-vjoy/package/btgunvjoy.inf` - Root\BTGunVJoy HIDClass package metadata.
- `windows/btgun-vjoy/tools/driver-bridge/DriverBridge.cpp` - `SUBMIT_INPUT`, `READ_OUTPUT`, `STATUS`, `QUIT` line protocol over `DeviceIoControl`.
- `windows/btgun-vjoy/tools/hid-output-sender/HidOutputSender.cpp` - Fallback HID output sender for report ID 2 with strength/duration/TTL flags.

## Decisions Made

- The kernel driver owns only HID report bridging, VHF lifecycle, IOCTL validation, bounded output queuing, and status counters.
- `IOCTL_BTGVJOY_SUBMIT_INPUT` requires exact `BTGVJOY_INPUT_REPORT` size, ABI version, report ID 1, and six-button mask validation before VHF submission.
- `EvtVhfAsyncOperationWriteReport` accepts report ID 2 only, normalizes report-buffer forms into the public 9-byte layout, and queues at most 16 output reports.
- The helper line protocol prints only `OK`, `OUTPUT <hex>`, `STATUS <json>`, or `ERR <code>` from the IOCTL bridge.

## Verification

- PASS: `rg -n "GUID_DEVINTERFACE_BTGUNVJOY|IOCTL_BTGVJOY_SUBMIT_INPUT|IOCTL_BTGVJOY_READ_OUTPUT|IOCTL_BTGVJOY_GET_STATUS|Root|BT Gun VJoy" windows/btgun-vjoy`
- PASS: `rg -n "VHF_CONFIG_INIT|EvtVhfAsyncOperationWriteReport|VhfCreate|VhfStart|VhfReadReportSubmit|IOCTL_BTGVJOY_SUBMIT_INPUT|IOCTL_BTGVJOY_READ_OUTPUT|IOCTL_BTGVJOY_GET_STATUS" windows/btgun-vjoy/driver windows/btgun-vjoy/package`
- PASS: `rg -n "SUBMIT_INPUT|READ_OUTPUT|STATUS|QUIT|DeviceIoControl|HidD_SetOutputReport|--strength|--duration-ms|--ttl-ms" windows/btgun-vjoy/tools`
- PASS: `rg -n "TODO|FIXME|placeholder|coming soon|not available|QR|HMAC|pairing proof|stream key|private key|raw QR|secret" windows/btgun-vjoy` returned no matches.
- SOURCE-ONLY SKIPPED: `powershell -NoProfile -ExecutionPolicy Bypass -Command "msbuild windows\btgun-vjoy\driver\BtGunVJoy.vcxproj /p:Configuration=Release /p:Platform=x64 /m"` could not run on this macOS host because `powershell` is unavailable. This is the plan-defined build gate path; Plan 05 CI must build/sign/package before target proof treats the driver as usable.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Windows WDK/MSBuild validation is unavailable on this macOS executor. The driver is source-only until the planned Windows CI build/sign/package gate passes.
- No authentication gates occurred.

## Known Stubs

None. No placeholders, TODOs, mock data, or unwired report paths were added.

## Threat Flags

None. The new kernel IOCTL, VHF output callback, and driver package surfaces are the surfaces planned in the Plan 02 threat model and include the required mitigations.

## User Setup Required

None - no external service configuration required in this plan.

## Next Phase Readiness

Plan 06-03 can build the Windows desktop backend bridge against `BtGunVJoyIoctl.h`. Plan 06-05 must validate WDK build/sign/package artifacts before Plan 06-04 smoke or Plan 06-06 target proof treats the driver as installable or target-usable.

## Self-Check: PASSED

- Found all 10 created Windows driver/helper files on disk.
- Found task commits `4f87f5b`, `54f7f44`, and `7669809` in git history.
- Found `.planning/phases/06-windows-virtual-joystick-path/06-02-SUMMARY.md` on disk after writing.

---
*Phase: 06-windows-virtual-joystick-path*
*Completed: 2026-06-10*
