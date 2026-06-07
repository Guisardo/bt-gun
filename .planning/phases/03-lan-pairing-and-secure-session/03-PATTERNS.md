# Phase 03: LAN Pairing and Secure Session - Pattern Map

**Mapped:** 2026-06-07
**Files analyzed:** 21
**Analogs found:** 18 / 21

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `android-host/app/build.gradle.kts` | config | build | `android-host/app/build.gradle.kts` | exact-modify |
| `android-host/app/src/main/AndroidManifest.xml` | config | request-response | `android-host/app/src/main/AndroidManifest.xml` | exact-modify |
| `android-host/app/src/main/java/com/btgun/host/session/PairingPayload.kt` | model/utility | transform | `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/session/TrustedDesktopStore.kt` | service/store | file-I/O | `android-host/app/src/main/java/com/btgun/host/motion/AimCalibration.kt` | data-flow-match |
| `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` | service | request-response/event-driven | `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/session/DesktopLinkState.kt` | model | event-driven | `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | partial |
| `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` | model | request-response | `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | component/state | event-driven | `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | exact-modify |
| `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | controller/component | request-response/event-driven | `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | exact-modify |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | service | event-driven | `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | exact-modify |
| `android-host/app/src/test/java/com/btgun/host/session/PairingPayloadTest.kt` | test | transform | `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` | role-match |
| `android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt` | test | file-I/O | `android-host/app/src/test/java/com/btgun/host/motion/AimCalibrationTest.kt` | role-match |
| `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt` | test | request-response/event-driven | `android-host/app/src/test/java/com/btgun/host/ble/IpegaBleGunAdapterTest.kt` | role-match |
| `desktop-companion/build.gradle.kts` | config | build | `android-host/app/build.gradle.kts` | partial |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt` | service | request-response | `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt` | partial |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/security/DesktopIdentityStore.kt` | service/store | file-I/O | `android-host/app/src/main/java/com/btgun/host/motion/AimCalibration.kt` | data-flow-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | service/controller | request-response/streaming | none | no analog |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` | component | event-driven | `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | partial |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/pairing/PairingSessionRegistryTest.kt` | test | request-response | `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` | role-match |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` | test | request-response/streaming | none | no analog |
| `docs/protocol/lan-pairing-v1.md` | config/spec | transform | `docs/protocol/ipega-phase1-haptics.md` | no analog read |

## Pattern Assignments

### `android-host/app/src/main/java/com/btgun/host/session/PairingPayload.kt` (model/utility, transform)

**Analog:** `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt`

**Imports/data model pattern** (lines 1-12):
```kotlin
package com.btgun.host.model

import com.btgun.host.motion.MotionCapabilityFlags

data class LiveEnvelope<T>(
    val stream: StreamKind,
    val seq: Long,
    val captureElapsedNanos: Long,
    val emittedElapsedNanos: Long,
    val payload: T,
    val provenance: Provenance? = null,
)
```

**Validation pattern** (lines 13-19):
```kotlin
init {
    require(seq > 0L) { "seq must start at 1" }
    require(captureElapsedNanos >= 0L) { "captureElapsedNanos must be non-negative" }
    require(emittedElapsedNanos >= captureElapsedNanos) {
        "emittedElapsedNanos must be greater than or equal to captureElapsedNanos"
    }
}
```

**Wire-name enum pattern** (lines 22-26, 113-127):
```kotlin
enum class StreamKind(val wireName: String) {
    GUN("gun"),
    MOTION("motion"),
    STATUS("status"),
}

enum class MotionProvider {
    GAME_ROTATION_VECTOR,
    ROTATION_VECTOR,
    GYRO_GRAVITY,
    TILT_FALLBACK,
    UNAVAILABLE;

    val wireName: String
        get() = when (this) {
            GAME_ROTATION_VECTOR -> "game_rotation_vector"
            ROTATION_VECTOR -> "rotation_vector"
            GYRO_GRAVITY -> "gyro_gravity"
            TILT_FALLBACK -> "tilt_fallback"
            UNAVAILABLE -> "unavailable"
        }
}
```

**Apply:** Use immutable `data class` payloads, explicit `wireName` values, monotonic elapsed timestamp fields where relevant, and `require` for impossible values. For QR/manual parse errors, return typed invalid results instead of throwing from normal user input.

---

### `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` (model, request-response)

**Analog:** `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt`

**Sequencing pattern** (lines 28-40):
```kotlin
class StreamSequencer {
    private val nextByStream = mutableMapOf<StreamKind, Long>()

    fun next(stream: StreamKind): Long {
        val next = nextByStream[stream] ?: 1L
        nextByStream[stream] = next + 1L
        return next
    }
}

fun interface ElapsedNanosClock {
    fun nowElapsedNanos(): Long
}
```

**Apply:** Mirror with a control-channel sequencer and injectable elapsed clock. Envelope should include version, type, message id, session id, sequence, sent elapsed nanos, and typed/serialized body. Reserve haptic type only; no payload semantics in Phase 3.

---

### `android-host/app/src/main/java/com/btgun/host/session/TrustedDesktopStore.kt` (service/store, file-I/O)

**Analog:** `android-host/app/src/main/java/com/btgun/host/motion/AimCalibration.kt`

**Imports/store pattern** (lines 1-4, 520-535):
```kotlin
package com.btgun.host.motion

import android.content.Context

class AimCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AimCalibration? =
        AimCalibrationCodec.decode(preferences.getString(KEY_CALIBRATION, null))

    fun save(calibration: AimCalibration) {
        preferences.edit()
            .putString(KEY_CALIBRATION, AimCalibrationCodec.encode(calibration))
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "bt_gun_aim_calibration"
        private const val KEY_CALIBRATION = "active_calibration"
    }
}
```

**Validation/codec invalid-data pattern** (lines 500-516):
```kotlin
if (parts.size != 2) {
    return null
}
val x = parts[0].toFloatOrNull() ?: return null
val y = parts[1].toFloatOrNull() ?: return null
points[mark] = RawAimPoint(x, y)
return when (
    val result = AimCalibrationSolver.buildFromCentered(
        providerName = fields[1],
        centeredPoints = points,
        createdAtEpochMillis = createdAt,
    )
) {
    is AimCalibrationBuildResult.Valid -> result.calibration
    is AimCalibrationBuildResult.Invalid -> null
}
```

**Apply:** Use `SharedPreferences` for non-secret trusted desktop metadata only: fingerprint/public key, display name, last endpoint, last seen. Decode corrupted rows to `null`/empty and fail closed on fingerprint mismatch. Do not store one-time pairing material.

---

### `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` (service, request-response/event-driven)

**Analog:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt`

**Service ownership/listener update pattern** (lines 50-73, 155-179):
```kotlin
class HostSessionService : Service() {
    private var adapter: IpegaBleGunAdapter? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentState: HostSessionState = HostSessionState()
        set(value) {
            field = value
            latestState = value
        }

    val listener = object : IpegaBleGunAdapter.Listener {
        override fun onConnectionState(state: BleGunConnectionState) {
            currentState = currentState.copy(
                phase = state.phase.toHostSessionPhase(),
                foregroundActive = currentState.foregroundActive,
                reconnectAttempt = state.reconnectAttempt,
                lastError = state.lastError,
                lastBleConnectionState = state,
            )
        }
    }
}
```

**Lifecycle/error pattern** (lines 182-198, 200-209):
```kotlin
private fun startHostForegroundSafely(): Boolean =
    try {
        startHostForeground()
        true
    } catch (error: SecurityException) {
        currentState = HostSessionState(
            phase = HostSessionPhase.ERROR,
            lastError = "Foreground service blocked: ${error.javaClass.simpleName}",
        )
        false
    } catch (error: IllegalStateException) {
        currentState = HostSessionState(
            phase = HostSessionPhase.ERROR,
            lastError = "Foreground service blocked: ${error.javaClass.simpleName}",
        )
        false
    }

private fun stopSession() {
    currentState = currentState.copy(phase = HostSessionPhase.STOPPING)
    handler.removeCallbacks(reloadHoldTick)
    adapter?.stopSession()
    adapter = null
    currentState = HostSessionState(phase = HostSessionPhase.STOPPED)
}
```

**Apply:** Own WebSocket lifecycle from the foreground session boundary or a dependency it explicitly controls. State changes must flow through immutable `copy`, with clear `lastError`, heartbeat age, connected/degraded/disconnected/trust-problem phases, and explicit cleanup of callbacks/socket on stop.

---

### `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` (component/state, event-driven)

**Analog:** same file.

**Placeholder activation target** (lines 73-90):
```kotlin
data class PlaceholderSurface(
    val title: String,
    val body: String,
    val active: Boolean,
)

data class DashboardPlaceholders(
    val desktopLink: PlaceholderSurface = PlaceholderSurface(
        title = "Desktop link",
        body = "Not built yet. Pending Phase 3.",
        active = false,
    ),
    val packetStream: PlaceholderSurface = PlaceholderSurface(
        title = "Packet stream",
        body = "Not built yet. Pending Phase 4.",
        active = false,
    ),
)
```

**Dashboard composition pattern** (lines 140-180):
```kotlin
fun from(
    permissionGateState: PermissionGateState,
    hostSessionState: HostSessionState,
    bleConnectionState: BleGunConnectionState = BleGunConnectionState(),
    nowElapsedNanos: Long = 0L,
): DashboardState {
    val placeholders = DashboardPlaceholders()
    return DashboardState(
        appTitle = "BT Gun Host",
        permission = permissionState(permissionGateState),
        primaryActionLabel = if (hostSessionState.isActive) "Stop session" else "Start live session",
        foregroundService = DashboardField("Foreground service", if (hostSessionState.foregroundActive) "running" else "stopped"),
        currentError = DashboardField("Current error", hostSessionState.lastError ?: bleConnectionState.lastError ?: "none"),
        placeholders = placeholders,
    )
}
```

**Apply:** Replace desktop placeholder with real desktop link state and actions; leave packet stream pending Phase 4. Keep diagnostics concise: session state, desktop identity/fingerprint suffix, heartbeat age, last control error.

---

### `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` (controller/component, request-response/event-driven)

**Analog:** same file.

**Imperative Android UI pattern** (lines 28-56, 70-97):
```kotlin
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var primaryAction: Button
    private val fields = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        renderDashboard()
    }

    private fun buildLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(247, 248, 246))
        }

        permissionAction = button("Grant permissions") { requestHostPermissions() }
        root.addView(permissionAction)
    }
}
```

**Render/action pattern** (lines 156-207, 210-238):
```kotlin
private fun renderDashboard() {
    val permissionGate = permissionGateState()
    val latestServiceState = HostSessionService.latestState
    val dashboard = DashboardState.from(
        permissionGateState = permissionGate,
        hostSessionState = latestServiceState,
        nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
    )
    setField("desktop_link", "${dashboard.placeholders.desktopLink.title}: ${dashboard.placeholders.desktopLink.body}")
    setField("packet_stream", "${dashboard.placeholders.packetStream.title}: ${dashboard.placeholders.packetStream.body}")
}

private fun toggleSession() {
    val intent = Intent(this, HostSessionService::class.java).setAction(action)
    try {
        if (action == HostSessionService.ACTION_START_SESSION && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    } catch (error: SecurityException) {
        localStartError = "Session start blocked: ${error.javaClass.simpleName}"
    } catch (error: IllegalStateException) {
        localStartError = "Session start blocked: ${error.javaClass.simpleName}"
    }
    renderDashboard()
}
```

**Apply:** Add QR scan primary action and visible manual entry action beside desktop link state. Keep same simple Activity style unless planner explicitly refactors. QR/stale endpoint errors should update visible state and allow rescan/manual edit.

---

### `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` (service, event-driven)

**Analog:** same file.

**Foreground/session boundary** (lines 122-153, 212-225):
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_STOP_SESSION -> stopSession()
        ACTION_START_SESSION, null -> startSession()
    }
    return START_STICKY
}

private fun startSession() {
    val gate = permissionGateState()
    if (!canStartWithPermissionGate(gate)) {
        currentState = HostSessionState(
            phase = HostSessionPhase.ERROR,
            lastError = blockedPermissionMessage(gate),
        )
        stopSelf()
        return
    }

    currentState = HostSessionState(phase = HostSessionPhase.STARTING)
    if (!startHostForegroundSafely()) {
        stopSelf()
        return
    }
    startMotionCapture()
}

private fun startHostForeground() {
    ensureNotificationChannel()
    startForeground(NOTIFICATION_ID, notification)
    currentState = currentState.copy(foregroundActive = true)
}
```

**Apply:** Phase 3 session networking must not bypass the foreground active-session model. If adding a desktop-control client to the service, start it only from explicit user pairing/connect flow and stop it with the session.

---

### `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt` (utility/model, request-response)

**Analog:** same file.

**Capability state pattern** (lines 17-30, 49-83):
```kotlin
data class PermissionGateState(
    val bluetoothPermissionModel: BluetoothPermissionModel,
    val bluetoothScan: CapabilityStatus,
    val bluetoothConnect: CapabilityStatus,
    val locationScanCompatibility: CapabilityStatus,
    val motionSensors: CapabilityStatus,
    val vibration: CapabilityStatus,
    val lanNetwork: CapabilityStatus,
) {
    val canStartSession: Boolean =
        bluetoothScan.state == CapabilityState.AVAILABLE &&
            bluetoothConnect.state == CapabilityState.AVAILABLE &&
            motionSensors.state == CapabilityState.AVAILABLE
}

object PermissionGate {
    @JvmStatic
    fun evaluate(input: PermissionGateInput): PermissionGateState {
        return PermissionGateState(
            bluetoothPermissionModel = model,
            bluetoothScan = bluetoothScan(input, android12OrNewer, locationCompatible),
            bluetoothConnect = bluetoothConnect(input, android12OrNewer),
            locationScanCompatibility = locationCompatible,
            motionSensors = motionSensors(input),
            vibration = hardwareCapability(...),
            lanNetwork = hardwareCapability(...),
        )
    }
}
```

**Apply:** Use the existing LAN capability as preflight/display signal. Do not make LAN service discovery part of normal pairing.

---

### Android Unit Tests (test, transform/file-I/O/event-driven)

**Analog:** `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt`

**Test runner shape** (lines 31-42):
```kotlin
fun main() {
    initialStateUsesRequiredShellCopyAndCollapsedDebugPanels()
    connectedSessionShowsServiceErrorAndLastGunEvent()
    activeControlsShowMultiplePressedButtons()
    activeControlsShowCompositeStickAxis()
    motionProviderShowsCapabilitiesPreviewAndBaseline()
    calibrationGraphShowsMarkProgressAndLatency()
    recenterShowsCountdownEmissionAndReloadVisibility()
    debugDetailsStayCollapsedUntilToggled()
    futureDesktopAndPacketSurfacesStayInactive()
    phoneHapticsStayLocalOnly()
}
```

**Assertion style** (lines 44-66, 68-96):
```kotlin
private fun initialStateUsesRequiredShellCopyAndCollapsedDebugPanels() {
    val state = DashboardState.initial(permissionGateState())

    expectEquals("app title", "BT Gun Host", state.appTitle)
    expectEquals("permission gate title", "Enable host permissions", state.permission.title)
    expectFalse("ble debug collapsed", state.debugPanels.bleProvenance.expanded)
}

private fun connectedSessionShowsServiceErrorAndLastGunEvent() {
    val state = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(
            phase = HostSessionPhase.CONNECTED,
            foregroundActive = true,
            lastError = "last gatt status ok",
        ),
    )

    expectEquals("gun value", "connected", state.gunConnection.value)
}
```

**Gradle registration pattern** (`android-host/app/build.gradle.kts` lines 32-60):
```kotlin
tasks.withType<Test>().configureEach {
    val unitTestTask = this
    failOnNoDiscoveredTests = false

    filter {
        isFailOnNoMatchingTests = false
    }

    doLast {
        listOf(
            "com.btgun.host.permissions.PermissionGateTest",
            "com.btgun.host.ui.DashboardStateTestKt",
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

**Apply:** Add plain `main()` unit tests and register new test classes in Gradle. Cover QR/manual parse, TTL, trust mismatch, no secret persistence, heartbeat state transitions, haptic type reservation, and packet stream still inactive.

---

### `desktop-companion/build.gradle.kts` (config, build)

**Analog:** `android-host/app/build.gradle.kts`

**Java/Kotlin target pattern** (lines 1-6, 20-30):
```kotlin
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

**Apply:** Desktop will differ in plugins (`org.jetbrains.kotlin.jvm`, serialization/application if chosen) but should pin JVM 17 and register main-style tests or standard JVM tests consistently. Human-verify Ktor/OkHttp/ZXing/serialization package versions before adding.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt` (service, request-response)

**Analog:** `android-host/app/src/main/java/com/btgun/host/permissions/PermissionGate.kt`

**Pure decision function pattern** (lines 55-83, 156-184):
```kotlin
@JvmStatic
fun evaluate(input: PermissionGateInput): PermissionGateState {
    val android12OrNewer = input.sdkInt >= 31
    val model = if (android12OrNewer) {
        BluetoothPermissionModel.ANDROID_12_NEARBY_DEVICES
    } else {
        BluetoothPermissionModel.LEGACY_LOCATION_SCAN
    }

    return PermissionGateState(
        bluetoothPermissionModel = model,
        bluetoothScan = bluetoothScan(input, android12OrNewer, locationCompatible),
        bluetoothConnect = bluetoothConnect(input, android12OrNewer),
        lanNetwork = hardwareCapability(
            available = input.hasNetwork,
            availableLabel = "LAN network available",
            unavailableLabel = "LAN network unavailable",
        ),
    )
}

private fun available(label: String, detail: String): CapabilityStatus =
    CapabilityStatus(CapabilityState.AVAILABLE, label, detail)
```

**Apply:** Make registry mostly pure and testable: create one active short-lived session, generate session id, desktop nonce, QR secret, 6-digit code, expiry, and rate-limit counters. Return explicit accepted/rejected states with reasons rather than hidden mutation.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/security/DesktopIdentityStore.kt` (service/store, file-I/O)

**Analog:** `android-host/app/src/main/java/com/btgun/host/motion/AimCalibration.kt`

**Store interface shape** (lines 520-535):
```kotlin
class AimCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AimCalibration? =
        AimCalibrationCodec.decode(preferences.getString(KEY_CALIBRATION, null))

    fun save(calibration: AimCalibration) {
        preferences.edit()
            .putString(KEY_CALIBRATION, AimCalibrationCodec.encode(calibration))
            .apply()
    }
}
```

**Apply:** Desktop store should expose simple `loadOrCreateIdentity()` / `fingerprint()` behavior and isolate Java `KeyStore`/certificate generation. Never log private key or one-time secrets; show only fingerprint.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` (component, event-driven)

**Analog:** `android-host/app/src/main/java/com/btgun/host/MainActivity.kt`

**State render loop pattern** (lines 45-63, 156-207):
```kotlin
private val refreshRunnable = object : Runnable {
    override fun run() {
        renderDashboard()
        handler.postDelayed(this, REFRESH_INTERVAL_MS)
    }
}

override fun onResume() {
    super.onResume()
    handler.post(refreshRunnable)
}

override fun onPause() {
    handler.removeCallbacks(refreshRunnable)
    super.onPause()
}

private fun renderDashboard() {
    val latestServiceState = HostSessionService.latestState
    val dashboard = DashboardState.from(...)
    setField("desktop_link", "${dashboard.placeholders.desktopLink.title}: ${dashboard.placeholders.desktopLink.body}")
}
```

**Apply:** Swing window should be a thin render surface over pairing/session state: start pairing button, QR image, visible host/port/code/fingerprint fallback, session state, heartbeat age, last error. Keep desktop UI protocol-portable; no Windows/macOS HID assumptions.

---

### `docs/protocol/lan-pairing-v1.md` (spec, transform)

**Analog:** no close source analog read; use research contract.

**Apply:** Document exact QR URI/schema, WSS endpoint, proof transcript, fingerprint format, expiry/replay/rate-limit rules, control envelope fields/types, heartbeat behavior, diagnostics fields, and reserved haptic type with no payload/ack semantics.

## Shared Patterns

### Android State Surfaces
**Source:** `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` lines 110-180
**Apply to:** `DashboardState.kt`, `MainActivity.kt`, session state files
```kotlin
data class DashboardState(
    val appTitle: String,
    val permission: PermissionUiState,
    val primaryActionLabel: String,
    val currentError: DashboardField,
    val placeholders: DashboardPlaceholders,
)
```
Use immutable UI state and render strings from state, not from networking callbacks directly.

### Foreground Session Ownership
**Source:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` lines 122-153, 200-209
**Apply to:** Android control client and pairing lifecycle
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_STOP_SESSION -> stopSession()
        ACTION_START_SESSION, null -> startSession()
    }
    return START_STICKY
}
```
Do not create an invisible independent Android network session.

### Error Handling
**Source:** `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` lines 182-198 and `MainActivity.kt` lines 226-237
**Apply to:** Android pairing/control paths
```kotlin
try {
    startForeground()
    true
} catch (error: SecurityException) {
    currentState = HostSessionState(
        phase = HostSessionPhase.ERROR,
        lastError = "Foreground service blocked: ${error.javaClass.simpleName}",
    )
    false
}
```
Surface class-name-level local errors to UI; redact secrets/proofs/codes from logs and diagnostic state.

### SharedPreferences Store
**Source:** `android-host/app/src/main/java/com/btgun/host/motion/AimCalibration.kt` lines 520-535
**Apply to:** Android trusted desktop identity store
```kotlin
private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

fun load(): AimCalibration? =
    AimCalibrationCodec.decode(preferences.getString(KEY_CALIBRATION, null))
```
Use for non-secret metadata only.

### Main-Function Test Style
**Source:** `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` lines 31-42 and `android-host/app/build.gradle.kts` lines 32-60
**Apply to:** Android tests and, if chosen, desktop JVM tests
```kotlin
fun main() {
    initialStateUsesRequiredShellCopyAndCollapsedDebugPanels()
    connectedSessionShowsServiceErrorAndLastGunEvent()
}
```
Register new tests in Gradle until a conventional test framework is introduced.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | service/controller | request-response/streaming | No desktop source tree and no WebSocket server code exist. Use `03-RESEARCH.md` Ktor WSS pattern and keep interface testable. |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` | test | request-response/streaming | No local streaming server test analog. Use desktop JVM test harness plus Ktor test tools if dependency verified. |
| `docs/protocol/lan-pairing-v1.md` | spec | transform | Existing protocol docs are reverse-engineering reports, not implementation protocol specs. Use research contract. |

## Metadata

**Analog search scope:** `android-host/app/src/main/java`, `android-host/app/src/test/java`, `android-host/app/build.gradle.kts`, `docs/protocol`, full `rg --files` source list.
**Files scanned:** 48 tracked source/docs/build files from `rg --files`.
**Pattern extraction date:** 2026-06-07
