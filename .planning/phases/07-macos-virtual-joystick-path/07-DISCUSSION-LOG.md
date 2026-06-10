# Phase 7: macOS Virtual Joystick Path - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-10T12:22:36Z
**Phase:** 7-macOS Virtual Joystick Path
**Areas discussed:** macOS HID path, macOS pass bar, Output/haptic truth, Packaging/dev setup, Live stream cutover

---

## macOS HID path

| Option | Description | Selected |
|--------|-------------|----------|
| CoreHID first | Target CoreHID `HIDVirtualDevice` first for fastest proof and no DriverKit entitlement dependency up front. | ✓ |
| DriverKit first | Build toward HIDDriverKit/system extension first; heavier but closer to distributable driver shape. | |
| Dual path | Plan CoreHID proof plus DriverKit fallback in the same phase; broader but higher risk. | |

**User's choice:** CoreHID first.
**Notes:** Fallback is research-gated. Switch away from CoreHID only if official docs or local compile/runtime proof show CoreHID cannot create an OS-visible gamepad device that satisfies Phase 7.

| Option | Description | Selected |
|--------|-------------|----------|
| Research-gated fallback | Try CoreHID proof first; switch to HIDDriverKit only if official docs/local proof show CoreHID cannot satisfy the device proof. | ✓ |
| Time-boxed fallback | Spend fixed effort on CoreHID, then document fallback if blocked. | |
| No fallback build | Only document HIDDriverKit as future path if CoreHID fails. | |

**User's choice:** Research-gated fallback.
**Notes:** Later output decision tightened this: if CoreHID cannot receive OS-origin output/rumble, HIDDriverKit fallback becomes mandatory.

---

## macOS pass bar

| Option | Description | Selected |
|--------|-------------|----------|
| Layered proof | CLI HID enumeration plus macOS-visible game controller/gamepad UI or tester plus agent/user visual confirmation. | ✓ |
| CLI enough | `ioreg`/CoreHID enumeration and input report logs are enough. | |
| Gamepad UI mandatory only | Pass only when a normal gamepad tester/UI shows buttons/axes moving; CLI evidence secondary. | |

**User's choice:** Layered proof.
**Notes:** Final input proof must use a live paired Android/gun stream. Replays are allowed only for tests/debug. Final visual proof needs both agent evidence and user confirmation.

| Option | Description | Selected |
|--------|-------------|----------|
| Live Android/gun stream mandatory | Final pass needs paired Android/gun moving macOS-visible axes/buttons. | ✓ |
| Replay acceptable | OS-visible device plus replayed fixture input can pass Phase 7. | |
| Both equal | Either live stream or replay can pass if evidence is clear. | |

**User's choice:** Live Android/gun stream mandatory.
**Notes:** Matches the stricter Windows target proof bar.

| Option | Description | Selected |
|--------|-------------|----------|
| Agent + user confirmation | Agent captures CLI/tester evidence; user confirms visible device/input on Mac UI. | ✓ |
| Agent evidence only | Screenshots/logs from agent are enough. | |
| User confirmation only | Manual confirmation is enough; agent records summary. | |

**User's choice:** Agent + user confirmation.
**Notes:** No raw secrets, pairing material, or screenshots with sensitive data should be committed.

---

## Output/haptic truth

| Option | Description | Selected |
|--------|-------------|----------|
| Probe then report honestly | Try CoreHID output-report receive path; if unsupported, document limitation and keep phone haptic via companion. | |
| Do not attempt OS output | Declare macOS output reports unsupported in v1 and only preserve phone haptic command path. | |
| Must support output | Phase 7 cannot pass unless macOS OS-origin output/rumble maps to phone haptic. | ✓ |

**User's choice:** Must support output.
**Notes:** This is a hard Phase 7 pass gate.

| Option | Description | Selected |
|--------|-------------|----------|
| DriverKit fallback mandatory | Keep CoreHID first, but HIDDriverKit/system extension becomes required fallback for output proof. | ✓ |
| Block Phase 7 | Stop and re-scope if CoreHID cannot do output. | |
| Companion haptic backstop | Keep phone haptic control path, but Phase 7 still fails OS-output hard gate unless later fixed. | |

**User's choice:** DriverKit fallback mandatory.
**Notes:** The companion phone-haptic path remains the v1 transport, but it must be triggered by macOS-origin output/rumble for Phase 7 pass.

---

## Packaging/dev setup

| Option | Description | Selected |
|--------|-------------|----------|
| Development proof package | Local signed macOS app/tool with documented CoreHID/current-target requirement and DriverKit fallback path. | ✓ |
| System extension package | Build/install signed app plus DriverKit extension in Phase 7 if fallback needed. | |
| Docs only for packaging | Build proof binary only; document packaging/entitlements for later. | |

**User's choice:** Development proof package.
**Notes:** Production notarized installer and distribution polish are not Phase 7 goals unless fallback proof forces specific packaging.

| Option | Description | Selected |
|--------|-------------|----------|
| macOS 15+ for CoreHID | Use macOS 15+ as primary target; document older macOS requires DriverKit fallback. | |
| Current Mac only | Target whatever this development Mac runs; document exact version from local proof. | ✓ |
| Older macOS support | Require support below macOS 15 in Phase 7, likely pushing DriverKit earlier. | |

**User's choice:** Current Mac only.
**Notes:** Local proof target detected during discussion: macOS 26.2 build 25C56, arm64.

| Option | Description | Selected |
|--------|-------------|----------|
| Ad-hoc/local dev first | Use local development signing where CoreHID permits; document exact commands and permission prompts. | ✓ |
| Developer ID required | Require Apple Developer signing even for Phase 7 proof. | |
| Entitlement request only if fallback | Start CoreHID proof; request/use DriverKit entitlements only if fallback becomes necessary. | |

**User's choice:** Ad-hoc/local dev first.
**Notes:** DriverKit entitlement handling becomes relevant if fallback is required.

---

## Live stream cutover

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror Windows runtime | `MacosBackendRuntime` attaches to `ControlServer.onUdpInputReceived`, maps to semantic state, publishes to backend, preserves callbacks. | ✓ |
| Standalone companion-local backend | macOS virtual device runs in companion process but does not follow Windows runtime attachment pattern. | |
| Separate helper process | Kotlin companion launches a native macOS helper and streams reports over local IPC. | |

**User's choice:** Mirror Windows runtime.
**Notes:** macOS must preserve companion ownership of LAN/session/security.

| Option | Description | Selected |
|--------|-------------|----------|
| Match Windows/Phase 4 | Clear active buttons, keep last aim axes, expose stale diagnostics. | ✓ |
| Neutral all controls | Clear buttons and zero all axes including aim. | |
| Keep all last state | Keep buttons and axes unchanged, only mark stale. | |

**User's choice:** Match Windows/Phase 4.
**Notes:** Keeps stale behavior consistent across platform backends.

| Option | Description | Selected |
|--------|-------------|----------|
| Native helper allowed | Use Swift/Objective-C helper if needed for CoreHID; Kotlin runtime owns session/security and sends semantic reports locally. | ✓ |
| Kotlin/JVM only | Avoid native helper; use JNA/JNI only if needed. | |
| Decide during planning | Planner chooses after research/prototype. | |

**User's choice:** Native helper allowed.
**Notes:** Helper must stay local and must not own LAN pairing, auth, UDP validation, or Android session lifecycle.

---

## the agent's Discretion

- Choose CoreHID descriptor/report details, helper language/project layout, local IPC, evidence artifact names, CLI enumeration commands, and macOS tester/tooling.
- Choose DriverKit fallback design during planning if CoreHID cannot satisfy OS-visible device or OS-origin output proof.

## Deferred Ideas

- Production notarized installer, release signing/distribution polish, and paid Developer ID flow are deferred beyond Phase 7 unless required by the selected fallback proof path.
- Profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Replay diagnostics beyond platform smoke remain Phase 10.
- Physical gun motor rumble remains v2/deferred.
