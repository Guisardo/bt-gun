package com.btgun.desktop.backend.windows

import com.btgun.desktop.control.ControlAuthenticationResult
import com.btgun.desktop.control.ControlEnvelope
import com.btgun.desktop.control.ControlMessageType
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.PairingProofRequest
import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.security.PairingProof
import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpReceivedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun main() {
    runtimePublishesTrustedUdpCallbackThroughWindowsBackend()
    runtimePreservesExistingUdpCallbackWhenAttached()
    staleInputPublishClearsButtonsAndStickButKeepsAim()
    backendOutputReportRoutesToAuthenticatedPhoneHaptic()
    outputReportWithoutActiveAndroidSessionRecordsNoSession()
}

private fun runtimePublishesTrustedUdpCallbackThroughWindowsBackend() {
    val bridge = FakeWindowsDriverBridge()
    val backend = WindowsVirtualControllerBackend(bridge = bridge)
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = backend,
        nowElapsedNanos = { 3_000_000_000L },
    )
    val server = ControlServer(registry = testRegistry())
    val input = receivedInput(
        pressedControls = setOf("trigger", "reload", "x", "a"),
        stickX = 12_345,
        stickY = -12_345,
        rawAimX = 0.5f,
        rawAimY = -0.25f,
        sequence = 42L,
    )

    runtime.attach(server)
    server.onUdpInputReceived(input)

    val expected = WindowsHidReportPacker.packInputReport(com.btgun.desktop.backend.UdpControllerStateAdapter.toState(input))
    expectEquals("backend started", com.btgun.desktop.backend.BackendLifecycleState.STARTED, backend.lifecycleState)
    expectEquals("one bridge submission", 1, bridge.submitted.size)
    expectByteArrayEquals("submitted packed report", expected.bytes, bridge.submitted.single().bytes)
    expectEquals("report metadata sequence", 42L, bridge.submitted.single().sourceSequence)
    expectTrue("last publish recorded", runtime.diagnostics().lastPublishResult is com.btgun.desktop.backend.BackendPublishResult.Published)
}

private fun runtimePreservesExistingUdpCallbackWhenAttached() {
    val bridge = FakeWindowsDriverBridge()
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = WindowsVirtualControllerBackend(bridge = bridge),
        nowElapsedNanos = { 3_000_000_000L },
    )
    val server = ControlServer(registry = testRegistry())
    val observed = mutableListOf<UdpReceivedInput>()
    val input = receivedInput(sequence = 43L)
    server.onUdpInputReceived = observed::add

    runtime.attach(server)
    server.onUdpInputReceived(input)

    expectEquals("existing callback kept", listOf(input), observed)
    expectEquals("runtime still published", 1, bridge.submitted.size)
}

private fun staleInputPublishClearsButtonsAndStickButKeepsAim() {
    val bridge = FakeWindowsDriverBridge()
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = WindowsVirtualControllerBackend(bridge = bridge),
        nowElapsedNanos = { 3_000_000_000L },
    )
    val server = ControlServer(registry = testRegistry())
    val input = receivedInput(
        pressedControls = setOf("trigger", "reload", "x", "y", "a", "b"),
        stickX = 32_000,
        stickY = -32_000,
        rawAimX = 0.5f,
        rawAimY = -0.25f,
        stale = true,
        sequence = 44L,
    )

    runtime.attach(server)
    server.onUdpInputReceived(input)

    val bytes = bridge.submitted.single().bytes
    expectEquals("stale button bits clear", 0, bytes[1].toInt() and 0xff)
    expectEquals("stale stick x clears", 0, bytes.readInt16Le(2))
    expectEquals("stale stick y clears", 0, bytes.readInt16Le(4))
    expectEquals("stale aim x kept", 16_384, bytes.readInt16Le(6))
    expectEquals("stale aim y kept", -8_192, bytes.readInt16Le(8))
    expectEquals("diagnostic stale", true, runtime.diagnostics().stale)
}

private fun backendOutputReportRoutesToAuthenticatedPhoneHaptic() {
    val bridge = FakeWindowsDriverBridge()
    bridge.outputReports.add(outputReport(strength = 192, durationMs = 150, ttlMs = 600))
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = WindowsVirtualControllerBackend(bridge = bridge),
        nowElapsedNanos = { 3_000_000_000L },
    )
    val registry = testRegistry()
    val session = registry.startPairing(nowEpochMillis = 1_000L)
    val server = ControlServer(registry = registry, maxMessageBytes = 2048)
    val trusted = server.authenticate(proofRequestFor(session, "aa".repeat(16)), nowEpochMillis = 2_000L)
    expectTrue("authenticated", trusted is ControlAuthenticationResult.Accepted)
    val outbound = Channel<ControlEnvelope>(Channel.UNLIMITED)
    server.registerActiveControlSessionForTest((trusted as ControlAuthenticationResult.Accepted).trustedSession, outbound)

    runtime.attach(server)
    server.onUdpInputReceived(receivedInput(sequence = 45L))

    val envelope = outbound.tryReceive().getOrNull()
    expectEquals("haptic send", HapticSendResult.Sent, runtime.diagnostics().lastHapticSendResult)
    expectEquals("reserved haptic command", ControlMessageType.RESERVED_HAPTIC_COMMAND, envelope?.type)
    expectEquals("command id from output report drain", "windows-output-report-1", envelope?.body?.stringField("commandId"))
    expectEquals("strength from output report bytes", (192.0 / 255.0).toString(), envelope?.body?.stringField("strength"))
    expectEquals("duration from output report bytes", "150", envelope?.body?.stringField("durationMs"))
    expectEquals("ttl from output report bytes", "600", envelope?.body?.stringField("ttlMs"))
    expectEquals("pattern not synthesized", null, envelope?.body?.get("pattern")?.jsonPrimitive?.contentOrNull)
}

private fun outputReportWithoutActiveAndroidSessionRecordsNoSession() {
    val bridge = FakeWindowsDriverBridge()
    bridge.outputReports.add(outputReport(strength = 64, durationMs = 75, ttlMs = 250))
    val runtime = WindowsBackendRuntime(
        config = WindowsBackendRuntimeConfig(bridgePath = "/tmp/btgun-driver-bridge.exe"),
        backend = WindowsVirtualControllerBackend(bridge = bridge),
        nowElapsedNanos = { 3_000_000_000L },
    )
    val server = ControlServer(registry = testRegistry())

    runtime.attach(server)
    server.onUdpInputReceived(receivedInput(sequence = 46L))

    expectEquals("no session recorded", HapticSendResult.NoActiveSession, runtime.diagnostics().lastHapticSendResult)
}

private class FakeWindowsDriverBridge : WindowsDriverBridgeClient {
    val submitted = mutableListOf<WindowsInputReport>()
    val outputReports = ArrayDeque<ByteArray>()

    override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult {
        submitted.add(report)
        return WindowsDriverBridgeResult.Ok
    }

    override fun readOutputReport(): ByteArray? =
        outputReports.removeFirstOrNull()

    override fun readStatus(): WindowsDriverBridgeStatus =
        WindowsDriverBridgeStatus(driverStarted = true, vhfStarted = true, submittedInputReports = submitted.size.toLong())

    override fun close() = Unit
}

private fun receivedInput(
    pressedControls: Set<String> = setOf("trigger"),
    stickX: Int = 0,
    stickY: Int = 0,
    rawAimX: Float = 0.0f,
    rawAimY: Float = 0.0f,
    stale: Boolean = false,
    sequence: Long,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = "control-sid-1",
        streamSessionIdHex = "00112233445566778899aabbccddeeff",
        frameType = UdpInputFrameType.SNAPSHOT,
        buttons = 0,
        pressedControls = pressedControls,
        stickX = stickX,
        stickY = stickY,
        motion = UdpReceivedMotion(
            provider = 2,
            capabilityFlags = 3,
            yaw = 1.0f,
            pitch = 2.0f,
            roll = 3.0f,
            rawAimX = rawAimX,
            rawAimY = rawAimY,
            sourceSensorElapsedNanos = 2_000_000_000L,
        ),
        captureElapsedNanos = 2_100_000_000L,
        sendElapsedNanos = 2_100_500_000L,
        receivedElapsedNanos = 2_101_000_000L,
        stale = stale,
        lastAcceptedSequence = sequence,
    )

private fun outputReport(
    strength: Int,
    durationMs: Int,
    ttlMs: Int,
): ByteArray =
    byteArrayOf(
        WINDOWS_OUTPUT_REPORT_ID.toByte(),
        WINDOWS_OUTPUT_REPORT_VERSION.toByte(),
        strength.toByte(),
        (durationMs and 0xff).toByte(),
        ((durationMs ushr 8) and 0xff).toByte(),
        (ttlMs and 0xff).toByte(),
        ((ttlMs ushr 8) and 0xff).toByte(),
        0,
        0,
    )

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

private fun kotlinx.serialization.json.JsonObject.stringField(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull

private fun ByteArray.readInt16Le(offset: Int): Int {
    val value = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    return if (value and 0x8000 != 0) value - 0x10000 else value
}

private fun expectByteArrayEquals(label: String, expected: ByteArray, actual: ByteArray) {
    if (!expected.contentEquals(actual)) {
        throw AssertionError("$label expected <${expected.toHex()}> but was <${actual.toHex()}>")
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

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

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
