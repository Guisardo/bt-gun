# Phase 09: visualizer-acceptance-path - Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 11
**Analogs found:** 11 / 11

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt` | provider | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt` | component | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` | model | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt` | utility | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerPanels.kt` | component | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` | config | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | service | request-response | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/control/VisualizerStatus.kt` | model | request-response | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt` | model | request-response | `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` | service | request-response | `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/Visualizer*Test.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` | exact |

## Pattern Assignments

### `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt` (provider, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt`

**Callback wrapping pattern** (lines 45-58):
```kotlin
fun attach(controlServer: ControlServer) {
    synchronized(lock) {
        require(attachedServer == null) { "WindowsBackendRuntime is already attached" }
        previousUdpCallback = controlServer.onUdpInputReceived
        val callback: (UdpReceivedInput) -> Unit = { input ->
            previousUdpCallback?.invoke(input)
            handleTrustedInput(controlServer, input)
        }
        runtimeUdpCallback = callback
        attachedServer = controlServer
        backend.start()
        updateDiagnosticsLocked(diagnostics.copy(lifecycleState = backend.lifecycleState))
        controlServer.onUdpInputReceived = callback
    }
}
```

**Restore pattern** (lines 64-83):
```kotlin
override fun close() {
    val serverToRestore: ControlServer?
    val previous: ((UdpReceivedInput) -> Unit)?
    val runtimeCallback: ((UdpReceivedInput) -> Unit)?
    synchronized(lock) {
        serverToRestore = attachedServer
        previous = previousUdpCallback
        runtimeCallback = runtimeUdpCallback
        attachedServer = null
        previousUdpCallback = null
        runtimeUdpCallback = null
    }
    val server = serverToRestore
    if (server != null && server.onUdpInputReceived === runtimeCallback) {
        server.onUdpInputReceived = previous ?: {}
    }
    backend.stop("windows backend runtime closed")
    synchronized(lock) {
        updateDiagnosticsLocked(diagnostics.copy(lifecycleState = backend.lifecycleState))
    }
}
```

Planner note: Phase 9 should prefer a real fanout hub over more single-callback wrapping. Preserve existing callbacks for `PairingWindow`, Windows runtime, macOS runtime, and new visualizer listeners.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt` (component, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt`

**Imports pattern** (lines 3-39):
```kotlin
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.transport.InputStreamLifecycleState
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
```

**EDT callback pattern** (lines 81-118):
```kotlin
controlServer.onSessionStateChanged = { serverState ->
    SwingUtilities.invokeLater {
        applyServerState(serverState)
    }
}
controlServer.onUdpInputStateChanged = { streamState ->
    SwingUtilities.invokeLater {
        packetStreamState = streamState
        updateDiagnostics()
    }
}
controlServer.onUdpInputReceived = { input ->
    SwingUtilities.invokeLater {
        lastMappedProductStream = input.mappedProductStream
        lastRawDebugEnabled = input.rawDebugEnabled
        updateDiagnostics()
    }
}
controlServer.onHapticResultReceived = { result ->
    SwingUtilities.invokeLater {
        lastHapticStatus = hapticStatusText(result)
        updateDiagnostics()
    }
}
```

**Layout pattern** (lines 165-193):
```kotlin
private fun content(): JPanel {
    val root = JPanel(BorderLayout(24, 24))
    root.border = BorderFactory.createEmptyBorder(32, 32, 32, 32)

    val header = JPanel(GridLayout(3, 1, 0, 8))
    header.add(title)
    header.add(state)
    header.add(endpoint)

    val primary = JPanel()
    primary.layout = BoxLayout(primary, BoxLayout.Y_AXIS)
    primary.add(qr)
    primary.add(Box.createVerticalStrut(8))
    primary.add(countdown)

    val side = JPanel()
    side.layout = BoxLayout(side, BoxLayout.Y_AXIS)
    side.add(manual)
    side.add(Box.createVerticalStrut(16))
    side.add(diagnostics)
    side.add(Box.createVerticalStrut(16))
    side.add(action)
    side.add(Box.createVerticalStrut(8))
    side.add(hapticAction)

    root.add(header, BorderLayout.NORTH)
    root.add(primary, BorderLayout.CENTER)
    root.add(side, BorderLayout.EAST)
    return root
}
```

**Haptic action pattern** (lines 278-287):
```kotlin
private fun sendTestHaptic() {
    val command = smokeHapticCommand("ui-test-${System.nanoTime()}")
    lastHapticStatus = when (val result = controlServer.sendHapticCommand(command)) {
        HapticSendResult.Sent -> "queued: ${command.commandId}"
        HapticSendResult.NoActiveSession -> "not connected"
        is HapticSendResult.Rejected -> "rejected: ${result.error.name.lowercase()}"
        is HapticSendResult.Failed -> "failed: ${result.reason}"
    }
    updateDiagnostics()
}
```

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` (model, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt`

**Immutable state pattern** (lines 3-16):
```kotlin
data class SemanticControllerState(
    val trigger: Boolean = false,
    val reload: Boolean = false,
    val x: Boolean = false,
    val y: Boolean = false,
    val a: Boolean = false,
    val b: Boolean = false,
    val stickX: Int = 0,
    val stickY: Int = 0,
    val aimX: Float = 0.0f,
    val aimY: Float = 0.0f,
    val stale: Boolean = false,
    val sourceSequence: Long? = null,
)
```

**Display-ready input boundary** (from `UdpControllerStateAdapter.kt`, lines 5-27):
```kotlin
object UdpControllerStateAdapter {
    fun toState(input: UdpReceivedInput): SemanticControllerState =
        if (!input.mappedProductStream) {
            SemanticControllerState(
                stale = true,
                sourceSequence = input.lastAcceptedSequence,
            )
        } else {
            SemanticControllerState(
                trigger = "trigger" in input.pressedControls,
                reload = "reload" in input.pressedControls,
                x = "x" in input.pressedControls,
                y = "y" in input.pressedControls,
                a = "a" in input.pressedControls,
                b = "b" in input.pressedControls,
                stickX = input.stickX,
                stickY = input.stickY,
                aimX = input.mappedAim.aimX.neutralIfNaN(),
                aimY = input.mappedAim.aimY.neutralIfNaN(),
                stale = input.stale,
                sourceSequence = input.lastAcceptedSequence,
            )
        }
}
```

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt` (utility, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt`

**Input timestamp and sequence fields** (lines 19-36):
```kotlin
data class UdpReceivedInput(
    val controlSessionId: String,
    val streamSessionIdHex: String,
    val frameType: UdpInputFrameType,
    val buttons: Int,
    val pressedControls: Set<String>,
    val stickX: Int,
    val stickY: Int,
    val motion: UdpReceivedMotion,
    val mappedAim: UdpReceivedMappedAim = UdpReceivedMappedAim(motion.rawAimX, motion.rawAimY),
    val mappedProductStream: Boolean = true,
    val rawDebugEnabled: Boolean = false,
    val captureElapsedNanos: Long,
    val sendElapsedNanos: Long,
    val receivedElapsedNanos: Long,
    val stale: Boolean,
    val lastAcceptedSequence: Long,
)
```

**Frame conversion pattern** (lines 38-71):
```kotlin
internal fun UdpInputFrame.toReceivedInput(
    controlSessionId: String,
    receivedElapsedNanos: Long,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = controlSessionId,
        streamSessionIdHex = streamSessionId,
        frameType = type,
        buttons = buttonBitmask,
        pressedControls = pressedControlsFrom(buttonBitmask),
        stickX = stickX,
        stickY = stickY,
        motion = UdpReceivedMotion(
            provider = motionProvider,
            capabilityFlags = motionCapabilityFlags,
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            rawAimX = rawAimX,
            rawAimY = rawAimY,
            sourceSensorElapsedNanos = sourceSensorElapsedNanos,
        ),
        mappedAim = UdpReceivedMappedAim(
            aimX = productAimX,
            aimY = productAimY,
        ),
        mappedProductStream = mappedProductStream,
        rawDebugEnabled = rawDebugEnabled,
        captureElapsedNanos = captureElapsedNanos,
        sendElapsedNanos = sendElapsedNanos,
        receivedElapsedNanos = receivedElapsedNanos,
        stale = false,
        lastAcceptedSequence = sequence,
    )
```

Planner note: never compute `System.nanoTime() - input.captureElapsedNanos` directly. Phase research says Android `elapsedRealtimeNanos` and JVM `System.nanoTime` need an offset estimate.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerPanels.kt` (component, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt`

**Stable component sizing and borders** (lines 72-79):
```kotlin
title.font = title.font.deriveFont(Font.BOLD, 22f)
qr.preferredSize = Dimension(QR_SIZE, QR_SIZE)
qr.minimumSize = Dimension(QR_SIZE, QR_SIZE)
manual.verticalAlignment = SwingConstants.TOP
manual.border = BorderFactory.createTitledBorder("Manual fallback")
diagnostics.verticalAlignment = SwingConstants.TOP
diagnostics.border = BorderFactory.createTitledBorder("Control state")
```

**Timer refresh pattern** (lines 156-158):
```kotlin
Timer(1_000) {
    refreshSession()
}.start()
```

Planner note: custom crosshair panels should follow Swing `JPanel` conventions and repaint from immutable snapshots. Keep raw debug in collapsed/default-secondary UI per context D-12.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` (config, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt`

**Swing launch pattern** (lines 13-17):
```kotlin
fun main() {
    SwingUtilities.invokeLater {
        createPairingWindow().show()
    }
}
```

**Dependency wiring pattern** (lines 19-32):
```kotlin
private fun createPairingWindow(): PairingWindow {
    val identityStore = DesktopIdentityStore.default()
    val registry = PairingSessionRegistry(identityStore = identityStore)
    val controlServer = ControlServer(registry = registry)
    val windowsBackendLaunch = createWindowsBackendLaunch()
    val macosBackendLaunch = createMacosBackendLaunch()
    return PairingWindow(
        registry = registry,
        controlServer = controlServer,
        windowsBackendRuntime = windowsBackendLaunch.runtime,
        windowsBackendStartupDiagnostic = windowsBackendLaunch.diagnostic,
        macosBackendRuntime = macosBackendLaunch.runtime,
        macosBackendStartupDiagnostic = macosBackendLaunch.diagnostic,
    )
}
```

Planner note: add visualizer/event-hub dependencies here or inside a small UI coordinator; keep `main()` EDT-only.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` (service, request-response)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`

**Callback surface** (lines 55-61):
```kotlin
var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}
var onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {}
var onProfileMetadataReceived: (ProfileMetadata) -> Unit = {}
var onUdpInputReceived: (UdpReceivedInput) -> Unit = {}
var onUdpInputRejected: (InputReplayRejectReason) -> Unit = {}
var onUdpInputStateChanged: (InputStreamLifecycleState) -> Unit = {}
var onHapticResultReceived: (HapticResult) -> Unit = {}
```

**Accepted envelope routing** (lines 258-287):
```kotlin
suspend fun handleAcceptedEnvelope(
    envelope: ControlEnvelope,
    heartbeat: HeartbeatMonitor,
    nowElapsedNanos: Long = System.nanoTime(),
    controlSessionToken: String? = null,
    sendEnvelope: suspend (ControlEnvelope) -> Unit = {},
) {
    when (envelope.type) {
        ControlMessageType.HEARTBEAT_PING -> {
            heartbeat.observePing(nowElapsedNanos)
            updateSessionState(heartbeat.stateAt(nowElapsedNanos), controlSessionToken, nowElapsedNanos)
            sendEnvelope(heartbeatEnvelope(ControlMessageType.HEARTBEAT_PONG, envelope.sessionId))
        }
        ControlMessageType.HEARTBEAT_PONG -> {
            heartbeat.observePong(nowElapsedNanos)
            updateSessionState(heartbeat.stateAt(nowElapsedNanos), controlSessionToken, nowElapsedNanos)
        }
        ControlMessageType.DIAGNOSTICS,
        ControlMessageType.PAIRING_STATE,
        ControlMessageType.SESSION_READY,
        ControlMessageType.INPUT_STREAM_CONFIG,
        -> onControlEnvelopeAccepted(envelope)
        ControlMessageType.PROFILE_METADATA -> {
            profileMetadataFromJsonBody(envelope.body)?.let { metadata ->
                onProfileMetadataReceived(metadata)
                onControlEnvelopeAccepted(envelope)
            }
        }
        ControlMessageType.HAPTIC_RESULT -> handleHapticResult(envelope, controlSessionToken)
        ControlMessageType.RESERVED_HAPTIC_COMMAND -> Unit
    }
}
```

**Haptic send path** (lines 340-374):
```kotlin
fun sendHapticCommand(
    command: HapticCommand,
    nowElapsedNanos: Long = System.nanoTime(),
): HapticSendResult {
    val active = synchronized(stateLock) { activeControlSession }
        ?: return HapticSendResult.NoActiveSession
    val envelope = hapticCommandEnvelopeFor(active.trustedSession, command, nowElapsedNanos)
    val encoded = ControlEnvelopeCodec.encode(envelope)
    when (val decoded = ControlEnvelopeCodec.decode(encoded, maxBytes = Int.MAX_VALUE)) {
        is ControlDecodeResult.Rejected -> return HapticSendResult.Rejected(decoded.error)
        is ControlDecodeResult.Accepted -> Unit
    }
    if (encoded.toByteArray(Charsets.UTF_8).size > maxMessageBytes) {
        return HapticSendResult.Rejected(ControlEnvelopeError.OVERSIZED)
    }
    val stillActive = synchronized(stateLock) {
        if (activeControlSession?.token == active.token) {
            pendingHapticCommandIds.add(command.commandId)
            true
        } else {
            false
        }
    }
    if (!stillActive) {
        return HapticSendResult.NoActiveSession
    }
    return if (active.outbound.trySend(envelope).isSuccess) {
        HapticSendResult.Sent
    } else {
        synchronized(stateLock) {
            pendingHapticCommandIds.remove(command.commandId)
        }
        HapticSendResult.Failed("active control socket rejected haptic command")
    }
}
```

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/control/VisualizerStatus.kt` (model, request-response)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt`

**Sanitized model and parser pattern** (lines 10-29):
```kotlin
data class ProfileMetadata(
    val profileId: String,
    val displayName: String,
    val revision: Long,
    val source: String,
    val rawDebugEnabled: Boolean = false,
)

internal fun profileMetadataFromJsonBody(body: JsonObject): ProfileMetadata? {
    val profileId = body.stringField("profileId")?.takeIf(String::isNotBlank) ?: return null
    val displayName = body.stringField("displayName")?.takeIf(String::isNotBlank) ?: return null
    val revision = body.longField("revision")?.takeIf { it >= 0L } ?: return null
    val source = body.stringField("source")?.takeIf { it == "android" } ?: return null
    return ProfileMetadata(
        profileId = profileId,
        displayName = displayName,
        revision = revision,
        source = source,
        rawDebugEnabled = body.booleanField("rawDebugEnabled") ?: false,
    )
}
```

**Current diagnostics boundary** (from `ControlDiagnostics.kt`, lines 3-8):
```kotlin
data class ControlDiagnostics(
    val sessionState: String,
    val desktopIdentitySuffix: String,
    val heartbeatAgeMillis: Long?,
    val lastControlError: String?,
)
```

Planner note: add only sanitized visualizer fields: recenter event/status, aim-zero/baseline label, Android time-sync sample if needed. Do not include secrets, raw packet bytes, full device IDs, HMAC keys, or screenshots.

---

### `android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt` (model, request-response)

**Analog:** `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt`

**Control envelope wire type pattern** (lines 13-32):
```kotlin
data class ControlEnvelope(
    val v: Int,
    val type: ControlMessageType,
    val msgId: String,
    val sessionId: String,
    val seq: Long,
    val sentElapsedNanos: Long,
    val body: JsonObject = JsonObject(emptyMap()),
)

enum class ControlMessageType(val wireName: String) {
    PAIRING_STATE("pairing_state"),
    SESSION_READY("session_ready"),
    HEARTBEAT_PING("heartbeat_ping"),
    HEARTBEAT_PONG("heartbeat_pong"),
    DIAGNOSTICS("diagnostics"),
    PROFILE_METADATA("profile_metadata"),
    INPUT_STREAM_CONFIG("input_stream_config"),
    RESERVED_HAPTIC_COMMAND("reserved_haptic_command"),
    HAPTIC_RESULT("haptic_result");
}
```

**Codec validation pattern** (lines 75-103):
```kotlin
fun decode(text: String, maxBytes: Int = DEFAULT_MAX_BYTES): ControlDecodeResult {
    if (text.toByteArray(Charsets.UTF_8).size > maxBytes) {
        return ControlDecodeResult.Rejected(ControlEnvelopeError.OVERSIZED)
    }
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
        return ControlDecodeResult.Rejected(ControlEnvelopeError.MALFORMED, it.message)
    }

    val version = root.intField("v") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "v")
    if (version != VERSION) {
        return ControlDecodeResult.Rejected(ControlEnvelopeError.UNSUPPORTED_VERSION)
    }
    val typeWireName = root.stringField("type") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "type")
    val type = ControlMessageType.fromWireName(typeWireName)
        ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.UNKNOWN_TYPE)
    val body = root["body"] as? JsonObject ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "body")
    return ControlDecodeResult.Accepted(
        ControlEnvelope(
            v = version,
            type = type,
            msgId = root.stringField("msgId") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "msgId"),
            sessionId = root.stringField("sessionId") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "sessionId"),
            seq = root.longField("seq") ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "seq"),
            sentElapsedNanos = root.longField("sentElapsedNanos")
                ?: return ControlDecodeResult.Rejected(ControlEnvelopeError.INVALID_FIELD, "sentElapsedNanos"),
            body = body,
        ),
    )
}
```

---

### `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` (service, request-response)

**Analog:** `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt`

**Inbound control handling pattern** (lines 431-465):
```kotlin
ControlMessageType.DIAGNOSTICS -> {
    decoded.envelope.body.toDiagnostics()?.let { diagnostics ->
        applyDiagnostics(diagnostics)
        onLinkStateChanged(linkState)
    }
    false
}
ControlMessageType.PROFILE_METADATA -> {
    decoded.envelope.body.toProfileMetadata()?.let(onProfileMetadataReceived)
    false
}
ControlMessageType.INPUT_STREAM_CONFIG -> {
    val streamConfig = decoded.envelope.body.toInputStreamConfig()
    if (streamConfig == null) {
        recordControlError("invalid input stream config")
    } else {
        onInputStreamConfigReceived(streamConfig)
    }
    false
}
ControlMessageType.RESERVED_HAPTIC_COMMAND -> {
    val observedElapsedNanos = elapsedRealtimeNanos()
    val command = DesktopHapticCommand.fromJsonBody(decoded.envelope.body)
    val result = if (command == null) {
        HapticResult(
            commandId = decoded.envelope.body.stringField("commandId")?.ifBlank { "invalid" } ?: "invalid",
            status = HapticResultStatus.FAILED,
            detail = "invalid haptic command",
            observedElapsedNanos = observedElapsedNanos,
        )
    } else {
        onHapticCommandReceived(command, observedElapsedNanos)
    }
    send(resultEnvelope(decoded.envelope.sessionId, result))
    false
}
```

**Outbound metadata envelope pattern** (lines 494-511):
```kotlin
private fun profileMetadataEnvelope(sessionId: String, profile: ProfileMetadata): ControlEnvelope =
    ControlEnvelope(
        v = 1,
        type = ControlMessageType.PROFILE_METADATA,
        msgId = "android-profile-${profile.profileId}-${profile.revision}",
        sessionId = sessionId,
        seq = 0L,
        sentElapsedNanos = elapsedRealtimeNanos(),
        body = JsonObject(
            mapOf(
                "profileId" to JsonPrimitive(profile.profileId),
                "displayName" to JsonPrimitive(profile.displayName),
                "revision" to JsonPrimitive(profile.revision),
                "source" to JsonPrimitive(profile.source),
                "rawDebugEnabled" to JsonPrimitive(profile.rawDebugEnabled),
            ),
        ),
    )
```

**Recenter source pattern** (from `ReloadHoldRecenter.kt`, lines 65-89):
```kotlin
fun onTick(nowElapsedNanos: Long): List<LiveEnvelope<StatusEvent>> {
    val pressedAt = state.pressedElapsedNanos
    if (!state.isReloadHeld || pressedAt == null) {
        return emptyList()
    }

    val emitted = mutableListOf<LiveEnvelope<StatusEvent>>()
    val heldNanos = nowElapsedNanos - pressedAt
    if (!state.recenterEmitted && heldNanos >= RELOAD_HOLD_NANOS) {
        state = state.copy(recenterEmitted = true)
        emitted += statusEvent(
            nowElapsedNanos = nowElapsedNanos,
            name = RECENTER_EVENT_NAME,
            label = RECENTER_STATUS_LABEL,
        )
    }
    if (!state.calibrationEmitted && heldNanos >= AIM_CALIBRATION_HOLD_NANOS) {
        state = state.copy(calibrationEmitted = true)
        emitted += statusEvent(
            nowElapsedNanos = nowElapsedNanos,
            name = AIM_CALIBRATION_EVENT_NAME,
            label = AIM_CALIBRATION_STATUS_LABEL,
        )
    }
    return emitted
}
```

**Host service recenter state update** (from `HostSessionService.kt`, lines 1288-1316):
```kotlin
private fun handleReloadHoldStatus(envelope: LiveEnvelope<StatusEvent>) {
    var nextState = currentState.copy(
        lastStatusEvent = envelope,
        reloadHoldState = recenter.state,
    )
    when (envelope.payload.name) {
        ReloadHoldRecenter.RECENTER_EVENT_NAME -> {
            val lastMotion = currentState.lastMotionSample?.payload
            if (lastMotion != null) {
                currentAimBaseline = AimBaseline(
                    yaw = lastMotion.yaw,
                    pitch = lastMotion.pitch,
                    roll = lastMotion.roll,
                    elapsedNanos = envelope.payload.baselineElapsedNanos ?: envelope.captureElapsedNanos,
                )
            }
            currentRawAim?.let { raw -> currentRawOrigin = raw }
            nextState = nextState.copy(
                lastRecenterStatus = envelope,
                aimBaseline = currentAimBaseline,
            )
        }
        ReloadHoldRecenter.AIM_CALIBRATION_EVENT_NAME -> {
            startAimCalibration()
            nextState = nextState.copy(aimCalibrationState = aimCalibrationSession.state)
        }
    }
    currentState = nextState.copy(aimCalibrationState = aimCalibrationSession.state)
}
```

---

### `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/Visualizer*Test.kt` (test, transform)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt`

**Plain test entrypoint pattern** (lines 14-21):
```kotlin
fun main() {
    pairingWindowExposesOnlyConciseTransportStateLabels()
    pairingWindowExposesHapticSmokeStateWithoutLaunchingSwing()
    pairingWindowFormatsMacosBackendDiagnostics()
    pairingWindowFormatsReadOnlyAndroidProfileDiagnostics()
    pairingWindowFreshProfileDiagnosticsAreNeutral()
    pairingWindowForbiddenDesktopProfileControlsAbsent()
}
```

**UI helper test pattern** (lines 37-59):
```kotlin
private fun pairingWindowExposesHapticSmokeStateWithoutLaunchingSwing() {
    val command = PairingWindow.smokeHapticCommand("cmd-ui")

    expectEquals("command id", "cmd-ui", command.commandId)
    expectEquals("strength", 0.6, command.strength)
    expectEquals("duration", 80L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectTrue("auth enables haptic", PairingWindow.hapticButtonEnabled(DesktopSessionUiState.AUTHENTICATED, serverAuthenticated = true))
    expectFalse("registry alone does not enable haptic", PairingWindow.hapticButtonEnabled(DesktopSessionUiState.AUTHENTICATED, serverAuthenticated = false))
    expectFalse("disconnect disables haptic", PairingWindow.hapticButtonEnabled(DesktopSessionUiState.DISCONNECTED))
    expectContains(
        "haptic result status",
        PairingWindow.hapticStatusText(
            HapticResult(
                commandId = "cmd-ui",
                status = HapticResultStatus.STARTED,
                detail = "phone pulse started",
                observedElapsedNanos = 10L,
            ),
        ),
        "started: phone pulse started",
    )
}
```

**Forbidden text/security test pattern** (lines 141-158):
```kotlin
private fun pairingWindowForbiddenDesktopProfileControlsAbsent() {
    val source = File("src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()
    val forbidden = listOf(
        "Edit desktop " + "profile",
        "Desktop profile " + "editor",
        "Request raw " + "stream",
        "Save " + "profile",
        "Duplicate " + "profile",
        "Hold-to-" + "recenter",
    )

    forbidden.forEach { label ->
        expectFalse("forbidden desktop control absent: $label", source.contains(label, ignoreCase = true))
    }
}
```

**Callback-preservation test pattern** (from `WindowsBackendRuntimeTest.kt`, lines 59-76):
```kotlin
private fun runtimePreservesExistingUdpCallbackWhenAttached() {
    val bridge = RuntimeFakeWindowsDriverBridge()
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = WindowsVirtualControllerBackend(bridge = bridge),
        nowElapsedNanos = { 3_000_000_000L },
    )
    val server = ControlServer(registry = testRegistry())
    val observed = mutableListOf<UdpReceivedInput>()
    val input = receivedInput(sequence = 43L)
    server.onUdpInputReceived = observed::add

    runtime.attach(server)
    server.onUdpInputReceived(input)

    expectEquals("existing callback kept", listOf(input), observed)
    expectEquals("runtime still published", 1, bridge.submitted.size)
}
```

## Shared Patterns

### Swing EDT
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt`
**Apply to:** `VisualizerWindow.kt`, `VisualizerPanels.kt`, visualizer launch changes
```kotlin
controlServer.onUdpInputReceived = { input ->
    SwingUtilities.invokeLater {
        lastMappedProductStream = input.mappedProductStream
        lastRawDebugEnabled = input.rawDebugEnabled
        updateDiagnostics()
    }
}
```

### Callback Fanout
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`
**Apply to:** `DesktopUiEventHub.kt`, `PairingWindow.kt`, backend runtime attachment
```kotlin
var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}
var onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {}
var onProfileMetadataReceived: (ProfileMetadata) -> Unit = {}
var onUdpInputReceived: (UdpReceivedInput) -> Unit = {}
var onUdpInputRejected: (InputReplayRejectReason) -> Unit = {}
var onUdpInputStateChanged: (InputStreamLifecycleState) -> Unit = {}
var onHapticResultReceived: (HapticResult) -> Unit = {}
```

### Semantic Input State
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt`
**Apply to:** `VisualizerModel.kt`, `VisualizerWindow.kt`, `VisualizerPanels.kt`
```kotlin
SemanticControllerState(
    trigger = "trigger" in input.pressedControls,
    reload = "reload" in input.pressedControls,
    x = "x" in input.pressedControls,
    y = "y" in input.pressedControls,
    a = "a" in input.pressedControls,
    b = "b" in input.pressedControls,
    stickX = input.stickX,
    stickY = input.stickY,
    aimX = input.mappedAim.aimX.neutralIfNaN(),
    aimY = input.mappedAim.aimY.neutralIfNaN(),
    stale = input.stale,
    sourceSequence = input.lastAcceptedSequence,
)
```

### Authenticated Haptics
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`
**Apply to:** `VisualizerWindow.kt`, checklist haptic rows, Windows output haptic proof
```kotlin
val active = synchronized(stateLock) { activeControlSession }
    ?: return HapticSendResult.NoActiveSession
val envelope = hapticCommandEnvelopeFor(active.trustedSession, command, nowElapsedNanos)
val encoded = ControlEnvelopeCodec.encode(envelope)
```

### Sanitized Diagnostics
**Source:** `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt`
**Apply to:** `VisualizerStatus.kt`, checklist/status display, tests
```kotlin
expectEquals(
    "diagnostic fields",
    listOf("sessionState", "desktopIdentitySuffix", "heartbeatAgeMillis", "lastControlError"),
    dataFieldNames(ControlDiagnostics::class.java),
)
```

### Android Recenter Source
**Source:** `android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt`
**Apply to:** Android visualizer status payload and desktop recenter row
```kotlin
if (!state.recenterEmitted && heldNanos >= RELOAD_HOLD_NANOS) {
    state = state.copy(recenterEmitted = true)
    emitted += statusEvent(
        nowElapsedNanos = nowElapsedNanos,
        name = RECENTER_EVENT_NAME,
        label = RECENTER_STATUS_LABEL,
    )
}
```

## No Analog Found

None. Every expected new or modified file has a usable project analog. The only missing direct analog is custom crosshair painting; planner should still keep it in Swing `JPanel` components and test model/math outside live Swing.

## Metadata

**Analog search scope:** `desktop-companion/src/main/kotlin`, `desktop-companion/src/test/kotlin`, `android-host/app/src/main/java`
**Files scanned:** 75+
**Pattern extraction date:** 2026-06-12
