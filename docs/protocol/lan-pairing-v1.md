# LAN Pairing v1

Phase 3 pairing is desktop initiated. Desktop opens one short-lived local session, shows a QR code as the normal path, and keeps a visible manual fallback for scan failures.

This contract covers local pairing, authenticated reliable control, liveness, diagnostics, active Android profile metadata, Phase 4 input stream negotiation, Phase 8 mapped product stream semantics, input stream lifecycle recovery, and Phase 4 phone haptic command/result transport. Later phases own visualizer metrics, broader replay diagnostics, and full pattern haptic behavior.

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

Receivers reject malformed JSON, oversized messages, unsupported versions, unknown types, missing required fields, invalid `sessionId`, and malformed type-specific bodies. The default envelope size limit is 16 KiB.

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
| `reserved_haptic_command` | Phase 4 phone haptic command body described below. Wire name preserved from Phase 3. |
| `haptic_result` | Android phone haptic result body described below. |

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
| `frameAgeLimitMs` | integer | Sender-local frame-age budget from Android `captureElapsedNanos` to Android `sendElapsedNanos`. Desktop must not compare Android `sendElapsedNanos` to desktop receive time unless a trusted control-channel clock offset exists. Default: `150`. |
| `streamTimeoutMs` | integer | Receiver timeout before active buttons/pressed controls clear. Default: `250`. |
| `controlDisconnectGraceMs` | integer | Short UDP grace after reliable control disconnect. Default: `1500`. |

Receivers reject missing, malformed, empty, out-of-range, or wrong-session configs. The stream id and auth secret are scoped to one trusted control session and must be replaced after reconnect or session change. Replay protection uses monotonically increasing sequence numbers. Stale input protection uses `frameAgeLimitMs` for Android-local capture-to-send age plus `streamTimeoutMs` and `controlDisconnectGraceMs`; desktop receive-time age checks require an explicit sender-to-receiver clock offset.

## Phone Haptic Command and Result

Desktop sends phone haptic commands over the authenticated reliable control channel. Haptics do not use UDP, direct desktop-to-gun Bluetooth, BLE `fff5`, or physical gun motor output reports in v1.

`reserved_haptic_command` body fields:

| Field | Type | Meaning |
|-------|------|---------|
| `commandId` | nonblank string | Desktop command id for result correlation. |
| `strength` | number `0.0..1.0` | Normalized phone pulse strength. |
| `durationMs` | integer `1..1000` | Pulse duration. |
| `ttlMs` | integer `1..2000` | Relative TTL from Android receive time to start attempt. |
| `pattern` | string or null | Reserved. Non-null values return `unsupported` in Phase 4. |

Android accepts haptic commands only after trusted `session_ready` and only for the active session id. It validates every field before vibrating. Expired or unsupported commands must not vibrate the phone. A valid new pulse cancels any active phone vibration before starting. Android returns a result after command validation and the vibration start attempt, not after waiting for the pulse duration.

`haptic_result` body fields:

| Field | Type | Meaning |
|-------|------|---------|
| `commandId` | string | Command id being reported. |
| `status` | string | One of `started`, `expired`, `unsupported`, `permission_blocked`, `failed`, or `cancelled`. |
| `detail` | string | Short non-secret diagnostic detail. |
| `observedElapsedNanos` | integer | Android monotonic elapsed timestamp when result was observed. |

Result details must not include QR secrets, manual codes, proof values, stream keys, HMAC keys, private key material, or raw secret-bearing control payloads.

## UDP Input Frames

Android sends fixed-size binary UDP frames authenticated with HMAC-SHA256 over bytes `0..87`; bytes `88..119` carry the full 32-byte tag. Multi-byte fields are big-endian. The frame size is always 120 bytes.

Phase 8 changes the default product meaning of these frames: the UDP stream is a mapped product stream. Android profile mapping runs before HID/LAN output, so button bits, stick axes, and aim axes are already Android-mapped to the v1 gamepad-like shape. Desktop consumes these mapped values and displays active profile metadata; it does not own profile storage or mapping authority.

Raw provider/motion fields are optional raw debug extras. They are sent only when the Android `Send raw debug data` session toggle is enabled. Desktop cannot request raw extras in Phase 8. When raw debug is off, receivers must treat provider, capability, yaw, pitch, roll, raw aim, and source sensor timestamp as absent/debug-neutral even if legacy bytes are present.

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
| 48 | 4 | button bitmask | Android-mapped trigger, reload, X/Y/A/B, and edge flags. |
| 52 | 2 | stick X int16 | Android-mapped normalized stick X in signed int16 range. |
| 54 | 2 | stick Y int16 | Android-mapped normalized stick Y in signed int16 range. |
| 56 | 1 | motion provider | Raw debug compact provider id; absent/debug-neutral unless Android raw debug is on. |
| 57 | 1 | motion capability flags | Raw debug compact capability bits; absent/debug-neutral unless Android raw debug is on. |
| 58 | 2 | flags/debug state | Bit 0 may mark Android raw debug on; other bits reserved zero. |
| 60 | 4 | aim X float32 | Android-mapped aim X for product stream. |
| 64 | 4 | aim Y float32 | Android-mapped aim Y for product stream. |
| 68 | 4 | roll/debug float32 | Raw debug roll only when Android raw debug is on; otherwise NaN. |
| 72 | 4 | raw aim X float32 or NaN | Raw debug motion-derived field only when Android raw debug is on. |
| 76 | 4 | raw aim Y float32 or NaN | Raw debug motion-derived field only when Android raw debug is on. |
| 80 | 8 | source sensor elapsed nanos | Raw debug sensor timestamp provenance when Android raw debug is on; otherwise zero. |
| 88 | 32 | HMAC-SHA256 tag | Full tag over bytes `0..87`. |

Desktop must reject malformed length, wrong magic, unsupported version, unknown type, wrong stream id, wrong trusted session, bad tag, Android-local capture-to-send age beyond `frameAgeLimitMs`, and duplicate or old sequence before applying input. Desktop must not reject by comparing Android `sendElapsedNanos` to desktop receive time until a trusted sender-to-receiver clock offset is negotiated.

Snapshot frames are authoritative current state and repair dropped edges. Edge frames are opportunistic low-latency control changes. A late edge older than the newest accepted sequence must be dropped. Stream timeout clears active buttons/pressed controls; aim remains last-known and marked stale for downstream status.

Mapped product stream values are authoritative for product input. Raw provider/capability/yaw/pitch/roll/raw-aim values are debug-only and Android-session controlled. Desktop must not treat raw debug extras as profile input unless the active Android session reports raw debug on.

## Input Stream Lifecycle

Phase 4 uses these packet-stream lifecycle labels:

| Label | Meaning |
|-------|---------|
| `active` | Trusted control is connected and authenticated UDP frames for the current stream config can apply. |
| `grace` | Reliable control disconnected briefly; UDP for the unchanged session may continue until `controlDisconnectGraceMs` expires. |
| `stale` | Input timed out or disconnect grace expired. Active buttons/pressed controls are cleared, while last-known mapped aim stays visible as stale. |
| `stopped` | No trusted stream config is active. UDP input must not apply. |

Disconnect and reconnect rules:

- Android may keep sending unchanged-session UDP only inside `controlDisconnectGraceMs`. After grace expires it stops sending until a new trusted `input_stream_config` arrives.
- Desktop may keep applying unchanged-session UDP only inside `controlDisconnectGraceMs`. After grace expires it rejects frames with `control_grace_expired` and marks packet stream `stale`.
- A fresh `input_stream_config` resets stream id, auth secret, sequence guard, and stale state. Frames from the old stream id/key must be rejected before apply.
- A changed trusted control session clears sender and receiver state immediately. The new session needs fresh authenticated control before UDP input is trusted.
- Stream timeout uses the same receiver state path as replay rejection: buttons, pressed controls, and stick axes clear; mapped aim and last accepted sequence remain visible with `stale=true`; raw debug extras remain visible only when the Android session toggle is on.
- A short reliable-control disconnect does not cancel an already-started phone pulse. A control session change cancels the active phone pulse and reports `cancelled`.

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

Phase 8 profile metadata is intentionally minimal and read-only on desktop. It describes the active Android profile for display and diagnostics only. The source field is always `source=android`, and raw debug state reflects the Android `Send raw debug data` session toggle.

| Field | Type | Meaning |
|-------|------|---------|
| `profileId` | string | Stable profile identifier. |
| `displayName` | string | User-visible profile name. |
| `revision` | integer | Monotonic metadata revision. |
| `source` | string | Literal `android`; displayed as `source=android`. |
| `rawDebugEnabled` | boolean | Whether Android is adding raw debug extras for this session. |

Full profile storage, aim mapping, button mapping, sensitivity, inversion, dead-zone, smoothing, validation, and profile application belong to Android in Phase 8. Desktop only displays this metadata and mapped-stream state.

## Redaction Gates

Diagnostics and logs must run through secret redaction before display or persistence.

Redact:

- `qr_secret`
- six-digit manual `code`
- `proof` and `pairing_proof` values
- stream authentication secrets
- private key markers or local key material

Allowed:

- session state names such as `pending`, `rate_limited`, and `trusted`
- endpoint metadata
- short fingerprint suffix for user confirmation

## Phase Boundary

Phase 3 defines pairing payload fields, proof verification, trust-anchor behavior, reliable control envelope validation, heartbeat/liveness, limited diagnostics, minimal profile metadata, and the `reserved_haptic_command` type name. Phase 4 defines `input_stream_config`, authenticated UDP input frames, replay/timeout/disconnect recovery, `reserved_haptic_command` body, and `haptic_result` body.

Later phases own:

- desktop virtual-controller input handling
- full haptic pattern playback and physical gun motor rumble
- Android profile mapping behavior and editing
- visualizer transport metrics

Phase 3 implementations must not vibrate the phone from desktop-origin commands. Phase 4 implementations may accept non-empty `reserved_haptic_command` bodies only with the validated phone-haptic shape above.
