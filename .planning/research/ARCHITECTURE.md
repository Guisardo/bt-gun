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
|                        Local LAN Transport                  |
+-------------------------------------------------------------+
|  UDP input/motion frames     TCP/WebSocket control/haptics  |
+-------------------------------------------------------------+
|                        Desktop Companion                    |
+-------------------------------------------------------------+
|  +--------------+  +--------------+  +------------------+   |
|  | Session      |  | Profile      |  | Visualizer       |   |
|  | Receiver     |  | Mapper       |  | Diagnostics      |   |
|  +------+-------+  +------+-------+  +--------+---------+   |
|         |                 |                   |             |
|  +------v-----------------v-------------------v---------+   |
|  |            Platform Virtual Gamepad Backend          |   |
|  |        Windows VHF/KMDF     macOS CoreHID/DriverKit  |   |
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
| UDP Input Stream | Send high-rate input/motion samples | Versioned binary packets under 1200 bytes |
| Control Channel | Pairing, profile metadata, heartbeat, diagnostics, haptics | TCP/WebSocket with authenticated messages |
| Desktop Session Receiver | Accept paired Android client and decrypt/validate packets | Native desktop service/app |
| Profile Mapper | Map normalized input and motion aim to virtual joystick axes/buttons | Desktop-owned profile engine |
| Virtual HID Backend | Expose regular gamepad/joystick gun to OS | Windows VHF/KMDF; macOS CoreHID or DriverKit |
| Visualizer | Verify inputs, axes, latency, recenter, haptics | Desktop diagnostic app |

## Recommended Project Structure

```text
android/
  app/                       # Android host app
  gun-protocol/              # Bluetooth adapter and decoded gun events
  sensors/                   # Motion sensor provider selection, fusion/fallback, recenter logic
  transport/                 # LAN pairing, UDP input, control channel

desktop/
  common/                    # Shared protocol spec, profile schema, fixtures
  visualizer/                # First acceptance harness
  windows/
    service/                 # Receiver/profile mapper user-mode service
    driver/                  # VHF/KMDF virtual HID driver
  macos/
    app/                     # Receiver/profile mapper host app
    hid/                     # CoreHID or DriverKit virtual HID backend

docs/
  refs/                      # Archived vendor APK/XAPK files
  protocol/                  # Reverse-engineered iPega and LAN protocol notes
```

### Structure Rationale

- **android/gun-protocol:** isolates unstable reverse-engineered hardware details from stable normalized events.
- **android/sensors:** keeps motion math and recenter separate from Bluetooth event parsing.
- **desktop/common:** keeps profile schema and packet fixtures shared across Windows/macOS without forcing one runtime.
- **desktop/windows/driver:** driver code has separate build/signing constraints and should not mix with app code.
- **desktop/macos/hid:** macOS virtual HID path may switch between CoreHID and DriverKit; keep it behind one backend boundary.
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

### Pattern 3: Desktop-Owned Profiles

**What:** Android sends normalized gun state and motion; desktop profile maps to HID axes/buttons.
**When to use:** User selected configurable profiles in v1.
**Trade-offs:** Desktop apps need profile UI/storage, but platform-specific game mapping stays on the platform that needs it.

### Pattern 4: Virtual HID Backend Boundary

**What:** Keep platform virtual controller code behind a common interface: `connect`, `publishInput`, `setIdentity`, `onHaptic`, `disconnect`.
**When to use:** Windows/macOS both v1 targets.
**Trade-offs:** Some features will not map perfectly across platforms; explicit capability flags are required.

## Data Flow

### Input Flow

```text
Physical trigger/stick/buttons
    -> Android Bluetooth adapter
        -> Normalized gun event
            -> UDP InputFrame with seq + capture timestamp
                -> Desktop receiver
                    -> Profile mapper
                        -> Virtual HID input report
                            -> Joystick visualizer/game

Android motion sample
    -> Sensor Capture
        -> Recenter/profile-neutral motion sample
            -> UDP InputFrame
                -> Desktop profile mapper
                    -> Aim axes
```

### Haptic Flow

```text
Joystick visualizer/game haptic request
    -> Platform virtual HID output report
        -> Desktop backend onHaptic
            -> Control channel HapticCmd
                -> Android control receiver
                    -> Android phone Vibrator
                        -> HapticAck/HapticFail
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
2. **Second bottleneck:** macOS/Windows driver packaging - fix with early spike before broad app UI.
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
| macOS virtual HID | CoreHID or HIDDriverKit | Entitlements and user approval are gating risks. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Gun adapter -> Normalized event pipeline | In-process typed events | Hardware-specific parsing stops here. |
| Android -> Desktop input | Authenticated UDP packets | Drop stale/out-of-order frames; no retransmit for input. |
| Android <-> Desktop control | Authenticated reliable channel | Pairing, heartbeat, profile metadata, diagnostics, haptics. |
| Profile mapper -> Virtual HID backend | Common backend interface | Backends expose capability flags for haptics, axes, buttons. |

## Sources

- Android controller input, sensor, Bluetooth, permission, and NSD docs.
- Microsoft Virtual HID Framework and GameInput/force-feedback docs.
- Apple CoreHID, HIDDriverKit, DriverKit, and GameController docs.
- IETF QUIC/TLS references for transport/security tradeoffs.
- Local APK/XAPK metadata and strings under `docs/refs/`.

---
*Architecture research for: Bluetooth Gun Driver*
*Researched: 2026-06-06*
