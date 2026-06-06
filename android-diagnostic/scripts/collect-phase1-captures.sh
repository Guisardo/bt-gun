#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EVIDENCE_DIR="${ROOT_DIR}/.evidence/phase1"
RAW_DIR="${EVIDENCE_DIR}/raw"
HCI_DIR="${EVIDENCE_DIR}/hci"
APP_LOG_DIR="${EVIDENCE_DIR}/app-logs"
ADB_BIN="${ADB:-adb}"

usage() {
  cat <<'USAGE'
Usage:
  collect-phase1-captures.sh --capture-id ID --action ACTION --clue-id CLUE_ID [--package PACKAGE] [--bugreport]

Creates ignored Phase 1 evidence paths, checks adb/device readiness, clears
logcat, records logcat while you perform the hardware action, and writes pointer
metadata for raw/HCI/app-log evidence.

Examples:
  android-diagnostic/scripts/collect-phase1-captures.sh \
    --capture-id trigger-001 \
    --action "trigger down/up" \
    --clue-id ARGUN2021-CONTROL-001 \
    --package com.btgun.diagnostic

  BTGUN_COLLECT_BUGREPORT=1 android-diagnostic/scripts/collect-phase1-captures.sh \
    --capture-id ble-scan-001 \
    --action "BLE scan with gun powered and pairable" \
    --clue-id ARGUN2021-BLE-001
USAGE
}

CAPTURE_ID=""
ACTION=""
CLUE_ID=""
PACKAGE_NAME=""
COLLECT_BUGREPORT="${BTGUN_COLLECT_BUGREPORT:-0}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --capture-id)
      CAPTURE_ID="${2:-}"
      shift 2
      ;;
    --action)
      ACTION="${2:-}"
      shift 2
      ;;
    --clue-id)
      CLUE_ID="${2:-}"
      shift 2
      ;;
    --package)
      PACKAGE_NAME="${2:-}"
      shift 2
      ;;
    --bugreport)
      COLLECT_BUGREPORT="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! "$CAPTURE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
  echo "ERROR: --capture-id must be a stable id like trigger-001" >&2
  exit 2
fi

if [ -z "$ACTION" ] || [ -z "$CLUE_ID" ]; then
  echo "ERROR: --action and --clue-id are required" >&2
  usage >&2
  exit 2
fi

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "ERROR: adb not found. Set ADB=/path/to/adb or add adb to PATH." >&2
  exit 1
fi

mkdir -p "$RAW_DIR" "$HCI_DIR" "$APP_LOG_DIR"

DEVICE_LINES="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
DEVICE_COUNT="$(printf '%s\n' "$DEVICE_LINES" | sed '/^$/d' | wc -l | tr -d ' ')"

if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "ERROR: no adb device in 'device' state." >&2
  "$ADB_BIN" devices >&2
  exit 1
fi

if [ "$DEVICE_COUNT" -gt 1 ]; then
  echo "ERROR: multiple adb devices connected. Set ANDROID_SERIAL for one device." >&2
  "$ADB_BIN" devices >&2
  exit 1
fi

DEVICE_SERIAL="$(printf '%s\n' "$DEVICE_LINES" | sed '/^$/d' | head -n 1)"
LOG_PATH="${APP_LOG_DIR}/${CAPTURE_ID}.logcat.txt"
POINTER_PATH="${RAW_DIR}/${CAPTURE_ID}.pointer.txt"
BUGREPORT_PATH="${HCI_DIR}/${CAPTURE_ID}.bugreport.zip"

cat > "$POINTER_PATH" <<EOF
capture_id=${CAPTURE_ID}
action=${ACTION}
clue_id=${CLUE_ID}
device_serial=${DEVICE_SERIAL}
raw_path=local://.evidence/phase1/raw/${CAPTURE_ID}.pointer.txt
app_log_path=local://.evidence/phase1/app-logs/${CAPTURE_ID}.logcat.txt
hci_path=local://.evidence/phase1/hci/${CAPTURE_ID}.bugreport.zip
package=${PACKAGE_NAME:-unfiltered}
status=pending_capture_review
EOF

echo "adb: $("$ADB_BIN" version | head -n 1)"
echo "device: ${DEVICE_SERIAL}"
echo "capture: ${CAPTURE_ID}"
echo "action: ${ACTION}"
echo "clue: ${CLUE_ID}"
echo "logcat: ${LOG_PATH}"
echo "pointer: ${POINTER_PATH}"

"$ADB_BIN" -s "$DEVICE_SERIAL" logcat -c

echo
echo "Perform hardware action now. Press Enter to stop logcat capture."
if [ -n "$PACKAGE_NAME" ]; then
  "$ADB_BIN" -s "$DEVICE_SERIAL" logcat -v threadtime | awk -v pkg="$PACKAGE_NAME" '$0 ~ pkg || /btgun|diagnostic|Bluetooth|BLE|KeyEvent|MotionEvent|rumble/ { print; fflush() }' > "$LOG_PATH" &
else
  "$ADB_BIN" -s "$DEVICE_SERIAL" logcat -v threadtime > "$LOG_PATH" &
fi
LOGCAT_PID="$!"
trap 'kill "$LOGCAT_PID" >/dev/null 2>&1 || true' EXIT
read -r _ || true
kill "$LOGCAT_PID" >/dev/null 2>&1 || true
wait "$LOGCAT_PID" 2>/dev/null || true
trap - EXIT

if [ "$COLLECT_BUGREPORT" = "1" ]; then
  echo "Collecting optional bugreport/HCI pointer: ${BUGREPORT_PATH}"
  "$ADB_BIN" -s "$DEVICE_SERIAL" bugreport "$BUGREPORT_PATH"
else
  echo "Optional HCI/bugreport not collected. Re-run with --bugreport or BTGUN_COLLECT_BUGREPORT=1 after enabling Bluetooth HCI snoop log."
fi

echo "Capture done. Commit only sanitized manifest/doc updates, not .evidence files."
