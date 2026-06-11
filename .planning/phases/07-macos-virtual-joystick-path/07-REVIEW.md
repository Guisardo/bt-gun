---
phase: 07-macos-virtual-joystick-path
reviewed: 2026-06-11T14:25:32Z
depth: deep
files_reviewed: 18
files_reviewed_list:
  - android-host/app/src/main/AndroidManifest.xml
  - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
  - android-host/app/src/main/java/com/btgun/host/MainActivity.kt
  - android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidStatus.kt
  - android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt
  - android-host/app/src/main/java/com/btgun/host/permissions/HostCapabilityProbe.kt
  - android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt
  - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
  - android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt
  - android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt
  - android-host/app/src/test/java/com/btgun/host/permissions/AndroidHidCapabilityTest.kt
  - android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java
  - tools/macos/BtGunGameControllerProbe.swift
  - docs/setup/android-bluetooth-hid-gamepad.md
findings:
  critical: 1
  warning: 0
  info: 0
  total: 1
status: issues_found
---

# Phase 07: Code Review Report

**Reviewed:** 2026-06-11T14:25:32Z
**Depth:** deep
**Files Reviewed:** 18
**Status:** issues_found

## Summary

Re-review verified the six previous findings were addressed: the macOS probe now requires a labeled target unless generic capture is explicit, Swift JSON optionals are unwrapped, HID proxy close uses the active proxy, stale callbacks are generation-gated, advertise permission is modeled/gated for the pairing window, and the setup guide documents the separate pairing action.

One new blocker remains in the Android HID permission boundary. The service can still reach Android 12+ HID profile APIs without a `BLUETOOTH_CONNECT` preflight or local `SecurityException` containment, so tapping the Bluetooth gamepad action with permission missing or revoked can crash the host process instead of surfacing a blocked HID state.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: HID start path can crash when BLUETOOTH_CONNECT is missing or revoked

**Classification:** BLOCKER

**File:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt:413`

**Issue:** `startBluetoothGamepad()` computes the current permission gate, but only blocks `BLUETOOTH_ADVERTISE` for `openPairingWindow = true`. It then calls `hidSessionController.startBluetoothGamepad()` or `openPairingWindow()`, which reaches `BluetoothAdapter.getProfileProxy()` at `AndroidBluetoothHidGamepad.kt:411` and later `BluetoothHidDevice.registerApp/sendReport/replyReport/reportError` without catching `SecurityException`. On Android 12+, those HID profile calls require `BLUETOOTH_CONNECT`. The dashboard can show the HID role as blocked, but the buttons still dispatch service actions; if permission is missing or revoked mid-session, the service can crash instead of reporting a blocked HID status.

**Fix:**

```kotlin
private fun startBluetoothGamepad(openPairingWindow: Boolean = false) {
    val gate = permissionGateState()
    if (gate.bluetoothConnect.state != CapabilityState.AVAILABLE) {
        currentState = currentState.copy(
            hidGamepadStatus = currentState.hidGamepadStatus.copy(
                unsupportedReason = gate.bluetoothConnect.detail,
            ),
        )
        AndroidLog.w(TAG, "startBluetoothGamepad blocked: ${gate.bluetoothConnect.detail}")
        return
    }
    if (openPairingWindow && gate.bluetoothAdvertise.state != CapabilityState.AVAILABLE) {
        // existing pairing blocked status
        return
    }
    // existing foreground/start path
}
```

Also wrap Android HID proxy calls inside `AndroidBtGunHidProfileConnector` and `AndroidBtGunHidDeviceProxy` so permission revocation after the service preflight returns `false` or `onProxyUnavailable("...SecurityException")` instead of escaping the callback path.

---

_Reviewed: 2026-06-11T14:25:32Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
