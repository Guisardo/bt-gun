# Phase 10: Diagnostics, Replay, and v1 Docs - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-15T16:16:39Z
**Phase:** 10-Diagnostics, Replay, and v1 Docs
**Areas discussed:** Replay artifact shape, Diagnostic failure taxonomy, Log/evidence redaction rules, Docs split, Known-limits wording

---

## Replay Artifact Shape

| Question | Selected | Alternatives |
|----------|----------|--------------|
| What should Phase 10 make canonical for replay tests? | Dual artifact: raw UDP fixture bytes/hex plus sanitized normalized JSONL/session snapshots. | Sanitized JSONL only; raw datagram bundle only; other. |
| Which raw-ish replay inputs should planner target first? | Golden + session clips: tiny committed golden datagrams and short sanitized session replay clips. | Golden only; long sanitized sessions; other. |
| What must replay tests prove? | Parser -> mapper -> visualizer: UDP decode/auth, Android-mapped profile state, visualizer metrics/checklist output. | Parser + mapper only; visualizer-focused; other. |
| Where should replay fixtures live? | `fixtures/replay/` plus docs/evidence manifests. | Under desktop tests; under docs/evidence only; other. |

**User's choice:** Options `1, 1, 1, 1`.
**Notes:** User chose the recommended full-chain replay path.

---

## Diagnostic Failure Taxonomy

| Question | Selected | Alternatives |
|----------|----------|--------------|
| What bucket model should Phase 10 use across Android + desktop diagnostics? | Five domain buckets: `gun_ble`, `sensor_motion`, `lan_control_udp`, `profile_mapping`, `hid_backend_haptics`. | More granular buckets; severity-first; other. |
| How should diagnostic logs/events be structured? | Stable structured event schema. | Human log lines + parser; separate schemas per platform; other. |
| Where should diagnostics show up? | Both UI + export. | UI only; export only; other. |
| What statuses should downstream planner lock? | `ok`, `degraded`, `blocked`, `unsupported`, `unknown`, plus reason code. | Freeform statuses; severity levels only; other. |

**User's choice:** Options `1, 1, 1, 1`.
**Notes:** User chose fixed domain/status model with machine-readable reason codes.

---

## Log/Evidence Redaction Rules

| Question | Selected | Alternatives |
|----------|----------|--------------|
| What is commit-safe? | Only sanitized summaries plus small fixtures. | Broader raw captures after redaction; no committed replay captures; other. |
| Which must Phase 10 explicitly ban from committed diagnostics/replay? | Secrets plus full identifiers; partial/truncated ids allowed. | Strict secret + identifier ban; secrets only; other. |
| How should allowed partial identifiers look? | Any truncated identifier. | Short stable suffix only; hash then suffix; other. |
| What should export include? | Replay-ready sanitized export bundle. | Troubleshooting bundle only; full local gitignored bundle; other. |

**User's choice:** Options `1, 3, 2, 1`.
**Notes:** User allows truncated identifiers after redaction, while full identifiers and secrets remain banned from committed artifacts.

---

## Docs Split

| Question | Selected | Alternatives |
|----------|----------|--------------|
| How should Phase 10 docs be organized? | Separate focused docs plus a v1 index. | One v1 guide; only update existing docs; other. |
| Who should docs serve first? | Developer/operator first. | End user first; maintainer first; other. |
| What must Android build/device-testing doc include? | Exact local workflow. | High-level only; CI-ish build only; other. |
| How deep should LAN protocol docs go? | Contract-level plus fixtures. | Narrative overview only; generated from code/tests; other. |

**User's choice:** Options `1, 1, 1, 1`.
**Notes:** User chose repeatability and implementation contracts over a single user-facing guide.

---

## Known-Limits Wording

| Question | Selected | Alternatives |
|----------|----------|--------------|
| How blunt should v1 known limits be? | Direct compatibility matrix. | User-friendly caveats; technical limitations only; other. |
| Which limits must be explicit pass/fallback/deferred rows? | All current v1 limits. | Only user-visible limits; only unresolved requirement limits; other. |
| How should docs frame Windows VHF fallback? | Equal primary path. | Retained working fallback; legacy fallback only; other. |
| When a limit is marked unsupported/deferred, what evidence must docs require? | Current evidence pointer plus next proof needed. | Statement only; evidence pointer only; other. |

**User's choice:** Options `1, 1, 2, 1`.
**Notes:** User wants macOS Android HID and Windows VHF presented as equal primary v1 OS-visible paths.

---

## the agent's Discretion

- Exact class names, file names, fixture schema fields, export command names, and reason-code names.
- Exact v1 index path and doc filenames.
- Exact replay-test placement, as long as final coverage proves UDP decode/authentication, mapped state, and visualizer output.

## Deferred Ideas

None.
