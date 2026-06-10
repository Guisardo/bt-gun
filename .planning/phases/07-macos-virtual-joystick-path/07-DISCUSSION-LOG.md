# Phase 7: Android Bluetooth HID Gamepad Path - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-10T21:33:48Z
**Phase:** 7-Android Bluetooth HID Gamepad Path
**Areas discussed:** HID Role Gate, Report Shape, Pairing Proof, Output Haptics

---

## HID Role Gate

| Option | Description | Selected |
|--------|-------------|----------|
| Startup gate + explicit HID start | Probe permission/Bluetooth/profile support first; user explicitly starts Bluetooth gamepad mode before macOS pairing. | yes |
| Lazy at pairing time | Register HID only when user starts macOS pairing flow. | |
| Probe-only first | Prove detection/blocked state first; registration/report sending later. | |
| You decide | Leave exact flow to planning. | |

**User's choice:** Startup gate + explicit HID start.
**Notes:** Android HID support should be visible before pairing, not hidden behind failed macOS pairing attempts.

| Option | Description | Selected |
|--------|-------------|----------|
| Full blocked-state matrix | Show Bluetooth off, missing permission, HID_DEVICE proxy unavailable, registerApp failed, no host connected, and host disconnected. | yes |
| Only hard blockers | Show permission/profile/register failures; keep connection details in debug. | |
| Debug-only | Simple unavailable UI with details in logs. | |
| You decide | Leave exact blocked states to planning. | |

**User's choice:** Full blocked-state matrix.
**Notes:** Blocked states should be user-visible and actionable.

| Option | Description | Selected |
|--------|-------------|----------|
| HID primary, LAN optional diagnostics | Android sends HID reports directly to macOS; LAN/desktop companion not required for success, retained for debug/fallback. | yes |
| HID + LAN both required | Keep desktop companion involved during proof. | |
| HID only | No LAN/session in Phase 7 proof path. | |
| You decide | Leave diagnostic role to planning. | |

**User's choice:** HID primary, LAN optional diagnostics.
**Notes:** This is the no-subscription macOS path. Desktop companion should not be in the input path.

| Option | Description | Selected |
|--------|-------------|----------|
| Graceful blocked pass for compatibility detection only | Unsupported phone can pass detection/docs but not DESK-03 input proof; use Windows fallback. | |
| Hard fail Phase 07 | Unsupported phone means Phase 7 cannot pass. | |
| Require alternate Android phone | If current phone lacks support, test another Android device before fallback. | yes |
| You decide | Leave fallback threshold to planning. | |

**User's choice:** Require alternate Android phone.
**Notes:** Windows VHF fallback should not trigger until at least one alternate Android phone is tried.

---

## Report Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror v1 backend contract exactly | Six buttons plus four axes: trigger/reload/X/Y/A/B, stickX/stickY, aimX/aimY. | yes |
| macOS-friendly tweak | Expose trigger as analog trigger axis too, while keeping digital button. | |
| Minimal proof descriptor | Buttons and aim axes only; add stick later. | |
| You decide | Leave descriptor details to planning. | |

**User's choice:** Mirror v1 backend contract exactly.
**Notes:** Preserve cross-platform descriptor parity with the existing backend contract.

| Option | Description | Selected |
|--------|-------------|----------|
| Android owns Bluetooth HID packer | Add Android-side packer from `GunInputState` plus latest `MotionSample`; enforce parity by docs/tests. | yes |
| Share JVM/Kotlin common module | Move report contract into shared code used by Android and desktop. | |
| Copy desktop macOS packer into Android | Fastest, but risks stale divergence. | |
| You decide | Leave ownership to planning. | |

**User's choice:** Android owns Bluetooth HID packer.
**Notes:** Avoid introducing shared-module churn during Phase 7.

| Option | Description | Selected |
|--------|-------------|----------|
| Use calibrated Android aimX/aimY fallback to raw | Prefer Android calibrated preview aim; fallback to normalized raw aim. | yes |
| Raw aim only | Send raw motion-derived aim with no calibration influence. | |
| Center aim until Phase 8 profiles | Buttons/stick proof only; aim deferred. | |
| You decide | Leave aim source to planning. | |

**User's choice:** Use calibrated Android `aimX`/`aimY`, fallback raw.
**Notes:** Phase 8 still owns desktop profile editing; Phase 7 can use Android calibration already present for the HID proof.

| Option | Description | Selected |
|--------|-------------|----------|
| Golden descriptor + golden report vectors | Pin descriptor bytes, bit order, endian/range, stale/center behavior, and parity with `btGunV1Descriptor`. | yes |
| Behavior tests only | Assert controls map correctly without pinning bytes. | |
| Manual smoke only | Rely on macOS pairing/input proof. | |
| You decide | Leave test strictness to planning. | |

**User's choice:** Golden descriptor + golden report vectors.
**Notes:** Tests should catch descriptor/report drift before hardware proof.

---

## Pairing Proof

| Option | Description | Selected |
|--------|-------------|----------|
| Bluetooth + Game Controller proof | Mac Bluetooth shows connected device and a `GCController`/tester sees buttons/axes. | yes |
| Bluetooth only | Mac connects to Android phone as input device; gameplay/controller API proof later. | |
| Game Controller only | Tester sees controller; skip System Settings evidence. | |
| You decide | Leave proof bar to planning. | |

**User's choice:** Bluetooth + Game Controller proof.
**Notes:** Pairing success alone is not enough.

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit pairing-mode control | Button/toggle starts HID registration/discoverable/connectable window with visible countdown/status. | yes |
| Always advertise while session active | Less friction, noisier Bluetooth surface. | |
| Developer-only action | Hidden debug command for Phase 7 proof; polish later. | |
| You decide | Leave UI trigger to planning. | |

**User's choice:** Explicit pairing-mode control.
**Notes:** Pairing mode should be intentional and visible.

| Option | Description | Selected |
|--------|-------------|----------|
| Diagnostics only | Companion can show instructions/evidence checklist/fallback status, but is not in input path. | yes |
| Pairing assistant | Companion guides user and records proof status, but still no input routing. | |
| Not involved | Android + macOS only; docs record manual steps. | |
| You decide | Leave companion role to planning. | |

**User's choice:** Diagnostics only.
**Notes:** Companion may help humans/debugging, but Android HID must stand alone for input.

| Option | Description | Selected |
|--------|-------------|----------|
| User confirms Mac sees controller + moving inputs | User says macOS Bluetooth and tester show controller, buttons/axes move from real gun/phone motion. | yes |
| Agent evidence enough | Screenshots/logs/tester output suffice. | |
| Automated tester only | Command-line/GameController smoke verifies; no manual confirmation. | |
| You decide | Leave confirmation bar to planning. | |

**User's choice:** User confirms Mac sees controller + moving inputs.
**Notes:** Final proof requires real Android/gun input, not replay-only evidence.

---

## Output Haptics

| Option | Description | Selected |
|--------|-------------|----------|
| Try OS-origin output report; honest unsupported allowed | Implement callbacks/probe; if macOS sends no output for descriptor, show unsupported while preserving phone haptic test. | yes |
| Must prove macOS output to phone haptic | Phase 7 cannot pass unless macOS sends output and Android vibrates. | |
| Phone local haptic only | Skip OS-origin output reports in Phase 7. | |
| You decide | Leave haptic bar to planning. | |

**User's choice:** Try OS-origin output report; honest unsupported allowed.
**Notes:** This replaces the older CoreHID hard-output gate with an honest Bluetooth HID capability result.

| Option | Description | Selected |
|--------|-------------|----------|
| Validate strictly + `reportError` | Accept only known report id/length/version/reserved bytes; reject malformed reports and surface status. | yes |
| Ignore silently | Safer UX, less diagnostic value. | |
| Best-effort vibrate | Any output payload triggers phone haptic. | |
| You decide | Leave validation details to planning. | |

**User's choice:** Validate strictly + `reportError`.
**Notes:** Unknown reports must not trigger vibration.

| Option | Description | Selected |
|--------|-------------|----------|
| Keep as diagnostic/fallback only | Authenticated LAN haptic stays for tests and Windows fallback; Android HID output is primary macOS path. | yes |
| Remove from Phase 7 proof | Ignore LAN haptic entirely. | |
| Treat either path as equivalent | HID output or LAN haptic can satisfy haptic proof. | |
| You decide | Leave fallback haptic role to planning. | |

**User's choice:** Keep as diagnostic/fallback only.
**Notes:** LAN haptic cannot satisfy macOS HID output proof.

| Option | Description | Selected |
|--------|-------------|----------|
| Capability + actual result rows | Record callback seen/not seen, validation result, phone vibration result, and unsupported reason if no output arrives. | yes |
| Only pass/fail | Haptic worked or did not. | |
| Manual note only | User says phone vibrated or did not. | |
| You decide | Leave evidence fields to planning. | |

**User's choice:** Capability + actual result rows.
**Notes:** Evidence must distinguish unsupported platform behavior from app bugs.

---

## the agent's Discretion

- Exact Android class names, HID descriptor byte layout, report ids, status model names, tester command names, evidence artifact names, and redaction format.
- Exact macOS tester implementation and Bluetooth evidence collection commands, subject to the user confirmation proof bar.

## Deferred Ideas

- Profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Broader replay diagnostics and packaging docs remain Phase 10.
- Direct desktop-to-gun Bluetooth and physical gun motor rumble remain deferred.
