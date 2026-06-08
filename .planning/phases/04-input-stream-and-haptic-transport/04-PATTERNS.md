# Phase 04: Input Stream and Haptic Transport - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 17 likely new/modified files
**Analogs found:** 17 / 17

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `docs/protocol/lan-pairing-v1.md` | protocol doc | request-response + streaming | `docs/protocol/lan-pairing-v1.md` | exact |
| `docs/protocol/input-stream-v1-fixtures.md` | protocol doc | streaming + transform | `docs/protocol/lan-pairing-v1.md` | role-match |
| `android-host/app/src/main/java/com/btgun/host/transport/InputStreamConfig.kt` | model | request-response | `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt` | utility | transform + streaming | `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` | service | streaming | `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/transport/InputStreamSequencer.kt` | utility | streaming | `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` | exact |
| `android-host/app/src/main/java/com/btgun/host/haptics/DesktopHapticCommand.kt` | service/model | request-response | `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` | exact |
| `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` | protocol codec | request-response | same file + desktop mirror | exact |
| `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` | client | request-response + event-driven | same file | exact |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | foreground service | event-driven + streaming | same file | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamConfig.kt` | model | request-response | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt` | utility | transform + streaming | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` | service | streaming | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt` | utility | streaming + state-machine | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/HeartbeatMonitor.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt` | model/utility | request-response | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlEnvelope.kt` | role-match |
| `android-host/app/src/test/java/com/btgun/host/transport/*Test.kt` | test | transform + streaming | `android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt` | role-match |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/*Test.kt` | test | request-response + streaming | `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` | role-match |

## Pattern Assignments

### Android control client message handling

**Apply to:** `DesktopControlClient.kt`, `InputStreamConfig.kt`, haptic result send path.

**Analog:** `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt`

**Imports/dependencies pattern** (lines 1-15):
```kotlin
import android.os.SystemClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
```

**Authenticated socket lifecycle** (lines 144-153, 170-184):
```kotlin
class DesktopControlClient(
    private val config: DesktopControlClientConfig,
    private val socketFactory: (Request, WebSocketListener) -> DesktopControlSocket = ::defaultSocket,
    private val elapsedRealtimeNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() },
) {
    private var socket: DesktopControlSocket? = null
    private var authenticated: Boolean = false
    private var trustedSessionId: String? = null
```

Copy this constructor injection pattern for testable time/socket dependencies. Add stream-config and haptic callbacks to `connect(...)`, not global singletons.

**Inbound envelope gate** (lines 342-394):
```kotlin
private fun handleServerEnvelope(...): Boolean =
    when (val decoded = ControlEnvelopeCodec.decode(text, maxBytes = config.maxMessageBytes)) {
        is ControlDecodeResult.Rejected -> {
            recordControlError(decoded.error.name.lowercase(Locale.US))
            false
        }
        is ControlDecodeResult.Accepted -> {
            val expectedSessionId = trustedSessionId
            if (expectedSessionId != null && decoded.envelope.sessionId != expectedSessionId) {
                recordControlError("session mismatch")
                false
            } else if (expectedSessionId == null && decoded.envelope.type != ControlMessageType.SESSION_READY) {
                recordControlError("session not ready")
                false
            } else {
                when (decoded.envelope.type) {
                    ControlMessageType.SESSION_READY -> { ... }
                    ControlMessageType.HEARTBEAT_PING -> { ... }
                    ControlMessageType.DIAGNOSTICS -> { ... }
                    ControlMessageType.PROFILE_METADATA -> { ... }
                    else -> false
                }
            }
        }
    }
```

Extend this `when` with `INPUT_STREAM_CONFIG` and `HAPTIC_COMMAND`. Preserve the pre-ready/session mismatch rejection before parsing bodies.

**Send gate** (lines 261-279):
```kotlin
fun send(envelope: ControlEnvelope): DesktopControlSendResult {
    val encoded = ControlEnvelopeCodec.encode(envelope)
    return when (val decoded = ControlEnvelopeCodec.decode(encoded, maxBytes = Int.MAX_VALUE)) {
        is ControlDecodeResult.Rejected -> DesktopControlSendResult.Rejected(decoded.error)
        is ControlDecodeResult.Accepted -> {
            if (encoded.toByteArray(Charsets.UTF_8).size > config.maxMessageBytes) {
                return DesktopControlSendResult.Rejected(ControlEnvelopeError.OVERSIZED)
            }
            if (!authenticated) {
                return DesktopControlSendResult.NotConnected
            }
            val activeSocket = socket ?: return DesktopControlSendResult.NotConnected
            if (activeSocket.send(encoded)) DesktopControlSendResult.Sent else DesktopControlSendResult.Failed("socket rejected message")
        }
    }
}
```

Use this for Android haptic result envelopes: encode/decode self-check, size check, authenticated check, socket send.

### Android foreground service state ownership

**Apply to:** `HostSessionService.kt`, `AndroidUdpInputSender.kt`, haptic session cleanup.

**Analog:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`

**Owned dependencies/state** (lines 62-89):
```kotlin
class HostSessionService : Service() {
    private var adapter: IpegaBleGunAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var desktopControlClient: DesktopControlClient? = null
    private val desktopLivenessCoordinator = DesktopLivenessCoordinator()

    @Volatile
    private var currentState: HostSessionState = HostSessionState()
        set(value) {
            field = value
            latestState = value
        }
```

Add UDP sender and desktop haptic executor as service-owned nullable fields. Route callbacks through `handler.post { if (field !== captured) return@post ... }`.

**Start/stop cleanup pattern** (lines 161-234):
```kotlin
private fun startSession() {
    val gate = permissionGateState()
    if (!canStartWithPermissionGate(gate)) { ... return }
    currentState = HostSessionState(phase = HostSessionPhase.STARTING)
    if (!startHostForegroundSafely()) { stopSelf(); return }
    startMotionCapture()
    adapter = IpegaBleGunAdapter(applicationContext, listener).also { it.startSession() }
    currentState = currentState.copy(phase = HostSessionPhase.SCANNING, foregroundActive = true)
}

private fun stopSession() {
    currentState = currentState.copy(phase = HostSessionPhase.STOPPING)
    stopDesktopControl()
    stopMotionCapture()
    handler.removeCallbacks(reloadHoldTick)
    adapter?.stopSession()
    adapter = null
    currentState = HostSessionState(phase = HostSessionPhase.STOPPED)
    stopForegroundCompat()
    stopSelf()
}
```

UDP streaming starts only inside active foreground session after trusted stream config. Stop stream in `stopSession()` and `stopDesktopControl()`; cancel active phone haptic only on session change, not short disconnect.

**Desktop control replacement pattern** (lines 368-430):
```kotlin
private fun connectDesktopControl(request: DesktopControlConnectionRequest, saveOnSuccess: Boolean) {
    cancelDesktopLivenessTick()
    desktopLivenessCoordinator.stop()
    desktopControlClient?.close()
    desktopControlClient = null
    ...
    val client = DesktopControlClient(request.config)
    val result = client.connect(
        authRequest = request.authRequest,
        onAuthenticated = {
            handler.post {
                if (desktopControlClient !== client) return@post
                ...
                startDesktopLiveness(client)
            }
        },
        onConnectionFailure = { reason ->
            handler.post {
                if (desktopControlClient !== client) return@post
                ...
            }
        },
```

Use the same identity guard for UDP sender/session config and haptic command callbacks.

### Normalized event and motion models

**Apply to:** UDP snapshot/edge source models and binary codec payload fields.

**Analog:** `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt`

**Envelope validation** (lines 5-20):
```kotlin
data class LiveEnvelope<T>(
    val stream: StreamKind,
    val seq: Long,
    val captureElapsedNanos: Long,
    val emittedElapsedNanos: Long,
    val payload: T,
    val provenance: Provenance? = null,
) {
    init {
        require(seq > 0L) { "seq must start at 1" }
        require(captureElapsedNanos >= 0L) { "captureElapsedNanos must be non-negative" }
        require(emittedElapsedNanos >= captureElapsedNanos) {
            "emittedElapsedNanos must be greater than or equal to captureElapsedNanos"
        }
    }
}
```

Copy `require(...)` validation into new config/frame models. Keep elapsed monotonic timestamps; do not add wall-clock fields.

**Sequencer pattern** (lines 28-35):
```kotlin
class StreamSequencer {
    private val nextByStream = mutableMapOf<StreamKind, Long>()

    fun next(stream: StreamKind): Long {
        val next = nextByStream[stream] ?: 1L
        nextByStream[stream] = next + 1L
        return next
    }
}
```

UDP sequence can use one monotonic counter per stream session. If planner chooses separate snapshot/edge counters, test independent counters the same way.

**Gun and motion payload fields** (lines 51-104):
```kotlin
data class GunInputState(
    val pressedControls: Set<String> = emptySet(),
    val stickAxisX: Float = 0f,
    val stickAxisY: Float = 0f,
)

data class MotionSample(
    val provider: MotionProvider,
    val providerName: String = provider.wireName,
    val capabilities: MotionCapabilityFlags = MotionCapabilityFlags(),
    val sourceSensorElapsedNanos: Long,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val rawAimX: Float? = null,
    val rawAimY: Float? = null,
    val aimX: Float? = null,
    val aimY: Float? = null,
)
```

UDP frame source should include `pressedControls`, stick axes, provider/capabilities, yaw/pitch/roll, raw aim, capture/send/source sensor timestamps. Exclude `aimX`/`aimY` as product mapping.

### Control envelope codec pattern

**Apply to:** Android and desktop `ControlEnvelope.kt`, haptic command/result body validation, input stream config body validation.

**Analog:** `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` and desktop mirror.

**Type allowlist and error model** (lines 13-50):
```kotlin
data class ControlEnvelope(..., val body: JsonObject = JsonObject(emptyMap()))

enum class ControlMessageType(val wireName: String) {
    PAIRING_STATE("pairing_state"),
    SESSION_READY("session_ready"),
    HEARTBEAT_PING("heartbeat_ping"),
    HEARTBEAT_PONG("heartbeat_pong"),
    DIAGNOSTICS("diagnostics"),
    PROFILE_METADATA("profile_metadata"),
    RESERVED_HAPTIC_COMMAND("reserved_haptic_command");
}

sealed interface ControlDecodeResult {
    data class Accepted(val envelope: ControlEnvelope) : ControlDecodeResult
    data class Rejected(val error: ControlEnvelopeError, val detail: String? = null) : ControlDecodeResult
}
```

Promote reserved type to concrete `HAPTIC_COMMAND` and add `HAPTIC_RESULT`, `INPUT_STREAM_CONFIG` if needed. Keep mirrored enums identical.

**Strict JSON codec** (lines 52-105):
```kotlin
private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}

fun decode(text: String, maxBytes: Int = DEFAULT_MAX_BYTES): ControlDecodeResult {
    if (text.toByteArray(Charsets.UTF_8).size > maxBytes) {
        return ControlDecodeResult.Rejected(ControlEnvelopeError.OVERSIZED)
    }
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
        return ControlDecodeResult.Rejected(ControlEnvelopeError.MALFORMED, it.message)
    }
    ...
    val body = root["body"] as? JsonObject ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "body")
```

Body validation should reject missing/invalid command id, strength, duration, TTL, session ids, and stream config fields with `INVALID_FIELD`-style errors.

### Android phone haptic wrapper

**Apply to:** `DesktopHapticCommand.kt`, `PhoneHaptics.kt`.

**Analog:** `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt`

**Status factory pattern** (lines 8-48):
```kotlin
data class PhoneHapticStatus(
    val code: String,
    val capability: String,
    val lastLocalTest: String,
) {
    companion object {
        fun available(): PhoneHapticStatus = ...
        fun unavailable(reason: String = "device reports no vibrator"): PhoneHapticStatus = ...
        fun started(durationMs: Long): PhoneHapticStatus = ...
        fun permissionBlocked(): PhoneHapticStatus = ...
        fun failed(error: String): PhoneHapticStatus = ...
    }
}
```

Add desktop-result codes `expired`, `unsupported`, and `cancelled`. Keep user/status strings short and non-secret.

**Platform haptic execution** (lines 51-87):
```kotlin
class PhoneHaptics(context: Context) {
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun test(durationMs: Long = LOCAL_TEST_DURATION_MS): PhoneHapticStatus {
        val localVibrator = vibrator ?: return PhoneHapticStatus.unavailable("missing vibrator service")
        if (!localVibrator.hasVibrator()) return PhoneHapticStatus.unavailable()

        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                localVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                localVibrator.vibrate(durationMs)
            }
            PhoneHapticStatus.started(durationMs)
        } catch (_: SecurityException) {
            PhoneHapticStatus.permissionBlocked()
        } catch (error: RuntimeException) {
            PhoneHapticStatus.failed(error.javaClass.simpleName)
        }
    }
}
```

For Phase 4, add `cancel()` before new valid command and map strength to amplitude on API 26+. Return result immediately after start attempt, not after duration.

### Desktop control server/session/liveness

**Apply to:** `ControlServer.kt`, `UdpInputReceiver.kt`, `InputReplayGuard.kt`, haptic command sender.

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`

**Trusted WSS server and heartbeat** (lines 29-103):
```kotlin
class ControlServer(
    private val registry: PairingSessionRegistry,
    private val maxMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
) {
    var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}
    var onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {}

    fun start(port: Int, host: String = "0.0.0.0"): ControlServer {
        stop()
        ...
        webSocket("/control") {
            onSessionStateChanged(ControlServerSessionState.ANDROID_CONNECTED)
            val trusted = authenticate(headers = call.request.headers, nowEpochMillis = System.currentTimeMillis())
            if (trusted == null) { close(...); return@webSocket }
            onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
            sendSessionReady(trusted)
            sendInitialMetadata(trusted)
            val heartbeat = HeartbeatMonitor()
            ...
        }
    }
}
```

Open/configure UDP receiver only after `trusted` exists. Tie receiver state to trusted `sid` and per-session stream id/key.

**Trusted envelope session gate** (lines 168-178):
```kotlin
fun handleTrustedEnvelope(trustedSession: TrustedPairingSession, text: String): ControlServerResult =
    when (val decoded = ControlEnvelopeCodec.decode(text, maxBytes = maxMessageBytes)) {
        is ControlDecodeResult.Rejected -> ControlServerResult.RejectedEnvelope(decoded.error)
        is ControlDecodeResult.Accepted -> {
            if (decoded.envelope.sessionId != trustedSession.sid) {
                ControlServerResult.RejectedEnvelope(ControlEnvelopeError.INVALID_FIELD)
            } else {
                ControlServerResult.Accepted(decoded.envelope, trustedSession)
            }
        }
    }
```

Use equivalent checks in UDP receiver: wrong session/stream id, duplicate/old sequence, bad MAC, age-expired all reject before applying input.

**Accepted envelope dispatch** (lines 180-202):
```kotlin
suspend fun handleAcceptedEnvelope(
    envelope: ControlEnvelope,
    heartbeat: HeartbeatMonitor,
    nowElapsedNanos: Long = System.nanoTime(),
    sendEnvelope: suspend (ControlEnvelope) -> Unit = {},
) {
    when (envelope.type) {
        ControlMessageType.HEARTBEAT_PING -> { ... }
        ControlMessageType.HEARTBEAT_PONG -> { ... }
        ControlMessageType.DIAGNOSTICS,
        ControlMessageType.PROFILE_METADATA,
        ControlMessageType.PAIRING_STATE,
        ControlMessageType.SESSION_READY,
        -> onControlEnvelopeAccepted(envelope)
        ControlMessageType.RESERVED_HAPTIC_COMMAND -> Unit
    }
}
```

Add haptic result handling here. Desktop-origin haptic command should be built with `sendEnvelope(...)` from trusted session context.

**Liveness utility** (HeartbeatMonitor lines 9-55):
```kotlin
class HeartbeatMonitor(
    private val connectedTimeoutNanos: Long = DEFAULT_CONNECTED_TIMEOUT_NANOS,
    private val disconnectedTimeoutNanos: Long = DEFAULT_DISCONNECTED_TIMEOUT_NANOS,
) {
    init {
        require(connectedTimeoutNanos > 0L)
        require(disconnectedTimeoutNanos > connectedTimeoutNanos)
    }

    fun stateAt(nowElapsedNanos: Long): LivenessState {
        val age = ageNanosAt(nowElapsedNanos) ?: return LivenessState.DISCONNECTED
        return when {
            age <= connectedTimeoutNanos -> LivenessState.CONNECTED
            age <= disconnectedTimeoutNanos -> LivenessState.DEGRADED
            else -> LivenessState.DISCONNECTED
        }
    }
}
```

Copy this small state-machine style for `InputReplayGuard` and stream timeout. On timeout, clear active buttons only; retain last aim with stale state visible.

### Protocol docs style

**Apply to:** `docs/protocol/lan-pairing-v1.md`, optional fixture catalog.

**Analog:** `docs/protocol/lan-pairing-v1.md`

**Reliable control doc style** (lines 101-129):
```markdown
## Reliable Control Channel

The reliable control channel is WebSocket-style over TLS using the pinned desktop SPKI fingerprint...

| Field | Type | Meaning |
|-------|------|---------|
| `v` | integer | Protocol version. Must be `1`. |
...
Allowed `type` wire names:

| Type | Body contract |
|------|---------------|
| `heartbeat_ping` | Empty body. Freshness signal. |
```

Add Phase 4 sections in same style: `input_stream_config`, UDP binary frame layout table with offsets, haptic command body, haptic result body, replay/stale rejection table.

**Boundary wording** (lines 188-190):
```markdown
## Phase Boundary

Phase 3 defines pairing payload fields, proof verification, trust-anchor behavior...
```

Keep phase boundary explicit: no virtual joystick, no desktop profile mapping, no physical gun motor rumble.

### Test and Gradle style

**Apply to:** all new Phase 4 tests and Gradle task lists.

**Android hand-run main test pattern** (`DesktopControlClientTest.kt` lines 10-31):
```kotlin
fun main() {
    envelopeCodecMirrorsDesktopAllowlist()
    envelopeCodecRejectsVersionUnknownTypeOversizedAndReservedHapticBody()
    ...
}
```

**Desktop hand-run main test pattern** (`ControlChannelTest.kt` lines 16-32):
```kotlin
fun main() {
    envelopeCodecAcceptsOnlyVersionOneAndKnownTypes()
    controlServerRejectsControlEnvelopeBeforeProof()
    heartbeatMonitorTransitionsConnectedDegradedDisconnected()
    ...
}
```

**Assertions pattern** (`NormalizedEventEnvelopeTest.kt` lines 73-92):
```kotlin
private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectThrows(label: String, block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalArgumentException) {
        return
    }
    throw AssertionError("$label expected IllegalArgumentException")
}
```

**Gradle task pattern** (`android-host/app/build.gradle.kts` lines 39-70; `desktop-companion/build.gradle.kts` lines 36-60):
```kotlin
tasks.withType<Test>().configureEach {
    val unitTestTask = this
    failOnNoDiscoveredTests = false

    filter {
        isFailOnNoMatchingTests = false
    }

    doLast {
        listOf(
            "com.btgun.host.session.DesktopControlClientTestKt",
        ).forEach { testClass ->
            providers.exec {
                commandLine("java", "-cp", project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath, testClass)
            }.result.get().assertNormalExitValue()
        }
    }
}
```

Add new `*TestKt` classes to these lists; do not introduce JUnit unless the project changes test style intentionally.

## Shared Patterns

### Trust and session gates

**Source:** `ControlServer.handleTrustedEnvelope`, `DesktopControlClient.handleServerEnvelope`
**Apply to:** UDP config, UDP receiver, haptic command/result.

Rules:
- Control messages only after WSS authentication/session_ready.
- Desktop rejects mismatched `sessionId`.
- Android rejects pre-ready non-`SESSION_READY` messages and mismatched session ids.
- UDP trust starts from trusted control config, not QR/manual payload alone.

### Error and validation style

**Source:** `ControlDecodeResult`, `ControlEnvelopeError`, model `require(...)`.
**Apply to:** codecs, configs, replay guard, haptic command models.

Return sealed result objects for parse/validation failure at protocol boundaries. Use `require(...)` for local model invariants. Tests should assert exact rejection reason when behavior matters.

### Time

**Source:** `LiveEnvelope`, `HeartbeatMonitor`, `DesktopControlClient`.
**Apply to:** UDP capture/send/receive timestamps, TTL, replay age, haptic expiry.

Use monotonic elapsed nanos. Android uses `SystemClock.elapsedRealtimeNanos()`. Desktop uses `System.nanoTime()`. Do not compare absolute monotonic values across devices; use relative TTL/frame age at receiver.

### Mirrored protocol code

**Source:** Android and desktop `ControlEnvelope.kt` mirrors plus tests.
**Apply to:** haptic envelopes and UDP codecs if planner keeps mirrored codecs.

Planner must require golden fixture tests in both modules for binary codec compatibility. Keep enum wire names and JSON body fields identical in both copies.

## No Analog Found

All target file roles have usable analogs. No exact UDP socket analog exists yet, so planner should use platform socket APIs from `04-RESEARCH.md` while copying lifecycle, validation, and test patterns from the files above.

## Metadata

**Analog search scope:** `android-host/app/src/main`, `android-host/app/src/test`, `desktop-companion/src/main`, `desktop-companion/src/test`, `docs/protocol`
**Files scanned:** 40+ via `rg --files`
**Pattern extraction date:** 2026-06-08
