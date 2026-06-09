package com.btgun.desktop.smoke

import java.nio.file.Paths
import kotlin.io.path.absolute

fun main() {
    val result = BackendSmokeRunner.run(
        platformId = "windows-stub",
        outputFile = Paths.get("build/test-results/btgun-smoke/windows/TEST-btgun-windows-stub.xml"),
    )
    result.requirePassed()
    println("btgun windows stub smoke XML: ${result.xmlPath.absolute()}")
}

private fun BackendSmokeResult.requirePassed() {
    val failed = cases.filterNot { it.passed }
    if (failed.isNotEmpty()) {
        throw AssertionError("windows-stub smoke failed: ${failed.joinToString { it.name }}")
    }
}
