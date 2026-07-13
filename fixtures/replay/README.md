# Replay Fixtures

Phase 10 replay fixtures are committed, sanitized inputs for repeatable desktop tests.

## Layout

- `udp-golden/*.hex` stores reviewable UDP datagrams, one authenticated frame per non-comment line.
- `udp-golden/*.jsonl` stores sanitized decoded/session notes and shared Android/desktop replay matrices. First row is always schema and redaction policy.
- `expected/*.json` stores deterministic visualizer model and metrics snapshots.

## Shared Matrix

- `udp-golden/input-stream-v1-v2-matrix.jsonl` is the shared source for Android and desktop replay/static tests.
- Datagram rows cover v1/v2 good frames, bad HMAC, wrong stream, stale sender age, duplicate sequence, old sequence, and unexpected UDP format.
- Negotiation rows cover missing/null `frameFormat` fallback to v1 and unknown non-null `frameFormat` rejection.

## Redaction Policy

Committed replay files use small fixture datagrams and sanitized refs only. Do not add one-time pairing values, proof tokens, stream-auth material, private signing material, full device identifiers, unsanitized capture logs, or screen captures.

Use fixture ids, suffix-only refs, and manifest links to provenance under `docs/evidence/manifests/`.
