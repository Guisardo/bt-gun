package com.btgun.desktop.docs

import java.io.File

fun main() {
    requiredDocsExist()
    androidSetupDocCoversPack01()
    lanSecurityDocCoversPack04()
    troubleshootingDocCoversDiagnosticsReplayAndExport()
    limitsDocCoversPack05RowsAndStatuses()
    knownLimitRowsRequireEvidenceAndNextProof()
    docsExcludeForbiddenEvidenceTerms()
    limitsDocAvoidsSoftenedUnsupportedLanguage()
}

private val requiredDocs = listOf(
    "docs/setup/android-build-device-testing.md",
    "docs/protocol/lan-session-security-v1.md",
    "docs/diagnostics/replay-and-troubleshooting.md",
    "docs/limits/v1-compatibility-limits.md",
)

private val phase10PublicArtifacts = requiredDocs + listOf(
    "docs/v1.md",
    "docs/evidence/manifests/phase10-diagnostic-export.jsonl",
    "docs/evidence/manifests/phase10-replay-fixtures.jsonl",
    "docs/evidence/manifests/phase10-v1-closeout.jsonl",
    "fixtures/replay/README.md",
    "fixtures/replay/udp-golden/mapped-session-001.hex",
    "fixtures/replay/udp-golden/mapped-session-001.jsonl",
    "fixtures/replay/expected/mapped-session-001-visualizer.json",
)

private fun requiredDocsExist() {
    requiredDocs.forEach { path ->
        expectTrue("required doc exists: $path", repoFile(path).isFile)
    }
}

private fun androidSetupDocCoversPack01() {
    val doc = readDoc("docs/setup/android-build-device-testing.md")
    listOf(
        "JAVA_HOME=/opt/homebrew/opt/openjdk@17",
        "GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home",
        "ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk",
        "adb logcat",
        "Start Bluetooth gamepad",
        "real gun",
        "LAN mode",
        "Common Blockers",
    ).forEach { required ->
        expectContains("Android setup includes $required", doc, required)
    }
}

private fun lanSecurityDocCoversPack04() {
    val doc = readDoc("docs/protocol/lan-session-security-v1.md")
    listOf(
        "pairing",
        "proof",
        "authenticated",
        "InputReplayGuard",
        "reserved_haptic_command",
        "haptic_result",
        "fixtures/replay",
        "Diagnostic export contract",
        "Redaction Model",
    ).forEach { required ->
        expectContains("LAN security includes $required", doc, required)
    }
    listOf(
        "docs/protocol/lan-pairing-v1.md",
        "docs/protocol/input-stream-v1-fixtures.md",
    ).forEach { canonical ->
        expectContains("LAN security links canonical source $canonical", doc, canonical)
    }
}

private fun troubleshootingDocCoversDiagnosticsReplayAndExport() {
    val doc = readDoc("docs/diagnostics/replay-and-troubleshooting.md")
    listOf(
        "gun_ble",
        "sensor_motion",
        "lan_control_udp",
        "profile_mapping",
        "hid_backend_haptics",
        "fixtures/replay",
        "DiagnosticExport",
        "redaction",
        "Failure Routing",
    ).forEach { required ->
        expectContains("troubleshooting includes $required", doc, required)
    }
}

private fun limitsDocCoversPack05RowsAndStatuses() {
    val doc = readDoc("docs/limits/v1-compatibility-limits.md")
    listOf("supported", "unsupported", "fallback", "deferred").forEach { status ->
        expectContains("limits includes status $status", doc, status)
    }
    listOf(
        "Direct desktop-to-gun Bluetooth",
        "Physical gun motor rumble",
        "Game-specific presets",
        "macOS HID haptics",
        "Android HID phone compatibility",
        "Windows VHF virtual joystick path",
    ).forEach { row ->
        expectContains("limits includes row $row", doc, row)
    }
}

private fun knownLimitRowsRequireEvidenceAndNextProof() {
    val doc = readDoc("docs/limits/v1-compatibility-limits.md")
    listOf(
        "Direct desktop-to-gun Bluetooth",
        "Physical gun motor rumble",
        "Game-specific presets",
        "macOS HID haptics",
        "Android HID phone compatibility",
        "CoreHID or DriverKit macOS virtual HID",
        "Custom gun-specific HID report",
        "Multi-gun sessions",
    ).forEach { rowName ->
        val cells = matrixRowCells(doc, rowName)
        expectTrue("$rowName row has at least five cells", cells.size >= 5)
        expectTrue("$rowName status is non-supported", cells[1] in setOf("unsupported", "fallback", "deferred"))
        expectTrue("$rowName has evidence pointer", cells[3].contains("docs/") || cells[3].contains(".planning/") || cells[3].contains("Phase"))
        expectTrue("$rowName has next proof", cells[4].isNotBlank() && !cells[4].equals("none", ignoreCase = true))
    }
}

private fun docsExcludeForbiddenEvidenceTerms() {
    val docs = phase10PublicArtifacts.associateWith(::readDoc)
    val forbiddenLiterals = listOf(
        "qr_" + "secret",
        "manual " + "code",
        "pairing_" + "proof",
        "stream " + "key",
        "HMAC " + "key",
        "private " + "key",
        "raw " + "screenshot",
        "raw " + "log",
        "device " + "serial",
        "Android " + "ID",
    )
    val bluetoothAddress = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")
    docs.forEach { (path, contents) ->
        forbiddenLiterals.forEach { forbidden ->
            expectFalse("$path excludes $forbidden", contents.contains(forbidden))
        }
        expectFalse("$path excludes full Bluetooth-style address", bluetoothAddress.containsMatchIn(contents))
    }
}

private fun limitsDocAvoidsSoftenedUnsupportedLanguage() {
    val doc = readDoc("docs/limits/v1-compatibility-limits.md")
    listOf("may work", "might work", "probably works", "should work").forEach { softened ->
        expectFalse("limits avoids softened phrase $softened", doc.contains(softened, ignoreCase = true))
    }
}

private fun matrixRowCells(doc: String, rowName: String): List<String> {
    val row = doc.lineSequence()
        .firstOrNull { line -> line.startsWith("|") && line.contains(rowName, ignoreCase = true) }
        ?: throw AssertionError("missing matrix row: $rowName")
    return row.trim('|')
        .split('|')
        .map { cell -> cell.trim() }
}

private fun readDoc(path: String): String = repoFile(path).readText()

private fun repoFile(path: String): File {
    val candidates = listOf(
        File(path),
        File("..", path),
    )
    return candidates.firstOrNull { it.exists() }
        ?: candidates.first()
}

private fun expectContains(label: String, actual: String, expected: String) {
    if (!actual.contains(expected)) {
        throw AssertionError("$label: missing <$expected>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) throw AssertionError(label)
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) throw AssertionError(label)
}
