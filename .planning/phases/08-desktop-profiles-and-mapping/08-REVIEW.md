---
phase: 08-desktop-profiles-and-mapping
reviewed: 2026-06-12T20:26:24Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - android-host/app/build.gradle.kts
  - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
  - android-host/app/src/main/java/com/btgun/host/MainActivity.kt
  - android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt
  - android-host/app/src/main/java/com/btgun/host/profile/AdaptiveAimSmoother.kt
  - android-host/app/src/main/java/com/btgun/host/profile/ProfileMapper.kt
  - android-host/app/src/main/java/com/btgun/host/profile/ProfileModels.kt
  - android-host/app/src/main/java/com/btgun/host/profile/ProfileStore.kt
  - android-host/app/src/main/java/com/btgun/host/profile/ProfileValidation.kt
  - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
  - android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt
  - android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt
  - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
  - android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt
  - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt
  - android-host/app/src/test/java/com/btgun/host/profile/AdaptiveAimSmootherTest.kt
  - android-host/app/src/test/java/com/btgun/host/profile/ProfileMapperTest.kt
  - android-host/app/src/test/java/com/btgun/host/profile/ProfileStoreTest.kt
  - android-host/app/src/test/java/com/btgun/host/profile/ProfileValidationTest.kt
  - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt
  - android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt
  - android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt
  - android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt
  - docs/protocol/lan-pairing-v1.md
findings:
  critical: 2
  warning: 2
  info: 0
  total: 4
status: issues_found
---

# Phase 08: Code Review Report

**Reviewed:** 2026-06-12T20:26:24Z
**Depth:** standard
**Files Reviewed:** 36
**Status:** issues_found

## Summary

Reviewed Android-owned profile mapping, mapped UDP transport, HID packing, desktop read-only profile diagnostics, and the related manual test harnesses. Two blockers need fixing before ship: raw debug is persisted despite the session-only contract, and profile edits can start a foreground service path that never calls `startForeground()`.

## Narrative Findings (AI reviewer)

### Critical Issues

#### CR-01: Raw Debug Toggle Persists Across Sessions

**Classification:** BLOCKER
**File:** `android-host/app/src/main/java/com/btgun/host/profile/ProfileStore.kt:242`
**Issue:** `rawDebugEnabled` is serialized and restored with profiles, while Phase 08 docs/protocol define raw debug as an Android-session-only toggle. The profile field is encoded at `ProfileStore.kt:242-249`, decoded at `ProfileStore.kt:271-293`, edited and saved from `MainActivity.kt:553-686`, then used by `HostSessionService.kt:982-999` and `AndroidUdpInputSender.kt:259-280` to include raw debug extras. After one enable, future sessions can leak raw diagnostic data without a fresh per-session opt-in.
**Fix:** Remove `rawDebugEnabled` from persisted `BtGunProfile` and `ProfileStore`; keep it only in volatile session state that defaults to false for each new host session.

```kotlin
data class HostSessionState(
    // existing fields...
    val rawDebugEnabled: Boolean = false,
)
```

Add a regression test that stores a profile after raw debug was enabled and verifies a fresh service/profile load reports and sends raw debug as disabled.

#### CR-02: Profile Save Can Start a Foreground Service Without Foreground Promotion

**Classification:** BLOCKER
**File:** `android-host/app/src/main/java/com/btgun/host/MainActivity.kt:497`
**Issue:** Saving/selecting/deleting a profile calls `startServiceAction(Intent(... ACTION_RELOAD_ACTIVE_PROFILE))` from `MainActivity.kt:491-501`; `startServiceAction()` uses `startForegroundService()` on API 26+ at `MainActivity.kt:906-910`. `HostSessionService.onStartCommand()` handles `ACTION_RELOAD_ACTIVE_PROFILE` at `HostSessionService.kt:406-418`, but `reloadActiveProfile()` at `HostSessionService.kt:541-546` does not call `startForeground()`. If the service is not already foreground, Android can throw/kill the app for failing the foreground-service contract.
**Fix:** Do not start the service for profile reload unless it is already active/foreground, or promote it to foreground before returning from this action.

```kotlin
val state = HostSessionService.latestState
if (state.foregroundActive || state.isActive) {
    startServiceAction(Intent(this, HostSessionService::class.java).apply {
        action = HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE
    })
}
```

Add a test for saving a profile while inactive that proves no foreground service start is issued.

### Warnings

#### WR-01: Profile Store and Validation Tests Are Not Registered in the Android Harness

**Classification:** WARNING
**File:** `android-host/app/build.gradle.kts:47`
**Issue:** The manual `testDebugUnitTest` harness list omits `ProfileStoreTestKt` and `ProfileValidationTestKt`, even though both files define top-level `main()` tests (`ProfileStoreTest.kt:3-9`, `ProfileValidationTest.kt:3-8`). These tests will not run in the normal Android test command, so profile persistence/validation regressions can pass CI locally.
**Fix:** Add both test classes to the harness list.

```kotlin
"com.btgun.host.profile.ProfileStoreTestKt",
"com.btgun.host.profile.ProfileValidationTestKt",
```

#### WR-02: Pairing Restart Leaves Stale Profile Diagnostics Visible

**Classification:** WARNING
**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt:202`
**Issue:** `renderSession()` resets pairing/control state at `PairingWindow.kt:202-214`, but does not clear `activeProfileMetadata`, `lastProfileUpdateElapsedNanos`, `lastMappedProductStream`, `lastRawDebugEnabled`, or `packetStreamState` declared at `PairingWindow.kt:59-68`. Starting a new pairing session can show the previous Android profile/raw-debug/mapped-stream diagnostics until new metadata arrives.
**Fix:** Clear profile and stream diagnostics when a new pairing session is rendered.

```kotlin
activeProfileMetadata = null
lastProfileUpdateElapsedNanos = null
lastMappedProductStream = false
lastRawDebugEnabled = false
packetStreamState = InputStreamLifecycleState.STOPPED
```

---

_Reviewed: 2026-06-12T20:26:24Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
