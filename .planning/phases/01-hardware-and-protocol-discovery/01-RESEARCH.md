# Phase 1: Hardware and Protocol Discovery - Research

**Researched:** 2026-06-06 [VERIFIED: local]
**Domain:** Android Bluetooth reverse engineering, physical iPega validation, diagnostic evidence, protocol fixtures [VERIFIED: local]
**Confidence:** HIGH for phase boundary and evidence workflow; MEDIUM for exact protocol path until hardware capture exists [VERIFIED: local]

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Reverse-Engineering Strategy
- **D-01:** Start from reverse engineering, not hardware probing alone. Assume even the Bluetooth handshake may be custom until the deprecated apps prove otherwise.
- **D-02:** Build the protocol hypothesis from decompiling deprecated APK/XAPK references before writing hardware-facing validation code.
- **D-03:** Analyze strongest local references first: `docs/refs/ARGun2021.apk`, `docs/refs/AR Cher_20200905_Apkpure.xapk`, and `docs/refs/WorldsAR_14.0_apkcombo.com.xapk`.
- **D-04:** Broaden to other references only if the strongest refs do not reveal handshake or control behavior.
- **D-05:** `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` is 0 bytes locally. Reacquire it only if the strongest valid refs block protocol discovery.

### Evidence Bar
- **D-06:** A protocol/control finding is verified only when all three exist: a static decompile clue, a hardware capture, and a normalized fixture. v1 haptics use phone vibration; physical gun motor rumble is deferred.
- **D-07:** Static APK/XAPK findings can guide implementation, but they are not sufficient proof without real-device capture evidence.
- **D-08:** Hardware captures must be tied back to the decompiled clue that motivated the test, so downstream agents can see why each fixture exists.

### Diagnostic Artifacts
- **D-09:** Phase 1 must leave a full evidence bundle: static analysis notes, decompiled clue index, Bluetooth scan output, logcat/HCI capture pointers, raw frame fixtures, normalized event fixtures, phone vibration evidence, and deferred motor notes.
- **D-10:** Commit sanitized protocol docs and small fixtures to the repo. Keep large raw captures and decompile output in ignored evidence directories, with committed manifest pointers that identify local file names, origin, test action, and expected interpretation.
- **D-11:** Build a throwaway diagnostic Android app/module for validation. It should scan and report standard Android input visibility, BLE/Classic services, decoded frames, phone vibration, and deferred motor test outcomes.
- **D-12:** The diagnostic app/module is validation tooling for Phase 1, not the production Android host app. Useful code can be reused later, but planners should not treat UI or module boundaries as production commitments.

### Fixture Shape
- **D-13:** Normalized fixtures should be JSON Lines event sequences for readable downstream tests.
- **D-14:** Raw protocol evidence should be stored as binary blobs or capture excerpts referenced from JSONL fixtures/manifests, not hand-transcribed byte strings only.
- **D-15:** Fixtures must cover trigger, reload, joystick axes, X/Y/A/B button down/up semantics, Bluetooth connection/handshake observations, and v1 phone haptic evidence. Physical gun motor command/ack/failure evidence is optional/deferred.

### the agent's Discretion
- Choose exact decompile tools, parser scripts, ignored evidence directory names, JSONL field names, and diagnostic UI layout during planning.
- Choose how much of each ignored raw capture to summarize in committed manifests, as long as downstream agents can reproduce or inspect the evidence locally.
- Choose whether to create small command-line helpers alongside the throwaway Android diagnostic app if they reduce manual fixture extraction.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

None — discussion stayed within Phase 1 scope.
</user_constraints>

## Summary

Phase 1 should plan a discovery pipeline, not product code: inventory local APK/XAPK refs, extract static Bluetooth/control/haptic clues, validate each clue against physical hardware or phone haptic evidence, then commit sanitized docs plus small JSONL fixtures while keeping large raw captures/decompile output out of git. [VERIFIED: local]

Primary recommendation: use `apktool`, `jadx`, and `adb` already present locally; require human verification before any package-manager install; make every protocol conclusion pass static clue -> hardware capture -> normalized fixture before planner treats it as verified. [VERIFIED: local]

Planner should preserve hard boundary: throwaway Android diagnostic module is allowed; production Android host, LAN pairing/session, desktop driver, profile mapper, and visualizer implementation are out of Phase 1. [VERIFIED: local]

## Project Constraints (from AGENTS.md)

- Use `$caveman ultra` communication style for agent messages. [VERIFIED: local]
- Current focus is Phase 1 Hardware and Protocol Discovery. [VERIFIED: local]
- No production source tree exists yet; current repo assets are planning docs and `docs/refs/` APK/XAPK archives. [VERIFIED: local]
- Before current project work, read `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, and `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md`. [VERIFIED: local]
- Phase 1 evidence rule: protocol/control finding counts as verified only when static decompile clue, hardware capture, and normalized fixture all exist. v1 haptic feedback is verified by phone vibration evidence and fixture linkage. [VERIFIED: local]
- No direct desktop-to-gun Bluetooth in v1; Android hosts the physical gun. [VERIFIED: local]
- Android-to-desktop v1 transport is Wi-Fi/LAN, but Phase 1 must not plan production LAN implementation. [VERIFIED: local]
- Desktop must eventually support Windows 11 x64 and macOS Apple Silicon, so Phase 1 normalized fixtures must avoid platform-specific desktop assumptions. [VERIFIED: local]
- HID shape for v1 is normal gamepad/joystick, not custom gun report; Phase 1 should name controls in normalized, platform-neutral terms. [VERIFIED: local]
- Aim mapping belongs to desktop profiles later; Phase 1 should capture raw/normalized gun controls and hardware facts, not desktop mapping behavior. [VERIFIED: local]
- Keep docs short, factual, agent-facing; no project code conventions exist yet. [VERIFIED: local]

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DISC-01 | Inventory every local reference APK/XAPK under `docs/refs/` with package name, target SDK, permissions, app type, and validity status. [VERIFIED: local] | Use `apktool` for manifests/resources, `jadx` for code clues, XAPK zip listing for embedded APK/OBB, committed inventory table. [CITED: apktool.org] [CITED: github.com/skylot/jadx] |
| DISC-02 | Run Android diagnostic that reports whether iPega appears as standard Android input device. [VERIFIED: local] | Diagnostic should enumerate `InputDevice`, capture `KeyEvent`, and capture `MotionEvent` before assuming proprietary Bluetooth. [CITED: developer.android.com] |
| DISC-03 | Run Android diagnostic that reports visible Bluetooth Classic and BLE services. [VERIFIED: local] | Diagnostic should cover BLE discovery/characteristics/notifications and Classic socket discovery/connection observations. [CITED: developer.android.com] |
| DISC-04 | Capture/store raw Bluetooth traffic or app-observed frames for trigger, reload, joystick, X/Y/A/B, and haptic/deferred motor tests. [VERIFIED: local] | Use `adb`, logcat, Bluetooth HCI snoop/bug report flow, app-observed frame logs, raw blob manifest pointers. [CITED: developer.android.com] |
| DISC-05 | Map every physical gun control to normalized event with down/up or axis semantics. [VERIFIED: local] | Use JSONL event sequences tied to raw blobs and static clue ids; include trigger, reload, joystick axes, X/Y/A/B. [VERIFIED: local] |
| DISC-06 | Verify Android phone vibration as the v1 feedback path and document physical gun motor rumble as deferred. [VERIFIED: local] | Require phone vibration log, human confirmation, capture manifest row, and haptic fixture; keep BLE motor attempts deferred. [VERIFIED: local] |
| DISC-07 | Save raw and normalized protocol fixtures for regression tests without physical gun. [VERIFIED: local] | Commit small JSONL fixtures/manifests; keep large captures ignored with reproducible metadata. [VERIFIED: local] |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Reference APK/XAPK inventory | Repo docs/evidence | Android tooling | Inventory is static evidence consumed by later plans, not runtime app behavior. [VERIFIED: local] |
| Static Bluetooth/protocol clue extraction | Repo docs/evidence | Android tooling | `apktool`/`jadx` reveal candidate classes, permissions, UUIDs, native/Unity assets, and haptic/deferred motor paths before hardware tests. [CITED: apktool.org] [CITED: github.com/skylot/jadx] |
| Physical gun input visibility test | Android diagnostic tooling | Physical hardware | Android owns `InputDevice`, `KeyEvent`, `MotionEvent`, Bluetooth scan/connect, and on-device observation. [CITED: developer.android.com] |
| BLE/Classic service discovery | Android diagnostic tooling | Physical hardware | BLE GATT and Classic socket discovery happen on Android against the paired gun. [CITED: developer.android.com] |
| Raw capture collection | Android diagnostic tooling | Local evidence storage | `adb`, logcat, HCI snoop, and app logs produce raw evidence; repo stores manifest pointers and small excerpts. [CITED: developer.android.com] [VERIFIED: local] |
| Normalized fixture creation | Repo fixtures | Android diagnostic tooling | Phase output must be replayable without hardware; JSONL normalized events are committed test inputs. [VERIFIED: local] |
| Phone haptic proof | Android diagnostic tooling | Physical device | v1 feedback uses Android phone vibration; proof requires phone-vibration log plus human observation tied to capture manifest and fixture. [VERIFIED: local] |
| Production Android host | Out of scope | - | Context explicitly says diagnostic module is validation tooling, not production host. [VERIFIED: local] |
| LAN pairing/streaming | Out of scope | - | Later phases own Wi-Fi/LAN session, UDP input, and reliable control channel. [VERIFIED: local] |
| Desktop virtual controller | Out of scope | - | Later phases own Windows/macOS virtual HID backends. [VERIFIED: local] |

## Standard Stack

### Core

| Tool/API | Version/Status | Purpose | Why Standard |
|----------|----------------|---------|--------------|
| `apktool` | 3.0.2 at `/opt/homebrew/bin/apktool` [VERIFIED: local] | Decode APK resources, manifests, smali. [CITED: apktool.org] | Official apktool purpose is reverse engineering Android APK resources/smali; use for manifest/permission/resource inventory. [CITED: apktool.org] |
| `jadx` | 1.5.5 at `/opt/homebrew/bin/jadx` [VERIFIED: local] | Decompile APK/DEX/AAR/JAR to Java-like source. [CITED: github.com/skylot/jadx] | Official jadx project is a dex-to-Java decompiler; use for Bluetooth bridge/control/haptic code clues. [CITED: github.com/skylot/jadx] |
| `adb` | 1.0.41 / platform-tools 36.0.0 at Android SDK path [VERIFIED: local] | Device logs, bug reports, HCI snoop evidence, diagnostic install/run. [CITED: developer.android.com] | Android docs cover bug reports/developer options and Bluetooth HCI snoop capture paths. [CITED: developer.android.com] |
| Android `InputDevice`/`KeyEvent`/`MotionEvent` | Platform APIs [CITED: developer.android.com] | Determine whether gun appears as standard controller. [CITED: developer.android.com] | Official controller docs cover identifying connected controllers and handling key/motion events. [CITED: developer.android.com] |
| Android Bluetooth Classic APIs | Platform APIs [CITED: developer.android.com] | Validate Classic/RFCOMM-style socket path if discovered. [CITED: developer.android.com] | Official docs cover `BluetoothSocket`/`BluetoothServerSocket` connection flow; RFCOMM/SPP is common socket path. [CITED: developer.android.com] |
| Android BLE APIs | Platform APIs [CITED: developer.android.com] | Discover services/chars, read/write/notify candidate gun protocol. [CITED: developer.android.com] | Official BLE docs cover service discovery, characteristic read/write, and notifications. [CITED: developer.android.com] |
| Android Bluetooth permissions | Platform APIs [CITED: developer.android.com] | Make diagnostic runnable on modern Android. [CITED: developer.android.com] | Android 12+ Bluetooth scan/connect/advertise permissions are runtime Nearby Devices permissions. [CITED: developer.android.com] |

### Supporting

| Tool/API | Version/Status | Purpose | When to Use |
|----------|----------------|---------|-------------|
| Android SensorManager motion sensors | Platform APIs [CITED: developer.android.com] | Optional diagnostic context for phone motion timestamps if needed. [CITED: developer.android.com] | Use only if Phase 1 diagnostic needs to confirm motion sensor availability; production motion-aim provider selection, accelerometer fallback, and recenter belong to Phase 2. [VERIFIED: local] |
| XAPK zip inspection | System zip tooling [VERIFIED: local] | Verify embedded APK/OBB names and validity. [VERIFIED: local] | Use before decode so invalid/0-byte refs do not waste plan time. [VERIFIED: local] |
| Logcat/HCI capture manifests | Repo-local docs/JSON [VERIFIED: local] | Preserve large capture provenance without committing heavy blobs. [VERIFIED: local] | Use for every hardware test action and haptic/deferred motor test. [VERIFIED: local] |
| Small CLI fixture helpers | Discretionary [ASSUMED] | Convert diagnostic logs/raw excerpts into JSONL. [ASSUMED] | Use only if it reduces manual extraction; no package-manager dependency without human verify gate. [VERIFIED: local] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `apktool` manifest/resource decode [CITED: apktool.org] | `aapt dump badging` [ASSUMED] | `aapt` is not on PATH locally, so planner should not rely on it without setup task/gate. [VERIFIED: local] |
| `jadx` Java-like decompile [CITED: github.com/skylot/jadx] | Smali-only review via apktool [CITED: apktool.org] | Smali is exact but slower for class flow; use both when Java decompile is unclear. [ASSUMED] |
| Android diagnostic module [VERIFIED: local] | Desktop Bluetooth probing [ASSUMED] | v1 architecture keeps Android as gun host; desktop Bluetooth is out of scope. [VERIFIED: local] |
| JSONL normalized fixtures [VERIFIED: local] | Handwritten byte-string notes [ASSUMED] | JSONL is replayable/readable; raw blobs keep byte-level proof. [VERIFIED: local] |

**Installation:**

```bash
# No package-manager install should be required by the Phase 1 plan.
# Use already-present system tools:
/opt/homebrew/bin/apktool --version
/opt/homebrew/bin/jadx --version
/Users/lucas.rancez/Library/Android/sdk/platform-tools/adb version

# If Android Studio/Gradle must generate a throwaway diagnostic module,
# planner must add a human-verify checkpoint before accepting generated Gradle/plugin deps.
```

**Version verification:** `apktool`, `jadx`, and `adb` were probed locally; no npm/PyPI/crates package is recommended for automatic install in Phase 1. [VERIFIED: local]

## Package Legitimacy Audit

Phase 1 should not require package-manager installs. [VERIFIED: local] If planner adds any npm/PyPI/Gradle dependency, it must insert `checkpoint:human-verify` before install/use because user explicitly required no package-manager install without human verify gate. [VERIFIED: local]

| Package/Tool | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|--------------|----------|-----|-----------|-------------|-----------|-------------|
| `apktool` | system tool, not package install [VERIFIED: local] | Not audited [ASSUMED] | Not audited [ASSUMED] | apktool official site [CITED: apktool.org] | Not run; no install [VERIFIED: local] | Approved as present local tool; no install. [VERIFIED: local] |
| `jadx` | system tool, not package install [VERIFIED: local] | Not audited [ASSUMED] | Not audited [ASSUMED] | official GitHub [CITED: github.com/skylot/jadx] | Not run; no install [VERIFIED: local] | Approved as present local tool; no install. [VERIFIED: local] |
| Android Gradle wrapper/plugin | Gradle/Maven [ASSUMED] | Not audited [ASSUMED] | Not audited [ASSUMED] | Android/Gradle upstream [ASSUMED] | Not run; no install [VERIFIED: local] | Do not add automatically; planner must gate generated deps behind human verify. [VERIFIED: local] |

**Packages removed due to slopcheck `[SLOP]` verdict:** none; no package install recommendations. [VERIFIED: local]
**Packages flagged as suspicious `[SUS]`:** none from research; any future package added by planner needs human verify gate. [VERIFIED: local]
**Graceful degradation:** slopcheck was not needed because Phase 1 recommendations avoid package-manager installs. [VERIFIED: local]

## Architecture Patterns

### System Architecture Diagram

```text
--------------------+        +----------------------+        +----------------------+
| Local ref archives |        | Static RE workspace  |        | Clue index           |
| docs/refs/*.apk    | -----> | apktool + jadx       | -----> | classes/UUIDs/calls  |
| docs/refs/*.xapk   |        | no prod code         |        | test hypotheses      |
+--------------------+        +----------------------+        +----------+-----------+
                                                                        |
                                                                        v
+--------------------+        +----------------------+        +----------------------+
| Physical iPega gun | -----> | Throwaway Android    | -----> | Hardware evidence    |
| real hardware      |        | diagnostic module    |        | scan/logcat/HCI/raw  |
+--------------------+        | InputDevice + BT     |        +----------+-----------+
                              | BLE + Classic        |                   |
                              +----------+-----------+                   v
                                         |                      +----------------------+
                                         +--------------------> | Normalized fixtures  |
                                                                | JSONL + raw refs     |
                                                                +----------+-----------+
                                                                           |
                                                                           v
                                                                +----------------------+
                                                                | Committed evidence   |
                                                                | docs/manifests/small |
                                                                | fixtures only        |
                                                                +----------------------+
```

### Recommended Project Structure

```text
docs/
  protocol/
    ipega-phase1-inventory.md       # ref app identity, SDK, perms, validity
    ipega-phase1-clues.md           # static decompile clue index
    ipega-phase1-hardware.md        # physical scan/capture notes
    ipega-phase1-haptics.md         # phone haptic proof + deferred motor note
  evidence/
    manifests/
      phase1-captures.jsonl         # committed pointers to ignored raw captures
fixtures/
  ipega/
    normalized/
      trigger.jsonl
      reload.jsonl
      joystick.jsonl
      buttons-xyab.jsonl
      handshake.jsonl
      haptics.jsonl
    raw/
      README.md                     # small committed index; big blobs ignored
android-diagnostic/
  README.md                         # throwaway module warning
  app/                              # created only if planner chooses module path
.gitignore
  # ignore decompile output and large captures
```

Structure above is recommended, not locked; exact names are agent discretion per CONTEXT. [VERIFIED: local]

### Pattern 1: Clue-Led Hardware Tests

**What:** Each hardware test starts from a static clue id, then records action, expected signal, capture file pointer, observed result, and fixture id. [VERIFIED: local]

**When to use:** For every input control and v1 haptic path. [VERIFIED: local]

**Example:**

```json
{"clue_id":"ARGUN2021-BT-001","source_ref":"docs/refs/ARGun2021.apk","source_kind":"jadx","hypothesis":"candidate BLE characteristic for input notifications","hardware_test":"press trigger once","capture_ref":"local://.evidence/phase1/hci/trigger-001.btsnoop","fixture":"fixtures/ipega/normalized/trigger.jsonl","status":"verified"}
```

### Pattern 2: Normalized Event JSONL With Raw Pointers

**What:** Store replayable events as one JSON object per line, with raw capture refs and monotonic capture timestamps when known. [VERIFIED: local]

**When to use:** DISC-05 and DISC-07 fixtures. [VERIFIED: local]

**Example:**

```jsonl
{"schema":"ipega.normalized.v1","seq":1,"control":"trigger","phase":"down","value":1,"raw_ref":"local://.evidence/phase1/raw/trigger-001.bin","clue_id":"ARGUN2021-BT-001","capture_time_ms":123456789}
{"schema":"ipega.normalized.v1","seq":2,"control":"trigger","phase":"up","value":0,"raw_ref":"local://.evidence/phase1/raw/trigger-001.bin","clue_id":"ARGUN2021-BT-001","capture_time_ms":123456861}
```

### Pattern 3: Diagnostic Module Is Disposable

**What:** Android diagnostic may scan `InputDevice`, BLE services/chars, Classic socket observations, log decoded frames, and run phone haptic or deferred motor tests; it must label itself throwaway/Phase 1. [VERIFIED: local] [CITED: developer.android.com]

**When to use:** Needed for DISC-02 through DISC-06. [VERIFIED: local]

**Planner guardrail:** do not create production UI architecture, LAN session code, desktop pairing, or driver APIs inside Phase 1 tasks. [VERIFIED: local]

### Anti-Patterns to Avoid

- **Static-only protocol claims:** Strings/classes/UUIDs are hypotheses until hardware capture plus normalized fixture exists. [VERIFIED: local]
- **Every archive first:** Strongest refs come first; secondary refs only if blocked or shared-library behavior matters. [VERIFIED: local]
- **Committed decompile dumps:** Large generated output should stay ignored; commit clue index and manifests instead. [VERIFIED: local]
- **Hand-transcribed bytes only:** Raw blobs/capture excerpts must exist; byte strings in docs alone are not enough. [VERIFIED: local]
- **Production module creep:** Diagnostic Android module should not lock future Android app module boundaries. [VERIFIED: local]
- **Package install creep:** No package-manager dependency may enter plan without human verify gate. [VERIFIED: local]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| APK resource/manifest decode | Custom APK parser [ASSUMED] | `apktool` [CITED: apktool.org] | APK formats/resources/smali are complex; apktool is purpose-built. [CITED: apktool.org] |
| DEX-to-source review | Custom dex parser/decompiler [ASSUMED] | `jadx` [CITED: github.com/skylot/jadx] | jadx is official upstream dex-to-Java decompiler project. [CITED: github.com/skylot/jadx] |
| Android controller event path | Custom Linux input reader in Android app [ASSUMED] | `InputDevice`, `KeyEvent`, `MotionEvent` [CITED: developer.android.com] | Platform APIs already expose game controller discovery/events. [CITED: developer.android.com] |
| BLE GATT plumbing | Ad hoc byte transport without service discovery [ASSUMED] | Android BLE service/characteristic/notification APIs [CITED: developer.android.com] | GATT service discovery/read/write/notify are platform-level APIs. [CITED: developer.android.com] |
| Classic Bluetooth socket flow | Raw vendor-specific socket stack [ASSUMED] | Android `BluetoothSocket`/`BluetoothServerSocket` APIs [CITED: developer.android.com] | Platform APIs own connection flow. [CITED: developer.android.com] |
| Evidence traceability | Freeform notes only [ASSUMED] | JSONL manifest + raw capture refs + clue ids [VERIFIED: local] | Planner and later agents need reproducible proof chain. [VERIFIED: local] |

**Key insight:** Phase 1 success is evidence quality, not app polish; standard tools reduce reverse-engineering noise so effort goes to physical proof. [VERIFIED: local]

## Common Pitfalls

### Pitfall 1: Standard-Gamepad Assumption
**What goes wrong:** Planner builds only `KeyEvent`/`MotionEvent` flow, but gun needs proprietary BLE/SPP handling. [VERIFIED: local]
**Why it happens:** Hardware looks like controller, while ref apps request Bluetooth permissions and may hide protocol in Unity/native code. [VERIFIED: local]
**How to avoid:** Diagnostic must test both `InputDevice` visibility and raw Bluetooth services before design claims. [CITED: developer.android.com]
**Warning signs:** No controller in `InputDevice.getDeviceIds()`, but BLE services/Classic paths appear during scan. [ASSUMED]

### Pitfall 2: Static-Only Reverse Engineering
**What goes wrong:** Code copies UUID strings or class names that fail on real gun. [VERIFIED: local]
**Why it happens:** Decompiled refs are clues, not proof; Unity/native layers can hide behavior. [VERIFIED: local]
**How to avoid:** Require static clue, hardware capture, and normalized fixture for every verified finding. [VERIFIED: local]
**Warning signs:** `fff0`/`fff1`/`fff3` strings appear but no captured bytes prove input characteristic semantics. [VERIFIED: local]

### Pitfall 3: Blocking v1 On Physical Gun Motor Rumble
**What goes wrong:** Input mapping succeeds, but v1 stays blocked by an unknown physical motor command path. [VERIFIED: local]
**Why it happens:** Motor rumble is an output path and may require write characteristic, Classic command, or vendor app behavior. [ASSUMED]
**How to avoid:** Use confirmed Android phone vibration for v1 feedback and record motor attempts as deferred evidence. [VERIFIED: local]
**Warning signs:** Diagnostic can read controls and vibrate the phone, but no repeatable gun motor activation exists. [ASSUMED]

### Pitfall 4: Evidence Too Large Or Too Local
**What goes wrong:** Useful captures exist only in an agent temp dir or massive decompile folders get committed. [VERIFIED: local]
**Why it happens:** Raw evidence and generated output are heavy, but planners need pointers. [VERIFIED: local]
**How to avoid:** Commit manifests and small fixtures; ignore large raw/decompile outputs with stable local paths and metadata. [VERIFIED: local]
**Warning signs:** Docs say "see capture" without file name, origin, action, expected interpretation, or fixture id. [VERIFIED: local]

### Pitfall 5: Diagnostic Becomes Product Host
**What goes wrong:** Phase 1 accidentally plans production Android architecture, LAN transport, or UI polish. [VERIFIED: local]
**Why it happens:** Diagnostic app touches the same Android APIs later host will use. [VERIFIED: local]
**How to avoid:** Label module throwaway, keep tasks evidence-centered, and defer production boundaries to Phase 2. [VERIFIED: local]
**Warning signs:** Phase 1 plan includes QR pairing, UDP packet schemas, desktop driver APIs, profile UI, or visualizer work. [VERIFIED: local]

## Code Examples

### Minimal Capture Manifest Row

```json
{"schema":"btgun.phase1.capture_manifest.v1","capture_id":"trigger-001","source_ref":"docs/refs/ARGun2021.apk","clue_id":"ARGUN2021-BT-001","device":"ipega-ar-gun","action":"trigger down/up","raw_path":"local://.evidence/phase1/raw/trigger-001.bin","hci_path":"local://.evidence/phase1/hci/trigger-001.btsnoop","normalized_fixture":"fixtures/ipega/normalized/trigger.jsonl","interpretation":"trigger emits down/up event","verification":"static+hardware+fixture"}
```

### Control Mapping Fixture Shape

```jsonl
{"schema":"btgun.ipega.normalized.v1","fixture_id":"buttons-xyab-001","seq":1,"control":"x","kind":"button","phase":"down","value":1,"raw_ref":"local://.evidence/phase1/raw/buttons-001.bin","clue_id":"ARCHER-BT-004"}
{"schema":"btgun.ipega.normalized.v1","fixture_id":"buttons-xyab-001","seq":2,"control":"x","kind":"button","phase":"up","value":0,"raw_ref":"local://.evidence/phase1/raw/buttons-001.bin","clue_id":"ARCHER-BT-004"}
```

### Haptic Evidence Fixture Shape

```jsonl
{"schema":"btgun.ipega.normalized.v1","fixture_id":"phone-vibrate-001","seq":1,"control":"phone_haptic","kind":"haptic_test","phase":"observed","value":{"requested_duration_ms":1000,"observed_phone_vibration":true,"physical_gun_motor":"deferred"},"raw_ref":"local://.evidence/phase1/app-logs/phone-vibrate-001.logcat.txt","clue_id":"ARGUN2021-RUMBLE-001","capture_id":"phone-vibrate-001"}
```

Field names above are recommendations; CONTEXT leaves exact JSONL field names to planner discretion. [VERIFIED: local]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` manifest permissions only [VERIFIED: local] | Android 12+ runtime Nearby Devices permissions for scan/connect/advertise [CITED: developer.android.com] | Android 12/API 31 permission model [CITED: developer.android.com] | Diagnostic module must handle runtime Bluetooth permissions if targeting modern Android. [CITED: developer.android.com] |
| Treat APK strings as protocol spec [ASSUMED] | Static clues guide hardware captures, then fixtures lock findings [VERIFIED: local] | Project Phase 1 decision [VERIFIED: local] | Planner must split static RE tasks from validation tasks. [VERIFIED: local] |
| Commit all generated RE output [ASSUMED] | Commit clue index/manifests/small fixtures; ignore big captures/decompile output [VERIFIED: local] | Project Phase 1 decision [VERIFIED: local] | Keeps repo usable while preserving local reproducibility. [VERIFIED: local] |

**Deprecated/outdated for Phase 1:**
- Treating `ARGunPro_1.0.19_apkcombo.com.xapk` as usable local input; it is 0 bytes locally and should be reacquired only if strongest refs block discovery. [VERIFIED: local]
- Planning production LAN/desktop/HID content inside Phase 1; later phases own that work. [VERIFIED: local]

## Local Reference Inventory Baseline

| Ref | Local Status | Research Baseline | Planner Action |
|-----|--------------|-------------------|----------------|
| `docs/refs/ARGun2021.apk` | 305M local APK [VERIFIED: local] | Existing research records package `com.lcp.arbrower`, label `ARGun`, target SDK 29, Bluetooth/location/camera/network/vibrate permissions, Unity/native libs. [VERIFIED: local] | Analyze first with `apktool` + `jadx`; create clue ids. [VERIFIED: local] |
| `docs/refs/AR Cher_20200905_Apkpure.xapk` | 153M local XAPK; contains `com.lenzetech.archer.apk` + OBB per orchestrator input [VERIFIED: local] | Existing research records package `com.lenzetech.archer`, target SDK 29, Bluetooth/admin/location permissions. [VERIFIED: local] | Analyze first-pass after/with ARGun2021. [VERIFIED: local] |
| `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` | 150M local XAPK; contains `com.lenze.armagic.apk` + OBB per orchestrator input [VERIFIED: local] | Existing research records package `com.lenze.armagic`, target SDK 22, Bluetooth/admin permissions. [VERIFIED: local] | Analyze first-pass after/with AR Cher. [VERIFIED: local] |
| `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` | 9.2M local APK [VERIFIED: local] | Existing research records package `com.argun`, target SDK 26, React Native-looking launcher/library, no Bluetooth manifest permission. [VERIFIED: local] | Secondary only if strongest refs insufficient or shared-library clues needed. [VERIFIED: local] |
| `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` | 0B invalid local file [VERIFIED: local] | No usable local archive. [VERIFIED: local] | Reacquire only if strongest valid refs block protocol discovery. [VERIFIED: local] |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| `apktool` | APK/XAPK manifest/resource/smali decode | yes [VERIFIED: local] | 3.0.2 [VERIFIED: local] | none needed |
| `jadx` | APK/DEX Java-like decompile | yes [VERIFIED: local] | 1.5.5 [VERIFIED: local] | smali via apktool, but slower [ASSUMED] |
| `adb` | Device logs, bug reports, HCI capture, diagnostic run | yes [VERIFIED: local] | 1.0.41 / platform-tools 36.0.0 [VERIFIED: local] | none for hardware validation |
| `aapt` | Optional APK badging shortcut | no [VERIFIED: local] | - | use apktool manifest decode [CITED: apktool.org] |
| `gradle` | Optional diagnostic module build if not using Android Studio generated wrapper | no [VERIFIED: local] | - | Android Studio/Gradle wrapper generated during execution, human verify gate required [ASSUMED] |
| `ctx7` | Documentation lookup fallback | no [VERIFIED: local] | - | Use orchestrator-provided official docs facts and local docs; do not install automatically [VERIFIED: local] |
| Project test framework | Nyquist validation | no [VERIFIED: local] | - | Wave 0 should create fixture/schema validation commands [VERIFIED: local] |

**Missing dependencies with no fallback:**
- None for static research/inventory using present tools. [VERIFIED: local]
- Physical Android device/gun connection state was not probed in this research; planner should make first hardware task verify `adb devices`, gun power/pairing state, and capture readiness. [ASSUMED]

**Missing dependencies with fallback:**
- `aapt` absent; use `apktool` manifest decode. [VERIFIED: local] [CITED: apktool.org]
- `gradle` absent; if diagnostic module needs build tooling, generate via Android Studio/Gradle wrapper during execution with human verify gate before dependency acceptance. [ASSUMED]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None detected; Phase 1 should create lightweight fixture/schema validation plus manual hardware verification checklist. [VERIFIED: local] |
| Config file | none; no `package.json`, Gradle files, pytest, Jest, or Vitest config detected. [VERIFIED: local] |
| Quick run command | `find fixtures -name '*.jsonl' -print` plus repo-local JSONL validator once Wave 0 creates it. [ASSUMED] |
| Full suite command | Static inventory checks + JSONL fixture validation + manual hardware evidence checklist generated in `VALIDATION.md`. [ASSUMED] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| DISC-01 | Ref APK/XAPK inventory includes identity, SDK, permissions, app type, validity. [VERIFIED: local] | static/doc validation | `test -f docs/protocol/ipega-phase1-inventory.md` and grep required refs. [ASSUMED] | no, Wave 0 |
| DISC-02 | Diagnostic reports Android input-device visibility. [VERIFIED: local] | device/manual + saved log validation | `test -f docs/protocol/ipega-phase1-hardware.md` and check manifest row for `input_device_scan`. [ASSUMED] | no, Wave 0 |
| DISC-03 | Diagnostic reports BLE/Classic services. [VERIFIED: local] | device/manual + saved log validation | Check capture manifest rows for `ble_scan` and `classic_scan`. [ASSUMED] | no, Wave 0 |
| DISC-04 | Raw/app-observed frames captured for controls and haptics/deferred rumble. [VERIFIED: local] | fixture/evidence validation | Validate `docs/evidence/manifests/phase1-captures.jsonl` rows include control action coverage. [ASSUMED] | no, Wave 0 |
| DISC-05 | Every physical control mapped to normalized event semantics. [VERIFIED: local] | fixture validation | Validate JSONL fixtures contain trigger, reload, joystick, x, y, a, b down/up or axis events. [ASSUMED] | no, Wave 0 |
| DISC-06 | Android phone vibration feedback identified and verified; physical gun motor deferred. [VERIFIED: local] | device/manual + fixture validation | Check haptic doc plus `fixtures/ipega/normalized/haptics.jsonl` contains `phone-vibrate-001` outcome. [ASSUMED] | no, Wave 0 |
| DISC-07 | Raw and normalized fixtures saved for no-hardware regression. [VERIFIED: local] | fixture validation | Run JSONL parser/coverage helper against committed fixture set. [ASSUMED] | no, Wave 0 |

### Sampling Rate

- **Per task commit:** run inventory/fixture validator for files touched by that task. [ASSUMED]
- **Per wave merge:** run full fixture coverage helper and manually confirm evidence checklist rows have raw refs, clue ids, and interpretation. [ASSUMED]
- **Phase gate:** `VALIDATION.md` should require all DISC rows pass and at least one verified chain per control/haptic finding: static clue -> hardware capture -> normalized fixture. [VERIFIED: local]

### Wave 0 Gaps

- [ ] `docs/protocol/ipega-phase1-inventory.md` - covers DISC-01. [VERIFIED: local]
- [ ] `docs/protocol/ipega-phase1-clues.md` - maps static clue ids to ref app/source location. [VERIFIED: local]
- [ ] `docs/protocol/ipega-phase1-hardware.md` - records `InputDevice`, BLE, Classic, logcat/HCI outcomes. [VERIFIED: local]
- [ ] `docs/protocol/ipega-phase1-haptics.md` - records v1 phone haptic proof and deferred physical motor status. [VERIFIED: local]
- [ ] `docs/evidence/manifests/phase1-captures.jsonl` - committed manifest for ignored raw captures. [VERIFIED: local]
- [ ] `fixtures/ipega/normalized/*.jsonl` - no-hardware replay fixtures for DISC-05/DISC-07. [VERIFIED: local]
- [ ] Repo-local JSONL/schema/coverage validator - exact implementation at planner discretion; no external package install without human verify. [ASSUMED]

## Security Domain

Phase 1 is evidence/diagnostic work, not production LAN/auth implementation. [VERIFIED: local] Security focus is safe handling of device permissions, local captures, and untrusted archive analysis. [VERIFIED: local]

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no for Phase 1 production behavior [VERIFIED: local] | Do not implement LAN pairing/auth in Phase 1; later phases own it. [VERIFIED: local] |
| V3 Session Management | no for Phase 1 production behavior [VERIFIED: local] | No production sessions; diagnostic-only device connection logs. [VERIFIED: local] |
| V4 Access Control | limited [ASSUMED] | Keep raw captures/decompile output local/ignored; commit sanitized manifests only. [VERIFIED: local] |
| V5 Input Validation | yes [VERIFIED: local] | Treat APK/XAPK/capture files as untrusted input; parse with standard tools, validate JSONL schemas, avoid executing decompiled code. [CITED: apktool.org] [CITED: github.com/skylot/jadx] |
| V6 Cryptography | no for Phase 1 implementation [VERIFIED: local] | Do not design/implement LAN crypto here; later session phase owns authenticated transport. [VERIFIED: local] |

### Known Threat Patterns for Phase 1 Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed APK/XAPK crashes custom parser | Denial of Service | Use `apktool`/`jadx`; avoid custom parsers. [CITED: apktool.org] [CITED: github.com/skylot/jadx] |
| Sensitive local Bluetooth capture committed | Information Disclosure | Commit manifests/small sanitized fixtures; ignore large raw captures. [VERIFIED: local] |
| Decompiled/vendor code accidentally executed | Elevation of Privilege | Decompile/read only; do not run extracted code or auto-install packages from archives. [ASSUMED] |
| Fixture/schema drift hides wrong mapping | Tampering | Validate JSONL coverage and clue/capture/fixture linkage before phase gate. [VERIFIED: local] |
| Android permission mismatch blocks diagnostic | Denial of Service | Request modern Bluetooth runtime Nearby Devices permissions for diagnostic module. [CITED: developer.android.com] |

## Open Questions (RESOLVED)

1. **Should Phase 1 start from hardware probing or reverse engineering?** Resolved: start from reverse engineering first, then validate against hardware. [VERIFIED: local]
2. **Which refs come first?** Resolved: `ARGun2021.apk`, `AR Cher_20200905_Apkpure.xapk`, and `WorldsAR_14.0_apkcombo.com.xapk`; secondary refs only if blocked. [VERIFIED: local]
3. **What counts as verified protocol evidence?** Resolved: static clue + hardware capture + normalized fixture. [VERIFIED: local]
4. **Should the diagnostic Android module be production app foundation?** Resolved: no; it is throwaway validation tooling. [VERIFIED: local]
5. **Can the plan require new package installs?** Resolved: no package-manager install should be required without human verify gate. [VERIFIED: local]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Small CLI helpers may help convert logs/raw excerpts into JSONL. | Standard Stack | Planner might add unnecessary scripting; keep optional and package-free. |
| A2 | Smali-only review is slower than Java-like jadx review. | Alternatives Considered | Planner may allocate time poorly; low product risk. |
| A3 | Physical gun motor rumble may require write characteristic, Classic command, or vendor app behavior. | Common Pitfalls | Motor research is deferred unless v2 reopens it. |
| A4 | Physical Android device/gun connection state needs first task verification. | Environment Availability | Plan may assume device ready when it is not connected/pairable. |
| A5 | Android Studio/Gradle wrapper generation may be needed for diagnostic module. | Environment Availability | Dependency setup may delay Phase 1 unless gated early. |
| A6 | Repo-local JSONL validator implementation details are planner discretion. | Validation Architecture | Nyquist plan needs Wave 0 task to create validation helper. |

## Sources

### Primary (HIGH confidence)

- `.planning/phases/01-hardware-and-protocol-discovery/01-CONTEXT.md` - locked Phase 1 boundary, decisions, evidence bar, fixture shape. [VERIFIED: local]
- `.planning/REQUIREMENTS.md` - DISC-01 through DISC-07 and v1/out-of-scope constraints. [VERIFIED: local]
- `.planning/ROADMAP.md` - Phase 1 goal, success criteria, phase boundaries. [VERIFIED: local]
- `.planning/PROJECT.md` - core value, architecture intent, hardware/ref app context. [VERIFIED: local]
- `AGENTS.md` - project workflow, constraints, current focus. [VERIFIED: local]
- Android Developers docs facts supplied by orchestrator - controller `KeyEvent`/`MotionEvent`/`InputDevice`, Bluetooth permissions, BLE, Classic sockets, HCI snoop/bug reports, motion sensors. [CITED: developer.android.com]
- apktool official site fact supplied by orchestrator - reverse-engineering APK resources/smali. [CITED: apktool.org]
- jadx official GitHub fact supplied by orchestrator - dex-to-Java decompiler for APK/DEX/AAR/JAR. [CITED: github.com/skylot/jadx]

### Secondary (MEDIUM confidence)

- `.planning/research/SUMMARY.md` - existing local ref metadata and broad Phase 1 implications. [VERIFIED: local]
- `.planning/research/STACK.md` - toolchain recommendations and Android/Bluetooth stack notes. [VERIFIED: local]
- `.planning/research/ARCHITECTURE.md` - normalized event boundary and evidence flow. [VERIFIED: local]
- `.planning/research/PITFALLS.md` - standard-gamepad/static-only/haptic pitfalls. [VERIFIED: local]
- `.planning/research/FEATURES.md` - discovery dependency notes and feature boundaries. [VERIFIED: local]

### Tertiary (LOW confidence)

- Assumptions in `## Assumptions Log`; planner should validate before locking tasks that depend on them. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - required tools are locally present and official docs facts were supplied; no package installs needed. [VERIFIED: local] [CITED: apktool.org] [CITED: github.com/skylot/jadx] [CITED: developer.android.com]
- Architecture: HIGH - Phase 1 responsibility is evidence/diagnostic/fixture flow, explicitly constrained by CONTEXT. [VERIFIED: local]
- Pitfalls: HIGH - main risks are explicitly documented in local research and locked evidence decisions. [VERIFIED: local]
- Exact protocol: LOW until implementation captures physical hardware evidence. [VERIFIED: local]

**Research date:** 2026-06-06 [VERIFIED: local]
**Valid until:** 2026-07-06 for planning structure; protocol facts become valid only when Phase 1 captures prove them. [ASSUMED]
