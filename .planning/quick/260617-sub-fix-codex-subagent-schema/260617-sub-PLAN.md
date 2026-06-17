---
quick_id: 260617-sub
slug: fix-codex-subagent-schema
status: planned
created: 2026-06-17T10:56:00-03:00
---

# Quick Task: Fix Codex Subagent Schema

## Objective

Convert BT Gun custom agent definitions to current Codex subagent TOML schema.

## Scope

- Read current OpenAI Codex subagents docs.
- Replace `.codex/agents/*.md` definitions with `.codex/agents/*.toml`.
- Preserve specialist review roles, read lists, truth checks, and output contracts.
- Apply caveman-compress rules without creating project backup files.
- Refresh AGENTS path guidance and GSD state.

## Tasks

1. Audit old agent definitions against current Codex schema.
2. Convert each agent to required `name`, `description`, `developer_instructions`.
3. Remove stale Claude-style `tools:` body metadata.
4. Validate TOML parse, required fields, filename/name match, no `.md` leftovers, no project `.original.md` backups.

## Acceptance

- All project custom agents are TOML files under `.codex/agents/`.
- Each file parses as TOML and has required Codex fields.
- Instructions stay terse, agent-facing, and BT Gun-specific.
- Project guidance points future agents to `.codex/agents/*.toml`.
