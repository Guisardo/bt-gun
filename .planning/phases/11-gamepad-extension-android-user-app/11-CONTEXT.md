# Phase 11: Gamepad Extension Android User App - Context

**Gathered:** 2026-06-15T21:30:00Z
**Status:** Ready for implementation

<domain>
## Phase Boundary

Phase 11 starts the v1.1 user-facing Android app beside the completed v1 debug host. It keeps the current diagnostic app available for fixtures, raw toggles, and developer dashboards while adding a minimal `Gamepad Extension` app for play, pairing, profiles, calibration, haptic status, and camera-HUD game mode.

Visual source: Figma `Gamepad Extension App` at `https://www.figma.com/make/AEIywgGKOkJS8ssV5pYpm7/Gamepad-Extension-App?t=042c352zlLfFJYGw-1`. User app styling must match phosphor `#00ff41`, dim green text, dark translucent HUD panels, reticle glow, scanline/corner framing, camera background, slide menu, and right-side zoom rail.

This phase does not replace v1 diagnostics, remove replay fixtures, change the regular gamepad HID shape, add direct desktop-to-gun Bluetooth, or claim physical gun motor rumble.
</domain>

<decisions>
## Implementation Decisions

### Module Ownership
- **D-11-01:** Keep `android-host/:app` as the debug/diagnostic host.
- **D-11-02:** Add `android-host/:runtime` as the shared Android runtime library for BLE gun input, motion, profiles, LAN, HID, haptics, and session orchestration.
- **D-11-03:** Add `android-host/:user-app` as package `com.btgun.gamepadextension`, label `Gamepad Extension`, depending on `:runtime`.
- **D-11-04:** Each app owns manifest, permissions, foreground-service declaration, and UI; runtime owns shared play/session logic only.

### Play Mode
- **D-11-05:** `PlayModeController` is the single output authority with modes `NONE`, `LAN`, and `BLUETOOTH_HID`.
- **D-11-06:** Switching modes stops current output, waits for stopped state, then starts the requested output.
- **D-11-07:** BLE gun and motion capture are common input prerequisites, not output modes.
- **D-11-08:** LAN output uses WSS control plus compact UDP only after capability negotiation; Bluetooth HID output starts only the HID role.

### User App UX
- **D-11-09:** First launch offers `LAN`, `Bluetooth`, and `Profiles`.
- **D-11-10:** HUD opens only when camera preview, BLE gun input, motion provider, active profile, and selected output mode are ready.
- **D-11-11:** Camera permission/preview denial blocks game mode with a camera-required state.
- **D-11-12:** User app uses framework/Camera2 preview lifecycle, not Compose or CameraX.
- **D-11-13:** HUD includes soft Back/Home/Select sources, menu, status strip, Figma-style reticle, and right-side zoom rail with `+`, `-`, current zoom label, hardware zoom when available, and software/no-op fallback when unavailable.
- **D-11-14:** Play activity is landscape-only and supports both landscape rotations; camera, HUD, and aim transforms share one rotation source.

### Protocol and Haptics
- **D-11-15:** Preserve the existing fixed 120-byte UDP frame as product v1.
- **D-11-16:** Add compact LAN v2 with new magic/version or frame type, full HMAC-SHA256, stream id/tag, sequence, capture timestamp, send timestamp, mapped button bitmask, stick axes, and aim axes.
- **D-11-17:** Desktop receiver mux accepts v1 and v2; control capability negotiation selects v2 and falls back to v1.
- **D-11-18:** Haptic timeline extends the existing one-shot schema compatibly. Timeline max is 8 pulses, 2000ms total, non-negative `atMs`, valid `durationMs`, strength `0.0..1.0`, with v1 pulse fallback.

### Profiles and Calibration
- **D-11-19:** Profile schema v2 stores aim calibration per profile and migrates current global `active_calibration` into the active profile when missing.
- **D-11-20:** Active profile switch loads that profile's calibration.
- **D-11-21:** Profile settings own aim deadzone, sensitivity, smoothing, button mappings, and soft controls `back`, `home`, and `select`.
- **D-11-22:** Reload hold longer than 2s recenters only; extra-long reload calibration is disabled. Menu `Calibrate` starts 4-corner trigger-capture calibration and saves to the active profile.
</decisions>

<canonical_refs>
## Canonical References

- `.planning/PROJECT.md`
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/STATE.md`
- `.planning/phases/10-diagnostics-replay-and-v1-docs/10-CONTEXT.md`
- `android-host/settings.gradle.kts`
- `android-host/app/build.gradle.kts`
- `android-host/app/src/main/AndroidManifest.xml`
- `android-host/runtime/src/main/java/com/btgun/host/HostSessionService.kt`
- `android-host/runtime/src/main/java/com/btgun/host/MainActivity.kt`
- `android-host/runtime/src/main/java/com/btgun/host/profile/ProfileModels.kt`
- `android-host/runtime/src/main/java/com/btgun/host/profile/ProfileStore.kt`
- `android-host/runtime/src/main/java/com/btgun/host/recenter/ReloadHoldRecenter.kt`
- `android-host/runtime/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt`
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt`
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt`
- `docs/protocol/lan-pairing-v1.md`
- `docs/protocol/input-stream-v1-fixtures.md`
</canonical_refs>

<test_plan>
## Required Coverage

- GSD Phase 11 docs guard.
- Gradle module split preserves debug app and adds runtime/user app.
- `PlayModeController` gates HID and LAN output.
- v1/v2 UDP mux, compact codec, replay/freshness using send timestamp, bad HMAC, wrong stream, stale/replay.
- Haptic timeline validation and v1 fallback.
- Profile v2 migration, active-profile calibration load, soft controls, and mappings.
- Reload hold emits recenter only.
- Camera permission/preview blocking, landscape/upside-down remap, and zoom fallback.
</test_plan>

---

*Phase: 11-Gamepad Extension Android User App*
