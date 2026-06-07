---
phase: 02-android-host-live-input
plan: 03
subsystem: android-host-ble-session
tags: [android, kotlin, ble, gatt, foreground-service, tdd, reconnect]
requires:
  - phase: 02-01
    provides: Android host scaffold, PermissionGate, LiveEnvelope, StatusEvent, and foreground-service permissions.
  - phase: 02-02
    provides: Fixture-backed IpegaPacketParser for fff3 gun/status events.
  - phase: 01-hardware-and-protocol-discovery
    provides: Physical ARGunGame fff0/fff3 GATT evidence and normalized handshake/control fixtures.
provides:
  - Foreground HostSessionService with active BLE session notification.
  - IpegaBleGunAdapter scanning ARGunGame/fff0, connecting over LE GATT, discovering fff0, and subscribing to fff3 via CCCD.
  - Bounded active-only reconnect policy with visible state and last error.
  - Serialized GATT operation queue with descriptor/characteristic completion callbacks.
affects: [02-android-host-live-input, android-host, ble-adapter, foreground-service, phase3-lan-boundary]
tech-stack:
  added: []
  patterns:
    - Platform Android BLE APIs only; no BLE helper or mocking dependencies.
    - Foreground service owns the active connection before long-running BLE work starts.
    - GATT writes are serialized through GattOperationQueue.
key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
    - android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt
    - android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt
    - android-host/app/src/test/java/com/btgun/host/ble/IpegaBleGunAdapterTest.kt
  modified:
    - android-host/app/src/main/AndroidManifest.xml
    - android-host/app/build.gradle.kts
key-decisions:
  - "HostSessionService starts the exact foreground notification before creating the BLE adapter session."
  - "IpegaBleGunAdapter accepts only ARGunGame advertising fff0 and routes only fff3 notification bytes into IpegaPacketParser."
  - "Reconnect is bounded by ReconnectPolicy, active-session only, and keeps the last GATT/scan error visible."
patterns-established:
  - "Pure adapter/service helper tests cover constants, reconnect policy, and GATT queue ordering without mock packages."
  - "BLE adapter catches SecurityException and IllegalStateException into status/lastError instead of crashing active sessions."
requirements-completed: [ANDR-02, ANDR-03, ANDR-08]
duration: 7min
completed: 2026-06-07
---

# Phase 02 Plan 03: BLE Adapter and Foreground Session Service Summary

**Foreground Android host session now scans ARGunGame/fff0, connects by LE GATT, subscribes fff3 notifications, parses live gun bytes, and reconnects only while active.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-07T00:35:06Z
- **Completed:** 2026-06-07T00:42:28Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added RED adapter/service tests before implementation for exact scan constants, notification copy, CCCD writes, reconnect decisions, stopped-session behavior, and GATT queue ordering.
- Added `HostSessionService` with `ACTION_START_SESSION` / `ACTION_STOP_SESSION`, PermissionGate start blocking, connected-device foreground-service manifest registration, and notification text `BT Gun Host running - live input active`.
- Implemented `IpegaBleGunAdapter` with low-latency `fff0` scan filter, `ARGunGame` match, `connectGatt(..., TRANSPORT_LE)`, service discovery, `fff3` notification enable, CCCD write, parser routing, status events, and bounded active-only reconnect.
- Added `GattOperationQueue` so descriptor and characteristic operations remain one-in-flight until completion callbacks drain the queue.

## Task Commits

1. **Task 1: Add adapter/service state tests** - `47dd22d` (test)
2. **Task 2: Implement HostSessionService foreground lifecycle** - `17d587d` (feat)
3. **Task 3: Implement BLE adapter and serialized GATT queue** - `2e7756d` (feat)

## Files Created/Modified

- `android-host/app/src/test/java/com/btgun/host/ble/IpegaBleGunAdapterTest.kt` - Pure JVM tests for scan filter, notification subscription, reconnect policy, stopped behavior, and GATT queue ordering.
- `android-host/app/build.gradle.kts` - Runs the new adapter/service test main in the no-dependency unit-test harness.
- `android-host/app/src/main/AndroidManifest.xml` - Registers `HostSessionService` as a non-exported connected-device foreground service.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - Foreground active-session lifecycle, permission gate, notification, session state, and reconnect policy.
- `android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt` - Serialized GATT operation queue and completion event model.
- `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt` - BLE scan/connect/discover/notify adapter and fff3 parser routing.

## Decisions Made

- Kept tests framework-free by exercising pure state/config helpers instead of mocking Android BLE framework objects.
- Required both `ARGunGame` and advertised `fff0` for product adapter acceptance, matching the Phase 1 proven target and rejecting unrelated BLE devices.
- Kept Phase 3/4 LAN/control/haptic transport out of the adapter; there are no `fff5` writes or desktop-origin haptic paths in this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added adapter and GATT queue contracts during service task**
- **Found during:** Task 2
- **Issue:** `HostSessionService` had to call an adapter boundary and the RED tests had to compile through queue/reconnect contracts before the full BLE adapter implementation.
- **Fix:** Added minimal `IpegaBleGunAdapter` and `GattOperationQueue` contracts with the service commit, then expanded the adapter to the full scan/connect/notify implementation in Task 3.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt`, `android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt`
- **Verification:** Focused adapter/service tests passed after Task 2; full adapter/parser tests passed after Task 3.
- **Committed in:** `17d587d`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Implementation order shifted slightly so the service could compile and test against stable contracts. Scope stayed inside BLE foreground-session work; no LAN transport, desktop control channel, haptic command path, or `fff5` write path was added.

## Issues Encountered

- Sandbox-blocked Gradle file-lock sockets produced `java.net.SocketException: Operation not permitted`; Gradle verification was rerun with approved escalation.
- Android Gradle verification used JDK 17 via `JAVA_HOME`, matching earlier Phase 2 local build requirements.

## Known Stubs

None. Nullable adapter/service fields are lifecycle state, not placeholder UI data.

## Threat Flags

None. The new Android runtime -> BLE API, physical BLE -> parser, and foreground service surfaces match the plan threat model. No new network endpoint, auth path, schema boundary, LAN transport, desktop control channel, or haptic command path was introduced.

## User Setup Required

None - no external service configuration required. Physical-device validation remains for the later Phase 2 manual validation plan.

## Verification

- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*IpegaBleGunAdapter*'` - RED failed before implementation on missing planned symbols, then PASS after Task 2.
- `node tools/phase1/validate-fixtures.mjs --full && JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*IpegaBleGunAdapter*' --tests '*IpegaPacketParser*'` - PASS.
- `node tools/phase1/validate-fixtures.mjs --full && JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug` - PASS.
- `rg -n "fff5|UDP|WebSocket|TCP|haptic|writeCharacteristic" android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - PASS, no matches.

## TDD Gate Compliance

- RED commit exists before implementation: `47dd22d`
- GREEN service commit exists after RED: `17d587d`
- GREEN adapter commit exists after service commit: `2e7756d`

## Next Phase Readiness

Plan 02-05 can consume live reload gun events from the parser/adapter path for the reload-hold recenter state machine. Plan 02-06 can bind dashboard state to the foreground session, adapter connection state, status stream, and visible `lastError` values.

## Self-Check: PASSED

- Found `.planning/phases/02-android-host-live-input/02-03-SUMMARY.md`
- Found `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`
- Found `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt`
- Found `android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt`
- Found `android-host/app/src/test/java/com/btgun/host/ble/IpegaBleGunAdapterTest.kt`
- Found task commits `47dd22d`, `17d587d`, and `2e7756d`

---
*Phase: 02-android-host-live-input*
*Completed: 2026-06-07*
