package com.btgun.desktop.smoke

import java.nio.file.Paths
import kotlin.io.path.absolute

fun main() {
    val result = BackendSmokeRunner.run(
        platformId = "macos-stub",
        outputFile = Paths.get("build/test-results/btgun-smoke/macos/TEST-btgun-macos-stub.xml"),
        includeHaptic = hapticSmokeEnabled(),
    )
    result.requirePassed()
    println("btgun macos stub smoke XML: ${result.xmlPath.absolute()}")
}

private fun hapticSmokeEnabled(): Boolean =
    System.getProperty("btgun.smoke.haptic").equals("true", ignoreCase = true)

private fun BackendSmokeResult.requirePassed() {
    val failed = cases.filterNot { it.passed }
    if (failed.isNotEmpty()) {
        throw AssertionError("macos-stub smoke failed: ${failed.joinToString { it.name }}")
    }
}
