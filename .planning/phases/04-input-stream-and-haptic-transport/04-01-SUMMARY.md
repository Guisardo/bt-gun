---
phase: 04-input-stream-and-haptic-transport
plan: 01
subsystem: transport
tags: [kotlin, android, desktop, udp, hmac, protocol, tdd]

requires:
  - phase: 03-lan-pairing-and-secure-session
    provides: trusted LAN pairing, WSS control envelope, session identity, heartbeat boundaries
provides:
  - Fixed 120-byte binary UDP input frame contract with snapshot and edge frame types
  - Mirrored Android and desktop Kotlin codecs with HMAC-SHA256 authentication
  - Golden snapshot and edge fixtures shared by both module tests
  - Input stream config models for trusted UDP stream parameters
  - Local Gradle startup workaround for Phase 04 validation
affects: [input-stream, haptic-transport, desktop-receiver, android-sender, protocol-docs]

tech-stack:
  added: []
  patterns:
    - Mirrored Kotlin wire codecs guarded by shared golden hex fixtures
    - Fixed big-endian ByteBuffer frame layout with full HMAC-SHA256 tag
    - Safe debug decoder summaries that omit stream auth material

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/transport/InputStreamConfig.kt
    - android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt
    - android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamConfig.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt
    - docs/protocol/input-stream-v1-fixtures.md
  modified:
    - .planning/phases/04-input-stream-and-haptic-transport/04-VALIDATION.md
    - android-host/app/build.gradle.kts
    - desktop-companion/build.gradle.kts
    - docs/protocol/lan-pairing-v1.md

key-decisions:
  - "Use mirrored Android and desktop codecs with golden fixtures rather than a shared module for Plan 04-01."
  - "Use fixed 120-byte big-endian UDP frames authenticated with full HMAC-SHA256 tags."
  - "Plain Gradle startup remains blocked locally; Phase 04 validation uses Homebrew JDK 17 plus GRADLE_USER_HOME under /private/tmp."

patterns-established:
  - "Golden fixture first: both modules encode/decode the same snapshot and edge hex."
  - "Fail closed before apply: length, magic, version, type, stream id, and HMAC validation produce typed rejection results."
  - "Debug summaries expose only non-secret frame fields and rejection reasons."

requirements-completed:
  - TRAN-05
  - DESK-01
  - PERF-03

duration: 7 min
completed: 2026-06-08
---

# Phase 04 Plan 01: Wire Contract Foundation Summary

**Authenticated binary UDP frame contract with mirrored Android/desktop codecs, shared golden fixtures, and documented Gradle validation workaround**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-08T18:13:24Z
- **Completed:** 2026-06-08T18:21:23Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Added shared golden snapshot/edge frame bytes and RED tests that failed on missing planned codec/config symbols.
- Implemented Android and desktop `InputStreamConfig`, `UdpInputFrame`, `UdpInputFrameType`, authenticated decode results, typed rejection reasons, and safe debug summaries.
- Updated protocol docs with `input_stream_config`, UDP binary schema, snapshot/edge recovery behavior, raw motion boundary, and reject-before-apply rules.
- Recorded local Gradle native-platform failure plus the working Phase 04 validation environment.

## Task Commits

1. **Task 1: RED codec fixtures and tests** - `3926b74` (test)
2. **Task 2: GREEN mirrored codecs and docs** - `f220fbe` (feat)

## Files Created/Modified

- `docs/protocol/input-stream-v1-fixtures.md` - Golden frame hex, layout offsets, fixture values, and boundary rules.
- `docs/protocol/lan-pairing-v1.md` - Trusted stream config and UDP frame schema documentation.
- `android-host/app/src/main/java/com/btgun/host/transport/InputStreamConfig.kt` - Android stream config validation and auth secret decoding.
- `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt` - Android binary frame encoder, authenticator/parser, typed rejections, debug summaries.
- `android-host/app/src/test/java/com/btgun/host/transport/UdpInputFrameCodecTest.kt` - Android fixture, malformed frame, redaction, and boundary tests.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputStreamConfig.kt` - Desktop mirror config validation and auth secret decoding.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt` - Desktop mirror binary frame encoder, authenticator/parser, typed rejections, debug summaries.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputFrameCodecTest.kt` - Desktop mirror fixture, malformed frame, redaction, and boundary tests.
- `android-host/app/build.gradle.kts` - Registers Android codec main-style test.
- `desktop-companion/build.gradle.kts` - Registers desktop codec main-style test.
- `.planning/phases/04-input-stream-and-haptic-transport/04-VALIDATION.md` - Records Gradle startup status and workaround.

## Decisions Made

- Mirrored codecs stay in existing Android and desktop modules because the repo already mirrors protocol code and tests can enforce wire compatibility.
- HMAC-only UDP is enough for this plan because Phase 04 requires authenticate/decrypt, no new dependency is allowed, and research selected no AEAD nonce complexity for MVP.
- Debug decode returns safe summaries only; it never includes stream auth material, pairing one-time material, fallback digits, proof values, or tag input material.

## TDD Gate Compliance

- RED commit exists: `3926b74`
- GREEN commit exists after RED: `f220fbe`
- Refactor commit: not needed

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Plain `gradle --version` still fails with `Could not initialize native services` / `Failed to load native library 'libnative-platform.dylib' for Mac OS X aarch64`.
- The documented workaround succeeds: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle --version`.
- Sandboxed Gradle test startup hit `java.net.SocketException: Operation not permitted`; targeted test verification passed after approved unsandboxed Gradle runs.

## Verification

- `gradle --version` - blocked locally as documented in `04-VALIDATION.md`.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle --version` - passed, Gradle 9.5.1.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*UdpInputFrameCodec*'` - passed.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*UdpInputFrameCodec*'` - passed.
- `! rg -n "PreviewAim|aimX|aimY|profile mapping|profile_mapper|json udp|qr_secret|manual code|HMAC key" android-host/app/src/main/java/com/btgun/host/transport desktop-companion/src/main/kotlin/com/btgun/desktop/transport docs/protocol/input-stream-v1-fixtures.md` - passed.

## Known Stubs

None. Nullable fields in debug summaries are intentional for rejected or partial diagnostic summaries.

## Threat Flags

None. New UDP parser/authenticator surface is covered by the plan threat model and tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04-02 can build Android UDP sender behavior against the fixed config and frame codec. Later receiver/replay guard work can rely on typed rejection results and golden fixture parity.

## Self-Check: PASSED

- Summary file exists.
- Android and desktop codec source files exist.
- Task commits `3926b74` and `f220fbe` exist in git history.
- No tracked file deletions occurred in task commits.

---
*Phase: 04-input-stream-and-haptic-transport*
*Completed: 2026-06-08*
