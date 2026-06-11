---
phase: 07-macos-virtual-joystick-path
fixed_at: 2026-06-11T14:16:57Z
review_path: .planning/phases/07-macos-virtual-joystick-path/07-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 6
skipped: 0
status: all_fixed
---

# Phase 07: Code Review Fix Report

**Fixed at:** 2026-06-11T14:16:57Z
**Source review:** `.planning/phases/07-macos-virtual-joystick-path/07-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 6
- Fixed: 6
- Skipped: 0

## Fixed Issues

### CR-01: macOS probe can pass DESK-03 with any connected gamepad

**Status:** fixed: requires human verification
**Files modified:** `tools/macos/BtGunGameControllerProbe.swift`
**Commit:** `3eb4021`
**Applied fix:** Requires BT Gun/Android label matches by default, records `--allow-generic-controller` when generic capture is explicitly allowed, and ignores IOHID input values from non-selected HID devices.

### CR-02: HID connector close passes a null proxy to Android

**Status:** fixed
**Files modified:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`
**Commit:** `943dd85`
**Applied fix:** Stores the active `BluetoothHidDevice` proxy from `onServiceConnected` and passes that proxy to `BluetoothAdapter.closeProfileProxy` on connector close.

### CR-03: stale proxy callbacks can re-register HID mode after stop

**Status:** fixed: requires human verification
**Files modified:** `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`, `android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt`
**Commit:** `d7a9544`
**Applied fix:** Added request generations to proxy and device callbacks, ignored stale callbacks after stop/close or newer starts, and pinned stale-callback behavior with focused tests.

### CR-04: pairing discoverability is not covered by the Android 12+ permission gate

**Status:** fixed: requires human verification
**Files modified:** `android-host/app/src/main/AndroidManifest.xml`, `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`, `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt`, `android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt`, `android-host/app/src/main/java/com/btgun/host/permissions/HostCapabilityProbe.kt`, `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt`, `android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt`, `android-host/app/src/test/java/com/btgun/host/permissions/AndroidHidCapabilityTest.kt`, `android-host/app/src/test/java/com/btgun/host/permissions/PermissionGateTest.java`, `docs/setup/android-bluetooth-hid-gamepad.md`
**Commit:** `7bbe966`
**Applied fix:** Added `BLUETOOTH_ADVERTISE` to manifest, runtime permission probing, HID capability evaluation, service pairing-window gating, and discoverability `SecurityException` status reporting.

### WR-01: Swift JSON emission keeps non-nil Optional values boxed

**Status:** fixed
**Files modified:** `tools/macos/BtGunGameControllerProbe.swift`
**Commit:** `75525a1`
**Applied fix:** Unwrapped optional controller slots before emission and reported invalid JSON/serialization failures to sanitized stderr instead of silently dropping probe rows.

### WR-02: setup doc tells users Start opens pairing, but code requires a separate action

**Status:** fixed
**Files modified:** `docs/setup/android-bluetooth-hid-gamepad.md`, `android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt`
**Commit:** `e991206`
**Applied fix:** Updated setup flow to Start Bluetooth gamepad, wait for HID registration, then Open pairing window; added a service-level test pinning the separate actions.

## Skipped Issues

None.

## Verification

- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*AndroidBluetoothHidGamepadState*' --tests '*AndroidHidCapability*' --tests '*PermissionGate*' --no-daemon --console=plain`
- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*HostSessionServiceLiveness*' --no-daemon --console=plain`
- PASS: `xcrun swiftc -typecheck tools/macos/BtGunGameControllerProbe.swift`
- NOTE: Gradle and Swift typecheck both failed inside the restricted sandbox first. Gradle was blocked by `java.net.SocketException: Operation not permitted`; Swift was blocked writing to the clang module cache. Both passed when rerun with approved unsandboxed execution.

---

_Fixed: 2026-06-11T14:16:57Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
