# Phase 09: visualizer-acceptance-path - Research

**Researched:** 2026-06-12
**Domain:** Kotlin/JVM Swing acceptance visualizer, authenticated LAN input metrics, OS-visible controller proof, phone haptic proof
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

Source for all copied constraints in this block: [VERIFIED: `.planning/phases/09-visualizer-acceptance-path/09-CONTEXT.md`]

### Locked Decisions
## Implementation Decisions

### Acceptance Path
- **D-01:** Phase 9 pass gate is a guided both-path acceptance flow. The visualizer must prove the LAN visualizer path plus macOS Android Bluetooth HID input plus Windows VHF input.
- **D-02:** Both OS-visible paths are required for Phase 9 acceptance: macOS through Android Bluetooth HID and Windows through the completed VHF path. Do not treat either one alone as sufficient.
- **D-03:** Haptic proof uses per-platform rows. The authenticated LAN visualizer haptic path must vibrate the Android phone. The Windows VHF output-to-phone haptic path must also pass. The macOS Bluetooth HID output/haptic row may pass as unsupported/deferred when it shows current evidence for the known limitation.
- **D-04:** Final Phase 9 acceptance is recorded as a guided manual checklist in the visualizer. Do not make a generated evidence bundle the primary pass artifact in this phase.

### Visualizer Surface
- **D-05:** Add a separate Swing `VisualizerWindow` instead of turning `PairingWindow` into the main visualizer.
- **D-06:** The visualizer auto-opens when Android reaches an authenticated control session.
- **D-07:** `PairingWindow` also provides a visible manual reopen button for the visualizer.
- **D-08:** The visualizer remains open across disconnects and shows stale or disconnected state instead of closing or clearing context.

### Live Display Shape
- **D-09:** The top of the visualizer shows the guided checklist and live gamepad panel together.
- **D-10:** The primary control display is gamepad-like: six button indicators, stick crosshair, aim crosshair, and stale overlay.
- **D-11:** Recenter and recent history show as a current recenter/aim-zero status row plus a short event strip with the last 10 product events.
- **D-12:** Raw debug state is visible as on/off status. Raw provider, yaw, pitch, roll, and raw aim values live in a collapsed debug drawer when Android raw debug is enabled.

### Metrics and Haptic Proof
- **D-13:** The headline latency metric is Android capture timestamp to Swing visualizer update/render, with the v1 target under 50 ms.
- **D-14:** Packet loss display is a simple current-session expected/missed counter and percent derived from accepted UDP sequence gaps.
- **D-15:** The visualizer haptic test flow includes an authenticated LAN phone pulse and then prompts or guides the Windows VHF output-to-phone haptic proof.
- **D-16:** Checklist rows pass through observed live state when possible plus user confirmation for physical, macOS, Windows, and haptic observations.

### the agent's Discretion
- Choose exact Swing component structure, layout managers, rendering cadence, update throttling, checklist row ids, and row labels as long as the decisions above stay true.
- Choose exact visual styling for indicators, crosshairs, stale overlays, and event strip within existing Swing constraints.
- Choose exact internal metrics classes and callback names, provided latency uses Android capture to visualizer update/render for the headline and packet loss uses accepted UDP sequence gaps.
- Choose whether supporting values such as capture-to-send, send-to-receive, receive-to-render, rejection reasons, and raw debug values appear in secondary diagnostics, but do not make them the primary acceptance metric.

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within Phase 9 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| VIS-01 | User can open a simple joystick visualizer that connects to the desktop companion pipeline. | Add separate Swing `VisualizerWindow`, auto-open on authenticated `ControlServerSessionState.AUTHENTICATED`, and manual reopen from `PairingWindow`. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`; VERIFIED: `ControlServer.kt`] |
| VIS-02 | Visualizer displays trigger, reload, joystick, X/Y/A/B, and aim axes in real time. | Use `UdpReceivedInput` plus `UdpControllerStateAdapter` / `SemanticControllerState`; render six button indicators, stick crosshair, and aim crosshair from accepted mapped UDP frames. [VERIFIED: `UdpReceivedInput.kt`; VERIFIED: `UdpControllerStateAdapter.kt`; VERIFIED: `SemanticControllerState.kt`] |
| VIS-03 | Visualizer displays recenter events and current aim-zero state. | Current UDP frame has no explicit recenter/aim-zero fields; planner should add sanitized Android diagnostics over existing authenticated control envelopes or another minimal authenticated status path. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `ReloadHoldRecenter.kt`; VERIFIED: `ControlEnvelope.kt`; VERIFIED: `HostSessionService.kt`] |
| VIS-04 | Visualizer displays Android connection, desktop virtual controller, packet stream, and haptic status. | Reuse `ControlServer` session callbacks, `InputStreamLifecycleState`, backend runtime diagnostics, profile metadata, and haptic result callbacks. [VERIFIED: `ControlServer.kt`; VERIFIED: `InputStreamLifecycleState.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`; VERIFIED: `PairingWindow.kt`] |
| VIS-05 | Visualizer includes a haptic test control that vibrates the Android phone and shows ack/fail result. | Reuse `ControlServer.sendHapticCommand`, existing `PairingWindow.smokeHapticCommand`, and `HapticResult` result flow; keep Windows VHF output-to-phone as a separate guided row. [VERIFIED: `ControlServer.kt`; VERIFIED: `PairingWindow.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`] |
| VIS-06 | Visualizer displays latency and packet loss metrics for the current session. | Packet loss comes from accepted UDP sequence gaps; latency requires Android-clock-to-desktop-clock offset because Android `elapsedRealtimeNanos` and JVM `System.nanoTime` have different origins. [VERIFIED: `UdpReceivedInput.kt`; VERIFIED: `InputReplayGuard.kt`; CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos(); CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--] |
| PERF-01 | End-to-end input path can be measured from Android capture timestamp to desktop visualizer update. | Add `VisualizerMetrics` with offset estimate, capture-to-send, send-to-receive estimate, receive-to-render/update, and headline capture-to-render estimate. [VERIFIED: `UdpReceivedInput.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`; CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--] |
| PERF-02 | v1 visualizer path targets under 50 ms end-to-end latency during normal local Wi-Fi testing. | UI should show current/p50/p95 or recent-window headline latency, target badge `<50 ms`, and stale/disconnect warning; manual acceptance still required for normal local Wi-Fi. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `.planning/REQUIREMENTS.md`; ASSUMED] |
</phase_requirements>

## Summary

Phase 9 should be planned as one user-facing desktop Swing acceptance harness plus a small Android-to-desktop status extension for data the UDP stream does not currently carry. The desktop already has the core feed: authenticated `ControlServer` state, accepted/rejected UDP callbacks, mapped product input fields, backend diagnostics, and phone haptic result callbacks. [VERIFIED: `ControlServer.kt`; VERIFIED: `UdpReceivedInput.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`; VERIFIED: `PairingWindow.kt`]

Two implementation hazards matter most. First, `ControlServer` callback fields are single function properties and existing backend runtimes wrap `onUdpInputReceived`; adding another consumer by assignment can silently clobber PairingWindow or backend behavior. Plan a callback fanout/event hub before `VisualizerWindow`. [VERIFIED: `ControlServer.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`] Second, Android capture timestamps and desktop render timestamps are both monotonic but from unrelated clock origins, so direct subtraction is invalid; Phase 9 needs a bounded offset estimate or an explicitly labeled estimated latency. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos(); CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--]

**Primary recommendation:** Add a small `DesktopUiEventHub`, `VisualizerWindow`, `VisualizerModel`, and `VisualizerMetrics`; extend authenticated Android diagnostics with recenter/aim-zero and optional time-sync fields; keep the visualizer read-only except for the LAN haptic test and manual checklist confirmations. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`; VERIFIED: `ControlEnvelope.kt`; VERIFIED: `HostSessionService.kt`]

## Project Constraints (from AGENTS.md)

| Directive | Planning Impact |
|-----------|-----------------|
| Use GSD before file edits unless user says bypass. [VERIFIED: `AGENTS.md`] | Planner should create phase plans before implementation. |
| New user-facing branches use `feature/<short-kebab-slug>`; never use `codex/`, `codex-`, or agent-name prefix unless explicit. [VERIFIED: `AGENTS.md`] | If branch creation appears, state exact branch first. |
| Desktop support must cover Windows 11 x64 and macOS Apple Silicon v1. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/REQUIREMENTS.md`] | Checklist must require macOS Android HID proof plus Windows VHF proof. |
| Visualizer latency target is under 50 ms and must be measured early. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/REQUIREMENTS.md`] | Metrics work should be Wave 0/1, not closeout-only. |
| Desktop profiles must not own aim mapping; Android owns mapped product stream after Phase 8. [VERIFIED: `.planning/STATE.md`; VERIFIED: `08-CONTEXT.md`] | Visualizer shows active Android profile and mapped stream, not profile controls. |
| Sensitive pairing material, stream secrets, HMAC keys, private key data, raw logs, device identifiers, and screenshots must stay out of diagnostics/evidence. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`] | Checklist persistence must be sanitized and manual-first. |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Live visualizer window | Desktop companion UI | Desktop event hub | Swing owns render/update; it must not own networking, HID publication, or Android profiles. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`] |
| Auth/session/UDP ownership | Desktop control/transport | Android control client | Existing `ControlServer`, `DesktopUdpInputRuntime`, and `UdpInputReceiver` own trust, stream config, replay, stale, and callbacks. [VERIFIED: `ControlServer.kt`; VERIFIED: `DesktopUdpInputRuntime.kt`; VERIFIED: `UdpInputReceiver.kt`] |
| Live gamepad state | Desktop semantic adapter | Android profile mapper | Android sends mapped product stream; desktop converts accepted UDP to semantic display/backend state. [VERIFIED: `ProfileMapper.kt`; VERIFIED: `AndroidUdpInputSender.kt`; VERIFIED: `UdpControllerStateAdapter.kt`] |
| Recenter/aim-zero status | Android host service | Desktop visualizer display | Android owns reload-hold recenter and aim baseline; current UDP product frame lacks explicit recenter/zero fields. [VERIFIED: `ReloadHoldRecenter.kt`; VERIFIED: `HostSessionService.kt`; VERIFIED: `UdpInputFrameCodec.kt`] |
| Latency metric | Desktop visualizer metrics | Android timestamp/status source | Desktop owns display, but it needs Android monotonic capture/send timestamps plus offset estimate. [VERIFIED: `UdpReceivedInput.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| Packet loss metric | Desktop visualizer metrics | UDP replay guard | Accepted UDP sequence gaps define expected/missed counters for current stream. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `InputReplayGuard.kt`] |
| LAN phone haptic test | Desktop visualizer action | Android haptic executor | Desktop sends authenticated control command; Android vibrates phone and returns ack/fail. [VERIFIED: `ControlServer.kt`; VERIFIED: `DesktopControlClient.kt`; VERIFIED: `HostSessionService.kt`] |
| Windows VHF haptic proof | Windows backend/runtime | Visualizer checklist | Backend diagnostics expose routed output haptics; user confirms physical phone vibration. [VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `docs/windows/phase6-proof-checklist.md`] |
| macOS Android HID proof | Android Bluetooth HID path | Visualizer checklist | macOS input proof is Android Bluetooth HID; macOS HID haptic row may be unsupported/deferred with Phase 7 evidence. [VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `09-CONTEXT.md`] |

## Standard Stack

### Core

| Library/Platform | Version | Purpose | Why Standard |
|------------------|---------|---------|--------------|
| Kotlin/JVM desktop companion | Kotlin plugin `2.0.21`, Java target 17. [VERIFIED: `desktop-companion/build.gradle.kts`] | Visualizer UI, event hub, metrics, tests | Existing desktop companion is Kotlin/JVM and already launches Swing from `Main.kt`. [VERIFIED: `Main.kt`; VERIFIED: `PairingWindow.kt`] |
| Java Swing (`javax.swing`) | JDK platform API through Java 17 runtime. [VERIFIED: local `java -version`; VERIFIED: `desktop-companion/build.gradle.kts`] | Separate `VisualizerWindow`, panels, buttons, checklists, custom gamepad painting | Phase context locks Swing; existing pairing UI already uses `JFrame`, `JPanel`, `JLabel`, `JButton`, `Timer`, and `SwingUtilities.invokeLater`. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`; CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html] |
| Swing EDT + `SwingUtilities.invokeLater` | JDK platform API | Thread-safe UI model updates | Oracle Swing docs say most Swing interaction must run on the event dispatch thread and long tasks there make UI unresponsive. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html; VERIFIED: `PairingWindow.kt`] |
| `javax.swing.Timer` | JDK platform API | UI refresh cadence, stale age, checklist elapsed labels | Oracle Swing Timer fires action events after delay and is intended for GUI tasks. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/misc/timer.html; VERIFIED: `PairingWindow.kt`] |
| Custom `JPanel.paintComponent` | JDK platform API | Stick/aim crosshairs, stale overlay, button lamps | Oracle Swing painting docs place custom painting in `paintComponent`. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/painting/closer.html] |
| Existing Ktor WebSocket control server | Ktor `3.5.0` in repo. [VERIFIED: `desktop-companion/build.gradle.kts`] | Authenticated control callbacks, haptic command/result, diagnostics/status messages | Already owns reliable control channel; Phase 9 should extend callbacks/status parsing rather than add a second control channel. [VERIFIED: `ControlServer.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| Existing authenticated UDP receiver | In repo | Accepted mapped input frames, sequence, stale state, packet loss source | Already validates HMAC/replay/age and exposes `UdpReceivedInput` fields needed by visualizer. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `InputReplayGuard.kt`; VERIFIED: `UdpReceivedInput.kt`] |

### Supporting

| Library/Platform | Version | Purpose | When to Use |
|------------------|---------|---------|-------------|
| Existing `SemanticControllerState` / `UdpControllerStateAdapter` | In repo | Display-ready button/stick/aim/stale state | Use as visualizer model input boundary so UI and Windows backend see the same semantics. [VERIFIED: `SemanticControllerState.kt`; VERIFIED: `UdpControllerStateAdapter.kt`] |
| Existing backend runtime diagnostics | In repo | Windows/macOS lifecycle, publish, stale, haptic routing status | Use for virtual-controller checklist rows and haptic proof status. [VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`] |
| Existing Android profile/recenter state | In repo | Recenter/aim-zero source and active profile labels | Add minimal authenticated diagnostics from Android because UDP lacks recenter fields. [VERIFIED: `ProfileModels.kt`; VERIFIED: `ReloadHoldRecenter.kt`; VERIFIED: `HostSessionService.kt`] |
| Existing evidence docs/manifests | In repo | Manual checklist grounding for macOS/Windows rows | Use as row labels and proof expectations; do not make generated bundle the primary pass artifact. [VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`; VERIFIED: `09-CONTEXT.md`] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Swing `VisualizerWindow` | JavaFX, Compose Desktop, web UI | Not recommended: Phase 9 explicitly locks Swing and existing desktop app is Swing. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`] |
| Authenticated control diagnostics for recenter | Extend 120-byte UDP frame | Prefer control diagnostics first because current fixed UDP frame and fixtures are stable; recenter is status, not high-rate input. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `UdpInputFrameCodec.kt`; ASSUMED] |
| Single callback assignment per feature | Directly replace `ControlServer.onUdpInputReceived` in each consumer | Do not use: existing runtimes wrap callbacks; another assignment can clobber backend or UI behavior. [VERIFIED: `ControlServer.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`] |

**Installation:** No new external packages recommended. [VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: `android-host/app/build.gradle.kts`]

## Package Legitimacy Audit

Not required: this phase should not install new external packages. Existing Gradle dependencies remain in place. `slopcheck` was not installed locally, but no new package install is recommended. [VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: local `pip show slopcheck`]

## Architecture Patterns

### System Architecture Diagram

```text
Android gun + motion + profile mapper
  -> mapped product UDP frame
       captureElapsedNanos, sendElapsedNanos, sequence, buttons, stick, mapped aim, raw-debug flags
  -> DesktopUdpInputRuntime / UdpInputReceiver
       authenticate HMAC -> replay/stale guard -> accepted UdpReceivedInput
  -> DesktopUiEventHub
       fanout to PairingWindow, VisualizerWindow, Windows/Mac backend runtimes
  -> VisualizerModel
       SemanticControllerState
       packet loss counters from accepted sequence gaps
       latency estimate from Android capture + offset estimate + desktop update/render timestamp
       checklist row state from live observations + user confirmations
  -> Swing VisualizerWindow
       guided checklist + live gamepad panel + metrics/haptics/status

Android recenter / aim-zero status
  -> authenticated control diagnostics/status envelope
  -> DesktopUiEventHub
  -> VisualizerModel recenter row + last-10 event strip

Visualizer haptic button
  -> ControlServer.sendHapticCommand
  -> Android DesktopHapticCommandExecutor / PhoneHaptics
  -> haptic_result callback
  -> Visualizer checklist ack/fail row

Windows VHF output proof
  -> WindowsBackendRuntime diagnostics / outputHapticCommandsRouted
  -> ControlServer.sendHapticCommand
  -> user confirms phone vibration in visualizer checklist
```

### Recommended Project Structure

```text
desktop-companion/src/main/kotlin/com/btgun/desktop/ui/
  DesktopUiEventHub.kt       # fanout; prevents callback clobber
  VisualizerWindow.kt        # Swing frame lifecycle + actions
  VisualizerModel.kt         # immutable UI snapshot and checklist state
  VisualizerMetrics.kt       # latency/loss calculations
  VisualizerPanels.kt        # gamepad/metrics/checklist components

desktop-companion/src/test/kotlin/com/btgun/desktop/ui/
  VisualizerMetricsTest.kt
  VisualizerModelTest.kt
  VisualizerWindowTest.kt

android-host/app/src/main/java/com/btgun/host/session/
  VisualizerStatus.kt        # optional sanitized diagnostics payload
```

### Pattern 1: Event Hub Before UI/Backend Consumers

**What:** Register `ControlServer` callbacks once, then fan out typed events to PairingWindow, VisualizerWindow, and backend runtimes. [VERIFIED: `ControlServer.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`]

**When to use:** Use before adding `VisualizerWindow`; current single-property callbacks are not safe for multiple independent consumers. [VERIFIED: `ControlServer.kt`]

**Example:**

```kotlin
// Source: existing ControlServer callbacks; Swing EDT requirement from Oracle docs.
class DesktopUiEventHub(private val controlServer: ControlServer) {
    private val udpListeners = mutableListOf<(UdpReceivedInput) -> Unit>()

    fun install() {
        controlServer.onUdpInputReceived = { input ->
            udpListeners.toList().forEach { listener -> listener(input) }
        }
    }

    fun onUdpInput(listener: (UdpReceivedInput) -> Unit) {
        udpListeners += listener
    }
}
```

### Pattern 2: EDT-Owned Immutable UI Snapshot

**What:** Convert each callback to a `VisualizerModel` snapshot on the EDT, then request repaint/update; custom panels only draw current snapshot. [VERIFIED: `PairingWindow.kt`; CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html; CITED: https://docs.oracle.com/javase/tutorial/uiswing/painting/closer.html]

**When to use:** Use for all Swing label updates, crosshair repaint, checklist state, and haptic result display. [VERIFIED: `PairingWindow.kt`]

**Example:**

```kotlin
controlServer.onUdpInputReceived = { input ->
    SwingUtilities.invokeLater {
        model = model.withInput(input, updatedAtNanos = System.nanoTime())
        gamepadPanel.snapshot = model.gamepad
        metricsLabel.text = model.metrics.headlineText
        gamepadPanel.repaint()
    }
}
```

### Pattern 3: Latency Estimate With Clock Offset, Not Direct Subtraction

**What:** Estimate Android-to-desktop monotonic offset from authenticated control/diagnostics samples, then compute capture-to-render as `desktopRenderNanos - (androidCaptureNanos + offsetEstimateNanos)`. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos(); CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--]

**When to use:** Use for the headline Phase 9 metric; also display capture-to-send as exact Android-local timing and offset quality as secondary diagnostics. [VERIFIED: `UdpReceivedInput.kt`; ASSUMED]

**Example:**

```kotlin
data class ClockOffsetEstimate(
    val androidToDesktopNanos: Long,
    val quality: String,
)

fun captureToRenderMillis(
    input: UdpReceivedInput,
    renderedAtDesktopNanos: Long,
    offset: ClockOffsetEstimate,
): Long {
    val captureOnDesktopClock = input.captureElapsedNanos + offset.androidToDesktopNanos
    return ((renderedAtDesktopNanos - captureOnDesktopClock).coerceAtLeast(0L) / 1_000_000L)
}
```

### Pattern 4: Current-Session Packet Loss From Accepted Sequence Gaps

**What:** Reset counters on new stream session/control session. For each accepted sequence after the first, add `gap - 1` to missed when `gap > 1`; expected is accepted + missed. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `InputReplayGuard.kt`; VERIFIED: `UdpReceivedInput.kt`]

**When to use:** Use only accepted UDP frames; rejected duplicate/old frames are replay/security data, not accepted-session packet loss. [VERIFIED: `InputReplayGuard.kt`; ASSUMED]

**Example:**

```kotlin
data class PacketLossCounter(
    val lastSequence: Long? = null,
    val accepted: Long = 0,
    val missed: Long = 0,
) {
    fun accept(sequence: Long): PacketLossCounter {
        val gapMissed = lastSequence?.let { previous ->
            (sequence - previous - 1L).coerceAtLeast(0L)
        } ?: 0L
        return copy(lastSequence = sequence, accepted = accepted + 1, missed = missed + gapMissed)
    }
}
```

### Anti-Patterns to Avoid

- **Direct Android/desktop timestamp subtraction:** JVM `System.nanoTime()` and Android `elapsedRealtimeNanos()` are elapsed-time clocks with unrelated origins; direct subtraction can produce nonsense latency. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos(); CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--]
- **Callback clobbering:** Assigning `controlServer.onUdpInputReceived` from VisualizerWindow after backend attachment can bypass backend publication or PairingWindow diagnostics. [VERIFIED: `ControlServer.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`]
- **Making PairingWindow the visualizer:** Forbidden by D-05; keep pairing/status surface and visualizer surface separate. [VERIFIED: `09-CONTEXT.md`]
- **Treating macOS HID haptic unsupported as failure of LAN haptics:** Context allows macOS Bluetooth HID output row to pass unsupported/deferred when current evidence is shown; LAN and Windows haptic rows still must pass. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`]
- **Raw debug crowding default UI:** Raw provider/yaw/pitch/roll/raw aim must be collapsed and only meaningful when Android raw debug is enabled. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| New GUI framework | Custom web server, JavaFX, Compose Desktop | Existing Swing/JDK APIs | Phase locks Swing and project already has Swing UI/tests. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`] |
| New LAN/session transport | Second socket/control protocol for visualizer | Existing `ControlServer` + UDP callbacks | Existing path already authenticates pairing, control, UDP, haptics, stale, and profile metadata. [VERIFIED: `ControlServer.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| New haptic protocol | Direct Android socket or UDP haptic | Existing `ControlServer.sendHapticCommand` and `haptic_result` | v1 haptics are authenticated reliable control messages, not UDP or direct Bluetooth. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `ControlServer.kt`] |
| Desktop profile/mapping editor | Swing profile controls | Android active profile metadata display only | Phase 8 locked Android as profile authority. [VERIFIED: `.planning/STATE.md`; VERIFIED: `08-CONTEXT.md`; VERIFIED: `PairingWindowTest.kt`] |
| Raw packet/evidence capture system | Generated evidence bundle as pass artifact | Guided checklist with sanitized state and user confirmations | D-04 says manual checklist is primary pass artifact. [VERIFIED: `09-CONTEXT.md`] |

**Key insight:** Phase 9 is mostly an observability and acceptance-path phase, not a new driver phase. The planner should spend effort on event fanout, truthful metrics, recenter status plumbing, and guided proof rows, not on new transport, mapping, or HID ownership. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `.planning/STATE.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`]

## Common Pitfalls

### Pitfall 1: Latency Math Uses Unrelated Clocks
**What goes wrong:** UI shows negative, huge, or falsely low capture-to-render latency. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; ASSUMED]
**Why it happens:** Android capture/send timestamps are Android elapsed realtime; desktop update/render uses JVM nanoTime with different origin. [CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos(); CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--]
**How to avoid:** Add authenticated time-sync/diagnostics offset estimate and label metric quality; show exact capture-to-send separately. [VERIFIED: `UdpReceivedInput.kt`; ASSUMED]
**Warning signs:** `desktopNow - input.captureElapsedNanos` appears in code, or tests use same fake clock for Android and desktop without offset. [ASSUMED]

### Pitfall 2: Recenter Row Cannot Be Proven From Current UDP Alone
**What goes wrong:** Visualizer tries to infer recenter from reload hold or aim returning near zero and becomes flaky. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `ReloadHoldRecenter.kt`; ASSUMED]
**Why it happens:** UDP frames carry buttons, stick, mapped aim, raw debug extras, timestamps, and sequence, but no explicit recenter event or aim-baseline state. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `UdpReceivedInput.kt`]
**How to avoid:** Add sanitized Android diagnostics/status over authenticated control with recenter event id/status and current aim-zero/baseline label. [VERIFIED: `ControlEnvelope.kt`; VERIFIED: `HostSessionService.kt`; ASSUMED]
**Warning signs:** VIS-03 tests only check reload button down/up or aimX/aimY near zero. [ASSUMED]

### Pitfall 3: UI Consumer Breaks Backend Runtime
**What goes wrong:** Visualizer updates, but Windows VHF or macOS helper stops receiving input. [VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`; ASSUMED]
**Why it happens:** `ControlServer` exposes one mutable callback per event; backend runtimes wrap `onUdpInputReceived`. [VERIFIED: `ControlServer.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`]
**How to avoid:** Add callback fanout/event hub and tests proving PairingWindow, VisualizerWindow, and backend runtime all receive same input. [ASSUMED]
**Warning signs:** Code assigns `controlServer.onUdpInputReceived = ...` in multiple classes. [VERIFIED: `PairingWindow.kt`; VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`]

### Pitfall 4: Checklist Passes Without Both OS Paths
**What goes wrong:** LAN visualizer looks good, but Phase 9 acceptance misses macOS Android HID or Windows VHF proof. [VERIFIED: `09-CONTEXT.md`]
**Why it happens:** Visualizer success criteria include LAN, macOS Android HID, Windows VHF, and haptics; LAN alone is easier to test. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `.planning/REQUIREMENTS.md`]
**How to avoid:** Make checklist rows explicit and not globally pass until LAN live stream, macOS HID input, Windows VHF input, LAN haptic, Windows output-to-phone haptic, and macOS unsupported/deferred evidence row are handled. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`]
**Warning signs:** One "connected" badge drives all checklist rows. [ASSUMED]

### Pitfall 5: Swing EDT Work Blocks Live Input Display
**What goes wrong:** High-rate input makes UI sluggish or haptic button unresponsive. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html; ASSUMED]
**Why it happens:** Heavy computation, network calls, or evidence writes run on EDT. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html]
**How to avoid:** Keep callbacks cheap, update model on EDT, render at a bounded cadence with `Timer`, and keep haptic send action quick. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/misc/timer.html; VERIFIED: `PairingWindow.kt`; ASSUMED]
**Warning signs:** `Thread.sleep`, blocking IO, Gradle/test execution, or long file writes in Swing action listeners. [ASSUMED]

## Code Examples

Verified patterns from official/current project sources:

### EDT Update Pattern

```kotlin
// Source: Oracle Swing EDT docs + existing PairingWindow.kt
controlServer.onSessionStateChanged = { state ->
    SwingUtilities.invokeLater {
        visualizerModel = visualizerModel.withSessionState(state)
        sessionLabel.text = visualizerModel.sessionText
    }
}
```

### Custom Crosshair Panel

```kotlin
// Source: Oracle Swing painting docs
class CrosshairPanel : JPanel() {
    var xAxis: Float = 0f
    var yAxis: Float = 0f

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val x = ((xAxis.coerceIn(-1f, 1f) + 1f) * 0.5f * width).toInt()
        val y = ((1f - (yAxis.coerceIn(-1f, 1f) + 1f) * 0.5f) * height).toInt()
        g.drawLine(width / 2, 0, width / 2, height)
        g.drawLine(0, height / 2, width, height / 2)
        g.fillOval(x - 4, y - 4, 8, 8)
    }
}
```

### Haptic Test Path

```kotlin
// Source: PairingWindow.smokeHapticCommand + ControlServer.sendHapticCommand
private fun sendLanPhoneHaptic() {
    val command = HapticCommand(
        commandId = "visualizer-ui-${System.nanoTime()}",
        strength = 0.6,
        durationMs = 80L,
        ttlMs = 500L,
    )
    val status = when (val result = controlServer.sendHapticCommand(command)) {
        HapticSendResult.Sent -> "queued: ${command.commandId}"
        HapticSendResult.NoActiveSession -> "not connected"
        is HapticSendResult.Rejected -> "rejected: ${result.error.name.lowercase()}"
        is HapticSendResult.Failed -> "failed: ${result.reason}"
    }
    model = model.withHapticQueued(status)
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| PairingWindow shows concise pairing, packet stream, profile metadata, backend, and haptic smoke only. | Add separate `VisualizerWindow` for guided acceptance and live gamepad/metrics display. | Phase 9 context, 2026-06-12. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `PairingWindow.kt`] | Pairing UI stays focused; acceptance harness gets its own surface. |
| Desktop profile/mapping could have owned aim mapping in earlier roadmap language. | Android owns profiles and sends mapped product stream after Phase 8. | Phase 8 complete, 2026-06-12. [VERIFIED: `.planning/STATE.md`; VERIFIED: `08-CONTEXT.md`] | Visualizer must not add desktop profile controls. |
| macOS virtual HID helper/CoreHID was explored. | Primary macOS v1 proof is Android phone as Bluetooth HID gamepad; CoreHID/DriverKit remain blocked/fallback evidence. | Phase 7 reroute, 2026-06-10/11. [VERIFIED: `.planning/STATE.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`] | Checklist must verify macOS Android HID input, not old CoreHID helper. |
| Haptic proof could be simulated in earlier backend smoke. | Phase 9 requires LAN phone pulse and Windows VHF output-to-phone proof; macOS HID haptic may be unsupported/deferred. | Phase 9 context. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`] | Guided rows need both live and user-confirmed status. |

**Deprecated/outdated:**
- Desktop profile editor in visualizer: forbidden after Phase 8. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `09-CONTEXT.md`]
- CoreHID/DriverKit as primary macOS acceptance row: superseded by Android Bluetooth HID path. [VERIFIED: `.planning/STATE.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`]
- Direct generated evidence bundle as primary Phase 9 artifact: D-04 says guided manual checklist is primary. [VERIFIED: `09-CONTEXT.md`]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Authenticated control diagnostics/status is preferable to extending the fixed UDP frame for recenter/aim-zero. | Standard Stack / Patterns / Pitfalls | If control diagnostics latency or semantics are insufficient, planner may need a UDP schema revision and fixture updates. |
| A2 | A bounded clock-offset estimate is acceptable for the Phase 9 under-50 ms visualizer metric when labeled with quality. | Patterns / Pitfalls | If user expects lab-grade one-way latency, plan needs stronger time sync or instrumentation. |
| A3 | 30-60 Hz Swing repaint/update cadence is sufficient for a simple acceptance visualizer. | Common Pitfalls | Too-low cadence could hide jitter or make aim feel laggy during manual proof. |
| A4 | Tests using one fake clock for Android and desktop would miss the offset bug. | Common Pitfalls | Planner may under-test latency math and ship bogus metrics. |
| A5 | VIS-03 tests that only check reload down/up or near-zero aim are insufficient. | Common Pitfalls | Recenter/aim-zero row may pass without proving actual recenter event. |
| A6 | Event hub tests should prove PairingWindow, VisualizerWindow, and backend runtime all receive same events. | Common Pitfalls / Wave 0 Gaps | Callback clobber could regress backend publication or existing diagnostics. |
| A7 | A single connected badge is insufficient checklist evidence. | Common Pitfalls | Phase may falsely pass without both OS-visible paths and haptic rows. |
| A8 | Blocking work such as sleep, IO, Gradle/test execution, or long file writes in Swing listeners would harm UI responsiveness. | Common Pitfalls | Visualizer may lag or freeze during live proof. |
| A9 | Checklist state can remain in-memory unless planner finds a stronger persistence requirement. | Open Questions | User may expect checklist progress to survive app restart. |
| A10 | Proposed Wave 0 test file names and boundaries are the best fit for existing test style. | Validation Architecture | Planner may choose different names but must preserve coverage. |
| A11 | Research validity windows are estimates. | Metadata | Tool/hardware availability can change sooner than expected. |

## Open Questions

1. **Exact Android status payload shape**
   - What we know: Android owns recenter/aim baseline and current mapped aim status. [VERIFIED: `HostSessionService.kt`; VERIFIED: `ProfileMapper.kt`; VERIFIED: `ReloadHoldRecenter.kt`]
   - What's unclear: Whether planner should encode it as generic `diagnostics` body fields or add a typed helper model while keeping the existing wire type. [VERIFIED: `ControlEnvelope.kt`; ASSUMED]
   - Recommendation: Use existing `diagnostics` control type with a strict parser for non-secret `visualizerStatus` fields and tests on both Android and desktop. [ASSUMED]

2. **Latency precision acceptance**
   - What we know: Requirement target is under 50 ms from Android capture to desktop visualizer update; direct clock subtraction is invalid. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`]
   - What's unclear: How much offset-estimation error is acceptable for user sign-off. [ASSUMED]
   - Recommendation: Plan `estimated` headline metric with offset quality and secondary exact Android-local capture-to-send; require manual local-Wi-Fi test rather than automated pass/fail only. [ASSUMED]

3. **Manual checklist persistence**
   - What we know: D-04 makes guided manual checklist primary and evidence must be sanitized. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`]
   - What's unclear: Whether checklist state must persist across app restarts. [ASSUMED]
   - Recommendation: Keep persistence out unless planner finds existing simple local preference pattern; preserving state while window remains open satisfies D-08. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| OpenJDK 17 | Desktop Swing build/tests | yes | `17.0.19` Homebrew. [VERIFIED: local `java -version`] | Use configured `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. |
| Gradle | Desktop/Android tests | yes with env workaround | Global Gradle `9.5.1`; default sandbox/global start fails native-platform or file-lock socket unless `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home` and unsandboxed test run. [VERIFIED: local `gradle --version`; VERIFIED: focused test run] | Use validated env command from Validation Architecture. |
| Gradle wrapper | Repo-local build | no | No `gradlew` found. [VERIFIED: local `find`] | Use Homebrew/global Gradle with pinned env. |
| ADB / Android SDK platform tools | Manual Android proof | yes | ADB `1.0.41`, platform-tools `36.0.0-13206524`. [VERIFIED: local `adb version`] | None for physical Android proof. |
| Windows PowerShell on local macOS | Local Windows proof commands | no | `powershell`/`pwsh` not found. [VERIFIED: local `which`] | Use Windows target/admin session from Phase 6 docs. |
| Windows 11 target with VHF artifact | Windows acceptance row | manual/external | Prior target `192.168.1.100` and artifact documented. [VERIFIED: `docs/windows/phase6-proof-checklist.md`] | Mark row pending/user-confirmed until target proof. |
| Android physical phone + iPega gun | End-to-end manual proof | manual/external | Real hardware is project assumption and prior phases have evidence. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/STATE.md`] | Simulator/fake tests only cover automation, not final acceptance. |
| Context7 CLI | Official docs lookup fallback | no | `ctx7` not found. [VERIFIED: local `ctx7 library ...`] | Used official docs via web search/open. |
| Knowledge graph | Semantic codebase graph | no | Graphify disabled; no `.planning/graphs/graph.json`. [VERIFIED: local graphify status] | Used direct grep and source reads. |

**Missing dependencies with no fallback:**
- Windows target/admin proof and Android physical hardware are required for final manual Phase 9 acceptance; they are not required to plan or implement automated seams. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`]

**Missing dependencies with fallback:**
- Gradle wrapper absent; global Gradle with `JAVA_HOME` and `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home` works for focused desktop tests when sandbox file-lock socket restriction is not present. [VERIFIED: focused test run]
- Context7 CLI absent; official Oracle/Android docs were used as fallback. [VERIFIED: local `ctx7` result; CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html; CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos()]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotlin/JVM executable test mains under Gradle `test`; Android unit tests under Gradle `testDebugUnitTest`. [VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: `android-host/app/build.gradle.kts`] |
| Config file | `desktop-companion/build.gradle.kts`, `android-host/app/build.gradle.kts`. [VERIFIED: local files] |
| Quick run command | `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Visualizer*' --tests '*PairingWindow*' --tests '*ControlChannel*' --tests '*UdpControllerStateAdapter*' --no-daemon --console=plain` |
| Full suite command | `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --no-daemon --console=plain` plus Android focused tests if planner adds Android visualizer status diagnostics. [VERIFIED: `08-VALIDATION.md`; VERIFIED: focused desktop test run] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| VIS-01 | Separate visualizer opens on authenticated session and can be manually reopened. | unit/UI model | Desktop quick command with `*VisualizerWindow*` | No - Wave 0 add. |
| VIS-02 | Buttons, stick, aim, stale overlay update from mapped UDP input. | unit/render model | Desktop quick command with `*VisualizerModel*` and `*UdpControllerStateAdapter*` | No - Wave 0 add. |
| VIS-03 | Recenter event and aim-zero status update from Android status diagnostics. | unit/integration | Desktop `*VisualizerStatus*`; Android `*HostSessionService*` if payload added | No - Wave 0 add. |
| VIS-04 | Android connection, backend, packet stream, haptic status rows update. | unit/UI model | Desktop quick command with `*VisualizerModel*`, `*PairingWindow*`, backend runtime tests | Partial existing. |
| VIS-05 | LAN haptic button sends command and ack/fail result displays. | unit + manual | Desktop quick command with `*VisualizerWindow*` and existing haptic tests | No - Wave 0 add. |
| VIS-06 | Packet loss and latency metrics update/reset per session. | unit | Desktop quick command with `*VisualizerMetrics*` | No - Wave 0 add. |
| PERF-01 | Capture-to-update metric uses offset estimate and rejects direct clock math. | unit | Desktop quick command with `*VisualizerMetrics*` | No - Wave 0 add. |
| PERF-02 | UI exposes `<50 ms` target and current/recent result for manual Wi-Fi proof. | unit + manual | Desktop quick command + guided checklist manual row | No - Wave 0 add. |

### Sampling Rate

- **Per task commit:** Run the desktop quick command or narrower touched `*Visualizer*` subset. [VERIFIED: `.planning/config.json`; VERIFIED: focused desktop test run]
- **Per wave merge:** Run full desktop suite; run Android unit subset when Android status diagnostics change. [VERIFIED: `08-VALIDATION.md`; ASSUMED]
- **Phase gate:** Full desktop suite green plus guided manual checklist rows for LAN, macOS Android HID, Windows VHF, LAN haptic, Windows haptic, macOS haptic unsupported/deferred. [VERIFIED: `09-CONTEXT.md`]

### Wave 0 Gaps

- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerMetricsTest.kt` - sequence-gap packet loss, session reset, offset latency math, `<50 ms` target labels. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerModelTest.kt` - live gamepad state, stale overlay, raw-debug collapse, haptic statuses, checklist row transitions. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt` - no Swing launch required; verifies labels/actions/helpers and forbidden desktop profile controls. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/DesktopUiEventHubTest.kt` - PairingWindow/Visualizer/backend all receive UDP/session/haptic events without callback clobber. [ASSUMED]
- [ ] Android status diagnostics tests if planner adds `VisualizerStatus.kt`: recenter emitted, aim-zero/baseline, raw debug flag, no secrets. [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Existing QR/manual proof and pinned desktop identity remain in `ControlServer`/Android control client; visualizer must not add unauthenticated control paths. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `ControlServer.kt`] |
| V3 Session Management | yes | Existing trusted control session, fresh UDP stream config, replay guard, stale/grace states remain authoritative. [VERIFIED: `ControlServer.kt`; VERIFIED: `InputReplayGuard.kt`; VERIFIED: `UdpInputReceiver.kt`] |
| V4 Access Control | yes | Haptic test enabled only after authenticated session; checklist confirmations are local UI state and do not grant transport trust. [VERIFIED: `PairingWindow.kt`; VERIFIED: `09-CONTEXT.md`] |
| V5 Input Validation | yes | Validate visualizer diagnostics body, metric ranges, sequence counters, haptic command fields, and raw-debug toggles before display/pass state. [VERIFIED: `ControlEnvelope.kt`; VERIFIED: `HapticCommand.kt`; VERIFIED: `UdpInputFrameCodec.kt`] |
| V6 Cryptography | yes | Do not change HMAC/TLS/session crypto; never display or persist QR secrets, stream keys, HMAC keys, proof values, or private keys. [VERIFIED: `docs/protocol/lan-pairing-v1.md`] |

### Known Threat Patterns for Swing Visualizer / LAN Acceptance

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret leakage in diagnostics/checklist | Information Disclosure | Redact and never include pairing secrets, manual codes, stream/HMAC keys, private key material, raw logs, device identifiers, or screenshots in visualizer state. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`] |
| Spoofed visualizer pass state | Spoofing / Repudiation | Separate observed live state from user-confirmed rows; row labels show source and timestamp; final pass requires explicit manual confirmations. [VERIFIED: `09-CONTEXT.md`; ASSUMED] |
| Replay/old UDP frames shown as current | Tampering | Use only accepted `UdpReceivedInput` after `InputReplayGuard`; stale overlay on timeout/grace expiry. [VERIFIED: `InputReplayGuard.kt`; VERIFIED: `UdpInputReceiver.kt`] |
| Haptic command after disconnect | Tampering / Safety | Enable LAN haptic button only for authenticated active session; show ack/fail from `haptic_result`. [VERIFIED: `PairingWindow.kt`; VERIFIED: `ControlServer.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| Callback clobber hides backend status | Reliability / Tampering | Use event hub/fanout and test all consumers receive same trusted input. [VERIFIED: `ControlServer.kt`; VERIFIED: `WindowsBackendRuntime.kt`; ASSUMED] |

## Sources

### Primary (HIGH confidence)
- `.planning/phases/09-visualizer-acceptance-path/09-CONTEXT.md` - locked decisions, phase boundary, canonical refs. [VERIFIED: local file]
- `.planning/REQUIREMENTS.md` - VIS/PERF requirements and acceptance criteria. [VERIFIED: local file]
- `.planning/STATE.md` - Phase 8 complete, Android profile ownership, macOS/Windows path decisions. [VERIFIED: local file]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - callbacks, session auth, UDP runtime, haptic command/result. [VERIFIED: local file]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - existing Swing/EDT/status/haptic patterns. [VERIFIED: local file]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` and `UdpInputFrameCodec.kt` - visualizer input fields and frame contract. [VERIFIED: local files]
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`, `ProfileMapper.kt`, `ReloadHoldRecenter.kt` - Android profile/recenter/status sources. [VERIFIED: local files]
- Oracle Swing EDT, Timer, and painting docs. [CITED: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html; CITED: https://docs.oracle.com/javase/tutorial/uiswing/misc/timer.html; CITED: https://docs.oracle.com/javase/tutorial/uiswing/painting/closer.html]
- Oracle `System.nanoTime` and Android `SystemClock.elapsedRealtimeNanos` docs. [CITED: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--; CITED: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos()]

### Secondary (MEDIUM confidence)
- `docs/protocol/lan-pairing-v1.md` - protocol docs, timestamp warning, haptic and UDP lifecycle. [VERIFIED: local file]
- `docs/setup/android-bluetooth-hid-gamepad.md` - macOS Android HID proof and unsupported haptic row. [VERIFIED: local file]
- `docs/windows/phase6-proof-checklist.md` - Windows VHF proof and output haptic path. [VERIFIED: local file]
- `.planning/phases/08-desktop-profiles-and-mapping/08-VALIDATION.md` - current test command pattern. [VERIFIED: local file]

### Tertiary (LOW confidence)
- No low-confidence web-only sources used. [VERIFIED: source audit]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Phase locks Swing and repo already contains Kotlin/JVM Swing, Ktor control, UDP receiver, backend runtimes, and tests. [VERIFIED: `09-CONTEXT.md`; VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: `PairingWindow.kt`]
- Architecture: HIGH - Boundaries are fixed by prior phases; the only open design is fanout/status payload shape. [VERIFIED: `.planning/STATE.md`; VERIFIED: `09-CONTEXT.md`]
- Pitfalls: HIGH for clock/callback/recenter gaps because code/protocol prove them; MEDIUM for repaint cadence and exact offset quality because those require live validation. [VERIFIED: `ControlServer.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`; ASSUMED]

**Research date:** 2026-06-12
**Valid until:** 2026-07-12 for Swing/codebase patterns; 2026-06-19 for live hardware/tool availability. [ASSUMED]
