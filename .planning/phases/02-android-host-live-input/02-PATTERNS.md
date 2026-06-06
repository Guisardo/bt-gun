# Phase 02: Android Host Live Input - Pattern Map

**Mapped:** 2026-06-06  
**Files analyzed:** 20 target files  
**Analogs found:** 15 / 20

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `android-host/settings.gradle.kts` | config | build/config | `android-diagnostic/settings.gradle.kts` | exact |
| `android-host/build.gradle.kts` | config | build/config | `android-diagnostic/build.gradle.kts` | exact |
| `android-host/app/build.gradle.kts` | config | build/config | `android-diagnostic/app/build.gradle.kts` | exact |
| `android-host/app/src/main/AndroidManifest.xml` | config | request-response/session | `android-diagnostic/app/src/main/AndroidManifest.xml` | role-match |
| `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | component/controller | request-response/event-driven | `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` | partial |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | service | event-driven/streaming | none | no-local-analog |
| `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt` | service | event-driven/streaming | `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` | exact logic, wrong owner |
| `android-host/app/src/main/java/com/btgun/host/ble/IpegaPacketParser.kt` | utility | transform | `fixtures/ipega/normalized/README.md` | data-shape match |
| `android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt` | utility/service | event-driven | `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` | exact |
| `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` | model | streaming/transform | `fixtures/ipega/normalized/README.md` | role-match |
| `android-host/app/src/main/java/com/btgun/host/model/Provenance.kt` | model | transform | `fixtures/ipega/normalized/README.md` | role-match |
| `android-host/app/src/main/java/com/btgun/host/motion/MotionAimProvider.kt` | service | streaming/event-driven | none | no-local-analog |
| `android-host/app/src/main/java/com/btgun/host/motion/PreviewAimMapper.kt` | utility | transform | none | no-local-analog |
| `android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt` | utility | event-driven/transform | `fixtures/ipega/normalized/README.md` | data-shape match |
| `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` | service | request-response | `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` | exact |
| `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt` | test | transform | `tools/phase1/validate-fixtures.mjs` | role-match |
| `android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt` | test | event-driven | `tools/phase1/validate-fixtures.mjs` | partial |
| `android-host/app/src/test/java/com/btgun/host/motion/MotionProviderSelectionTest.kt` | test | transform | none | no-local-analog |
| `android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt` | test | streaming/transform | `tools/phase1/validate-fixtures.mjs` | role-match |
| `android-host/app/src/test/java/com/btgun/host/ui/DashboardStateTest.kt` | test | request-response | none | no-local-analog |

## Pattern Assignments

### `android-host/settings.gradle.kts` (config, build/config)

**Analog:** `android-diagnostic/settings.gradle.kts`

**Copy pattern** (lines 1-18):
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-host"
include(":app")
```

Planner note: only change `rootProject.name`; keep repositories and `FAIL_ON_PROJECT_REPOS`.

### `android-host/build.gradle.kts` (config, build/config)

**Analog:** `android-diagnostic/build.gradle.kts`

**Copy pattern** (lines 1-4):
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

Planner note: no new Gradle plugins or third-party packages unless routed through human dependency review.

### `android-host/app/build.gradle.kts` (config, build/config)

**Analog:** `android-diagnostic/app/build.gradle.kts`

**Copy pattern** (lines 1-28):
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btgun.host"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.btgun.host"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase2-host"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

Planner note: add test source files under `src/test`; keep local unit tests package-free if possible.

### `android-host/app/src/main/AndroidManifest.xml` (config, session/request-response)

**Analog:** `android-diagnostic/app/src/main/AndroidManifest.xml`

**Permission pattern** (lines 1-16):
```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="false" />

<uses-permission
    android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission
    android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.VIBRATE" />
```

**Activity pattern** (lines 18-32):
```xml
<application
    android:allowBackup="false"
    android:label="BT Gun Diagnostic"
    android:supportsRtl="true"
    android:theme="@style/AppTheme">
    <activity
        android:name=".MainActivity"
        android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenLayout|screenSize"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

Planner note: host manifest must add foreground-service declarations and likely network state/access permissions for Phase 2 placeholders/capability state. There is no local foreground service analog.

### `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` (component/controller, request-response/event-driven)

**Analog:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Imports to reuse selectively** (lines 3-35):
```kotlin
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
```

**Permission state pattern** (lines 300-310):
```kotlin
logReport(
    "permission_state",
    "sdk_int" to Build.VERSION.SDK_INT,
    "bluetooth_enabled" to bluetoothEnabled(),
    "bluetooth_scan" to permissionStatus(Manifest.permission.BLUETOOTH_SCAN),
    "bluetooth_connect" to permissionStatus(Manifest.permission.BLUETOOTH_CONNECT),
    "bluetooth_legacy" to permissionStatus(Manifest.permission.BLUETOOTH),
    "bluetooth_admin" to permissionStatus(Manifest.permission.BLUETOOTH_ADMIN),
    "location_fine" to permissionStatus(Manifest.permission.ACCESS_FINE_LOCATION),
    "location_coarse" to permissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION)
)
```

**Do not copy:** diagnostic log-first Activity layout and manual capture buttons. Product UI must follow `02-UI-SPEC.md`: dashboard first, debug panels collapsed, product events default, inactive desktop/packet placeholders.

### `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` (service, event-driven/streaming)

**Analog:** none.

Use Android platform foreground service APIs. Required local contracts:

- Service owns active BLE session lifetime, not Activity.
- Notification text: `BT Gun Host running - live input active`.
- Start session only after permission gate passes.
- Stop session closes scan/GATT work and foreground service.
- Auto-reconnect only while active session is running; surface state and last error.

### `android-host/app/src/main/java/com/btgun/host/ble/IpegaBleGunAdapter.kt` (service, event-driven/streaming)

**Analog:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**BLE scan match pattern** (lines 76-109):
```kotlin
val serviceUuids = result.scanRecord?.serviceUuids?.joinToString("|").orEmpty()
val serviceMatch = result.scanRecord?.serviceUuids?.any { it.uuid == BLE_SERVICE_UUID } == true
val name = safeDeviceName(result.device)
val nameMatch = name.equals(ARGUN_DEVICE_NAME, ignoreCase = true)
if (!serviceMatch && !nameMatch) {
    return
}
if (gattCandidateDevice != null) {
    return
}
gattCandidateDevice = result.device
stopGattScan()
connectGatt(result.device)
```

**Connect/discover pattern** (lines 116-145):
```kotlin
override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
    if (newState == BluetoothProfile.STATE_CONNECTED) {
        try {
            val started = gatt.discoverServices()
            logReport("ble_gatt_discovery", "state" to "discover_services_requested", "started" to started)
        } catch (error: SecurityException) {
            logReport("ble_gatt_discovery", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
        }
    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        pendingGattOperations.clear()
        gattOperationInFlight = false
    }
}

override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    inspectGattServices(gatt)
}
```

**Scan start pattern** (lines 364-408):
```kotlin
val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BLE_SERVICE_UUID)).build())
val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
try {
    scanner.stopScan(bleScanCallback)
    scanner.startScan(filters, settings, gattScanCallback)
} catch (error: SecurityException) {
    logReport("ble_gatt_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
} catch (error: IllegalStateException) {
    logReport("ble_gatt_scan", "state" to "failed", "error" to error.javaClass.simpleName)
}
```

### `android-host/app/src/main/java/com/btgun/host/ble/GattOperationQueue.kt` (utility/service, event-driven)

**Analog:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Queue pattern** (lines 566-592):
```kotlin
private fun enqueueGattOperation(name: String, start: () -> Boolean) {
    pendingGattOperations.addLast(PendingGattOperation(name, start))
    drainGattOperations()
}

private fun drainGattOperations() {
    if (gattOperationInFlight || pendingGattOperations.isEmpty()) {
        return
    }
    val operation = pendingGattOperations.removeFirst()
    gattOperationInFlight = true
    val started = operation.start()
    logReport("ble_gatt_operation", "state" to if (started) "started" else "start_failed", "operation" to operation.name)
    if (!started) {
        gattOperationInFlight = false
        drainGattOperations()
    }
}

private fun completeGattOperation(callback: String) {
    gattOperationInFlight = false
    logReport("ble_gatt_operation", "state" to "completed", "callback" to callback)
    drainGattOperations()
}
```

Planner note: extract this into a small class with callback/status reporting. Do not leave queue state coupled to Activity.

### `android-host/app/src/main/java/com/btgun/host/ble/IpegaPacketParser.kt` (utility, transform)

**Analog:** `fixtures/ipega/normalized/README.md`

**Fixture schema to parse toward** (lines 3-18):
```markdown
Each non-empty line is one JSON object using schema `btgun.ipega.normalized.v1`.
Required event fields:
`schema`, `fixture_id`, `seq`, `control`, `kind`, `phase`, `value`,
`raw_ref`, `clue_id`, `capture_id`.
```

**Control mapping source:** `docs/protocol/ipega-phase1-hardware.md`

- Trigger: `ARGun KeyPressed` then zero frame.
- Reload: `B8DOWN` / `B8UP`.
- Stick: left `B6`, right `B4`, up `B5`, down `B7`.
- Buttons: X `BA`, Y `B3`, A `B2`, B `B9`, with `DOWN`/`UP`.

Planner note: unknown `fff3` payloads become debug/status events, not product controls.

### `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` (model, streaming/transform)

**Analog:** `fixtures/ipega/normalized/README.md`

**Fields to preserve/extend** (lines 7-18):
```markdown
| `seq` | yes | Positive integer sequence number inside the fixture. |
| `control` | yes | Physical or logical control, for example `trigger`, `reload`, `stick_x`, `x`, or `phone_haptic`. |
| `kind` | yes | Event kind: `button`, `axis`, `connection`, `handshake`, or `haptic_test`. |
| `phase` | yes | Event phase: `down`, `up`, `move`, `observed`, `command`, `ack`, or `fail`. |
| `value` | yes | Normalized button value, axis value, object payload, or observed status. |
```

Planner note: Phase 2 runtime envelope adds `stream`, per-stream `seq`, `capture_elapsed_nanos`, and `emitted_elapsed_nanos`. Keep gun, motion, and status streams separate.

### `android-host/app/src/main/java/com/btgun/host/model/Provenance.kt` (model, transform)

**Analog:** `fixtures/ipega/normalized/README.md`

**Evidence-link fields** (lines 16-25):
```markdown
| `raw_ref` | yes | `local://.evidence/phase1/raw/...`, `local://.evidence/phase1/hci/...`, or app-log ref. |
| `clue_id` | yes | Static clue id that motivated this capture/test. |
| `capture_id` | yes | Capture manifest row id linking hardware evidence to this normalized event. |

Coverage rules:
- Static clues are hypotheses until linked to hardware capture rows and normalized fixtures.
- Verified status is only valid when static clue, hardware capture, and normalized fixture are all linked.
```

Planner note: provenance is debug-only in product UI, but retain raw ASCII/hex, BLE characteristic, clue id, capture id, and confidence when available.

### `android-host/app/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt` (utility, event-driven/transform)

**Analog:** fixture semantics plus Phase 2 context.

Pattern to implement:

- On reload down: emit reload down immediately.
- If still held after `2_000 ms`: emit separate `recenter` event.
- On reload up: emit reload up always.
- If released early: no recenter.

Use monotonic elapsed time. Do not consume reload semantics.

### `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` (service, request-response)

**Analog:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Phone vibration pattern** (lines 1048-1075):
```kotlin
val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
if (vibrator == null) {
    logReport("phone_vibrate", "state" to "unavailable", "reason" to "missing_vibrator_service")
    return
}
val hasVibrator = if (Build.VERSION.SDK_INT >= 11) vibrator.hasVibrator() else true
if (!hasVibrator) {
    logReport("phone_vibrate", "state" to "unavailable", "reason" to "device_reports_no_vibrator")
    return
}
try {
    if (Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(VibrationEffect.createOneShot(PHONE_VIBRATE_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(PHONE_VIBRATE_DURATION_MS)
    }
    logReport("phone_vibrate", "state" to "started")
} catch (error: SecurityException) {
    logReport("phone_vibrate", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
}
```

Planner note: do not copy physical gun rumble probes. Phase 2 haptics are phone local test only.

### `android-host/app/src/main/java/com/btgun/host/motion/MotionAimProvider.kt` (service, streaming/event-driven)

**Analog:** none.

Pattern from decisions:

- Provider order: game rotation vector, rotation vector, gyro plus gravity/accelerometer, then gravity/accelerometer tilt fallback.
- Emit provider name, capability flags, and monotonic capture timestamp.
- If unavailable, report explicit unavailable status and do not fake aim.
- If provider changes mid-session, publish status/product event for provider change.

### `android-host/app/src/main/java/com/btgun/host/motion/PreviewAimMapper.kt` (utility, transform)

**Analog:** none.

Pattern from decisions/UI spec:

- Android aim mapping is preview/calibration only.
- Keep final game/profile mapping out of Android.
- Expose stable preview X/Y values and baseline timestamp.
- Keep square aim pad stable even when motion unavailable.

## Test Pattern Assignments

### Parser and envelope tests

**Analog:** `tools/phase1/validate-fixtures.mjs`

**Fixture list and required events** (lines 32-68):
```javascript
const REQUIRED_FULL_FIXTURES = [
  "fixtures/ipega/normalized/handshake.jsonl",
  "fixtures/ipega/normalized/trigger.jsonl",
  "fixtures/ipega/normalized/reload.jsonl",
  "fixtures/ipega/normalized/joystick.jsonl",
  "fixtures/ipega/normalized/buttons-xyab.jsonl",
  "fixtures/ipega/normalized/haptics.jsonl",
];

const REQUIRED_FULL_EVENTS = [
  ["trigger", "down"],
  ["trigger", "up"],
  ["reload", "down"],
  ["reload", "up"],
  ["stick_left", "down"],
  ["stick_left", "up"],
  ["stick_right", "down"],
  ["stick_right", "up"],
  ["stick_up", "down"],
  ["stick_up", "up"],
  ["stick_down", "down"],
  ["stick_down", "up"],
  ["x", "down"],
  ["x", "up"],
  ["y", "down"],
  ["y", "up"],
  ["a", "down"],
  ["a", "up"],
  ["b", "down"],
  ["b", "up"],
  ["phone_haptic", "observed"],
];
```

**JSONL parse/validate style** (lines 92-109, 165-191):
```javascript
function parseJsonlText(text, source) {
  const rows = [];
  const lines = text.split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index].trim();
    if (!line) continue;
    rows.push({ line: index + 1, row: JSON.parse(line) });
  }
  return rows;
}
```

Planner note: Kotlin unit tests should load committed JSONL fixtures as test resources or plain files and assert parser outputs product events plus optional provenance.

### Validation commands

Use `02-VALIDATION.md` commands:

```bash
node tools/phase1/validate-fixtures.mjs --full && ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest
```

Full wave:

```bash
node tools/phase1/validate-fixtures.mjs --full && ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug
```

## Shared Patterns

### Bluetooth Permissions

**Source:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Apply to:** permission gate, BLE adapter, session service.

```kotlin
private fun hasBluetoothScanPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= 31) {
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

private fun hasBluetoothConnectPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= 31) {
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
```

### Error Handling

**Source:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Apply to:** BLE scan/connect/GATT operations, haptics.

```kotlin
try {
    scanner.startScan(filters, settings, gattScanCallback)
} catch (error: SecurityException) {
    logReport("ble_gatt_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
} catch (error: IllegalStateException) {
    logReport("ble_gatt_scan", "state" to "failed", "error" to error.javaClass.simpleName)
}
```

### Constants

**Source:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Apply to:** BLE adapter/parser.

```kotlin
private const val ARGUN_DEVICE_NAME = "ARGunGame"
private val BLE_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val FFF1_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private val BLE_CHARACTERISTIC_TARGETS = arrayOf(
    Triple(FFF1_CHARACTERISTIC_UUID, "ARGUN2021-BLE-001", "read_or_notify_candidate"),
    Triple(UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"), "ARCHER-BLE-001", "notify_candidate"),
)
```

### Runtime Logging / Status Emission

**Source:** `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt`

**Apply to:** status stream and debug panels.

```kotlin
val allFields = linkedMapOf<String, Any?>(
    "schema" to schema,
    "report" to report,
    "ts_elapsed_ms" to SystemClock.elapsedRealtime(),
    "session_id" to sessionId
)
```

Planner note: product code should emit typed status models, not only JSON log strings. Keep `SystemClock.elapsedRealtime()` / elapsed nanos convention.

### UI Dashboard Contract

**Source:** `02-UI-SPEC.md`

**Apply to:** `MainActivity.kt`, dashboard state tests.

Required order: top app bar, session status strip, action row, live input group, motion group, recenter group, inactive desktop/packet placeholders, haptic group, debug panels. Native Android manual UI; no Compose, AndroidX, shadcn, or third-party UI library without dependency review.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `HostSessionService.kt` | service | event-driven/streaming | Diagnostic connection is Activity-owned; Phase 2 needs foreground service ownership. |
| `MotionAimProvider.kt` | service | streaming/event-driven | No local SensorManager code exists. |
| `PreviewAimMapper.kt` | utility | transform | No local aim preview or mapping code exists. |
| `MotionProviderSelectionTest.kt` | test | transform | No local Android sensor tests exist. |
| `DashboardStateTest.kt` | test | request-response | Diagnostic UI is log-first and not product architecture. |

## Metadata

**Analog search scope:** `android-diagnostic/`, `tools/phase1/`, `fixtures/ipega/normalized/`, `docs/protocol/`, phase docs.  
**Files scanned:** 13 required files plus phase docs.  
**Pattern extraction date:** 2026-06-06.
