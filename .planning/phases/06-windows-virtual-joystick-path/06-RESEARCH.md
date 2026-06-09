# Phase 06: Windows Virtual Joystick Path - Research

**Researched:** 2026-06-09
**Domain:** Windows 11 x64 KMDF/VHF virtual HID gamepad-style joystick, user-mode bridge, HID output-to-phone haptic routing
**Confidence:** HIGH for phase boundaries and Microsoft VHF/KMDF facts; MEDIUM for exact root-device install flow and `joy.cpl` output behavior until tested on `192.168.1.100`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Driver Strategy
- **D-01:** Phase 6 pass condition is a real Windows VHF/KMDF virtual joystick path, not vJoy, ViGEm, or the Phase 5 stub backend.
- **D-02:** A locally test-signed/self-signed driver package is acceptable for Phase 6 development proof. Paid release signing, Partner Center submission, and EV-certificate flow are not required for this phase.
- **D-03:** Every Windows boot/signing setting change or reboot requires explicit user approval before execution.
- **D-04:** Do not install WDK, Visual Studio, MSBuild, Git, or other Windows build toolchains on `192.168.1.100` during Phase 6. Use that machine as the install/proof target.
- **D-05:** Platform-specific Windows driver build may run in GitHub Actions on `origin` (`git@github.com:Guisardo/bt-gun.git`).
- **D-06:** CI signing should use a persistent test-signing certificate stored as a GitHub Actions secret. Do not commit private key material.

### Windows Proof Target
- **D-07:** Windows visibility proof is layered: CLI/PnP evidence, HID/game-controller enumeration, and visual proof.
- **D-08:** `joy.cpl` is mandatory for final visibility proof. Phase 6 does not pass unless Windows Game Controllers shows the virtual joystick.
- **D-09:** Final input proof must use a live paired Android/gun stream moving Windows-visible joystick axes/buttons. Fake state and replay fixtures are allowed for automated tests, CI, and debugging only.
- **D-10:** Final visual proof requires both agent-captured evidence and user manual confirmation.

### Stream Cutover
- **D-11:** The existing desktop companion receives LAN/control traffic, keeps session/security ownership, maps semantic state, and sends HID reports to the Windows driver through a user-mode IOCTL-style bridge.
- **D-12:** The Windows VHF/KMDF driver stays a small HID/report bridge. It must not own LAN networking, pairing, authentication, profile mapping, or Android session lifecycle.
- **D-13:** Phase 6 uses fixed default mapping only: Phase 5 semantic trigger/reload/X/Y/A/B, stickX/stickY, and rawAimX/rawAimY map directly into the Windows HID report.
- **D-14:** Do not add Phase 8 profile UI, sensitivity, inversion, dead-zone, smoothing, or remapping behavior.
- **D-15:** If the live stream becomes stale while the joystick is active, clear active buttons, keep last aim axes, and expose stale diagnostic state. Preserve the Phase 4 stale-stream behavior.

### Output Haptic Proof
- **D-16:** Final Phase 6 output proof must use a real Windows HID output report mapped to Android phone haptic. The Phase 5 simulated output report path is not enough.
- **D-17:** Try to initiate the output path through `joy.cpl` first.
- **D-18:** If `joy.cpl` cannot send output/rumble for this virtual joystick descriptor, document that limitation and then use a small Windows HID output sender as fallback proof.
- **D-19:** Final haptic proof requires both Windows-side output report evidence and user confirmation that the Android phone physically vibrated.

### Windows Host Facts
- **D-20:** The available Windows target is `192.168.1.100`, Windows 11 Education `10.0.22000` x64.
- **D-21:** The observed SSH session is administrator-capable; Java 17 is available. Git, Visual Studio/MSBuild, Windows Kits/WDK, `signtool`, `inf2cat`, and `devcon` were not found in PATH or expected install locations during discussion.
- **D-22:** Secure Boot and VBS/HVCI were observed disabled on the Windows host during discussion, but any future boot/signing setting changes still require explicit approval.

### the agent's Discretion
- Choose exact VHF/KMDF project layout, driver/device names, INF details, HID descriptor bytes, report ids, IOCTL contract shape, and user-mode bridge implementation as long as the Phase 5 semantic descriptor and decisions above hold.
- Choose exact GitHub Actions workflow structure, artifact names, secret names, and signing commands, while keeping test-certificate private material out of git history and logs.
- Choose exact CLI/PnP/HID enumeration commands and screenshot capture mechanism for evidence, provided final proof still includes `joy.cpl` and user confirmation.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

- Production release signing, paid developer program setup, attestation signing, EV certificate handling, and installer polish are deferred beyond Phase 6 unless later planning explicitly pulls them in.
- macOS virtual joystick path remains Phase 7.
- Profile storage, mapping UI, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Physical gun motor rumble remains v2/deferred.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DESK-02 | Desktop companion can expose a regular gamepad-style virtual joystick on Windows 11 x64. | Use a real KMDF HID source driver with VHF, a Generic Desktop Game Pad or Joystick top-level collection, fixed Phase 5 input report packing, and mandatory `joy.cpl` proof. [VERIFIED: `.planning/REQUIREMENTS.md`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-; CITED: https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/input/hardware/input-hardware-mapping?view=gdk-2604] |
| DESK-05 | Windows virtual joystick path can receive desktop rumble/output requests and map them to v1 phone haptic commands. | Register VHF `EvtVhfAsyncOperationWriteReport`, queue real HID output report bytes to user mode, map report ID 2 to `HapticCommand`, and send through existing authenticated `ControlServer.sendHapticCommand`. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nc-vhf-evt_vhf_async_operation; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/hidport/ni-hidport-ioctl_hid_write_report] |
| PACK-02 | Repository documents the selected Windows virtual HID strategy, driver signing requirements, and development setup. | Document VHF/KMDF architecture, CI build/signing, test certificate handling, `bcdedit /set testsigning on` approval gate, install/uninstall proof commands, `joy.cpl` screenshot/manual confirmation, and fallback output sender. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/test-signing; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option] |
</phase_requirements>

## Summary

Phase 6 should implement the Windows product path as a small KMDF HID source driver that uses Microsoft Virtual HID Framework (VHF), plus a Kotlin/JVM user-mode bridge inside the existing desktop companion. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] The driver should only expose the virtual HID device, accept packed input reports from user mode, submit those reports with `VhfReadReportSubmit`, and surface HID output reports back to user mode. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfreadreportsubmit; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nc-vhf-evt_vhf_async_operation]

Do not move LAN pairing, UDP authentication, replay/stale checks, profile mapping, or phone haptic transport into the driver. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; VERIFIED: `.planning/research/ARCHITECTURE.md`] The existing `SemanticControllerState`, `btGunV1Descriptor`, `UdpControllerStateAdapter`, `ControlServer`, and `HapticCommand` are the integration boundary for Phase 6. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt`]

**Primary recommendation:** Plan one vertical Windows slice: descriptor and report packer tests -> VHF/KMDF driver and INF -> user-mode IOCTL bridge -> CI-built test-signed package -> install/proof on `192.168.1.100` with `joy.cpl` input visibility and real HID output-to-phone haptic fallback sender if `joy.cpl` cannot emit output. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| UDP receive/session security | Desktop companion user mode | Android host | Existing `ControlServer` and UDP runtime own authenticated sessions and stream configs; the Windows driver must not parse LAN traffic or secrets. [VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| Semantic-to-HID mapping | Desktop companion Windows backend | KMDF bridge validation | Phase 6 fixed mapping uses Phase 5 semantic fields; driver should receive already-packed bounded reports and reject malformed bridge buffers. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt`] |
| Virtual HID enumeration | Windows KMDF/VHF driver | INF/install scripts | VHF is the Microsoft framework for a kernel-mode HID source driver that enumerates virtual HID children. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] |
| Input report submission | Windows KMDF/VHF driver | User-mode bridge feeds reports | HID input reports enter Windows through `VhfReadReportSubmit`; default VHF buffering is acceptable for Phase 6 unless tests prove backpressure needs explicit callbacks. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfreadreportsubmit] |
| HID output report capture | Windows KMDF/VHF driver | Desktop companion haptic mapper | `EvtVhfAsyncOperationWriteReport` receives HID write/output operations; user mode converts report bytes to v1 phone haptic commands. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nc-vhf-evt_vhf_async_operation; VERIFIED: `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt`] |
| Phone vibration | Android host | Desktop control channel | v1 haptics remain Android phone vibration via authenticated reliable control; physical gun motor stays deferred. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; `.planning/REQUIREMENTS.md`] |
| Windows proof evidence | Windows target + user | Repo evidence docs | Final pass requires CLI/PnP, HID/game-controller enumeration, `joy.cpl`, live Android/gun input, real HID output report, and human confirmation. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |

## Project Constraints (from AGENTS.md)

- Keep docs short, factual, and agent-facing. [VERIFIED: `AGENTS.md`]
- v1 must support Windows 11 x64 and macOS Apple Silicon; protocol and virtual-controller contract must not assume one platform. [VERIFIED: `AGENTS.md`; `.planning/PROJECT.md`]
- Android-to-desktop v1 transport is Wi-Fi/LAN with QR or pair code; no direct desktop-to-gun Bluetooth in v1. [VERIFIED: `AGENTS.md`; `.planning/REQUIREMENTS.md`]
- Visualizer input path targets under 50 ms, so Phase 6 should not add driver-side buffering beyond what VHF needs. [VERIFIED: `AGENTS.md`; `.planning/PROJECT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfreadreportsubmit]
- HID shape must be normal gamepad/joystick, not a custom HID gun report. [VERIFIED: `AGENTS.md`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- Desktop profiles own aim mapping; Phase 6 uses fixed default mapping only and must not add Phase 8 profile UI or tuning. [VERIFIED: `AGENTS.md`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- Phase 1 evidence rule remains for hardware/protocol claims; Phase 6 final proof must use live Android/gun input but should not reopen Bluetooth protocol discovery. [VERIFIED: `AGENTS.md`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- No project skills exist under `.codex/skills/` or `.agents/skills/`; no project skill rules apply. [VERIFIED: local `find .codex .agents -maxdepth 3 -name SKILL.md`]

## Standard Stack

### Core

| Library / Tool | Version | Purpose | Why Standard |
|----------------|---------|---------|--------------|
| Windows KMDF + VHF | Windows 10+ VHF API; target Windows 11 x64 | Kernel-mode HID source driver and virtual HID child enumeration | VHF eliminates writing a HID transport minidriver and provides the supported virtual HID tree path. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] |
| WDK | Latest docs recommend WDK `28000.1761` with VS 2026; WDK `26100.6584` is documented for VS 2022 continuity | Build KMDF/VHF driver, INF, catalog, signing artifacts | Microsoft documents WDK/SDK matching and MSBuild/Visual Studio driver builds. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/develop/building-a-driver] |
| Kotlin/JVM desktop companion | Kotlin plugin `2.0.21`, Java 17 target | Existing LAN/control/session owner and Windows user-mode bridge caller | Current desktop companion is Kotlin/JVM and already owns `ControlServer`, UDP receiver, haptic routing, and smoke commands. [VERIFIED: `desktop-companion/build.gradle.kts`; `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt`] |
| Windows device interface + custom IOCTLs | WDF device interface, `CTL_CODE`, `METHOD_BUFFERED` | User-mode companion talks to driver without embedding LAN logic in kernel | WDF device interfaces are symbolic links user-mode apps can open with `CreateFile`; user-mode IOCTLs use `DeviceIoControl`/`IRP_MJ_DEVICE_CONTROL`. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/using-device-interfaces; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes] |
| PnPUtil + DevGen/installer utility | Built-in PnPUtil; DevGen from WDK on Windows 11 22H2+ WDK | Add/install driver package and create/test root-enumerated device | PnPUtil manages driver packages and ships with Windows; DevGen creates software/root-enumerated devices for testing. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen] |
| Test signing | Self-signed/test certificate, TESTSIGNING boot option if needed | Development load of x64 kernel driver | x64 kernel-mode drivers must be signed; loading test-signed drivers requires explicit test-signing setup and reboot. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/test-signing; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option] |

### Supporting

| Library / Tool | Version | Purpose | When to Use |
|----------------|---------|---------|-------------|
| `joy.cpl` | Windows built-in Game Controllers UI | Mandatory final visibility proof | Use after install and input publishing; final pass requires user manual confirmation. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/test/hlk/testref/game-controller-testing-prerequisites] |
| HID output sender | Small Windows CLI in repo | Fallback real output-report proof | Use only after documenting `joy.cpl` cannot initiate output for this descriptor. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/hidclass/ni-hidclass-ioctl_hid_set_output_report] |
| GitHub Actions Windows runner | Exact image/toolchain to verify during implementation | Build/sign driver artifact without installing toolchain on `192.168.1.100` | Locked decision allows Windows driver build on `origin`; exact runner image and WDK install method need implementation validation. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; ASSUMED] |
| PowerShell/SSH | Local OpenSSH client available; Windows target SSH admin-capable per context | Copy artifacts, run CLI proof, collect sanitized evidence | Use for non-GUI proof only; GUI `joy.cpl` needs user/agent visual proof. [VERIFIED: local `ssh -V`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Real VHF/KMDF path | vJoy or ViGEm | Forbidden as Phase 6 pass path; only real VHF/KMDF satisfies locked decision. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| Custom IOCTL bridge | Driver owns LAN/control | Violates boundary and increases kernel attack surface; user mode already owns auth/session/haptics. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; `ControlServer.kt`] |
| Simple output report ID 2 | Full HID PID/force-feedback device | Full PID force feedback is broader than v1 phone pulse proof; Phase 6 should only implement simple output unless user explicitly expands scope. [CITED: https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/input/hardware/input-hardware-force-feedback?view=gdk-2604; ASSUMED] |
| PnPUtil-only install | DevGen or installer helper creates root devnode | PnPUtil manages driver packages but may not create a new root-enumerated devnode by itself; planner should include an explicit install-flow proof task. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen; ASSUMED] |

**Installation / proof skeleton:**

```powershell
# Build/sign happens in CI or a separate Windows build machine, not on 192.168.1.100. [VERIFIED: 06-CONTEXT.md]
# On proof target, after explicit user approval for any boot/signing/reboot change:
bcdedit /enum
bcdedit /set testsigning on
# reboot required before test-signed load takes effect [CITED: Microsoft TESTSIGNING docs]

# After artifact copy:
pnputil /add-driver .\btgunvjoy.inf /install
pnputil /enum-drivers | findstr /i btgun
pnputil /enum-devices /connected | findstr /i "BT Gun"
control joy.cpl
```

**Version verification:** WDK/SDK versions must be verified in the CI/build environment, not assumed from this macOS workspace. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk] The Windows target facts in Phase 6 context say `192.168.1.100` lacks WDK, Visual Studio/MSBuild, `signtool`, `inf2cat`, and `devcon`; do not plan local target builds. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

## Package Legitimacy Audit

No npm/PyPI/crates package install is recommended for Phase 6 research. [VERIFIED: current repo stack; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] Driver build tools should come from Microsoft WDK/SDK/Visual Studio/EWDK channels or GitHub Actions setup steps, and the exact CI acquisition method must be documented in implementation. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk; ASSUMED]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| none | - | - | - | - | not run | No application registry package proposed. [VERIFIED: local build strategy] |

**Packages removed due to slopcheck [SLOP] verdict:** none. [VERIFIED: no packages proposed]
**Packages flagged as suspicious [SUS]:** none. [VERIFIED: no packages proposed]

## Architecture Patterns

### System Architecture Diagram

```text
------------------------------+
| Android gun + phone sensors |
+--------------+---------------+
               |
               v
+------------------------------+
| Existing LAN/control stack   |
| UDP input + WSS haptics      |
+--------------+---------------+
               |
               v
+------------------------------+
| SemanticControllerState      |
| trigger/reload/x/y/a/b       |
| stickX/stickY/aimX/aimY      |
+--------------+---------------+
               |
               v
+------------------------------+
| WindowsBackend (user mode)   |
| stale guard + report packer  |
| DeviceIoControl bridge       |
+--------------+---------------+
               |
               v
+------------------------------+
| KMDF driver control device   |
| custom IOCTL queue           |
| VHF input submit             |
+--------------+---------------+
               |
               v
+------------------------------+
| VHF/HIDClass virtual device  |
| Generic Desktop Game Pad TLC |
+--------------+---------------+
               |
               v
      Windows joy.cpl / games

Output:
Windows HID output report
  -> VHF WriteReport callback
  -> driver output queue
  -> user-mode WindowsBackend
  -> HapticCommand
  -> ControlServer authenticated WSS
  -> Android phone vibration
```

This flow preserves Phase 6 D-11/D-12: user mode owns session/security and driver stays a report bridge. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

### Recommended Project Structure

```text
desktop-companion/
  src/main/kotlin/com/btgun/desktop/backend/windows/
    WindowsVirtualControllerBackend.kt      # VirtualControllerBackend implementation
    WindowsHidReportPacker.kt               # Semantic state -> report ID 1 bytes
    WindowsOutputReportMapper.kt            # report ID 2 bytes -> HapticCommand
    WindowsDriverBridge.kt                  # JNA/CLI/Process bridge or native helper wrapper
  src/test/kotlin/com/btgun/desktop/backend/windows/
    WindowsHidReportPackerTest.kt
    WindowsOutputReportMapperTest.kt
    WindowsBackendContractTest.kt

windows/
  btgun-vjoy/
    driver/
      BtGunVJoy.vcxproj
      Driver.c
      Device.c
      Queue.c
      Report.h
      Public.h                          # IOCTL codes + device interface GUID
      Trace.h
    package/
      btgunvjoy.inf
      README.md                         # test-sign/install/proof steps
    tools/
      hid-output-sender/                 # fallback real output report sender
      install-helper/                    # only if PnPUtil/DevGen proof needs it

docs/windows/
  virtual-hid-strategy.md
  test-signing-and-install.md
  phase6-proof-checklist.md

.github/workflows/
  windows-driver.yml                     # build, test-sign, upload artifact
```

This separates Kotlin companion code from WDK/KMDF code and keeps INF/signing docs near the driver package. [VERIFIED: `.planning/research/ARCHITECTURE.md`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

### Pattern 1: Fixed Phase 5 State to HID Report ID 1

**What:** Map `SemanticControllerState` to one fixed input report. [VERIFIED: `SemanticControllerState.kt`; `VirtualControllerDescriptor.kt`]

**Recommended input report bytes:** report ID 1, 10 bytes total: `01 buttons stickXlo stickXhi stickYlo stickYhi aimXlo aimXhi aimYlo aimYhi`. [ASSUMED]

```kotlin
// Source: Phase 5 descriptor + Phase 6 D-13/D-15.
fun packInput(state: SemanticControllerState, lastAimX: Short, lastAimY: Short): ByteArray {
    val safe = if (state.stale) state.copy(
        trigger = false, reload = false, x = false, y = false, a = false, b = false,
        stickX = 0, stickY = 0,
    ) else state
    val aimX = if (state.stale) lastAimX else (safe.aimX.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
    val aimY = if (state.stale) lastAimY else (safe.aimY.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
    val buttons = listOf(safe.trigger, safe.reload, safe.x, safe.y, safe.a, safe.b)
        .foldIndexed(0) { index, acc, pressed -> if (pressed) acc or (1 shl index) else acc }
    return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        .put(1).put(buttons.toByte())
        .putShort(safe.stickX.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        .putShort(safe.stickY.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        .putShort(aimX).putShort(aimY)
        .array()
}
```

Keep stale clearing in user mode and also validate report length/ranges in the driver. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes]

### Pattern 2: HID Descriptor With Input and Simple Output

**What:** Use Generic Desktop Game Pad usage for Windows game-controller enumeration, six buttons, four signed 16-bit axes, and a simple vendor-defined output report for Phase 6 haptic proof. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/hid-usages; CITED: https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/input/hardware/input-hardware-mapping?view=gdk-2604; ASSUMED]

```c
// Source: Microsoft VHF report descriptor pattern + Phase 5 descriptor.
// Report ID 1 input: 6 buttons + padding + X/Y/Rx/Ry signed int16.
// Report ID 2 output: vendor-defined 8-byte phone haptic command payload.
static const UCHAR BtGunReportDescriptor[] = {
    0x05, 0x01,       // Usage Page (Generic Desktop)
    0x09, 0x05,       // Usage (Game Pad)
    0xA1, 0x01,       // Collection (Application)
    0x85, 0x01,       //   Report ID (1)
    0x05, 0x09,       //   Usage Page (Button)
    0x19, 0x01,       //   Usage Minimum (1)
    0x29, 0x06,       //   Usage Maximum (6)
    0x15, 0x00,       //   Logical Minimum (0)
    0x25, 0x01,       //   Logical Maximum (1)
    0x75, 0x01,       //   Report Size (1)
    0x95, 0x06,       //   Report Count (6)
    0x81, 0x02,       //   Input (Data,Var,Abs)
    0x75, 0x01,       //   Report Size (1)
    0x95, 0x02,       //   Report Count (2)
    0x81, 0x03,       //   Input (Const,Var,Abs)
    0x05, 0x01,       //   Usage Page (Generic Desktop)
    0x09, 0x30,       //   Usage (X)      stickX
    0x09, 0x31,       //   Usage (Y)      stickY
    0x09, 0x33,       //   Usage (Rx)     aimX
    0x09, 0x34,       //   Usage (Ry)     aimY
    0x16, 0x00, 0x80, //   Logical Minimum (-32768)
    0x26, 0xFF, 0x7F, //   Logical Maximum (32767)
    0x75, 0x10,       //   Report Size (16)
    0x95, 0x04,       //   Report Count (4)
    0x81, 0x02,       //   Input (Data,Var,Abs)
    0x85, 0x02,       //   Report ID (2)
    0x06, 0x00, 0xFF, //   Usage Page (Vendor Defined)
    0x09, 0x01,       //   Usage (Vendor 1)
    0x15, 0x00,       //   Logical Minimum (0)
    0x26, 0xFF, 0x00, //   Logical Maximum (255)
    0x75, 0x08,       //   Report Size (8)
    0x95, 0x08,       //   Report Count (8)
    0x91, 0x02,       //   Output (Data,Var,Abs)
    0xC0              // End Collection
};
```

The exact descriptor must be validated on Windows because `joy.cpl` visibility is a hard pass gate. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; ASSUMED]

### Pattern 3: Driver Bridge IOCTLs

**What:** Register one WDF device interface for the companion and define small buffered IOCTLs in `Public.h`. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/using-device-interfaces; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes]

```c
// Source: Microsoft CTL_CODE guidance. Exact GUID should be generated once and committed.
#define FILE_DEVICE_BT_GUN_VJOY 0x8000
#define IOCTL_BTGVJOY_SUBMIT_INPUT \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x801, METHOD_BUFFERED, FILE_WRITE_DATA)
#define IOCTL_BTGVJOY_READ_OUTPUT \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x802, METHOD_BUFFERED, FILE_READ_DATA)
#define IOCTL_BTGVJOY_GET_STATUS \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x803, METHOD_BUFFERED, FILE_READ_DATA)

typedef struct _BTGVJOY_INPUT_BRIDGE_REPORT {
    UINT16 Size;
    UINT16 Version;
    UINT64 SourceSequence;
    UCHAR HidReport[10]; // report ID 1 + payload
} BTGVJOY_INPUT_BRIDGE_REPORT;

typedef struct _BTGVJOY_OUTPUT_BRIDGE_REPORT {
    UINT16 Size;
    UINT16 Version;
    UINT64 OutputSequence;
    UCHAR HidReport[9]; // report ID 2 + payload
} BTGVJOY_OUTPUT_BRIDGE_REPORT;
```

Use `FILE_WRITE_DATA` for input submission and `FILE_READ_DATA` for output reads; avoid `FILE_ANY_ACCESS` for mutating driver state. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes]

### Anti-Patterns to Avoid

- **Driver owns LAN/session state:** Puts authentication, replay handling, and network parsing in kernel; keep all of that in existing user-mode companion. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- **PnP proof without `joy.cpl`:** Device Manager/HIDClass evidence is not enough for Phase 6 final pass. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- **Fake/replay final input proof:** Replay is valid for CI and debugging only; final proof needs live paired Android/gun input moving Windows-visible axes/buttons. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- **Silent boot/signing changes:** `bcdedit`, reboot, Secure Boot, and test-signing changes need explicit user approval. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option]
- **Full force-feedback/PID scope creep:** Phase 6 needs real HID output-to-phone haptic proof, not a full DirectInput/GameInput force-feedback product. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/input/hardware/input-hardware-force-feedback?view=gdk-2604; ASSUMED]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Virtual HID transport stack | Custom HID minidriver/transport from scratch | Microsoft VHF in KMDF source driver | VHF provides the virtual HID tree and HID transport minidriver behavior. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] |
| Kernel networking/auth | LAN receiver in driver | Existing Kotlin `ControlServer` and UDP receiver | Existing code already validates trusted sessions, UDP HMAC, replay, stale lifecycle, and haptics. [VERIFIED: `ControlServer.kt`; `UdpInputReceiver.kt`; `docs/protocol/lan-pairing-v1.md`] |
| Output haptic transport | New Windows-to-Android channel | Existing `ControlServer.sendHapticCommand` | v1 haptics are already authenticated reliable control messages. [VERIFIED: `ControlServer.kt`; `HapticCommand.kt`] |
| Driver package manager | Ad hoc file copy into system dirs | INF + PnPUtil/DevGen/install helper | Windows driver packages should be installed through driver package/device mechanisms. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen] |
| Release signing program | EV/Partner Center in Phase 6 | Local test signing only | Locked decision defers paid/release signing. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |

**Key insight:** The hard problem is the boundary proof, not HID byte packing alone: Windows must see the VHF device in `joy.cpl`, live Android input must move it, and a real HID output report must traverse back through the existing authenticated phone haptic path. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

## Common Pitfalls

### Pitfall 1: PnP Device Exists but Game Controller UI Does Not
**What goes wrong:** `pnputil` or Device Manager shows a device, but `joy.cpl` does not show a usable game controller. [ASSUMED]
**Why it happens:** Descriptor usage, top-level collection shape, HIDClass binding, or game-controller mapping is wrong. [CITED: https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/input/hardware/input-hardware-mapping?view=gdk-2604; ASSUMED]
**How to avoid:** Use Generic Desktop Game Pad or Joystick top-level usage, validate descriptor bytes in tests, and make `joy.cpl` an early manual gate before haptic work. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/hid-usages; VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
**Warning signs:** HIDClass device appears but no Game Controllers entry or no axis movement in Properties/Test. [ASSUMED]

### Pitfall 2: Output Report Callback Not Registered
**What goes wrong:** Input works, but output/haptic proof never reaches user mode. [ASSUMED]
**Why it happens:** VHF completes WriteReport as unsupported unless the HID source driver registers the corresponding async operation callback. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-]
**How to avoid:** Register `EvtVhfAsyncOperationWriteReport` in `VHF_CONFIG`, parse only report ID 2, complete with `VhfAsyncOperationComplete`, and add a fallback HID output sender test. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nc-vhf-evt_vhf_async_operation]
**Warning signs:** Fallback sender returns success/failure inconsistently, no output sequence increments, or Android never receives `reserved_haptic_command`. [VERIFIED: `ControlServer.kt`; ASSUMED]

### Pitfall 3: Test-Signed Driver Loads on CI but Not Target
**What goes wrong:** Artifact builds and signs, but Windows target refuses to load it. [ASSUMED]
**Why it happens:** x64 kernel drivers require signatures; target test-signing/certificate/boot state can differ from build machine. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/test-signing; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option]
**How to avoid:** Document certificate import, `bcdedit /enum`, `TESTSIGNING`, reboot requirement, and explicit user approval before changes. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option]
**Warning signs:** Code 52/signature errors, missing `Test Mode` watermark when expected, or load failure after install. [ASSUMED]

### Pitfall 4: Stale Input Leaves Buttons Pressed
**What goes wrong:** A lost stream leaves trigger/reload/buttons down in Windows. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
**Why it happens:** Phase 5 state carries `stale`, but a Windows report packer might submit the previous button bits. [VERIFIED: `SemanticControllerState.kt`; `UdpControllerStateAdapter.kt`]
**How to avoid:** User-mode Windows packer must clear buttons and stick axes when stale, keep last aim axes, and expose stale diagnostic state. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; `docs/protocol/lan-pairing-v1.md`]
**Warning signs:** Disconnect while trigger is held leaves `joy.cpl` button active. [ASSUMED]

### Pitfall 5: Secrets in Driver Evidence
**What goes wrong:** CI logs, screenshots, or manifests leak QR material, HMAC keys, private key paths, or stream secrets. [VERIFIED: `.planning/STATE.md`; `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl`]
**Why it happens:** Driver install/debug logs are verbose and Phase 5 smoke generated QR artifacts. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-SUMMARY.md`]
**How to avoid:** Evidence manifests should record pass/fail, device names, sanitized IDs, XML paths, and user confirmation only. [VERIFIED: `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl`]
**Warning signs:** `rg` finds `qr_secret`, proof, HMAC key, private key, Bluetooth address, or full device ID in committed docs/artifacts. [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-SUMMARY.md`]

## Code Examples

Verified patterns from official sources and repo contracts:

### VHF Create and Start

```c
// Source: Microsoft VHF docs: VHF_CONFIG_INIT -> VhfCreate -> VhfStart.
VHF_CONFIG vhfConfig;
VHF_CONFIG_INIT(&vhfConfig,
    WdfDeviceWdmGetDeviceObject(device),
    sizeof(BtGunReportDescriptor),
    BtGunReportDescriptor);
vhfConfig.EvtVhfAsyncOperationWriteReport = BtGunEvtWriteReport;
status = VhfCreate(&vhfConfig, &context->VhfHandle);
if (NT_SUCCESS(status)) {
    status = VhfStart(context->VhfHandle);
}
```

VHF callbacks are not invoked until `VhfStart`, and `VhfCreate` is called after `WdfDeviceCreate`. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfcreate; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfstart]

### Submit Input Report

```c
// Source: Microsoft VhfReadReportSubmit docs.
HID_XFER_PACKET packet = {0};
packet.reportId = 1;
packet.reportBuffer = reportBytes;
packet.reportBufferLen = sizeof(reportBytes);
status = VhfReadReportSubmit(context->VhfHandle, &packet);
```

If no `EvtVhfReadyForNextReadReport` callback is registered, VHF uses default buffering and the driver can reuse the transfer buffer after the call returns. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfreadreportsubmit]

### Output Report to Haptic Command

```kotlin
// Source: Phase 4/5 HapticCommand contract + Phase 6 output report fallback.
fun mapOutputReport(bytes: ByteArray): HapticCommand? {
    if (bytes.size != 9 || bytes[0].toInt() != 2) return null
    val version = bytes[1].toInt() and 0xff
    if (version != 1) return null
    val strength = (bytes[2].toInt() and 0xff) / 255.0
    val durationMs = (bytes[3].toInt() and 0xff) or ((bytes[4].toInt() and 0xff) shl 8)
    val ttlMs = (bytes[5].toInt() and 0xff) or ((bytes[6].toInt() and 0xff) shl 8)
    return HapticCommand(
        commandId = "windows-output-${System.nanoTime()}",
        strength = strength,
        durationMs = durationMs.toLong().coerceIn(1L, 1_000L),
        ttlMs = ttlMs.toLong().coerceIn(1L, 2_000L),
        pattern = null,
    )
}
```

The mapped command must still pass through `ControlServer.sendHapticCommand`; direct Android haptic sockets are out of scope. [VERIFIED: `ControlServer.kt`; `HapticCommand.kt`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Write a HID transport minidriver from scratch | Write a KMDF/WDM HID source driver using VHF | Windows 10 VHF era | Phase 6 should use VHF rather than custom transport plumbing. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-] |
| DevCon as default new-device management tool | PnPUtil is recommended for new projects; DevGen can create test root/software devices | Current Microsoft docs | Use PnPUtil first for package management, but plan root-device creation proof explicitly. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devcon; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen] |
| Unsigned x64 kernel driver during development | Test-signed driver package plus explicit test-signing/certificate setup | Current x64 Windows policy | Plan test-signing and reboot approvals as manual gates. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/test-signing] |
| Treat gamepad visibility as Device Manager success | Require Game Controllers / `joy.cpl` final proof | Phase 6 locked decision | CLI evidence is necessary but insufficient. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |

**Deprecated/outdated:**
- vJoy/ViGEm as pass path: rejected by locked Phase 6 decision, regardless of prototype usefulness. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- Full production release signing in Phase 6: deferred beyond this phase. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Generic Desktop Game Pad report descriptor with six buttons and X/Y/Rx/Ry axes will appear in `joy.cpl` on Windows 11 target. | Architecture Patterns / Pitfalls | Descriptor may need Joystick usage or registry mapping changes; early Windows proof task required. |
| A2 | PnPUtil alone may not create the root-enumerated devnode for this virtual driver. | Standard Stack / Alternatives | Plan may need DevGen or a small installer helper before target install can pass. |
| A3 | Simple vendor-defined output report is enough for Phase 6 haptic proof with fallback sender. | Architecture Patterns / Alternatives | If user wants game-origin force feedback, Phase 6 scope would expand to HID PID/force-feedback descriptor work. |
| A4 | GitHub Actions Windows runner can be configured with matching WDK/SDK without installing toolchains on `192.168.1.100`. | Standard Stack | CI setup may require longer workflow/tool-cache work or a separate Windows build box. |

## Open Questions

1. **Root-enumerated install mechanism**
   - What we know: PnPUtil manages driver packages and DevGen creates software/root-enumerated devices for testing. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen]
   - What's unclear: Whether `pnputil /add-driver /install` alone will create the first `Root\BTGunVJoy` devnode for this package on Windows 11 `10.0.22000`. [ASSUMED]
   - Recommendation: Planner should put install-flow validation before live input work and include a fallback install helper/DevGen path. [ASSUMED]

2. **`joy.cpl` output initiation**
   - What we know: `joy.cpl` final visibility proof is mandatory, and Phase 6 must try `joy.cpl` first for output. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
   - What's unclear: Whether `joy.cpl` exposes any output/rumble action for this simple HID descriptor. [ASSUMED]
   - Recommendation: Plan a fallback HID output sender that calls `HidD_SetOutputReport`/output write and document `joy.cpl` limitation if observed. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/hidclass/ni-hidclass-ioctl_hid_set_output_report]

3. **CI WDK version**
   - What we know: Microsoft docs require matching SDK/WDK build numbers and support WDK through installer, EWDK, and NuGet paths. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk]
   - What's unclear: Which GitHub Actions image/toolchain path is fastest and stable for `origin`. [ASSUMED]
   - Recommendation: Planner should include a CI spike task that records WDK/SDK/MSBuild versions in artifact metadata. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Local `ssh` | Remote Windows CLI proof/copy | yes | OpenSSH_10.0p2 | None needed. [VERIFIED: local `ssh -V`] |
| Local Java | Existing desktop companion tests | yes | OpenJDK 17.0.19 | Use configured JDK 17 path. [VERIFIED: local `java -version`; `desktop-companion/build.gradle.kts`] |
| Local Gradle | Existing desktop companion tests | yes | command present; version command produced no text in this shell | Use existing phase commands with `GRADLE_USER_HOME=/private/tmp/bt-gun-gradle`. [VERIFIED: local `command -v gradle`; `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-SUMMARY.md`] |
| Windows target `192.168.1.100` | Final install/proof | yes per context | Windows 11 Education `10.0.22000` x64; Java 17; admin-capable SSH | No toolchain installs; use artifact-only proof target. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| Visual Studio/MSBuild/WDK on target | Driver build | no per context | - | Build in GitHub Actions or separate build host. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| `signtool`/`inf2cat`/`devcon` on target | Signing/root install helpers | no per context | - | Sign in CI; use PnPUtil built into Windows; add DevGen/install-helper artifact only if needed. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil] |
| Local macOS `msbuild`/`signtool` | Windows driver build/sign | not usable for WDK target | Mono `msbuild`; non-WDK `signtool` path observed | Do not use for Windows kernel driver artifacts. [VERIFIED: local `file` checks; ASSUMED] |

**Missing dependencies with no fallback:**
- None for research. [VERIFIED: this file written from docs/source only]

**Missing dependencies with fallback:**
- Windows WDK/VS/MSBuild on `192.168.1.100`: fallback is CI-built artifact, per locked D-04/D-05. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- `joy.cpl` output initiation: fallback is a small HID output sender after documenting the limitation. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Current desktop tests are Kotlin `main()` test classes invoked by Gradle `Test.doLast`; driver tests should add CI build checks and small C/user-mode smoke tools. [VERIFIED: `desktop-companion/build.gradle.kts`] |
| Config file | `desktop-companion/build.gradle.kts`; future `.github/workflows/windows-driver.yml`; future `windows/btgun-vjoy/driver/BtGunVJoy.vcxproj`. [VERIFIED: local repo; ASSUMED for future files] |
| Quick run command | `cd desktop-companion && GRADLE_USER_HOME=/private/tmp/bt-gun-gradle gradle test --offline --no-daemon --console=plain` [VERIFIED: `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-SUMMARY.md`] |
| Full suite command | Quick run plus Windows driver CI workflow plus manual Windows target proof checklist. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; ASSUMED] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| DESK-02 | Semantic state packs to report ID 1 with six buttons/four axes and stale clearing. | unit | `gradle test --offline --no-daemon --console=plain` | No - Wave 0 add `WindowsHidReportPackerTest.kt`. [VERIFIED: existing tests; ASSUMED] |
| DESK-02 | KMDF/VHF driver builds and package contains `.sys`, `.inf`, `.cat`, public IOCTL header, and symbols/log metadata. | CI build | GitHub Actions Windows driver workflow | No - Wave 0/1 add workflow and driver project. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; ASSUMED] |
| DESK-02 | Windows target sees HIDClass/PnP device and `joy.cpl` shows controller. | manual gate | `pnputil`, `Get-PnpDevice`, `control joy.cpl` | No - proof docs needed. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/test/hlk/testref/game-controller-testing-prerequisites] |
| DESK-02 | Live Android/gun input moves Windows-visible buttons/axes. | manual + live smoke | Windows backend run + live paired Android/gun | No - final proof only. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| DESK-05 | Report ID 2 maps to bounded `HapticCommand` and rejects bad length/version/pattern. | unit | `gradle test --offline --no-daemon --console=plain` | No - add `WindowsOutputReportMapperTest.kt`. [VERIFIED: `HapticCommand.kt`; ASSUMED] |
| DESK-05 | Real HID output report reaches driver callback and Android phone vibrates. | manual + fallback sender | `hid-output-sender.exe` after `joy.cpl` attempt if needed | No - add sender and proof checklist. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| PACK-02 | Docs cover VHF strategy, signing, install, proof, rollback. | docs review | `rg -n "VHF|testsigning|joy.cpl|pnputil" docs/windows windows` | No - add docs. [VERIFIED: `.planning/REQUIREMENTS.md`; ASSUMED] |

### Sampling Rate

- **Per task commit:** Run desktop Kotlin quick tests for changed companion code. [VERIFIED: `desktop-companion/build.gradle.kts`]
- **Per driver build change:** Run Windows driver CI build/sign/package and upload artifact. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; ASSUMED]
- **Before target install:** Human approval checkpoint for test-signing/boot/reboot/install actions. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- **Phase gate:** Full Windows proof: PnP/HID CLI evidence, `joy.cpl` visual evidence, live Android/gun input movement, real HID output report, user phone-vibration confirmation, and sanitized manifest. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]

### Wave 0 Gaps

- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPacker.kt` - covers DESK-02 fixed mapping. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/com/btgun/desktop/backend/windows/WindowsHidReportPackerTest.kt` - report bytes and stale behavior. [ASSUMED]
- [ ] `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsOutputReportMapper.kt` - covers DESK-05. [ASSUMED]
- [ ] `windows/btgun-vjoy/driver/*` - KMDF/VHF source driver. [ASSUMED]
- [ ] `windows/btgun-vjoy/package/btgunvjoy.inf` - VHF lower filter and root hardware ID. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-; ASSUMED]
- [ ] `.github/workflows/windows-driver.yml` - CI build/sign/package artifact. [ASSUMED]
- [ ] `docs/windows/test-signing-and-install.md` - boot/signing/install/user approval steps. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Existing pinned trusted desktop/session proof remains in user mode; driver receives no LAN credentials. [VERIFIED: `ControlServer.kt`; `docs/protocol/lan-pairing-v1.md`] |
| V3 Session Management | yes | Existing `ControlServer` active session and UDP stream lifecycle remain authoritative. [VERIFIED: `ControlServer.kt`; `docs/protocol/lan-pairing-v1.md`] |
| V4 Access Control | yes | Custom IOCTLs should require read/write access bits and avoid unrestricted `FILE_ANY_ACCESS` for mutating calls. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes] |
| V5 Input Validation | yes | Validate IOCTL buffer sizes, report IDs, report lengths, axis ranges, haptic duration/TTL/strength, and stale behavior before driver submission/control send. [VERIFIED: `HapticCommand.kt`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes] |
| V6 Cryptography | yes | Do not create new crypto in driver; preserve existing TLS/HMAC/session-secret handling and keep CI signing private keys in secrets only. [VERIFIED: `docs/protocol/lan-pairing-v1.md`; `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |

### Known Threat Patterns for Windows Driver Bridge

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unprivileged process spams IOCTL input reports | Tampering / DoS | Restrict device interface ACL if needed, require write access for submit IOCTL, validate length/version, and keep driver logic minimal. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes; ASSUMED] |
| Malformed HID output report triggers bad haptic command | Tampering | Reject unknown report ID, wrong size, unsupported version, out-of-range duration/TTL/strength before `ControlServer.sendHapticCommand`. [VERIFIED: `HapticCommand.kt`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/hidclass/ni-hidclass-ioctl_hid_set_output_report] |
| Test certificate/private key leak | Information Disclosure | Store persistent signing cert as GitHub Actions secret; never commit private key material or print it in logs. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`] |
| Stale stream leaves pressed controls active | Safety / Tampering | Clear buttons and stick axes on stale, keep last aim, expose diagnostic stale state. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; `docs/protocol/lan-pairing-v1.md`] |
| Driver load policy weakened silently | Elevation of Privilege / Operational risk | Require explicit user approval for `bcdedit`, Secure Boot, HVCI/VBS, and reboot changes; document reversal. [VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`; CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option] |

## Sources

### Primary (HIGH confidence)
- `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md` - locked Phase 6 decisions, Windows target facts, proof gates. [VERIFIED: local file]
- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-05-SUMMARY.md` - Phase 5 backend/haptic smoke handoff. [VERIFIED: local file]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/*.kt` - descriptor/state/capability/stub contract. [VERIFIED: local code]
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` and `haptics/HapticCommand.kt` - authenticated haptic command path. [VERIFIED: local code]
- Microsoft VHF docs: `https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfcreate`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfstart`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nf-vhf-vhfreadreportsubmit`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/vhf/nc-vhf-evt_vhf_async_operation`. [CITED: Microsoft Learn]
- Microsoft signing/install docs: `https://learn.microsoft.com/en-us/windows-hardware/drivers/install/test-signing`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/install/the-testsigning-boot-configuration-option`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/pnputil`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devgen`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/devcon`. [CITED: Microsoft Learn]
- Microsoft WDF/IOCTL docs: `https://learn.microsoft.com/en-us/windows-hardware/drivers/wdf/using-device-interfaces`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/defining-i-o-control-codes`. [CITED: Microsoft Learn]

### Secondary (MEDIUM confidence)
- Microsoft HID output docs: `https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/hidport/ni-hidport-ioctl_hid_write_report`, `https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/hidclass/ni-hidclass-ioctl_hid_set_output_report`. [CITED: Microsoft Learn]
- Microsoft game-controller mapping docs: `https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/input/hardware/input-hardware-mapping?view=gdk-2604`, `https://learn.microsoft.com/en-us/windows-hardware/test/hlk/testref/game-controller-testing-prerequisites`. [CITED: Microsoft Learn]

### Tertiary (LOW confidence)
- Assumptions about `joy.cpl` output behavior and exact root-enumerated install mechanism. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - VHF/KMDF and test-signing facts are from Microsoft docs and locked project decisions. [CITED: Microsoft Learn; VERIFIED: `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md`]
- Architecture: HIGH - Boundaries come from Phase 6 context and existing Phase 5 code. [VERIFIED: local files]
- Report descriptor/install details: MEDIUM - recommended bytes and install flow need target validation. [ASSUMED]
- Haptic output proof: MEDIUM - VHF WriteReport path is documented, but `joy.cpl` output behavior is unverified until target proof. [CITED: Microsoft Learn; ASSUMED]

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 for VHF/KMDF architecture; refresh WDK/CI image versions before implementation because Microsoft toolchain recommendations can change. [CITED: https://learn.microsoft.com/en-us/windows-hardware/drivers/download-the-wdk]
