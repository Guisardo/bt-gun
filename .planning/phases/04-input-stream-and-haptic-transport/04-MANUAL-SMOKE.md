# Phase 04 Manual Smoke

Use this after automated Android and desktop tests pass. Goal: prove the real Android host and desktop companion recover from LAN/control interruptions without applying old input or stale phone haptics.

Record results in `04-PHYSICAL-SMOKE-RESULTS.md`. Keep raw screenshots, logs, or packet captures only under `.evidence/phase4/input-haptic-transport/`. Commit only sanitized notes and stable `capture_id` values.

## Setup

1. Build and install the Android host app on the test phone.
2. Start the desktop companion on the same LAN.
3. Start a desktop pairing session and pair Android by QR. Use the visible pairing-code fallback only if QR scan is unavailable.
4. Start the Android live gun session and connect to the trusted desktop.
5. Confirm Android dashboard shows:
   - Gun connection: connected.
   - Desktop link: connected.
   - Packet stream: active.
   - Phone haptic: available or started/failed status from the last local test.

## Input Stream

| capture_id | Action | Expected |
|---|---|---|
| `phase4-input-stream-001` | Press/release trigger and reload; press/release X, Y, A, and B; move stick left/right/up/down. | Desktop receiver state updates trigger, reload, X/Y/A/B, and stick while packet stream remains `active`; released controls clear. |
| `phase4-control-edge-001` | Tap trigger, reload, X/Y/A/B with short presses. | Desktop receiver shows button down/up edges without waiting for a later snapshot. |
| `phase4-motion-stream-001` | Move the phone while stream stays connected. | Desktop receiver raw motion/provider fields update while packet stream remains `active`. |

## Disconnect Recovery

| capture_id | Action | Expected |
|---|---|---|
| `phase4-disconnect-grace-001` | While holding trigger or another button, interrupt reliable control or LAN briefly. | During the configured grace window, unchanged-session UDP may continue and packet stream shows `grace`. |
| `phase4-stale-timeout-001` | Keep the interruption past the grace window. | Active buttons/pressed controls clear, stick axes clear, and last-known raw aim/motion remains visible with `stale` status. |
| `phase4-reconnect-reject-001` | Reconnect through trusted control, wait for a fresh stream config, then replay or resend an old UDP frame from the prior stream if diagnostics allow it. | Old frame is rejected before apply; packet stream returns `active` only for the new stream. Mark `blocked` if replay/resend tooling is unavailable. |

## Phone Haptics

| capture_id | Action | Expected |
|---|---|---|
| `phase4-haptic-valid-001` | Send a valid phone haptic command from desktop with nonzero strength, short duration, and unexpired TTL. | Phone pulses once and Android returns `started`. |
| `phase4-haptic-expired-001` | Send an expired haptic command. | Phone does not pulse and Android returns `expired`. |
| `phase4-haptic-session-change-001` | Start a longer valid phone pulse, then change the trusted control session by starting a new pairing session and reconnecting Android. | Active pulse is cancelled, Android reports `cancelled`, and old input from the prior stream is rejected. |
| `phase4-haptic-short-disconnect-001` | During a short reliable-control disconnect without session change, keep an already-started pulse running. | No cancel result is emitted solely because of the short disconnect. |

## Pass Criteria

- Current-session input applies while active.
- Old UDP input cannot apply after fresh reconnect.
- Timeout clears active controls only; aim/motion stays last-known and stale.
- Valid haptic command pulses phone and returns `started`.
- Expired haptic command does not pulse and returns `expired`.
- Session change cancels active phone haptic and rejects old input.
