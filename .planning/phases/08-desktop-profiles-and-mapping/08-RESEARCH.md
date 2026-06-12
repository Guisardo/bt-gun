# Phase 08: desktop-profiles-and-mapping - Research

**Researched:** 2026-06-12
**Domain:** Android-owned profile mapping, HID/LAN mapped stream, desktop read-only diagnostics
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

Source for all copied constraints in this block: [VERIFIED: `.planning/phases/08-desktop-profiles-and-mapping/08-CONTEXT.md`]

### Locked Decisions
## Implementation Decisions

### Profile Ownership and Stream Shape
- **D-01:** Android owns all profiles. Android stores and applies profiles for gun controls and motion aim; this replaces the prior desktop-owned profile direction.
- **D-02:** Android sends the minimal mapped product stream by default.
- **D-03:** Android exposes an in-app debug option/button that enables sending raw motion/provider data in addition to the mapped stream.
- **D-04:** The raw-data debug toggle is Android-session controlled. Desktop cannot request raw extras by itself in Phase 8.
- **D-05:** Remove desktop profile editing from Phase 8. Desktop only displays active Android profile id/name/revision and mapped-stream status.

### Aim Feel Model
- **D-06:** The built-in default uses a responsive balanced feel: low smoothing, small dead zone, sensitivity `1.0`, and inversion off.
- **D-07:** Normal profile editing includes shared aim settings, and the advanced editor surfaces provider-specific overrides for calibrated/fused rotation, gyro/raw aim, and accelerometer/gravity tilt fallback.
- **D-08:** Smoothing uses a latency-capped adaptive filter.
- **D-09:** Adaptive smoothing must report or bound added filter lag. If the filter threatens the latency target or feels laggy, the implementation must auto-fallback to a Low smoothing behavior.

### Button Remap Rules
- **D-10:** Phase 8 supports limited remapping: physical trigger, reload, X, Y, A, and B can map among the v1 virtual button outputs.
- **D-11:** Stick axes and aim axes stay semantic. Do not allow arbitrary physical-button-to-axis or axis-to-button remaps in Phase 8.
- **D-12:** The hold-to-recenter gesture can move from physical reload to another physical button.
- **D-13:** Each saved profile must keep exactly one hold-to-recenter physical button.
- **D-14:** Android validates profiles and blocks save for missing required controls, invalid duplicate mappings, or missing recenter control. Do not silently auto-repair invalid mappings.

### Profile Storage and Android UI
- **D-15:** Android ships an immutable built-in Default Visualizer profile.
- **D-16:** Users can create local Android profiles by duplicating a profile, then rename, edit, select, and delete user-created profiles.
- **D-17:** Profile persistence is local Android storage in Phase 8. Reset restores default-derived settings.
- **D-18:** Import/export is deferred.
- **D-19:** The Android profile editor is advanced: it should expose full per-provider overrides in the main profile editing flow, not hide them behind a future phase.

### the agent's Discretion
- Choose exact Android storage API, schema versioning shape, profile ids, revision semantics, validation error labels, UI layout, adaptive smoothing algorithm, filter constants, default dead-zone value, and raw debug payload fields, as long as the decisions above and v1 HID shape remain true.
- Choose exact desktop read-only metadata display and mapped-stream diagnostics, provided desktop profile editing is not reintroduced.

### Deferred Ideas (OUT OF SCOPE)
- Profile import/export is deferred to a later game-profile or v2 phase.
- Game-specific preset browser remains out of Phase 8.
- Phase 9 owns visualizer UI, latency dashboard, packet-loss dashboard, recenter display, and haptic test controls.
- Phase 10 owns broader replay diagnostics and v1 documentation.
- Direct desktop-to-gun Bluetooth remains v2/deferred.
- Physical gun motor rumble remains v2/deferred.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROF-01 | Desktop companion stores aim mapping profiles locally on the desktop. | Must be corrected before code: Android stores local profiles; desktop stores none. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `.planning/REQUIREMENTS.md`] |
| PROF-02 | User can configure motion aim mapping to joystick axes per desktop profile, including provider-specific tuning. | Implement as Android profile editor with shared settings plus provider overrides for calibrated/fused, raw gyro, and tilt fallback. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `NormalizedEvents.kt`] |
| PROF-03 | User can configure sensitivity, inversion, dead zone, and smoothing per aim profile. | Implement in Android profile model and profile mapper before HID/LAN output. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `BtGunHidReportPacker.kt`; VERIFIED: `HostSessionService.kt`] |
| PROF-04 | User can map trigger, reload, joystick, and X/Y/A/B to virtual joystick controls per profile. | Scope is limited Android-side button remap among trigger/reload/X/Y/A/B; stick and aim axes remain semantic. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `VirtualControllerDescriptor.kt`] |
| PROF-05 | Desktop companion applies profile changes without requiring Android app rebuilds. | Corrected behavior: Android applies saved profile changes at runtime without rebuild; desktop only consumes mapped stream and metadata. [VERIFIED: `08-CONTEXT.md`] |
| PROF-06 | Desktop companion stores a default visualizer profile that works immediately after pairing. | Corrected behavior: Android ships immutable Default Visualizer profile; desktop displays active profile metadata/revision. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `ProfileMetadata.kt`] |
</phase_requirements>

## Summary

Phase 8 should be planned as an Android profile and mapper phase, not a desktop profile phase. This is a deliberate reroute after Phase 7 proved Android Bluetooth HID as the primary macOS input path, so the stale desktop-owned wording in `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, `.planning/PROJECT.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/STACK.md`, `.planning/research/PITFALLS.md`, and `docs/protocol/lan-pairing-v1.md` must be corrected in Wave 0 before implementation. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `.planning/ROADMAP.md`; VERIFIED: `.planning/PROJECT.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`]

The implementation should add Android profile model, storage, validation, mapper, and UI. The mapper should consume `GunInputState` plus `MotionSample`, produce mapped semantic state, then feed both Android Bluetooth HID report packing and LAN mapped-stream output. Existing Android code already has `MotionSample` provider/capability fields, aim calibration and fallback primitives, HID report packing, LAN UDP sending, and dashboard state seams. [VERIFIED: `NormalizedEvents.kt`; VERIFIED: `AimCalibration.kt`; VERIFIED: `PreviewAimMapper.kt`; VERIFIED: `BtGunHidReportPacker.kt`; VERIFIED: `HostSessionService.kt`; VERIFIED: `DashboardState.kt`]

**Primary recommendation:** Build `ProfileStore -> ProfileValidator -> ProfileMapper -> MappedControllerState`, wire it into `HostSessionService` before HID and UDP output, and keep the desktop companion read-only for profile metadata and mapped-stream diagnostics. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `HostSessionService.kt`; VERIFIED: `ControlServer.kt`; VERIFIED: `PairingWindow.kt`]

## Project Constraints (from AGENTS.md)

| Directive | Planning Impact |
|-----------|-----------------|
| Use GSD before file edits unless bypassed. [VERIFIED: `AGENTS.md`] | Phase plan should start with GSD Wave 0 docs correction and test gaps. |
| New user-facing branches use `feature/<short-kebab-slug>` and must not use `codex/` prefixes unless explicit. [VERIFIED: `AGENTS.md`] | If planner creates a branch, state exact branch name first. |
| Keep docs short, factual, agent-facing. [VERIFIED: `AGENTS.md`] | Avoid broad tutorials in plan docs. |
| Current architecture says Android gun adapter, normalized pipeline, UDP/control transport, desktop receiver, profile mapper, virtual HID backend, but Phase 8 context supersedes profile ownership. [VERIFIED: `AGENTS.md`; VERIFIED: `08-CONTEXT.md`] | Planner must reconcile stale profile mapper placement. |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Profile storage and revisioning | Android app local storage | Android UI | User decision makes Android the profile authority. [VERIFIED: `08-CONTEXT.md`] |
| Profile editing and validation | Android UI/service | Android model layer | Saves must block invalid mappings and exactly-one recenter violations before runtime use. [VERIFIED: `08-CONTEXT.md`] |
| Aim calibration input | Android sensor/motion layer | Android profile mapper | Existing calibration produces `aimX/aimY`, raw aim, provider, capability, and latency fields. [VERIFIED: `AimCalibration.kt`; VERIFIED: `NormalizedEvents.kt`] |
| Aim feel mapping | Android profile mapper | HID/LAN output adapters | Mapping must happen before Bluetooth HID report packing and LAN mapped stream output. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `BtGunHidReportPacker.kt`; VERIFIED: `HostSessionService.kt`] |
| Button remapping | Android profile mapper | Recenter state machine | Remap only physical trigger/reload/X/Y/A/B to v1 virtual buttons; recenter is a separate exactly-one physical hold control. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `ReloadHoldRecenterTest.kt`] |
| Raw debug-data toggle | Android app/session | LAN protocol docs | Raw extras are Android-session controlled and default off. Desktop cannot request them. [VERIFIED: `08-CONTEXT.md`] |
| Desktop profile display | Desktop companion UI | Control protocol | Desktop shows Android active profile id/name/revision and mapped-stream diagnostics only. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `ProfileMetadata.kt`; VERIFIED: `PairingWindow.kt`] |
| Windows fallback publication | Desktop companion/backend | Android mapped LAN stream | Windows VHF remains fallback and consumes semantic controller state. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; VERIFIED: `SemanticControllerState.kt`] |

## Standard Stack

### Core

| Library/Platform | Version | Purpose | Why Standard |
|------------------|---------|---------|--------------|
| Android native app, Kotlin | Kotlin Android plugin in repo; `compileSdk 35`, `targetSdk 35`, Java 17. [VERIFIED: `android-host/app/build.gradle.kts`] | Profile UI, storage, mapper, HID/LAN wiring | Existing host app is native Android with no Compose dependency and service-owned runtime. [VERIFIED: `MainActivity.kt`; VERIFIED: `HostSessionService.kt`] |
| Android `SharedPreferences` | Platform API | Local profile list, active profile id, revision metadata | Existing project uses SharedPreferences-backed stores for trusted desktops and aim calibration; profile payload is local, small, and no new dependency is needed. Android docs describe SharedPreferences for simple key-value app data; Android docs also say DataStore is the modern replacement, so this is a project-pattern choice, not a universal best practice. [VERIFIED: `TrustedDesktopStore.kt`; VERIFIED: `AimCalibration.kt`; CITED: https://developer.android.com/training/data-storage/shared-preferences; CITED: https://developer.android.com/topic/libraries/architecture/datastore] |
| `kotlinx.serialization-json` | `1.11.0` in Android and desktop builds. [VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `desktop-companion/build.gradle.kts`] | Profile JSON codec and control metadata parsing | Already approved in repo; avoids adding new profile schema packages. [VERIFIED: `.planning/STATE.md`] |
| Android Sensor APIs | Platform API | Provider-specific aim inputs and monotonic timestamps | `MotionSample` already carries provider, capability flags, sensor timestamp, raw aim, calibrated aim, and aim latency; Android `SensorEvent.timestamp` uses nanoseconds and the same time base as `SystemClock.elapsedRealtimeNanos()`. [VERIFIED: `NormalizedEvents.kt`; CITED: https://developer.android.com/reference/android/hardware/SensorEvent] |
| Android Bluetooth HID APIs | Platform API | Primary macOS HID output path | Existing Phase 7 adapter uses Android HID device role and `BtGunHidReportPacker`; Android docs define `BluetoothHidDevice` as the platform HID Device Service proxy. [VERIFIED: `BtGunHidReportPacker.kt`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`; CITED: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice] |
| Kotlin/JVM desktop companion + Swing | Kotlin JVM plugin `2.0.21`, Java 17 target. [VERIFIED: `desktop-companion/build.gradle.kts`] | Read-only profile metadata and diagnostics display | Existing desktop UI is Swing and already shows session, packet stream, backend, and haptic diagnostics. [VERIFIED: `PairingWindow.kt`] |

### Supporting

| Library/Platform | Version | Purpose | When to Use |
|------------------|---------|---------|-------------|
| Existing Android `AimCalibration` and `PreviewAimMapper` | In repo | Calibration and fallback math source for mapper | Reuse for raw-to-normalized aim and provider display. [VERIFIED: `AimCalibration.kt`; VERIFIED: `PreviewAimMapper.kt`] |
| Existing `BtGunHidDescriptor` / `BtGunHidReportPacker` | In repo | Stable v1 HID shape and golden report behavior | Feed mapped state into this packer; do not change descriptor shape unless tests/docs require parity update. [VERIFIED: `BtGunHidDescriptor.kt`; VERIFIED: `BtGunHidReportPacker.kt`] |
| Existing LAN control/UDP transport | In repo | Profile metadata and fallback mapped stream | Extend protocol to mark product stream as mapped and raw extras as debug-only. [VERIFIED: `ControlServer.kt`; VERIFIED: `AndroidUdpInputSenderTest.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| SharedPreferences JSON profile store | Jetpack DataStore | DataStore is the official modern recommendation, but adding it increases dependency and migration scope; profile data is small and existing stores already use SharedPreferences. [CITED: https://developer.android.com/topic/libraries/architecture/datastore; VERIFIED: `TrustedDesktopStore.kt`] |
| Android native views | Compose | Compose would add UI dependencies and new patterns; current UI is programmatic native views. [VERIFIED: `MainActivity.kt`; VERIFIED: `android-host/app/build.gradle.kts`] |
| Desktop-side profile editor | Swing editor | Forbidden by Phase 8 context; desktop must stay read-only. [VERIFIED: `08-CONTEXT.md`] |

**Installation:** No new external packages recommended for Phase 8. [VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `desktop-companion/build.gradle.kts`]

## Package Legitimacy Audit

Not required: this research recommends no new external packages. Existing dependencies stay in place. [VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `desktop-companion/build.gradle.kts`]

## Architecture Patterns

### System Architecture Diagram

```text
---------------- Android host app ----------------+
| GunInputState + MotionSample + calibration       |
|      |                                           |
|      v                                           |
| Active Profile Store -> Profile Validator        |
|      |                                           |
|      v                                           |
| Profile Mapper                                   |
|   - button remap                                 |
|   - recenter physical control gate               |
|   - aim provider selection                       |
|   - sensitivity / inversion / dead zone          |
|   - latency-capped adaptive smoothing            |
|      |                                           |
|      +--> MappedControllerState                  |
|              |                                   |
|              +--> Android Bluetooth HID packer   |
|              |       -> macOS gamepad input      |
|              |                                   |
|              +--> LAN mapped UDP product stream  |
|                      -> desktop Windows fallback |
|                                              |
| Raw debug toggle off by default              |
|      | on                                    |
|      v                                       |
| raw/provider extras in debug stream only     |
+----------------------------------------------+

Desktop companion:
  control channel profile_metadata -> read-only UI row
  mapped UDP stream -> semantic state diagnostics / Windows VHF fallback
  no profile edit/save authority
```

### Recommended Project Structure

```text
android-host/app/src/main/java/com/btgun/host/profile/
  ProfileModels.kt          # schema, defaults, ids, revision
  ProfileValidation.kt      # save gate and labels
  ProfileStore.kt           # SharedPreferences-backed local store
  ProfileMapper.kt          # GunInputState + MotionSample -> mapped state
  AdaptiveAimSmoother.kt    # latency-capped filter

android-host/app/src/test/java/com/btgun/host/profile/
  ProfileStoreTest.kt
  ProfileValidationTest.kt
  ProfileMapperTest.kt
  AdaptiveAimSmootherTest.kt

desktop-companion/src/main/kotlin/com/btgun/desktop/control/
  ProfileMetadata.kt        # keep minimal, maybe add source/revision seen time

desktop-companion/src/test/kotlin/com/btgun/desktop/
  control/ControlChannelTest.kt
  backend/UdpControllerStateAdapterTest.kt
  ui/PairingWindowTest.kt
```

### Pattern 1: Versioned Android Profile Schema

**What:** Store a versioned profile document with immutable built-in default plus user profiles. [VERIFIED: `08-CONTEXT.md`]

**When to use:** All local profile persistence and editing in Phase 8. [VERIFIED: `08-CONTEXT.md`]

**Recommended fields:** `schemaVersion=1`, `activeProfileId`, `profiles[]`, `profileId`, `displayName`, `revision`, `createdAtEpochMillis`, `updatedAtEpochMillis`, `aim`, `providerOverrides`, `buttonMapping`, `recenterPhysicalControl`, `rawDebugEnabled`. [ASSUMED]

**Example:**

```kotlin
data class BtGunProfile(
    val schemaVersion: Int = 1,
    val profileId: String,
    val displayName: String,
    val revision: Long,
    val builtIn: Boolean,
    val aim: AimMappingSettings,
    val providerOverrides: Map<MotionProvider, AimMappingSettings> = emptyMap(),
    val buttonMapping: Map<PhysicalButton, VirtualButton>,
    val recenterPhysicalControl: PhysicalButton,
    val rawDebugEnabled: Boolean = false,
)
```

### Pattern 2: Validate Before Save, Never Auto-Repair

**What:** `ProfileValidator` returns explicit errors and blocks persistence for invalid profiles. [VERIFIED: `08-CONTEXT.md`]

**Rules:** require nonblank name, known physical/virtual controls, no missing v1 virtual buttons, no duplicate physical-to-virtual ambiguity where the user expects one-to-one controls, no axis/button crossing, exactly one recenter physical control, and recenter control within trigger/reload/X/Y/A/B. [VERIFIED: `08-CONTEXT.md`; ASSUMED]

**Example:**

```kotlin
sealed interface ProfileValidationError {
    data class MissingVirtualButton(val virtualButton: VirtualButton) : ProfileValidationError
    data object MissingRecenterControl : ProfileValidationError
    data class DuplicateVirtualButton(val virtualButton: VirtualButton) : ProfileValidationError
    data class UnsupportedAxisRemap(val source: String) : ProfileValidationError
}
```

### Pattern 3: Mapper Before Output Adapters

**What:** Add a pure mapper from raw normalized input/motion plus active profile to mapped semantic output. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `HostSessionService.kt`]

**When to use:** Every live gun event and motion sample before `hidSessionController.fanOutLiveInput` and before `sendUdpSnapshot/sendUdpEdge`. [VERIFIED: `HostSessionService.kt`]

**Example:**

```kotlin
data class MappedControllerState(
    val pressedControls: Set<String>,
    val stickAxisX: Float,
    val stickAxisY: Float,
    val aimX: Float,
    val aimY: Float,
    val aimSource: String,
    val smoothingMode: String,
    val estimatedFilterLagMillis: Long,
)
```

### Pattern 4: Latency-Capped Adaptive Smoothing

**What:** Use a pure one-pole adaptive low-pass filter where alpha is computed from sample delta time and a dynamic time constant. [ASSUMED]

**Recommended algorithm:** Use `alpha = dt / (tau + dt)`, choose lower `tau` during fast motion and higher `tau` during slow jitter, estimate added lag as bounded by current `tau`, and force `Low` smoothing if estimated added lag exceeds `12 ms` or if current `MotionSample.aimLatencyMillis` leaves less than `20 ms` headroom to the 50 ms visualizer target. [ASSUMED; VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `NormalizedEvents.kt`]

**Recommended constants:** `off=0 ms`, `low tau=12 ms`, `balanced tau=24 ms`, `high tau=40 ms`, adaptive cap `12 ms` added lag, dead zone default `0.03`, output clamp `[-1, 1]`. [ASSUMED]

### Anti-Patterns to Avoid

- **Desktop profile authority:** Forbidden by Phase 8 context. [VERIFIED: `08-CONTEXT.md`]
- **Raw debug stream by default:** Product stream must be minimal mapped output unless Android user enables raw debug. [VERIFIED: `08-CONTEXT.md`]
- **Silent profile repair:** Saves must fail with errors; do not rewrite invalid mappings behind the user's back. [VERIFIED: `08-CONTEXT.md`]
- **Axis/button free-for-all:** Stick axes and aim axes remain semantic; no arbitrary button-to-axis or axis-to-button remaps. [VERIFIED: `08-CONTEXT.md`]
- **Descriptor drift:** v1 HID shape remains six product buttons plus four axes. [VERIFIED: `BtGunHidDescriptor.kt`; VERIFIED: `VirtualControllerDescriptor.kt`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Local schema serialization | Ad hoc delimiter codec for complex profile documents | Existing `kotlinx.serialization-json` | Complex nested profile settings need forward-compatible decode and defaults. [VERIFIED: `android-host/app/build.gradle.kts`; ASSUMED] |
| Sensor timestamp base | Wall-clock timestamp math | Existing `MotionSample` elapsed nanos and Android sensor timestamps | Sensor timestamps are monotonic nanos in the elapsed realtime time base. [VERIFIED: `NormalizedEvents.kt`; CITED: https://developer.android.com/reference/android/hardware/SensorEvent] |
| HID report bytes | New descriptor/report format | Existing `BtGunHidDescriptor` and `BtGunHidReportPacker` | Phase 7 locked stable macOS-visible input shape. [VERIFIED: `BtGunHidDescriptor.kt`; VERIFIED: `BtGunHidReportPacker.kt`] |
| Pairing/control security | New profile control channel | Existing WSS control envelope and `profile_metadata` type | Control protocol already has authenticated session and metadata envelope. [VERIFIED: `ControlServer.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| Desktop profile UI | New Swing editor | Read-only metadata/diagnostics rows | Desktop editing is explicitly out. [VERIFIED: `08-CONTEXT.md`] |

**Key insight:** The complex part is no longer desktop UI; it is keeping one Android-owned profile mapper consistent across Bluetooth HID and LAN fallback without reintroducing raw desktop mapping. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `HostSessionService.kt`]

## Runtime State Inventory

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | Existing Android SharedPreferences stores: `bt_gun_trusted_desktops`, `bt_gun_aim_calibration`. No profile store exists yet. [VERIFIED: `TrustedDesktopStore.kt`; VERIFIED: `AimCalibration.kt`] | Add new local Android profile store; no migration from existing desktop profiles because none exist. |
| Live service config | Active desktop control protocol currently sends default desktop `profile_metadata`. [VERIFIED: `ControlServer.kt`] | Flip authority: Android should publish active profile metadata/revision; desktop default metadata should become fallback/unset only. |
| OS-registered state | Android Bluetooth HID descriptor/report shape is registered at runtime through Phase 7 path. [VERIFIED: `BtGunHidDescriptor.kt`; VERIFIED: `HostSessionService.kt`] | Do not change descriptor shape; mapper changes only report values. |
| Secrets/env vars | Pairing secrets, stream HMAC keys, TLS identity, and manual codes remain transport-owned. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `ControlServer.kt`] | Profile code must not log secrets or raw debug payloads by default. |
| Build artifacts | `desktop-companion/build/install` exists and may contain old classpath artifacts. [VERIFIED: `rg` output from research] | No profile migration; rebuild after code changes. |

**Nothing found in category:** No existing desktop profile database, import/export files, OS profile registry, or cloud profile state found in source/docs. [VERIFIED: `rg` source scan]

## Common Pitfalls

### Pitfall 1: Planning Against Stale Desktop-Owned Requirements
**What goes wrong:** Planner builds desktop store/editor and violates Phase 8 reroute. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `08-CONTEXT.md`]
**How to avoid:** Wave 0 must update stale wording before code. [VERIFIED: `08-CONTEXT.md`]
**Warning signs:** New Swing profile edit controls, desktop profile JSON files, or desktop sensitivity/dead-zone logic. [VERIFIED: `08-CONTEXT.md`]

### Pitfall 2: HID and LAN Paths Diverge
**What goes wrong:** Bluetooth HID uses mapped aim but LAN Windows fallback still uses raw aim. [VERIFIED: `BtGunHidReportPacker.kt`; VERIFIED: `UdpControllerStateAdapter.kt`]
**How to avoid:** Produce one mapped state and feed both HID and LAN product stream. [VERIFIED: `08-CONTEXT.md`]
**Warning signs:** `UdpControllerStateAdapter` still maps `input.motion.rawAimX/rawAimY` as product aim after Phase 8. [VERIFIED: `UdpControllerStateAdapter.kt`]

### Pitfall 3: Smoothing Breaks Latency Target
**What goes wrong:** Aim feels stable but laggy. [VERIFIED: `.planning/REQUIREMENTS.md`; VERIFIED: `.planning/research/PITFALLS.md`]
**How to avoid:** Bound filter lag, expose smoothing status, and auto-fallback to Low. [VERIFIED: `08-CONTEXT.md`; ASSUMED]
**Warning signs:** Filter constants not tested with timestamps, no fallback path, no lag metric in dashboard state. [ASSUMED]

### Pitfall 4: Recenter Becomes a Remapped Virtual Button
**What goes wrong:** Recenter disappears or duplicates when reload is remapped. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `HostSessionService.kt`]
**How to avoid:** Treat recenter as a physical control gesture, separate from virtual output mapping, and validate exactly one physical recenter button. [VERIFIED: `08-CONTEXT.md`]
**Warning signs:** Recenter code checks virtual `reload` instead of selected physical control. [VERIFIED: `HostSessionService.kt`; ASSUMED]

### Pitfall 5: Raw Debug Data Leaks Into Product Stream
**What goes wrong:** Desktop or logs receive raw provider/motion data by default. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`]
**How to avoid:** Raw extras are Android-controlled, session-scoped, default off, and visibly labeled debug. [VERIFIED: `08-CONTEXT.md`]
**Warning signs:** Desktop sends a request for raw extras, or raw payload fields remain mandatory in the default UDP stream. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `docs/protocol/lan-pairing-v1.md`]

## Code Examples

### Profile Store Shape

```kotlin
class ProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("bt_gun_profiles", Context.MODE_PRIVATE)

    fun loadDocument(): ProfileDocument =
        preferences.getString("profiles_v1", null)
            ?.let(ProfileDocumentJson::decodeOrNull)
            ?: ProfileDocument.defaults()

    fun saveDocument(document: ProfileDocument) {
        preferences.edit()
            .putString("profiles_v1", ProfileDocumentJson.encode(document))
            .apply()
    }
}
```

Source: existing store pattern plus existing JSON dependency. [VERIFIED: `TrustedDesktopStore.kt`; VERIFIED: `AimCalibration.kt`; VERIFIED: `android-host/app/build.gradle.kts`]

### Mapper Output Before HID

```kotlin
val mapped = profileMapper.map(
    profile = activeProfile,
    gun = currentState.gunInputState,
    motion = currentState.lastMotionSample?.payload,
    nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
)
hidSessionController.fanOutMappedInput(mapped)
udpMappedSender?.sendSnapshot(mapped)
```

Source: current output fan-out points. [VERIFIED: `HostSessionService.kt`]

### Validation Gate

```kotlin
val errors = ProfileValidator.validate(candidate)
if (errors.isNotEmpty()) {
    return SaveProfileResult.Rejected(errors)
}
profileStore.save(candidate.bumpRevision())
```

Source: locked no-auto-repair decision. [VERIFIED: `08-CONTEXT.md`]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Desktop stores and applies aim profiles. [VERIFIED: `.planning/PROJECT.md`; VERIFIED: `.planning/REQUIREMENTS.md`] | Android owns, stores, edits, validates, and applies profiles. [VERIFIED: `08-CONTEXT.md`] | Phase 8 discussion, 2026-06-12. [VERIFIED: `08-CONTEXT.md`] | Wave 0 docs correction is mandatory before code. |
| LAN UDP carries raw motion for later desktop mapping. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; VERIFIED: `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md`] | Product stream should be minimal mapped output by default; raw extras debug-only. [VERIFIED: `08-CONTEXT.md`] | Phase 8 discussion after Phase 7 Android HID proof. [VERIFIED: `08-CONTEXT.md`] | Protocol docs/tests must change. |
| `UdpControllerStateAdapter` maps raw UDP aim into semantic desktop state. [VERIFIED: `UdpControllerStateAdapter.kt`] | Desktop consumes Android-mapped state for product behavior. [VERIFIED: `08-CONTEXT.md`] | Phase 8. [VERIFIED: `08-CONTEXT.md`] | Adapter must become compatibility/fallback aware. |

**Deprecated/outdated:**
- "Desktop Profiles and Mapping" title and desktop-owned PROF text are stale for implementation. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `.planning/ROADMAP.md`; VERIFIED: `.planning/REQUIREMENTS.md`]
- Architecture/Pitfalls warnings against "profiles baked into Android" are superseded by the Phase 7 Android HID reroute for v1, but the warning still applies to game-specific or platform-specific constants leaking into Android. [VERIFIED: `.planning/research/PITFALLS.md`; VERIFIED: `08-CONTEXT.md`]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Use SharedPreferences JSON instead of DataStore for profiles. | Standard Stack | Future migration may be needed if profile documents grow, need Flow observation, or need multi-process safety. |
| A2 | Default dead zone `0.03`, low tau `12 ms`, balanced tau `24 ms`, high tau `40 ms`, lag cap `12 ms`. | Architecture Patterns | Aim feel may need adjustment in Phase 9 live visualizer testing. |
| A3 | Validation should reject duplicate virtual button mappings for the normal one-to-one v1 editor. | Architecture Patterns | User might later want duplicated controls; Phase 8 context says invalid duplicates should block save but exact duplicate rule is discretionary. |
| A4 | Raw debug payload fields can remain the existing provider/capability/yaw/pitch/roll/rawAim/source timestamp fields when enabled. | Architecture Patterns | Protocol may need a new debug envelope if default mapped UDP layout changes too much. |

## Open Questions

1. **Exact UX layout for advanced editor**
   - What we know: Android native `MainActivity` is programmatic views, and context requires advanced provider overrides in the main flow. [VERIFIED: `MainActivity.kt`; VERIFIED: `08-CONTEXT.md`]
   - What's unclear: Whether to use one long screen, collapsible rows, or separate Activity.
   - Recommendation: Plan a dense native view with profile selector, duplicate/rename/delete, shared aim settings, provider override sections, button mapping, recenter selector, and raw debug toggle. [ASSUMED]

2. **LAN frame schema migration**
   - What we know: Current UDP frame is fixed 120 bytes and raw-motion-only. [VERIFIED: `docs/protocol/lan-pairing-v1.md`]
   - What's unclear: Whether to version a new mapped frame or reinterpret existing `rawAimX/rawAimY` fields as mapped aim.
   - Recommendation: Version new mapped product semantics in docs/tests; keep raw extras behind debug mode rather than overloading field names silently. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java | Android and desktop tests | yes | OpenJDK 17.0.19 available by `java -version`; Gradle launcher also found Java 26 without env. [VERIFIED: command output] | Use explicit Java 17 when running Gradle gates. |
| Gradle | Android and desktop tests | partial | Default `gradle --version` failed loading native platform; `env GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle --version` passed with Gradle 9.5.1. [VERIFIED: command output] | Always set `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home`; use repo-known Java 17 env from prior phases if needed. |
| Android SDK | Android build/tests | yes | SDK directory exists under `/Users/lucas.rancez/Library/Android/sdk`. [VERIFIED: command output] | None needed locally. |
| Windows host/WDK | Windows fallback manual proof | not required for core Phase 8 Android mapper | Phase 6 says Windows VHF exists as fallback. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] | Keep manual Windows smoke as optional post-map parity check. |

**Missing dependencies with no fallback:** None for research/planning. [VERIFIED: command output]

**Missing dependencies with fallback:** Default Gradle home/native-platform startup is broken; use `/private/tmp/bt-gun-gradle-home`. [VERIFIED: command output; VERIFIED: `.planning/STATE.md`]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Android unit tests via Gradle `testDebugUnitTest`; desktop Kotlin/JVM tests via Gradle `test`. [VERIFIED: `android-host/app/build.gradle.kts`; VERIFIED: `desktop-companion/build.gradle.kts`] |
| Config file | `android-host/app/build.gradle.kts`, `desktop-companion/build.gradle.kts`. [VERIFIED: files] |
| Quick run command | `env GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*Profile*' --tests '*BtGunHidReportPacker*' --tests '*AndroidUdpInputSender*' --tests '*DashboardState*' --no-daemon --console=plain` |
| Full suite command | `env GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --no-daemon --console=plain` and `env GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --no-daemon --console=plain` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| PROF-01 | Android persists local profiles and active selection; desktop no profile store. | unit + grep boundary | Android quick command plus `rg "ProfileStore|desktop profile" desktop-companion/src` | no, Wave 0 |
| PROF-02 | Provider-specific aim overrides choose calibrated/fused, raw gyro, or tilt fallback behavior. | unit | Android quick command | no, Wave 0 |
| PROF-03 | Sensitivity, inversion, dead zone, smoothing, and latency fallback map deterministically. | unit | Android quick command | no, Wave 0 |
| PROF-04 | Limited button remap and exactly-one recenter validation. | unit | Android quick command | no, Wave 0 |
| PROF-05 | Runtime profile changes affect HID/LAN output without rebuild. | unit/service seam | Android quick command plus HostSessionService seam tests | partial; new tests needed |
| PROF-06 | Immutable Default Visualizer profile works on first run and sends metadata. | unit/control | Android quick command plus desktop control test | no, Wave 0 |

### Sampling Rate

- **Per task commit:** Android focused quick command for changed profile/HID/LAN/UI tests. [VERIFIED: existing Gradle test pattern]
- **Per wave merge:** Android full unit suite and desktop full unit suite. [VERIFIED: test files list]
- **Phase gate:** Full Android + desktop suites green, docs wording corrected, and boundary grep proves no desktop editor/store. [VERIFIED: `08-CONTEXT.md`]

### Wave 0 Gaps

- [ ] `android-host/app/src/test/java/com/btgun/host/profile/ProfileValidationTest.kt` - covers PROF-01, PROF-04.
- [ ] `android-host/app/src/test/java/com/btgun/host/profile/ProfileStoreTest.kt` - covers PROF-01, PROF-06.
- [ ] `android-host/app/src/test/java/com/btgun/host/profile/ProfileMapperTest.kt` - covers PROF-02, PROF-03, PROF-04.
- [ ] `android-host/app/src/test/java/com/btgun/host/profile/AdaptiveAimSmootherTest.kt` - covers PROF-03 latency fallback.
- [ ] `android-host/app/src/test/java/com/btgun/host/hid/BtGunHidReportPackerTest.kt` extension - proves mapped output parity. Existing file exists. [VERIFIED: test file list]
- [ ] `android-host/app/src/test/java/com/btgun/host/transport/AndroidUdpInputSenderTest.kt` extension - proves mapped product stream and raw debug toggle. Existing file exists. [VERIFIED: test file list]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt` extension - proves read-only active Android profile metadata. Existing file exists. [VERIFIED: test file list]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Existing QR/manual trusted desktop pairing; profiles must not alter auth. [VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| V3 Session Management | yes | Raw debug toggle is Android-session controlled and resets/visible per active session. [VERIFIED: `08-CONTEXT.md`] |
| V4 Access Control | yes | Desktop cannot request raw extras or edit profiles. [VERIFIED: `08-CONTEXT.md`] |
| V5 Input Validation | yes | Profile validator rejects malformed schema, unsupported controls, invalid duplicates, missing recenter, out-of-range numeric fields. [VERIFIED: `08-CONTEXT.md`; ASSUMED] |
| V6 Cryptography | yes | Do not modify existing TLS/HMAC transport secrets; profile metadata contains no secrets. [VERIFIED: `docs/protocol/lan-pairing-v1.md`] |
| V8 Data Protection | yes | Local profile data is non-secret but raw debug data may reveal sensor behavior; default off and redact logs. [VERIFIED: `08-CONTEXT.md`; VERIFIED: `docs/setup/android-bluetooth-hid-gamepad.md`] |

### Known Threat Patterns for Android Profile Mapping

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed profile JSON crashes app | Denial of Service | Decode to default document or rejected state; never crash foreground service. [ASSUMED; VERIFIED: `TrustedDesktopStore.kt`] |
| Raw motion debug data exposed by default | Information Disclosure | Android-controlled default-off toggle and redacted logs. [VERIFIED: `08-CONTEXT.md`] |
| Desktop coerces Android into raw mode | Elevation/Tampering | No desktop request path for raw extras in Phase 8. [VERIFIED: `08-CONTEXT.md`] |
| Invalid remap sends unintended trigger/reload | Tampering/Safety | Validate before save and keep default profile immutable fallback. [VERIFIED: `08-CONTEXT.md`] |
| Haptic/control boundary widened by profile code | Tampering | Keep haptics on authenticated control path and profiles out of haptic validation. [VERIFIED: `ControlServer.kt`; VERIFIED: `docs/protocol/lan-pairing-v1.md`] |

## Plan Slicing Recommendation

| Wave | Scope | Depends On | Why |
|------|-------|------------|-----|
| Wave 0 | Correct stale docs/requirements/roadmap/project/protocol wording; add validation test stubs. | none | Prevents planner/executor from building forbidden desktop editor. [VERIFIED: `08-CONTEXT.md`] |
| Wave 1 | Android profile schema, defaults, store, validator, revision semantics. | Wave 0 | Storage/validation must exist before mapper/UI writes. [VERIFIED: `08-CONTEXT.md`] |
| Wave 2 | Pure mapper and adaptive smoother tests. | Wave 1 | Isolates math/button rules before service integration. [VERIFIED: `NormalizedEvents.kt`; ASSUMED] |
| Wave 3 | Wire mapper into HID and LAN mapped stream, raw debug toggle default off. | Wave 2 | Output parity is the risk center. [VERIFIED: `HostSessionService.kt`; VERIFIED: `BtGunHidReportPacker.kt`] |
| Wave 4 | Android UI for duplicate/rename/edit/select/delete/reset, provider overrides, validation errors, debug raw toggle. | Waves 1-3 | UI should drive real store/mapper behavior. [VERIFIED: `MainActivity.kt`; VERIFIED: `DashboardState.kt`] |
| Wave 5 | Desktop read-only metadata and mapped-stream diagnostics; remove stale default desktop metadata authority. | Waves 1-3 | Desktop work is small and must stay read-only. [VERIFIED: `ProfileMetadata.kt`; VERIFIED: `ControlServer.kt`; VERIFIED: `PairingWindow.kt`] |
| Wave 6 | Full validation, boundary grep, protocol/docs final pass, optional Windows fallback smoke. | Waves 1-5 | Confirms no desktop profile editing and no raw-default regression. [VERIFIED: `08-CONTEXT.md`] |

## Sources

### Primary (HIGH confidence)

- `.planning/phases/08-desktop-profiles-and-mapping/08-CONTEXT.md` - locked Phase 8 reroute and decisions.
- `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/PROJECT.md`, `.planning/STATE.md` - stale wording and current phase state.
- Prior contexts for Phases 04, 05, 06, 07 - transport, backend, Windows fallback, Android HID reroute.
- Android source files: `NormalizedEvents.kt`, `AimCalibration.kt`, `PreviewAimMapper.kt`, `BtGunHidReportPacker.kt`, `BtGunHidDescriptor.kt`, `HostSessionService.kt`, `DashboardState.kt`, `MainActivity.kt`.
- Desktop source files: `ProfileMetadata.kt`, `ControlServer.kt`, `SemanticControllerState.kt`, `UdpControllerStateAdapter.kt`, `VirtualControllerDescriptor.kt`, `PairingWindow.kt`.

### Secondary (MEDIUM confidence)

- Android official SharedPreferences docs - simple key-value app data, with DataStore caution.
- Android official DataStore docs - modern storage alternative.
- Android official SensorEvent docs - monotonic nanosecond sensor timestamps.
- Android official BluetoothHidDevice docs - HID Device Service proxy.

### Tertiary (LOW confidence)

- Filter constants, dead-zone defaults, and exact UI layout recommendations are engineering assumptions that must be validated by Phase 9 feel/latency testing. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Based on existing repo build files, source patterns, and official Android docs.
- Architecture: HIGH - Phase 8 context locks Android ownership and existing service/output seams are clear.
- Pitfalls: HIGH - Conflicts are visible in current docs/source and prior phase decisions.
- Filter constants/UI layout: LOW - Discretionary defaults need live tuning.

**Research date:** 2026-06-12
**Valid until:** 2026-07-12 for source-bound architecture; revisit Android storage/API package guidance if adding new dependencies.
