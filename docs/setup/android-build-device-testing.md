# Android Build and Device Testing

This doc is the v1 operator path for building the Android host, installing it on a test phone, capturing local evidence, and proving the two runtime modes: Bluetooth HID gamepad and LAN paired session.

## Toolchain

| Item | Expected value |
|------|----------------|
| Android modules | `android-host/:runtime`, `android-host/:app`, `android-host/:user-app` |
| Gradle project | `android-host` |
| Kotlin/JVM target | Java 17 |
| Android SDK | compile SDK `35`, target SDK `35`, min SDK `23` |
| Local SDK path | Set `ANDROID_HOME` to the local Android SDK path. |
| Local JDK path | Set `JAVA_HOME` to the local OpenJDK 17 path. |
| Gradle cache workaround | `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home` |

Module roles:

| Module | Role | Output |
|--------|------|--------|
| `:runtime` | Shared BLE gun input, motion, profiles, calibration, LAN, HID, haptics, and play/session orchestration. | Android library; no APK. |
| `:app` | Debug/diagnostic host app with fixtures, raw toggles, and developer dashboards. | `android-host/app/build/outputs/apk/debug/app-debug.apk` |
| `:user-app` | User-facing `Gamepad Extension` app package `com.btgun.gamepadextension`. | `android-host/user-app/build/outputs/apk/debug/user-app-debug.apk` |

Use this local shell prefix when the default Gradle launch path fails:

```bash
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --no-daemon --console=plain
```

## Build and Install

Run runtime tests before device work:

```bash
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host :runtime:testDebugUnitTest --no-daemon --console=plain
```

Build the debug host APK:

```bash
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host :app:assembleDebug --no-daemon --console=plain
```

Build the user app APK:

```bash
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host :user-app:assembleDebug --no-daemon --console=plain
```

Install the debug host through USB:

```bash
adb devices
adb install -r android-host/app/build/outputs/apk/debug/app-debug.apk
```

Install the user app through USB:

```bash
adb devices
adb install -r android-host/user-app/build/outputs/apk/debug/user-app-debug.apk
```

## Runtime Permissions

Grant or verify these before proof:

| Permission area | Required for |
|-----------------|--------------|
| Nearby devices / Bluetooth | iPega BLE gun, Android Bluetooth HID role, paired desktop modes |
| Location on older Android versions | Bluetooth scanning when platform requires it |
| Sensors | motion aim providers and recenter proof |
| Notifications / foreground service visibility | long-running host session status |
| Network access | LAN pairing/control and UDP input stream |

Disabled Bluetooth, missing Nearby Devices permission, missing sensor access, or blocked foreground service must show a blocked state in the app instead of hidden failure.

## USB and Evidence Capture

Use USB for install and local capture. Keep raw captures under ignored evidence paths. Commit only sanitized manifest rows or doc summaries.

Basic log capture:

```bash
adb logcat -c
adb logcat -v threadtime
```

Phase 2 evidence script pattern:

```bash
android-host/scripts/collect-phase2-host-evidence.sh .evidence/phase2/host-live-input
```

For Phase 10 diagnostics, prefer `.evidence/phase10/` for local raw sources and export only sanitized diagnostic bundles.

## Real Gun Steps

1. Charge the iPega gun and power it on.
2. Open Android host app.
3. Grant Bluetooth, Nearby Devices, sensors, foreground-service, and network permissions.
4. Connect to the real gun from the app session surface.
5. Verify trigger, reload, joystick, X/Y/A/B, and connection state update.
6. Move the phone and verify motion aim provider plus aim graph/status update.
7. Hold reload for two seconds and verify recenter appears without losing normal reload down/up.
8. Run phone haptic proof from desktop LAN, Windows VHF, or visualizer path. Physical gun motor feedback is deferred.

## Android Bluetooth HID Mode

Use this mode when macOS or Windows should see the Android phone as a normal gamepad-style joystick.

1. Confirm the app shows HID role available.
2. Tap **Start Bluetooth gamepad**.
3. Wait for HID registration to report active.
4. Tap **Open pairing window**.
5. Pair from the desktop Bluetooth UI.
6. Verify host connected status.
7. Press gun controls and move phone aim.

This mode is separate from the BLE gun connection and LAN desktop companion. If HID role or registration is blocked by the phone/OEM, document the blocked row and use the Windows VHF path as the other v1 OS-visible route.

## LAN Mode

Use LAN mode for desktop companion pairing, diagnostics, replay, Windows VHF fallback, visualizer status, and phone haptics.

1. Start desktop companion pairing window.
2. Scan the QR payload or use the visible fallback entry path.
3. Wait for authenticated control session ready.
4. Let desktop send fresh UDP input stream config over the authenticated control channel.
5. Verify packet stream state moves to active.
6. Run visualizer haptic test and expect Android phone haptic result.

Do not start UDP from QR or fallback material alone. UDP starts only after trusted control delivers fresh stream config.

## Common Blockers

| Blocker | Likely fix |
|---------|------------|
| Gradle cannot start with default local cache | Use `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home` and Java 17. |
| Android SDK not found | Set `ANDROID_HOME` to the local Android SDK path. |
| Device not listed | Replug USB, trust the computer prompt, then rerun `adb devices`. |
| Bluetooth off or permission blocked | Enable Bluetooth and grant Nearby Devices/Bluetooth permission. |
| HID role unavailable | Test another Android phone or use Windows VHF path. |
| macOS pairs but haptics absent | Expected current limitation; use LAN or Windows VHF phone-haptic route. |
| LAN pairing fails | Start a new desktop pairing session and retry QR/fallback entry before changing code. |
| Packet stream stale | Reconnect trusted control so desktop sends fresh stream config. |
