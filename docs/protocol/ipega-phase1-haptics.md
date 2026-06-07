# iPega Phase 1 Haptics

v1 feedback uses Android phone vibration. Physical gun motor rumble is deferred.

## Phone Haptic Proof

| capture_id | API path | requested | observed | evidence |
|------------|----------|-----------|----------|----------|
| `phone-vibrate-001` | Android `Vibrator` API | 1000 ms | phone vibrated, human confirmed | `.evidence/phase1/app-logs/phone-vibrate-001.logcat.txt` |

Normalized fixture: `fixtures/ipega/normalized/haptics.jsonl`.

## Deferred Physical Motor

- BLE `fff5` is a read/write candidate, but no verified physical gun motor command path exists.
- Reference-app `android.permission.VIBRATE` / Unity `Handheld.Vibrate` is treated as phone haptic evidence for v1.
- Physical gun motor command discovery moves to v2/deferred research unless a later capture proves a motor command path.

## v1 Decision

`DISC-06` is satisfied for v1 by `phone-vibrate-001`: Android log state `started`, 1000 ms duration, and human confirmation that the phone vibrated. This does not claim physical gun motor success.
