---
phase: 08-desktop-profiles-and-mapping
plan: 06
subsystem: desktop-profile-metadata
tags: [desktop, profiles, udp, mapped-stream, swing, tdd]

requires:
  - phase: 08-04
    provides: Android active profile metadata and mapped LAN output
provides:
  - Desktop acceptance of authenticated Android profile metadata
  - Desktop mapped UDP decode with mapped aim and raw-debug flags
  - Read-only desktop diagnostics rows for Android profile and mapped stream state
affects: [phase-08, phase-09, desktop-companion, windows-fallback]

tech-stack:
  added: []
  patterns:
    - Read-only Android-owned profile metadata callbacks
    - Mapped UDP product stream flags
    - Swing diagnostics rows without desktop profile controls

key-files:
  created:
    - .planning/phases/08-desktop-profiles-and-mapping/08-06-SUMMARY.md
  modified:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt

key-decisions:
  - Desktop consumes authenticated Android profile metadata as read-only state and no longer sends default profile authority.
  - Desktop semantic state uses Android-mapped product aim when mapped stream flag is present.
  - Unmapped legacy UDP frames remain decodable but are marked incompatible for product publishing.

patterns-established:
  - Profile metadata parsing requires id, displayName, revision, and source=android before invoking desktop callbacks.
  - UDP offset 6 is streamFlags with mapped product and raw-debug bits, mirroring Android.
  - PairingWindow chains UDP callbacks before backend runtime attach so diagnostics and backends both observe mapped input.

requirements-completed: [PROF-05, PROF-06]

duration: 16m
completed: 2026-06-12
---

# Phase 08 Plan 06: Desktop Read-Only Profile Metadata Summary

**Desktop companion now accepts Android-owned profile metadata, consumes mapped UDP product aim, and shows read-only mapped-stream diagnostics.**

## Performance

- **Duration:** 16m
- **Started:** 2026-06-12T17:24:07Z
- **Completed:** 2026-06-12T17:40:12Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added authenticated `PROFILE_METADATA` parsing and callback for Android-owned profile id/name/revision/source/raw-debug state.
- Removed desktop default profile metadata emission from initial control-channel metadata.
- Mirrored Android UDP stream flags and mapped product aim fields in desktop decode and received-input state.
- Updated `UdpControllerStateAdapter` to use mapped product aim and mark unmapped legacy frames incompatible for product publishing.
- Added `PairingWindow` rows for `Active Android profile`, `Profile source`, `Mapped stream`, and `Last profile update` without adding profile edit/raw-request controls.

## Task Commits

1. **Task 1 RED: Android profile metadata tests** - `ddb3e89` (test)
2. **Task 1 GREEN: Android metadata acceptance** - `5a8902a` (feat)
3. **Task 2 RED: mapped UDP product stream tests** - `c724393` (test)
4. **Task 2 GREEN: mapped UDP product stream decode** - `f0337a8` (feat)
5. **Task 3 RED: read-only desktop profile rows tests** - `9ae1327` (test)
6. **Task 3 GREEN: read-only desktop profile diagnostics** - `fecc5f2` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ProfileMetadata.kt` - Added Android source/raw-debug fields and strict JSON body parser.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - Added profile metadata callback and removed desktop default profile metadata envelope.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt` - Added stream flags, mapped product aim, raw-debug flag, and debug summary fields.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` - Added mapped aim and mapped/raw-debug stream status.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` - Uses mapped aim and treats unmapped input as incompatible/stale.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Shows read-only Android profile and mapped stream diagnostics.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` - Updated smoke fixture bytes to mapped Phase 8 frames.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt` - TDD coverage for metadata acceptance and default-authority removal.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt` - TDD coverage for stream flags, mapped aim, raw-debug flag, and malformed flags.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt` - TDD coverage for mapped aim and unmapped legacy incompatibility.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` - TDD coverage for read-only diagnostics and forbidden labels.
- `.planning/phases/08-desktop-profiles-and-mapping/08-06-SUMMARY.md` - Plan closeout.

## Decisions Made

- Accepted only `source=android` profile metadata for desktop UI state, keeping desktop profile ownership absent.
- Kept legacy zero-flag UDP frames decodable but product-incompatible instead of rejecting at HMAC/decode level.
- Used the existing callback-chain pattern so `PairingWindow`, Windows runtime, and macOS runtime can all observe UDP input.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated backend smoke fixtures for mapped UDP frames**
- **Found during:** Task 2 GREEN
- **Issue:** Gradle custom test execution runs all desktop test mains; the backend smoke runner still replayed legacy zero-flag fixtures, which became product-incompatible after mapped-stream enforcement.
- **Fix:** Updated `BackendSmokeRunner` golden fixture bytes to mapped Phase 8 frames.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt`
- **Verification:** Final combined desktop test command passed.
- **Committed in:** `f0337a8`

---

**Total deviations:** 1 auto-fixed (Rule 3 blocking)
**Impact on plan:** No scope expansion. Fix keeps existing smoke validation aligned with mapped product stream semantics.

## Issues Encountered

- Gradle cannot create its file-lock socket inside the managed sandbox. Required Gradle commands were rerun with approved escalation and passed.
- A direct diagnostic smoke run briefly created an unintended root `build/` directory; it was removed before closeout. The unrelated untracked `.codex/` directory was preserved.

## TDD Gate Compliance

- Task 1 RED before GREEN: `ddb3e89` -> `5a8902a`.
- Task 2 RED before GREEN: `c724393` -> `f0337a8`.
- Task 3 RED before GREEN: `9ae1327` -> `fecc5f2`.
- No refactor commits were needed.

## Verification

- Task 1 RED failed as expected on missing `source`, `rawDebugEnabled`, and `onProfileMetadataReceived`.
- Task 1 GREEN passed: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ControlChannel*' --no-daemon --console=plain`.
- Task 2 RED failed as expected on missing mapped stream flags, mapped aim fields, and received-input status.
- Task 2 GREEN passed: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*UdpInputFrameCodec*' --tests '*UdpControllerStateAdapter*' --no-daemon --console=plain`.
- Task 3 RED failed as expected on missing `PairingWindow.profileDiagnosticsHtml`.
- Task 3 GREEN passed: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingWindow*' --no-daemon --console=plain`.
- Final required command passed: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ControlChannel*' --tests '*UdpInputFrameCodec*' --tests '*UdpControllerStateAdapter*' --tests '*PairingWindow*' --no-daemon --console=plain`.
- Forbidden-label guard passed: `! rg -n "Edit desktop profile|Desktop profile editor|Request raw stream|Save profile|Duplicate profile|Hold-to-recenter" desktop-companion/src/main/kotlin desktop-companion/src/test/kotlin`.

## Known Stubs

None. Stub-pattern scan found default/null callback and diagnostic values, but no product-blocking stubs or unwired profile UI data.

## Threat Flags

None. New metadata and UDP trust-boundary handling was covered by T-08-10 and T-08-11 in the plan threat model.

## Self-Check: PASSED

- Summary file exists.
- Key modified files exist.
- Task commits found: `ddb3e89`, `5a8902a`, `c724393`, `f0337a8`, `9ae1327`, `fecc5f2`.

## User Setup Required

None.

## Next Phase Readiness

Phase 08 can execute Plan 07 or hand off to Phase 09 with desktop read-only profile diagnostics and Windows fallback consuming Android-mapped product state.

---
*Phase: 08-desktop-profiles-and-mapping*
*Completed: 2026-06-12*
