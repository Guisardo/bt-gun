# Phase 07: macos-virtual-joystick-path - Research

**Researched:** 2026-06-10
**Domain:** macOS virtual HID gamepad backend, CoreHID, IOHIDUserDevice, HIDDriverKit fallback
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

**Source:** `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md` [VERIFIED: codebase grep]

### Locked Decisions

### macOS HID Path
- **D-01:** Phase 7 targets CoreHID `HIDVirtualDevice` first for the macOS virtual joystick proof.
- **D-02:** CoreHID fallback is research-gated. Switch away from CoreHID only if official documentation or local compile/runtime proof shows CoreHID cannot create an OS-visible gamepad-style device that satisfies Phase 7.
- **D-03:** If CoreHID cannot receive macOS-origin output/rumble reports, HIDDriverKit/system extension becomes the mandatory fallback path for Phase 7 output proof.

### macOS Proof Target
- **D-04:** Phase 7 pass condition is layered proof: CLI HID enumeration, macOS-visible game controller/gamepad UI or tester evidence, and agent/user visual confirmation.
- **D-05:** Final input proof must use a live paired Android/gun stream moving macOS-visible joystick axes/buttons. Replay fixtures are allowed for automated tests, CI, and debugging only.
- **D-06:** Final visual proof requires both agent-captured evidence and user confirmation that the virtual device/input is visible in a Mac UI or tester.

### Output Haptic Proof
- **D-07:** Phase 7 cannot pass unless macOS OS-origin output/rumble maps to Android phone haptic.
- **D-08:** The existing desktop companion phone-haptic command path remains the v1 haptic transport, but it is not enough by itself unless it is triggered by macOS-origin output/rumble.
- **D-09:** If CoreHID cannot expose the required output/rumble path, planners must include HIDDriverKit fallback work rather than documenting output as unsupported.

### Packaging and Development Setup
- **D-10:** Phase 7 targets a development proof package: local macOS app/tool with documented launch, signing, permission, and fallback steps.
- **D-11:** The proof target is the current development Mac unless planning discovers a blocker: macOS 26.2 build 25C56 on arm64.
- **D-12:** Use ad-hoc/local development signing first where CoreHID permits. Do not require Developer ID signing for Phase 7 unless the selected fallback path requires it.
- **D-13:** Document exact commands, permission prompts, minimum observed OS requirement, and DriverKit entitlement fallback notes.

### Live Stream Cutover
- **D-14:** The macOS runtime mirrors the Windows runtime shape: `MacosBackendRuntime` attaches to `ControlServer.onUdpInputReceived`, preserves any previous callback, maps `UdpReceivedInput` to `SemanticControllerState`, publishes to the macOS backend, and exposes diagnostics.
- **D-15:** Stale-stream behavior must match Phase 4 and Windows: clear active buttons, keep last aim axes, and expose stale diagnostic state.
- **D-16:** A native Swift or Objective-C helper is allowed if needed for CoreHID. The Kotlin/JVM desktop companion must keep ownership of LAN pairing, session security, UDP input validation, semantic state mapping, and phone-haptic routing.

### the agent's Discretion
- Choose exact CoreHID report descriptor bytes, vendor/product ids, report ids, helper process protocol, Swift/Objective-C project layout, local IPC mechanism, CLI enumeration commands, and macOS tester/tooling, as long as the decisions above hold.
- Choose exact DriverKit fallback design during planning if CoreHID cannot satisfy OS-visible device or OS-origin output proof.
- Choose exact evidence artifact names and redaction format, provided no pairing material, session secrets, keys, or private signing material are committed.

### Deferred Ideas (OUT OF SCOPE)
- Production notarized installer, release signing/distribution polish, and paid Developer ID flow are deferred beyond Phase 7 unless required by the selected fallback proof path.
- Profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Replay diagnostics beyond platform smoke remain Phase 10.
- Physical gun motor rumble remains v2/deferred.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DESK-03 | Desktop companion can expose a regular gamepad-style virtual joystick on macOS Apple Silicon. [VERIFIED: `.planning/REQUIREMENTS.md`] | CoreHID `HIDVirtualDevice` is the primary path because Apple documents virtual HID emulation and the local SDK exposes `HIDVirtualDevice` with descriptor-backed properties and `dispatchInputReport`. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice; VERIFIED: local SDK `CoreHID.swiftinterface` lines 121-142] |
| DESK-06 | macOS virtual joystick path can receive desktop rumble/output requests or clearly report the platform limitation while preserving v1 phone haptic support. [VERIFIED: `.planning/REQUIREMENTS.md`] | CoreHID delegate and IOHIDUserDevice both expose set-report callbacks; Phase 7 must prove a separate OS/HID client can trigger them and route bytes to the existing Kotlin haptic command path. [VERIFIED: local SDK `CoreHID.swiftinterface` lines 166-168; VERIFIED: local SDK `IOHIDUserDevice.h` lines 168-187] |
| PACK-03 | Repository documents selected macOS virtual HID strategy, entitlement requirements, and development setup. [VERIFIED: `.planning/REQUIREMENTS.md`] | Apple docs and local headers show virtual HID and DriverKit entitlement surfaces; local environment audit shows missing full Xcode/signing and a Swift SDK/toolchain mismatch that setup docs must address. [CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.hid.virtual.device; VERIFIED: local commands `xcodebuild -version`, `swift -e`, `security find-identity`] |
</phase_requirements>

## Summary

Use CoreHID `HIDVirtualDevice` first, but make Wave 0 a real feasibility gate rather than assuming it works. Apple documents CoreHID virtual devices, and the current local macOS 26.2 SDK exposes `HIDVirtualDevice.Properties`, `activate(delegate:)`, `dispatchInputReport`, and delegate set/get report callbacks. [CITED: https://developer.apple.com/documentation/corehid/creatingvirtualdevices; VERIFIED: local SDK `CoreHID.swiftinterface` lines 121-168] The same local target currently cannot compile a Swift CoreHID probe because only Command Line Tools are selected, no full Xcode app is installed, no valid code-signing identities are present, and the Swift compiler rejects the CLT SDK as a toolchain mismatch. [VERIFIED: local commands `xcodebuild -version`, `find /Applications -name Xcode*.app`, `security find-identity`, `swift -e`]

CoreHID appears capable of receiving OS-origin output reports at the API level: `HIDVirtualDeviceDelegate` has `receivedSetReportRequestOfType`, and the lower-level `IOHIDUserDevice` API has set-report callbacks. [VERIFIED: local SDK `CoreHID.swiftinterface` lines 166-168; VERIFIED: local SDK `IOHIDUserDevice.h` lines 168-187] That is not yet proof that a macOS game/controller tester will generate rumble for this virtual gamepad descriptor, so the plan must include a separate OS/HID output probe that opens the enumerated virtual device and calls `IOHIDDeviceSetReport` against output report ID 2, then requires the delegate path to route a phone haptic command. [ASSUMED]

**Primary recommendation:** Plan a CoreHID user-space helper first, with an immediate output-report proof; if either OS-visible enumeration or set-report-to-phone-haptic proof fails after setup is fixed, switch to a HIDDriverKit system-extension fallback in the same phase. [VERIFIED: `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md`; VERIFIED: local SDK `IOHIDDevice.iig` lines 89-107]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| LAN pairing/session security | Desktop companion Kotlin/JVM | Android host | Existing `ControlServer` and session code own trust, WSS control, UDP validation, and haptic transport; native HID helper must not receive secrets. [VERIFIED: `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md`; VERIFIED: codebase grep] |
| UDP input to semantic state | Desktop companion Kotlin/JVM | - | Existing `UdpControllerStateAdapter` maps `UdpReceivedInput` to `SemanticControllerState`; Phase 7 must reuse it. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt`] |
| macOS HID report packing | Desktop companion Kotlin/JVM | Native helper | Kotlin tests can prove deterministic report bytes like the Windows packer; helper should only publish bytes to CoreHID and return output bytes. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt`; ASSUMED] |
| OS-visible virtual device | Native macOS helper | Desktop companion Kotlin/JVM | CoreHID and IOHIDUserDevice are native Apple APIs; Kotlin/JVM should call a helper boundary instead of embedding platform HID API details. [VERIFIED: local SDK `CoreHID.swiftinterface`; VERIFIED: local SDK `IOHIDUserDevice.h`] |
| OS-origin output/rumble receive | Native macOS helper | Desktop companion Kotlin/JVM | Helper receives `SetReport`; Kotlin maps the report bytes to `HapticCommand` and sends through authenticated control channel. [VERIFIED: local SDK `CoreHID.swiftinterface` lines 166-168; VERIFIED: `WindowsVirtualControllerBackend.kt`] |
| DriverKit fallback | macOS system extension | Desktop companion Kotlin/JVM | HIDDriverKit `IOHIDDevice` provides `handleReport`, `getReport`, and `setReport`; packaging and approval live in System Extensions, while Kotlin keeps app/session logic. [VERIFIED: local SDK `IOHIDDevice.iig` lines 45-107; CITED: https://developer.apple.com/documentation/hiddriverkit] |
| Development setup documentation | Repository docs | Native helper build scripts | PACK-03 requires documenting strategy, entitlements, commands, prompts, and fallback notes. [VERIFIED: `.planning/REQUIREMENTS.md`] |

## Project Constraints (from AGENTS.md)

- Use GSD workflow for project edits; this research is part of the Phase 7 GSD flow. [VERIFIED: `AGENTS.md`]
- New user-facing branches use `feature/<short-kebab-slug>`; do not use `codex/`, `codex-`, or agent-name prefixes unless explicitly requested. [VERIFIED: `AGENTS.md`]
- Before creating or pushing a branch, state the exact branch name. [VERIFIED: `AGENTS.md`]
- Keep v1 support for Windows 11 x64 and macOS Apple Silicon; protocol and virtual-controller code must not assume one platform. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/PROJECT.md`]
- Transport remains Android-to-desktop Wi-Fi/LAN in v1; no direct desktop-to-gun Bluetooth in Phase 7. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/REQUIREMENTS.md`]
- Desktop profile/mapping owns aim mapping; Android sends normalized gyro/raw aim data. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/research/ARCHITECTURE.md`]
- HID shape must be a normal gamepad-style joystick, not a custom HID gun report. [VERIFIED: `AGENTS.md`; VERIFIED: `VirtualControllerDescriptor.kt`]
- Do not commit pairing material, session secrets, keys, private signing material, or sensitive raw logs in evidence. [VERIFIED: `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md`; VERIFIED: prior evidence manifests]

## Standard Stack

### Core

| Technology | Version | Purpose | Why Standard |
|------------|---------|---------|--------------|
| Kotlin/JVM desktop companion | Kotlin plugin 2.0.21, JVM 17 [VERIFIED: `desktop-companion/build.gradle.kts`; `java -version`] | Preserve existing LAN/session/security, semantic state, backend contract, and haptic control path. | Existing desktop companion is Kotlin/JVM, and Windows runtime already demonstrates the platform backend boundary. [VERIFIED: `WindowsBackendRuntime.kt`] |
| CoreHID `HIDVirtualDevice` | macOS 15+ API, local SDK target macOS 26.5 interface [VERIFIED: local SDK `CoreHID.swiftinterface` lines 1-3, 121-142] | Primary user-space virtual gamepad-style joystick device. | Apple documents virtual HID emulation; local SDK exposes descriptor-backed properties and input dispatch. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice; VERIFIED: local SDK] |
| CoreHID `HIDVirtualDeviceDelegate` | macOS 15+ API [VERIFIED: local SDK `CoreHID.swiftinterface` lines 165-168] | Receive OS/HID set/get report requests for output/haptic proof. | It is the direct CoreHID callback surface for incoming reports. [VERIFIED: local SDK] |
| IOKit `IOHIDUserDevice` | macOS 10.15+ API [VERIFIED: local SDK `IOHIDUserDevice.h` lines 113-143] | Legacy/alternate native user-space virtual HID implementation or diagnostic shim. | Header documents virtual `IOHIDDevice` creation, required report descriptor property, entitlement requirement, report dispatch, and set-report callbacks. [VERIFIED: local SDK `IOHIDUserDevice.h` lines 113-187, 350-375] |
| HIDDriverKit `IOHIDDevice` | DriverKit 19.0+ API [VERIFIED: local SDK `IOHIDDevice.iig` lines 45-107] | Mandatory fallback if user-space CoreHID/IOHIDUserDevice cannot satisfy OS-visible or OS-origin output proof. | HIDDriverKit exposes provider-side `handleReport`, `getReport`, and `setReport` methods. [VERIFIED: local SDK; CITED: https://developer.apple.com/documentation/hiddriverkit] |
| System Extensions framework | Current macOS API [CITED: https://developer.apple.com/documentation/systemextensions/installing-system-extensions-and-drivers] | Install/activate HIDDriverKit fallback driver if needed. | Apple documents shipping system extensions inside an app bundle and activating them from the app. [CITED: https://developer.apple.com/documentation/systemextensions/installing-system-extensions-and-drivers] |

### Supporting

| Tool/API | Version | Purpose | When to Use |
|----------|---------|---------|-------------|
| `hidutil list` | macOS 26.2 local tool [VERIFIED: local `hidutil list -h`] | CLI enumeration of HID services/devices by vendor/product/usage. | Automated proof that the virtual device is OS-visible after helper launch. [VERIFIED: local command output] |
| `ioreg -r -c IOHIDDevice -l -w 0` | macOS 26.2 local tool [VERIFIED: local `ioreg -h`] | IORegistry proof of HID properties, report descriptor, max report sizes, and matching. | Secondary enumeration evidence and debugging descriptor/entitlement behavior. [VERIFIED: local command output] |
| `IOHIDDeviceSetReport` via separate probe process | IOKit user-space HID client [VERIFIED: Apple IOKit docs page exists; VERIFIED: local IOKit headers expose report APIs] | Generate OS/HID-origin output report against the virtual device. | Required before claiming DESK-06 output proof; simulated backend output is not enough. [ASSUMED] |
| `codesign` with entitlements plist | macOS local tool [VERIFIED: local `codesign` command exists] | Sign helper/app with virtual HID entitlement where allowed. | CoreHID/IOHIDUserDevice proof and DriverKit fallback setup docs. [VERIFIED: local SDK `IOHIDUserDevice.h` lines 119-121] |
| `systemextensionsctl` | macOS local tool [VERIFIED: `command -v systemextensionsctl`] | Inspect DriverKit/system-extension fallback installation state. | Fallback proof and PACK-03 docs if CoreHID cannot pass. [CITED: https://developer.apple.com/documentation/systemextensions/ossystemextensionmanager] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| CoreHID Swift helper | Objective-C/C helper using `IOHIDUserDeviceCreateWithProperties` | More verbose but avoids current Swift SDK/toolchain mismatch; still requires the virtual HID entitlement and must pass the same OS-visible/output proof. [VERIFIED: local SDK `IOHIDUserDevice.h`; VERIFIED: local ObjC syntax probe] |
| User-space CoreHID/IOHIDUserDevice | HIDDriverKit system extension | Heavier setup, entitlements, user approval, and full Xcode packaging, but required if user-space path cannot satisfy output proof. [VERIFIED: `.planning/phases/07.../07-CONTEXT.md`; CITED: https://developer.apple.com/documentation/driverkit/requesting-entitlements-for-driverkit-development] |
| Length-prefixed child-process IPC | Loopback TCP/WebSocket to helper | Child-process IPC avoids exposing another local network service and keeps LAN/session secrets entirely in Kotlin. [ASSUMED] |
| Output proof through a game only | Separate `IOHIDDeviceSetReport` output probe first | Game/controller testers may not generate generic HID rumble consistently; an explicit HID set-report probe gives deterministic proof that the OS/HID stack reaches the helper. [ASSUMED] |

**Installation:**

No new external packages should be installed for Phase 7 unless planning discovers an unavoidable tester dependency. [VERIFIED: current stack uses platform APIs; ASSUMED for tester tooling]

```bash
# Existing project validation, from desktop-companion/
GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test
GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle smokeDesktopBackendMacosStub

# Phase 7 should add commands like:
GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle smokeDesktopBackendMacosCoreHid
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|PrimaryUsage|MaxOutputReportSize'
```

**Version verification:** No npm/PyPI/crates packages are recommended. [VERIFIED: package audit not triggered] Apple framework availability was verified against the local macOS SDK and official Apple documentation. [VERIFIED: local SDK paths; CITED: https://developer.apple.com/documentation/corehid]

## Package Legitimacy Audit

No external package installation is recommended for Phase 7 research. [VERIFIED: Standard Stack above]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| none | n/a | n/a | n/a | n/a | n/a | No audit required because Phase 7 should use Apple platform APIs and existing Gradle/Kotlin dependencies. [VERIFIED: codebase grep; ASSUMED for tester tooling] |

**Packages removed due to slopcheck [SLOP] verdict:** none. [VERIFIED: no external packages recommended]
**Packages flagged as suspicious [SUS]:** none. [VERIFIED: no external packages recommended]

## Architecture Patterns

### System Architecture Diagram

```text
--------------------+        authenticated UDP/WSS        +---------------------------+
| Android host/gun   | ----------------------------------> | Kotlin desktop companion  |
| controls + motion  |                                    | ControlServer             |
+--------------------+                                    | UdpControllerStateAdapter |
                                                          | MacosBackendRuntime       |
                                                          +-------------+-------------+
                                                                        |
                                                                        | length-prefixed local frames
                                                                        | no session secrets
                                                                        v
                                                          +---------------------------+
                                                          | Native macOS HID helper   |
                                                          | CoreHID HIDVirtualDevice  |
                                                          | or IOHIDUserDevice shim   |
                                                          +------+------+-------------+
                                                                 |      ^
                                                     input report|      |set/get output report
                                                                 v      |
                                                          +---------------------------+
                                                          | macOS HID stack           |
                                                          | hidutil/ioreg/tester/game |
                                                          +-------------+-------------+
                                                                        |
                                                                        | output bytes returned
                                                                        v
                                                          +---------------------------+
                                                          | Kotlin output mapper      |
                                                          | ControlServer haptic cmd  |
                                                          +-------------+-------------+
                                                                        |
                                                                        v
                                                          Android phone vibration
```

### Recommended Project Structure

```text
desktop-companion/
  src/main/kotlin/com/btgun/desktop/backend/macos/
    MacosHidReportPacker.kt          # semantic state -> macOS input report bytes [ASSUMED]
    MacosOutputReportMapper.kt       # output report bytes -> HapticCommand [ASSUMED]
    MacosVirtualControllerBackend.kt # Kotlin backend contract adapter [ASSUMED]
    MacosBackendRuntime.kt           # ControlServer callback attachment [VERIFIED: Windows pattern]
    MacosHidHelperClient.kt          # child process IPC client [ASSUMED]
  src/main/kotlin/com/btgun/desktop/smoke/
    MacosCoreHidBackendSmokeMain.kt  # replay smoke plus optional OS output probe [ASSUMED]
  native/macos-hid-helper/
    Sources/                         # Swift CoreHID helper or ObjC IOHIDUserDevice shim [ASSUMED]
    Entitlements.plist               # virtual HID entitlement where allowed [VERIFIED: local SDK]
docs/setup/
  macos-virtual-hid.md               # PACK-03 strategy/setup/fallback proof docs [VERIFIED: requirement]
docs/evidence/manifests/
  phase7-macos-virtual-hid.jsonl     # sanitized proof manifest [ASSUMED]
```

### Pattern 1: CoreHID Helper as a Thin Native HID Bridge

**What:** Keep CoreHID/IOHIDUserDevice creation, activation, and report dispatch inside a signed native helper; pass only descriptor metadata, input report bytes, and output report events over local IPC. [VERIFIED: local SDK APIs; ASSUMED for IPC]

**When to use:** Use for the Phase 7 primary path after Wave 0 fixes local Xcode/Swift/signing setup. [VERIFIED: context D-01/D-16]

**Example:**

```swift
// Source: local SDK CoreHID.swiftinterface lines 121-168 [VERIFIED: local SDK]
import CoreHID
import Foundation

final class OutputDelegate: HIDVirtualDeviceDelegate {
    func hidVirtualDevice(
        _ device: HIDVirtualDevice,
        receivedSetReportRequestOfType type: HIDReportType,
        id: HIDReportID?,
        data: Data
    ) async throws {
        // Forward type, report id, and bytes to Kotlin over local IPC.
    }

    func hidVirtualDevice(
        _ device: HIDVirtualDevice,
        receivedGetReportRequestOfType type: HIDReportType,
        id: HIDReportID?,
        maxSize: Int
    ) async throws -> Data {
        Data()
    }
}
```

### Pattern 2: Kotlin Owns Runtime Attachment and Diagnostics

**What:** Mirror `WindowsBackendRuntime`: preserve previous UDP callback, publish semantic state to backend, drain output haptics, send through `ControlServer`, and expose diagnostics. [VERIFIED: `WindowsBackendRuntime.kt`]

**When to use:** Always for live stream cutover in Phase 7. [VERIFIED: context D-14/D-15]

**Example:**

```kotlin
// Source: WindowsBackendRuntime pattern [VERIFIED: codebase grep]
val callback: (UdpReceivedInput) -> Unit = { input ->
    previousUdpCallback?.invoke(input)
    val state = UdpControllerStateAdapter.toState(input)
    val publishResult = backend.publish(state)
    val hapticResults = backend.drainOutputHaptics(System.nanoTime()).map { command ->
        controlServer.sendHapticCommand(command, nowElapsedNanos = System.nanoTime())
    }
    // Update macOS diagnostics: lifecycle, publish result, stale, sequence, haptic result.
}
```

### Pattern 3: Separate OS Output Probe from Simulated Output

**What:** Add a process outside the helper that finds the virtual device by vendor/product and sends output report ID 2 through the macOS HID API; require the helper delegate to receive it and Kotlin to route phone haptic. [ASSUMED]

**When to use:** Required before claiming DESK-06 output proof. [VERIFIED: context D-07/D-08/D-09]

**Example:**

```text
macos-hid-output-probe
  -> IOHIDManager match VendorID/ProductID
  -> IOHIDDeviceSetReport(kIOHIDReportTypeOutput, reportID=2, bytes)
  -> CoreHID delegate receives SetReport
  -> Kotlin MacosOutputReportMapper creates HapticCommand
  -> ControlServer sends authenticated phone haptic command
```

### Anti-Patterns to Avoid

- **Counting `simulateOutputReport` as DESK-06:** Simulated reports prove mapper logic only; Phase 7 requires OS-origin output/rumble. [VERIFIED: context D-07/D-08]
- **Putting LAN/session keys in the native helper:** The helper should not own pairing, WSS, UDP auth, or haptic transport. [VERIFIED: context D-16]
- **Skipping environment setup proof:** Current local Swift/Xcode/signing state is not ready for CoreHID execution; planning must gate implementation on fixing that. [VERIFIED: local commands]
- **Treating "gamepad UI sees axes" as enough:** D-04 through D-07 require CLI enumeration, tester/UI evidence, live Android input, and OS-origin output-to-phone-haptic proof. [VERIFIED: context]
- **Jumping to DriverKit before CoreHID proof:** D-01/D-02 lock CoreHID first unless official docs or local proof show it cannot satisfy the phase. [VERIFIED: context]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Virtual HID device registration | Custom kernel extension or raw IORegistry manipulation | CoreHID `HIDVirtualDevice` first; IOHIDUserDevice shim if Swift wrapper is blocked; HIDDriverKit fallback if output proof fails. | Apple platform APIs own HID registration, report delivery, entitlement checks, and OS matching. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice; VERIFIED: local SDK] |
| Driver/system-extension install flow | Shell scripts copying drivers into system locations | System Extensions framework activation from an app if DriverKit fallback is needed. | Apple documents app-bundled system extension activation and user approval flow. [CITED: https://developer.apple.com/documentation/systemextensions/installing-system-extensions-and-drivers] |
| Session security in native helper | New local auth protocol or helper-side LAN stack | Existing Kotlin `ControlServer` and UDP receiver. | Existing code already validates and routes authenticated session traffic. [VERIFIED: codebase grep] |
| Output/rumble haptic mapping | Freeform strings or platform-specific ad hoc events | Versioned output report bytes mapped by `MacosOutputReportMapper` to existing `HapticCommand`. | Windows path already uses a report mapper and preserves haptic TTL/duration validation. [VERIFIED: `WindowsOutputReportMapper.kt`] |
| Evidence storage | Raw logs/screenshots with secrets | Sanitized JSONL manifest and redacted setup notes. | Prior phases explicitly avoid committed secrets, device ids, proof values, and raw sensitive logs. [VERIFIED: `.planning/STATE.md`; `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl`] |

**Key insight:** The hard part is not HID byte packing; it is proving macOS policy, entitlement, enumeration, and OS-origin output behavior on the actual target. [VERIFIED: local environment audit; VERIFIED: context D-02/D-03/D-07]

## Common Pitfalls

### Pitfall 1: CoreHID Exists but Local Tooling Cannot Build It

**What goes wrong:** The plan assumes Swift/CoreHID can build immediately, but this host currently has only CLT selected and Swift reports an SDK/toolchain mismatch. [VERIFIED: local `xcodebuild -version`; local `swift -e`]

**Why it happens:** CoreHID is present in the SDK, but Swift module compilation depends on a matching Apple toolchain/SDK pair. [VERIFIED: local Swift error]

**How to avoid:** Plan Wave 0 setup: install/select full matching Xcode or matching CLT, configure module cache under writable project/temp paths, and compile a minimal CoreHID helper before backend work. [ASSUMED]

**Warning signs:** `swift -e 'import CoreHID'` fails, `xcodebuild -version` reports CLT-only, or `security find-identity` has no signing identities. [VERIFIED: local commands]

### Pitfall 2: Entitlement Confusion Between User-Space Virtual HID and DriverKit

**What goes wrong:** The helper creates no device because virtual HID entitlement is missing, or the plan requests DriverKit entitlements unnecessarily. [VERIFIED: local `IOHIDUserDevice.h` lines 119-121; CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.hid.virtual.device]

**Why it happens:** IOHIDUserDevice explicitly requires `com.apple.developer.hid.virtual.device`; DriverKit has separate entitlements such as `com.apple.developer.driverkit`, `com.apple.developer.driverkit.family.hid.device`, and `com.apple.developer.driverkit.transport.hid` in local headers. [VERIFIED: local SDK `IOHIDUserDevice.h`; VERIFIED: local SDK `IOKitKeys.h` lines 121-150]

**How to avoid:** Document two entitlement paths: CoreHID/IOHIDUserDevice user-space virtual HID first, HIDDriverKit/system-extension fallback only if needed. [VERIFIED: context D-01/D-03]

**Warning signs:** Device construction returns nil, helper runs unsigned, `hidutil list` has no matching device, or System Settings asks for extension approval when the plan expected a user-space helper only. [ASSUMED]

### Pitfall 3: Output Report API Exists but Rumble Is Not Proven

**What goes wrong:** The backend declares output-report support after unit tests pass, but no OS/HID client has triggered the actual CoreHID set-report callback. [VERIFIED: context D-07/D-08]

**Why it happens:** CoreHID delegate supports SetReport at API level, but generic macOS rumble behavior for a custom gamepad descriptor still needs target proof. [VERIFIED: local SDK; ASSUMED for generic rumble behavior]

**How to avoid:** Add an output probe process and require phone vibration from a live paired Android session before setting `outputReport=true` in capabilities. [VERIFIED: context D-07; ASSUMED for probe design]

**Warning signs:** Only `simulateOutputReport` works, `MaxOutputReportSize` is 0 in IORegistry, or the delegate never receives SetReport. [VERIFIED: local `ioreg` output shape; ASSUMED for failure modes]

### Pitfall 4: Native Helper Becomes a Second Desktop Companion

**What goes wrong:** Native code starts owning session security, profile mapping, transport, or haptic command transport. [VERIFIED: context D-16]

**Why it happens:** It is tempting to put all "macOS backend" behavior in the native helper. [ASSUMED]

**How to avoid:** Helper protocol accepts only report bytes/device control and emits output report bytes/status; Kotlin owns all semantic mapping, session security, diagnostics, and haptic routing. [VERIFIED: context D-16; ASSUMED for protocol]

**Warning signs:** Helper code imports networking libraries, sees session ids/secrets, or constructs `HapticCommand` directly. [ASSUMED]

### Pitfall 5: DriverKit Fallback Underplanned

**What goes wrong:** CoreHID output fails late, but Phase 7 has no tasks for Xcode, system extension packaging, entitlements, activation, or user approval. [VERIFIED: context D-03/D-09]

**Why it happens:** DriverKit is much heavier than user-space CoreHID and needs app-bundled system extension flow. [CITED: https://developer.apple.com/documentation/hiddriverkit; CITED: https://developer.apple.com/documentation/systemextensions/installing-system-extensions-and-drivers]

**How to avoid:** Add a fallback branch in the plan that starts only after a documented CoreHID failure gate, with explicit setup and entitlement checkpoints. [VERIFIED: context D-03]

**Warning signs:** Plan says "document unsupported" after CoreHID output failure; that contradicts D-07/D-09. [VERIFIED: context]

## Code Examples

### IOHIDUserDevice User-Space Shim Shape

```objective-c
// Source: IOHIDUserDevice.h lines 113-187 and 350-375 [VERIFIED: local SDK]
IOHIDUserDeviceRef device =
    IOHIDUserDeviceCreateWithProperties(kCFAllocatorDefault, properties, 0);

IOHIDUserDeviceRegisterSetReportCallback(
    device,
    set_report_callback,
    context
);

IOHIDUserDeviceHandleReportWithTimeStamp(
    device,
    mach_absolute_time(),
    report_bytes,
    report_length
);
```

### DriverKit Fallback Surface

```cpp
// Source: HIDDriverKit/IOHIDDevice.iig lines 60-107 [VERIFIED: local SDK]
kern_return_t MyHidDevice::handleReport(
    uint64_t timestamp,
    IOMemoryDescriptor *report,
    uint32_t reportLength,
    IOHIDReportType reportType,
    IOOptionBits options
);

kern_return_t MyHidDevice::setReport(
    IOMemoryDescriptor *report,
    IOHIDReportType reportType,
    IOOptionBits options,
    uint32_t completionTimeout,
    OSAction *action
);
```

### CLI Evidence Commands

```bash
# Source: local hidutil/ioreg help [VERIFIED: local commands]
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|PrimaryUsage|MaxOutputReportSize'
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Kernel extensions for virtual HID | User-space CoreHID/IOHIDUserDevice first where entitlement permits; HIDDriverKit system extension as fallback | CoreHID `HIDVirtualDevice` is documented as macOS 15+ and local SDK exposes it. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice; VERIFIED: local SDK] | Phase 7 should not start with DriverKit unless CoreHID proof fails. [VERIFIED: context D-01/D-02] |
| Driver installed by copying system files | App-bundled System Extensions activation for DriverKit fallback | Apple System Extensions docs describe app-bundled install/activation. [CITED: https://developer.apple.com/documentation/systemextensions/installing-system-extensions-and-drivers] | Fallback docs must include app bundle, activation request, user approval, and possible reboot/result handling. [CITED: https://developer.apple.com/documentation/systemextensions/ossystemextensionrequest] |
| Stub macOS smoke | Real OS-visible CoreHID smoke plus live Android/gun proof | Phase 7 context tightens proof target after Phase 5 stubs. [VERIFIED: `07-CONTEXT.md`] | Existing `MacosBackendSmokeMain` must be evolved or superseded; stub smoke cannot pass DESK-03/DESK-06. [VERIFIED: `MacosBackendSmokeMain.kt`] |

**Deprecated/outdated:**
- Treating Phase 5 macOS stub capabilities as product support is invalid for Phase 7. [VERIFIED: `BackendCapabilityPresets.macosStub`; VERIFIED: context D-04/D-07]
- Documenting macOS output as unsupported after CoreHID failure is invalid under the locked Phase 7 context; DriverKit fallback work is required. [VERIFIED: context D-03/D-09]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Use a child-process length-prefixed local helper protocol instead of loopback TCP. | Standard Stack, Architecture Patterns | Planner may need to choose a different IPC if helper lifetime or buffering behaves poorly. |
| A2 | Kotlin should pack macOS input reports and map output bytes, while native helper only publishes/receives HID bytes. | Responsibility Map, Project Structure | Planner may move packing into Swift/ObjC if CoreHID descriptor/report coupling is easier there. |
| A3 | A separate `IOHIDDeviceSetReport` probe is acceptable evidence of OS/HID-origin output before game-specific rumble proof. | Patterns, Pitfalls | User may require a macOS game/controller UI that generates rumble instead of a HID API probe. |
| A4 | No third-party macOS controller tester package is needed. | Package Audit, Environment | Planner may add a human-verified tester dependency if built-in CLI/UI evidence is insufficient. |

## Open Questions (RESOLVED)

1. **Can the current developer machine obtain/use `com.apple.developer.hid.virtual.device` for a local proof?**
   - What we know: Local headers say IOHIDUserDevice virtual device creation requires this entitlement. [VERIFIED: local SDK `IOHIDUserDevice.h` lines 119-121]
   - RESOLVED: Treat entitlement availability as a Wave 1 execution gate, not an unresolved planning question. Plan `07-01` must compile/sign/run a minimal helper and record exact entitlement/signing result before backend support can be claimed.
   - RESOLVED: If ad-hoc/local signing cannot use the entitlement, execution must either use an approved local signing/provisioning path or record a blocker. Do not weaken DESK-03/DESK-06 or claim CoreHID support from docs alone.

2. **Will generic macOS game/controller UI generate rumble/output for this custom gamepad descriptor?**
   - What we know: CoreHID and IOHIDUserDevice expose set-report callbacks. [VERIFIED: local SDK]
   - RESOLVED: Generic game/controller UI rumble is not required as the deterministic Phase 7 output proof. Plan `07-05` must use a separate macOS HID client/probe that calls `IOHIDDeviceSetReport` for output report ID `0x02`, and final Plan `07-07` must prove that OS/HID-origin output reaches Android phone haptics.
   - RESOLVED: UI/tester evidence remains required for visible input per D-04/D-06, but it cannot replace the separate OS-origin output probe for DESK-06.

3. **Is Swift CoreHID viable after toolchain repair, or should the helper use Objective-C IOHIDUserDevice?**
   - What we know: Current `swift -e import CoreHID` fails because the SDK and compiler build ids differ. [VERIFIED: local command]
   - RESOLVED: Plan `07-01` attempts Swift CoreHID first because D-01 locks CoreHID as the first path. If Swift remains blocked by toolchain mismatch after setup, the same plan may use Objective-C `IOHIDUserDevice` as a user-space virtual HID shim without switching to DriverKit.
   - RESOLVED: DriverKit fallback is reserved for recorded user-space visibility/output/runtime failure after CoreHID/IOHIDUserDevice proof attempts, per D-02/D-03/D-09.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| macOS target | DESK-03 live proof | yes | macOS 26.2 build 25C56, arm64 [VERIFIED: `sw_vers`, `uname -m`] | none |
| Command Line Tools SDK | CoreHID/IOKit header research | yes | `/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk` [VERIFIED: `xcrun --show-sdk-path`] | Full Xcode |
| Full Xcode app | Swift helper/DriverKit/system extension build | no | `xcodebuild` reports CLT-only; no `/Applications/Xcode*.app` found [VERIFIED: local commands] | Install/select matching Xcode |
| Swift compiler | CoreHID Swift helper | blocked | Swift 6.3.2, target arm64 macOS 26.0; `import CoreHID` fails due SDK/toolchain mismatch [VERIFIED: `swift --version`; `swift -e`] | Use matching Xcode/CLT or ObjC IOHIDUserDevice shim |
| Apple clang | ObjC IOHIDUserDevice shim | yes | Apple clang 21.0.0 [VERIFIED: `/usr/bin/clang --version`] | Full Xcode clang |
| ObjC IOKit syntax probe | IOHIDUserDevice shim | yes | `IOHIDUserDeviceCreateWithProperties` syntax-only compile passes [VERIFIED: local `/usr/bin/clang -fsyntax-only`] | none |
| Code signing identities | Entitled helper/package | no | `0 valid identities found` [VERIFIED: `security find-identity -v -p codesigning`] | Ad-hoc signing if entitlement permits; otherwise developer certificate/provisioning |
| `hidutil` | CLI enumeration | yes | local macOS tool [VERIFIED: `hidutil list -h`] | `ioreg` |
| `ioreg` | CLI enumeration/debug | yes | local macOS tool [VERIFIED: `ioreg -h`] | `hidutil` |
| `systemextensionsctl` | DriverKit fallback inspection | yes | path exists [VERIFIED: `command -v systemextensionsctl`] | System Settings UI plus logs |
| Java | Kotlin desktop tests | yes | OpenJDK 17.0.19 [VERIFIED: `java -version`] | configured JDK 17 |
| Gradle | Kotlin desktop tests | partially blocked | Homebrew Gradle present, but sandbox run failed on native services/socket lock [VERIFIED: local `gradle --version` attempt] | Existing project workaround uses `GRADLE_USER_HOME=/private/tmp/btgun-gradle`; may need unsandboxed run |
| Android live device/session | Final proof | required, not audited here | Existing phases have Android/gun path approved. [VERIFIED: `.planning/STATE.md`] | Replay fixtures for smoke only |

**Missing dependencies with no fallback:**
- Full Xcode or matching CLT/toolchain for Swift CoreHID and DriverKit planning/build. [VERIFIED: local commands]
- Valid signing/provisioning path for restricted virtual HID entitlement is unresolved. [VERIFIED: local signing identity audit; ASSUMED entitlement access]

**Missing dependencies with fallback:**
- Swift CoreHID compile is blocked; Objective-C `IOHIDUserDevice` syntax probe works as a user-space shim candidate, but it still needs runtime entitlement proof. [VERIFIED: local commands]
- Gradle task listing failed in sandbox; planner can use known `desktop-companion/build.gradle.kts` tasks and run Gradle with the established `/private/tmp` Gradle home or approval if needed. [VERIFIED: codebase; VERIFIED: local command]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotlin/JVM main-style tests invoked by Gradle `Test` task, Kotlin plugin 2.0.21, JDK 17 [VERIFIED: `desktop-companion/build.gradle.kts`; `java -version`] |
| Config file | `desktop-companion/build.gradle.kts`, `desktop-companion/settings.gradle.kts` [VERIFIED: codebase grep] |
| Quick run command | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*Macos*'` [ASSUMED; current build uses main-style tests rather than pure JUnit selectors] |
| Full suite command | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test` [VERIFIED: build file task exists; local sandbox run blocked] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| DESK-03 | macOS backend packs semantic state into descriptor-compatible input reports and publishes them to helper. | unit/integration | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*MacosHidReportPacker*'` [ASSUMED] | no - Wave 0 |
| DESK-03 | Helper creates OS-visible HID device enumerated by `hidutil`/`ioreg`. | live smoke | `gradle smokeDesktopBackendMacosCoreHid` plus `hidutil list --matching ...` [ASSUMED] | no - Wave 0 |
| DESK-03 | Live paired Android/gun stream moves OS-visible joystick axes/buttons. | manual/live proof | `MacosBackendRuntime` attached to live `ControlServer`, plus user/agent tester evidence. [VERIFIED: context D-05/D-06] | no - later wave |
| DESK-06 | Output report bytes map to `HapticCommand` with strength/duration/TTL constraints. | unit | `gradle test --tests '*MacosOutputReportMapper*'` [ASSUMED] | no - Wave 0 |
| DESK-06 | OS/HID set-report probe reaches helper and routes phone haptic command. | integration/live | `smokeDesktopBackendMacosCoreHid -Pbtgun.smoke.haptic=true` plus output probe [ASSUMED] | no - later wave |
| PACK-03 | Setup docs cover strategy, entitlements, commands, permission prompts, and fallback. | docs/static | `test -f docs/setup/macos-virtual-hid.md && rg -n 'CoreHID|HIDVirtualDevice|com.apple.developer.hid.virtual.device|HIDDriverKit|system extension|hidutil|ioreg' docs/setup/macos-virtual-hid.md` [ASSUMED] | no - later wave |

### Sampling Rate

- **Per task commit:** `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test --tests '*Macos*'` plus native helper syntax/build command for touched helper language. [ASSUMED]
- **Per wave merge:** `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test` and relevant `smokeDesktopBackendMacos*` command. [VERIFIED: existing Gradle pattern; ASSUMED new task names]
- **Phase gate:** Full desktop tests, CoreHID or fallback live smoke, CLI enumeration artifact, OS-output-to-phone-haptic proof, live Android/gun stream visual proof, and PACK-03 docs complete. [VERIFIED: context D-04 through D-13]

### Wave 0 Gaps

- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPackerTest.kt` - covers DESK-03 report bytes. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosOutputReportMapperTest.kt` - covers DESK-06 mapper constraints. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackendTest.kt` - covers lifecycle/capabilities without OS-visible claim until helper proof. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt` - covers callback preservation, stale behavior, diagnostics, and output haptic routing. [ASSUMED]
- [ ] Native helper minimal build/proof command - covers CoreHID/IOHIDUserDevice compile, signing, entitlement, launch, and enumeration. [ASSUMED]
- [ ] `docs/setup/macos-virtual-hid.md` - covers PACK-03. [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Preserve existing paired desktop/session authentication in Kotlin `ControlServer`; native helper receives no pairing/session material. [VERIFIED: context D-16; VERIFIED: codebase grep] |
| V3 Session Management | yes | Keep one active trusted session and existing haptic command TTL/ack behavior; helper is local process only. [VERIFIED: `.planning/STATE.md`; VERIFIED: `WindowsBackendRuntime.kt`] |
| V4 Access Control | yes | DriverKit fallback user-client access must be scoped by DriverKit entitlements and app bundle identifiers. [VERIFIED: local SDK `IOKitKeys.h` lines 121-150; CITED: https://developer.apple.com/documentation/driverkit/requesting-entitlements-for-driverkit-development] |
| V5 Input Validation | yes | Validate helper frames, report lengths, report IDs, reserved bytes, duration, TTL, and state ranges before publishing/haptic routing. [VERIFIED: Windows mapper pattern; ASSUMED for macOS mapper] |
| V6 Cryptography | yes | Do not add crypto in native helper; reuse existing authenticated LAN session path. [VERIFIED: context D-16; VERIFIED: `.planning/STATE.md`] |

### Known Threat Patterns for macOS Helper Boundary

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Local helper spoofing or stale helper binary | Spoofing/Tampering | Kotlin launches explicit helper path, checks version/capabilities handshake, and fails closed on mismatch. [ASSUMED] |
| Malformed output report triggers arbitrary haptic command | Tampering | `MacosOutputReportMapper` validates report ID/version/length/reserved bytes/duration/TTL before creating `HapticCommand`. [VERIFIED: Windows mapper pattern; ASSUMED] |
| Session secret leakage into helper logs | Information Disclosure | Helper protocol excludes session ids, keys, QR/manual codes, proof values, and raw LAN payloads. [VERIFIED: context D-16; ASSUMED] |
| Over-broad DriverKit user client | Elevation of Privilege | If fallback is used, require explicit user-client entitlement/access design and document it in PACK-03. [VERIFIED: local SDK `IOKitKeys.h`; ASSUMED fallback design] |
| Output/haptic replay after disconnect | Tampering | Kotlin keeps haptic TTL and trusted-session routing; helper output bytes alone cannot vibrate phone. [VERIFIED: existing haptic transport decisions in `.planning/STATE.md`] |

## Sources

### Primary (HIGH confidence)

- Apple CoreHID `HIDVirtualDevice` documentation - virtual HID device concept and API surface. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice]
- Apple CoreHID "Creating virtual devices" documentation - virtual device creation/testing context. [CITED: https://developer.apple.com/documentation/corehid/creatingvirtualdevices]
- Apple CoreHID entitlement page `com.apple.developer.hid.virtual.device` - virtual HID entitlement reference. [CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.hid.virtual.device]
- Apple HIDDriverKit documentation - HID DriverKit framework and system-extension packaging context. [CITED: https://developer.apple.com/documentation/hiddriverkit]
- Apple DriverKit entitlement request documentation - entitlement request process for DriverKit development. [CITED: https://developer.apple.com/documentation/driverkit/requesting-entitlements-for-driverkit-development]
- Apple System Extensions install documentation - app-bundled system extension activation model. [CITED: https://developer.apple.com/documentation/systemextensions/installing-system-extensions-and-drivers]
- Local SDK `CoreHID.swiftinterface` - `HIDVirtualDevice`, `dispatchInputReport`, delegate set/get report, macOS availability. [VERIFIED: local SDK `/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks/CoreHID.framework/.../arm64e-apple-macos.swiftinterface`]
- Local SDK `IOHIDUserDevice.h` - virtual device creation, entitlement requirement, report descriptor requirement, set-report callbacks, report dispatch. [VERIFIED: local SDK]
- Local SDK `HIDDriverKit/IOHIDDevice.iig` and `DriverKit/IOKitKeys.h` - DriverKit HID report methods and entitlement constants. [VERIFIED: local SDK]
- Project code files under `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/` and Phase 7 context. [VERIFIED: codebase grep]

### Secondary (MEDIUM confidence)

- USB-IF HID Usage Tables 1.7 - generic HID usage source for gamepad/joystick descriptor semantics. [CITED: https://www.usb.org/document-library/hid-usage-tables-17]
- Apple docs search snippets for CoreHID virtual game controller language and System Extensions approval behavior. [CITED: https://developer.apple.com/documentation/corehid; CITED: https://developer.apple.com/documentation/systemextensions/ossystemextensionrequest]

### Tertiary (LOW confidence)

- Assumed helper IPC and probe design choices in this research. [ASSUMED]
- Assumed availability of a macOS tester/game UI that can produce useful visual and rumble evidence. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM - CoreHID/IOHIDUserDevice/DriverKit APIs are verified from Apple docs and local SDK, but local Swift/signing/runtime proof is blocked. [VERIFIED: sources above]
- Architecture: HIGH - Existing Kotlin backend/runtime boundaries and user decisions are explicit in code and context. [VERIFIED: codebase grep; VERIFIED: context]
- Pitfalls: MEDIUM - Entitlement/output behavior risks are verified at API/setup level, but live CoreHID behavior still needs target proof. [VERIFIED: local SDK; ASSUMED runtime behavior]

**Research date:** 2026-06-10
**Valid until:** 2026-06-17 for Apple/macOS tooling findings; 2026-07-10 for repository architecture findings. [ASSUMED]
