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

## Security Notes

- Desktop private key material stays local to the desktop identity store.
- Durable desktop identity is the SPKI SHA-256 fingerprint, not host or display name.
- One-time QR secrets and manual codes are not durable metadata.
- Logs and diagnostics must redact QR secrets, manual codes, HMAC proof strings, and private key material.

## Phase Boundary

This document covers only pairing payload fields and desktop manual fallback fields for Phase 3 Plan 01. High-rate input transport, visualizer timing metrics, and phone feedback command bodies are later-phase contracts.
