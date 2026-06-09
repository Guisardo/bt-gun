---
phase: 05-desktop-backend-contract-and-smoke-harness
plan: 02
subsystem: desktop-backend
tags: [kotlin, gradle, tdd, backend-capabilities, virtual-controller]
requires:
  - phase: 05-01
    provides: Semantic controller state and BT Gun v1 descriptor contract
provides:
  - Structured backend capability model with haptic support matrix
  - Explicit unsupported reasons for platform/device/output-report limits
  - Synchronized stub virtual-controller backend lifecycle and publish state
affects: [phase-05, phase-06, phase-07, phase-08]
tech-stack:
  added: []
  patterns: [plain Kotlin main-style tests, structured capability presets, synchronized stub backend]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendCapabilitiesTest.kt
  modified:
    - desktop-companion/build.gradle.kts
key-decisions:
  - "Phase 5 stub capabilities use explicit platform ids macos-stub and windows-stub without claiming OS-visible device support."
  - "Stub backend publish state is synchronized and records current state plus last publish result through the shared backend interface."
patterns-established:
  - "Capability presets mirror btGunV1Descriptor buttons and axes exactly."
  - "Unsupported capability fields carry platform, feature, and detail rather than bitmasks or freeform diagnostics."
requirements-completed: [DESK-07]
duration: 10 min
completed: 2026-06-09
---

# Phase 05 Plan 02: Structured Backend Capabilities and Stub Backend Lifecycle Summary

**Structured virtual-controller capabilities with explicit platform limits and synchronized stub backend lifecycle coverage.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-09T18:33:49Z
- **Completed:** 2026-06-09T18:43:17Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added `UnsupportedReason`, `HapticEffectCapability`, `BackendCapabilities`, and `BackendCapabilityPresets`.
- Added `VirtualControllerBackend` lifecycle/publish surface and result types.
- Added `StubVirtualControllerBackend` with synchronized lifecycle, current state, and last publish result.
- Registered and passed `BackendCapabilitiesTestKt` through the existing Gradle main-style test runner.

## Task Commits

1. **Task 1: RED capability and backend lifecycle tests** - `1de2716` (test)
2. **Task 2: GREEN structured capabilities and stub backend** - `5f7c0b1` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - Structured capabilities, haptic matrix, presets, and unsupported reasons.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt` - Backend lifecycle, publish results, and shared interface.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt` - Thread-safe macOS/Windows stub backend.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendCapabilitiesTest.kt` - Descriptor/capability/haptic/lifecycle invariant tests.
- `desktop-companion/build.gradle.kts` - Registers the backend capability test main class.

## Decisions Made

- Stub platforms are explicit `macos-stub` and `windows-stub`.
- Phase 5 stubs support strength, duration, and phone haptic intent, while pattern and output-report support are explicitly unsupported.
- Stub backend state is guarded by a private lock and does not create OS-visible virtual devices.

## Verification

- PASS: RED run failed at `:compileTestKotlin` on missing planned backend capability/backend symbols.
- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --rerun-tasks --no-daemon -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home -Dkotlin.compiler.execution.strategy=in-process`
- PASS: Forbidden backend scope scan returned no matches:
  `rg -n "supportsHaptics\\s*:\\s*Boolean|capabilityMask|bitmask|freeform|HIDVirtualDevice|HIDDriverKit|VHF|physical gun motor|ProfileMapper|profile mapping" desktop-companion/src/main/kotlin/com/btgun/desktop/backend desktop-companion/src/test/kotlin/com/btgun/desktop/backend`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used escalated Gradle verification**
- **Found during:** Task 1 and final verification
- **Issue:** Sandbox blocked Gradle file-lock socket creation with `FileLockContentionHandler`.
- **Fix:** Ran required verification with approval, JDK 17, `/private/tmp/bt-gun-gradle`, `--no-daemon`, and in-process Kotlin compiler.
- **Files modified:** None
- **Verification:** Final Gradle test run passed.
- **Committed in:** N/A, verification environment only.

---

**Total deviations:** 1 auto-fixed (Rule 3 verification blocker)
**Impact on plan:** No product scope change. DESK-07 capability and stub backend behavior were implemented as planned.

## Issues Encountered

- Kotlin RED output included secondary unresolved-property diagnostics caused by intentionally missing capability/backend symbols.
- No authentication gates occurred.

## Known Stubs

None. `lastPublishResult = null` is the initial no-publish state for the stub backend, not placeholder UI/data.

## Threat Flags

None. This plan added no network endpoints, auth paths, file access boundaries, or schema changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 05 Plan 03 can adapt `UdpReceivedInput` into `SemanticControllerState` and publish through `VirtualControllerBackend`.

## TDD Gate Compliance

- RED gate commit exists: `1de2716`.
- GREEN gate commit exists after RED: `5f7c0b1`.
- No refactor commit was needed.

## Self-Check: PASSED

- Found created files: `BackendCapabilities.kt`, `VirtualControllerBackend.kt`, `StubVirtualControllerBackend.kt`, `BackendCapabilitiesTest.kt`.
- Found task commits: `1de2716`, `5f7c0b1`.
- Final Gradle verification passed.
- Final forbidden scope scan returned no matches.

---
*Phase: 05-desktop-backend-contract-and-smoke-harness*
*Completed: 2026-06-09*
