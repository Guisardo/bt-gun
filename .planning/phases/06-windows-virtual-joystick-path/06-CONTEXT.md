# Phase 6: Windows Virtual Joystick Path - Context

**Gathered:** 2026-06-09T22:32:51Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 6 builds and proves the Windows 11 x64 virtual joystick path. Windows must see the live Android gun stream as a regular gamepad-style joystick through the real Windows VHF/KMDF path, and Windows-origin HID output must map back to Android phone haptics.

This phase does not build the macOS virtual joystick path, Phase 8 profile editing, visualizer UI, game-specific profiles, direct desktop-to-gun Bluetooth, physical gun motor rumble, or a production release-signing/distribution program.

</domain>

<decisions>
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 6 goal, dependency on Phase 5, `DESK-02`, `DESK-05`, `PACK-02`, and success criteria.
- `.planning/REQUIREMENTS.md` - Windows virtual joystick, output-to-phone-haptic, smoke, profile, visualizer, and packaging boundaries.
- `.planning/PROJECT.md` - v1 Windows/macOS constraints, gamepad-style HID shape, desktop-owned mapping, and v1 phone-haptic decision.
- `.planning/STATE.md` - current state after Phase 5 and accumulated desktop backend decisions.

### Research Context
- `.planning/research/STACK.md` - Windows KMDF/VHF product path, WDK/signing risks, and vJoy/ViGEm prototype-only guidance.
- `.planning/research/ARCHITECTURE.md` - virtual HID backend boundary, user-mode desktop companion split, and haptic return flow.
- `.planning/research/PITFALLS.md` - driver packaging/signing risk and warning against treating prototype virtual controllers as product core.
- `.planning/research/FEATURES.md` - Windows virtual gamepad priority and regular gamepad/joystick descriptor decision.

### Prior Phase Context
- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md` - Phase 5 backend contract, descriptor, capabilities, UDP handoff, smoke harness, and haptic boundary.
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - UDP receiver, stale-stream handling, haptic command/result transport, and raw-motion-only input.
- `.planning/phases/03-lan-pairing-and-secure-session/03-CONTEXT.md` - desktop control channel, trusted session, heartbeat, and pairing/session ownership.

### Desktop Backend Code
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt` - backend lifecycle/publish/output surface that Windows adapter must satisfy.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` - locked v1 gamepad-like joystick descriptor contract.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` - semantic state fields to map into Windows HID reports.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - capability and limitation model to update from Windows stub to real Windows backend.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` - current UDP-to-semantic mapping and stale-state behavior.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt` - Phase 5 stub reference only; not sufficient for Phase 6 pass.

### Desktop Transport and Haptics
- `desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt` - authenticated session, UDP input callbacks, and phone haptic command send path.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpInputReceiver.kt` - trusted UDP receiver lifecycle and stale/replay behavior.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/transport/UdpReceivedInput.kt` - receiver output consumed by semantic mapping.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/haptics/HapticCommand.kt` - phone haptic command/result model.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/WindowsBackendSmokeMain.kt` - existing Windows stub smoke entrypoint to evolve or supersede.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` - replay smoke reference for tests/debug, not final pass.

### Protocol and Evidence
- `docs/protocol/lan-pairing-v1.md` - reliable control haptic command/result contract and session boundaries.
- `docs/protocol/input-stream-v1-fixtures.md` - replay fixture contract for automated tests/debug.
- `docs/evidence/manifests/phase5-desktop-backend-smoke.jsonl` - Phase 5 stub smoke and haptic baseline evidence.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `VirtualControllerBackend` already defines lifecycle, semantic state publish, capabilities, and simulated output surface.
- `btGunV1Descriptor` locks a regular gamepad-like joystick: trigger/reload/X/Y/A/B, stickX/stickY/aimX/aimY, digital trigger.
- `SemanticControllerState` is already platform-neutral and should feed the Windows HID report bridge.
- `UdpControllerStateAdapter` already maps trusted UDP receiver output into Phase 5 semantic state and handles NaN aim values.
- `ControlServer` and `HapticCommand` already provide the authenticated desktop-to-Android phone haptic path.

### Established Patterns
- Desktop companion is Kotlin/JVM with plain Kotlin test/smoke entrypoints and Swing UI.
- LAN pairing, session auth, UDP input, profile metadata, and phone haptics stay in user-mode companion code.
- Earlier phases avoid secret material in docs, logs, evidence manifests, and committed artifacts.
- Replay fixtures and stubs are allowed as development/test harnesses but do not satisfy physical/live proof gates when the phase requires real-device evidence.

### Integration Points
- Add a real Windows backend beside the existing backend package, preserving the Phase 5 contract.
- Add a Windows user-mode bridge from companion semantic state to VHF driver reports.
- Add GitHub Actions Windows build/sign workflow and artifact publishing for the driver package.
- Add Windows install/proof documentation for test certificate import, test-signing state, driver install, `joy.cpl`, input movement, and HID output-to-phone haptic evidence.
- Update capabilities so Windows reports real OS-visible-device/output-report support when the real backend is active, with explicit limitations otherwise.

</code_context>

<specifics>
## Specific Ideas

- The Windows target machine may be used over SSH for command-line evidence and as a GUI proof target when visual access is available.
- `joy.cpl` is intentionally strict: it is mandatory for final Windows visibility proof even if CLI/HID evidence also passes.
- Live Android/gun input is mandatory for final Phase 6 input proof; replay/fake paths are only scaffolding.
- `joy.cpl` should be attempted first for output/haptic initiation, but a small HID output sender is acceptable only after documenting that `joy.cpl` cannot send output for this descriptor.
- Discussion-time external research referenced Microsoft VHF, test-signing, and HID output-report docs; downstream research should refresh official docs rather than relying on this context alone.

</specifics>

<deferred>
## Deferred Ideas

- Production release signing, paid developer program setup, attestation signing, EV certificate handling, and installer polish are deferred beyond Phase 6 unless later planning explicitly pulls them in.
- macOS virtual joystick path remains Phase 7.
- Profile storage, mapping UI, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Physical gun motor rumble remains v2/deferred.

</deferred>

---

*Phase: 6-Windows Virtual Joystick Path*
*Context gathered: 2026-06-09T22:32:51Z*
