# Phase 3: LAN Pairing and Secure Session - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-07T16:28:16Z
**Phase:** 3-LAN Pairing and Secure Session
**Areas discussed:** Pairing entry flow, Discovery/addressing, Session security, Reliable control channel

---

## Pairing Entry Flow

### What is normal pairing flow?

| Option | Description | Selected |
|--------|-------------|----------|
| Desktop QR | Desktop starts session, shows QR with endpoint plus one-time secret; Android scans, pairs, then stores session. | Yes |
| Pair code first | Desktop shows short code; Android enters it. Slower, but works without camera. | |
| Android discovers | Android scans LAN services first, then user picks desktop. Less typing, more network-discovery risk. | |

**User's choice:** Desktop QR
**Notes:** Desktop starts a local pairing session and shows a QR code; Android scans the QR to pair.

### When QR scan fails, what fallback should v1 provide?

| Option | Description | Selected |
|--------|-------------|----------|
| Pair code fallback | Same desktop session also shows a short code; Android can enter code instead of scanning QR. | |
| Manual endpoint fallback | Android can type IP/port plus code. Useful for debugging, noisy for normal users. | Yes |
| No fallback in v1 | QR only; simpler but brittle on devices without camera permission or QR scan issues. | |

**User's choice:** Manual endpoint fallback
**Notes:** Android can type IP/port plus pairing code as fallback/debug path; this is not the normal user path.

### After first successful pairing, what should happen next time?

| Option | Description | Selected |
|--------|-------------|----------|
| Remember trusted desktop | Android stores desktop identity/session metadata; user taps saved desktop to reconnect, with reauth if needed. | Yes |
| Pair every session | Always scan QR/code again. Simpler and safer, worse daily use. | |
| Auto-reconnect | Android reconnects to last desktop when live session starts. Fastest, more risk if wrong network/desktop present. | |

**User's choice:** Remember trusted desktop
**Notes:** Android stores desktop identity/session metadata and lets user choose the saved desktop later, with reauthentication when needed.

### What should QR contain?

| Option | Description | Selected |
|--------|-------------|----------|
| Endpoint + one-time secret | Host/IP, port, session id, protocol version, expiry, public key/fingerprint or nonce material. Planner can choose exact schema. | Yes |
| Secret only | Android uses LAN discovery for endpoint, QR only authenticates. Cleaner QR, depends on discovery. | |
| Full config bundle | Include endpoint, secret, desktop name, capabilities, profile hints. Convenient, but risks bloated/stale QR. | |

**User's choice:** Endpoint + one-time secret
**Notes:** QR should include endpoint information, session id/protocol version/expiry, and one-time authentication material; exact schema is planner discretion.

---

## Discovery/addressing

### How should Android find desktop in the normal path?

| Option | Description | Selected |
|--------|-------------|----------|
| QR endpoint primary | QR carries LAN host/IP plus port. Android connects directly. Lowest v1 ambiguity. | Yes |
| QR + verify with discovery | QR carries identity/secret; Android also checks LAN discovery for same desktop. More robust, more moving parts. | |
| NSD/mDNS primary | Android discovers `_btgun._tcp` service, QR/code authenticates only. Cleaner URLs, but discovery can be flaky. | |

**User's choice:** QR endpoint primary
**Notes:** QR carries LAN host/IP and port; Android connects directly. LAN discovery is not required for the v1 normal path.

### What address should desktop advertise in QR?

| Option | Description | Selected |
|--------|-------------|----------|
| Best local IPv4 | Desktop chooses active LAN IPv4 plus port; simple for home Wi-Fi and manual fallback. | Yes |
| Hostname + IPv4 | Include desktop hostname and IP; Android tries both. More resilient, QR schema a bit larger. | |
| All candidates | Include multiple interfaces/IPs. Useful on VPNs/docks, but can confuse users and planner. | |

**User's choice:** Best local IPv4
**Notes:** Desktop chooses active LAN IPv4 plus port for home Wi-Fi and manual fallback simplicity.

### How much manual network setup belongs in v1 UI?

| Option | Description | Selected |
|--------|-------------|----------|
| Debug/fallback only | Manual IP/port/code hidden behind fallback/debug path; normal flow stays QR. | |
| Visible fallback button | Main pairing screen has "Enter manually" next to scan QR. | Yes |
| Full manual mode | Manual IP/port first-class; good for dev, worse product feel. | |

**User's choice:** Visible fallback button
**Notes:** Main pairing screen should expose an Enter manually path next to QR scanning, but QR remains the normal path.

### If QR endpoint is stale or unreachable, what should Android do?

| Option | Description | Selected |
|--------|-------------|----------|
| Clear error + retry scan/manual | Show unreachable reason, allow rescan or manual edit; no silent scanning. | Yes |
| Try LAN discovery automatically | Attempt NSD/mDNS fallback before error. More helpful, but adds discovery scope. | |
| Ask desktop to refresh QR | Android only tells user to refresh desktop session. Simple but less helpful. | |

**User's choice:** Clear error + retry scan/manual
**Notes:** Show unreachable reason and allow rescan or manual edit. Do not silently broaden into discovery.

---

## Session security

### What security baseline should Phase 3 lock?

| Option | Description | Selected |
|--------|-------------|----------|
| Authenticated encrypted control channel | Pairing secret establishes encrypted/authenticated local session before any trusted control messages. | Yes |
| Authenticated only | Sign/MAC messages but no encryption. Simpler, weaker privacy on LAN. | |
| Pairing token only | One-time token gates connect, then plaintext channel. Fast but not acceptable if we care about LAN attackers. | |

**User's choice:** Authenticated encrypted control channel
**Notes:** Pairing secret establishes an encrypted and authenticated local session before any trusted control messages.

### How long should one-time pairing secret live?

| Option | Description | Selected |
|--------|-------------|----------|
| Short TTL 2-5 min | Enough to scan/type, limits stale QR reuse. | Yes |
| Very short 60s | Safer but annoying during debugging or slow setup. | |
| Until desktop cancels | Convenient, but stale pairing windows linger. | |

**User's choice:** Short TTL 2-5 min
**Notes:** Secret should live long enough to scan or type, but stale QR/code reuse must be limited.

### Pairing code shape for manual fallback?

| Option | Description | Selected |
|--------|-------------|----------|
| 6 digits + TTL | Easy to type; rely on short TTL, rate limit, session binding. | Yes |
| 8 chars alphanumeric | Stronger, harder to type correctly. | |
| Long phrase/token | Strongest manual secret, poor UX. | |

**User's choice:** 6 digits + TTL
**Notes:** Use short TTL, rate limiting, and session binding to make a 6-digit code acceptable for local fallback.

### What identity should Android remember for trusted desktop?

| Option | Description | Selected |
|--------|-------------|----------|
| Desktop public key/fingerprint | Stable trust anchor; reconnect can detect impersonation or changed desktop identity. | Yes |
| Desktop name + IP | Simple display identity, weak security because IP/name can change or collide. | |
| Session id only | Easy implementation, not a durable trust identity. | |

**User's choice:** Desktop public key/fingerprint
**Notes:** Desktop public key or fingerprint is the durable trust anchor. Android should detect changed or impersonated desktop identity.

---

## Reliable control channel

### What transport should Phase 3 establish?

| Option | Description | Selected |
|--------|-------------|----------|
| WebSocket over encrypted session | Reliable bidirectional messages, easy desktop UI/debug tooling, natural heartbeat. | Yes |
| Raw TCP framed protocol | Lean and portable, more custom framing/debug work. | |
| HTTP polling | Simple request model, poor fit for heartbeat and desktop-to-Android events. | |

**User's choice:** WebSocket over encrypted session
**Notes:** Use a reliable bidirectional control channel with heartbeat-friendly semantics and debuggable desktop tooling.

### Which messages belong in Phase 3?

| Option | Description | Selected |
|--------|-------------|----------|
| Pairing/session/heartbeat/diagnostics only | Establish channel and liveness now; haptic execution waits Phase 4. | |
| Add profile metadata too | Include placeholder/default profile metadata now for later desktop profiles. | |
| Add haptic command stubs | Define command envelope now but do not vibrate until Phase 4. | Yes |

**User's choice:** Add haptic command stubs
**Notes:** User wanted haptic command stubs, but scope was narrowed to schema/lifecycle reservation only; execution remains Phase 4.

### How should Phase 3 represent haptic stubs?

| Option | Description | Selected |
|--------|-------------|----------|
| Declared unsupported capability | Control channel can report `phone_haptics=false/pending_phase_4`; no haptic command schema yet. | |
| Envelope reserved only | Define generic control message envelope and reserve `haptic_command` type for Phase 4. | Yes |
| Full haptic schema, disabled handler | Define command id/strength/duration/TTL now, but handler returns not-implemented. More Phase 4 leakage. | |

**User's choice:** Envelope reserved only
**Notes:** Define a generic control message envelope and reserve the haptic command type for Phase 4. Do not define full haptic payload semantics in Phase 3.

### Heartbeat/liveness behavior?

| Option | Description | Selected |
|--------|-------------|----------|
| Bidirectional heartbeat + timeout | Both sides send/expect periodic ping/pong; UI shows connected/degraded/disconnected. | Yes |
| Desktop-driven heartbeat | Desktop pings, Android replies. Simpler, less symmetric. | |
| Connection-close only | Rely on socket errors. Too slow/ambiguous for LAN UX. | |

**User's choice:** Bidirectional heartbeat + timeout
**Notes:** Both sides send or expect periodic ping/pong; UI shows connected, degraded, or disconnected.

### What diagnostics should show in Phase 3?

| Option | Description | Selected |
|--------|-------------|----------|
| Pairing/control status only | Session state, desktop identity, heartbeat age, last control error, no packet-loss/latency metrics yet. | Yes |
| Add basic RTT | Include heartbeat round-trip time; useful early latency signal, but visualizer latency still Phase 9. | |
| Full transport metrics | Packet loss, jitter, frame rates now. This belongs more to Phase 4/9. | |

**User's choice:** Pairing/control status only
**Notes:** Show session state, desktop identity, heartbeat age, and last control error. Packet-loss and visualizer latency metrics wait for later phases.

---

## the agent's Discretion

- Exact QR payload format, URI scheme, serialization, key agreement, authenticated encryption mechanism, WebSocket library, control message names, timeout intervals, desktop scaffold shape, and Android storage mechanism.
- Minimal profile metadata needed to satisfy `TRAN-06`; full profile storage and mapping remain Phase 8.

## Deferred Ideas

- High-rate UDP input frames and stale/replay input rejection remain Phase 4.
- Phone haptic command payloads, execution, TTL handling, and ack/fail semantics remain Phase 4.
- Full profile editing/mapping remains Phase 8.
- Packet loss, jitter, frame-rate, and visualizer latency metrics remain later diagnostics/visualizer phases.
