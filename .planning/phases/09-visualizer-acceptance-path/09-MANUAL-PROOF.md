# Phase 9 Manual Proof Guide

Use this guide for final Phase 9 sign-off. The pass artifact is the guided `BT Gun Visualizer` checklist, not a generated evidence bundle.

## Scope

- D-01: prove the LAN visualizer path, macOS Android Bluetooth HID input, and Windows VHF input.
- D-02: require both OS-visible input paths; one platform alone is not enough.
- D-03: prove LAN phone haptic and Windows VHF phone haptic; accept macOS HID haptic only as unsupported/deferred evidence.
- D-04: record final acceptance in the visualizer checklist.
- D-05 to D-08: use the separate visualizer window, auto-open on authenticated Android session, allow manual reopen, and keep stale/disconnected context visible.
- D-09 to D-12: keep checklist and live gamepad visible first, show recenter/events, and keep raw debug secondary.
- D-13 to D-14: verify latency target and packet-loss visibility from current-session metrics.
- D-15 to D-16: run haptic proof rows and require user confirmation for physical, OS-visible, and phone-vibration observations.

## Required Rows

| Row ID | What to observe | How to complete |
|--------|-----------------|-----------------|
| `lan_visualizer_stream` | Android reaches authenticated desktop session and mapped LAN input reaches the visualizer. | Pair Android to desktop, move the gun/phone, confirm the row becomes observed. |
| `live_controls` | Trigger, reload, X, Y, A, B, physical stick, and mapped aim appear in the live gamepad panel. | Press each control and move stick/aim; confirm visible activity. |
| `recenter_aim_zero` | Recenter status and aim-zero labels update after holding reload for 2000 ms. | Hold reload, verify aim-zero/recenter copy, then use `Confirm observed`. |
| `macos_hid_input` | macOS sees Android as a Bluetooth HID gamepad and receives live controls/aim. | Follow `docs/setup/android-bluetooth-hid-gamepad.md`, verify OS-visible input, then use `Confirm observed`. |
| `windows_vhf_input` | Windows VHF backend publishes live input from the Android/gun stream. | Follow the approval-gated Phase 6 checklist behavior and verify the Windows controller moves, then use `Confirm observed`. |
| `lan_phone_haptic` | `Run phone haptic test` queues and receives an Android phone haptic ack over authenticated LAN. | Run the visualizer action, feel the Android phone vibrate, then use `Confirm observed`. |
| `windows_vhf_haptic` | A Windows VHF output report routes to Android phone haptic. | Use the Phase 6 proof flow after required approval, feel the phone vibrate, then use `Confirm observed`. |
| `macos_hid_haptic_limit` | Current macOS Android HID haptic evidence remains unsupported/deferred. | Verify the row displays the limitation and use `Confirm limitation`. |
| `latency_target` | Headline latency shows a current sample under `50 ms` for normal local Wi-Fi. | Confirm the row becomes observed during live input. |
| `packet_loss` | Current-session expected/missed/percent packet-loss counters are visible. | Confirm the row becomes observed while stream metrics are shown. |

## Windows Caveat

Windows proof follows `docs/windows/phase6-proof-checklist.md`. That checklist owns target approval, `joy.cpl` visibility, live input, and output-to-phone haptic behavior. Do not copy target material, local host details, screenshots, command transcripts, identifiers, pairing data, session keys, or certificate material into this guide.

## macOS Caveat

macOS input proof uses Android Bluetooth HID from `docs/setup/android-bluetooth-hid-gamepad.md`. LAN visualizer input does not satisfy `macos_hid_input`. Current macOS HID haptic support is unsupported/deferred; LAN haptic and Windows VHF haptic remain valid phone-haptic paths.

## Closeout

Pass only when the visualizer top summary reads `Phase 9 checks passing`. If any row is failed or waiting, keep Phase 9 open and document the blocker in the final checkpoint notes.
