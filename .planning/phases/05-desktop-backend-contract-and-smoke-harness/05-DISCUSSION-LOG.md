# Phase 5: Desktop Backend Contract and Smoke Harness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-09T17:25:09Z
**Phase:** 5-Desktop Backend Contract and Smoke Harness
**Areas discussed:** Backend contract, Fake smoke harness, Gamepad descriptor shape, Capability flags, Haptic/output boundary

---

## Backend contract

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Where should Phase 5 draw shared backend contract? | Normalized controller backend | Kotlin interface consumes stable buttons/axes/haptic intents; Windows/macOS adapters translate later. | yes |
| Where should Phase 5 draw shared backend contract? | HID report backend | Phase 5 defines descriptor and raw report bytes now; smoke harness validates exact report shape. | |
| Where should Phase 5 draw shared backend contract? | UDP input backend | Backend consumes `UdpReceivedInput` directly; mapper/descriptor waits until later. | |
| Who feeds contract in Phase 5? | Backend state objects | Fake harness creates `ControllerInputState`; UDP/profile mapper plugs in later. | |
| Who feeds contract in Phase 5? | Tiny UDP adapter now | Convert `UdpReceivedInput` into backend state now; still no profiles. | yes |
| Who feeds contract in Phase 5? | Profile mapper stub now | Add default mapper path early, but risk pulling Phase 8 into Phase 5. | |
| Contract output shape? | Named semantic controls | `trigger`, `reload`, `stickX/Y`, `aimX/Y`, `x/y/a/b`; adapters decide HID report packing. | yes |
| Contract output shape? | Fixed HID slots | Already shape as HID buttons/axes indexes; simpler descriptor smoke, less semantic clarity. | |
| Contract output shape? | Both layers | Semantic state plus optional fixed slot projection for smoke tests. | |
| Error/status surface? | State + capabilities + last error | Contract exposes current controller state, backend capabilities, lifecycle, last publish/error. | yes |
| Error/status surface? | Callbacks only | Simpler event stream; tests inspect emitted events. | |
| Error/status surface? | Diagnostics object | Rich snapshot with timing, sequence, source, backend info; more Phase 9-ish. | |

**User's choice:** normalized controller backend; tiny UDP adapter now; named semantic controls; state + capabilities + last error.
**Notes:** Contract should hide Windows/macOS details while still proving Phase 4 receiver handoff.

---

## Fake smoke harness

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| What counts as Phase 5 pass? | Contract fake backend | Same JVM fake backend runs on macOS + Windows, proves state publish, capability flags, haptic intents. | |
| What counts as Phase 5 pass? | Platform stub harness | Separate macOS/Windows command stubs, no OS-visible device, but catches platform packaging early. | yes |
| What counts as Phase 5 pass? | OS-visible skeleton | Try minimal virtual HID device now; risks pulling Phase 6/7 into Phase 5. | |
| Fake input source? | Scripted semantic frames | Deterministic sequence: neutral, trigger, reload, stick sweep, aim sweep, haptic request. | |
| Fake input source? | Replay UDP fixtures | Feed encoded UDP frames through receiver path; better integration, less direct backend control. | yes |
| Fake input source? | Both minimal | Semantic frames primary, one UDP adapter smoke proving Phase 4 handoff. | |
| Platform coverage expectation in Phase 5? | Same repo tests, platform-labeled stubs | CI/local can run on current machine; stubs assert Windows/macOS capability declarations. | |
| Platform coverage expectation in Phase 5? | Must run commands on both OSes now | Stronger proof, but may block if one machine/toolchain unavailable. | yes |
| Platform coverage expectation in Phase 5? | Current OS only + docs for other | Fastest, weaker gate before Phase 6/7. | |
| What evidence should stub commands emit? | Machine-readable smoke report | JSON/text report with input sequence, backend state snapshots, capabilities, haptic intents, pass/fail. | |
| What evidence should stub commands emit? | Console-only pass/fail | Faster, enough for developer use, weaker for later audit. | |
| What evidence should stub commands emit? | JUnit-style test output | Better CI fit, but platform command evidence less human-readable. | yes |

**User's choice:** platform stub harness; replay UDP fixtures; must run on both OSes; JUnit-style output.
**Notes:** No OS-visible virtual HID requirement yet, but both platform stubs must execute.

---

## Gamepad descriptor shape

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| V1 virtual controller shape? | One regular gamepad-like joystick | Buttons + axes in one device. Avoid custom gun HID; broad compatibility. | yes |
| V1 virtual controller shape? | Joystick + separate mouse-like aim | Better pointer feel, but violates regular gamepad gun direction. | |
| V1 virtual controller shape? | Two logical gamepads | Split gun/stick from motion aim; likely confusing for games/tools. | |
| Axes to expose in v1 descriptor contract? | Stick X/Y + aim X/Y | Four axes: physical stick and motion aim stay distinct. | yes |
| Axes to expose in v1 descriptor contract? | Aim replaces stick | Two axes only; simpler but loses physical joystick semantics. | |
| Axes to expose in v1 descriptor contract? | Stick X/Y + aim X/Y + raw yaw/pitch/roll | Richer, but too custom/noisy for normal gamepad shape. | |
| Buttons in v1 contract? | Trigger, reload, X/Y/A/B | Six semantic buttons only; physical stick remains axes. | yes |
| Buttons in v1 contract? | Trigger, reload, X/Y/A/B + stick direction buttons | Preserves BLE edge semantics, but duplicates stick axes. | |
| Buttons in v1 contract? | Gamepad labels only | Map trigger/reload into generic button indexes now; less clear. | |
| Trigger semantics? | Trigger as digital button | Matches verified iPega data; analog trigger can wait for evidence. | yes |
| Trigger semantics? | Trigger as both button + analog axis | More gamepad-like, but invents analog data. | |
| Trigger semantics? | Trigger as axis only | Poor fit for current binary trigger evidence. | |

**User's choice:** one regular gamepad-like joystick; stick X/Y and aim X/Y; trigger/reload/X/Y/A/B buttons; digital trigger.
**Notes:** Descriptor stays compatible and avoids inventing analog or custom raw-motion controls.

---

## Capability flags

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Capability model granularity? | Structured capability object | Lists supported buttons, axes, haptics, output reports, lifecycle, platform limits. | yes |
| Capability model granularity? | Bitmask only | Compact and stable, but weak diagnostics/readability. | |
| Capability model granularity? | Freeform diagnostics strings | Easy now, too loose for downstream planning/tests. | |
| Platform limits? | Explicit unsupported reasons | `supported=false`, `reason`, `platform`, `details`; useful for macOS output limits. | yes |
| Platform limits? | Boolean only | `haptics=false`; simpler but hides why. | |
| Platform limits? | Throw on unsupported | Forces callers to handle, noisy for normal capability display. | |
| Haptic capability levels? | None / phone pulse / output reports | Separates v1 phone haptic from future OS output-report support. | |
| Haptic capability levels? | Boolean haptics only | Simpler, but conflates phone fallback with real HID output. | |
| Haptic capability levels? | Detailed effect matrix | Strength/duration/pattern limits now; more than Phase 5 needs. | yes |
| Descriptor/capability consistency gate? | Required invariant tests | Capabilities must match descriptor contract: six buttons, four axes, digital trigger, declared haptic/output support. | yes |
| Descriptor/capability consistency gate? | Manual review only | Planner/devs inspect generated reports. | |
| Descriptor/capability consistency gate? | Runtime warnings | Allow mismatch but surface diagnostic warnings. | |

**User's choice:** structured capability object; explicit unsupported reasons; detailed haptic effect matrix; required invariant tests.
**Notes:** Capability reporting should be strong enough to guide both platform phases.

---

## Haptic/output boundary

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Phase 5 output behavior? | Simulate backend haptic intent | Backend accepts haptic/output intent and records it; Android phone command path remains existing control channel. | |
| Phase 5 output behavior? | Send real phone haptic command from smoke | Stronger integration, but needs live Android/paired session. | yes |
| Phase 5 output behavior? | Output capability only | No haptic action in harness; just declare capability. | |
| OS output report simulation? | Stub output report -> phone haptic | Platform stubs simulate a future HID output report and verify it routes to phone haptic command. | yes |
| OS output report simulation? | No output report simulation | Smoke sends haptic command directly through current control server. | |
| OS output report simulation? | Full report parser now | Parse HID output report bytes in Phase 5; may pull Phase 6/7 details forward. | |
| If no Android is paired during smoke? | Dual mode | Stub tests pass without Android using fake control session; optional live smoke proves real phone vibration. | |
| If no Android is paired during smoke? | Fail if Android absent | Strong proof, but Phase 5 blocks on hardware every time. | yes |
| If no Android is paired during smoke? | Skip haptics when absent | Fast, but weak output boundary. | |
| Result evidence? | Require ack status + timing | Capture command id, output source, Android ack/fail status, observed timestamp/duration budget. | |
| Result evidence? | Require phone observed manually | Human confirms vibration; stronger physical proof, slower. | yes |
| Result evidence? | Command sent enough | Avoids Android result dependency, too weak for this phase. | |

**User's choice:** real phone haptic command; stub output report routes to phone haptic; fail if Android absent; human confirms phone vibration.
**Notes:** Phase 5 haptic smoke is hardware/session-gated by user choice.

---

## the agent's Discretion

- Exact Kotlin package names and class names.
- Exact immutable state and capability object layout.
- Exact axis numeric ranges and fixture file format.
- Exact JUnit-style output schema and storage location.
- Exact stub command names and invocation details.

## Deferred Ideas

- Production Windows virtual HID implementation remains Phase 6.
- Production macOS virtual HID implementation remains Phase 7.
- Desktop profile mapping and editing remain Phase 8.
- Visualizer UI and latency dashboards remain Phase 9.
- Replay/diagnostic depth beyond smoke harness remains Phase 10.
- Physical gun motor rumble remains v2/deferred.
