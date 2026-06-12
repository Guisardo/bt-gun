---
phase: 08-desktop-profiles-and-mapping
plan: 02
subsystem: android-profile-storage
tags: [android, profiles, validation, sharedpreferences, tdd]

requires:
  - phase: 08-desktop-profiles-and-mapping
    provides: Android-owned Phase 8 profile authority wording from Plan 01
provides:
  - Android-local profile schema with immutable Default Visualizer
  - Profile validation save gate with stable UI labels
  - SharedPreferences JSON profile store with safe malformed fallback
affects: [phase-08, android-host, profiles, mapped-stream]

tech-stack:
  added: []
  patterns:
    - Android owns profile schema, validation, and persistence.
    - SharedPreferences stores one versioned JSON profile document under bt_gun_profiles/profiles_v1.
    - Invalid profile edits return explicit rejected results instead of silent repair.

key-files:
  created:
    - android-host/app/src/main/java/com/btgun/host/profile/ProfileModels.kt
    - android-host/app/src/main/java/com/btgun/host/profile/ProfileValidation.kt
    - android-host/app/src/main/java/com/btgun/host/profile/ProfileStore.kt
    - android-host/app/src/test/java/com/btgun/host/profile/ProfileValidationTest.kt
    - android-host/app/src/test/java/com/btgun/host/profile/ProfileStoreTest.kt
  modified: []

key-decisions:
  - "Default Visualizer is a built-in Android profile with profileId=default_visualizer, revision=1, and builtIn=true."
  - "ProfileStore returns explicit loaded/defaulted/rejected load states and saved/rejected mutation states for dashboard use."
  - "Unsupported axis/button crossing attempts are represented only as invalid Android profile candidates and rejected by validation."

patterns-established:
  - "TDD profile tests use standalone Kotlin main() unit tests, matching existing Android host test style."
  - "Profile mutations increment document revision; rename/edit increment profile revision; select/delete increment document revision only."

requirements-completed: [PROF-01, PROF-04, PROF-06]

duration: 14min
completed: 2026-06-12
---

# Phase 08 Plan 02: Desktop Profiles and Mapping Summary

**Android-local profile defaults, validation labels, and durable SharedPreferences JSON store**

## Performance

- **Duration:** 14 min
- **Started:** 2026-06-12T16:02:20Z
- **Completed:** 2026-06-12T16:15:59Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Added Android profile model types for v1 physical/virtual buttons, aim settings, provider overrides, immutable `Default Visualizer`, and `ProfileDocument.defaults()`.
- Added `ProfileValidator` with exact UI labels for required names, outputs, duplicates, recenter, unsupported axis mapping, and invalid aim settings.
- Added `ProfileStore` backed by `bt_gun_profiles` SharedPreferences key `profiles_v1`, with default fallback, malformed JSON rejection status, mutation semantics, revisions, and reset.
- Added focused TDD coverage for defaults, validation labels, malformed persistence, duplicate/rename/edit/select/delete/reset, and built-in immutability.

## Task Commits

Each behavior task used RED then GREEN commits:

1. **Task 1 RED: Profile defaults test** - `4d99e79` (test)
2. **Task 1 GREEN: Profile defaults implementation** - `63881d6` (feat)
3. **Task 2 RED: Profile validation test** - `ee53da3` (test)
4. **Task 2 GREEN: Profile validation implementation** - `833ac51` (feat)
5. **Task 3 RED: Profile store test** - `467aeef` (test)
6. **Task 3 GREEN: Profile store implementation** - `dd4c06d` (feat)
7. **Refactor: duplicate-output validation cleanup** - `e3a0ffa` (refactor)

## Files Created/Modified

- `android-host/app/src/main/java/com/btgun/host/profile/ProfileModels.kt` - Android-local profile schema, v1 button ids, aim defaults, provider override keys, and Default Visualizer document.
- `android-host/app/src/main/java/com/btgun/host/profile/ProfileValidation.kt` - Profile save gate and stable labels.
- `android-host/app/src/main/java/com/btgun/host/profile/ProfileStore.kt` - SharedPreferences JSON persistence and mutation result types.
- `android-host/app/src/test/java/com/btgun/host/profile/ProfileValidationTest.kt` - Validation label and invalid aim behavior tests.
- `android-host/app/src/test/java/com/btgun/host/profile/ProfileStoreTest.kt` - Default, persistence, malformed fallback, mutation, revision, and immutability tests.

## Decisions Made

- Used manual `kotlinx.serialization-json` object encoding/decoding instead of adding the Kotlin serialization compiler plugin.
- Kept profile persistence Android-only; no desktop profile store, editor, or profile authority was added.
- Added `unsupportedMappings` as candidate-state validation input so axis/button crossing attempts can be rejected without widening the valid v1 mapping model.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle needed sandbox escalation because its local file-lock/socket setup is blocked by the managed sandbox. The required Gradle commands were rerun unchanged with approval and passed.
- Kotlin incremental compilation initially fell back around an inline `groupingBy` duplicate check. The refactor commit replaced it with explicit set tracking; the final required test run passed without the incremental fallback.

## Known Stubs

None. Stub scan found only intentional nullable/empty defaults for validation candidates and result defaults; none flow to UI rendering as placeholders.

## Threat Flags

None. The new SharedPreferences JSON trust boundary and validation tampering gate are covered by `T-08-03` and `T-08-04` in the plan threat model.

## User Setup Required

None - no external service configuration required.

## Verification

- RED Task 1: `gradle -p android-host testDebugUnitTest --tests '*ProfileStore*' --no-daemon --console=plain` failed on missing profile model symbols.
- GREEN Task 1: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*ProfileStore*' --no-daemon --console=plain` - PASS.
- RED Task 2: same Gradle environment with `--tests '*ProfileValidation*'` failed on missing validator symbols.
- GREEN Task 2: same Gradle environment with `--tests '*ProfileValidation*'` - PASS.
- RED Task 3: same Gradle environment with `--tests '*ProfileStore*' --tests '*ProfileValidation*'` failed on missing store/result/preference types.
- GREEN/overall: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*ProfileStore*' --tests '*ProfileValidation*' --no-daemon --console=plain` - PASS.
- Desktop profile authority scan: `rg -n "class .*ProfileStore|Profile editor|Edit profile|Save profile|SharedPreferences.*profile|profiles_v1" desktop-companion desktop-companion/src` - PASS with no matches.

## Next Phase Readiness

Ready for `08-03-PLAN.md`: Android profile mapper can consume this schema/store/validator foundation.

## Self-Check: PASSED

- Summary file exists.
- Task commits exist: `4d99e79`, `63881d6`, `ee53da3`, `833ac51`, `467aeef`, `dd4c06d`, `e3a0ffa`.
- Key created files exist.
- No tracked files were deleted by task commits.
- Untracked `.codex/` remains untouched as requested.

---
*Phase: 08-desktop-profiles-and-mapping*
*Completed: 2026-06-12*
