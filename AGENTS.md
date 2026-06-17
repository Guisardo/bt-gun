$caveman ultra

<!-- GSD:project-start source:.planning/PROJECT.md -->

## Project

**Bluetooth Gun Driver**

New host/driver stack for dead iPega AR gun joystick. Android app talk to gun over Bluetooth, read gun controls + Android gyro, then throw normalized input over Wi-Fi/LAN to desktop driver for macOS Apple Silicon and Windows 11 x64.

Desktop show stream like normal gamepad gun: joystick axes, buttons, trigger, reload, rumble. First test: dumb joystick visualizer prove input work end-to-end, aim mapping, recenter, rumble go-and-come-back.

**Core Value:** Make dead iPega AR gun work like normal wireless joystick gun on new macOS/Windows. Fast gyro aim + rumble both ways.

### Constraints

- **Desktop support:** Windows 11 x64 + macOS Apple Silicon both v1. Protocol + virtual-controller must not assume one platform.
- **Transport:** Android-to-desktop v1 go over Wi-Fi/LAN. QR or pair code.
- **Latency:** Visualizer input path want <50 ms. Do timestamp + latency measure early.
- **HID shape:** Desktop show normal gamepad gun, not custom HID gun report.
- **Aim mapping:** Android profiles own aim mapping/calibration. Desktop display active profile metadata and consume mapped stream.
- **Calibration:** Hold reload 2 second to recenter gyro aim. No break normal reload press/release.
- **Reverse engineering:** APK/XAPK + Bluetooth protocol reverse engineer OK if Android controller APIs miss controls or rumble.
- **Hardware:** Real iPega gun here now. Build real-device diagnostics early.

### Current Planning Refs

- `.planning/PROJECT.md` - product intent, constraints, context.
- `.planning/REQUIREMENTS.md` - v1 requirements + acceptance criteria.
- `.planning/ROADMAP.md` - phase order and success criteria.
- `.planning/STATE.md` - current focus: Phase 11, Gamepad Extension Android User App.
- `.planning/phases/11-gamepad-extension-android-user-app/11-CONTEXT.md` - required Phase 11 context before planning/implementation.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:.planning/research/STACK.md -->

## Technology Stack

### Recommended Stack

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Android native app, Kotlin | Pin during implementation | Bluetooth gun host, sensor capture, pairing UI, transport client | Native Android APIs give Bluetooth, controller input, sensors, permissions, foreground service, Wi-Fi/NSD. |
| Android Bluetooth Classic + BLE APIs | Platform APIs | Connect to iPega gun + reverse-engineered protocol | Ref apps ask legacy Bluetooth permissions + show BLE clues. Keep Classic SPP fallback til hardware capture prove real transport. |
| Android SensorManager | Platform APIs | Gyro, rotation vector, calibration, timestamped motion samples | Android sensor APIs give gyro/rotation-vector stream with monotonic sensor timestamp. |
| Local LAN transport | UDP + TCP/WebSocket | Low-latency input stream + reliable control channel | UDP dodge TCP head-of-line stall for aim/input. TCP/WebSocket fit pairing, config, diagnostics, heartbeat, rumble. |
| Windows KMDF + Virtual HID Framework | Windows 11 WDK | Product virtual gamepad/joystick HID | Microsoft VHF is supported HID source-driver framework. |
| Android Bluetooth HID gamepad | Android BluetoothHidDevice | Primary no-subscription macOS gamepad path | Phone advertises normal HID gamepad; CoreHID/DriverKit stay fallback/blocker evidence only. |
| Simple desktop visualizer | Native desktop or cross-platform UI | First acceptance harness | Prove buttons, axes, gyro mapping, recenter, latency, rumble before game-specific work. |

### Supporting Tools

- `apktool`: decode APK/XAPK manifests/resources.
- `jadx`: decompile Java/Kotlin bytecode for Bluetooth bridge clues.
- AssetRipper or ILSpy: extract/decompile Unity C# assemblies.
- `adb` + btsnoop/HCI logs: dynamic protocol validation.
- nRF Connect or equivalent BLE scanner: inspect BLE services/characteristics.
- Wireshark or packet logger: LAN timing/loss diagnostics.

### Installation

Real install commands come during implementation after repo stack exist. No install cross-platform frameworks before driver strategy proven.

Expected toolchain setup by phase:

```bash
# Android reverse engineering and host app
apktool
jadx
adb
Android Studio / Android SDK

# Windows virtual HID
Windows Driver Kit
Visual Studio build tools

# macOS virtual HID
Xcode
Apple Developer account with required entitlements
```

### Use / Avoid

- Use Android `InputDevice`, `KeyEvent`, and `MotionEvent` first. Reverse engineer Bluetooth only for missing controls/rumble.
- Use UDP for fast input/gyro frames. Use reliable TCP/WebSocket for pairing/control/rumble.
- Use QR/code-paired session keys with authed packets. Plaintext LAN input no good.
- Use normal gamepad/joystick HID descriptor for v1. No custom HID gun report.
- No direct desktop-to-gun Bluetooth in v1.
- No ViGEmBus/vJoy as must-have product core. OK only for prototypes/spikes.
- Use monotonic sensor and elapsed realtime timestamps, not wall-clock timestamps.
- Keep Android foreground/active-session model for latency-touchy sessions.

### Local Reference Apps

- `docs/refs/ARGun2021.apk` - strongest first-pass APK reference.
- `docs/refs/AR Cher_20200905_Apkpure.xapk` - strongest first-pass XAPK reference.
- `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` - strongest first-pass XAPK reference.
- `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` - secondary reference.
- `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` - 0-byte local file. Reacquire only if strongest valid refs block protocol discovery.

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:none-yet -->

## Conventions

Follow existing Kotlin/JVM, Android Views, C/KMDF, Swift, PowerShell, and fixture patterns. Keep new docs short, factual, agent-facing.

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:.planning/research/ARCHITECTURE.md -->

## Architecture

Architecture mapped in `.planning/research/ARCHITECTURE.md`.

### Standard Flow

```text
Physical gun + Android gyro
  -> Android gun Bluetooth adapter + SensorManager capture
  -> Normalized event pipeline
  -> Android profile/calibration mapper
  -> Android Bluetooth HID gamepad for macOS
  -> LAN UDP/WSS diagnostics + Windows VHF fallback
  -> Visualizer/game
```

### Boundaries

- Gun adapter own iPega-specific Bluetooth/HID/BLE/GATT details.
- Normalized event pipeline stop raw protocol leak into desktop code.
- Android profiles own aim/button mapping and calibration.
- Desktop profile view is read-only metadata plus diagnostics.
- Virtual HID/backend boundary cover Android Bluetooth HID, Windows VHF/KMDF, and blocked macOS CoreHID/DriverKit fallback docs.
- Visualizer is first end-to-end acceptance harness.

### Phase 1 Evidence Rule

Protocol/control/rumble finding count verified only when static decompile clue + hardware capture + normalized fixture all exist.

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:no-project-skills -->

## Project Skills

No project skills found. Custom Codex agents live under `.codex/agents/*.md`; use them for BT Gun reviews before broad implementation work.

<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before file edits, start through GSD unless user say bypass.

- `/gsd-quick`: small fixes, doc updates, ad-hoc tasks.
- `/gsd-debug`: investigation and bug fixing.
- `/gsd-execute-phase`: planned phase work.

For current project work, read these first:

- `.planning/STATE.md`
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/phases/11-gamepad-extension-android-user-app/11-CONTEXT.md`

Current focus: Phase 11 Gamepad Extension Android User App. Source tree exists under `android-host/`, `desktop-companion/`, `windows/`, `native/`, `fixtures/`, `tools/`, and `docs/`.

<!-- GSD:workflow-end -->

<!-- GSD:profile-start source:not-configured -->

## Developer Profile

Profile not set. Run `/gsd-profile-user` when need. No edit generated profile block by hand once set.

<!-- GSD:profile-end -->
