---
phase: 03-lan-pairing-and-secure-session
plan: 03
subsystem: authenticated-pairing-proof
tags: [kotlin, android, desktop, hmac, replay-protection, trust-anchor]

requires:
  - phase: 03-lan-pairing-and-secure-session
    provides: Desktop pairing material and Android trusted desktop metadata from Plans 03-01 and 03-02
provides:
  - Versioned HMAC-SHA256 pairing proof transcript on Android and desktop
  - Desktop proof verifier with expiry, single-use, replay, rate-limit, and fingerprint mismatch rejection
  - Android trust validation states for first-trust, trusted, missing, and mismatch
  - Secret redaction coverage for QR secret, manual code, proof values, and private key markers
  - LAN pairing protocol proof transcript and trust-anchor documentation
affects: [03-lan-pairing-and-secure-session, desktop-companion, android-host-session, protocol-docs]

tech-stack:
  added: []
  patterns: [main-style TDD tests, JCA Mac HMAC-SHA256, MessageDigest constant-time comparison, fail-closed trust validation]

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/security/PairingProof.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt
    - android-host/app/src/main/java/com/btgun/host/session/PairingProof.kt
  modified:
    - desktop-companion/build.gradle.kts
    - desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt
    - android-host/app/src/main/java/com/btgun/host/session/TrustedDesktopStore.kt
    - android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt
    - docs/protocol/lan-pairing-v1.md

key-decisions:
  - "Use a versioned `btgun-pair-v1` HMAC transcript with session id, desktop nonce, Android nonce, desktop SPKI fingerprint, and one-time material."
  - "Desktop proof verification returns explicit accepted/rejected result types and never returns trusted control state on failure."
  - "Android trust validation reports fingerprint mismatch without overwriting stored trusted metadata."

requirements-completed: [TRAN-03]

duration: 19min
completed: 2026-06-07
---

# Phase 03 Plan 03: Authenticated Pairing Proof Summary

**Authenticated one-time pairing proof with replay/rate-limit defenses, fail-closed fingerprint trust, and protocol transcript documentation.**

## Performance

- **Duration:** 19 min
- **Completed:** 2026-06-07T19:06:33Z
- **Tasks:** 3 completed
- **Files modified:** 9 source/doc files, plus planning metadata

## Accomplishments

- Added RED tests first for accepted-once proof, expired session, reused session, replayed Android nonce, wrong QR secret, wrong manual code, wrong fingerprint, exhausted attempts, Android trust mismatch, and redaction gates.
- Implemented matching Android and desktop `PairingProof` helpers using JCA `Mac` with HMAC-SHA256 and `MessageDigest.isEqual` for proof comparison.
- Added `PairingSessionRegistry.verifyProof()` with explicit accepted/rejected results for expiry, wrong proof, replay, rate-limit, and fingerprint mismatch.
- Ensured no trusted control state is returned until proof succeeds, and accepted sessions are consumed immediately.
- Updated Android `TrustedDesktopStore.validateIdentity()` to return `FirstTrust`, `Trusted`, `Missing`, or `Mismatch` without silent fingerprint overwrite.
- Documented proof transcript order, TTL, single-use rule, nonce replay rule, rate-limit behavior, trust mismatch behavior, and redaction requirements without adding Phase 4 input or haptic payload scope.

## Task Commits

1. **Task 1: RED - add security proof, replay, and trust mismatch tests** - `9d20c4f` (test)
2. **Task 2: GREEN - implement proof transcript and fail-closed trust behavior** - `08a1952` (feat)
3. **Task 3: REFACTOR - document transcript and enforce redaction gates** - `e0b1442` (docs)

## Verification

- RED desktop: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingSecurity*'` failed on missing planned `PairingProof`, `PairingAttemptResult`, `PairingProofRequest`, `PairingSecurityState`, and proof/rate-limit methods.
- RED Android: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*TrustedDesktopStore*'` failed on missing planned `TrustValidationResult` and `PairingProof`.
- Focused GREEN desktop: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingSecurity*' --tests '*PairingSession*'` passed.
- Focused GREEN Android: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*TrustedDesktopStore*' --tests '*PairingPayload*'` passed.
- Task 3 focused: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*PairingSecurity*'` passed.
- Wave closeout desktop: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test` passed.
- Wave closeout Android: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` passed.
- Boundary grep: `rg -n "button bitmask|input frame schema|packet loss|jitter|haptic strength|haptic duration|haptic ttl|haptic ack|haptic fail" docs/protocol/lan-pairing-v1.md` returned no matches.

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/PairingProof.kt` - desktop HMAC transcript, proof creation, and constant-time verification helper.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt` - proof request/result types, accepted session state, nonce replay tracking, failed-attempt counting, and rate-limited security state.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt` - proof/private-key redaction rules tightened without hiding fingerprint suffixes.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt` - main-style security tests for proof, replay, rate-limit, fingerprint mismatch, and redaction.
- `desktop-companion/build.gradle.kts` - registers `PairingSecurityTestKt`.
- `android-host/app/src/main/java/com/btgun/host/session/PairingProof.kt` - Android HMAC transcript and proof creation helper matching desktop field order.
- `android-host/app/src/main/java/com/btgun/host/session/TrustedDesktopStore.kt` - fail-closed trust validation result model.
- `android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt` - Android proof transcript and trust mismatch coverage.
- `docs/protocol/lan-pairing-v1.md` - proof transcript, replay, rate-limit, trust anchor, and redaction contract.

## Decisions Made

- HMAC transcript is line-delimited and version-labeled as `btgun-pair-v1` so Android and desktop can share exact field order without a new serialization dependency.
- Failed proof attempts record Android nonces, so replayed nonces fail even if an earlier attempt used a wrong proof.
- Rate limiting locks the active desktop pairing session after the configured failed-attempt limit; correct material cannot bypass a locked session.
- `TrustValidationResult.FirstTrust` does not persist anything by itself. The explicit trust/re-pair path must call `saveTrustedDesktop()` after user confirmation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added missing RED test helper before committing**
- **Found during:** Task 1
- **Issue:** Android RED tests initially referenced `expectTrue()` before defining it.
- **Fix:** Added the helper before the RED commit, then reran RED to confirm failures were limited to planned missing symbols.
- **Files modified:** `android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt`
- **Commit:** `9d20c4f`

**2. [Rule 2 - Security] Strengthened redaction during GREEN**
- **Found during:** Task 2
- **Issue:** New security tests required proof/private-key redaction before Task 3 documentation work.
- **Fix:** Added `pairing_proof` and private-key marker redaction while keeping fingerprint suffix display allowed.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt`
- **Commit:** `08a1952`, refined in `e0b1442`

**3. [Rule 1 - Bug] Narrowed private-key redaction**
- **Found during:** Task 3
- **Issue:** A broad `private_key=.*` rule redacted the fingerprint suffix that diagnostics are allowed to show.
- **Fix:** Redacted private-key marker/value tokens without consuming later diagnostic fields.
- **Files modified:** `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt`, `desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt`
- **Commit:** `e0b1442`

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 security hardening)
**Impact on plan:** All fixes support the planned security requirements. No UDP input frame schema, packet metrics, haptic payload, or haptic ack/fail behavior was introduced.

## Issues Encountered

- Raw `gsd-tools` was not on PATH; the Node CLI path was available for state inspection. Planning metadata was updated directly.
- Sandboxed Gradle could not create its local file-lock socket, so required Gradle commands were rerun with approved escalation.

## Auth Gates

None.

## Known Stubs

None. Nullable `activeSession` and test in-memory preference values are state absence markers, not UI or protocol stubs.

## TDD Gate Compliance

- RED commit exists before GREEN: `9d20c4f`
- GREEN commit exists after RED: `08a1952`
- REFACTOR/docs commit exists after GREEN: `e0b1442`

## Threat Flags

None. New cryptographic proof, trust validation, and diagnostics redaction surfaces match the plan threat model.

## Next Phase Readiness

Plan 03-04 can gate the reliable control-channel client/server on `PairingAttemptResult.Accepted` and use `TrustedPairingSession.sid` plus desktop fingerprint metadata. Packet streaming, phone haptic command execution, and transport metrics remain later-phase scope.

## Self-Check: PASSED

- Found summary file, desktop and Android `PairingProof` files, desktop `PairingSecurityTest`, and protocol doc updates.
- Found task commits `9d20c4f`, `08a1952`, and `e0b1442` in git log.
- Full desktop and Android unit suites passed.
- Boundary grep returned no forbidden Phase 4 terms in `docs/protocol/lan-pairing-v1.md`.

---
*Phase: 03-lan-pairing-and-secure-session*
*Completed: 2026-06-07*
