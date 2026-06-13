package com.btgun.desktop.ui

import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.transport.UdpInputFrameType
import com.btgun.desktop.transport.UdpReceivedInput
import com.btgun.desktop.transport.UdpReceivedMotion

fun main() {
    offsetQualityStartsUnavailableThenUsesStatusAndUdpSamples()
    sequenceGapRecordsCurrentSessionExpectedAndMissed()
    omittedRejectedFramesDoNotAffectAcceptedPacketLossCounters()
    latencyUsesExplicitClockOffsetInsteadOfDirectClockSubtraction()
    metricsLabelsExposeUiSpecCopy()
    packetCountersResetOnControlOrStreamSessionChange()
    clockOffsetClearsOnControlOrStreamSessionChange()
    pendingStatusOffsetAppliesToMatchingNewControlSession()
}

private fun offsetQualityStartsUnavailableThenUsesStatusAndUdpSamples() {
    val metrics = VisualizerMetrics()

    expectEquals("initial offset unavailable", VisualizerClockOffsetQuality.UNAVAILABLE, metrics.snapshot().offsetQuality)

    val statusOffset = metrics.recordStatus(
        status = VisualizerStatus(
            rawDebugEnabled = false,
            aimZeroState = "ready",
            recenterState = "idle",
            androidElapsedNanos = 1_000_000_000L,
        ),
        desktopReceivedElapsedNanos = 10_000_000_000L,
    )
    val statusSnapshot = metrics.record(
        input = acceptedInput(sequence = 1L, captureElapsedNanos = 1_005_000_000L, sendElapsedNanos = 1_010_000_000L),
        desktopRenderElapsedNanos = 10_030_000_000L,
    )
    val udpOnly = VisualizerMetrics().record(
        input = acceptedInput(sequence = 1L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_010_000_000L, receivedElapsedNanos = 10_020_000_000L),
        desktopRenderElapsedNanos = 10_030_000_000L,
    )

    expectEquals("status offset quality", VisualizerClockOffsetQuality.GOOD, statusOffset.quality)
    expectEquals("status-derived offset", VisualizerClockOffsetQuality.GOOD, statusSnapshot.offsetQuality)
    expectEquals("status headline latency", 25L, statusSnapshot.headlineLatencyMillis)
    expectEquals("udp offset quality", VisualizerClockOffsetQuality.ESTIMATED, udpOnly.offsetQuality)
    expectEquals("udp estimated headline latency", 20L, udpOnly.headlineLatencyMillis)
}

private fun sequenceGapRecordsCurrentSessionExpectedAndMissed() {
    val metrics = VisualizerMetrics()

    metrics.record(acceptedInput(sequence = 10L), desktopRenderElapsedNanos = 1_100_000_000L)
    val snapshot = metrics.record(acceptedInput(sequence = 13L), desktopRenderElapsedNanos = 1_120_000_000L)

    expectEquals("expected packets from inclusive sequence span", 4L, snapshot.packetExpected)
    expectEquals("missed packets from gap", 2L, snapshot.packetMissed)
    expectEquals("packet loss percent", 50.0, snapshot.packetLossPercent)
    expectEquals("packet loss label", "Packet loss: 2/4 (50.0%)", snapshot.packetLossLabel)
}

private fun omittedRejectedFramesDoNotAffectAcceptedPacketLossCounters() {
    val metrics = VisualizerMetrics()

    metrics.record(acceptedInput(sequence = 10L), desktopRenderElapsedNanos = 1_100_000_000L)
    val snapshot = metrics.record(acceptedInput(sequence = 11L), desktopRenderElapsedNanos = 1_110_000_000L)

    expectEquals("duplicate/old rejects never entered", 2L, snapshot.packetExpected)
    expectEquals("no rejected-frame penalty", 0L, snapshot.packetMissed)
}

private fun latencyUsesExplicitClockOffsetInsteadOfDirectClockSubtraction() {
    val metrics = VisualizerMetrics()
    val input = acceptedInput(
        sequence = 20L,
        captureElapsedNanos = 1_000_000_000L,
        sendElapsedNanos = 1_012_000_000L,
        receivedElapsedNanos = 9_995_000_000L,
    )
    val desktopRenderElapsedNanos = 10_000_000_000L
    val directInvalidMillis = (desktopRenderElapsedNanos - input.captureElapsedNanos) / 1_000_000L

    val snapshot = metrics.record(
        input = input,
        desktopRenderElapsedNanos = desktopRenderElapsedNanos,
        clockOffset = VisualizerClockOffset(androidToDesktopNanos = 8_950_000_000L, quality = VisualizerClockOffsetQuality.GOOD),
    )

    expectEquals("direct subtraction would be invalid", 9_000L, directInvalidMillis)
    expectEquals("offset-converted headline", 50L, snapshot.headlineLatencyMillis)
    expectEquals("offset quality", VisualizerClockOffsetQuality.GOOD, snapshot.offsetQuality)
    expectEquals("headline label", "Latency: 50 ms | target <50 ms | warn", snapshot.headlineLatencyLabel)
    expectEquals("capture to send", 12L, snapshot.captureToSendMillis)
    expectEquals("receive to render", 5L, snapshot.receiveToRenderMillis)
}

private fun metricsLabelsExposeUiSpecCopy() {
    val snapshot = VisualizerMetricSnapshot(
        headlineLatencyMillis = 42L,
        headlineLatencyLabel = "Latency: 42 ms | target <50 ms | pass",
        offsetQuality = VisualizerClockOffsetQuality.ESTIMATED,
        captureToSendMillis = 7L,
        receiveToRenderMillis = 4L,
        sampleAgeMillis = 5L,
        packetExpected = 11L,
        packetMissed = 2L,
        packetLossPercent = 18.2,
        packetLossLabel = "Packet loss: 2/11 (18.2%)",
        targetStatus = "pass",
        controlSessionId = "control-sid-1",
        streamSessionIdHex = "00112233445566778899aabbccddeeff",
    )

    expectEquals(
        "metric labels",
        listOf(
            "Latency: 42 ms | target <50 ms | pass",
            "Clock offset: estimated",
            "Capture to send: 7 ms",
            "Receive to render: 4 ms",
            "Last input: 5 ms ago",
            "Packet loss: 2/11 (18.2%)",
        ),
        VisualizerPanels.metricsLabels(snapshot),
    )
}

private fun packetCountersResetOnControlOrStreamSessionChange() {
    val metrics = VisualizerMetrics()

    metrics.record(acceptedInput(sequence = 10L), desktopRenderElapsedNanos = 1_100_000_000L)
    metrics.record(acceptedInput(sequence = 13L), desktopRenderElapsedNanos = 1_120_000_000L)
    val controlReset = metrics.record(
        acceptedInput(controlSessionId = "control-sid-2", sequence = 3L),
        desktopRenderElapsedNanos = 1_130_000_000L,
    )
    val streamReset = metrics.record(
        acceptedInput(controlSessionId = "control-sid-2", streamSessionIdHex = "ffeeddccbbaa99887766554433221100", sequence = 9L),
        desktopRenderElapsedNanos = 1_140_000_000L,
    )

    expectEquals("control reset expected", 1L, controlReset.packetExpected)
    expectEquals("control reset missed", 0L, controlReset.packetMissed)
    expectEquals("stream reset expected", 1L, streamReset.packetExpected)
    expectEquals("stream reset missed", 0L, streamReset.packetMissed)
}

private fun clockOffsetClearsOnControlOrStreamSessionChange() {
    val metrics = VisualizerMetrics()
    metrics.recordStatus(
        status = VisualizerStatus(
            rawDebugEnabled = false,
            aimZeroState = "ready",
            recenterState = "idle",
            androidElapsedNanos = 1_000_000_000L,
        ),
        desktopReceivedElapsedNanos = 10_000_000_000L,
    )
    metrics.record(
        acceptedInput(sequence = 1L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_010_000_000L),
        desktopRenderElapsedNanos = 10_030_000_000L,
    )

    val reset = metrics.record(
        acceptedInput(
            controlSessionId = "control-sid-2",
            sequence = 1L,
            captureElapsedNanos = 2_000_000_000L,
            sendElapsedNanos = 2_010_000_000L,
            receivedElapsedNanos = 20_020_000_000L,
        ),
        desktopRenderElapsedNanos = 20_030_000_000L,
    )

    expectEquals("new session uses UDP estimated offset", VisualizerClockOffsetQuality.ESTIMATED, reset.offsetQuality)
    expectEquals("new session does not reuse stale good offset", 20L, reset.headlineLatencyMillis)
}

private fun pendingStatusOffsetAppliesToMatchingNewControlSession() {
    val metrics = VisualizerMetrics()
    metrics.record(
        acceptedInput(sequence = 1L, captureElapsedNanos = 1_000_000_000L, sendElapsedNanos = 1_010_000_000L),
        desktopRenderElapsedNanos = 10_030_000_000L,
    )
    metrics.recordStatus(
        status = VisualizerStatus(
            controlSessionId = "control-sid-2",
            rawDebugEnabled = false,
            aimZeroState = "ready",
            recenterState = "idle",
            androidElapsedNanos = 2_000_000_000L,
        ),
        desktopReceivedElapsedNanos = 20_000_000_000L,
    )

    val snapshot = metrics.record(
        acceptedInput(
            controlSessionId = "control-sid-2",
            sequence = 1L,
            captureElapsedNanos = 2_005_000_000L,
            sendElapsedNanos = 2_010_000_000L,
            receivedElapsedNanos = 20_040_000_000L,
        ),
        desktopRenderElapsedNanos = 20_030_000_000L,
    )

    expectEquals("new session uses pending status offset", VisualizerClockOffsetQuality.GOOD, snapshot.offsetQuality)
    expectEquals("new session pending status latency", 25L, snapshot.headlineLatencyMillis)
}

private fun acceptedInput(
    controlSessionId: String = "control-sid-1",
    streamSessionIdHex: String = "00112233445566778899aabbccddeeff",
    sequence: Long,
    captureElapsedNanos: Long = 1_000_000_000L,
    sendElapsedNanos: Long = 1_001_000_000L,
    receivedElapsedNanos: Long = 1_005_000_000L,
): UdpReceivedInput =
    UdpReceivedInput(
        controlSessionId = controlSessionId,
        streamSessionIdHex = streamSessionIdHex,
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
            sourceSensorElapsedNanos = 900_000_000L,
        ),
        captureElapsedNanos = captureElapsedNanos,
        sendElapsedNanos = sendElapsedNanos,
        receivedElapsedNanos = receivedElapsedNanos,
        stale = false,
        lastAcceptedSequence = sequence,
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
