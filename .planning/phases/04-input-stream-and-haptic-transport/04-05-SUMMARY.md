---
phase: "04-input-stream-and-haptic-transport"
plan: "05"
subsystem: "transport"
tags: ["android", "desktop", "kotlin", "udp", "lifecycle", "haptics", "tdd"]

requires:
  - phase: "04-input-stream-and-haptic-transport"
    provides: "Plans 04-01 through 04-04 established trusted pairing, UDP input frames, receiver replay guard, and reliable phone haptic command/result transport."
provides:
  - "Input stream lifecycle tests for disconnect grace, stale timeout, reconnect, and old-frame rejection."
  - "Android UDP sender and desktop UDP receiver active/grace/stale/stopped lifecycle state."
  - "Session-change haptic cancellation while short reliable-control disconnect leaves an active phone pulse alone."
  - "Packet stream state labels in Android dashboard and desktop pairing diagnostics."
  - "Final Phase 4 protocol recovery rules and manual smoke guide."
affects: ["phase-04", "phase-05", "android-host", "desktop-companion", "protocol-docs"]

tech-stack:
  added: []
  patterns:
    - "Mirrored InputStreamLifecycleState enum labels across Android and desktop."
    - "Fresh input_stream_config resets lifecycle and replay state after reconnect."
    - "Session changes cancel active phone haptic; short disconnects do not."

key-files:
  created:
    - ".planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md"
    - "android-host/app/src/main/java/com/btgun/host/transport/InputStreamLifecycleState.kt"
    - "android-host/app/src/test/java/com/btgun/host/transport/InputStreamLifecycleTest.kt"
    - "desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamLifecycleState.kt"
    - "desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputStreamLifecycleTest.kt"
    - "desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt"
  modified:
    - "android-host/app/src/main/java/com/btgun/host/HostSessionService.kt"
    - "android-host/app/src/main/java/com/btgun/host/haptics/DesktopHapticCommand.kt"
    - "android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt"
    - "android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt"
    - "desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt"
    - "desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt"
    - "desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt"
    - "docs/protocol/lan-pairing-v1.md"

key-decisions:
  - "Use active/grace/stale/stopped as the shared packet stream lifecycle labels."
  - "Allow unchanged-session UDP only during controlDisconnectGraceMs; require a fresh stream config after reconnect or session change."
  - "Cancel phone haptic on trusted session change, not on short reliable-control disconnect."

patterns-established:
  - "Lifecycle state is local endpoint state and must not become a visualizer latency or packet-loss dashboard."
  - "Timeout cleanup clears buttons/pressed/stick state while preserving last raw aim/motion as stale."

requirements-completed: ["ANDR-07", "TRAN-09", "DESK-01", "PERF-03"]

duration: "15m 32s"
completed: "2026-06-08"
---

# Phase 04 Plan 05: Input Stream Recovery Summary

**Disconnect-safe UDP input and phone haptic lifecycle recovery with stale-state surfacing and physical smoke guidance**

## Performance

- **Duration:** 15m 32s
- **Started:** 2026-06-08T20:10:10Z
- **Completed:** 2026-06-08T20:25:42Z
- **Tasks:** 2
- **Files modified:** 17

## Accomplishments

- Added RED lifecycle tests across Android sender/haptics/dashboard and desktop receiver/pairing diagnostics.
- Implemented shared `active`, `grace`, `stale`, and `stopped` lifecycle labels for packet stream state.
- Enforced disconnect grace expiry, reconnect reset, and old UDP frame rejection before stale input can apply.
- Preserved last raw aim/motion on stream timeout while clearing active controls and stick state.
- Documented Phase 4 recovery semantics and added a manual smoke guide for Android/desktop LAN and phone haptic checks.

## Task Commits

1. **Task 1: RED - add disconnect, replay, stale input, and haptic lifecycle tests** - `92d592d` (test)
2. **Task 2: GREEN - implement recovery behavior, final docs, and smoke guide** - `0bc1e99` (feat)

## TDD Gate Compliance

- RED gate: `92d592d` added failing lifecycle tests.
- GREEN gate: `0bc1e99` implemented the lifecycle behavior and passed focused plus full Android/desktop test suites.
- Refactor gate: not needed; no behavior-preserving cleanup commit was required after GREEN.

## Files Created/Modified

- `.planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md` - Physical Android/desktop smoke guide for stream recovery and phone haptics.
- `android-host/app/src/main/java/com/btgun/host/transport/InputStreamLifecycleState.kt` - Android packet stream lifecycle labels.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamLifecycleState.kt` - Desktop packet stream lifecycle labels.
- `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` - Disconnect grace, reconnect reset, session-change stop, and stale lifecycle state.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - Grace expiry rejection, stream timeout stale state, and fresh config reconnect.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - Packet stream state propagation and session-change haptic cancellation.
- `android-host/app/src/main/java/com/btgun/host/haptics/DesktopHapticCommand.kt` - Explicit short-disconnect vs session-change haptic lifecycle behavior.
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - Android dashboard packet stream status labels.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Desktop diagnostics packet stream status labels.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt` - `CONTROL_GRACE_EXPIRED` rejection reason.
- `docs/protocol/lan-pairing-v1.md` - Final Phase 4 input stream lifecycle, haptic result, timeout, reconnect, and redaction rules.

## Decisions Made

- Lifecycle labels stay concise and endpoint-local so later visualizer metrics and profile mapping do not leak into Phase 4.
- Reconnect uses fresh authenticated control and fresh stream config; stale UDP from an old stream is rejected before state apply.
- Short reliable-control disconnect does not cancel a phone pulse; trusted session change cancels active haptic and returns `cancelled`.

## Deviations from Plan

### Auto-fixed Issues

None - planned behavior was implemented directly.

### Verification Scope Adjustment

**1. Boundary grep scoped to new implementation surface**
- **Found during:** Task 2 verification
- **Issue:** The exact plan grep flags pre-existing Phase 3 pairing protocol terms such as `qr_secret`, `manual code`, and `HMAC key`, plus existing protocol boundary text for later phases.
- **Fix:** Kept the exact grep evidence, then verified the new Phase 4 implementation surface and added diff lines do not introduce physical gun motor, virtual HID, profile mapper, direct desktop Bluetooth, packet-loss graph, latency dashboard, game preset, or secret-bearing docs.
- **Files modified:** None
- **Verification:** Scoped implementation grep passed; added-line boundary grep returned no matches.
- **Committed in:** `0bc1e99`

**Total deviations:** 0 auto-fixed; 1 verification-scope adjustment.
**Impact on plan:** No product scope change. Existing security protocol terminology remains intact.

## Issues Encountered

- Sandboxed Gradle runs fail with `java.net.SocketException: Operation not permitted` when Gradle opens its file-lock socket. The documented workaround was used with `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home` and escalated execution.
- Android test helper names were adjusted to avoid package-level class redeclaration with existing sender tests.
- The grace TTL boundary test was aligned to the existing strict `>` expiry rule.

## Verification

- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*InputStreamLifecycle*' --tests '*DashboardState*' --tests '*DesktopHapticCommand*'` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*InputStreamLifecycle*' --tests '*InputReplayGuard*' --tests '*PairingWindow*'` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test` - passed.
- `! rg -n "physical gun motor|fff5|RUMBLE2|VirtualHID|HIDVirtualDevice|VHF|profile mapper|profile mapping|packet loss graph|latency dashboard|game preset|direct desktop bluetooth|qr_secret|manual code|HMAC key" android-host/app/src/main/java desktop-companion/src/main/kotlin docs/protocol/lan-pairing-v1.md .planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md` - failed on pre-existing Phase 3 pairing and protocol boundary terminology.
- `! rg -n "physical gun motor|fff5|RUMBLE2|VirtualHID|HIDVirtualDevice|VHF|profile mapper|profile mapping|packet loss graph|latency dashboard|game preset|direct desktop bluetooth" android-host/app/src/main/java/com/btgun/host/transport android-host/app/src/main/java/com/btgun/host/haptics android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt desktop-companion/src/main/kotlin/com/btgun/desktop/transport desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt .planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md` - passed.
- `git diff -U0 92d592d -- android-host/app/src/main/java desktop-companion/src/main/kotlin docs/protocol/lan-pairing-v1.md .planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md | rg -n "^\\+.*(physical gun motor|fff5|RUMBLE2|VirtualHID|HIDVirtualDevice|VHF|profile mapper|profile mapping|packet loss graph|latency dashboard|game preset|direct desktop bluetooth|qr_secret|manual code|HMAC key)"` - returned no matches.

## Known Stubs

None.

## Threat Flags

None. New trust-boundary behavior matches plan threats T-04-19 through T-04-23.

## User Setup Required

Follow `.planning/phases/04-input-stream-and-haptic-transport/04-MANUAL-SMOKE.md` on the physical Android phone and desktop after automated tests. No new external service setup is required.

## Next Phase Readiness

Phase 5 can consume lifecycle-safe receiver state, raw input, and haptic command/result behavior without trusting stale UDP frames after reconnect. Later phases still own virtual HID, desktop profile mapping, visualizer metrics, game presets, and physical gun motor protocol work.

## Self-Check: PASSED

- Found summary file, manual smoke guide, Android lifecycle enum, and desktop lifecycle enum.
- Found task commits `92d592d` and `0bc1e99`.
- Stub scan found only nullable state fields, existing dashboard placeholder model names, and control-flow defaults; no blocking stubs were introduced.
- Threat scan found only planned transport/auth surfaces covered by T-04-19 through T-04-23.

---
*Phase: 04-input-stream-and-haptic-transport*
*Completed: 2026-06-08*
