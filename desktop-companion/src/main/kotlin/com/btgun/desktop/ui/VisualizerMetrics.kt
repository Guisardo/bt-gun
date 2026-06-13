package com.btgun.desktop.ui

import com.btgun.desktop.transport.UdpReceivedInput
import kotlin.math.round

enum class VisualizerClockOffsetQuality {
    UNAVAILABLE,
    ESTIMATED,
    GOOD,
}

data class VisualizerClockOffset(
    val androidToDesktopNanos: Long,
    val quality: VisualizerClockOffsetQuality,
)

data class VisualizerMetricSnapshot(
    val headlineLatencyMillis: Long?,
    val headlineLatencyLabel: String,
    val offsetQuality: VisualizerClockOffsetQuality,
    val captureToSendMillis: Long,
    val receiveToRenderMillis: Long,
    val sampleAgeMillis: Long,
    val packetExpected: Long,
    val packetMissed: Long,
    val packetLossPercent: Double,
    val packetLossLabel: String,
    val targetStatus: String,
    val controlSessionId: String?,
    val streamSessionIdHex: String?,
) {
    companion object {
        fun empty(targetLatencyMillis: Long = VisualizerMetrics.DEFAULT_TARGET_LATENCY_MILLIS): VisualizerMetricSnapshot =
            VisualizerMetricSnapshot(
                headlineLatencyMillis = null,
                headlineLatencyLabel = "Latency: unavailable | target <${targetLatencyMillis} ms",
                offsetQuality = VisualizerClockOffsetQuality.UNAVAILABLE,
                captureToSendMillis = 0L,
                receiveToRenderMillis = 0L,
                sampleAgeMillis = 0L,
                packetExpected = 0L,
                packetMissed = 0L,
                packetLossPercent = 0.0,
                packetLossLabel = "Packet loss: 0/0 (0.0%)",
                targetStatus = "unavailable",
                controlSessionId = null,
                streamSessionIdHex = null,
            )
    }
}

class VisualizerMetrics(
    private val targetLatencyMillis: Long = DEFAULT_TARGET_LATENCY_MILLIS,
) {
    private var sessionKey: SessionKey? = null
    private var firstSequence: Long? = null
    private var lastSequence: Long? = null
    private var acceptedCount: Long = 0L
    private var snapshot: VisualizerMetricSnapshot = VisualizerMetricSnapshot.empty(targetLatencyMillis)

    fun record(
        input: UdpReceivedInput,
        desktopRenderElapsedNanos: Long,
        clockOffset: VisualizerClockOffset? = null,
    ): VisualizerMetricSnapshot {
        val nextKey = SessionKey(input.controlSessionId, input.streamSessionIdHex)
        if (sessionKey != nextKey) {
            sessionKey = nextKey
            firstSequence = null
            lastSequence = null
            acceptedCount = 0L
        }

        if (firstSequence == null) {
            firstSequence = input.lastAcceptedSequence
        }
        lastSequence = input.lastAcceptedSequence
        acceptedCount += 1L

        val expected = expectedPackets()
        val missed = (expected - acceptedCount).coerceAtLeast(0L)
        val percent = if (expected == 0L) 0.0 else round((missed.toDouble() / expected.toDouble()) * 1_000.0) / 10.0
        val captureToSendMillis = nanosToMillis(input.sendElapsedNanos - input.captureElapsedNanos)
        val receiveToRenderMillis = nanosToMillis(desktopRenderElapsedNanos - input.receivedElapsedNanos)
        val latencyMillis = clockOffset?.let { offset ->
            val captureOnDesktopClock = input.captureElapsedNanos + offset.androidToDesktopNanos
            nanosToMillis(desktopRenderElapsedNanos - captureOnDesktopClock)
        }
        val targetStatus = when {
            latencyMillis == null -> "unavailable"
            latencyMillis < targetLatencyMillis -> "pass"
            else -> "warn"
        }

        snapshot = VisualizerMetricSnapshot(
            headlineLatencyMillis = latencyMillis,
            headlineLatencyLabel = latencyLabel(latencyMillis, targetStatus),
            offsetQuality = clockOffset?.quality ?: VisualizerClockOffsetQuality.UNAVAILABLE,
            captureToSendMillis = captureToSendMillis,
            receiveToRenderMillis = receiveToRenderMillis,
            sampleAgeMillis = receiveToRenderMillis,
            packetExpected = expected,
            packetMissed = missed,
            packetLossPercent = percent,
            packetLossLabel = "Packet loss: $missed/$expected ($percent%)",
            targetStatus = targetStatus,
            controlSessionId = input.controlSessionId,
            streamSessionIdHex = input.streamSessionIdHex,
        )
        return snapshot
    }

    fun reset(): VisualizerMetricSnapshot {
        sessionKey = null
        firstSequence = null
        lastSequence = null
        acceptedCount = 0L
        snapshot = VisualizerMetricSnapshot.empty(targetLatencyMillis)
        return snapshot
    }

    fun snapshot(): VisualizerMetricSnapshot = snapshot

    private fun expectedPackets(): Long {
        val first = firstSequence ?: return 0L
        val last = lastSequence ?: return 0L
        return (last - first + 1L).coerceAtLeast(acceptedCount)
    }

    private fun latencyLabel(latencyMillis: Long?, targetStatus: String): String =
        if (latencyMillis == null) {
            "Latency: unavailable | target <$targetLatencyMillis ms"
        } else {
            "Latency: $latencyMillis ms | target <$targetLatencyMillis ms | $targetStatus"
        }

    companion object {
        const val DEFAULT_TARGET_LATENCY_MILLIS = 50L
    }
}

private data class SessionKey(
    val controlSessionId: String,
    val streamSessionIdHex: String,
)

private fun nanosToMillis(nanos: Long): Long =
    (nanos.coerceAtLeast(0L)) / 1_000_000L
