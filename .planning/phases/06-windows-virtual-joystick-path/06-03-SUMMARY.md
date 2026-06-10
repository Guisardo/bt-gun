---
phase: 06-windows-virtual-joystick-path
plan: 03
subsystem: desktop-windows-backend
tags: [kotlin, gradle, tdd, windows-vhf, driver-bridge, haptics]
requires:
  - phase: 06-windows-virtual-joystick-path
    provides: Windows report packer/mapper and VHF driver helper line protocol from Plans 01 and 02
provides:
  - Windows VHF `VirtualControllerBackend` implementation for Phase 5 semantic controller state
  - Kotlin `WindowsDriverBridge` line-protocol client for `SUBMIT_INPUT`, `READ_OUTPUT`, and `STATUS`
  - `windows-vhf` capability preset with real output-report phone-haptic support
affects: [phase-06, windows-runtime-wiring, windows-target-proof, control-server-haptic-routing]
tech-stack:
  added: []
  patterns: [fake bridge TDD seam, sanitized helper line protocol, packer/mapper-only Windows backend]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsDriverBridge.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackend.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackendTest.kt
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt
key-decisions:
  - "WindowsVirtualControllerBackend publishes semantic state only through WindowsHidReportPacker and WindowsDriverBridge.submitInputReport."
  - "Windows output report bytes are drained from the helper bridge and mapped through WindowsOutputReportMapper with windows-output-report-* command ids."
  - "windows-vhf capabilities declare real output-report and phone-haptic support while keeping pattern output unsupported."
patterns-established:
  - "Windows backend tests inject a fake WindowsDriverBridgeClient so automated tests need no Windows target, WDK, or installed driver."
  - "WindowsDriverBridge accepts only exact OK, ERR, OUTPUT, and STATUS line-protocol responses and hides helper stderr."
requirements-completed: [DESK-02, DESK-05]
duration: 6 min
completed: 2026-06-10
---

# Phase 06 Plan 03: Windows Backend Bridge Summary

**Windows VHF desktop backend bridge that publishes packed semantic input reports and drains real output report bytes into phone haptic commands.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-06-10T00:36:41Z
- **Completed:** 2026-06-10T00:42:34Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added RED coverage for Windows backend lifecycle, D-13 fixed report mapping, bridge submit failure handling, D-16 output report haptic mapping, and `windows-vhf` capability flags.
- Implemented `WindowsDriverBridge` and `WindowsDriverBridgeClient` for the Plan 02 helper protocol without adding LAN, pairing, auth, or profile behavior to the Windows backend.
- Implemented `WindowsVirtualControllerBackend` using only `WindowsHidReportPacker` for publish and `WindowsOutputReportMapper` for output haptic drain.
- Added `BackendCapabilityPresets.windowsVhf()` with real output-report support, phone haptics, and explicit unsupported pattern output.

## Task Commits

1. **Task 1: RED tests for Windows backend bridge lifecycle** - `5b31742` (test)
2. **Task 2: GREEN Windows bridge and backend implementation** - `17b86de` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsDriverBridge.kt` - Kotlin process/line-protocol client for the Windows helper.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackend.kt` - Real Windows VHF backend implementation of `VirtualControllerBackend`.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackendTest.kt` - Main-style tests using an injected fake bridge.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - Adds `windows-vhf` capability preset.
- `desktop-companion/build.gradle.kts` - Registers `WindowsVirtualControllerBackendTestKt`.

## Decisions Made

- Use a small `WindowsDriverBridgeClient` seam so tests can fake bridge behavior while production uses the Plan 02 helper process.
- Keep submit errors as `BackendPublishResult.Rejected` and leave `currentState` unchanged when driver submission fails.
- Drain output reports only after backend start, map each bridge byte report through `WindowsOutputReportMapper`, and skip malformed output by returning no command.

## Verification

- PASS RED: pinned JDK17 offline Gradle failed only on missing planned Windows backend symbols: `WindowsVirtualControllerBackend`, `WindowsDriverBridgeClient`, `WindowsDriverBridgeResult`, `WindowsDriverBridgeStatus`, and `windowsVhf`.
- PASS GREEN: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- PASS final verification: same pinned JDK17 offline Gradle command completed successfully after both task commits.
- PASS: `rg -n "WindowsVirtualControllerBackendTestKt|WindowsHidReportPacker|WindowsOutputReportMapper|WindowsDriverBridgeResult|WindowsDriverBridgeStatus|windowsVhf" ...`
- PASS: `rg -n "Socket|ServerSocket|Udp|ControlServer|Pairing|HMAC|qr|QR|secret|proof|stream key|private key|Profile|dead.zone|smoothing|sensitivity|auth" desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows` returned no matches.
- PASS: `rg -n "println|printStackTrace|logger|log\\(|stderr|redirectError|Exception\\(|message" ...` found only `redirectError(ProcessBuilder.Redirect.DISCARD)`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ran pinned JDK17 Gradle outside sandbox after socket denial**
- **Found during:** Task 1 RED verification
- **Issue:** The plan Gradle command failed inside the sandbox with `java.net.SocketException: Operation not permitted` while creating Gradle file-lock socket services.
- **Fix:** Re-ran the user-provided pinned JDK17 offline Gradle command outside the sandbox for RED, GREEN, and final verification.
- **Files modified:** None
- **Verification:** RED produced the expected missing-symbol failure and GREEN/final runs passed.
- **Committed in:** N/A, verification environment only.

---

**Total deviations:** 1 auto-fixed (Rule 3 verification blocker)
**Impact on plan:** No product scope change. Verification used the requested fallback command because sandbox sockets blocked Gradle startup.

## Issues Encountered

- Sandbox Gradle startup is blocked by file-lock socket creation. The exact blocker was `java.net.SocketException: Operation not permitted`.
- No authentication gates occurred.

## Known Stubs

None. Nullable process fields and `pattern = null` haptic output are intentional lifecycle/model behavior, not unwired UI or data placeholders.

## Threat Flags

None. The new companion-helper process boundary and helper-output-to-haptic mapping are both declared in the plan threat model and mitigated by exact token parsing, bad hex/length rejection, packer/mapper-only report construction, and sanitized errors.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 06-04 can wire the live companion runtime to `WindowsVirtualControllerBackend`, then route drained output haptic commands through the authenticated control server without moving LAN/session/security into the Windows driver.

## Self-Check: PASSED

- Found `.planning/phases/06-windows-virtual-joystick-path/06-03-SUMMARY.md` on disk.
- Found created Windows bridge, backend, and test files on disk.
- Found task commits `5b31742` and `17b86de` in git history.
- Final pinned JDK17 offline Gradle verification passed.

---
*Phase: 06-windows-virtual-joystick-path*
*Completed: 2026-06-10*
