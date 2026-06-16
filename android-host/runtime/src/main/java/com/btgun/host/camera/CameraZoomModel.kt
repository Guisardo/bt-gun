package com.btgun.host.camera

data class CameraZoomState(
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val currentZoom: Float = 1f,
    val hardwareSupported: Boolean = false,
) {
    val label: String
        get() = "%.1fx".format(currentZoom)
}

object CameraZoomModel {
    fun stateFor(maxHardwareZoom: Float?): CameraZoomState {
        val max = maxHardwareZoom?.takeIf { it.isFinite() && it > 1f } ?: 1f
        return CameraZoomState(
            minZoom = 1f,
            maxZoom = max,
            currentZoom = 1f,
            hardwareSupported = max > 1f,
        )
    }

    fun setZoom(state: CameraZoomState, requestedZoom: Float): CameraZoomState =
        state.copy(currentZoom = requestedZoom.coerceIn(state.minZoom, state.maxZoom))

    fun step(state: CameraZoomState, delta: Float): CameraZoomState =
        setZoom(state, state.currentZoom + delta)
}
