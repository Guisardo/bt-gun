# Phase 2: Android Host Live Input - Research

**Researched:** 2026-06-06 [VERIFIED: local]  
**Domain:** Native Android Kotlin host app, BLE GATT live input, SensorManager motion aim, foreground active session, normalized event streams [VERIFIED: local] [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data] [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview]  
**Confidence:** HIGH for Phase 1 BLE/control evidence and phase boundary; MEDIUM for production Android service/UI details until implementation and physical-device validation run [VERIFIED: local]

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Gun Connection Flow
- **D-01:** Use auto-connect as the normal path after Bluetooth and permission checks pass: scan for `ARGunGame` advertising service `fff0`, connect immediately, and show progress.
- **D-02:** Auto-reconnect while the active session is running. Show connection state and last error instead of silently retrying.
- **D-03:** Use a guided first-run permission gate for Bluetooth, location, and sensor permissions. Explain missing state only when blocked.
- **D-04:** Own the live BLE connection in a visible foreground service so it can survive screen changes and backgrounding.

### Normalized Live Sample Shape
- **D-05:** Keep separate internal streams for gun, motion, and status in Phase 2. Merge into transport frames in later LAN phases.
- **D-06:** Use a common envelope across streams: `stream`, per-stream `seq`, `capture_elapsed_nanos`, and `emitted_elapsed_nanos`.
- **D-07:** Product events are default. A debug-provenance toggle exposes Phase 1 details such as raw ASCII/hex, BLE characteristic, `clue_id`, `capture_id`, and semantic confidence.
- **D-08:** Preserve physical stick direction events as `stick_left`, `stick_right`, `stick_up`, and `stick_down`, and derive X/Y axes for local preview.

### Motion Aim and Recenter
- **D-09:** Select motion provider automatically: game rotation vector first, then rotation vector, then gyro plus gravity/accelerometer, then gravity/accelerometer tilt fallback.
- **D-10:** Android applies local mapped aim in Phase 2 for preview/calibration only. Final desktop/game aim mapping still belongs to desktop profiles.
- **D-11:** Reload down/up events always emit. If reload remains held for two seconds, also emit a `recenter` event without consuming normal reload semantics.
- **D-12:** Show immediate recenter feedback with the current zero/baseline timestamp and brief recenter status.

### Android Status Surface
- **D-13:** Main session screen is an operational dashboard showing connection state, last gun event, motion provider, preview aim, recenter state, foreground-service status, and current error line.
- **D-14:** Put raw BLE/provenance, permission state, and GATT status behind expandable debug panels.
- **D-15:** Show inactive placeholder cards for desktop link and packet stream: "not built yet" / "pending Phase 3 or Phase 4".
- **D-16:** Haptic status in Phase 2 means phone vibrator capability and last local test. Desktop-origin haptic commands wait for Phase 4.

### the agent's Discretion
- Choose exact Android module/package split, UI toolkit, foreground-service notification copy, stream class names, and local preview math during planning.
- Choose exact debug panel layout, as long as product events stay primary and provenance remains available.
- Choose exact sample rates and buffering strategy during planning, as long as monotonic capture/emitted timestamps remain present.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

- LAN pairing and authenticated desktop session remain Phase 3.
- UDP input frame transport, reliable control channel, and desktop-origin phone haptic commands remain Phase 4.
- Final desktop aim profiles and game/platform mapping remain Phase 8.
- Physical gun motor rumble remains deferred/v2 unless later evidence proves a command path.
</user_constraints>

## Summary

Phase 2 should create a new production Android host surface, not promote the Phase 1 diagnostic Activity as product architecture. [VERIFIED: local] Reuse the diagnostic BLE findings and small parser fragments behind a production `IpegaBleGunAdapter`, but keep diagnostic log-first UI, rumble probes, and evidence-marker actions out of the host app. [VERIFIED: local]

Primary recommendation: add a production Android app/project rooted at `android-host/` with package `com.btgun.host`, reuse the already-approved Android Gradle/Kotlin plugin versions, and keep Phase 2 package-free beyond platform APIs unless a human explicitly approves new Gradle coordinates. [VERIFIED: local]

The first implementation plan should split work into: production module scaffold, permission/session service, BLE adapter/parser, normalized event pipeline plus fixture-backed parser tests, motion provider/recenter logic, UI dashboard from `02-UI-SPEC.md`, and physical-device validation. [VERIFIED: local]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Permission gate | Android app UI | Foreground service | User grants Bluetooth/location/network permissions through app UI; service should only start after prerequisites pass. [VERIFIED: local] [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] |
| BLE scan/connect/notify | Android foreground service | Gun adapter | Service owns active session lifetime; adapter owns `ARGunGame`/`fff0`/`fff3` details. [VERIFIED: local] [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data] |
| Gun packet parsing | Android gun adapter | Normalized event pipeline | Hardware-specific ASCII/hex frames must stop before product UI and later transport. [VERIFIED: local] |
| Gun/motion/status stream envelope | Android normalized event pipeline | UI state store | Phase 2 emits internal streams with comparable monotonic timestamps; later LAN phases frame them for transport. [VERIFIED: local] |
| Motion provider selection | Android sensor subsystem | UI preview | Android owns SensorManager access and provider capability flags; desktop owns final game mapping later. [VERIFIED: local] [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_position] |
| Reload-hold recenter | Android normalized event pipeline | Motion subsystem | Reload down/up must remain gun events while the pipeline also emits a recenter event after the hold threshold. [VERIFIED: local] |
| Dashboard | Android app UI | Session service state | UI shows live operational state and inactive future placeholders, not LAN/desktop behavior. [VERIFIED: local] |
| Phone haptic local test | Android app/service | UI haptic status | Phase 2 only proves phone vibrator capability and local test result; desktop-origin haptics are Phase 4. [VERIFIED: local] |
| LAN pairing/UDP/control | Out of scope | - | CONTEXT defers authenticated desktop session and transport frames. [VERIFIED: local] |

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ANDR-01 | User can grant required Android Bluetooth, nearby device, sensor, and LAN permissions from the Android host app. [VERIFIED: local] | Implement guided permission/capability gate for Android 12+ Bluetooth runtime permissions, legacy location compatibility, declared LAN permissions, and sensor capability status. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] [CITED: https://developer.android.com/develop/connectivity/wifi/wifi-permissions] |
| ANDR-02 | User can connect the Android host app to the physical iPega gun. [VERIFIED: local] | Use Phase 1 proven BLE GATT path: scan for `ARGunGame` advertising service `0000fff0-0000-1000-8000-00805f9b34fb`, connect via app-level GATT without OS pairing, discover service, enable `fff3` notification. [VERIFIED: local] [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server] |
| ANDR-03 | Android host app emits normalized events for trigger, reload, joystick, X/Y/A/B, and connection state. [VERIFIED: local] | Parse Phase 1 `fff3` payloads into product events while retaining optional provenance from fixtures and capture manifest. [VERIFIED: local] |
| ANDR-04 | Android host app samples motion aim data with monotonic capture timestamps, using rotation-vector/game-rotation-vector, gyroscope, accelerometer, and gravity providers as available. [VERIFIED: local] | Use SensorManager provider selection order from CONTEXT and SensorEvent timestamps for capture time. [VERIFIED: local] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent] |
| ANDR-05 | Android host app merges gun input and motion sensor data into ordered normalized input samples with sensor provider and capability metadata. [VERIFIED: local] | Keep separate streams but use common envelope and comparable elapsed-nanos timestamps; UI can display latest per stream and status. [VERIFIED: local] |
| ANDR-06 | Holding reload for two seconds recenters motion aim without preventing normal reload press/release events. [VERIFIED: local] | Add a reload hold state machine that emits reload down immediately, recenter after 2,000 ms if still down, and reload up on release. [VERIFIED: local] |
| ANDR-08 | Android host app shows active session status for gun connection, desktop link, packet stream, and haptic feedback. [VERIFIED: local] | Implement `02-UI-SPEC.md` dashboard: gun connection, inactive desktop/packet placeholders, motion provider, service state, local phone haptic status, and debug panels. [VERIFIED: local] |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- Use `$caveman ultra` style for agent messages; docs remain short, factual, and agent-facing. [VERIFIED: local]
- Phase 2 current focus is Android Host Live Input. [VERIFIED: local]
- Production code should follow existing patterns once source tree exists; current Android code is diagnostic-only. [VERIFIED: local]
- Android-to-desktop transport is Wi-Fi/LAN in v1, but Phase 2 must not build LAN pairing, UDP, reliable control, or desktop haptic commands. [VERIFIED: local]
- Desktop owns final aim mapping and profiles; Android preview aim is local calibration only. [VERIFIED: local]
- Use monotonic sensor and elapsed realtime timestamps, not wall-clock timestamps. [VERIFIED: local] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent]
- Preserve Phase 1 evidence rule and provenance when exposing debug details. [VERIFIED: local]

## Standard Stack

### Core

| Library/API | Version | Purpose | Why Standard |
|-------------|---------|---------|--------------|
| Android native app, Kotlin | Existing `org.jetbrains.kotlin.android` 2.0.21 in `android-diagnostic/build.gradle.kts` [VERIFIED: local] | Production Android host app code. [VERIFIED: local] | Matches repo-approved diagnostic build stack and uses platform Bluetooth/sensor/service APIs directly. [VERIFIED: local] |
| Android Gradle Plugin | Existing `com.android.application` 8.7.3 [VERIFIED: local] | Android app build. [VERIFIED: local] | Already human-reviewed for Phase 1 diagnostic build; reuse avoids new dependency decisions. [VERIFIED: local] |
| Android Bluetooth LE APIs | Platform APIs, compile SDK 35 available locally [VERIFIED: local] | Scan, connect GATT, discover services, read/subscribe characteristics. [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data] | Official BLE API path supports service discovery and notification-based characteristic updates. [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data] |
| Android SensorManager | Platform APIs [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview] | Motion provider selection, sampling, timestamps, preview aim. [CITED: https://developer.android.com/reference/android/hardware/SensorManager] | Official sensor APIs provide rotation-vector, game-rotation-vector, gyro, accelerometer, and gravity sensor access. [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_position] [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion] |
| Android foreground service | Platform APIs [CITED: https://developer.android.com/develop/background-work/services/fgs/service-types] | Visible active BLE session lifecycle. [VERIFIED: local] | Official BLE background guidance points to foreground service for long-running user-visible BLE work. [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/background] |
| Android platform Views | Platform APIs [VERIFIED: local] | Native operational dashboard from `02-UI-SPEC.md`. [VERIFIED: local] | Existing repo has no Compose/AndroidX dependency; manual native UI satisfies approved UI contract without new package gate. [VERIFIED: local] |

### Supporting

| Library/API | Version | Purpose | When to Use |
|-------------|---------|---------|-------------|
| Android Vibrator/VibrationEffect | Platform APIs [VERIFIED: local] [CITED: https://developer.android.com/reference/android/os/Vibrator] | Local phone haptic capability/test only. [VERIFIED: local] | Use for `Test phone vibration`; do not implement desktop-origin command path in Phase 2. [VERIFIED: local] |
| JVM local unit tests | Gradle Android plugin default test tasks available [VERIFIED: local] | Parser, event envelope, recenter state machine, provider-selection pure tests. [CITED: https://developer.android.com/training/testing/local-tests] | Use for no-hardware feedback under 30 seconds. [VERIFIED: local] |
| Phase 1 fixture validator | `node tools/phase1/validate-fixtures.mjs --full` passes locally [VERIFIED: local] | Guard control fixture provenance and parser test source data. [VERIFIED: local] | Run before/after parser changes to avoid drifting from Phase 1 evidence. [VERIFIED: local] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| New `android-host/` production app [VERIFIED: local] | Extend `android-diagnostic` in place [ASSUMED] | Extending diagnostic keeps build reuse but risks preserving log-first UI, rumble probes, and Phase 1 evidence workflow in product. [VERIFIED: local] |
| Platform Views [VERIFIED: local] | Jetpack Compose [ASSUMED] | Compose is good for Android UI but adds Gradle dependencies and a design-system decision not needed for the approved operational dashboard. [ASSUMED] |
| Foreground service owns BLE session [VERIFIED: local] | Activity-owned connection [VERIFIED: local] | Activity-only worked for diagnostics but does not satisfy screen-change/background active-session requirement. [VERIFIED: local] |
| Separate gun/motion/status streams [VERIFIED: local] | Unified global stream [ASSUMED] | Unified stream may help later transport, but locked Phase 2 decision keeps internal streams separate. [VERIFIED: local] |

**Installation:**

```bash
# No new package install is recommended for Phase 2 research.
# Reuse existing approved Android build coordinates when scaffolding android-host:
# com.android.application 8.7.3
# org.jetbrains.kotlin.android 2.0.21
#
# Known local Gradle invocation pattern:
ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk \
GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home \
gradle -p android-host testDebugUnitTest
```

**Version verification:** Gradle 9.5.1 is available locally; Android SDK platforms 31, 33, 34, 35, and 36 are installed; ADB 36.0.0 is available; Node 22.12.0 runs the Phase 1 validator. [VERIFIED: local]

## Package Legitimacy Audit

Phase 2 research recommends no new external packages. [VERIFIED: local] Existing Gradle plugin coordinates were human-approved during Phase 1 and should be reused; any new AndroidX, Compose, test, BLE helper, coroutine, or UI package must get a `checkpoint:human-verify` before install/build. [VERIFIED: local]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `com.android.application` 8.7.3 | Gradle/Maven [VERIFIED: local] | Not audited [ASSUMED] | Not audited [ASSUMED] | Android Gradle Plugin upstream [ASSUMED] | Not applicable; existing approved coordinate [VERIFIED: local] | Approved to reuse, no new decision. [VERIFIED: local] |
| `org.jetbrains.kotlin.android` 2.0.21 | Gradle/Maven [VERIFIED: local] | Not audited [ASSUMED] | Not audited [ASSUMED] | Kotlin upstream [ASSUMED] | Not applicable; existing approved coordinate [VERIFIED: local] | Approved to reuse, no new decision. [VERIFIED: local] |

**Packages removed due to slopcheck `[SLOP]` verdict:** none; no new package recommendations. [VERIFIED: local]  
**Packages flagged as suspicious `[SUS]`:** none from research; planner must gate any added dependency. [VERIFIED: local]  
**Graceful degradation:** slopcheck was not run because no npm/PyPI/crates install is recommended and Gradle plugin coordinates already exist locally. [VERIFIED: local]

## Architecture Patterns

### System Architecture Diagram

```text
-------------------------+
| Android Activity UI    |
| Permission gate        |
| Dashboard/debug panels |
+-----------+-------------+
            |
            v
+-------------------------+       +-------------------------+
| HostSessionService      | ----> | Notification            |
| foreground connected    |       | "live input active"     |
+-----------+-------------+       +-------------------------+
            |
            +-----------------------------+
            |                             |
            v                             v
+-------------------------+       +-------------------------+
| IpegaBleGunAdapter      |       | MotionAimProvider       |
| scan ARGunGame/fff0     |       | game rotation -> tilt   |
| connect GATT            |       | SensorEvent timestamps  |
| subscribe fff3          |       +-----------+-------------+
| parse fff3 payloads     |                   |
+-----------+-------------+                   |
            |                                 |
            v                                 v
+----------------------------------------------------------+
| NormalizedEventPipeline                                  |
| gun stream seq + motion stream seq + status stream seq   |
| capture_elapsed_nanos + emitted_elapsed_nanos            |
| reload-hold recenter state machine                       |
+-----------------------------+----------------------------+
                              |
                              v
                 +-------------------------+
                 | UI state / debug export |
                 | product mode default    |
                 | provenance toggle       |
                 +-------------------------+
```

### Recommended Project Structure

```text
android-host/
  settings.gradle.kts
  build.gradle.kts
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/btgun/host/
      MainActivity.kt                  # Dashboard and permission gate
      HostSessionService.kt            # Foreground session owner
      ble/
        IpegaBleGunAdapter.kt          # Scan/connect/GATT/notify
        IpegaPacketParser.kt           # fff3 payload -> gun events
        GattOperationQueue.kt          # Serialized GATT ops
      model/
        NormalizedEvents.kt            # Gun/motion/status envelopes
        Provenance.kt                  # Debug-only Phase 1 fields
      motion/
        MotionAimProvider.kt           # Sensor provider selection
        PreviewAimMapper.kt            # Local preview/calibration only
      recenter/
        ReloadHoldRecenter.kt          # Pure state machine
      haptics/
        PhoneHaptics.kt                # Local phone vibration test
    src/test/java/com/btgun/host/
      IpegaPacketParserTest.kt
      ReloadHoldRecenterTest.kt
      MotionProviderSelectionTest.kt
      NormalizedEventEnvelopeTest.kt
```

### Pattern 1: New Host Module, Reuse Diagnostic Logic Selectively

**What:** Create `android-host` as production code and copy/refactor only proven diagnostic pieces: service UUID constants, `ARGunGame` scan filter, GATT connect/discovery, CCCD notification setup, serialized GATT operation queue, permission-state checks, and phone vibration proof. [VERIFIED: local]

**When to use:** Always for Phase 2 implementation. [VERIFIED: local]

**Do not copy:** diagnostic button list, raw endless log UI, manual evidence markers, physical motor rumble candidates, AES/`fff5` handshake probes, and test-only report names. [VERIFIED: local]

### Pattern 2: Serialized GATT Operation Queue

**What:** Queue read, CCCD descriptor writes, and any GATT operation so only one operation is in flight until its callback completes. [VERIFIED: local]

**Why:** Diagnostic code already needed `pendingGattOperations`/`gattOperationInFlight`; Phase 2 should keep that pattern behind `GattOperationQueue`. [VERIFIED: local]

**Example:**

```kotlin
// Source: Phase 1 diagnostic pattern + Android BLE GATT docs.
queue.enqueue("read:fff3") { gatt.readCharacteristic(fff3) }
queue.enqueue("notify:fff3") {
    gatt.setCharacteristicNotification(fff3, true)
    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    gatt.writeDescriptor(cccd)
}
```

### Pattern 3: Product Event Default, Debug Provenance Optional

**What:** Product UI consumes `GunEvent`, `MotionSample`, and `StatusEvent`; debug mode adds `raw_ascii`, `raw_hex`, `characteristic_uuid`, `clue_id`, `capture_id`, and `semantic_confidence`. [VERIFIED: local]

**When to use:** Every live event shown in dashboard or exposed for logs. [VERIFIED: local]

**Implementation detail:** Keep provenance nullable and redacted by default; never require raw evidence fields for normal app operation. [VERIFIED: local]

### Pattern 4: Comparable Monotonic Timestamps

**What:** Envelope every stream item as `{ stream, seq, capture_elapsed_nanos, emitted_elapsed_nanos }`. [VERIFIED: local]

**When to use:** Gun notifications, motion samples, status changes, recenter events, and phone haptic local-test results. [VERIFIED: local]

**Timestamp source:** Use `SensorEvent.timestamp` for sensor capture; use `SystemClock.elapsedRealtimeNanos()` for BLE notification/status capture and for emitted time. [CITED: https://developer.android.com/reference/android/hardware/SensorEvent]

### Pattern 5: Reload Hold State Machine Is Pure and Tested

**What:** Treat reload down/up as gun events first; a separate state machine observes reload down duration and emits one recenter event if held for 2,000 ms. [VERIFIED: local]

**When to use:** Parser emits `reload down`; pipeline calls `ReloadHoldRecenter.onReloadDown(captureNanos)`; timer or event loop emits `recenter` only if reload still held. [VERIFIED: local]

### Anti-Patterns to Avoid

- **Promoting diagnostic Activity to product:** It has evidence-marker UX and raw logs by design; Phase 2 needs operational dashboard. [VERIFIED: local]
- **BLE callbacks updating UI directly:** BLE belongs in service/adapter and should publish normalized state to UI. [ASSUMED]
- **One global sequence only:** Locked decision requires per-stream sequence; global ordering can be derived later from timestamps if needed. [VERIFIED: local]
- **Consuming reload for recenter:** Must always emit reload down/up around recenter. [VERIFIED: local]
- **Pretending LAN/desktop exists:** Desktop link and packet stream must be inactive placeholders. [VERIFIED: local]
- **Using wall-clock timestamps:** Wall-clock jumps break latency comparison; use elapsed realtime/sensor timestamps. [VERIFIED: local] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BLE stack | Custom Bluetooth transport or raw HCI parser | Android BLE GATT APIs | Platform APIs already provide scan, GATT connect, service discovery, reads, writes, and notifications. [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data] |
| Sensor fusion baseline | Custom quaternion fusion before measuring devices | Android game rotation vector / rotation vector first | Android provides fused rotation sensors; custom fusion is only fallback when fused providers are absent. [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_position] |
| Foreground active session | Silent background loop | Android foreground service with notification and `connectedDevice` type | Android foreground services are for user-noticeable ongoing work; Android 14+ requires declared foreground service types. [CITED: https://developer.android.com/develop/background-work/services/fgs/service-types] |
| Parser regression input | Fresh hardware-only tests | Phase 1 normalized fixtures | Fixtures already cover handshake, trigger, reload, joystick, X/Y/A/B, and haptics without hardware. [VERIFIED: local] |
| UI component framework | New Compose/AndroidX component stack | Platform Views for Phase 2 | UI contract is operational and dependency-free; new UI packages create avoidable package gate. [VERIFIED: local] |

**Key insight:** Phase 2 complexity is state ownership and evidence fidelity, not UI polish or new libraries. [VERIFIED: local]

## Common Pitfalls

### Pitfall 1: Treating Android Settings Pairing as Required

**What goes wrong:** Implementation waits for OS Bluetooth pairing even though Phase 1 showed direct settings pairing failed and app-level GATT worked. [VERIFIED: local]

**Why it happens:** Bluetooth devices often look pairable in Settings, but this gun path is app-level BLE GATT. [VERIFIED: local]

**How to avoid:** Scan by `fff0`/`ARGunGame`, connect with `connectGatt(..., TRANSPORT_LE)`, and show Settings pairing only as debug context. [VERIFIED: local]

**Warning signs:** UI says "pair in Settings" or requires bonded Classic device before scan/connect. [VERIFIED: local]

### Pitfall 2: Missing Android 12+ Bluetooth Runtime Permissions

**What goes wrong:** BLE scan/connect fails on modern target SDK because only legacy `BLUETOOTH` permissions exist. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions]

**Why it happens:** Phase 1 physical test ran on SDK 29 where Android 12 nearby-device permissions were not applicable. [VERIFIED: local]

**How to avoid:** Declare/request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` for API 31+, retain legacy/location compatibility for older scan behavior, and report blocked states explicitly. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions]

**Warning signs:** Scan returns no devices with no visible error, or GATT APIs throw `SecurityException`. [VERIFIED: local]

### Pitfall 3: Thinking Motion Sensors Have a Normal Runtime Permission Dialog

**What goes wrong:** Planner blocks motion aim on a nonexistent generic gyro/accelerometer permission flow instead of checking provider availability. [ASSUMED]

**Why it happens:** Phase goal says "sensor permissions"; Android motion sensors are accessed through SensorManager capability APIs, while health/body sensors are a separate permission domain. [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview] [ASSUMED]

**How to avoid:** UI permission gate should show "sensor capability" status for game rotation, rotation vector, gyro, accelerometer, and gravity; do not wait for a generic motion-sensor runtime permission. [ASSUMED]

**Warning signs:** App asks for health/body-sensor permissions for normal gyro aiming. [ASSUMED]

### Pitfall 4: Losing Phase 1 Provenance

**What goes wrong:** Product parser hardcodes `B8DOWN` etc. with no clue/capture link, making noisy/candidate controls hard to debug. [VERIFIED: local]

**Why it happens:** Production UI should hide raw details, but implementation may also delete them from the model. [ASSUMED]

**How to avoid:** Product event model defaults to clean names; debug provenance carries raw BLE payload, characteristic, `clue_id`, `capture_id`, and semantic confidence. [VERIFIED: local]

**Warning signs:** Tests assert only product names and never verify fixture `capture_id`/raw payloads. [VERIFIED: local]

### Pitfall 5: Foreground Service Without Correct Type/Notification

**What goes wrong:** Active BLE session dies or fails policy checks when app backgrounds. [ASSUMED]

**Why it happens:** Modern Android foreground services require user-visible notifications and target-specific foreground service declarations. [CITED: https://developer.android.com/develop/background-work/services/fgs/service-types]

**How to avoid:** Declare a connected-device foreground service, request required foreground-service permissions for target API, call `startForeground()` promptly after service start, and expose service state in UI. [CITED: https://developer.android.com/develop/background-work/services/fgs/launch]

**Warning signs:** Connection only works while Activity is visible or screen stays awake. [ASSUMED]

### Pitfall 6: Motion Preview Becomes Desktop Mapping

**What goes wrong:** Android stores sensitivity/dead-zone/game mapping decisions that later conflict with desktop profiles. [VERIFIED: local]

**Why it happens:** Preview aim needs local X/Y output, so it is tempting to finish game mapping there. [ASSUMED]

**How to avoid:** Label local aim as preview/calibration only and keep provider/raw orientation metadata for later desktop profile mapping. [VERIFIED: local]

**Warning signs:** Android code contains game profile names, Windows/macOS HID axis constants, or persistent desktop mapping settings. [VERIFIED: local]

## Code Examples

Verified patterns from official/local sources:

### BLE `fff3` Notification Subscription

```kotlin
// Source: Android BLE docs + Phase 1 diagnostic implementation.
private fun subscribeFff3(gatt: BluetoothGatt, fff3: BluetoothGattCharacteristic) {
    gatt.setCharacteristicNotification(fff3, true)
    val cccd = fff3.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
    @Suppress("DEPRECATION")
    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    @Suppress("DEPRECATION")
    gatt.writeDescriptor(cccd)
}
```

### Common Envelope

```kotlin
// Source: Phase 2 CONTEXT.md D-05/D-06.
data class LiveEnvelope<T>(
    val stream: StreamKind,
    val seq: Long,
    val captureElapsedNanos: Long,
    val emittedElapsedNanos: Long,
    val payload: T,
    val provenance: Provenance? = null
)
```

### Parser Mapping From Phase 1 Fixtures

```kotlin
// Source: fixtures/ipega/normalized/*.jsonl.
fun parseFff3Payload(bytes: ByteArray): GunEvent? =
    when (bytes.decodeToString()) {
        "B8DOWN" -> GunEvent.Reload(pressed = true)
        "B8UP" -> GunEvent.Reload(pressed = false)
        "B6DOWN" -> GunEvent.StickDirection("stick_left", pressed = true)
        "B6UP" -> GunEvent.StickDirection("stick_left", pressed = false)
        "B4DOWN" -> GunEvent.StickDirection("stick_right", pressed = true)
        "B4UP" -> GunEvent.StickDirection("stick_right", pressed = false)
        "B5DOWN" -> GunEvent.StickDirection("stick_up", pressed = true)
        "B5UP" -> GunEvent.StickDirection("stick_up", pressed = false)
        "B7DOWN" -> GunEvent.StickDirection("stick_down", pressed = true)
        "B7UP" -> GunEvent.StickDirection("stick_down", pressed = false)
        "BADOWN" -> GunEvent.Button("button_x", pressed = true)
        "BAUP" -> GunEvent.Button("button_x", pressed = false)
        "B3DOWN" -> GunEvent.Button("button_y", pressed = true)
        "B3UP" -> GunEvent.Button("button_y", pressed = false)
        "B2DOWN" -> GunEvent.Button("button_a", pressed = true)
        "B2UP" -> GunEvent.Button("button_a", pressed = false)
        "B9DOWN" -> GunEvent.Button("button_b", pressed = true)
        "B9UP" -> GunEvent.Button("button_b", pressed = false)
        "ARGun KeyPressed" -> GunEvent.Trigger(pressed = true, confidence = "candidate")
        else -> if (bytes.all { it == 0.toByte() }) GunEvent.Trigger(pressed = false, confidence = "candidate") else null
    }
```

### Reload-Hold Recenter

```kotlin
// Source: Phase 2 CONTEXT.md D-11.
fun onReloadEvent(pressed: Boolean, nowNanos: Long) {
    emitGunReload(pressed, nowNanos)
    if (pressed) {
        reloadDownAtNanos = nowNanos
        schedule(RELOAD_HOLD_NANOS) {
            if (reloadDownAtNanos != null && !recenterAlreadyEmitted) {
                recenterAlreadyEmitted = true
                emitRecenter(SystemClock.elapsedRealtimeNanos())
            }
        }
    } else {
        reloadDownAtNanos = null
        recenterAlreadyEmitted = false
    }
}
```

### Motion Provider Selection

```kotlin
// Source: Android sensor docs + Phase 2 CONTEXT.md D-09.
val provider =
    sensor(TYPE_GAME_ROTATION_VECTOR)?.let { Provider.GameRotationVector(it) }
        ?: sensor(TYPE_ROTATION_VECTOR)?.let { Provider.RotationVector(it) }
        ?: gyroGravityProviderOrNull()
        ?: accelerometerGravityTiltProviderOrNull()
        ?: Provider.Unavailable
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Legacy Bluetooth permissions only | Android 12+ runtime `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` plus legacy compatibility | Android 12/API 31 [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] | Phase 2 permission gate must branch by SDK. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] |
| Untyped foreground services | Declared foreground service type and target-specific permissions | Android 14/API 34 [CITED: https://developer.android.com/about/versions/14/changes/fgs-types-required] | Phase 2 service manifest should declare connected-device use. [CITED: https://developer.android.com/develop/background-work/services/fgs/service-types] |
| Wall-clock motion timing | Sensor timestamp / elapsed realtime nanos | Current Android sensor docs [CITED: https://developer.android.com/reference/android/hardware/SensorEvent] | Latency and stream ordering must use monotonic time. [CITED: https://developer.android.com/reference/android/hardware/SensorEvent] |
| Activity-owned BLE diagnostic | Foreground session service with UI dashboard | Phase 2 decision [VERIFIED: local] | Production lifecycle must survive screen changes/backgrounding. [VERIFIED: local] |

**Deprecated/outdated:**
- Direct OS pairing requirement for this gun: Phase 1 observed Settings pairing failure and app-level GATT success, so production should not require bonded Classic pairing. [VERIFIED: local]
- Physical gun motor rumble in v1: no verified command path exists; phone vibration is v1 feedback. [VERIFIED: local]
- Diagnostic log-first UI: approved UI spec requires operational dashboard with debug panels collapsed by default. [VERIFIED: local]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Platform Views are sufficient for Phase 2 product dashboard without AndroidX/Compose. | Standard Stack | If UI grows more complex, planner may need dependency checkpoint and UI implementation adjustment. |
| A2 | Normal gyro/accelerometer access should be represented as sensor capability status rather than a runtime "sensor permission" dialog. | Common Pitfalls | If target device/vendor gates sensors unusually, permission gate may need OEM-specific handling. |
| A3 | BLE callbacks should publish through service state rather than update UI directly. | Anti-Patterns | If implementation stays Activity-only, foreground/background success criteria may fail. |
| A4 | Local preview aim math can start simple with orientation deltas and recenter baseline. | Open Questions | If device orientation is unstable, planner may need a dedicated calibration/math task. |

## Open Questions

1. **Exact target module layout**
   - What we know: `android-diagnostic` is diagnostic-only and production should be separate. [VERIFIED: local]
   - What's unclear: Whether to create `android-host` as a sibling standalone Gradle project or convert repo toward a shared multi-project Android root later. [ASSUMED]
   - Recommendation: For Phase 2, create `android-host/` sibling project using same plugin versions; defer monorepo Android refactor until desktop/transport structure exists. [ASSUMED]

2. **Motion preview coordinate math**
   - What we know: provider order is locked and Android sensor docs require attention to device/screen coordinate systems. [VERIFIED: local] [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview]
   - What's unclear: Best local preview transform for the physical phone mounting orientation on the gun. [ASSUMED]
   - Recommendation: Plan a physical-device tuning/manual check that records phone orientation and verifies preview dot directions before treating math as final. [ASSUMED]

3. **Target SDK during Phase 2**
   - What we know: diagnostic uses compile/target SDK 35 and local SDK 35/36 are installed. [VERIFIED: local]
   - What's unclear: Whether production should target 35 immediately or 36. [ASSUMED]
   - Recommendation: Reuse target SDK 35 for Phase 2 to match approved diagnostic build; revisit only with a dependency/toolchain checkpoint. [VERIFIED: local]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Gradle | Android build/test | yes [VERIFIED: local] | 9.5.1 [VERIFIED: local] | Use same `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home` pattern if user-home cache fails. [VERIFIED: local] |
| Android SDK | Android compile/install | yes [VERIFIED: local] | platforms 31, 33, 34, 35, 36 [VERIFIED: local] | None needed. [VERIFIED: local] |
| ADB | physical-device checks | yes [VERIFIED: local] | 36.0.0 / 1.0.41 [VERIFIED: local] | Manual Android Studio deploy if ADB unavailable. [ASSUMED] |
| Connected Android device | manual BLE/session validation | yes [VERIFIED: local] | `3200337647aea561` attached [VERIFIED: local] | Planner can mark physical checks manual-blocked if device disconnects. [ASSUMED] |
| Node | fixture validator | yes [VERIFIED: local] | 22.12.0 [VERIFIED: local] | None; validator is package-free. [VERIFIED: local] |
| Context7 CLI | docs lookup | no [VERIFIED: local] | - | Official Android docs via WebSearch/WebFetch. [VERIFIED: local] |

**Missing dependencies with no fallback:** none for planning; physical gun presence still requires human/device check during execution. [VERIFIED: local]  
**Missing dependencies with fallback:** Context7 CLI unavailable; official docs were checked through web lookup. [VERIFIED: local]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Android Gradle local unit tests plus existing Node fixture validator [VERIFIED: local] |
| Config file | `android-host/app/build.gradle.kts` to be created; existing diagnostic has `android-diagnostic/app/build.gradle.kts` [VERIFIED: local] |
| Quick run command | `node tools/phase1/validate-fixtures.mjs --full && ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` [VERIFIED: local] |
| Full suite command | `node tools/phase1/validate-fixtures.mjs --full && ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest lintDebug` [VERIFIED: local] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ANDR-01 | Permission gate computes required Bluetooth/location/network/sensor capability states by SDK. [VERIFIED: local] | unit | `gradle -p android-host testDebugUnitTest --tests '*PermissionGate*'` | no - Wave 0 |
| ANDR-02 | BLE adapter scans `ARGunGame`/`fff0` and transitions through connect/discover/notify states. [VERIFIED: local] | unit + manual | `gradle -p android-host testDebugUnitTest --tests '*BleGunAdapter*'` | no - Wave 0 |
| ANDR-03 | Parser maps fixture payloads to trigger/reload/stick/X/Y/A/B/status events with provenance. [VERIFIED: local] | unit | `gradle -p android-host testDebugUnitTest --tests '*IpegaPacketParser*'` | no - Wave 0 |
| ANDR-04 | Motion provider selection chooses game rotation, rotation, gyro+gravity, then tilt fallback. [VERIFIED: local] | unit + manual | `gradle -p android-host testDebugUnitTest --tests '*MotionProviderSelection*'` | no - Wave 0 |
| ANDR-05 | Envelopes include stream, per-stream seq, capture and emitted elapsed nanos. [VERIFIED: local] | unit | `gradle -p android-host testDebugUnitTest --tests '*NormalizedEventEnvelope*'` | no - Wave 0 |
| ANDR-06 | Reload hold emits reload down, recenter after 2 seconds, reload up. [VERIFIED: local] | unit | `gradle -p android-host testDebugUnitTest --tests '*ReloadHoldRecenter*'` | no - Wave 0 |
| ANDR-08 | Dashboard state model exposes gun, desktop placeholder, packet placeholder, motion provider, haptic status. [VERIFIED: local] | unit + manual UI | `gradle -p android-host testDebugUnitTest --tests '*DashboardState*'` | no - Wave 0 |

### Sampling Rate

- **Per task commit:** run parser/recenter/provider focused unit test plus `node tools/phase1/validate-fixtures.mjs --full` when parser fixtures change. [VERIFIED: local]
- **Per wave merge:** run `gradle -p android-host testDebugUnitTest` and Phase 1 fixture validator. [VERIFIED: local]
- **Phase gate:** local unit tests green, lint green if available, and manual physical device checklist complete. [VERIFIED: local]

### Wave 0 Gaps

- [ ] `android-host/` production Gradle scaffold. [VERIFIED: local]
- [ ] `android-host/app/src/test/java/com/btgun/host/ble/IpegaPacketParserTest.kt` covers Phase 1 fixtures. [VERIFIED: local]
- [ ] `android-host/app/src/test/java/com/btgun/host/recenter/ReloadHoldRecenterTest.kt` covers hold/release timing. [VERIFIED: local]
- [ ] `android-host/app/src/test/java/com/btgun/host/motion/MotionProviderSelectionTest.kt` covers provider fallback. [VERIFIED: local]
- [ ] `android-host/app/src/test/java/com/btgun/host/model/NormalizedEventEnvelopeTest.kt` covers per-stream seq and timestamps. [VERIFIED: local]
- [ ] Manual checklist: permission grant, foreground notification, connect to physical gun, press each control, move phone, hold reload 2 seconds, test phone vibration. [VERIFIED: local]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no [VERIFIED: local] | LAN pairing/auth deferred to Phase 3; Phase 2 has no remote desktop session. [VERIFIED: local] |
| V3 Session Management | partial [VERIFIED: local] | Active local foreground session state with explicit start/stop and reconnect boundaries. [VERIFIED: local] |
| V4 Access Control | partial [VERIFIED: local] | Android runtime permissions gate Bluetooth scan/connect and future LAN access. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] |
| V5 Input Validation | yes [VERIFIED: local] | Fixture-backed parser accepts known `fff3` payloads and treats unknown payloads as debug/status, not product controls. [VERIFIED: local] |
| V6 Cryptography | no [VERIFIED: local] | No authenticated LAN/session crypto in Phase 2; do not add ad hoc crypto. [VERIFIED: local] |
| V8 Data Protection | yes [VERIFIED: local] | Redact Bluetooth addresses/raw payloads from release logs; raw provenance behind debug toggle. [VERIFIED: local] |
| V10 Malicious Code | yes [VERIFIED: local] | No new packages; any dependency must pass human checkpoint. [VERIFIED: local] |

### Known Threat Patterns for Android Host

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Raw Bluetooth address leakage in logs | Information Disclosure | Sanitize addresses in product logs; debug toggle only for local development. [VERIFIED: local] |
| Unknown BLE payload interpreted as button | Tampering | Whitelist Phase 1 fixture payloads and emit unknown status/debug event. [VERIFIED: local] |
| Silent reconnect loop | Denial of Service | Bound retries/backoff while active session is running and show last error. [VERIFIED: local] |
| Background BLE work without visible service | Elevation/Policy bypass | Foreground service notification and explicit UI service state. [CITED: https://developer.android.com/develop/background-work/services/fgs/service-types] |
| Desktop haptic controls added early | Scope/Safety | Keep Phase 2 haptics local-only; desktop haptic commands wait for Phase 4. [VERIFIED: local] |

## Sources

### Primary (HIGH confidence)

- `.planning/phases/02-android-host-live-input/02-CONTEXT.md` - locked Phase 2 decisions and deferred scope. [VERIFIED: local]
- `.planning/phases/02-android-host-live-input/02-UI-SPEC.md` - approved native Android dashboard contract. [VERIFIED: local]
- `docs/protocol/ipega-phase1-hardware.md` - physical BLE/GATT observations and control mappings. [VERIFIED: local]
- `fixtures/ipega/normalized/*.jsonl` - parser/control fixture evidence. [VERIFIED: local]
- `android-diagnostic/app/src/main/java/com/btgun/diagnostic/MainActivity.kt` - reusable BLE scan/connect/GATT queue patterns. [VERIFIED: local]
- Android Bluetooth permissions docs - `https://developer.android.com/develop/connectivity/bluetooth/bt-permissions`. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions]
- Android BLE transfer/GATT docs - `https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data` and `https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server`. [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data] [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server]
- Android sensor docs - `https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview`, `sensors_position`, `sensors_motion`, `SensorEvent`, and `SensorManager`. [CITED: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview] [CITED: https://developer.android.com/reference/android/hardware/SensorEvent]
- Android foreground service docs - `https://developer.android.com/develop/background-work/services/fgs/service-types`, `launch`, and BLE background guidance. [CITED: https://developer.android.com/develop/background-work/services/fgs/service-types] [CITED: https://developer.android.com/develop/connectivity/bluetooth/ble/background]

### Secondary (MEDIUM confidence)

- Existing project research docs under `.planning/research/` for stack, architecture, pitfalls, features, and summary. [VERIFIED: local]
- Android local unit test docs - `https://developer.android.com/training/testing/local-tests`. [CITED: https://developer.android.com/training/testing/local-tests]
- Android Wi-Fi permission docs for future LAN permission placeholder - `https://developer.android.com/develop/connectivity/wifi/wifi-permissions`. [CITED: https://developer.android.com/develop/connectivity/wifi/wifi-permissions]

### Tertiary (LOW confidence)

- Assumptions about platform Views sufficiency, generic motion-sensor permission absence as UI phrasing, and preview aim math. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Reuses local approved Android Gradle/Kotlin stack and official Android platform APIs. [VERIFIED: local] [CITED: https://developer.android.com]
- Architecture: HIGH - Phase 2 decisions and Phase 1 BLE evidence strongly define boundaries. [VERIFIED: local]
- Pitfalls: MEDIUM - Most are verified by Phase 1 or official docs; motion permission wording and preview math need implementation validation. [VERIFIED: local] [ASSUMED]
- Validation: MEDIUM - No `android-host` test tree exists yet, but Gradle/ADB/SDK environment is available and Phase 1 validator passes. [VERIFIED: local]

**Research date:** 2026-06-06 [VERIFIED: local]  
**Valid until:** 2026-07-06 for Android docs and local stack; re-check if target SDK or Gradle/AGP versions change. [ASSUMED]
