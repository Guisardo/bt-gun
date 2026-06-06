# iPega Phase 1 Hardware Capture Notes

Physical evidence only. Do not mark rows verified until Plan 04 or Plan 05 links static clue, hardware capture, and normalized fixture.

## Capture Rules

- Raw logcat, HCI, bugreport, and app-log files stay under ignored `.evidence/phase1/` paths.
- Committed docs and manifests contain sanitized pointers, clue ids, action context, expected interpretation, and honest status.
- Missing permissions, unavailable Bluetooth, no observed frame, timeout, and no motor movement are valid captured outcomes.
- Rumble attempts must be bounded to static clue candidates and must not be treated as `DISC-06` proof without observed physical motor activation.

## Dependency Review

Before any Gradle build/install, human must review:

| Coordinate | Value | Status |
|------------|-------|--------|
| Gradle plugin | `com.android.application:8.7.3` | pending human review |
| Gradle plugin | `org.jetbrains.kotlin.android:2.0.21` | pending human review |
| Repositories | `google()`, `mavenCentral()`, `gradlePluginPortal()` | pending human review |
| App id | `com.btgun.diagnostic` | pending human review |

No dependency has been downloaded or built by Task 1.

## Checkpoint Observation: 2026-06-06

Human checkpoint response:

- Android phone is connected over USB.
- Android phone can see the iPega device.
- Direct pairing does not complete; the device appears to require a custom handshake.
- Gradle/plugin dependency build/install was not approved.

Interpretation: direct Android settings pairing is currently unavailable as an evidence path. This supports keeping the BLE/Classic custom-handshake clues as hypotheses, but it does not prove a transport, input control, or rumble path. No trigger, reload, joystick, X/Y/A/B, or rumble success evidence was captured.

## Capture Checklist

| capture_id | section | clue_id | action | raw/app/HCI pointer | status | notes |
|------------|---------|---------|--------|---------------------|--------|-------|
| input-device-scan-001 | input_device_scan | COMMON-GAMEPAD-001 | Run diagnostic device scan with gun powered/pairable; press no controls during baseline. | `.evidence/phase1/app-logs/input-device-scan-001.logcat.txt` | pending hardware | Record device ids, source flags, motion ranges, and permission state. |
| ble-scan-001 | ble_scan | ARGUN2021-BLE-001 | Run BLE scan for `fff0`, `fff1`, `fff3`, and `fff5` candidates. | `.evidence/phase1/app-logs/ble-scan-001.logcat.txt` | pending hardware | Record advertised services, characteristics, notify/read/write support, and denied/unavailable state if any. |
| classic-scan-001 | classic_scan | ARCHER-BT-001 | Inspect bonded Classic devices, SPP UUID, and channel-1/socket observations. | `.evidence/phase1/app-logs/classic-scan-001.logcat.txt` | pending hardware | Record address/name only as safe local evidence; commit sanitized summaries. |
| trigger-001 | trigger | ARGUN2021-CONTROL-001 | Press trigger down/up once during capture. | `.evidence/phase1/app-logs/trigger-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/trigger.jsonl`. |
| reload-001 | reload | ARGUN2021-CONTROL-001 | Press reload down/up once during capture. | `.evidence/phase1/app-logs/reload-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/reload.jsonl`. |
| joystick-001 | joystick_axes | ARCHER-INPUT-001 | Move stick X/Y through neutral, min, max, and release. | `.evidence/phase1/app-logs/joystick-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/joystick.jsonl`. |
| button-x-001 | x_button | ARCHER-INPUT-001 | Press X down/up once during capture. | `.evidence/phase1/app-logs/button-x-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/buttons-xyab.jsonl`. |
| button-y-001 | y_button | ARCHER-INPUT-001 | Press Y down/up once during capture. | `.evidence/phase1/app-logs/button-y-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/buttons-xyab.jsonl`. |
| button-a-001 | a_button | ARCHER-INPUT-001 | Press A down/up once during capture. | `.evidence/phase1/app-logs/button-a-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/buttons-xyab.jsonl`. |
| button-b-001 | b_button | ARCHER-INPUT-001 | Press B down/up once during capture. | `.evidence/phase1/app-logs/button-b-001.logcat.txt` | pending hardware | Expected normalized target: `fixtures/ipega/normalized/buttons-xyab.jsonl`. |
| rumble-ble-fff5-001 | rumble_attempt | ARGUN2021-RUMBLE-001 | Attempt bounded BLE `fff5` write only after input characteristic evidence exists. | `.evidence/phase1/app-logs/rumble-ble-fff5-001.logcat.txt` | pending hardware | Record payload ref, ack/fail, and physical motor observation or no-motor failure. |
| rumble-classic-spp-001 | rumble_attempt | ARCHER-RUMBLE-001 | Attempt bounded Classic SPP write only after Classic input evidence exists. | `.evidence/phase1/app-logs/rumble-classic-spp-001.logcat.txt` | pending hardware | Record payload ref, ack/fail, and physical motor observation or no-motor failure. |

## input_device_scan

Record:

- Android SDK and Bluetooth/location permission state.
- `InputDevice` ids, names, sources, vendor/product ids when available.
- Motion ranges and controller-like flags.
- Any `KeyEvent` or `MotionEvent` logs tied to the gun.
- Explicit unavailable state if the gun does not appear as a standard input device.

## ble_scan

Record:

- BLE scan start/stop/error state.
- Device name/address only in ignored raw logs unless safe to summarize.
- Advertised service UUIDs and discovered service/characteristic UUIDs.
- Properties for candidate `fff1`, `fff3`, and `fff5` characteristics.
- Notification/read/write support and clue id tested.

## classic_scan

Record:

- Bonded device state, device type, and UUID list.
- SPP UUID `00001101-0000-1000-8000-00805F9B34FB` result.
- Channel-1 fallback result.
- Socket success/failure, bytes received count, and exception class/message.

## Controls

For trigger, reload, joystick axes, X, Y, A, and B:

- Run one capture per action.
- Use a static `clue_id` from `docs/protocol/ipega-phase1-clues.md`.
- Save ignored raw/app/HCI pointer paths.
- Document whether the action produced Android input events, BLE frames, Classic bytes, or no observable frame.
- Keep status pending until normalized fixtures are added.

## Rumble Attempts

Rumble is output-path evidence, not input proof.

- Attempt rumble only through `ARGUN2021-RUMBLE-001`, `ARCHER-RUMBLE-001`, or `WORLDAR-RUMBLE-001` candidate paths.
- Record command target, payload ref or short excerpt, bounded duration/strength if known, and capture pointer.
- Record `rumble_observed` only when the physical motor moves.
- Record `rumble_failed` for timeout, write failure, no ack, no motor, permission denied, or unavailable path.
- Do not mark `DISC-06` satisfied from a failed or unavailable attempt.
