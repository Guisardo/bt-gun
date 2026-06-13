package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlEnvelope
import com.btgun.desktop.control.ControlMessageType
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.pairing.LocalEndpointSelector
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentity
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.transport.InputReplayRejectReason
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpReceivedMotion

fun main() {
    hubFansOutUdpToMultipleListenersAndPreviousCallback()
    hubFansOutEveryControlServerUiEvent()
    hubCloseRestoresPreviousCallbackWhenItStillOwnsCallback()
    hubDoesNotOwnNetworkingHidProfileEditingOrHaptics()
}

private fun hubFansOutUdpToMultipleListenersAndPreviousCallback() {
    val server = ControlServer(registry = testRegistry())
    val input = receivedInput(sequence = 10L)
    val previous = mutableListOf<UdpReceivedInput>()
    val first = mutableListOf<UdpReceivedInput>()
    val second = mutableListOf<UdpReceivedInput>()
    server.onUdpInputReceived = previous::add

    val hub = DesktopUiEventHub(server).attach()
    hub.listen(DesktopUiEventListener(onUdpInputReceived = first::add))
    hub.listen(DesktopUiEventListener(onUdpInputReceived = second::add))

    server.onUdpInputReceived(input)

    expectEquals("previous callback still receives udp", listOf(input), previous)
    expectEquals("first listener receives udp", listOf(input), first)
    expectEquals("second listener receives udp", listOf(input), second)
    hub.close()
}

private fun hubFansOutEveryControlServerUiEvent() {
    val server = ControlServer(registry = testRegistry())
    val observed = mutableListOf<String>()
    val hub = DesktopUiEventHub(server).attach()
    hub.listen(
        DesktopUiEventListener(
            onSessionStateChanged = { observed.add("session:${it.name}") },
            onControlEnvelopeAccepted = { observed.add("envelope:${it.type.wireName}") },
            onProfileMetadataReceived = { observed.add("profile:${it.profileId}") },
            onUdpInputReceived = { observed.add("udp:${it.lastAcceptedSequence}") },
            onUdpInputRejected = { observed.add("reject:${it.name}") },
            onUdpInputStateChanged = { observed.add("stream:${it.name}") },
            onHapticResultReceived = { observed.add("haptic:${it.status.wireName}") },
        ),
    )

    server.onSessionStateChanged(ControlServerSessionState.AUTHENTICATED)
    server.onControlEnvelopeAccepted(
        ControlEnvelope(
            v = 1,
            type = ControlMessageType.DIAGNOSTICS,
            msgId = "diag",
            sessionId = "sid",
            seq = 1L,
            sentElapsedNanos = 2L,
        ),
    )
    server.onProfileMetadataReceived(
        ProfileMetadata(
            profileId = "default_visualizer",
            displayName = "Default Visualizer",
            revision = 1L,
            source = "android",
        ),
    )
    server.onUdpInputReceived(receivedInput(sequence = 11L))
    server.onUdpInputRejected(InputReplayRejectReason.OLD_SEQUENCE)
    server.onUdpInputStateChanged(InputStreamLifecycleState.ACTIVE)
    server.onHapticResultReceived(
        HapticResult(
            commandId = "cmd-1",
            status = HapticResultStatus.STARTED,
            detail = "phone pulse started",
            observedElapsedNanos = 3L,
        ),
    )

    expectEquals(
        "all event types fan out",
        listOf(
            "session:AUTHENTICATED",
            "envelope:diagnostics",
            "profile:default_visualizer",
            "udp:11",
            "reject:OLD_SEQUENCE",
            "stream:ACTIVE",
            "haptic:started",
        ),
        observed,
    )
    hub.close()
}

private fun hubCloseRestoresPreviousCallbackWhenItStillOwnsCallback() {
    val server = ControlServer(registry = testRegistry())
    val input = receivedInput(sequence = 12L)
    val previous = mutableListOf<UdpReceivedInput>()
    val listener = mutableListOf<UdpReceivedInput>()
    server.onUdpInputReceived = previous::add
    val hub = DesktopUiEventHub(server).attach()
    hub.listen(DesktopUiEventListener(onUdpInputReceived = listener::add))

    hub.close()
    server.onUdpInputReceived(input)

    expectEquals("previous callback restored", listOf(input), previous)
    expectEquals("hub listener no longer attached", emptyList<UdpReceivedInput>(), listener)
}

private fun hubDoesNotOwnNetworkingHidProfileEditingOrHaptics() {
    val source = java.io.File("src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt")
        .takeIf { it.exists() }
        ?.readText()
        .orEmpty()

    listOf("DatagramSocket", "ServerSocket", "WindowsVirtualControllerBackend", "MacosVirtualControllerBackend", "sendHapticCommand")
        .forEach { forbidden ->
            expectFalse("hub excludes $forbidden", source.contains(forbidden))
        }
}

private fun receivedInput(sequence: Long): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = "control-sid-1",
        streamSessionIdHex = "00112233445566778899aabbccddeeff",
        frameType = UdpInputFrameType.SNAPSHOT,
        buttons = 0,
        pressedControls = setOf("trigger"),
        stickX = 1,
        stickY = -1,
        motion = UdpReceivedMotion(
            provider = 2,
            capabilityFlags = 3,
            yaw = 1.0f,
            pitch = 2.0f,
            roll = 3.0f,
            rawAimX = 0.1f,
            rawAimY = -0.1f,
            sourceSensorElapsedNanos = 1_000_000L,
        ),
        captureElapsedNanos = 2_000_000L,
        sendElapsedNanos = 3_000_000L,
        receivedElapsedNanos = 4_000_000L,
        stale = false,
        lastAcceptedSequence = sequence,
    )

private fun testRegistry(): PairingSessionRegistry =
    PairingSessionRegistry(
        endpointSelector = LocalEndpointSelector.fixed(host = "192.168.50.25", port = 41731),
        identityStore = object : DesktopIdentityStore {
            override fun loadOrCreateIdentity(): DesktopIdentity =
                DesktopIdentity(desktopSpkiSha256 = "aa".repeat(32))
        },
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError(label)
    }
}
