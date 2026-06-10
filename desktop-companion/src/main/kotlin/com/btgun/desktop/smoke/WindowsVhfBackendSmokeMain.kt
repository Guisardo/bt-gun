package com.btgun.desktop.smoke

import com.btgun.desktop.backend.BackendCapabilityPresets
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.backend.windows.WindowsDriverBridge
import com.btgun.desktop.backend.windows.WindowsDriverBridgeConfig
import com.btgun.desktop.backend.windows.WindowsVirtualControllerBackend
import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.system.measureNanoTime

fun main() {
    val outputFile = Paths.get("build/test-results/btgun-smoke/windows-vhf/TEST-btgun-windows-vhf.xml")
    val bridgePath = System.getProperty(BRIDGE_PATH_PROPERTY)?.trim().orEmpty()
    val cases = mutableListOf<SmokeCaseResult>()

    cases += timedCase("plan05-bridge-artifact-present") {
        validateBridgeArtifact(bridgePath)
    }
    cases += timedCase("windows-vhf-capabilities-real-output-report") {
        val capabilities = BackendCapabilityPresets.windowsVhf()
        require(capabilities.platform == "windows-vhf") { "unexpected platform id" }
        require(capabilities.haptics.outputReport) { "windows-vhf output report capability missing" }
        require(capabilities.limitations.none { it.feature == "os-visible-device" }) { "real VHF backend must not report stub device limits" }
    }
    cases += timedCase("windows-vhf-publishes-plan04-fixture") {
        val artifact = validateBridgeArtifact(bridgePath)
        val backend = WindowsVirtualControllerBackend(
            bridge = WindowsDriverBridge(
                WindowsDriverBridgeConfig(command = listOf(artifact.toString())),
            ),
        )
        backend.start()
        try {
            val accepted = acceptedPlan04Fixture()
            val state = UdpControllerStateAdapter.toState(accepted.input)
            val publish = backend.publish(state)
            require(publish == BackendPublishResult.Published) { "real windows-vhf publish failed" }
            require(backend.currentState.sourceSequence == 42L) { "plan04 fixture sequence not published" }
        } finally {
            backend.stop("windows-vhf smoke complete")
        }
    }

    JunitSmokeXml.write(outputFile, "btgun-desktop-backend-windows-vhf", cases)
    val failed = cases.filterNot { it.passed }
    if (failed.isNotEmpty()) {
        throw AssertionError("windows-vhf smoke failed: ${failed.joinToString { it.name }}")
    }
    println("btgun windows-vhf smoke XML: ${outputFile.absolute()}")
}

private fun validateBridgeArtifact(bridgePath: String): Path {
    require(bridgePath.isNotBlank()) { "$BRIDGE_PATH_PROPERTY is required" }
    val path = Paths.get(bridgePath)
    require(Files.isRegularFile(path)) { "btgun-driver-bridge.exe not found" }
    require(path.fileName.toString().equals("btgun-driver-bridge.exe", ignoreCase = true)) {
        "bridge path must point to btgun-driver-bridge.exe"
    }
    val artifactRoot = path.parent?.parent
    require(artifactRoot != null && Files.isRegularFile(artifactRoot.resolve("build-metadata.json"))) {
        "Plan 05 artifact metadata missing"
    }
    return path
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
        message = failure?.message?.take(160) ?: "passed",
        elapsedSeconds = elapsedNanos / NANOS_PER_SECOND,
    )
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private const val BRIDGE_PATH_PROPERTY = "btgun.windows.driver.bridge.path"
private const val CONTROL_SESSION_ID = "control-sid-smoke"
private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val RECEIVED_ELAPSED_NANOS = 1_111_111_333L
private const val NANOS_PER_SECOND = 1_000_000_000.0

private const val GOLDEN_SNAPSHOT_FRAME_HEX =
    "425447490101000000112233445566778899aabbccddeeff000000000000002a00000000423a35c700000000423a3636000000233039cfc7020700003fa00000c02000003f4000003e000000be80000000000000423a3558ad0f94e008b50a045111a7bbb25688c2f1d399a8de4b3b8f2e325c0f63fb7d5f"
