---
phase: 02-android-host-live-input
plan: 02
subsystem: android-host
tags: [android, kotlin, ble, parser, fixtures, tdd, provenance]
requires:
  - phase: 02-01
    provides: Android host scaffold, LiveEnvelope, StreamSequencer, GunEvent, StatusEvent, and Provenance contracts.
  - phase: 01-hardware-and-protocol-discovery
    provides: BLE fff3 control fixtures and evidence/capture ids.
provides:
  - Fixture-backed BLE fff3 payload parser for trigger, reload, stick directions, and X/Y/A/B.
  - Unknown BLE payload rejection as status/debug output only.
  - Parser and envelope tests covering per-stream sequence counters and elapsed-nanos timestamps.
  - Provenance preservation for raw ASCII, raw hex, characteristic UUID, clue id, capture id, and confidence.
affects: [02-android-host-live-input, android-host, ble-parser, normalized-events]
tech-stack:
  added: []
  patterns:
    - No new Gradle dependencies; Kotlin main-based JVM tests run through testDebugUnitTest.
    - Strict parser whitelist: only known fff3 fixture payloads can produce GunEvent.
key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/ble/IpegaPacketParser.kt
    - android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt
    - android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt
  modified:
    - android-host/app/build.gradle.kts
key-decisions:
  - "Use a strict fff3 fixture whitelist; unknown bytes become UnknownBlePayload with status/debug envelope only."
  - "Represent trigger and X/Y/A/B noisy/candidate evidence as SemanticConfidence.CANDIDATE in provenance."
  - "Keep parser free of Android UI imports, BLE callback ownership, LAN transport, desktop, and haptic command scope."
patterns-established:
  - "IpegaPacketParser.parseFff3(...) returns ParsedGunPacket.Event only for known Phase 1 payloads."
  - "UnknownBlePayload carries raw evidence plus a status envelope without exposing a product GunEvent."
requirements-completed: [ANDR-03, ANDR-05]
duration: 9min
completed: 2026-06-07
---

# Phase 02 Plan 02: Fixture-Backed Packet Parser Summary

**BLE fff3 fixture parser maps known iPega controls to elapsed-nanos gun envelopes while preserving evidence provenance and rejecting unknown bytes as debug/status only.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-06-07T00:14:29Z
- **Completed:** 2026-06-07T00:23:35Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- Added TDD RED tests for every Phase 1 `fff3` control payload: trigger, reload, stick left/right/up/down, and X/Y/A/B down/up.
- Implemented `IpegaPacketParser.parseFff3(...)` with a strict whitelist table, parser-owned stream sequencing, elapsed-nanos envelopes, and optional provenance.
- Unknown BLE payloads now return `UnknownBlePayload` with a `StatusEvent` envelope and never become product `GunEvent` controls.
- Added focused envelope coverage for independent gun/motion/status sequence counters and rejection of impossible emitted-before-capture timing.

## Task Commits

1. **Task 1: RED parser and envelope tests from fixtures** - `15e02ef` (test)
2. **Task 2: GREEN parser whitelist and provenance model use** - `65e3cc5` (feat)
3. **Task 3: REFACTOR parser boundary and full quick gate** - `2e5e21f` (refactor)

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/ble/IpegaPacketParser.kt` - Whitelist-backed parser, parsed event model, unknown payload model, and UUID constants.
- `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt` - Fixture mapping regression tests and unknown-payload rejection test.
- `android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt` - Independent stream sequencer and elapsed-nanos envelope tests.
- `android-host/app/build.gradle.kts` - Runs parser and envelope test mains in the existing no-dependency test harness.

## Decisions Made

- Used raw hex as the whitelist key so zero frames and printable ASCII payloads are matched deterministically.
- Kept reload and stick events confirmed in parser provenance because normalized fixture rows do not carry candidate semantics; trigger and X/Y/A/B keep candidate confidence.
- Routed unknown bytes into a status stream envelope instead of product controls, matching the BLE trust-boundary threat model.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Extended no-dependency Gradle harness for new test mains**
- **Found during:** Task 1
- **Issue:** Existing `testDebugUnitTest` harness only ran `PermissionGateTest`, so new parser/envelope tests would compile but not execute.
- **Fix:** Added parser and envelope Kotlin main classes to the Gradle test task's explicit harness list.
- **Files modified:** `android-host/app/build.gradle.kts`
- **Verification:** RED failed on missing parser symbols; GREEN and full quick gates executed the new tests.
- **Committed in:** `15e02ef`

**2. [Rule 1 - Bug] Fixed RED test helper and parser-instance setup**
- **Found during:** Task 2
- **Issue:** The RED test helper treated ASCII payloads as hex input, and then recreated the parser for every case, hiding parser-owned gun sequence increments.
- **Fix:** Split the zero-frame helper into `hexCase(...)` and reused one parser instance across known payload cases.
- **Files modified:** `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt`
- **Verification:** Focused parser/envelope tests passed after the parser implementation.
- **Committed in:** `65e3cc5`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes were required for the TDD gate to test the intended behavior. No scope creep into BLE adapter, LAN transport, desktop, or haptic commands.

## Issues Encountered

- Sandbox-blocked Gradle file-lock sockets produced `java.net.SocketException: Operation not permitted`; Gradle verification was rerun with approved escalation.
- Android Gradle verification used JDK 17 via `JAVA_HOME`, matching Plan 02-01's known local requirement.

## Known Stubs

None. Nullable model fields and `mapping == null` unknown-payload handling are intentional contracts, not placeholders or UI stubs.

## Threat Flags

None. The new BLE trust-boundary surface is exactly the planned parser whitelist; no network endpoint, auth path, file access path, schema change, LAN transport, desktop, or haptic command path was introduced.

## User Setup Required

None - no external service configuration required. Android Gradle verification should use JDK 17.

## Verification

- `node tools/phase1/validate-fixtures.mjs --full` - PASS
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*IpegaPacketParser*' --tests '*NormalizedEventEnvelope*'` - PASS
- `node tools/phase1/validate-fixtures.mjs --full && JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` - PASS
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host lintDebug` - PASS

## TDD Gate Compliance

- RED commit exists before GREEN: `15e02ef`
- GREEN implementation commit exists after RED: `65e3cc5`
- REFACTOR commit exists after GREEN: `2e5e21f`

## Next Phase Readiness

Plan 02-03 can call `IpegaPacketParser.parseFff3(...)` from the BLE adapter once `fff3` notifications arrive. Parser outputs are already split into product gun envelopes and debug/status unknown payloads, with Phase 1 provenance preserved for future debug panels.

## Self-Check: PASSED

- Found `.planning/phases/02-android-host-live-input/02-02-SUMMARY.md`
- Found `android-host/app/src/main/java/com/btgun/host/ble/IpegaPacketParser.kt`
- Found `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt`
- Found `android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt`
- Found task commits `15e02ef`, `65e3cc5`, and `2e5e21f`

---
*Phase: 02-android-host-live-input*
*Completed: 2026-06-07*
