---
status: testing
phase: 03-lan-pairing-and-secure-session
source: [03-VERIFICATION.md, 03-MANUAL-SMOKE.md]
started: 2026-06-08T12:20:05Z
updated: 2026-06-08T16:49:38Z
---

# Phase 03 UAT

## Current Test

number: 4
name: Heartbeat degradation
expected: |
  Connected becomes degraded, then disconnected; packet stream stays inactive and no phone haptic command executes.
awaiting: none

## Tests

### 1. QR normal path

expected: Android uses QR host/port without manual IP entry, proves identity, saves trusted desktop metadata, and reaches connected/authenticated control state.
result: passed
evidence: Desktop reached authenticated state; Android reached connected state with no control error after QR scan.

### 2. Manual fallback and wrong code

expected: Valid manual entry uses only host/port, 6-digit code, and trusted desktop fingerprint suffix; wrong code and expired material fail before trusted state and show clear recovery.
result: passed
evidence: User reported UAT test 2 passed after the UX fix removed desktop challenge/session id from phone manual entry.

### 3. Trust mismatch

expected: Android shows Desktop identity changed/trust problem and does not silently overwrite the stored fingerprint.
result: passed
evidence: Agent executed on SM-A750G by seeding a temporary trusted fingerprint, sending a QR with a different fingerprint, and visually confirming Android showed "Desktop identity changed"; trusted store retained the old fingerprint and was restored to the original desktop row afterward. User also reported UAT test 3 passed with the changed desktop identity flow.

### 4. Heartbeat degradation

expected: Connected becomes degraded, then disconnected; packet stream stays inactive and no phone haptic command executes.
result: passed
evidence: User reported UAT test 4 passed after Android heartbeat liveness poller deployment.

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None. Automated verification passed; physical/manual smoke remains.
