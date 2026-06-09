# Phase 06: Windows Virtual Joystick Path - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 13
**Analogs found:** 9 / 13

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt` | utility | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` | role+flow |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPackerTest.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt` | role+flow |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt` | utility | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SimulatedOutputReport.kt` + `StubVirtualControllerBackend.kt` | role-match |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapperTest.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendHapticSmokeTest.kt` | role+flow |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackend.kt` | service | event-driven / request-response | `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt` | role+contract |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsDriverBridge.kt` | service | request-response / file-I/O | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | partial |
| `windows/btgun-vjoy/driver/*` | driver/service | request-response / event-driven | Phase 6 research VHF snippets | no code analog |
| `windows/btgun-vjoy/package/btgunvjoy.inf` | config | batch / install | no existing Windows package tree | no analog |
| `windows/btgun-vjoy/tools/hid-output-sender/*` | utility | request-response | Phase 6 research HID output sender note | no code analog |
| `.github/workflows/windows-driver.yml` | config | batch | no existing workflow tree | no analog |
| `docs/windows/virtual-hid-strategy.md` | docs | batch | `docs/protocol/lan-pairing-v1.md` | docs analog |
| `docs/windows/test-signing-and-install.md` | docs | batch | Phase 5 summary evidence/proof sections | docs analog |
| `docs/windows/phase6-proof-checklist.md` | docs | batch | `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl` | evidence analog |

## Pattern Assignments

### `WindowsHidReportPacker.kt` (utility, transform)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt`

**Imports pattern** (lines 1-4):
```kotlin
package com.btgun.desktop.backend

import com.btgun.desktop.transport.UdpReceivedInput
```

Use package `com.btgun.desktop.backend.windows`; import `SemanticControllerState` only. Keep as `object` with pure functions.

**Core transform pattern** (lines 5-20):
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

**Contract source:** `SemanticControllerState.kt` lines 3-16 defines exact Phase 5 fields. `VirtualControllerDescriptor.kt` lines 10-15 locks six buttons and four axes.

**Required Phase 6 deltas:** pack report ID 1 as little-endian bytes; clear buttons and stick axes when stale; keep last aim axes; clamp axes; expose stale diagnostic state in backend result/capability, not report bytes.

### `WindowsHidReportPackerTest.kt` (test, transform)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt`

**Plain main test pattern** (lines 8-12):
```kotlin
fun main() {
    snapshotFixtureReplaysThroughReceiverBeforeMapping()
    edgeFixtureMapsSemanticControlsAndNeutralizesNanAim()
    staleReceiverInputClearsButtonsAndStickBeforeMapping()
}
```

**Assertion style** (lines 111-120):
```kotlin
private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
```

**Stale behavior reference** (lines 47-69):
```kotlin
val stale = receiver.onStreamTimeout(nowElapsedNanos = RECEIVED_ELAPSED_NANOS + 1_000_000L)
    ?: throw AssertionError("stale input expected")
val state = stale.toSemanticState()
expectEquals("trigger stale", false, state.trigger)
expectEquals("stickX stale", 0, state.stickX)
expectEquals("stale flag", true, state.stale)
```

Add new test main class to `desktop-companion/build.gradle.kts` test launcher list, following lines 48-65.

### `WindowsOutputReportMapper.kt` (utility, transform)

**Analogs:** `SimulatedOutputReport.kt`, `StubVirtualControllerBackend.kt`, `HapticCommand.kt`

**Validation bounds** (`SimulatedOutputReport.kt` lines 3-13):
```kotlin
data class SimulatedOutputReport(
    val strength: Double,
    val durationMs: Long,
    val ttlMs: Long,
    val pattern: String? = null,
) {
    init {
        require(strength in 0.0..1.0) { "strength must be in 0.0..1.0" }
        require(durationMs in 1L..1_000L) { "durationMs must be in 1..1000" }
        require(ttlMs in 1L..2_000L) { "ttlMs must be in 1..2000" }
    }
}
```

**Mapping pattern** (`StubVirtualControllerBackend.kt` lines 52-66):
```kotlin
override fun simulateOutputReport(report: SimulatedOutputReport): HapticCommand? =
    synchronized(lock) {
        if (report.pattern != null) {
            null
        } else {
            outputReportCounter += 1L
            HapticCommand(
                commandId = "stub-output-report-${platform.id}-$outputReportCounter",
                strength = report.strength,
                durationMs = report.durationMs,
                ttlMs = report.ttlMs,
                pattern = null,
            )
        }
    }
```

**Haptic command contract** (`HapticCommand.kt` lines 11-23): nonblank `commandId`, `strength 0.0..1.0`, `durationMs 1..1000`, `ttlMs 1..2000`.

Reject bad report ID, length, version, duration, TTL, and strength before constructing `HapticCommand`. Keep `pattern = null`.

### `WindowsOutputReportMapperTest.kt` (test, transform)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendHapticSmokeTest.kt`

**Valid mapping assertion** (lines 32-50):
```kotlin
val command = requireNotNull(
    backend.simulateOutputReport(
        SimulatedOutputReport(strength = 0.75, durationMs = 120L, ttlMs = 500L, pattern = null),
    ),
)
expectEquals("strength", 0.75, command.strength)
expectEquals("duration", 120L, command.durationMs)
expectEquals("ttl", 500L, command.ttlMs)
expectEquals("pattern", null, command.pattern)
```

**Auth routing test reference** (lines 78-102): build a trusted `ControlServer`, call `sendHapticCommand`, assert `RESERVED_HAPTIC_COMMAND` body fields. Reuse this for backend-level output proof tests, not mapper unit tests.

### `WindowsVirtualControllerBackend.kt` (service, event-driven/request-response)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt`

**Lifecycle/state pattern** (lines 5-17, 25-50):
```kotlin
class StubVirtualControllerBackend(
    private val platform: StubPlatform,
    override val descriptor: VirtualControllerDescriptor = btGunV1Descriptor,
) : VirtualControllerBackend {
    private val lock = Any()
    private var state: SemanticControllerState = SemanticControllerState()
    private var lifecycle: BackendLifecycleState = BackendLifecycleState.STOPPED
    private var lastResult: BackendPublishResult? = null

    init {
        requireBtGunV1Invariant(descriptor)
    }

    override fun publish(state: SemanticControllerState): BackendPublishResult =
        synchronized(lock) {
            val result = if (lifecycle == BackendLifecycleState.STARTED) {
                this.state = state
                BackendPublishResult.Published
            } else {
                BackendPublishResult.Rejected("backend not started")
            }
            lastResult = result
            result
        }
}
```

**Interface contract** (`VirtualControllerBackend.kt` lines 26-37):
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

Implement same contract, but bridge `publish` to `WindowsDriverBridge.submitInput(reportBytes)`. Do not put LAN, pairing, authentication, or profile mapping in this class.

### `WindowsDriverBridge.kt` (service, request-response/file-I/O)

**Partial analog:** `ControlServer.kt`

**Callback/integration seams** (lines 55-60):
```kotlin
var onSessionStateChanged: (ControlServerSessionState) -> Unit = {}
var onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {}
var onUdpInputReceived: (UdpReceivedInput) -> Unit = {}
var onUdpInputRejected: (InputReplayRejectReason) -> Unit = {}
var onUdpInputStateChanged: (InputStreamLifecycleState) -> Unit = {}
var onHapticResultReceived: (HapticResult) -> Unit = {}
```

**Haptic send boundary** (lines 334-368):
```kotlin
fun sendHapticCommand(command: HapticCommand, nowElapsedNanos: Long = System.nanoTime()): HapticSendResult {
    val active = synchronized(stateLock) { activeControlSession }
        ?: return HapticSendResult.NoActiveSession
    val envelope = hapticCommandEnvelopeFor(active.trustedSession, command, nowElapsedNanos)
    val encoded = ControlEnvelopeCodec.encode(envelope)
    // decode/size validation, then outbound.trySend(envelope)
}
```

Driver bridge should expose small methods: open/close, submit input report, poll/read output report, status. Map output reports to `HapticCommand`, then caller sends through `ControlServer.sendHapticCommand`.

### `windows/btgun-vjoy/driver/*` (driver/service, request-response/event-driven)

**No existing code analog. Use Phase 6 research snippets and Microsoft VHF pattern.**

**Descriptor/report contract:** Phase 6 research recommends report ID 1 input: six buttons, X/Y/Rx/Ry signed int16; report ID 2 output: vendor-defined phone haptic payload.

**IOCTL public header pattern:**
```c
#define FILE_DEVICE_BT_GUN_VJOY 0x8000
#define IOCTL_BTGVJOY_SUBMIT_INPUT \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x801, METHOD_BUFFERED, FILE_WRITE_DATA)
#define IOCTL_BTGVJOY_READ_OUTPUT \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x802, METHOD_BUFFERED, FILE_READ_DATA)
#define IOCTL_BTGVJOY_GET_STATUS \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x803, METHOD_BUFFERED, FILE_READ_DATA)
```

**VHF pattern:** `VHF_CONFIG_INIT`, set `EvtVhfAsyncOperationWriteReport`, call `VhfCreate`, then `VhfStart`; submit input with `VhfReadReportSubmit`. Validate version, size, report ID, and buffer length before VHF submit.

### `.github/workflows/windows-driver.yml` (config, batch)

**No existing workflow analog.**

Follow Phase 6 constraints: build/sign/package in GitHub Actions; do not install WDK/MSBuild on `192.168.1.100`; private signing key only from Actions secrets; artifact must include `.sys`, `.inf`, `.cat`, public IOCTL header, version/build metadata, and install docs.

### `docs/windows/*.md` and evidence manifest (docs, batch)

**Analogs:** Phase 5 summary and evidence manifest.

**Proof/evidence pattern:** Phase 5 summary records exact commands, pass/fail evidence, redaction scan, known stubs, and user confirmation. Copy that shape for Phase 6 docs:
- CLI/PnP proof.
- HID/game-controller enumeration.
- mandatory `joy.cpl` visual proof.
- live Android/gun input proof.
- real HID output report to Android phone haptic proof.
- explicit user approval for `bcdedit`, boot/signing, reboot, install, rollback.
- redaction scan for QR/proof/session/private-key/device-id leaks.

## Shared Patterns

### Gradle/Kotlin Tests
**Source:** `desktop-companion/build.gradle.kts` lines 40-75  
**Apply to:** all new desktop companion tests
```kotlin
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
    filter { isFailOnNoMatchingTests = false }
    doLast {
        listOf(
            "com.btgun.desktop.backend.BackendContractTestKt",
            "com.btgun.desktop.backend.BackendHapticSmokeTestKt",
        ).forEach { testClass ->
            providers.exec {
                commandLine("java", "-cp", project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath, testClass)
            }.result.get().assertNormalExitValue()
        }
    }
}
```

### Backend Contract
**Source:** `VirtualControllerBackend.kt` lines 26-37  
**Apply to:** Windows backend implementation

Keep lifecycle, `currentState`, `lastPublishResult`, and capabilities observable. Reject publishes before start.

### Descriptor Invariant
**Source:** `VirtualControllerDescriptor.kt` lines 10-30  
**Apply to:** Windows packer/backend/driver docs

Six buttons: `trigger`, `reload`, `x`, `y`, `a`, `b`. Four axes: `stickX`, `stickY`, `aimX`, `aimY`. Digital trigger. Normal gamepad-like joystick.

### Haptic Security Boundary
**Source:** `ControlServer.kt` lines 334-383  
**Apply to:** Windows output report path

Output report only maps to `HapticCommand`; sending remains through `ControlServer.sendHapticCommand`. No direct Android socket/channel from Windows driver or bridge.

### Capability Reporting
**Source:** `BackendCapabilities.kt` lines 24-84  
**Apply to:** Windows backend capabilities

Use structured capabilities and `UnsupportedReason`. For real Windows backend, change platform to Windows real path and set `outputReport = true` only after real HID output report is wired.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `windows/btgun-vjoy/driver/*` | driver/service | request-response/event-driven | No C/WDK/KMDF/VHF code exists in repo. Use Phase 6 research + Microsoft VHF patterns. |
| `windows/btgun-vjoy/package/btgunvjoy.inf` | config | install/batch | No existing Windows driver package tree. |
| `windows/btgun-vjoy/tools/hid-output-sender/*` | utility | request-response | No Windows HID user-mode helper exists. |
| `.github/workflows/windows-driver.yml` | config | batch | No existing GitHub Actions workflow in repo. |

## Metadata

**Analog search scope:** `desktop-companion`, `docs`, `.github`, `windows`, `.codex`, `.agents`  
**Files scanned:** 53 repo files from `rg --files`; no `windows/` or `.github/` tree exists yet  
**Pattern extraction date:** 2026-06-09
