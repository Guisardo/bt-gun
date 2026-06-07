# LAN Pairing v1

Phase 3 pairing is desktop initiated. Desktop opens one short-lived local session, shows a QR code as the normal path, and keeps a visible manual fallback for scan failures.

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

All fields are URL query encoded. Receivers must reject missing, duplicated, malformed, expired, or unsupported-version fields.

## Manual Fallback

Manual fallback is visible on the desktop surface but secondary to QR scan.

Fields shown:

| Field | Type | Meaning |
|-------|------|---------|
| Host | string | Same selected endpoint host as the QR payload. |
| Port | integer | Same endpoint port as the QR payload. |
| Code | six decimal digits | One-time code bound to the active `sid`. |
| Fingerprint suffix | lowercase hex string | Last eight characters of `desktop_spki_sha256` for visual confirmation. |
| Session id | string | Same `sid`, used internally for binding. |

The manual code has the same expiry as the QR material. Starting a new desktop pairing window replaces previous one-time QR and manual material.

## Proof Transcript

Before any trusted control state is accepted, Android proves possession of the one-time QR secret or manual code. The proof is an HMAC-SHA256 hex string using the one-time material as the HMAC key.
This proof transcript defines replay, rate limit, and fingerprint mismatch handling for pairing.

Transcript string:

```text
btgun-pair-v1
sid={sid}
desktop_nonce={desktop_nonce}
android_nonce={android_nonce}
desktop_spki_sha256={desktop_spki_sha256}
one_time_material={qr_secret-or-manual-code}
```

Field order is fixed. Implementations must reject proofs built with any different order, label, nonce, session id, fingerprint, or one-time material.

Rules:

- Pairing material expires with the desktop session TTL. v1 desktop sessions use a short 2-5 minute window.
- A session is single-use. After one accepted proof, the same `sid`, QR secret, or manual code cannot establish another trusted state.
- Android sends a fresh `android_nonce` per attempt. Desktop tracks nonces per active `sid` and rejects replay.
- Wrong QR secret, wrong manual code, malformed proof, and reused nonce fail closed without returning trusted control state.
- Desktop locks the active session after its configured failed-attempt limit and reports `rate_limited` pairing state.
- `desktop_spki_sha256` in the proof request must match the active desktop identity fingerprint. Fingerprint mismatch fails before trusted state exists.

## Trust Anchor Behavior

Android durable trust is the desktop SPKI SHA-256 fingerprint. Desktop display name, host, and port are metadata only.

Trust validation states:

| State | Meaning |
|-------|---------|
| `first_trust` | No trusted row conflicts with the presented fingerprint, name, or endpoint. UI may ask the user to trust and then save. |
| `trusted` | Presented fingerprint matches a stored trusted desktop. |
| `missing` | Presented fingerprint is absent or malformed. |
| `fingerprint_mismatch` | Name or endpoint matches a stored desktop but the presented fingerprint differs. Do not overwrite the stored fingerprint silently. |

Mismatch handling must preserve the old stored fingerprint until an explicit re-pair/trust confirmation path saves new metadata.

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

## Security Notes

- Desktop private key material stays local to the desktop identity store.
- Durable desktop identity is the SPKI SHA-256 fingerprint, not host or display name.
- One-time QR secrets and manual codes are not durable metadata.
- Logs and diagnostics must redact QR secrets, manual codes, HMAC proof strings, and private key material.

## Phase Boundary

This document covers only pairing payload fields, proof verification, trust-anchor behavior, and control-foundation security for Phase 3. High-rate transport and phone feedback command bodies are later-phase contracts.
