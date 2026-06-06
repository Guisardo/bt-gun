# Android Diagnostic Module

Diagnostic-only Phase 1 validation tooling for the iPega AR gun. This module exists to test Android visibility of the physical gun before production Android host architecture is chosen.

## Scope

- Enumerate Android `InputDevice` entries and record whether the gun appears as a standard controller.
- Record `KeyEvent` and `MotionEvent` observations while pressing trigger, reload, joystick, X/Y/A/B, and any visible axes.
- Scan BLE services/characteristics and Bluetooth Classic bonded devices, UUIDs, and socket candidates.
- Emit structured diagnostic log lines that Plan 03 can turn into capture manifests and normalized fixtures.
- Record permission state and unavailable/denied states explicitly so scan absence is not treated as protocol proof.

## Out Of Scope

- No production Android host architecture.
- No LAN transport, QR pairing, UDP/TCP session, or desktop link behavior.
- No desktop HID, virtual controller, profiles, aim mapping, or visualizer behavior.
- No claim that `DISC-02` or `DISC-03` is complete without Plan 03 physical hardware evidence.

## Evidence Rule

Static clues from `docs/protocol/ipega-phase1-clues.md` are hypotheses. A finding is verified only after Plan 03 links:

1. static `clue_id`
2. physical hardware capture or app-observed frame
3. normalized fixture row

## Build Boundary

Gradle files are scaffolding only. Do not build, install, or download Gradle/plugin dependencies until Plan 03 performs human dependency review.
