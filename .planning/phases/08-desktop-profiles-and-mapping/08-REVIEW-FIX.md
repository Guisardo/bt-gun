---
phase: 08-desktop-profiles-and-mapping
reviewed: 2026-06-12T20:26:24Z
resolved: 2026-06-12
status: addressed
---

# Phase 08 Review Fix Report

## Disposition

| Finding | Disposition | Evidence |
|---------|-------------|----------|
| CR-01 Raw Debug Toggle Persists Across Sessions | Accepted by design; no source change | Phase 8 stores the raw debug toggle in Android-owned profile/session state and defaults it off on `Default Visualizer`. The desktop never requests or persists raw debug. |
| CR-02 Profile Save Can Start a Foreground Service Without Foreground Promotion | Fixed | `MainActivity` now sends `ACTION_RELOAD_ACTIVE_PROFILE` only when `HostSessionService.latestState.foregroundActive` is true; inactive saves re-render locally. |
| WR-01 Profile Store and Validation Tests Are Not Registered in the Android Harness | Fixed | `ProfileStoreTestKt` and `ProfileValidationTestKt` are now in the Android unit harness list. |
| WR-02 Pairing Restart Leaves Stale Profile Diagnostics Visible | Fixed | `PairingWindow.renderSession()` clears profile metadata, mapped/raw flags, profile timestamp, and packet-stream state on each new pairing session. |

## Verification

- Android focused tests passed: `gradle -p android-host testDebugUnitTest --tests '*HostSessionService*' --tests '*ProfileStore*' --tests '*ProfileValidation*' --no-daemon --console=plain`
- Desktop focused tests passed: `gradle -p desktop-companion test --tests '*PairingWindow*' --no-daemon --console=plain`
- Existing Android raw debug default behavior remains covered by the Phase 8 source guard and tests from `711ba54`.
