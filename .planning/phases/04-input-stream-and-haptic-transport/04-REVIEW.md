---
phase: 04-input-stream-and-haptic-transport
reviewed: 2026-06-08T20:33:37Z
depth: deep
files_reviewed: 37
files_reviewed_list:
  - android-host/app/build.gradle.kts
  - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
  - android-host/app/src/main/java/com/btgun/host/haptics/DesktopHapticCommand.kt
  - android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt
  - android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt
  - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
  - android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt
  - android-host/app/src/main/java/com/btgun/host/transport/InputStreamConfig.kt
  - android-host/app/src/main/java/com/btgun/host/transport/InputStreamLifecycleState.kt
  - android-host/app/src/main/java/com/btgun/host/transport/InputStreamSequencer.kt
  - android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt
  - android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt
  - android-host/app/src/test/java/com/btgun/host/haptics/DesktopHapticCommandTest.kt
  - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt
  - android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt
  - android-host/app/src/test/java/com/btgun/host/transport/InputStreamLifecycleTest.kt
  - android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt
  - desktop-companion/build.gradle.kts
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamConfig.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamLifecycleState.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/haptics/HapticCommandCodecTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputStreamLifecycleTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputReceiverTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt
  - docs/protocol/input-stream-v1-fixtures.md
  - docs/protocol/lan-pairing-v1.md
findings:
  critical: 4
  warning: 1
  info: 0
  total: 5
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-06-08T20:33:37Z
**Depth:** deep
**Files Reviewed:** 37
**Status:** issues_found

## Summary

Reviewed Android host input sender/control/haptics, desktop control/UDP receiver, protocol docs, and focused tests. The implementation has four ship-blocking protocol/lifecycle defects: real-device UDP age checks use unrelated device clocks, QR-path control messages can run before `session_ready`, HMAC-valid malformed UDP frames can crash decode, and Android keeps UDP active after heartbeat timeout.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: BLOCKER - Desktop Drops Or Accepts UDP Frames Using Unrelated Device Clocks

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt:80`

**Issue:** `frameAgeExpired` subtracts Android `sendElapsedNanos` from desktop `receivedElapsedNanos`. Android writes `sendElapsedNanos` from `SystemClock.elapsedRealtimeNanos()` in `AndroidUdpInputSender.kt:116`, while the desktop receiver/test path uses desktop `System.nanoTime()` style values. Those monotonic clocks have different origins, so real LAN frames will be wrongly age-expired when desktop uptime is larger, or effectively never expire when Android uptime is larger. This breaks the Phase 4 input stream on real devices and invalidates the `frameAgeLimitMs` security/robustness gate.

**Fix:**
```kotlin
// Short-term: do not compare sender and receiver monotonic clocks.
private fun frameAgeExpired(frame: UdpInputFrame, receivedElapsedNanos: Long): Boolean =
    false

// Correct v1 fix: negotiate a sender-to-receiver monotonic offset over the
// trusted control channel, then apply the configured age limit in receiver time.
private fun frameAgeExpired(frame: UdpInputFrame, receivedElapsedNanos: Long): Boolean {
    val offset = config.senderToReceiverOffsetNanos ?: return false
    val senderSendInReceiverClock = frame.sendElapsedNanos + offset
    val ageNanos = receivedElapsedNanos - senderSendInReceiverClock
    return ageNanos < 0L || ageNanos > config.frameAgeLimitMs * NANOS_PER_MILLI
}
```

Add tests where Android send timestamps and desktop receive timestamps have large unrelated baselines; valid frames must not fail only because device uptimes differ.

### CR-02: BLOCKER - QR Path Accepts Trusted Stream And Haptic Messages Before `session_ready`

**File:** `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt:176`

**Issue:** For QR auth, `trustedSessionId` is initialized from the expected `sid` before any server `session_ready`. The pre-ready guard only blocks messages when `expectedSessionId == null`, so QR connections accept matching-session `input_stream_config` and `reserved_haptic_command` before the trusted-ready signal. `HostSessionService.kt:480` then starts UDP from that config, and line `488` can vibrate the phone, even though `onAuthenticated` has not run and first trust has not been saved. Existing tests cover the manual-code pre-ready case, but not the QR case.

**Fix:**
```kotlin
private var sessionReady = false

private fun handleServerEnvelope(...): Boolean {
    ...
    if (!sessionReady) {
        if (decoded.envelope.type != ControlMessageType.SESSION_READY) {
            recordControlError("session not ready")
            return false
        }
        val expected = authExpectedSessionId
        if (expected != null && decoded.envelope.sessionId != expected) {
            recordControlError("session mismatch")
            return false
        }
        trustedSessionId = decoded.envelope.sessionId
        sessionReady = true
        observeHeartbeat(elapsedRealtimeNanos())
        return true
    }
    if (decoded.envelope.sessionId != trustedSessionId) {
        recordControlError("session mismatch")
        return false
    }
    ...
}
```

Add QR-path tests that send `input_stream_config` and `reserved_haptic_command` before `session_ready` and assert no callback, no UDP start, no haptic execution, and `lastControlError == "session not ready"`.

### CR-03: BLOCKER - HMAC-Valid Malformed UDP Fields Crash The Decoder

**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt:156`

**Issue:** After HMAC verification, `authenticateAndDecode` directly constructs `UdpInputFrame`. Constructor `require` checks reject invalid sequence/timestamp invariants (`sequence > 0`, non-negative timestamps, `sendElapsedNanos >= captureElapsedNanos`). If authenticated bytes contain sequence `0`, negative timestamps, or send-before-capture, the constructor throws instead of returning `Rejected`. The desktop receiver path does not catch this, so a valid-HMAC malformed datagram from the trusted Android app can crash the receiver rather than being rejected before apply. The Android codec copy has the same decoder behavior at `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt:156`.

**Fix:**
```kotlin
val frame = runCatching {
    UdpInputFrame(
        type = type,
        streamSessionId = streamSessionId,
        sequence = buffer.getLong(OFFSET_SEQUENCE),
        captureElapsedNanos = buffer.getLong(OFFSET_CAPTURE_ELAPSED_NANOS),
        sendElapsedNanos = buffer.getLong(OFFSET_SEND_ELAPSED_NANOS),
        buttonBitmask = buffer.getInt(OFFSET_BUTTON_BITMASK),
        stickX = buffer.getShort(OFFSET_STICK_X).toInt(),
        stickY = buffer.getShort(OFFSET_STICK_Y).toInt(),
        motionProvider = bytes[OFFSET_MOTION_PROVIDER].toInt() and 0xff,
        motionCapabilityFlags = bytes[OFFSET_MOTION_CAPABILITY_FLAGS].toInt() and 0xff,
        yaw = buffer.getFloat(OFFSET_YAW),
        pitch = buffer.getFloat(OFFSET_PITCH),
        roll = buffer.getFloat(OFFSET_ROLL),
        rawAimX = buffer.getFloat(OFFSET_RAW_AIM_X),
        rawAimY = buffer.getFloat(OFFSET_RAW_AIM_Y),
        sourceSensorElapsedNanos = buffer.getLong(OFFSET_SOURCE_SENSOR_ELAPSED_NANOS),
    )
}.getOrElse {
    return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, it.message)
}
return UdpInputFrameDecodeResult.Accepted(frame)
```

Add codec and receiver tests for valid-HMAC frames with sequence `0`, negative timestamps, and `sendElapsedNanos < captureElapsedNanos`; each should reject as malformed and must not throw.

### CR-04: BLOCKER - Android Keeps Sending UDP After Heartbeat Timeout

**File:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt:565`

**Issue:** WebSocket failure/close paths call `scheduleUdpDisconnectGraceStop()` at lines `436` and `460`, which moves the UDP sender into grace and then stale. The heartbeat timeout path does not. When `DesktopLivenessCoordinator.refresh` marks the client disconnected, `refreshDesktopLiveness` clears and closes the client but leaves `udpInputSender` active and keeps snapshot ticks scheduled. Because the close callback will be ignored after `desktopControlClient` is nulled, the Android host can continue sending authenticated UDP indefinitely after trusted control liveness is lost.

**Fix:**
```kotlin
if (update.shouldClearClient) {
    scheduleUdpDisconnectGraceStop()
    desktopControlClient = null
    cancelDesktopLivenessTick()
    if (update.shouldCloseClient) {
        client.close()
    }
}
```

Add a HostSessionService or coordinator integration test for heartbeat timeout with an active `udpInputSender`: packet stream should become `GRACE`, then `STALE`, and no further snapshots should be sent after `controlDisconnectGraceMs`.

## Warnings

### WR-01: WARNING - Haptic Result Details Are Discarded Before Returning To Desktop

**File:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt:488`

**Issue:** `DesktopHapticCommandExecutor.handle` produces a full `HapticResult` with useful non-secret details such as `"phone pulse started"`, `"vibrate permission blocked"`, or runtime failure class names. `HostSessionService` only returns `.status`, and `DesktopControlClient.kt:420` rebuilds the result with `detail = status.wireName`. This violates the documented result detail contract and makes desktop diagnostics less useful. Existing integration tests only assert result status, so the loss is not covered.

**Fix:** Change the callback type to return `HapticResult`, pass through the executor result, and only synthesize a failed result when the command body is invalid.

```kotlin
onHapticCommandReceived = { command, receivedElapsedNanos ->
    desktopHapticExecutor.handle(command, receivedElapsedNanos)
}
```

Add client tests that cover `permission_blocked`, `failed`, and `expired` command results over the control channel and assert the `detail` field matches the executor result.

---

_Reviewed: 2026-06-08T20:33:37Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
