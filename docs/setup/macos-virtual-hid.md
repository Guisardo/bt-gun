# macOS Virtual HID Development Proof

This is the PACK-03 setup path for Phase 7. It is a development proof only: no production notarization, installer, or Developer ID requirement is introduced here.

## Phase 7 Decisions

- D-01: Try CoreHID `HIDVirtualDevice` first.
- D-02: Switch away from CoreHID only when official documentation or local compile/runtime proof shows it cannot satisfy the OS-visible gamepad-style joystick path.
- D-03: If CoreHID cannot receive OS-origin output/rumble reports, use HIDDriverKit/system extension fallback work for the output proof.
- D-09: Do not document output as unsupported when CoreHID output proof fails; record a fallback gate instead.
- D-10: Target a local development proof package with exact launch, signing, permission, and fallback commands.
- D-11: Current proof target is macOS 26.2 build 25C56 on arm64 unless the local probe records a concrete blocker.
- D-12: Use ad-hoc/local development signing first where CoreHID permits.
- D-13: Record commands, prompts, observed OS/toolchain requirements, and DriverKit entitlement fallback notes.

## Evidence And Redaction Rules

Committed docs and `docs/evidence/manifests/phase7-macos-virtual-hid.jsonl` must contain status metadata only. Do not commit raw signing identity hashes, private key paths, QR/manual codes, proof values, stream keys, HMAC keys, Bluetooth addresses, device ids, screenshots, or raw logs.

Use the environment probe:

```bash
native/macos-hid-helper/scripts/probe-macos-hid-environment.sh
```

The probe prints sanitized `sw_vers`, `uname -m`, `xcodebuild -version`, `xcrun --show-sdk-path`, `swift --version`, `security find-identity -v -p codesigning`, `command -v hidutil`, `command -v ioreg`, and `command -v systemextensionsctl`.

## CoreHID First Path

The primary helper path creates `BT Gun Virtual Joystick` through CoreHID `HIDVirtualDevice` with:

- Vendor ID: `0x1209`
- Product ID: `0xB707`
- Input report ID: `0x01`
- Output report ID: `0x02`
- Entitlement: `com.apple.developer.hid.virtual.device`

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

DriverKit fallback requires explicit later approval before any system extension activation, install, rollback, entitlement use, or reboot. Expected proof commands for that later path are documented in Plan 07-06, not run in this plan:

```bash
systemextensionsctl list
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|MaxOutputReportSize'
```

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
