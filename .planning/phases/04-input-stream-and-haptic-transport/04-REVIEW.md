---
phase: 04-input-stream-and-haptic-transport
reviewed: 2026-06-08T23:43:03Z
depth: deep
files_reviewed: 5
files_reviewed_list:
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/pairing/PairingSessionRegistry.kt
  - docs/protocol/lan-pairing-v1.md
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
---

# Phase 04: Code Review Report

**Reviewed:** 2026-06-08T23:43:03Z
**Depth:** deep
**Files Reviewed:** 5
**Status:** clean

## Summary

Deep final re-review after commit `d8be19d fix(04): use active pairing endpoint for UDP config`.

Stale CR-01 is resolved. `ControlServer` no longer freezes constructor-time UDP defaults for the UI path: `start()` reads the active pairing endpoint before TLS startup, QR authentication preserves the pending endpoint before `PairingSessionRegistry.consumeSession()` clears `activeSession`, manual-code authentication does the same, and `freshInputStreamConfig()` emits the current `activeUdpHost` / `activeUdpPort`. `PairingWindow` still constructs `ControlServer` before `startPairing()`, but `startControlServer(current)` now causes the config endpoint to come from `current.endpoint`, not `127.0.0.1:41731`.

The regression `controlServerUsesPairingEndpointForInputStreamConfigWhenConstructedBeforePairing()` covers the stale-blocker sequence: construct `ControlServer`, then create pairing, authenticate, build `input_stream_config`, and assert host/port match the pairing endpoint and do not use the loopback fallback.

Prior review findings remain represented as resolved: desktop no longer applies cross-device monotonic frame-age rejection before clock-offset negotiation; Android trusted stream/haptic handling remains gated on `session_ready`; authenticated malformed UDP fields are covered by malformed rejection; Android heartbeat timeout now schedules UDP disconnect grace; haptic result `detail` survives callback-to-envelope; no-stream disconnect stays `stopped`; `AGE_EXPIRED` is absent; protocol docs reserve `frameAgeLimitMs` until sender-to-receiver clock offset exists.

Verification performed:

- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ControlChannelTest*'` - passed.

All reviewed files meet quality standards. No issues found.

## Narrative Findings (AI reviewer)

No critical, warning, or info findings.

---

_Reviewed: 2026-06-08T23:43:03Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
