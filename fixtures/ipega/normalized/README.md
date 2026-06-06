# iPega Phase 1 Normalized Fixtures

Normalized fixtures are JSON Lines files under this directory. Each non-empty line is one JSON object using schema `btgun.ipega.normalized.v1`.

Required event fields:

| Field | Required | Meaning |
|-------|----------|---------|
| `schema` | yes | Must be `btgun.ipega.normalized.v1`. |
| `fixture_id` | yes | Stable fixture sequence id, for example `trigger-001`. |
| `seq` | yes | Positive integer sequence number inside the fixture. |
| `control` | yes | Physical or logical control, for example `trigger`, `reload`, `stick_x`, `x`, or `rumble`. |
| `kind` | yes | Event kind: `button`, `axis`, `connection`, `handshake`, or `rumble_test`. |
| `phase` | yes | Event phase: `down`, `up`, `move`, `observed`, `command`, `ack`, or `fail`. |
| `value` | yes | Normalized button value, axis value, object payload, or observed status. |
| `raw_ref` | yes | `local://.evidence/phase1/raw/...`, `local://.evidence/phase1/hci/...`, or app-log ref. |
| `clue_id` | yes | Static clue id that motivated this capture/test. |
| `capture_id` | yes | Capture manifest row id linking hardware evidence to this normalized event. |

Coverage rules:

- Static clues are hypotheses until linked to hardware capture rows and normalized fixtures.
- Verified status is only valid when static clue, hardware capture, and normalized fixture are all linked.
- Large raw blobs, HCI logs, app logs, and decompile output stay in ignored `.evidence/phase1/` paths.
- Committed JSONL fixtures should be small, replayable, and tied back to `docs/evidence/manifests/phase1-captures.jsonl`.

Minimal example:

```jsonl
{"schema":"btgun.ipega.normalized.v1","fixture_id":"trigger-001","seq":1,"control":"trigger","kind":"button","phase":"down","value":1,"raw_ref":"local://.evidence/phase1/raw/trigger-001.bin","clue_id":"ARGUN2021-BT-001","capture_id":"trigger-001"}
```
