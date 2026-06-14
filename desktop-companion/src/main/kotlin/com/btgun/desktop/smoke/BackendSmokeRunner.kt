package com.btgun.desktop.smoke

import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.StubVirtualControllerBackend
import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputFrame
import com.btgun.desktop.transport.UdpInputFrameCodec
import com.btgun.desktop.transport.UdpInputFrameType
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
            val accepted = receiver.acceptFrame(
                productFrame(sequence = 42L, buttonBitmask = BUTTON_R2 or BUTTON_L2 or BUTTON_B2),
                "snapshot",
            )
            acceptedSequences += accepted.input.lastAcceptedSequence
            val state = UdpControllerStateAdapter.toState(accepted.input)
            expectPublished("snapshot", backend.publish(state))
            publishedStates += state
        }
        cases += timedCase("receiver-accepted-edge") {
            val accepted = receiver.acceptFrame(
                productFrame(
                    type = UdpInputFrameType.EDGE,
                    sequence = 43L,
                    buttonBitmask = BUTTON_R2 or EDGE_CONTROL_CHANGED,
                    stickX = Short.MIN_VALUE.toInt(),
                    stickY = Short.MAX_VALUE.toInt(),
                    productAimX = -0.5f,
                    productAimY = 0.25f,
                ),
                "edge",
            )
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

    private fun UdpInputReceiver.acceptFrame(
        frame: UdpInputFrame,
        label: String,
    ): UdpInputReceiverResult.Accepted =
        when (val result = handleDatagram(UdpInputFrameCodec.encode(frame, fixtureConfig()), RECEIVED_ELAPSED_NANOS)) {
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

    private fun productFrame(
        type: UdpInputFrameType = UdpInputFrameType.SNAPSHOT,
        sequence: Long,
        buttonBitmask: Int,
        stickX: Int = 12_345,
        stickY: Int = -12_345,
        productAimX: Float = 0.375f,
        productAimY: Float = -0.625f,
    ): UdpInputFrame =
        UdpInputFrame(
            type = type,
            streamSessionId = STREAM_SESSION_ID_HEX,
            sequence = sequence,
            captureElapsedNanos = 1_111_111_111L,
            sendElapsedNanos = 1_111_111_222L,
            buttonBitmask = buttonBitmask,
            stickX = stickX,
            stickY = stickY,
            motionProvider = 2,
            motionCapabilityFlags = 0x07,
            yaw = productAimX,
            pitch = productAimY,
            roll = 0.75f,
            rawAimX = 0.125f,
            rawAimY = -0.25f,
            sourceSensorElapsedNanos = 1_111_111_000L,
            streamFlags = UdpInputFrame.FLAG_MAPPED_PRODUCT_STREAM or UdpInputFrame.FLAG_RAW_DEBUG_EXTRAS,
            productAimX = productAimX,
            productAimY = productAimY,
            rawRoll = 0.75f,
        )

    private const val CONTROL_SESSION_ID = "control-sid-smoke"
    private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
    private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
    private const val RECEIVED_ELAPSED_NANOS = 1_111_111_333L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val BUTTON_B2 = 1 shl 1
    private const val BUTTON_L2 = 1 shl 6
    private const val BUTTON_R2 = 1 shl 7
    private const val EDGE_CONTROL_CHANGED = 1 shl 30
}
