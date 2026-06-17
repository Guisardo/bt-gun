---
quick_id: 260617-agt
slug: create-btgun-custom-codex-agents
status: planned
created: 2026-06-17T10:30:00-03:00
---

# Quick Task: Create BT Gun Custom Codex Agents

## Objective

Create compressed project-local Codex agent definitions from cavecrew review findings so future BT Gun work gets specialist review viewpoints.

## Scope

- Add `.codex/agents/*.md` definitions.
- Refresh `AGENTS.md` stale current-phase/source-tree guidance enough that future agents load Phase 11 truth.
- Track this quick task in `.planning/quick/` and `.planning/STATE.md`.

## Tasks

1. Gather review input.
   - Use cavecrew-style reviewers across architecture/docs, Android/Bluetooth, desktop/HID, and test/evidence.
   - Convert their role suggestions into a compact team.

2. Create agent definitions.
   - Use project-local Codex agent `.md` format.
   - Keep content agent-facing and caveman-compressed.
   - Include required reads, project truth, checks, and output contract.

3. Validate.
   - Run caveman-compress if available; otherwise apply its compression rules manually and record failure.
   - Verify agent definition schema shape and file list.

## Acceptance

- Software architect, Android designer, Windows designer, tester, documenter, gamepad specialist, Bluetooth specialist, and extra project-specific agents exist under `.codex/agents/`.
- Agent definitions are concise and tied to BT Gun requirements/docs/code.
- GSD quick summary records cavecrew review and compression/validation result.
