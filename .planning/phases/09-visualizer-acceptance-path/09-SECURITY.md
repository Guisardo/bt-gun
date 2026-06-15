---
phase: 09
slug: visualizer-acceptance-path
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-15
---

# Phase 09 - Security

Per-phase security contract for the visualizer acceptance path.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Accepted desktop transport to visualizer model | `ControlServer` emits only accepted UDP/control/status/haptic callbacks into the visualizer event hub. | Accepted `UdpReceivedInput`, diagnostics, profile metadata, haptic results. |
| Visualizer UI to authenticated control channel | The visualizer can request a phone haptic test only through the existing authenticated control server path. | `HapticCommand`, command id, duration/strength/TTL, ack/fail result. |
| Android foreground session to desktop diagnostics | Android publishes visualizer status through nested authenticated diagnostics only after trusted desktop connection state exists. | Raw-debug enabled flag, aim-zero/recenter labels, elapsed timestamps, sequence. |
| Backend proof diagnostics to checklist | Windows/macOS backend status can mark rows observed, but user confirmation remains separate from observed runtime state. | Backend lifecycle, publish result, haptic routed count, limitation evidence. |
| Manual proof guide to phase sign-off | Human proof instructions must not become raw evidence dumps or leak local identifiers/secrets. | Checklist row ids, operator actions, limitation acknowledgement. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-09-01 | Tampering | `DesktopUiEventHub` | mitigate | Listener fanout preserves prior callbacks and restores them on close; tests prove previous and new listeners receive trusted events (`DesktopUiEventHub.kt:58`, `:89`, `:150`; `DesktopUiEventHubTest.kt:35-43`, `:137-144`). | closed |
| T-09-02 | Spoofing | `VisualizerModel` checklist rows | mitigate | Observed rows are separate from confirmed rows; pass requires explicit confirmation or accepted limitation (`VisualizerModel.kt:54-56`, `:369-400`, `:418-445`; `VisualizerModelTest.kt:77-89`, `:400-418`). | closed |
| T-09-03 | Tampering | `VisualizerMetrics` packet loss | mitigate | Packet loss records only accepted `UdpReceivedInput` sequence gaps and resets on control/stream session changes (`VisualizerMetrics.kt:67-99`, `:152-168`; `VisualizerMetricsTest.kt:49-58`, `:130-144`). | closed |
| T-09-04 | Information Disclosure | model labels/diagnostics | mitigate | Raw debug starts collapsed, exposes only whitelisted fields, and redacts forbidden terms (`VisualizerModel.kt:86`, `:140-148`; `VisualizerPanels.kt:121-129`, `:279-304`; `VisualizerModelTest.kt:272-301`, `:423-442`). | closed |
| T-09-05 | Spoofing | auto-open session gate | mitigate | Coordinator auto-opens the visualizer only when session state is `AUTHENTICATED`, not from pairing material alone (`VisualizerWindow.kt:392-405`; `VisualizerWindowTest.kt:419-440`). | closed |
| T-09-06 | Denial of Service | visualizer window lifecycle | mitigate | Factory reuses the existing visualizer handle on repeated opens; tests cover reuse (`VisualizerWindow.kt:371-379`; `VisualizerWindowTest.kt:399-415`). | closed |
| T-09-07 | Elevation of Privilege | `PairingWindow` reopen button | mitigate | Reopen button invokes only the injected visualizer opener and does not run haptic/session/profile actions (`PairingWindow.kt:415-420`; `PairingWindowTest.kt:171-180`, `:203-212`). | closed |
| T-09-08 | Information Disclosure | visualizer shell labels | mitigate | Empty/disconnected UI uses generic copy and tests reject secret/profile/editor labels from the visualizer source (`VisualizerWindow.kt:246-248`, `:329-332`; `VisualizerWindowTest.kt:379-393`). | closed |
| T-09-09 | Tampering | haptic action | mitigate | Haptic button is enabled only for authenticated control sessions and sends through `ControlServer.sendHapticCommand`, which rejects missing sessions/invalid envelopes (`VisualizerWindow.kt:152`, `:165`, `:267-278`; `ControlServer.kt:346-376`; `HapticCommand.kt:20-22`). | closed |
| T-09-10 | Information Disclosure | raw-debug drawer | mitigate | Raw debug renders provider/yaw/pitch/roll/raw aim/rejection only when enabled, with redaction and length limits (`VisualizerPanels.kt:121-129`, `:279-304`; `VisualizerModelTest.kt:272-301`). | closed |
| T-09-11 | Spoofing | haptic checklist row | mitigate | `STARTED` ack marks LAN phone haptic observed but still requires user confirmation before pass (`VisualizerModel.kt:254-265`; `VisualizerModelTest.kt:356-372`). | closed |
| T-09-12 | Denial of Service | Swing rendering | mitigate | Visualizer updates use EDT scheduling and stable component dimensions; tests guard against blocking sleep/network I/O in the window (`VisualizerWindow.kt:115`, `:133-138`, `:219-228`; `VisualizerPanels.kt:146-147`, `:202-207`, `:233-248`; `VisualizerWindowTest.kt:148-149`). | closed |
| T-09-13 | Information Disclosure | `VisualizerStatus` JSON | mitigate | Android status body whitelists stable fields, sanitizes state/labels, omits invalid negative optional values, and tests reject forbidden material (`VisualizerStatus.kt:21-30`, `:51-56`; `VisualizerStatusTest.kt:51-74`, `:78-98`). | closed |
| T-09-14 | Spoofing | status diagnostics | mitigate | Android sends visualizer status only through existing `DIAGNOSTICS` envelopes after a trusted session id exists; no new unauthenticated broadcast is added (`DesktopControlClient.kt:317-319`, `:518-526`; `DesktopControlClientTest.kt:640-672`). | closed |
| T-09-15 | Tampering | recenter status | mitigate | Recenter status derives from `ReloadHoldRecenter`, service aim baseline, and local state rather than desktop fields (`HostSessionService.kt:450-464`, `:1554-1588`; `HostSessionServiceLivenessTest.kt:650-678`, `:697-705`). | closed |
| T-09-16 | Denial of Service | status sends | mitigate | Status publishing requires trusted desktop connection plus meaningful change and returns early otherwise; no blocking UI/network loop is introduced (`HostSessionService.kt:415-420`, `:1591-1602`; `HostSessionServiceLivenessTest.kt:681-692`). | closed |
| T-09-17 | Spoofing | `ControlServer.onVisualizerStatusReceived` | mitigate | Desktop invokes the visualizer status callback only from accepted diagnostics envelope handling, while preserving generic accepted-envelope callback (`ControlServer.kt:259-283`; `ControlChannelTest.kt:204-238`). | closed |
| T-09-18 | Tampering | status parser | mitigate | Desktop parser rejects unknown fields, non-Android source, malformed required fields, and negative elapsed values (`VisualizerStatus.kt:24-40`, `:66-70`; `ControlChannelTest.kt:291-352`). | closed |
| T-09-19 | Repudiation | recenter checklist row | mitigate | Row source/age metadata is stored separately from user confirmation; recenter observed state is not auto-confirmed (`VisualizerModel.kt:54-56`, `:199-230`, `:369-383`; `VisualizerModelTest.kt:317-331`). | closed |
| T-09-20 | Information Disclosure | status UI | mitigate | Status UI uses sanitized parsed fields and forbidden-field tests reject secret/log/device material (`VisualizerStatus.kt:24-40`, `:100`; `ControlChannelTest.kt:369-372`; `VisualizerWindowTest.kt:337-344`). | closed |
| T-09-21 | Spoofing | final checklist summary | mitigate | All required rows, including macOS input, Windows input, LAN haptic, Windows haptic, and macOS limitation, must be accepted before `PASSING` (`VisualizerModel.kt:37-44`, `:418-445`; `VisualizerModelTest.kt:77-89`, `:93-132`, `:423-439`). | closed |
| T-09-22 | Repudiation | manual confirmations | mitigate | UI exposes explicit confirm observed, confirm limitation, and reset actions; manual guide records exact row actions for final approval (`VisualizerWindow.kt:258-265`; `VisualizerModel.kt:369-417`; `09-MANUAL-PROOF.md:18-29`). | closed |
| T-09-23 | Information Disclosure | manual proof guide | mitigate | Manual guide is the pass artifact and tests/guards reject raw logs, screenshots, device serials, secrets, and generated-bundle-primary scope (`09-MANUAL-PROOF.md:3`, `:18-29`; `VisualizerWindowTest.kt:349-370`; `09-06-SUMMARY.md:143-145`). | closed |
| T-09-24 | Tampering | macOS haptic limitation row | mitigate | macOS HID haptic can pass only as `unsupported/deferred`; normal confirmed state is not accepted for that limitation row (`VisualizerModel.kt:351-358`, `:393-400`, `:442-445`; `VisualizerModelTest.kt:204-225`; `VisualizerWindowTest.kt:324-335`). | closed |
| T-09-SC | Supply Chain | package installs | accept | Phase 9 added no package-manager dependency installs; work stayed in Swing/Android/platform code and planning docs (`09-01-SUMMARY.md:120-122`; `09-02-SUMMARY.md:117-119`; `09-03-SUMMARY.md:119-121`; `09-04-SUMMARY.md:105-107`; `09-05-SUMMARY.md:133-135`; `09-06-SUMMARY.md:123`, `:143-145`). | closed |

## Summary Threat Flags

No unregistered threat flags. Each Phase 9 summary reports no new network endpoint, unauthenticated path, file access pattern, schema/HID/haptic path, raw log dump, secret-bearing display, or package dependency beyond the plan-time register (`09-01-SUMMARY.md:120-122`, `09-02-SUMMARY.md:117-119`, `09-03-SUMMARY.md:119-121`, `09-04-SUMMARY.md:105-107`, `09-05-SUMMARY.md:133-135`, `09-06-SUMMARY.md:123`).

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-09-SC | T-09-SC | Phase 9 did not add package-manager dependencies or run npm/pip/cargo/Gradle dependency installs; future dependency changes require a new threat review. | Plan-time threat model / Codex secure-phase | 2026-06-15 |

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-15 | 25 | 25 | 0 | Codex secure-phase |

## Sign-Off

- [x] All threats have a disposition.
- [x] Accepted risks documented.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

**Approval:** verified 2026-06-15
