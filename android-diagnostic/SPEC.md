# Android Diagnostic Spec

Phase 1 diagnostic-only contract for partial `DISC-02` and `DISC-03` coverage. Hardware proof remains Plan 03 work.

## Required Reports

Every diagnostic row is emitted as one line with `schema`, `report`, `ts_elapsed_ms`, `session_id`, and report-specific fields. Required report names:

| report | Purpose |
|--------|---------|
| `permission_state` | Records Android SDK, granted/denied/missing Bluetooth and location permissions, and whether scans are allowed. |
| `input_device_scan` | Lists `InputDevice` ids, names, vendor/product ids when available, sources, keyboard type, motion ranges, and controller-like flags. |
| `key_event` | Records `KeyEvent` action, key code, scan code, repeat count, source, device id, and event time. |
| `motion_event` | Records `MotionEvent` action, source, device id, pointer count, axis values, precision, and event time. |
| `ble_scan` | Records BLE scan start/stop/error states, device address/name when available, RSSI, advertised service UUIDs, and scan record summary. |
| `ble_characteristic` | Records discovered BLE service UUID, characteristic UUID, properties, descriptors, read/notify/write support, and clue ids tested. |
| `classic_scan` | Records bonded Classic devices, address/name when available, type, bond state, UUIDs, and SPP/channel hypotheses. |
| `classic_socket_observation` | Records Classic socket attempt path, UUID/channel, success/failure, bytes received count, exception class/message, and clue id. |
| `app_observed_frame` | Records Base64/hex app-observed payload excerpts from BLE or Classic callbacks, source path, device id/address, characteristic/socket id, and clue id. |
| `rumble_attempt` | Records bounded output attempt parameters, characteristic/socket target, payload ref or hex excerpt, duration/strength if known, and clue id. |
| `rumble_observed` | Records physical motor observation, command ref, capture ref, ack/status if any, and observer note. |
| `rumble_failed` | Records failed output attempt, error/timeout/no-motor outcome, and why it does not satisfy `DISC-06`. |

## Clue Targets

Use `clue_id` values from `docs/protocol/ipega-phase1-clues.md`. Initial diagnostic targets:

- BLE service `fff0` with candidate characteristics `fff1`, `fff3`, and `fff5`.
- BLE notification/read callbacks that may carry Base64 payloads into Unity.
- Bluetooth Classic SPP UUID `00001101-0000-1000-8000-00805F9B34FB` and channel-1 fallback.
- Standard Android controller path from `InputDevice`, `KeyEvent`, and `MotionEvent`.

## Runtime Behavior

- Report permission state before scans.
- If permission is denied or unavailable, emit `permission_state` and a scan/error report instead of silently returning no devices.
- Do not infer that no protocol exists from a missing scan result.
- Do not write arbitrary payloads. `rumble_attempt` must be bounded to static candidates and Plan 03 human hardware workflow.
- Preserve raw payloads as refs or short excerpts only. Large raw/HCI/app-log files stay under ignored `.evidence/phase1/` paths.

## Completion Boundary

This module partially covers `DISC-02` and `DISC-03` by creating the tool contract. Those requirements are not complete until Plan 03 records physical gun evidence, because D-06 and D-07 require static clue, hardware capture, and normalized fixture linkage.

## Exclusions

No production Android host, LAN transport, desktop HID, profile mapping, visualizer, QR pairing, UDP/TCP protocol, or platform driver behavior belongs in this module.
