package com.btgun.desktop.transport

enum class InputReplayRejectReason {
    WRONG_CONTROL_SESSION,
    WRONG_STREAM_SESSION,
    DUPLICATE_SEQUENCE,
    OLD_SEQUENCE,
    AGE_EXPIRED,
    CONTROL_GRACE_EXPIRED,
    BAD_HMAC,
    MALFORMED,
}

sealed interface InputReplayDecision {
    data class Accepted(val input: UdpReceivedInput) : InputReplayDecision
    data class Rejected(val reason: InputReplayRejectReason) : InputReplayDecision
}

class InputReplayGuard(
    private val trustedControlSessionId: String,
    private val config: InputStreamConfig,
) {
    var current: UdpReceivedInput? = null
        private set

    private var highestAcceptedSequence: Long? = null

    fun acceptDatagram(
        bytes: ByteArray,
        receivedElapsedNanos: Long,
        controlSessionId: String,
    ): InputReplayDecision =
        when (val decoded = UdpInputFrameCodec.authenticateAndDecode(bytes, config)) {
            is UdpInputFrameDecodeResult.Accepted -> accept(decoded.frame, receivedElapsedNanos, controlSessionId)
            is UdpInputFrameDecodeResult.Rejected -> InputReplayDecision.Rejected(decoded.reason.toReplayRejectReason())
        }

    fun accept(
        frame: UdpInputFrame,
        receivedElapsedNanos: Long,
        controlSessionId: String,
    ): InputReplayDecision {
        if (controlSessionId != trustedControlSessionId) {
            return InputReplayDecision.Rejected(InputReplayRejectReason.WRONG_CONTROL_SESSION)
        }
        if (frame.streamSessionId != config.streamSessionIdHex) {
            return InputReplayDecision.Rejected(InputReplayRejectReason.WRONG_STREAM_SESSION)
        }
        val senderLocalFrameAgeNanos = frame.sendElapsedNanos - frame.captureElapsedNanos
        if (senderLocalFrameAgeNanos > config.frameAgeLimitMs * NANOS_PER_MILLISECOND) {
            return InputReplayDecision.Rejected(InputReplayRejectReason.AGE_EXPIRED)
        }
        // Android and desktop monotonic clocks have unrelated origins. Do not
        // compare sendElapsedNanos with receivedElapsedNanos until a trusted
        // control-channel clock offset exists; sequence replay, Android-local
        // capture-to-send age, stream timeout, and control-disconnect grace
        // still bound stale input.
        val highest = highestAcceptedSequence
        if (highest != null) {
            if (frame.sequence == highest) {
                return InputReplayDecision.Rejected(InputReplayRejectReason.DUPLICATE_SEQUENCE)
            }
            if (frame.sequence < highest) {
                return InputReplayDecision.Rejected(InputReplayRejectReason.OLD_SEQUENCE)
            }
        }

        val input = frame.toReceivedInput(
            controlSessionId = controlSessionId,
            receivedElapsedNanos = receivedElapsedNanos,
        )
        highestAcceptedSequence = frame.sequence
        current = input
        return InputReplayDecision.Accepted(input)
    }

    fun onTimeout(current: UdpReceivedInput): UdpReceivedInput =
        current.copy(
            buttons = 0,
            pressedControls = emptySet(),
            stickX = 0,
            stickY = 0,
            stale = true,
        ).also { this.current = it }

}

private const val NANOS_PER_MILLISECOND = 1_000_000L

private fun UdpInputFrameRejectReason.toReplayRejectReason(): InputReplayRejectReason =
    when (this) {
        UdpInputFrameRejectReason.INVALID_LENGTH,
        UdpInputFrameRejectReason.BAD_MAGIC,
        UdpInputFrameRejectReason.UNSUPPORTED_VERSION,
        UdpInputFrameRejectReason.UNKNOWN_TYPE,
        UdpInputFrameRejectReason.MALFORMED_FIELD,
        -> InputReplayRejectReason.MALFORMED
        UdpInputFrameRejectReason.WRONG_STREAM_SESSION -> InputReplayRejectReason.WRONG_STREAM_SESSION
        UdpInputFrameRejectReason.BAD_HMAC -> InputReplayRejectReason.BAD_HMAC
    }
