#!/usr/bin/env bash
set -euo pipefail

OUT_ROOT="${1:-.evidence/phase2/host-live-input}"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
OUT_DIR="${OUT_ROOT}/${RUN_ID}"
ADB="${ADB:-adb}"

mkdir -p "${OUT_DIR}"

cat > "${OUT_DIR}/README.md" <<EOF
# Phase 2 Host Live Input Evidence

Run id: ${RUN_ID}

Raw files in this directory are intentionally gitignored. Copy only sanitized
capture ids, expected interpretation, and pass/fail notes into:

- .planning/phases/02-android-host-live-input/02-MANUAL-VALIDATION.md
- docs/evidence/manifests/phase2-host-live-input.jsonl
EOF

"${ADB}" logcat -d -v threadtime > "${OUT_DIR}/logcat-threadtime.txt"
"${ADB}" shell dumpsys bluetooth_manager > "${OUT_DIR}/dumpsys-bluetooth-manager.txt"
"${ADB}" exec-out screencap -p > "${OUT_DIR}/dashboard-screenshot.png" || true

cat > "${OUT_DIR}/manual-notes.md" <<EOF
# Manual Notes

- permission grant:
- BLE connect:
- controls:
- motion preview:
- reload recenter:
- foreground survival:
- phone haptic:
EOF

printf '%s\n' "${OUT_DIR}"
