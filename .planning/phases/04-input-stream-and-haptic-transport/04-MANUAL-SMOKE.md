# Phase 04 Manual Smoke

Use this after automated Android and desktop tests pass. Goal: prove the real Android host and desktop companion recover from LAN/control interruptions without applying old input or stale phone haptics.

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

1. Press and release trigger. Expected: desktop receiver state updates trigger down/up.
2. Press and release reload. Expected: desktop receiver state updates reload down/up.
3. Press X, Y, A, and B. Expected: desktop receiver state shows each button in pressed controls while held, then clears on release.
4. Move the gun stick through left/right/up/down. Expected: desktop receiver stick axes update and return to zero when released.
5. Move the phone. Expected: desktop receiver raw motion fields update while packet stream remains active.

## Disconnect Recovery

1. While holding trigger or another button, interrupt reliable control or LAN briefly.
2. Expected during the configured grace window: unchanged-session UDP may continue and packet stream shows grace.
3. Wait past the grace window.
4. Expected: active buttons/pressed controls clear, stick axes clear, last-known raw aim/motion remains visible with stale status.
5. Reconnect through trusted control and wait for a fresh stream config.
6. Replay or resend an old UDP frame from the prior stream if available from diagnostics.
7. Expected: old frame is rejected before apply; packet stream returns active only for the new stream.

## Phone Haptics

1. Send a valid phone haptic command from desktop with nonzero strength, short duration, and unexpired TTL.
2. Expected: phone pulses once and Android returns `started`.
3. Send an expired haptic command.
4. Expected: phone does not pulse and Android returns `expired`.
5. Start a longer valid phone pulse.
6. Change the trusted control session by starting a new pairing session and reconnecting Android.
7. Expected: active pulse is cancelled, Android reports `cancelled`, and old input from the prior stream is rejected.
8. During a short reliable-control disconnect without session change, keep an already-started pulse running.
9. Expected: no cancel result is emitted solely because of the short disconnect.

## Pass Criteria

- Current-session input applies while active.
- Old UDP input cannot apply after fresh reconnect.
- Timeout clears active controls only; aim/motion stays last-known and stale.
- Valid haptic command pulses phone and returns `started`.
- Expired haptic command does not pulse and returns `expired`.
- Session change cancels active phone haptic and rejects old input.
