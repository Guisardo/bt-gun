---
phase: 03-lan-pairing-and-secure-session
plan: 07
subsystem: android-pairing-ui-control-lifecycle
tags: [android, kotlin, pairing, qr, trusted-desktop, foreground-service, control-channel]

requires:
  - phase: 03-06
    provides: Runnable desktop companion pairing/control surface with QR/manual fallback and proof-gated control server.
  - phase: 03-02
    provides: Android QR/manual payload parser, trusted desktop store, and desktop-link dashboard state.
provides:
  - Android QR scan action that routes scanned desktop payloads into HostSessionService-owned control connection.
  - Visible manual fallback fields for host/IP, port, 6-digit code, fingerprint suffix, and session id.
  - Explicit trusted desktop reconnect action using stored fingerprint metadata.
  - HostSessionService-owned DesktopControlClient lifecycle with disconnect-on-stop behavior.
affects: [03-lan-pairing-and-secure-session, android-host, phase-03-plan-08, phase-04-input-stream-and-haptic-transport]

tech-stack:
  added: []
  patterns: [imperative Android platform-view pairing actions, foreground-service-owned control client, fail-closed fingerprint trust]

key-files:
  created:
    - .planning/phases/03-lan-pairing-and-secure-session/03-07-SUMMARY.md
  modified:
    - android-host/app/src/main/java/com/btgun/host/MainActivity.kt
    - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
    - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
    - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "HostSessionService owns DesktopControlClient creation, trusted metadata persistence, and socket shutdown."
  - "QR payloads are parsed in the foreground service so normal pairing reaches DesktopControlClient.connect from scanned endpoint and proof data."
  - "Trusted reconnect requires an explicit Android tap and reuses stored fingerprint metadata instead of silent auto-reconnect."
  - "Manual fallback stays visible; without full fingerprint material it only connects against saved trusted desktop metadata."

patterns-established:
  - "Android pairing UI sends explicit service actions for QR, manual, and trusted desktop connect requests."
  - "Desktop control lifecycle is bounded by foreground service stop and exposes disconnected desktop-link state."

requirements-completed: [TRAN-02, TRAN-03, TRAN-06]

duration: 8min
completed: 2026-06-07T23:34:00Z
---

# Phase 03 Plan 07: Android QR/Manual Pairing Wiring and Service Lifecycle Summary

**Android QR/manual/trusted-desktop actions now route through the foreground service into pinned desktop control connections, while packet streaming and haptic execution remain inactive.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-07T23:26:12Z
- **Completed:** 2026-06-07T23:34:00Z
- **Tasks:** 2 completed
- **Files modified:** 4 code/test files, plus planning metadata

## Accomplishments

- Wired Android `Scan desktop QR` to launch scanner handling, pass QR payload text to `HostSessionService`, parse endpoint/proof data, and call `DesktopControlClient.connect`.
- Added visible manual fallback fields for host/IP, port, 6-digit code, fingerprint suffix, and session id with explicit manual connect action.
- Added explicit `Use trusted desktop` action that appears only when stored metadata exists and connects using the stored fingerprint.
- Bound desktop control socket ownership to `HostSessionService`; service stop closes the socket and moves desktop link to disconnected.
- Added tests for QR-derived control requests, proof headers, trust mismatch fail-closed behavior, socket close, liveness, diagnostics, and Phase 4 packet inactivity.

## Task Commits

1. **Task 1: Wire Android QR scanner, manual fallback, and trusted desktop actions** - `db2d24b` (feat)
2. **Task 2: Bind desktop control lifecycle to foreground HostSessionService** - `68af6ec` (fix)
3. **Auto-fix: Avoid empty trusted reconnect proof** - `498df5e` (fix)

## Verification

- Focused: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DesktopControlClient*' --tests '*DashboardState*' --tests '*PairingPayload*' --tests '*TrustedDesktopStore*'` passed.
- Task 2 focused: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DesktopControlClient*' --tests '*DashboardState*'` passed.
- Full Android closeout: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` passed.
- Boundary grep: `rg -n "UDP input|desktop input parsing|packet loss|jitter|frame-rate|frame rate|visualizer latency|haptic strength|haptic duration|haptic ttl|haptic ack|haptic fail|phone vibration" android-host/app/src` returned no matches.
- Source assertions passed for QR payload flow to `DesktopControlClient.connect`, manual host/port/code fields, explicit trusted desktop action, trust mismatch `TRUST_PROBLEM`, and packet stream remaining inactive.

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` - adds QR scanner launch handling, visible manual fallback fields, explicit trusted desktop action, and service action routing.
- `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` - owns QR/manual/trusted desktop connection actions, trust validation, trusted metadata persistence, desktop control client lifecycle, and stop cleanup.
- `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt` - adds QR/trusted connection request builders, proof headers, and trusted metadata conversion.
- `android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt` - covers QR request construction, proof headers, trust mismatch fail-closed behavior, liveness, diagnostics, and close/disconnect lifecycle.

## Decisions Made

- Keep desktop control client ownership inside `HostSessionService` rather than a hidden Activity-owned socket.
- Store trusted desktop metadata only after a successful QR-derived connection.
- Use explicit user action for trusted reconnect; no silent primary auto-reconnect.
- Keep manual fallback visible and safe by requiring saved trusted fingerprint metadata for manual connection when only a suffix is available.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Avoided unresolved Google Code Scanner dependency during offline verification**
- **Found during:** Task 1
- **Issue:** Adding `com.google.android.gms:play-services-code-scanner:16.1.0` made Gradle fail because Google Maven DNS resolution was unavailable in this environment.
- **Fix:** Kept the scanner path optional through runtime reflection and preserved scanner-unavailable/manual fallback behavior so Android unit tests and the pairing flow compile without downloading a new artifact.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/MainActivity.kt`
- **Verification:** Focused and full Android unit tests passed; scanner-unavailable state shows manual fallback.
- **Committed in:** `db2d24b`

**2. [Rule 1 - Bug] Removed empty trusted reconnect proof header**
- **Found during:** Closeout stub scan
- **Issue:** Trusted reconnect request construction used an empty proof string, which created weak request metadata and tripped stub scanning.
- **Fix:** Derived non-empty proof material from stored fingerprint metadata and the Android nonce for trusted reconnect requests.
- **Files modified:** `android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt`
- **Verification:** Focused `DesktopControlClient` and `DashboardState` tests passed after the fix.
- **Committed in:** `498df5e`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Core Android pairing/service behavior is implemented and tested. Code Scanner remains optional until Google Maven is reachable or the artifact is present in cache; QR payload-to-control-client flow is still wired and scanner-unavailable fallback is visible.

## Issues Encountered

- Sandboxed Gradle could not create local file-lock sockets, so Gradle verification was rerun with approved escalation and normal Gradle behavior.
- Google Maven DNS resolution failed for the approved Code Scanner coordinate, so no new dependency was added in this plan.
- Sandboxed git index writes were blocked for later commits, so `git add` and `git commit` were rerun with approved escalation and normal hooks.

## Auth Gates

None.

## Known Stubs

None. Nullable fields in service/client state are lifecycle state, and `DashboardPlaceholders` is the established dashboard surface name for active desktop link plus inactive packet stream.

## Threat Flags

None. The new trust-boundary surface matches the plan threat model: QR/manual data becomes a service-owned control request, trusted reconnect is explicit, fingerprint mismatch fails closed, and service stop closes the desktop control socket.

## Next Phase Readiness

Plan 03-08 can finalize protocol docs and manual smoke against the Android QR/manual/trusted action surface. Phase 4 still owns UDP input streaming, desktop input parsing, packet-loss/jitter/frame-rate/visualizer latency metrics, haptic payloads, haptic execution, and haptic ack/fail semantics.

## Self-Check: PASSED

- Found summary path: `.planning/phases/03-lan-pairing-and-secure-session/03-07-SUMMARY.md`.
- Found key files: `MainActivity.kt`, `HostSessionService.kt`, `DesktopControlClient.kt`, `DashboardState.kt`, `DesktopControlClientTest.kt`, and `DashboardStateTest.kt`.
- Found task commits `db2d24b`, `68af6ec`, and `498df5e` in git log.
- Focused and full Android unit tests passed.
- Boundary grep returned no forbidden Phase 4 packet, haptic execution, or visualizer metric behavior.

---
*Phase: 03-lan-pairing-and-secure-session*
*Completed: 2026-06-07*
