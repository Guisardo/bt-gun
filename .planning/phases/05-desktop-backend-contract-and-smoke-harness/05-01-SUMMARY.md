---
phase: 05-desktop-backend-contract-and-smoke-harness
plan: 01
subsystem: desktop-backend
tags: [kotlin, gradle, tdd, backend-contract, virtual-controller]
requires:
  - phase: 04-input-stream-and-haptic-transport
    provides: Authenticated desktop UDP receiver and raw input contract
provides:
  - Immutable semantic controller state for BT Gun v1 controls
  - Exact gamepad-like joystick descriptor contract
  - Main-style Gradle contract test for DESK-04
affects: [phase-05, phase-06, phase-07, phase-08]
tech-stack:
  added: []
  patterns: [plain Kotlin main-style tests, immutable backend contract models]
key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt
    - desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt
    - desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendContractTest.kt
  modified:
    - desktop-companion/build.gradle.kts
key-decisions:
  - "BT Gun v1 backend descriptor is fixed to one gamepad_like_joystick with six named buttons, four named axes, and digital trigger."
  - "SemanticControllerState remains platform-neutral and contains only named semantic controls plus stale/sourceSequence."
patterns-established:
  - "Descriptor invariants are executable Kotlin checks before platform adapters exist."
  - "Backend contract tests follow existing Gradle doLast main-class registration."
requirements-completed: [DESK-04]
duration: 14 min
completed: 2026-06-09
---

# Phase 05 Plan 01: Semantic Controller State and Descriptor Summary

**BT Gun v1 desktop backend contract with immutable semantic state, exact six-button/four-axis descriptor, and Gradle TDD coverage.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-06-09T18:14:38Z
- **Completed:** 2026-06-09T18:28:40Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added `SemanticControllerState` with neutral defaults for trigger, reload, X/Y/A/B, stick axes, aim axes, stale state, and optional source sequence.
- Added `VirtualControllerDescriptor`, `btGunV1Descriptor`, and `requireBtGunV1Invariant` for the exact DESK-04 gamepad-like joystick contract.
- Registered and passed `BackendContractTestKt` through the existing plain Kotlin Gradle test pattern.

## Task Commits

1. **Task 1: RED descriptor and semantic state contract tests** - `26bef4d` (test)
2. **Task 2: GREEN semantic state and v1 descriptor contract** - `f0eef70` (feat)

## Files Created/Modified

- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` - Immutable semantic state model.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` - Descriptor model, v1 descriptor value, and invariant check.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/BackendContractTest.kt` - Main-style descriptor/default-state contract test.
- `desktop-companion/build.gradle.kts` - Registers backend contract test class.

## Decisions Made

- BT Gun v1 descriptor is fixed to `gamepad_like_joystick`, buttons `trigger,reload,x,y,a,b`, axes `stickX,stickY,aimX,aimY`, and trigger kind `digital`.
- Backend state stays semantic-only; platform report packing, profile mapping, and OS adapter details remain later-phase work.

## Verification

- PASS: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --rerun-tasks --no-daemon`
- PASS: RED first failed on missing `btGunV1Descriptor`, `requireBtGunV1Invariant`, and `SemanticControllerState` symbols only.
- PASS: Word-boundary forbidden-scope scan found no raw motion/platform/profile terms:
  `rg -n "\byaw\b|\bpitch\b|\broll\b|analog|ProfileMapper|profile mapping|HIDVirtualDevice|VHF|HIDDriverKit|VirtualHID" desktop-companion/src/main/kotlin/com/btgun/desktop/backend desktop-companion/src/test/kotlin/com/btgun/desktop/backend`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used JDK 17/no-daemon Gradle verification**
- **Found during:** Task 1 RED verification
- **Issue:** Sandbox blocked Gradle file-lock sockets, and the default Gradle launcher reused Java 26, which Kotlin 2.0.21 rejected.
- **Fix:** Ran required Gradle verification with escalation, full JDK 17 `JAVA_HOME`, `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle`, and `--no-daemon`.
- **Files modified:** None
- **Verification:** Final Gradle test run passed.
- **Committed in:** N/A, verification environment only.

**2. [Rule 3 - Blocking] Interpreted over-broad forbidden-term grep**
- **Found during:** Task 2 acceptance verification
- **Issue:** The literal plan grep includes `roll`, which necessarily matches required `Controller` identifiers.
- **Fix:** Recorded the literal command output and used a word-boundary scan to verify no actual forbidden raw motion, profile, or platform terms were introduced.
- **Files modified:** None
- **Verification:** Word-boundary scan returned no matches.
- **Committed in:** N/A, verification command correction only.

---

**Total deviations:** 2 auto-fixed (Rule 3 verification blockers)
**Impact on plan:** No product scope change. DESK-04 contract and tests were implemented as planned.

## Issues Encountered

- The exact plan grep is structurally unsatisfiable because `roll` is a substring of required `Controller` symbol names.
- No authentication gates occurred.

## Known Stubs

None. `sourceSequence = null` is the intended neutral value for a state that has not been sourced from a packet.

## Threat Flags

None. No new network endpoints, auth paths, file access boundaries, or schema changes were added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 05 Plan 02 can build capabilities and stub backend lifecycle on top of `SemanticControllerState` and `btGunV1Descriptor`.

## Self-Check: PASSED

- Found created files: `SemanticControllerState.kt`, `VirtualControllerDescriptor.kt`, `BackendContractTest.kt`.
- Found task commits: `26bef4d`, `f0eef70`.
- Final Gradle verification passed.

---
*Phase: 05-desktop-backend-contract-and-smoke-harness*
*Completed: 2026-06-09*
