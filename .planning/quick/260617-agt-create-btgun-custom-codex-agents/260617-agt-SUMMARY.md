---
quick_id: 260617-agt
slug: create-btgun-custom-codex-agents
status: complete
completed: 2026-06-17T10:45:00-03:00
commit: this commit
files:
  - .codex/agents/btgun-software-architect.md
  - .codex/agents/btgun-android-ux-hud-designer.md
  - .codex/agents/btgun-bluetooth-specialist.md
  - .codex/agents/btgun-android-hid-compat.md
  - .codex/agents/btgun-windows-vhf-designer.md
  - .codex/agents/btgun-macos-hid-strategy.md
  - .codex/agents/btgun-gamepad-hid-specialist.md
  - .codex/agents/btgun-lan-protocol-haptics.md
  - .codex/agents/btgun-profile-calibration.md
  - .codex/agents/btgun-test-replay-engineer.md
  - .codex/agents/btgun-documenter-traceability.md
  - .codex/agents/btgun-evidence-security-auditor.md
  - AGENTS.md
  - .planning/STATE.md
---

# Quick Task Summary: Create BT Gun Custom Codex Agents

## Result

Created BT Gun specialist Codex agent team under `.codex/agents/` and refreshed AGENTS current-phase/source-tree guidance.

## Cavecrew Review Used

- Architecture/docs reviewer flagged stale Phase 1 AGENTS guidance, stale source-tree assumptions, Android-owned profile boundary, macOS Android HID primary path, LAN v2 protocol risk, and docs/status drift.
- Android/Bluetooth reviewer flagged missing BLE/HID/Camera2/profile/proof specialist views.
- Desktop/HID reviewer flagged Windows VHF, HID ABI, output-report haptics, macOS entitlement, and DriverKit fallback review needs.
- Test/evidence reviewer flagged acceptance/UAT drift, replay matrix gaps, ad hoc probe commands, and synthetic-key/redaction policy needs.

## Agents Added

- `btgun-software-architect`
- `btgun-android-ux-hud-designer`
- `btgun-bluetooth-specialist`
- `btgun-android-hid-compat`
- `btgun-windows-vhf-designer`
- `btgun-macos-hid-strategy`
- `btgun-gamepad-hid-specialist`
- `btgun-lan-protocol-haptics`
- `btgun-profile-calibration`
- `btgun-test-replay-engineer`
- `btgun-documenter-traceability`
- `btgun-evidence-security-auditor`

## Compression

Attempted `/Users/lucas.rancez/.agents/skills/caveman-compress/scripts` on `/private/tmp/btgun-agent-team/draft.md`.

- Sandbox run failed during Claude call after `pyenv` shim write warning.
- Escalated rerun also failed during Claude call.
- Original draft stayed untouched; no backup/compressed output was created.
- Final agent files apply caveman-compress rules manually: terse fragments, no filler, exact code/path preservation.

## Verification

- PASS: `ruby -ryaml -e ...` parsed all 12 agent frontmatter blocks and confirmed file basename/name match.
- PASS: `git diff --check`.
- PASS: no `.original.md` backup files under `.codex/agents/`.
