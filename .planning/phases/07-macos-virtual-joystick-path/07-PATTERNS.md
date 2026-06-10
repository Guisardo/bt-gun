# Phase 07: Android Bluetooth HID Gamepad Path - Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 15 likely new/modified files
**Analogs found:** 15 / 15
**Scope correction:** This replaces stale CoreHID/DriverKit primary-path patterns. Phase 7 primary path is Android `BluetoothHidDevice`; desktop companion stays diagnostics/fallback docs only.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `android-host/app/src/main/AndroidManifest.xml` | config | request-response/capability | `AndroidManifest.xml` current Bluetooth + foreground perms | exact extension |
| `android-host/app/src/main/java/com/btgun/host/permissions/AndroidHidCapability.kt` | model/utility | request-response/capability | `PermissionGate.kt` | exact |
| `android-host/app/src/main/java/com/btgun/host/permissions/HostCapabilityProbe.kt` | utility | request-response/capability | current `HostCapabilityProbe.kt` | exact extension |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidDescriptor.kt` | utility/config | transform | `VirtualControllerDescriptor.kt` + `WindowsHidReportPacker.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` | utility | transform | `WindowsHidReportPacker.kt` + `AndroidUdpInputSender.kt` | exact semantic |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidOutputReportMapper.kt` | utility | transform | `WindowsOutputReportMapper.kt` | exact semantic |
| `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt` | service/adapter | event-driven/request-response | `AndroidUdpInputSender.kt` + `HostSessionService.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidStatus.kt` | model | event-driven/status | `DesktopLinkState.kt`, `DashboardState.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | service | event-driven | current service, `AndroidUdpInputSender.kt` | exact extension |
| `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | view-model | transform | current `DashboardState.kt` | exact extension |
| `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | activity/controller | request-response | current `MainActivity.kt` | exact extension |
| `android-host/app/src/test/java/com/btgun/host/hid/*Test.kt` | test | transform/event-driven | Windows HID tests + Android permission/transport tests | exact semantic |
| `docs/setup/android-bluetooth-hid-gamepad.md` | docs | file-I/O | `docs/setup/macos-virtual-hid.md`, `docs/setup/macos-driverkit-fallback.md` | role-match |
| `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | evidence | file-I/O | phase evidence JSONL manifests | role-match |
| Desktop companion docs/status only | docs/diagnostic | fallback | `BackendCapabilities.kt`, Windows runtime diagnostics | limited |

## Pattern Assignments

### `AndroidManifest.xml` (config, capability)

**Analog:** `android-host/app/src/main/AndroidManifest.xml`

**Copy pattern:** extend existing Bluetooth/runtime permission surface. Current file already declares legacy Bluetooth, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, network, vibration, and connected-device foreground service at lines 6-20. Add HID/discoverability-adjacent needs only when platform docs require them; do not add desktop driver, CoreHID, or DriverKit permissions here.

**Pitfall:** Android HID device role mostly uses `BLUETOOTH_CONNECT` on API 31+. Missing runtime grant must surface as blocked, not as generic register failure.

### `permissions/AndroidHidCapability.kt` + `HostCapabilityProbe.kt` (utility/model, capability)

**Analog:** `PermissionGate.kt` and `HostCapabilityProbe.kt`

**Imports/API package suggestions from research:**

```kotlin
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
```

**Capability data pattern** (`PermissionGate.kt` lines 3-31):

```kotlin
data class PermissionGateInput(...)
data class PermissionGateState(..., val lanNetwork: CapabilityStatus) {
    val canStartSession: Boolean = ...
}
data class CapabilityStatus(val state: CapabilityState, val label: String, val detail: String)
enum class CapabilityState { AVAILABLE, BLOCKED, UNAVAILABLE }
```

**Evaluation pattern** (`PermissionGate.kt` lines 56-119): pure function returns explicit rows for Bluetooth off, missing permission, available, blocked, unavailable. Add HID rows: `hidDeviceProfile`, `hidAppRegistration`, `hidHostConnection`, `hidOutputReport`.

**Probe pattern** (`HostCapabilityProbe.kt` lines 16-34): `evaluate(context)` delegates to pure input/evaluate model; `runtimePermissionsForHost()` remains source of permission request list. Add a non-invasive HID probe method that checks Bluetooth enabled + `BLUETOOTH_CONNECT`; service proxy availability likely needs async adapter, so model status should support `UNKNOWN`/`NOT_PROBED` or a blocked detail like `Start Bluetooth gamepad to probe HID_DEVICE`.

**Tests:** copy `PermissionGateTest.java` line 25 style: construct inputs, assert `CapabilityState.BLOCKED/AVAILABLE/UNAVAILABLE` and exact detail strings.

### `hid/BtGunHidDescriptor.kt` (config/utility, transform)

**Analogs:** `VirtualControllerDescriptor.kt`, `WindowsHidReportPacker.kt`

**Contract source** (`VirtualControllerDescriptor.kt` lines 7-18):

```kotlin
val btGunV1Descriptor = VirtualControllerDescriptor(
    deviceKind = "gamepad_like_joystick",
    buttons = listOf("trigger", "reload", "x", "y", "a", "b"),
    axes = listOf("stickX", "stickY", "aimX", "aimY"),
    triggerKind = "digital",
)
```

**Implement:** Android-owned HID report descriptor bytes in package `com.btgun.host.hid`. Include constants: input report id `0x01`, output report id `0x02`, output version `0x01`, six buttons, four signed 16-bit axes. Descriptor test must pin exact bytes and a semantic parity assertion against copied v1 contract values; do not import desktop production code into Android just to share constants.

**Pitfall:** v1 shape is normal gamepad-like joystick. No custom gun HID usage page.

### `hid/BtGunHidReportPacker.kt` (utility, transform)

**Analogs:** `WindowsHidReportPacker.kt`, `AndroidUdpInputSender.kt`, `NormalizedEvents.kt`

**Input model source** (`NormalizedEvents.kt` lines 51-83, 89-103):

```kotlin
data class GunInputState(
    val pressedControls: Set<String> = emptySet(),
    val stickAxisX: Float = 0f,
    val stickAxisY: Float = 0f,
)
data class MotionSample(
    val rawAimX: Float? = null,
    val rawAimY: Float? = null,
    val aimX: Float? = null,
    val aimY: Float? = null,
)
```

**Button/axis pattern** (`AndroidUdpInputSender.kt` lines 229-249, 273-279):

```kotlin
when (control) {
    "trigger" -> BUTTON_TRIGGER
    "reload" -> BUTTON_RELOAD
    "button_x" -> BUTTON_X
    "button_y" -> BUTTON_Y
    "button_a" -> BUTTON_A
    "button_b" -> BUTTON_B
    else -> 0
}
private fun Float.toInt16Axis(): Int = ...
```

**Report byte pattern** (`WindowsHidReportPacker.kt` lines 21-38): report id byte, button bits, little-endian signed int16 stick axes, little-endian signed int16 aim axes, stale clears buttons/stick.

**Android-specific rule:** pack from `GunInputState` + latest `MotionSample`; aim uses `aimX/aimY` when calibrated/available, else `rawAimX/rawAimY`, else center. Preserve Windows bit order: trigger bit 0, reload bit 1, X bit 2, Y bit 3, A bit 4, B bit 5.

**Tests:** copy `WindowsHidReportPackerTest.kt` line 11 golden vector and line 58 stale behavior. Add Android tests for descriptor bytes, button order, endian/range, calibrated aim preferred, raw fallback, null/stale center.

### `hid/BtGunHidOutputReportMapper.kt` (utility, transform)

**Analog:** `WindowsOutputReportMapper.kt`

**Validation pattern** (`WindowsOutputReportMapper.kt` lines 5-25):

```kotlin
const val WINDOWS_OUTPUT_REPORT_ID = 0x02
const val WINDOWS_OUTPUT_REPORT_VERSION = 0x01
const val WINDOWS_OUTPUT_REPORT_LENGTH_BYTES = 9

fun toHapticCommand(reportBytes: ByteArray, commandId: String): HapticCommand? {
    if (commandId.isBlank()) return null
    if (reportBytes.size != WINDOWS_OUTPUT_REPORT_LENGTH_BYTES) return null
    if (reportBytes[0].toUnsignedInt() != WINDOWS_OUTPUT_REPORT_ID) return null
    if (reportBytes[1].toUnsignedInt() != WINDOWS_OUTPUT_REPORT_VERSION) return null
    ...
    if (flags != 0 || reserved != 0) return null
}
```

**Android target:** return `DesktopHapticCommand`, not desktop `HapticCommand`. Reuse same report id/version/length/strength/duration/TTL/flags/reserved validation. On invalid HID callback, caller must use `BluetoothHidDevice.reportError(...)`.

**Tests:** copy `WindowsOutputReportMapperTest.kt` line 8 valid report and line 26 malformed matrix. Add report-id mismatch, length mismatch, version mismatch, zero/oversized duration/TTL, nonzero flags/reserved, blank command id.

### `hid/AndroidBluetoothHidGamepad.kt` (service/adapter, event-driven)

**Analogs:** `AndroidUdpInputSender.kt`, `HostSessionService.kt`

**Android package suggestions from research:**

```kotlin
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import java.util.concurrent.Executor
```

**Adapter lifecycle pattern** (`AndroidUdpInputSender.kt` lines 92-121, 151-175): explicit `start(config)`, `stop(reason)`, `close()`, no sends before active config.

**Callback/status pattern:** mirror `HostSessionService` state updates through one owner. HID adapter owns `getProfileProxy`, `registerApp`, `unregisterApp`, `sendReport`, `replyReport`, `reportError`, host connection callbacks, `onSetReport`, `onInterruptData`, `onGetReport`, `onVirtualCableUnplug`.

**Start action:** explicit user action only. Register SDP + open discoverable/connectable pairing window with countdown. Do not auto-advertise on app startup.

**Blocked states:** Bluetooth off, missing `BLUETOOTH_CONNECT`, `HID_DEVICE` proxy unavailable, `registerApp` rejected/`onAppStatusChanged(registered=false)`, no host connected, host disconnected.

### `HostSessionService.kt` (service, event-driven integration)

**Analog:** current `HostSessionService.kt`

**Action dispatch pattern** (`HostSessionService.kt` lines 199-206, 1124-1129):

```kotlin
when (intent?.action) {
    ACTION_STOP_SESSION -> stopSession()
    ACTION_CONNECT_DESKTOP_QR -> ...
    ACTION_START_SESSION, null -> startSession()
}
```

Add `ACTION_START_BLUETOOTH_GAMEPAD`, `ACTION_STOP_BLUETOOTH_GAMEPAD`, maybe `ACTION_START_HID_PAIRING_WINDOW`. Keep separate from `ACTION_START_SESSION`; gun/motion session can run while HID mode starts/stops.

**State update pattern** (`HostSessionService.kt` lines 128-131): `currentState` setter updates static `latestState`. Extend `HostSessionState` with `hidGamepadStatus` so dashboard can poll without binding.

**Input fanout integration** (`HostSessionService.kt` lines 707-728, 1076-1106): when gun event or motion sample updates current state, call HID adapter `sendInputReport(currentState.gunInputState, currentState.lastMotionSample?.payload)` if HID host connected. Keep UDP diagnostics unchanged.

**Haptic integration point** (`HostSessionService.kt` lines 118-123, 523): route valid HID output mapper result through existing `DesktopHapticCommandExecutor.handle(...)`; record HID output status separately from LAN haptic ack.

**Pitfall:** desktop companion/LAN haptics are diagnostics/fallback. They do not satisfy HID output proof.

### `DashboardState.kt` + `MainActivity.kt` (view-model/activity, request-response)

**Analogs:** current dashboard/activity.

**Dashboard construction pattern** (`DashboardState.kt` lines 165-183, 218-235): format service state into simple `DashboardField`/`PlaceholderSurface` values. Add HID fields/actions: role capability, registration, pairing countdown, host connection, last input report, output callback seen, last output validation/haptic result, fallback recommendation.

**Activity pattern** (`MainActivity.kt` lines 109-116, 190-213, 251-272): buttons call service actions; render polls `HostSessionService.latestState`. Add `Start Bluetooth gamepad` / `Stop Bluetooth gamepad` button and optional `Start pairing mode` button. Use explicit labels from status model; do not hide blocked states behind `current_error`.

**Debug panels:** extend permission debug with HID row. Add evidence-friendly strings but redact device identifiers.

### Tests

**Android unit style analogs:**

- `PermissionGateTest.java` line 25: pure input -> capability rows.
- `AndroidUdpInputSenderTest.kt` line 49: golden current state + motion vector; line 170: source-code guard excluding out-of-scope mapping.
- `DesktopHapticCommandTest.kt` line 61: valid pulse; line 187: invalid command no vibration.
- `WindowsHidReportPackerTest.kt` line 11: golden report bytes; line 58: stale clears buttons/stick.
- `WindowsOutputReportMapperTest.kt` line 8: valid report; line 26: malformed report matrix.

**Add these tests:**

| Test File | Must Cover |
|---|---|
| `BtGunHidDescriptorTest.kt` | exact descriptor bytes; six buttons/four axes; report IDs; no custom gun usage |
| `BtGunHidReportPackerTest.kt` | button bit order, int16 little-endian axes, stickY inversion decision pinned, calibrated aim, raw fallback, null center, stale behavior |
| `BtGunHidOutputReportMapperTest.kt` | strict id/version/length/duration/TTL/flags/reserved validation into `DesktopHapticCommand` |
| `AndroidHidCapabilityTest.kt` | Bluetooth off, missing permission, proxy unavailable, registration failed, no host, disconnected |
| `AndroidBluetoothHidGamepadStateTest.kt` | no `sendReport` before registered+connected; output invalid -> reportError; unregister/close idempotent |
| `DashboardStateTest.kt` extension | all HID blocked rows visible; pairing countdown/status; output unsupported status honest |

## Shared Patterns

### Auth/Permissions

**Source:** `PermissionGate.kt` lines 85-119, `HostCapabilityProbe.kt` lines 34-46.

Use runtime permission rows and explicit blocked details. Android 12+ requires Nearby Devices/`BLUETOOTH_CONNECT`. HID start must check permission before `getProfileProxy`, `registerApp`, `sendReport`, `replyReport`, or `reportError`.

### Error/Status

**Source:** `HostSessionService.kt` lines 216-258 and `DashboardState.kt` lines 218-235.

Use explicit state fields, not thrown errors, for user-visible platform blocks. Only use `lastError` for exceptional/unexpected failure; expected incompatibility gets a `CapabilityStatus`/`BtGunHidStatus` row.

### Haptics

**Source:** `DesktopHapticCommand.kt` lines 29-35, 106-130; `PhoneHaptics.kt` lines 89-120.

Reuse validation limits: strength `0.0..1.0`, duration `1..1000ms`, TTL `1..2000ms`, no pattern playback. Existing executor cancels active pulse before next pulse and maps permission/runtime failures.

### Redaction

**Source:** `SecretRedactor.kt` lines 3-18 plus Phase 7 context evidence rule.

Evidence/docs must not commit pairing material, session secrets, stream keys, HMAC keys, private keys, Bluetooth addresses, stable device identifiers, or screenshots with sensitive device names. Add Android HID evidence redaction for MAC-like strings (`AA:BB:CC:DD:EE:FF`), Android device serials, hostnames when identifying, pairing codes, and raw Bluetooth dumps.

### Evidence Rows

**Apply to:** `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`

Rows needed: `android-hid-capability-probe`, `android-hid-register-app`, `android-hid-pairing-window`, `macos-bluetooth-connected-user-confirmed`, `macos-game-controller-input-user-confirmed`, `android-hid-output-callback-seen` or `macos-output-unsupported`, `android-hid-output-phone-haptic`, `alternate-phone-tested` if blocked, `windows-vhf-fallback-selected` only after current+alternate phone fail.

## Docs/Evidence Pattern

### `docs/setup/android-bluetooth-hid-gamepad.md`

Include:

- Android phone compatibility gate and alternate-phone checkpoint.
- Permission steps and Bluetooth on/off block meanings.
- `Start Bluetooth gamepad` action and pairing countdown.
- macOS Bluetooth pairing proof steps.
- Game Controller/tester proof steps for buttons + axes.
- HID descriptor/report table with report IDs, button order, axis range/endian.
- HID output report behavior: supported when callback arrives; honest unsupported if macOS sends no usable output.
- Windows VHF fallback boundary.
- CoreHID/DriverKit retained only as fallback evidence; no SIP/system-extension/security-state changes without later approval.
- Redaction rules above.

### Desktop Companion Scope

Desktop companion may gain docs/status text or a diagnostic checklist only. Do not put it in primary macOS input path. Do not revive `MacosHidReportPacker`, CoreHID helper, DriverKit activation, or virtual HID proof as required path for Phase 7.

## No Analog Found

None. Android `BluetoothHidDevice` profile adapter has no exact local class, but existing service/adapter lifecycle + capability gate + HID packer/output mapper analogs are sufficient. Use official Android API names from research for missing platform-specific calls.

## Pitfalls

- API exists at compile time but phone/OEM may not expose `HID_DEVICE` proxy. Treat as blocked and test alternate phone before fallback.
- `registerApp` returning accepted does not equal registered. Wait for callback app status.
- macOS pairing visible does not equal Game Controller input visible. Need both.
- LAN haptic command does not prove macOS HID output report.
- Do not leak Bluetooth MAC/device name/screenshots in evidence.
- Do not move Android packer into shared desktop module in Phase 7.
- Do not make CoreHID/DriverKit/SIP/system-extension work primary path.

## Metadata

**Analog search scope:** `android-host`, `desktop-companion`, `docs/setup`, `docs/evidence`, Phase 7 planning docs.
**Files scanned:** Android host source/tests, desktop backend source/tests, setup/evidence docs.
**Pattern extraction date:** 2026-06-10.
