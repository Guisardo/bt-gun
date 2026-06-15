# Phase 10: Diagnostics, Replay, and v1 Docs - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 19
**Analogs found:** 19 / 19

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `fixtures/replay/README.md` | docs/fixture | file-I/O | `docs/protocol/input-stream-v1-fixtures.md` | role-match |
| `fixtures/replay/udp-golden/mapped-session-001.hex` | fixture | file-I/O | `docs/protocol/input-stream-v1-fixtures.md` | exact |
| `fixtures/replay/udp-golden/mapped-session-001.jsonl` | fixture | event-driven | `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl` | role-match |
| `fixtures/replay/expected/mapped-session-001-visualizer.json` | fixture | transform | `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` | partial |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt` | model | event-driven | `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt` | role-match |
| `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt` | utility/service | file-I/O | `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt` | partial |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt` | test | batch/transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt` | exact |
| `desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticExportTest.kt` | test | file-I/O | `desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticEvent.kt` | model | event-driven | `android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt` | role-match |
| `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticReporter.kt` | service | event-driven | `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt` | role-match |
| `android-host/app/src/test/java/com/btgun/host/diagnostics/DiagnosticEventTest.kt` | test | event-driven | `android-host/app/src/test/java/com/btgun/host/session/VisualizerStatusTest.kt` | role-match |
| `android-host/app/src/test/java/com/btgun/host/diagnostics/DiagnosticRedactionTest.kt` | test | transform | `desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt` | partial |
| `desktop-companion/build.gradle.kts` | config | batch | `desktop-companion/build.gradle.kts` | exact |
| `android-host/app/build.gradle.kts` | config | batch | `android-host/app/build.gradle.kts` | exact |
| `docs/v1.md` | docs | request-response | `docs/setup/android-bluetooth-hid-gamepad.md` | role-match |
| `docs/setup/android-build-device-testing.md` | docs | batch/file-I/O | `docs/setup/android-bluetooth-hid-gamepad.md` | role-match |
| `docs/protocol/lan-session-security-v1.md` | docs | request-response/streaming | `docs/protocol/lan-pairing-v1.md` | exact |
| `docs/diagnostics/replay-and-troubleshooting.md` | docs | batch/event-driven | `android-host/scripts/collect-phase2-host-evidence.sh` | role-match |
| `docs/limits/v1-compatibility-limits.md` | docs | matrix/evidence | `docs/setup/android-bluetooth-hid-gamepad.md` | role-match |

## Pattern Assignments

### `fixtures/replay/*` (fixture/docs, file-I/O/event-driven)

**Analog:** `docs/protocol/input-stream-v1-fixtures.md`

**Fixture contract pattern** (lines 10-24):
```markdown
| Field | Value |
|-------|-------|
| Magic | `BTGI` |
| Version | `1` |
| Stream session id | `00112233445566778899aabbccddeeff` |
| Frame size | `120` bytes |
| HMAC tag size | `32` bytes |
| HMAC input | bytes `0..87` |
| HMAC tag | bytes `88..119` |
```

**Hex fixture pattern** (lines 52-56):
```markdown
## GOLDEN_SNAPSHOT_FRAME_HEX

~~~text
425447490101000000112233445566778899aabbccddeeff...
~~~
```

**Boundary/redaction pattern** (lines 102-106):
```markdown
- UDP payloads are fixed binary frames, not JSON.
- Motion fields are raw provider/capability/yaw/pitch/roll/raw-aim values only.
- Android preview-derived product fields, desktop-side mapper details, pairing one-time material, fallback digits, proof material, and stream auth material must not appear in debug output.
```

**JSONL manifest analog:** `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`

**Schema row pattern** (line 1):
```json
{"schema":"btgun.phase7.android_bluetooth_hid_manifest.v1","record_type":"schema","description":"Schema and redaction policy...","accepted_record_types":[...],"required_fields":["schema","record_type","capture_id","scenario","target","status","sanitized","evidence_refs","blocking_notes"],"status_values":["pass","blocked","unsupported","inconclusive"],"redaction_policy":{"forbidden":[...],"required":"Rows must use sanitized labels, local artifact paths, and non-identifying status text only."}}
```

Apply: first replay JSONL row should be a schema/redaction row; following rows use sanitized fixture ids, `capture_id`, `status`, `evidence_refs`, and no raw secrets.

---

### `desktop-companion/src/test/kotlin/com/btgun/desktop/replay/ReplayFixtureTest.kt` (test, batch/transform)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapterTest.kt`

**Imports/main-function test pattern** (lines 1-18):
```kotlin
package com.btgun.desktop.backend

import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputFrame
import com.btgun.desktop.transport.UdpInputFrameCodec
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult

fun main() {
    snapshotFixtureReplaysThroughReceiverBeforeMapping()
    edgeFixtureMapsSemanticControlsAndNeutralizesNanAim()
}
```

**Core replay-through-receiver pattern** (lines 20-36):
```kotlin
private fun snapshotFixtureReplaysThroughReceiverBeforeMapping() {
    val state = acceptFrame(productFrame(sequence = 42L, buttonBitmask = BUTTON_R2 or BUTTON_L2 or BUTTON_B2))
        .toSemanticState()

    expectEquals("trigger", true, state.trigger)
    expectEquals("reload", true, state.reload)
    expectEquals("aimX", 0.375f, state.aimX)
    expectEquals("aimY", -0.625f, state.aimY)
    expectEquals("sourceSequence", 42L, state.sourceSequence)
}
```

**Guard before mapping pattern** (lines 142-154):
```kotlin
private fun acceptFrame(frame: UdpInputFrame): UdpInputReceiverResult.Accepted {
    val result = startedReceiver().handleDatagram(
        bytes = UdpInputFrameCodec.encode(frame, fixtureConfig()),
        receivedElapsedNanos = RECEIVED_ELAPSED_NANOS,
    )
    if (result !is UdpInputReceiverResult.Accepted) {
        throw AssertionError("fixture must be accepted by UdpInputReceiver before adapter, got $result")
    }
    return result
}

private fun UdpInputReceiverResult.Accepted.toSemanticState(): SemanticControllerState =
    UdpControllerStateAdapter.toState(input)
```

Also copy `VisualizerModel.withAcceptedInput` and metrics flow from `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt` lines 137-173 and `VisualizerMetrics.kt` lines 64-131 to complete PERF-04 chain.

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticEvent.kt` (model, event-driven)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlDiagnostics.kt`

**Simple data model pattern** (lines 1-8):
```kotlin
package com.btgun.desktop.control

data class ControlDiagnostics(
    val sessionState: String,
    val desktopIdentitySuffix: String,
    val heartbeatAgeMillis: Long?,
    val lastControlError: String?,
)
```

Apply with locked fields: `schema`, `tsElapsed`, `domain`, `status`, `reasonCode`, `detail`, `sessionRefs`, `context`. Prefer enums with wire names for domains `gun_ble`, `sensor_motion`, `lan_control_udp`, `profile_mapping`, `hid_backend_haptics` and statuses `ok`, `degraded`, `blocked`, `unsupported`, `unknown`.

**Desktop control integration analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt`

**UI model update pattern** (lines 175-197):
```kotlin
fun withPacketLifecycle(state: InputStreamLifecycleState): VisualizerModel =
    copy(packetLifecycle = state)

fun withControlSessionState(state: ControlServerSessionState): VisualizerModel =
    copy(
        controlSessionState = state,
        packetLifecycle = when (state) {
            ControlServerSessionState.AUTHENTICATED -> InputStreamLifecycleState.ACTIVE
            ControlServerSessionState.DEGRADED -> InputStreamLifecycleState.STALE
            ControlServerSessionState.DISCONNECTED,
            ControlServerSessionState.STOPPED,
            -> InputStreamLifecycleState.STOPPED
            else -> packetLifecycle
        },
    )

fun withInputRejection(reason: String): VisualizerModel =
    copy(rawDebug = rawDebug.copy(lastRejection = reason.take(80)))
```

---

### `desktop-companion/src/main/kotlin/com/btgun/desktop/diagnostics/DiagnosticExport.kt` (utility/service, file-I/O)

**Analog:** `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt`

**Central utility pattern** (lines 1-19):
```kotlin
package com.btgun.desktop.security

object SecretRedactor {
    private val rules = listOf(
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", setOf(RegexOption.DOT_MATCHES_ALL)) to "<redacted-private-key>",
        Regex("(qr_secret=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("(code=)\\d{6}") to "$1<redacted>",
        Regex("((?:pairing_)?proof=)[A-Za-z0-9_-]+") to "$1<redacted>",
    )

    fun redact(value: String): String =
        rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
}
```

**Export boundary analog:** `android-host/scripts/collect-phase2-host-evidence.sh`

**Raw evidence stays ignored pattern** (lines 4-25):
```bash
OUT_ROOT="${1:-.evidence/phase2/host-live-input}"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
OUT_DIR="${OUT_ROOT}/${RUN_ID}"

mkdir -p "${OUT_DIR}"

adb logcat -d -v threadtime > "${OUT_DIR}/logcat-threadtime.txt"
adb shell dumpsys bluetooth_manager > "${OUT_DIR}/dumpsys-bluetooth-manager.txt"
adb exec-out screencap -p > "${OUT_DIR}/dashboard-screenshot.png" || true
```

Apply: raw capture may land under ignored `.evidence/`; committed export bundle includes only sanitized JSONL, replay clips, app/build versions, capability statuses, and manifest pointer.

---

### `desktop-companion/src/test/kotlin/com/btgun/desktop/diagnostics/DiagnosticExportTest.kt` (test, file-I/O/transform)

**Analog:** `desktop-companion/src/test/kotlin/com/btgun/desktop/security/PairingSecurityTest.kt`

**Main test registration pattern** (lines 11-19):
```kotlin
fun main() {
    correctQrProofAcceptsExactlyOnce()
    expiredSessionRejectsBeforeTrustedState()
    wrongManualCodeAndQrSecretReject()
    replayedAndroidNonceRejects()
    fingerprintMismatchRejectsBeforeTrustedState()
    exhaustedAttemptsLockSession()
    redactorHidesProofMaterialAndPrivateKeyMarkers()
}
```

**Redaction assertion pattern** (lines 143-159):
```kotlin
private fun redactorHidesProofMaterialAndPrivateKeyMarkers() {
    val redacted = SecretRedactor.redact(
        "qr_secret=abcdefghijklmnopqrstuvwxyzABCDEF code=123456 proof=abcdef0123456789 " +
            "pairing_proof=nonce-abcdef0123456789 X-BT-Gun-Pairing-Proof: feedface " +
            "private_key=-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----",
    )

    expectFalse("no qr secret", redacted.contains("abcdefghijklmnopqrstuvwxyzABCDEF"))
    expectFalse("no manual code", redacted.contains("123456"))
    expectFalse("no proof", redacted.contains("abcdef0123456789"))
    expectFalse("no private key marker", redacted.contains("BEGIN PRIVATE KEY"))
}
```

Extend for stream keys, HMAC material, full Bluetooth addresses, full serials, Android IDs, raw screenshots/log dumps.

---

### `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticEvent.kt` (model, event-driven)

**Analog:** `android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt`

**Imports and JSON body pattern** (lines 1-31):
```kotlin
package com.btgun.host.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class VisualizerStatus(...) {
    fun toJsonBody(): JsonObject =
        buildJsonObject {
            put("rawDebugEnabled", rawDebugEnabled)
            put("aimZeroState", aimZeroState.sanitizedState())
            put("recenterState", recenterState.sanitizedState())
            put("androidElapsedNanos", androidElapsedNanos.coerceAtLeast(0L))
            put("recenterLabel", recenterLabel.sanitizedLabel())
            put("aimZeroLabel", aimZeroLabel.sanitizedLabel())
        }
}
```

**Validation/sanitization pattern** (lines 43-62):
```kotlin
private const val MAX_LABEL_CHARS = 48
private val safeStatePattern = Regex("^[a-z][a-z0-9_]{0,31}$")

private fun String.sanitizedState(): String {
    val value = trim()
    return if (safeStatePattern.matches(value)) value else STATE_UNAVAILABLE
}

private fun String.sanitizedLabel(): String {
    val normalized = trim()
        .filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' || it == ':' || it == '/' }
        .replace(Regex("\\s+"), " ")
        .ifBlank { STATE_UNAVAILABLE }
    return normalized.take(MAX_LABEL_CHARS)
}
```

Apply: serialize only whitelisted diagnostic fields; coerce timestamps nonnegative; sanitize detail/reason text.

---

### `android-host/app/src/main/java/com/btgun/host/diagnostics/DiagnosticReporter.kt` (service, event-driven)

**Analog:** `android-host/app/src/main/java/com/btgun/host/ui/DashboardState.kt`

**State aggregation pattern** (lines 178-205):
```kotlin
fun from(
    permissionGateState: PermissionGateState,
    hostSessionState: HostSessionState,
    bleConnectionState: BleGunConnectionState = BleGunConnectionState(),
    ...
    nowElapsedNanos: Long = lastGunEvent?.emittedElapsedNanos
        ?: lastMotionSample?.emittedElapsedNanos
        ?: lastRecenterStatus?.emittedElapsedNanos
        ?: 0L,
): DashboardState {
    val placeholders = DashboardPlaceholders(
        desktopLink = formatDesktopLink(desktopLinkState),
        packetStream = formatPacketStream(hostSessionState.packetStreamState),
    )
    val desktopControlError = desktopLinkState.lastControlError
        ?.takeUnless { it.equals("none", ignoreCase = true) }
    return DashboardState(
```

**Dashboard field output pattern** (lines 213-237):
```kotlin
gunConnection = DashboardField("Gun connection", gunConnectionValue(hostSessionState, bleConnectionState)),
motionProvider = DashboardField("Motion provider", lastMotionSample?.payload?.providerName ?: "unavailable"),
motionCapabilities = DashboardField("Motion capability flags", formatCapabilities(lastMotionSample?.payload?.capabilities)),
profile = formatProfile(hostSessionState),
phoneHaptic = DashboardPhoneHaptic(
    label = "Phone haptic",
    capability = phoneHapticStatus.capability,
    lastLocalTest = phoneHapticStatus.lastLocalTest,
),
hidGamepad = formatHidGamepad(
    hidRole = permissionGateState.bluetoothHidRole,
    status = hostSessionState.hidGamepadStatus,
),
```

**Logging pattern:** `android-host/app/src/main/java/com/btgun/host/util/AndroidLog.kt` lines 5-24. Use `AndroidLog.i/w`, not raw `Log.*`, in unit-testable diagnostics code.

---

### `desktop-companion/build.gradle.kts` and `android-host/app/build.gradle.kts` (config, batch)

**Desktop analog:** `desktop-companion/build.gradle.kts`

**Main-function test runner pattern** (lines 49-97):
```kotlin
tasks.withType<Test>().configureEach {
    filter {
        includeTestsMatching("com.btgun.no_junit_tests.*")
        isFailOnNoMatchingTests = false
    }

    doLast {
        listOf(
            "com.btgun.desktop.transport.UdpInputReceiverTestKt",
            "com.btgun.desktop.ui.VisualizerMetricsTestKt",
            "com.btgun.desktop.ui.VisualizerModelTestKt",
            "com.btgun.desktop.backend.UdpControllerStateAdapterTestKt",
        ).forEach { testClass ->
            providers.exec {
                commandLine("java", "-cp", project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath, testClass)
            }.result.get().assertNormalExitValue()
        }
    }
}
```

**Android analog:** `android-host/app/build.gradle.kts`

**Android test registration pattern** (lines 48-85):
```kotlin
doLast {
    listOf(
        "com.btgun.host.session.VisualizerStatusTestKt",
        "com.btgun.host.transport.UdpInputFrameCodecTestKt",
        "com.btgun.host.ui.DashboardStateTestKt",
    ).forEach { testClass ->
        providers.exec {
            commandLine(
                "java",
                "-cp",
                project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath,
                testClass,
            )
        }.result.get().assertNormalExitValue()
    }
}
```

Apply: add new Phase 10 test main classes to these lists when tests are created.

---

### `docs/v1.md`, `docs/setup/android-build-device-testing.md`, `docs/protocol/lan-session-security-v1.md`, `docs/diagnostics/replay-and-troubleshooting.md`, `docs/limits/v1-compatibility-limits.md` (docs)

**Android setup analog:** `docs/setup/android-bluetooth-hid-gamepad.md`

**Compatibility gate matrix pattern** (lines 7-24):
```markdown
## Compatibility Gate

Use Android HID mode only when the phone can provide the platform `BluetoothProfile.HID_DEVICE` role.

| Gate | Pass state | Blocked state |
|------|------------|---------------|
| Bluetooth adapter | Enabled | Bluetooth off or unavailable |
| Runtime permission | `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE` granted on Android 12+ | Nearby Devices/Bluetooth permission missing |
| HID profile proxy | `HID_DEVICE` proxy available | Phone/OEM does not expose HID Device profile |
```

**Step workflow pattern** (lines 26-39):
```markdown
## macOS Pairing Proof

1. Start the Android host app and connect the iPega gun if live controls are being tested.
2. Tap **Start Bluetooth gamepad**.
3. Wait for the HID proxy/registration row to show active or registered.
...
10. Record only sanitized evidence rows in `docs/evidence/manifests/phase7-android-bluetooth-hid.jsonl`.
```

**Redaction rules pattern** (lines 120-132):
```markdown
Committed setup docs and evidence rows must be sanitized.

Do not commit:

- Bluetooth MAC addresses.
- Phone serials, Android IDs, stable hardware identifiers, or account names.
- Pairing credentials, manual pairing codes, transport secrets, cryptographic signing material, transcript secrets, or raw Bluetooth dumps.
- Screenshots or screenshot paths that may contain sensitive device names.
```

**Protocol/security analog:** `docs/protocol/lan-pairing-v1.md`

**Control envelope table pattern** (lines 101-131):
```markdown
## Reliable Control Channel

Control envelopes are JSON objects with these fields:

| Field | Type | Meaning |
|-------|------|---------|
| `v` | integer | Protocol version. Must be `1`. |
| `type` | string | One of the allowed control message type wire names. |
| `msgId` | string | Message id for diagnostics and local correlation. |
| `sessionId` | string | Trusted pairing session id. Desktop rejects mismatches. |
```

**UDP contract pattern** (lines 179-215): copy the field table and rejection rules style; add diagnostics/replay references instead of duplicating conflicting schemas.

**Known-limits pattern:** use `docs/setup/android-bluetooth-hid-gamepad.md` lines 134-138: state primary/fallback/deferred boundaries directly, with evidence manifest pointer and next proof needed.

## Shared Patterns

### Authenticated UDP Decode
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt`
**Apply to:** replay tests, replay fixtures, docs protocol tables
```kotlin
fun authenticateAndDecode(bytes: ByteArray, config: InputStreamConfig): UdpInputFrameDecodeResult {
    if (bytes.size != FRAME_SIZE) return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.INVALID_LENGTH, "size=${bytes.size}")
    if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_MAGIC)
    val streamSessionId = bytes.copyOfRange(8, OFFSET_SEQUENCE).toHex()
    if (streamSessionId != config.streamSessionIdHex) return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.WRONG_STREAM_SESSION)
    if (!MessageDigest.isEqual(expectedTag, actualTag)) return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_HMAC)
}
```
Lines 160-180.

### Receiver Before Mapping
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt`
**Apply to:** PERF-04 replay chain
```kotlin
return when (val decision = activeGuard.acceptDatagram(bytes, receivedElapsedNanos, controlSessionId)) {
    is InputReplayDecision.Accepted -> {
        current = decision.input
        onInput(decision.input)
        UdpInputReceiverResult.Accepted(decision.input)
    }
    is InputReplayDecision.Rejected -> UdpInputReceiverResult.Rejected(decision.reason)
}
```
Lines 41-51.

### Visualizer Model and Metrics
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt`, `VisualizerMetrics.kt`
**Apply to:** replay expected snapshots and desktop diagnostics display
```kotlin
fun withAcceptedInput(input: UdpReceivedInput, observedElapsedNanos: Long): VisualizerModel {
    val state = UdpControllerStateAdapter.toState(input)
    return copy(
        liveState = state,
        checklistRows = checklistRows
            .markObserved(id = VisualizerChecklistRowId.LAN_VISUALIZER_STREAM, source = "authenticated LAN mapped UDP frame", observedElapsedNanos = observedElapsedNanos)
            .markObserved(id = VisualizerChecklistRowId.LIVE_CONTROLS, source = "live mapped controls and aim stream", observedElapsedNanos = observedElapsedNanos),
    )
}
```
`VisualizerModel.kt` lines 137-173. Metrics `record` calculates packet expected/missed, latency, offset quality, and target status at `VisualizerMetrics.kt` lines 64-131.

### Redaction Gate
**Source:** `desktop-companion/src/main/kotlin/com/btgun/desktop/security/SecretRedactor.kt`
**Apply to:** all committed diagnostics, replay JSONL, manifest rows, docs examples
```kotlin
fun redact(value: String): String =
    rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
```
Lines 17-18. Extend rules, do not create parallel redactors.

### Android JSON Whitelist
**Source:** `android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt`
**Apply to:** Android diagnostic JSON/event bodies
```kotlin
fun toJsonBody(): JsonObject =
    buildJsonObject {
        put("rawDebugEnabled", rawDebugEnabled)
        put("aimZeroState", aimZeroState.sanitizedState())
        put("androidElapsedNanos", androidElapsedNanos.coerceAtLeast(0L))
        put("recenterLabel", recenterLabel.sanitizedLabel())
    }
```
Lines 19-31.

### Evidence Safety
**Source:** `docs/setup/android-bluetooth-hid-gamepad.md`
**Apply to:** docs, fixtures, export bundles, manifests
```markdown
Do not commit:

- Bluetooth MAC addresses.
- Phone serials, Android IDs, stable hardware identifiers, or account names.
- Personal device names or nearby-device names that identify people or hardware.
- Pairing credentials, manual pairing codes, transport secrets, cryptographic signing material, transcript secrets, or raw Bluetooth dumps.
- Screenshots or screenshot paths that may contain sensitive device names.
```
Lines 120-132.

## No Analog Found

None. Some files have only partial analogs because Phase 10 creates the first formal replay/export layer, but every file has usable codebase patterns.

## Metadata

**Analog search scope:** `android-host/`, `desktop-companion/`, `docs/`, `fixtures/`, `.planning/phases/10-diagnostics-replay-and-v1-docs/`
**Files scanned:** repo file list plus targeted analog reads
**Pattern extraction date:** 2026-06-15
