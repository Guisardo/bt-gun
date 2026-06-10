package com.btgun.desktop.smoke

import com.btgun.desktop.backend.BackendCapabilityPresets
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.backend.macos.MacosBackendRuntimeConfig
import com.btgun.desktop.backend.macos.MacosHidHelper
import com.btgun.desktop.backend.macos.MacosHidHelperConfig
import com.btgun.desktop.backend.macos.MacosVirtualControllerBackend
import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.system.measureNanoTime

fun main() {
    val outputFile = Paths.get("build/test-results/btgun-smoke/macos-corehid/TEST-btgun-macos-corehid.xml")
    val helperPath = System.getProperty(HELPER_PATH_PROPERTY)?.trim().orEmpty()
    val cases = mutableListOf<SmokeCaseResult>()

    cases += timedCase("macos-corehid-helper-artifact-present") {
        validateHelperArtifact(helperPath)
    }
    cases += timedCase("macos-corehid-runtime-config-requires-helper-path") {
        validateRuntimeConfig(helperPath)
    }
    cases += timedCase("macos-corehid-capabilities-real-output-gated") {
        validateCapabilitiesGate()
    }
    cases += timedCase("macos-corehid-helper-startup") {
        validateHelperStartup(validateHelperArtifact(helperPath))
    }
    cases += timedCase("macos-corehid-publishes-plan04-fixture") {
        validatePlan04Publish(validateHelperArtifact(helperPath))
    }
    cases += timedCase("phase7-corehid-cli-enumeration-hidutil-list") {
        validateHidutilVisibility()
    }
    cases += timedCase("phase7-corehid-ui-visible-ioreg-sanitized") {
        validateIoregVisibility()
    }

    JunitSmokeXml.write(outputFile, "btgun-desktop-backend-macos-corehid", cases)
    val failed = cases.filterNot { it.passed }
    if (failed.isNotEmpty()) {
        throw AssertionError("macos-corehid smoke failed: ${failed.joinToString { it.name }}; phase7-corehid-gate=blocked")
    }
    println("btgun macos-corehid smoke XML: ${outputFile.absolute()}")
}

private fun validateHelperArtifact(helperPath: String): Path {
    require(helperPath.isNotBlank()) { "$HELPER_PATH_PROPERTY is required" }
    val path = Paths.get(helperPath)
    require(Files.isRegularFile(path)) { "BtGunMacosHidHelper not found" }
    require(path.fileName.toString() == "BtGunMacosHidHelper") { "helper path must point to BtGunMacosHidHelper" }
    require(Files.isExecutable(path)) { "BtGunMacosHidHelper is not executable" }
    return path
}

private fun validateRuntimeConfig(helperPath: String) {
    val config = MacosBackendRuntimeConfig(helperPath = helperPath)
    require(config.helperCommand == listOf(helperPath)) { "macos runtime command did not preserve helper path" }
    require(config.helperPath == helperPath) { "macos runtime helper path mismatch" }
}

private fun validateCapabilitiesGate() {
    val blocked = BackendCapabilityPresets.macosCoreHid()
    require(blocked.platform == "macos-corehid") { "unexpected macos platform id" }
    require(!blocked.haptics.outputReport) { "macos-corehid must not claim output report before proof" }
    require(blocked.limitations.any { it.feature == "os-visible-device" }) {
        "macos-corehid must report os-visible-device limitation before proof"
    }
}

private fun validateHelperStartup(helperPath: Path) {
    val helper = MacosHidHelper(MacosHidHelperConfig(command = listOf(helperPath.toString())))
    try {
        val status = helper.readStatus()
        require(status.deviceActive) { "corehid-runtime-blocked: helper status inactive" }
    } finally {
        helper.close()
    }
}

private fun validatePlan04Publish(helperPath: Path) {
    val helper = MacosHidHelper(MacosHidHelperConfig(command = listOf(helperPath.toString())))
    val backend = MacosVirtualControllerBackend(helper = helper)
    backend.start()
    try {
        val accepted = acceptedPlan04Fixture()
        val state = UdpControllerStateAdapter.toState(accepted.input)
        val publish = backend.publish(state)
        require(publish == BackendPublishResult.Published) { "corehid-runtime-blocked: real macos-corehid publish failed" }
        require(backend.currentState.sourceSequence == 42L) { "plan04 fixture sequence not published" }
    } finally {
        backend.stop("macos-corehid smoke complete")
    }
}

private fun validateHidutilVisibility() {
    val output = runCommand(
        listOf("hidutil", "list", "--matching", """{"VendorID":0x1209,"ProductID":0xB707}"""),
    )
    require(output.exitCode == 0) { "corehid-visibility-failed: hidutil list failed" }
    require(output.stdout.contains("BT Gun Virtual Joystick") || output.stdout.contains("0xB707", ignoreCase = true)) {
        "corehid-visibility-failed: hidutil did not show BT Gun Virtual Joystick"
    }
}

private fun validateIoregVisibility() {
    val output = runCommand(IOREG_COMMAND)
    require(output.exitCode == 0) { "corehid-visibility-failed: ioreg failed" }
    require(
        output.stdout.contains("BT Gun Virtual Joystick") &&
            output.stdout.contains("VendorID") &&
            output.stdout.contains("ProductID"),
    ) {
        "corehid-visibility-failed: ioreg sanitized output missing BT Gun VID/PID"
    }
}

private fun acceptedPlan04Fixture(): UdpInputReceiverResult.Accepted {
    val receiver = UdpInputReceiver().start(
        trustedSession = CONTROL_SESSION_ID,
        config = fixtureConfig(),
    )
    return when (val result = receiver.handleDatagram(GOLDEN_SNAPSHOT_FRAME_HEX.hexToBytes(), RECEIVED_ELAPSED_NANOS)) {
        is UdpInputReceiverResult.Accepted -> result
        is UdpInputReceiverResult.Rejected -> throw IllegalStateException("Plan 04 fixture rejected")
        UdpInputReceiverResult.Stopped -> throw IllegalStateException("receiver stopped")
    }
}

private fun fixtureConfig(): InputStreamConfig =
    InputStreamConfig(
        streamSessionIdHex = STREAM_SESSION_ID_HEX,
        udpHost = "127.0.0.1",
        udpPort = 41234,
        hmacSha256KeyBase64Url = HMAC_KEY_BASE64URL,
        snapshotHz = 60,
        frameAgeLimitMs = 150,
        streamTimeoutMs = 250,
        controlDisconnectGraceMs = 1500,
    )

private data class CommandOutput(
    val exitCode: Int,
    val stdout: String,
)

private fun runCommand(command: List<String>): CommandOutput {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!completed) {
        process.destroyForcibly()
        return CommandOutput(exitCode = -1, stdout = "command timed out")
    }
    val output = process.inputStream.bufferedReader().readText().take(MAX_COMMAND_OUTPUT_CHARS)
    return CommandOutput(exitCode = process.exitValue(), stdout = output)
}

private fun timedCase(name: String, block: () -> Unit): SmokeCaseResult {
    var failure: Throwable? = null
    val elapsedNanos = measureNanoTime {
        try {
            block()
        } catch (error: Throwable) {
            failure = error
        }
    }
    return SmokeCaseResult(
        name = name,
        passed = failure == null,
        message = failure?.message?.sanitizedFailureMessage() ?: "passed",
        elapsedSeconds = elapsedNanos / NANOS_PER_SECOND,
    )
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun String.sanitizedFailureMessage(): String =
    replace(Regex("""/Users/[^ ]+"""), "redacted-path")
        .replace(Regex("""[A-Fa-f0-9]{40,}"""), "redacted-hex")
        .take(160)

private const val HELPER_PATH_PROPERTY = "btgun.macos.hid.helper.path"
private const val CONTROL_SESSION_ID = "control-sid-smoke"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val RECEIVED_ELAPSED_NANOS = 1_111_111_333L
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val COMMAND_TIMEOUT_SECONDS = 5L
private const val MAX_COMMAND_OUTPUT_CHARS = 16_384
private val IOREG_COMMAND = listOf("ioreg", "-r", "-c", "IOHIDDevice", "-l", "-w", "0")
private const val IOREG_COMMAND_DISPLAY = "ioreg -r -c IOHIDDevice -l -w 0"

private const val GOLDEN_SNAPSHOT_FRAME_HEX =
    "425447490101000000112233445566778899aabbccddeeff000000000000002a00000000423a35c700000000423a3636000000233039cfc7020700003fa00000c02000003f4000003e000000be80000000000000423a3558ad0f94e008b50a045111a7bbb25688c2f1d399a8de4b3b8f2e325c0f63fb7d5f"
