package com.btgun.host.ui

import com.btgun.host.HostSessionPhase
import com.btgun.host.HostSessionState
import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.ble.BleGunConnectionState
import com.btgun.host.haptics.PhoneHapticStatus
import com.btgun.host.model.GunEvent
import com.btgun.host.model.GunInputState
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionSample
import com.btgun.host.model.Provenance
import com.btgun.host.model.StatusEvent
import com.btgun.host.motion.AimBaseline
import com.btgun.host.motion.AimCalibrationMark
import com.btgun.host.motion.AimCalibrationMode
import com.btgun.host.motion.AimCalibrationState
import com.btgun.host.motion.MotionCapabilityFlags
import com.btgun.host.motion.PreviewAim
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.recenter.ReloadHoldRecenter
import com.btgun.host.recenter.ReloadHoldState
import com.btgun.host.recenter.recenterEvent
import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState

enum class DashboardEventMode {
    PRODUCT_EVENTS,
    DEBUG_PROVENANCE,
}

data class DashboardField(
    val label: String,
    val value: String,
)

data class PermissionUiState(
    val title: String,
    val body: String,
    val actionLabel: String,
    val visible: Boolean,
    val details: String,
)

data class DashboardPreviewAim(
    val label: String,
    val x: Float,
    val y: Float,
    val enabled: Boolean,
    val statusLabel: String,
    val baselineElapsedNanos: Long,
    val rawX: Float,
    val rawY: Float,
    val calibrated: Boolean,
    val latencyMillis: Long?,
)

data class DashboardAimGraph(
    val x: Float,
    val y: Float,
    val enabled: Boolean,
    val calibrated: Boolean,
    val statusLabel: String,
    val activeMark: AimCalibrationMark?,
    val capturedMarks: List<AimCalibrationMark>,
    val latencyMillis: Long?,
)

data class DashboardPhoneHaptic(
    val label: String,
    val capability: String,
    val lastLocalTest: String,
)

data class PlaceholderSurface(
    val title: String,
    val body: String,
    val active: Boolean,
)

data class DashboardPlaceholders(
    val desktopLink: PlaceholderSurface = PlaceholderSurface(
        title = "Desktop link",
        body = "Not built yet. Pending Phase 3.",
        active = false,
    ),
    val packetStream: PlaceholderSurface = PlaceholderSurface(
        title = "Packet stream",
        body = "Not built yet. Pending Phase 4.",
        active = false,
    ),
)

data class DebugExpansion(
    val bleProvenance: Boolean = false,
    val permissionState: Boolean = false,
    val gattStatus: Boolean = false,
)

data class DebugPanel(
    val title: String,
    val expanded: Boolean,
    val body: String,
)

data class DashboardDebugPanels(
    val bleProvenance: DebugPanel,
    val permissionState: DebugPanel,
    val gattStatus: DebugPanel,
)

data class DashboardState(
    val appTitle: String,
    val permission: PermissionUiState,
    val primaryActionLabel: String,
    val hapticActionLabel: String,
    val emptyStateHeading: String,
    val emptyStateBody: String,
    val eventMode: DashboardEventMode,
    val gunConnection: DashboardField,
    val lastGunEvent: DashboardField,
    val activeGunControls: DashboardField,
    val motionProvider: DashboardField,
    val motionCapabilities: DashboardField,
    val previewAim: DashboardPreviewAim,
    val aimGraph: DashboardAimGraph,
    val aimCalibration: DashboardField,
    val recenterState: DashboardField,
    val foregroundService: DashboardField,
    val currentError: DashboardField,
    val placeholders: DashboardPlaceholders,
    val phoneHaptic: DashboardPhoneHaptic,
    val debugPanels: DashboardDebugPanels,
) {
    override fun toString(): String =
        "DashboardState(appTitle=$appTitle,eventMode=$eventMode,phoneHaptic=$phoneHaptic)"

    companion object {
        fun initial(permissionGateState: PermissionGateState): DashboardState =
            from(permissionGateState = permissionGateState, hostSessionState = HostSessionState())

        fun from(
            permissionGateState: PermissionGateState,
            hostSessionState: HostSessionState,
            bleConnectionState: BleGunConnectionState = BleGunConnectionState(),
            lastGunEvent: LiveEnvelope<GunEvent>? = hostSessionState.lastGunEvent,
            gunInputState: GunInputState = hostSessionState.gunInputState,
            lastMotionSample: LiveEnvelope<MotionSample>? = hostSessionState.lastMotionSample,
            previewAim: PreviewAim? = null,
            aimBaseline: AimBaseline? = null,
            aimCalibrationState: AimCalibrationState = hostSessionState.aimCalibrationState,
            reloadHoldState: ReloadHoldState = hostSessionState.reloadHoldState,
            lastRecenterStatus: LiveEnvelope<StatusEvent>? = hostSessionState.lastRecenterStatus,
            phoneHapticStatus: PhoneHapticStatus = PhoneHapticStatus.available(),
            desktopLinkState: DesktopLinkState = DesktopLinkState(),
            debugExpanded: DebugExpansion = DebugExpansion(),
            eventMode: DashboardEventMode = DashboardEventMode.PRODUCT_EVENTS,
            nowElapsedNanos: Long = lastGunEvent?.emittedElapsedNanos
                ?: lastMotionSample?.emittedElapsedNanos
                ?: lastRecenterStatus?.emittedElapsedNanos
                ?: 0L,
        ): DashboardState {
            val placeholders = DashboardPlaceholders(
                desktopLink = formatDesktopLink(desktopLinkState),
            )
            return DashboardState(
                appTitle = "BT Gun Host",
                permission = permissionState(permissionGateState),
                primaryActionLabel = if (hostSessionState.isActive) "Stop session" else "Start live session",
                hapticActionLabel = "Test phone vibration",
                emptyStateHeading = "No live gun input yet",
                emptyStateBody = "Start a live session, then press a gun control or move the phone to see product events here.",
                eventMode = eventMode,
                gunConnection = DashboardField("Gun connection", gunConnectionValue(hostSessionState, bleConnectionState)),
                lastGunEvent = DashboardField("Last gun event", formatGunEvent(lastGunEvent)),
                activeGunControls = DashboardField("Active controls", formatActiveControls(gunInputState)),
                motionProvider = DashboardField("Motion provider", lastMotionSample?.payload?.providerName ?: "unavailable"),
                motionCapabilities = DashboardField("Motion capability flags", formatCapabilities(lastMotionSample?.payload?.capabilities)),
                previewAim = formatPreview(previewAim, aimBaseline),
                aimGraph = formatAimGraph(previewAim, aimCalibrationState),
                aimCalibration = DashboardField("Aim calibration", formatCalibration(aimCalibrationState)),
                recenterState = DashboardField("Recenter state", formatRecenter(reloadHoldState, lastRecenterStatus, nowElapsedNanos)),
                foregroundService = DashboardField("Foreground service", if (hostSessionState.foregroundActive) "running" else "stopped"),
                currentError = DashboardField("Current error", hostSessionState.lastError ?: bleConnectionState.lastError ?: "none"),
                placeholders = placeholders,
                phoneHaptic = DashboardPhoneHaptic(
                    label = "Phone haptic",
                    capability = phoneHapticStatus.capability,
                    lastLocalTest = phoneHapticStatus.lastLocalTest,
                ),
                debugPanels = DashboardDebugPanels(
                    bleProvenance = DebugPanel(
                        title = "BLE provenance",
                        expanded = debugExpanded.bleProvenance,
                        body = formatProvenance(lastGunEvent?.provenance),
                    ),
                    permissionState = DebugPanel(
                        title = "Permission state",
                        expanded = debugExpanded.permissionState,
                        body = formatPermissionDebug(permissionGateState),
                    ),
                    gattStatus = DebugPanel(
                        title = "GATT status",
                        expanded = debugExpanded.gattStatus,
                        body = bleConnectionState.lastError ?: hostSessionState.lastStatusEvent?.payload?.message ?: "no gatt status",
                    ),
                ),
            )
        }

        private fun permissionState(state: PermissionGateState): PermissionUiState =
            PermissionUiState(
                title = "Enable host permissions",
                body = "Bluetooth, nearby device, location, sensors, and LAN access are needed before the host can scan, connect, and show live input.",
                actionLabel = "Grant permissions",
                visible = !state.canStartSession,
                details = listOf(
                    state.bluetoothScan,
                    state.bluetoothConnect,
                    state.locationScanCompatibility,
                    state.motionSensors,
                    state.lanNetwork,
                    state.vibration,
                ).joinToString("\n") { "${it.label}: ${it.detail}" },
            )

        private fun gunConnectionValue(
            hostSessionState: HostSessionState,
            bleConnectionState: BleGunConnectionState,
        ): String =
            when {
                hostSessionState.phase == HostSessionPhase.CONNECTED ||
                    bleConnectionState.phase == BleGunConnectionPhase.CONNECTED -> "connected"
                hostSessionState.phase == HostSessionPhase.RECONNECTING ||
                    bleConnectionState.phase == BleGunConnectionPhase.RECONNECTING -> "Reconnecting..."
                else -> hostSessionState.phase.wireName
            }

        private fun formatGunEvent(event: LiveEnvelope<GunEvent>?): String =
            if (event == null) {
                "none"
            } else {
                val state = when (event.payload.pressed) {
                    true -> "down"
                    false -> "up"
                    null -> ""
                }
                val axis = if (event.payload.axisX != null || event.payload.axisY != null) {
                    "x=${formatAxis(event.payload.axisX ?: 0f)} y=${formatAxis(event.payload.axisY ?: 0f)}"
                } else {
                    ""
                }
                listOf(
                    listOf(event.payload.name, state, axis).filter { it.isNotEmpty() }.joinToString(" "),
                    "seq=${event.seq}",
                    "elapsed=${event.captureElapsedNanos}ns",
                ).joinToString(" | ")
            }

        private fun formatActiveControls(state: GunInputState): String =
            state.activeControls()
                .map { control ->
                    if (control == "stick") {
                        "stick x=${formatAxis(state.stickAxisX)} y=${formatAxis(state.stickAxisY)}"
                    } else {
                        control
                    }
                }
                .ifEmpty { listOf("none") }
                .joinToString(", ")

        private fun formatAxis(value: Float): String =
            java.lang.String.format(java.util.Locale.US, "%.1f", value)

        private fun formatCapabilities(capabilities: MotionCapabilityFlags?): String =
            if (capabilities == null) {
                "unavailable"
            } else {
                listOf(
                    "game_rotation_vector=${capabilities.gameRotationVector}",
                    "rotation_vector=${capabilities.rotationVector}",
                    "gyroscope=${capabilities.gyroscope}",
                    "accelerometer=${capabilities.accelerometer}",
                    "gravity=${capabilities.gravity}",
                    "tilt_fallback=${capabilities.tiltFallback}",
                    "timestamp_source=${capabilities.timestampSource}",
                ).joinToString(" | ")
            }

        private fun formatPreview(previewAim: PreviewAim?, aimBaseline: AimBaseline?): DashboardPreviewAim =
            DashboardPreviewAim(
                label = "Preview aim",
                x = previewAim?.x ?: 0f,
                y = previewAim?.y ?: 0f,
                enabled = previewAim?.padEnabled ?: false,
                statusLabel = previewAim?.statusLabel ?: "Motion unavailable",
                baselineElapsedNanos = previewAim?.baselineElapsedNanos ?: aimBaseline?.elapsedNanos ?: 0L,
                rawX = previewAim?.rawX ?: 0f,
                rawY = previewAim?.rawY ?: 0f,
                calibrated = previewAim?.calibrated ?: false,
                latencyMillis = previewAim?.latencyMillis,
            )

        private fun formatAimGraph(
            previewAim: PreviewAim?,
            calibrationState: AimCalibrationState,
        ): DashboardAimGraph =
            DashboardAimGraph(
                x = previewAim?.x ?: 0f,
                y = previewAim?.y ?: 0f,
                enabled = previewAim?.padEnabled ?: false,
                calibrated = previewAim?.calibrated == true,
                statusLabel = calibrationState.statusLabel,
                activeMark = calibrationState.activeMark,
                capturedMarks = calibrationState.capturedPoints.map { point -> point.mark },
                latencyMillis = previewAim?.latencyMillis,
            )

        private fun formatCalibration(state: AimCalibrationState): String {
            val progress = if (state.capturedPoints.isNotEmpty()) {
                " | captured=${state.capturedPoints.size}/4"
            } else {
                ""
            }
            val active = if (state.isCalibrated) " | active" else ""
            val failure = if (state.mode == AimCalibrationMode.FAILED && state.error != null) {
                " | error=${state.error}"
            } else {
                ""
            }
            return state.statusLabel + progress + active + failure
        }

        private fun formatRecenter(
            reloadHoldState: ReloadHoldState,
            lastRecenterStatus: LiveEnvelope<StatusEvent>?,
            nowElapsedNanos: Long,
        ): String {
            if (lastRecenterStatus != null) {
                val recenter = lastRecenterStatus.payload.recenterEvent
                return "${recenter.statusLabel} | baseline=${recenter.baselineElapsedNanos}ns"
            }
            val pressedAt = reloadHoldState.pressedElapsedNanos
            if (!reloadHoldState.isReloadHeld || pressedAt == null) {
                return "idle"
            }
            val elapsedMs = ((nowElapsedNanos - pressedAt).coerceAtLeast(0L) / 1_000_000L)
            return if (elapsedMs >= 500L) {
                "hold ${elapsedMs}ms / 2000ms"
            } else {
                "reload held"
            }
        }

        private fun formatProvenance(provenance: Provenance?): String =
            if (provenance == null) {
                "no provenance"
            } else {
                listOf(
                    "raw_ascii=${provenance.rawAscii.orEmpty()}",
                    "raw_hex=${provenance.rawHex.orEmpty()}",
                    "service=${provenance.bleServiceUuid.orEmpty()}",
                    "characteristic=${provenance.bleCharacteristicUuid.orEmpty()}",
                    "clue_id=${provenance.clueId.orEmpty()}",
                    "capture_id=${provenance.captureId.orEmpty()}",
                    "confidence=${provenance.semanticConfidence}",
                ).joinToString("\n")
            }

        private fun formatPermissionDebug(state: PermissionGateState): String =
            listOf(
                "model=${state.bluetoothPermissionModel}",
                "bluetooth_scan=${state.bluetoothScan.label}",
                "bluetooth_connect=${state.bluetoothConnect.label}",
                "location=${state.locationScanCompatibility.label}",
                "motion=${state.motionSensors.label}",
                "lan=${state.lanNetwork.label}",
                "vibration=${state.vibration.label}",
            ).joinToString("\n")

        private fun formatDesktopLink(state: DesktopLinkState): PlaceholderSurface =
            PlaceholderSurface(
                title = "Desktop link",
                body = listOf(
                    state.diagnosticText,
                    "session_state=${state.phase.wireName}",
                    "primary_action=${state.primaryActionLabel}",
                    "manual_action=${state.manualActionLabel}",
                    when (state.phase) {
                        DesktopLinkPhase.DISCONNECTED -> "Rescan QR"
                        else -> null
                    },
                ).filterNotNull().joinToString(" | "),
                active = true,
            )
    }
}
