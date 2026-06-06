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
| Gradle plugin | `com.android.application:8.7.3` | approved by human 2026-06-06 |
| Gradle plugin | `org.jetbrains.kotlin.android:2.0.21` | approved by human 2026-06-06 |
| Repositories | `google()`, `mavenCentral()`, `gradlePluginPortal()` | approved by human 2026-06-06 |
| App id | `com.btgun.diagnostic` | approved by human 2026-06-06 |

No dependency has been downloaded or built. Build/install is still paused because local disk has only 7.6 GiB free and the data volume is 99% full.

## Checkpoint Observation: 2026-06-06

Human checkpoint response:

- Android phone is connected over USB.
- Android phone can see the iPega device.
- Direct pairing does not complete; the device appears to require a custom handshake.
- Gradle/plugin dependency build/install was not approved.

Interpretation: direct Android settings pairing is currently unavailable as an evidence path. This supports keeping the BLE/Classic custom-handshake clues as hypotheses, but it does not prove a transport, input control, or rumble path. No trigger, reload, joystick, X/Y/A/B, or rumble success evidence was captured.

## No-Build ADB Observation: 2026-06-06

Disk status before Gradle/build: `/System/Volumes/Data` had 7.6 GiB free and 99% capacity used. Gradle CLI was not installed on PATH, and no Gradle wrapper/cache was present under `~/.gradle/wrapper`. To avoid dependency downloads on low disk, the capture path stayed no-build and used ADB `dumpsys` only.

Connected Android device:

- Model: `SM_A750G`.
- Transport: USB ADB.
- Bluetooth setting: enabled (`settings get global bluetooth_on` returned `1`).

`adb shell dumpsys input` observation:

- Current Event Hub devices were `Virtual`, `gpio_keys`, `Codec3035 Headset Events`, `sec_touchscreen`, and `flip_cover`.
- No current external gamepad/gun-like input device appeared while Android Bluetooth settings was focused on `BluetoothScanDialog`.
- Interpretation: after direct pairing failure, the iPega gun is not exposed as a standard Android `InputDevice` through the OS pairing path. This does not rule out custom BLE/Classic app-level input.

`adb shell dumpsys bluetooth_manager` observation:

- Bluetooth was `ON`; adapter scan mode was connectable/discoverable.
- Android Settings selected device name `ARGunGame`.
- Bond attempt for sanitized address prefix `FFFF10` went `BOND_STATE_NONE -> BOND_STATE_BONDING -> BOND_STATE_NONE`.
- ACL link connected at 11:16:43 and disconnected at 11:17:17 with reason `8`.
- Stack log included `gatt_main.cc -- [ble] no client app, drop the link`.
- BluetoothDataManager recorded remote name `ARGunGame`.

Interpretation: the phone can see `ARGunGame` and can briefly establish a low-level Bluetooth link, but default Settings pairing drops because no app/client completes the expected protocol path. This strengthens the custom-handshake hypothesis and blocks OS-pairing-only control capture. No trigger, reload, joystick, X/Y/A/B, or rumble command evidence was captured by this no-build path.

## Capture Checklist

| capture_id | section | clue_id | action | raw/app/HCI pointer | status | notes |
|------------|---------|---------|--------|---------------------|--------|-------|
| input-device-scan-001 | input_device_scan | COMMON-GAMEPAD-001 | Run diagnostic device scan with gun powered/pairable; press no controls during baseline. | `.evidence/phase1/raw/input-device-scan-001.adb-pointer.txt` | captured_no_standard_input_device | ADB dumpsys showed no current external gun/gamepad InputDevice after direct pairing failure. Diagnostic APK scan still pending if disk allows build/install. |
| ble-scan-001 | ble_scan | ARGUN2021-BLE-001 | Run BLE scan for `fff0`, `fff1`, `fff3`, and `fff5` candidates. | `.evidence/phase1/raw/bluetooth-manager-001.adb-pointer.txt` | captured_settings_pairing_failure | ADB dumpsys showed ARGunGame bond attempt and BLE link drop with no client app; service/characteristic discovery still pending. |
| classic-scan-001 | classic_scan | ARCHER-BT-001 | Inspect bonded Classic devices, SPP UUID, and channel-1/socket observations. | `.evidence/phase1/raw/bluetooth-manager-001.adb-pointer.txt` | captured_unbonded_pairing_failure | ADB dumpsys showed ARGunGame not bonded after failed Settings pairing; SPP/socket capture still pending. |
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
