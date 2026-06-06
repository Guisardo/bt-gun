#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const CAPTURE_SCHEMA = "btgun.phase1.capture_manifest.v1";
const NORMALIZED_SCHEMA = "btgun.ipega.normalized.v1";

const REQUIRED_REFS = [
  "docs/refs/ARGun2021.apk",
  "docs/refs/AR Cher_20200905_Apkpure.xapk",
  "docs/refs/WorldsAR_14.0_apkcombo.com.xapk",
  "docs/refs/ARGun Library_1.0.1_apkcombo.com.apk",
  "docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk",
];

const REQUIRED_IGNORES = [
  ".evidence/phase1/raw/",
  ".evidence/phase1/hci/",
  ".evidence/phase1/decompile/",
  ".evidence/phase1/app-logs/",
  "android-diagnostic/build/",
];

const REQUIRED_DOCS = {
  manifest: "docs/evidence/manifests/phase1-captures.jsonl",
  fixtureReadme: "fixtures/ipega/normalized/README.md",
  inventory: "docs/protocol/ipega-phase1-inventory.md",
  clues: "docs/protocol/ipega-phase1-clues.md",
};

function rel(file) {
  return path.join(ROOT, file);
}

function exists(file) {
  return fs.existsSync(rel(file));
}

function read(file) {
  return fs.readFileSync(rel(file), "utf8");
}

function fail(message) {
  throw new Error(message);
}

function assertStableId(value, field, source) {
  if (typeof value !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(value)) {
    fail(`${source}: ${field} must be a stable id`);
  }
}

function parseJsonlText(text, source) {
  const rows = [];
  const lines = text.split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index].trim();
    if (!line) continue;
    try {
      rows.push({ line: index + 1, row: JSON.parse(line) });
    } catch (error) {
      fail(`${source}:${index + 1}: malformed JSONL: ${error.message}`);
    }
  }
  return rows;
}

function parseJsonlFile(file) {
  if (!exists(file)) fail(`${file}: missing`);
  return parseJsonlText(read(file), file);
}

function validateManifestRows(rows) {
  const captureIds = new Set();
  const normalizedFixtures = new Set();
  for (const { line, row } of rows) {
    const source = `${REQUIRED_DOCS.manifest}:${line}`;
    if (row.schema !== CAPTURE_SCHEMA) fail(`${source}: schema must be ${CAPTURE_SCHEMA}`);
    if (row.record_type === "scaffold") continue;
    assertStableId(row.capture_id, "capture_id", source);
    captureIds.add(row.capture_id);
    for (const field of ["source_ref", "clue_id", "action", "normalized_fixture"]) {
      if (typeof row[field] !== "string" || row[field].length === 0) {
        fail(`${source}: ${field} is required`);
      }
    }
    if (!row.raw_path && !row.hci_path && !row.app_log_path) {
      fail(`${source}: one raw_path, hci_path, or app_log_path is required`);
    }
    normalizedFixtures.add(row.normalized_fixture);
  }
  return { captureIds, normalizedFixtures };
}

function fixtureFiles() {
  const dir = rel("fixtures/ipega/normalized");
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((name) => name.endsWith(".jsonl"))
    .map((name) => `fixtures/ipega/normalized/${name}`)
    .sort();
}

function validateFixtureRows(files) {
  const fixtureIds = new Set();
  const clueIds = new Set();
  const captureIds = new Set();
  for (const file of files) {
    const rows = parseJsonlFile(file);
    for (const { line, row } of rows) {
      const source = `${file}:${line}`;
      if (row.schema !== NORMALIZED_SCHEMA) fail(`${source}: schema must be ${NORMALIZED_SCHEMA}`);
      assertStableId(row.fixture_id, "fixture_id", source);
      if (!Number.isInteger(row.seq) || row.seq < 1) fail(`${source}: seq must be a positive integer`);
      for (const field of ["control", "kind", "phase", "raw_ref", "clue_id", "capture_id"]) {
        if (typeof row[field] !== "string" || row[field].length === 0) {
          fail(`${source}: ${field} is required`);
        }
      }
      if (!Object.prototype.hasOwnProperty.call(row, "value")) fail(`${source}: value is required`);
      fixtureIds.add(row.fixture_id);
      clueIds.add(row.clue_id);
      captureIds.add(row.capture_id);
    }
  }
  return { fixtureIds, clueIds, captureIds };
}

function requireGitignore() {
  if (!exists(".gitignore")) fail(".gitignore: missing");
  const body = read(".gitignore");
  for (const pattern of REQUIRED_IGNORES) {
    if (!body.includes(pattern)) fail(`.gitignore: missing ${pattern}`);
  }
}

function requireFixtureReadme() {
  if (!exists(REQUIRED_DOCS.fixtureReadme)) fail(`${REQUIRED_DOCS.fixtureReadme}: missing`);
  const body = read(REQUIRED_DOCS.fixtureReadme);
  for (const token of [NORMALIZED_SCHEMA, "raw_ref", "clue_id", "capture_id"]) {
    if (!body.includes(token)) fail(`${REQUIRED_DOCS.fixtureReadme}: missing ${token}`);
  }
}

function validateInventory({ required }) {
  if (!exists(REQUIRED_DOCS.inventory)) {
    if (required) fail(`${REQUIRED_DOCS.inventory}: missing`);
    return { present: false };
  }
  const body = read(REQUIRED_DOCS.inventory);
  for (const ref of REQUIRED_REFS) {
    if (!body.includes(ref)) fail(`${REQUIRED_DOCS.inventory}: missing ${ref}`);
  }
  for (const token of ["Package", "Target SDK", "Permissions", "Validity", "First-pass"]) {
    if (!body.toLowerCase().includes(token.toLowerCase())) {
      fail(`${REQUIRED_DOCS.inventory}: missing ${token} field`);
    }
  }
  if (!body.includes("ARGunPro_1.0.19_apkcombo.com.xapk") || !/0 bytes|invalid/i.test(body)) {
    fail(`${REQUIRED_DOCS.inventory}: 0-byte ARGunPro ref must be marked invalid`);
  }
  return { present: true };
}

function clueIdsFromMarkdown(body) {
  const ids = new Set();
  for (const match of body.matchAll(/\b[A-Z0-9]+(?:-[A-Z0-9]+)+-\d{3}\b/g)) {
    ids.add(match[0]);
  }
  return ids;
}

function validateClues({ required, manifest, fixtures }) {
  if (!exists(REQUIRED_DOCS.clues)) {
    if (required) fail(`${REQUIRED_DOCS.clues}: missing`);
    return { present: false, clueIds: new Set() };
  }
  const body = read(REQUIRED_DOCS.clues);
  for (const token of ["clue_id", "source ref", "hypothesis", "planned hardware test", "unverified"]) {
    if (!body.toLowerCase().includes(token.toLowerCase())) {
      fail(`${REQUIRED_DOCS.clues}: missing ${token}`);
    }
  }
  const clueIds = clueIdsFromMarkdown(body);
  if (clueIds.size === 0) fail(`${REQUIRED_DOCS.clues}: no stable clue ids found`);

  const verifiedLines = body
    .split(/\r?\n/)
    .map((line, index) => ({ line, number: index + 1 }))
    .filter(({ line }) => /\bverified\b/i.test(line) && !/\bunverified\b/i.test(line));

  for (const { line, number } of verifiedLines) {
    const ids = [...clueIdsFromMarkdown(line)];
    if (ids.length === 0) fail(`${REQUIRED_DOCS.clues}:${number}: verified row lacks clue_id`);
    for (const id of ids) {
      const hasCapture = [...manifest.captureIds].some((captureId) => line.includes(captureId));
      const hasFixture = [...manifest.normalizedFixtures].some((fixture) => line.includes(fixture));
      const hasFixtureRow = fixtures.clueIds.has(id);
      if (!hasCapture || !hasFixture || !hasFixtureRow) {
        fail(`${REQUIRED_DOCS.clues}:${number}: verified status requires static clue, capture, and normalized fixture linkage`);
      }
    }
  }
  return { present: true, clueIds };
}

function runQuick({ full = false } = {}) {
  const notes = [];
  requireGitignore();
  requireFixtureReadme();
  const manifest = validateManifestRows(parseJsonlFile(REQUIRED_DOCS.manifest));
  const fixtures = validateFixtureRows(fixtureFiles());
  const inventory = validateInventory({ required: full });
  const clues = validateClues({ required: full, manifest, fixtures });
  if (!inventory.present) notes.push("inventory missing: allowed before Task 2");
  if (!clues.present) notes.push("clue index missing: allowed before Task 3");
  return notes;
}

function runSelfTest() {
  const valid = parseJsonlText('{"schema":"x","id":1}\n\n{"schema":"x","id":2}\n', "self-test-valid");
  if (valid.length !== 2) fail("self-test: valid JSONL parse count wrong");
  let malformedRejected = false;
  try {
    parseJsonlText('{"schema":"x"}\n{bad}\n', "self-test-malformed");
  } catch {
    malformedRejected = true;
  }
  if (!malformedRejected) fail("self-test: malformed JSONL was not rejected");
  validateManifestRows([
    {
      line: 1,
      row: {
        schema: CAPTURE_SCHEMA,
        capture_id: "trigger-001",
        source_ref: "docs/refs/ARGun2021.apk",
        clue_id: "ARGUN2021-BT-001",
        action: "trigger down/up",
        raw_path: "local://.evidence/phase1/raw/trigger-001.bin",
        normalized_fixture: "fixtures/ipega/normalized/trigger.jsonl",
      },
    },
  ]);
  validateFixtureRowsFromMemory();
}

function validateFixtureRowsFromMemory() {
  const oldRead = fs.readFileSync;
  const oldExists = fs.existsSync;
  try {
    fs.existsSync = (file) => file.endsWith("self-test.jsonl") || oldExists(file);
    fs.readFileSync = (file, options) => {
      if (file.endsWith("self-test.jsonl")) {
        return '{"schema":"btgun.ipega.normalized.v1","fixture_id":"trigger-001","seq":1,"control":"trigger","kind":"button","phase":"down","value":1,"raw_ref":"local://.evidence/phase1/raw/trigger-001.bin","clue_id":"ARGUN2021-BT-001","capture_id":"trigger-001"}\n';
      }
      return oldRead(file, options);
    };
    validateFixtureRows(["self-test.jsonl"]);
  } finally {
    fs.readFileSync = oldRead;
    fs.existsSync = oldExists;
  }
}

function main() {
  const mode = process.argv[2];
  if (!["--self-test", "--quick", "--full"].includes(mode)) {
    console.error("Usage: node tools/phase1/validate-fixtures.mjs --self-test|--quick|--full");
    process.exit(2);
  }
  try {
    if (mode === "--self-test") runSelfTest();
    if (mode === "--quick") {
      const notes = runQuick({ full: false });
      for (const note of notes) console.log(`NOTE ${note}`);
    }
    if (mode === "--full") runQuick({ full: true });
    console.log(`PASS ${mode}`);
  } catch (error) {
    console.error(`FAIL ${mode}: ${error.message}`);
    process.exit(1);
  }
}

main();
