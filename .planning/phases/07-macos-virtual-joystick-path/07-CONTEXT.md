# Phase 7: macOS Virtual Joystick Path - Context

**Gathered:** 2026-06-10T12:22:36Z
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 7 builds and proves the macOS Apple Silicon virtual joystick path. macOS must see the live Android gun stream as a regular gamepad-style joystick, and macOS-origin output/rumble must map back to Android phone haptics.

This phase does not build Phase 8 profile editing, sensitivity/inversion/dead-zone/smoothing controls, Phase 9 visualizer UI, game-specific presets, direct desktop-to-gun Bluetooth, physical gun motor rumble, or a production notarized installer.

</domain>

<decisions>
## Implementation Decisions

### macOS HID Path
- **D-01:** Phase 7 targets CoreHID `HIDVirtualDevice` first for the macOS virtual joystick proof.
- **D-02:** CoreHID fallback is research-gated. Switch away from CoreHID only if official documentation or local compile/runtime proof shows CoreHID cannot create an OS-visible gamepad-style device that satisfies Phase 7.
- **D-03:** If CoreHID cannot receive macOS-origin output/rumble reports, HIDDriverKit/system extension becomes the mandatory fallback path for Phase 7 output proof.

### macOS Proof Target
- **D-04:** Phase 7 pass condition is layered proof: CLI HID enumeration, macOS-visible game controller/gamepad UI or tester evidence, and agent/user visual confirmation.
- **D-05:** Final input proof must use a live paired Android/gun stream moving macOS-visible joystick axes/buttons. Replay fixtures are allowed for automated tests, CI, and debugging only.
- **D-06:** Final visual proof requires both agent-captured evidence and user confirmation that the virtual device/input is visible in a Mac UI or tester.

### Output Haptic Proof
- **D-07:** Phase 7 cannot pass unless macOS OS-origin output/rumble maps to Android phone haptic.
- **D-08:** The existing desktop companion phone-haptic command path remains the v1 haptic transport, but it is not enough by itself unless it is triggered by macOS-origin output/rumble.
- **D-09:** If CoreHID cannot expose the required output/rumble path, planners must include HIDDriverKit fallback work rather than documenting output as unsupported.

### Packaging and Development Setup
- **D-10:** Phase 7 targets a development proof package: local macOS app/tool with documented launch, signing, permission, and fallback steps.
- **D-11:** The proof target is the current development Mac unless planning discovers a blocker: macOS 26.2 build 25C56 on arm64.
- **D-12:** Use ad-hoc/local development signing first where CoreHID permits. Do not require Developer ID signing for Phase 7 unless the selected fallback path requires it.
- **D-13:** Document exact commands, permission prompts, minimum observed OS requirement, and DriverKit entitlement fallback notes.

### Live Stream Cutover
- **D-14:** The macOS runtime mirrors the Windows runtime shape: `MacosBackendRuntime` attaches to `ControlServer.onUdpInputReceived`, preserves any previous callback, maps `UdpReceivedInput` to `SemanticControllerState`, publishes to the macOS backend, and exposes diagnostics.
- **D-15:** Stale-stream behavior must match Phase 4 and Windows: clear active buttons, keep last aim axes, and expose stale diagnostic state.
- **D-16:** A native Swift or Objective-C helper is allowed if needed for CoreHID. The Kotlin/JVM desktop companion must keep ownership of LAN pairing, session security, UDP input validation, semantic state mapping, and phone-haptic routing.

### the agent's Discretion
- Choose exact CoreHID report descriptor bytes, vendor/product ids, report ids, helper process protocol, Swift/Objective-C project layout, local IPC mechanism, CLI enumeration commands, and macOS tester/tooling, as long as the decisions above hold.
- Choose exact DriverKit fallback design during planning if CoreHID cannot satisfy OS-visible device or OS-origin output proof.
- Choose exact evidence artifact names and redaction format, provided no pairing material, session secrets, keys, or private signing material are committed.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` - Phase 7 goal, dependency, `DESK-03`, `DESK-06`, `PACK-03`, and success criteria.
- `.planning/REQUIREMENTS.md` - macOS virtual joystick, output-to-phone-haptic, smoke, profile, visualizer, and packaging boundaries.
- `.planning/PROJECT.md` - v1 Windows/macOS constraints, regular gamepad-style HID shape, desktop-owned mapping, and v1 phone-haptic decision.
- `.planning/STATE.md` - current state after Phase 6 and carried macOS entitlement/output concern.

### Research Context
- `.planning/research/STACK.md` - macOS CoreHID/HIDDriverKit direction, entitlement risk, and virtual joystick sequencing.
- `.planning/research/ARCHITECTURE.md` - virtual HID backend boundary, desktop companion split, profile mapper boundary, and haptic return flow.
- `.planning/research/PITFALLS.md` - platform driver packaging/signing risk and warning against prototype-only virtual controllers.
- `.planning/research/FEATURES.md` - macOS virtual gamepad priority and regular gamepad/joystick descriptor decision.

### Prior Phase Context
- `.planning/phases/06-windows-virtual-joystick-path/06-CONTEXT.md` - Windows runtime split, live proof bar, output haptic proof, default mapping, and driver boundary.
- `.planning/phases/05-desktop-backend-contract-and-smoke-harness/05-CONTEXT.md` - backend contract, descriptor, capabilities, UDP handoff, smoke harness, and haptic boundary.
- `.planning/phases/04-input-stream-and-haptic-transport/04-CONTEXT.md` - UDP receiver, stale-stream handling, haptic command/result transport, and raw-motion-only input.

### Desktop Backend Code
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerBackend.kt` - backend lifecycle/publish/output surface that macOS adapter must satisfy.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/BackendCapabilities.kt` - capability and limitation model to update from macOS stub to real macOS backend.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/VirtualControllerDescriptor.kt` - locked v1 gamepad-like joystick descriptor contract.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/SemanticControllerState.kt` - semantic state fields to publish into macOS reports.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/UdpControllerStateAdapter.kt` - current UDP-to-semantic mapping and stale-state behavior.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/StubVirtualControllerBackend.kt` - Phase 5 macOS stub reference only; not sufficient for Phase 7 pass.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsVirtualControllerBackend.kt` - real backend pattern for lifecycle, report publishing, and output-to-haptic mapping.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/backend/windows/WindowsBackendRuntime.kt` - runtime attachment pattern that macOS should mirror.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/MacosBackendSmokeMain.kt` - existing macOS stub smoke entrypoint to evolve or supersede.
- `desktop-companion/src/main/kotlin/com/btgun/desktop/smoke/BackendSmokeRunner.kt` - replay smoke reference for tests/debug, not final pass.
- `desktop-companion/build.gradle.kts` - current desktop companion test and smoke task wiring.

### Platform Reference Docs
- Apple CoreHID `HIDVirtualDevice` documentation - refresh during research; use as primary CoreHID source.
- Apple HIDDriverKit and DriverKit entitlement documentation - refresh during research; use for mandatory fallback if CoreHID cannot satisfy output proof.
- Apple IOKit `IOHIDUserDeviceCreateWithProperties` documentation - refresh during research as a legacy/alternate user-space virtual HID reference if CoreHID is blocked.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `VirtualControllerBackend` already defines lifecycle, semantic state publish, capabilities, current state, last publish result, and simulated output surface.
- `btGunV1Descriptor` locks the v1 regular gamepad-like joystick: trigger/reload/X/Y/A/B, stickX/stickY/aimX/aimY, digital trigger.
- `SemanticControllerState` is platform-neutral and should feed the macOS HID report path.
- `UdpControllerStateAdapter` already maps trusted UDP receiver output into semantic state and handles NaN aim values.
- `WindowsVirtualControllerBackend` and `WindowsBackendRuntime` provide the closest implementation pattern for real backend lifecycle, runtime attachment, and OS-output-to-phone-haptic routing.
- `MacosBackendSmokeMain` and `BackendSmokeRunner` provide the current macOS stub smoke baseline but cannot satisfy Phase 7's real OS-visible proof bar.

### Established Patterns
- Desktop companion is Kotlin/JVM with plain Kotlin test/smoke entrypoints and Swing UI.
- LAN pairing, session auth, UDP input validation, profile metadata, and phone haptics stay in user-mode companion code.
- Platform-specific backends publish from semantic controller state and must not own LAN networking, pairing, authentication, profile mapping, or Android session lifecycle.
- Replay fixtures and stubs are allowed as development/test harnesses, but final platform phases require live device proof when the phase says so.
- Existing docs and evidence avoid committing secrets, pairing material, stream authentication material, private keys, or raw sensitive logs.

### Integration Points
- Add a real macOS backend beside the existing backend package, preserving the Phase 5 `VirtualControllerBackend` contract.
- Add `MacosBackendRuntime` beside `WindowsBackendRuntime`, preserving the `ControlServer.onUdpInputReceived` callback chain.
- Add a native macOS helper only if CoreHID access is not practical from Kotlin/JVM; keep helper protocol local and semantic/report oriented.
- Add macOS smoke/proof commands that distinguish replay smoke from live Android/gun proof.
- Update capabilities so the real macOS backend reports OS-visible-device and output-report support only when proven, with explicit limitations otherwise.
- Add macOS setup/proof documentation for CoreHID launch/signing/permissions, OS visibility, live input, OS-output-to-phone-haptic evidence, and DriverKit fallback requirements.

</code_context>

<specifics>
## Specific Ideas

- CoreHID is preferred for speed of proof, but output/rumble support is a hard gate. If CoreHID cannot receive output reports, DriverKit fallback is required in Phase 7.
- The final macOS proof should be as strict as the Windows proof: live Android/gun stream, normal OS-visible controller surface, and user confirmation.
- The local proof target during discussion was macOS 26.2 build 25C56 on arm64.
- Discussion-time external research found Apple docs for CoreHID `HIDVirtualDevice`, HIDDriverKit, DriverKit entitlements, and IOKit `IOHIDUserDeviceCreateWithProperties`; downstream research must refresh official Apple docs before planning.

</specifics>

<deferred>
## Deferred Ideas

- Production notarized installer, release signing/distribution polish, and paid Developer ID flow are deferred beyond Phase 7 unless required by the selected fallback proof path.
- Profile storage, configurable aim mapping, sensitivity, inversion, dead zone, smoothing, and remapping remain Phase 8.
- Visualizer UI, latency dashboard, packet-loss dashboard, and recenter display remain Phase 9.
- Replay diagnostics beyond platform smoke remain Phase 10.
- Physical gun motor rumble remains v2/deferred.

</deferred>

---

*Phase: 7-macOS Virtual Joystick Path*
*Context gathered: 2026-06-10T12:22:36Z*
