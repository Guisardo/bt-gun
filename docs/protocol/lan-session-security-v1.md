# LAN Session Security v1

This doc is the contract-level security map for the v1 LAN path. Canonical field layouts remain in `docs/protocol/lan-pairing-v1.md` and `docs/protocol/input-stream-v1-fixtures.md`; this page links them and names the required lifecycle, authentication, replay, haptic, diagnostics, fixture, and redaction rules.

## Scope

LAN is used for local desktop companion pairing/control, authenticated UDP input, diagnostics, replay, Windows VHF fallback, and phone haptics. It is not direct desktop-to-gun Bluetooth, physical gun motor output, or a custom desktop HID protocol.

## Canonical Schema Sources

| Contract | Source |
|----------|--------|
| QR pairing URI, fallback entry, proof transcript, trust anchor, control envelope | `docs/protocol/lan-pairing-v1.md` |
| UDP input stream config and lifecycle | `docs/protocol/lan-pairing-v1.md` |
| Fixed UDP frame offsets and fixture hex | `docs/protocol/input-stream-v1-fixtures.md` |
| Replay fixture corpus | `fixtures/replay/README.md`, `fixtures/replay/udp-golden/mapped-session-001.hex`, `fixtures/replay/expected/mapped-session-001-visualizer.json` |
| Diagnostic export contract | `docs/evidence/manifests/phase10-diagnostic-export.jsonl` |

Do not duplicate byte tables here. Update canonical schema docs first, then update this contract summary.

## Pairing Flow

1. Desktop creates one short-lived pairing session.
2. Desktop shows QR as normal path and a visible fallback entry path for scan failures.
3. Android parses endpoint and desktop identity material.
4. Android connects to the desktop reliable control endpoint.
5. Desktop verifies possession proof before trusted control state exists.
6. Desktop returns `session_ready` only after version, expiry, identity, nonce, rate-limit, and proof checks pass.
7. Android saves trusted desktop metadata by SPKI SHA-256 fingerprint after accepted first trust.

Pairing material is single-use. New pairing window replaces old one-time material.

## Authentication and Proof Rules

Trusted control state requires:

| Check | Requirement |
|-------|-------------|
| Protocol version | `v=1` only |
| Expiry | Pairing material still inside TTL |
| Desktop identity | Presented SPKI fingerprint matches active desktop identity |
| Android nonce | Fresh per attempt |
| Proof | HMAC-SHA256 transcript matches canonical field order |
| Attempts | Session not rate limited |
| Trust state | first trust, trusted match, or explicit re-pair path |

Reliable control envelopes must be rejected when malformed, oversized, unsupported-version, unknown-type, wrong-session, or body-invalid.

## Replay Guard

Replay protection exists at both pairing/control and UDP input layers.

| Layer | Guard |
|-------|-------|
| Pairing | fresh Android nonce, short TTL, single-use session, failed-attempt lockout |
| Reliable control | monotonic sequence per trusted session, strict session id validation |
| UDP input | stream id match, HMAC-SHA256 tag check, `InputReplayGuard` highest-sequence tracking |
| Stale input | Android-local capture-to-send budget plus desktop stream timeout and control grace |
| Reconnect | fresh `input_stream_config` after trusted session change or expired control grace |

`InputReplayGuard` rejects duplicate or old datagrams before mapped state or visualizer output can change.

## Lifecycle

| State | Meaning |
|-------|---------|
| `active` | Trusted control and current authenticated UDP stream can apply input. |
| `grace` | Reliable control disconnected briefly; unchanged-session UDP may continue until grace expires. |
| `stale` | Stream timed out or grace expired; active controls clear, last aim can remain visible as stale. |
| `stopped` | No trusted stream config is active; UDP must not apply. |

Session changes cancel active phone haptic ownership and require new stream config. Short control disconnect alone does not cancel an already-started valid phone pulse.

## Haptic Command and Result

Desktop haptics use reliable authenticated control, not UDP.

| Message | Direction | Contract |
|---------|-----------|----------|
| `reserved_haptic_command` | desktop to Android | command id, strength, duration, TTL, optional pattern |
| `haptic_result` | Android to desktop | command id, status, safe detail, observed elapsed timestamp |

Android validates every command before vibrating. Expired, malformed, wrong-session, unsupported-pattern, or permission-blocked commands must not vibrate. Current v1 output is Android phone vibration. Physical gun motor support is deferred.

## Diagnostics

Phase 10 diagnostics use five buckets on Android and desktop:

| Domain | Primary use |
|--------|-------------|
| `gun_ble` | physical gun connection and parser state |
| `sensor_motion` | Android sensor provider, calibration, recenter, aim quality |
| `lan_control_udp` | pairing, control, heartbeat, UDP auth/replay/lifecycle |
| `profile_mapping` | Android profile load/apply/mapped product stream state |
| `hid_backend_haptics` | Android HID, Windows VHF, output and phone haptic paths |

Diagnostic event status values are `ok`, `degraded`, `blocked`, `unsupported`, and `unknown`. Export bundles write sanitized `diagnostics.jsonl` plus manifest metadata; raw local evidence is excluded by default.

## Replay Fixtures

The v1 replay source is intentionally small:

- `fixtures/replay/udp-golden/mapped-session-001.hex`
- `fixtures/replay/udp-golden/mapped-session-001.jsonl`
- `fixtures/replay/expected/mapped-session-001-visualizer.json`
- `docs/evidence/manifests/phase10-replay-fixtures.jsonl`

Replay tests route datagrams through UDP decode/authentication, `InputReplayGuard`, mapped controller state, visualizer checklist rows, latency, and packet-loss metrics. Fixtures must stay sanitized and reviewable.

## Redaction Model

Committed docs, fixtures, manifests, diagnostics, and export bundles must exclude:

- pairing one-time values and fallback digits
- proof values and transcript secrets
- UDP auth material
- cryptographic signing material
- full Bluetooth-style addresses
- full phone or host hardware identifiers
- screen captures and capture dumps

Allowed evidence includes sanitized capture ids, suffix-only identity refs, status names, reason codes, fixture refs, manifest refs, and short non-identifying details.
