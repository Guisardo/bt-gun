---
phase: 04-input-stream-and-haptic-transport
fixed_at: 2026-06-08T21:18:03Z
review_path: .planning/phases/04-input-stream-and-haptic-transport/04-REVIEW.md
iteration: "1-retry-continuation"
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 04: Code Review Fix Report

**Fixed at:** 2026-06-08T21:18:03Z
**Source review:** `.planning/phases/04-input-stream-and-haptic-transport/04-REVIEW.md`
**Iteration:** 1-retry-continuation

**Summary:**
- Findings in scope: 5
- Fixed: 5
- Skipped: 0

## Fixed Issues

### CR-01: BLOCKER - Desktop Drops Or Accepts UDP Frames Using Unrelated Device Clocks

**Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt`, `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt`
**Commit:** 5fd1049
**Applied fix:** Removed cross-device monotonic clock age expiry and covered unrelated Android/desktop uptime baselines.

### CR-02: BLOCKER - QR Path Accepts Trusted Stream And Haptic Messages Before `session_ready`

**Files modified:** `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt`, `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt`
**Commit:** c5a8719
**Applied fix:** Added a `sessionReady` gate so QR and manual paths reject input stream config and haptic command messages until `session_ready`.

### CR-03: BLOCKER - HMAC-Valid Malformed UDP Fields Crash The Decoder

**Files modified:** `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt`, `android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt`, `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt`, `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt`, `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputReceiverTest.kt`
**Commit:** 5dcf350
**Applied fix:** Wrapped authenticated field construction so invalid sequence/timestamps reject as malformed instead of throwing.

### CR-04: BLOCKER - Android Keeps Sending UDP After Heartbeat Timeout

**Files modified:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`, `android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt`, `android-host/app/build.gradle.kts`
**Commit:** 47dc63c
**Applied fix:** Heartbeat timeout clear now schedules UDP disconnect grace before clearing the client, and a focused host liveness action test locks the timeout-to-grace behavior.

### WR-01: WARNING - Haptic Result Details Are Discarded Before Returning To Desktop

**Files modified:** `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt`, `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`, `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt`
**Commit:** ca5f770
**Applied fix:** Changed the Android haptic callback to return full `HapticResult`, passed executor results through `HostSessionService`, and asserted `detail` survives for `permission_blocked`, `failed`, and `expired`.

## Skipped Issues

None.

## Verification

- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*HostSessionServiceLiveness*' --tests '*InputStreamLifecycle*' --tests '*DesktopControlClient*'` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DesktopControlClient*' --tests '*DesktopHapticCommand*'` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test` - passed.

---

_Fixed: 2026-06-08T21:18:03Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1-retry-continuation_
