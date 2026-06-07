package com.btgun.host.recenter

import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.StatusEvent
import com.btgun.host.model.StreamKind
import com.btgun.host.model.StreamSequencer

data class RecenterEvent(
    val baselineElapsedNanos: Long,
    val statusLabel: String,
)

data class ReloadHoldState(
    val isReloadHeld: Boolean = false,
    val pressedElapsedNanos: Long? = null,
    val recenterEmitted: Boolean = false,
)

val StatusEvent.recenterEvent: RecenterEvent
    get() = RecenterEvent(
        baselineElapsedNanos = requireNotNull(baselineElapsedNanos) {
            "recenter status must include baselineElapsedNanos"
        },
        statusLabel = statusLabel ?: message.orEmpty(),
    )

class ReloadHoldRecenter(
    private val sequencer: StreamSequencer = StreamSequencer(),
) {
    var state: ReloadHoldState = ReloadHoldState()
        private set

    fun onReload(pressed: Boolean, nowElapsedNanos: Long): List<LiveEnvelope<GunEvent>> {
        state = if (pressed) {
            ReloadHoldState(
                isReloadHeld = true,
                pressedElapsedNanos = nowElapsedNanos,
                recenterEmitted = false,
            )
        } else {
            ReloadHoldState()
        }

        return listOf(
            LiveEnvelope(
                stream = StreamKind.GUN,
                seq = sequencer.next(StreamKind.GUN),
                captureElapsedNanos = nowElapsedNanos,
                emittedElapsedNanos = nowElapsedNanos,
                payload = GunEvent(
                    name = RELOAD_EVENT_NAME,
                    pressed = pressed,
                ),
            ),
        )
    }

    fun onTick(nowElapsedNanos: Long): List<LiveEnvelope<StatusEvent>> {
        val pressedAt = state.pressedElapsedNanos
        if (!state.isReloadHeld || pressedAt == null || state.recenterEmitted) {
            return emptyList()
        }

        if (nowElapsedNanos - pressedAt < RELOAD_HOLD_NANOS) {
            return emptyList()
        }

        state = state.copy(recenterEmitted = true)
        return listOf(
            LiveEnvelope(
                stream = StreamKind.STATUS,
                seq = sequencer.next(StreamKind.STATUS),
                captureElapsedNanos = nowElapsedNanos,
                emittedElapsedNanos = nowElapsedNanos,
                payload = StatusEvent(
                    name = RECENTER_EVENT_NAME,
                    message = RECENTER_STATUS_LABEL,
                    baselineElapsedNanos = nowElapsedNanos,
                    statusLabel = RECENTER_STATUS_LABEL,
                ),
            ),
        )
    }

    companion object {
        const val RELOAD_HOLD_NANOS: Long = 2_000_000_000L
        const val RECENTER_EVENT_NAME: String = "recenter"
        const val RECENTER_STATUS_LABEL: String = "recenter emitted"
        private const val RELOAD_EVENT_NAME: String = "reload"
    }
}
