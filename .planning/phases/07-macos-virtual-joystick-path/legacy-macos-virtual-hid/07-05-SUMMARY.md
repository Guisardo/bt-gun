---
phase: 07-macos-virtual-joystick-path
plan: 05
subsystem: macos-virtual-hid
tags: [macos, corehid, hidvirtualdevice, iohidmanager, iohiddevicesetreport, smoke, pack-03]

requires:
  - phase: 07-macos-virtual-joystick-path
    provides: MacosVirtualControllerBackend, CoreHID helper line protocol, runtime wiring, and output mapper from Plans 03 and 04
provides:
  - Real `smokeDesktopBackendMacosCoreHid` Gradle command with required helper path and fail-closed XML output
  - Separate `BtGunMacosHidOutputProbe` executable using `IOHIDDeviceSetReport`
  - Sanitized CoreHID enumeration, UI visibility, output probe, and gate rows
  - `corehid-runtime-blocked` Plan 07-06 fallback trigger
affects: [phase-07, macos-corehid, driverkit-fallback, desk-03, desk-06, pack-03]

tech-stack:
  added: [Swift IOKit HID output probe]
  patterns:
    - Real CoreHID smoke must require explicit helper path and fail closed without OS proof
    - OS/HID-origin output proof must come from a separate HID client, not simulated output
    - CoreHID non-pass gates trigger DriverKit fallback rather than weakening DESK-06

key-files:
  created:
    - desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosCoreHidBackendSmokeMain.kt
    - native/macos-hid-helper/Sources/BtGunMacosHidOutputProbe/main.swift
    - .planning/phases/07-macos-virtual-joystick-path/07-05-SUMMARY.md
  modified:
    - desktop-companion/build.gradle.kts
    - native/macos-hid-helper/Package.swift
    - docs/setup/macos-virtual-hid.md
    - docs/evidence/manifests/phase7-macos-virtual-hid.jsonl
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "CoreHID remains non-pass for Plan 07-05: the helper builds and signs, but normal macOS kills it before enumeration."
  - "The separate IOHIDDeviceSetReport probe is the required OS/HID-origin output proof path; simulateOutputReport and direct haptic sends remain insufficient."
  - "Plan 07-06 DriverKit fallback is mandatory because CoreHID did not prove OS-visible joystick or OS-origin output callback support."

patterns-established:
  - "CoreHID smoke XML stores only case names, status, timing, and sanitized failure messages."
  - "Phase 7 CoreHID gate rows use phase7-corehid-cli-enumeration, phase7-corehid-ui-visible, phase7-corehid-output-probe, and phase7-corehid-gate."

requirements-completed: []

duration: 12 min
completed: 2026-06-10
---

# Phase 07 Plan 05: CoreHID Gate Summary

**Real CoreHID smoke and OS/HID output probe now exist, and the gate records `corehid-runtime-blocked` with DriverKit fallback required.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-06-10T16:54:50Z
- **Completed:** 2026-06-10T17:06:46Z
- **Tasks:** 3
- **Files modified:** 7 plan files plus planning metadata

## Accomplishments

- Added `smokeDesktopBackendMacosCoreHid`, requiring `btgun.macos.hid.helper.path` and never falling back to `macos-stub`.
- Added `MacosCoreHidBackendSmokeMain.kt` with helper artifact validation, CoreHID capability checks, real helper startup/publish attempts, `hidutil` enumeration, and sanitized `ioreg` visibility checks.
- Added `BtGunMacosHidOutputProbe`, a separate Swift HID client that opens VID `0x1209`/PID `0xB707` and calls `IOHIDDeviceSetReport` with report ID `0x02`.
- Recorded `phase7-corehid-cli-enumeration`, `phase7-corehid-ui-visible`, `phase7-corehid-output-probe`, and `phase7-corehid-gate` rows.
- Confirmed the current CoreHID gate is `corehid-runtime-blocked`: helper builds and signs, but macOS kills it before enumeration; output probe cannot find the device.

## Task Commits

1. **Task 1: Real CoreHID smoke command with no stub fallback** - `c4acabc` (feat)
2. **Task 2: Separate macOS HID output report probe** - `6a7b059` (feat)
3. **Task 3: CoreHID visibility and output gate** - `a580bcf` (docs)
4. **Plan metadata:** this commit (docs)

## Files Created/Modified

- `desktop-companion/build.gradle.kts` - Registers `smokeDesktopBackendMacosCoreHid` and CoreHID smoke properties.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosCoreHidBackendSmokeMain.kt` - Real helper smoke command, Plan 04 fixture publish, and CLI visibility checks.
- `native/macos-hid-helper/Package.swift` - Adds `BtGunMacosHidOutputProbe` product/target.
- `native/macos-hid-helper/Sources/BtGunMacosHidOutputProbe/main.swift` - Separate OS/HID set-report probe.
- `docs/setup/macos-virtual-hid.md` - Documents probe build/run commands and Plan 07-05 gate result.
- `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` - Adds CoreHID enumeration/output/gate rows.
- `.planning/phases/07-macos-virtual-joystick-path/07-05-SUMMARY.md` - Captures plan result.

## Decisions Made

- Treat this CoreHID attempt as `corehid-runtime-blocked`, not pass or unsupported-output documentation.
- Use the separate `IOHIDDeviceSetReport` probe as the deterministic macOS OS/HID-origin output proof path.
- Trigger Plan 07-06 DriverKit fallback because CoreHID did not prove OS visibility or set-report callback delivery.

## Verification

- PASS static smoke scan: `rg -n "smokeDesktopBackendMacosCoreHid|MacosCoreHidBackendSmokeMainKt|btgun\\.macos\\.hid\\.helper\\.path|macos-corehid|TEST-btgun-macos-corehid.xml|hidutil list|ioreg -r -c IOHIDDevice" ...`
- PASS Kotlin compile: pinned offline `gradle compileKotlin` passed outside sandbox.
- PASS fail-closed smoke behavior: `gradle smokeDesktopBackendMacosCoreHid` without helper path wrote XML and failed closed.
- PASS native probe build: `swift build --package-path native/macos-hid-helper --scratch-path /private/tmp/btgun-macos-hid-helper-build -c debug --product BtGunMacosHidOutputProbe`.
- PASS native package build: SwiftPM package builds with both helper and output probe targets.
- PASS CoreHID gate run: helper build/sign script compiled both targets, embedded `com.apple.developer.hid.virtual.device`, then recorded `corehid-runtime-blocked` because macOS killed the helper before enumeration.
- PASS output probe non-pass reason: `BtGunMacosHidOutputProbe --strength 180 --duration-ms 120 --ttl-ms 500` returned `BT Gun Virtual Joystick 0x1209:0xB707 not found`.
- PASS manifest parse: `jq -c . docs/evidence/manifests/phase7-macos-virtual-hid.jsonl`.
- PASS gate-row scan found `phase7-corehid-cli-enumeration`, `phase7-corehid-ui-visible`, `phase7-corehid-output-probe`, `phase7-corehid-gate`, and `corehid-runtime-blocked`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ran Gradle and SwiftPM verification outside sandbox**
- **Found during:** Tasks 1-3
- **Issue:** Sandbox blocked Gradle file-lock sockets and SwiftPM manifest sandboxing.
- **Fix:** Used approved unsandboxed verification for Gradle and SwiftPM; no source change was needed.
- **Files modified:** None
- **Verification:** Kotlin compile, SwiftPM builds, CoreHID helper proof, and smoke task all ran to their expected pass or fail-closed result.
- **Committed in:** N/A, verification environment only

**2. [Rule 1 - Swift Build Bug] Fixed output probe set-report closure**
- **Found during:** Task 2
- **Issue:** Swift required an unwrapped report buffer pointer and explicit return from the `IOHIDDeviceSetReport` closure.
- **Fix:** Unwrapped the non-empty report buffer and returned the `IOReturn`.
- **Files modified:** `native/macos-hid-helper/Sources/BtGunMacosHidOutputProbe/main.swift`
- **Verification:** `swift build --product BtGunMacosHidOutputProbe` passed.
- **Committed in:** `6a7b059`

---

**Total deviations:** 2 auto-fixed (Rule 3: 1, Rule 1: 1)  
**Impact on plan:** Scope stayed inside planned smoke/probe/gate work. No SIP, developer-mode, system-extension, install, activation, removal, rollback, reboot, or OS security-state command was run.

## Issues Encountered

- CoreHID remains blocked at runtime on normal macOS: the signed helper is killed before enumeration even after SwiftPM build and entitlement embedding.
- `hidutil` and `ioreg` did not show `BT Gun Virtual Joystick` because the helper never survived long enough to register the virtual HID device.
- The separate output probe could not open the virtual joystick because no matching OS-visible device existed.

## Known Stubs

None. Empty/default helper statuses are fail-closed lifecycle states, not UI stubs. No direct simulated output is counted as proof.

## Threat Flags

None. The new smoke helper boundary, separate output probe, evidence rows, and DriverKit fallback gate were covered by the plan threat model and mitigated with explicit VID/PID matching, exact report bytes, sanitized XML/manifest output, and no OS security-state changes.

## User Setup Required

None for this plan closeout. Plan 07-06 owns the fallback path. Any future SIP, system extension developer mode, activation, install, removal, rollback, reboot, or OS security-state command still requires explicit approval before execution.

## Next Phase Readiness

Plan 07-06 must proceed down the DriverKit fallback branch unless a future entitlement-capable CoreHID runtime proof replaces the current `corehid-runtime-blocked` gate. DESK-03, DESK-06, and PACK-03 remain pending.

## Self-Check: PASSED

- Found `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosCoreHidBackendSmokeMain.kt`.
- Found `native/macos-hid-helper/Sources/BtGunMacosHidOutputProbe/main.swift`.
- Found `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl`.
- Found `docs/setup/macos-virtual-hid.md`.
- Found task commits `c4acabc`, `6a7b059`, and `a580bcf` in git history.
- Confirmed manifest JSONL parses with `jq`.
- Confirmed gate rows and `corehid-runtime-blocked` appear in the manifest.
- Confirmed stub scan found no TODO, FIXME, placeholder, coming soon, or user-visible empty-data patterns in created/modified plan files.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
