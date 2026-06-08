package com.btgun.desktop.transport

enum class InputReplayRejectReason {
    WRONG_CONTROL_SESSION,
    WRONG_STREAM_SESSION,
    DUPLICATE_SEQUENCE,
    OLD_SEQUENCE,
    CONTROL_GRACE_EXPIRED,
    BAD_HMAC,
    MALFORMED,
    AGE_EXPIRED,
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
        // Android and desktop monotonic clocks have unrelated origins. Do not
        // compare sendElapsedNanos with receivedElapsedNanos until a trusted
        // control-channel clock offset exists; sequence replay, stream timeout,
        // and control-disconnect grace still bound stale input.
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
