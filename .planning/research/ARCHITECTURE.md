# Architecture Research

**Domain:** Android-hosted Bluetooth gun controller with Wi-Fi desktop virtual gamepad drivers
**Researched:** 2026-06-06
**Confidence:** MEDIUM

## Standard Architecture

### System Overview

```text
+-------------------------------------------------------------+
|                        Android Host                         |
+-------------------------------------------------------------+
|  +--------------+  +--------------+  +------------------+   |
|  | Gun BT       |  | Sensor       |  | Pairing/Session  |   |
|  | Adapter      |  | Capture      |  | UI               |   |
|  +------+-------+  +------+-------+  +--------+---------+   |
|         |                 |                   |             |
|  +------v-----------------v-------------------v---------+   |
|  |              Normalized Event Pipeline               |   |
|  +------+-------------------------------+---------------+   |
+---------+-------------------------------+-------------------+
|      Android Bluetooth HID gamepad output (macOS primary)  |
+-------------------------------------------------------------+
|                        Local LAN Transport                  |
+-------------------------------------------------------------+
|  UDP mapped input frames     TCP/WebSocket control/haptics  |
+-------------------------------------------------------------+
|                        Desktop Companion                    |
+-------------------------------------------------------------+
|  +--------------+  +--------------+  +------------------+   |
|  | Session      |  | Metadata /   |  | Visualizer       |   |
|  | Receiver     |  | Diagnostics  |  | Diagnostics      |   |
|  +------+-------+  +------+-------+  +--------+---------+   |
|         |                 |                   |             |
|  +------v-----------------v-------------------v---------+   |
|  |      Platform Backend Runtime Boundary               |   |
|  | Windows VHF/KMDF fallback; CoreHID/DriverKit scaffold |
|  +------------------------------------------------------+   |
+-------------------------------------------------------------+
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| Gun Bluetooth Adapter | Pair/connect to iPega gun and decode input frames | Android Bluetooth HID/InputDevice path first; BLE GATT/SPP adapter if proprietary protocol required |
| Sensor Capture | Sample motion aim data, choose best available provider, timestamp samples, handle recenter | Android SensorManager with rotation-vector, gyroscope, accelerometer, gravity, and monotonic timestamps |
| Normalized Event Pipeline | Merge gun controls and motion samples into platform-independent events | Kotlin module with explicit event schema and logs |
| Pairing/Session UI | Scan QR/code, establish authenticated local session | Android native UI, QR scanner/generator, session key storage |
| UDP Input Stream | Send high-rate Android-mapped input/motion samples | Versioned binary packets under 1200 bytes |
| Control Channel | Pairing, profile metadata, heartbeat, diagnostics, haptics | TCP/WebSocket with authenticated messages |
| Desktop Session Receiver | Accept paired Android client and validate packets | Native desktop service/app with no profile-editing authority |
| Android Profile/Calibration Mapper | Store, validate, edit, and apply profiles/calibration before HID or LAN output | Android-owned profile engine; desktop remains read-only for active profile metadata |
| Virtual HID Backend | Expose regular gamepad/joystick gun where a desktop backend is used | Windows VHF/KMDF fallback; Android Bluetooth HID is the macOS product route; CoreHID/DriverKit remain blocked/scaffold |
| Visualizer | Verify inputs, axes, latency, recenter, haptics | Desktop diagnostic app |

## Recommended Project Structure

```text
android-host/
  runtime/                   # BLE gun input, sensors, profiles, calibration, LAN, HID, haptics
  app/                       # Debug/diagnostic host app
  user-app/                  # Gamepad Extension user app

desktop/
  common/                    # Shared protocol specs, backend contracts, fixtures
  visualizer/                # First acceptance harness
  windows/
    service/                 # Receiver/backend user-mode service
    driver/                  # VHF/KMDF virtual HID driver
  macos/
    app/                     # Receiver/diagnostic host app
    hid/                     # CoreHID/DriverKit blocked scaffold only

docs/
  refs/                      # Archived vendor APK/XAPK files
  protocol/                  # Reverse-engineered iPega and LAN protocol notes
```

### Structure Rationale

- **android-host/runtime:** isolates unstable reverse-engineered hardware details, motion math, recenter, profiles, calibration, LAN, HID, and haptics behind shared Android runtime APIs.
- **android-host/app and android-host/user-app:** keep debug/diagnostic entrypoints separate from the user-facing Gamepad Extension app while sharing runtime behavior.
- **desktop/common:** keeps packet fixtures, backend contracts, and read-only profile metadata shared across Windows/macOS without forcing one runtime.
- **desktop/windows/driver:** driver code has separate build/signing constraints and should not mix with app code.
- **desktop/macos/hid:** CoreHID/DriverKit work is retained as blocked/fallback scaffold only; Android Bluetooth HID is the product macOS route.
- **docs/protocol:** reverse-engineering notes are product-critical and should be versioned.

## Architectural Patterns

### Pattern 1: Hardware Adapter -> Normalized Events

**What:** Convert iPega-specific frames into stable events like `TriggerDown`, `ReloadUp`, `Stick(x,y)`, `ButtonA`, `HapticAck`.
**When to use:** Always; do not leak BLE/SPP packet shapes into desktop code.
**Trade-offs:** Requires up-front schema discipline but prevents platform drift.

### Pattern 2: Split Realtime Input and Reliable Control

**What:** Send high-rate input/motion over UDP; send pairing, config, heartbeat, diagnostics, and haptics over reliable TCP/WebSocket.
**When to use:** v1 LAN transport.
**Trade-offs:** More protocol code than one WebSocket, but avoids aim stutter from TCP retransmit/head-of-line stalls.

### Pattern 3: Android-Owned Profiles

**What:** Android owns v1 profile storage, validation, editing, calibration, and application; desktop remains read-only for active Android profile metadata and mapped-stream diagnostics.
**When to use:** Phase 8 and later v1 profile work after the Phase 7 Android Bluetooth HID reroute.
**Trade-offs:** Android must keep game/platform constants out of profile defaults, while Windows VHF fallback consumes Android-mapped LAN input instead of running a desktop editor.

### Pattern 4: Virtual HID Backend Boundary

**What:** Keep platform virtual controller code behind a common interface: `connect`, `publishInput`, `setIdentity`, `onHaptic`, `disconnect`.
**When to use:** Desktop backend paths such as Windows VHF/KMDF; macOS CoreHID/DriverKit remains scaffold only while Android Bluetooth HID is the product route.
**Trade-offs:** Some features will not map perfectly across platforms; explicit capability flags are required.

## Data Flow

### Input Flow

```text
Physical trigger/stick/buttons
    -> Android Bluetooth adapter
        -> Normalized gun event
            -> Android profile/calibration mapper
                -> Android Bluetooth HID input report for macOS
                -> UDP InputFrame with seq + capture timestamp for LAN diagnostics/backend
                    -> Desktop receiver
                        -> Android-mapped semantic state
                            -> Visualizer and Windows VHF fallback

Android motion sample
    -> Sensor Capture
        -> Recenter/profile-neutral motion sample
            -> Android profile mapper
                -> HID/LAN mapped aim axes
```

### Haptic Flow

```text
Joystick visualizer/game haptic request on LAN or Windows VHF path
    -> Platform backend output report or visualizer haptic command
        -> Desktop backend onHaptic
            -> Control channel HapticCmd
                -> Android control receiver
                    -> Android phone Vibrator
                        -> HapticAck/HapticFail

macOS Bluetooth HID haptics
    -> unsupported/deferred on stable Android HID descriptor
        -> use LAN or Windows VHF phone-haptic route
```

### Pairing Flow

```text
Desktop starts session
    -> Advertise via NSD/mDNS if available
    -> Display QR/pairing code with session id, host, ports, public key, one-time secret
        -> Android scans QR/code
            -> Authenticated handshake
                -> Session keys
                    -> UDP input + reliable control channels open
```

### Key Data Flows

1. **Gun input decode:** Bluetooth frame/event -> normalized event -> packet fixture -> desktop visualizer.
2. **Aim mapping:** Sensor sample -> provider-specific normalization -> recenter transform -> profile mapping -> HID axis report.
3. **Haptic return:** Virtual HID output report -> desktop control message -> Android phone vibration command.
4. **Latency measurement:** Capture timestamp -> send timestamp -> receive timestamp -> HID publish timestamp -> visualizer metric.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1 gun / 1 desktop | Fixed session id, one receiver, one profile active | v1 target |
| Few local devices | Device identity table, multiple sessions, per-gun profiles | v2+ only |
| Public distribution | Signed installers, entitlement automation, support logs, crash reporting | Required before non-developer release |

### Scaling Priorities

1. **First bottleneck:** Reverse-engineered gun protocol uncertainty - fix with static/dynamic traces and packet fixtures.
2. **Second bottleneck:** Windows driver packaging and macOS HID compatibility - fix with explicit proof rows before broad app claims.
3. **Third bottleneck:** Motion drift/latency - fix with instrumentation visible in visualizer.

## Anti-Patterns

### Anti-Pattern 1: Raw BLE Frames Everywhere

**What people do:** Pass raw iPega frames directly to desktop apps.
**Why it's wrong:** Every platform becomes coupled to reverse-engineered hardware quirks.
**Do this instead:** Normalize on Android and record fixtures at both raw and normalized layers.

### Anti-Pattern 2: All Features Before Visualizer

**What people do:** Build full app UI, profiles, and installers before proving one input path.
**Why it's wrong:** Driver/protocol failures stay hidden until late.
**Do this instead:** Make visualizer the first end-to-end target.

### Anti-Pattern 3: Virtual Driver as an Afterthought

**What people do:** Build Android/transport first and assume desktop driver will be easy.
**Why it's wrong:** Signing, entitlements, output reports, and device identity can reshape the design.
**Do this instead:** Spike Windows and macOS virtual HID backends early.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| iPega gun Bluetooth | Android Bluetooth HID/InputDevice or BLE/SPP protocol adapter | Confirm with physical hardware and btsnoop/HCI logs. |
| Android sensors | SensorManager listener | Prefer fused rotation/game rotation; support gyro+accelerometer, gyro-only degraded mode, and accelerometer/gravity tilt fallback with monotonic sensor timestamps. |
| LAN discovery | Android NSD/mDNS plus QR fallback | Some networks block mDNS; QR host/port fallback is required. |
| Windows virtual HID | VHF/KMDF driver + user-mode service | Requires WDK, signing, installer, and output report handling for haptics. |
| macOS product input | Android Bluetooth HID gamepad | No-subscription v1 route; haptics remain unsupported/deferred on the stable descriptor. |
| macOS virtual HID scaffold | CoreHID or HIDDriverKit | Retained only as blocked/fallback evidence; entitlement, signing, and user approval are gating risks. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Gun adapter -> Normalized event pipeline | In-process typed events | Hardware-specific parsing stops here. |
| Android -> Desktop input | Authenticated UDP packets | Drop stale/out-of-order frames; no retransmit for input. |
| Android <-> Desktop control | Authenticated reliable channel | Pairing, heartbeat, profile metadata, diagnostics, haptics. |
| Android profile mapper -> HID/LAN output | Android Bluetooth HID and authenticated UDP mapped stream | Android owns profile application; desktop remains read-only and Windows VHF fallback consumes Android-mapped input. |
| Desktop mapped input -> Virtual HID backend | Common backend interface | Backends expose capability flags for haptics, axes, buttons without desktop profile editing authority. |

## Sources

- Android controller input, sensor, Bluetooth, permission, and NSD docs.
- Microsoft Virtual HID Framework and GameInput/force-feedback docs.
- Apple CoreHID, HIDDriverKit, DriverKit, and GameController docs.
- IETF QUIC/TLS references for transport/security tradeoffs.
- Local APK/XAPK metadata and strings under `docs/refs/`.

---
*Architecture research for: Bluetooth Gun Driver*
*Researched: 2026-06-06*
