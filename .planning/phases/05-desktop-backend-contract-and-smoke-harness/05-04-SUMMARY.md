---
phase: 05-desktop-backend-contract-and-smoke-harness
plan: 04
subsystem: desktop-backend
tags: [kotlin, gradle, tdd, smoke-harness, junit-xml]
requires:
  - phase: 05-03
    provides: Authenticated UDP receiver handoff to semantic controller state
provides:
  - macOS and Windows fake-input backend smoke commands
  - Receiver-first backend smoke runner using Phase 4 UDP fixture bytes
  - JUnit-style smoke XML artifacts under desktop-companion/build/test-results/btgun-smoke
affects: [phase-05, phase-06, phase-07, phase-10]
tech-stack:
  added: []
  patterns: [plain Kotlin main-style smoke, receiver-first fixture replay, no-dependency JUnit XML writer]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsBackendSmokeMain.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/smoke/BackendSmokeRunnerTest.kt
  modified:
    - desktop-companion/build.gradle.kts
key-decisions:
  - "Platform smoke commands use distinct macos-stub and windows-stub JavaExec entrypoints with separate XML paths."
  - "Smoke XML records only case names/results/timing; fixture bytes and stream authentication material stay out of artifacts."
patterns-established:
  - "Backend smoke cases must accept fixture datagrams through UdpInputReceiver before publishing semantic backend state."
  - "Platform stub commands fail the process only after writing JUnit-style XML evidence."
requirements-completed: [DESK-08]
duration: 10min
completed: 2026-06-09
---

# Phase 05 Plan 04: Platform Stub Smoke Commands Summary

**macOS and Windows backend stub commands now replay authenticated UDP fixtures into the shared backend and emit zero-failure JUnit-style XML.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-09T18:56:00Z
- **Completed:** 2026-06-09T19:05:57Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added a shared `BackendSmokeRunner` that replays the Phase 4 snapshot and edge fixture bytes through `UdpInputReceiver` before backend publish.
- Added a no-dependency `JunitSmokeXml` writer with XML escaping and zero-failure evidence output.
- Added `smokeDesktopBackendMacosStub` and `smokeDesktopBackendWindowsStub` Gradle commands with distinct platform entrypoints and XML paths.
- Verified both smoke XML files exist under `desktop-companion/build/test-results/btgun-smoke/`.

## Task Commits

1. **Task 1 RED: Smoke runner/XML tests** - `62e5891` (test)
2. **Task 1 GREEN: Smoke runner/XML implementation** - `479333f` (feat)
3. **Task 2: Platform smoke commands** - `52eb3bc` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` - Receiver-first fixture replay and backend publish smoke flow.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt` - JUnit-style XML renderer/writer with metacharacter escaping.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt` - macOS stub smoke entrypoint writing `TEST-btgun-macos-stub.xml`.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsBackendSmokeMain.kt` - Windows stub smoke entrypoint writing `TEST-btgun-windows-stub.xml`.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/smoke/BackendSmokeRunnerTest.kt` - Main-style tests for XML escaping, receiver replay, backend publish, and secret-free XML.
- `desktop-companion/build.gradle.kts` - Registers the smoke test class and both JavaExec smoke tasks.

## Decisions Made

- Platform smoke uses `macos-stub` and `windows-stub` as explicit capability/command identities; no host OS check skips either command.
- Smoke XML remains case/result-only evidence and does not include fixture bytes, QR/manual/proof material, stream keys, HMAC keys, or private keys.
- Smoke entrypoints throw after writing XML when any case fails, preserving evidence while still failing Gradle.

## Verification

- PASS: RED run failed at `:compileTestKotlin` on unresolved `JunitSmokeXml`, `SmokeCaseResult`, and `BackendSmokeRunner`.
- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test smokeDesktopBackendMacosStub smokeDesktopBackendWindowsStub --rerun-tasks --no-daemon -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process`
- PASS: `test -f desktop-companion/build/test-results/btgun-smoke/macos/TEST-btgun-macos-stub.xml`
- PASS: `test -f desktop-companion/build/test-results/btgun-smoke/windows/TEST-btgun-windows-stub.xml`
- PASS: XML files contain `<testsuite`, `failures="0"`, and platform suite names `btgun-desktop-backend-macos-stub` / `btgun-desktop-backend-windows-stub`.
- PASS: Secret scan returned no matches: `rg -n "qr_secret|manual code|proof|stream key|HMAC key|private key" desktop-companion/build/test-results/btgun-smoke`
- PASS: Platform-stub boundary scan returned no matches: `rg -n "HIDVirtualDevice|HIDDriverKit|VHF|VhfCreate|CoreHID|DriverKit|physical gun motor|ProfileMapper|profile mapping|visualizer" desktop-companion/src/main/kotlin/com/btgun/desktop/smoke desktop-companion/build.gradle.kts`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used escalated Gradle verification**
- **Found during:** Task 1 and final verification
- **Issue:** Sandbox blocked Gradle file-lock socket creation with `FileLockContentionHandler`.
- **Fix:** Ran required Gradle verification with approval, JDK 17, `/private/tmp/bt-gun-gradle`, `--no-daemon`, and in-process Kotlin compiler.
- **Files modified:** None
- **Verification:** RED, GREEN, and final Gradle runs completed.
- **Committed in:** N/A, verification environment only.

**2. [Rule 1 - Bug] Fixed unavailable Kotlin path import**
- **Found during:** Task 1 GREEN
- **Issue:** `kotlin.io.path.parent` was unavailable in the current desktop companion build and broke `:compileKotlin`.
- **Fix:** Removed the extension import and used Java `Path.parent`.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt`
- **Verification:** `gradle test` passed.
- **Committed in:** `479333f`

---

**Total deviations:** 2 auto-fixed (1 Rule 3 verification blocker, 1 Rule 1 compile bug)
**Impact on plan:** No scope expansion. Smoke commands remain platform stubs only and do not add production HID, profile mapping, visualizer, or haptic-output behavior.

## Issues Encountered

- Local sandbox blocked normal Gradle and Git index writes; approved escalated commands were used for required verification and commits.
- No authentication gates occurred.

## Known Stubs

None. The plan intentionally creates platform stub smoke commands, but they are functional smoke harnesses with explicit `macos-stub` and `windows-stub` identities, not unfinished placeholders.

## Threat Flags

None. The plan adds local smoke XML file output already covered by the plan threat model and no new network endpoints, auth paths, schema changes, or production HID APIs.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 05 Plan 05 can layer simulated output-report haptic smoke on top of the existing `BackendSmokeRunner` and platform command surfaces.

## TDD Gate Compliance

- RED gate commit exists: `62e5891`.
- GREEN gate commit exists after RED: `479333f`.
- No refactor commit was needed.

## Self-Check: PASSED

- Found created files: `BackendSmokeRunner.kt`, `JunitSmokeXml.kt`, `MacosBackendSmokeMain.kt`, `WindowsBackendSmokeMain.kt`, `BackendSmokeRunnerTest.kt`.
- Found task commits: `62e5891`, `479333f`, `52eb3bc`.
- Final Gradle verification passed.
- Smoke XML files exist for macOS and Windows and contain zero failures.
- Final secret scan and platform-stub boundary scan returned no matches.

---
*Phase: 05-desktop-backend-contract-and-smoke-harness*
*Completed: 2026-06-09*
