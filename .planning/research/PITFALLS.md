# Pitfalls Research

**Domain:** Android-hosted Bluetooth gun controller with Wi-Fi desktop virtual gamepad drivers
**Researched:** 2026-06-06
**Confidence:** MEDIUM

## Critical Pitfalls

### Pitfall 1: Assuming the Gun Is a Standard Android Gamepad

**What goes wrong:**
The Android app waits for `KeyEvent`/`MotionEvent` input, but the gun only talks through a proprietary BLE/SPP protocol used by the vendor apps.

**Why it happens:**
The hardware looks like a controller, but the original ecosystem was app-specific and the archived apps request Bluetooth permissions.

**How to avoid:**
Start with a real-device diagnostic that checks both Android `InputDevice` visibility and raw Bluetooth services/sockets. Record results before building UI.

**Warning signs:**
No controller appears in `InputDevice.getDeviceIds()`, but BLE services or Classic SPP connections appear during scan/capture.

**Phase to address:**
Phase 1: hardware/protocol discovery.

---

### Pitfall 2: Static APK Analysis Without Hardware Verification

**What goes wrong:**
Implementation copies class names or UUID-like strings from APKs but fails on the real gun.

**Why it happens:**
Unity apps and native libraries can hide protocol logic in assets, C# assemblies, or native code; strings alone are incomplete evidence.

**How to avoid:**
Pair static analysis with dynamic `adb logcat`, btsnoop/HCI capture, BLE scanner output, and physical command tests.

**Warning signs:**
Code references `fff0/fff1` or SPP UUIDs but no captured bytes prove which characteristics carry input or rumble.

**Phase to address:**
Phase 1: reverse-engineering and capture fixtures.

---

### Pitfall 3: TCP-Only Aiming Stream

**What goes wrong:**
Aim feels sticky or jumps during packet loss because one lost TCP packet stalls later gyro samples.

**Why it happens:**
TCP/WebSocket is simpler, but head-of-line blocking is bad for realtime aim.

**How to avoid:**
Use UDP for high-rate input/gyro frames and a reliable TCP/WebSocket control channel for pairing, heartbeat, diagnostics, and rumble.

**Warning signs:**
Visualizer shows latency spikes after Wi-Fi loss even though average latency looks fine.

**Phase to address:**
Phase 2: LAN protocol.

---

### Pitfall 4: Desktop Driver Packaging Left Too Late

**What goes wrong:**
Core app works in prototype, but installable Windows/macOS virtual controller path is blocked by signing, entitlements, or system approval.

**Why it happens:**
Virtual HID work looks like an integration detail, but OS policy dominates feasibility.

**How to avoid:**
Spike Windows VHF and macOS CoreHID/DriverKit early with a fake input source and rumble loopback.

**Warning signs:**
Visualizer only works with a dev-only driver, unsigned extension, or manual security bypass.

**Phase to address:**
Phase 3: virtual HID spikes before polished companion app.

---

### Pitfall 5: Rumble Without TTL/Acknowledgement

**What goes wrong:**
The gun vibrates late, not at all, or keeps vibrating after disconnect/reconnect.

**Why it happens:**
Rumble is treated as a fire-and-forget output instead of a stateful bidirectional command.

**How to avoid:**
Include command id, duration, strength, TTL/play deadline, ack/fail, and disconnect cleanup in the control protocol.

**Warning signs:**
Rumble tests pass only when connection is perfect; stale commands play after reconnect.

**Phase to address:**
Phase 2/3: control channel and virtual HID output reports.

---

### Pitfall 6: Profiles Baked Into Android

**What goes wrong:**
Every desktop/game mapping change requires Android app changes and platform-specific logic leaks into the phone.

**Why it happens:**
Gyro math is captured on Android, so it is tempting to finish mapping there.

**How to avoid:**
Android sends normalized raw/semiprocessed motion; desktop profiles decide joystick axes, sensitivity, inversion, dead zones, and game-specific mapping.

**Warning signs:**
Android code contains Windows/macOS/game profile names or HID-axis decisions.

**Phase to address:**
Phase 4: profile mapper and visualizer.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| JSON for input frames | Fast debugging | Larger packets, parsing overhead, schema drift | Debug logs/control only |
| vJoy/ViGEm as only backend | Fast Windows prototype | Product risk due maintenance/signing/status | Early spike only |
| Hardcoded BLE UUIDs without docs | Quick connection | No maintainable protocol record | Never without capture evidence |
| One desktop implementation first with no shared spec | Faster first demo | Windows/macOS drift | Only if protocol spec is written first |
| Manual IP-only pairing | Very easy | Poor user experience and support pain | Debug fallback only |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Android Bluetooth permissions | Use only legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` on modern target | Request Android 12+ runtime Bluetooth permissions and keep legacy compatibility rules. |
| BLE scan/connect | Treat scan result as protocol proof | Verify services, characteristics, notifications, write type, MTU, and captured bytes. |
| Android sensors | Use wall-clock time and ignore drift | Use sensor timestamps, recenter, and visible calibration state. |
| LAN discovery | Depend only on mDNS | QR/code pairing must include host/port fallback. |
| Windows virtual HID | Prototype with existing virtual pad then call it done | Validate VHF signed-driver path and output reports. |
| macOS virtual HID | Assume entitlement availability | Verify CoreHID/DriverKit entitlement and install flow before roadmap commits to one path. |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| TCP input stream | Aim freezes or batches under Wi-Fi loss | UDP input, drop stale frames | Any packet loss/jitter |
| Oversized UDP packets | Random loss on some networks | Keep packets under 1200 bytes | Fragmentation path |
| Android background throttling | Latency grows when screen dims/app backgrounds | Foreground active session, wake/low-latency Wi-Fi handling | Longer sessions |
| Unmeasured desktop publish latency | Android looks fast but OS input lags | Timestamp every stage through visualizer | Driver integration |
| Sensor over-filtering | Smooth but sluggish aim | Profile-tunable filters with raw mode | First aiming tests |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Plaintext LAN input | Other local devices can inject controls | Pairing secret, authenticated encryption, replay window |
| Permanent pairing code | Unauthorized reuse | One-time code/QR secret, short expiry, paired desktop key |
| No packet replay protection | Old fire/rumble frames can replay | Sequence numbers and nonce/replay window |
| Verbose APK/protocol logs in release | Leaks device/session details | Redacted logs and explicit debug mode |
| Accept any desktop on LAN | Wrong computer can pair | User-visible host identity and pairing confirmation |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No visible connection state | User cannot tell if gun, LAN, or driver failed | Separate Gun, Desktop, Driver, Rumble status indicators |
| No recenter feedback | Aim feels broken after hold gesture | Show recenter event and active zero point |
| No latency display | Hard to debug "feels laggy" reports | Visualizer stage timing and packet loss |
| Pairing only by IP | Error-prone setup | QR/code pairing with manual fallback |
| Hidden rumble failures | User thinks motor is broken | Rumble test button with ack/fail display |

## "Looks Done But Isn't" Checklist

- [ ] **Gun input:** Works with one button but not all trigger/reload/button up/down states - verify full matrix.
- [ ] **Gyro aim:** Moves axes but drifts badly - verify recenter, drift, sample rate, and axis inversion.
- [ ] **LAN stream:** Works on local machine only - verify phone-to-desktop on real Wi-Fi.
- [ ] **Windows driver:** Works unsigned in dev mode only - verify signing/install path.
- [ ] **macOS virtual device:** Works in sample app only - verify system-wide visibility to visualizer.
- [ ] **Rumble:** Desktop receives output report but gun does not vibrate - verify command reaches Android and hardware motor.
- [ ] **Latency:** Average under 50 ms but spikes over 100 ms - verify p95/p99, not just mean.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Gun protocol wrong | HIGH | Capture HCI logs, decompile Unity assemblies, build packet fixture corpus, rewrite adapter only. |
| macOS entitlement blocked | HIGH | Switch to alternate virtual HID path, limit macOS v1 to supported OS/entitlement, or make visualizer-only macOS milestone explicit. |
| Windows VHF too slow | MEDIUM | Prototype vJoy/ViGEm to validate product behavior while VHF driver continues. |
| UDP packet design flawed | MEDIUM | Version packets, add compatibility parser, keep debug decoder. |
| Aim mapping feels bad | MEDIUM | Add raw mode, profile tuning, calibration UI, and replayable sensor traces. |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Assuming standard gamepad | Phase 1 | Diagnostic proves Android input path and Bluetooth services. |
| Static-only reverse engineering | Phase 1 | Captured real packet fixtures committed. |
| TCP-only aiming | Phase 2 | UDP packet stream passes loss/jitter visualizer test. |
| Late driver packaging | Phase 3 | Fake input source appears as virtual gamepad on Windows and macOS. |
| Rumble without TTL/ack | Phase 2/3 | Stale rumble command is dropped; ack/fail visible. |
| Profiles baked into Android | Phase 4 | Android has no desktop/game mapping constants. |

## Sources

- Android controller input, sensors, Bluetooth, Bluetooth permission, and NSD docs.
- Microsoft Virtual HID Framework and force-feedback docs.
- Apple CoreHID/HIDDriverKit/DriverKit docs.
- Nefarius ViGEm end-of-life docs.
- Local refs APK/XAPK archive metadata and string scans.

---
*Pitfalls research for: Bluetooth Gun Driver*
*Researched: 2026-06-06*
