---
phase: 03
slug: lan-pairing-and-secure-session
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-11
---

# Phase 03 - Security

Retroactive threat verification for Phase 03 LAN pairing and secure session.

Scope is the Phase 03 pairing, trust, proof, reliable control, heartbeat, diagnostics, minimal profile metadata, and reserved haptic type surfaces. Later Phase 4+ UDP input, haptic command bodies/results, virtual joystick backends, profile mapping, and visualizer behavior are outside this Phase 03 security gate unless referenced by the Phase 03 threat register.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Desktop identity store to pairing UI/payload | Desktop private key stays local; QR/manual surfaces expose SPKI SHA-256 fingerprint data only. | Public fingerprint, fingerprint suffix, endpoint, pairing session metadata. |
| QR/manual user input to Android host | Android parses untrusted QR/manual pairing payloads before opening control sockets. | Endpoint, port, expiry, desktop fingerprint, nonce, QR secret, manual code suffix. |
| Android host to desktop control server | Pairing proof or manual code must authenticate before trusted control state exists. | HMAC proof headers, manual code header, Android nonce, desktop fingerprint. |
| Authenticated reliable control channel | Versioned WSS control envelopes carry only allowed Phase 03 message types before later transport phases. | Pairing state, session ready, heartbeat, diagnostics, profile metadata, reserved haptic type. |
| Package registry to Gradle builds | Phase 03 adds Ktor, OkHttp, Code Scanner, ZXing, and kotlinx.serialization dependencies. | Maven/Google Maven coordinates and transitive artifacts. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-03-01 | Spoofing | Desktop identity and trusted desktop store | mitigate | Desktop exposes SPKI SHA-256 fingerprint; Android validates by fingerprint, not name/host, and mismatch returns trust problem without overwrite. Evidence: `TrustedDesktopStoreTest.kt`, `DesktopControlClientTest.kt`, `PairingSecurityTest.kt`, UAT trust mismatch. | closed |
| T-03-02 | Spoofing/Tampering | Manual code and one-time pairing material | mitigate | Per-session 6-digit code and QR secret have TTL, bind to current active session, reject wrong values, and rate-limit failed attempts before trusted state. Evidence: `PairingSecurityTest.kt`, `PairingSessionRegistry.kt`, `03-UAT.md` manual fallback/wrong code. | closed |
| T-03-03 | Repudiation/Tampering | Pairing proof and replay protection | mitigate | HMAC transcript includes fixed label/order, session id, desktop nonce, Android nonce, fingerprint, and one-time material; accepted sessions are consumed and reused nonces are rejected. Evidence: desktop and Android `PairingProof`, `PairingSecurityTest.kt`, `TrustedDesktopStoreTest.kt`. | closed |
| T-03-04 | Elevation of Privilege | Control server/client trusted state | mitigate | `ControlServer` rejects trusted envelopes before proof/manual authentication; Android waits for `session_ready` before applying trusted config or haptic handling. Evidence: `ControlChannelTest.kt`, `DesktopControlClientTest.kt`. | closed |
| T-03-05 | Denial of Service | Pairing/control parsers and liveness | mitigate | QR/manual parser rejects malformed, expired, unsupported, and missing fields; control envelope codec rejects oversized, unsupported-version, unknown-type, and invalid fields; heartbeat state is timeout bounded. Evidence: `PairingPayloadTest.kt`, `ControlChannelTest.kt`, `DesktopControlClientTest.kt`. | closed |
| T-03-06 | Information Disclosure | UI, diagnostics, logs, docs | mitigate | Secret redaction hides QR secret, manual code, proof, and private-key material; diagnostics/profile metadata contain only bounded non-secret fields and fingerprint suffix. Evidence: `SecretRedactor`, `PairingSecurityTest.kt`, `ControlChannelTest.kt`, `TrustedDesktopStoreTest.kt`, `docs/protocol/lan-pairing-v1.md`. | closed |
| T-03-SC | Tampering | Maven dependencies | mitigate | Plan 03-01 required blocking package legitimacy approval before dependency edits; summaries list approved coordinates and Gradle files use those approved coordinates. Evidence: `03-01-SUMMARY.md`, `03-04-SUMMARY.md`, `03-07-SUMMARY.md`, Android/desktop Gradle files. | closed |

## Summary Threat Flags

| Plan | Summary Threat Flags |
|------|----------------------|
| 03-02 | None. QR/manual parser, trusted metadata storage, and dashboard diagnostics match the plan threat model. |
| 03-03 | None. Cryptographic proof, trust validation, and diagnostics redaction match the plan threat model. |
| 03-04 | None. WSS/client/server/control-envelope surface includes proof gating, fingerprint pin checks, byte limits, version allowlist, and type allowlist. |
| 03-05 | None. Liveness state is timeout-bounded, diagnostics exclude secrets and later transport metrics, and profile metadata excludes mappings. |
| 03-06 | None. UI displays endpoint/code/fingerprint suffix only, diagnostics go through redaction, and trusted control handling remains proof-gated. |
| 03-07 | None. QR/manual data becomes a service-owned control request, trusted reconnect is explicit, fingerprint mismatch fails closed, and service stop closes the socket. |
| 03-08 | None. Protocol docs cover fingerprint pinning, wrong-code/rate-limit validation, proof gating, and redaction requirements. |

## Verification Evidence

| Check | Result |
|-------|--------|
| Desktop security-focused gate | PASS: `gradle -p desktop-companion test --rerun-tasks --tests '*PairingSecurity*' --tests '*PairingSession*' --tests '*ControlChannel*'` |
| Android security-focused gate | PASS: `gradle -p android-host testDebugUnitTest --rerun-tasks --tests '*PairingPayload*' --tests '*TrustedDesktopStore*' --tests '*DesktopControlClient*' --tests '*DashboardState*' --tests '*HostSessionServiceLiveness*'` |
| UAT trust and runtime evidence | PASS: `03-UAT.md` records QR normal path, manual fallback/wrong code, trust mismatch, and heartbeat degradation passed. |
| Validation status | PASS: `03-VALIDATION.md` is `nyquist_compliant: true`. |

## Accepted Risks Log

No accepted risks.

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-11 | 7 | 7 | 0 | Codex secure-phase |

## Sign-Off

- [x] All threats have a disposition.
- [x] Accepted risks documented in Accepted Risks Log.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

**Approval:** verified 2026-06-11
