# Phase 7: Android Bluetooth HID Gamepad Path - Context

**Gathered:** 2026-06-10T21:33:48Z
**Status:** Ready for replanning

<domain>
## Phase Boundary

Phase 7 builds and proves the Android phone Bluetooth HID gamepad path. The Android host app must detect whether the phone can act as a Bluetooth HID gamepad peripheral, expose the live iPega gun controls and Android motion aim as a normal gamepad-style HID report, and pair with macOS Apple Silicon so macOS sees an OS-visible controller without paid CoreHID/HIDDriverKit virtual HID entitlements.

This phase does not build Phase 8 desktop profile editing, sensitivity/inversion/dead-zone/smoothing controls, Phase 9 visualizer UI, game-specific presets, direct desktop-to-gun Bluetooth, physical gun motor rumble, or a production notarized macOS virtual HID driver. Existing CoreHID/DriverKit and Windows VHF work remain fallback evidence/scaffolding only.

</domain>

<decisions>
## Implementation Decisions

### HID Role Gate
- **D-01:** Android uses a startup capability gate plus an explicit user-controlled "Start Bluetooth gamepad" action. The app probes Bluetooth state, permissions, and HID device profile support before the user starts HID mode.
- **D-02:** Android must show a full blocked-state matrix: Bluetooth off, missing Bluetooth permission, HID_DEVICE proxy unavailable, HID app registration failed, no host connected, and host disconnected.
- **D-03:** Android Bluetooth HID is the primary macOS input path for Phase 7. LAN streaming and the desktop companion remain optional diagnostics/fallback scaffolding, not required for macOS success.
- **D-04:** If the current Android phone lacks HID peripheral support, test an alternate Android phone before declaring Phase 7 blocked and falling back to Windows VHF.

### HID Report Shape
- **D-05:** The Android Bluetooth HID descriptor mirrors the existing v1 backend contract exactly: six buttons plus four axes for trigger, reload, X/Y/A/B, stickX/stickY, and aimX/aimY.
- **D-06:** Android owns the Bluetooth HID report packer. It maps `GunInputState` plus the latest `MotionSample` into HID report bytes. Do not move code into a shared module during Phase 7 just to share constants.
- **D-07:** HID aim axes use calibrated Android `aimX`/`aimY` when available. If calibrated aim is unavailable, fall back to normalized raw aim values.
- **D-08:** Tests must pin golden descriptor bytes and golden report vectors, including button bit order, axis endian/range, stale/center behavior, and parity with `btGunV1Descriptor`.

### Pairing and Proof
- **D-09:** Phase 7 pairing proof requires both macOS Bluetooth connection evidence and Game Controller/tester evidence that macOS sees buttons and axes.
- **D-10:** Android exposes an explicit pairing-mode control. Starting it registers HID mode and opens a discoverable/connectable pairing window with visible countdown and status.
- **D-11:** The desktop companion is diagnostics only during Android HID proof. It may show instructions, evidence checklist, and fallback status, but it must not be in the macOS input path.
- **D-12:** Final Phase 7 input proof requires user confirmation that macOS Bluetooth sees the controller and a tester/Game Controller surface shows real gun controls and phone motion moving buttons/axes.

### Output Haptics
- **D-13:** Phase 7 must attempt host-origin Bluetooth HID output/rumble report handling, but honest unsupported status is acceptable if macOS sends no output reports for this descriptor.
- **D-14:** Android validates output reports strictly. Only known report id, length, version, and reserved-byte shape can trigger phone haptics; malformed or unknown reports return `reportError` and surface last error/status.
- **D-15:** The authenticated LAN desktop-to-Android phone haptic path remains diagnostic/fallback only for Phase 7. It can support tests and the Windows fallback path, but it is not equivalent to Android HID output for macOS proof.
- **D-16:** Final haptic evidence captures capability plus actual result rows: host-output callback seen/not seen, report validation result, phone vibration result, and macOS unsupported reason when no output arrives.

### Legacy macOS Virtual HID Work
- **D-17:** CoreHID/DriverKit work from earlier Phase 7 plans remains retained evidence/fallback scaffolding. It is not the primary no-subscription macOS v1 path.
- **D-18:** SIP changes, system extension developer mode, install, activation, removal, rollback, reboot, or other OS security-state changes still require explicit later approval.
- **D-19:** Completed Windows VHF work remains the working OS-visible fallback if Android Bluetooth HID cannot be proven after testing an alternate Android phone.

### the agent's Discretion
- Choose exact Android class/package names, HID descriptor byte layout that preserves the v1 contract, report ids, SDP settings, status model names, tester command names, evidence artifact names, and redaction format.
- Choose exact macOS Game Controller tester implementation and Bluetooth evidence collection commands, provided final proof still includes user confirmation and does not commit secrets or sensitive device identifiers.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 7 rerouted goal, Android Bluetooth HID success criteria, legacy CoreHID/DriverKit fallback note, and Windows fallback boundary.
- `.planning/REQUIREMENTS.md` - `ANDR-09`, `ANDR-10`, `ANDR-11`, `DESK-03`, `DESK-06`, `PACK-03`, and `PACK-06`.
- `.planning/PROJECT.md` - v1 Windows/macOS constraints, gamepad-style HID shape, desktop-owned profile mapping boundary, and phone-haptic decision.
- `.planning/STATE.md` - current state after Phase 7 reroute and retained fallback decisions.
- `.planning/quick/260610-m2r-update-requirements-and-roadmap-to-use-a/SUMMARY.md` - quick-task record of the Android Bluetooth HID reroute.

### Prior Phase Context
- `.planning/phases/02-android-host-live-input/02-CONTEXT.md` - Android host BLE, motion, recenter, foreground service, and local phone haptic boundaries.
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - LAN/haptic transport retained for diagnostics and Windows fallback.
- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md` - `btGunV1Descriptor`, semantic controller state, backend capabilities, and smoke harness boundaries.
- `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md` - Windows VHF fallback proof bar, OS-visible controller evidence, and output-to-phone-haptic precedent.

### Legacy Phase 7 Evidence
- `.planning/phases/07-macos-virtual-joystick-path/07-05-SUMMARY.md` - CoreHID gate recorded `corehid-runtime-blocked`; do not use CoreHID as primary no-subscription path.
- `.planning/phases/07-macos-virtual-joystick-path/07-06-SUMMARY.md` - HIDDriverKit fallback stopped before OS security-state changes and remains approval-gated.
- `docs/setup/macos-virtual-hid.md` - legacy CoreHID setup/proof notes retained as fallback evidence.
- `docs/setup/macos-driverkit-fallback.md` - lab-only DriverKit fallback notes; no activation without explicit approval.
- `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` - legacy CoreHID/DriverKit evidence rows.

### Android Host Code
- `android-host/app/src/main/AndroidManifest.xml` - current Bluetooth, network, foreground service, and vibration permission surface to extend for HID mode.
- `android-host/app/src/main/java/com/btgun/host/permissions/HostCapabilityProbe.kt` - existing capability gate pattern to extend with Bluetooth HID device-role status.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - foreground session owner for BLE gun, motion samples, recenter, UDP diagnostics, and haptics.
- `android-host/app/src/main/java/com/btgun/host/model/NormalizedEvents.kt` - `GunInputState` and `MotionSample` inputs for the Android HID report packer.
- `android-host/app/src/main/java/com/btgun/host/haptics/DesktopHapticCommand.kt` - phone haptic validation/result model to reuse for HID output report execution.
- `android-host/app/src/main/java/com/btgun/host/haptics/PhoneHaptics.kt` - Android phone vibration actuator.

### Desktop/Fallback Code
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` - locked v1 gamepad-like joystick descriptor contract.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - capability/limitation model useful for diagnostics and fallback status.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt` - Windows report packing precedent for bit order, axis scaling, and output parity.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosHidReportPacker.kt` - legacy macOS packer precedent only; Android owns the new HID packer.

### Platform API References To Refresh During Research
- Official Android `BluetoothHidDevice` documentation - required for profile proxy, registration, `sendReport`, `replyReport`, and permission behavior.
- Official Android `BluetoothHidDevice.Callback` documentation - required for `onAppStatusChanged`, `onConnectionStateChanged`, `onGetReport`, `onSetReport`, `onInterruptData`, and `reportError` handling.
- Official Android `BluetoothHidDeviceAppSdpSettings` documentation - required for HID app SDP/descriptor registration.
- Apple macOS Bluetooth controller setup and Game Controller documentation - required for macOS pairing and `GCController` proof.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HostCapabilityProbe` already centralizes Android permission/capability evaluation; extend it rather than scattering HID support checks through UI/service code.
- `HostSessionService` already owns foreground lifecycle, BLE gun events, motion samples, recenter, UDP diagnostics, and haptic executor. It is the natural integration point for explicit HID mode start/stop.
- `GunInputState` and `MotionSample` already carry button/stick and aim inputs needed by Android HID report packing.
- `PhoneHaptics` and `DesktopHapticCommandExecutor` already validate and execute bounded phone vibration commands; use the same safety rules for HID output reports.
- `btGunV1Descriptor` locks the product shape that Android HID must mirror.

### Established Patterns
- Hardware/platform-specific protocol details stay behind adapter boundaries and surface normalized events/status.
- Product UI must show honest blocked/unavailable states instead of silently retrying or claiming support early.
- Android local aim mapping exists for preview/calibration, while desktop profile editing remains Phase 8.
- Replay/golden fixtures are allowed for automated tests, but final Phase 7 input proof requires live gun/phone motion and user confirmation.
- Evidence must avoid pairing material, session secrets, stream keys, HMAC keys, private keys, Bluetooth addresses, device identifiers, screenshots with sensitive data, and signing material.

### Integration Points
- Add an Android Bluetooth HID gamepad adapter/service component behind `HostSessionService`.
- Extend Android permission/capability state and dashboard rendering with HID role, registration, host connection, output-report, and fallback status.
- Add Android HID descriptor/report packer tests with golden descriptor/report vectors and parity checks against the v1 descriptor contract.
- Add a macOS tester/proof command or doc path that verifies both Bluetooth connection and Game Controller/tester button/axis movement.
- Keep desktop companion involvement limited to diagnostics, evidence checklist, and fallback routing to Windows VHF.

</code_context>

<specifics>
## Specific Ideas

- User wants an explicit Android "Start Bluetooth gamepad" control rather than always-on advertising.
- Pairing mode should show a visible countdown/status while Android is discoverable/connectable.
- If the current Android phone cannot provide HID peripheral mode, planners must try an alternate Android phone before declaring the path blocked.
- The final macOS proof must be user-visible: Bluetooth connection plus a tester/Game Controller surface showing live real inputs.
- DESK-06 can be honest unsupported for macOS output reports if Android implements the callback/probe path and records that macOS did not send usable output.

</specifics>

<deferred>
## Deferred Ideas

- Phase 8 owns profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping.
- Phase 9 owns visualizer UI, latency dashboard, packet-loss dashboard, and recenter display.
- Phase 10 owns broader replay diagnostics and packaging documentation beyond Phase 7 proof docs.
- Direct desktop-to-gun Bluetooth remains v2/deferred.
- Physical gun motor rumble remains v2/deferred.
- Production notarized macOS virtual HID driver flow remains out of scope unless a later phase explicitly revives DriverKit as product path.

</deferred>

---

*Phase: 7-Android Bluetooth HID Gamepad Path*
*Context gathered: 2026-06-10T21:33:48Z*
