---
phase: 06-windows-virtual-joystick-path
plan: 04
subsystem: windows-runtime-wiring
tags: [kotlin, gradle, tdd, windows-vhf, control-server, haptics, smoke]
requires:
  - phase: 06-windows-virtual-joystick-path
    provides: Windows VHF backend bridge and Plan 05 CI artifact proof
provides:
  - Live `ControlServer` trusted UDP callback to Windows VHF backend runtime wiring
  - Windows output report drain to authenticated Android phone haptic send path
  - Launch gate for `btgun.windows.driver.enabled` and `btgun.windows.driver.bridge.path`
  - Real `windows-vhf` smoke entrypoint requiring a Plan 05 driver bridge artifact
affects: [phase-06, windows-target-proof, phase-09-visualizer, haptic-routing]
tech-stack:
  added: []
  patterns: [trusted callback chaining, runtime diagnostics without raw packets, fail-closed real-backend smoke]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsVhfBackendSmokeMain.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntimeTest.kt
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
key-decisions:
  - "WindowsBackendRuntime attaches only to ControlServer.onUdpInputReceived, preserving existing callbacks and keeping LAN/session/auth ownership in ControlServer."
  - "Desktop launch enables the real Windows backend only when btgun.windows.driver.enabled=true and an explicit bridge path is provided."
  - "The real windows-vhf smoke command requires a Plan 05 btgun-driver-bridge.exe artifact path and never falls back to Phase 5 stubs."
patterns-established:
  - "Runtime diagnostics expose lifecycle, last publish result, stale flag, source sequence, haptic send result, and routed count without raw packets or secrets."
  - "Windows output reports become haptic commands only by draining WindowsVirtualControllerBackend output bytes."
requirements-completed: [DESK-02, DESK-05]
duration: 11 min
started: 2026-06-10T02:00:03Z
completed: 2026-06-10T02:10:47Z
---

# Phase 06 Plan 04: Live Windows Runtime Wiring Summary

**Trusted desktop input now routes through the real Windows VHF backend runtime, and drained Windows output reports route back to authenticated Android phone haptics.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-06-10T02:00:03Z
- **Completed:** 2026-06-10T02:10:47Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added RED coverage for trusted UDP callback publishing, callback preservation, stale report behavior, output-report haptic routing, and no-session haptic diagnostics.
- Implemented `WindowsBackendRuntime` with `WindowsBackendRuntimeConfig`, trusted callback chaining, backend publish diagnostics, and output report drain through `ControlServer.sendHapticCommand`.
- Added launch gating through `btgun.windows.driver.enabled=true` plus required `btgun.windows.driver.bridge.path`, with visible Swing diagnostics when disabled or missing.
- Added `smokeDesktopBackendWindowsVhf` and `WindowsVhfBackendSmokeMainKt` for real backend smoke use with Plan 05 artifact validation and sanitized JUnit XML.

## Task Commits

1. **Task 1: Runtime tests for live input and output haptic routing** - `5a1de4f` (test)
2. **Task 2: Runtime wiring and Windows VHF launch flag** - `680e4c7` (feat)
3. **Task 3: Windows VHF smoke entrypoint** - `a0fbf0f` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt` - Runtime attachment between trusted `ControlServer` input, `WindowsVirtualControllerBackend`, and phone haptic sends.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsVhfBackendSmokeMain.kt` - Real `windows-vhf` smoke entrypoint with Plan 05 bridge artifact validation and JUnit XML output.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntimeTest.kt` - Main-style tests for runtime publish, stale behavior, callback preservation, output haptics, and no-session diagnostics.
- `desktop-companion/build.gradle.kts` - Registers `WindowsBackendRuntimeTestKt` and `smokeDesktopBackendWindowsVhf`.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` - Adds Windows backend system-property launch gate.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Adds concise Windows backend lifecycle/publish/stale/haptic diagnostics while retaining pairing/server ownership.

## Decisions Made

- Runtime wiring starts from `ControlServer.onUdpInputReceived`; no new UDP socket, pairing, auth, profile, or driver-owned session lifecycle was added.
- Missing `btgun.windows.driver.bridge.path` when Windows backend is enabled fails closed as a visible UI diagnostic instead of starting a partial runtime.
- The real backend smoke requires `btgun-driver-bridge.exe` plus Plan 05 `build-metadata.json` layout, so copied source-only ABI or stub paths cannot satisfy it.

## Verification

- PASS RED: pinned JDK17 offline Gradle failed only on missing planned `WindowsBackendRuntime` and `WindowsBackendRuntimeConfig` symbols.
- PASS GREEN/final: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- PASS: `rg -n "WindowsBackendRuntimeTestKt|WindowsBackendRuntime|btgun\\.windows\\.driver\\.enabled|btgun\\.windows\\.driver\\.bridge\\.path|smokeDesktopBackendWindowsVhf|WindowsVhfBackendSmokeMainKt|TEST-btgun-windows-vhf.xml" ...`
- PASS: `smokeDesktopBackendWindowsVhf` without a bridge path failed closed and wrote `build/test-results/btgun-smoke/windows-vhf/TEST-btgun-windows-vhf.xml`.
- PASS: smoke XML redaction scan found no fixture bytes, QR data, proof values, stream keys, private key material, or HMAC key text.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ran pinned JDK17 Gradle outside sandbox after socket denial**
- **Found during:** Task 1 RED verification
- **Issue:** Sandbox Gradle startup failed with `java.net.SocketException: Operation not permitted` while creating file-lock socket services.
- **Fix:** Used the user-provided pinned JDK17 offline Gradle command outside the sandbox for RED, GREEN, and final verification.
- **Files modified:** None
- **Verification:** RED produced expected missing-symbol failure; GREEN and final Gradle runs passed.
- **Committed in:** N/A, verification environment only.

**2. [Rule 1 - Bug] Fixed nullable callback restore compile error**
- **Found during:** Task 2 GREEN verification
- **Issue:** `WindowsBackendRuntime.close()` restored `ControlServer.onUdpInputReceived` through a nullable receiver.
- **Fix:** Introduced a non-null local before restoring the previous callback.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt`
- **Verification:** Pinned JDK17 offline Gradle progressed past main compile.
- **Committed in:** `680e4c7`

**3. [Rule 1 - Bug] Renamed colliding test fake bridge**
- **Found during:** Task 2 GREEN verification
- **Issue:** `FakeWindowsDriverBridge` duplicated a private top-level class name in the same Kotlin package, breaking compilation of existing Windows backend tests.
- **Fix:** Renamed the new runtime test helper to `RuntimeFakeWindowsDriverBridge`.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntimeTest.kt`
- **Verification:** Pinned JDK17 offline Gradle passed.
- **Committed in:** `680e4c7`

---

**Total deviations:** 3 auto-fixed (1 Rule 3, 2 Rule 1)
**Impact on plan:** No product scope change. Fixes were limited to verification environment and compile correctness for planned runtime tests.

## Issues Encountered

- Sandbox Gradle startup remains blocked by file-lock socket creation, so all meaningful Gradle verification used the pinned JDK17 command outside the sandbox.
- No authentication gates occurred.

## Known Stubs

None. Nullable diagnostics and disabled-runtime launch values are intentional lifecycle states, not unwired UI placeholders.

## Threat Flags

None. The new trusted input and output-haptic trust boundaries were declared in the plan threat model. Diagnostics store only lifecycle/result/state metadata and no raw packets, fixture bytes, proof values, stream keys, or private key material.

## TDD Gate Compliance

- RED gate commit exists: `5a1de4f`
- GREEN gate commit exists after RED: `680e4c7`
- Task 3 was not marked TDD and has its own feature commit: `a0fbf0f`

## User Setup Required

None for Plan 04. Running the real `smokeDesktopBackendWindowsVhf` command requires a Plan 05 artifact path for `btgun-driver-bridge.exe` and an installed/runnable Windows VHF target environment.

## Next Phase Readiness

Plan 06-06 can use the runtime launch flags and real smoke entrypoint for approval-gated Windows target proof. Fake/replay smoke remains debug/test only; final D-09 proof still requires live Android/gun input and real Windows-visible joystick evidence.

## Self-Check: PASSED

- Found `.planning/phases/06-windows-virtual-joystick-path/06-04-SUMMARY.md` on disk.
- Found created runtime, smoke entrypoint, and runtime test files on disk.
- Found task commits `5a1de4f`, `680e4c7`, and `a0fbf0f` in git history.
- Final pinned JDK17 offline Gradle verification passed.

---
*Phase: 06-windows-virtual-joystick-path*
*Completed: 2026-06-10*
