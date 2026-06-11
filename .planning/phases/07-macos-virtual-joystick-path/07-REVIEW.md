---
phase: 07-macos-virtual-joystick-path
reviewed: 2026-06-11T13:54:24Z
depth: deep
files_reviewed: 23
files_reviewed_list:
  - android-host/app/build.gradle.kts
  - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
  - android-host/app/src/main/java/com/btgun/host/MainActivity.kt
  - android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt
  - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidStatus.kt
  - android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt
  - android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt
  - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
  - android-host/app/src/main/java/com/btgun/host/util/AndroidLog.kt
  - android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt
  - android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt
  - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidDescriptorTest.kt
  - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidOutputReportMapperTest.kt
  - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt
  - android-host/app/src/test/java/com/btgun/host/permissions/AndroidHidCapabilityTest.kt
  - android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java
  - android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt
  - tools/macos/BtGunGameControllerProbe.swift
  - docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl
  - docs/setup/android-bluetooth-hid-gamepad.md
findings:
  critical: 4
  warning: 2
  info: 0
  total: 6
status: issues_found
---

# Phase 07: Code Review Report

**Reviewed:** 2026-06-11T13:54:24Z
**Depth:** deep
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Deep review found blocker-tier issues in the macOS proof tool and Android HID lifecycle. The most serious risk is that the macOS probe can mark DESK-03 evidence as passed from an unrelated controller because both GameController and IOHID fallback paths accept generic gamepads. Android HID stop/permission behavior also has crash and lifecycle races that can leave registration active after the user stops it.

## Critical Issues

### CR-01: macOS probe can pass DESK-03 with any connected gamepad

**Classification:** BLOCKER

**File:** `tools/macos/BtGunGameControllerProbe.swift:86`

**Issue:** The GameController path ranks controllers but still selects `ranked.first` even when `score` is 0, so an unrelated generic controller can be watched and reported as observed input. The IOHID fallback has the same problem more strongly: it matches every Generic Desktop/GamePad device, sets `selectedLabelMatch` to `bt-gun-or-android-label` when any device exists, and registers one manager-wide input callback for all matched devices. That can produce sanitized `phase7-gamecontroller-input` pass rows from the wrong controller and falsely close DESK-03.

**Fix:**

```swift
let ranked = controllers.enumerated()
    .map { (offset: $0.offset, controller: $0.element, score: score(controller: $0.element)) }
    .filter { $0.score > 0 }
    .sorted { $0.score > $1.score }

guard let selected = ranked.first else {
    emitOnce(
        key: "no-btgun-controller-visible",
        event: "controller-visible",
        fields: ["controller_count": controllers.count, "label_match": "generic-controller-only", "status": "not-visible"]
    )
    return
}
```

For IOHID, filter `IOHIDManagerCopyDevices` by sanitized manufacturer/product tokens before setting `hidFallbackVisible`, and register input callbacks only for the selected matching device. Generic gamepad observations should be `inconclusive` unless an explicit `--allow-generic-controller` capture flag is passed and recorded in the evidence row.

### CR-02: HID connector close passes a null proxy to Android

**Classification:** BLOCKER

**File:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt:393`

**Issue:** `AndroidBtGunHidProfileConnector.close()` stores only the service listener, then calls `adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, null)`. Android's close path requires the actual `BluetoothProfile` proxy object; passing null can crash on stop/destroy and also means the HID profile proxy is not reliably released.

**Fix:**

```kotlin
private var activeProxy: BluetoothHidDevice? = null

override fun requestHidDeviceProxy(callback: BtGunHidProfileCallback) {
    val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                activeProxy = proxy
                callback.onProxyAvailable(AndroidBtGunHidDeviceProxy(proxy, executor))
            } else {
                callback.onProxyUnavailable("HID_DEVICE proxy unavailable")
            }
        }
        // ...
    }
}

override fun close() {
    if (closed) return
    closed = true
    activeProxy?.let { proxy ->
        runCatching { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy) }
    }
    activeProxy = null
    listener = null
}
```

Add a unit seam/test that proves the Android connector closes the same proxy instance it received.

### CR-03: stale proxy callbacks can re-register HID mode after stop

**Classification:** BLOCKER

**File:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt:144`

**Issue:** `startGamepadMode()` requests the HID proxy asynchronously, but `stopGamepadMode()` only flips `started = false` and clears fields. If `onProxyAvailable()` arrives after stop or close, it does not check `started` or `closed`; it stores the proxy and calls `registerApp()` anyway. A user can tap stop, then Android can later register the phone as a HID gamepad unexpectedly.

**Fix:**

```kotlin
private fun onProxyAvailable(newProxy: BtGunHidDeviceProxy) {
    if (closed || !started) {
        newProxy.unregisterApp()
        return
    }
    proxy = newProxy
    // existing registration path
}

private fun onProxyUnavailable(reason: String) {
    if (closed || !started) return
    // existing unavailable path
}
```

Prefer a monotonically increasing request generation token so callbacks from older starts cannot affect a newer start/stop cycle.

### CR-04: pairing discoverability is not covered by the Android 12+ permission gate

**Classification:** BLOCKER

**File:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt:382`

**Issue:** The pairing path launches `BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE`, but the permission model only tracks scan/connect. `PermissionGate` defines `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`, `AndroidHidCapability` blocks only on connect permission, and the setup doc lists only `BLUETOOTH_CONNECT` as the runtime permission gate. On Android 12+, discoverability/advertising requires the advertise permission path; phones can fail the pairing window or throw `SecurityException` even while the dashboard says the HID gate is startable.

**Fix:**

```kotlin
object PermissionGate {
    const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    // include it in granted-permission checks and Android HID role state
}
```

Add `BLUETOOTH_ADVERTISE` to the manifest and runtime request list, model it in `AndroidHidCapabilityInput`, and gate `startBluetoothGamepad(openPairingWindow = true)` before calling `openPairingWindow`. Catch `SecurityException` around `ACTION_REQUEST_DISCOVERABLE` and surface a blocked HID status instead of only returning `false`.

## Warnings

### WR-01: Swift JSON emission keeps non-nil Optional values boxed

**Classification:** WARNING

**File:** `tools/macos/BtGunGameControllerProbe.swift:424`

**Issue:** `emit()` converts nil optionals to `NSNull`, but non-nil optionals such as `selectedSlot as Any` remain boxed as `Optional<Int>`. Those values are not valid JSON objects, so `JSONSerialization.data` can fail and silently drop lines because the result is optional-ignored. This affects summary, fallback-visible, and haptic-attempt events that include `controller_slot`.

**Fix:** Avoid passing optionals as `Any`; unwrap before building fields.

```swift
private func optionalInt(_ value: Int?) -> Any {
    value.map { $0 as Any } ?? NSNull()
}

fields: [
    "controller_slot": optionalInt(selectedSlot),
    // ...
]
```

Also make `emit` report serialization failures to stderr with sanitized text so probe output loss is visible.

### WR-02: setup doc tells users Start opens pairing, but code requires a separate action

**Classification:** WARNING

**File:** `docs/setup/android-bluetooth-hid-gamepad.md:22`

**Issue:** The setup guide says **Start Bluetooth gamepad** starts the HID probe, registers SDP, and opens the pairing-mode countdown. The implementation's start action only calls `HostSessionHidController.startBluetoothGamepad`; the discoverable pairing window is opened only by `ACTION_START_HID_PAIRING_WINDOW` through the separate **Open pairing window** button. Users following the doc can wait for a countdown that never opens.

**Fix:** Either change `startBluetoothGamepad()` to open the pairing window after registration succeeds, or update the guide to say: tap **Start Bluetooth gamepad**, wait for registration, then tap **Open pairing window** before pairing from macOS. Add a UI/service test that pins the chosen behavior.

---

_Reviewed: 2026-06-11T13:54:24Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
