---
phase: 07-macos-virtual-joystick-path
plan: 04
subsystem: macos-virtual-hid
tags: [kotlin, gradle, tdd, macos-corehid, runtime-wiring, haptics, swing]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: MacosHidReportPacker, MacosOutputReportMapper, MacosVirtualControllerBackend, and helper line protocol from Plans 02 and 03
provides:
  - TDD-covered `MacosBackendRuntime` callback wiring from authenticated `ControlServer` UDP input to macOS backend publish
  - Helper-origin macOS output report routing through authenticated `ControlServer.sendHapticCommand`
  - macOS backend launch gate using explicit `btgun.macos.hid.enabled` and `btgun.macos.hid.helper.path`
  - Swing UI macOS backend diagnostics with lifecycle, publish, stale, haptic, routed count, and helper status metadata
affects: [phase-07, macos-backend-runtime, macos-launch-gate, desktop-pairing-window, desk-03, desk-06]

tech-stack:
  added: []
  patterns: [windows-runtime-mirror, callback-chain-runtime, authenticated-output-haptic-routing, fail-closed-helper-launch]

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntime.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt
    - .planning/phases/07-macos-virtual-joystick-path/07-04-SUMMARY.md
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt

key-decisions:
  - "MacosBackendRuntime mirrors WindowsBackendRuntime by attaching only to the existing trusted ControlServer UDP callback and preserving prior callbacks."
  - "macOS helper-origin output reports route through ControlServer.sendHapticCommand; no haptic bypass or direct Android/session ownership was added."
  - "macOS backend launch is disabled by default and fails closed unless btgun.macos.hid.helper.path is explicit."

patterns-established:
  - "macOS runtime diagnostics expose helper status metadata but no raw report bytes, pairing proof material, stream keys, private keys, or screenshots."
  - "PairingWindow attaches Windows then macOS runtimes and closes macOS first so callback restoration unwinds in reverse order."

requirements-completed: []

duration: 8 min
completed: 2026-06-10
---

# Phase 07 Plan 04: macOS Runtime Wiring Summary

**Live trusted desktop input now reaches the macOS backend runtime, and helper-origin output reports route through the authenticated phone-haptic path.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-10T16:42:23Z
- **Completed:** 2026-06-10T16:49:54Z
- **Tasks:** 3
- **Files modified:** 7 production/test/config files plus planning metadata

## Accomplishments

- Added RED runtime tests covering callback preservation, trusted UDP publish, stale report behavior, authenticated output haptic routing, no-session handling, diagnostics, and boundary ownership.
- Implemented `MacosBackendRuntime`, `MacosBackendRuntimeConfig`, and `MacosBackendRuntimeDiagnostics` with helper status metadata.
- Wired `btgun.macos.hid.enabled=true` plus `btgun.macos.hid.helper.path=/path/to/BtGunMacosHidHelper` into desktop launch, fail-closed on missing helper path.
- Added PairingWindow macOS backend diagnostics without adding new haptic controls or weakening the existing phone-haptic path.

## Task Commits

1. **Task 1: RED runtime tests for live input and output haptic routing** - `867af78` (test)
2. **Task 2: GREEN MacosBackendRuntime implementation** - `249c863` (feat)
3. **Task 3: macOS backend launch gate and UI diagnostics** - `ebad322` (feat)
4. **Plan metadata:** this commit (docs)

## Files Created/Modified

- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt` - Main-style tests for macOS runtime callback chaining, publish, stale reports, output haptics, no-session behavior, diagnostics, and boundary scans.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntime.kt` - Runtime attach/close lifecycle, trusted UDP-to-backend publish, helper-output drain, authenticated haptic send, and diagnostics.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt` - Adds `helperStatus()` so runtime diagnostics can display sanitized helper metadata.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` - Adds macOS backend system-property launch gate beside the Windows gate.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Accepts optional macOS runtime, attaches/closes it, and displays macOS backend status.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` - Tests macOS backend diagnostic formatting and metadata-only content.
- `desktop-companion/build.gradle.kts` - Registers `MacosBackendRuntimeTestKt`.

## Decisions Made

- Followed the Windows runtime shape for macOS rather than adding a new networking/auth owner.
- Kept native/helper responsibility limited to HID bytes and helper status; Kotlin keeps UDP validation, semantic mapping, session auth, and haptic routing.
- Did not mark DESK-03 or DESK-06 complete because OS-visible macOS input and OS-origin output proof remain later Phase 7 work.

## Verification

- PASS RED: pinned offline Gradle failed only on unresolved `MacosBackendRuntime` and `MacosBackendRuntimeConfig` symbols after the RED test commit.
- PASS GREEN/final: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- PASS launch scan: `rg -n "btgun\\.macos\\.hid\\.enabled|btgun\\.macos\\.hid\\.helper\\.path|macOS backend|MacosBackendRuntime" desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt`
- PASS runtime boundary scan: `rg -n "raw packet|proof|stream key|HMAC key|private key|qrSecret|hmacSha256KeyBase64Url|Bluetooth address|screenshot|DatagramSocket|ServerSocket|java\\.net\\.Socket|io\\.ktor|android\\." desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntime.kt desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` returned no matches.
- PASS TDD gate: `git log --oneline --all --grep="test(07-04)" --grep="feat(07-04)"` shows RED before GREEN.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test Bug] Removed RED-phase noise from test scaffolding**
- **Found during:** Task 1 RED verification
- **Issue:** Initial RED test included a missing local fingerprint constant and an inferred diagnostics callback type that caused extra compile errors beyond the planned missing runtime symbols.
- **Fix:** Added a local test fingerprint constant and used a typed `(Any) -> Unit` callback recorder so RED failed only on `MacosBackendRuntime` and `MacosBackendRuntimeConfig`.
- **Files modified:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt`
- **Verification:** RED rerun failed only on unresolved `MacosBackendRuntime` and `MacosBackendRuntimeConfig`.
- **Committed in:** `867af78`

**2. [Rule 2 - Missing Critical] Exposed helper status for runtime diagnostics**
- **Found during:** Task 2 implementation
- **Issue:** Plan-required `helperStatus` diagnostics could not be populated by `MacosBackendRuntime` without a sanitized backend status accessor.
- **Fix:** Added `MacosVirtualControllerBackend.helperStatus()` to read the helper status already used by capabilities.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosVirtualControllerBackend.kt`
- **Verification:** Runtime and UI tests assert helper status appears as metadata-only diagnostics.
- **Committed in:** `249c863`

**3. [Rule 3 - Blocking] Ran Gradle verification outside sandbox**
- **Found during:** Task 1 RED and subsequent verification
- **Issue:** Sandbox blocks Gradle file-lock socket creation with `java.net.SocketException: Operation not permitted`.
- **Fix:** Used approved unsandboxed pinned Gradle command with `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle`.
- **Files modified:** None
- **Verification:** RED failed as expected; GREEN/final Gradle tests passed.
- **Committed in:** N/A, verification environment only.

---

**Total deviations:** 3 auto-fixed (Rule 1: 1, Rule 2: 1, Rule 3: 1)  
**Impact on plan:** Scope stayed inside planned macOS runtime, launch, diagnostics, and tests. No SIP, developer-mode, system-extension, DriverKit activation, or OS security-state command was run.

## Issues Encountered

- Gradle still needs unsandboxed execution in this environment because sandboxed file-lock socket creation fails before the build starts.
- No authentication gates occurred.

## Known Stubs

None. Nullable diagnostics fields and disabled backend launch diagnostics are lifecycle/configuration state, not user-visible stubs.

## Threat Flags

None. New launch flag, helper execution, helper-output haptic routing, and diagnostics surfaces were already covered by the plan threat model and mitigated by explicit helper path, authenticated `ControlServer.sendHapticCommand`, and metadata-only UI/runtime diagnostics.

## User Setup Required

None - no external service configuration required. To exercise the macOS runtime later, launch desktop companion with `btgun.macos.hid.enabled=true` and an explicit `btgun.macos.hid.helper.path`; OS-visible and OS-origin proof remain later Phase 7 work.

## Next Phase Readiness

Plan 07-05 can use `MacosBackendRuntime` to drive the CoreHID helper from the live authenticated Android stream and prove macOS visibility/output behavior. DESK-03 and DESK-06 remain pending until that live OS proof succeeds or the later approved DriverKit fallback path replaces it.

## Self-Check: PASSED

- Found `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntime.kt` on disk.
- Found `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/macos/MacosBackendRuntimeTest.kt` on disk.
- Found task commits `867af78`, `249c863`, and `ebad322` in git history.
- Confirmed RED and GREEN TDD gate commits exist in order.
- Confirmed pinned offline desktop Gradle tests pass.
- Confirmed macOS launch/system-property scan passes.
- Confirmed runtime boundary scan does not find raw packet, proof, key, socket, Ktor, or Android ownership terms in modified runtime/launch/UI files.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
