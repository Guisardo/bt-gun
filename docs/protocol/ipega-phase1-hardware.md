# iPega Phase 1 Hardware Capture Notes

Physical evidence only. Do not mark rows verified until Plan 04 or Plan 05 links static clue, hardware capture, and normalized fixture.

## Capture Rules

- Raw logcat, HCI, bugreport, and app-log files stay under ignored `.evidence/phase1/` paths.
- Committed docs and manifests contain sanitized pointers, clue ids, action context, expected interpretation, and honest status.
- Missing permissions, unavailable Bluetooth, no observed frame, timeout, and no motor movement are valid captured outcomes.
- Physical gun motor attempts must be bounded to static clue candidates and kept separate from v1 `DISC-06`; v1 feedback proof is Android phone vibration.

## Dependency Review

Before any Gradle build/install, human must review:

| Coordinate | Value | Status |
|------------|-------|--------|
| Gradle plugin | `com.android.application:8.7.3` | approved by human 2026-06-06 |
| Gradle plugin | `org.jetbrains.kotlin.android:2.0.21` | approved by human 2026-06-06 |
| Repositories | `google()`, `mavenCentral()`, `gradlePluginPortal()` | approved by human 2026-06-06 |
| App id | `com.btgun.diagnostic` | approved by human 2026-06-06 |

Dependency review completed before build/install. First successful diagnostic build used:

```bash
ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle assembleDebug
```

The local Gradle user home override was required because the default user-home native Gradle cache failed to initialize. Build output was installed with `adb install -r android-diagnostic/app/build/outputs/apk/debug/app-debug.apk`.

## Checkpoint Observation: 2026-06-06

Human checkpoint response:

- Android phone is connected over USB.
- Android phone can see the iPega device.
- Direct pairing does not complete; the device appears to require a custom handshake.
- Gradle/plugin dependency build/install was approved later in this checkpoint sequence.

Interpretation: direct Android settings pairing is currently unavailable as an evidence path. This supports keeping the BLE/Classic custom-handshake clues as hypotheses, but it does not prove a transport, input control, or physical motor path. No trigger, reload, joystick, X/Y/A/B, or motor success evidence was captured.

## Diagnostic APK Observation: 2026-06-06

Build/install:

- Disk was freed to about 20 GiB before Gradle build/install continued.
- `gradle assembleDebug` initially required Android SDK path and JVM target fixes; the module now builds Java/Kotlin bytecode with JVM 17.
- `com.btgun.diagnostic` installed successfully on `SM_A750G`.
- Android SDK was 29. Legacy Bluetooth and coarse/fine location permissions were granted; Android 12 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` runtime permissions are not applicable on SDK 29.

Diagnostic `input_device_scan` observation:

- Devices remained `Virtual`, `gpio_keys`, `Codec3035 Headset Events`, `sec_touchscreen`, and `flip_cover`.
- No `ARGunGame` or external gun/gamepad-like `InputDevice` appeared in the diagnostic scan.

Diagnostic BLE scan observation:

- `ARGunGame` advertisements were captured repeatedly.
- Committed docs sanitize the address to prefix `FFFF10`; ignored raw logcat keeps the full address.
- RSSI was approximately `-38` to `-40` during the saved capture window.
- Advertised service UUID was `0000fff0-0000-1000-8000-00805f9b34fb`.

Diagnostic Classic observation:

- Classic scan enumerated existing bonded devices, but `ARGunGame` was not bonded.
- No Classic SPP UUID or channel-1 socket evidence was captured for the gun.

Bluetooth manager after diagnostic:

- `com.btgun.diagnostic` registered as a BLE scan client and accumulated scan results.
- Bluetooth manager still showed `Connections: 0` and no Low Energy connection attempts for the diagnostic app.

Interpretation: physical hardware now confirms the deprecated-app BLE `fff0` hypothesis at the advertisement level. It does not yet prove `fff1`, `fff3`, `fff5`, control frames, Classic transport, pairing handshake semantics, or physical motor rumble. The next diagnostic step needs GATT connect/service discovery and notification/read capture against the `fff0` device; direct OS pairing remains blocked by the custom-handshake path.

## Diagnostic GATT Observation: 2026-06-06

Diagnostic app-level BLE connection succeeded without Android Settings pairing.

GATT connection:

- `ARGunGame` was found by `fff0` service scan filter.
- `connectGatt(..., TRANSPORT_LE)` connected with status `0`.
- Service discovery completed with status `0`.

Discovered services:

- `00001801-0000-1000-8000-00805f9b34fb` with `2a05` indicate.
- `00001800-0000-1000-8000-00805f9b34fb` with `2a00`, `2a01`, and `2a04` read characteristics.
- `0000180a-0000-1000-8000-00805f9b34fb` with `2a50` read characteristic.
- `0000fff0-0000-1000-8000-00805f9b34fb` with five characteristics:
  - `fff1` read|notify, CCCD `2902`.
  - `fff2` read.
  - `fff3` read|notify, CCCD `2902`.
  - `fff4` read.
  - `fff5` read|write.

Candidate reads/subscriptions:

- `fff1` read status `0`, 16-byte payload `22000000000000000000000000000005`; CCCD notify enable status `0`.
- `fff3` read status `0`, 16-byte zero payload; CCCD notify enable status `0`.
- `fff5` read status `0`, 16-byte zero payload; no write attempted.

Mixed physical control notification window:

- Notifications arrived on `fff3`.
- Payloads included ASCII `ARGun KeyPressed`, a zero frame, `B8DOWN`, `B8UP`, `B4DOWN`, `B4UP`, and `B6DOWN`.
- This proves app-level BLE notification input exists on `fff3`, but it is not yet a verified semantic map for trigger, reload, joystick, X, Y, A, or B because the capture window did not produce per-control timing labels.

Targeted trigger captures:

- Human reported pressing trigger twice during `trigger-001`.
- First trigger-only capture emitted one `fff3` zero frame: `00000000000000000000000000000000`.
- Repeat trigger-only capture emitted `fff3` ASCII `ARGun KeyPressed` (`415247756e204b657950726573736564`), then the same 16-byte zero frame.
- No Android `KeyEvent` or `MotionEvent` appeared.

Interpretation: trigger most likely maps through BLE `fff3`, with `ARGun KeyPressed` as trigger active/press and zero frame as idle/clear. Keep this as a trigger candidate until Plan 04 fixture normalization decides exact down/up semantics.

Targeted reload capture:

- Human was instructed to press reload twice and hold about 1 second each time.
- Early capture and clean capture both emitted two matching `fff3` pairs.
- Reload down candidate: ASCII `B8DOWN`, hex `4238444f574e`.
- Reload up candidate: ASCII `B8UP`, hex `42385550`.
- No Android `KeyEvent` or `MotionEvent` appeared.

Interpretation: reload maps through BLE `fff3` as `B8DOWN`/`B8UP`. Keep this as a reload candidate until Plan 04 fixture normalization writes down/up semantics.

Targeted joystick capture:

- Human confirmed joystick sequence was left, right, up, then down.
- Capture emitted `fff3` pairs in that order across the saved retry/early buffers.
- Left candidate: ASCII `B6DOWN`/`B6UP`, hex `4236444f574e` / `42365550`.
- Right candidate: ASCII `B4DOWN`/`B4UP`, hex `4234444f574e` / `42345550`.
- Up candidate: ASCII `B5DOWN`/`B5UP`, hex `4235444f574e` / `42355550`.
- Down candidate: ASCII `B7DOWN`/`B7UP`, hex `4237444f574e` / `42375550`.
- No Android `KeyEvent` or `MotionEvent` appeared.

Interpretation: the stick appears as four digital direction events over BLE `fff3`, not analog axes. Keep axis semantics pending until Plan 04 fixture normalization decides whether to represent these as digital buttons or normalized axis extremes.

Joystick sweep capture:

- Diagnostic `joystick_sweep_marker` captured a full outer-rim clockwise/counterclockwise sweep in `.evidence/phase1/app-logs/joystick-sweep-20260607T154507Z.logcat.txt`.
- Raw payload set stayed at the same four switch pairs: `B4`, `B5`, `B6`, and `B7` down/up.
- Reconstructed active switch states produced nine normalized stick points: neutral `(0,0)`, cardinals `(+1,0)`, `(0,+1)`, `(-1,0)`, `(0,-1)`, and diagonals `(+1,+1)`, `(+1,-1)`, `(-1,+1)`, `(-1,-1)`.
- Axis convention from this capture: `B4=right`, `B6=left`, `B5=up`, `B7=down`; Y up is positive.

Interpretation: the stick is not analog, but it is not just last-direction-only either. Adjacent switch overlap around the rim is meaningful and should be emitted as a composite normalized `stick` axis event.

Targeted X/Y/A/B capture:

- Human was instructed to press X, Y, A, then B in order.
- Sequence capture emitted `BA`, `B3`, and `B2` down/up-style events in that order; the final B event was not completed in that sequence window.
- Separate B-only capture emitted `B9DOWN`/`B9UP`.
- X candidate: ASCII `BADOWN`/`BAUP`, hex `4241444f574e` / `42415550`.
- Y candidate: ASCII `B3DOWN`/`B3UP`, hex `4233444f574e` / `42335550`.
- A candidate: ASCII `B2DOWN`/`B2UP`, hex `4232444f574e` / `42325550`.
- B candidate: ASCII `B9DOWN`/`B9UP`, hex `4239444f574e` / `42395550`.
- No Android `KeyEvent` or `MotionEvent` appeared.

Interpretation: X/Y/A/B appear as digital button events over BLE `fff3`. Keep these as candidate mappings because the clean per-button Y and A windows were empty; the mapping depends on the confirmed sequence capture plus the B-only capture.

Phone vibration control check:

- Diagnostic `Phone Vibrate 1s` button called Android `Vibrator` API for `1000` ms.
- Saved log: `.evidence/phase1/app-logs/phone-vibrate-001.logcat.txt`.
- Log emitted `phone_vibrate` state `started`; human confirmed the phone vibrated.

Interpretation: Android app-side haptics are available on the phone. This supports treating reference-app `android.permission.VIBRATE` / Unity `Handheld.Vibrate` as phone vibration evidence unless another static or hardware clue ties it to BLE output. It satisfies the v1 feedback decision for `DISC-06`; physical gun motor activation remains deferred because no BLE motor command moved the gun.

Interpretation: direct OS pairing is unnecessary for the BLE path. The custom app handshake currently appears to be GATT connect + service discovery + `fff1`/`fff3` notification enable, but this is still partial until targeted per-control captures and normalized fixtures exist. Physical motor rumble is deferred; `fff5` is only proven as read|write.

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

Interpretation: the phone can see `ARGunGame` and can briefly establish a low-level Bluetooth link, but default Settings pairing drops because no app/client completes the expected protocol path. This strengthens the custom-handshake hypothesis and blocks OS-pairing-only control capture. No trigger, reload, joystick, X/Y/A/B, or physical motor command evidence was captured by this no-build path.

## Capture Checklist

| capture_id | section | clue_id | action | raw/app/HCI pointer | status | notes |
|------------|---------|---------|--------|---------------------|--------|-------|
| input-device-scan-001 | input_device_scan | COMMON-GAMEPAD-001 | Run diagnostic device scan with gun powered/pairable; press no controls during baseline. | `.evidence/phase1/app-logs/ble-scan-001.logcat.txt` | captured_diagnostic_no_standard_input_device | Diagnostic APK scan showed no external gun/gamepad InputDevice; raw ADB dumpsys also showed none after direct pairing failure. |
| ble-scan-001 | ble_scan | ARGUN2021-BLE-001 | Run BLE scan for `fff0`, `fff1`, `fff3`, and `fff5` candidates. | `.evidence/phase1/app-logs/ble-scan-001.logcat.txt` | captured_ble_advertised_fff0 | Diagnostic BLE scan saw `ARGunGame` advertising service `0000fff0-0000-1000-8000-00805f9b34fb`; GATT details captured separately in `ble-gatt-discovery-001`. |
| ble-gatt-discovery-001 | ble_gatt | ARGUN2021-BLE-001 | Connect to `ARGunGame`, discover GATT services, read candidates, enable notify. | `.evidence/phase1/app-logs/ble-gatt-discovery-001.logcat.txt` | captured_gatt_fff0_characteristics | GATT connect succeeded; `fff0` has `fff1` read|notify, `fff3` read|notify, and `fff5` read|write. |
| control-actions-001 | control_notifications | ARCHER-INPUT-001 | Mixed physical control press window after `fff3` notifications enabled. | `.evidence/phase1/app-logs/control-actions-001.logcat.txt` | captured_unmapped_ble_notifications | `fff3` emitted ASCII control-like payloads including `B8DOWN/UP`, `B4DOWN/UP`, and `B6DOWN`; targeted semantic mapping still required. |
| classic-scan-001 | classic_scan | ARCHER-BT-001 | Inspect bonded Classic devices, SPP UUID, and channel-1/socket observations. | `.evidence/phase1/raw/bluetooth-manager-after-diagnostic-001.txt` | captured_unbonded_no_classic_gun | Diagnostic Classic scan showed existing bonded devices only; `ARGunGame` was not bonded and no gun SPP/socket evidence was captured. |
| trigger-001 | trigger | ARGUN2021-CONTROL-001 | Press trigger down/up once during capture. | `.evidence/phase1/app-logs/trigger-001-repeat.logcat.txt` | captured_trigger_candidate | `fff3` emitted ASCII `ARGun KeyPressed` followed by zero frame during trigger-only capture; candidate active/idle map pending fixture normalization. |
| reload-001 | reload | ARGUN2021-CONTROL-001 | Press reload down/up once during capture. | `.evidence/phase1/app-logs/reload-001.logcat.txt` | captured_reload_candidate | `fff3` emitted two `B8DOWN`/`B8UP` pairs during reload-only capture; candidate down/up map pending fixture normalization. |
| joystick-001 | joystick_axes | ARCHER-INPUT-001 | Move stick X/Y through neutral, min, max, and release. | `.evidence/phase1/app-logs/joystick-001-retry-early.logcat.txt` | captured_joystick_candidate | Confirmed order left/right/up/down maps to `B6`/`B4`/`B5`/`B7` down/up pairs on `fff3`; likely digital directions, not analog axes. |
| joystick-sweep-001 | joystick_axis_map | ARCHER-INPUT-001 | Sweep joystick around full outer rim clockwise and counterclockwise. | `.evidence/phase1/app-logs/joystick-sweep-20260607T154507Z.logcat.txt` | captured_joystick_axis_map | Same four switch payload pairs combine into eight outer-edge axis points plus neutral; host maps active state to normalized `stick` x/y. |
| button-x-001 | x_button | ARCHER-INPUT-001 | Press X down/up once during capture. | `.evidence/phase1/app-logs/buttons-xyab-sequence-001.logcat.txt` | captured_button_candidate | Sequence capture maps X to `BADOWN`/`BAUP`; candidate pending fixture normalization. |
| button-y-001 | y_button | ARCHER-INPUT-001 | Press Y down/up once during capture. | `.evidence/phase1/app-logs/buttons-xyab-sequence-001.logcat.txt` | captured_button_candidate | Sequence capture maps Y to `B3DOWN`/`B3UP`; candidate pending fixture normalization. |
| button-a-001 | a_button | ARCHER-INPUT-001 | Press A down/up once during capture. | `.evidence/phase1/app-logs/buttons-xyab-sequence-001.logcat.txt` | captured_button_candidate | Sequence capture maps A to `B2DOWN`; matching `B2UP` exists in the noisy X window. |
| button-b-001 | b_button | ARCHER-INPUT-001 | Press B down/up once during capture. | `.evidence/phase1/app-logs/button-b-001.logcat.txt` | captured_button_candidate | B-only capture maps B to `B9DOWN`/`B9UP`; candidate pending fixture normalization. |
| phone-vibrate-001 | phone_haptics | ARGUN2021-RUMBLE-001 | Tap diagnostic `Phone Vibrate 1s` button. | `.evidence/phase1/app-logs/phone-vibrate-001.logcat.txt` | captured_phone_vibrate_confirmed | Android `Vibrator` path works on the phone and is the v1 feedback proof; this is not gun motor evidence. |
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

## Deferred Motor Attempts

Physical gun motor rumble is deferred output-path evidence, not input proof.

- Attempt physical motor writes only through `ARGUN2021-RUMBLE-001`, `ARCHER-RUMBLE-001`, or `WORLDAR-RUMBLE-001` candidate paths.
- Record command target, payload ref or short excerpt, bounded duration/strength if known, and capture pointer.
- Record `rumble_observed` only when the physical motor moves.
- Record `rumble_failed` for timeout, write failure, no ack, no motor, permission denied, or unavailable path.
- Do not mark physical gun motor success from a failed or unavailable attempt; v1 `DISC-06` is satisfied only by phone vibration proof plus deferred motor status.
