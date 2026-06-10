---
phase: 07-macos-virtual-joystick-path
plan: 01
subsystem: macos-virtual-hid
tags: [macos, corehid, hidvirtualdevice, codesign, keychain, pack-03]

requires:
  - phase: 05-desktop-backend-contract-and-smoke-harness
    provides: backend descriptor, capabilities model, and smoke harness baseline
provides:
  - PACK-03 CoreHID-first development proof setup docs
  - Minimal Swift CoreHID HIDVirtualDevice helper package
  - Sanitized macOS toolchain/signing/CoreHID feasibility evidence rows
  - Runtime blocker classification for downstream fallback planning
affects: [phase-07, macos-backend, pack-03, desk-03, desk-06]

tech-stack:
  added: [SwiftPM CoreHID helper, macOS codesign, hidutil, ioreg]
  patterns:
    - CoreHID-first helper proof before backend support claims
    - Sanitized JSONL evidence rows with explicit gate labels
    - Keychain signing retry via BTGUN_MACOS_HID_SIGN_IDENTITY

key-files:
  created:
    - .planning/phases/07-macos-virtual-joystick-path/07-01-SUMMARY.md
  modified:
    - docs/setup/macos-virtual-hid.md
    - docs/evidence/manifests/phase7-macos-virtual-hid.jsonl
    - native/macos-hid-helper/scripts/build-corehid-helper.sh

key-decisions:
  - "CoreHID remains first path, but Plan 07-01 records corehid-runtime-blocked until entitlement/runtime policy is resolved."
  - "keychain-bio-local was used for the requested signing retry; it embedded the virtual HID entitlement but did not prevent macOS from killing the helper before enumeration."
  - "No DESK-03 or DESK-06 backend support is claimed from this plan."

patterns-established:
  - "CoreHID gate labels: corehid-pass, corehid-compile-blocked, corehid-runtime-blocked, corehid-visibility-failed, corehid-output-failed."
  - "Evidence rows store sanitized status metadata only, with raw build output kept under /private/tmp."

requirements-completed: []

duration: "11 min resume; prior Tasks 1-2 completed before checkpoint"
completed: 2026-06-10
---

# Phase 07 Plan 01: CoreHID Feasibility Summary

**CoreHID helper builds and signs locally, but macOS kills the virtual HID helper before OS enumeration, leaving a precise entitlement/runtime checkpoint.**

## Performance

- **Duration:** 11 min resume; Tasks 1-2 were already committed before this checkpoint resume.
- **Started:** 2026-06-10T13:36:58Z
- **Completed:** 2026-06-10T13:48:10Z
- **Tasks:** 3/3 addressed; Task 3 remains blocked by OS entitlement/runtime policy.
- **Files modified:** 4

## Accomplishments

- Added a narrow `BTGUN_MACOS_HID_SIGN_IDENTITY` build-script override so the requested Keychain identity could be used without replacing the ad-hoc default.
- Reran the CoreHID helper proof outside the sandbox with `keychain-bio-local`.
- Confirmed Swift CoreHID import, SwiftPM build, named codesign, and embedded `com.apple.developer.hid.virtual.device` entitlement.
- Recorded `corehid-runtime-blocked` because macOS killed the helper before `hidutil`/`ioreg` enumeration.

## Task Commits

1. **Task 1: macOS toolchain, entitlement, and PACK-03 foundation** - `6694f03` (docs)
2. **Task 2: CoreHID helper feasibility probe** - `dcbe3b5` (feat)
3. **Task 3: Keychain signing retry and runtime checkpoint** - this checkpoint commit (fix/docs)

## Files Created/Modified

- `docs/setup/macos-virtual-hid.md` - Documents the Keychain signing retry command and current runtime blocker.
- `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` - Updates `phase7-corehid-feasibility` to the latest sanitized `corehid-runtime-blocked` evidence.
- `native/macos-hid-helper/scripts/build-corehid-helper.sh` - Adds `BTGUN_MACOS_HID_SIGN_IDENTITY`, entitlement extraction, and precise resume text.
- `.planning/phases/07-macos-virtual-joystick-path/07-01-SUMMARY.md` - Captures the checkpoint outcome and next required action.

## Decisions Made

- Use `keychain-bio-local` for the requested retry.
- Keep CoreHID as the first path until a pass or a formal D-02/D-03 fallback gate decides otherwise.
- Do not update ROADMAP/STATE plan-completion counters while the runtime proof remains blocked.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added Keychain signing identity override**
- **Found during:** Task 3
- **Issue:** Existing helper script only supported ad-hoc signing, so the requested `keychain-bio-local` retry could not run through the standard helper.
- **Fix:** Added `BTGUN_MACOS_HID_SIGN_IDENTITY`, preserving ad-hoc signing as default.
- **Files modified:** `native/macos-hid-helper/scripts/build-corehid-helper.sh`, `docs/setup/macos-virtual-hid.md`
- **Verification:** Reran helper with `BTGUN_MACOS_HID_SIGN_IDENTITY=keychain-bio-local`.
- **Committed in:** this checkpoint commit

**2. [Rule 1 - Bug] Corrected runtime-blocked resume guidance**
- **Found during:** Task 3
- **Issue:** Runtime entitlement failures reused the compile-blocked `xcode-select` resume command, which was misleading after Swift build passed.
- **Fix:** Added gate-specific resume text for signing/entitlement runtime failures.
- **Files modified:** `native/macos-hid-helper/scripts/build-corehid-helper.sh`
- **Verification:** Latest helper run emitted an entitlement/provisioning resume command for `corehid-runtime-blocked`.
- **Committed in:** this checkpoint commit

---

**Total deviations:** 2 auto-fixed (Rule 3: 1, Rule 1: 1)  
**Impact on plan:** Scope stayed inside Task 3 proof mechanics and evidence. No backend support or DriverKit work was added.

## Issues Encountered

- Sandbox run could not complete SwiftPM because `sandbox-exec` failed; rerun outside sandbox was required for valid Keychain/codesign proof.
- Outside sandbox, `security find-identity` saw `keychain-bio-local`, codesign accepted it, and entitlement extraction showed `com.apple.developer.hid.virtual.device=true`.
- The helper process was killed before `hidutil` enumeration, so no OS-visible HID device proof exists yet.

## User Setup Required

Current blocker: `corehid-runtime-blocked`.

Required human/OS action:

1. Provide an entitlement-capable signing identity/provisioning profile for `com.apple.developer.hid.virtual.device`, or decide that the D-02/D-03 fallback gate should proceed.
2. After setup, rerun:

```bash
BTGUN_MACOS_HID_SIGN_IDENTITY=<identity> native/macos-hid-helper/scripts/build-corehid-helper.sh
```

Current Xcode state is not the active blocker: Swift CoreHID import and SwiftPM build passed with Command Line Tools. Full Xcode remains only a fallback setup step if later compile/tooling proof fails.

## Next Phase Readiness

Blocked for Plan 07-02/07-03 support claims. Downstream work may use the helper source and docs, but must not claim DESK-03 or DESK-06 until CoreHID passes or a D-02/D-03 fallback gate is selected.

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED

- Verified plan files exist: helper package, setup doc, manifest, build script, and this summary.
- Verified prior task commits exist: `6694f03`, `dcbe3b5`.
- Verified JSONL manifest parses with `jq`.
- Verified static proof search includes `HIDVirtualDevice`, `com.apple.developer.hid.virtual.device`, `HIDDriverKit`, `hidutil`, `ioreg`, and `phase7-corehid-feasibility`.
- Verified secret scan found only redaction policy text, not raw signing hashes, private key paths, pairing material, Bluetooth addresses, device ids, or screenshots.

---
*Phase: 07-macos-virtual-joystick-path*
*Completed: 2026-06-10*
