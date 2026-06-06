# Phase 1: Hardware and Protocol Discovery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-06
**Phase:** 1-Hardware and Protocol Discovery
**Areas discussed:** reverse-engineering strategy, evidence bar, diagnostic artifacts, fixture shape

---

## Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Evidence bar | Lock what counts as verified for controls, services, frames, and rumble before planning. | |
| Diagnostic artifacts | Lock runnable outputs: on-device diagnostic, adb/log files, raw captures, normalized JSON fixtures. | |
| Reverse-engineering depth | Lock how far to decompile/reacquire apps if standard Android input already works or partly works. | ✓ |
| Other | Freeform direction. | ✓ |

**User's choice:** Freeform: reverse-engineering first, even the Bluetooth handshake may be custom; build the protocol from decompiling deprecated APKs.
**Notes:** This shifted Phase 1 from "standard Android input first" to "decompile-first protocol hypothesis, then hardware validation."

---

## Reverse-Engineering Depth

| Option | Description | Selected |
|--------|-------------|----------|
| Exhaust all valid refs | Decompile every valid APK/XAPK, inspect Unity/assets/native libs/OBB where present, extract handshake/control/rumble clues, then validate on hardware. | |
| Strongest refs first | Analyze `ARGun2021`, `AR Cher`, and `WorldsAR`; broaden only if protocol unclear. | ✓ |
| Minimal static pass | Manifest/strings only, then hardware. | |
| Other | Freeform direction. | |

**User's choice:** Strongest refs first.
**Notes:** Ordered first-pass references are `docs/refs/ARGun2021.apk`, `docs/refs/AR Cher_20200905_Apkpure.xapk`, and `docs/refs/WorldsAR_14.0_apkcombo.com.xapk`.

---

## Invalid ARGunPro Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Reacquire only if blocked | Skip at first; fetch/find new copy only if strongest refs do not reveal handshake/control/rumble. | ✓ |
| Reacquire immediately | Treat ARGunPro as likely important; recover before static analysis starts. | |
| Ignore for Phase 1 | Keep phase focused on valid local refs. | |
| Other | Freeform direction. | |

**User's choice:** Reacquire only if blocked.
**Notes:** `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` is known invalid/0-byte locally.

---

## Evidence Bar

| Option | Description | Selected |
|--------|-------------|----------|
| Static clue + hardware capture + normalized fixture | Decompile clue identifies path, real device capture confirms bytes/events, fixture records raw + normalized output. | ✓ |
| Hardware capture alone | Static APK guides work, but verification only requires device-observed frames/events. | |
| Static clue acceptable for non-rumble | Control mapping can be static-derived; rumble must be hardware-confirmed. | |
| Other | Freeform direction. | |

**User's choice:** Static clue + hardware capture + normalized fixture.
**Notes:** This is the verification threshold for controls, handshake observations, and rumble path.

---

## Diagnostic Artifacts

| Option | Description | Selected |
|--------|-------------|----------|
| Full evidence bundle | Static notes, decompiled clue index, scan output, btsnoop/logcat captures, raw frame fixtures, normalized event fixtures, rumble evidence. | ✓ |
| Fixtures first | Raw + normalized fixtures and minimal notes; keep large captures out unless needed. | |
| Human notes first | Protocol doc + mapping table; fixtures added later in implementation. | |
| Other | Freeform direction. | |

**User's choice:** Full evidence bundle.
**Notes:** The phase should leave enough evidence for later agents to trust the protocol without redoing all discovery.

---

## Evidence Storage

| Option | Description | Selected |
|--------|-------------|----------|
| Repo fixtures + ignored raw captures | Commit small sanitized notes/fixtures; keep big raw `btsnoop`/decompile output under ignored evidence dirs with manifest pointers. | ✓ |
| Commit everything useful | Keep all captures/decompile outputs in repo unless too large. | |
| Docs only | Commit protocol notes and fixture summaries; raw captures stay local. | |
| Other | Freeform direction. | |

**User's choice:** Repo fixtures + ignored raw captures.
**Notes:** Use committed manifests to point at ignored local evidence files.

---

## Diagnostic Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Throwaway diagnostic Android app/module | Purpose-built scanner/capture UI, logs standard `InputDevice`, BLE/Classic services, decoded frames, rumble tests. | ✓ |
| Start production Android host shell | Diagnostic screens become seed of real app. | |
| Scripts/tools first | adb/logcat/btsnoop + desktop parsing scripts, minimal Android UI. | |
| Other | Freeform direction. | |

**User's choice:** Throwaway diagnostic Android app/module.
**Notes:** Validation tool, not a production app commitment.

---

## Normalized Fixture Format

| Option | Description | Selected |
|--------|-------------|----------|
| JSON lines events + binary raw blobs | Raw capture refs plus readable normalized event sequence for controls/rumble. | ✓ |
| Single JSON fixture per test | Easier snapshots, less stream-like. | |
| Binary/protobuf-like schema now | Closer to future transport, more premature. | |
| Other | Freeform direction. | |

**User's choice:** JSON lines events + binary raw blobs.
**Notes:** Keep normalized fixture streams readable while preserving raw protocol evidence.

---

## Completion Gate

| Option | Description | Selected |
|--------|-------------|----------|
| Ready for context | Write `01-CONTEXT.md` and discussion log. | ✓ |
| Explore more gray areas | Continue discussion before writing context. | |
| Other | Freeform direction. | |

**User's choice:** Ready for context.
**Notes:** No deferred ideas or scope-creep items were raised.

---

## the agent's Discretion

- Exact decompile tools and helper scripts.
- Exact ignored evidence directory names and manifest schema.
- Exact diagnostic UI layout and JSONL field names.

## Deferred Ideas

None.
