# Phase 8: Desktop Profiles and Mapping - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-06-12T01:02:10Z
**Phase:** 8-Desktop Profiles and Mapping
**Areas discussed:** Profile Path Boundary, Aim Feel Model, Button Remap Rules, Profile Storage UX

---

## Profile Path Boundary

### Where should Phase 8 profiles apply?

| Option | Description | Selected |
|--------|-------------|----------|
| Desktop LAN/Windows only | Build real profile mapper in desktop companion; Android HID/macOS keeps fixed default mapping. | |
| Unified desktop + Android HID sync | Desktop stores profiles and sends active mapping to Android. | |
| Split contract now, Android sync later | Build desktop mapper and durable schema; Android consumption later. | |
| Android owns all profiles | User freeform reroute: Android stores and applies all profiles. | yes |

**User's choice:** Android owns all profiles.
**Notes:** This intentionally supersedes prior desktop-owned profile direction because Android HID became the primary macOS path.

### What should Android send after applying profile?

| Option | Description | Selected |
|--------|-------------|----------|
| Mapped + raw | Android sends mapped buttons/axes plus raw motion/provider data. | |
| Mapped only | Simpler product path, weaker diagnostics. | |
| Raw + active profile snapshot | Desktop recomputes from Android-owned profile snapshot. | |
| Minimal mapped plus debug raw toggle | User freeform: minimal by default, raw only when debug button/option enabled. | yes |

**User's choice:** Minimal mapped stream by default; debug toggle enables raw data.
**Notes:** Raw data should be opt-in from Android.

### Debug raw-data toggle behavior?

| Option | Description | Selected |
|--------|-------------|----------|
| Android session toggle | Toggle in Android app, controlled from Android session UI. | yes |
| Desktop request toggle | Desktop can request debug raw data over control channel. | |
| Both ends must agree | Android toggle plus desktop request. | |

**User's choice:** Android session toggle.
**Notes:** Desktop does not gain a raw-debug request surface in Phase 8.

### Desktop profile surface after Android owns profiles?

| Option | Description | Selected |
|--------|-------------|----------|
| Remove desktop profile editing | Desktop only shows active Android profile metadata/revision plus mapped stream status. | yes |
| Keep read-only desktop selector | Desktop can ask Android to switch among Android profiles. | |
| Keep local desktop fallback profile | Desktop keeps one emergency/default mapper for Windows LAN if Android raw/profile is missing. | |

**User's choice:** Remove desktop profile editing.
**Notes:** Desktop remains status/diagnostics and fallback publisher, not profile authority.

---

## Aim Feel Model

### Default aim profile feel?

| Option | Description | Selected |
|--------|-------------|----------|
| Responsive balanced | Low smoothing, small dead zone, sensitivity 1.0, inversion off. | yes |
| Raw/direct | No smoothing/dead zone. | |
| Stable assisted | More smoothing/dead zone. | |

**User's choice:** Responsive balanced.
**Notes:** Default should feel usable immediately without making debug raw mode the normal product feel.

### Provider-specific tuning?

| Option | Description | Selected |
|--------|-------------|----------|
| Per provider presets | Separate defaults for calibrated/fused, gyro/raw, and tilt fallback. | |
| One shared aim config | Same settings for all providers. | |
| Expert-only provider overrides | Shared normal config plus advanced provider overrides. | yes |

**User's choice:** Expert-only provider overrides.
**Notes:** Later UI answer makes these advanced overrides visible in the main editor flow.

### Smoothing control shape?

| Option | Description | Selected |
|--------|-------------|----------|
| Off/Low/Medium | Simple segmented choice mapped to fixed filter values. | |
| Numeric slider | More precise but easier to tune badly. | |
| Adaptive smoothing | Dynamic smoothing based on movement. | yes |

**User's choice:** Adaptive smoothing.
**Notes:** Needs guardrail against input lag.

### Adaptive smoothing guardrail?

| Option | Description | Selected |
|--------|-------------|----------|
| Latency-capped adaptive | Bound/report added filter lag; fallback to Low if lag hurts. | yes |
| Feel-first adaptive | Prioritize stability even if latency rises. | |
| Debug-comparable adaptive | Adaptive product stream with raw debug overlay. | |

**User's choice:** Latency-capped adaptive.
**Notes:** Filter must not undermine the v1 latency target.

---

## Button Remap Rules

### Remap scope?

| Option | Description | Selected |
|--------|-------------|----------|
| Limited remap | Trigger/reload/X/Y/A/B can map among v1 virtual buttons; stick/aim axes stay semantic. | yes |
| Full remap | Any physical control can map to any v1 virtual button/axis. | |
| Fixed default only | No button remap in Phase 8. | |

**User's choice:** Limited remap.
**Notes:** Stick axes and aim axes stay semantic.

### Reload/recenter rule?

| Option | Description | Selected |
|--------|-------------|----------|
| Preserve reload-hold recenter always | Physical reload hold remains recenter. | |
| Remap can move recenter gesture | User chooses which physical button holds to recenter. | yes |
| Disable remap for reload | Reload fixed to avoid gesture confusion. | |

**User's choice:** Remap can move recenter gesture.
**Notes:** Physical reload is no longer mandatory as the recenter button.

### Recenter gesture safety?

| Option | Description | Selected |
|--------|-------------|----------|
| One recenter button required | Exactly one hold-to-recenter physical button required. | yes |
| Allow no recenter | User can disable recenter. | |
| Multiple recenter buttons | Several buttons can recenter. | |

**User's choice:** One recenter button required.
**Notes:** Mandatory recenter preserves acceptance/debuggability while allowing remap.

### Bad mapping handling?

| Option | Description | Selected |
|--------|-------------|----------|
| Validate and block save | Refuse profiles that lose required controls or recenter. | yes |
| Warn but allow | Flexible but easier to break v1 acceptance. | |
| Save but auto-repair | Android silently/noticeably changes invalid mappings. | |

**User's choice:** Validate and block save.
**Notes:** Invalid mappings must not be persisted.

---

## Profile Storage UX

### Number of profiles in Phase 8?

| Option | Description | Selected |
|--------|-------------|----------|
| Default + custom list | Built-in Default Visualizer profile plus user-created local profiles. | yes |
| One editable default | Simpler but no real select/manage flow. | |
| Defaults only | No creation yet. | |

**User's choice:** Default + custom list.
**Notes:** Phase 8 includes create/store/select on Android.

### Profile persistence and reset?

| Option | Description | Selected |
|--------|-------------|----------|
| Local Android storage + reset default | Phone-local profiles; immutable default; user profiles duplicate/edit/delete. | yes |
| File-based profiles | JSON files user can manage. | |
| Desktop backup store | Android owns profiles but desktop keeps backups. | |

**User's choice:** Local Android storage + reset default.
**Notes:** Built-in default is immutable; reset restores default-derived settings.

### Import/export?

| Option | Description | Selected |
|--------|-------------|----------|
| Defer import/export | Local Android profiles only. | yes |
| Export only | Share/debug profiles but no import. | |
| Full import/export now | Adds validation, file UI, and versioning. | |

**User's choice:** Defer import/export.
**Notes:** Import/export belongs to later game-profile or v2 work.

### Profile editing UI depth?

| Option | Description | Selected |
|--------|-------------|----------|
| Practical Android editor | List/select/duplicate/rename/delete, aim settings, limited remap, recenter, debug raw toggle. | |
| Minimal editor | Select default/custom and tune aim only. | |
| Advanced editor | Full per-provider overrides surfaced in main flow. | yes |

**User's choice:** Advanced editor.
**Notes:** The main Android editor should expose provider overrides instead of hiding them for later.

---

## the agent's Discretion

- Choose Android profile schema/storage, UI layout, validation labels, adaptive smoothing constants, default numeric values, and read-only desktop metadata display details.

## Deferred Ideas

- Profile import/export.
- Game-specific preset browser.
- Phase 9 visualizer UI and latency/packet-loss dashboards.
