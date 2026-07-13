# Review Backlog Fixes

**Wave:** 0 docs/backlog manifest
**Scope:** Documentation-only review backlog fixes. No code changes, no runtime proof reruns, and no new proof claims.
**Owned files:** `.planning/review-backlog-fixes.md`, `.planning/PROJECT.md`, `.planning/research/ARCHITECTURE.md`, `docs/setup/android-build-device-testing.md`, `docs/setup/macos-virtual-hid.md`, `docs/setup/macos-driverkit-fallback.md`, `docs/limits/v1-compatibility-limits.md`.

## Proof Status

| Status | Meaning |
|--------|---------|
| `fixed-docs` | Wording corrected from existing repo context; no new proof claimed. |
| `pending-proof` | Docs or code mention the path, but accepted proof still needs a later target run. |
| `blocked-scaffold` | Scaffold remains useful, but current proof is blocked by entitlement, signing, approval, or OS policy. |
| `deferred` | Out of v1 or saved for later planning. |

## Android Owner

| Finding | Proof status | Fix / backlog action |
|---------|--------------|----------------------|
| Android owns v1 profiles, calibration, profile validation, and mapped output. Desktop must not be described as profile authority. | `fixed-docs` | Correct project and architecture docs so Android owns profiles/calibration and desktop is read-only metadata/diagnostics/backend. |
| Android build docs must describe the Phase 11 module split. | `fixed-docs` | List `:runtime`, debug `:app`, and user `:user-app`; include debug-host and user-app APK build/install commands. |
| Post-HID-subclass macOS Android Bluetooth HID behavior needs a fresh target proof before refreshed compatibility claims. | `pending-proof` | Later rerun macOS pairing/GameController input proof after any HID descriptor/subclass change and record whether haptic limitation status remains unchanged. |

## Desktop Owner

| Finding | Proof status | Fix / backlog action |
|---------|--------------|----------------------|
| Desktop boundary drifted toward profile mapping authority in older docs. | `fixed-docs` | Reframe desktop as LAN receiver, visualizer, diagnostics, read-only active profile metadata, and platform backend runtime. |
| Windows `0.6.3.0` Chrome/Gamepad API haptics are named in Windows docs but not proven by this Wave 0 work. | `pending-proof` | Keep native Windows VHF/report ID 2 proof as the accepted path; schedule a Windows target run for package `0.6.3.0` before claiming Chrome vibration support. |

## macOS Owner

| Finding | Proof status | Fix / backlog action |
|---------|--------------|----------------------|
| macOS product route is Android Bluetooth HID, not CoreHID or DriverKit. | `fixed-docs` | Mark CoreHID/DriverKit docs as retained blocked/scaffold notes only. |
| CoreHID helper was killed before enumeration and output probe could not find the device. | `blocked-scaffold` | Keep `corehid-runtime-blocked`; do not use CoreHID as a support claim without entitlement-capable proof. |
| DriverKit/system extension path needs explicit user approval and proper entitlement/signing proof before activation. | `blocked-scaffold` | Keep as lab-only fallback scaffold; no SIP, developer-mode, activation, install, rollback, or reboot commands are approved by this manifest. |

## Deferred / Not In Wave 0

| Finding | Proof status | Fix / backlog action |
|---------|--------------|----------------------|
| Direct desktop-to-gun Bluetooth is not v1. | `deferred` | Leave under v2 requirements until a desktop Bluetooth stack and hardware proof plan exists. |
| Physical gun motor rumble is not v1. | `deferred` | Keep Android phone haptics as v1 feedback until motor command bytes, parser mapping, Android execution, and physical proof exist. |
