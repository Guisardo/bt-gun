---
phase: 08
slug: desktop-profiles-and-mapping
status: draft
created: 2026-06-12
---

# Phase 08 - Pattern Map

## Scope

Phase 8 adds Android-owned profile storage, profile editing, mapping, and mapped-stream diagnostics. Desktop remains read-only for active Android profile metadata and mapped stream state.

## New Or Changed Files

| Target File | Role | Closest Existing Analog | Pattern To Reuse |
|-------------|------|-------------------------|------------------|
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileModels.kt` | Profile schema, defaults, ids, revisions | `NormalizedEvents.kt`, `BtGunHidDescriptor.kt` | Small immutable data classes, explicit enums/ids, no Android framework dependency |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileValidation.kt` | Save gate and validation labels | `PairingPayload.kt`, `DesktopHapticCommand.kt` | Parse/validate with sealed result types and stable error names |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileStore.kt` | Local Android persistence | `TrustedDesktopStore.kt`, `AimCalibration.kt` store code | SharedPreferences JSON, default fallback, bounded local-only state |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileMapper.kt` | Raw normalized input to mapped product state | `PreviewAimMapper.kt`, `BtGunHidReportPacker.kt` | Pure Kotlin mapper with clamped normalized axes and deterministic source labels |
| `android-host/app/src/main/java/com/btgun/host/profile/AdaptiveAimSmoother.kt` | Latency-capped smoothing | `PreviewAimMapper.kt` | Pure math, timestamp-driven tests, no service dependency |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | Runtime active profile and fanout owner | Existing HID/LAN service wiring | Foreground service owns live state; adapters receive prepared state |
| `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` | Mapped UDP product stream | Existing fixed 120-byte sender | Keep authenticated fixed frame; add explicit flags/semantics; no unauth raw stream |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` | Desktop decoded mapped input | Existing `UdpReceivedInput` | Keep value object; add flags and mapped aim fields without secrets |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` | Windows fallback semantic state | Existing adapter | Convert trusted decoded mapped state to `SemanticControllerState`; no profile math |
| `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | Profile/dashboard formatter | Existing dashboard formatters | Plain labels, deterministic strings, testable without launching Activity |
| `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | Native Views profile UI | Existing programmatic dashboard | Compact native views, action groups, no Compose/new UI toolkit |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` | Read-only desktop diagnostics | Existing Swing diagnostics HTML | Add rows, no editor controls, sanitize text |

## Data Flow

```text
GunInputState + MotionSample
  -> Android active ProfileDocument/ProfileMapper
  -> MappedControllerState
  -> Android Bluetooth HID report packer
  -> Android UDP mapped product stream
  -> Desktop UdpControllerStateAdapter for Windows fallback
```

Raw provider/motion extras are Android-session debug only. Desktop cannot request them in Phase 8.

## Constraints For Executors

- Use existing `kotlinx.serialization-json`; add no new storage/UI dependencies.
- Keep v1 physical and virtual controls to `trigger`, `reload`, `button_x`, `button_y`, `button_a`, `button_b`.
- Preserve stick axes and aim axes as semantic axes; do not expose axis remap controls.
- Treat hold-to-recenter as a physical control selection separate from virtual button remap.
- Keep desktop profile code read-only; no desktop profile store, editor, or profile authority.
- Keep screenshots under ignored `.evidence/phase8/android-profile-ui/` or `/private/tmp`; never commit raw screenshots.
