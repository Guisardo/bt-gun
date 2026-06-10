---
phase: 07-macos-virtual-joystick-path
plan: 01
subsystem: android
tags: [android, bluetooth-hid, permissions, evidence, tdd]

requires:
  - phase: 02-android-host-live-input
    provides: Android host permission/capability gate and live BLE session status patterns
  - phase: 05-desktop-backend-contract-and-smoke-harness
    provides: Locked gamepad-like joystick shape for later HID reports
  - phase: 06-windows-virtual-joystick-path
    provides: Completed Windows VHF fallback path
provides:
  - Android Bluetooth HID role capability/status model
  - HID blocked-state tests for proxy, registration, host connection, Bluetooth, and permission failures
  - Schema-only sanitized Phase 7 Android Bluetooth HID evidence manifest
affects: [android-host, phase-07, bluetooth-hid, evidence]

tech-stack:
  added: []
  patterns:
    - Pure Android capability model feeds existing PermissionGate without changing BLE session start behavior
    - Schema-only evidence manifest records future proof row names before live hardware claims

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt
    - android-host/app/src/test/java/com/btgun/host/permissions/AndroidHidCapabilityTest.kt
    - docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl
  modified:
    - android-host/app/build.gradle.kts
    - android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt
    - android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java

key-decisions:
  - "Android Bluetooth HID readiness is modeled separately from the existing BLE gun session gate."
  - "HostCapabilityProbe remains synchronous; async HID_DEVICE proxy, registration, and host connection results are accepted through explicit status inputs."
  - "Phase 7 Android HID evidence starts as schema/policy only and makes no current-phone, macOS, output-report, unsupported-output, or fallback success claim."

patterns-established:
  - "AndroidHidCapability: pure input-to-status model for phone/OEM HID peripheral readiness."
  - "PermissionGate HID extension: adds bluetoothHidRole while preserving canStartSession semantics."
  - "Evidence manifest schema: list accepted future record_type values plus redaction policy before live rows."

requirements-completed: [ANDR-09, PACK-06]

duration: 8min
completed: 2026-06-10
---

# Phase 07 Plan 01: Android Bluetooth HID Foundation Summary

**Android Bluetooth HID role readiness model with blocked-state tests and schema-only sanitized evidence manifest**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-10T22:21:06Z
- **Completed:** 2026-06-10T22:29:19Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Proved the local Android Gradle startup gate with JDK 17 and the `/private/tmp/bt-gun-gradle-home` Gradle home.
- Added `AndroidHidCapability` with explicit states for Bluetooth off, missing `BLUETOOTH_CONNECT`, not-yet-probed, `HID_DEVICE` proxy unavailable, registration failed, no host connected, host disconnected, and connected.
- Extended `PermissionGateState` with `bluetoothHidRole` while preserving existing BLE `canStartSession` behavior.
- Added `AndroidHidCapabilityTestKt` to the Gradle manual test registry and extended `PermissionGateTest`.
- Created a one-row schema/policy manifest for later Phase 7 Android Bluetooth HID proof rows without claiming live success.

## Task Commits

Each task was committed atomically:

1. **Task 1: Prove Android Gradle startup before relying on local tests** - `4ed25ae` (chore)
2. **Task 2 RED: Add failing Android HID capability tests** - `76d05bb` (test)
3. **Task 2 GREEN: Implement Android HID capability matrix** - `a0a4670` (feat)
4. **Task 3: Create sanitized Phase 7 Android HID evidence manifest target** - `5d5f8aa` (docs)

_Note: Task 2 followed TDD with separate RED and GREEN commits. No refactor commit was needed._

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt` - Pure HID role capability/status model.
- `android-host/app/src/test/java/com/btgun/host/permissions/AndroidHidCapabilityTest.kt` - Blocked-state and connected-state matrix tests.
- `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt` - Adds HID status inputs and `bluetoothHidRole`.
- `android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java` - Proves HID readiness does not change BLE live-session gate semantics.
- `android-host/app/build.gradle.kts` - Registers `AndroidHidCapabilityTestKt` in the existing manual unit-test class list.
- `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` - Schema-only accepted row types and redaction policy.
- `.planning/phases/07-macos-virtual-joystick-path/07-01-SUMMARY.md` - Plan closeout.

## Decisions Made

- Android HID role readiness remains separate from BLE gun/LAN session readiness.
- Synchronous startup probing only infers Bluetooth and `BLUETOOTH_CONNECT`; async HID proxy, app registration, and host connection results are external status inputs.
- Evidence starts as a schema-only manifest row. Later plans must add live proof rows after real phone/macOS checks.

## TDD Gate Compliance

- **RED:** `76d05bb` added `AndroidHidCapabilityTestKt` and `PermissionGateTest` expectations. The first focused Gradle run failed on unresolved `AndroidHidCapability` symbols as expected.
- **GREEN:** `a0a4670` added the status model and permission-gate wiring. Focused Gradle tests then passed.
- **REFACTOR:** Not needed.

## Verification

- **PASS:** `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --no-daemon --console=plain -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process`
- **PASS:** `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*AndroidHidCapability*' --tests '*PermissionGate*' --no-daemon --console=plain`
- **PASS:** `rg -n "phase7-android-hid-proxy|phase7-gamecontroller-input|phase7-hid-output-callback|phase7-alternate-phone-tested|phase7-windows-vhf-fallback-selected" docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`
- **PASS:** Redaction scan found no Bluetooth address, secret, key, device-id, serial, or screenshot-path pattern in the new manifest.
- **PASS:** Stub scan found no TODO/FIXME/placeholder/empty UI stub patterns in files created or modified by this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Avoided redaction-scan false positive in manifest policy wording**
- **Found during:** Task 3
- **Issue:** The initial manifest policy used a forbidden literal that would trip the planned evidence redaction scanner.
- **Fix:** Reworded that policy item to "phone hardware sequence numbers" while preserving the intended ban on phone serial-like identifiers.
- **Files modified:** `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`
- **Verification:** Redaction scan returned no matches.
- **Committed in:** `5d5f8aa`

---

**Total deviations:** 1 auto-fixed (Rule 1).
**Impact on plan:** No scope expansion. The fix keeps the manifest compatible with later automated redaction gates.

## Issues Encountered

- Sandbox-blocked Gradle startup reported `java.net.SocketException: Operation not permitted`; reran the required Gradle gates outside the sandbox with approval.
- Task 1 had no file edits. An empty `chore(07-01)` commit records the required startup gate as its atomic task commit.

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 07-02. The permission/capability model can now feed the Android Bluetooth HID descriptor/report work without claiming live phone or macOS proof yet.

## Self-Check: PASSED

- Created files exist: `AndroidHidCapability.kt`, `AndroidHidCapabilityTest.kt`, `phase7-android-bluetooth-hid.jsonl`, and this summary.
- Task commits exist: `4ed25ae`, `76d05bb`, `a0a4670`, `5d5f8aa`.
- Plan-level Gradle and manifest checks passed after final code/docs changes.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
