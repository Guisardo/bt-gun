package com.btgun.host.ui

import com.btgun.host.HostSessionPhase
import com.btgun.host.HostSessionState
import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.ble.BleGunConnectionState
import com.btgun.host.haptics.PhoneHapticStatus
import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.Provenance
import com.btgun.host.model.SemanticConfidence
import com.btgun.host.model.StatusEvent
import com.btgun.host.model.StreamKind
import com.btgun.host.motion.AimBaseline
import com.btgun.host.motion.MotionCapabilityFlags
import com.btgun.host.motion.PreviewAim
import com.btgun.host.permissions.BluetoothPermissionModel
import com.btgun.host.permissions.CapabilityState
import com.btgun.host.permissions.CapabilityStatus
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.recenter.ReloadHoldState

fun main() {
    initialStateUsesRequiredShellCopyAndCollapsedDebugPanels()
    connectedSessionShowsServiceErrorAndLastGunEvent()
    motionProviderShowsCapabilitiesPreviewAndBaseline()
    recenterShowsCountdownEmissionAndReloadVisibility()
    debugDetailsStayCollapsedUntilToggled()
    futureDesktopAndPacketSurfacesStayInactive()
    phoneHapticsStayLocalOnly()
}

private fun initialStateUsesRequiredShellCopyAndCollapsedDebugPanels() {
    val state = DashboardState.initial(permissionGateState())

    expectEquals("app title", "BT Gun Host", state.appTitle)
    expectEquals("permission gate title", "Enable host permissions", state.permission.title)
    expectEquals(
        "permission gate body",
        "Bluetooth, nearby device, location, sensors, and LAN access are needed before the host can scan, connect, and show live input.",
        state.permission.body,
    )
    expectEquals("permission action", "Grant permissions", state.permission.actionLabel)
    expectEquals("primary action", "Start live session", state.primaryActionLabel)
    expectEquals("empty heading", "No live gun input yet", state.emptyStateHeading)
    expectEquals(
        "empty body",
        "Start a live session, then press a gun control or move the phone to see product events here.",
        state.emptyStateBody,
    )
    expectEquals("event mode", DashboardEventMode.PRODUCT_EVENTS, state.eventMode)
    expectFalse("ble debug collapsed", state.debugPanels.bleProvenance.expanded)
    expectFalse("permission debug collapsed", state.debugPanels.permissionState.expanded)
    expectFalse("gatt debug collapsed", state.debugPanels.gattStatus.expanded)
}

private fun connectedSessionShowsServiceErrorAndLastGunEvent() {
    val state = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(
            phase = HostSessionPhase.CONNECTED,
            foregroundActive = true,
            lastError = "last gatt status ok",
        ),
        bleConnectionState = BleGunConnectionState(
            phase = BleGunConnectionPhase.CONNECTED,
            lastError = "fff3_notifications_enabled",
        ),
        lastGunEvent = gunEvent(
            seq = 42L,
            captureElapsedNanos = 1_234_000_000L,
            name = "trigger",
            pressed = true,
        ),
    )

    expectEquals("gun label", "Gun connection", state.gunConnection.label)
    expectEquals("gun value", "connected", state.gunConnection.value)
    expectEquals("foreground label", "Foreground service", state.foregroundService.label)
    expectEquals("foreground value", "running", state.foregroundService.value)
    expectEquals("error label", "Current error", state.currentError.label)
    expectEquals("error value", "last gatt status ok", state.currentError.value)
    expectEquals("last event label", "Last gun event", state.lastGunEvent.label)
    expectEquals("last event text", "trigger down | seq=42 | elapsed=1234000000ns", state.lastGunEvent.value)
}

private fun motionProviderShowsCapabilitiesPreviewAndBaseline() {
    val capabilities = MotionCapabilityFlags(
        gameRotationVector = true,
        rotationVector = true,
        gyroscope = true,
        accelerometer = true,
        gravity = true,
    )
    val state = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(phase = HostSessionPhase.CONNECTED, foregroundActive = true),
        lastMotionSample = LiveEnvelope(
            stream = StreamKind.MOTION,
            seq = 7L,
            captureElapsedNanos = 5_000L,
            emittedElapsedNanos = 6_000L,
            payload = MotionSample(
                provider = MotionProvider.GAME_ROTATION_VECTOR,
                providerName = "game_rotation_vector",
                capabilities = capabilities,
                sourceSensorElapsedNanos = 5_000L,
                yaw = 11f,
                pitch = -6f,
                roll = 0f,
            ),
        ),
        previewAim = PreviewAim(
            x = 0.25f,
            y = -0.5f,
            padEnabled = true,
            statusLabel = "Preview calibration",
            baselineElapsedNanos = 4_000L,
        ),
        aimBaseline = AimBaseline(yaw = 10f, pitch = -5f, roll = 0f, elapsedNanos = 4_000L),
    )

    expectEquals("motion label", "Motion provider", state.motionProvider.label)
    expectEquals("motion value", "game_rotation_vector", state.motionProvider.value)
    expectContains("capability game", state.motionCapabilities.value, "game_rotation_vector=true")
    expectContains("capability gyro", state.motionCapabilities.value, "gyroscope=true")
    expectEquals("preview label", "Preview aim", state.previewAim.label)
    expectEquals("preview x", 0.25f, state.previewAim.x)
    expectEquals("preview y", -0.5f, state.previewAim.y)
    expectEquals("preview baseline", 4_000L, state.previewAim.baselineElapsedNanos)
}

private fun recenterShowsCountdownEmissionAndReloadVisibility() {
    val state = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(phase = HostSessionPhase.CONNECTED, foregroundActive = true),
        reloadHoldState = ReloadHoldState(
            isReloadHeld = true,
            pressedElapsedNanos = 1_000_000_000L,
            recenterEmitted = false,
        ),
        nowElapsedNanos = 1_500_000_000L,
        lastGunEvent = gunEvent(
            seq = 3L,
            captureElapsedNanos = 1_000_000_000L,
            name = "reload",
            pressed = true,
        ),
    )

    expectEquals("reload visible", "reload down | seq=3 | elapsed=1000000000ns", state.lastGunEvent.value)
    expectEquals("recenter label", "Recenter state", state.recenterState.label)
    expectEquals("countdown", "hold 500ms / 2000ms", state.recenterState.value)

    val emitted = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(phase = HostSessionPhase.CONNECTED, foregroundActive = true),
        reloadHoldState = ReloadHoldState(
            isReloadHeld = true,
            pressedElapsedNanos = 1_000_000_000L,
            recenterEmitted = true,
        ),
        lastRecenterStatus = LiveEnvelope(
            stream = StreamKind.STATUS,
            seq = 1L,
            captureElapsedNanos = 3_000_000_000L,
            emittedElapsedNanos = 3_000_000_000L,
            payload = StatusEvent(
                name = "recenter",
                message = "recenter emitted",
                baselineElapsedNanos = 3_000_000_000L,
                statusLabel = "recenter emitted",
            ),
        ),
    )
    expectEquals("emitted value", "recenter emitted | baseline=3000000000ns", emitted.recenterState.value)
}

private fun debugDetailsStayCollapsedUntilToggled() {
    val state = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(phase = HostSessionPhase.CONNECTED, foregroundActive = true),
        bleConnectionState = BleGunConnectionState(
            phase = BleGunConnectionPhase.CONNECTED,
            lastError = "fff3_notifications_enabled",
        ),
        lastGunEvent = gunEvent(
            seq = 5L,
            captureElapsedNanos = 2_000L,
            name = "button_x",
            pressed = true,
            provenance = Provenance(
                rawAscii = "BADOWN",
                rawHex = "4241444f574e",
                bleServiceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
                bleCharacteristicUuid = "0000fff3-0000-1000-8000-00805f9b34fb",
                clueId = "ARCHER-INPUT-001",
                captureId = "button-x-001",
                semanticConfidence = SemanticConfidence.CANDIDATE,
            ),
        ),
        debugExpanded = DebugExpansion(
            bleProvenance = true,
            permissionState = true,
            gattStatus = true,
        ),
        eventMode = DashboardEventMode.DEBUG_PROVENANCE,
    )

    expectEquals("debug mode", DashboardEventMode.DEBUG_PROVENANCE, state.eventMode)
    expectTrue("ble expanded", state.debugPanels.bleProvenance.expanded)
    expectContains("raw ascii", state.debugPanels.bleProvenance.body, "raw_ascii=BADOWN")
    expectContains("capture id", state.debugPanels.bleProvenance.body, "capture_id=button-x-001")
    expectContains("permission model", state.debugPanels.permissionState.body, "ANDROID_12_NEARBY_DEVICES")
    expectContains("gatt status", state.debugPanels.gattStatus.body, "fff3_notifications_enabled")
}

private fun futureDesktopAndPacketSurfacesStayInactive() {
    val state = DashboardState.initial(permissionGateState())

    expectEquals("desktop title", "Desktop link", state.placeholders.desktopLink.title)
    expectEquals("desktop body", "Not built yet. Pending Phase 3.", state.placeholders.desktopLink.body)
    expectFalse("desktop inactive", state.placeholders.desktopLink.active)
    expectEquals("packet title", "Packet stream", state.placeholders.packetStream.title)
    expectEquals("packet body", "Not built yet. Pending Phase 4.", state.placeholders.packetStream.body)
    expectFalse("packet inactive", state.placeholders.packetStream.active)
}

private fun phoneHapticsStayLocalOnly() {
    val state = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(),
        phoneHapticStatus = PhoneHapticStatus.started(durationMs = 1_000L),
    )

    expectEquals("haptic label", "Phone haptic", state.phoneHaptic.label)
    expectEquals("haptic capability", "Phone vibration available", state.phoneHaptic.capability)
    expectEquals("haptic status", "started | local phone vibration 1000ms", state.phoneHaptic.lastLocalTest)
    val text = state.toString().lowercase()
    listOf("ack", "ttl", "transport", "desktop command", "command_id").forEach { forbidden ->
        expectFalse("no desktop haptic token $forbidden", text.contains(forbidden))
    }
}

private fun gunEvent(
    seq: Long,
    captureElapsedNanos: Long,
    name: String,
    pressed: Boolean,
    provenance: Provenance? = null,
): LiveEnvelope<GunEvent> =
    LiveEnvelope(
        stream = StreamKind.GUN,
        seq = seq,
        captureElapsedNanos = captureElapsedNanos,
        emittedElapsedNanos = captureElapsedNanos,
        payload = GunEvent(name = name, pressed = pressed),
        provenance = provenance,
    )

private fun permissionGateState(): PermissionGateState =
    PermissionGateState(
        bluetoothPermissionModel = BluetoothPermissionModel.ANDROID_12_NEARBY_DEVICES,
        bluetoothScan = CapabilityStatus(CapabilityState.AVAILABLE, "Bluetooth scan available", "Runtime permission granted."),
        bluetoothConnect = CapabilityStatus(CapabilityState.AVAILABLE, "Bluetooth connect available", "Runtime permission granted."),
        locationScanCompatibility = CapabilityStatus(CapabilityState.AVAILABLE, "Location compatibility not required", "Android 12+ scan uses Nearby Devices."),
        motionSensors = CapabilityStatus(CapabilityState.AVAILABLE, "Motion sensors available", "Capability detected."),
        vibration = CapabilityStatus(CapabilityState.AVAILABLE, "Phone vibration available", "Capability detected."),
        lanNetwork = CapabilityStatus(CapabilityState.AVAILABLE, "LAN network available", "Capability detected."),
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, condition: Boolean) {
    if (!condition) {
        throw AssertionError(label)
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    expectTrue(label, !condition)
}

private fun expectContains(label: String, actual: String, expectedPart: String) {
    if (!actual.contains(expectedPart)) {
        throw AssertionError("$label expected <$actual> to contain <$expectedPart>")
    }
}
