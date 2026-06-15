# Replay and Troubleshooting

Use this page to route failures through the v1 diagnostic buckets, replay committed fixture data, and create safe diagnostic exports. Keep raw local captures outside git.

## Diagnostic Buckets

| Bucket | Likely owner | Primary symptoms | First artifact |
|--------|--------------|------------------|----------------|
| `gun_ble` | Android gun adapter | gun not connected, control missing, unknown payload, BLE permission blocked | Android dashboard diagnostics, Phase 2 evidence capture |
| `sensor_motion` | Android motion/recenter path | no aim, unstable aim, recenter missing, wrong provider | Android dashboard diagnostics, visualizer recenter/aim rows |
| `lan_control_udp` | pairing/control/UDP transport | pairing rejected, heartbeat stale, UDP rejected, replay/stale drop | desktop diagnostics, replay fixture test, `fixtures/replay` |
| `profile_mapping` | Android profile mapper | wrong axis, button mapping rejected, raw-debug mismatch | Android profile diagnostics, desktop read-only profile metadata |
| `hid_backend_haptics` | Android HID / Windows VHF / haptic bridge | OS-visible input absent, output unsupported, phone haptic failed | Phase 7/Phase 6 manifests, visualizer haptic row |

Statuses are fixed: `ok`, `degraded`, `blocked`, `unsupported`, `unknown`. Use reason codes for details. Do not add free-form status values.

## Replay Workflow

Run the desktop replay slice when a packet, mapping, visualizer, latency, or packet-loss failure is suspected:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion test --tests '*ReplayFixture*' --tests '*UdpControllerStateAdapter*' --tests '*VisualizerMetrics*' --tests '*VisualizerModel*' --no-daemon --console=plain
```

Replay source files:

- `fixtures/replay/udp-golden/mapped-session-001.hex`
- `fixtures/replay/udp-golden/mapped-session-001.jsonl`
- `fixtures/replay/expected/mapped-session-001-visualizer.json`
- `docs/evidence/manifests/phase10-replay-fixtures.jsonl`

Expected path:

```text
UDP hex
  -> UdpInputFrameCodec authentication/decode
  -> UdpInputReceiver stale/replay guard
  -> UdpControllerStateAdapter mapped product state
  -> VisualizerModel checklist rows
  -> VisualizerMetrics latency/loss output
```

If replay passes but live input fails, investigate `gun_ble`, `sensor_motion`, or live `lan_control_udp` state. If replay fails, treat it as parser/mapping/visualizer regression before retesting hardware.

## DiagnosticExport Bundle

Use `DiagnosticExport` when a run needs a shareable issue bundle. The v1 writer persists:

| File | Contents |
|------|----------|
| `diagnostics.jsonl` | sanitized diagnostic events |
| `manifest.json` | app/build versions, capability statuses, replay refs, manifest pointer, raw-included flag |

Default export behavior excludes raw local captures. Bundles should reference replay fixtures and sanitized manifests instead of copying capture dumps.

## Redaction Rules

Before committing any troubleshooting artifact, run the Phase 10 docs/source guard or the equivalent forbidden-pattern scan for secret or full identifier drift.

Allowed committed data:

- sanitized capture ids
- short suffix refs
- diagnostic domains, statuses, and reason codes
- fixture paths
- replay refs
- app/build versions without local account/device identity

Do not commit pairing one-time values, proof material, UDP auth material, cryptographic signing material, full Bluetooth-style addresses, full phone identifiers, screen captures, or capture dumps.

## Failure Routing

| Symptom | Bucket | First check | Next action |
|---------|--------|-------------|-------------|
| iPega gun absent | `gun_ble` | Android permission and BLE connection state | Reconnect gun, then capture sanitized Android diagnostics. |
| Gun controls stuck or unknown | `gun_ble` | parser/debug detail for unknown payload | Compare against Phase 1 normalized fixtures; add evidence only after hardware capture. |
| Aim frozen | `sensor_motion` | provider state and sensor permission | Verify provider fallback and recenter state before changing profile code. |
| Recenter missing | `sensor_motion` | reload-hold elapsed state | Confirm normal reload down/up still appears and two-second hold emits recenter. |
| Pairing rejected | `lan_control_udp` | expiry, identity, nonce, proof, rate limit | Start a new desktop pairing session; do not reuse old material. |
| UDP rejected | `lan_control_udp` | stream id, tag, sequence, frame age | Run replay tests; if replay passes, inspect live config/session lifecycle. |
| Packet stream stale | `lan_control_udp` | heartbeat/control grace and timeout | Reconnect trusted control so desktop issues fresh stream config. |
| Buttons/axes wrong | `profile_mapping` | active Android profile metadata | Check Android profile validation and mapped product stream flags. |
| Raw debug absent | `profile_mapping` | Android raw-debug toggle | Enable from Android only; desktop cannot request raw extras. |
| macOS input visible but haptic absent | `hid_backend_haptics` | Phase 7 haptic limitation row | Use LAN or Windows VHF phone-haptic path for v1 feedback. |
| Windows VHF input absent | `hid_backend_haptics` | Phase 6 install/proof docs and bridge status | Validate installed package and `joy.cpl` visibility on Windows target. |
| Phone haptic failed | `hid_backend_haptics` | command validation, TTL, permission, active Android session | Retry from authenticated visualizer/LAN path with safe command shape. |

## Evidence Handling

Use `.evidence/phase10/` for local raw sources. Commit only:

- docs updates
- sanitized JSONL manifest rows
- replay fixture files under `fixtures/replay/`
- `DiagnosticExport` output after redaction scan

If a live proof is manual-only, record the result as sanitized manifest/status text and preserve the requirement that user confirmation remains the acceptance signal.
