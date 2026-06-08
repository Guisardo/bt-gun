---
phase: 04-input-stream-and-haptic-transport
plan: 02
subsystem: transport
tags: [kotlin, android, udp, hmac, tdd, control-channel]

requires:
  - phase: 04-input-stream-and-haptic-transport
    provides: fixed UDP frame codec and trusted input stream config model from 04-01
provides:
  - Trusted WSS input_stream_config handling on Android
  - Foreground-service-owned Android UDP sender for snapshot and edge frames
  - Per-stream-session monotonic UDP sequence helper
  - Fake datagram-sink tests for trusted config gating and raw-motion-only frames
affects: [android-sender, input-stream, control-channel, phase-04]

tech-stack:
  added: []
  patterns:
    - Injected datagram sink and elapsed clock for UDP sender tests
    - Foreground service owns UDP sender lifecycle and 60 Hz snapshot tick
    - Control-channel config callback after session_ready and session-id gates

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt
    - android-host/app/src/main/java/com/btgun/host/transport/InputStreamSequencer.kt
    - android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt
  modified:
    - android-host/app/build.gradle.kts
    - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
    - android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt
    - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
    - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt

key-decisions:
  - "Start Android UDP only from trusted WSS input_stream_config, never from QR/manual material."
  - "Use one monotonic sequence across snapshot and edge frames per stream session."
  - "Keep Android UDP payload raw-motion-only; preview/product aim remains local dashboard state."

patterns-established:
  - "RED main-style tests define missing Android sender/control-client behavior before implementation."
  - "AndroidUdpInputSender uses injected sink/clock in tests and a platform DatagramSocket sink at runtime."
  - "HostSessionService stops or grace-expires UDP sender under foreground/control lifecycle ownership."

requirements-completed:
  - TRAN-04
  - TRAN-05

duration: 7 min
completed: 2026-06-08
---

# Phase 04 Plan 02: Android Trusted Config UDP Sender Summary

**Trusted WSS-configured Android UDP sender with 60 Hz snapshots, immediate edge frames, and raw motion payloads**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-08T18:27:47Z
- **Completed:** 2026-06-08T18:34:39Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added RED tests for `input_stream_config` allowlist/session gates, sender inactivity before trusted config, snapshot frames, immediate edge frames, monotonic mixed-frame sequencing, and raw-motion-only encoding.
- Implemented Android `ControlMessageType.INPUT_STREAM_CONFIG` parsing and callback delivery only after `session_ready` and matching trusted session id.
- Added `AndroidUdpInputSender` and `InputStreamSequencer`, using the Plan 04-01 HMAC frame codec and no new external packages.
- Wired `HostSessionService` to own UDP sender lifecycle, send 60 Hz snapshots, send control-edge frames from live gun state, stop on explicit session/control stop, and grace-expire after short control disconnects.

## Task Commits

1. **Task 1: RED - add Android trusted-config UDP sender tests** - `9d2830f` (test)
2. **Task 2: GREEN - wire Android sender into foreground service lifecycle** - `65ef340` (feat)

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` - Encodes authenticated UDP snapshot/edge frames from live gun/motion state and sends through injected or platform datagram sink.
- `android-host/app/src/main/java/com/btgun/host/transport/InputStreamSequencer.kt` - Resets sequence for each stream session and emits monotonic UDP sequence values.
- `android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt` - Fake-sink sender tests for trusted config, snapshots, edges, sequence, and raw-motion boundary.
- `android-host/app/src/main/java/com/btgun/host/session/ControlEnvelope.kt` - Adds `input_stream_config` control type.
- `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` - Parses trusted input stream configs behind session-ready/session-match gates.
- `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt` - Adds config allowlist, pre-ready rejection, mismatch rejection, and callback tests.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - Starts/stops sender inside foreground session, schedules snapshots, and emits edge frames from gun events.
- `android-host/app/build.gradle.kts` - Registers `AndroidUdpInputSenderTestKt`.

## Decisions Made

- Android UDP trust is derived only from authenticated WSS config because QR/manual material is pairing input, not stream key material.
- Edge frames reuse the same sequence counter as snapshots so receiver replay handling can stay simple.
- The runtime datagram sink uses platform UDP sockets; tests inject a fake sink so no network is required.

## Deviations from Plan

### Verification Scope Adjustment

**1. Exact grep command includes pre-existing local preview fields**
- **Found during:** Task 2 verification
- **Issue:** The plan's exact grep includes `HostSessionService.kt` and bans `PreviewAim|aimX|aimY`; those terms already exist from Phase 2 local dashboard preview/calibration behavior and are unrelated to UDP transport.
- **Adjustment:** Kept existing preview behavior intact. Verified the new UDP transport/control paths with the same banned-term pattern, and verified the `HostSessionService.kt` task diff adds no banned boundary terms.
- **Files modified:** None for this adjustment.
- **Verification:** Scoped grep passed for transport + `DesktopControlClient.kt`; `git diff -U0 HEAD -- HostSessionService.kt | rg ...` returned no matches before commit.
- **Committed in:** `65ef340`

**Total deviations:** 1 verification-scope adjustment.  
**Impact on plan:** No product scope change. UDP sender/control code remains raw-motion-only; existing local preview UI code stays outside transport payloads.

## Issues Encountered

- Sandboxed Gradle startup failed with `java.net.SocketException: Operation not permitted`; the approved unsandboxed Gradle run using the documented Phase 04 workaround passed.
- Exact boundary grep failed because of pre-existing Phase 2 preview terms in `HostSessionService.kt`; scoped transport/control grep passed and the task diff showed no new banned terms.

## Verification

- RED: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*AndroidUdpInputSender*' --tests '*DesktopControlClient*'` - failed as expected on missing `INPUT_STREAM_CONFIG`, `onInputStreamConfigReceived`, `AndroidUdpInputSender`, `InputStreamSequencer`, and `AndroidUdpDatagramSink`.
- GREEN/final: same Gradle command - passed.
- `! rg -n "PreviewAim|aimX|aimY|profile mapping|profile_mapper|virtual joystick|physical gun motor|RUMBLE2" android-host/app/src/main/java/com/btgun/host/transport android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` - passed.
- Exact planned grep including `HostSessionService.kt` - failed on pre-existing local preview fields; see deviation.
- `git log --oneline --grep="04-02" -5` - found `9d2830f` and `65ef340`.

## TDD Gate Compliance

- RED commit exists: `9d2830f`
- GREEN commit exists after RED: `65ef340`
- Refactor commit: not needed

## Known Stubs

None. Nullable state fields in modified Android classes are active lifecycle state, not UI stubs or mock data.

## Threat Flags

None. New UDP sender/network surface is covered by plan threats T-04-05 through T-04-08 and mitigated by trusted config gating, HMAC encoding, no secret logging, and lifecycle stop/grace behavior.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04-03 can build the desktop UDP receiver against authenticated Android frames, stream session ids, monotonic mixed snapshot/edge sequence numbers, and 60 Hz snapshot policy.

## Self-Check: PASSED

- Summary file exists.
- `AndroidUdpInputSender.kt` and `InputStreamSequencer.kt` exist.
- Task commits `9d2830f` and `65ef340` exist in git history.
- No tracked file deletions occurred in task commits.

---
*Phase: 04-input-stream-and-haptic-transport*
*Completed: 2026-06-08*
