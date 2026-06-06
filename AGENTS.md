<!-- GSD:project-start source:PROJECT.md -->

## Project

**Bluetooth Gun Driver**

Bluetooth Gun Driver is a clean replacement host/driver stack for a discontinued iPega AR gun joystick. An Android app connects to the physical gun over Bluetooth, reads gun controls plus the Android device gyroscope, and forwards a normalized input stream over a wireless LAN link to desktop companion drivers for macOS on Apple Silicon and Windows 11 x64.

The desktop side exposes the stream as a regular gamepad-style gun controller so games and tools can see joystick axes, buttons, trigger, reload, and rumble without depending on the original discontinued Android apps. The first validation target is a simple joystick visualizer that proves end-to-end input, configurable aim mapping, recentering, and rumble round-trip behavior.

**Core Value:** Make the discontinued iPega AR gun usable as a normal wireless joystick gun on modern macOS and Windows with responsive gyro aiming and bidirectional rumble.

### Constraints

- **Desktop support**: Windows 11 x64 and macOS Apple Silicon are both v1 targets - the protocol and virtual-controller model must avoid platform-specific assumptions.
- **Transport**: Android-to-desktop v1 transport is Wi-Fi/LAN with QR or pairing code - this keeps pairing/debugging simpler while preserving wireless use.
- **Latency**: End-to-end target is under 50 ms for visualizer input path - design must support timestamping and latency measurement early.
- **HID shape**: Desktop exposes a regular gamepad-style gun controller - avoids custom HID compatibility risk in v1.
- **Aim mapping**: Aim mapping is configurable by desktop-side profiles - Android should send normalized gyro/raw aim data without baking in one desktop mapping.
- **Calibration**: Holding reload for two seconds recenters gyro aim - this gesture must not break normal reload behavior.
- **Reverse engineering**: Reference APK/XAPK and Bluetooth protocol reverse-engineering is allowed - useful if standard Android controller APIs do not reveal full input or rumble behavior.
- **Hardware availability**: Physical iPega gun is available for testing now - implementation should include real-device diagnostic tooling early.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Android native app, Kotlin | Pin during implementation | Bluetooth gun host, sensor capture, pairing UI, transport client | Native Android APIs expose Bluetooth, controller input events, sensors, permissions, foreground service behavior, and Wi-Fi/NSD without framework indirection. |
| Android Bluetooth Classic + BLE APIs | Platform APIs | Connect to the iPega gun and reverse-engineered protocol | Reference apps request legacy Bluetooth permissions and include BLE evidence; Classic SPP fallback should stay available until hardware capture confirms actual transport. |
| Android SensorManager | Platform APIs | Gyro, rotation vector, calibration, timestamped motion samples | Official Android sensor APIs provide gyroscope and rotation-vector streams with monotonic sensor timestamps. |
| Local LAN transport | UDP + TCP/WebSocket | Low-latency input stream plus reliable control channel | UDP avoids TCP head-of-line stalls for high-rate aim/input samples; TCP/WebSocket is simpler for pairing, config, diagnostics, heartbeat, and rumble commands. |
| Windows KMDF + Virtual HID Framework | Windows 11 WDK | Product-grade virtual gamepad/joystick HID device | Microsoft VHF is the supported framework for HID source drivers and lets a driver enumerate virtual HID children. |
| macOS CoreHID HIDVirtualDevice or HIDDriverKit | macOS target dependent | Product-grade virtual HID gamepad on Apple Silicon | CoreHID virtual device is preferred if target macOS/entitlements allow it; HIDDriverKit is the fallback for a system extension based virtual HID path. |
| Simple desktop visualizer | Native desktop or cross-platform UI | First acceptance harness | A visualizer proves buttons, axes, gyro mapping, recenter, latency, and rumble before game-specific integration. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| QR code library | Pin during implementation | Pairing-code/QR creation and scanning | Pairing flow should encode host, ports, session id, public key, and one-time secret. |
| Authenticated encryption library | Platform-native or vetted dependency | Encrypt local UDP/TCP traffic | Required because QR/code pairing gives a session secret; packets should be authenticated and replay-resistant. |
| apktool | Installed locally | Decode Android manifests/resources | Use for reference APK/XAPK static analysis. |
| jadx | Installed locally | Decompile Java/Kotlin bytecode | Use for Bluetooth bridge classes and protocol clues. |
| AssetRipper or ILSpy | Pin during reverse-engineering phase | Extract/decompile Unity C# assemblies | Needed because reference apps are Unity/XAPK and protocol logic may live in Unity assets/assemblies. |
| adb + btsnoop/HCI logs | Android SDK/device tools | Dynamic protocol validation | Use with physical iPega gun to confirm BLE/SPP services, packet bytes, and rumble behavior. |
| nRF Connect or equivalent BLE scanner | Current Android app | BLE service/characteristic inspection | Use before coding custom GATT handling. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio + Android SDK | Build Android host app | Target current Android SDK, request Android 12+ Bluetooth runtime permissions, keep old-device compatibility if needed. |
| Windows Driver Kit | Build/sign VHF driver | Driver signing, HVCI/Secure Boot behavior, and installer flow are first-class product risks. |
| Xcode | Build macOS virtual HID helper/driver | CoreHID/DriverKit entitlement availability must be validated early. |
| Wireshark or packet logger | Transport diagnostics | Capture UDP/TCP timing and packet loss during latency tests. |
| Joystick visualizer | Acceptance test | Can start as an internal desktop tool, then become the first user-facing diagnostic. |

## Installation

# Android reverse engineering and host app

# Windows virtual HID

# macOS virtual HID

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Windows VHF custom HID | ViGEmBus | Use only as an optional/legacy prototype backend for Xbox 360/DS4 emulation. It is retired/archived and should not be the core product dependency. |
| Windows VHF custom HID | vJoy | Useful for prototype joystick visualization, but maintenance/signing risk makes it weak as product core. |
| macOS CoreHID HIDVirtualDevice | HIDDriverKit dext | Use DriverKit if CoreHID virtual HID is unavailable for target macOS or entitlement path. |
| Native Android | React Native/Flutter | Avoid for first device/protocol work; add UI framework only if native proof succeeds and UI complexity justifies it. |
| UDP input stream | All-input WebSocket/TCP | Accept only for early prototype if LAN is clean; TCP retransmit stalls can produce aim stutter. |
| Custom binary packets | JSON packets | JSON is acceptable for debug/control but not high-rate input frames under latency budget. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Custom HID gun report for v1 | Compatibility risk with visualizers and games | Regular gamepad/joystick HID descriptor |
| Direct desktop-to-gun Bluetooth in v1 | Original product relies on Android phone + gyro; desktop Bluetooth adds platform-specific complexity | Android host app as the gun bridge |
| ViGEmBus as mandatory core | Retired/archived project risk | Custom VHF HID driver as product path |
| vJoy as mandatory core | Maintenance/signing uncertainty | VHF product path; vJoy only for prototypes |
| Background-only Android service model | Modern Android can throttle or restrict background behavior, hurting latency | Foreground app/session with explicit active connection UI |
| Wall-clock timestamps | Wall clock can jump and is not aligned with sensor capture time | Monotonic sensor and elapsed realtime timestamps |
| Plaintext local UDP | LAN is not a trust boundary | QR/code-paired session keys with authenticated packets |

## Stack Patterns by Variant

- Read `InputDevice`, `KeyEvent`, and `MotionEvent` first.
- Use Bluetooth protocol reverse engineering only for missing controls or rumble.
- Build a narrow Android protocol adapter around discovered service/characteristics and decoded frames.
- Keep normalized internal events independent of BLE packet details.
- Use CoreHID for a user-space virtual gamepad path.
- Keep DriverKit as fallback for older target or entitlement constraints.
- Use DriverKit/HIDDriverKit packaged in a system extension app.
- Plan for user approval, entitlement, signing, and installer work early.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Android target SDK 31+ | Bluetooth runtime permissions | Must request `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and possibly `BLUETOOTH_ADVERTISE` at runtime. |
| Reference APKs target 22-29 | Legacy Bluetooth permissions | Useful as protocol evidence, but not a modern permission model. |
| Windows 11 x64 | WDK VHF/KMDF | Product driver requires signing and installer plan. |
| macOS Apple Silicon | CoreHID or DriverKit | Exact path depends on macOS version and Apple entitlement approval. |

## Sources

- Android controller input docs - `KeyEvent`, `MotionEvent`, and controller `InputDevice` path.
- Android motion sensor docs - gyroscope, rotation vector, and sensor framework.
- Android Bluetooth docs - platform Bluetooth stack and permissions.
- Android NSD docs - local network service discovery on Wi-Fi/LAN.
- Microsoft Virtual HID Framework docs - Windows HID source driver path.
- Microsoft GameInput/force-feedback docs - modern Windows input and haptics context.
- Apple HIDDriverKit and DriverKit docs - virtual/system extension HID driver path.
- Apple CoreHID `HIDVirtualDevice` docs - possible macOS 15+ user-space virtual HID path.
- Nefarius ViGEm end-of-life statement - retired/archived risk.
- Local refs: `ARGun2021.apk`, `ARGun Library_1.0.1_apkcombo.com.apk`, `AR Cher_20200905_Apkpure.xapk`, `WorldsAR_14.0_apkcombo.com.xapk`.

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
