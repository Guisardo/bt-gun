package com.btgun.desktop.smoke

import com.btgun.desktop.backend.SimulatedOutputReport
import com.btgun.desktop.backend.StubVirtualControllerBackend
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.FileDesktopIdentityStore
import com.btgun.desktop.security.SecretRedactor
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

object BackendHapticSmokeSession {
    fun runLiveHaptic(
        platformId: String,
        port: Int = ControlServer.DEFAULT_UDP_PORT,
        timeoutMillis: Long = 120_000L,
    ): SmokeCaseResult {
        val startedNanos = System.nanoTime()
        fun result(passed: Boolean, message: String): SmokeCaseResult =
            SmokeCaseResult(
                name = "live-phone-haptic-$platformId",
                passed = passed,
                message = SecretRedactor.redact(message).take(MAX_CASE_MESSAGE_CHARS),
                elapsedSeconds = (System.nanoTime() - startedNanos) / NANOS_PER_SECOND,
            )

        val registry = PairingSessionRegistry(
            endpointSelector = LocalEndpointSelector(port = port),
            identityStore = FileDesktopIdentityStore(
                path = Paths.get("build/btgun-smoke/live-haptic-desktop-identity.p12"),
                password = "btgun-smoke-live-haptic".toCharArray(),
            ),
        )
        val pairing = registry.startPairing()
        val server = ControlServer(registry = registry)
        val state = AtomicReference(ControlServerSessionState.STOPPED)
        val hapticResult = AtomicReference<HapticResult?>(null)
        server.onSessionStateChanged = state::set
        server.onHapticResultReceived = hapticResult::set

        return try {
            println("btgun haptic smoke QR URI: ${pairing.qrPayload.toPairingUri()}")
            println(
                "btgun haptic smoke manual: host=${pairing.manualPayload.host} " +
                    "port=${pairing.manualPayload.port} code=${pairing.manualPayload.code} " +
                    "fingerprint_suffix=${pairing.manualPayload.desktopSpkiSha256Suffix}",
            )
            server.start(port = port)
            val deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L
            if (!waitUntil(deadlineNanos) { state.get() == ControlServerSessionState.AUTHENTICATED }) {
                return result(passed = false, message = "no authenticated Android session before timeout")
            }

            val command = backendFor(platformId).simulateOutputReport(
                SimulatedOutputReport(
                    strength = 0.75,
                    durationMs = 120L,
                    ttlMs = 500L,
                    pattern = null,
                ),
            ) ?: return result(passed = false, message = "simulated output report unsupported")

            when (val send = server.sendHapticCommand(command)) {
                HapticSendResult.Sent -> Unit
                HapticSendResult.NoActiveSession ->
                    return result(passed = false, message = "no active Android session for haptic send")
                is HapticSendResult.Rejected ->
                    return result(passed = false, message = "haptic command rejected: ${send.error}")
                is HapticSendResult.Failed ->
                    return result(passed = false, message = "haptic command failed: ${send.reason}")
            }

            if (!waitUntil(deadlineNanos) { hapticResult.get()?.commandId == command.commandId }) {
                return result(passed = false, message = "no haptic result before timeout")
            }
            val observed = hapticResult.get()
            if (observed?.status == HapticResultStatus.STARTED) {
                result(passed = true, message = "Android reported haptic started")
            } else {
                result(
                    passed = false,
                    message = "haptic result was ${observed?.status?.wireName ?: "missing"}",
                )
            }
        } catch (error: Throwable) {
            result(
                passed = false,
                message = "live haptic error ${error::class.simpleName}: ${error.message.orEmpty()}",
            )
        } finally {
            server.stop()
        }
    }

    private fun backendFor(platformId: String): StubVirtualControllerBackend =
        when (platformId) {
            "macos-stub" -> StubVirtualControllerBackend.macos()
            "windows-stub" -> StubVirtualControllerBackend.windows()
            else -> throw IllegalArgumentException("unsupported platform id")
        }

    private fun waitUntil(deadlineNanos: Long, condition: () -> Boolean): Boolean {
        while (System.nanoTime() < deadlineNanos) {
            if (condition()) return true
            Thread.sleep(WAIT_POLL_MILLIS)
        }
        return condition()
    }

    private const val WAIT_POLL_MILLIS = 25L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MAX_CASE_MESSAGE_CHARS = 160
}
