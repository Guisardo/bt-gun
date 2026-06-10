---
phase: 07-macos-virtual-joystick-path
plan: 02
subsystem: macos-virtual-hid
tags: [kotlin, gradle, tdd, macos-hid, haptics]

requires:
  - phase: 05-desktop-backend-contract-and-smoke-harness
    provides: SemanticControllerState, BT Gun v1 descriptor, backend haptic command contract
  - phase: 06-windows-virtual-joystick-path
    provides: Windows report packing and output report mapping pattern
  - phase: 07-macos-virtual-joystick-path
    provides: CoreHID feasibility result and local DriverKit fallback decision
provides:
  - Deterministic macOS input report ID 1 packing for Phase 5 semantic controller state
  - Strict macOS output report ID 2 validation and pattern-null phone haptic command mapping
  - Main-style Gradle tests for macOS report byte contracts
affects: [phase-07, macos-backend, macos-helper-client, driverkit-fallback, desk-03, desk-06]

tech-stack:
  added: []
  patterns: [plain Kotlin main-style tests, pure report byte mappers, little-endian HID report packing]

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPacker.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapper.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPackerTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapperTest.kt
    - .planning/phases/07-macos-virtual-joystick-path/07-02-SUMMARY.md
  modified:
    - desktop-companion/build.gradle.kts
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "macOS input report ID 1 uses byte 0 report id, byte 1 trigger/reload/X/Y/A/B bits, then stickX/stickY/aimX/aimY signed int16 little-endian axes."
  - "macOS output report ID 2 version 1 uses one strength byte, duration/TTL uint16 little-endian fields, and two zero reserved bytes before creating a pattern-null HapticCommand."
  - "Plan 07-02 addresses DESK-03 and DESK-06 report byte contracts only; it does not complete OS-visible joystick or OS-origin output proof."

patterns-established:
  - "macOS report mappers stay pure and side-effect free before helper/backend/runtime code consume them."
  - "Stale semantic state clears buttons and stick axes while preserving aim axes and stale/sourceSequence metadata."

requirements-completed: []

duration: 5 min
completed: 2026-06-10
---

# Phase 07 Plan 02: macOS HID Report Contract Summary

**macOS HID input report packing and output-to-phone-haptic byte validation for the Phase 7 helper/backend path.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-10T16:19:26Z
- **Completed:** 2026-06-10T16:23:53Z
- **Tasks:** 2
- **Files modified:** 5 production/test files plus 3 planning files

## Accomplishments

- Added RED tests for macOS report ID 1 input packing, stale clearing, axis clamping, report ID 2 output mapping, malformed output rejection, blank command id rejection, and mapper-only proof limits.
- Implemented `MacosHidReportPacker` for fixed semantic button/axis mapping into a 10-byte macOS HID input report.
- Implemented `MacosOutputReportMapper` for 9-byte report ID 2 validation into bounded `HapticCommand` values with `pattern = null`.
- Registered both macOS report test mains in the desktop companion Gradle test task.

## Task Commits

1. **Task 1: RED tests for macOS report contract** - `05a9056` (test)
2. **Task 2: GREEN implementation for report packer and output mapper** - `c66b135` (feat)
3. **Plan metadata:** this commit (docs)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPacker.kt` - Packs `SemanticControllerState` into report ID 1 bytes.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapper.kt` - Validates report ID 2 bytes and maps them to `HapticCommand`.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPackerTest.kt` - Tests input report byte layout, axis bounds, and stale behavior.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapperTest.kt` - Tests valid output mapping, malformed output rejection, blank command id rejection, and D-07/D-08 mapper-only proof limits.
- `desktop-companion/build.gradle.kts` - Registers both new main-style macOS report tests.
- `.planning/STATE.md` - Advances Phase 7 to Plan 3 with Plan 07-02 complete.
- `.planning/ROADMAP.md` - Marks Plan 07-02 complete while leaving Phase 7 incomplete.

## Decisions Made

- Input report ID 1 layout is fixed at 10 bytes: report id, six-button bitmask, stick X/Y signed int16 little-endian, and aim X/Y signed int16 little-endian.
- Output report ID 2 layout is fixed at 9 bytes: report id, version, strength byte, duration uint16 little-endian, TTL uint16 little-endian, flags byte, and reserved byte.
- Stale state clears active controls and stick axes but keeps aim axes plus `MacosInputReport.stale` and `sourceSequence` metadata for later diagnostics.
- DESK-03 and DESK-06 remain pending at requirement level until later OS-visible device and OS-origin output proof plans pass.

## Verification

- PASS RED: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` stopped only on missing planned `MacosHidReportPacker`, `MacosOutputReportMapper`, and `MACOS_*` production constants.
- PASS GREEN: same pinned-JDK offline Gradle command completed successfully.
- PASS: `rg -n "MacosHidReportPackerTestKt|MacosOutputReportMapperTestKt" desktop-companion/build.gradle.kts`
- PASS: `rg -n "MACOS_INPUT_REPORT_ID|MACOS_INPUT_REPORT_LENGTH_BYTES|stale|clamp|bad report id|bad length|bad version|zero duration|zero ttl|oversized duration|oversized ttl|unsupported flags|reserved byte|blank command id|D-07/D-08" desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos`
- PASS: `rg -n "ProfileMapper|dead.zone|smoothing|sensitivity|stream key|HMAC key|qr_secret|private key" desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos` returned no matches.
- PASS: `git log --oneline --all | rg "05a9056|c66b135"`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test Bug] Removed RED-phase Kotlin inference cascade**
- **Found during:** Task 1 RED verification
- **Issue:** Missing `MacosOutputReportMapper` caused extra `Cannot infer type` cascade errors around `requireNotNull`, making the RED output less precise than the acceptance criteria.
- **Fix:** Added explicit `HapticCommand` type annotations in the RED test.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapperTest.kt`
- **Verification:** RED rerun failed only on missing planned `Macos*` symbols/constants.
- **Committed in:** `05a9056`

**2. [Rule 3 - Blocking] Ran Gradle tests outside sandbox**
- **Found during:** Task 1 RED verification
- **Issue:** Sandbox blocked Gradle file-lock/socket setup with `java.net.SocketException: Operation not permitted`.
- **Fix:** Reran the same pinned offline test command with approved unsandboxed execution.
- **Files modified:** None
- **Verification:** RED produced expected missing-symbol output; GREEN full desktop tests passed.
- **Committed in:** N/A, verification environment only.

---

**Total deviations:** 2 auto-fixed (Rule 1: 1, Rule 3: 1)  
**Impact on plan:** No product scope change. The macOS report contracts were implemented and verified as planned.

## Issues Encountered

- Gradle requires approved unsandboxed execution on this host because sandboxing blocks its local file-lock/socket service.
- No authentication gates occurred.

## Known Stubs

None. The `pattern = null` output mapping is intentional v1 phone-haptic behavior, not an unwired placeholder.

## Threat Flags

None. The new semantic-state-to-report and output-report-to-haptic trust boundaries were in the plan threat model, and no new network endpoints, auth paths, file access patterns, schema changes, secrets, QR payloads, HMAC keys, or private key material were added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 07-03 can consume the fixed macOS input/output report constants and pure mappers when adding helper client, backend lifecycle, and honest capabilities. OS-visible macOS joystick proof and OS-origin output-to-phone-haptic proof remain pending for later Phase 7 plans.

## Self-Check: PASSED

- Found all created macOS packer, mapper, and test files on disk.
- Found task commits `05a9056` and `c66b135` in git history.
- Confirmed RED and GREEN TDD gate commits exist in order.
- Confirmed full desktop companion tests pass with the pinned offline Gradle command.
- Confirmed no forbidden profile/session-secret terms appear in the new macOS backend package.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
