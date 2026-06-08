# Input Stream v1 Fixtures

Golden UDP frame fixtures for the Phase 4 binary input stream contract.

Both Android and desktop tests use these identifiers:

- `GOLDEN_SNAPSHOT_FRAME_HEX`
- `GOLDEN_EDGE_FRAME_HEX`

Shared fixture config:

| Field | Value |
|-------|-------|
| Magic | `BTGI` |
| Version | `1` |
| Snapshot type | `1` |
| Edge type | `2` |
| Stream session id | `00112233445566778899aabbccddeeff` |
| HMAC key hex | `0123456789abcdeffedcba98765432100123456789abcdeffedcba9876543210` |
| HMAC key base64url | `ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA` |
| Frame size | `120` bytes |
| HMAC tag size | `32` bytes |
| HMAC input | bytes `0..87` |
| HMAC tag | bytes `88..119` |

## Field Offsets

| Offset | Size | Field |
|--------|------|-------|
| 0 | 4 | magic `BTGI` |
| 4 | 1 | version |
| 5 | 1 | frame type |
| 6 | 2 | flags |
| 8 | 16 | stream session id |
| 24 | 8 | sequence |
| 32 | 8 | capture elapsed nanos |
| 40 | 8 | send elapsed nanos |
| 48 | 4 | button bitmask |
| 52 | 2 | stick X int16 |
| 54 | 2 | stick Y int16 |
| 56 | 1 | motion provider |
| 57 | 1 | motion capability flags |
| 58 | 2 | reserved |
| 60 | 4 | yaw float32 |
| 64 | 4 | pitch float32 |
| 68 | 4 | roll float32 |
| 72 | 4 | raw aim X float32 or NaN |
| 76 | 4 | raw aim Y float32 or NaN |
| 80 | 8 | source sensor elapsed nanos |
| 88 | 32 | HMAC-SHA256 tag |

## GOLDEN_SNAPSHOT_FRAME_HEX

```text
425447490101000000112233445566778899aabbccddeeff000000000000002a00000000423a35c700000000423a3636000000233039cfc7020700003fa00000c02000003f4000003e000000be80000000000000423a3558ad0f94e008b50a045111a7bbb25688c2f1d399a8de4b3b8f2e325c0f63fb7d5f
```

Snapshot decoded fields:

| Field | Value |
|-------|-------|
| sequence | `42` |
| captureElapsedNanos | `1111111111` |
| sendElapsedNanos | `1111111222` |
| buttonBitmask | `0x00000023` |
| stickX | `12345` |
| stickY | `-12345` |
| motionProvider | `2` |
| motionCapabilityFlags | `0x07` |
| yaw | `1.25` |
| pitch | `-2.5` |
| roll | `0.75` |
| rawAimX | `0.125` |
| rawAimY | `-0.25` |
| sourceSensorElapsedNanos | `1111111000` |

## GOLDEN_EDGE_FRAME_HEX

```text
425447490102000000112233445566778899aabbccddeeff000000000000002b00000000423a36a500000000423a37140000010180007fff03030000bf8000003f000000400000007fc000007fc0000000000000423a36843b9a10ccf01f62a02db4cc6065db9d133b1f4e20e1b4f8c74579b672755e8d24
```

Edge decoded fields:

| Field | Value |
|-------|-------|
| sequence | `43` |
| captureElapsedNanos | `1111111333` |
| sendElapsedNanos | `1111111444` |
| buttonBitmask | `0x00000101` |
| stickX | `-32768` |
| stickY | `32767` |
| motionProvider | `3` |
| motionCapabilityFlags | `0x03` |
| yaw | `-1.0` |
| pitch | `0.5` |
| roll | `2.0` |
| rawAimX | `NaN` |
| rawAimY | `NaN` |
| sourceSensorElapsedNanos | `1111111300` |

## Boundary Rules

- UDP payloads are fixed binary frames, not JSON.
- Motion fields are raw provider/capability/yaw/pitch/roll/raw-aim values only.
- Android preview aim, desktop profile mapping, QR secrets, manual codes, proof material, and HMAC keys must not appear in debug output.
