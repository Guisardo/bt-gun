package com.btgun.desktop.replay

import com.btgun.desktop.backend.UdpControllerStateAdapter
import com.btgun.desktop.transport.InputStreamConfig
import com.btgun.desktop.transport.UdpInputReceiver
import com.btgun.desktop.transport.UdpInputReceiverResult
import com.btgun.desktop.ui.VisualizerChecklistRowId
import com.btgun.desktop.ui.VisualizerChecklistState
import com.btgun.desktop.ui.VisualizerClockOffsetQuality
import com.btgun.desktop.ui.VisualizerMetrics
import com.btgun.desktop.ui.VisualizerModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

fun main() {
    replayFixtureLoadsRawUdpHex()
    replayFixturePassesReceiverBeforeMappedState()
    replayFixtureDrivesVisualizerRows()
    replayFixtureExpectedTimingUsesOffsetQuality()
}

private fun replayFixtureLoadsRawUdpHex() {
    val datagrams = replayDatagrams()

    expectEquals("fixture datagram count", 2, datagrams.size)
    datagrams.forEachIndexed { index, datagram ->
        expectEquals("datagram ${index + 1} length", 120, datagram.size)
    }
}

private fun replayFixturePassesReceiverBeforeMappedState() {
    val first = replaySession().acceptedInputs.first()
    val state = UdpControllerStateAdapter.toState(first.input)
    val expected = expectedVisualizer()

    expectEquals("trigger", expected.booleanAt("mapped_state", "trigger"), state.trigger)
    expectEquals("reload", expected.booleanAt("mapped_state", "reload"), state.reload)
    expectEquals("stickX", expected.intAt("mapped_state", "stickX"), state.stickX)
    expectEquals("stickY", expected.intAt("mapped_state", "stickY"), state.stickY)
    expectEquals("aimX", expected.floatAt("mapped_state", "aimX"), state.aimX)
    expectEquals("aimY", expected.floatAt("mapped_state", "aimY"), state.aimY)
}

private fun replayFixtureDrivesVisualizerRows() {
    val replay = replaySession()
    val expected = expectedVisualizer()
    val model = replay.model

    listOf(
        VisualizerChecklistRowId.LAN_VISUALIZER_STREAM,
        VisualizerChecklistRowId.LIVE_CONTROLS,
        VisualizerChecklistRowId.LATENCY_TARGET,
        VisualizerChecklistRowId.PACKET_LOSS,
    ).forEach { rowId ->
        expectEquals("${rowId.wireId} observed", VisualizerChecklistState.OBSERVED, model.row(rowId).state)
    }

    expectEquals("packet expected", expected.longAt("metrics", "packetExpected"), model.metrics.packetExpected)
    expectEquals("packet missed", expected.longAt("metrics", "packetMissed"), model.metrics.packetMissed)
    expectEquals("latency status", expected.stringAt("metrics", "targetStatus"), model.metrics.targetStatus)
}

private fun replayFixtureExpectedTimingUsesOffsetQuality() {
    val expected = expectedVisualizer()
    val timing = expected.jsonObject["timing"]
        ?.jsonObject
        ?: throw AssertionError("expected visualizer missing timing object")
    val offsetQuality = timing["offsetQuality"]?.jsonPrimitive?.contentOrNull
        ?: throw AssertionError("expected visualizer missing timing.offsetQuality")
    val directClockSubtraction = timing["directAndroidToDesktopClockSubtraction"]?.jsonPrimitive?.boolean
        ?: throw AssertionError("expected visualizer missing timing.directAndroidToDesktopClockSubtraction")

    expectTrue("offset quality is explicit", offsetQuality == "good" || offsetQuality == "estimated")
    expectEquals("no direct monotonic subtraction", false, directClockSubtraction)
    expectEquals("replay model offset quality", VisualizerClockOffsetQuality.valueOf(offsetQuality.uppercase()), replaySession().model.metrics.offsetQuality)
}

private fun replaySession(): ReplayResult {
    val datagrams = replayDatagrams()
    val expected = expectedVisualizer()
    val receiver = UdpInputReceiver().start(
        trustedSession = expected.stringAt("stream", "controlSessionRef"),
        config = fixtureConfig(),
    )
    val metrics = VisualizerMetrics()
    var model = VisualizerModel.initial()
    val accepted = mutableListOf<UdpInputReceiverResult.Accepted>()

    datagrams.forEachIndexed { index, datagram ->
        val result = receiver.handleDatagram(
            bytes = datagram,
            receivedElapsedNanos = RECEIVED_BASE_ELAPSED_NANOS + index,
        )
        if (result !is UdpInputReceiverResult.Accepted) {
            throw AssertionError("fixture datagram ${index + 1} must pass UdpInputReceiver before mapping, got $result")
        }
        accepted += result
        val renderElapsedNanos = RECEIVED_BASE_ELAPSED_NANOS + 20_000_000L + index
        model = model
            .withAcceptedInput(result.input, observedElapsedNanos = renderElapsedNanos)
            .withMetrics(metrics.record(result.input, desktopRenderElapsedNanos = renderElapsedNanos))
    }

    return ReplayResult(acceptedInputs = accepted, model = model)
}

private data class ReplayResult(
    val acceptedInputs: List<UdpInputReceiverResult.Accepted>,
    val model: VisualizerModel,
)

private fun replayDatagrams(): List<ByteArray> {
    val file = File("fixtures/replay/udp-golden/mapped-session-001.hex")
    if (!file.exists()) {
        throw AssertionError("missing replay hex fixture: ${file.path}")
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.hexToBytes() }
}

private fun expectedVisualizer(): JsonObject {
    val file = File("fixtures/replay/expected/mapped-session-001-visualizer.json")
    if (!file.exists()) {
        throw AssertionError("missing replay expected visualizer snapshot: ${file.path}")
    }
    return Json.parseToJsonElement(file.readText()).jsonObject
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

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex length must be even" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun JsonObject.stringAt(vararg path: String): String =
    nodeAt(*path).jsonPrimitive.contentOrNull
        ?: throw AssertionError("missing expected string field: ${path.joinToString(".")}")

private fun JsonObject.booleanAt(vararg path: String): Boolean =
    nodeAt(*path).jsonPrimitive.boolean

private fun JsonObject.intAt(vararg path: String): Int =
    stringAt(*path).toInt()

private fun JsonObject.longAt(vararg path: String): Long =
    stringAt(*path).toLong()

private fun JsonObject.floatAt(vararg path: String): Float =
    stringAt(*path).toFloat()

private fun JsonObject.nodeAt(vararg path: String) =
    path.fold(this as kotlinx.serialization.json.JsonElement) { current, key ->
        current.jsonObject[key] ?: throw AssertionError("missing expected field: ${path.joinToString(".")}")
    }

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError(label)
    }
}

private const val STREAM_SESSION_ID_HEX = "00112233445566778899aabbccddeeff"
private const val HMAC_KEY_BASE64URL = "ASNFZ4mrze_-3LqYdlQyEAEjRWeJq83v_ty6mHZUMhA"
private const val RECEIVED_BASE_ELAPSED_NANOS = 1_111_121_444L
