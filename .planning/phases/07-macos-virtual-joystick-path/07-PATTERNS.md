# Phase 07: macos-virtual-joystick-path - Pattern Map

**Mapped:** 2026-06-10T12:44:47Z
**Files analyzed:** 23
**Analogs found:** 12 / 23

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPacker.kt` | utility | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapper.kt` | utility | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt` | service | request-response | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackend.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntime.kt` | service | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidHelperClient.kt` | service | streaming | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsDriverBridge.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` | model/config | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` | exact modify |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosCoreHidBackendSmokeMain.kt` | utility | batch | `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt` | exact |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` | utility | batch | `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` | exact modify |
| `desktop-companion/build.gradle.kts` | config | batch | `desktop-companion/build.gradle.kts` | exact modify |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPackerTest.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPackerTest.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapperTest.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapperTest.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackendTest.kt` | test | request-response | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackendTest.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt` | test | event-driven | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntimeTest.kt` | exact |
| `native/macos-hid-helper/Sources/...` | service | streaming | none | no local analog |
| `native/macos-hid-helper/Entitlements.plist` | config | request-response | none | no local analog |
| `docs/setup/macos-virtual-hid.md` | config/doc | batch | none | no local analog |
| `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` | config/doc | event-driven | `docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl` | role-match |
| `docs/setup/macos-driverkit-fallback.md` | config/doc | batch | none; use `07-RESEARCH.md` DriverKit findings | no local analog |
| `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.cpp` | service | streaming | none; use Apple HIDDriverKit `IOHIDDevice` pattern from `07-RESEARCH.md` | no local analog |
| `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.h` | service | streaming | none; use Apple HIDDriverKit headers from `07-RESEARCH.md` | no local analog |
| `native/macos-hid-driverkit/BTGunHidDriver/Info.plist` | config | request-response | none; use DriverKit/system-extension plist docs from `07-RESEARCH.md` | no local analog |
| `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.swift` | service | request-response | none; use Apple System Extensions `OSSystemExtensionRequest` pattern from `07-RESEARCH.md` | no local analog |
| `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.entitlements` | config | request-response | none; use DriverKit entitlement constants from `07-RESEARCH.md` | no local analog |

## Pattern Assignments

### `MacosHidReportPacker.kt` (utility, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt`

**Imports/constants pattern** (lines 1-8):
```kotlin
package com.btgun.desktop.backend.windows

import com.btgun.desktop.backend.SemanticControllerState
import kotlin.math.roundToInt

const val WINDOWS_INPUT_REPORT_ID = 0x01
const val WINDOWS_INPUT_REPORT_LENGTH_BYTES = 10
```

**Core pack pattern** (lines 24-39):
```kotlin
object WindowsHidReportPacker {
    fun packInputReport(state: SemanticControllerState): WindowsInputReport {
        val report = ByteArray(WINDOWS_INPUT_REPORT_LENGTH_BYTES)
        report[0] = WINDOWS_INPUT_REPORT_ID.toByte()
        report[1] = if (state.stale) 0 else state.buttonBits().toByte()
        report.writeInt16Le(offset = 2, value = if (state.stale) 0 else state.stickX.clampSignedInt16())
        report.writeInt16Le(offset = 4, value = if (state.stale) 0 else state.stickY.invertSignedInt16())
        report.writeInt16Le(offset = 6, value = state.aimX.toSignedInt16Axis())
        report.writeInt16Le(offset = 8, value = state.aimY.toSignedInt16Axis())

        return WindowsInputReport(
            bytes = report,
            stale = state.stale,
            sourceSequence = state.sourceSequence,
        )
    }
}
```

**Validation/axis pattern** (lines 58-73):
```kotlin
private fun Float.toSignedInt16Axis(): Int {
    if (isNaN()) return 0
    val bounded = coerceIn(-1.0f, 1.0f)
    return when {
        bounded >= 1.0f -> Short.MAX_VALUE.toInt()
        bounded <= -1.0f -> Short.MIN_VALUE.toInt()
        bounded < 0.0f -> (bounded * -Short.MIN_VALUE.toInt()).roundToInt()
        else -> (bounded * Short.MAX_VALUE.toInt()).roundToInt()
    }.clampSignedInt16()
}
```

### `MacosOutputReportMapper.kt` (utility, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt`

**Core validation and haptic mapping** (lines 9-33):
```kotlin
object WindowsOutputReportMapper {
    fun toHapticCommand(reportBytes: ByteArray, commandId: String): HapticCommand? {
        if (commandId.isBlank()) return null
        if (reportBytes.size != WINDOWS_OUTPUT_REPORT_LENGTH_BYTES) return null
        if (reportBytes[0].toUnsignedInt() != WINDOWS_OUTPUT_REPORT_ID) return null
        if (reportBytes[1].toUnsignedInt() != WINDOWS_OUTPUT_REPORT_VERSION) return null

        val strength = reportBytes[2].toUnsignedInt() / 255.0
        val durationMs = reportBytes.readUInt16Le(offset = 3).toLong()
        val ttlMs = reportBytes.readUInt16Le(offset = 5).toLong()
        val flags = reportBytes[7].toUnsignedInt()
        val reserved = reportBytes[8].toUnsignedInt()

        if (durationMs !in 1L..1_000L) return null
        if (ttlMs !in 1L..2_000L) return null
        if (flags != 0 || reserved != 0) return null

        return HapticCommand(commandId = commandId, strength = strength, durationMs = durationMs, ttlMs = ttlMs, pattern = null)
    }
}
```

### `MacosVirtualControllerBackend.kt` (service, request-response)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackend.kt`

**Imports/backend contract pattern** (lines 3-14):
```kotlin
import com.btgun.desktop.backend.BackendCapabilities
import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.VirtualControllerBackend
import com.btgun.desktop.backend.VirtualControllerDescriptor
import com.btgun.desktop.backend.btGunV1Descriptor
import com.btgun.desktop.backend.requireBtGunV1Invariant
```

**Lifecycle/publish pattern** (lines 43-64):
```kotlin
override fun start(): BackendLifecycleResult =
    synchronized(lock) {
        lifecycle = BackendLifecycleState.STARTED
        BackendLifecycleResult.Started
    }

override fun publish(state: SemanticControllerState): BackendPublishResult =
    synchronized(lock) {
        val result = if (lifecycle != BackendLifecycleState.STARTED) {
            BackendPublishResult.Rejected("backend not started")
        } else {
            when (val bridgeResult = bridge.submitInputReport(WindowsHidReportPacker.packInputReport(state))) {
                WindowsDriverBridgeResult.Ok -> {
                    this.state = state
                    BackendPublishResult.Published
                }
                is WindowsDriverBridgeResult.Error -> BackendPublishResult.Rejected(bridgeResult.detail)
            }
        }
        lastResult = result
        result
    }
```

**Output-to-haptic pattern** (lines 66-81):
```kotlin
fun drainOutputHaptics(nowElapsedNanos: Long): List<HapticCommand> =
    synchronized(lock) {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        if (lifecycle != BackendLifecycleState.STARTED) return@synchronized emptyList()

        val commands = mutableListOf<HapticCommand>()
        while (true) {
            val report = bridge.readOutputReport() ?: break
            outputReportCounter += 1L
            WindowsOutputReportMapper.toHapticCommand(reportBytes = report, commandId = "windows-output-report-$outputReportCounter")?.let(commands::add)
        }
        commands
    }
```

### `MacosBackendRuntime.kt` (service, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt`

**Callback attach pattern** (lines 45-58):
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

### DriverKit fallback files (conditional, no local analog)

**Analog:** No repository implementation analog. Use `07-RESEARCH.md` sections "DriverKit Fallback Surface", "Pitfall 5: DriverKit Fallback Underplanned", and "Security Domain" as source-of-truth patterns.

**Files covered:**
- `docs/setup/macos-driverkit-fallback.md`
- `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.cpp`
- `native/macos-hid-driverkit/BTGunHidDriver/BTGunHidDriver.h`
- `native/macos-hid-driverkit/BTGunHidDriver/Info.plist`
- `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.swift`
- `native/macos-hid-driverkit/BTGunHidHostApp/BTGunHidHostApp.entitlements`

**Implementation guidance:**
- Only create these files if Plan `07-05` records `corehid-visibility-failed`, `corehid-output-failed`, or `corehid-runtime-blocked`.
- If `corehid-pass` exists, record `phase7-driverkit-fallback-skipped-corehid-pass` and do not create DriverKit source.
- DriverKit code stays a HID report bridge only: input report ID `0x01`, output report ID `0x02`, no LAN/session/security/UDP/profile/haptic transport ownership.
- Host app owns System Extension activation through `OSSystemExtensionRequest`; execution requires explicit user approval and documented rollback.

**Close/restore pattern** (lines 64-83):
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
}
```

**Trusted input + diagnostics pattern** (lines 86-103):
```kotlin
private fun handleTrustedInput(controlServer: ControlServer, input: UdpReceivedInput) {
    val state = UdpControllerStateAdapter.toState(input)
    val publishResult = backend.publish(state)
    val hapticSendResults = backend.drainOutputHaptics(nowElapsedNanos()).map { command ->
        controlServer.sendHapticCommand(command, nowElapsedNanos = nowElapsedNanos())
    }
    synchronized(lock) {
        updateDiagnosticsLocked(
            diagnostics.copy(
                lifecycleState = backend.lifecycleState,
                lastPublishResult = publishResult,
                stale = state.stale,
                lastSourceSequence = state.sourceSequence,
                lastHapticSendResult = hapticSendResults.lastOrNull() ?: diagnostics.lastHapticSendResult,
                outputHapticCommandsRouted = diagnostics.outputHapticCommandsRouted + hapticSendResults.size,
            ),
        )
    }
}
```

### `MacosHidHelperClient.kt` (service, streaming)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsDriverBridge.kt`

**Process IPC setup pattern** (lines 15-23, 48-57):
```kotlin
data class WindowsDriverBridgeConfig(
    val command: List<String> = listOf("DriverBridge.exe"),
    val workingDirectory: File? = null,
) {
    init {
        require(command.isNotEmpty()) { "command must not be empty" }
        require(command.all { it.isNotBlank() }) { "command entries must be nonblank" }
    }
}

interface WindowsDriverBridgeClient : AutoCloseable {
    fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult
    fun readOutputReport(): ByteArray?
    fun readStatus(): WindowsDriverBridgeStatus
    override fun close()
}
```

**Command/error pattern** (lines 63-88):
```kotlin
override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult =
    synchronized(lock) {
        runCatching {
            writeCommand("SUBMIT_INPUT ${report.bytes.toHex()}")
            when (val response = readResponse()) {
                "OK" -> WindowsDriverBridgeResult.Ok
                null -> WindowsDriverBridgeResult.Error("driver bridge closed")
                else -> parseError(response) ?: WindowsDriverBridgeResult.Error("unexpected driver bridge response")
            }
        }.getOrElse {
            WindowsDriverBridgeResult.Error("driver bridge unavailable")
        }
    }
```

**Process lifecycle pattern** (lines 130-140):
```kotlin
private fun ensureProcess() {
    if (process?.isAlive == true && stdin != null && stdout != null) return
    close()
    val builder = ProcessBuilder(config.command)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
    config.workingDirectory?.let(builder::directory)
    val started = builder.start()
    process = started
    stdin = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))
    stdout = BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8))
}
```

### `BackendCapabilities.kt` (model/config, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt`

**Capability model pattern** (lines 15-31):
```kotlin
data class HapticEffectCapability(
    val strength: Boolean,
    val duration: Boolean,
    val pattern: Boolean,
    val phoneHaptic: Boolean,
    val outputReport: Boolean,
    val unsupported: List<UnsupportedReason>,
)

data class BackendCapabilities(
    val platform: String,
    val buttons: List<String>,
    val axes: List<String>,
    val haptics: HapticEffectCapability,
    val lifecycle: List<BackendLifecycleState>,
    val limitations: List<UnsupportedReason>,
)
```

**Real backend preset pattern** (lines 52-73):
```kotlin
fun windowsVhf(): BackendCapabilities =
    BackendCapabilities(
        platform = "windows-vhf",
        buttons = btGunV1Descriptor.buttons,
        axes = btGunV1Descriptor.axes,
        haptics = HapticEffectCapability(
            strength = true,
            duration = true,
            pattern = false,
            phoneHaptic = true,
            outputReport = true,
            unsupported = listOf(
                UnsupportedReason(
                    platform = "windows-vhf",
                    feature = "pattern",
                    detail = "Windows VHF output reports support phone haptic strength and duration only; pattern output is unsupported in v1.",
                ),
            ),
        ),
        lifecycle = listOf(BackendLifecycleState.STOPPED, BackendLifecycleState.STARTED),
        limitations = emptyList(),
    )
```

### Smoke entrypoints and Gradle wiring (utility/config, batch)

**Analogs:** `MacosBackendSmokeMain.kt`, `BackendSmokeRunner.kt`, `build.gradle.kts`

**Smoke main pattern** (MacosBackendSmokeMain lines 6-14):
```kotlin
fun main() {
    val result = BackendSmokeRunner.run(
        platformId = "macos-stub",
        outputFile = Paths.get("build/test-results/btgun-smoke/macos/TEST-btgun-macos-stub.xml"),
        includeHaptic = hapticSmokeEnabled(),
    )
    result.requirePassed()
    println("btgun macos stub smoke XML: ${result.xmlPath.absolute()}")
}
```

**Fixture smoke pattern** (BackendSmokeRunner lines 36-55):
```kotlin
backend.start()
val receiver = UdpInputReceiver().start(
    trustedSession = CONTROL_SESSION_ID,
    config = fixtureConfig(),
)

cases += timedCase("receiver-accepted-snapshot") {
    val accepted = receiver.acceptFixture(GOLDEN_SNAPSHOT_FRAME_HEX, "snapshot")
    acceptedSequences += accepted.input.lastAcceptedSequence
    val state = UdpControllerStateAdapter.toState(accepted.input)
    expectPublished("snapshot", backend.publish(state))
    publishedStates += state
}
```

**Gradle task pattern** (build.gradle.kts lines 85-107):
```kotlin
tasks.register<JavaExec>("smokeDesktopBackendMacosStub") {
    group = "verification"
    description = "Runs the macOS desktop backend stub smoke and writes JUnit-style XML."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.btgun.desktop.smoke.MacosBackendSmokeMainKt")
    systemProperty("btgun.smoke.haptic", btgunSmokeHapticEnabled.get().toString())
}
```

### macOS backend tests

**Analogs:** Windows backend tests under `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/`

**Main-style test runner pattern** (WindowsBackendRuntimeTest lines 22-28):
```kotlin
fun main() {
    runtimePublishesTrustedUdpCallbackThroughWindowsBackend()
    runtimePreservesExistingUdpCallbackWhenAttached()
    staleInputPublishClearsButtonsAndStickButKeepsAim()
    backendOutputReportRoutesToAuthenticatedPhoneHaptic()
    outputReportWithoutActiveAndroidSessionRecordsNoSession()
}
```

**Runtime callback/stale test pattern** (WindowsBackendRuntimeTest lines 78-106):
```kotlin
private fun staleInputPublishClearsButtonsAndStickButKeepsAim() {
    val bridge = RuntimeFakeWindowsDriverBridge()
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = WindowsVirtualControllerBackend(bridge = bridge),
        nowElapsedNanos = { 3_000_000_000L },
    )
    val server = ControlServer(registry = testRegistry())
    val input = receivedInput(
        pressedControls = setOf("trigger", "reload", "x", "y", "a", "b"),
        stickX = 32_000,
        stickY = -32_000,
        rawAimX = 0.5f,
        rawAimY = -0.25f,
        stale = true,
        sequence = 44L,
    )

    runtime.attach(server)
    server.onUdpInputReceived(input)

    val bytes = bridge.submitted.single().bytes
    expectEquals("stale button bits clear", 0, bytes[1].toInt() and 0xff)
    expectEquals("diagnostic stale", true, runtime.diagnostics().stale)
}
```

**Backend fake bridge pattern** (WindowsVirtualControllerBackendTest lines 119-139):
```kotlin
private class FakeWindowsDriverBridge : WindowsDriverBridgeClient {
    val submitted = mutableListOf<WindowsInputReport>()
    val outputReports = ArrayDeque<ByteArray>()
    var nextSubmitResult: WindowsDriverBridgeResult = WindowsDriverBridgeResult.Ok

    override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult {
        submitted.add(report)
        return nextSubmitResult
    }

    override fun readOutputReport(): ByteArray? =
        outputReports.removeFirstOrNull()

    override fun close() {
        closed = true
    }
}
```

## Shared Patterns

### Backend Contract

**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt` lines 26-36
**Apply to:** macOS backend implementation.

```kotlin
interface VirtualControllerBackend {
    val descriptor: VirtualControllerDescriptor
    val capabilities: BackendCapabilities
    val lifecycleState: BackendLifecycleState
    val currentState: SemanticControllerState
    val lastPublishResult: BackendPublishResult?

    fun start(): BackendLifecycleResult
    fun publish(state: SemanticControllerState): BackendPublishResult
    fun simulateOutputReport(report: SimulatedOutputReport): com.btgun.desktop.haptics.HapticCommand?
    fun stop(reason: String = "stopped"): BackendLifecycleResult
}
```

### Descriptor Invariant

**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` lines 10-30
**Apply to:** macOS report descriptor, backend init, and capability preset.

```kotlin
val btGunV1Descriptor = VirtualControllerDescriptor(
    deviceKind = "gamepad_like_joystick",
    buttons = listOf("trigger", "reload", "x", "y", "a", "b"),
    axes = listOf("stickX", "stickY", "aimX", "aimY"),
    triggerKind = "digital",
)

fun requireBtGunV1Invariant(descriptor: VirtualControllerDescriptor) {
    require(descriptor.deviceKind == btGunV1Descriptor.deviceKind)
    require(descriptor.buttons == btGunV1Descriptor.buttons)
    require(descriptor.axes == btGunV1Descriptor.axes)
    require(descriptor.triggerKind == btGunV1Descriptor.triggerKind)
}
```

### UDP-to-Semantic Mapping

**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` lines 5-23
**Apply to:** `MacosBackendRuntime`; do not duplicate LAN parsing in native helper.

```kotlin
object UdpControllerStateAdapter {
    fun toState(input: UdpReceivedInput): SemanticControllerState =
        SemanticControllerState(
            trigger = "trigger" in input.pressedControls,
            reload = "reload" in input.pressedControls,
            x = "x" in input.pressedControls,
            y = "y" in input.pressedControls,
            a = "a" in input.pressedControls,
            b = "b" in input.pressedControls,
            stickX = input.stickX,
            stickY = input.stickY,
            aimX = input.motion.rawAimX.neutralIfNaN(),
            aimY = input.motion.rawAimY.neutralIfNaN(),
            stale = input.stale,
            sourceSequence = input.lastAcceptedSequence,
        )
}
```

### Error Handling

**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsDriverBridge.kt` lines 63-75, 77-88
**Apply to:** helper client IPC and native helper status reads.

```kotlin
runCatching {
    writeCommand("SUBMIT_INPUT ${report.bytes.toHex()}")
    when (val response = readResponse()) {
        "OK" -> WindowsDriverBridgeResult.Ok
        null -> WindowsDriverBridgeResult.Error("driver bridge closed")
        else -> parseError(response) ?: WindowsDriverBridgeResult.Error("unexpected driver bridge response")
    }
}.getOrElse {
    WindowsDriverBridgeResult.Error("driver bridge unavailable")
}
```

### Test Harness

**Source:** `desktop-companion/build.gradle.kts` lines 43-83
**Apply to:** add macOS test main classes to the explicit `doLast` Java runner list.

```kotlin
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
    filter {
        isFailOnNoMatchingTests = false
    }
    doLast {
        listOf(
            "com.btgun.desktop.backend.windows.WindowsHidReportPackerTestKt",
            "com.btgun.desktop.backend.windows.WindowsOutputReportMapperTestKt",
            "com.btgun.desktop.backend.windows.WindowsVirtualControllerBackendTestKt",
            "com.btgun.desktop.backend.windows.WindowsBackendRuntimeTestKt",
        ).forEach { testClass ->
            providers.exec {
                commandLine("java", "-cp", project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath, testClass)
            }.result.get().assertNormalExitValue()
        }
    }
}
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `native/macos-hid-helper/Sources/...` | service | streaming | No Swift, Objective-C, CoreHID, IOHIDUserDevice, DriverKit, or native helper source exists in repo. Use `07-RESEARCH.md` CoreHID/IOHIDUserDevice excerpts and keep helper thin. |
| `native/macos-hid-helper/Entitlements.plist` | config | request-response | No plist/signing entitlement files exist. Use Apple entitlement research and document local signing proof. |
| `docs/setup/macos-virtual-hid.md` | config/doc | batch | No setup doc directory exists. Use concise project docs style from planning docs; include exact commands and entitlement/fallback notes. |
| HIDDriverKit fallback source files | service/config | request-response | No DriverKit/system extension source exists. Only plan after CoreHID proof fails. |

## Metadata

**Analog search scope:** `desktop-companion/src/main/kotlin`, `desktop-companion/src/test/kotlin`, `desktop-companion/build.gradle.kts`, `docs/evidence/manifests`.
**Files scanned:** 64
**Project instructions:** `AGENTS.md` read; no `.codex/skills` or `.agents/skills` directories present.
**Pattern extraction date:** 2026-06-10T12:44:47Z
