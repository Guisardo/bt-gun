# Phase 05: desktop-backend-contract-and-smoke-harness - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 14
**Analogs found:** 14 / 14

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` | model | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` | model | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` | model | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt` | service | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt` | service | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` | utility | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` | service | batch | `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt` | data-flow match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt` | utility | file-I/O | `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt` | partial |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt` | route | batch | `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsBackendSmokeMain.kt` | route | batch | `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt` | role-match |
| `desktop-companion/build.gradle.kts` | config | batch | `desktop-companion/build.gradle.kts` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendContractTest.kt` | test | batch | `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendCapabilitiesTest.kt` | test | batch | `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` | exact |

## Pattern Assignments

### `SemanticControllerState.kt` (model, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt`

**Imports pattern:** none; simple package + data classes.

**Core pattern** (lines 3-28):
```kotlin
data class UdpReceivedMotion(...)

data class UdpReceivedInput(
    val controlSessionId: String,
    val streamSessionIdHex: String,
    val frameType: UdpInputFrameType,
    val buttons: Int,
    val pressedControls: Set<String>,
    val stickX: Int,
    val stickY: Int,
    val motion: UdpReceivedMotion,
    ...
)
```

Copy as small immutable data classes. Add `require(...)` guards only for hard invariants; use default values for neutral state.

### `VirtualControllerDescriptor.kt` (model, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt`

**Core pattern** (lines 1-8):
```kotlin
package com.btgun.desktop.control

data class ControlDiagnostics(
    val sessionState: String,
    val desktopIdentitySuffix: String,
    val heartbeatAgeMillis: Long?,
    val lastControlError: String?,
)
```

Keep descriptor as plain data, likely with lists/enums for six buttons and four axes. Tests should assert field names/counts like `ControlChannelTest.kt` lines 226-239.

### `BackendCapabilities.kt` (model, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt`

**Imports pattern** (lines 3-9):
```kotlin
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
```

Use this JSON style only if capabilities need artifact/control serialization. Otherwise keep package-only model classes.

**Validation pattern** (lines 18-23):
```kotlin
init {
    require(commandId.isNotBlank()) { "commandId must be nonblank" }
    require(strength in 0.0..1.0) { "strength must be in 0.0..1.0" }
    require(durationMs in 1L..1_000L) { "durationMs must be in 1..1000" }
    require(ttlMs in 1L..2_000L) { "ttlMs must be in 1..2000" }
}
```

Copy for capability invariants: unsupported reason `platform` and `detail` nonblank, haptic matrix ranges explicit.

### `VirtualControllerBackend.kt` (service, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt`

**Interface pattern** (lines 11-22):
```kotlin
sealed interface UdpInputRuntimeStartResult {
    data object Started : UdpInputRuntimeStartResult
    data class Failed(val reason: String) : UdpInputRuntimeStartResult
}

interface UdpInputRuntime {
    val lifecycleState: InputStreamLifecycleState

    fun start(trustedSession: String, config: InputStreamConfig): UdpInputRuntimeStartResult
    fun onControlDisconnected(nowElapsedNanos: Long)
    fun stop(reason: String = "stopped")
}
```

Copy sealed result + interface style. Backend should expose `descriptor`, `capabilities`, `currentState`, `lastPublishResult`, lifecycle, `start()`, `publish(state)`, `stop(reason)`.

### `StubVirtualControllerBackend.kt` (service, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt`

**State/lifecycle pattern** (lines 24-43, 74-80):
```kotlin
class DesktopUdpInputRuntime(...) : UdpInputRuntime {
    private val lock = Any()

    override val lifecycleState: InputStreamLifecycleState
        get() = synchronized(lock) { receiver.lifecycleState }

    override fun stop(reason: String) {
        val state = synchronized(lock) {
            stopLocked(reason)
            receiver.lifecycleState
        }
        onStateChanged(state)
    }
}
```

Stub backend should be synchronized, keep last semantic state/result, and callback on state change if needed. Do not claim OS-visible HID support.

### `UdpControllerStateAdapter.kt` (utility, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt`

**Core transform** (lines 30-57):
```kotlin
internal fun UdpInputFrame.toReceivedInput(
    controlSessionId: String,
    receivedElapsedNanos: Long,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = controlSessionId,
        streamSessionIdHex = streamSessionId,
        ...
        pressedControls = pressedControlsFrom(buttonBitmask),
        stickX = stickX,
        stickY = stickY,
        motion = UdpReceivedMotion(...),
        stale = false,
        lastAcceptedSequence = sequence,
    )
```

**Button mapping** (lines 59-67):
```kotlin
private fun pressedControlsFrom(buttons: Int): Set<String> =
    buildSet {
        if (buttons and 0x01 != 0) add("trigger")
        if (buttons and 0x02 != 0) add("reload")
        if (buttons and 0x04 != 0) add("x")
        if (buttons and 0x08 != 0) add("y")
        if (buttons and 0x10 != 0) add("a")
        if (buttons and 0x20 != 0) add("b")
    }
```

Adapter should map `pressedControls` booleans and pass `stickX`, `stickY`, `motion.rawAimX`, `motion.rawAimY`, `stale`, and `lastAcceptedSequence`. No profile mapping.

### `BackendSmokeRunner.kt` (service, batch)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt`

**UDP loopback replay pattern** (lines 14-31):
```kotlin
val received = mutableListOf<UdpReceivedInput>()
val states = mutableListOf<InputStreamLifecycleState>()
val config = fixtureConfig(udpPort = freeUdpPort(), streamTimeoutMs = 250L)
val runtime = DesktopUdpInputRuntime(
    onInput = received::add,
    onStateChanged = states::add,
)

expectEquals("start", UdpInputRuntimeStartResult.Started, runtime.start(CONTROL_SESSION_ID, config))
sendUdp(config.udpPort, UdpInputFrameCodec.encode(frame(sequence = 1L), config))
waitUntil("input received") { received.isNotEmpty() }
runtime.stop()
```

Runner should replay encoded UDP frames through receiver/runtime, then adapter, then backend publish. Keep fixture constants local and deterministic.

### `JunitSmokeXml.kt` (utility, file-I/O)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt`

No direct XML analog exists. Use same no-dependency data validation style from `HapticCommand.kt` lines 18-23. Planner should create a tiny writer that escapes XML text, writes `<testsuite>` and `<testcase>`, and records failure text without secrets.

### `MacosBackendSmokeMain.kt` and `WindowsBackendSmokeMain.kt` (route, batch)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntimeTest.kt`

**Main entrypoint pattern** (lines 7-12):
```kotlin
fun main() {
    runtimeReceivesLoopbackDatagramAndSurfacesInput()
    runtimeRejectsMalformedDatagramsAndStops()
    runtimeTimeoutMarksCurrentInputStale()
    runtimeRestartsOnSamePort()
}
```

Each smoke main should call shared runner with platform id `macos-stub` or `windows-stub`, emit JUnit-style XML, and fail by throwing `AssertionError` or uncaught exception.

### `desktop-companion/build.gradle.kts` (config, batch)

**Analog:** same file.

**Current test registration pattern** (lines 31-52):
```kotlin
tasks.withType<Test>().configureEach {
    val unitTestTask = this
    failOnNoDiscoveredTests = false

    filter {
        isFailOnNoMatchingTests = false
    }

    doLast {
        listOf(
            "com.btgun.desktop.control.ControlChannelTestKt",
            "com.btgun.desktop.transport.DesktopUdpInputRuntimeTestKt",
        ).forEach { testClass ->
            providers.exec {
                commandLine(
                    "java",
                    "-cp",
                    project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath,
                    testClass,
                )
            }.result.get().assertNormalExitValue()
        }
    }
}
```

Append new backend test main classes to this list. Add `JavaExec` tasks for `smokeDesktopBackendMacosStub` and `smokeDesktopBackendWindowsStub` using the same classpath shape.

### Backend tests (test, batch/transform)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt`

**Main test style** (lines 27-55):
```kotlin
fun main() {
    envelopeCodecAcceptsOnlyVersionOneAndKnownTypes()
    ...
    controlServerStartsUdpRuntimeWithAdvertisedInputStreamConfig()
}
```

**Assertions pattern** (`ControlChannelTest.kt` lines 757-760; `DesktopUdpInputRuntimeTest.kt` lines 130-134):
```kotlin
private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
```

Use plain `main()` tests, local `expectEquals`/`expectTrue`, no JUnit dependency. Contract tests must assert exactly six buttons, four axes, digital trigger, capability/detail reasons, and output/haptic support declarations.

## Shared Patterns

### Trusted UDP Input
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt`
**Apply to:** Smoke runner, adapter tests.
```kotlin
fun handleDatagram(bytes: ByteArray, receivedElapsedNanos: Long): UdpInputReceiverResult {
    val controlSessionId = trustedControlSessionId ?: return UdpInputReceiverResult.Stopped
    val activeGuard = guard ?: return UdpInputReceiverResult.Stopped
    return when (val decision = activeGuard.acceptDatagram(bytes, receivedElapsedNanos, controlSessionId)) {
        is InputReplayDecision.Accepted -> {
            current = decision.input
            onInput(decision.input)
            UdpInputReceiverResult.Accepted(decision.input)
        }
        is InputReplayDecision.Rejected -> UdpInputReceiverResult.Rejected(decision.reason)
    }
}
```

### Stale Input Handling
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt`
**Apply to:** `UdpControllerStateAdapter.kt`, smoke runner.
```kotlin
fun onTimeout(current: UdpReceivedInput): UdpReceivedInput =
    current.copy(
        buttons = 0,
        pressedControls = emptySet(),
        stickX = 0,
        stickY = 0,
        stale = true,
    ).also { this.current = it }
```

Semantic stale state should clear button/stick state; aim values may remain from the source input unless planner chooses neutral aim for stale.

### Phone Haptic Path
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`
**Apply to:** output-report simulation and haptic smoke.
```kotlin
fun sendHapticCommand(
    command: HapticCommand,
    nowElapsedNanos: Long = System.nanoTime(),
): HapticSendResult {
    val active = synchronized(stateLock) { activeControlSession }
        ?: return HapticSendResult.NoActiveSession
    val envelope = hapticCommandEnvelopeFor(active.trustedSession, command, nowElapsedNanos)
    ...
    return if (active.outbound.trySend(envelope).isSuccess) {
        HapticSendResult.Sent
    } else {
        HapticSendResult.Failed("active control socket rejected haptic command")
    }
}
```

### Haptic Test Double
**Source:** `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt`
**Apply to:** backend output-report tests.
```kotlin
val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
server.registerActiveControlSessionForTest(trustedSession, outbound)

val send = server.sendHapticCommand(command, nowElapsedNanos = 3_000_000_000L)
val sentEnvelope = outbound.tryReceive().getOrNull()

expectEquals("active haptic sent", HapticSendResult.Sent, send)
expectEquals("haptic type", ControlMessageType.RESERVED_HAPTIC_COMMAND, sentEnvelope?.type)
```

### No-Secret Diagnostics
**Source:** `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt`
**Apply to:** JUnit XML and evidence manifest.
```kotlin
listOf("qrSecret", "qr_secret", "manualCode", "manual code", "pairingProof", "proof").forEach { secret ->
    expectTrue("config body excludes $secret", first.body.toString().contains(secret, ignoreCase = true).not())
}
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt` | utility | file-I/O | No XML/file artifact writer exists; use local no-dependency utility. |

## Metadata

**Analog search scope:** `desktop-companion/src/main/kotlin`, `desktop-companion/src/test/kotlin`, Phase 05 docs.
**Files scanned:** 24 Kotlin files plus Gradle/phase docs.
**Pattern extraction date:** 2026-06-09
