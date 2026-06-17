---
quick_id: 260617-sub
slug: fix-codex-subagent-schema
status: complete
completed: 2026-06-17T10:56:00-03:00
commit: this commit
files:
  - .codex/agents/btgun-software-architect.toml
  - .codex/agents/btgun-android-ux-hud-designer.toml
  - .codex/agents/btgun-bluetooth-specialist.toml
  - .codex/agents/btgun-android-hid-compat.toml
  - .codex/agents/btgun-windows-vhf-designer.toml
  - .codex/agents/btgun-macos-hid-strategy.toml
  - .codex/agents/btgun-gamepad-hid-specialist.toml
  - .codex/agents/btgun-lan-protocol-haptics.toml
  - .codex/agents/btgun-profile-calibration.toml
  - .codex/agents/btgun-test-replay-engineer.toml
  - .codex/agents/btgun-documenter-traceability.toml
  - .codex/agents/btgun-evidence-security-auditor.toml
  - AGENTS.md
  - .planning/STATE.md
---

# Quick Task Summary: Fix Codex Subagent Schema

## Result

Converted BT Gun custom Codex agents from old Markdown/frontmatter shape to current standalone TOML custom agent files.

## Schema Fix

- Added required `name`, `description`, and `developer_instructions`.
- Added `sandbox_mode = "read-only"` because all BT Gun agents are reviewers.
- Removed unsupported Claude-style body metadata: `tools: Read, Bash, Grep, Glob`.
- Kept role truth, read-first files, checks, and output contracts in terse developer instructions.
- Updated AGENTS generated guidance to `.codex/agents/*.toml`.

## Compression

`caveman-compress` script was attempted on existing temp Markdown draft `/private/tmp/btgun-agent-team/draft.md`.

- Sandbox run failed at Claude call.
- Escalated rerun was rejected because it would send project-specific draft text to external Claude without explicit export approval.
- Final TOML files apply caveman-compress rules manually: terse fragments, no filler, exact code/path preservation.
- No `.original.md` project backups created.

## Verification

- PASS: `python3 -c ...` parsed 12 TOML agents, required `name`/`description`/`developer_instructions`, basename/name match, `sandbox_mode = "read-only"`, and no stale `tools:` block in developer instructions.
- PASS: `rg --files .codex/agents` shows 12 `.toml` agent files and no `.md` agent files.
- PASS: `find .codex/agents -name '*.original.md' -print` returned nothing.
- PASS: `git diff --check`.
