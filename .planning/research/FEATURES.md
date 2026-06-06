# Feature Research

**Domain:** Android-hosted Bluetooth gun controller with Wi-Fi desktop virtual gamepad drivers
**Researched:** 2026-06-06
**Confidence:** MEDIUM

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these means the product cannot validate its core value.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Gun Bluetooth connection | Physical iPega gun must connect to Android before anything else works | HIGH | Must discover whether standard Android controller APIs are enough or proprietary BLE/SPP decoding is required. |
| Gun input decoding | Trigger, reload, joystick, X/Y/A/B are the product's base controls | HIGH | Normalize hardware-specific frames into stable internal events. |
| Android gyro capture | Original experience depends on phone gyro aiming | MEDIUM | Use SensorManager; support recenter and drift handling. |
| LAN pairing | User needs a reliable way to connect phone and desktop without typing IPs | MEDIUM | QR primary, code fallback, local-only session. |
| Low-latency input stream | Aiming must feel responsive | HIGH | Use timestamped binary UDP frames for high-rate samples. |
| Reliable control/rumble channel | Rumble is required in v1 and needs acknowledgements/failure states | MEDIUM | Use TCP/WebSocket control channel separate from UDP input. |
| Desktop profile mapping | User selected configurable aim mapping in v1 | MEDIUM | Profiles live on desktop and map normalized input to HID axes/buttons. |
| Windows virtual gamepad | Windows 11 x64 is a v1 target | HIGH | Product path should be VHF/KMDF, with prototype backend allowed if useful. |
| macOS virtual gamepad | macOS Apple Silicon is a v1 target | HIGH | CoreHID/DriverKit entitlement and packaging must be validated early. |
| Joystick visualizer | First validation target must show full end-to-end behavior | MEDIUM | Should show buttons, sticks, mapped aim axes, latency, recenter state, and rumble test. |
| Latency instrumentation | v1 target is under 50 ms | MEDIUM | Need capture/send/receive/apply timestamps and visible metrics. |
| Reverse-engineering workflow | Reference apps are the only known working software | HIGH | Use static and dynamic analysis to confirm protocol, not guesses. |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Same normalized protocol for macOS and Windows | Keeps Android host and desktop profiles consistent across platforms | MEDIUM | Requires protocol spec and test fixtures early. |
| Bidirectional rumble with TTL | Makes the gun feel alive and avoids stuck vibration | MEDIUM | Include command ids, duration, strength, pattern, ack/failure. |
| Profile-based gyro mapping | Supports multiple games/tools without Android rebuilds | MEDIUM | Desktop owns mapping; Android sends raw/normalized motion. |
| Protocol capture tooling | Speeds up reverse engineering and future hardware support | MEDIUM | Add logs, packet dumps, and replayable fixtures. |
| Optional transport abstraction | Keeps Bluetooth/BLE or Wi-Fi Direct possible later | MEDIUM | Do not implement multiple transports until LAN path works. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Direct desktop Bluetooth to gun | Removes Android phone from chain | Loses phone gyro role and multiplies platform-specific Bluetooth work | Android host app for v1 |
| Game-specific profiles first | Users ultimately want real games | Premature before virtual HID and visualizer prove base behavior | Visualizer first, game profiles later |
| Cloud or internet relay | Seems convenient across networks | Latency, security, and reliability risk | Local LAN only |
| Custom gun HID descriptor | Feels semantically accurate | Higher compatibility risk | Regular gamepad/joystick HID shape |
| All traffic over TCP/WebSocket | Easier implementation | Packet loss can stall aim updates | UDP input plus reliable control channel |
| Silent background Android service | Less visible UI | Android background restrictions can hurt connection and latency | Foreground active-session UI |

## Feature Dependencies

```text
Reference APK/protocol investigation
    -> Gun Bluetooth adapter
        -> Normalized Android input events
            -> LAN input stream
                -> Desktop profile mapper
                    -> Windows virtual HID
                    -> macOS virtual HID
                    -> Joystick visualizer

LAN pairing/security
    -> UDP input stream
    -> TCP/WebSocket control + rumble

Sensor capture + recenter
    -> Aim profiles
    -> Latency instrumentation
```

### Dependency Notes

- **Protocol investigation requires physical device tests:** Static APK analysis gives names, permissions, and probable BLE/SPP paths; real packet capture confirms actual behavior.
- **Normalized events must precede desktop drivers:** Desktop implementations should not depend on raw iPega BLE frame shapes.
- **Pairing/security must precede UDP input:** Authenticated session keys avoid unauthenticated local input injection.
- **Visualizer must precede real game support:** It isolates driver/protocol correctness from game-specific quirks.
- **Rumble requires bidirectional control channel:** UDP input alone is not enough for acknowledged rumble commands.

## MVP Definition

### Launch With (v1)

- [ ] Android connects to physical iPega gun.
- [ ] Android decodes trigger, reload, joystick, X/Y/A/B, and rumble capability.
- [ ] Android captures gyroscope/rotation data with timestamps.
- [ ] Reload-hold for two seconds recenters aim.
- [ ] Desktop and Android pair by QR or code over LAN.
- [ ] Android streams normalized input/gyro frames under a 50 ms visualizer target.
- [ ] Desktop profiles map aim to configurable joystick axes.
- [ ] Windows exposes a virtual gamepad/joystick gun.
- [ ] macOS exposes a virtual gamepad/joystick gun.
- [ ] Desktop sends rumble command back to Android and physical gun vibrates.
- [ ] Joystick visualizer proves buttons, axes, recenter, latency, and rumble.

### Add After Validation (v1.x)

- [ ] Game-specific profile presets - once visualizer path is stable.
- [ ] Packet replay/regression suite - once real packet captures exist.
- [ ] Better calibration UI - once raw drift/axis issues are measured.
- [ ] Optional prototype backends - if vJoy/ViGEm/CoreHID choices help testing without becoming product core.

### Future Consideration (v2+)

- [ ] Direct desktop-to-gun Bluetooth mode - defer until Android-hosted product works.
- [ ] Wi-Fi Direct/BLE Android-to-desktop transport - defer until LAN protocol is stable.
- [ ] Multi-gun support - defer until single-device identity and timing are stable.
- [ ] Game-specific overlays/macros - defer until base input semantics are reliable.

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Reference APK/protocol investigation | HIGH | HIGH | P1 |
| Android gun Bluetooth connection | HIGH | HIGH | P1 |
| Android gyro capture/recenter | HIGH | MEDIUM | P1 |
| LAN pairing | HIGH | MEDIUM | P1 |
| UDP input stream + control channel | HIGH | HIGH | P1 |
| Desktop profile mapping | HIGH | MEDIUM | P1 |
| Windows virtual HID | HIGH | HIGH | P1 |
| macOS virtual HID | HIGH | HIGH | P1 |
| Rumble round-trip | HIGH | MEDIUM | P1 |
| Joystick visualizer | HIGH | MEDIUM | P1 |
| Game presets | MEDIUM | MEDIUM | P2 |
| Extra wireless transports | MEDIUM | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

This product is not primarily competing with active commercial tools; it replaces discontinued vendor apps and restores hardware utility.

| Feature | Reference vendor apps | General virtual controller tools | Our Approach |
|---------|-----------------------|----------------------------------|--------------|
| Gun Bluetooth pairing | Works with original apps only | Usually unrelated to this hardware | Android host owns the original gun protocol. |
| Phone gyro aim | Original AR design uses Android motion | Desktop virtual pads do not solve phone gyro | Android captures gyro and desktop maps profiles. |
| Desktop virtual joystick | Not supported by vendor apps | vJoy/ViGEm can help but have maintenance limits | Build product path around platform virtual HID APIs. |
| Rumble | Hardware motor exists; app behavior must be confirmed | Varies by backend/API | Make rumble round-trip a v1 acceptance check. |

## Sources

- Android controller input docs.
- Android sensor/motion docs.
- Android Bluetooth and Bluetooth permission docs.
- Android Network Service Discovery docs.
- Microsoft Virtual HID Framework docs.
- Microsoft GameInput/force-feedback docs.
- Apple CoreHID/HIDDriverKit/DriverKit docs.
- Nefarius ViGEm end-of-life docs.
- Local APK/XAPK archive metadata and string scans.

---
*Feature research for: Bluetooth Gun Driver*
*Researched: 2026-06-06*
