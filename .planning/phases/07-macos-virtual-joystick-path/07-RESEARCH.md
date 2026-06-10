# Phase 07: Android Bluetooth HID Gamepad Path - Research

**Researched:** 2026-06-10
**Domain:** Android `BluetoothHidDevice` peripheral gamepad path for macOS-visible controller proof
**Confidence:** MEDIUM - Android API surface is verified, but phone OEM support and macOS output-report behavior need live proof.

<user_constraints>
## User Constraints (from CONTEXT.md)

**Source:** `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md` [VERIFIED: codebase grep]

### Locked Decisions

### HID Role Gate
- **D-01:** Android uses a startup capability gate plus an explicit user-controlled "Start Bluetooth gamepad" action. The app probes Bluetooth state, permissions, and HID device profile support before the user starts HID mode.
- **D-02:** Android must show a full blocked-state matrix: Bluetooth off, missing Bluetooth permission, HID_DEVICE proxy unavailable, HID app registration failed, no host connected, and host disconnected.
- **D-03:** Android Bluetooth HID is the primary macOS input path for Phase 7. LAN streaming and the desktop companion remain optional diagnostics/fallback scaffolding, not required for macOS success.
- **D-04:** If the current Android phone lacks HID peripheral support, test an alternate Android phone before declaring Phase 7 blocked and falling back to Windows VHF.

### HID Report Shape
- **D-05:** The Android Bluetooth HID descriptor mirrors the existing v1 backend contract exactly: six buttons plus four axes for trigger, reload, X/Y/A/B, stickX/stickY, and aimX/aimY.
- **D-06:** Android owns the Bluetooth HID report packer. It maps `GunInputState` plus the latest `MotionSample` into HID report bytes. Do not move code into a shared module during Phase 7 just to share constants.
- **D-07:** HID aim axes use calibrated Android `aimX`/`aimY` when available. If calibrated aim is unavailable, fall back to normalized raw aim values.
- **D-08:** Tests must pin golden descriptor bytes and golden report vectors, including button bit order, axis endian/range, stale/center behavior, and parity with `btGunV1Descriptor`.

### Pairing and Proof
- **D-09:** Phase 7 pairing proof requires both macOS Bluetooth connection evidence and Game Controller/tester evidence that macOS sees buttons and axes.
- **D-10:** Android exposes an explicit pairing-mode control. Starting it registers HID mode and opens a discoverable/connectable pairing window with visible countdown and status.
- **D-11:** The desktop companion is diagnostics only during Android HID proof. It may show instructions, evidence checklist, and fallback status, but it must not be in the macOS input path.
- **D-12:** Final Phase 7 input proof requires user confirmation that macOS Bluetooth sees the controller and a tester/Game Controller surface shows real gun controls and phone motion moving buttons/axes.

### Output Haptics
- **D-13:** Phase 7 must attempt host-origin Bluetooth HID output/rumble report handling, but honest unsupported status is acceptable if macOS sends no output reports for this descriptor.
- **D-14:** Android validates output reports strictly. Only known report id, length, version, and reserved-byte shape can trigger phone haptics; malformed or unknown reports return `reportError` and surface last error/status.
- **D-15:** The authenticated LAN desktop-to-Android phone haptic path remains diagnostic/fallback only for Phase 7. It can support tests and the Windows fallback path, but it is not equivalent to Android HID output for macOS proof.
- **D-16:** Final haptic evidence captures capability plus actual result rows: host-output callback seen/not seen, report validation result, phone vibration result, and macOS unsupported reason when no output arrives.

### Legacy macOS Virtual HID Work
- **D-17:** CoreHID/DriverKit work from earlier Phase 7 plans remains retained evidence/fallback scaffolding. It is not the primary no-subscription macOS v1 path.
- **D-18:** SIP changes, system extension developer mode, install, activation, removal, rollback, reboot, or other OS security-state changes still require explicit later approval.
- **D-19:** Completed Windows VHF work remains the working OS-visible fallback if Android Bluetooth HID cannot be proven after testing an alternate Android phone.

### the agent's Discretion
- Choose exact Android class/package names, HID descriptor byte layout that preserves the v1 contract, report ids, SDP settings, status model names, tester command names, evidence artifact names, and redaction format.
- Choose exact macOS Game Controller tester implementation and Bluetooth evidence collection commands, provided final proof still includes user confirmation and does not commit secrets or sensitive device identifiers.

### Deferred Ideas (OUT OF SCOPE)
- Phase 8 owns profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping.
- Phase 9 owns visualizer UI, latency dashboard, packet-loss dashboard, and recenter display.
- Phase 10 owns broader replay diagnostics and packaging documentation beyond Phase 7 proof docs.
- Direct desktop-to-gun Bluetooth remains v2/deferred.
- Physical gun motor rumble remains v2/deferred.
- Production notarized macOS virtual HID driver flow remains out of scope unless a later phase explicitly revives DriverKit as product path.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ANDR-09 | Android host app can register or advertise as a Bluetooth HID gamepad using the phone's supported HID peripheral role, with blocked state when unsupported. [VERIFIED: `.planning/REQUIREMENTS.md`] | Use `BluetoothAdapter.getProfileProxy(..., BluetoothProfile.HID_DEVICE)` and `BluetoothHidDevice.registerApp(...)`; API methods and `HID_DEVICE` profile are present in local Android 35 SDK and documented by Android. [VERIFIED: Android SDK `android.jar` via `javap`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice; CITED: https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothProfile] |
| ANDR-10 | Android maps normalized gun controls and Android motion aim into regular gamepad-style HID input reports without a macOS virtual HID driver. [VERIFIED: `.planning/REQUIREMENTS.md`] | Descriptor/report bytes must mirror `btGunV1Descriptor`: six buttons, four axes, digital trigger; use Android `sendReport(device, reportId, data)` for input reports. [VERIFIED: `VirtualControllerDescriptor.kt`; VERIFIED: `WindowsHidReportPacker.kt`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] |
| ANDR-11 | Android receives Bluetooth HID output or rumble reports and maps valid output to phone haptics, or reports limitation clearly. [VERIFIED: `.planning/REQUIREMENTS.md`] | Implement `BluetoothHidDevice.Callback.onSetReport`, `onGetReport`, and `onInterruptData`; use `replyReport` for GET_REPORT and `reportError` for invalid SET_REPORT shape. [VERIFIED: Android SDK `android.jar` via `javap`; CITED: https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothHidDevice.Callback; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] |
| DESK-03 | macOS Apple Silicon can see Android phone as regular Bluetooth HID gamepad-style joystick. [VERIFIED: `.planning/REQUIREMENTS.md`] | Pair Android as Bluetooth controller, then prove via macOS Bluetooth UI plus a Game Controller API/tester app reading `GCController.extendedGamepad`. [CITED: https://support.apple.com/guide/games/connect-a-game-controller-devf8cec167c/mac; CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller; CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller/extendedgamepad] |
| DESK-06 | macOS Bluetooth HID gamepad path can receive OS output/rumble reports and route them to phone haptics, or clearly report limitation while preserving v1 phone haptics. [VERIFIED: `.planning/REQUIREMENTS.md`] | Android callback proof is mandatory; macOS may expose Game Controller haptics only for devices/profile types it recognizes, so no output callback seen is an acceptable documented limitation only after live probe. [CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller/haptics; VERIFIED: `.planning/phases/07.../07-CONTEXT.md`; ASSUMED: generic descriptor may not trigger macOS rumble] |
| PACK-03 | Document selected macOS strategy: Android Bluetooth HID primary, CoreHID/DriverKit retained only as blocked/fallback evidence. [VERIFIED: `.planning/REQUIREMENTS.md`] | Docs must name Android HID as primary path and refer to `corehid-runtime-blocked` evidence only as fallback context. [VERIFIED: `.planning/quick/260610-m2r.../SUMMARY.md`; VERIFIED: `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl`] |
| PACK-06 | Document Android Bluetooth HID setup, phone compatibility, pairing, descriptors, output behavior, and Windows VHF fallback. [VERIFIED: `.planning/REQUIREMENTS.md`] | Setup docs must include permission/profile gate, pairing-mode countdown, descriptor/report bytes, macOS tester procedure, redaction rules, and Windows fallback decision gate. [VERIFIED: `.planning/phases/07.../07-CONTEXT.md`; VERIFIED: Phase 6 context] |
</phase_requirements>

## Summary

Primary Phase 7 plan should use Android `BluetoothHidDevice` as a Bluetooth Classic HID Device/peripheral role, not macOS CoreHID/DriverKit. [VERIFIED: `.planning/ROADMAP.md`; VERIFIED: `.planning/phases/07.../07-CONTEXT.md`] Android exposes the needed public API surface: `BluetoothProfile.HID_DEVICE`, `BluetoothHidDevice.registerApp`, `sendReport`, `replyReport`, `reportError`, and `BluetoothHidDevice.Callback` methods for app status, connection state, get/set reports, interrupt data, and virtual-cable unplug. [VERIFIED: Android SDK `android.jar` via `javap`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice; CITED: https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothHidDevice.Callback]

Hardest unknown is compatibility, not API shape. [ASSUMED] Some Android builds may not expose the HID Device profile proxy or may fail `registerApp`, so the plan needs explicit blocked-state rows and an alternate-phone checkpoint before falling back to Windows VHF. [VERIFIED: context D-02/D-04/D-19; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] macOS input proof must be user-visible: Bluetooth pairing plus Game Controller/tester evidence that buttons and axes move from real gun controls and Android motion. [VERIFIED: context D-09/D-12; CITED: https://support.apple.com/guide/games/connect-a-game-controller-devf8cec167c/mac; CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller]

**Primary recommendation:** Replan remaining Phase 7 as Android HID adapter + report packer + live macOS pairing proof + output-report probe + fallback gate; keep desktop companion only for docs/evidence/fallback status, not in primary macOS input path. [VERIFIED: context D-03/D-11/D-19]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| HID role capability gate | Android host app | Android framework Bluetooth service | Existing `HostCapabilityProbe` owns capability reporting; Android framework owns actual `HID_DEVICE` proxy availability. [VERIFIED: `HostCapabilityProbe.kt`; VERIFIED: Android SDK `BluetoothProfile.HID_DEVICE`] |
| Bluetooth HID registration/advertising | Android host app | Android Bluetooth stack | App must call `getProfileProxy` then `registerApp` with SDP settings and descriptor; Android framework adds SDP record during registration. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings] |
| Input report packing | Android host app | Tests | Phase 7 decision says Android owns packer; source inputs are `GunInputState` and `MotionSample`. [VERIFIED: context D-06; VERIFIED: `NormalizedEvents.kt`] |
| macOS controller visibility | macOS host OS | Android host app | macOS must pair to Android and expose controller through Bluetooth/Game Controller surfaces; desktop companion is not in input path. [VERIFIED: context D-09/D-11; CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller] |
| HID output/rumble receive | Android `BluetoothHidDevice.Callback` | Phone haptic executor | Host-origin `SET_REPORT`/interrupt data must be validated on Android then mapped through existing bounded phone haptic command rules. [VERIFIED: Android SDK callback methods; VERIFIED: `DesktopHapticCommand.kt`; VERIFIED: `PhoneHaptics.kt`] |
| Fallback decision | Planning/evidence docs | Windows VHF path | If current and alternate Android phones cannot prove HID peripheral/macOS input, completed Phase 6 Windows VHF remains OS-visible fallback. [VERIFIED: context D-04/D-19; VERIFIED: Phase 6 context] |

## Project Constraints (from AGENTS.md)

- Use GSD workflow for project edits unless user says bypass. [VERIFIED: `AGENTS.md`]
- New user-facing branches must use `feature/<short-kebab-slug>`; do not use `codex/`, `codex-`, or agent-name prefixes unless explicit. [VERIFIED: `AGENTS.md`]
- Before creating or pushing a branch, state exact branch name. [VERIFIED: `AGENTS.md`]
- Desktop support remains Windows 11 x64 plus macOS Apple Silicon for v1. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/PROJECT.md`]
- Android-to-desktop v1 transport remains Wi-Fi/LAN, but Phase 7 primary macOS path is Android-to-macOS Bluetooth HID and desktop companion is diagnostics/fallback only. [VERIFIED: `.planning/ROADMAP.md`; VERIFIED: `07-CONTEXT.md`]
- HID shape must be normal gamepad-style joystick, not custom gun report. [VERIFIED: `AGENTS.md`; VERIFIED: `VirtualControllerDescriptor.kt`]
- Desktop profiles own final aim mapping; Phase 7 only maps current normalized/default aim values into fixed HID axes. [VERIFIED: `.planning/PROJECT.md`; VERIFIED: `07-CONTEXT.md`]
- Evidence must not commit pairing material, session secrets, stream keys, HMAC keys, private keys, Bluetooth addresses, device identifiers, screenshots with sensitive data, or signing material. [VERIFIED: `07-CONTEXT.md`; VERIFIED: prior evidence manifest patterns]

## Standard Stack

### Core

| Library/API | Version | Purpose | Why Standard |
|-------------|---------|---------|--------------|
| Android native app, Kotlin | Existing Android Gradle plugin 8.7.3, Kotlin 2.0.21, compileSdk 35. [VERIFIED: `android-host/build.gradle.kts`; `app/build.gradle.kts`] | Implement HID adapter/report packer inside existing host app. | Existing Android app already owns gun BLE, motion, recenter, foreground session, and phone haptics. [VERIFIED: `HostSessionService.kt`] |
| `android.bluetooth.BluetoothHidDevice` | Added API 28; present in local Android 35 SDK. [VERIFIED: Android SDK `android.jar` via `javap`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] | Register Android as HID device, send input reports, reply to host requests, report invalid output. | Official Android API for HID Device profile. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] |
| `BluetoothHidDevice.Callback` | Present in local Android 35 SDK. [VERIFIED: `javap`] | Observe app registration, host connection, GET_REPORT, SET_REPORT, interrupt data, virtual cable unplug. | Required callback surface for registration/connection/output proof. [CITED: https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothHidDevice.Callback] |
| `BluetoothHidDeviceAppSdpSettings` | Present in local Android 35 SDK. [VERIFIED: `javap`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings] | Name/description/provider/subclass/report descriptor for SDP registration. | Android framework uses it to add SDP record during app registration. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings] |
| Android `BLUETOOTH_CONNECT` permission | Runtime permission on Android 12+. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] | Register/connect/send reports on targetSdk 35 app. | Android docs require user approval for Nearby Devices permissions before Bluetooth communication/discoverability. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions] |
| Apple Game Controller framework | Current macOS framework. [CITED: https://developer.apple.com/documentation/gamecontroller] | Build/use tester that reads `GCController` and `extendedGamepad`. | Apple-documented controller visibility/input surface for macOS proof. [CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller; CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller/extendedgamepad] |
| Windows VHF path | Completed Phase 6. [VERIFIED: `.planning/STATE.md`; VERIFIED: `06-CONTEXT.md`] | Fallback if Android HID phone/macOS proof blocks. | Accepted OS-visible Windows controller path already exists. [VERIFIED: `.planning/STATE.md`] |

### Supporting

| Tool/API | Version | Purpose | When to Use |
|----------|---------|---------|-------------|
| `BluetoothAdapter.getProfileProxy` | Present in Android SDK. [VERIFIED: `javap`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] | Obtain `BluetoothHidDevice` proxy using `BluetoothProfile.HID_DEVICE`. | Start HID mode and detect proxy unavailable blocked state. [VERIFIED: context D-02] |
| `BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE` | Android platform API. [ASSUMED] | Open user-approved discoverable/connectable pairing window. | Pairing-mode countdown after explicit Start Bluetooth gamepad action. [VERIFIED: context D-10; ASSUMED: exact discoverability API use] |
| macOS Bluetooth Settings / Apple Games app | macOS user UI. [CITED: https://support.apple.com/guide/games/connect-a-game-controller-devf8cec167c/mac] | Pair Android phone and confirm connected controller. | Manual proof row for DESK-03. [VERIFIED: context D-09/D-12] |
| Local Game Controller tester app/command | To be added or documented. [ASSUMED] | Enumerate `GCController.controllers()`, read `extendedGamepad`, display/log redacted axes/buttons. | Manual proof row for live controls and motion. [CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller] |
| Existing `PhoneHaptics` and `DesktopHapticCommandExecutor` | Current repo code. [VERIFIED: `PhoneHaptics.kt`; `DesktopHapticCommand.kt`] | Execute bounded haptic pulses from valid HID output reports. | Reuse validation/status semantics rather than inventing haptic safety logic. [VERIFIED: Phase 4 context] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Android Bluetooth HID primary | Revive CoreHID/DriverKit | CoreHID was blocked by entitlement/runtime evidence and DriverKit needs approval-gated OS security/system-extension work; keep only as fallback evidence. [VERIFIED: `.planning/quick/260610-m2r.../SUMMARY.md`; VERIFIED: `phase7-macos-virtual-hid.jsonl`] |
| Bluetooth HID output report proof | LAN desktop-to-Android haptic command | LAN haptics are already proven but do not prove macOS HID host output behavior. [VERIFIED: context D-15; VERIFIED: Phase 4 evidence] |
| Current phone only | Alternate Android phone checkpoint | OEM support risk means one phone failure cannot prove API path impossible. [VERIFIED: context D-04; ASSUMED: OEM variance risk] |
| Game-only proof | Game Controller/tester proof first | Games can obscure mapping; tester exposes raw controller profile state and is more debuggable. [CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller; ASSUMED: tester better debug surface] |

**Installation:**

No new external package install is recommended for Phase 7. [VERIFIED: current stack uses platform APIs]

```bash
# Android unit tests after HID code is added
cd android-host
gradle test

# Existing desktop/fallback tests if docs/status touch desktop companion
cd desktop-companion
gradle test
```

**Version verification:** Android API existence was verified with local `android.jar` API 35 using `javap`; official Android docs were used for API behavior and permission claims. [VERIFIED: local command output; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice]

## Package Legitimacy Audit

No new npm/PyPI/crates/Maven package is recommended by this research. [VERIFIED: Standard Stack]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| none | n/a | n/a | n/a | n/a | n/a | No external package install required. [VERIFIED: Standard Stack] |

**Packages removed due to slopcheck [SLOP] verdict:** none. [VERIFIED: no packages recommended]
**Packages flagged as suspicious [SUS]:** none. [VERIFIED: no packages recommended]

## Architecture Patterns

### System Architecture Diagram

```text
Physical iPega gun + Android motion sensors
  -> existing BLE gun adapter + SensorManager capture
  -> GunInputState + latest MotionSample
  -> Android HID gamepad report packer
  -> BluetoothHidDevice.registerApp(SDP descriptor)
  -> sendReport(reportId=1, payload)
  -> macOS Bluetooth host
  -> Game Controller/tester reads buttons + axes

macOS host output path, if any:
macOS Bluetooth HID host
  -> BluetoothHidDevice.Callback.onSetReport/onInterruptData
  -> strict output report validator (reportId=2, version=1, reserved=0)
  -> DesktopHapticCommandExecutor + PhoneHaptics
  -> phone vibration result/status

Failure branch:
HID_DEVICE proxy unavailable OR registerApp failed OR macOS cannot pair/input after alternate phone
  -> record blocked evidence
  -> keep completed Windows VHF path as OS-visible fallback
```

### Recommended Project Structure

```text
android-host/app/src/main/java/com/btgun/host/hid/
  AndroidBluetoothHidGamepad.kt       # profile proxy, register/unregister, sendReport [ASSUMED]
  AndroidHidCapability.kt             # HID role/proxy/registration blocked-state model [ASSUMED]
  BtGunHidDescriptor.kt               # golden descriptor bytes + constants [ASSUMED]
  BtGunHidReportPacker.kt             # GunInputState + MotionSample -> report payload [ASSUMED]
  BtGunHidOutputReportMapper.kt       # host report bytes -> DesktopHapticCommand [ASSUMED]
  BtGunHidStatus.kt                   # registration/host/output status for dashboard [ASSUMED]

android-host/app/src/test/java/com/btgun/host/hid/
  BtGunHidDescriptorTest.kt
  BtGunHidReportPackerTest.kt
  BtGunHidOutputReportMapperTest.kt
  AndroidBluetoothHidGamepadStateTest.kt

docs/setup/
  android-bluetooth-hid-gamepad.md    # setup, pairing, compatibility, fallback docs [ASSUMED]

docs/evidence/manifests/
  phase7-android-bluetooth-hid.jsonl  # sanitized proof rows [ASSUMED]
```

### Pattern 1: HID Profile Proxy and Registration Boundary

**What:** Add one Android HID component behind `HostSessionService`; it owns proxy lifecycle, registration, host connection, and report send/error calls. [VERIFIED: existing service boundary; ASSUMED: new class name]

**When to use:** Start only after explicit user action and capability gate success. [VERIFIED: context D-01/D-10]

```kotlin
// Source: Android BluetoothHidDevice docs + local SDK javap [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice]
val listener = object : BluetoothProfile.ServiceListener {
    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        if (profile != BluetoothProfile.HID_DEVICE || proxy !is BluetoothHidDevice) {
            setBlocked("HID_DEVICE proxy unavailable")
            return
        }
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT Gun Gamepad",
            "BT Gun Android HID Gamepad",
            "BT Gun",
            (BluetoothHidDevice.SUBCLASS1_COMBO.toInt() or BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()).toByte(),
            BtGunHidDescriptor.bytes,
        )
        val accepted = proxy.registerApp(sdp, null, null, mainExecutor, callback)
        if (!accepted) setBlocked("HID app registration command rejected")
    }

    override fun onServiceDisconnected(profile: Int) {
        if (profile == BluetoothProfile.HID_DEVICE) setBlocked("HID_DEVICE proxy disconnected")
    }
}
```

### Pattern 2: Android-Owned Report Packer

**What:** Pack report ID 1 payload from `GunInputState` plus latest `MotionSample`; keep byte contract in Android tests and compare semantic shape to `btGunV1Descriptor`. [VERIFIED: context D-05/D-08; VERIFIED: `VirtualControllerDescriptor.kt`]

**When to use:** Every gun control edge and throttled motion snapshot while host is connected. [ASSUMED]

```kotlin
// Source: mirrors existing Windows report semantics, but Android owns implementation. [VERIFIED: WindowsHidReportPacker.kt; VERIFIED: context D-06]
data class BtGunHidInputPayload(val bytes: ByteArray)

object BtGunHidReportPacker {
    const val INPUT_REPORT_ID: Int = 0x01
    const val INPUT_PAYLOAD_LENGTH_BYTES: Int = 9

    fun pack(state: GunInputState, motion: MotionSample?, stale: Boolean): BtGunHidInputPayload {
        val payload = ByteArray(INPUT_PAYLOAD_LENGTH_BYTES)
        payload[0] = if (stale) 0 else buttonBits(state).toByte()
        payload.writeInt16Le(1, if (stale) 0 else axis16(state.stickAxisX))
        payload.writeInt16Le(3, if (stale) 0 else axis16(-state.stickAxisY))
        payload.writeInt16Le(5, axis16((motion?.aimX ?: motion?.rawAimX ?: 0f)))
        payload.writeInt16Le(7, axis16((motion?.aimY ?: motion?.rawAimY ?: 0f)))
        return BtGunHidInputPayload(payload)
    }
}
```

### Pattern 3: Strict Output Report Mapping

**What:** Treat host output as untrusted input; only report ID 2, version 1, valid strength/duration/TTL, and zero reserved bytes can start phone vibration. [VERIFIED: context D-14; VERIFIED: `WindowsOutputReportMapper.kt`]

**When to use:** In `onSetReport` for output reports and `onInterruptData` if host sends output over interrupt channel. [VERIFIED: Android SDK callback methods; ASSUMED: host may choose either callback]

```kotlin
// Source: Android callback surface + existing Windows output shape. [VERIFIED: Android SDK javap; VERIFIED: WindowsOutputReportMapper.kt]
override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
    if (type != BluetoothHidDevice.REPORT_TYPE_OUTPUT || id.toInt() != 0x02) {
        hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
        return
    }
    val command = BtGunHidOutputReportMapper.toDesktopHapticCommand(data, "hid-output-${SystemClock.elapsedRealtimeNanos()}")
    if (command == null) {
        hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
        return
    }
    val result = desktopHapticExecutor.handle(command, SystemClock.elapsedRealtimeNanos())
    recordHidOutputResult(result)
}
```

### Anti-Patterns to Avoid

- **Auto-advertise HID at app startup:** Violates explicit user-controlled Start Bluetooth gamepad decision and can confuse normal gun-host session state. [VERIFIED: context D-01/D-10]
- **Claim support when `getProfileProxy` returns no HID_DEVICE proxy:** That is the core phone compatibility gate and must be surfaced as blocked. [VERIFIED: context D-02]
- **Use LAN haptic as macOS HID output proof:** LAN haptics are useful fallback diagnostics but cannot satisfy DESK-06. [VERIFIED: context D-15]
- **Move packer into shared module for parity:** Phase 7 decision says Android owns HID report packing and tests prove parity. [VERIFIED: context D-06/D-08]
- **Commit raw Bluetooth MACs or screenshots with device names:** Evidence must be sanitized. [VERIFIED: context code_context evidence rule]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bluetooth HID Device profile | Custom RFCOMM/L2CAP HID server | Android `BluetoothHidDevice` | Android framework owns HID Device service, SDP registration, host connection, and report channels. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] |
| macOS controller recognition | Custom macOS driver as primary path | Bluetooth pairing + Game Controller proof | Reroute explicitly avoids CoreHID/DriverKit entitlement path for primary macOS support. [VERIFIED: reroute summary; CITED: https://developer.apple.com/documentation/gamecontroller] |
| Haptic validation | New unbounded vibrator path | Existing `DesktopHapticCommandExecutor` and `PhoneHaptics` | Existing code enforces strength/duration/TTL/status limits. [VERIFIED: `DesktopHapticCommand.kt`; `PhoneHaptics.kt`] |
| Compatibility decision | Silent fallback or one-device conclusion | Blocked matrix + alternate-phone checkpoint + Windows VHF fallback gate | Context requires clear blocked states and alternate phone before fallback. [VERIFIED: context D-02/D-04/D-19] |
| Descriptor/report correctness | Ad hoc byte edits without tests | Golden descriptor/report vectors | Context requires golden descriptor and report vector tests. [VERIFIED: context D-08] |

**Key insight:** Phase 7 risk is not writing bytes; risk is proving that real Android firmware exposes HID Device role and real macOS treats the descriptor as a usable controller. [VERIFIED: Android APIs exist locally; ASSUMED: compatibility is main risk]

## Common Pitfalls

### Pitfall 1: Treating API presence as phone support
**What goes wrong:** Code compiles but `getProfileProxy(...HID_DEVICE)` never yields usable proxy or `registerApp` fails. [ASSUMED; VERIFIED: context requires blocked state]  
**Why it happens:** Android framework API exists at SDK level, but device/OEM Bluetooth stack may not expose HID Device service reliably. [ASSUMED]  
**How to avoid:** Add startup probe, explicit Start Bluetooth gamepad action, register callback status, alternate-phone checkpoint. [VERIFIED: context D-01/D-04]  
**Warning signs:** `HID_DEVICE proxy unavailable`, `onServiceDisconnected`, `onAppStatusChanged registered=false`, no host connection after pairing window. [VERIFIED: Android callback surface]

### Pitfall 2: Missing Android 12+ Bluetooth runtime permission
**What goes wrong:** HID register/connect/report operations throw `SecurityException` or fail on targetSdk 35. [CITED: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions]  
**Why it happens:** Android 12+ Nearby Devices permissions are runtime permissions, and existing manifest already targets Android 35 with `BLUETOOTH_CONNECT`. [CITED: Android Bluetooth permissions docs; VERIFIED: `AndroidManifest.xml`; `app/build.gradle.kts`]  
**How to avoid:** Extend `HostCapabilityProbe` and `PermissionGate` so HID mode blocks on missing `BLUETOOTH_CONNECT`. [VERIFIED: `HostCapabilityProbe.kt`; ASSUMED: implementation]  
**Warning signs:** Permission state says live session can start but HID mode still fails. [ASSUMED]

### Pitfall 3: Putting report ID in wrong byte location
**What goes wrong:** macOS ignores input or maps axes incorrectly. [ASSUMED]  
**Why it happens:** Android `sendReport(device, id, data)` separates report id from payload, while existing Windows report byte array includes report ID at byte 0. [CITED: Android `sendReport` docs; VERIFIED: `WindowsHidReportPacker.kt`]  
**How to avoid:** Define Android HID payload bytes separately from Windows helper byte arrays; golden tests must pin whether Android payload excludes report ID. [VERIFIED: context D-08; ASSUMED: Android payload excludes ID based on API signature]  
**Warning signs:** Android logs `sendReport=true` but Game Controller tester shows no movement. [ASSUMED]

### Pitfall 4: Output callback never arrives
**What goes wrong:** Input proof passes but DESK-06 haptic proof stalls. [VERIFIED: context D-13/D-16]  
**Why it happens:** macOS Game Controller haptics may not send generic HID output reports for a custom gamepad descriptor. [CITED: https://developer.apple.com/documentation/gamecontroller/gccontroller/haptics; ASSUMED: generic descriptor behavior]  
**How to avoid:** Implement callbacks, output report descriptor, explicit status rows: callback seen/not seen, report validation result, phone vibration result, unsupported reason. [VERIFIED: context D-13/D-16]  
**Warning signs:** macOS sees controller input but Android `onSetReport`/`onInterruptData` counters stay zero. [VERIFIED: callback surface; ASSUMED: diagnostic counter]

### Pitfall 5: Leaking device identifiers in evidence
**What goes wrong:** Bluetooth addresses, phone names, or unique macOS controller ids get committed. [VERIFIED: context evidence rule]  
**Why it happens:** Pairing screenshots/logs often include identifiers. [ASSUMED]  
**How to avoid:** Evidence manifest stores only redacted host labels, suffixes/hashes when needed, pass/fail, timestamps, and user confirmation text. [VERIFIED: existing evidence manifest style]  
**Warning signs:** `rg` finds MAC-address patterns, QR/session secrets, stream keys, or private keys in docs/evidence. [VERIFIED: prior validation style]

## Code Examples

### Capability Probe Shape

```kotlin
// Source: existing HostCapabilityProbe pattern + Android Bluetooth docs. [VERIFIED: HostCapabilityProbe.kt; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice]
data class HidCapabilityState(
    val bluetoothEnabled: CapabilityStatus,
    val bluetoothConnect: CapabilityStatus,
    val hidProfileProxy: CapabilityStatus,
    val appRegistration: CapabilityStatus,
    val hostConnection: CapabilityStatus,
)
```

### Report Send

```kotlin
// Source: Android BluetoothHidDevice.sendReport docs. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice]
fun publishInput(hid: BluetoothHidDevice, host: BluetoothDevice, state: GunInputState, motion: MotionSample?) {
    val payload = BtGunHidReportPacker.pack(state, motion, stale = false)
    val accepted = hid.sendReport(host, BtGunHidReportPacker.INPUT_REPORT_ID, payload.bytes)
    if (!accepted) recordHidStatus("send_report_rejected")
}
```

### GET_REPORT Reply

```kotlin
// Source: Android BluetoothHidDevice.replyReport docs. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice]
override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
    val payload = when {
        type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == 0x01 -> latestInputPayload()
        type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id.toInt() == 0x02 -> lastOutputPayload()
        else -> null
    }
    if (payload == null) {
        hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
    } else {
        hid.replyReport(device, type, id, payload)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| macOS CoreHID/DriverKit virtual joystick as Phase 7 primary | Android phone as Bluetooth HID gamepad primary | 2026-06-10 reroute. [VERIFIED: quick summary] | Avoids paid Apple virtual HID entitlement path for primary macOS proof; moves implementation to Android HID role. [VERIFIED: ROADMAP; quick summary] |
| LAN desktop companion in macOS input path | macOS pairs directly to Android phone HID | 2026-06-10 reroute. [VERIFIED: `07-CONTEXT.md`] | Desktop companion becomes diagnostics/fallback status only for Phase 7. [VERIFIED: context D-11] |
| macOS output proof required OS-origin CoreHID/DriverKit set-report | Android HID output callback attempted; unsupported status acceptable if macOS sends none | 2026-06-10 reroute. [VERIFIED: context D-13/D-16] | DESK-06 can close with honest platform limitation after callbacks/probes are implemented and observed. [VERIFIED: context D-13/D-16] |
| Windows VHF as one of two desktop virtual HID paths | Windows VHF as fallback if Android HID blocked | 2026-06-10 reroute. [VERIFIED: quick summary; STATE] | Do not discard Phase 6; use as decision gate fallback. [VERIFIED: context D-19] |

**Deprecated/outdated:**
- CoreHID/DriverKit primary-path framing: outdated for Phase 7 planning after reroute; retain only as blocked/fallback evidence. [VERIFIED: quick summary; `07-CONTEXT.md`]
- Treating simulated output haptics as DESK-06 proof: invalid; HID output callback or clear unsupported status is required. [VERIFIED: context D-13/D-16]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | OEM Android builds may expose API but not usable HID Device profile. | Summary, Pitfalls | Planner may over-focus on code and under-plan alternate-phone/fallback evidence. |
| A2 | Android `sendReport(device, id, data)` payload should exclude report ID because ID is separate param. | Pitfall 3, Code Examples | Wrong report payload shape could break macOS input proof. Planner should add live/golden tests. |
| A3 | macOS may not send generic rumble/output reports for this custom gamepad descriptor. | DESK-06, Pitfall 4 | Haptic proof may end as documented unsupported rather than phone vibration from Bluetooth HID. |
| A4 | A local tester app is better than game-only proof. | Alternatives | Planner may need to add tester implementation if no existing macOS controller tester is available. |
| A5 | `ACTION_REQUEST_DISCOVERABLE` is the right user-approved pairing-window mechanism. | Supporting, Pattern 1 | Planner may need to adjust to Android HID registration/pairing behavior on real phone. |

## Open Questions

1. **Does current Android phone expose usable `BluetoothProfile.HID_DEVICE`?**  
   - What we know: API exists in Android SDK; context requires blocked-state matrix. [VERIFIED: Android SDK; `07-CONTEXT.md`]  
   - What's unclear: Current physical phone/OEM stack behavior. [ASSUMED]  
   - Recommendation: Plan first hardware checkpoint before report-packer integration is considered done. [VERIFIED: context D-04]

2. **Will macOS Game Controller map this descriptor as `extendedGamepad`?**  
   - What we know: Apple supports Bluetooth game controllers and `GCController.extendedGamepad`. [CITED: Apple Support; Apple Developer docs]  
   - What's unclear: Mapping behavior for Android-advertised custom HID gamepad descriptor. [ASSUMED]  
   - Recommendation: Plan live tester proof with raw HID/Bluetooth connection evidence plus Game Controller evidence. [VERIFIED: context D-09/D-12]

3. **Will macOS send output/rumble report ID 2?**  
   - What we know: Android can receive set/interrupt callbacks and Game Controller has haptics APIs for supported controllers. [VERIFIED: Android SDK; CITED: Apple Game Controller haptics docs]  
   - What's unclear: Generic descriptor output behavior from macOS. [ASSUMED]  
   - Recommendation: Implement callback counters and report unsupported if no callback after documented probe. [VERIFIED: context D-13/D-16]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java 17 | Android/desktop Gradle tests | Yes [VERIFIED: `java -version`] | OpenJDK 17.0.19 | none |
| Android SDK platform API 35 | Compile/probe Android Bluetooth HID APIs | Yes [VERIFIED: `android.jar` path and `javap`] | API 35 | none |
| `adb` | Physical Android proof | Yes [VERIFIED: `adb version`] | 36.0.0-13206524 | manual device UI only |
| Gradle wrapper | Repeatable local test commands | No [VERIFIED: `find . -name gradlew`] | n/a | Use installed `gradle` only after local Gradle startup is repaired, or run tests in CI. [VERIFIED: `gradle --version` failed locally] |
| Installed Gradle CLI | Android/desktop local tests | Broken [VERIFIED: `gradle --version`] | command present, startup fails with `Failed to load native library 'libnative-platform.dylib' for Mac OS X aarch64` | Planner must add environment repair/checkpoint before relying on local Gradle tests. [VERIFIED: local command output] |
| Xcode `simctl` | Optional macOS tester/dev tooling | No [VERIFIED: `xcrun simctl` error] | n/a | Swift/AppKit tester can be manual or use existing tools; not blocker for Android HID code. [ASSUMED] |
| Local Bluetooth controller visibility | macOS Bluetooth pairing proof | Unclear [VERIFIED: `system_profiler SPBluetoothDataType` returned controllerInfo nil] | n/a | Use System Settings/manual proof; if local Bluetooth broken, need another Mac or hardware fix. [ASSUMED] |
| Android HID-capable phone | ANDR-09/DESK-03 proof | Unknown [VERIFIED: not probed in this research] | n/a | Test alternate Android phone before Windows VHF fallback. [VERIFIED: context D-04/D-19] |

**Missing dependencies with no fallback:**
- Live Android phone that exposes HID Device role is required for primary Phase 7 pass. [VERIFIED: requirements ANDR-09/DESK-03]
- macOS Bluetooth pairing/tester proof is required for DESK-03. [VERIFIED: context D-09/D-12]
- A working Android/desktop Gradle test runner is required for automated validation; local installed `gradle` is currently broken and no wrapper exists. [VERIFIED: local commands]

**Missing dependencies with fallback:**
- If Android HID proof fails after alternate phone, use completed Windows VHF path as fallback. [VERIFIED: context D-19]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Android/JVM main-function unit tests executed from Gradle `test`; no JUnit framework dependency observed. [VERIFIED: `android-host/app/build.gradle.kts`] |
| Config file | `android-host/app/build.gradle.kts` explicit test class runner list. [VERIFIED: file read] |
| Quick run command | `cd android-host && gradle test` after Gradle startup repair; currently blocked locally by `libnative-platform.dylib` load failure. [VERIFIED: local command output] |
| Full suite command | `cd android-host && gradle test && cd ../desktop-companion && gradle test` after Gradle startup repair. [VERIFIED: local Gradle currently broken; ASSUMED: commands match existing project layout] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ANDR-09 | Capability gate distinguishes Bluetooth off, missing `BLUETOOTH_CONNECT`, HID proxy unavailable, registration failed, no host, host disconnected. [VERIFIED: context D-02] | unit | `cd android-host && gradle test` | No - Wave 0 add `AndroidBluetoothHidGamepadStateTest.kt` and extend `PermissionGateTest`. |
| ANDR-09 | `getProfileProxy`/`registerApp` callbacks update status without crashing when unavailable. [VERIFIED: Android callback surface] | unit with fake adapter/proxy | `cd android-host && gradle test` | No - Wave 0 add adapter seam test. |
| ANDR-10 | HID descriptor bytes declare gamepad/joystick-style input with six buttons and four axes. [VERIFIED: context D-05/D-08] | unit/golden | `cd android-host && gradle test` | No - Wave 0 add `BtGunHidDescriptorTest.kt`. |
| ANDR-10 | Report packer maps trigger/reload/X/Y/A/B bits, stickX/stickY, aimX/aimY, stale/center behavior. [VERIFIED: context D-08] | unit/golden | `cd android-host && gradle test` | No - Wave 0 add `BtGunHidReportPackerTest.kt`. |
| ANDR-10 | Packer uses calibrated `aimX`/`aimY` first, raw aim fallback second. [VERIFIED: context D-07] | unit | `cd android-host && gradle test` | No - Wave 0 add in packer test. |
| ANDR-11 | Output report validator accepts only report ID 2/version 1/valid strength-duration-TTL/reserved zero. [VERIFIED: context D-14; Windows mapper precedent] | unit | `cd android-host && gradle test` | No - Wave 0 add `BtGunHidOutputReportMapperTest.kt`. |
| ANDR-11 | Invalid output reports call/report `reportError` and do not vibrate. [VERIFIED: context D-14] | unit with fake HID + fake phone | `cd android-host && gradle test` | No - Wave 0 add callback behavior test. |
| DESK-03 | macOS Bluetooth pairs to Android and sees OS-visible gamepad. [VERIFIED: context D-09/D-12] | manual/live | Evidence row in `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | No - Wave 0 add evidence schema/doc. |
| DESK-03 | Game Controller/tester shows live gun buttons and phone motion axes. [VERIFIED: context D-12] | manual/live | Tester output + user confirmation row | No - Wave 0 add tester/proof guide. |
| DESK-06 | Android callback records host output seen/not seen and phone haptic result or unsupported reason. [VERIFIED: context D-16] | manual/live + unit validator | `cd android-host && gradle test`; evidence row | No - Wave 0 add mapper test + proof doc. |
| PACK-03 | Docs frame Android HID as primary and CoreHID/DriverKit as fallback evidence only. [VERIFIED: requirements; quick summary] | docs/static | `rg -n 'Android Bluetooth HID|corehid-runtime-blocked|fallback' docs/setup/android-bluetooth-hid-gamepad.md` | No - add docs. |
| PACK-06 | Docs include setup, compatibility checks, pairing, descriptor/report bytes, output behavior, Windows fallback. [VERIFIED: requirements] | docs/static | `rg -n 'HID_DEVICE|registerApp|sendReport|onSetReport|Windows VHF fallback|redaction' docs/setup/android-bluetooth-hid-gamepad.md` | No - add docs. |

### Manual Proof Rows

| Proof ID | Required Evidence | Redaction |
|----------|-------------------|-----------|
| phase7-android-hid-proxy | Phone model class redacted, Android SDK/version bucket, proxy available/unavailable, registration accepted/failed. [VERIFIED: context D-02/D-04] | No Bluetooth MAC, phone serial, account name, raw device id. [VERIFIED: context evidence rule] |
| phase7-macos-bluetooth-paired | macOS Bluetooth UI/System Settings confirms connected controller label, sanitized. [VERIFIED: context D-09] | Redact Bluetooth address, exact phone name if personal, screenshots with nearby devices. [VERIFIED: context evidence rule] |
| phase7-gamecontroller-input | Tester shows trigger/reload/X/Y/A/B and four axes move from real gun/phone motion. [VERIFIED: context D-12] | Store sanitized text rows, not raw screenshots unless scrubbed. [VERIFIED: context evidence rule] |
| phase7-hid-output-callback | Android status shows `onSetReport`/`onInterruptData` seen or not seen, validation result, phone vibration result or unsupported reason. [VERIFIED: context D-16] | No raw host device ids; output bytes allowed only if non-secret descriptor/test bytes. [ASSUMED] |
| phase7-windows-fallback-gate | If blocked, records current phone + alternate phone attempt and cites Phase 6 Windows VHF fallback. [VERIFIED: context D-04/D-19] | No target host IPs or unique device ids unless redacted. [VERIFIED: evidence rule] |

### Sampling Rate

- **Per task commit:** `cd android-host && gradle test` for Android HID logic after Gradle startup repair. [VERIFIED: local Gradle currently broken]
- **Per wave merge:** Android tests plus relevant docs/static `rg` checks after Gradle startup repair. [ASSUMED]
- **Phase gate:** Unit tests green, macOS Bluetooth/Game Controller live proof complete, output proof or unsupported row complete, redaction scan clean. [VERIFIED: context D-09/D-16]

### Wave 0 Gaps

- [ ] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidDescriptorTest.kt` - covers ANDR-10 descriptor bytes. [ASSUMED]
- [ ] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt` - covers ANDR-10 report vectors. [ASSUMED]
- [ ] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidOutputReportMapperTest.kt` - covers ANDR-11 validation. [ASSUMED]
- [ ] `android-host/app/src/test/java/com/btgun/host/hid/AndroidBluetoothHidGamepadStateTest.kt` - covers ANDR-09 status transitions. [ASSUMED]
- [ ] Extend `PermissionGateTest.java` / `DashboardStateTest.kt` for HID capability rows. [VERIFIED: existing tests]
- [ ] `docs/setup/android-bluetooth-hid-gamepad.md` - covers PACK-03/PACK-06. [ASSUMED]
- [ ] `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` - sanitized evidence rows. [ASSUMED]
- [ ] Redaction scan command: `rg -n '([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}|qr_secret|stream key|HMAC key|private key|device[_ -]?id|serial' docs .planning/phases/07-macos-virtual-joystick-path`. [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no for Bluetooth HID input path; yes for retained LAN fallback. [VERIFIED: Phase 7 context; Phase 3/4 context] | Do not alter existing LAN pairing/auth; Bluetooth pairing handled by OS. [VERIFIED: existing architecture] |
| V3 Session Management | yes for HID mode lifecycle. [ASSUMED] | Explicit Start/Stop Bluetooth gamepad state, host connected/disconnected state, unregister on stop. [VERIFIED: context D-01/D-02] |
| V4 Access Control | yes. [ASSUMED] | Only connected HID host can receive reports; Android Bluetooth stack enforces paired host, app enforces one active host status. [CITED: Android BluetoothHidDevice docs; ASSUMED: one active host policy] |
| V5 Input Validation | yes. [VERIFIED: context D-14] | Strict output report parser; invalid shape triggers `reportError` and no haptic. [VERIFIED: context D-14; Android SDK `reportError`] |
| V6 Cryptography | no new crypto in primary Bluetooth HID path. [ASSUMED] | Do not add custom crypto; preserve existing LAN crypto separately. [VERIFIED: prior phase contexts] |
| V9 Communications | yes. [ASSUMED] | Use OS Bluetooth pairing/HID stack; do not build custom Bluetooth transport. [CITED: Android BluetoothHidDevice docs] |
| V14 Configuration | yes. [ASSUMED] | Capability matrix, blocked states, evidence redaction, no secrets/device ids in logs. [VERIFIED: context evidence rule] |

### Known Threat Patterns for Android HID Path

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed HID output triggers unbounded vibration | Tampering/DoS | Validate report id/type/version/length/ranges/reserved bytes; cap duration/TTL via existing haptic executor. [VERIFIED: context D-14; `DesktopHapticCommand.kt`] |
| Accidental pairing to wrong Mac | Spoofing | Show connected host status, manual proof step, and user-visible pairing window only after explicit action. [VERIFIED: context D-01/D-10; ASSUMED: host display detail available] |
| Sensitive device ids in evidence | Information Disclosure | Redaction scan and manifest-only sanitized rows. [VERIFIED: context evidence rule] |
| HID mode left advertised after session | Elevation/DoS | Stop/unregister HID app on Stop session/timeout/service destroy. [ASSUMED; VERIFIED: existing service stop pattern] |
| Desktop companion secretly remains in input path | Boundary violation | Validation must prove macOS Bluetooth/Game Controller input without desktop companion. [VERIFIED: context D-03/D-11] |

## Sources

### Primary (HIGH confidence)

- Local repo: `AGENTS.md`, `.planning/STATE.md`, `.planning/PROJECT.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, `07-CONTEXT.md`, quick reroute summary, Phase 2/4/5/6 contexts. [VERIFIED: codebase grep]
- Local repo code: `AndroidManifest.xml`, `HostCapabilityProbe.kt`, `PermissionGate.kt`, `HostSessionService.kt`, `NormalizedEvents.kt`, `DesktopHapticCommand.kt`, `PhoneHaptics.kt`, `VirtualControllerDescriptor.kt`, `WindowsHidReportPacker.kt`, `WindowsOutputReportMapper.kt`. [VERIFIED: codebase grep]
- Local Android SDK API 35 `android.jar` inspected with `javap`: `BluetoothHidDevice`, `BluetoothHidDevice.Callback`, `BluetoothHidDeviceAppSdpSettings`, `BluetoothProfile`, `BluetoothAdapter`, `PackageManager`. [VERIFIED: local SDK]
- Android `BluetoothHidDevice` docs: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice [CITED]
- Android `BluetoothHidDevice.Callback` docs: https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothHidDevice.Callback [CITED]
- Android `BluetoothHidDeviceAppSdpSettings` docs: https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings [CITED]
- Android Bluetooth permissions docs: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions [CITED]
- Apple Game Controller docs: https://developer.apple.com/documentation/gamecontroller/gccontroller and https://developer.apple.com/documentation/gamecontroller/gccontroller/extendedgamepad [CITED]
- Apple Support macOS controller pairing: https://support.apple.com/guide/games/connect-a-game-controller-devf8cec167c/mac [CITED]

### Secondary (MEDIUM confidence)

- Apple Game Controller haptics docs: https://developer.apple.com/documentation/gamecontroller/gccontroller/haptics [CITED]
- USB-IF HID Usage Tables for gamepad/joystick recognition: https://www.usb.org/sites/default/files/hut1_6.pdf [CITED]

### Tertiary (LOW confidence)

- Community reports imply Android HID Device support can vary by device/OEM; this is treated as [ASSUMED] and gated by live phone proof, not as authoritative. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Android API surface, existing code, and official docs are verified. [VERIFIED: local SDK; official docs]
- Architecture: HIGH - Phase 7 context locks Android HID primary path and desktop diagnostic-only boundary. [VERIFIED: `07-CONTEXT.md`]
- Compatibility proof: MEDIUM - phone HID role and macOS output behavior need live device evidence. [ASSUMED; VERIFIED: context requires proof]
- Pitfalls: MEDIUM - API/permission pitfalls are verified; OEM/macOS behavior is live-proof dependent. [VERIFIED: Android docs; ASSUMED: compatibility variance]

**Research date:** 2026-06-10
**Valid until:** 2026-07-10 for API docs; 2026-06-17 for compatibility conclusions because phone/macOS behavior must be measured on real devices. [ASSUMED]
