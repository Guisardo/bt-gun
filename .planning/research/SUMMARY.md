# Project Research Summary

**Project:** Bluetooth Gun Driver
**Domain:** Android-hosted Bluetooth gun controller with Wi-Fi desktop virtual gamepad drivers
**Researched:** 2026-06-06
**Confidence:** MEDIUM

## Executive Summary

Bluetooth Gun Driver should be built as a three-part system: an Android gun server app, a local LAN transport, and native desktop virtual gamepad backends. Android owns the physical iPega gun and phone motion sensors because that matches the discontinued product's original model. Desktop owns profile mapping and virtual HID exposure because Windows/macOS behavior and game expectations belong on the host platform.

The recommended v1 proof is not a real game. It is a simple joystick visualizer that can show every control, mapped aim axes, recenter behavior, packet timing, and phone haptic round-trip. This avoids mixing driver/protocol risk with game-specific quirks.

The main technical risks are reverse-engineering the iPega Bluetooth protocol, shipping product-grade virtual HID paths on both desktop OSes, and keeping aim latency under 50 ms. Research suggests starting with hardware/protocol discovery, then a timestamped UDP input protocol plus reliable control/haptic channel, then desktop HID backend spikes, then the visualizer/profile layer.

## Key Findings

### Recommended Stack

Use native Android/Kotlin for the host app, Android Bluetooth and SensorManager APIs for gun/motion capture, UDP for high-rate input frames, TCP/WebSocket for pairing/control/haptics, Windows VHF/KMDF for product-grade virtual HID, and macOS CoreHID or HIDDriverKit depending on OS/entitlement feasibility.

**Core technologies:**
- Android Bluetooth APIs: physical iPega connection and protocol bridge.
- Android SensorManager: motion capture with monotonic timestamps, preferring fused rotation/game rotation, using gyro+accelerometer where useful, and falling back to accelerometer/gravity tilt when no gyroscope exists.
- UDP input protocol: low-latency aim/input stream.
- TCP/WebSocket control channel: pairing, heartbeat, diagnostics, haptics.
- Windows Virtual HID Framework: product-grade Windows virtual joystick/gamepad.
- macOS CoreHID or HIDDriverKit: product-grade macOS virtual joystick/gamepad path.
- apktool/jadx/AssetRipper/adb/btsnoop: reverse-engineering and validation toolchain.

### Expected Features

**Must have (table stakes):**
- Android connects to the physical iPega gun.
- Android decodes trigger, reload, joystick, and X/Y/A/B.
- Android captures motion aim data and supports reload-hold recenter.
- Desktop pairs with Android by QR or code over local LAN.
- Android streams normalized input/motion to desktop under the 50 ms visualizer target.
- Windows and macOS expose a regular virtual gamepad/joystick gun.
- Desktop profiles map aim to configurable joystick axes.
- Desktop sends haptic feedback back to the Android phone.
- Joystick visualizer proves end-to-end behavior.

**Should have (competitive):**
- Shared packet/profile schema for both desktop targets.
- Packet capture/replay fixtures for protocol regression testing.
- Visible latency, packet loss, connection, and haptic diagnostics.

**Defer (v2+):**
- Direct desktop-to-gun Bluetooth.
- Physical gun motor rumble.
- Extra Android-to-desktop transports beyond LAN.
- Game-specific presets.
- Multi-gun support.

### Architecture Approach

Normalize hardware input on Android, then stream versioned, timestamped, authenticated events to desktop. Keep desktop profile mapping independent from the Android hardware adapter. Put each virtual HID backend behind a common interface so Windows and macOS can share packet/profile behavior while respecting different driver APIs.

**Major components:**
1. Android gun adapter - connects to and decodes iPega gun input.
2. Android sensor capture - reads motion samples, selects the best available provider, exposes fallback/capability metadata, and handles recenter.
3. LAN session transport - QR/code pairing, encrypted UDP input, reliable control/haptics.
4. Desktop receiver/profile mapper - maps normalized events to virtual gamepad reports.
5. Windows/macOS virtual HID backends - expose OS-visible gamepad/joystick devices.
6. Joystick visualizer - first acceptance harness and diagnostics surface.

### Critical Pitfalls

1. **Assuming standard gamepad input** - first diagnostic must test both Android `InputDevice` visibility and raw Bluetooth services.
2. **Static-only APK analysis** - protocol claims must be confirmed with physical-device packet captures.
3. **TCP-only aiming** - use UDP input frames to avoid aim stalls under packet loss.
4. **Late driver packaging** - spike Windows/macOS virtual HID install paths early.
5. **Haptics without TTL/ack** - include command ids, duration, expiry, and ack/fail.
6. **Profiles baked into Android** - keep target mappings on desktop.

## Local Reference App Findings

- `ARGun2021.apk` package is `com.lcp.arbrower`, label `ARGun`, target SDK 29, and requests Bluetooth, Bluetooth admin, location, camera, network, and vibrate permissions. It declares Bluetooth LE and Bluetooth features and includes Unity/native libraries.
- `ARGun Library_1.0.1_apkcombo.com.apk` package is `com.argun`, target SDK 26, appears to be a React Native launcher/library app, and does not request Bluetooth permissions in the manifest.
- `WorldsAR_14.0_apkcombo.com.xapk` package metadata is `com.lenze.armagic`, target SDK 22, includes `com.lenze.armagic.apk` plus OBB, and requests Bluetooth/Bluetooth admin.
- `AR Cher_20200905_Apkpure.xapk` package metadata is `com.lenzetech.archer`, target SDK 29, includes `com.lenzetech.archer.apk` plus OBB, and requests Bluetooth/Bluetooth admin and location.
- `ARGunPro_1.0.19_apkcombo.com.xapk` is 0 bytes locally and should be reacquired if needed.
- Lightweight string scans show probable BLE UUID fragments such as `fff0`, `fff1`, and `fff3`, but this is not enough to define the protocol. Real hardware capture is required.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Hardware and Protocol Discovery
**Rationale:** The gun protocol is the highest unknown and can invalidate implementation assumptions.
**Delivers:** Android diagnostic app, static APK notes, live Bluetooth scan/capture, raw packet fixtures.
**Addresses:** Gun connection, input decode, phone haptic proof, and deferred motor status.
**Avoids:** Assuming standard gamepad input and static-only reverse engineering.

### Phase 2: LAN Session Protocol
**Rationale:** Desktop and Android need a stable normalized contract before platform drivers diverge.
**Delivers:** QR/code pairing, encrypted session, UDP InputFrame, reliable control/haptic channel, latency instrumentation.
**Uses:** Android NSD/QR, UDP, TCP/WebSocket, authenticated packets.
**Implements:** Transport and normalized event pipeline.

### Phase 3: Desktop Virtual HID Spikes
**Rationale:** Windows and macOS feasibility depends on driver APIs, signing, entitlement, and output reports.
**Delivers:** Fake-input virtual gamepad on Windows and macOS, output callback/loopback mapped to phone haptics, backend capability interface.
**Uses:** Windows VHF/KMDF; macOS CoreHID or HIDDriverKit.
**Implements:** Platform virtual HID backends.

### Phase 4: End-to-End Visualizer MVP
**Rationale:** First complete value path should be testable outside a game.
**Delivers:** Android host streams real gun/motion input to desktop visualizer; desktop profiles map aim axes; reload-hold recenters; phone haptic round-trip works.
**Implements:** Profile mapper, visualizer, integration path.

### Phase 5: Hardening and Packaging
**Rationale:** Product cannot be useful if setup is fragile or diagnostics are missing.
**Delivers:** Installers/dev setup docs, signed-driver plan, entitlement notes, logs, replay tests, latency reports.
**Implements:** Distribution readiness for next milestone.

### Phase Ordering Rationale

- Protocol discovery comes first because the real gun may not expose standard Android gamepad events.
- LAN protocol comes before desktop drivers so both desktop targets share one normalized event contract.
- Driver spikes come before visualizer polish because OS policy can reshape implementation.
- Visualizer comes before game-specific support because it isolates product correctness from game quirks.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** BLE/SPP protocol and Unity C# reverse engineering need exact packet evidence.
- **Phase 3:** macOS virtual HID path depends on CoreHID/DriverKit entitlement and target OS availability.
- **Phase 3:** Windows output semantics depend on HID descriptor/output report and target input APIs; v1 maps output to phone haptics.

Phases with standard patterns:
- **Phase 2:** QR/code pairing, UDP input stream, reliable control channel, and timestamping are standard enough to plan directly after specific schema decisions.
- **Phase 4:** Visualizer/profile UI can use ordinary application patterns once backend APIs exist.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Official platform docs support the broad direction; exact macOS path needs entitlement/OS validation. |
| Features | HIGH | User goals and hardware behavior define table stakes clearly. |
| Architecture | MEDIUM | Split architecture is sound; exact protocol/driver implementations depend on discovery. |
| Pitfalls | HIGH | Main risks are strongly implied by platform constraints and reference-app evidence. |

**Overall confidence:** MEDIUM

### Gaps to Address

- **Exact iPega Bluetooth protocol:** Run hardware scan, APK decompile, btsnoop/HCI capture, and packet fixture creation in Phase 1.
- **macOS virtual HID feasibility:** Verify CoreHID `HIDVirtualDevice` availability and entitlement path; otherwise use HIDDriverKit.
- **Windows driver distribution:** Confirm VHF output report and signing/install strategy before product packaging.
- **Haptic semantics:** Map desktop output reports to phone haptics for v1; keep physical gun motor research deferred.
- **Latency budget:** Measure on real phone/Wi-Fi/desktop, not just architecture estimates.

## Sources

### Primary (HIGH confidence)
- Android Developers - game controller input, sensors/motion, Bluetooth, Bluetooth permissions, Network Service Discovery.
- Microsoft Learn - Virtual HID Framework and GameInput/force-feedback.
- Apple Developer Documentation - CoreHID, HIDDriverKit, DriverKit, GameController.

### Secondary (MEDIUM confidence)
- Nefarius documentation - ViGEm end-of-life status.
- vJoy repository/project pages - virtual joystick prototype option and maintenance risk.
- IETF QUIC/TLS specs - transport/security reference points.

### Local Evidence
- `docs/refs/ARGun2021.apk` - Unity/native app, `com.lcp.arbrower`, Bluetooth/LE/vibrate permissions/features.
- `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` - React Native-looking launcher/library, `com.argun`, no Bluetooth manifest permission.
- `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` - `com.lenze.armagic`, target SDK 22, Bluetooth permissions, APK plus OBB.
- `docs/refs/AR Cher_20200905_Apkpure.xapk` - `com.lenzetech.archer`, target SDK 29, Bluetooth/location permissions, APK plus OBB.
- `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` - 0-byte invalid local file.

---
*Research completed: 2026-06-06*
*Ready for roadmap: yes*
