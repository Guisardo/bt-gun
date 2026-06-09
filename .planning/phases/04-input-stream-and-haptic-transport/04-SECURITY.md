---
phase: 04
slug: input-stream-and-haptic-transport
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-09
---

# Phase 04 - Security

Per-phase security contract for input-stream and haptic transport.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Android trusted control to desktop WSS | Pairing-authenticated reliable channel. | Session ids, stream config, haptic commands/results. |
| Android UDP sender to desktop UDP receiver | HMAC-authenticated low-latency input channel. | Fixed-size input frames with controls and raw motion. |
| Desktop UI to control runtime | Local smoke surface calls active control session. | Haptic smoke command and live packet state. |
| Android service to phone haptic executor | Trusted session command path into vibrator APIs. | Validated phone haptic command/result only. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-04-01 | Spoofing/Tampering | UDP codec | mitigate | Fixed 120-byte frame, magic/version/type/stream/HMAC validation before decode/apply. | closed |
| T-04-02 | Tampering | UDP frame/config | mitigate | Frame/config carry stream id, sequence, capture/send timestamps, and age limit. | closed |
| T-04-03 | Info Disclosure | UDP diagnostics | mitigate | Debug summaries omit QR/manual/proof/stream/HMAC/private material. | closed |
| T-04-04 | DoS | UDP parser | mitigate | Parser rejects non-120-byte datagrams before field parsing and uses fixed offsets. | closed |
| T-04-05 | EoP | Android control client | mitigate | Android ignores pre-ready non-ready messages, pins trusted sid, and requires matching session. | closed |
| T-04-06 | Tampering | Android UDP sender | mitigate | Sender builds frames only from trusted config and signs with HMAC codec before send. | closed |
| T-04-07 | Info Disclosure | Logs/docs | mitigate | Phase code has no logging calls; protocol redaction gate forbids secret material. | closed |
| T-04-08 | DoS | Android lifecycle | mitigate | Host service stops UDP sender on session replace/stop and schedules disconnect grace expiry. | closed |
| T-04-09 | Spoofing/Tampering | Desktop receiver | mitigate | Receiver authenticates/decodes before replay guard can accept input. | closed |
| T-04-10 | Tampering | Replay guard | mitigate | Rejects wrong session/stream, bad HMAC, malformed, duplicate, old, and Android-local age-expired frames before apply. | closed |
| T-04-11 | DoS | UDP parser | mitigate | No untrusted variable-size payload allocation in parser path. | closed |
| T-04-12 | Info Disclosure | Phase implementation | mitigate | No Phase 04 logging calls found; docs forbid secret-bearing diagnostics. | closed |
| T-04-13 | Tampering | Timeout handling | mitigate | Timeout clears buttons/sticks, marks stale, and preserves raw aim/motion only. | closed |
| T-04-14 | Spoofing/EoP | Haptic command path | mitigate | Desktop sends haptic only through active authenticated session; Android accepts only matching ready session. | closed |
| T-04-15 | Tampering | Haptic command validation | mitigate | Android validates command id, strength, duration, TTL, and pattern before vibrating. | closed |
| T-04-16 | DoS | Haptic executor | mitigate | Android enforces TTL and latest-valid-wins cancellation before starting a pulse. | closed |
| T-04-17 | Repudiation | Haptic results | mitigate | Android returns command id, status, detail, and observed elapsed nanos after validation/start. | closed |
| T-04-18 | Info Disclosure | Haptic details | mitigate | Result details are fixed/short and protocol forbids secret material. | closed |
| T-04-19 | Tampering/DoS | Disconnect grace | mitigate | Android stops after grace; desktop rejects after grace with `CONTROL_GRACE_EXPIRED`. | closed |
| T-04-20 | Tampering/DoS | Reconnect/reset | mitigate | Timeout/reconnect clear controls, reset guard, and reject old stream ids before apply. | closed |
| T-04-21 | DoS | Haptic/session lifecycle | mitigate | TTL/latest-valid-wins/session-change cancel exists and desktop clears pending haptic state on session change. | closed |
| T-04-22 | Info Disclosure | Manual smoke/docs | mitigate | Manual smoke contains no concrete secret values; protocol forbids logging secret values. | closed |
| T-04-23 | Spoofing/Tampering | Session replacement | mitigate | Session changes stop/reset UDP sender/receiver; desktop creates fresh stream id/key per trusted session. | closed |
| T-04-SC | Supply Chain | Dependencies | mitigate | No new production dependency added for Phase 04 transport/haptics. | closed |

## Key Evidence

- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/InputReplayGuard.kt:49` rejects Android-local capture-to-send age beyond `frameAgeLimitMs` without comparing Android and desktop monotonic clocks.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/InputReplayGuardTest.kt:79` keeps cross-device clock skew accepted and `:85` verifies `AGE_EXPIRED`.
- `desktop-companion/src/test/kotlin/com/btgun/desktop/transport/UdpInputReceiverTest.kt` verifies receiver-level `AGE_EXPIRED` rejection.
- `docs/protocol/lan-pairing-v1.md:146` and `:207` document sender-local age checks and explicitly defer Android-to-desktop receive-time checks until clock offset exists.

## Accepted Risks Log

No accepted risks.

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-09 | 24 | 23 | 1 | gsd-security-auditor |
| 2026-06-09 | 24 | 24 | 0 | Codex |
| 2026-06-09 | 24 | 24 | 0 | gsd-security-auditor rerun |

## Sign-Off

- [x] All threats have a disposition.
- [x] Accepted risks documented.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

**Approval:** verified 2026-06-09
