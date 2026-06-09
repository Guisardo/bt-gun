---
phase: 05-desktop-backend-contract-and-smoke-harness
plan: 05
subsystem: desktop-backend
tags: [kotlin, gradle, haptics, smoke-harness, android-trust, windows]
requires:
  - phase: 05-04
    provides: macOS and Windows backend smoke commands with JUnit-style XML
  - phase: 04-input-stream-and-haptic-transport
    provides: authenticated desktop-to-Android phone haptic command path
provides:
  - simulated backend output-report to authenticated phone haptic routing
  - live haptic smoke flag for platform backend commands
  - sanitized macOS, Windows, and human phone-haptic evidence manifest rows
affects: [phase-05, phase-06, phase-07, phase-10, android-trust-store]
tech-stack:
  added: []
  patterns: [authenticated haptic smoke, secret-free QR image artifact, endpoint-scoped desktop trust]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SimulatedOutputReport.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendHapticSmokeSession.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendHapticSmokeTest.kt
    - docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsBackendSmokeMain.kt
    - android-host/app/src/main/java/com/btgun/host/session/TrustedDesktopStore.kt
    - android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt
key-decisions:
  - "Backend simulated output reports create haptic commands but never bypass ControlServer authenticated session gates."
  - "Headless haptic smoke writes a scannable QR image and keeps secrets out of XML and committed manifest rows."
  - "Android desktop trust is endpoint-scoped for unknown fingerprints so macOS and Windows desktops can both be trusted."
patterns-established:
  - "Live haptic platform smoke must fail when Android does not authenticate or no started haptic result arrives."
  - "Multiple desktop hosts may share display copy; fingerprint mismatch blocks only an already-trusted endpoint."
requirements-completed: [DESK-07, DESK-08]
duration: 2h 10m
completed: 2026-06-09
---

# Phase 05 Plan 05: Output-Report Haptic Smoke Summary

**Simulated backend output reports now reach Android phone haptics only through authenticated control, with macOS and Windows live smoke XML evidence.**

## Performance

- **Duration:** 2h 10m
- **Started:** 2026-06-09T19:08:00Z
- **Completed:** 2026-06-09T21:18:43Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added `SimulatedOutputReport` validation and backend mapping to `HapticCommand` for null-pattern reports.
- Added `BackendHapticSmokeSession` and `btgun.smoke.haptic` mode so platform smoke waits for paired Android, sends over `ControlServer.sendHapticCommand`, and fails closed without `started`.
- Added focused tests proving no active session returns `NoActiveSession` and active trusted session emits `reserved_haptic_command`.
- Produced sanitized manifest evidence for macOS haptic smoke, Windows haptic smoke, and human-confirmed phone vibration, all marked `pass`.
- Fixed Android trusted-desktop validation so separate Mac and Windows endpoints can both be trusted without weakening same-endpoint identity mismatch protection.

## Task Commits

1. **Task 1 RED: Haptic routing tests** - `902ceff` (test)
2. **Task 1 GREEN: Output-report haptic routing** - `2668968` (feat)
3. **Task 2: Haptic smoke flag and manifest scaffold** - `cea66e1` (feat)
4. **Task 2 fix: Headless QR image output** - `23e0e9d` (fix)
5. **Task 3 fix: Reuse normal desktop identity for smoke** - `7004f8e` (fix)
6. **Task 3 evidence: macOS haptic pass** - `b9b5202` (docs)
7. **Task 3 evidence: Windows blocker recorded** - `b6e9feb` (docs)
8. **Task 3 fix: Android multi-endpoint trust** - `8720b9d` (fix)
9. **Task 3 evidence: Windows haptic pass** - `d344b5e` (docs)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SimulatedOutputReport.kt` - Semantic output-report shape and Phase 4 haptic bounds.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt` - Adds simulated output-report contract.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt` - Maps simple output reports to haptic commands.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendHapticSmokeSession.kt` - Live authenticated phone-haptic smoke flow and QR PNG artifact.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` - Includes live haptic case when enabled.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt` - Reads haptic smoke property for macOS entrypoint.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsBackendSmokeMain.kt` - Reads haptic smoke property for Windows entrypoint.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendHapticSmokeTest.kt` - Output-report mapping, no-session failure, authenticated envelope routing, and QR image tests.
- `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl` - Sanitized macOS, Windows, and phone-haptic pass evidence.
- `android-host/app/src/main/java/com/btgun/host/session/TrustedDesktopStore.kt` - Endpoint-scoped unknown-fingerprint conflict detection.
- `android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt` - Regression coverage for same display name across different endpoints.

## Decisions Made

- Simulated output reports are semantic test inputs only. Backends return `HapticCommand?`; the smoke harness owns sending through the existing authenticated control server.
- Non-null haptic patterns remain unsupported in Phase 05 and do not imply pattern playback.
- Headless live smoke emits a QR PNG path instead of requiring users to copy raw QR URI material.
- Desktop identity for haptic smoke defaults to the same persistent identity as the normal desktop UI.
- Android trust mismatch remains strict for same host and port, but duplicate display names across different endpoints are allowed.

## Verification

- PASS: `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --rerun-tasks --offline --no-daemon --console=plain --stacktrace -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=1`
- PASS: `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle smokeDesktopBackendMacosStub smokeDesktopBackendWindowsStub --rerun-tasks --offline --no-daemon --console=plain --stacktrace -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=1`
- PASS: macOS live haptic smoke XML: `tests=4`, `failures=0`, includes `live-phone-haptic-macos-stub`.
- PASS: Windows live haptic smoke XML: `tests=4`, `failures=0`, includes `live-phone-haptic-windows-stub`.
- PASS: Human confirmed Android phone physically vibrated during live haptic smoke.
- PASS: `ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk gradle :app:testDebugUnitTest --offline --no-daemon --console=plain --stacktrace -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=1`
- PASS: `ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk gradle :app:assembleDebug --offline --no-daemon --console=plain --stacktrace -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=1`
- PASS: Updated APK installed over USB with `adb install -r android-host/app/build/outputs/apk/debug/app-debug.apk`.
- PASS: Manifest all-pass check: `phase5-macos-stub-xml`, `phase5-windows-stub-xml`, and `phase5-phone-haptic-confirmed` all have `status=pass`.
- PASS: Redaction scan returned no matches: `rg -n "qr_secret|manual code|proof|stream key|HMAC key|private key|Bluetooth address|device id|screenshot" docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl desktop-companion/build/test-results/btgun-smoke`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Wrote QR image for headless smoke**
- **Found during:** Task 3 macOS live haptic run
- **Issue:** Headless Gradle smoke did not open a UI, so raw console QR material was not practically scannable.
- **Fix:** `BackendHapticSmokeSession` writes `haptic-pairing-qr.png` under smoke test results and prints the image path.
- **Files modified:** `BackendHapticSmokeSession.kt`, `BackendHapticSmokeTest.kt`
- **Verification:** `gradle test` passed and QR PNG size assertion passed.
- **Committed in:** `23e0e9d`

**2. [Rule 1 - Bug] Reused normal desktop identity for haptic smoke**
- **Found during:** Task 3 macOS live haptic run
- **Issue:** The haptic smoke harness used a separate certificate from the normal desktop UI, causing Android to report desktop identity mismatch.
- **Fix:** Defaulted haptic smoke to `DesktopIdentityStore.default()` and injected a temp file-backed identity in tests.
- **Files modified:** `BackendHapticSmokeSession.kt`, `BackendHapticSmokeTest.kt`
- **Verification:** `gradle test` passed; macOS haptic smoke passed after pairing.
- **Committed in:** `7004f8e`

**3. [Rule 1 - Bug] Allowed multiple desktop endpoints with the same display name**
- **Found during:** Task 3 Windows live haptic run
- **Issue:** Android treated Windows as an identity change because Mac and Windows both present the `BT Gun Desktop` display name.
- **Fix:** Trust conflict detection now keys unknown-fingerprint mismatch by same host and port, not display name alone.
- **Files modified:** `TrustedDesktopStore.kt`, `TrustedDesktopStoreTest.kt`
- **Verification:** `:app:testDebugUnitTest` passed, updated APK installed, Windows haptic smoke passed.
- **Committed in:** `8720b9d`

**4. [Rule 3 - Blocking] Used copied smoke runtime on remote Windows host**
- **Found during:** Task 3 Windows evidence
- **Issue:** Remote Windows host had Java 17 but no visible `gradle` command or checked-out `bt-gun` repo.
- **Fix:** Copied the compiled smoke runtime and dependency jars, ran `WindowsBackendSmokeMainKt` with Java 17 on Windows, then fetched the produced XML.
- **Files modified:** None
- **Verification:** Windows XML contained `tests=4`, `failures=0`.
- **Committed in:** N/A, verification environment only.

---

**Total deviations:** 4 auto-fixed (2 Rule 1 bugs, 2 Rule 3 blockers)
**Impact on plan:** No auth or transport bypass was introduced. The Android trust fix was required for the project constraint that macOS and Windows are both v1 desktop targets.

## Issues Encountered

- Sandbox blocked Gradle file-lock socket creation; approved escalated Gradle commands were used.
- `/private/tmp` Gradle cache lacked Android Gradle Plugin for Android host tests; default Gradle cache plus `ANDROID_HOME` was used for the Android verification.
- Windows first haptic attempts timed out before authentication until the Android trusted-desktop rule was fixed and the updated APK was installed.

## Known Stubs

- Platform backends are still `macos-stub` and `windows-stub`; this plan validates backend contract and haptic routing, not production virtual HID output report APIs.

## Threat Flags

None. Haptic output reports still route through authenticated control only, and committed evidence excludes raw QR/manual/proof/session material, HMAC keys, private keys, device ids, and screenshots.

## User Setup Required

None for the completed evidence gate. The updated debug APK was installed on the connected Android device during verification.

## Next Phase Readiness

Phase 05 is ready to close. Phase 06 can consume the backend contract, platform smoke XML conventions, authenticated haptic routing, and endpoint-scoped Android trust behavior before production virtual-controller backend work.

## TDD Gate Compliance

- RED gate commit exists: `902ceff`.
- GREEN gate commit exists after RED: `2668968`.
- Follow-up fixes have focused regression tests or live smoke evidence.

## Self-Check: PASSED

- Found created haptic smoke files and manifest.
- Found all task/evidence/fix commits.
- macOS and Windows haptic smoke XML files contain zero failures.
- Manifest has all required capture ids and all statuses are `pass`.
- Final redaction scan returned no matches.

---
*Phase: 05-desktop-backend-contract-and-smoke-harness*
*Completed: 2026-06-09*
