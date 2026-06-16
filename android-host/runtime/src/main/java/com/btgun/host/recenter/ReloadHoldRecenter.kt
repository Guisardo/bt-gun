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
            if (state.isReloadHeld) {
                state
            } else {
                ReloadHoldState(
                    isReloadHeld = true,
                    pressedElapsedNanos = nowElapsedNanos,
                    recenterEmitted = false,
                )
            }
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
        if (!state.isReloadHeld || pressedAt == null) {
            return emptyList()
        }

        val emitted = mutableListOf<LiveEnvelope<StatusEvent>>()
        val heldNanos = nowElapsedNanos - pressedAt
        if (!state.recenterEmitted && heldNanos >= RELOAD_HOLD_NANOS) {
            state = state.copy(recenterEmitted = true)
            emitted += statusEvent(
                nowElapsedNanos = nowElapsedNanos,
                name = RECENTER_EVENT_NAME,
                label = RECENTER_STATUS_LABEL,
            )
        }
        return emitted
    }

    private fun statusEvent(
        nowElapsedNanos: Long,
        name: String,
        label: String,
    ): LiveEnvelope<StatusEvent> =
        LiveEnvelope(
            stream = StreamKind.STATUS,
            seq = sequencer.next(StreamKind.STATUS),
            captureElapsedNanos = nowElapsedNanos,
            emittedElapsedNanos = nowElapsedNanos,
            payload = StatusEvent(
                name = name,
                message = label,
                baselineElapsedNanos = nowElapsedNanos.takeIf { name == RECENTER_EVENT_NAME },
                statusLabel = label,
            ),
        )

    companion object {
        const val RELOAD_HOLD_NANOS: Long = 2_000_000_000L
        const val RECENTER_EVENT_NAME: String = "recenter"
        const val RECENTER_STATUS_LABEL: String = "recenter emitted"
        private const val RELOAD_EVENT_NAME: String = "reload"
    }
}
