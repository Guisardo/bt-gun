# Phase 10: Diagnostics, Replay, and v1 Docs - Context

**Gathered:** 2026-06-15T16:16:39Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 10 makes the v1 MVP repeatable, diagnosable, and documented after the Phase 9 visualizer pass. It delivers replay tests for packet/session artifacts, structured diagnostics that separate gun, sensor, LAN, profile, and OS-visible controller failures, and developer/operator docs for Android setup, LAN protocol/security, troubleshooting, replay, and known v1 limits.

This phase does not add new game presets, direct desktop-to-gun Bluetooth, physical gun motor rumble, new HID report shapes, new profile ownership, or a new macOS virtual HID path. It captures and hardens the completed v1 behavior.

</domain>

<decisions>
## Implementation Decisions

### Replay Artifacts
- **D-01:** Phase 10 replay uses dual artifacts: raw UDP fixture bytes/hex plus sanitized normalized JSONL/session snapshots.
- **D-02:** The first replay corpus should be small: committed golden datagrams plus short sanitized session clips, not long raw sessions.
- **D-03:** Replay tests must prove the chain from UDP decode/authentication through Android-mapped profile state to visualizer model, metrics, and checklist output.
- **D-04:** Committed replay fixtures live under `fixtures/replay/`. Evidence manifests link those fixtures back to sanitized live-evidence provenance.

### Diagnostics
- **D-05:** Diagnostics use five domain buckets across Android and desktop: `gun_ble`, `sensor_motion`, `lan_control_udp`, `profile_mapping`, and `hid_backend_haptics`.
- **D-06:** Diagnostic events use a stable structured schema with at least `schema`, `ts_elapsed`, `domain`, `status`, `reason_code`, `detail`, `session_refs`, and redacted context fields.
- **D-07:** Diagnostics show concise live status in Android dashboard and desktop visualizer, and can also produce a sanitized export bundle for issue replay.
- **D-08:** Diagnostic statuses are a small fixed set: `ok`, `degraded`, `blocked`, `unsupported`, and `unknown`. Machine-readable `reason_code` carries detail.

### Redaction and Evidence Safety
- **D-09:** Commit only sanitized summaries, small replay fixtures, sanitized JSONL, and manifest rows. Raw logs and screenshots stay gitignored by default.
- **D-10:** Committed diagnostics/replay must ban secrets and full identifiers: pairing codes, QR secrets, proof values, stream keys, HMAC material, private keys, full Bluetooth addresses, full serials, full Android IDs, and raw screenshots/log dumps.
- **D-11:** Truncated identifiers are allowed when useful after redaction. Full identifiers remain banned from committed artifacts.
- **D-12:** The sanitized export bundle should be replay-ready and include diagnostics JSONL, replay clips, app/build versions, capability statuses, and a manifest pointer. It should not include raw logs by default.

### Documentation
- **D-13:** Phase 10 documentation is split into focused docs plus a v1 index. Target docs include Android build/device testing, LAN protocol/security, diagnostics/replay troubleshooting, and known limits.
- **D-14:** Docs serve developers/operators first: build, install, test, replay, and troubleshoot in repeatable order. End-user polish is secondary.
- **D-15:** Android setup docs must include exact local workflow: Gradle/JDK/SDK expectations, permissions, install, USB/logcat capture, real gun steps, Android Bluetooth HID mode, and common blockers.
- **D-16:** LAN protocol/security docs stay contract-level with schemas, field tables, authentication/replay rules, lifecycle, haptic messages, and replay fixture references.

### Known Limits
- **D-17:** Known limits use a direct compatibility matrix with clear supported, unsupported, fallback, and deferred rows. Do not soften the status language.
- **D-18:** Required known-limit rows include no direct desktop-to-gun Bluetooth, physical gun motor rumble deferred, no game-specific presets, macOS HID haptics unsupported/deferred, Android HID phone compatibility risk, and Windows VHF path availability.
- **D-19:** Docs frame macOS Android Bluetooth HID and Windows VHF as equal primary v1 OS-visible paths, not as one true primary plus a barely-mentioned fallback.
- **D-20:** Every unsupported/deferred row needs a current evidence pointer and the next proof needed to change status.

### the agent's Discretion
- Choose exact class names, package locations, file names, fixture schema fields, export command names, and reason-code names as long as the decisions above remain true.
- Choose exact v1 index path and doc file names, provided Android setup, LAN protocol/security, diagnostics/replay, and known limits remain separate and discoverable.
- Choose whether replay tests are implemented first on desktop, Android, or both, provided final coverage proves decode/authentication, mapped state, and visualizer output.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 10 goal, requirements, success criteria, dependencies, and v1 closeout boundary.
- `.planning/REQUIREMENTS.md` - `PERF-04`, `PERF-05`, `PACK-01`, `PACK-04`, and `PACK-05`.
- `.planning/PROJECT.md` - v1 product intent, Android host plus desktop companion architecture, OS-visible gamepad shape, and phone-haptic decision.
- `.planning/STATE.md` - current accumulated decisions after Phase 9.

### Prior Phase Context
- `.planning/phases/09-visualizer-acceptance-path/09-CONTEXT.md` - visualizer model, checklist, latency, packet loss, haptic proof, and Phase 10 boundary.
- `.planning/phases/08-desktop-profiles-and-mapping/08-CONTEXT.md` - Android-owned profile mapping, mapped product stream, raw-debug toggle, and desktop read-only metadata.
- `.planning/phases/07-macos-virtual-joystick-path/07-CONTEXT.md` - Android Bluetooth HID path, macOS output limitation handling, and Windows VHF fallback precedent.

### Protocol, Setup, and Evidence Docs
- `docs/protocol/lan-pairing-v1.md` - pairing/control channel, authenticated UDP input stream, profile metadata, haptic command/result, lifecycle, redaction gates, and current diagnostics scope.
- `docs/protocol/input-stream-v1-fixtures.md` - existing golden UDP frame fixture contract and field offsets.
- `docs/setup/android-bluetooth-hid-gamepad.md` - Android HID setup, pairing proof, output behavior, evidence rows, and redaction rules.
- `docs/windows/test-signing-and-install.md` - Windows VHF setup and target proof notes.
- `docs/windows/phase6-proof-checklist.md` - Windows VHF proof checklist and evidence pattern.
- `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` - Android Bluetooth HID evidence rows, including macOS haptic unsupported/deferred proof.
- `docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl` - Windows VHF evidence rows.

### Desktop Code
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt` - desktop UDP decode/authentication and debug summary foundation.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - accepted/rejected/stale UDP receiver behavior and stream lifecycle.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` - visualizer checklist, haptic status, raw debug, recenter, and backend diagnostics model.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt` - latency, clock offset, packet loss, and target-status calculations.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt` - current minimal control diagnostics shape.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt` - existing redaction rules to extend.

### Android Code
- `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt` - Android UDP frame encoding/debug shape mirrored with desktop.
- `android-host/app/src/main/java/com/btgun/host/util/AndroidLog.kt` - Android logging helper and test-safe logging pattern.
- `android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt` - sanitized Android visualizer status payload.
- `android-host/app/build.gradle.kts` - Android Gradle/JDK/test task setup to document in the Android workflow.
- `android-host/scripts/collect-phase2-host-evidence.sh` - existing local device evidence collection pattern.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UdpInputFrameCodec` exists on Android and desktop with mirrored field layout, HMAC authentication, frame debug summaries, and golden fixture precedent.
- `UdpInputReceiver` already separates accepted, rejected, stopped, stale, and control-grace behavior. Phase 10 can build replay around this boundary instead of inventing a second receiver.
- `VisualizerModel` and `VisualizerMetrics` already expose the model-level assertions Phase 10 needs for replay: checklist rows, live state, haptic status, recenter state, latency, and packet loss.
- `SecretRedactor` already redacts several pairing/proof/private-key patterns. Phase 10 should extend it for stream keys and full identifiers rather than create a parallel redactor.
- `AndroidLog` and the existing evidence collection script give Android-side logging and device-capture patterns to document and refine.

### Established Patterns
- Android owns profile mapping and raw-debug toggles; desktop consumes mapped product input and read-only metadata.
- UDP frame contracts are deterministic, binary, and fixture-backed. Haptic/control messages stay on authenticated reliable control.
- Evidence must avoid secrets and raw device identity data. Manifest rows should contain sanitized capture ids and proof summaries, not raw logs.
- Visualizer checklist rows combine observed state with user confirmation for physical/OS-visible/haptic proof; diagnostics alone must not claim physical proof when user confirmation is required.

### Integration Points
- Add replay fixtures under `fixtures/replay/` and link them from `docs/evidence/manifests/`.
- Add replay tests near UDP receiver, mapped-state adapter, and visualizer model/metrics tests.
- Add diagnostic event models close to existing Android session/dashboard and desktop control/visualizer paths.
- Extend Android dashboard and desktop visualizer with concise per-domain diagnostic status.
- Add focused docs under existing `docs/setup/`, `docs/protocol/`, and new troubleshooting/limits locations as needed.

</code_context>

<specifics>
## Specific Ideas

- Keep replay fixtures small and intentional; prefer goldens and short session clips over broad logs.
- Make diagnostics machine-readable first, then render concise user-visible summaries.
- Treat redaction as a testable product behavior, not only a documentation rule.
- Use a direct v1 compatibility matrix so future agents and users can see what is supported, unsupported, fallback, or deferred without inference.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 10 scope.

</deferred>

---

*Phase: 10-Diagnostics, Replay, and v1 Docs*
*Context gathered: 2026-06-15T16:16:39Z*
