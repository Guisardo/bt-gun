---
phase: 06-windows-virtual-joystick-path
plan: 01
subsystem: desktop-windows-backend
tags: [kotlin, gradle, tdd, windows-hid, haptics]
requires:
  - phase: 05-desktop-backend-contract-and-smoke-harness
    provides: SemanticControllerState, BT Gun v1 descriptor, backend haptic command contract
provides:
  - Deterministic Windows input report ID 1 packing for Phase 5 semantic controller state
  - Deterministic Windows output report ID 2 validation and haptic command mapping
  - Main-style Gradle tests for Windows report byte contracts
affects: [phase-06, windows-driver, windows-backend-bridge]
tech-stack:
  added: []
  patterns: [plain Kotlin main-style tests, pure report byte mappers, little-endian HID report packing]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPackerTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapperTest.kt
  modified:
    - desktop-companion/build.gradle.kts
key-decisions:
  - "Windows input report ID 1 uses byte 0 report id, byte 1 trigger/reload/X/Y/A/B bits, then stickX/stickY/aimX/aimY signed int16 little-endian axes."
  - "Windows output report ID 2 version 1 uses one strength byte, duration/TTL uint16 little-endian fields, and two zero reserved bytes before creating a pattern-null HapticCommand."
patterns-established:
  - "Windows report mappers stay pure and side-effect free before driver and bridge code consume them."
  - "Stale semantic state clears buttons and stick axes while preserving aim axes and stale/sourceSequence metadata."
requirements-completed: [DESK-02, DESK-05]
duration: 12 min
completed: 2026-06-10
---

# Phase 06 Plan 01: Windows HID Report Contract Summary

**Windows HID input report packing and output-to-phone-haptic byte validation for the Phase 6 VHF bridge.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-06-10T00:00:46Z
- **Completed:** 2026-06-10T00:12:54Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added RED tests for report ID 1 input packing, stale clearing, axis clamping, report ID 2 output mapping, and malformed output rejection.
- Implemented `WindowsHidReportPacker` for fixed Phase 5 semantic button/axis mapping into a 10-byte Windows HID input report.
- Implemented `WindowsOutputReportMapper` for 9-byte report ID 2 validation into bounded `HapticCommand` values with `pattern = null`.
- Registered both Windows report test mains in the desktop companion Gradle test task.

## Task Commits

1. **Task 1: RED tests for Windows report contract** - `73a6871` (test)
2. **Task 2: GREEN implementation for report packer and output mapper** - `42a5d5d` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt` - Packs `SemanticControllerState` into report ID 1 bytes.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt` - Validates report ID 2 bytes and maps them to `HapticCommand`.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPackerTest.kt` - Tests input report byte layout, axis bounds, and stale behavior.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapperTest.kt` - Tests valid output mapping and malformed output rejection.
- `desktop-companion/build.gradle.kts` - Registers both new main-style Windows report tests.

## Decisions Made

- Input report ID 1 layout is fixed at 10 bytes: report id, six-button bitmask, stick X/Y signed int16 little-endian, and aim X/Y signed int16 little-endian.
- Output report ID 2 layout is fixed at 9 bytes: report id, version, strength byte, duration uint16 little-endian, TTL uint16 little-endian, flags byte, and reserved byte.
- Stale state clears active controls and stick axes but keeps aim axes plus `WindowsInputReport.stale` and `sourceSequence` metadata for later diagnostics.

## Verification

- PASS RED: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` stopped only on missing `WindowsHidReportPacker`, `WINDOWS_INPUT_REPORT_*`, `WindowsOutputReportMapper`, and `WINDOWS_OUTPUT_REPORT_*` production symbols.
- PASS GREEN: same pinned-JDK offline Gradle command completed successfully.
- PASS: `rg -n "WindowsHidReportPackerTestKt|WindowsOutputReportMapperTestKt" desktop-companion/build.gradle.kts`
- PASS: `rg -n "WINDOWS_INPUT_REPORT_ID|WINDOWS_INPUT_REPORT_LENGTH_BYTES|stale|WINDOWS_OUTPUT_REPORT_ID|WINDOWS_OUTPUT_REPORT_VERSION|WINDOWS_OUTPUT_REPORT_LENGTH_BYTES|bad report id|bad length|zero duration|zero ttl|oversized duration|oversized ttl|unsupported flags" desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows`
- PASS: `rg -n "vJoy|ViGEm|ProfileMapper|dead.zone|smoothing|sensitivity|LAN|secret" desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows` returned no matches.
- PASS: `git log --oneline --all | rg "73a6871"`
- PASS: `git log --oneline --grep="feat(06-01): implement Windows HID report contracts" --all`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pinned JDK 17 and disabled Gradle Java auto-detection**
- **Found during:** Task 1 RED verification
- **Issue:** The exact offline Gradle command first hit sandbox file-lock/socket denial, then the approved run stalled in Gradle Java toolchain probing.
- **Fix:** Ran the same offline test target with approval, explicit JDK 17 `JAVA_HOME`, `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle`, and `org.gradle.java.installations.auto-detect=false`.
- **Files modified:** None
- **Verification:** RED produced the expected missing-symbol result; GREEN passed the full desktop companion tests.
- **Committed in:** N/A, verification environment only.

---

**Total deviations:** 1 auto-fixed (Rule 3 verification blocker)
**Impact on plan:** No product scope change. The report contracts were implemented and verified as planned.

## Issues Encountered

- Gradle Java toolchain probing can stall on this macOS host unless JDK 17 is pinned and auto-detection is disabled.
- No authentication gates occurred.

## Known Stubs

None. The `pattern = null` output mapping is intentional v1 phone-haptic behavior, not an unwired placeholder.

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, schema changes, secrets, QR payloads, HMAC keys, or private key material were added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 06-02 can consume the fixed input/output report constants and byte layout when building the VHF/KMDF driver, IOCTL ABI, INF, and helper tools.

## Self-Check: PASSED

- Found all created Windows packer, mapper, and test files on disk.
- Found task commits `73a6871` and `42a5d5d` in git history.
- Found `.planning/phases/06-windows-virtual-joystick-path/06-01-SUMMARY.md` on disk.

---
*Phase: 06-windows-virtual-joystick-path*
*Completed: 2026-06-10*
