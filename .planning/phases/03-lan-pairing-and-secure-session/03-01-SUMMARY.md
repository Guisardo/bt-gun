---
phase: 03-lan-pairing-and-secure-session
plan: 01
subsystem: desktop-pairing
tags: [kotlin-jvm, swing, zxing, qr, pairing, keystore]

requires:
  - phase: 02-android-host-live-input
    provides: Android host live input boundary and inactive desktop-link surface
provides:
  - Desktop companion JVM harness with main-style tests
  - Desktop pairing session registry with QR and manual fallback material
  - Local IPv4 endpoint selection and explicit loopback fallback state
  - Desktop identity store with SPKI SHA-256 fingerprint
  - Swing pairing window with visible QR and manual fallback
  - LAN pairing v1 QR/manual payload document
affects: [03-lan-pairing-and-secure-session, desktop-companion, android-host-session]

tech-stack:
  added: [org.jetbrains.kotlin.jvm 2.0.21, com.google.zxing:core 3.5.4, com.google.zxing:javase 3.5.4]
  patterns: [main-style JVM tests, immutable pairing payloads, one-active-session registry, visible manual fallback]

key-files:
  created:
    - desktop-companion/settings.gradle.kts
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
    - docs/protocol/lan-pairing-v1.md
  modified:
    - .gitignore
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Use an isolated Kotlin/JVM desktop-companion Gradle build for first desktop pairing work."
  - "Use desktop-initiated one-active-session pairing with QR as normal path and visible manual fallback as secondary path."
  - "Persist desktop key material in a Java KeyStore-backed local identity store and expose only SPKI SHA-256 fingerprint to pairing payloads."

patterns-established:
  - "Desktop tests follow existing Android main-style test registration instead of adding a new unit-test framework."
  - "PairingPayloadV1 serializes `btgun://pair` query fields with explicit validation."
  - "SecretRedactor removes QR secrets, manual codes, HMAC proof text, and private-key text before diagnostics/logging."

requirements-completed: [TRAN-01]

duration: 9min
completed: 2026-06-07
---

# Phase 03 Plan 01: Desktop Pairing Session Summary

**Desktop companion pairing harness with short-lived QR payloads, visible 6-digit manual fallback, SPKI fingerprint identity, and Swing pairing surface.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-06-07T18:36:54Z
- **Completed:** 2026-06-07T18:45:30Z
- **Tasks:** 3 completed
- **Files modified:** 17

## Accomplishments

- Created `desktop-companion` as an isolated Kotlin/JVM Gradle build with main-style test execution.
- Added RED pairing tests first, committed before production code, then implemented GREEN desktop pairing code.
- Implemented one active desktop pairing session with 2-5 minute TTL, per-session QR secret, desktop nonce, session id, selected endpoint, 6-digit manual code, and fingerprint suffix.
- Added ZXing QR rendering and a Swing `BT Gun Desktop` window with QR, countdown, endpoint, code, and fingerprint suffix visible.
- Documented only LAN pairing QR/manual fields in `docs/protocol/lan-pairing-v1.md` and kept later input/feedback contracts out.

## Task Commits

1. **Task 1: Verify external package legitimacy before dependency installation** - no commit; user approved blocking-human package gate in prompt.
2. **Task 2: RED - create desktop JVM harness and pairing session tests** - `a244616` (test)
3. **Task 3: GREEN - implement desktop pairing session and QR/manual surface** - `2f621f4` (feat)

## Verification

- RED: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingSession*'` failed on missing planned symbols before GREEN.
- GREEN focused: same command passed after implementation.
- GREEN full desktop: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test` passed.
- Boundary: `rg -n "button bitmask|input frame schema|packet loss|jitter|haptic strength|haptic duration|haptic ack|haptic fail" docs/protocol/lan-pairing-v1.md` returned no matches.

## Files Created/Modified

- `.gitignore` - ignores desktop Gradle build outputs.
- `desktop-companion/settings.gradle.kts` - isolated desktop Gradle settings with Maven Central.
- `desktop-companion/build.gradle.kts` - Kotlin/JVM 17 app harness, ZXing deps, main-style test registration.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt` - launches desktop pairing window.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/LocalEndpointSelector.kt` - chooses active non-loopback IPv4 with explicit loopback fallback.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingPayload.kt` - validates and serializes QR/manual payload fields.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt` - creates, replaces, rejects, and expires active pairing sessions.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/QrCodeRenderer.kt` - renders 240px+ ZXing QR images.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/DesktopIdentityStore.kt` - creates/loads Java KeyStore-backed desktop identity and SPKI fingerprint.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt` - redacts one-time secrets and proof/private-key text.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` - Swing pairing UI with visible QR and manual fallback.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/pairing/PairingSessionRegistryTest.kt` - main-style tests for TTL, QR URI, manual fallback, identity persistence, QR rendering, and redaction.
- `docs/protocol/lan-pairing-v1.md` - Phase 3 Plan 01 QR/manual payload contract.

## Decisions Made

- Desktop companion begins as a JVM/Swing surface to stay portable across macOS Apple Silicon and Windows 11 x64 before driver-specific work.
- Manual fallback stays visible in the desktop window but secondary to QR, matching D-01 and D-02.
- `FileDesktopIdentityStore` uses Java KeyStore storage for local key material; pairing payloads and UI expose only fingerprint data.
- `requirements-completed` marks `TRAN-01` only. `TRAN-02`, `TRAN-03`, and `TRAN-06` remain pending because later Phase 03 plans own Android QR/manual pairing, authenticated proof/replay defenses, and reliable control channel.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ignored desktop Gradle build output**
- **Found during:** Task 2
- **Issue:** Running RED generated `desktop-companion/build/` as untracked output.
- **Fix:** Added desktop companion Gradle output paths to `.gitignore`.
- **Files modified:** `.gitignore`
- **Verification:** `git status --short` no longer listed generated build outputs.
- **Committed in:** `a244616`

**2. [Rule 1 - Bug] Fixed Java KeyStore secret-entry algorithm**
- **Found during:** Task 3
- **Issue:** A focused identity-store test showed PKCS12 rejected arbitrary secret-key algorithm names for encoded key material.
- **Fix:** Switched the local Java KeyStore implementation to JCEKS with `RAW` secret entries and verified persistent fingerprint reload.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/security/DesktopIdentityStore.kt`, `desktop-companion/src/test/kotlin/com/btgun/desktop/pairing/PairingSessionRegistryTest.kt`
- **Verification:** Focused and full desktop tests passed.
- **Committed in:** `2f621f4`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes were required for clean execution and desktop identity correctness. No Phase 4 scope was added.

## Issues Encountered

- `gsd-tools` was not available on PATH, so state/roadmap/requirements updates were applied directly.
- Initial sandboxed Gradle and git operations were blocked by local file-lock/socket restrictions; reran required commands with approved escalation and normal hooks.

## User Setup Required

None - package legitimacy gate was already approved by the user in the execution prompt. Approved coordinates were:

- `io.ktor:ktor-server-core-jvm:3.5.0`
- `io.ktor:ktor-server-netty-jvm:3.5.0`
- `io.ktor:ktor-server-websockets-jvm:3.5.0`
- `io.ktor:ktor-server-content-negotiation-jvm:3.5.0`
- `io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.0`
- `com.squareup.okhttp3:okhttp:5.3.2`
- `com.google.android.gms:play-services-code-scanner:16.1.0`
- `com.google.zxing:core:3.5.4`
- `com.google.zxing:javase:3.5.4`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0`

## Auth Gates

None.

## Known Stubs

None.

## TDD Gate Compliance

- RED commit exists before GREEN: `a244616`
- GREEN commit exists after RED: `2f621f4`
- Refactor commit: none needed.

## Next Phase Readiness

Plan 03-02 can consume `PairingPayloadV1` field names and `docs/protocol/lan-pairing-v1.md` to build Android QR/manual parsing and trusted desktop storage. Later plans must still implement proof verification, replay/rate limits, WSS control, heartbeat, diagnostics, and Android service/UI wiring.

## Self-Check: PASSED

- Found summary, desktop harness, pairing registry, pairing window, and protocol doc files.
- Found task commits `a244616` and `2f621f4` in git log.
- Boundary grep returned no matches for forbidden Phase 4 input/feedback terms.

---
*Phase: 03-lan-pairing-and-secure-session*
*Completed: 2026-06-07*
