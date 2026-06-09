package com.btgun.desktop.smoke

import java.nio.file.Files

fun main() {
    junitXmlEscapesMetacharacters()
    macosSmokeRunnerReplaysReceiverBeforeBackendPublish()
    smokeXmlDoesNotExposeSecrets()
}

private fun junitXmlEscapesMetacharacters() {
    val xml = JunitSmokeXml.render(
        suiteName = "btgun-desktop-backend-macos-stub<&\"'",
        cases = listOf(
            SmokeCaseResult(
                name = "receiver < accepted & published",
                passed = true,
                message = "all good <&>\"'",
                elapsedSeconds = 0.125,
            ),
            SmokeCaseResult(
                name = "failure < case",
                passed = false,
                message = "bad <&>\"'",
                elapsedSeconds = 0.25,
            ),
        ),
    )

    expectContains("suite element", xml, "<testsuite name=\"btgun-desktop-backend-macos-stub&lt;&amp;&quot;&apos;\"")
    expectContains("test count", xml, "tests=\"2\"")
    expectContains("failure count", xml, "failures=\"1\"")
    expectContains("testcase", xml, "<testcase name=\"receiver &lt; accepted &amp; published\"")
    expectContains("failure element", xml, "<failure message=\"bad &lt;&amp;&gt;&quot;&apos;\"")
}

private fun macosSmokeRunnerReplaysReceiverBeforeBackendPublish() {
    val output = Files.createTempFile("btgun-macos-smoke", ".xml")
    val result = BackendSmokeRunner.run(
        platformId = "macos-stub",
        outputFile = output,
    )

    expectEquals("platform", "macos-stub", result.platformId)
    expectEquals("xml path", output, result.xmlPath)
    expectTrue("all cases pass", result.cases.all { it.passed })
    expectTrue("both fixture datagrams accepted", result.acceptedFixtureSequences.containsAll(listOf(42L, 43L)))
    expectTrue("publish happens after accepted input", result.publishedStates.size >= 2)
    val finalState = result.finalState
    expectEquals("final x", true, finalState.x)
    expectEquals("final stickX", -32768, finalState.stickX)
    expectEquals("final stickY", 32767, finalState.stickY)
    expectEquals("final sequence", 43L, finalState.sourceSequence)

    val xml = Files.readString(output)
    expectContains("parseable suite", xml, "<testsuite name=\"btgun-desktop-backend-macos-stub\"")
    expectContains("zero failures", xml, "failures=\"0\"")
    expectContains("receiver case", xml, "receiver-accepted-snapshot")
    expectContains("backend case", xml, "backend-published-edge-state")
}

private fun smokeXmlDoesNotExposeSecrets() {
    val output = Files.createTempFile("btgun-windows-smoke", ".xml")
    BackendSmokeRunner.run(
        platformId = "windows-stub",
        outputFile = output,
    )

    val xml = Files.readString(output)
    SECRET_PATTERNS.forEach { forbidden ->
        if (xml.contains(forbidden, ignoreCase = true)) {
            throw AssertionError("smoke XML leaked forbidden text <$forbidden>")
        }
    }
}

private fun expectContains(label: String, text: String, needle: String) {
    if (!text.contains(needle)) {
        throw AssertionError("$label expected <$needle> in <$text>")
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError("$label expected true")
    }
}

private val SECRET_PATTERNS = listOf(
    "qr_secret",
    "manual code",
    "proof",
    "stream auth key",
    "HMAC key",
    "private key",
    "ASNFZ4mrze",
    "0123456789abcdef",
)
