package com.btgun.desktop.smoke

import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.StubVirtualControllerBackend
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult
import java.nio.file.Path
import kotlin.system.measureNanoTime

data class BackendSmokeResult(
    val platformId: String,
    val cases: List<SmokeCaseResult>,
    val xmlPath: Path,
    val acceptedFixtureSequences: List<Long>,
    val publishedStates: List<SemanticControllerState>,
    val finalState: SemanticControllerState,
)

object BackendSmokeRunner {
    fun run(
        platformId: String,
        outputFile: Path,
        includeHaptic: Boolean = false,
        hapticPort: Int = 41731,
        hapticTimeoutMillis: Long = 120_000L,
    ): BackendSmokeResult {
        val suiteName = suiteName(platformId)
        val backend = backendFor(platformId)
        val acceptedSequences = mutableListOf<Long>()
        val publishedStates = mutableListOf<SemanticControllerState>()
        val cases = mutableListOf<SmokeCaseResult>()

        backend.start()
        val receiver = UdpInputReceiver().start(
            trustedSession = CONTROL_SESSION_ID,
            config = fixtureConfig(),
        )

        cases += timedCase("receiver-accepted-snapshot") {
            val accepted = receiver.acceptFixture(GOLDEN_SNAPSHOT_FRAME_HEX, "snapshot")
            acceptedSequences += accepted.input.lastAcceptedSequence
            val state = UdpControllerStateAdapter.toState(accepted.input)
            expectPublished("snapshot", backend.publish(state))
            publishedStates += state
        }
        cases += timedCase("receiver-accepted-edge") {
            val accepted = receiver.acceptFixture(GOLDEN_EDGE_FRAME_HEX, "edge")
            acceptedSequences += accepted.input.lastAcceptedSequence
            val state = UdpControllerStateAdapter.toState(accepted.input)
            expectPublished("edge", backend.publish(state))
            publishedStates += state
        }
        cases += timedCase("backend-published-edge-state") {
            val finalState = backend.currentState
            require(finalState.trigger) { "edge state trigger not published" }
            require(!finalState.x) { "edge control flag leaked into x button" }
            require(finalState.stickX == -32768) { "edge state stickX mismatch" }
            require(finalState.stickY == 32767) { "edge state stickY mismatch" }
            require(finalState.sourceSequence == 43L) { "edge state sequence mismatch" }
        }
        if (includeHaptic) {
            cases += BackendHapticSmokeSession.runLiveHaptic(
                platformId = platformId,
                port = hapticPort,
                timeoutMillis = hapticTimeoutMillis,
            )
        }

        JunitSmokeXml.write(outputFile, suiteName, cases)
        return BackendSmokeResult(
            platformId = platformId,
            cases = cases,
            xmlPath = outputFile,
            acceptedFixtureSequences = acceptedSequences.toList(),
            publishedStates = publishedStates.toList(),
            finalState = backend.currentState,
        )
    }

    private fun UdpInputReceiver.acceptFixture(
        hex: String,
        label: String,
    ): UdpInputReceiverResult.Accepted =
        when (val result = handleDatagram(hex.hexToBytes(), RECEIVED_ELAPSED_NANOS)) {
            is UdpInputReceiverResult.Accepted -> result
            is UdpInputReceiverResult.Rejected -> throw IllegalStateException("$label fixture rejected by receiver")
            UdpInputReceiverResult.Stopped -> throw IllegalStateException("$label receiver stopped")
        }

    private fun expectPublished(label: String, result: BackendPublishResult) {
        if (result != BackendPublishResult.Published) {
            throw IllegalStateException("$label backend publish failed")
        }
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
        val safeMessage = failure?.message?.take(160) ?: "passed"
        return SmokeCaseResult(
            name = name,
            passed = failure == null,
            message = safeMessage,
            elapsedSeconds = elapsedNanos / NANOS_PER_SECOND,
        )
    }

    private fun backendFor(platformId: String): StubVirtualControllerBackend =
        when (platformId) {
            "macos-stub" -> StubVirtualControllerBackend.macos()
            "windows-stub" -> StubVirtualControllerBackend.windows()
            else -> throw IllegalArgumentException("unsupported platform id")
        }

    private fun suiteName(platformId: String): String =
        "btgun-desktop-backend-$platformId"

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

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private const val CONTROL_SESSION_ID = "control-sid-smoke"
    private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
    private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
    private const val RECEIVED_ELAPSED_NANOS = 1_111_111_333L
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    private const val GOLDEN_SNAPSHOT_FRAME_HEX =
        "425447490101000300112233445566778899aabbccddeeff000000000000002a00000000423a35c700000000423a3636000000233039cfc7020700003ec00000bf2000003f4000003e000000be80000000000000423a3558e5fe65b7e6e39c6eb8109901c44e1078e75277dded88c2c0732a5a15e517c6dc"

    private const val GOLDEN_EDGE_FRAME_HEX =
        "425447490102000100112233445566778899aabbccddeeff000000000000002b00000000423a36a500000000423a37140000010180007fff03030000bf0000003e800000400000007fc000007fc0000000000000423a3684a5b9af27f6b97e7699e380efcfbc21fb7ce9dd851c9b632de938d6081ee4a669"
}
