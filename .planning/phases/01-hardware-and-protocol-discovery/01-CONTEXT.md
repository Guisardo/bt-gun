# Phase 1: Hardware and Protocol Discovery - Context

**Gathered:** 2026-06-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 1 proves how the real iPega gun exposes input and accepts rumble by reverse-engineering the deprecated APK/XAPK apps first, then validating the discovered handshake, control mapping, and vibration command path against physical hardware. The phase delivers evidence, diagnostics, and fixtures for downstream Android host work; it does not build the production Android host, LAN transport, desktop companion, profiles, or visualizer.

</domain>

<decisions>
## Implementation Decisions

### Reverse-Engineering Strategy
- **D-01:** Start from reverse engineering, not hardware probing alone. Assume even the Bluetooth handshake may be custom until the deprecated apps prove otherwise.
- **D-02:** Build the protocol hypothesis from decompiling deprecated APK/XAPK references before writing hardware-facing validation code.
- **D-03:** Analyze strongest local references first: `docs/refs/ARGun2021.apk`, `docs/refs/AR Cher_20200905_Apkpure.xapk`, and `docs/refs/WorldsAR_14.0_apkcombo.com.xapk`.
- **D-04:** Broaden to other references only if the strongest refs do not reveal handshake, control, or rumble behavior.
- **D-05:** `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` is 0 bytes locally. Reacquire it only if the strongest valid refs block protocol discovery.

### Evidence Bar
- **D-06:** A protocol/control/rumble finding is verified only when all three exist: a static decompile clue, a hardware capture, and a normalized fixture.
- **D-07:** Static APK/XAPK findings can guide implementation, but they are not sufficient proof without real-device capture evidence.
- **D-08:** Hardware captures must be tied back to the decompiled clue that motivated the test, so downstream agents can see why each fixture exists.

### Diagnostic Artifacts
- **D-09:** Phase 1 must leave a full evidence bundle: static analysis notes, decompiled clue index, Bluetooth scan output, logcat/HCI capture pointers, raw frame fixtures, normalized event fixtures, and rumble evidence.
- **D-10:** Commit sanitized protocol docs and small fixtures to the repo. Keep large raw captures and decompile output in ignored evidence directories, with committed manifest pointers that identify local file names, origin, test action, and expected interpretation.
- **D-11:** Build a throwaway diagnostic Android app/module for validation. It should scan and report standard Android input visibility, BLE/Classic services, decoded frames, and rumble test outcomes.
- **D-12:** The diagnostic app/module is validation tooling for Phase 1, not the production Android host app. Useful code can be reused later, but planners should not treat UI or module boundaries as production commitments.

### Fixture Shape
- **D-13:** Normalized fixtures should be JSON Lines event sequences for readable downstream tests.
- **D-14:** Raw protocol evidence should be stored as binary blobs or capture excerpts referenced from JSONL fixtures/manifests, not hand-transcribed byte strings only.
- **D-15:** Fixtures must cover trigger, reload, joystick axes, X/Y/A/B button down/up semantics, Bluetooth connection/handshake observations, and rumble command/ack/failure evidence where discovered.

### the agent's Discretion
- Choose exact decompile tools, parser scripts, ignored evidence directory names, JSONL field names, and diagnostic UI layout during planning.
- Choose how much of each ignored raw capture to summarize in committed manifests, as long as downstream agents can reproduce or inspect the evidence locally.
- Choose whether to create small command-line helpers alongside the throwaway Android diagnostic app if they reduce manual fixture extraction.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` — Phase 1 goal, requirements, success criteria, dependencies, and later-phase boundaries.
- `.planning/REQUIREMENTS.md` — `DISC-01` through `DISC-07`, v1 constraints, acceptance criteria, and out-of-scope items.
- `.planning/PROJECT.md` — product architecture intent, target hardware, local reference app list, constraints, and key decisions.
- `.planning/STATE.md` — current project state and known blockers.

### Research Context
- `.planning/research/SUMMARY.md` — research summary, local reference app findings, and Phase 1 implications.
- `.planning/research/STACK.md` — recommended Android/Bluetooth/reverse-engineering tools and stack constraints.
- `.planning/research/ARCHITECTURE.md` — adapter-to-normalized-event boundary and expected evidence flow.
- `.planning/research/PITFALLS.md` — critical pitfalls for standard-gamepad assumptions, static-only RE, and rumble proof.
- `.planning/research/FEATURES.md` — feature dependency notes and table-stakes discovery expectations.

### Local Reference Apps
- `docs/refs/ARGun2021.apk` — strongest first-pass APK reference.
- `docs/refs/AR Cher_20200905_Apkpure.xapk` — strongest first-pass XAPK reference.
- `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` — strongest first-pass XAPK reference.
- `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` — secondary reference; inspect only if strongest refs are insufficient or if it explains shared library behavior.
- `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` — invalid 0-byte local file; reacquire only if blocked.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- No production source tree exists yet. The only concrete local assets are project planning docs and archived APK/XAPK files under `docs/refs/`.
- Existing research docs already identify likely tools: apktool, jadx, Unity/asset decompilation tools, adb/logcat, Bluetooth HCI snoop captures, BLE scanners, and fixture manifests.

### Established Patterns
- Hardware-specific protocol details should stop at an Android gun adapter boundary and convert into normalized events.
- Reverse-engineered claims must be backed by physical-device evidence before they become stable implementation assumptions.
- Large raw evidence should not bloat the repo; committed artifacts should point to ignored local evidence files.

### Integration Points
- New Phase 1 work should create an evidence/doc/fixture structure that later Android host and transport phases can consume.
- The diagnostic Android app/module should connect to the physical gun and emit captures/fixtures without forcing production app architecture decisions.

</code_context>

<specifics>
## Specific Ideas

- User explicitly prefers reverse-engineering first because the Bluetooth handshake itself may be custom.
- The strongest-ref path should avoid wasting time on every archived app before the obvious candidates are exhausted.
- A finding should not be called verified unless it survives the full static clue -> hardware capture -> normalized fixture chain.
- JSONL normalized event fixtures plus binary raw blobs are preferred because downstream tests can read the normalized stream while preserving raw evidence for protocol work.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 1 scope.

</deferred>

---

*Phase: 1-Hardware and Protocol Discovery*
*Context gathered: 2026-06-06*
