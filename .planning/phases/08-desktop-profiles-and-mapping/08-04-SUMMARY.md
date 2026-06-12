---
phase: 08-desktop-profiles-and-mapping
plan: 04
subsystem: android-profile-runtime
tags: [android, profiles, mapping, hid, udp, control, tdd]

requires:
  - phase: 08-03
    provides: Pure ProfileMapper and adaptive aim smoothing
provides:
  - Android HostSessionService active profile runtime apply path
  - Mapped Android Bluetooth HID report packing
  - Mapped UDP product stream flags and raw-debug gating
  - Authenticated Android profile metadata control message
affects: [phase-08, phase-09, android-host, desktop-consumer]

tech-stack:
  added: []
  patterns:
    - Android-owned active profile authority
    - MappedControllerState fanout before HID and LAN output
    - Authenticated profile_metadata diagnostics only

key-files:
  created:
    - .planning/phases/08-desktop-profiles-and-mapping/08-04-SUMMARY.md
  modified:
    - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
    - android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt
    - android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt
    - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
    - android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt
    - android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt
    - android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt
    - android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt
    - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt
    - android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt
    - android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt
    - docs/protocol/lan-pairing-v1.md

key-decisions:
  - HostSessionService loads and applies Android active profiles before HID and LAN fanout.
  - Android UDP frames use offset-6 stream flags for mapped product stream and raw-debug extras.
  - Android sends profile_metadata with source=android and rawDebugEnabled after trusted control auth and profile reload.

patterns-established:
  - HostSessionProfileRuntime is the service boundary for loading, validating, and mapping active profiles.
  - HID and UDP callers consume MappedControllerState instead of remapping downstream.
  - Raw debug extras are Android-controlled and advertised by frame flag, never desktop-requested.

requirements-completed: [PROF-02, PROF-03, PROF-04, PROF-05, PROF-06]

duration: 23m 25s
completed: 2026-06-12
---

# Phase 08 Plan 04: Android Runtime Profile Fanout Summary

**Android active profiles now drive live HID, LAN product stream, and authenticated desktop metadata while raw debug stays Android-controlled.**

## Performance

- **Duration:** 23m 25s
- **Started:** 2026-06-12T16:39:11Z
- **Completed:** 2026-06-12T17:02:36Z
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- HostSessionService loads, exposes, reloads, and applies the active Android profile before output fanout.
- Recenter hold uses the selected physical profile control while mapped virtual reload still publishes normally.
- Android Bluetooth HID packs MappedControllerState into the existing report ID 1 shape without descriptor/report drift.
- UDP input frames carry mapped product aim by default and include raw provider/motion extras only when Android raw debug is enabled.
- DesktopControlClient sends authenticated profile_metadata with source=android, profile id/name/revision, and rawDebugEnabled.

## Task Commits

1. **Task 1 RED: active profile runtime tests** - `4398498` (test)
2. **Task 1 GREEN: HostSessionService active profile runtime** - `c59cc4d` (feat)
3. **Task 2 RED: mapped HID report tests** - `46bb012` (test)
4. **Task 2 GREEN: mapped Android HID reports** - `5ab109f` (feat)
5. **Task 3 RED: mapped UDP and metadata tests** - `299e6f0` (test)
6. **Task 3 GREEN: mapped UDP stream and Android metadata** - `16c2111` (feat)

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - Active profile runtime, mapped state fanout, profile reload action, physical recenter selection, metadata send hook.
- `android-host/app/src/main/java/com/btgun/host/hid/AndroidBluetoothHidGamepad.kt` - Mapped input send path using the existing HID payload contract.
- `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` - MappedControllerState packer overload.
- `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` - Authenticated profile_metadata sender and Android source/raw-debug fields.
- `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` - Mapped UDP snapshots/edges and raw-debug gating.
- `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt` - Stream flags, product aim fields, raw debug extras, and unknown-flag rejection.
- `android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt` - Active load/reload, fanout order, physical recenter, and metadata integration coverage.
- `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt` - Mapped HID golden coverage.
- `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt` - Metadata body and no desktop raw-stream request coverage.
- `android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt` - Mapped product stream and raw-debug flag coverage.
- `android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt` - Frame flag decode/reject coverage.
- `docs/protocol/lan-pairing-v1.md` - UDP offset 6 stream flag semantics.

## Decisions Made

- Android remains the only active profile authority; desktop receives metadata only.
- Mapped product aim uses the existing aim offsets with explicit mapped semantics and stream flag bit 0.
- Raw provider/capability/motion/source timestamp fields are neutral unless Android raw debug is enabled and stream flag bit 1 is set.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Updated wire docs and codec tests for stream flags**
- **Found during:** Task 3 (Send mapped UDP product stream and Android profile metadata)
- **Issue:** The plan added offset-6 stream flags but did not list the protocol doc or codec test file in Task 3 files. Without those updates, the new wire contract would be under-specified and weakly verified.
- **Fix:** Updated `docs/protocol/lan-pairing-v1.md` and `UdpInputFrameCodecTest` for mapped/raw-debug flags and unknown-flag rejection.
- **Files modified:** `docs/protocol/lan-pairing-v1.md`, `android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt`
- **Verification:** Focused UDP/control tests and final required Gradle command passed.
- **Committed in:** `16c2111`

---

**Total deviations:** 1 auto-fixed (Rule 2)
**Impact on plan:** Wire contract documentation and tests now match implementation. No desktop profile authority or raw-stream request path added.

## Issues Encountered

- Gradle file-lock socket creation is blocked by the managed sandbox. Focused and final Gradle runs were rerun with approved escalation.
- A transient `.git/index.lock` permission error occurred once during commit flow; retry succeeded without changing files.

## TDD Gate Compliance

- RED commits exist before each GREEN commit: `4398498`, `46bb012`, `299e6f0`.
- GREEN commits exist after each RED commit: `c59cc4d`, `5ab109f`, `16c2111`.
- No refactor commit was needed.

## Verification

- Task 1 RED failed as expected on missing active profile runtime APIs.
- Task 1 GREEN passed: `gradle -p android-host testDebugUnitTest --tests '*HostSessionService*' --tests '*ProfileMapper*' --no-daemon --console=plain`
- Task 2 RED failed as expected on missing mapped HID packer overload.
- Task 2 GREEN passed: `gradle -p android-host testDebugUnitTest --tests '*BtGunHidReportPacker*' --tests '*HostSessionService*' --no-daemon --console=plain`
- Task 3 RED failed as expected on missing mapped UDP fields/flags and profile metadata sender.
- Task 3 GREEN passed: `gradle -p android-host testDebugUnitTest --tests '*AndroidUdpInputSender*' --tests '*DesktopControlClient*' --no-daemon --console=plain`
- Final required command passed: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*HostSessionService*' --tests '*BtGunHidReportPacker*' --tests '*AndroidUdpInputSender*' --tests '*DesktopControlClient*' --no-daemon --console=plain`
- Key link scan passed: HostSessionService uses ProfileMapper/MappedControllerState and sends profile metadata through DesktopControlClient.
- Raw-stream request scan passed in production source. Test-only banned-token assertions remain intentional.

## Known Stubs

None. Stub scan found nullable state/test defaults and pre-existing dashboard placeholders only; no new product-blocking stubs were introduced.

## Threat Flags

None. New profile, UDP raw-debug, and metadata surfaces were covered by the plan threat model and mitigated in tests.

## Self-Check: PASSED

- Summary file exists.
- Key modified source/doc files exist.
- Task commits found: `4398498`, `c59cc4d`, `46bb012`, `5ab109f`, `299e6f0`, `16c2111`.

## User Setup Required

None.

## Next Phase Readiness

Phase 08 can continue with desktop read-only metadata display and any later visualizer work consuming Android-mapped product stream semantics.

---
*Phase: 08-desktop-profiles-and-mapping*
*Completed: 2026-06-12*
