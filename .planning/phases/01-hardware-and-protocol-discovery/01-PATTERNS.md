# Phase 01: Hardware and Protocol Discovery - Pattern Map

**Mapped:** 2026-06-06
**Files analyzed:** 14 planned new/modified files
**Analogs found:** 0 / 14

No production source tree exists yet. Pattern compliance for this phase comes from `01-CONTEXT.md`, `01-RESEARCH.md`, and project research docs, not existing code analogs.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `.gitignore` | config | file-I/O | none | no-analog |
| `docs/protocol/ipega-phase1-inventory.md` | evidence doc | static analysis | none | no-analog |
| `docs/protocol/ipega-phase1-clues.md` | evidence doc | static analysis -> hypothesis | none | no-analog |
| `docs/protocol/ipega-phase1-hardware.md` | evidence doc | hardware capture -> summary | none | no-analog |
| `docs/protocol/ipega-phase1-haptics.md` | evidence doc | haptic command -> outcome | none | no-analog |
| `docs/evidence/manifests/phase1-captures.jsonl` | manifest | capture refs -> fixture refs | none | no-analog |
| `fixtures/ipega/normalized/README.md` | fixture doc | schema reference | none | no-analog |
| `fixtures/ipega/normalized/handshake.jsonl` | fixture | raw refs -> normalized event | none | no-analog |
| `fixtures/ipega/normalized/trigger.jsonl` | fixture | raw refs -> normalized event | none | no-analog |
| `fixtures/ipega/normalized/reload.jsonl` | fixture | raw refs -> normalized event | none | no-analog |
| `fixtures/ipega/normalized/joystick.jsonl` | fixture | raw refs -> normalized event | none | no-analog |
| `fixtures/ipega/normalized/buttons-xyab.jsonl` | fixture | raw refs -> normalized event | none | no-analog |
| `fixtures/ipega/normalized/haptics.jsonl` | fixture | command refs -> normalized outcome | none | no-analog |
| `tools/phase1/validate-fixtures.mjs` | validation helper | file-I/O -> coverage report | none | no-analog |

## Pattern Assignments

### No Existing Code Analogs

There is no app/runtime source tree to copy from. Plans must instead reference:

- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md` for locked decisions D-01 through D-15.
- `.planning/phases/01-hardware-and-protocol-discovery/01-RESEARCH.md` for recommended structure, fixture schema examples, threat patterns, and validation architecture.
- `.planning/research/ARCHITECTURE.md` for the Android gun adapter -> normalized event boundary.
- `.planning/research/PITFALLS.md` for standard-gamepad, static-only, and haptic/deferred motor failure modes.

## Shared Patterns

- Evidence docs must distinguish static clue, hardware capture, normalized fixture, and final verification status.
- JSONL fixtures must be parseable one object per line and include enough fields to link back to raw refs and clue ids.
- Large raw captures and decompile output must stay out of git; committed manifests point at stable local ignored paths.
- Diagnostic Android work must be labeled throwaway Phase 1 validation tooling and must not define production app/LAN/desktop architecture.
- Any package-manager dependency added during execution must be preceded by a blocking human verification checkpoint.
