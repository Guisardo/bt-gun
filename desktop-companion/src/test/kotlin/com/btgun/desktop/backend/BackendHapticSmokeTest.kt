package com.btgun.desktop.backend

import com.btgun.desktop.control.ControlEnvelope
import com.btgun.desktop.control.ControlMessageType
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlAuthenticationResult
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.PairingProof
import com.btgun.desktop.smoke.BackendHapticSmokeSession
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    simulatedOutputReportMapsToPhoneHapticCommand()
    patternedOutputReportDoesNotCreatePhoneCommand()
    controlServerReturnsNoActiveSessionWithoutTrustedAndroid()
    activeTrustedSessionRoutesHapticOverReservedControlEnvelope()
    liveHapticSmokeFailsClosedWithoutAndroid()
}

private fun simulatedOutputReportMapsToPhoneHapticCommand() {
    val backend = StubVirtualControllerBackend.macos()
    val command = requireNotNull(
        backend.simulateOutputReport(
            SimulatedOutputReport(
                strength = 0.75,
                durationMs = 120L,
                ttlMs = 500L,
                pattern = null,
            ),
        ),
    )

    expectEquals("command id", "stub-output-report-macos-stub-1", command.commandId)
    expectEquals("strength", 0.75, command.strength)
    expectEquals("duration", 120L, command.durationMs)
    expectEquals("ttl", 500L, command.ttlMs)
    expectEquals("pattern", null, command.pattern)
}

private fun patternedOutputReportDoesNotCreatePhoneCommand() {
    val backend = StubVirtualControllerBackend.windows()

    val command = backend.simulateOutputReport(
        SimulatedOutputReport(
            strength = 0.75,
            durationMs = 120L,
            ttlMs = 500L,
            pattern = "double-pulse",
        ),
    )

    expectEquals("patterned report unsupported", null, command)
}

private fun controlServerReturnsNoActiveSessionWithoutTrustedAndroid() {
    val server = ControlServer(registry = testRegistry(), maxMessageBytes = 1024)
    val command = requireNotNull(
        StubVirtualControllerBackend.macos().simulateOutputReport(
            SimulatedOutputReport(strength = 0.75, durationMs = 120L, ttlMs = 500L),
        ),
    )

    expectEquals("no session", HapticSendResult.NoActiveSession, server.sendHapticCommand(command))
}

private fun activeTrustedSessionRoutesHapticOverReservedControlEnvelope() {
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 1024)
    val trusted = server.authenticate(proofRequestFor(session, "aa".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    server.registerActiveControlSessionForTest((trusted as ControlAuthenticationResult.Accepted).trustedSession, outbound)
    val command = requireNotNull(
        StubVirtualControllerBackend.macos().simulateOutputReport(
            SimulatedOutputReport(strength = 0.75, durationMs = 120L, ttlMs = 500L),
        ),
    )

    val send = server.sendHapticCommand(command, nowElapsedNanos = 3_000_000_000L)
    val envelope = outbound.tryReceive().getOrNull()

    expectEquals("sent", HapticSendResult.Sent, send)
    expectEquals("reserved type", ControlMessageType.RESERVED_HAPTIC_COMMAND, envelope?.type)
    expectEquals("command id", command.commandId, envelope?.body?.stringField("commandId"))
    expectEquals("strength", "0.75", envelope?.body?.stringField("strength"))
    expectEquals("duration", "120", envelope?.body?.stringField("durationMs"))
    expectEquals("ttl", "500", envelope?.body?.stringField("ttlMs"))
    expectEquals("pattern", null, envelope?.body?.get("pattern")?.jsonPrimitive?.contentOrNull)
}

private fun liveHapticSmokeFailsClosedWithoutAndroid() {
    val port = ServerSocket(0).use { it.localPort }
    val qrPath = Paths.get("build/test-results/btgun-smoke/macos-stub/haptic-pairing-qr.png")
    Files.deleteIfExists(qrPath)

    val result = BackendHapticSmokeSession.runLiveHaptic(
        platformId = "macos-stub",
        port = port,
        timeoutMillis = 50L,
    )

    expectEquals("no Android is failure", false, result.passed)
    expectTrue("failure mentions Android session", result.message.contains("Android session"))
    expectTrue("haptic smoke writes scannable QR image", Files.size(qrPath) > 100L)
}

private fun proofRequestFor(session: PairingSession, androidNonce: String): PairingProofRequest =
    PairingProofRequest(
        sid = session.sid,
        androidNonce = androidNonce,
        desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
        proofHex = PairingProof.create(
            sid = session.sid,
            desktopNonce = session.qrPayload.desktopNonce,
            androidNonce = androidNonce,
            desktopSpkiSha256 = session.qrPayload.desktopSpkiSha256,
            oneTimeMaterial = session.qrPayload.qrSecret,
        ),
    )

private fun testRegistry(): PairingSessionRegistry =
    PairingSessionRegistry(
        endpointSelector = LocalEndpointSelector.fixed(host = "192.168.50.25", port = 41731),
        identityStore = object : DesktopIdentityStore {
            override fun loadOrCreateIdentity(): DesktopIdentity =
                DesktopIdentity(desktopSpkiSha256 = FINGERPRINT)
        },
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}

private fun kotlinx.serialization.json.JsonObject.stringField(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
