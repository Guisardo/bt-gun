# Phase 9: Visualizer Acceptance Path - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 9-Visualizer Acceptance Path
**Areas discussed:** Acceptance Path, Visualizer Surface, Live Display Shape, Metrics + Haptic Proof

---

## Acceptance Path

### Pass gate

| Option | Description | Selected |
|--------|-------------|----------|
| LAN visualizer | Desktop companion visualizer proves authenticated mapped UDP/control/haptic path; OS HID probes stay supporting evidence. | |
| Guided both-path | One acceptance flow proves LAN visualizer plus macOS Android HID or Windows VHF OS-visible behavior. | yes |
| OS-first | Visualizer primarily reads OS gamepad APIs; LAN remains diagnostics and haptic fallback. | |

**User's choice:** Guided both-path acceptance.
**Notes:** Later narrowed to both macOS Android HID and Windows VHF, not either/or.

### OS-visible path

| Option | Description | Selected |
|--------|-------------|----------|
| macOS primary, Windows fallback | Prove Android Bluetooth HID on macOS first; Windows VHF only if macOS path blocked. | |
| Either available path | Pass if macOS Android HID or Windows VHF is live and documented for that run. | |
| Both macOS + Windows | Require both target paths before Phase 09 passes. | yes |

**User's choice:** Both macOS Android HID and Windows VHF.
**Notes:** Phase 09 acceptance must cover both OS targets.

### Haptic proof

| Option | Description | Selected |
|--------|-------------|----------|
| LAN haptic required | Visualizer haptic button must vibrate Android phone through authenticated LAN; macOS HID haptic row reports unsupported/deferred. | |
| Per-platform haptic rows | LAN haptic required; Windows VHF output-to-phone haptic required; macOS HID output may be unsupported with evidence. | yes |
| Windows-only haptic | Haptic pass comes from Windows VHF output; LAN button is just convenience. | |

**User's choice:** Per-platform haptic rows.
**Notes:** macOS HID output remains known unsupported/deferred when evidence supports it.

### Final acceptance record

| Option | Description | Selected |
|--------|-------------|----------|
| Guided manual checklist | Visualizer shows steps/pass rows; user confirms physical gun, macOS, Windows, haptic observations. | yes |
| Scripted evidence bundle | Command generates sanitized JSONL/JUnit rows; manual confirmation only for physical feel. | |
| Both checklist + bundle | Visualizer drives checklist and exports sanitized evidence rows for Phase 10 docs/replay. | |

**User's choice:** Guided manual checklist.
**Notes:** Generated evidence bundle is not the primary Phase 9 pass artifact.

---

## Visualizer Surface

### Visualizer location

| Option | Description | Selected |
|--------|-------------|----------|
| Second Swing window | Keep pairing window focused; add `VisualizerWindow` sharing `ControlServer` state. | yes |
| Extend PairingWindow | One window, faster path, but risks dense pairing/acceptance UI. | |
| New UI stack | Compose/JavaFX visualizer, nicer UI, but new dependencies and patterns. | |

**User's choice:** Second Swing window.
**Notes:** Reuse the current Swing desktop pattern.

### Open behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Button from PairingWindow | `Open visualizer` visible after app launch; same desktop companion process. | |
| Separate Gradle app/task | Run visualizer directly for acceptance, pairing window separate. | |
| Auto-open after auth | Visualizer appears automatically when Android authenticates. | yes |

**User's choice:** Auto-open after auth.
**Notes:** The visualizer should appear when the trusted session is ready.

### Manual reopen

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, PairingWindow button | Auto-open on auth, plus manual reopen if closed. | yes |
| Menu/shortcut only | Less visible, still recoverable. | |
| No manual reopen | Closing visualizer means restart/re-auth. | |

**User's choice:** Yes, PairingWindow button.
**Notes:** PairingWindow remains the recovery surface.

### Disconnect lifecycle

| Option | Description | Selected |
|--------|-------------|----------|
| Stay open, show stale/disconnected | Preserves test context; rows turn stale/fail until reconnect. | yes |
| Close on disconnect | Clear signal but user loses checklist state. | |
| Reset on every new session | Keep window open but clear checklist/proof rows when session changes. | |

**User's choice:** Stay open, show stale/disconnected.
**Notes:** Do not close or clear the acceptance context on transient disconnect.

---

## Live Display Shape

### Top layout

| Option | Description | Selected |
|--------|-------------|----------|
| Checklist + live gamepad | Pass rows across LAN/macOS/Windows, plus buttons/axes panel. | yes |
| Live gamepad first | Big axes/buttons, checklist below. | |
| Diagnostics first | Connection/latency/loss/haptic rows first, controls below. | |

**User's choice:** Checklist + live gamepad.
**Notes:** Acceptance progress and live input should be visible immediately.

### Controls display

| Option | Description | Selected |
|--------|-------------|----------|
| Gamepad-like panel | Six button indicators, stick crosshair, aim crosshair, stale overlay. | yes |
| Table rows | Compact list of control name/value/timestamp/source. | |
| Both panel + rows | Panel for feel, rows for exact values. | |

**User's choice:** Gamepad-like panel.
**Notes:** Dense rows are not the primary display.

### Recenter and event history

| Option | Description | Selected |
|--------|-------------|----------|
| Status row + short event strip | Current aim-zero/recenter status plus last 10 product events. | yes |
| Recenter status only | No event history unless diagnostics expanded. | |
| Full event timeline | Scrolling log of all accepted inputs/events. | |

**User's choice:** Status row + short event strip.
**Notes:** Use a short product event history, not a full log.

### Raw debug display

| Option | Description | Selected |
|--------|-------------|----------|
| Collapsed debug drawer | Visible `raw debug on/off`; raw provider/yaw/pitch/roll available behind expand. | yes |
| Always visible | Raw provider/motion values shown in main visualizer. | |
| Hidden in Phase 09 | Visualizer only shows mapped product stream. | |

**User's choice:** Collapsed debug drawer.
**Notes:** Raw debug is secondary and should not crowd the main acceptance view.

---

## Metrics + Haptic Proof

### Headline latency

| Option | Description | Selected |
|--------|-------------|----------|
| Capture-to-visualizer update | Android capture timestamp to Swing visualizer render/update, target `<50 ms`. | yes |
| Capture-to-desktop receive | Easier, but misses UI update cost. | |
| Multiple lanes | Show capture-to-send, send-to-receive, receive-to-render separately; headline total. | |

**User's choice:** Capture-to-visualizer update.
**Notes:** This is the main v1 latency acceptance metric.

### Packet loss

| Option | Description | Selected |
|--------|-------------|----------|
| Simple expected/missed counter | Derive from accepted UDP sequence gaps; show current session loss count and percent. | yes |
| Rolling window | Last 10s/30s loss rate plus total. | |
| Detailed stream table | Rejected reasons, duplicates, old sequence, malformed, HMAC, timeout. | |

**User's choice:** Simple expected/missed counter.
**Notes:** Detailed rejection reasons can remain secondary diagnostics.

### Haptic test

| Option | Description | Selected |
|--------|-------------|----------|
| One-click LAN pulse | Send authenticated LAN phone haptic, show queued/result/confirmed row. | |
| LAN + Windows output sequence | Button triggers LAN pulse, then prompts Windows VHF output proof. | yes |
| Checklist rows only | No button; user runs existing haptic commands outside visualizer. | |

**User's choice:** LAN + Windows output sequence.
**Notes:** Visualizer haptic flow must cover both authenticated LAN and Windows VHF output proof.

### Checklist pass

| Option | Description | Selected |
|--------|-------------|----------|
| Observed live state + user confirm | Visualizer detects state when possible, user confirms physical/macOS/Windows observations. | yes |
| Detected state only | No user confirmation unless hardware path cannot be sensed. | |
| User confirm only | Visualizer is guide; no automated pass/fail logic. | |

**User's choice:** Observed live state + user confirm.
**Notes:** Some acceptance rows need manual confirmation because the app cannot sense physical feel or external OS UI directly.

---

## the agent's Discretion

- Exact Swing layout, component names, row ids, indicator styling, update cadence, and metrics helper shape.
- Exact secondary diagnostics beyond the required headline latency and simple packet loss display.

## Deferred Ideas

None.
