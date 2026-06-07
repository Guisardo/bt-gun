#!/usr/bin/env node
import fs from "node:fs";

const inputPath = process.argv[2];
if (!inputPath) {
  console.error("usage: node tools/phase1/map-joystick-capture.mjs <logcat-file>");
  process.exit(2);
}

const text = fs.readFileSync(inputPath, "utf8");
const rows = [];
for (const line of text.split(/\r?\n/)) {
  const start = line.indexOf("{");
  if (start < 0) continue;
  try {
    const row = JSON.parse(line.slice(start));
    if (
      row.report === "ble_gatt_characteristic_changed" &&
      String(row.characteristic_uuid).toLowerCase() === "0000fff3-0000-1000-8000-00805f9b34fb"
    ) {
      rows.push(row);
    }
  } catch {
    // Ignore non-diagnostic logcat lines.
  }
}

const joystickDirections = new Map([
  ["B4", "right"],
  ["B5", "up"],
  ["B6", "left"],
  ["B7", "down"],
]);

const byPayload = new Map();
for (const row of rows) {
  const key = row.payload_ascii || row.payload_hex || "";
  const entry = byPayload.get(key) || {
    payload_ascii: row.payload_ascii || "",
    payload_hex: row.payload_hex || "",
    count: 0,
    first_ts_elapsed_ms: row.ts_elapsed_ms,
    last_ts_elapsed_ms: row.ts_elapsed_ms,
  };
  entry.count += 1;
  entry.last_ts_elapsed_ms = row.ts_elapsed_ms;
  byPayload.set(key, entry);
}

const statePoints = new Map();
const activeDirections = new Set();
let markerSeen = false;

function activePoint() {
  const axisX = (activeDirections.has("right") ? 1 : 0) - (activeDirections.has("left") ? 1 : 0);
  const axisY = (activeDirections.has("up") ? 1 : 0) - (activeDirections.has("down") ? 1 : 0);
  return {
    axisX,
    axisY,
    controls: [...activeDirections].sort(),
  };
}

function recordStatePoint(row) {
  const point = activePoint();
  const key = `${point.axisX},${point.axisY}:${point.controls.join("+")}`;
  const entry = statePoints.get(key) || {
    axisX: point.axisX,
    axisY: point.axisY,
    controls: point.controls,
    count: 0,
    first_ts_elapsed_ms: row.ts_elapsed_ms,
    last_ts_elapsed_ms: row.ts_elapsed_ms,
  };
  entry.count += 1;
  entry.last_ts_elapsed_ms = row.ts_elapsed_ms;
  statePoints.set(key, entry);
}

for (const line of text.split(/\r?\n/)) {
  const start = line.indexOf("{");
  if (start < 0) continue;
  let row;
  try {
    row = JSON.parse(line.slice(start));
  } catch {
    continue;
  }
  if (row.report === "joystick_sweep_marker") {
    activeDirections.clear();
    markerSeen = true;
    recordStatePoint(row);
    continue;
  }
  if (!markerSeen) continue;
  if (
    row.report !== "ble_gatt_characteristic_changed" ||
    String(row.characteristic_uuid).toLowerCase() !== "0000fff3-0000-1000-8000-00805f9b34fb"
  ) {
    continue;
  }
  const match = String(row.payload_ascii || "").match(/^(B[4567])(DOWN|UP)$/);
  if (!match) continue;
  const direction = joystickDirections.get(match[1]);
  if (!direction) continue;
  if (match[2] === "DOWN") {
    activeDirections.add(direction);
  } else {
    activeDirections.delete(direction);
  }
  recordStatePoint(row);
}

const ordered = [...byPayload.values()].sort((left, right) =>
  String(left.payload_ascii || left.payload_hex).localeCompare(String(right.payload_ascii || right.payload_hex)),
);

const orderedStatePoints = [...statePoints.values()].sort((left, right) => {
  if (left.axisY !== right.axisY) return right.axisY - left.axisY;
  return left.axisX - right.axisX;
});

console.log(JSON.stringify({
  schema: "btgun.joystick_capture_summary.v1",
  source: inputPath,
  fff3_notification_count: rows.length,
  unique_payload_count: ordered.length,
  payloads: ordered,
  state_point_count: orderedStatePoints.length,
  state_points: orderedStatePoints,
}, null, 2));
