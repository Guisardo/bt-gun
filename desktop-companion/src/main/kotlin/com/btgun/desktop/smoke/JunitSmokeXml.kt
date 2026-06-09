package com.btgun.desktop.smoke

import java.nio.file.Files
import java.nio.file.Path

data class SmokeCaseResult(
    val name: String,
    val passed: Boolean,
    val message: String,
    val elapsedSeconds: Double,
) {
    init {
        require(name.isNotBlank()) { "name must be nonblank" }
        require(elapsedSeconds >= 0.0) { "elapsedSeconds must be non-negative" }
    }
}

object JunitSmokeXml {
    fun render(suiteName: String, cases: List<SmokeCaseResult>): String {
        require(suiteName.isNotBlank()) { "suiteName must be nonblank" }
        val failures = cases.count { !it.passed }
        val totalSeconds = cases.sumOf { it.elapsedSeconds }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<testsuite name="${suiteName.xmlEscaped()}" tests="${cases.size}" failures="$failures" time="${totalSeconds.formatSeconds()}">""",
            )
            cases.forEach { case ->
                append("""  <testcase name="${case.name.xmlEscaped()}" time="${case.elapsedSeconds.formatSeconds()}"""")
                if (case.passed) {
                    appendLine("/>")
                } else {
                    appendLine(">")
                    appendLine("""    <failure message="${case.message.xmlEscaped()}">${case.message.xmlEscaped()}</failure>""")
                    appendLine("  </testcase>")
                }
            }
            appendLine("</testsuite>")
        }
    }

    fun write(path: Path, suiteName: String, cases: List<SmokeCaseResult>) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, render(suiteName = suiteName, cases = cases))
    }

    private fun String.xmlEscaped(): String =
        buildString {
            this@xmlEscaped.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&apos;"
                        else -> char
                    },
                )
            }
        }

    private fun Double.formatSeconds(): String =
        "%.6f".format(java.util.Locale.US, this)
}
