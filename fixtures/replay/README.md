# Replay Fixtures

Phase 10 replay fixtures are committed, sanitized inputs for repeatable desktop tests.

## Layout

- `udp-golden/*.hex` stores reviewable UDP datagrams, one authenticated frame per non-comment line.
- `udp-golden/*.jsonl` stores sanitized decoded/session notes. First row is always schema and redaction policy.
- `expected/*.json` stores deterministic visualizer model and metrics snapshots.

## Redaction Policy

Committed replay files must not contain pairing codes, QR secrets, proof values, stream keys, HMAC material, private keys, full Bluetooth addresses, full serials, full Android IDs, raw logs, or raw screenshots.

Use sanitized fixture ids, suffix-only refs, and manifest links to provenance under `docs/evidence/manifests/`.
