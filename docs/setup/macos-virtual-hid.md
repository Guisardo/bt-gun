# macOS Virtual HID Blocked/Fallback Notes

This is retained Phase 7 CoreHID/DriverKit evidence, not the product macOS path. The product route is Android Bluetooth HID gamepad; CoreHID and DriverKit remain blocked/scaffold unless a later entitlement-capable proof records OS-visible input, output behavior, rollback, and explicit planning approval.

No post-HID-subclass macOS proof is recorded here. Any later Android HID descriptor/subclass change needs a fresh macOS pairing/GameController proof before refreshed compatibility claims.

## Legacy Virtual-HID Decisions

- D-01: Tried CoreHID `HIDVirtualDevice` first.
- D-02: Switched away from CoreHID after local runtime proof showed the helper could not satisfy the OS-visible gamepad-style joystick path on normal macOS.
- D-03: HIDDriverKit/system extension remains fallback scaffold only; it is not a product support claim.
- D-09: Superseded by the Android Bluetooth HID reroute. macOS Bluetooth HID output/haptics are documented as unsupported/deferred for the stable Android HID path.
- D-10: Target a local development proof package with exact launch, signing, permission, and fallback commands.
- D-11: Historical proof target was macOS 26.2 build 25C56 on arm64.
- D-12: Use ad-hoc/local development signing first where CoreHID permits.
- D-13: Record commands, prompts, observed OS/toolchain requirements, and DriverKit entitlement fallback notes.

## Evidence And Redaction Rules

Committed docs and `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` must contain status metadata only. Do not commit raw signing identity hashes, private key paths, QR/manual codes, proof values, stream keys, HMAC keys, Bluetooth addresses, device ids, screenshots, or raw logs.

Use the environment probe:

```bash
native/macos-hid-helper/scripts/probe-macos-hid-environment.sh
```

The probe prints sanitized `sw_vers`, `uname -m`, `xcodebuild -version`, `xcrun --show-sdk-path`, `swift --version`, `security find-identity -v -p codesigning`, `command -v hidutil`, `command -v ioreg`, and `command -v systemextensionsctl`.

## CoreHID Legacy Probe Path

The legacy helper path attempted to create `BT Gun Virtual Joystick` through CoreHID `HIDVirtualDevice` with:

- Vendor ID: `0x1209`
- Product ID: `0xB707`
- Input report ID: `0x01`
- Output report ID: `0x02`
- Entitlement: `com.apple.developer.hid.virtual.device`

The Kotlin desktop backend runs the helper as a local stdin/stdout byte bridge. The helper accepts:

- `HELLO 1`
- `SUBMIT_INPUT <hex>` for a 10-byte report ID `0x01` payload
- `READ_OUTPUT`
- `STATUS`
- `QUIT`

The helper returns only `OK`, `ERR <safe-token>`, `OUTPUT <hex>`, or:

```json
{"version":1,"deviceActive":true,"osVisible":false,"setReportCallbackSeen":false,"inputReportsSubmitted":0,"outputReportsQueued":0,"malformedInputReports":0,"malformedOutputReports":0}
```

`STATUS` is prefixed with `STATUS `. `osVisible` remains false until a later proof command records CLI/UI visibility.

## Separate OS/HID Output Probe

`BtGunMacosHidOutputProbe` is a separate macOS HID client. It is the deterministic D-07/D-08 proof path for OS/HID-origin output reports because it opens the enumerated `BT Gun Virtual Joystick` by VID `0x1209`, PID `0xB707`, checks usage/descriptor metadata where available, and calls `IOHIDDeviceSetReport` with output report ID `0x02`.

Build the helper and probe:

```bash
swift build --package-path native/macos-hid-helper -c debug --product BtGunMacosHidHelper
swift build --package-path native/macos-hid-helper -c debug --product BtGunMacosHidOutputProbe
```

Run the helper in one shell:

```bash
native/macos-hid-helper/.build/debug/BtGunMacosHidHelper
```

Run the OS/HID-origin output probe in another shell:

```bash
native/macos-hid-helper/.build/debug/BtGunMacosHidOutputProbe --strength 180 --duration-ms 120 --ttl-ms 500
```

Expected probe bytes are report ID `0x02`, version `0x01`, strength byte, duration uint16 little-endian, TTL uint16 little-endian, zero flags, and zero reserved. For the command above, the report is:

```text
0201b47800f4010000
```

Expected helper proof after the probe:

```text
STATUS {"version":1,"deviceActive":true,"osVisible":false,"setReportCallbackSeen":true,...}
READ_OUTPUT
OUTPUT 0201b47800f4010000
```

The helper delegate method `receivedSetReportRequestOfType` receives this report, then the Kotlin backend drains it through `READ_OUTPUT` and `MacosOutputReportMapper`. Direct `simulateOutputReport` calls and direct `ControlServer.sendHapticCommand` calls are mapper/control-path tests only; they do not satisfy deterministic OS/HID-origin output proof.

Build and run the helper proof:

```bash
native/macos-hid-helper/scripts/build-corehid-helper.sh
```

The build script must:

1. Run the sanitized environment probe.
2. Compile the Swift package if `swift` can import CoreHID.
3. Ad-hoc sign first with `codesign --force --sign - --entitlements native/macos-hid-helper/Entitlements.plist`, unless `BTGUN_MACOS_HID_SIGN_IDENTITY` names a local Keychain identity for a blocked runtime proof retry.
4. Run the helper in `--probe` mode.
5. Collect sanitized `hidutil` and `ioreg` visibility evidence.
6. Record `corehid-pass`, `corehid-compile-blocked`, `corehid-runtime-blocked`, `corehid-visibility-failed`, or `corehid-output-failed` for later plans.

Manual compile command if the wrapper fails before SwiftPM:

```bash
cd native/macos-hid-helper
swift build -c debug
```

Manual ad-hoc signing command:

```bash
codesign --force --sign - --entitlements native/macos-hid-helper/Entitlements.plist native/macos-hid-helper/.build/debug/BtGunMacosHidHelper
```

Manual local Keychain signing command, used only when ad-hoc signing is runtime-blocked:

```bash
BTGUN_MACOS_HID_SIGN_IDENTITY=keychain-bio-local native/macos-hid-helper/scripts/build-corehid-helper.sh
```

Current Task 3 retry result: `keychain-bio-local` was visible to `security find-identity` outside the sandbox and `codesign` accepted it, but the helper was still killed before `hidutil` enumeration. Current blocker is `corehid-runtime-blocked`: virtual HID entitlement or macOS runtime policy, not Swift compilation.

Checkpoint decision on 2026-06-10: the local proof found no stock, software-only CoreHID route that satisfies `com.apple.developer.hid.virtual.device` on normal macOS with ad-hoc signing, a self-signed certificate, or the named local `keychain-bio-local` identity. The user also confirmed no USB bridge is available. The selected route is therefore a local-development-only HIDDriverKit/system-extension fallback that may require documented, temporary security-relaxed development mode. This route has no paid Apple Developer subscription requirement for the current lab path, but it is not a shippable or user-facing support claim.

Until later proof rows pass, do not claim DESK-03 or DESK-06 production support from this CoreHID result. Treat the CoreHID path as `corehid-runtime-blocked` and the next work as lab-only fallback exploration.

Plan 07-05 gate result: the real `smokeDesktopBackendMacosCoreHid` command and `BtGunMacosHidOutputProbe` were added, but the CoreHID helper was killed before enumeration on normal macOS after SwiftPM build and ad-hoc codesign embedded `com.apple.developer.hid.virtual.device`. The smoke XML was written at `desktop-companion/build/test-results/btgun-smoke/macos-corehid/TEST-btgun-macos-corehid.xml` with `corehid-runtime-blocked` helper startup/publish failures and `corehid-visibility-failed` enumeration failures. The separate output probe returned `BT Gun Virtual Joystick 0x1209:0xB707 not found`, so no OS/HID-origin set-report callback proof exists. Plan 07-06 DriverKit fallback remains mandatory; no SIP, system extension developer mode, install, activation, removal, rollback, reboot, or OS security-state command was run.

Manual launch command:

```bash
native/macos-hid-helper/.build/debug/BtGunMacosHidHelper --probe
```

Manual CLI visibility commands:

```bash
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|PrimaryUsage|MaxInputReportSize|MaxOutputReportSize'
```

## Permission And Signing Notes

The helper first attempts ad-hoc signing. If macOS rejects the virtual HID entitlement or no OS-visible device appears, record `corehid-runtime-blocked` or `corehid-visibility-failed` with sanitized reason text. Do not commit identity hashes or provisioning UUIDs.

If signing identity/provisioning is required, the exact human setup request is:

```bash
security find-identity -v -p codesigning
xcode-select -p
```

Then select a matching full Xcode only if CoreHID or IOHIDUserDevice compile/runtime proof is blocked by CLT/SDK mismatch:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

The setup is reversible:

```bash
sudo xcode-select -s /Library/Developer/CommandLineTools
```

## IOHIDUserDevice Shim Gate

If Swift/CoreHID is blocked only by local tooling, the same wave may use an Objective-C `IOHIDUserDeviceCreateWithProperties` shim as a user-space diagnostic fallback. The shim must use the same descriptor, `0x1209/0xB707`, report IDs `0x01/0x02`, and `com.apple.developer.hid.virtual.device` entitlement. Runtime entitlement proof remains required; IOHIDUserDevice cannot replace D-03/D-09 output proof.

Expected shim proof commands:

```bash
clang -fobjc-arc -framework Foundation -framework IOKit native/macos-hid-helper/Sources/IOHIDUserDeviceShim/main.m -o /tmp/btgun-iohiduserdevice-shim
codesign --force --sign - --entitlements native/macos-hid-helper/Entitlements.plist /tmp/btgun-iohiduserdevice-shim
/tmp/btgun-iohiduserdevice-shim --probe
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
```

## HIDDriverKit Fallback Gate

HIDDriverKit/system extension work starts only after a recorded CoreHID non-pass gate:

- `corehid-runtime-blocked`
- `corehid-visibility-failed`
- `corehid-output-failed`

Selected local-dev-only branch: `corehid-runtime-blocked` plus no USB bridge available. This branch may use HIDDriverKit/system extension development workflows with temporarily relaxed local security checks, but only as a lab proof. It must not be packaged, shipped, or advertised as the product macOS path without proper entitlement/signing proof and later accepted planning.

DriverKit fallback requires explicit later approval before any SIP change, system extension developer mode change, system extension activation, install, removal, rollback, entitlement use, reboot, or other OS security-state change. Expected proof/status commands for that later path are documented in Plan 07-06, not run in this plan:

```bash
systemextensionsctl list
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|MaxOutputReportSize'
```

Security-relaxed development commands, if Plan 07-06 later requests them, must be treated as human-approved manual setup only. They are included here as future-risk documentation, not as commands to run during Plan 07-01 closeout:

```bash
# Recovery environment only; changes system security posture.
csrutil disable

# Normal boot; enables system extension developer workflow.
sudo systemextensionsctl developer on

# Reversal after lab proof, subject to current macOS requirements.
sudo systemextensionsctl developer off
# Recovery environment only.
csrutil enable
```

Risks: these commands can weaken platform protections, require reboot/recovery workflows, and can affect more than this project. They require explicit later approval before use.

## Manifest Status Values

Use these exact status classes in the Phase 7 manifest and summaries:

- `pending`: proof not attempted yet.
- `blocked`: local toolchain/signing/entitlement state prevents proof.
- `pass`: local proof satisfied the row.
- `fail`: local proof ran and disproved the expected behavior.

Use these CoreHID gate notes for downstream branch selection:

- `corehid-pass`
- `corehid-compile-blocked`
- `corehid-runtime-blocked`
- `corehid-visibility-failed`
- `corehid-output-failed`
- `corehid-blocked` only as an umbrella wording in docs, never as the precise branch gate.
