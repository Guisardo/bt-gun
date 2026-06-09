# Phase 6: Windows Virtual Joystick Path - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-09T22:32:51Z
**Phase:** 6-Windows Virtual Joystick Path
**Areas discussed:** Driver strategy bar, Windows proof target, Stream cutover, Output haptic proof

---

## Driver Strategy Bar

| Question | Option | Selected |
|----------|--------|----------|
| What driver strategy bar should Phase 6 lock? | VHF-first staged | |
| What driver strategy bar should Phase 6 lock? | Prototype first | |
| What driver strategy bar should Phase 6 lock? | Full product now | yes |

**User's choice:** Full product now if self-signature is allowed or no payments are required for the project.
**Notes:** Locked as VHF/KMDF pass path with self-signed/test-signed package allowed for dev proof. Paid release signing is not required for Phase 6.

| Question | Option | Selected |
|----------|--------|----------|
| Allow Phase 6 to change Windows boot/test settings and reboot `192.168.1.100` if needed? | Yes, with warning | |
| Allow Phase 6 to change Windows boot/test settings and reboot `192.168.1.100` if needed? | Ask every time | yes |
| Allow Phase 6 to change Windows boot/test settings and reboot `192.168.1.100` if needed? | No reboot/settings change | |

**User's choice:** Ask every time.
**Notes:** Every boot/signing setting change or reboot needs explicit approval.

| Question | Option | Selected |
|----------|--------|----------|
| Should Phase 6 include toolchain setup on the Windows machine? | Yes, setup required | |
| Should Phase 6 include toolchain setup on the Windows machine? | Document only | |
| Should Phase 6 include toolchain setup on the Windows machine? | Use existing only | yes |

**User's choice:** Use existing only; GitHub Actions on remote `origin` may build platform-specific artifacts.
**Notes:** Do not install WDK/VS/MSBuild/Git on the Windows host in Phase 6.

| Question | Option | Selected |
|----------|--------|----------|
| How should CI self-signing work? | Ephemeral CI cert | |
| How should CI self-signing work? | Stored GitHub secret cert | yes |
| How should CI self-signing work? | Unsigned CI artifact | |

**User's choice:** Stored GitHub secret cert.
**Notes:** Persistent test-signing certificate lives in GitHub Actions secret. Private key material must not be committed.

---

## Windows Proof Target

| Question | Option | Selected |
|----------|--------|----------|
| What counts as Phase 6 Windows sees joystick? | Layered proof | yes |
| What counts as Phase 6 Windows sees joystick? | Headless only | |
| What counts as Phase 6 Windows sees joystick? | Visual only | |

**User's choice:** Layered proof.
**Notes:** CLI/PnP evidence, HID/game-controller enumeration, and visual proof are required.

| Question | Option | Selected |
|----------|--------|----------|
| How strict should input proof be once device appears? | State changes visible | |
| How strict should input proof be once device appears? | Enumerates only | |
| How strict should input proof be once device appears? | Live stream visible | yes |

**User's choice:** Live stream visible.
**Notes:** Real Android stream must move Windows-visible joystick controls for final pass.

| Question | Option | Selected |
|----------|--------|----------|
| How should visual proof be captured? | Codex/agent captures when possible | |
| How should visual proof be captured? | User confirms manually | |
| How should visual proof be captured? | Both required | yes |

**User's choice:** Both required.
**Notes:** Need agent-captured evidence and user manual confirmation.

| Question | Option | Selected |
|----------|--------|----------|
| Should Phase 6 require `joy.cpl` specifically, or allow another Windows controller surface? | Prefer `joy.cpl`, allow fallback | |
| Should Phase 6 require `joy.cpl` specifically, or allow another Windows controller surface? | `joy.cpl` mandatory | yes |
| Should Phase 6 require `joy.cpl` specifically, or allow another Windows controller surface? | Any OS-visible proof | |

**User's choice:** `joy.cpl` mandatory.
**Notes:** Windows Game Controllers must show the device for Phase 6 to pass.

---

## Stream Cutover

| Question | Option | Selected |
|----------|--------|----------|
| Where should live Android stream enter Windows path? | User-mode companion -> driver IOCTL | yes |
| Where should live Android stream enter Windows path? | Driver receives network directly | |
| Where should live Android stream enter Windows path? | Separate native bridge | |

**User's choice:** User-mode companion -> driver IOCTL.
**Notes:** Companion owns LAN/control/session; driver stays HID/report-only.

| Question | Option | Selected |
|----------|--------|----------|
| For final Phase 6 pass, what must feed the driver? | Live Android only | yes |
| For final Phase 6 pass, what must feed the driver? | Replay acceptable | |
| For final Phase 6 pass, what must feed the driver? | Either live or replay | |

**User's choice:** Live Android only.
**Notes:** Replay/fake state is only for tests and debug.

| Question | Option | Selected |
|----------|--------|----------|
| How should Phase 6 handle profile mapping before Phase 8 exists? | Fixed default mapping only | yes |
| How should Phase 6 handle profile mapping before Phase 8 exists? | Minimal config file | |
| How should Phase 6 handle profile mapping before Phase 8 exists? | Start Phase 8 early | |

**User's choice:** Fixed default mapping only.
**Notes:** No profile UI/config work in Phase 6.

| Question | Option | Selected |
|----------|--------|----------|
| What should happen if live stream becomes stale while Windows joystick is active? | Clear buttons, mark stale, keep last aim | yes |
| What should happen if live stream becomes stale while Windows joystick is active? | Neutral everything | |
| What should happen if live stream becomes stale while Windows joystick is active? | Keep last full state | |

**User's choice:** Clear buttons, mark stale, keep last aim.
**Notes:** Preserve Phase 4 stale-stream behavior.

---

## Output Haptic Proof

| Question | Option | Selected |
|----------|--------|----------|
| What output path must Phase 6 prove? | Real HID output report -> phone | yes |
| What output path must Phase 6 prove? | Companion simulated output only | |
| What output path must Phase 6 prove? | Force-feedback API proof | |

**User's choice:** Real HID output report -> phone.
**Notes:** Simulated output report is not enough for final pass.

| Question | Option | Selected |
|----------|--------|----------|
| Who should initiate the output report for proof? | Small Windows test tool | |
| Who should initiate the output report for proof? | `joy.cpl` only | yes |
| Who should initiate the output report for proof? | External game/app | |

**User's choice:** `joy.cpl` only.
**Notes:** The first proof attempt must be through Windows Game Controllers.

| Question | Option | Selected |
|----------|--------|----------|
| If `joy.cpl` cannot send any output/rumble report for the virtual joystick, what should Phase 6 do? | Block and ask you | |
| If `joy.cpl` cannot send any output/rumble report for the virtual joystick, what should Phase 6 do? | Fallback to test tool | yes |
| If `joy.cpl` cannot send any output/rumble report for the virtual joystick, what should Phase 6 do? | Drop output final proof | |

**User's choice:** Fallback to test tool.
**Notes:** Fallback allowed only after documenting the `joy.cpl` limitation.

| Question | Option | Selected |
|----------|--------|----------|
| What physical confirmation is required for phone haptic output? | User confirms phone vibrated | |
| What physical confirmation is required for phone haptic output? | Ack/result only | |
| What physical confirmation is required for phone haptic output? | Both phone + Windows evidence | yes |

**User's choice:** Both phone + Windows evidence.
**Notes:** Need user phone-vibration confirmation and agent-captured Windows output-report evidence.

---

## the agent's Discretion

- Exact driver project shape, HID descriptor bytes, INF details, IOCTL contract, GitHub Actions workflow, and evidence commands are planner discretion within the locked decisions.

## Deferred Ideas

- Production release signing and paid signing setup.
- macOS virtual joystick path.
- Profile editing and aim tuning.
- Visualizer UI and latency dashboard.
- Physical gun motor rumble.
