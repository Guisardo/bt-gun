# Phase 10: Diagnostics, Replay, and v1 Docs - Research

**Researched:** 2026-06-15
**Domain:** Diagnostics schema, replay fixtures, evidence-safe exports, v1 developer docs
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

Source for all copied constraints in this block: [VERIFIED: `.planning/phases/10-diagnostics-replay-and-v1-docs/10-CONTEXT.md`]

### Locked Decisions
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

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within Phase 10 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-04 | Packet logs can be replayed in tests to verify parser, profile mapping, and visualizer output. | Use `fixtures/replay/` with raw UDP hex plus sanitized JSONL/session snapshots, then test through `UdpInputFrameCodec`, `UdpInputReceiver`, `UdpControllerStateAdapter`, `VisualizerModel`, and `VisualizerMetrics`. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `UdpInputReceiver.kt`; VERIFIED: `UdpControllerStateAdapterTest.kt`; VERIFIED: `VisualizerModel.kt`; VERIFIED: `VisualizerMetrics.kt`] |
| PERF-05 | Android and desktop expose enough diagnostic logs to distinguish gun, sensor, LAN, profile, and virtual-driver failures. | Define shared `DiagnosticEvent` schema for the five locked domains and feed Android dashboard plus desktop visualizer from those events. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `DashboardState.kt`; VERIFIED: `VisualizerModel.kt`; VERIFIED: `ControlDiagnostics.kt`] |
| PACK-01 | Repository documents selected Android build toolchain and device testing workflow. | Create focused Android build/device doc using current `compileSdk 35`, `targetSdk 35`, Java 17, Gradle env workaround, `adb`, logcat, HID setup, and existing evidence script patterns. [VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `android-host/scripts/collect-phase2-host-evidence.sh`; CITED: https://developer.android.com/studio/test/command-line; CITED: https://developer.android.com/tools/logcat] |
| PACK-04 | Repository documents LAN session protocol, packet schemas, pairing flow, and security model. | Extend or split from `docs/protocol/lan-pairing-v1.md` and `docs/protocol/input-stream-v1-fixtures.md`; keep contract-level schemas, auth/replay rules, lifecycle, haptics, diagnostics, and fixture references. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`] |
| PACK-05 | Repository documents known limitations, including unsupported direct desktop-to-gun Bluetooth, Android Bluetooth HID device compatibility risk, and lack of game-specific presets in v1. | Write compatibility matrix with explicit status, current evidence pointer, and next proof needed for each unsupported/deferred row. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`; VERIFIED: `docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl`] |
</phase_requirements>

## Summary

Phase 10 is a hardening and closeout phase, not a feature expansion phase. The planner should build a small replay corpus, a shared structured diagnostics layer, a sanitized export path, and focused v1 docs that make the existing Phase 9 MVP repeatable without hidden setup knowledge. [VERIFIED: `.planning/ROADMAP.md`; VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `10-CONTEXT.md`; VERIFIED: `.planning/STATE.md`]

Existing code already has most replay seams: Android and desktop mirror fixed 120-byte authenticated UDP frame codecs, desktop receiver rejects unauthenticated/stale/replayed input, Android profile mapping feeds mapped product stream flags, and desktop visualizer model/metrics already expose checklist state, latency, packet loss, raw-debug labels, and backend diagnostics. Phase 10 should compose those seams into replay tests and diagnostic events instead of adding a parallel parser or visualizer path. [VERIFIED: `android-host/app/src/main/java/com/btgun/host/transport/UdpInputFrameCodec.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt`]

**Primary recommendation:** Plan Phase 10 as four waves: replay fixtures/tests, shared diagnostics schema/rendering, sanitized export/redaction gates, and focused v1 docs/index/known-limits matrix. [VERIFIED: `10-CONTEXT.md`; VERIFIED: existing test and docs layout via `rg --files`]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Replay fixture corpus | Repository test fixtures | Evidence manifests | Fixtures are committed under `fixtures/replay/`; manifests link fixtures to sanitized provenance. [VERIFIED: `10-CONTEXT.md`] |
| UDP decode/auth replay | Desktop companion transport tests | Android host transport tests | Desktop currently owns receiver/replay guard behavior; both codecs mirror the wire contract. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `UdpInputReceiver.kt`; VERIFIED: `android-host/.../UdpInputFrameCodec.kt`] |
| Profile-mapped state replay | Desktop companion backend adapter | Android profile mapper evidence | Desktop consumes Android-mapped product stream; profile ownership remains Android. [VERIFIED: `.planning/STATE.md`; VERIFIED: `UdpControllerStateAdapterTest.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| Visualizer output replay | Desktop companion UI model/metrics | Test fixture expected snapshots | `VisualizerModel` and `VisualizerMetrics` already convert accepted input/status/backend diagnostics into checklist and metric output. [VERIFIED: `VisualizerModel.kt`; VERIFIED: `VisualizerMetrics.kt`] |
| Live diagnostics schema | Shared contract in Android and desktop code | Docs/protocol | Five locked domains cross Android and desktop; schema must stay stable and machine-readable. [VERIFIED: `10-CONTEXT.md`] |
| Android gun/sensor/profile diagnostics | Android host service/dashboard | Android logs/export | Android owns BLE gun connection, motion provider/capability, profile mapping, HID gamepad role, and phone haptic status. [VERIFIED: `DashboardState.kt`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`] |
| LAN/control/UDP diagnostics | Desktop control/transport layer | Android control client | Existing control diagnostics are minimal; Phase 10 expands them to domain events and redacted exports. [VERIFIED: `ControlDiagnostics.kt`; VERIFIED: `DesktopControlClient.kt`; VERIFIED: `ControlServer.kt`] |
| HID/backend/haptics diagnostics | Desktop backend runtimes and Android HID status | Visualizer checklist | Windows and macOS backend diagnostics already expose publish/haptic/limitation metadata; Android HID status exposes role/registration/output validation. [VERIFIED: `WindowsBackendRuntime.kt`; VERIFIED: `MacosBackendRuntime.kt`; VERIFIED: `DashboardState.kt`; VERIFIED: `VisualizerModel.kt`] |
| Sanitized export bundle | Repository tooling/scripts | Android/desktop producers | Export bundles must be replay-ready and safe to commit only after redaction. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `SecretRedactor.kt`; VERIFIED: `.gitignore`] |
| v1 docs/index | Repository docs | Evidence manifests | Docs must cover build/test, LAN protocol/security, diagnostics/replay, and known limits with evidence pointers. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `docs/` file layout] |

## Project Constraints (from AGENTS.md)

| Directive | Planning Impact |
|-----------|-----------------|
| Use GSD before file edits unless user says bypass. [VERIFIED: `AGENTS.md`] | Phase plan should stay inside GSD plan/execute/verify flow. |
| New user-facing branches use `feature/<short-kebab-slug>` and must not use `codex/` prefixes unless explicit. [VERIFIED: `AGENTS.md`] | If planner creates a branch, state exact branch name before branch creation. |
| Keep docs short, factual, agent-facing. [VERIFIED: `AGENTS.md`] | Docs should be operator/developer checklists and contracts, not broad tutorials. |
| Current focus is Phase 10; Phase 1 through Phase 9 are complete. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/STATE.md`] | Do not reopen prior phases except to reference evidence and update docs. |
| Android-to-desktop v1 uses Wi-Fi/LAN, Android Bluetooth HID is primary macOS OS-visible path, Windows VHF is retained fallback. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/PROJECT.md`; VERIFIED: `.planning/STATE.md`] | Known limits and docs must frame both v1 OS-visible paths correctly. |
| Protocol and virtual-controller model must support Windows 11 x64 and macOS Apple Silicon. [VERIFIED: `AGENTS.md`; VERIFIED: `.planning/PROJECT.md`] | Diagnostics schema must not encode a single-platform worldview. |
| Raw logs, screenshots, pairing material, stream keys, HMAC material, private keys, and full device identifiers must not be committed. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`] | Redaction tests and gitignore updates are phase-critical. |

## Standard Stack

### Core

| Library/Platform | Version | Purpose | Why Standard |
|------------------|---------|---------|--------------|
| Kotlin/JVM desktop companion | Kotlin JVM plugin `2.0.21`, Java 17 target. [VERIFIED: `desktop-companion/build.gradle.kts`] | Replay tests, diagnostics model, desktop visualizer output checks, export tooling | Existing desktop code and tests are Kotlin/JVM executable mains run by Gradle. [VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: desktop test files] |
| Android native app, Kotlin | `compileSdk 35`, `targetSdk 35`, Java 17 target. [VERIFIED: `android-host/app/build.gradle.kts`] | Android diagnostics, device workflow docs, Android-side replay/sanitization tests | Existing Android host owns BLE, sensors, profile mapping, Bluetooth HID, and status dashboard. [VERIFIED: `DashboardState.kt`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`] |
| Existing UDP frame codecs | Fixed `120` byte frame, version `1`, HMAC-SHA256 tag `32` bytes. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`] | Replay raw UDP hex and auth/decode checks | The wire contract is deterministic and already fixture-backed. [VERIFIED: `UdpInputFrameCodecTest.kt`; VERIFIED: `input-stream-v1-fixtures.md`] |
| Existing desktop receiver/replay guard | In repo. [VERIFIED: `UdpInputReceiver.kt`; VERIFIED: `InputReplayGuardTest.kt`] | Reject duplicate, old, wrong-stream, bad-HMAC, malformed, and stale frames before model updates | PERF-04 requires replay through parser and mapping, not just byte decode. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `UdpInputReceiverTest.kt`] |
| `kotlinx.serialization-json` | `1.11.0` in Android and desktop builds. [VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `desktop-companion/build.gradle.kts`] | Diagnostics JSONL, replay session snapshots, export manifest serialization | Kotlin docs describe kotlinx.serialization as Kotlin's serialization component for JSON and other formats. [CITED: https://kotlinlang.org/docs/serialization.html] |
| Android `BluetoothHidDevice` APIs | Platform API. [VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] | Android HID status docs, compatibility limits, output callback diagnostics | Android docs define `BluetoothHidDevice` as proxy object for controlling HID Device Service. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] |
| Android `adb`/logcat | ADB `36.0.0-13206524` installed locally; device list requires unsandboxed ADB and currently shows no attached devices. [VERIFIED: `adb version`; VERIFIED: `adb devices` escalation output] | Device workflow docs and raw local evidence collection | Android docs state logcat dumps system and app log messages from the command line. [CITED: https://developer.android.com/tools/logcat] |
| Windows VHF docs/tooling | Existing repo docs and Phase 6 evidence. [VERIFIED: `docs/windows/test-signing-and-install.md`; VERIFIED: `phase6-windows-virtual-joystick.jsonl`] | Known limits and fallback diagnostics | Microsoft docs describe VHF as supported framework for HID source drivers. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] |

### Supporting

| Library/Platform | Version | Purpose | When to Use |
|------------------|---------|---------|-------------|
| Gradle Test task | Gradle `9.5.1` available with `JAVA_HOME` and `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home`. [VERIFIED: local `gradle --version` probe] | Run executable Kotlin test mains through current project pattern | Gradle docs cover JVM test execution; current builds override JUnit scan and launch named main classes. [VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: `android-host/app/build.gradle.kts`; CITED: https://docs.gradle.org/current/userguide/java_testing.html] |
| Existing `AndroidLog` helper | In repo. [VERIFIED: `AndroidLog.kt`] | Android diagnostics logging that does not fail unit tests on mocked `android.util.Log` | Use this helper instead of direct `Log.*` calls in new Android diagnostics code. [VERIFIED: `AndroidLog.kt`] |
| Existing `SecretRedactor` | In repo. [VERIFIED: `SecretRedactor.kt`] | Central redaction for diagnostics/export strings | Extend this redactor for stream keys, HMAC material, Bluetooth addresses, serials, Android IDs, and screenshot/log dump markers. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `PairingSecurityTest.kt`] |
| Existing evidence manifests | JSONL rows under `docs/evidence/manifests/`. [VERIFIED: docs manifest files] | Provenance pointers for replay fixtures and known limits | Manifest rows are short, sanitized status/evidence refs rather than raw logs. [VERIFIED: `phase7-android-bluetooth-hid.jsonl`; VERIFIED: `phase6-windows-virtual-joystick.jsonl`] |
| Existing docs folders | `docs/protocol/`, `docs/setup/`, `docs/windows/`, `docs/evidence/manifests/`. [VERIFIED: docs file listing] | v1 docs split | Add focused docs near existing subjects and a v1 index that points to them. [VERIFIED: `10-CONTEXT.md`; VERIFIED: docs file listing] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| In-repo replay fixtures plus existing test mains | New replay CLI framework | New framework adds package/install and parser surface; existing executable tests already cover codec, receiver, adapter, model, and metrics. [VERIFIED: current test files; VERIFIED: build files] |
| `kotlinx.serialization-json` JSONL models | Manual string JSON | Manual JSON risks schema drift and escaping/redaction mistakes; serialization dependency already exists. [VERIFIED: build files; CITED: https://kotlinlang.org/docs/serialization.html] |
| Central `DiagnosticEvent` model | UI-specific strings only | UI strings cannot feed replay/export/tests reliably; Phase 10 locked a stable structured schema. [VERIFIED: `10-CONTEXT.md`] |
| `SecretRedactor` extension | Per-call regex snippets | Per-call redaction misses new evidence paths; existing redactor has tests and should become the product gate. [VERIFIED: `SecretRedactor.kt`; VERIFIED: `PairingSecurityTest.kt`] |
| Long committed packet logs | Small golden datagrams and short sanitized clips | Long logs increase privacy risk and review noise; context locks small first corpus. [VERIFIED: `10-CONTEXT.md`] |

**Installation:**

No new external packages are recommended for Phase 10. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `desktop-companion/build.gradle.kts`]

**Version verification:** Existing dependency versions are verified from Gradle files and local tool probes, not package registries, because Phase 10 should not install new dependencies. [VERIFIED: build files; VERIFIED: local tool probes]

## Package Legitimacy Audit

Not required: Phase 10 research recommends no new external package installs. [VERIFIED: `10-CONTEXT.md`; VERIFIED: build files]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| None new | n/a | n/a | n/a | n/a | not run | Approved: no install task needed. [VERIFIED: `10-CONTEXT.md`] |

**Packages removed due to slopcheck [SLOP] verdict:** none. [VERIFIED: no new package recommendation]
**Packages flagged as suspicious [SUS]:** none. [VERIFIED: no new package recommendation]

## Architecture Patterns

### System Architecture Diagram

```text
Live v1 MVP evidence
  -> Android host domains
       gun_ble
       sensor_motion
       profile_mapping
       hid_backend_haptics
  -> LAN/control/UDP domains
       lan_control_udp
  -> Shared DiagnosticEvent schema
       schema, ts_elapsed, domain, status, reason_code, detail,
       session_refs, redacted context
  -> Redaction gate
       SecretRedactor + identifier scrubbing + size limits
  -> Outputs
       live Android dashboard status
       live desktop visualizer status
       sanitized export bundle
       docs/evidence manifest rows

Replay path
  fixtures/replay raw UDP hex/bytes
    -> UdpInputFrameCodec.authenticateAndDecode
    -> UdpInputReceiver replay/stale/auth guard
    -> UdpControllerStateAdapter mapped product state
    -> VisualizerModel + VisualizerMetrics
    -> expected sanitized JSONL/checklist snapshot
```

### Recommended Project Structure

```text
fixtures/replay/
  README.md                         # schema, redaction policy, provenance links
  udp-golden/
    mapped-session-001.hex          # small committed raw datagrams as hex
    mapped-session-001.jsonl        # sanitized expected decoded/session events
  expected/
    mapped-session-001-visualizer.json

desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/
  DiagnosticEvent.kt                # shared desktop schema and reason codes
  DiagnosticExport.kt               # sanitized bundle writer, if desktop-owned

desktop-companion/src/test/kotlin/com/btgun/desktop/replay/
  ReplayFixtureTest.kt              # PERF-04 decode -> model/metrics chain
  DiagnosticExportTest.kt           # redaction + schema tests

android-host/app/src/main/java/com/btgun/host/diagnostics/
  DiagnosticEvent.kt                # Android schema mirror
  DiagnosticReporter.kt             # dashboard/export source

android-host/app/src/test/java/com/btgun/host/diagnostics/
  DiagnosticEventTest.kt
  DiagnosticRedactionTest.kt

docs/
  v1.md                             # short index
  setup/android-build-device-testing.md
  protocol/lan-session-security-v1.md
  diagnostics/replay-and-troubleshooting.md
  limits/v1-compatibility-limits.md
```

Exact names are discretionary; the split must keep Android setup, LAN protocol/security, diagnostics/replay, and known limits separate and discoverable. [VERIFIED: `10-CONTEXT.md`; VERIFIED: agent discretion] [ASSUMED]

### Pattern 1: Replay Fixture Contract

**What:** Store raw UDP datagrams as hex plus sanitized JSONL/session snapshots and expected visualizer output. [VERIFIED: `10-CONTEXT.md`]

**When to use:** PERF-04 tests for parser, mapped product state, visualizer checklist, latency metrics, packet loss, stale/drop behavior, and haptic/checklist snapshots. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `VisualizerModelTest.kt`; VERIFIED: `VisualizerMetricsTest.kt`]

**Example:**

```json
{
  "schema": "btgun.replay.fixture.v1",
  "fixture_id": "mapped-session-001",
  "source_manifest": "docs/evidence/manifests/phase4-input-haptic-transport.jsonl#phase4-input-stream-001",
  "stream": {
    "control_session_ref": "control-session-redacted",
    "stream_session_ref": "stream-session-suffix-ddeeff",
    "auth": "fixture_key_only"
  },
  "datagrams": [
    {"seq": 42, "kind": "snapshot", "hex_ref": "udp-golden/mapped-session-001.hex#line-1"},
    {"seq": 43, "kind": "edge", "hex_ref": "udp-golden/mapped-session-001.hex#line-2"}
  ],
  "expected": {
    "trigger": true,
    "reload": true,
    "aimX": 0.375,
    "aimY": -0.625,
    "latency_target": "pass",
    "packet_expected": 2
  }
}
```

Source: existing fixed UDP fixture docs and Phase 10 context. [VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`; VERIFIED: `10-CONTEXT.md`]

### Pattern 2: Full-Chain Replay Test

**What:** Tests should replay through the same production boundaries used by live input, not call only the codec or model in isolation. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `UdpControllerStateAdapterTest.kt`]

**When to use:** Primary PERF-04 automated gate. [VERIFIED: `.planning/REQUIREMENTS.md`]

**Example:**

```kotlin
fun replayFixtureDrivesVisualizerModelAndMetrics() {
    val bytes = fixtureHex("mapped-session-001").map(String::hexToBytes)
    val receiver = UdpInputReceiver().start(
        trustedSession = "fixture-control-session",
        config = fixtureInputStreamConfig(),
    )
    val metrics = VisualizerMetrics()
    var model = VisualizerModel.initial()

    bytes.forEachIndexed { index, datagram ->
        val result = receiver.handleDatagram(datagram, receivedElapsedNanos = 10_000_000_000L + index)
        require(result is UdpInputReceiverResult.Accepted)
        model = model.withAcceptedInput(result.input, observedElapsedNanos = 10_000_100_000L + index)
        model = model.withMetrics(metrics.record(result.input, desktopRenderElapsedNanos = 10_000_200_000L + index))
    }

    check(model.row(VisualizerChecklistRowId.LAN_VISUALIZER_STREAM).state == VisualizerChecklistState.OBSERVED)
    check(model.liveState.trigger)
    check(model.metrics.packetExpected == 2L)
}
```

Source: existing test style and production APIs. [VERIFIED: `UdpInputReceiverTest.kt`; VERIFIED: `UdpControllerStateAdapterTest.kt`; VERIFIED: `VisualizerModelTest.kt`; VERIFIED: `VisualizerMetricsTest.kt`]

### Pattern 3: DiagnosticEvent Schema

**What:** Define one stable diagnostic event shape with five locked domains and five locked statuses. [VERIFIED: `10-CONTEXT.md`]

**When to use:** Android dashboard status, desktop visualizer status, JSONL exports, docs examples, replay troubleshooting. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `DashboardState.kt`; VERIFIED: `VisualizerModel.kt`]

**Example:**

```kotlin
enum class DiagnosticDomain(val wireName: String) {
    GUN_BLE("gun_ble"),
    SENSOR_MOTION("sensor_motion"),
    LAN_CONTROL_UDP("lan_control_udp"),
    PROFILE_MAPPING("profile_mapping"),
    HID_BACKEND_HAPTICS("hid_backend_haptics"),
}

enum class DiagnosticStatus(val wireName: String) {
    OK("ok"),
    DEGRADED("degraded"),
    BLOCKED("blocked"),
    UNSUPPORTED("unsupported"),
    UNKNOWN("unknown"),
}

data class DiagnosticEvent(
    val schema: String = "btgun.diagnostics.v1",
    val tsElapsed: Long,
    val domain: DiagnosticDomain,
    val status: DiagnosticStatus,
    val reasonCode: String,
    val detail: String,
    val sessionRefs: Map<String, String> = emptyMap(),
    val context: Map<String, String> = emptyMap(),
)
```

Source: Phase 10 context locked fields/status/domain names. [VERIFIED: `10-CONTEXT.md`]

### Pattern 4: Redaction Before Persistence

**What:** Run every exported diagnostic row and replay snapshot through centralized redaction before writing committed artifacts. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `SecretRedactor.kt`]

**When to use:** JSONL export, manifest rows, docs examples, local issue bundle, and any log-derived text. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `.gitignore`]

**Example:**

```kotlin
fun sanitizeDiagnosticLine(raw: String): String =
    SecretRedactor.redact(raw)
        .replace(BLUETOOTH_MAC_REGEX, "<redacted-bluetooth-address>")
        .replace(ANDROID_ID_REGEX, "<redacted-android-id>")
        .take(MAX_DIAGNOSTIC_LINE_CHARS)
```

Source: existing redactor plus Phase 10 forbidden field list. [VERIFIED: `SecretRedactor.kt`; VERIFIED: `10-CONTEXT.md`; VERIFIED: `PairingSecurityTest.kt`]

### Anti-Patterns to Avoid

- **Codec-only replay:** Passing `UdpInputFrameCodec` tests alone does not satisfy PERF-04 because the requirement includes profile mapping and visualizer output. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `10-CONTEXT.md`]
- **UI-only diagnostics:** A dashboard label without JSONL/schema cannot support export, replay, or machine checks. [VERIFIED: `10-CONTEXT.md`]
- **Direct Android-to-desktop timestamp subtraction:** Phase 9 proved Android elapsed realtime and desktop `System.nanoTime` need explicit offset handling. [VERIFIED: `.planning/STATE.md`; VERIFIED: `VisualizerMetrics.kt`; VERIFIED: `VisualizerMetricsTest.kt`]
- **Raw log commit:** Raw logcat, screenshots, Bluetooth addresses, serials, pairing material, stream keys, and proof values are forbidden in committed artifacts. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `.gitignore`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`]
- **Observed equals proven:** Backend diagnostics can mark rows observed, but physical OS-visible controller and phone-haptic proof still need user confirmation where prior phases require it. [VERIFIED: `.planning/STATE.md`; VERIFIED: `VisualizerModel.kt`; VERIFIED: `09-UAT.md`]
- **Docs drift by duplication:** Copying the same LAN schema into several docs will create contradictions; prefer one protocol/security contract and link from the v1 index/troubleshooting docs. [VERIFIED: `docs/protocol/lan-pairing-v1.md`] [ASSUMED]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| UDP auth/decode | New parser for replay files | Existing `UdpInputFrameCodec.authenticateAndDecode` | Existing codec enforces fixed frame size, magic/version/type, stream id, HMAC, flags, reserved fields, and malformed-field rejection. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `UdpInputFrameCodecTest.kt`] |
| Replay/stale checks | Ad hoc sequence counter in replay tests | Existing `UdpInputReceiver` and `InputReplayGuard` | Existing receiver covers duplicate, old, wrong-stream, bad-HMAC, age-expired, control grace, timeout, and stale semantics. [VERIFIED: `UdpInputReceiver.kt`; VERIFIED: `UdpInputReceiverTest.kt`; VERIFIED: `InputReplayGuardTest.kt`] |
| Profile mapped state | Desktop-side profile reconstruction | Existing `UdpControllerStateAdapter` consuming Android-mapped stream | Android owns profile mapping; desktop must not recreate profile authority. [VERIFIED: `.planning/STATE.md`; VERIFIED: `UdpControllerStateAdapterTest.kt`] |
| Visualizer assertions | Screenshot/image diff | Existing `VisualizerModel`, `VisualizerMetrics`, and panel label tests | Model/metrics tests are deterministic and already encode checklist/latency/loss behavior. [VERIFIED: `VisualizerModelTest.kt`; VERIFIED: `VisualizerMetricsTest.kt`; VERIFIED: `VisualizerWindowTest.kt`] |
| JSON serialization | Manual string concatenation | Existing `kotlinx.serialization-json` dependency or `kotlinx.serialization.json` builders | Existing code uses JSON object builders and parsers for control/status metadata. [VERIFIED: `VisualizerStatus.kt`; VERIFIED: `DesktopControlClient.kt`; CITED: https://kotlinlang.org/docs/serialization.html] |
| Android logging | Direct `Log.*` in unit-testable code | Existing `AndroidLog` wrapper | Wrapper handles Android log stubs in JVM tests. [VERIFIED: `AndroidLog.kt`] |
| Redaction | One-off regex near each export | Central `SecretRedactor` plus new identifier rules | Existing redactor is test-covered and must become the export gate. [VERIFIED: `SecretRedactor.kt`; VERIFIED: `PairingSecurityTest.kt`; VERIFIED: `10-CONTEXT.md`] |
| Windows virtual HID proof docs | New Windows setup path | Existing Phase 6 docs/manifests | Phase 10 docs should link and summarize, not invent a new driver installation flow. [VERIFIED: `docs/windows/test-signing-and-install.md`; VERIFIED: `docs/windows/phase6-proof-checklist.md`] |

**Key insight:** Phase 10 value comes from composing existing verified runtime boundaries into repeatable artifacts; custom duplicate parsers, mappings, redactors, or setup flows increase drift and security risk. [VERIFIED: `.planning/STATE.md`; VERIFIED: `10-CONTEXT.md`]

## Common Pitfalls

### Pitfall 1: Replay Fixtures Bypass Production Receiver

**What goes wrong:** Replay tests decode bytes, then directly construct semantic state, skipping receiver trust/stale/replay behavior. [VERIFIED: `UdpInputFrameCodecTest.kt`; VERIFIED: `UdpInputReceiverTest.kt`]

**Why it happens:** Golden frame tests already exist and look sufficient at first glance. [VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`; VERIFIED: `UdpInputFrameCodecTest.kt`]

**How to avoid:** Chain fixture bytes through `UdpInputReceiver` before `UdpControllerStateAdapter` and `VisualizerModel`. [VERIFIED: `UdpControllerStateAdapterTest.kt`]

**Warning signs:** New tests call `UdpControllerStateAdapter.toState` with hand-built `UdpReceivedInput` for PERF-04 and no raw datagram fixture appears under `fixtures/replay/`. [VERIFIED: `10-CONTEXT.md`] [ASSUMED]

### Pitfall 2: Diagnostics Schema Becomes UI Copy

**What goes wrong:** `detail` strings become the only machine-readable signal and reason codes drift across Android/desktop. [VERIFIED: `10-CONTEXT.md`]

**Why it happens:** Existing surfaces already format many state labels for users. [VERIFIED: `DashboardState.kt`; VERIFIED: `VisualizerModel.kt`; VERIFIED: `PairingWindowTest.kt`]

**How to avoid:** Keep fixed `domain`, `status`, and `reason_code`; render UI from those fields. [VERIFIED: `10-CONTEXT.md`]

**Warning signs:** Tests assert English text but not schema/domain/status/reason-code values. [ASSUMED]

### Pitfall 3: Redaction Only Covers Pairing Secrets

**What goes wrong:** Existing redactor hides QR/proof/private key strings but misses stream keys, HMAC material, Bluetooth addresses, serials, Android IDs, and screenshots. [VERIFIED: `SecretRedactor.kt`; VERIFIED: `10-CONTEXT.md`]

**Why it happens:** Phase 3 redaction scope was pairing/control only, while Phase 10 export includes broader device/session diagnostics. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `10-CONTEXT.md`]

**How to avoid:** Extend `SecretRedactor` tests before export writer work and add forbidden-string scans over replay fixtures/docs/manifests. [VERIFIED: `PairingSecurityTest.kt`; VERIFIED: `.gitignore`; VERIFIED: `10-CONTEXT.md`]

**Warning signs:** Export tests check QR/proof only or fixture JSONL contains raw MAC-like patterns. [ASSUMED]

### Pitfall 4: Metrics Replay Reuses Stale Clock Offset

**What goes wrong:** Replay snapshots compute latency using direct Android capture timestamp to desktop render timestamp, causing impossible multi-second latency or false pass/fail. [VERIFIED: `VisualizerMetricsTest.kt`; VERIFIED: `.planning/STATE.md`]

**Why it happens:** Android and desktop monotonic clocks have different origins. [VERIFIED: `VisualizerMetricsTest.kt`; VERIFIED: `.planning/STATE.md`]

**How to avoid:** Include a visualizer status offset sample or use the existing UDP estimated offset path in replay tests. [VERIFIED: `VisualizerMetrics.kt`; VERIFIED: `VisualizerMetricsTest.kt`]

**Warning signs:** Replay expected file stores only `captureElapsedNanos` and `desktopRenderElapsedNanos` without `offsetQuality` or status sample. [ASSUMED]

### Pitfall 5: Known Limits Get Softened

**What goes wrong:** Docs imply macOS HID haptics, physical gun rumble, direct desktop Bluetooth, or game presets are nearly supported. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `.planning/STATE.md`]

**Why it happens:** Prior phases contain multiple fallback/proof paths that are easy to conflate. [VERIFIED: `.planning/STATE.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `docs/windows/test-signing-and-install.md`]

**How to avoid:** Use matrix statuses `supported`, `unsupported`, `fallback`, and `deferred`; each unsupported/deferred row needs evidence pointer and next proof. [VERIFIED: `10-CONTEXT.md`]

**Warning signs:** Known limits doc says "may work" without an evidence row or proof needed. [ASSUMED]

### Pitfall 6: Export Bundle Becomes Raw Evidence Dump

**What goes wrong:** Export includes logcat, screenshots, raw device names, raw Bluetooth dumps, or full identifiers by default. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; VERIFIED: `.gitignore`]

**Why it happens:** Existing evidence collection script captures raw logcat and screenshots into ignored `.evidence/` paths for manual extraction. [VERIFIED: `android-host/scripts/collect-phase2-host-evidence.sh`; VERIFIED: `.gitignore`]

**How to avoid:** Keep raw collection local and gitignored; export only sanitized JSONL, replay clips, app/build versions, capability statuses, and manifest pointer. [VERIFIED: `10-CONTEXT.md`]

**Warning signs:** New committed docs reference raw `.evidence` files as required replay input. [ASSUMED]

## Code Examples

Verified patterns from existing sources:

### Existing Redaction Extension Point

```kotlin
// Source: desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt
object SecretRedactor {
    private val rules = listOf(
        Regex("(qr_secret=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("(code=)\\d{6}") to "$1<redacted>",
        Regex("((?:pairing_)?proof=)[A-Za-z0-9_-]+") to "$1<redacted>",
    )

    fun redact(value: String): String =
        rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
}
```

Use this as the central place for Phase 10 export redaction, then add tests for stream/HMAC/device identifiers. [VERIFIED: `SecretRedactor.kt`; VERIFIED: `PairingSecurityTest.kt`; VERIFIED: `10-CONTEXT.md`]

### Existing Android Visualizer Status JSON Pattern

```kotlin
// Source: android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt
fun toJsonBody(): JsonObject =
    buildJsonObject {
        put("rawDebugEnabled", rawDebugEnabled)
        put("aimZeroState", aimZeroState.sanitizedState())
        put("recenterState", recenterState.sanitizedState())
        put("androidElapsedNanos", androidElapsedNanos.coerceAtLeast(0L))
        put("recenterLabel", recenterLabel.sanitizedLabel())
        put("aimZeroLabel", aimZeroLabel.sanitizedLabel())
    }
```

Use the same whitelist/sanitize pattern for diagnostic event JSON bodies. [VERIFIED: `VisualizerStatus.kt`; VERIFIED: `VisualizerStatusTest.kt`]

### Existing Desktop Authenticated Diagnostics Parse Pattern

```kotlin
// Source: desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
ControlMessageType.DIAGNOSTICS -> {
    visualizerStatusFromJsonBody(envelope.body)
        ?.copy(controlSessionId = envelope.sessionId)
        ?.let(onVisualizerStatusReceived)
    onControlEnvelopeAccepted(envelope)
}
```

Do not add unauthenticated diagnostic channels; parse only from accepted trusted envelopes or local runtime sources. [VERIFIED: `ControlServer.kt`; VERIFIED: `ControlChannelTest.kt`; VERIFIED: `.planning/phases/09-visualizer-acceptance-path/09-SECURITY.md`]

### Existing Android Evidence Collection Boundary

```bash
# Source: android-host/scripts/collect-phase2-host-evidence.sh
adb logcat -d -v threadtime > "${OUT_DIR}/logcat-threadtime.txt"
adb shell dumpsys bluetooth_manager > "${OUT_DIR}/dumpsys-bluetooth-manager.txt"
adb exec-out screencap -p > "${OUT_DIR}/dashboard-screenshot.png" || true
```

Keep raw outputs in ignored `.evidence/` paths and copy only sanitized summaries/manifests into committed docs. [VERIFIED: `android-host/scripts/collect-phase2-host-evidence.sh`; VERIFIED: `.gitignore`; CITED: https://developer.android.com/tools/logcat]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Static/handwritten packet fixture constants only | Small committed raw UDP hex plus sanitized session JSONL and expected visualizer output | Phase 10 context on 2026-06-15 [VERIFIED: `10-CONTEXT.md`] | Planner must add fixture files under `fixtures/replay/` and tests that consume them. [VERIFIED: `10-CONTEXT.md`] |
| Pairing/control-only diagnostics | Five-domain diagnostic event schema across Android and desktop | Phase 10 context on 2026-06-15 [VERIFIED: `10-CONTEXT.md`] | Planner must expand beyond existing `ControlDiagnostics` fields. [VERIFIED: `ControlDiagnostics.kt`; VERIFIED: `10-CONTEXT.md`] |
| Manual Phase 9 checklist as final proof artifact | Replay-ready sanitized export bundle plus docs/manifests for issue replay | Phase 10 scope after Phase 9 UAT [VERIFIED: `09-UAT.md`; VERIFIED: `10-CONTEXT.md`] | Planner should preserve manual proof distinction while adding replay/export artifacts. [VERIFIED: `VisualizerModel.kt`; VERIFIED: `09-UAT.md`] |
| macOS CoreHID/DriverKit as primary path candidate | Android Bluetooth HID primary macOS path; Windows VHF fallback retained | Phase 7 reroute and Phase 9 state [VERIFIED: `.planning/STATE.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`] | Known limits docs must not revive CoreHID/DriverKit as v1 primary. [VERIFIED: `10-CONTEXT.md`] |
| Desktop-owned profile mapping assumption | Android-owned profile mapping with desktop read-only metadata | Phase 8 completion [VERIFIED: `.planning/STATE.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] | Replay should treat UDP mapped stream as product input. [VERIFIED: `UdpControllerStateAdapterTest.kt`] |

**Deprecated/outdated:**

- Direct desktop-to-gun Bluetooth is v2/deferred for v1 docs. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `10-CONTEXT.md`]
- Physical gun motor rumble is deferred; v1 uses Android phone vibration. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `.planning/PROJECT.md`; VERIFIED: `10-CONTEXT.md`]
- Game-specific presets are out of v1. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `10-CONTEXT.md`]
- macOS HID haptics over Android Bluetooth HID are unsupported/deferred; LAN/Windows paths still provide phone haptics. [VERIFIED: `.planning/STATE.md`; VERIFIED: `phase7-android-bluetooth-hid.jsonl`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Exact new file names under `docs/diagnostics/`, `docs/limits/`, and `fixtures/replay/` are recommendations, not locked decisions. | Recommended Project Structure | Low: planner may choose different names if docs remain separate/discoverable. |
| A2 | A desktop-owned export writer is the simplest first implementation path. | Recommended Project Structure | Medium: Android-only export may be easier if implementation wants device-side issue bundles first. |
| A3 | Forbidden-pattern scans can be implemented as main-function tests instead of a dedicated script. | Common Pitfalls / Validation Architecture | Low: existing tests already use source/file scans, but planner may prefer shell checks. |
| A4 | English-text-only diagnostic tests are a warning sign for schema drift. | Common Pitfalls | Low: true even if planner chooses a stronger schema test shape. |
| A5 | Export tests that cover only QR/proof secrets are insufficient for Phase 10. | Common Pitfalls | Medium: wrong redaction scope could leak stream or device identifiers. |
| A6 | Replay expected files need clock-offset quality or status samples to avoid invalid latency assertions. | Common Pitfalls | Medium: wrong replay timing model could create false pass/fail metrics. |
| A7 | Known-limit wording such as "may work" without evidence should be rejected. | Common Pitfalls | Low: locked context already requires direct status language. |
| A8 | Committed docs should not require raw `.evidence` files as replay input. | Common Pitfalls | Medium: wrong docs could normalize raw log dependency. |
| A9 | First export location is still open between desktop-only, Android-only, or dual implementation. | Open Questions | Medium: planner must pick a concrete first implementation target. |
| A10 | Hex-only replay fixtures are likely lower-risk than binary files for first corpus review. | Open Questions | Low: both are allowed by locked context. |
| A11 | Docs/source-scan tests can cover PACK-01/PACK-04/PACK-05. | Validation Architecture | Low: planner can replace with manual checklist if needed. |
| A12 | Research validity is roughly 30 days for external docs/toolchain facts. | Metadata | Low: repo-local architecture remains stable, but external docs/toolchain can drift. |
| A13 | Focused Gradle `--tests '*Replay*'` / `'*Diagnostic*'` patterns will match future Phase 10 test class names. | Validation Architecture | Low: planner can replace with exact class filters after files exist. |
| A14 | Redaction/forbidden-pattern scan command shape can be a narrow Gradle test or equivalent script. | Validation Architecture | Medium: missing scan could let unsafe exports pass. |
| A15 | Duplicating LAN schema across multiple docs will create drift risk. | Anti-Patterns | Low: existing docs already centralize LAN schema, but planner may choose a different doc split. |
| A16 | PERF-04 replay tests that use hand-built `UdpReceivedInput` without raw datagrams are insufficient. | Common Pitfalls | Medium: could falsely satisfy replay while skipping auth/decode behavior. |

## Open Questions

1. **Where should the first export command live?**
   - What we know: Phase 10 allows the agent to choose export command names and file locations. [VERIFIED: `10-CONTEXT.md`]
   - What's unclear: Whether first export should be desktop-only, Android-only, or dual. [ASSUMED]
   - Recommendation: Start desktop export first because replay parser, visualizer model, metrics, and manifests are desktop-adjacent; add Android diagnostic event producer and dashboard rendering in same phase. [VERIFIED: desktop replay seams] [ASSUMED]

2. **Should replay fixtures include binary `.bin` files or hex-only text?**
   - What we know: Context allows raw UDP fixture bytes/hex, and existing docs use hex constants. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`]
   - What's unclear: Whether binary files are worth the review overhead. [ASSUMED]
   - Recommendation: Use hex text first for reviewable diffs; add binary only if a future replay CLI needs exact file-stream behavior. [VERIFIED: existing fixture docs] [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java | Desktop and Android tests | yes | OpenJDK `17.0.19` from Homebrew. [VERIFIED: local `java -version`] | None needed. |
| Gradle | Test/build commands | yes with env workaround | Gradle `9.5.1`; plain `gradle --version` fails with native-platform dylib error, but `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle --version` works. [VERIFIED: local probes] | Planner must include the env vars in commands. |
| Android SDK / ADB | Device workflow docs, evidence collection | partially | ADB `36.0.0-13206524` installed. [VERIFIED: local `adb version`] | Unsandboxed `adb devices` works but shows no attached devices. [VERIFIED: escalated `adb devices`] |
| Android device | Manual device workflow | no current device attached | n/a [VERIFIED: escalated `adb devices`] | Docs can be written; live device export proof needs device attached later. |
| `ctx7` docs CLI | Context7 fallback | no | not found. [VERIFIED: local `command -v ctx7`] | Used official docs via web search. |
| `slopcheck` | Package legitimacy gate | no | not found. [VERIFIED: local `command -v slopcheck`] | Not needed because no new packages recommended. |
| PowerShell (`pwsh`/`powershell`) | Windows docs/checklist local authoring | no | not found. [VERIFIED: local probes] | Windows target commands remain docs/manual, not local execution. |
| Git | Commit research/docs | yes | Apple Git `2.50.1`. [VERIFIED: local `git --version`] | None needed. |

**Missing dependencies with no fallback:**

- No Android device is currently attached for live device verification; Phase 10 docs/replay planning can proceed, but any live export proof must be manual/hardware-gated. [VERIFIED: escalated `adb devices`]

**Missing dependencies with fallback:**

- `ctx7` unavailable; official docs were checked through web search. [VERIFIED: local `command -v ctx7`; CITED: official docs listed in Sources]
- `slopcheck` unavailable; no package audit needed because no new packages are recommended. [VERIFIED: local `command -v slopcheck`; VERIFIED: no new package recommendation]
- PowerShell unavailable locally; Windows target workflow remains documented from Phase 6 evidence. [VERIFIED: local probes; VERIFIED: `docs/windows/test-signing-and-install.md`]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Desktop Kotlin/JVM executable main tests through Gradle `test`; Android JVM unit executable main tests through `testDebugUnitTest`. [VERIFIED: `desktop-companion/build.gradle.kts`; VERIFIED: `android-host/app/build.gradle.kts`] |
| Config file | `desktop-companion/build.gradle.kts`, `android-host/app/build.gradle.kts`. [VERIFIED: build files] |
| Quick run command | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*Replay*' --tests '*Diagnostic*' --tests '*Visualizer*' --tests '*Udp*' --no-daemon --console=plain` [VERIFIED: Gradle env probe] [ASSUMED] |
| Full suite command | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --no-daemon --console=plain` plus `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --no-daemon --console=plain`. [VERIFIED: build files; VERIFIED: Gradle env probe] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PERF-04 | Raw replay fixture decodes/authenticates, passes receiver replay guard, maps to semantic state, updates visualizer model/metrics/checklist expected snapshot. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `10-CONTEXT.md`] | integration/unit | `gradle -p desktop-companion test --tests '*ReplayFixture*' --tests '*UdpControllerStateAdapter*' --tests '*VisualizerMetrics*' --tests '*VisualizerModel*'` | no - Wave 0 add `ReplayFixtureTest.kt` and fixtures. |
| PERF-05 | Android diagnostic events cover `gun_ble`, `sensor_motion`, `profile_mapping`, `hid_backend_haptics`, and sanitize output. [VERIFIED: `10-CONTEXT.md`] | unit | `gradle -p android-host testDebugUnitTest --tests '*Diagnostic*' --tests '*DashboardState*' --tests '*VisualizerStatus*'` | no - Wave 0 add Android diagnostics tests. |
| PERF-05 | Desktop diagnostic events cover `lan_control_udp`, `profile_mapping`, `hid_backend_haptics`, visualizer rendering, and redacted export. [VERIFIED: `10-CONTEXT.md`] | unit/integration | `gradle -p desktop-companion test --tests '*Diagnostic*' --tests '*ControlChannel*' --tests '*VisualizerModel*'` | no - Wave 0 add desktop diagnostics tests. |
| PACK-01 | Android build/device testing doc includes Gradle/JDK/SDK, permissions, install, USB/logcat, real gun steps, HID mode, blockers. [VERIFIED: `10-CONTEXT.md`] | docs/source scan | `gradle -p desktop-companion test --tests '*Docs*'` or focused source-scan test. [ASSUMED] | no - Wave 0 add docs guard or manual checklist. |
| PACK-04 | LAN protocol/security docs include schemas, pairing, auth/replay, lifecycle, haptics, diagnostics, fixture refs. [VERIFIED: `10-CONTEXT.md`] | docs/source scan | `gradle -p desktop-companion test --tests '*Docs*'` or focused source-scan test. [ASSUMED] | partial docs exist; Phase 10 doc guard missing. |
| PACK-05 | Known limits matrix includes required unsupported/deferred/fallback rows, evidence pointer, and next proof needed. [VERIFIED: `10-CONTEXT.md`] | docs/source scan | `gradle -p desktop-companion test --tests '*Docs*'` or focused source-scan test. [ASSUMED] | no - Wave 0 add doc and scan. |

### Sampling Rate

- **Per task commit:** Run the narrow Gradle command for the touched side plus redaction/forbidden-pattern scan. [VERIFIED: current Gradle test pattern] [ASSUMED]
- **Per wave merge:** Run full desktop suite; run full Android unit suite when Android diagnostics/status/dashboard code changes. [VERIFIED: build files]
- **Phase gate:** Full desktop suite, Android relevant suite, docs source scan, manifest/fixture redaction scan, and manual review of docs/known-limits evidence pointers. [VERIFIED: `10-CONTEXT.md`] [ASSUMED]

### Wave 0 Gaps

- [ ] `fixtures/replay/README.md` and first `fixtures/replay/udp-golden/*` files for PERF-04. [VERIFIED: `10-CONTEXT.md`]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt` for decode -> receiver -> mapped state -> visualizer output. [VERIFIED: current test layout] [ASSUMED]
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt` and tests for schema/status/reason codes. [VERIFIED: `10-CONTEXT.md`] [ASSUMED]
- [ ] `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticEvent.kt` and tests for Android dashboard/export event generation. [VERIFIED: `10-CONTEXT.md`] [ASSUMED]
- [ ] Redaction tests for stream keys, HMAC material, Bluetooth addresses, serials, Android IDs, raw screenshots/log dumps. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `SecretRedactor.kt`]
- [ ] Docs source-scan or checklist tests for PACK-01, PACK-04, PACK-05. [VERIFIED: requirements] [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Keep diagnostics/replay exports separate from pairing/auth material; accepted control diagnostics stay behind existing trusted sessions. [VERIFIED: `ControlServer.kt`; VERIFIED: `.planning/phases/09-visualizer-acceptance-path/09-SECURITY.md`; CITED: https://owasp.org/www-project-application-security-verification-standard/] |
| V3 Session Management | yes | Do not expose full session ids, stream keys, HMAC material, or replay tokens; use redacted refs/suffixes only. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| V4 Access Control | yes | Export bundle must include only sanitized local artifacts and no raw `.evidence` logs by default. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `.gitignore`] |
| V5 Input Validation | yes | Validate diagnostic JSON fields, fixed enum values, replay fixture schemas, known reason codes, and doc/matrix required rows. [VERIFIED: `10-CONTEXT.md`; CITED: https://owasp.org/www-project-application-security-verification-standard/] |
| V6 Cryptography | yes | Use existing JCE HMAC-SHA256 handling and never log/export keys, proof values, private keys, or stream secrets. [VERIFIED: `UdpInputFrameCodec.kt`; VERIFIED: `SecretRedactor.kt`; VERIFIED: `10-CONTEXT.md`] |
| V7 Error Handling and Logging | yes | Logs/diagnostics need stable reason codes plus redacted details; Android logcat remains local raw evidence unless sanitized. [VERIFIED: `AndroidLog.kt`; VERIFIED: `10-CONTEXT.md`; CITED: https://developer.android.com/tools/logcat] |
| V14 Configuration | yes | Docs must record required env vars and local toolchain setup, especially Gradle `JAVA_HOME`/`GRADLE_USER_HOME` workaround. [VERIFIED: local Gradle probes; VERIFIED: `android-host/app/build.gradle.kts`] |

### Known Threat Patterns for Diagnostics/Replay

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret leakage in exports | Information Disclosure | Central redaction, forbidden-pattern tests, gitignored raw evidence, sanitized manifest rows only. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `SecretRedactor.kt`; VERIFIED: `.gitignore`] |
| Forged diagnostics claim healthy state | Spoofing/Tampering | Accept remote diagnostics only from authenticated control envelopes; local runtime events carry source/domain/status and reason code. [VERIFIED: `ControlServer.kt`; VERIFIED: `VisualizerStatus.kt`; VERIFIED: `09-SECURITY.md`] |
| Replay fixtures mask auth failures | Tampering | Replay raw bytes through `UdpInputReceiver` and existing HMAC/session/replay checks. [VERIFIED: `UdpInputReceiver.kt`; VERIFIED: `UdpInputReceiverTest.kt`] |
| Export bundle too large or contains raw logs | Information Disclosure/DoS | Commit small fixtures/summaries only; raw logs and screenshots remain ignored by default. [VERIFIED: `10-CONTEXT.md`; VERIFIED: `.gitignore`] |
| Unsupported feature documented as supported | Repudiation | Known-limits matrix requires current evidence pointer and next proof needed for each deferred/unsupported row. [VERIFIED: `10-CONTEXT.md`] |

## Sources

### Primary (HIGH confidence)

- `.planning/phases/10-diagnostics-replay-and-v1-docs/10-CONTEXT.md` - locked Phase 10 decisions, requirements, docs split, redaction rules, known limits. [VERIFIED: codebase grep]
- `.planning/REQUIREMENTS.md` - PERF-04, PERF-05, PACK-01, PACK-04, PACK-05 and v1 out-of-scope boundaries. [VERIFIED: codebase grep]
- `.planning/STATE.md` - accumulated Phase 1-9 decisions and current Phase 10 focus. [VERIFIED: codebase grep]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt` and Android mirror - UDP codec and debug summary. [VERIFIED: codebase grep]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - receiver lifecycle/replay guard boundary. [VERIFIED: codebase grep]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` and `VisualizerMetrics.kt` - visualizer/checklist/metrics replay outputs. [VERIFIED: codebase grep]
- `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` - Android dashboard diagnostic source surfaces. [VERIFIED: codebase grep]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt` - central redaction extension point. [VERIFIED: codebase grep]
- `docs/protocol/lan-pairing-v1.md` and `docs/protocol/input-stream-v1-fixtures.md` - current protocol/session/input frame docs. [VERIFIED: codebase grep]
- `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` and `docs/evidence/manifests/phase6-windows-virtual-joystick.jsonl` - known-limit and OS-visible path evidence pointers. [VERIFIED: codebase grep]

### Secondary (MEDIUM confidence)

- Android `BluetoothHidDevice` API reference - HID Device Service proxy and callback surface. [CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice]
- Android command-line testing docs - Gradle command-line test workflow for Android apps. [CITED: https://developer.android.com/studio/test/command-line]
- Android logcat docs - command-line log output behavior. [CITED: https://developer.android.com/tools/logcat]
- Gradle JVM testing docs - Gradle Test task/testing model. [CITED: https://docs.gradle.org/current/userguide/java_testing.html]
- Kotlin serialization docs - JSON serialization component. [CITED: https://kotlinlang.org/docs/serialization.html]
- Microsoft VHF docs - Windows VHF supported source-driver framework. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]
- Microsoft test-signing docs - Windows test-signed driver mode context. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option]
- OWASP ASVS project - security categories used for validation framing. [CITED: https://owasp.org/www-project-application-security-verification-standard/]

### Tertiary (LOW confidence)

- None used as authority. [VERIFIED: source selection]

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH - Existing repo stack and local tool versions were verified from Gradle files and command probes; no new packages recommended. [VERIFIED: build files; VERIFIED: local probes]
- Architecture: HIGH - Phase 10 decisions are locked and integration seams exist in code. [VERIFIED: `10-CONTEXT.md`; VERIFIED: listed source files]
- Pitfalls: HIGH - Pitfalls derive from prior phase state, existing tests, and explicit Phase 10 constraints. [VERIFIED: `.planning/STATE.md`; VERIFIED: `10-CONTEXT.md`; VERIFIED: existing tests]
- Environment: MEDIUM - Local tool probes verified Java/Gradle/ADB, but no Android device is attached and Windows target availability was not probed from this machine. [VERIFIED: local probes]

**Research date:** 2026-06-15
**Valid until:** 2026-07-15 for repo-local architecture; recheck external Android/Gradle/Microsoft docs before changing toolchain or driver setup. [ASSUMED]
