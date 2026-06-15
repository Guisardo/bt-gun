---
phase: 07-macos-virtual-joystick-path
plan: 03
subsystem: macos-virtual-hid
tags: [kotlin, gradle, tdd, macos-corehid, helper-protocol, haptics]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: CoreHID runtime blocker decision and macOS report packer/output mapper contracts from Plans 01 and 02
provides:
  - Kotlin `MacosHidHelper` line-protocol client for HELLO, SUBMIT_INPUT, READ_OUTPUT, STATUS, and QUIT
  - `MacosVirtualControllerBackend` using `MacosHidReportPacker` and `MacosOutputReportMapper`
  - `macos-corehid` capability preset with honest OS-visible and output-report limits
  - Native Swift helper stdin/stdout byte bridge with malformed counters and output queue
affects: [phase-07, macos-backend-runtime, corehid-helper, driverkit-fallback, desk-03, desk-06]

tech-stack:
  added: []
  patterns: [fake helper TDD seam, sanitized helper line protocol, CoreHID byte bridge, proof-gated capabilities]

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidHelperClient.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackendTest.kt
    - .planning/phases/07-macos-virtual-joystick-path/07-03-SUMMARY.md
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt
    - native/macos-hid-helper/Sources/BtGunMacosHidHelper/main.swift
    - docs/setup/macos-virtual-hid.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "MacosVirtualControllerBackend publishes only packed report bytes through the helper and leaves LAN/session/security/profile/haptic transport ownership outside native code."
  - "macos-corehid capabilities claim output-report support only after both OS-visible and set-report callback proof status are true."
  - "Native Swift helper remains a CoreHID byte bridge with HELLO/SUBMIT_INPUT/READ_OUTPUT/STATUS/QUIT only."

patterns-established:
  - "macOS backend tests inject a fake MacosHidHelperClient so desktop tests need no CoreHID entitlement or OS-visible device."
  - "CoreHID helper protocol returns exact OK, ERR safe token, OUTPUT hex, and STATUS JSON lines."

requirements-completed: []

duration: 10 min
completed: 2026-06-10
---

# Phase 07 Plan 03: macOS Helper Backend Bridge Summary

**macOS helper client and backend bridge publish packed input reports and drain helper output bytes while capabilities stay proof-gated.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-10T16:27:48Z
- **Completed:** 2026-06-10T16:37:29Z
- **Tasks:** 3
- **Files modified:** 7 production/test/docs files plus planning metadata

## Accomplishments

- Added RED tests for macOS backend lifecycle, helper submit failure handling, output drain, simulated-output limits, honest capabilities, and byte-only helper interactions.
- Implemented `MacosHidHelperClient`, `MacosHidHelperResult`, `MacosHidHelperStatus`, production `MacosHidHelper`, and `MacosVirtualControllerBackend`.
- Added `BackendCapabilityPresets.macosCoreHid(...)` with explicit `os-visible-device`, `output-report`, and pattern limitations until proof status allows support claims.
- Reworked the Swift helper into the Kotlin-expected line protocol while preserving `--probe` behavior for the existing CoreHID build script.

## Task Commits

1. **Task 1: RED tests for helper boundary and backend lifecycle** - `f3af8af` (test)
2. **Task 2: GREEN helper client, backend, and capability preset** - `c9da720` (feat)
3. **Task 3: Native helper line protocol implementation** - `95874aa` (feat)
4. **Plan metadata:** this commit (docs)

## Files Created/Modified

- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackendTest.kt` - Main-style tests for backend lifecycle, publish, helper errors, output drain, simulation limits, and capability honesty.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidHelperClient.kt` - Kotlin process client for HELLO, SUBMIT_INPUT, READ_OUTPUT, STATUS, and QUIT.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt` - `VirtualControllerBackend` implementation using macOS report packer/mapper only.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - Adds `macos-corehid` capability preset.
- `desktop-companion/build.gradle.kts` - Registers `MacosVirtualControllerBackendTestKt`.
- `native/macos-hid-helper/Sources/BtGunMacosHidHelper/main.swift` - Implements helper line protocol, CoreHID input dispatch, output queue, and sanitized status/errors.
- `docs/setup/macos-virtual-hid.md` - Documents the helper protocol and status shape.

## Decisions Made

- The helper boundary stays report-byte and status oriented. Native code does not receive companion control-plane or profile data.
- `simulateOutputReport` remains mapper-only and never flips `macos-corehid` output capability proof.
- `osVisible` remains false in helper status until a later proof command records CLI/UI visibility; Plan 07-05/07-07 still own OS-visible and OS-origin output proof.

## Verification

- PASS RED: pinned JDK17 offline Gradle failed only on missing planned macOS backend/helper/capability symbols plus override cascade from the intentionally missing helper interface.
- PASS GREEN/final: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- PASS native source build: `CLANG_MODULE_CACHE_PATH=/private/tmp/btgun-swift-module-cache swift build --package-path native/macos-hid-helper --scratch-path /private/tmp/btgun-macos-hid-helper-build -c debug`
- PASS boundary scan: `rg -n "Socket|ServerSocket|Udp|ControlServer|Pairing|HMAC|qr|QR|secret|proof|stream key|private key|Profile|dead.zone|smoothing|sensitivity|auth" desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos` returned no matches.
- PASS helper protocol scan found HELLO, SUBMIT_INPUT, READ_OUTPUT, STATUS, QUIT, dispatchInputReport, receivedSetReportRequestOfType, and malformed counters in the native helper/docs.
- PASS TDD gate: `git log --oneline --all --grep="test(07-03)"` and `--grep="feat(07-03)"` show RED before GREEN.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test Bug] Removed RED-phase Kotlin inference cascade**
- **Found during:** Task 1 RED verification
- **Issue:** The unresolved `macosCoreHid` factory caused extra lambda `it` inference errors, making RED noisier than the planned missing-symbol failure.
- **Fix:** Added explicit `BackendCapabilities` typing and named lambda parameters.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackendTest.kt`
- **Verification:** RED rerun failed only on planned missing macOS backend/helper/capability symbols plus override cascade from missing `MacosHidHelperClient`.
- **Committed in:** `f3af8af`

**2. [Rule 3 - Blocking] Ran Gradle and Swift build outside sandbox**
- **Found during:** Task 1 RED, Task 2 GREEN, Task 3 native verification, and final verification
- **Issue:** Sandbox blocked Gradle file-lock sockets and Swift module cache writes.
- **Fix:** Used approved unsandboxed pinned Gradle command and Swift build with `/private/tmp` module/scratch paths.
- **Files modified:** None
- **Verification:** Desktop Gradle tests and Swift helper build passed.
- **Committed in:** N/A, verification environment only.

**3. [Rule 1 - Swift Build Bug] Marked helper state as unchecked Sendable**
- **Found during:** Task 3 native verification
- **Issue:** Swift 6 rejected `BtGunVirtualDeviceDelegate` because its stored `HelperState` property was not Sendable.
- **Fix:** Marked `HelperState` as `@unchecked Sendable`; access remains guarded by `NSLock`.
- **Files modified:** `native/macos-hid-helper/Sources/BtGunMacosHidHelper/main.swift`
- **Verification:** Swift helper build passed.
- **Committed in:** `95874aa`

---

**Total deviations:** 3 auto-fixed (Rule 1: 2, Rule 3: 1)  
**Impact on plan:** Scope stayed inside planned tests, Kotlin backend/client, and native helper protocol. No DriverKit activation or OS security-state operation was run.

## Issues Encountered

- Gradle sandbox startup is still blocked by `java.net.SocketException: Operation not permitted`; approved unsandboxed verification was required.
- Swift build needed a writable `/private/tmp` module/scratch path outside the sandbox.
- No authentication gates occurred.

## Known Stubs

None. Empty queues and nullable process/state fields are lifecycle internals, not user-visible stubs. `pattern = null` remains intentional v1 phone-haptic behavior.

## Threat Flags

None. The new Kotlin-helper, helper-Kotlin, and helper-CoreHID trust boundaries were in the plan threat model and mitigated by exact command tokens, hex/length/status validation, sanitized `ERR` tokens, stderr discard, and no control-plane material crossing the helper boundary.

## User Setup Required

None - no external service configuration required. OS-visible CoreHID proof remains blocked by earlier entitlement/runtime policy and is owned by later Phase 7 plans.

## Next Phase Readiness

Plan 07-04 can wire live companion runtime to `MacosVirtualControllerBackend` and route drained `macos-output-report-*` commands through the authenticated desktop-to-Android haptic path. Plans 07-05 through 07-07 still must prove OS-visible macOS input and OS-origin output-to-phone-haptic behavior before DESK-03/DESK-06 completion.

## Self-Check: PASSED

- Found created macOS helper client, backend, test, and summary files on disk.
- Found task commits `f3af8af`, `c9da720`, and `95874aa` in git history.
- Confirmed RED and GREEN TDD gate commits exist in order.
- Confirmed pinned offline desktop Gradle tests pass.
- Confirmed native Swift helper source builds with temp module/scratch paths.
- Confirmed forbidden companion boundary terms do not appear in production macOS backend Kotlin.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
