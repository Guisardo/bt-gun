$caveman ultra

<!-- GSD:project-start source:.planning/PROJECT.md -->

## Project

**Bluetooth Gun Driver**

Clean replacement host/driver stack for discontinued iPega AR gun joystick. Android app connects to physical gun over Bluetooth, reads gun controls + Android gyro, then streams normalized input over Wi-Fi/LAN to desktop companion drivers for macOS Apple Silicon and Windows 11 x64.

Desktop exposes stream as regular gamepad-style gun controller: joystick axes, buttons, trigger, reload, rumble. First validation target: simple joystick visualizer proving end-to-end input, configurable aim mapping, recenter, rumble round trip.

**Core Value:** Make discontinued iPega AR gun usable as normal wireless joystick gun on modern macOS/Windows with responsive gyro aim + bidirectional rumble.

### Constraints

- **Desktop support:** Windows 11 x64 + macOS Apple Silicon both v1 targets. Protocol + virtual-controller model must avoid platform-specific assumptions.
- **Transport:** Android-to-desktop v1 transport is Wi-Fi/LAN with QR or pairing code.
- **Latency:** Visualizer input path target <50 ms. Design timestamping + latency measurement early.
- **HID shape:** Desktop exposes regular gamepad-style gun controller, not custom HID gun report.
- **Aim mapping:** Desktop profiles own aim mapping. Android sends normalized gyro/raw aim data.
- **Calibration:** Hold reload 2 seconds to recenter gyro aim; do not break normal reload press/release.
- **Reverse engineering:** APK/XAPK + Bluetooth protocol reverse engineering allowed if Android controller APIs miss controls or rumble.
- **Hardware:** Physical iPega gun available now. Build real-device diagnostics early.

### Current Planning Refs

- `.planning/PROJECT.md` - product intent, constraints, context.
- `.planning/REQUIREMENTS.md` - v1 requirements + acceptance criteria.
- `.planning/ROADMAP.md` - phase order and success criteria.
- `.planning/STATE.md` - current focus: Phase 1, Hardware and Protocol Discovery.
- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md` - required Phase 1 context before planning/implementation.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:.planning/research/STACK.md -->

## Technology Stack

### Recommended Stack

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Android native app, Kotlin | Pin during implementation | Bluetooth gun host, sensor capture, pairing UI, transport client | Native Android APIs expose Bluetooth, controller input events, sensors, permissions, foreground service behavior, Wi-Fi/NSD. |
| Android Bluetooth Classic + BLE APIs | Platform APIs | Connect to iPega gun + reverse-engineered protocol | Reference apps request legacy Bluetooth permissions and include BLE evidence; keep Classic SPP fallback until hardware capture proves actual transport. |
| Android SensorManager | Platform APIs | Gyro, rotation vector, calibration, timestamped motion samples | Android sensor APIs provide gyroscope/rotation-vector streams with monotonic sensor timestamps. |
| Local LAN transport | UDP + TCP/WebSocket | Low-latency input stream + reliable control channel | UDP avoids TCP head-of-line stalls for aim/input; TCP/WebSocket fits pairing, config, diagnostics, heartbeat, rumble. |
| Windows KMDF + Virtual HID Framework | Windows 11 WDK | Product virtual gamepad/joystick HID | Microsoft VHF is supported HID source-driver framework. |
| macOS CoreHID HIDVirtualDevice or HIDDriverKit | macOS target dependent | Product virtual HID gamepad on Apple Silicon | Prefer CoreHID if target macOS/entitlements allow; HIDDriverKit fallback. |
| Simple desktop visualizer | Native desktop or cross-platform UI | First acceptance harness | Proves buttons, axes, gyro mapping, recenter, latency, rumble before game-specific work. |

### Supporting Tools

- `apktool`: decode APK/XAPK manifests/resources.
- `jadx`: decompile Java/Kotlin bytecode for Bluetooth bridge clues.
- AssetRipper or ILSpy: extract/decompile Unity C# assemblies.
- `adb` + btsnoop/HCI logs: dynamic protocol validation.
- nRF Connect or equivalent BLE scanner: inspect BLE services/characteristics.
- Wireshark or packet logger: LAN timing/loss diagnostics.

### Installation

Concrete install commands must be produced during implementation after repo stack exists. Do not install cross-platform frameworks before driver strategy is proven.

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

- Use Android `InputDevice`, `KeyEvent`, and `MotionEvent` first; reverse engineer Bluetooth only for missing controls/rumble.
- Use UDP for high-rate input/gyro frames; use reliable TCP/WebSocket for pairing/control/rumble.
- Use QR/code-paired session keys with authenticated packets. Plaintext LAN input is not acceptable.
- Use regular gamepad/joystick HID descriptor for v1. Avoid custom HID gun report.
- Avoid direct desktop-to-gun Bluetooth in v1.
- Avoid ViGEmBus/vJoy as mandatory product core; okay only for prototypes/spikes.
- Use monotonic sensor and elapsed realtime timestamps, not wall-clock timestamps.
- Keep Android foreground/active-session model for latency-sensitive sessions.

### Local Reference Apps

- `docs/refs/ARGun2021.apk` - strongest first-pass APK reference.
- `docs/refs/AR Cher_20200905_Apkpure.xapk` - strongest first-pass XAPK reference.
- `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` - strongest first-pass XAPK reference.
- `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` - secondary reference.
- `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` - 0-byte local file; reacquire only if strongest valid refs block protocol discovery.

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:none-yet -->

## Conventions

No project-specific code conventions yet. Follow existing patterns once source tree exists. Keep new docs short, factual, and agent-facing.

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:.planning/research/ARCHITECTURE.md -->

## Architecture

Architecture mapped in `.planning/research/ARCHITECTURE.md`.

### Standard Flow

```text
Physical gun + Android gyro
  -> Android gun Bluetooth adapter + SensorManager capture
  -> Normalized event pipeline
  -> UDP input/gyro frames + TCP/WebSocket control/rumble
  -> Desktop receiver + profile mapper
  -> Platform virtual gamepad backend
  -> Visualizer/game
```

### Boundaries

- Gun adapter owns iPega-specific Bluetooth/HID/BLE/SPP details.
- Normalized event pipeline stops raw protocol leakage into desktop code.
- Desktop profile mapper owns platform/game mapping.
- Virtual HID backend boundary covers Windows VHF/KMDF and macOS CoreHID/HIDDriverKit.
- Visualizer is first end-to-end acceptance harness.

### Phase 1 Evidence Rule

Protocol/control/rumble findings count as verified only when static decompile clue + hardware capture + normalized fixture all exist.

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:no-project-skills -->

## Project Skills

No project skills found. Add skills under `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with `SKILL.md`.

<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before file edits, start through GSD unless user explicitly bypasses.

- `/gsd-quick`: small fixes, doc updates, ad-hoc tasks.
- `/gsd-debug`: investigation and bug fixing.
- `/gsd-execute-phase`: planned phase work.

For current project work, read these first:

- `.planning/STATE.md`
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md`

Current focus: Phase 1 Hardware and Protocol Discovery. No production source tree exists yet; only `.planning/` docs and `docs/refs/` APK/XAPK archives.

<!-- GSD:workflow-end -->

<!-- GSD:profile-start source:not-configured -->

## Developer Profile

Profile not configured. Run `/gsd-profile-user` when needed. Do not edit generated profile block manually once configured.

<!-- GSD:profile-end -->
