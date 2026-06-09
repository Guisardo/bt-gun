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
| Test date | not-run |
| Android device | not-run |
| Android host build | not-run |
| Desktop OS/build | not-run |
| Desktop companion build | not-run |
| iPega gun connected | not-run |
| Same-LAN condition | not-run |
| Sanitized raw evidence root | `.evidence/phase4/input-haptic-transport/` |

## Input Stream

| capture_id | Status | Expected | Observed | Sanitized evidence pointer |
|---|---|---|---|---|
| `phase4-input-stream-001` | not-run | Desktop receives live current-session trigger, reload, X/Y/A/B, and stick updates while packet stream is `active`. | not-run | `.evidence/phase4/input-haptic-transport/phase4-input-stream-001/` |
| `phase4-control-edge-001` | not-run | Short trigger/reload/X/Y/A/B presses appear as down/up edges without waiting for a later snapshot. | not-run | `.evidence/phase4/input-haptic-transport/phase4-control-edge-001/` |
| `phase4-motion-stream-001` | not-run | Desktop receives raw motion/provider updates while packet stream is `active`. | not-run | `.evidence/phase4/input-haptic-transport/phase4-motion-stream-001/` |

## Disconnect Recovery

| capture_id | Status | Expected | Observed | Sanitized evidence pointer |
|---|---|---|---|---|
| `phase4-disconnect-grace-001` | not-run | During a short reliable-control or LAN interruption, unchanged-session UDP may continue and packet stream shows `grace`. | not-run | `.evidence/phase4/input-haptic-transport/phase4-disconnect-grace-001/` |
| `phase4-stale-timeout-001` | not-run | After grace expires, active controls and stick axes clear while last raw motion remains visible with `stale` status. | not-run | `.evidence/phase4/input-haptic-transport/phase4-stale-timeout-001/` |
| `phase4-reconnect-reject-001` | not-run | After trusted reconnect and fresh stream config, old UDP input from the prior stream is rejected before apply. | not-run | `.evidence/phase4/input-haptic-transport/phase4-reconnect-reject-001/` |

## Phone Haptics

| capture_id | Status | Expected | Observed | Sanitized evidence pointer |
|---|---|---|---|---|
| `phase4-haptic-valid-001` | not-run | Valid desktop haptic command pulses the Android phone once and returns `started`. | not-run | `.evidence/phase4/input-haptic-transport/phase4-haptic-valid-001/` |
| `phase4-haptic-expired-001` | not-run | Expired desktop haptic command returns `expired` and does not pulse the phone. | not-run | `.evidence/phase4/input-haptic-transport/phase4-haptic-expired-001/` |
| `phase4-haptic-session-change-001` | not-run | Trusted session change cancels an active phone pulse and old stream input is rejected. | not-run | `.evidence/phase4/input-haptic-transport/phase4-haptic-session-change-001/` |
| `phase4-haptic-short-disconnect-001` | not-run | Short reliable-control disconnect without session change does not emit `cancelled` solely because of the disconnect. | not-run | `.evidence/phase4/input-haptic-transport/phase4-haptic-short-disconnect-001/` |

## Blockers

None recorded yet. Use one row per blocker and reference affected `capture_id` values.

| capture_id | Blocker | Next action |
|---|---|---|
| not-run | not-run | not-run |

## Final Verdict

Status: not-run

Notes: Physical Android, iPega gun, LAN, desktop receiver, and phone haptic smoke has not been run yet.
