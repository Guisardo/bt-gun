# Phase 04: Input Stream and Haptic Transport - Research

**Researched:** 2026-06-08  
**Domain:** Authenticated UDP input stream, reliable haptic command transport, Android phone haptics, desktop receiver parsing  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### UDP Input Frames
- **D-01:** Phase 4 uses binary UDP input frames plus a debug decoder. JSON remains appropriate for control/debug docs, not the high-rate input stream.
- **D-02:** UDP input uses a hybrid model: 60 Hz state snapshots plus immediate control-edge frames for trigger, reload, X/Y/A/B, and stick/button changes.
- **D-03:** Snapshot frames carry current button bitmask, stick axes, raw normalized motion, provider/capability flags, session identity, sequence number, capture timestamp, and send timestamp.
- **D-04:** Motion payload is raw normalized motion only: provider, capability flags, raw aim/yaw/pitch/roll, and timestamps. Do not include Android preview aim as product mapping. Desktop profiles own final aim mapping.
- **D-05:** Edge frames are for faster button/control response, not authoritative long-term state. Snapshot frames remain the recovery source after loss.

### Phone Haptics
- **D-06:** Phase 4 turns the Phase 3 reserved haptic type into a pulse-first haptic command over the reliable control channel.
- **D-07:** Haptic command body includes command id, strength, duration, and TTL/play deadline. Optional pattern fields may be reserved for compatibility, but full pattern behavior is not required in Phase 4.
- **D-08:** Latest valid haptic command wins. Android cancels any active phone vibration before starting the new valid command.
- **D-09:** Android returns ack/fail after accepting the command and attempting to start vibration, not after the vibration duration ends.
- **D-10:** Haptic result status must distinguish at least `started`, `expired`, `unsupported`, `permission_blocked`, `failed`, and `cancelled`.
- **D-11:** If the haptic command is expired before Android can start it, Android must not vibrate the phone and must return `expired`.

### Recovery and Replay Handling
- **D-12:** Desktop accepts UDP input only for the expected trusted session and rejects wrong-session, duplicate-sequence, old-sequence, and age-expired frames.
- **D-13:** Input stream timeout clears active buttons/pressed controls only. Aim remains last-known rather than being zeroed, but downstream status should make the stream stale/timeout state visible.
- **D-14:** Active phone haptic is cancelled on session change. A short control-channel disconnect does not automatically cancel a currently playing pulse.
- **D-15:** UDP may continue for a brief grace window after reliable control disconnect, but new or changed sessions require fresh authenticated control before input is trusted again.

### the agent's Discretion
- Choose exact binary field layout, field widths, byte order, AEAD/nonce construction, replay-window size, frame age limit, snapshot jitter tolerance, and UDP grace duration during planning.
- Choose exact haptic strength scale, default duration caps, TTL defaults, command/result envelope names, and cancellation result details as long as the locked behavior above is preserved.
- Choose whether Phase 4 writes one shared protocol module or keeps mirrored Android/desktop codecs, provided tests prove both sides stay wire-compatible.

### Deferred Ideas (OUT OF SCOPE)
- Full haptic pattern playback remains deferred beyond Phase 4 unless planner can include it without risk to pulse-first behavior.
- Physical gun motor rumble remains deferred/v2.
- Virtual joystick backends remain Phase 5 through Phase 7.
- Desktop profile mapping and tuning remain Phase 8.
- Visualizer latency dashboards, packet-loss UI, and full replay diagnostics remain Phase 9 or Phase 10 as mapped.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ANDR-07 | Android host app can receive a haptic command from desktop and vibrate the Android phone. [VERIFIED: .planning/REQUIREMENTS.md] | Extend `PhoneHaptics` from local test to desktop-origin one-shot pulse execution with `VibrationEffect.createOneShot` on API 26+ and legacy `Vibrator.vibrate(duration)` below API 26. [VERIFIED: local code grep] [CITED: https://developer.android.com/reference/android/os/VibrationEffect] |
| TRAN-04 | Android host app streams high-rate input and motion samples to desktop using versioned UDP input frames. [VERIFIED: .planning/REQUIREMENTS.md] | Add Android UDP sender under `HostSessionService`, negotiated by trusted WSS control state, sending binary snapshot and edge frames. [VERIFIED: 04-CONTEXT.md] [CITED: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html] |
| TRAN-05 | UDP input frames include sequence number, session id, capture timestamp, send timestamp, button bitmask, axes, motion payload, and motion provider/capability flags. [VERIFIED: .planning/REQUIREMENTS.md] | Define a fixed binary frame layout using existing `GunInputState`, `MotionSample`, and monotonic elapsed timestamps as source data. [VERIFIED: android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent] |
| TRAN-07 | Desktop can send a haptic command with command id, strength, duration, expiry/TTL, and optional pattern. [VERIFIED: .planning/REQUIREMENTS.md] | Promote the Phase 3 reserved haptic control type into a validated haptic command body and add a haptic result body on the reliable control channel. [VERIFIED: docs/protocol/lan-pairing-v1.md] [VERIFIED: 04-CONTEXT.md] |
| TRAN-08 | Android host app returns haptic acknowledgement or failure status to desktop. [VERIFIED: .planning/REQUIREMENTS.md] | Android should return `started`, `expired`, `unsupported`, `permission_blocked`, `failed`, or `cancelled` immediately after command validation and vibration start attempt. [VERIFIED: 04-CONTEXT.md] |
| TRAN-09 | Desktop and Android can recover cleanly from LAN disconnect without playing stale haptic commands. [VERIFIED: .planning/REQUIREMENTS.md] | Use per-session UDP keys, strict session ids, monotonic UDP sequence rejection, haptic TTL, latest-command-wins cancellation, and control-session change cleanup. [VERIFIED: 04-CONTEXT.md] |
| DESK-01 | Desktop companion can receive, validate, decrypt/authenticate, and parse normalized Android input frames. [VERIFIED: .planning/REQUIREMENTS.md] | Add desktop UDP receiver/parser gated by trusted control session and HMAC-authenticated binary frame verification before parse/apply. [VERIFIED: 04-CONTEXT.md] [CITED: https://www.rfc-editor.org/info/rfc2104/] |
| PERF-03 | Desktop drops stale or replayed UDP input frames instead of applying old aim/control data. [VERIFIED: .planning/REQUIREMENTS.md] | Track highest accepted sequence per stream session, reject duplicate/old/wrong-session/bad-MAC/age-expired frames, and expose timeout state separately from last aim. [VERIFIED: 04-CONTEXT.md] |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- Chat responses use `$caveman ultra`; project artifacts should remain normal clear Markdown. [VERIFIED: AGENTS.md]
- Desktop support remains Windows 11 x64 and macOS Apple Silicon for v1; Phase 4 protocol must not assume one platform receiver. [VERIFIED: AGENTS.md]
- Android-to-desktop v1 transport is Wi-Fi/LAN and must reuse QR or pair-code session trust. [VERIFIED: AGENTS.md]
- Visualizer input path targets under 50 ms, so Phase 4 must preserve capture/send/receive timestamps even though full visualizer metrics are later. [VERIFIED: AGENTS.md] [VERIFIED: .planning/REQUIREMENTS.md]
- Desktop profiles own final aim mapping; Android sends normalized raw motion and provider metadata. [VERIFIED: AGENTS.md] [VERIFIED: 04-CONTEXT.md]
- Direct desktop-to-gun Bluetooth is out of v1 scope. [VERIFIED: AGENTS.md]
- Production source exists for Android host and desktop companion; no project-defined `.codex/skills` or `.agents/skills` exist. [VERIFIED: local `find .codex/skills .agents/skills`] [VERIFIED: local `rg --files`]

## Summary

Phase 4 should add one narrow runtime transport slice: trusted WSS control negotiates UDP stream parameters and carries haptic command/result messages, while Android sends authenticated binary UDP input frames to a desktop receiver. [VERIFIED: 04-CONTEXT.md] The recommended MVP uses existing Kotlin/JVM and Android dependencies only, platform UDP sockets, `javax.crypto.Mac` HMAC-SHA256 for frame authentication, and fixture-backed mirrored codecs because the project already keeps Android and desktop protocol code in separate Gradle builds. [VERIFIED: local build.gradle.kts] [CITED: https://www.rfc-editor.org/info/rfc2104/] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html]

The critical planning constraint is session coupling: UDP must not start from QR data alone, and haptics must not execute from untrusted or stale control state. [VERIFIED: docs/protocol/lan-pairing-v1.md] A trusted control session should send an `input_stream_config` payload containing UDP host/port, a random per-session stream id, a random per-session HMAC key, frame-age limit, and stream timeout values; Android should start UDP only after this trusted config arrives. [ASSUMED] Haptic TTL should be relative/local because Android and desktop monotonic clocks are not comparable across devices without an explicit clock-sync protocol. [CITED: https://developer.android.com/reference/android/os/SystemClock] [ASSUMED]

**Primary recommendation:** Plan Phase 4 as five vertical slices: shared protocol docs/fixtures first, Android UDP sender second, desktop authenticated UDP receiver third, haptic command/result over existing WSS fourth, then disconnect/replay/timeout UI and manual smoke. [VERIFIED: 04-CONTEXT.md] [VERIFIED: local code grep]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Trusted stream negotiation | Desktop control server | Android control client | Desktop already owns trusted session acceptance, pairing proof, and WSS server; Android should only start UDP after trusted config. [VERIFIED: desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt] [VERIFIED: android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt] |
| UDP input capture/serialization | Android foreground service | Android normalized models | `HostSessionService` already owns gun state, motion samples, recenter, and desktop control lifecycle; transport should not bypass foreground state. [VERIFIED: android-host/app/src/main/java/com/btgun/host/HostSessionService.kt] |
| UDP validation/parsing | Desktop companion | Protocol codec | Desktop companion must authenticate, reject stale/replayed frames, parse normalized state, and expose receiver state for later backend/visualizer work. [VERIFIED: .planning/REQUIREMENTS.md] [VERIFIED: 04-CONTEXT.md] |
| Final aim mapping | Later desktop profile tier | Phase 4 receiver only preserves raw motion | Locked decision D-04 forbids Android preview aim as product mapping; Phase 4 should forward raw normalized yaw/pitch/roll/raw aim and provider flags only. [VERIFIED: 04-CONTEXT.md] |
| Haptic command issue | Desktop control channel | Future virtual backend / visualizer | Phase 4 only defines desktop-origin phone-haptic command path; later driver/visualizer phases will become command sources. [VERIFIED: .planning/ROADMAP.md] |
| Haptic execution | Android phone haptics | Android control client | v1 feedback is Android phone vibration, and existing `PhoneHaptics` wraps the platform vibrator API. [VERIFIED: .planning/STATE.md] [VERIFIED: android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt] |
| Disconnect cleanup | Both endpoints | Heartbeat/liveness state | Phase 3 already has connected/degraded/disconnected heartbeat states; Phase 4 should attach UDP grace and haptic cancellation policy to those states. [VERIFIED: desktop-companion/src/main/kotlin/com/btgun/desktop/control/HeartbeatMonitor.kt] [VERIFIED: 04-CONTEXT.md] |

## Standard Stack

### Core

| Library / API | Version | Purpose | Why Standard |
|---------------|---------|---------|--------------|
| Android Kotlin host app | compileSdk 35, minSdk 23, targetSdk 35. [VERIFIED: android-host/app/build.gradle.kts] | Capture normalized gun/motion state and send UDP frames. | Existing Android host already owns normalized events, motion capture, foreground service, and desktop control client. [VERIFIED: local code grep] |
| Kotlin/JVM desktop companion | Kotlin JVM plugin 2.0.21, Java 17 target. [VERIFIED: desktop-companion/build.gradle.kts] | UDP receiver, parser, haptic command source, and diagnostics. | Existing desktop companion already owns pairing/control server and portable Swing surface. [VERIFIED: local code grep] |
| Ktor WebSockets | `io.ktor:*` 3.5.0 already installed. [VERIFIED: desktop-companion/build.gradle.kts] | Reliable control channel for stream config, heartbeat, haptic command/result. | Ktor docs support full-duplex WebSocket server sessions, and Phase 3 already uses Ktor for trusted WSS. [CITED: https://ktor.io/docs/server-websockets.html] [VERIFIED: desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt] |
| OkHttp WebSocket | `com.squareup.okhttp3:okhttp` 5.3.2 already installed. [VERIFIED: android-host/app/build.gradle.kts] | Android reliable control client. | OkHttp WebSocket is non-blocking and Phase 3 already uses it with pinned SPKI trust. [CITED: https://square.github.io/okhttp/5.x/okhttp/okhttp3/-web-socket/] [VERIFIED: android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt] |
| Java/Android UDP sockets | Platform APIs. [CITED: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html] | Binary UDP frame send/receive. | Datagram channels/sockets avoid adding transport dependencies and fit one producer/one receiver MVP. [CITED: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html] |
| `javax.crypto.Mac` HmacSHA256 | Platform JCA API. [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] | Authenticate UDP frames. | HMAC is a standard keyed message-authentication construction, and Java requires standard Mac algorithm support. [CITED: https://www.rfc-editor.org/info/rfc2104/] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |
| Android `Vibrator` / `VibrationEffect` | Platform APIs; `VibrationEffect.createOneShot` added API 26. [CITED: https://developer.android.com/reference/android/os/VibrationEffect] | Phone haptic pulse execution. | Existing wrapper already handles API split and permission/runtime failures for local haptic tests. [VERIFIED: android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt] |
| `SystemClock.elapsedRealtimeNanos` / `SensorEvent.timestamp` | Android platform APIs. [CITED: https://developer.android.com/reference/android/os/SystemClock] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent] | Monotonic capture/send/receive timing. | Android docs define elapsed realtime as monotonic since boot, and SensorEvent timestamps use nanoseconds for event time. [CITED: https://developer.android.com/reference/android/os/SystemClock] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent] |

### Supporting

| Library / API | Version | Purpose | When to Use |
|---------------|---------|---------|-------------|
| kotlinx.serialization JSON | 1.11.0 already installed. [VERIFIED: local build.gradle.kts] | Control-channel haptic/config payload JSON. | Use for WSS control bodies only; binary UDP frames should use explicit byte codecs. [VERIFIED: 04-CONTEXT.md] [CITED: https://kotlinlang.org/docs/serialization.html] |
| Java `ByteBuffer` | Platform API; newly created buffers default to big endian. [CITED: https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html] | Fixed binary UDP frame encode/decode. | Use explicit `ByteOrder.BIG_ENDIAN` at the protocol boundary so tests catch accidental endian drift. [CITED: https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html] |
| Existing heartbeat/liveness classes | Local code. [VERIFIED: desktop-companion/src/main/kotlin/com/btgun/desktop/control/HeartbeatMonitor.kt] | Control disconnect, UDP grace, stale state surfacing. | Reuse Phase 3 connected/degraded/disconnected timing instead of adding an unrelated lifecycle model. [VERIFIED: local code grep] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| HMAC-authenticated plaintext UDP | AES-GCM AEAD UDP | AEAD would add confidentiality but forces strict nonce construction and replay handling; Phase 4 requirements allow authenticate-or-decrypt and local input is not secret enough to justify nonce-risk complexity in the MVP. [VERIFIED: .planning/REQUIREMENTS.md] [CITED: https://www.rfc-editor.org/info/rfc5116/] [ASSUMED] |
| Binary UDP frames | JSON UDP frames | JSON is easier to inspect but contradicts locked D-01 and adds high-rate parse/size overhead. [VERIFIED: 04-CONTEXT.md] |
| UDP for haptic commands | Existing WSS control channel | UDP haptics would need its own retry/ack/ordering; Phase 3 already built reliable WSS for haptic command space. [VERIFIED: docs/protocol/lan-pairing-v1.md] |
| Shared Kotlin protocol module | Mirrored Android/desktop codecs with golden fixtures | A shared module reduces duplication but requires Gradle restructure across separate builds; mirrored codecs match current code and are safe if fixture compatibility tests are mandatory. [VERIFIED: local build.gradle.kts] [ASSUMED] |
| Absolute haptic play deadline | Relative `ttlMs` evaluated on Android receive/dispatch clock | Absolute monotonic timestamps are not comparable across devices; relative TTL avoids false expiry until an explicit clock-sync protocol exists. [CITED: https://developer.android.com/reference/android/os/SystemClock] [ASSUMED] |

**Installation:** No new external package installs are recommended for Phase 4. [VERIFIED: local build.gradle.kts]

```bash
# Reuse existing dependencies in android-host and desktop-companion.
# Planner should not add transport/crypto/haptic packages for the MVP.
```

**Version verification:** Existing package versions were read from `android-host/app/build.gradle.kts` and `desktop-companion/build.gradle.kts`; no registry lookup is required because this research recommends no new external package installation. [VERIFIED: local build.gradle.kts]

## Package Legitimacy Audit

Phase 4 recommends no new external packages, so the Package Legitimacy Gate is not triggered. [VERIFIED: local build.gradle.kts]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| none | — | — | — | — | not run | No new install |

**Packages removed due to slopcheck [SLOP] verdict:** none; no packages were proposed. [VERIFIED: research scope]  
**Packages flagged as suspicious [SUS]:** none; no packages were proposed. [VERIFIED: research scope]

## Architecture Patterns

### System Architecture Diagram

```text
---------------------------+       WSS trusted session        +----------------------------+
| Desktop Control Server   | <------------------------------> | Android DesktopControlClient|
| proof-gated session      |                                  | pinned SPKI trust          |
| heartbeat/liveness       | -- input_stream_config --------> | starts UDP after config    |
| haptic command source    | <- haptic_result --------------- | haptic ack/fail sender     |
+-------------+-------------+                                  +-------------+--------------+
              |                                                                  |
              | opens UDP receiver keyed by streamSessionId                      |
              v                                                                  v
+-------------+-------------+       authenticated UDP input       +--------------+-------------+
| Desktop UDP Receiver     | <------------------------------------ | Android UDP Sender          |
| verify HMAC/session/seq  |                                       | snapshots + edge frames     |
| reject stale/replay      |                                       | capture/send timestamps     |
| expose parsed input      |                                       | raw normalized motion only   |
+-------------+-------------+                                       +--------------+-------------+
              |
              v
+-------------+-------------+
| Receiver State / MVP Tap |
| latest input snapshot    |
| stale flag / buttons clear|
| latency sample fields    |
+---------------------------+
```

### Recommended Project Structure

```text
docs/protocol/
  lan-pairing-v1.md                  # Update with stream config, UDP frame schema, haptic command/result
  input-stream-v1-fixtures.md         # Human-readable fixture catalog, or JSON fixture index

android-host/app/src/main/java/com/btgun/host/transport/
  InputStreamConfig.kt                # Trusted config from control channel
  UdpInputFrameCodec.kt               # Android encoder and debug decoder
  AndroidUdpInputSender.kt            # Foreground-session-owned UDP sender
  InputStreamSequencer.kt             # UDP sequence and snapshot/edge timing

android-host/app/src/main/java/com/btgun/host/haptics/
  DesktopHapticCommand.kt             # Command/result models and execution adapter

desktop-companion/src/main/kotlin/com/btgun/desktop/transport/
  InputStreamConfig.kt                # Desktop config producer and receiver state
  UdpInputFrameCodec.kt               # Desktop decoder and fixture encoder
  UdpInputReceiver.kt                 # HMAC/session/age/sequence validation
  InputReplayGuard.kt                 # Sequence, stale, duplicate, timeout behavior

desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/
  HapticCommand.kt                    # Command/result JSON body builders

android-host/app/src/test/java/com/btgun/host/transport/
desktop-companion/src/test/kotlin/com/btgun/desktop/transport/
  *CodecTest.kt                       # Golden fixture compatibility tests
```

### Pattern 1: Trusted Control Negotiates UDP Stream

**What:** After `session_ready`, desktop sends a trusted control envelope with UDP stream parameters and a random per-session HMAC key. [VERIFIED: docs/protocol/lan-pairing-v1.md] [ASSUMED]  
**When to use:** Every accepted Phase 4 control session, including QR and manual pairing paths. [VERIFIED: docs/protocol/lan-pairing-v1.md]  
**Planning implication:** Do not derive UDP trust directly from QR/manual material; manual reconnect has no high-entropy QR secret on Android, while WSS can safely deliver a fresh stream key after authentication. [VERIFIED: docs/protocol/lan-pairing-v1.md] [ASSUMED]

```json
{
  "v": 1,
  "type": "input_stream_config",
  "msgId": "desktop-input-stream-config",
  "sessionId": "<trusted-control-session-id>",
  "seq": 2,
  "sentElapsedNanos": 1234567890,
  "body": {
    "streamSessionIdHex": "16-byte-random-hex",
    "udpHost": "192.168.1.44",
    "udpPort": 41731,
    "hmacSha256KeyBase64Url": "32-byte-random-key",
    "snapshotHz": 60,
    "frameAgeLimitMs": 150,
    "streamTimeoutMs": 250,
    "controlDisconnectGraceMs": 1500
  }
}
```

### Pattern 2: Binary Authenticated UDP Frame

**What:** Use one fixed-size header plus fixed scalar payload for snapshot/edge frames, followed by HMAC-SHA256 over all bytes before the MAC. [VERIFIED: 04-CONTEXT.md] [CITED: https://www.rfc-editor.org/info/rfc2104/]  
**When to use:** Android-to-desktop high-rate input and motion frames. [VERIFIED: .planning/REQUIREMENTS.md]  
**Recommended layout:** Versioned big-endian fields keep parser logic deterministic and fixture-friendly. [CITED: https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html] [ASSUMED]

| Offset | Size | Field | Notes |
|--------|------|-------|-------|
| 0 | 4 | magic `BTGI` | Rejects wrong datagrams before parse. [ASSUMED] |
| 4 | 1 | version `1` | Enables future schema changes. [VERIFIED: .planning/REQUIREMENTS.md] |
| 5 | 1 | frame type | `1=snapshot`, `2=edge`. [VERIFIED: 04-CONTEXT.md] |
| 6 | 2 | header flags | Reserve bits for future compression/encryption. [ASSUMED] |
| 8 | 16 | stream session id | Random id from trusted control config. [ASSUMED] |
| 24 | 8 | sequence | Monotonic per stream session, all frame types. [VERIFIED: 04-CONTEXT.md] |
| 32 | 8 | capture elapsed nanos | From source gun/motion envelope where available. [VERIFIED: android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt] |
| 40 | 8 | send elapsed nanos | Android `SystemClock.elapsedRealtimeNanos()` at send. [CITED: https://developer.android.com/reference/android/os/SystemClock] |
| 48 | 4 | button bitmask | Trigger, reload, X/Y/A/B, edge flags. [VERIFIED: 04-CONTEXT.md] |
| 52 | 2 | stick X int16 | Normalize float `[-1,1]` to signed int16. [ASSUMED] |
| 54 | 2 | stick Y int16 | Normalize float `[-1,1]` to signed int16. [ASSUMED] |
| 56 | 1 | motion provider | Enum mapping from `MotionProvider.wireName`. [VERIFIED: android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt] |
| 57 | 1 | motion capability flags | Compact bits from `MotionCapabilityFlags`. [VERIFIED: local code grep] |
| 58 | 2 | reserved | Align next floats. [ASSUMED] |
| 60 | 4 | yaw float32 | Raw normalized motion payload. [VERIFIED: 04-CONTEXT.md] |
| 64 | 4 | pitch float32 | Raw normalized motion payload. [VERIFIED: 04-CONTEXT.md] |
| 68 | 4 | roll float32 | Raw normalized motion payload. [VERIFIED: 04-CONTEXT.md] |
| 72 | 4 | raw aim X float32 or NaN | Android preview aim is excluded from product mapping. [VERIFIED: 04-CONTEXT.md] |
| 76 | 4 | raw aim Y float32 or NaN | Android preview aim is excluded from product mapping. [VERIFIED: 04-CONTEXT.md] |
| 80 | 8 | source sensor elapsed nanos | Preserves sensor timestamp provenance. [VERIFIED: android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt] |
| 88 | 32 | HMAC-SHA256 tag | Full tag avoids truncation decisions in MVP. [CITED: https://www.rfc-editor.org/info/rfc2104/] [ASSUMED] |

### Pattern 3: Snapshot Authoritative, Edge Opportunistic

**What:** Android sends 60 Hz snapshots from current state plus immediate edge frames for controls; desktop applies only frames with sequence greater than last applied. [VERIFIED: 04-CONTEXT.md]  
**When to use:** All live input. [VERIFIED: 04-CONTEXT.md]  
**Planning implication:** A late edge frame older than a newer snapshot must be rejected; snapshots repair any dropped edge. [VERIFIED: 04-CONTEXT.md] [ASSUMED]

### Pattern 4: Haptic Command/Result Over Existing WSS

**What:** Phase 4 should allow a valid body for the existing `reserved_haptic_command` wire type and add a `haptic_result` wire type for Android responses. [VERIFIED: 04-CONTEXT.md] [ASSUMED]  
**When to use:** Desktop-origin phone pulse requests and Android ack/fail responses. [VERIFIED: .planning/REQUIREMENTS.md]  
**Planning implication:** If the planner chooses to rename `reserved_haptic_command` to `haptic_command`, it must update the protocol doc and tests as a conscious protocol migration because Phase 4 context says to turn the reserved type into the command. [VERIFIED: 04-CONTEXT.md]

```json
{
  "type": "reserved_haptic_command",
  "body": {
    "commandId": "haptic-0001",
    "strength": 0.75,
    "durationMs": 80,
    "ttlMs": 250,
    "pattern": null
  }
}
```

```json
{
  "type": "haptic_result",
  "body": {
    "commandId": "haptic-0001",
    "status": "started",
    "detail": "one_shot",
    "observedElapsedNanos": 1234567890
  }
}
```

### Anti-Patterns to Avoid

- **Starting UDP from QR parse alone:** QR proves pairing only after control authentication; UDP must wait for trusted WSS `session_ready` plus stream config. [VERIFIED: docs/protocol/lan-pairing-v1.md] [ASSUMED]
- **Using desktop monotonic timestamps as Android deadlines:** `elapsedRealtimeNanos` is monotonic per device, not a shared wall clock; use relative TTL or add explicit clock sync later. [CITED: https://developer.android.com/reference/android/os/SystemClock] [ASSUMED]
- **Applying out-of-order edge frames:** Edge frames are faster hints, not authoritative recovery state; snapshots must dominate after loss. [VERIFIED: 04-CONTEXT.md]
- **Zeroing aim on timeout:** Locked D-13 says clear active buttons only and preserve last-known aim with a stale status. [VERIFIED: 04-CONTEXT.md]
- **Keeping Phase 3 haptic rejection tests unchanged:** Phase 4 must intentionally replace the empty-body-only reserved behavior with the command/result behavior. [VERIFIED: 04-CONTEXT.md] [VERIFIED: local tests]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| UDP message authentication | Custom checksum, XOR, or ad hoc hash | `javax.crypto.Mac` HmacSHA256 | HMAC is the standard keyed message-authentication construction and is available through platform crypto APIs. [CITED: https://www.rfc-editor.org/info/rfc2104/] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |
| Reliable haptic acknowledgement | Custom UDP retry protocol | Existing WSS control channel | Phase 3 already established reliable authenticated control and haptic type space. [VERIFIED: docs/protocol/lan-pairing-v1.md] |
| High-rate input debug format | JSON parser in hot UDP path | Binary frame plus debug decoder | Locked D-01 requires binary UDP for high-rate input. [VERIFIED: 04-CONTEXT.md] |
| Cross-device clock deadline math | Comparing desktop `sentElapsedNanos` to Android `elapsedRealtimeNanos` | Relative `ttlMs` and local receive/dispatch deadline | Monotonic elapsed clocks are local to each device unless a synchronization protocol exists. [CITED: https://developer.android.com/reference/android/os/SystemClock] [ASSUMED] |
| Android vibration compatibility layer from scratch | Direct scattered `Vibrator` calls | Extend existing `PhoneHaptics` wrapper | Current wrapper already centralizes availability, permission, API-level, and failure statuses. [VERIFIED: android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt] |
| WebSocket framing and lifecycle | Raw TCP control frames | Existing Ktor/OkHttp WSS | Current code already handles WSS lifecycle, pinned identity, heartbeat, and message limits. [VERIFIED: local code grep] [CITED: https://ktor.io/docs/server-websockets.html] |

**Key insight:** Phase 4 complexity is state-machine correctness, not library choice; use existing standard APIs and make every transition fixture-testable. [VERIFIED: local code grep] [ASSUMED]

## Common Pitfalls

### Pitfall 1: UDP Key Lifetime Outlives Trusted Session
**What goes wrong:** Desktop accepts packets from an old stream after reconnect or re-pair. [VERIFIED: 04-CONTEXT.md]  
**Why it happens:** UDP is connectionless and can arrive after WSS state changes. [CITED: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html]  
**How to avoid:** Generate a fresh stream session id and HMAC key per trusted control session, reject old stream ids immediately after session change, and allow only the D-15 grace window for unchanged sessions. [VERIFIED: 04-CONTEXT.md] [ASSUMED]  
**Warning signs:** UDP receiver key is stored globally or keyed only by host/port. [ASSUMED]

### Pitfall 2: Haptic TTL Uses the Wrong Clock
**What goes wrong:** Commands falsely expire or play late because desktop and Android elapsed clocks are compared directly. [ASSUMED]  
**Why it happens:** Both sides have monotonic clocks, but their origins are per device. [CITED: https://developer.android.com/reference/android/os/SystemClock]  
**How to avoid:** Treat `ttlMs` as a relative local receive/dispatch budget and reject if Android cannot start before `receivedElapsedNanos + ttlMs`. [ASSUMED]  
**Warning signs:** Haptic schema has only `expiresAtElapsedNanos` from desktop. [ASSUMED]

### Pitfall 3: Replay Guard Breaks Legitimate Snapshot Recovery
**What goes wrong:** A dropped edge leaves a button stuck because snapshots are not treated as authoritative state. [VERIFIED: 04-CONTEXT.md]  
**Why it happens:** Edge and snapshot frames are modeled as independent streams. [ASSUMED]  
**How to avoid:** Use one monotonic UDP sequence across snapshots and edges; apply the newest valid frame, and let snapshots clear/restore full button state. [VERIFIED: 04-CONTEXT.md] [ASSUMED]  
**Warning signs:** Desktop applies edge frames without keeping the latest snapshot state. [ASSUMED]

### Pitfall 4: Receiver Clears Too Much on Timeout
**What goes wrong:** Aim jumps to zero when Wi-Fi stalls. [VERIFIED: 04-CONTEXT.md]  
**Why it happens:** Timeout logic resets the whole input state instead of only pressed controls. [VERIFIED: 04-CONTEXT.md]  
**How to avoid:** Clear pressed controls and active button bits, keep last aim/motion sample, and mark stream stale for downstream UI/backends. [VERIFIED: 04-CONTEXT.md]  
**Warning signs:** Timeout function creates a new all-zero input snapshot. [ASSUMED]

### Pitfall 5: Protocol Drift Between Android and Desktop Codecs
**What goes wrong:** Android encodes fields in one order or endian and desktop decodes a different schema. [ASSUMED]  
**Why it happens:** Existing protocol code is duplicated between Android and desktop modules. [VERIFIED: local code grep]  
**How to avoid:** Write golden UDP frame fixtures before live sender/receiver wiring, and run both Android and desktop codec tests against the same fixture bytes. [VERIFIED: 04-CONTEXT.md] [ASSUMED]  
**Warning signs:** Tests only construct frames in memory and never compare exact bytes. [ASSUMED]

## Code Examples

Verified patterns from official and local sources:

### UDP Frame Authentication Shape

```kotlin
// Source: RFC 2104 HMAC + Java javax.crypto.Mac docs.
fun hmacSha256(key: ByteArray, bytesWithoutTag: ByteArray): ByteArray {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(bytesWithoutTag)
}
```

### Android Haptic Pulse Execution Shape

```kotlin
// Source: Android VibrationEffect docs and existing PhoneHaptics wrapper.
if (android.os.Build.VERSION.SDK_INT >= 26) {
    vibrator.vibrate(
        android.os.VibrationEffect.createOneShot(durationMs, amplitude),
    )
} else {
    @Suppress("DEPRECATION")
    vibrator.vibrate(durationMs)
}
```

### Receiver Timeout State Shape

```kotlin
// Source: Phase 4 D-13.
data class ReceiverInputState(
    val lastAim: MotionPayload?,
    val buttons: Int,
    val stale: Boolean,
)

fun onStreamTimeout(current: ReceiverInputState): ReceiverInputState =
    current.copy(buttons = 0, stale = true)
```

### Haptic TTL Local Deadline Shape

```kotlin
// Source: Android SystemClock docs; Phase 4 D-11.
val receivedAt = android.os.SystemClock.elapsedRealtimeNanos()
val deadline = receivedAt + ttlMs * 1_000_000L
if (android.os.SystemClock.elapsedRealtimeNanos() > deadline) {
    return HapticResult(commandId, "expired")
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Fire-and-forget haptic command | Command id, TTL, ack/fail result, latest-valid-wins cancellation | Locked for Phase 4 on 2026-06-08. [VERIFIED: 04-CONTEXT.md] | Planner must include result statuses and stale-command tests before manual smoke. [VERIFIED: 04-CONTEXT.md] |
| All-input reliable WebSocket/TCP | UDP input plus WSS control/haptics split | Project stack research before Phase 4. [VERIFIED: .planning/research/STACK.md] | Planner must add UDP receiver while preserving WSS trust/liveness. [VERIFIED: .planning/research/ARCHITECTURE.md] |
| Empty `reserved_haptic_command` body | Executable pulse-first haptic body plus result body | Phase 4 boundary. [VERIFIED: docs/protocol/lan-pairing-v1.md] [VERIFIED: 04-CONTEXT.md] | Existing Phase 3 tests must be updated rather than left as rejection gates. [VERIFIED: local tests] |
| Android preview aim as UI-only data | Raw normalized motion forwarded to desktop for future profile mapping | Phase 2 and Phase 4 decisions. [VERIFIED: .planning/STATE.md] [VERIFIED: 04-CONTEXT.md] | UDP frame should exclude product mapped aim and preserve provider/capability flags. [VERIFIED: 04-CONTEXT.md] |

**Deprecated/outdated:**
- `reserved_haptic_command` empty-body-only behavior is Phase 3-only and must be retired or migrated in Phase 4. [VERIFIED: docs/protocol/lan-pairing-v1.md] [VERIFIED: 04-CONTEXT.md]
- JSON for high-rate input frames is explicitly out for Phase 4 UDP input. [VERIFIED: 04-CONTEXT.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Use HMAC-authenticated plaintext UDP instead of AEAD encryption for the MVP. | Standard Stack / Alternatives | If confidentiality is required, planner must add AEAD nonce/key design and more crypto tests. |
| A2 | Desktop sends fresh UDP stream key and stream session id over trusted WSS after `session_ready`. | Architecture Patterns | If user wants key derivation from pairing material, manual reconnect flow needs redesign. |
| A3 | Keep the existing `reserved_haptic_command` wire name as the executable command type in Phase 4. | Architecture Patterns | If user prefers a clean `haptic_command` name, protocol docs and compatibility tests need an explicit migration. |
| A4 | Haptic TTL is relative and evaluated on Android local receive/dispatch time. | Common Pitfalls / Code Examples | If strict desktop-send deadline is required, planner must add clock offset estimation. |
| A5 | Mirrored codecs plus golden fixtures are lower risk than restructuring into a shared Kotlin protocol module in Phase 4. | Standard Stack | If duplication risk is unacceptable, planner must add Gradle/module restructure tasks before codec work. |

## Open Questions

1. **Should UDP input use HMAC-only or full AEAD encryption?**
   - What we know: Requirement allows desktop to validate, decrypt or authenticate frames, and context says authenticated UDP frames. [VERIFIED: .planning/REQUIREMENTS.md] [VERIFIED: 04-CONTEXT.md]
   - What's unclear: Whether local input confidentiality is a product requirement. [ASSUMED]
   - Recommendation: Use HMAC-only for MVP unless user explicitly requires confidentiality. [ASSUMED]

2. **Should the haptic command wire name stay `reserved_haptic_command`?**
   - What we know: Phase 3 reserved that exact type, and Phase 4 says to turn the reserved type into a command. [VERIFIED: docs/protocol/lan-pairing-v1.md] [VERIFIED: 04-CONTEXT.md]
   - What's unclear: Whether readability justifies a protocol rename. [ASSUMED]
   - Recommendation: Keep the wire name for Phase 4, document it as promoted, and add `haptic_result`. [ASSUMED]

3. **What exact frame-age and timeout defaults should the plan lock?**
   - What we know: Visualizer path target is under 50 ms, but Phase 4 only needs stale/replay rejection and timestamp preservation. [VERIFIED: .planning/REQUIREMENTS.md] [VERIFIED: 04-CONTEXT.md]
   - What's unclear: Actual local Wi-Fi jitter on the test setup. [ASSUMED]
   - Recommendation: Start with `frameAgeLimitMs=150`, `streamTimeoutMs=250`, `controlDisconnectGraceMs=1500`, then tune in manual smoke. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java runtime | Desktop tests/app | yes | OpenJDK 26.0.1 from `java -version`. [VERIFIED: shell] | Configure Gradle Java toolchain to target 17 as existing build does. [VERIFIED: desktop-companion/build.gradle.kts] |
| Gradle CLI | Android/desktop tests | present but broken | `gradle --version` fails loading native-platform dylib on this machine. [VERIFIED: shell] | Planner should include an environment repair/checkpoint before relying on Gradle tests. [ASSUMED] |
| Android SDK / adb | Android host manual smoke | yes | adb 36.0.0. [VERIFIED: shell] | Manual device smoke can use installed adb after build environment is healthy. [ASSUMED] |
| Wireshark / tshark | Packet diagnostics | no | `command -v wireshark` and `command -v tshark` returned missing. [VERIFIED: shell] | Use app packet logs and fixture dumps for Phase 4; install packet analyzer only if manual LAN debugging needs it. [ASSUMED] |
| ctx7 | Documentation lookup fallback | no | `command -v ctx7` returned missing. [VERIFIED: shell] | Official docs were checked by web search instead. [VERIFIED: web search] |
| slopcheck | Package legitimacy | no | `command -v slopcheck` returned missing. [VERIFIED: shell] | Not needed because no new packages are proposed. [VERIFIED: research scope] |

**Missing dependencies with no fallback:**
- Gradle is present but currently fails to start, so automated validation is blocked until the local Gradle native-platform issue is repaired. [VERIFIED: shell]

**Missing dependencies with fallback:**
- Wireshark/tshark are missing; Phase 4 can still plan fixture/log validation and reserve packet capture as optional manual debugging. [VERIFIED: shell] [ASSUMED]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Existing Kotlin/JVM and Android unit-test tasks with hand-run `main()` test classes. [VERIFIED: local build.gradle.kts] |
| Config file | `android-host/app/build.gradle.kts`, `desktop-companion/build.gradle.kts`. [VERIFIED: local build.gradle.kts] |
| Quick run command | `gradle test` from `desktop-companion/` for desktop-only transport, once Gradle starts. [VERIFIED: desktop-companion/build.gradle.kts] |
| Full suite command | `gradle test` from `android-host/` and `gradle test` from `desktop-companion/`, once Gradle starts. [VERIFIED: local build.gradle.kts] |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ANDR-07 | Desktop haptic command validates TTL/strength/duration, cancels previous pulse, calls phone haptic wrapper, returns result status. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `gradle test --tests '*DesktopHapticCommandTest*'` from `android-host/`. [ASSUMED] | no, Wave 0 |
| TRAN-04 | Android sends snapshot plus edge UDP frames after trusted stream config. [VERIFIED: 04-CONTEXT.md] | unit + integration fake socket | `gradle test --tests '*AndroidUdpInputSenderTest*'` from `android-host/`. [ASSUMED] | no, Wave 0 |
| TRAN-05 | Binary frame contains required fields and matches golden bytes. [VERIFIED: .planning/REQUIREMENTS.md] | codec fixture | `gradle test --tests '*UdpInputFrameCodecTest*'` in both modules. [ASSUMED] | no, Wave 0 |
| TRAN-07 | Desktop command body contains command id, strength, duration, TTL, optional pattern. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `gradle test --tests '*HapticCommandCodecTest*'` from `desktop-companion/`. [ASSUMED] | no, Wave 0 |
| TRAN-08 | Android sends ack/fail statuses for started/expired/unsupported/permission_blocked/failed/cancelled. [VERIFIED: 04-CONTEXT.md] | unit | `gradle test --tests '*DesktopHapticCommandTest*'` from `android-host/`. [ASSUMED] | no, Wave 0 |
| TRAN-09 | Reconnect/session change cancels haptic and rejects old UDP stream ids. [VERIFIED: 04-CONTEXT.md] | state-machine unit | `gradle test --tests '*InputStreamLifecycleTest*'` in both modules. [ASSUMED] | no, Wave 0 |
| DESK-01 | Desktop verifies HMAC, session id, sequence, age, and parses valid frames. [VERIFIED: .planning/REQUIREMENTS.md] | unit + fake UDP | `gradle test --tests '*UdpInputReceiverTest*'` from `desktop-companion/`. [ASSUMED] | no, Wave 0 |
| PERF-03 | Desktop drops stale/replayed UDP input and does not apply old controls. [VERIFIED: .planning/REQUIREMENTS.md] | replay guard unit | `gradle test --tests '*InputReplayGuardTest*'` from `desktop-companion/`. [ASSUMED] | no, Wave 0 |

### Sampling Rate

- **Per task commit:** Run the smallest changed module test command after Gradle environment repair. [ASSUMED]
- **Per wave merge:** Run both `android-host` and `desktop-companion` `gradle test` commands after Gradle environment repair. [ASSUMED]
- **Phase gate:** Full suite green plus manual Android/desktop LAN smoke for real UDP and haptic pulse. [VERIFIED: .planning/ROADMAP.md] [ASSUMED]

### Wave 0 Gaps

- [ ] `docs/protocol/lan-pairing-v1.md` update for `input_stream_config`, UDP binary frame schema, and haptic result body. [VERIFIED: docs/protocol/lan-pairing-v1.md]
- [ ] `android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt` for golden frame encode/decode. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt` for same fixture bytes. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt` for duplicate/old/wrong-session/age-expired behavior. [ASSUMED]
- [ ] `android-host/app/src/test/java/com/btgun/host/haptics/DesktopHapticCommandTest.kt` for TTL/status/cancel semantics. [ASSUMED]
- [ ] Local Gradle startup repair or documented workaround before any automated test command is considered runnable. [VERIFIED: shell]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Reuse Phase 3 proof-gated WSS session before stream config or haptic command acceptance. [VERIFIED: docs/protocol/lan-pairing-v1.md] |
| V3 Session Management | yes | Fresh stream session id/key per trusted control session, explicit grace window, session-change cleanup. [VERIFIED: 04-CONTEXT.md] [ASSUMED] |
| V4 Access Control | yes | Desktop UDP receiver accepts only expected trusted stream session id and key; Android haptics execute only from authenticated control channel. [VERIFIED: 04-CONTEXT.md] |
| V5 Input Validation | yes | Binary magic/version/length/type checks, JSON control body allowlists, numeric range checks for strength/duration/TTL. [VERIFIED: local code grep] [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html] |
| V6 Cryptography | yes | Platform HMAC-SHA256 for UDP authentication; WSS/TLS remains Phase 3 reliable control protection. [CITED: https://www.rfc-editor.org/info/rfc2104/] [VERIFIED: docs/protocol/lan-pairing-v1.md] |
| V10 Communications | yes | Preserve WSS for reliable control and authenticate UDP frames before parse/apply. [VERIFIED: docs/protocol/lan-pairing-v1.md] [VERIFIED: 04-CONTEXT.md] |

### Known Threat Patterns for Phase 4 Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| LAN UDP injection | Spoofing/Tampering | HMAC every UDP frame with per-session key and reject bad MAC before parsing payload. [CITED: https://www.rfc-editor.org/info/rfc2104/] |
| Replay of old trigger/aim packet | Tampering | Reject wrong-session, duplicate, old sequence, and age-expired frames. [VERIFIED: 04-CONTEXT.md] |
| Stale haptic after disconnect/reconnect | Tampering/Denial of Service | TTL, latest-valid-wins cancellation, session-change cancel, no command queue across new session. [VERIFIED: 04-CONTEXT.md] |
| Oversized control body or UDP datagram | Denial of Service | Keep WSS size limit, fixed UDP frame size, and fail closed on malformed length/version/type. [VERIFIED: docs/protocol/lan-pairing-v1.md] [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html] |
| Secret leakage in diagnostics | Information Disclosure | Reuse Phase 3 redaction rules; never log stream key, HMAC tag inputs, QR secret, or manual code. [VERIFIED: docs/protocol/lan-pairing-v1.md] |

## Sources

### Primary (HIGH confidence)
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - locked decisions for UDP, haptics, replay, timeout, and scope. [VERIFIED: local file]
- `.planning/REQUIREMENTS.md` - Phase 4 requirement IDs and acceptance constraints. [VERIFIED: local file]
- `docs/protocol/lan-pairing-v1.md` - Phase 3 QR/proof/trust/control envelope and reserved haptic boundary. [VERIFIED: local file]
- Android `VibrationEffect` docs - one-shot vibration API. [CITED: https://developer.android.com/reference/android/os/VibrationEffect]
- Android `SystemClock` docs - monotonic elapsed realtime. [CITED: https://developer.android.com/reference/android/os/SystemClock]
- Android `SensorEvent` docs - sensor event timestamp and payload. [CITED: https://developer.android.com/reference/android/hardware/SensorEvent]
- Java `DatagramChannel` docs - UDP datagram channel behavior. [CITED: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html]
- Java `Mac` docs and RFC 2104 - HMAC support and construction. [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] [CITED: https://www.rfc-editor.org/info/rfc2104/]

### Secondary (MEDIUM confidence)
- OWASP WebSocket Security Cheat Sheet - message size, allowlist, and validation guidance for WSS control bodies. [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html]
- RFC 5116 - AEAD interface and nonce-related design context for the alternative encryption path. [CITED: https://www.rfc-editor.org/info/rfc5116/]
- Ktor WebSocket docs and OkHttp WebSocket docs - current framework behavior for existing control channel. [CITED: https://ktor.io/docs/server-websockets.html] [CITED: https://square.github.io/okhttp/5.x/okhttp/okhttp3/-web-socket/]

### Tertiary (LOW confidence)
- No tertiary sources were needed. [VERIFIED: research log]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Phase 4 reuses existing project dependencies and platform APIs; no new package selection is required. [VERIFIED: local build.gradle.kts]
- Architecture: HIGH - Phase 4 context and prior protocol docs strongly constrain UDP/control split, session trust, and haptic semantics. [VERIFIED: 04-CONTEXT.md] [VERIFIED: docs/protocol/lan-pairing-v1.md]
- Pitfalls: HIGH - Pitfalls follow directly from locked replay/haptic/disconnect decisions and current duplicated codec structure. [VERIFIED: 04-CONTEXT.md] [VERIFIED: local code grep]

**Research date:** 2026-06-08  
**Valid until:** 2026-07-08 for architecture; re-check Android/Java docs before changing minSdk, targetSdk, or cryptographic mode. [ASSUMED]
