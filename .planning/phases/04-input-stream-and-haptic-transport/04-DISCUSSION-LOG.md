# Phase 4: Input Stream and Haptic Transport - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-08T17:37:53Z
**Phase:** 4-Input Stream and Haptic Transport
**Areas discussed:** UDP input frames, Phone haptics, Recovery

---

## UDP Input Frames

| Option | Description | Selected |
|--------|-------------|----------|
| Full snapshot | Each frame has current buttons, stick axes, motion sample, provider flags, timestamps. Loss is cheap. | |
| Events/deltas | Smaller, but packet loss can create stuck buttons/aim state. | |
| Hybrid | Snapshots at fixed rate, event bursts for edges. More complex. | yes |

**User's choice:** Hybrid frames.
**Notes:** Hybrid must use snapshots as recovery source and edge frames for faster button/control response.

| Option | Description | Selected |
|--------|-------------|----------|
| 60 Hz snapshots + immediate control edges | Steady aim updates, low packet load, trigger/reload/buttons sent instantly. | yes |
| Sensor-rate snapshots + immediate control edges | Lowest motion delay, more UDP traffic and battery cost. | |
| 30 Hz snapshots + immediate control edges | Lighter load, but aim may feel less responsive. | |

**User's choice:** 60 Hz snapshots plus immediate control edges.
**Notes:** Planner can choose exact scheduling/jitter details.

| Option | Description | Selected |
|--------|-------------|----------|
| Raw normalized motion only | Provider, capability flags, raw aim/yaw/pitch/roll, timestamps; desktop profiles own final mapping. | yes |
| Raw plus Android preview aim | Include current Android preview aim too, useful debug but risks planner using it as product mapping. | |
| Minimal raw aim only | Smaller packet, weaker diagnostics and provider-specific tuning. | |

**User's choice:** Raw normalized motion only.
**Notes:** Android preview aim must not become product mapping.

| Option | Description | Selected |
|--------|-------------|----------|
| Binary UDP frame + debug decoder | Compact under latency budget, with docs/tests to inspect frames. | yes |
| JSON UDP frame first | Faster to inspect, worse fit for high-rate input. | |
| Agent decides | Planner picks exact binary layout and compatibility strategy. | |

**User's choice:** Binary UDP frame plus debug decoder.
**Notes:** Debuggability comes from decoder/docs/tests, not JSON packet body.

---

## Phone Haptics

| Option | Description | Selected |
|--------|-------------|----------|
| Pulse-first | Command id, strength, duration, TTL; optional pattern reserved but not required. | yes |
| Pulse + simple pattern now | Command id, strength, duration, TTL, optional on/off pattern. | |
| Full pattern now | Richer, more edge cases for Phase 4. | |

**User's choice:** Pulse-first.
**Notes:** Optional pattern fields can be reserved, but full pattern behavior is not required.

| Option | Description | Selected |
|--------|-------------|----------|
| Latest command wins | Cancel active phone vibration, play new valid command. | yes |
| Reject while busy | Simpler state, but game feedback may drop. | |
| Queue short commands | More complex, can feel stale. | |

**User's choice:** Latest valid command wins.
**Notes:** New valid command cancels active vibration first.

| Option | Description | Selected |
|--------|-------------|----------|
| Ack after command accepted and start attempted | Includes started, expired, unsupported, permission_blocked, failed, cancelled. | yes |
| Ack only after vibration duration ends | Clearer completion, slower feedback. | |
| Fire-and-forget plus diagnostics | Less code, does not satisfy ack/fail well. | |

**User's choice:** Ack after start attempt.
**Notes:** Result codes must distinguish start/fail cases.

| Option | Description | Selected |
|--------|-------------|----------|
| Drop if expired before Android starts it | Never play stale commands; return expired. | yes |
| Allow small grace window | May feel better on jittery LAN, but can play late. | |
| Agent decides | Planner sets conservative TTL rules. | |

**User's choice:** Drop expired commands before start.
**Notes:** Expired command must not vibrate phone.

---

## Recovery

| Option | Description | Selected |
|--------|-------------|----------|
| Strict session+sequence window | Reject wrong session, duplicate seq, old seq, and frames past age limit. | yes |
| Sequence only | Simpler, weaker against reconnect/stale packets. | |
| Agent decides | Planner sets security window. | |

**User's choice:** Strict session plus sequence plus age window.
**Notes:** Exact age/window values are planner discretion.

| Option | Description | Selected |
|--------|-------------|----------|
| Clear active controls and freeze/zero aim state | No stuck trigger/buttons after stream loss. | |
| Keep last state until reconnect | Preserves continuity but can leave stale controls. | |
| Clear buttons only | Aim remains last-known. | yes |

**User's choice:** Clear buttons only.
**Notes:** Aim should remain last-known, with stale/timeout state visible.

| Option | Description | Selected |
|--------|-------------|----------|
| Cancel immediately | Avoid stale feedback after link loss. | |
| Let current pulse finish | Smoother but violates strict cleanup. | |
| Cancel only on session change | Allows short disconnects to finish. | yes |

**User's choice:** Cancel only on session change.
**Notes:** Short disconnect does not automatically cancel current pulse.

| Option | Description | Selected |
|--------|-------------|----------|
| Require authenticated control session before accepting UDP again | No input until trust/control live. | |
| Allow brief UDP grace after control disconnect | Smoother but more complex/risky. | yes |
| Agent decides | Planner chooses conservative fail-closed. | |

**User's choice:** Allow brief UDP grace after control disconnect.
**Notes:** New or changed sessions still require fresh authenticated control.

## the agent's Discretion

- Exact binary layout, crypto details, replay-window size, frame age limit, UDP grace duration, and haptic strength scale.
- Exact haptic command/result wire names as long as required semantics and statuses are preserved.
- Whether to centralize shared protocol code or keep mirrored Android/desktop codecs with compatibility tests.

## Deferred Ideas

- Full haptic pattern playback.
- Physical gun motor rumble.
- Virtual joystick backends.
- Desktop profile mapping/editing.
- Visualizer latency and packet-loss dashboards.
