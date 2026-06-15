# Phase 05: desktop-backend-contract-and-smoke-harness - Research

**Researched:** 2026-06-09
**Domain:** Kotlin/JVM desktop backend contract, UDP fixture replay, platform smoke harness, virtual HID handoff
**Confidence:** HIGH for existing Kotlin patterns and Phase 5 scope; MEDIUM for future Windows/macOS driver risk because Phase 5 stubs intentionally stop before OS-visible HID devices.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Backend Contract
- **D-01:** Phase 5 uses a normalized controller backend contract. It consumes stable semantic controller state and hides Windows/macOS implementation details behind later platform adapters.
- **D-02:** Phase 5 includes a small adapter from `UdpReceivedInput` to normalized controller state so the Phase 4 receiver handoff is proven now. Do not add configurable profile mapping in Phase 5.
- **D-03:** Controller state uses named semantic controls: `trigger`, `reload`, `x`, `y`, `a`, `b`, `stickX`, `stickY`, `aimX`, and `aimY`. Backend adapters decide HID report packing later.
- **D-04:** The backend surface exposes current controller state, backend capabilities, lifecycle, and the last publish/error result.

### Fake Smoke Harness
- **D-05:** Phase 5 uses separate macOS and Windows platform stub commands. These stubs do not need to create OS-visible virtual HID devices, but they must catch platform command wiring and backend contract assumptions before Phase 6/7.
- **D-06:** The smoke harness feeds replayed UDP fixtures through the receiver path rather than only publishing direct semantic frames.
- **D-07:** Phase 5 is not accepted unless smoke commands run on both macOS and Windows.
- **D-08:** Smoke evidence should be emitted as JUnit-style test output.

### Gamepad Descriptor Shape
- **D-09:** The v1 controller is one regular gamepad-like joystick device, not a custom gun HID report, mouse-like aim device, or split multi-device shape.
- **D-10:** The v1 axes are physical stick X/Y plus motion aim X/Y. Do not expose raw yaw, pitch, roll, or Android-local preview aim as HID axes in Phase 5.
- **D-11:** The v1 buttons are trigger, reload, X, Y, A, and B. Physical stick direction buttons should not duplicate the stick axes in the descriptor contract.
- **D-12:** Trigger is digital only in v1 because current iPega evidence is binary. Do not invent an analog trigger axis.

### Capability Flags
- **D-13:** Backends expose a structured capability object, not only a bitmask or freeform diagnostic string.
- **D-14:** Unsupported features must include explicit unsupported reasons with platform and detail fields so platform limits are visible to later planning and diagnostics.
- **D-15:** Haptic capabilities use a detailed effect matrix with strength, duration, pattern, phone-haptic, and output-report support limits. This avoids conflating v1 phone haptics with future OS output-report support.
- **D-16:** Required invariant tests must prove the capability object matches the descriptor contract: six buttons, four axes, digital trigger, and declared haptic/output support.

### Haptic and Output Boundary
- **D-17:** Phase 5 smoke sends a real phone haptic command through the existing desktop-to-Android control path.
- **D-18:** Platform stubs simulate a future HID output report and verify it routes to a phone haptic command.
- **D-19:** Android absence fails Phase 5 haptic smoke. The pass path requires a paired Android session.
- **D-20:** Haptic smoke evidence requires human confirmation that the phone vibrated. Sending a command alone is not enough.

### the agent's Discretion
- Choose exact Kotlin package names, interface/class names, immutable state representation, axis numeric ranges, fixture filenames, JUnit XML schema details, and stub command names during planning.
- Choose how to store cross-platform smoke artifacts, provided both macOS and Windows runs are distinguishable and auditable.
- Choose whether the first UDP replay fixture is generated from existing codec helpers or checked in as a small sanitized binary/JSON fixture.

### the agent's Discretion
See locked `### the agent's Discretion` section above.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

- Production Windows virtual HID implementation remains Phase 6.
- Production macOS virtual HID implementation remains Phase 7.
- Profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Packet replay diagnostics beyond the Phase 5 smoke harness remain Phase 10.
- Physical gun motor rumble remains v2/deferred.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DESK-04 | Virtual joystick descriptor exposes trigger, reload, joystick axes, X/Y/A/B buttons, and aim axes. | Use `VirtualControllerDescriptor` with six buttons, four axes, digital trigger, and invariant tests before platform packing. [VERIFIED: `.planning/REQUIREMENTS.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| DESK-07 | Desktop companion exposes backend capability flags for buttons, axes, haptic feedback, output reports, and platform limitations. | Use structured `BackendCapabilities` plus explicit `UnsupportedReason(platform, detail)` and haptic effect matrix. [VERIFIED: `.planning/REQUIREMENTS.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| DESK-08 | Developer can run fake-input virtual controller smoke test on both Windows and macOS before using the real Android stream. | Add shared contract tests plus `smokeDesktopBackendMacosStub` and `smokeDesktopBackendWindowsStub` commands that replay UDP fixtures through `UdpInputReceiver`/`DesktopUdpInputRuntime` and emit JUnit-style XML. [VERIFIED: `.planning/ROADMAP.md`; `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/*`] |
</phase_requirements>

## Summary

Phase 5 should add a new Kotlin/JVM desktop backend layer under `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/` and keep it semantic, immutable, and platform-neutral. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`; `desktop-companion/build.gradle.kts`] It should not create OS-visible Windows VHF or macOS CoreHID/HIDDriverKit devices yet; it should prove that future adapters receive one stable controller state shape, one descriptor contract, and one structured capability model. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]

Use the existing Phase 4 UDP receiver path as the input handoff: fixture datagram -> `UdpInputReceiver` or `DesktopUdpInputRuntime` -> `UdpReceivedInput` -> `UdpControllerStateAdapter` -> backend `publish(state)`. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt`; `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/DesktopUdpInputRuntime.kt`; `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt`] Do not add profile mapping; map Phase 4 `rawAimX/rawAimY` directly into semantic `aimX/aimY` only as Phase 5's fixed smoke/default adapter. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`; `docs/protocol/input-stream-v1-fixtures.md`]

Current desktop tests are plain Kotlin `main()` classes invoked from Gradle `Test.doLast`, and a successful `gradle test` run on 2026-06-09 produced Gradle binary/html test output but no `*.xml` files. [VERIFIED: `desktop-companion/build.gradle.kts`; command `env GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test`; `desktop-companion/build/test-results/test/binary/*`] Therefore Phase 5 smoke must add its own JUnit-style XML artifact writer or intentionally migrate tests to a real test framework; the no-new-dependency path is a tiny local XML writer used by platform smoke commands. [VERIFIED: local command output; CITED: https://docs.gradle.org/current/userguide/java_testing.html]

**Primary recommendation:** Implement `VirtualControllerBackend`, `SemanticControllerState`, `VirtualControllerDescriptor`, `BackendCapabilities`, `UdpControllerStateAdapter`, `StubVirtualControllerBackend`, and two platform smoke entrypoints that share contract tests and emit auditable per-platform JUnit-style XML. [VERIFIED: local Kotlin patterns; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Semantic controller state | Desktop companion backend | Platform adapters | State owns named controls before HID report packing so Windows/macOS divergence is isolated later. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| UDP fixture replay | Desktop transport | Backend adapter | Reuse Phase 4 receiver/replay protection before backend publish; do not bypass trust/codec behavior with direct state injection. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt`; `docs/protocol/input-stream-v1-fixtures.md`] |
| Descriptor contract | Desktop backend contract | Windows/macOS adapters | Descriptor invariants define six buttons/four axes/digital trigger before platform-specific HID bytes. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| Capability reporting | Desktop backend contract | Pairing UI diagnostics later | Structured capabilities must expose haptics/output/platform limits before Phase 6/7 implementation. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| Phone haptic smoke | Control channel | Backend output-report simulation | Phase 5 simulated output reports should route to existing `ControlServer.sendHapticCommand`, not create new haptic transport. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`; `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt`] |
| Platform smoke evidence | Gradle/Kotlin CLI | Human UAT | Stubs prove command wiring on macOS and Windows; real Android haptic pass still needs human phone-vibration confirmation. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |

## Project Constraints (from AGENTS.md)

- Keep chat and generated project docs short, factual, and agent-facing. [VERIFIED: `AGENTS.md`]
- v1 must support Windows 11 x64 and macOS Apple Silicon; protocol and virtual-controller contract must not assume one platform. [VERIFIED: `AGENTS.md`; `.planning/PROJECT.md`]
- Android-to-desktop v1 transport is Wi-Fi/LAN, not desktop-to-gun Bluetooth. [VERIFIED: `AGENTS.md`; `.planning/PROJECT.md`]
- Visualizer path targets under 50 ms and needs timestamp/latency measurement early, but Phase 5 does not build the visualizer dashboard. [VERIFIED: `AGENTS.md`; `.planning/ROADMAP.md`]
- Desktop HID shape must be normal gamepad/joystick, not custom HID gun report. [VERIFIED: `AGENTS.md`; `.planning/PROJECT.md`]
- Desktop profiles own aim mapping; Android sends normalized gyro/raw aim data. [VERIFIED: `AGENTS.md`; `.planning/PROJECT.md`]
- Phase 1 evidence rule remains relevant for hardware/protocol claims, but Phase 5 is a desktop contract/smoke phase and should not reopen physical gun protocol discovery. [VERIFIED: `AGENTS.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
- No project skills exist under `.codex/skills/` or `.agents/skills/`; no project-skill rules apply. [VERIFIED: local `find .codex ...`; local `.agents` absent]

## Standard Stack

### Core

| Library / Tool | Version | Purpose | Why Standard |
|----------------|---------|---------|--------------|
| Kotlin/JVM Gradle plugin | `2.0.21` in `desktop-companion/build.gradle.kts` | Backend contract, adapters, smoke entrypoints | Existing desktop companion is Kotlin/JVM; adding a new stack would slow Phase 5 and complicate Phase 6/7 handoff. [VERIFIED: `desktop-companion/build.gradle.kts`] |
| Java toolchain | JVM 17 target in Gradle; local Java `/opt/homebrew/opt/openjdk@17/bin/java` reports `17.0.19` | Compile/run desktop companion | Existing build pins Java 17 and local JDK 17 is present. [VERIFIED: `desktop-companion/build.gradle.kts`; local `java -version`] |
| Gradle application plugin | Present in `desktop-companion/build.gradle.kts` | Add JavaExec smoke commands | Gradle Application plugin provides `run`/JavaExec style launch support for JVM main classes. [VERIFIED: `desktop-companion/build.gradle.kts`; CITED: https://docs.gradle.org/current/userguide/application_plugin.html] |
| Existing transport codec/receiver | Phase 4 code | Replay accepted UDP fixtures into backend | `UdpInputFrameCodec`, `UdpInputReceiver`, and `DesktopUdpInputRuntime` already validate/authenticate/drop stale/replayed input. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputFrameCodec.kt`; `UdpInputReceiver.kt`; `DesktopUdpInputRuntime.kt`] |
| Existing control/haptic path | Phase 4 code | Phone haptic smoke and output-report simulation | `ControlServer.sendHapticCommand` already gates haptic sends on active trusted sessions and uses `reserved_haptic_command`. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`; `docs/protocol/lan-pairing-v1.md`] |

### Supporting

| Library / Tool | Version | Purpose | When to Use |
|----------------|---------|---------|-------------|
| `kotlinx-serialization-json` | `1.11.0` in desktop build | JSON control body handling already present | Reuse only if smoke artifact metadata needs JSON sidecars; JUnit XML can be written without it. [VERIFIED: `desktop-companion/build.gradle.kts`] |
| Ktor server modules | `3.5.0` in desktop build | Existing WSS control server | Use only for live Android haptic smoke through `ControlServer`; do not add new network path. [VERIFIED: `desktop-companion/build.gradle.kts`; `ControlServer.kt`] |
| JUnit-style XML writer | Local Kotlin utility, no dependency | Cross-platform smoke artifacts | Needed because current main-style tests do not produce XML files. [VERIFIED: local `gradle test` artifact inspection] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Local Kotlin JUnit-style XML writer | Add JUnit Jupiter dependency and migrate tests | Real Gradle XML would be cleaner, but project convention says do not introduce JUnit unless test style intentionally changes. [VERIFIED: `.planning/phases/04-input-stream-and-haptic-transport/04-PATTERNS.md`; `desktop-companion/build.gradle.kts`] |
| Stub backend smoke | Real Windows VHF or macOS CoreHID device | Out of Phase 5 scope; Phase 6/7 own OS-visible virtual devices. [VERIFIED: `.planning/ROADMAP.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| Fixed smoke adapter | Configurable profile mapper | Profiles/mapping are Phase 8; adding them now violates Phase 5 boundary. [VERIFIED: `.planning/ROADMAP.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |

**Installation:** No new external packages are recommended for Phase 5. [VERIFIED: existing Gradle deps; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]

```bash
cd desktop-companion
GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test
GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle smokeDesktopBackendMacosStub
GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle smokeDesktopBackendWindowsStub
```

**Version verification:** Existing package versions were verified from `desktop-companion/build.gradle.kts`; no new registry package is required, so npm/PyPI/crates verification and slopcheck are not applicable. [VERIFIED: local build file]

## Package Legitimacy Audit

No new external package install is recommended for Phase 5. [VERIFIED: local build strategy] Existing dependencies remain `ktor 3.5.0`, `kotlinx-serialization-json 1.11.0`, and ZXing `3.5.4`; Phase 5 does not need additional registry coordinates. [VERIFIED: `desktop-companion/build.gradle.kts`]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| none | — | — | — | — | not run | No install planned. [VERIFIED: local build strategy] |

**Packages removed due to slopcheck [SLOP] verdict:** none. [VERIFIED: no packages proposed]
**Packages flagged as suspicious [SUS]:** none. [VERIFIED: no packages proposed]

## Architecture Patterns

### System Architecture Diagram

```text
--------------------------+
| Phase 4 UDP fixture     |
| 120-byte BTGI datagram  |
+------------+-------------+
             |
             v
+--------------------------+
| UdpInputReceiver /       |
| DesktopUdpInputRuntime   |
| auth + replay + stale    |
+------------+-------------+
             |
             v
+--------------------------+
| UdpReceivedInput         |
| raw controls + raw aim   |
+------------+-------------+
             |
             v
+--------------------------+
| UdpControllerStateAdapter|
| fixed Phase 5 mapping    |
+------------+-------------+
             |
             v
+--------------------------+
| SemanticControllerState  |
| trigger/reload/x/y/a/b   |
| stickX/stickY/aimX/aimY  |
+------------+-------------+
             |
             v
+--------------------------+
| VirtualControllerBackend |
| descriptor/capabilities  |
| publish/lifecycle/result |
+------+-------------------+
       |
       +--> macOS stub smoke -> JUnit-style XML artifact
       |
       +--> Windows stub smoke -> JUnit-style XML artifact
       |
       +--> simulated output report -> HapticCommand -> ControlServer
```

All arrows above describe existing-or-planned data flow; only the stub backends are new in Phase 5. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`; local transport/control code]

### Recommended Project Structure

```text
desktop-companion/src/main/kotlin/com/btgun/desktop/
├── backend/
│   ├── SemanticControllerState.kt        # named buttons/axes, immutable state
│   ├── VirtualControllerDescriptor.kt    # six-button/four-axis/digital-trigger contract
│   ├── BackendCapabilities.kt            # structured capabilities + unsupported reasons
│   ├── VirtualControllerBackend.kt       # lifecycle + publish + last result
│   ├── StubVirtualControllerBackend.kt   # shared fake backend for contract tests
│   └── UdpControllerStateAdapter.kt      # UdpReceivedInput -> SemanticControllerState
├── smoke/
│   ├── BackendSmokeRunner.kt             # shared fixture replay/assertion flow
│   ├── JunitSmokeXml.kt                  # no-dependency XML writer
│   ├── MacosBackendSmokeMain.kt          # macOS stub command
│   └── WindowsBackendSmokeMain.kt        # Windows stub command
└── control/
    └── existing ControlServer.kt         # reused for real phone-haptic command path

desktop-companion/src/test/kotlin/com/btgun/desktop/backend/
├── BackendContractTest.kt
├── UdpControllerStateAdapterTest.kt
└── BackendCapabilitiesTest.kt
```

This structure follows existing `control`, `transport`, `haptics`, and `ui` package boundaries instead of mixing backend contract code into the transport package. [VERIFIED: local `desktop-companion/src/main/kotlin/com/btgun/desktop/*` layout]

### Pattern 1: Semantic State Before HID Packing

**What:** Keep named controls in a data class and defer platform HID report byte packing to Phase 6/7. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**When to use:** All backend publish paths in Phase 5. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Example:**

```kotlin
// Source: .planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md D-03
data class SemanticControllerState(
    val trigger: Boolean = false,
    val reload: Boolean = false,
    val x: Boolean = false,
    val y: Boolean = false,
    val a: Boolean = false,
    val b: Boolean = false,
    val stickX: Int = 0,
    val stickY: Int = 0,
    val aimX: Float = 0.0f,
    val aimY: Float = 0.0f,
    val stale: Boolean = false,
    val sourceSequence: Long? = null,
)
```

Keep `stickX/stickY` as signed int16-compatible `Int` because Phase 4 UDP frames already expose stick axes as signed int16 values. [VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`; `UdpInputFrameCodec.kt`] Keep `aimX/aimY` as `Float` because Phase 4 raw aim fields are float32 or `NaN`. [VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`; `UdpInputFrameCodec.kt`]

### Pattern 2: Descriptor Invariants as Code

**What:** Represent controller descriptor as data, not comments, then test invariants. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**When to use:** Before any platform adapter or smoke command publishes input. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Example:**

```kotlin
// Source: Phase 5 D-09..D-16
data class VirtualControllerDescriptor(
    val deviceKind: String = "gamepad_like_joystick",
    val buttons: List<String> = listOf("trigger", "reload", "x", "y", "a", "b"),
    val axes: List<String> = listOf("stickX", "stickY", "aimX", "aimY"),
    val triggerKind: String = "digital",
)

fun VirtualControllerDescriptor.requireBtGunV1Invariant() {
    require(buttons == listOf("trigger", "reload", "x", "y", "a", "b")) { "v1 descriptor requires six named buttons" }
    require(axes == listOf("stickX", "stickY", "aimX", "aimY")) { "v1 descriptor requires four named axes" }
    require(triggerKind == "digital") { "v1 trigger is digital" }
}
```

Future HID packing should map this descriptor to Generic Desktop joystick/gamepad usages plus Button usages; Phase 5 should not lock exact report bytes because Windows and macOS adapters may choose platform-specific report IDs/layouts. [CITED: https://www.usb.org/hid; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]

### Pattern 3: Structured Capabilities With Reasons

**What:** Use nested capability data and explicit unsupported reasons. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**When to use:** Stub backend capability output and future platform adapter diagnostics. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Example:**

```kotlin
// Source: Phase 5 D-13..D-16
data class UnsupportedReason(
    val platform: String,
    val feature: String,
    val detail: String,
)

data class HapticEffectCapability(
    val strength: Boolean,
    val duration: Boolean,
    val pattern: Boolean,
    val phoneHaptic: Boolean,
    val outputReport: Boolean,
    val unsupported: List<UnsupportedReason> = emptyList(),
)

data class BackendCapabilities(
    val platform: String,
    val buttons: Set<String>,
    val axes: Set<String>,
    val haptics: HapticEffectCapability,
    val lifecycle: Set<String>,
    val limitations: List<UnsupportedReason>,
)
```

The macOS Phase 5 stub should set `phoneHaptic=true`, `outputReport=false` or `simulatedOnly=true` depending on final naming, and include an unsupported reason that real OS output-report capture is Phase 7. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] The Windows Phase 5 stub should set the same descriptor support and mark real VHF output reports as simulated-only until Phase 6. [VERIFIED: `.planning/ROADMAP.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]

### Pattern 4: UDP Replay Through Receiver

**What:** Feed bytes into `UdpInputReceiver.handleDatagram` or loopback them through `DesktopUdpInputRuntime`, then adapt accepted `UdpReceivedInput`. [VERIFIED: `UdpInputReceiver.kt`; `DesktopUdpInputRuntime.kt`]
**When to use:** All fake-input backend smoke; direct semantic injection can be a unit test but not acceptance smoke. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Example:**

```kotlin
// Source: existing UdpInputReceiverTest.kt and UdpInputFrameCodecTest.kt
val received = mutableListOf<UdpReceivedInput>()
val receiver = UdpInputReceiver(onInput = received::add)
    .start(trustedSession = "control-sid-1", config = fixtureConfig())

val result = receiver.handleDatagram(
    UdpInputFrameCodec.encode(frame(sequence = 42L), fixtureConfig()),
    receivedElapsedNanos = 1_111_111_300L,
)

check(result is UdpInputReceiverResult.Accepted)
val state = UdpControllerStateAdapter.toState(received.single())
backend.publish(state)
```

### Anti-Patterns to Avoid

- **Direct semantic-only smoke:** It bypasses Phase 4 HMAC/replay/stale behavior and fails D-06. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
- **Profile mapper in Phase 5:** Profiles, configurable aim mapping, inversion, smoothing, and remapping are Phase 8. [VERIFIED: `.planning/ROADMAP.md`]
- **Raw yaw/pitch/roll as backend axes:** D-10 limits v1 axes to stick X/Y plus motion aim X/Y. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
- **Analog trigger axis:** Current iPega evidence is binary and D-12 forbids inventing analog trigger. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
- **Relying on Gradle Test XML without checking files:** Current main-style tests do not emit XML files. [VERIFIED: local `find desktop-companion/build -name '*.xml'` after `gradle test`]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| UDP frame parsing/authentication | Custom parser in smoke harness | `UdpInputFrameCodec` + `UdpInputReceiver` | Existing code validates fixed size, magic, version, stream id, HMAC, reserved fields, age, and replay. [VERIFIED: `UdpInputFrameCodec.kt`; `InputReplayGuard.kt`] |
| Haptic control envelope | New socket/message path | `ControlServer.sendHapticCommand` + `HapticCommand` | Existing path uses authenticated reliable control channel and pending/result tracking. [VERIFIED: `ControlServer.kt`; `HapticCommand.kt`] |
| Platform smoke assertions | Freeform logs only | Shared smoke runner + JUnit-style XML | D-08 requires JUnit-style evidence and both OS runs must be distinguishable. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |
| HID report packing in Phase 5 | Platform-specific bytes | Semantic descriptor + capability invariants | Phase 6/7 own real VHF/CoreHID/HIDDriverKit packing. [VERIFIED: `.planning/ROADMAP.md`] |
| XML escaping | String concat without escaping | One local `xmlEscape` helper | Smoke XML must remain parseable when failure messages include symbols. [ASSUMED] |

**Key insight:** Phase 5's value is contract pressure before OS driver work; custom fake paths that skip existing receiver/haptic code create false confidence. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`; local Phase 4 code]

## Common Pitfalls

### Pitfall 1: Gradle Success Without JUnit XML

**What goes wrong:** `gradle test` passes but no `*.xml` artifact exists for Phase 5 smoke evidence. [VERIFIED: local `gradle test`; local `find desktop-companion/build -name '*.xml'`]
**Why it happens:** Existing tests are `main()` classes launched in `Test.doLast`, not Gradle-discovered JUnit/TestNG/Jupiter tests. [VERIFIED: `desktop-companion/build.gradle.kts`; `.planning/phases/04-input-stream-and-haptic-transport/04-PATTERNS.md`]
**How to avoid:** Add explicit JUnit-style XML output for smoke commands, e.g. `desktop-companion/build/test-results/btgun-smoke/macos/TEST-btgun-macos-stub.xml` and `.../windows/TEST-btgun-windows-stub.xml`. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Warning signs:** `build/test-results/test/binary/*` exists but no XML. [VERIFIED: local artifact inspection]

### Pitfall 2: Fake Input Bypasses Receiver Stale/Replay Rules

**What goes wrong:** Backend smoke passes with direct `SemanticControllerState` while Phase 4 receiver handoff is broken. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Why it happens:** Direct publish ignores HMAC, stream id, sequence, and timeout behavior. [VERIFIED: `InputReplayGuard.kt`; `UdpInputFrameCodec.kt`]
**How to avoid:** Smoke must include at least one accepted snapshot frame, one edge frame, and one timeout/stale transition through `UdpInputReceiver` or runtime loopback. [VERIFIED: `docs/protocol/input-stream-v1-fixtures.md`; `UdpInputReceiverTest.kt`; `DesktopUdpInputRuntimeTest.kt`]
**Warning signs:** Smoke test imports backend only and never imports `com.btgun.desktop.transport.*`. [VERIFIED: local package names]

### Pitfall 3: Capability Flags Hide Platform Limits

**What goes wrong:** Later Phase 6/7 planners cannot tell whether haptic/output support is absent, simulated, phone-only, or OS-output capable. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Why it happens:** Boolean-only capability flags collapse distinct haptic paths. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**How to avoid:** Use a haptic matrix with `strength`, `duration`, `pattern`, `phoneHaptic`, `outputReport`, plus `UnsupportedReason(platform, feature, detail)`. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
**Warning signs:** Capability model has one field like `supportsHaptics: Boolean`. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]

### Pitfall 4: Treating macOS CoreHID Like a Settled Phase 7 Path

**What goes wrong:** Contract assumes CoreHID availability/output behavior that may not hold on target machines or entitlements. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice; CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.hid.virtual.device]
**Why it happens:** `HIDVirtualDevice` exists on macOS 15+, but creating virtual HID devices and output request handling still depend on descriptor, activation, delegate behavior, and entitlements. [CITED: https://developer.apple.com/documentation/corehid/creatingvirtualdevices; CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.hid.virtual.device]
**How to avoid:** Phase 5 macOS stub capability should mark real OS virtual device/output report support as simulated or unsupported until Phase 7 validates it. [VERIFIED: `.planning/ROADMAP.md`]
**Warning signs:** Phase 5 tests import CoreHID or require Xcode. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]

### Pitfall 5: Treating Windows VHF as User-Mode

**What goes wrong:** Phase 5 contract assumes a desktop JVM process can directly create the Windows virtual HID device. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]
**Why it happens:** Microsoft VHF source drivers are kernel-mode in the documented VHF model, and VHF requires WDK headers/libraries plus driver install/filter details. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]
**How to avoid:** Keep Phase 5 Windows command as a stub that validates descriptor/capability/output-routing assumptions and leaves real VHF to Phase 6. [VERIFIED: `.planning/ROADMAP.md`]
**Warning signs:** Phase 5 task wants `VhfCreate` from Kotlin/JVM. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]

## Code Examples

### Backend Interface

```kotlin
// Source: Phase 5 D-04; local style mirrors plain Kotlin data/interface classes.
interface VirtualControllerBackend {
    val descriptor: VirtualControllerDescriptor
    val capabilities: BackendCapabilities
    val lifecycleState: BackendLifecycleState
    val currentState: SemanticControllerState
    val lastPublishResult: BackendPublishResult?

    fun start(): BackendLifecycleResult
    fun publish(state: SemanticControllerState): BackendPublishResult
    fun simulateOutputReport(report: SimulatedOutputReport): HapticCommand?
    fun stop(reason: String = "stopped")
}
```

### UDP Adapter

```kotlin
// Source: UdpReceivedInput.kt pressedControls shape and Phase 5 D-02/D-03.
object UdpControllerStateAdapter {
    fun toState(input: UdpReceivedInput): SemanticControllerState =
        SemanticControllerState(
            trigger = "trigger" in input.pressedControls,
            reload = "reload" in input.pressedControls,
            x = "x" in input.pressedControls,
            y = "y" in input.pressedControls,
            a = "a" in input.pressedControls,
            b = "b" in input.pressedControls,
            stickX = input.stickX,
            stickY = input.stickY,
            aimX = input.motion.rawAimX.takeUnless { it.isNaN() } ?: 0.0f,
            aimY = input.motion.rawAimY.takeUnless { it.isNaN() } ?: 0.0f,
            stale = input.stale,
            sourceSequence = input.lastAcceptedSequence,
        )
}
```

This adapter intentionally uses `rawAimX/rawAimY` as Phase 5 fixed smoke aim fields and does not use yaw/pitch/roll as HID axes. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`; `docs/protocol/input-stream-v1-fixtures.md`]

### JUnit-Style Smoke XML

```kotlin
// Source: Phase 5 D-08; local no-new-dependency test style.
data class SmokeCaseResult(
    val name: String,
    val passed: Boolean,
    val message: String = "",
    val elapsedSeconds: Double = 0.0,
)

object JunitSmokeXml {
    fun render(suiteName: String, cases: List<SmokeCaseResult>): String {
        val failures = cases.count { !it.passed }
        val body = cases.joinToString("\n") { case ->
            if (case.passed) {
                """  <testcase classname="${xml(suiteName)}" name="${xml(case.name)}" time="${case.elapsedSeconds}"/>"""
            } else {
                """  <testcase classname="${xml(suiteName)}" name="${xml(case.name)}" time="${case.elapsedSeconds}"><failure message="${xml(case.message)}"/></testcase>"""
            }
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="${xml(suiteName)}" tests="${cases.size}" failures="$failures" errors="0">
$body
</testsuite>
"""
    }

    private fun xml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
```

Gradle's built-in Test task can create JUnit XML for discovered tests, but the current project test pattern is hand-run `main()` classes, so a local writer is the lower-risk Phase 5 path. [VERIFIED: `desktop-companion/build.gradle.kts`; CITED: https://docs.gradle.org/current/userguide/java_testing.html]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Windows HID transport minidriver for virtual/software transport | Windows VHF HID source driver using KMDF/WDM, VHF static library, and in-box VHF driver | Microsoft docs say VHF starts in Windows 10 era | Phase 6 should target VHF, but Phase 5 must not require kernel driver build. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] |
| macOS virtual HID only through DriverKit/system-extension style fallback | CoreHID `HIDVirtualDevice` exists for virtual HID devices on macOS 15+ | Apple docs list `HIDVirtualDevice` availability as macOS 15.0+ | Phase 7 can evaluate CoreHID first, but Phase 5 should mark it capability risk. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice] |
| Real haptic output path assumed same on all OSes | Phone haptic v1 plus simulated output-report route in Phase 5 | Locked by Phase 1/4/5 decisions | Capability matrix must separate phone haptic from OS output report support. [VERIFIED: `.planning/STATE.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`] |

**Deprecated/outdated:**
- ViGEmBus/vJoy as product core: existing stack research says avoid as mandatory product core and use only prototypes/spikes. [VERIFIED: `.planning/research/STACK.md`]
- Custom HID gun report for v1: explicitly out of scope; use regular gamepad/joystick shape. [VERIFIED: `.planning/REQUIREMENTS.md`; `.planning/PROJECT.md`]

## Handoff Risks for Phase 6 / Phase 7

| Risk | Applies To | Evidence | Phase 5 Mitigation |
|------|------------|----------|--------------------|
| VHF is kernel-mode source-driver work, not Kotlin/JVM-only user-mode work. | Phase 6 Windows | Microsoft VHF docs state this release supports HID source driver only in kernel mode and uses WDK `Vhf.h`/`VhfKm.lib`. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] | Keep contract OS-neutral and make Windows stub expose `platform="windows-stub"` plus real VHF limitation. [VERIFIED: Phase 5 D-05/D-14] |
| Windows output/write reports require async callback support if driver wants WriteReport handling. | Phase 6 Windows haptics | VHF docs describe `IOCTL_HID_WRITE_REPORT` through `EvtVhfAsyncOperationWriteReport`. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] | Model output-report support separately from phone haptic support. [VERIFIED: Phase 5 D-15/D-18] |
| `VhfReadReportSubmit` buffering policy can affect input latency/ordering. | Phase 6 Windows | Microsoft docs describe default buffering vs `EvtVhfReadyForNextReadReport` policy. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfreadreportsubmit] | Backend publish result should capture accepted/rejected/last error, not assume every publish becomes OS input immediately. [VERIFIED: Phase 5 D-04] |
| CoreHID `HIDVirtualDevice` is macOS 15+ and entitlement-sensitive. | Phase 7 macOS | Apple docs list macOS 15.0+ availability and virtual-device entitlement key. [CITED: https://developer.apple.com/documentation/corehid/hidvirtualdevice; CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.hid.virtual.device] | macOS stub must report real OS device creation as unsupported/simulated until Phase 7. [VERIFIED: Phase 5 D-05/D-14] |
| HIDDriverKit virtual HID path requires DriverKit/System Extensions app packaging and requested entitlement. | Phase 7 macOS | Apple HIDDriverKit docs say package DriverKit/HIDDriverKit driver in an app using System Extensions; Apple entitlement docs say DriverKit virtual HID entitlement requires request form. [CITED: https://developer.apple.com/documentation/hiddriverkit; CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.driverkit.family.hid.virtual.device] | Capability limitations should include entitlement/setup blockers as structured reasons. [VERIFIED: Phase 5 D-14] |
| macOS output report behavior differs between CoreHID delegate requests and DriverKit provider model. | Phase 7 macOS | CoreHID docs route set/get report requests through `HIDVirtualDeviceDelegate`; HIDDriverKit uses driver/provider types. [CITED: https://developer.apple.com/documentation/corehid/creatingvirtualdevices; CITED: https://developer.apple.com/documentation/hiddriverkit] | Phase 5 simulated output report should be a semantic event, not CoreHID/DriverKit-specific bytes. [VERIFIED: Phase 5 D-18] |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A tiny local XML writer is preferable to adding JUnit for Phase 5 smoke artifacts. [ASSUMED] | Standard Stack / Code Examples | Planner might choose dependency migration instead; would need package legitimacy gate and broader build changes. |
| A2 | XML escaping helper is sufficient for smoke output values. [ASSUMED] | Don't Hand-Roll | Bad escaping could produce invalid evidence XML; planner should include parse verification. |

## Open Questions (RESOLVED)

1. **RESOLVED: Where will Windows smoke run?** [VERIFIED: Phase 5 D-07 requires both OSes]
   - What we know: Current machine is macOS arm64, and Phase 5 is not accepted unless commands run on both macOS and Windows. [VERIFIED: local `uname -a`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
   - Resolution: Phase 5 plans include a human-run evidence checkpoint for `smokeDesktopBackendWindowsStub` on Windows 11 x64 because no Windows executor exists in this session. [VERIFIED: Phase 5 D-07]
   - Planner decision: Windows acceptance is recorded in `05-05-PLAN.md` and `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl`. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-PLAN.md`]

2. **RESOLVED: Should smoke XML live under Gradle `test-results` or a docs evidence directory?** [VERIFIED: Phase 5 D-08]
   - What we know: Existing Gradle `test` output has no XML; Phase 4 evidence manifests live under `docs/evidence/manifests/`. [VERIFIED: local build output; `docs/evidence/manifests/phase4-input-haptic-transport.jsonl`]
   - Resolution: Emit machine XML under `desktop-companion/build/test-results/btgun-smoke/<platform>/` and commit sanitized evidence rows under `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl`. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-04-PLAN.md`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-PLAN.md`]
   - Planner decision: `05-04-PLAN.md` owns smoke XML paths; `05-05-PLAN.md` owns manifest rows and human evidence gate. [VERIFIED: local plan files]

3. **RESOLVED: Does Phase 5 haptic smoke require live Android during every platform stub run?** [VERIFIED: D-19/D-20]
   - What we know: Android absence fails haptic smoke, and phone vibration needs human confirmation. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md`]
   - Resolution: Default contract smoke can pass without live Android; haptic mode with `-Pbtgun.smoke.haptic=true` is human-gated and fails if Android is absent. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-PLAN.md`]
   - Planner decision: Phase acceptance requires both platform XML artifacts plus paired Android phone haptic confirmation. [VERIFIED: D-07/D-19/D-20; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-PLAN.md`]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| macOS arm64 host | macOS stub smoke local run | yes | macOS `26.2`, Darwin arm64 | None for local macOS smoke. [VERIFIED: `sw_vers`; `uname -a`] |
| Java 17 | Desktop compile/test | yes | OpenJDK `17.0.19` at `/opt/homebrew/opt/openjdk@17/bin/java` | Use configured Gradle Java toolchain if available. [VERIFIED: local `java -version`; `desktop-companion/build.gradle.kts`] |
| Gradle | Desktop build/test/smoke tasks | partial | `9.5.1` works with `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle`; default cache fails native-platform load | Always set `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle` in validation commands. [VERIFIED: local `gradle --version`; local `gradle test`] |
| Gradle socket/file-lock support | Running Gradle inside sandbox | partial | Sandbox blocked `FileLockContentionHandler` socket; escalated run passed | Planner/executor may need approval/escalation for Gradle tests in this environment. [VERIFIED: local command failure/success] |
| Xcode full app | Future macOS real HID work | no | `xcodebuild` reports Command Line Tools only | Phase 5 stub must not require Xcode; Phase 7 must address install. [VERIFIED: local `xcodebuild -version`; `.planning/ROADMAP.md`] |
| Windows 11 x64 host | Windows stub smoke acceptance | no local evidence | Current host is macOS arm64 | Human/remote Windows checkpoint required. [VERIFIED: local `uname -a`; Phase 5 D-07] |
| Windows WDK/Visual Studio | Phase 6 real VHF | no local evidence | `msbuild` and `signtool` found are non-WDK/local tools and do not prove WDK | Not needed in Phase 5; Phase 6 must verify WDK. [VERIFIED: local `command -v`; `.planning/ROADMAP.md`] |
| Android paired session | Live haptic smoke | required but not probed in research | Phase 4 physical smoke passed on 2026-06-09 | Human checkpoint with paired Android session. [VERIFIED: `docs/evidence/manifests/phase4-input-haptic-transport.jsonl`; Phase 5 D-19/D-20] |

**Missing dependencies with no fallback:**
- Windows 11 x64 run environment for Phase 5 acceptance. [VERIFIED: local host; Phase 5 D-07]
- Paired Android session for accepted haptic smoke pass. [VERIFIED: Phase 5 D-19/D-20]

**Missing dependencies with fallback:**
- Full Xcode is missing locally, but Phase 5 uses macOS stub only and should not need Xcode. [VERIFIED: local `xcodebuild -version`; Phase 5 D-05]
- Default Gradle cache/runtime fails locally; `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle` plus escalation worked. [VERIFIED: local command output]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Plain Kotlin `main()` test classes registered in Gradle `Test.doLast`; no JUnit dependency. [VERIFIED: `desktop-companion/build.gradle.kts`; `.planning/phases/04-input-stream-and-haptic-transport/04-PATTERNS.md`] |
| Config file | `desktop-companion/build.gradle.kts`. [VERIFIED: local file] |
| Quick run command | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test`. [VERIFIED: local command passed with escalation on 2026-06-09] |
| Full suite command | Same as quick run until Phase 5 adds platform smoke tasks; then add `smokeDesktopBackendMacosStub` and Windows human-run result. [VERIFIED: local build shape; Phase 5 D-07] |
| JUnit XML artifact | Must be newly emitted by smoke harness; current Gradle run emits no XML files. [VERIFIED: local artifact inspection] |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DESK-04 | Descriptor has six buttons, four axes, digital trigger, gamepad-like one-device shape. | unit/contract | `gradle test` running `BackendContractTestKt` | no, Wave 0. [VERIFIED: local files] |
| DESK-04 | UDP snapshot/edge fixture maps to semantic trigger/reload/X/Y/A/B/stick/aim state. | unit/integration | `gradle test` running `UdpControllerStateAdapterTestKt` | no, Wave 0. [VERIFIED: local files] |
| DESK-07 | Capabilities match descriptor and include haptic/output/platform unsupported reasons. | unit/contract | `gradle test` running `BackendCapabilitiesTestKt` | no, Wave 0. [VERIFIED: local files] |
| DESK-08 | macOS stub command replays UDP through receiver and emits XML. | smoke | `gradle smokeDesktopBackendMacosStub` | no, Wave 0. [VERIFIED: local files] |
| DESK-08 | Windows stub command replays same shared contract and emits XML. | smoke/human-run on Windows | `gradle smokeDesktopBackendWindowsStub` on Windows 11 x64 | no, Wave 0. [VERIFIED: local files; Phase 5 D-07] |
| DESK-08 / D-17..D-20 | Simulated output report routes to phone haptic command and human confirms phone pulse. | smoke/manual | `gradle smokeDesktopBackend{Platform}Stub -Pbtgun.smoke.haptic=true` plus manifest row | no, Wave 0. [VERIFIED: Phase 5 D-17..D-20] |

### Sampling Rate

- **Per task commit:** `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test`. [VERIFIED: local command passed]
- **Per wave merge:** `gradle test` plus any new stub command for the touched platform. [VERIFIED: Phase 5 D-07/D-08]
- **Phase gate:** `gradle test`, macOS stub XML artifact, Windows stub XML artifact, and haptic human-confirmed manifest row. [VERIFIED: Phase 5 D-07/D-20]

### Wave 0 Gaps

- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` — DESK-04/D-03. [VERIFIED: file absent]
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` — DESK-04 invariants. [VERIFIED: file absent]
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` — DESK-07. [VERIFIED: file absent]
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` — DESK-08 receiver handoff. [VERIFIED: file absent]
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/JunitSmokeXml.kt` — D-08 artifact. [VERIFIED: file absent]
- [ ] Gradle tasks `smokeDesktopBackendMacosStub` and `smokeDesktopBackendWindowsStub`. [VERIFIED: `desktop-companion/build.gradle.kts`]
- [ ] `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl` — cross-platform evidence manifest. [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes, for live haptic smoke only | Reuse existing proof-gated `ControlServer`; do not create unauthenticated smoke haptic socket. [VERIFIED: `ControlServer.kt`; `docs/protocol/lan-pairing-v1.md`] |
| V3 Session Management | yes, for control/UDP session boundaries | Reuse trusted session id and stream session config; reject stale/wrong sessions through existing receiver/control code. [VERIFIED: `InputReplayGuard.kt`; `ControlServer.kt`] |
| V4 Access Control | yes | Backend smoke should not bypass active-session gate for haptic command pass; contract-only output simulation can return a command without sending. [VERIFIED: Phase 5 D-17..D-20] |
| V5 Input Validation | yes | Reuse `UdpInputFrameCodec`, `InputStreamConfig` validation, and descriptor invariant checks. [VERIFIED: `UdpInputFrameCodec.kt`; `InputStreamConfig.kt`] |
| V6 Cryptography | yes, existing transport only | Do not create new crypto; Phase 4 HMAC/authenticated control remains source of truth. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; `UdpInputFrameCodec.kt`] |

### Known Threat Patterns for Phase 5 Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Forged fake smoke bypasses UDP auth | Spoofing/Tampering | Smoke must replay authenticated fixtures through existing receiver or explicitly label direct state tests as unit-only. [VERIFIED: Phase 5 D-06; `UdpInputReceiver.kt`] |
| Old session haptic command reaches Android | Replay/Tampering | Use `ControlServer.sendHapticCommand` active session tracking and pending result correlation. [VERIFIED: `ControlServer.kt`] |
| Secret leakage in smoke XML/manifest | Information Disclosure | Do not write QR secrets, manual codes, proof values, stream auth keys, or raw secret-bearing control payloads. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; `UdpInputFrameCodecTest.kt`] |
| Platform stub overstating real OS support | Misrepresentation/Repudiation | Include `platform` and explicit unsupported detail fields in capability output. [VERIFIED: Phase 5 D-14] |

## Sources

### Primary (HIGH confidence)

- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md` — locked Phase 5 decisions D-01..D-20, discretion, deferred scope. [VERIFIED: local file]
- `.planning/REQUIREMENTS.md` — DESK-04, DESK-07, DESK-08 and traceability. [VERIFIED: local file]
- `.planning/ROADMAP.md` — Phase 5/6/7/8/9/10 boundaries. [VERIFIED: local file]
- `.planning/PROJECT.md` — platform support, gamepad shape, desktop-owned profiles, v1 phone haptics. [VERIFIED: local file]
- `docs/protocol/lan-pairing-v1.md` — control envelopes, UDP frames, haptic command/result. [VERIFIED: local file]
- `docs/protocol/input-stream-v1-fixtures.md` — golden snapshot/edge fixture contract. [VERIFIED: local file]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` — session, UDP, haptic seams. [VERIFIED: local code]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/*` — UDP runtime/receiver/replay/codec shapes. [VERIFIED: local code]
- `desktop-companion/src/test/kotlin/com/btgun/desktop/*` and `desktop-companion/build.gradle.kts` — main-style test convention and Gradle registration. [VERIFIED: local code]
- Microsoft VHF docs — Windows VHF source-driver architecture, callbacks, write/read reports. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]
- Apple CoreHID/HIDVirtualDevice docs — macOS virtual device creation, descriptors, input reports, delegate requests. [CITED: https://developer.apple.com/documentation/corehid/creatingvirtualdevices]
- Apple HIDDriverKit and entitlement docs — DriverKit/system-extension HID path and virtual HID entitlement request. [CITED: https://developer.apple.com/documentation/hiddriverkit; CITED: https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.driverkit.family.hid.virtual.device]
- Gradle Java/JVM testing docs — Test task JUnit XML behavior for discovered tests. [CITED: https://docs.gradle.org/current/userguide/java_testing.html]

### Secondary (MEDIUM confidence)

- `.planning/research/STACK.md` and `.planning/research/ARCHITECTURE.md` — earlier stack/architecture direction; validated against current Phase 5 code where relevant. [VERIFIED: local docs]
- USB-IF HID page — HID standard and usage-table authority for joystick/gamepad descriptor direction. [CITED: https://www.usb.org/hid]

### Tertiary (LOW confidence)

- Assumptions about exact smoke XML path and local XML writer preference. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — existing Kotlin/JVM desktop build and no-new-package path are verified locally. [VERIFIED: `desktop-companion/build.gradle.kts`; local Gradle run]
- Architecture: HIGH — Phase 5 decisions and Phase 4 receiver/control seams are explicit and verified in code. [VERIFIED: `05-CONTEXT.md`; `ControlServer.kt`; `UdpInputReceiver.kt`]
- Pitfalls: HIGH for Gradle/JUnit artifact gap and receiver bypass; MEDIUM for future platform driver gaps because Phase 6/7 will verify real OS behavior. [VERIFIED: local Gradle artifacts; CITED: Microsoft/Apple docs]

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 for Kotlin/JVM/local patterns; 2026-06-16 for CoreHID/VHF risk notes because platform docs/tooling are fast-moving. [ASSUMED]
