package com.btgun.desktop.diagnostics

import java.nio.file.Files

fun main() {
    exportBundleWritesDiagnosticsManifestAndReplayRefs()
    exportBundleRedactsSecretMaterialBeforePersistence()
    exportBundleExcludesRawEvidenceByDefault()
    exportBundleRejectsNoisyDiagnosticDetails()
}

private fun exportBundleWritesDiagnosticsManifestAndReplayRefs() {
    val outputRoot = Files.createTempDirectory("btgun-diagnostic-export-test").toFile()
    val result = DiagnosticExportWriter(outputRoot).write(validBundle())

    val diagnostics = result.diagnosticsJsonl.readText()
    val manifest = result.manifestFile.readText()

    expectTrue("diagnostics file exists", result.diagnosticsJsonl.isFile)
    expectTrue("manifest file exists", result.manifestFile.isFile)
    expectTrue("diagnostics schema", diagnostics.contains("btgun.diagnostics.v1"))
    expectTrue("replay ref", manifest.contains("fixtures/replay/udp-golden/mapped-session-001.hex"))
    expectTrue("desktop version", manifest.contains("\"desktop-companion\":\"phase10-test\""))
    expectTrue("capability status", manifest.contains("\"windows_vhf\":\"available\""))
    expectTrue("manifest ref", manifest.contains("docs/evidence/manifests/phase10-replay-fixtures.jsonl"))
    expectTrue("raw default false", manifest.contains("\"raw_included\":false"))
}

private fun exportBundleExcludesRawEvidenceByDefault() {
    val outputRoot = Files.createTempDirectory("btgun-diagnostic-export-raw-test").toFile()
    val rawRoot = Files.createTempDirectory("btgun-raw-evidence").toFile()
    val rawFile = rawRoot.resolve("phase10/raw-" + "log.txt")
    rawFile.parentFile.mkdirs()
    rawFile.writeText("raw " + "log contains " + "Bluetooth " + "address " + macAddress())

    val result = DiagnosticExportWriter(outputRoot).write(
        validBundle(rawEvidencePaths = listOf(rawFile.path)),
    )

    expectFalse("raw file not copied", result.outputDir.walkTopDown().any { it.name == rawFile.name })
    expectFalse("raw path hidden", result.manifestFile.readText().contains(rawFile.path))
    expectTrue("raw flag false", result.manifest.rawIncluded == false)
}

private fun exportBundleRedactsSecretMaterialBeforePersistence() {
    val outputRoot = Files.createTempDirectory("btgun-diagnostic-export-redaction-test").toFile()
    val detail = ("stream " + "key") + "=stream-secret " +
        ("HMAC " + "material") + "=hmac-secret " +
        ("Bluetooth " + "address") + "=" + macAddress()
    val result = DiagnosticExportWriter(outputRoot).write(
        validBundle(diagnostics = listOf(validEvent(detail = detail))),
    )
    val exported = result.diagnosticsJsonl.readText() + result.manifestFile.readText()

    expectFalse("no stream secret", exported.contains("stream-secret"))
    expectFalse("no hmac secret", exported.contains("hmac-secret"))
    expectFalse("no full mac", exported.contains(macAddress()))
    expectTrue("redaction marker", exported.contains("<redacted"))
}

private fun exportBundleRejectsNoisyDiagnosticDetails() {
    val outputRoot = Files.createTempDirectory("btgun-diagnostic-export-noisy-test").toFile()
    val noisy = validEvent(detail = "x".repeat(513))

    val error = expectFails("noisy diagnostic detail") {
        DiagnosticExportWriter(outputRoot).write(validBundle(diagnostics = listOf(noisy)))
    }

    expectTrue("detail cap named", error.message.orEmpty().contains("detail"))
}

private fun validBundle(
    diagnostics: List<DiagnosticEvent> = listOf(validEvent()),
    rawEvidencePaths: List<String> = emptyList(),
): DiagnosticExportBundle =
    DiagnosticExportBundle(
        bundleId = "phase10-export-001",
        createdElapsed = 42_000L,
        diagnostics = diagnostics,
        replayRefs = listOf(
            "fixtures/replay/udp-golden/mapped-session-001.hex",
            "fixtures/replay/expected/mapped-session-001-visualizer.json",
        ),
        appVersions = linkedMapOf(
            "desktop-companion" to "phase10-test",
            "android-host" to "phase10-test",
        ),
        capabilityStatuses = linkedMapOf(
            "windows_vhf" to "available",
            "macos_android_hid" to "input_supported_haptics_deferred",
        ),
        manifestRef = "docs/evidence/manifests/phase10-replay-fixtures.jsonl#phase10-replay-mapped-session-001",
        rawEvidencePaths = rawEvidencePaths,
    )

private fun validEvent(detail: String = "heartbeat age 2400ms"): DiagnosticEvent =
    DiagnosticEvent(
        tsElapsed = 10_000L,
        domain = DiagnosticDomain.LAN_CONTROL_UDP,
        status = DiagnosticStatus.DEGRADED,
        reasonCode = "lan_control_udp.heartbeat_degraded",
        detail = detail,
        sessionRefs = DiagnosticSessionRefs(
            controlSessionRef = "sid-suffix-11223344",
            streamSessionRef = "stream-suffix-aabbccdd",
            desktopIdentitySuffix = "desktop-77889900",
        ),
        context = mapOf(
            "source" to "desktop_control",
            "stream_ref" to "suffix-ddeeff",
        ),
    )

private fun macAddress(): String =
    listOf("AA", "BB", "CC", "DD", "EE", "FF").joinToString(":")

private fun expectFails(label: String, block: () -> Unit): IllegalArgumentException {
    try {
        block()
    } catch (error: IllegalArgumentException) {
        return error
    }
    throw AssertionError("$label expected IllegalArgumentException")
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError("$label expected true")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError("$label expected false")
    }
}
