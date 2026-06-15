# Phase 04 Physical Smoke Results

Use this file only for sanitized pass/fail records. Keep raw logs, screenshots, and captures under ignored `.evidence/phase4/input-haptic-transport/` paths.

Allowed status values for every scenario:

- `pass`: observed behavior matches expected behavior on real Android phone, iPega gun, desktop companion, and LAN.
- `fail`: physical smoke ran, but observed behavior did not match expected behavior.
- `blocked`: physical smoke could not complete because hardware, LAN, build, permission, diagnostic, or replay tooling was unavailable.
- `not-run`: physical smoke has not been attempted yet.

## Setup

| Field | Value |
|---|---|
| Test date | 2026-06-09 |
| Android device | pass - physical Android phone, sanitized by user approval |
| Android host build | pass - Phase 4 Android host build, build details not committed |
| Desktop OS/build | pass - desktop companion host, host details not committed |
| Desktop companion build | pass - Phase 4 desktop companion build, build details not committed |
| iPega gun connected | pass |
| Same-LAN condition | pass |
| Sanitized raw evidence root | `.evidence/phase4/input-haptic-transport/` |

## Input Stream

| capture_id | Status | Expected | Observed | Sanitized evidence pointer |
|---|---|---|---|---|
| `phase4-input-stream-001` | pass | Desktop receives live current-session trigger, reload, X/Y/A/B, and stick updates while packet stream is `active`. | User-approved physical smoke on 2026-06-09 confirmed live current-session controls reached desktop while packet stream was `active`. | `.evidence/phase4/input-haptic-transport/phase4-input-stream-001/` |
| `phase4-control-edge-001` | pass | Short trigger/reload/X/Y/A/B presses appear as down/up edges without waiting for a later snapshot. | User-approved physical smoke on 2026-06-09 confirmed short control presses appeared as down/up edges. | `.evidence/phase4/input-haptic-transport/phase4-control-edge-001/` |
| `phase4-motion-stream-001` | pass | Desktop receives raw motion/provider updates while packet stream is `active`. | User-approved physical smoke on 2026-06-09 confirmed raw motion/provider updates reached desktop while packet stream was `active`. | `.evidence/phase4/input-haptic-transport/phase4-motion-stream-001/` |

## Disconnect Recovery

| capture_id | Status | Expected | Observed | Sanitized evidence pointer |
|---|---|---|---|---|
| `phase4-disconnect-grace-001` | pass | During a short reliable-control or LAN interruption, unchanged-session UDP may continue and packet stream shows `grace`. | User-approved physical smoke on 2026-06-09 confirmed the packet stream entered `grace` during a short interruption. | `.evidence/phase4/input-haptic-transport/phase4-disconnect-grace-001/` |
| `phase4-stale-timeout-001` | pass | After grace expires, active controls and stick axes clear while last raw motion remains visible with `stale` status. | User-approved physical smoke on 2026-06-09 confirmed active controls cleared after grace expiry while last raw motion stayed visible with `stale` status. | `.evidence/phase4/input-haptic-transport/phase4-stale-timeout-001/` |
| `phase4-reconnect-reject-001` | pass | After trusted reconnect and fresh stream config, old UDP input from the prior stream is rejected before apply. | User-approved physical smoke on 2026-06-09 confirmed trusted reconnect used a fresh stream and old prior-stream input was rejected before apply. | `.evidence/phase4/input-haptic-transport/phase4-reconnect-reject-001/` |

## Phone Haptics

| capture_id | Status | Expected | Observed | Sanitized evidence pointer |
|---|---|---|---|---|
| `phase4-haptic-valid-001` | pass | Valid desktop haptic command pulses the Android phone once and returns `started`. | User-approved physical smoke on 2026-06-09 confirmed one phone pulse and `started` for a valid command. | `.evidence/phase4/input-haptic-transport/phase4-haptic-valid-001/` |
| `phase4-haptic-expired-001` | pass | Expired desktop haptic command returns `expired` and does not pulse the phone. | User-approved physical smoke on 2026-06-09 confirmed no phone pulse and `expired` for an expired command. | `.evidence/phase4/input-haptic-transport/phase4-haptic-expired-001/` |
| `phase4-haptic-session-change-001` | pass | Trusted session change cancels an active phone pulse and old stream input is rejected. | User-approved physical smoke on 2026-06-09 confirmed trusted session change cancelled the active pulse and rejected old stream input. | `.evidence/phase4/input-haptic-transport/phase4-haptic-session-change-001/` |
| `phase4-haptic-short-disconnect-001` | pass | Short reliable-control disconnect without session change does not emit `cancelled` solely because of the disconnect. | User-approved physical smoke on 2026-06-09 confirmed short reliable-control disconnect alone did not emit `cancelled`. | `.evidence/phase4/input-haptic-transport/phase4-haptic-short-disconnect-001/` |

## Blockers

None recorded yet. Use one row per blocker and reference affected `capture_id` values.

| capture_id | Blocker | Next action |
|---|---|---|
| none | none | none |

## Final Verdict

Status: pass

Notes: User approved Phase 4 physical smoke on 2026-06-09. All planned capture ids passed. Raw logs, screenshots, host identifiers, pairing material, and key material are not committed.
