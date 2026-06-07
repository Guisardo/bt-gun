---
phase: 02-android-host-live-input
plan: 01
subsystem: android-host
tags: [android, kotlin, gradle, permissions, live-envelope, tdd]
requires:
  - phase: 01-hardware-and-protocol-discovery
    provides: BLE fff0/fff3 evidence, normalized control fixtures, and phone haptic decision
provides:
  - Production android-host Gradle scaffold using approved AGP/Kotlin versions
  - Pure permission and capability gate for Android 12+ and legacy scan behavior
  - Common gun, motion, and status live envelope contracts with elapsed-nanos timestamps
  - Optional debug provenance model carrying Phase 1 raw/capture metadata
affects: [02-android-host-live-input, android-host, permission-gate, normalized-events]
tech-stack:
  added: []
  patterns:
    - No new Gradle dependencies; use pure JVM harness under testDebugUnitTest
    - Pure model and permission contracts with no Activity, View, BLE, SensorManager, or dashboard imports
key-files:
  created:
    - android-host/.gitignore
    - android-host/settings.gradle.kts
    - android-host/build.gradle.kts
    - android-host/app/build.gradle.kts
    - android-host/app/src/main/AndroidManifest.xml
    - android-host/app/src/main/res/values/styles.xml
    - android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt
    - android-host/app/src/main/java/com/btgun/host/model/Provenance.kt
    - android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt
    - android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java
  modified: []
key-decisions:
  - "Use a no-dependency Java main harness under Gradle testDebugUnitTest so Phase 2 keeps the no-new-dependencies constraint."
  - "Keep PermissionGate pure Kotlin and pass Android runtime facts as values rather than importing Activity/View APIs."
  - "Keep LiveEnvelope elapsed-nanos only; no wall-clock timestamp fields were added."
patterns-established:
  - "PermissionGate.evaluate(input) returns checkable CapabilityStatus values for Bluetooth, legacy location scan compatibility, motion sensors, vibration, and LAN/network capability."
  - "StreamSequencer keeps independent sequence counters for gun, motion, and status streams."
requirements-completed: [ANDR-01, ANDR-05]
duration: 10min
completed: 2026-06-07
---

# Phase 02 Plan 01: Android Host Scaffold and Contracts Summary

**Production Android host scaffold with TDD-covered permission/capability gate and elapsed-nanos live envelope contracts.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-06T23:57:42Z
- **Completed:** 2026-06-07T00:07:06Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Created `android-host/` as a production sibling Android app with package `com.btgun.host`, compile/target SDK 35, min SDK 23, JVM 17, and approved diagnostic Gradle plugin versions only.
- Added `PermissionGate` with Android 12+ scan/connect permission branching, legacy fine/coarse location scan compatibility, motion sensor capability state, vibration capability, and LAN/network capability state.
- Added `LiveEnvelope`, `StreamKind`, `StreamSequencer`, `ElapsedNanosClock`, gun/motion/status payload types, and optional debug `Provenance`.
- Kept Phase 3/4 LAN and desktop behavior deferred; manifest declares capability permissions but no transport or desktop link behavior exists.

## Task Commits

1. **Task 1: RED gate for permission and host scaffold** - `3484777` (test)
2. **Task 2: GREEN permission gate and shared live envelope contracts** - `f2c78bb` (feat)
3. **Task 3: REFACTOR permission and envelope boundaries** - `d5b44de` (refactor)
4. **Post-wave lint fix** - `f2d0680` (fix)

## Files Created/Modified

- `android-host/.gitignore` - Ignores Gradle caches and build outputs inside the host module.
- `android-host/settings.gradle.kts` - Sibling Gradle project setup.
- `android-host/build.gradle.kts` - Approved AGP 8.7.3 and Kotlin Android 2.0.21 plugin versions.
- `android-host/app/build.gradle.kts` - Android app module config plus no-dependency test harness wiring.
- `android-host/app/src/main/AndroidManifest.xml` - BLE, legacy Bluetooth, Android 12+ Bluetooth, location, network, vibration, and foreground-service permissions.
- `android-host/app/src/main/res/values/styles.xml` - Minimal native Android app theme.
- `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt` - Pure permission/capability evaluation contract.
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - Stream envelope, sequencer, clock, and payload contracts.
- `android-host/app/src/main/java/com/btgun/host/model/Provenance.kt` - Optional debug provenance and semantic confidence model.
- `android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java` - RED/GREEN permission tests plus envelope/provenance boundary assertions.

## Decisions Made

- Used a pure Java main-based test harness instead of adding JUnit or Kotlin test dependencies, preserving the plan's no-new-dependencies rule.
- Used value-based permission inputs instead of Android framework imports so permission/capability behavior remains unit-testable.
- Stored provenance as optional model metadata so product streams can ignore raw BLE details unless later debug UI enables them.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added no-discovered-tests handling for custom harness**
- **Found during:** Task 3
- **Issue:** Full `testDebugUnitTest` failed because Gradle saw test sources but no JUnit-discovered tests.
- **Fix:** Set `failOnNoDiscoveredTests = false` and kept the harness execution in the Gradle `Test` task.
- **Files modified:** `android-host/app/build.gradle.kts`
- **Verification:** `gradle -p android-host testDebugUnitTest` passed.
- **Committed in:** `d5b44de`

**2. [Rule 3 - Blocking] Used JDK 17 for Android Gradle verification**
- **Found during:** Task 1 verification
- **Issue:** Default JDK 26 failed Android `jlink` transform for SDK 35 unit-test compilation.
- **Fix:** Ran Android Gradle verification with local OpenJDK 17 via `JAVA_HOME`.
- **Files modified:** none
- **Verification:** Focused and full unit-test commands passed under JDK 17.
- **Committed in:** none - environment-only verification fix

**3. [Rule 3 - Blocking] Removed API 24 `Map.getOrDefault` call for minSdk 23**
- **Found during:** Post-wave `lintDebug`
- **Issue:** Android lint flagged `java.util.Map#getOrDefault` in `StreamSequencer` because the host app supports minSdk 23.
- **Fix:** Replaced it with Kotlin map access plus Elvis default.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt`
- **Verification:** `gradle -p android-host testDebugUnitTest` and `gradle -p android-host lintDebug` passed under JDK 17.
- **Committed in:** `f2d0680`

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** Fixes preserved the no-new-dependencies, Android/JVM 17, and minSdk 23 constraints. No UI, BLE adapter, SensorManager, LAN transport, dashboard, or haptic command behavior was added.

## Issues Encountered

- `lintDebug` initially needed uncached Android lint artifacts and then found the minSdk 23 issue above. After sandbox-approved Gradle execution and `f2d0680`, lint passes.

## Known Stubs

None. Nullable defaults in `LiveEnvelope`, `GunEvent`, `StatusEvent`, and `Provenance` are optional contract fields, not UI or mock-data stubs.

## Threat Flags

None. Manifest permissions and debug provenance are covered by the plan threat model; no network endpoint, desktop control channel, file access path, or runtime LAN behavior was introduced.

## User Setup Required

None - no external service configuration required. Android Gradle verification should use JDK 17.

## Verification

- `node tools/phase1/validate-fixtures.mjs --full` - PASS
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*PermissionGate*'` - PASS
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` - PASS
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host lintDebug` - PASS

## TDD Gate Compliance

- RED commit exists before GREEN: `3484777`
- GREEN implementation commit exists after RED: `f2c78bb`
- REFACTOR commit exists after GREEN: `d5b44de`
- Post-wave lint fix commit exists after REFACTOR: `f2d0680`

## Next Phase Readiness

Plan 02-02 can add fixture-backed packet parser tests and code against `LiveEnvelope`, `StreamKind`, `StreamSequencer`, `GunEvent`, and `Provenance`. Later UI/service plans can call `PermissionGate.evaluate(...)` without pulling Android UI or BLE code into the pure gate.

## Self-Check: PASSED

- Found `.planning/phases/02-android-host-live-input/02-01-SUMMARY.md`
- Found `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt`
- Found `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt`
- Found task commits `3484777`, `f2c78bb`, `d5b44de`, and post-wave fix `f2d0680`

---
*Phase: 02-android-host-live-input*
*Completed: 2026-06-07*
