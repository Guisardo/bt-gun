# macOS HIDDriverKit Fallback Proof

This is the Phase 7 lab-only fallback path for `corehid-runtime-blocked`. It is not a production macOS support claim and does not make DESK-03 or DESK-06 complete until the proof rows pass.

## Trigger

Plan 07-05 recorded `corehid-runtime-blocked`: the CoreHID helper built and signed, but macOS killed it before `hidutil` enumeration and the separate output probe could not find `BT Gun Virtual Joystick` at VID `0x1209`, PID `0xB707`.

DriverKit work is selected only for these CoreHID non-pass gates:

- `corehid-runtime-blocked`
- `corehid-visibility-failed`
- `corehid-output-failed`

If a later entitlement-capable CoreHID run records `corehid-pass`, this fallback is skipped and `phase7-driverkit-fallback-skipped-corehid-pass` is the only DriverKit row.

## Boundary

`BTGunHidDriver` is a HID byte/report bridge only:

- input report ID `0x01`, 10 bytes: report id, trigger/reload/X/Y/A/B bits, stickX, stickY, aimX, aimY
- output report ID `0x02`, 9 bytes: report id, version `0x01`, strength, duration ms, TTL ms, flags, reserved
- no LAN sockets
- no session auth
- no UDP parsing
- no desktop profile or aim mapping
- no `ControlServer`
- no `HapticCommand`
- no Android lifecycle

Kotlin/JVM desktop companion keeps ownership of pairing, secure session, UDP validation, semantic mapping, and phone-haptic routing.

## Source Scaffold

Fallback source lives under:

```text
native/macos-hid-driverkit/
  BTGunHidDriver/
    BTGunHidDriver.cpp
    BTGunHidDriver.h
    Info.plist
  BTGunHidHostApp/
    BTGunHidHostApp.swift
    BTGunHidHostApp.entitlements
```

The source is a minimal Xcode DriverKit target scaffold. Local activation still needs user-approved signing/provisioning and System Extension approval before any proof run.

## Entitlements

Expected DriverKit entitlements discovered in the local SDK:

- `com.apple.developer.driverkit`
- `com.apple.developer.driverkit.family.hid.device`
- `com.apple.developer.driverkit.userclient-access`
- `com.apple.developer.driverkit.communicates-with-drivers`
- `com.apple.developer.system-extension.install`

The HID family entitlement can require Apple approval/provisioning. If unavailable, record a blocker and keep Phase 7 inconclusive.

## Build And Sign Commands

Use these only after the local Xcode DriverKit project is created from the scaffold sources and a valid provisioning profile is selected. These commands do not require disabling SIP by themselves, but signing/provisioning can still block.

```bash
xcodebuild -project native/macos-hid-driverkit/BTGunHidDriver.xcodeproj -scheme BTGunHidHostApp -configuration Debug -derivedDataPath /private/tmp/btgun-driverkit-derived build
codesign --display --entitlements :- /private/tmp/btgun-driverkit-derived/Build/Products/Debug/BTGunHidHostApp.app
codesign --display --entitlements :- /private/tmp/btgun-driverkit-derived/Build/Products/Debug/BTGunHidHostApp.app/Contents/Library/SystemExtensions/com.btgun.driver.BTGunHidDriver.dext
```

Required entitlement strings in the output:

```text
com.apple.developer.driverkit
com.apple.developer.driverkit.family.hid.device
com.apple.developer.driverkit.userclient-access
com.apple.developer.system-extension.install
```

## USER APPROVAL REQUIRED

Do not run activation, install, removal, rollback, reboot, SIP, or system-extension developer-mode commands until the user explicitly approves this plan checkpoint.

Activation command after approval:

```bash
/private/tmp/btgun-driverkit-derived/Build/Products/Debug/BTGunHidHostApp.app/Contents/MacOS/BTGunHidHostApp activate
```

Expected prompt:

```text
System Settings asks to approve the BT Gun HID Driver system extension.
```

Status commands after approval:

```bash
systemextensionsctl list
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|MaxInputReportSize|MaxOutputReportSize'
```

Output probe after approval:

```bash
native/macos-hid-helper/.build/debug/BtGunMacosHidOutputProbe --strength 180 --duration-ms 120 --ttl-ms 500
```

Required pass rows if proof succeeds:

- `phase7-driverkit-system-extension-approved`
- `phase7-driverkit-cli-enumeration`
- `phase7-driverkit-output-probe`

## Security-Relaxed Lab Commands

These are listed for exact risk review only. They change OS security state and are not approved by this commit.

```bash
# Recovery environment only.
csrutil disable

# Normal boot.
sudo systemextensionsctl developer on

# Reversal after lab proof, subject to current macOS requirements.
sudo systemextensionsctl developer off

# Recovery environment only.
csrutil enable
```

Risk: these commands can weaken platform protections, require reboot/recovery workflows, and affect more than this project.

## Rollback Commands

Only after approval:

```bash
/private/tmp/btgun-driverkit-derived/Build/Products/Debug/BTGunHidHostApp.app/Contents/MacOS/BTGunHidHostApp deactivate
systemextensionsctl list
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
```

If local developer mode was enabled for the lab proof, reversal is:

```bash
sudo systemextensionsctl developer off
```

If SIP was changed in Recovery for the lab proof, reversal is:

```bash
csrutil enable
```

## Logs And Evidence

Collect sanitized evidence only. Do not commit signing identity hashes, provisioning UUIDs, device ids, QR/manual codes, proof values, stream keys, HMAC keys, screenshots, or raw logs.

```bash
log show --last 10m --style compact --predicate 'subsystem CONTAINS "com.apple.systemextensions" OR eventMessage CONTAINS "BTGunHidDriver"'
systemextensionsctl list
hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}'
ioreg -r -c IOHIDDevice -l -w 0 | rg 'BT Gun|VendorID|ProductID|MaxInputReportSize|MaxOutputReportSize'
```

Current Plan 07-06 status: blocked on explicit user approval before any system-extension activation or OS security-state change.
