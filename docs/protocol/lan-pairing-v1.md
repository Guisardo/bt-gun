# LAN Pairing v1

Phase 3 pairing is desktop initiated. Desktop opens one short-lived local session, shows a QR code as the normal path, and keeps a visible manual fallback for scan failures.

This contract covers local pairing, authenticated reliable control, liveness, diagnostics, minimal profile metadata, and the Phase 4 input-stream wire foundation. Later phases own desktop virtual joystick behavior, profile mapping, visualizer metrics, and full phone haptic execution behavior.

## QR URI

Scheme:

```text
btgun://pair
```

Query fields:

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `v` | integer | yes | Protocol version. Initial value is `1`. |
| `sid` | string | yes | Desktop-generated pairing session id. |
| `host` | string | yes | Selected active LAN IPv4 host. |
| `port` | integer | yes | Desktop pairing/control port. |
| `expires_at_epoch_millis` | integer | yes | Absolute expiry for one-time pairing material. |
| `desktop_spki_sha256` | lowercase hex string | yes | SHA-256 fingerprint of desktop Subject Public Key Info bytes. |
| `desktop_nonce` | lowercase hex string | yes | Per-session desktop nonce. |
| `qr_secret` | base64url string | yes | One-time secret shown only in the QR path. |

All fields are URL query encoded. Receivers must reject missing, duplicated, malformed, expired, oversized, or unsupported-version fields. Android must use the QR-provided `host` and `port` for the normal path and must not broaden a stale or unreachable endpoint into LAN scanning. Recovery is rescan or visible manual edit.

## Manual Fallback

Manual fallback is visible on the desktop surface but secondary to QR scan.

Fields shown:

| Field | Type | Meaning |
|-------|------|---------|
| Host | string | Same selected endpoint host as the QR payload. |
| Port | integer | Same endpoint port as the QR payload. |
| Code | six decimal digits | One-time code bound to the active desktop pairing session. |
| Fingerprint suffix | lowercase hex string | Last eight characters of `desktop_spki_sha256` for visual confirmation. |

The manual code has the same expiry as the QR material. Starting a new desktop pairing window replaces previous one-time QR and manual material.

Android manual entry validates nonblank host, port `1..65535`, a six-digit code, and enough lowercase fingerprint suffix characters to identify exactly one saved trusted desktop before any connection attempt. Manual connect uses the saved trusted desktop fingerprint metadata to recover the full SPKI hash, then sends the code over the pinned TLS control handshake. Desktop authenticates the code against its single active pairing session without Android sending or knowing `sid` or `desktop_nonce`. QR is still the first-trust path that can save full fingerprint metadata.

## QR Proof Transcript

Before any trusted control state is accepted through QR, Android proves possession of the one-time QR secret. The proof is an HMAC-SHA256 hex string using the QR secret as the HMAC key. This proof transcript defines replay, rate limit, and fingerprint mismatch handling for QR pairing.

Transcript string:

```text
btgun-pair-v1
sid={sid}
desktop_nonce={desktop_nonce}
android_nonce={android_nonce}
desktop_spki_sha256={desktop_spki_sha256}
one_time_material={qr_secret}
```

For QR, `one_time_material` is `qr_secret`. Field order is fixed. Implementations must reject proofs built with any different order, label, nonce, session id, fingerprint, or one-time material.

## Manual Code Authentication

Manual pairing does not use the QR proof transcript because Android does not know `sid` or `desktop_nonce`. Android opens the pinned TLS control channel using the saved desktop fingerprint and sends these authentication headers:

| Header | Meaning |
|--------|---------|
| `X-BT-Gun-Desktop-Fingerprint` | Full saved trusted desktop SPKI SHA-256 fingerprint. |
| `X-BT-Gun-Android-Nonce` | Fresh Android nonce for replay defense. |
| `X-BT-Gun-Manual-Code` | Six-digit manual code shown by the desktop. |

Desktop validates the fingerprint, current active session, expiry, rate limit, fresh Android nonce, and manual code. On success it consumes the active session and returns `session_ready` with the trusted `sessionId`; Android uses that `sessionId` for later control envelopes.

Rules:

- Pairing material expires with the desktop session TTL. v1 desktop sessions use a short 2-5 minute window.
- A session is single-use. After one accepted proof, the same `sid`, QR secret, or manual code cannot establish another trusted state.
- Android sends a fresh `android_nonce` per attempt. Desktop tracks nonces per active `sid` and rejects replay.
- Wrong QR secret, wrong manual code, malformed proof, reused nonce, unsupported version, and expired material are rejected before trusted control state exists.
- Desktop locks the active session after its configured failed-attempt limit and reports `rate_limited` pairing state.
- `desktop_spki_sha256` in the proof request must match the active desktop identity fingerprint. Fingerprint mismatch is rejected before trusted state exists.
- Desktop private key material stays local to the desktop identity store. One-time QR secrets, manual codes, and proof values are not durable metadata.

## Trust Anchor Behavior

Android durable trust is the desktop SPKI SHA-256 fingerprint. Desktop display name, host, and port are metadata only.

Trust validation states:

| State | Meaning |
|-------|---------|
| `first_trust` | No trusted row conflicts with the presented fingerprint, name, or endpoint. UI may ask the user to trust and then save after a successful QR-derived control connection. |
| `trusted` | Presented fingerprint matches a stored trusted desktop. |
| `missing` | Presented fingerprint is absent or malformed. |
| `fingerprint_mismatch` | Name or endpoint matches a stored desktop but the presented fingerprint differs. Do not overwrite the stored fingerprint silently. |

Mismatch handling must preserve the old stored fingerprint until an explicit re-pair/trust confirmation path saves new metadata. Android surfaces this as `trust_problem` / "Desktop identity changed". Trusted desktop reconnect is explicit user action, not silent primary reconnect.

## Reliable Control Channel

The reliable control channel is WebSocket-style over TLS using the pinned desktop SPKI fingerprint. Desktop accepts trusted control messages only after QR proof or manual code authentication succeeds. Android sends QR proof or manual-code authentication headers, then waits for a `session_ready` envelope before saving first trust or sending trusted control messages.

Control envelopes are JSON objects with these fields:

| Field | Type | Meaning |
|-------|------|---------|
| `v` | integer | Protocol version. Must be `1`. |
| `type` | string | One of the allowed control message type wire names. |
| `msgId` | string | Message id for diagnostics and local correlation. |
| `sessionId` | string | Trusted pairing session id. Desktop rejects mismatches. |
| `seq` | integer | Reliable-control sequence number. |
| `sentElapsedNanos` | integer | Sender monotonic elapsed timestamp. |
| `body` | object | Type-specific JSON object. Empty object is valid where no fields are defined. |

Receivers reject malformed JSON, oversized messages, unsupported versions, unknown types, missing required fields, invalid `sessionId`, and reserved haptic bodies. The default envelope size limit is 16 KiB.

Allowed `type` wire names:

| Type | Body contract |
|------|---------------|
| `pairing_state` | Pairing state and user-visible status only. |
| `session_ready` | Trusted control session ready state only. |
| `heartbeat_ping` | Empty body. Freshness signal. |
| `heartbeat_pong` | Empty body. Freshness signal. |
| `diagnostics` | Minimal diagnostics described below. |
| `profile_metadata` | Minimal profile metadata described below. |
| `input_stream_config` | Trusted UDP input stream parameters described below. |
| `reserved_haptic_command` | Empty body only in Phase 3. Payload shape and execution belong to Phase 4. |

## Input Stream Config

Desktop sends `input_stream_config` only after trusted control authentication. Android must not start UDP from QR/manual material alone.

Body fields:

| Field | Type | Meaning |
|-------|------|---------|
| `streamSessionIdHex` | 16-byte lowercase hex string | Random per-session UDP stream id. |
| `udpHost` | string | Desktop UDP receiver host selected for the trusted session. |
| `udpPort` | integer | Desktop UDP receiver port. |
| `hmacSha256KeyBase64Url` | base64url string | Random 32-byte stream authentication secret delivered only over trusted control. |
| `snapshotHz` | integer | Snapshot target rate. Default: `60`. |
| `frameAgeLimitMs` | integer | Maximum input frame age before desktop receiver drops it. Default: `150`. |
| `streamTimeoutMs` | integer | Receiver timeout before active buttons/pressed controls clear. Default: `250`. |
| `controlDisconnectGraceMs` | integer | Short UDP grace after reliable control disconnect. Default: `1500`. |

Receivers reject missing, malformed, empty, out-of-range, or wrong-session configs. The stream id and auth secret are scoped to one trusted control session and must be replaced after reconnect or session change.

## UDP Input Frames

Android sends fixed-size binary UDP frames authenticated with HMAC-SHA256 over bytes `0..87`; bytes `88..119` carry the full 32-byte tag. Multi-byte fields are big-endian. The frame size is always 120 bytes.

| Offset | Size | Field | Notes |
|--------|------|-------|-------|
| 0 | 4 | magic `BTGI` | Reject wrong datagrams. |
| 4 | 1 | version `1` | Reject unsupported schema versions. |
| 5 | 1 | frame type | `1=snapshot`, `2=edge`. |
| 6 | 2 | flags | Reserved, zero in v1. |
| 8 | 16 | stream session id | Must match trusted `input_stream_config`. |
| 24 | 8 | sequence | Monotonic per stream session across snapshot and edge frames. |
| 32 | 8 | capture elapsed nanos | Source capture timestamp. |
| 40 | 8 | send elapsed nanos | Android send timestamp. |
| 48 | 4 | button bitmask | Trigger, reload, X/Y/A/B, and edge flags. |
| 52 | 2 | stick X int16 | Normalized stick X in signed int16 range. |
| 54 | 2 | stick Y int16 | Normalized stick Y in signed int16 range. |
| 56 | 1 | motion provider | Compact provider id. |
| 57 | 1 | motion capability flags | Compact capability bits. |
| 58 | 2 | reserved | Zero in v1. |
| 60 | 4 | yaw float32 | Raw normalized motion field. |
| 64 | 4 | pitch float32 | Raw normalized motion field. |
| 68 | 4 | roll float32 | Raw normalized motion field. |
| 72 | 4 | raw aim X float32 or NaN | Raw motion-derived field only. |
| 76 | 4 | raw aim Y float32 or NaN | Raw motion-derived field only. |
| 80 | 8 | source sensor elapsed nanos | Sensor timestamp provenance. |
| 88 | 32 | HMAC-SHA256 tag | Full tag over bytes `0..87`. |

Desktop must reject malformed length, wrong magic, unsupported version, unknown type, wrong stream id, wrong trusted session, bad tag, duplicate or old sequence, and age-expired frames before applying input.

Snapshot frames are authoritative current state and repair dropped edges. Edge frames are opportunistic low-latency control changes. A late edge older than the newest accepted sequence must be dropped. Stream timeout clears active buttons/pressed controls; aim remains last-known and marked stale for downstream status.

Motion payload is raw provider/capability/yaw/pitch/roll/raw-aim data only. Android-local preview aim is not product mapping, and desktop profile mapping remains a later desktop responsibility.

## Heartbeat and Liveness

Heartbeat is bidirectional. Either `heartbeat_ping` or `heartbeat_pong` refreshes liveness at the observer's monotonic elapsed timestamp.

Default states:

| State | Meaning |
|-------|---------|
| `connected` | Last heartbeat is within the connected timeout. Default threshold: 1 second. |
| `degraded` | Heartbeat is stale but still within the disconnected timeout. Default threshold: 3 seconds. |
| `disconnected` | No heartbeat is available or the disconnected timeout elapsed. |
| `trust_problem` | Fingerprint mismatch or stored identity conflict blocks trust. |

Android and desktop UI may show heartbeat age in milliseconds or seconds for the control channel only.

## Diagnostics

Phase 3 diagnostics are limited to pairing/control status:

| Field | Type | Meaning |
|-------|------|---------|
| `sessionState` | string | Control session state such as `connected`, `degraded`, `disconnected`, `trust_problem`, or `rate_limited`. |
| `desktopIdentitySuffix` | string | Short fingerprint suffix for user confirmation. |
| `heartbeatAgeMillis` | integer or null | Current control heartbeat age, if known. |
| `lastControlError` | string or null | Last pairing/control error after secret redaction. |

Diagnostics must not include one-time secrets, full proof strings, private key material, later fast-stream metrics, or virtual-controller/profile mapping details.

## Profile Metadata

Phase 3 profile metadata is intentionally minimal:

| Field | Type | Meaning |
|-------|------|---------|
| `profileId` | string | Stable profile identifier. |
| `displayName` | string | User-visible profile name. |
| `revision` | integer | Monotonic metadata revision. |

Full profile storage, aim mapping, button mapping, sensitivity, inversion, dead-zone, smoothing, and platform backend capabilities belong to Phase 8 and later desktop phases.

## Redaction Gates

Diagnostics and logs must run through secret redaction before display or persistence.

Redact:

- `qr_secret`
- six-digit manual `code`
- `proof` and `pairing_proof` values
- private key markers or local key material

Allowed:

- session state names such as `pending`, `rate_limited`, and `trusted`
- endpoint metadata
- short fingerprint suffix for user confirmation

## Phase Boundary

Phase 3 defines pairing payload fields, proof verification, trust-anchor behavior, reliable control envelope validation, heartbeat/liveness, limited diagnostics, minimal profile metadata, and the `reserved_haptic_command` type name.

Later phases own:

- high-rate Android-to-desktop transport schemas and parsing
- desktop virtual-controller input handling
- phone haptic command body shape and execution outcomes
- profile mapping behavior and editing
- visualizer transport metrics

Phase 3 implementations must not vibrate the phone from desktop-origin commands, must not define reserved haptic body fields, and must reject non-empty `reserved_haptic_command` bodies.
