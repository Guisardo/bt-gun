# Phase 2: Android Host Live Input - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-06T21:58:03Z
**Phase:** 2-Android Host Live Input
**Areas discussed:** Gun connection flow, Normalized live sample shape, Motion aim and recenter, Android status surface

---

## Gun Connection Flow

| Question | Options | Selected |
|----------|---------|----------|
| How should Phase 2 start gun connection after Bluetooth/permission checks pass? | Auto Connect; Device Picker; Diagnostic First | Auto Connect |
| When BLE connection drops during Phase 2, what should app do? | Auto Reconnect; Ask To Reconnect; Diagnostic Retry Panel | Auto Reconnect |
| How should permission handling feel? | Guided Gate; System-First; Debug Checklist | Guided Gate |
| Should gun connection stay alive outside foreground UI? | Foreground Session; Foreground Service; UI Only | Foreground Service |

**Notes:** Auto-connect scans for `ARGunGame` / `fff0`, connects immediately, and shows progress. Reconnect runs while the active session is running and keeps visible state plus last error. Foreground service is a deliberate production choice beyond the diagnostic Activity-owned connection.

---

## Normalized Live Sample Shape

| Question | Options | Selected |
|----------|---------|----------|
| How should Phase 2 shape live samples inside Android before LAN protocol exists? | Unified Ordered Stream; Separate Streams; Fixture Mirror | Separate Streams |
| How should streams share timing/order? | Common Envelope + Per-Stream Seq; Per-Stream Only; Global Seq Too | Common Envelope + Per-Stream Seq |
| How much Phase 1 provenance should live events carry? | Full Debug Provenance; Compact Product Events; Dual Mode | Dual Mode |
| How should Phase 2 represent the physical stick? | Digital Directions + Derived Axes; Axes Only; Digital Only | Digital Directions + Derived Axes |

**Notes:** Separate streams are gun, motion, and status. Common envelope includes `stream`, per-stream `seq`, `capture_elapsed_nanos`, and `emitted_elapsed_nanos`. Product mode hides raw provenance; debug mode exposes it. Stick direction events are preserved while X/Y axes are derived for local preview.

---

## Motion Aim and Recenter

| Question | Options | Selected |
|----------|---------|----------|
| Which provider policy should Phase 2 use? | Best Available Auto; Manual Provider Select; Gyro First | Best Available Auto |
| How should motion samples be exposed before desktop mapping exists? | Raw + Preview Aim; Raw Only; Mapped Aim Now | Mapped Aim Now |
| Reload hold recenter: what exact behavior? | Press/Release + Hold Event; Hold Consumes Reload; Hold On Release | Press/Release + Hold Event |
| How should app show recenter feedback? | Immediate State Update; Toast Only; No UI Feedback | Immediate State Update |

**Notes:** Provider order is game rotation vector, rotation vector, gyro plus gravity/accelerometer, then gravity/accelerometer tilt fallback. Android local mapped aim is preview/calibration only; final game/platform mapping remains desktop-owned. Reload press/release always emits, with a separate recenter event after two seconds.

---

## Android Status Surface

| Question | Options | Selected |
|----------|---------|----------|
| What should Phase 2 app show on main session screen? | Operational Dashboard; Minimal Live View; Diagnostics Console | Operational Dashboard |
| How should debug detail appear? | Expandable Debug Panels; Separate Debug Screen; Always Visible | Expandable Debug Panels |
| Phase 2 has no LAN yet. How should desktop link/packet stream status appear? | Placeholder Cards; Hide Until Built; Fake Local Stream | Placeholder Cards |
| What should haptic status mean in Phase 2? | Phone Haptic Capability Only; Hide Haptics; Manual Test Button | Phone Haptic Capability Only |

**Notes:** Dashboard shows connection state, last gun event, motion provider, preview aim, recenter state, foreground-service status, and error line. Desktop link/packet stream are inactive placeholders. Haptic status is phone capability plus last local test; desktop-origin commands wait for Phase 4.

---

## the agent's Discretion

- Exact module/package split.
- Exact Android UI toolkit and layout.
- Exact foreground-service notification text.
- Exact stream class names, sample rates, buffers, and preview aim math.
- Exact debug panel layout.

## Deferred Ideas

- LAN pairing/authenticated session: Phase 3.
- UDP transport/control channel/desktop haptic commands: Phase 4.
- Final desktop aim profiles: Phase 8.
- Physical gun motor rumble: v2/deferred unless later evidence proves command path.
