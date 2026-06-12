---
phase: 08-desktop-profiles-and-mapping
verified: 2026-06-12T21:20:00Z
status: passed
score: 6/6 must-haves verified
overrides_applied: 0
---

# Phase 8: Desktop Profiles and Mapping Verification Report

**Phase Goal:** Users configure Android-owned aim and button profiles that apply at runtime; desktop only displays active Android profile metadata.
**Verified:** 2026-06-12T21:20:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

Roadmap Phase 8 success criteria and `PROF-01` through `PROF-06` are satisfied by implementation evidence in Android profile storage/editor/mapping/runtime output and desktop read-only metadata display. `gsd-tools` was not on PATH, so roadmap and requirement extraction used direct markdown parsing.

### User Flow Coverage

Roadmap marks Phase 8 as `mode: mvp`, but the roadmap goal is not in canonical `As a..., I want..., so that...` form. Verification used the supplied phase goal and goal-backward checks.

| Step | Expected | Evidence | Status |
| --- | --- | --- | --- |
| Open Android dashboard | Profile section shows active profile, mapping, recenter, raw debug, and error rows | `MainActivity.kt:174-187`, `DashboardState.kt:318-349`; USB evidence `phase8-dashboard-profile-rows` inspected | VERIFIED |
| Open profile management | Built-in Default Visualizer appears first, cannot be edited/deleted, can be duplicated | `MainActivity.kt:440-488`; USB evidence `phase8-profile-list-default` inspected | VERIFIED |
| Edit a user profile | Shared aim settings, provider override groups, button mapping, recenter, and raw debug toggle are in Android UI | `MainActivity.kt:511-635`; USB evidence `phase8-profile-editor-provider-overrides` and tilt continuation inspected | VERIFIED |
| Save invalid profile | Save is blocked with visible validation labels, no silent repair | `MainActivity.kt:645-660`, `ProfileValidation.kt:3-66`; USB evidence `phase8-validation-blocked-save` inspected | VERIFIED |
| Desktop observes profile | Desktop shows active Android profile and mapped stream state; no desktop profile editor/raw request | `ControlServer.kt:280-284`, `PairingWindow.kt:92-106`, `PairingWindow.kt:349-372` | VERIFIED |

### Observable Truths

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Android stores local profiles and active revision; desktop only displays active Android metadata. | VERIFIED | `ProfileStore` persists `bt_gun_profiles/profiles_v1`, duplicates/selects/deletes user profiles, blocks built-in edits, and tracks revisions (`ProfileStore.kt:50-150`). Desktop parses only `source=android` metadata and displays it (`ProfileMetadata.kt:11-28`, `PairingWindow.kt:349-372`). |
| 2 | Android editor configures motion aim/provider behavior, sensitivity, inversion, dead zone, and smoothing. | VERIFIED | Profile schema has shared aim settings and provider overrides (`ProfileModels.kt:50-80`), mapper applies provider-specific settings and aim math (`ProfileMapper.kt:95-184`), and UI exposes provider groups plus controls (`MainActivity.kt:523-635`). |
| 3 | Android limited remap covers trigger/reload/X/Y/A/B; stick and aim axes stay semantic. | VERIFIED | Physical/virtual enums contain only six v1 buttons (`ProfileModels.kt:5-35`), mapper only converts physical buttons to virtual button ids and passes stick/aim separately (`ProfileMapper.kt:73-86`, `ProfileMapper.kt:170-175`). |
| 4 | Android profile changes apply at runtime without Android rebuilds or desktop editor. | VERIFIED | `HostSessionProfileRuntime.reloadActiveProfile` reloads store state and remaps current input (`HostSessionService.kt:256-283`); profile save/select triggers reload only for active foreground service (`MainActivity.kt:491-508`, `HostSessionService.kt:1466-1467`). Tests cover runtime reload/remap. |
| 5 | Immutable `Default Visualizer` works immediately after pairing, and desktop shows read-only mapped-stream status. | VERIFIED | `BtGunProfile.defaultVisualizer()` defines built-in `default_visualizer`, revision 1, one-to-one mapping, raw debug off (`ProfileModels.kt:96-119`). Desktop rows show active profile/source/mapped stream (`PairingWindow.kt:349-372`). |
| 6 | Mapped product stream is default; raw provider/motion extras are Android-controlled and not desktop-requested. | VERIFIED | Android UDP sender sets mapped flag always and only includes raw extras when `rawDebugEnabled` is true (`AndroidUdpInputSender.kt:259-280`); desktop adapter consumes mapped aim and marks unmapped frames stale (`UdpControllerStateAdapter.kt:5-30`). Source guard found no product desktop raw request path. |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| --- | --- | --- | --- |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileModels.kt` | Versioned Android profile schema/defaults | VERIFIED | Contains button ids, aim settings, provider overrides, immutable Default Visualizer. |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileStore.kt` | Android-local profile persistence | VERIFIED | SharedPreferences-backed load/save/mutate/reset with default fallback and built-in immutability. |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileValidation.kt` | Save gate with labels | VERIFIED | Blocks blank names, missing/duplicate outputs, missing recenter, unsupported axis mapping, invalid aim settings. |
| `android-host/app/src/main/java/com/btgun/host/profile/ProfileMapper.kt` | Profile to mapped controller state | VERIFIED | Applies provider overrides, aim math, smoothing, button remap, semantic stick/aim, recenter physical id. |
| `android-host/app/src/main/java/com/btgun/host/MainActivity.kt` | Native Android profile UI | VERIFIED | Profile list/editor/actions, validation block, raw debug toggle, no desktop editor. |
| `android-host/app/src/main/java/com/btgun/host/HostSessionService.kt` | Runtime profile application | VERIFIED | Loads/reloads active profile, maps before HID/UDP, sends Android metadata. |
| `android-host/app/src/main/java/com/btgun/host/hid/BtGunHidReportPacker.kt` | Mapped HID report packing | VERIFIED | Packs mapped virtual buttons/stick/aim into existing input report (`BtGunHidReportPacker.kt:47-65`). |
| `android-host/app/src/main/java/com/btgun/host/transport/AndroidUdpInputSender.kt` | Mapped UDP stream/raw debug flag | VERIFIED | Mapped stream flag and raw-debug-only extras (`AndroidUdpInputSender.kt:259-280`). |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` | Android profile metadata acceptance | VERIFIED | Accepts inbound `PROFILE_METADATA`; initial desktop metadata sends diagnostics only (`ControlServer.kt:280-284`, `ControlServer.kt:436-457`). |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt` | Read-only profile diagnostics | VERIFIED | Displays active Android profile/source/mapped stream; clears stale profile diagnostics on restart. |
| `docs/evidence/manifests/phase8-android-profile-ui.jsonl` | Sanitized USB UI evidence manifest | VERIFIED | Contains required capture IDs and no secret/path/device fields. |

### Key Link Verification

| From | To | Via | Status | Details |
| --- | --- | --- | --- | --- |
| `ProfileStore.kt` | `ProfileValidation.kt` | `saveProfile` rejects validator errors | WIRED | `ProfileStore.kt:105-115`. |
| `ProfileMapper.kt` | `HostSessionService.kt` | `HostSessionProfileRuntime.mapCurrentState` | WIRED | `HostSessionService.kt:262-283`. |
| `HostSessionService.kt` | HID output | `hidSessionController.fanOutLiveInput(currentState)` uses mapped state | WIRED | `HostSessionService.kt:541-545`; packer mapped overload exists. |
| `HostSessionService.kt` | UDP output | `sendSnapshot/sendEdge(mappedState, rawDebugEnabled)` | WIRED | `HostSessionService.kt:975-999`. |
| `HostSessionService.kt` | Desktop metadata | `sendProfileMetadata(ProfileMetadata(... source="android"))` | WIRED | `HostSessionService.kt:548-557`. |
| `ControlServer.kt` | `PairingWindow.kt` | `onProfileMetadataReceived` callback | WIRED | `PairingWindow.kt:92-99`. |
| `UdpInputFrameCodec.kt` | `UdpControllerStateAdapter.kt` | mapped flags and `mappedAim` | WIRED | `UdpControllerStateAdapter.kt:5-30`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| --- | --- | --- | --- | --- |
| `HostSessionService.kt` | `activeProfile` | `ProfileStore.load().document.activeProfile()` | Yes - SharedPreferences/default document | FLOWING |
| `ProfileMapper.kt` | `MappedControllerState` | `GunInputState` + `MotionSample` + `BtGunProfile` | Yes - live state mapped with profile settings | FLOWING |
| `BtGunHidReportPacker.kt` | HID input report bytes | `MappedControllerState` | Yes - mapped buttons/stick/aim packed | FLOWING |
| `AndroidUdpInputSender.kt` | UDP frame | `MappedControllerState` + raw debug flag | Yes - mapped frame with optional raw extras | FLOWING |
| `ControlServer.kt` -> `PairingWindow.kt` | `ProfileMetadata` | authenticated `PROFILE_METADATA` envelope | Yes - metadata callback updates diagnostics | FLOWING |
| `UdpControllerStateAdapter.kt` | `SemanticControllerState` | decoded mapped UDP input | Yes - mapped aim/buttons drive semantic state | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| --- | --- | --- | --- |
| Android Phase 8 profile/storage/mapper/service/UI tests | `gradle -p android-host testDebugUnitTest --no-daemon --console=plain` | Sandbox failed with `SocketException: Operation not permitted`; rerun outside sandbox passed, `BUILD SUCCESSFUL in 14s` | PASS |
| Desktop profile metadata/mapped UDP/read-only UI tests | `gradle -p desktop-companion test --no-daemon --console=plain` | Sandbox failed with `SocketException: Operation not permitted`; rerun outside sandbox passed, `BUILD SUCCESSFUL in 18s` | PASS |
| Forbidden desktop profile/raw request source guard | `rg` across `android-host`, `desktop-companion`, `.planning`, `docs` | Product source has no desktop editor/raw-request path; only tests and historical planning context mention forbidden terms | PASS |
| UI evidence manifest and ignored raw screenshots | `git check-ignore`, `find .evidence/phase8/android-profile-ui`, image inspection | Manifest has all required capture IDs; raw screenshot directory ignored; screenshots inspected | PASS |

### Probe Execution

| Probe | Command | Result | Status |
| --- | --- | --- | --- |
| Phase 8 probes | `find scripts -path '*/tests/probe-*.sh' -type f` plus phase artifact grep | No probe scripts or probe declarations found | SKIPPED |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| --- | --- | --- | --- | --- |
| PROF-01 | 08-01, 08-02, 08-05, 08-07 | Android stores local profiles and active-profile revision; desktop only displays metadata. | SATISFIED | `ProfileStore.kt`, `MainActivity.kt`, `PairingWindow.kt`; no desktop profile storage. |
| PROF-02 | 08-01, 08-03, 08-04, 08-05, 08-07 | Android editor configures motion aim mapping with provider-specific tuning. | SATISFIED | `ProfileModels.kt`, `ProfileMapper.kt`, `MainActivity.kt`; provider screenshot evidence inspected. |
| PROF-03 | 08-01, 08-03, 08-04, 08-05, 08-07 | Sensitivity, inversion, dead zone, smoothing for shared/provider settings. | SATISFIED | `AimMappingSettings`, `ProviderAimOverrides`, mapper math, editor controls. |
| PROF-04 | 08-01, 08-02, 08-03, 08-04, 08-05, 08-07 | Limited remap of trigger/reload/X/Y/A/B; stick/aim axes semantic. | SATISFIED | Physical/virtual enums only include six buttons; mapper separates stick/aim. |
| PROF-05 | 08-01, 08-04, 08-05, 08-06, 08-07 | Profile changes apply at runtime without rebuild or desktop editor. | SATISFIED | Runtime reload path, foreground-safe save/select trigger, tests for reload/remap. |
| PROF-06 | 08-01, 08-02, 08-04, 08-05, 08-06, 08-07 | Immutable Default Visualizer and desktop id/name/revision/source/mapped-stream display. | SATISFIED | `defaultVisualizer`, built-in UI restrictions, desktop metadata/source/mapped stream rows. |

No orphaned Phase 8 requirements found in `.planning/REQUIREMENTS.md`; all `PROF-01` through `PROF-06` are present in plan frontmatter and mapped to Phase 8.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| --- | --- | --- | --- | --- |
| None blocking | - | - | - | Debt-marker scan found no `TBD`, `FIXME`, or `XXX` in Phase 8 implementation files. `return null` matches are parser/Android API control flow, not stubs. Historical placeholder text belongs to older dashboard phases, not Phase 8 profile behavior. |

### Human Verification Required

None remaining. The only planned blocking human checkpoint was the USB Android screenshot evidence; the manifest and ignored screenshot files exist, and required images were inspected during verification.

### Gaps Summary

No blocking gaps found. Review CR-01 is treated as accepted-by-design per `08-REVIEW-FIX.md`: raw debug remains Android-owned profile/session state defaulting off, and desktop never requests or persists raw debug. CR-02, WR-01, and WR-02 fixes are present in source/tests.

---

_Verified: 2026-06-12T21:20:00Z_
_Verifier: the agent (gsd-verifier)_
