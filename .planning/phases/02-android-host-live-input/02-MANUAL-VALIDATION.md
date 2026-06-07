# Phase 02 Manual Validation - Android Host Live Input

Status: awaiting physical-device validation.

Raw evidence stays under ignored `.evidence/phase2/host-live-input/`. Commit only sanitized pass/fail notes and manifest rows.

## Automation Setup

Agent command for raw capture after the app is exercised:

```bash
android-host/scripts/collect-phase2-host-evidence.sh
```

Expected output path: `.evidence/phase2/host-live-input/<run-id>/`.

## Manual Rows

| Capture ID | Requirement | Manual action | Expected result | Result | Notes |
|---|---|---|---|---|---|
| `phase2-permissions-001` | ANDR-01 | Install and open `com.btgun.host`; tap `Grant permissions`; approve Bluetooth/Nearby Devices and location prompts as shown by Android. | Dashboard permission gate clears or shows only honest unavailable hardware capability; no silent scan/connect failure. | pending | |
| `phase2-ble-connect-001` | ANDR-02, ANDR-08 | Power iPega gun nearby; tap `Start live session`. | Foreground notification says `BT Gun Host running - live input active`; dashboard `Gun connection` reaches `connected`; GATT status mentions `fff3_notifications_enabled`. | pending | |
| `phase2-controls-001` | ANDR-03 | Press trigger, reload, stick left/right/up/down, X, Y, A, and B. Use short distinct presses. | `Last gun event` updates in place with `trigger down/up`, `reload down/up`, `stick_left`, `stick_right`, `stick_up`, `stick_down`, `button_x`, `button_y`, `button_a`, and `button_b`; Debug provenance can show raw BLE details only when expanded. | pending | |
| `phase2-motion-001` | ANDR-04, ANDR-05 | Move phone/gun through yaw left/right and pitch up/down. | `Motion provider` shows selected provider or `tilt fallback`; `Preview aim` dot/numeric X/Y move in expected physical direction; capability flags and baseline elapsed timestamp are visible. | pending | |
| `phase2-recenter-001` | ANDR-06 | Press reload briefly, then hold reload for two seconds, then release. | Brief press shows `reload down` then `reload up` only; two-second hold shows countdown after 500 ms, emits `recenter emitted` with baseline elapsed timestamp, and still shows `reload up` on release. | pending | |
| `phase2-foreground-001` | ANDR-02, ANDR-08 | Start live session, switch apps or lock/unlock screen, then return to host app. | Foreground notification remains visible; dashboard still shows service running plus connected/reconnecting/error state honestly. | pending | |
| `phase2-phone-haptic-001` | ANDR-08 | Tap `Test phone vibration`. | Phone vibrates locally for about 1000 ms; dashboard `Phone haptic` shows `started | local phone vibration 1000ms` or honest unavailable/error. No desktop-origin haptic command, ack/fail, TTL, or control-channel UI appears. | pending | |

## Sign-Off

Type `approved` only after all rows pass. If any row fails, report failed capture ids and notes.
