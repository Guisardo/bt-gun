package com.btgun.host

import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.session.DesktopLivenessCoordinator
import com.btgun.host.session.DesktopLivenessUpdate
import com.btgun.host.haptics.DesktopHapticCommand
import com.btgun.host.haptics.HapticResult
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.hid.BtGunHidDescriptor
import com.btgun.host.hid.BtGunHidHostConnectionState
import com.btgun.host.hid.BtGunHidInputSendResult
import com.btgun.host.hid.BtGunHidOutputCallbackKind
import com.btgun.host.hid.BtGunHidOutputValidationState
import com.btgun.host.hid.BtGunHidReportTypes
import com.btgun.host.hid.BtGunHidStatus
import com.btgun.host.transport.InputStreamLifecycleState
import com.btgun.host.model.GunInputState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.permissions.PermissionGate
import com.btgun.host.permissions.PermissionGateInput
import com.btgun.host.profile.AimMappingSettings
import com.btgun.host.profile.BtGunProfile
import com.btgun.host.profile.MappedControllerState
import com.btgun.host.profile.PhysicalButton
import com.btgun.host.profile.ProfileMapper
import com.btgun.host.profile.ProfilePreferences
import com.btgun.host.profile.ProfileStore
import com.btgun.host.profile.SaveProfileResult
import com.btgun.host.profile.SmoothingMode
import com.btgun.host.profile.VirtualButton
import com.btgun.host.recenter.ReloadHoldRecenter
import com.btgun.host.recenter.ReloadHoldState
import com.btgun.host.session.VisualizerStatus

fun main() {
    heartbeatTimeoutClearSchedulesUdpDisconnectGrace()
    controlDisconnectWithoutUdpStreamStaysStopped()
    bluetoothGamepadActionConstantsAreExplicit()
    profileReloadServiceActionOnlyRunsWhenForegroundActive()
    bluetoothGamepadStartRequiresConnectPermission()
    bluetoothGamepadStartDoesNotStartLanDesktopControl()
    bluetoothGamepadStartAndPairingWindowAreSeparateActions()
    bluetoothGamepadStopSessionAndDestroyCloseHidMode()
    liveInputFanoutOnlySendsWhenHidHostConnected()
    hidFanoutDropsDuplicatesAndPrioritizesButtonEdges()
    fastAimFanoutDoesNotFloodButtonEdges()
    bleConnectionLossClearsLatchedGunInput()
    hidOutputCallbackRoutesThroughPhoneHapticExecutorStatus()
    hostProfileRuntimeLoadsActiveProfileAndMapsCurrentInput()
    hostProfileRuntimeReloadsSelectedProfileWithoutRestart()
    hidFanoutUsesMappedStateAfterServiceMapping()
    recenterUsesSelectedPhysicalControlWhileVirtualReloadPublishes()
    visualizerStatusReflectsRecenterAimZeroAndRawDebug()
    visualizerStatusDoesNotReportReadyBeforeLiveMotion()
    visualizerStatusReportsHeldEvenAfterPriorRecenter()
    visualizerStatusNoopsWithoutTrustedDesktopConnection()
    reloadDownUpEventsStillFanOutAroundRecenterStatus()
}

private fun heartbeatTimeoutClearSchedulesUdpDisconnectGrace() {
    val timeoutUpdate = DesktopLivenessUpdate(
        linkState = DesktopLinkState(
            phase = DesktopLinkPhase.DISCONNECTED,
            lastControlError = DesktopLivenessCoordinator.DEFAULT_TIMEOUT_ERROR,
        ),
        shouldContinuePolling = false,
        shouldClearClient = true,
        shouldCloseClient = true,
    )

    val action = hostDesktopLivenessActionFor(timeoutUpdate)

    expectTrue("timeout schedules udp grace", action.shouldScheduleUdpDisconnectGrace)
    expectTrue("timeout clears client", action.shouldClearClient)
    expectTrue("timeout cancels liveness tick", action.shouldCancelLivenessTick)
    expectTrue("timeout closes client", action.shouldCloseClient)
    expectFalse("timeout stops polling", action.shouldContinuePolling)
}

private fun controlDisconnectWithoutUdpStreamStaysStopped() {
    expectEquals(
        "no sender stays stopped",
        InputStreamLifecycleState.STOPPED,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = false,
            hasConfig = false,
            controlDisconnectGraceMs = null,
        ),
    )
    expectEquals(
        "missing config stays stopped",
        InputStreamLifecycleState.STOPPED,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = false,
            controlDisconnectGraceMs = null,
        ),
    )
    expectEquals(
        "zero grace marks active stream stale",
        InputStreamLifecycleState.STALE,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = true,
            controlDisconnectGraceMs = 0L,
        ),
    )
    expectEquals(
        "positive grace enters grace",
        InputStreamLifecycleState.GRACE,
        hostPacketStreamStateAfterControlDisconnect(
            hasSender = true,
            hasConfig = true,
            controlDisconnectGraceMs = 1_500L,
        ),
    )
}

private fun bluetoothGamepadActionConstantsAreExplicit() {
    expectEquals(
        "start hid action",
        "com.btgun.host.action.START_BLUETOOTH_GAMEPAD",
        HostSessionService.ACTION_START_BLUETOOTH_GAMEPAD,
    )
    expectEquals(
        "stop hid action",
        "com.btgun.host.action.STOP_BLUETOOTH_GAMEPAD",
        HostSessionService.ACTION_STOP_BLUETOOTH_GAMEPAD,
    )
    expectEquals(
        "pairing hid action",
        "com.btgun.host.action.START_HID_PAIRING_WINDOW",
        HostSessionService.ACTION_START_HID_PAIRING_WINDOW,
    )
}

private fun profileReloadServiceActionOnlyRunsWhenForegroundActive() {
    expectFalse(
        "inactive profile save does not start foreground service",
        HostSessionService.shouldStartServiceForProfileReload(HostSessionState()),
    )
    expectTrue(
        "foreground profile save reloads live service",
        HostSessionService.shouldStartServiceForProfileReload(HostSessionState(foregroundActive = true)),
    )
}

private fun bluetoothGamepadStartRequiresConnectPermission() {
    val gate = PermissionGate.evaluate(
        PermissionGateInput(
            sdkInt = 31,
            grantedPermissions = setOf(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_ADVERTISE),
            bluetoothEnabled = true,
            locationServiceAvailable = true,
            hasGyroscope = true,
            hasRotationVector = true,
            hasGameRotationVector = false,
            hasAccelerometer = true,
            hasGravity = true,
            hasVibrator = true,
            hasNetwork = true,
        ),
    )

    val blockedStart = hidStartBlockedStatusFor(gate, openPairingWindow = false)
    val blockedPairing = hidStartBlockedStatusFor(gate, openPairingWindow = true)

    expectEquals("start blocked detail", "Grant Nearby Devices connect permission.", blockedStart?.unsupportedReason)
    expectEquals("start pairing unchanged", "not opened", blockedStart?.pairingWindow?.detail)
    expectEquals("pairing blocked detail", "Grant Nearby Devices connect permission.", blockedPairing?.unsupportedReason)
    expectEquals("pairing status detail", "Grant Nearby Devices connect permission.", blockedPairing?.pairingWindow?.detail)
}

private fun bluetoothGamepadStartDoesNotStartLanDesktopControl() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })
    val before = HostSessionState(
        desktopLinkState = DesktopLinkState(phase = DesktopLinkPhase.DISCONNECTED),
        packetStreamState = InputStreamLifecycleState.STOPPED,
    )

    val after = controller.startBluetoothGamepad(before)

    expectEquals("hid started", 1, driver.startCount)
    expectEquals("desktop unchanged", before.desktopLinkState, after.desktopLinkState)
    expectEquals("packet unchanged", InputStreamLifecycleState.STOPPED, after.packetStreamState)
}

private fun bluetoothGamepadStartAndPairingWindowAreSeparateActions() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })

    controller.startBluetoothGamepad(HostSessionState())
    expectEquals("start does not open pairing", 0, driver.openPairingCount)

    controller.openPairingWindow(HostSessionState())
    expectEquals("pairing action starts hid mode", 2, driver.startCount)
    expectEquals("pairing action opens one window", 1, driver.openPairingCount)
}

private fun bluetoothGamepadStopSessionAndDestroyCloseHidMode() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })

    controller.startBluetoothGamepad(HostSessionState())
    controller.stopBluetoothGamepad(HostSessionState())
    controller.startBluetoothGamepad(HostSessionState())
    controller.close()

    expectEquals("stop called for explicit stop and close", 2, driver.stopCount)
    expectEquals("close called once", 1, driver.closeCount)
}

private fun liveInputFanoutOnlySendsWhenHidHostConnected() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })
    controller.startBluetoothGamepad(HostSessionState())
    val inputState = GunInputState(pressedControls = setOf("trigger"), stickAxisX = 0.25f, stickAxisY = -0.5f)
    val motion = MotionSample(
        provider = MotionProvider.ROTATION_VECTOR,
        sourceSensorElapsedNanos = 2_000L,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        aimX = 0.5f,
        aimY = -0.25f,
    )

    controller.fanOutLiveInput(HostSessionState(gunInputState = inputState, lastMotionSample = null))
    driver.status = driver.status.copy(hostConnection = BtGunHidHostConnectionState.CONNECTED)
    val mappedState = HostSessionProfileRuntime(
        profileStore = ProfileStore(InMemoryProfilePreferences()),
        mapper = ProfileMapper(),
        elapsedRealtimeNanos = { 2_000L },
    ).loadActiveProfile(HostSessionState(gunInputState = inputState, lastMotionSample = motionEnvelope(motion)))
    controller.fanOutLiveInput(mappedState)

    expectEquals("only connected send", 1, driver.sentMappedInputs.size)
    expectEquals("sent mapped controls", setOf("jp_button_r2"), driver.sentMappedInputs.single().state.pressedVirtualControls)
    expectEquals("sent mapped stick x", inputState.stickAxisX, driver.sentMappedInputs.single().state.stickAxisX)
    expectEquals("sent mapped aim x", motion.aimX, driver.sentMappedInputs.single().state.aimAxisX)
}

private fun hidFanoutDropsDuplicatesAndPrioritizesButtonEdges() {
    val driver = RecordingHostHidGamepadDriver()
    var nowElapsedNanos = 1_000_000_000L
    val controller = HostSessionHidController(
        driverFactory = { driver },
        elapsedRealtimeNanos = { nowElapsedNanos },
        maxMotionReportHz = 60,
    )
    controller.startBluetoothGamepad(HostSessionState())
    driver.status = driver.status.copy(hostConnection = BtGunHidHostConnectionState.CONNECTED)
    val base = defaultMappedState()

    controller.fanOutLiveInput(HostSessionState(mappedControllerState = base.copy(aimAxisX = 0f)))
    controller.fanOutLiveInput(HostSessionState(mappedControllerState = base.copy(aimAxisX = 0f)))
    nowElapsedNanos += 1_000_000L
    controller.fanOutLiveInput(HostSessionState(mappedControllerState = base.copy(aimAxisX = 0.1f)))
    controller.fanOutLiveInput(
        HostSessionState(
            mappedControllerState = base.copy(
                aimAxisX = 0.1f,
                pressedVirtualControls = setOf("jp_button_b3"),
            ),
        ),
    )
    controller.fanOutLiveInput(HostSessionState(mappedControllerState = base.copy(aimAxisX = 0.1f)))
    nowElapsedNanos += 17_000_000L
    controller.fanOutLiveInput(HostSessionState(mappedControllerState = base.copy(aimAxisX = 0.2f)))

    expectEquals("initial press release plus next aim frame", 4, driver.sentMappedInputs.size)
    expectEquals("duplicate skipped", 0f, driver.sentMappedInputs[0].state.aimAxisX)
    expectEquals("button edge bypasses aim throttle", setOf("jp_button_b3"), driver.sentMappedInputs[1].state.pressedVirtualControls)
    expectEquals("release edge bypasses aim throttle", emptySet<String>(), driver.sentMappedInputs[2].state.pressedVirtualControls)
    expectEquals("aim frame after interval", 0.2f, driver.sentMappedInputs[3].state.aimAxisX)
}

private fun fastAimFanoutDoesNotFloodButtonEdges() {
    val driver = RecordingHostHidGamepadDriver()
    var nowElapsedNanos = 0L
    val controller = HostSessionHidController(
        driverFactory = { driver },
        elapsedRealtimeNanos = { nowElapsedNanos },
        maxMotionReportHz = 30,
    )
    controller.startBluetoothGamepad(HostSessionState())
    driver.status = driver.status.copy(hostConnection = BtGunHidHostConnectionState.CONNECTED)
    val base = defaultMappedState()

    controller.fanOutLiveInput(HostSessionState(mappedControllerState = base.copy(aimAxisX = 0f)))
    for (millis in 1L..120L) {
        nowElapsedNanos = millis * 1_000_000L
        val controls = when (millis) {
            50L -> setOf("jp_button_b3")
            else -> emptySet()
        }
        controller.fanOutLiveInput(
            HostSessionState(
                mappedControllerState = base.copy(
                    aimAxisX = (millis / 120f).coerceIn(0f, 1f),
                    aimAxisY = (millis / 240f).coerceIn(0f, 1f),
                    pressedVirtualControls = controls,
                ),
            ),
        )
    }

    expectEquals("fast aim sends bounded report count", 6, driver.sentMappedInputs.size)
    expectEquals("button press still immediate", setOf("jp_button_b3"), driver.sentMappedInputs[2].state.pressedVirtualControls)
    expectEquals("button release still immediate", emptySet<String>(), driver.sentMappedInputs[3].state.pressedVirtualControls)
}

private fun bleConnectionLossClearsLatchedGunInput() {
    expectFalse(
        "connected keeps pressed control",
        shouldClearGunInputOnBleConnectionPhase(
            phase = BleGunConnectionPhase.CONNECTED,
            gunInputState = GunInputState(pressedControls = setOf("trigger")),
        ),
    )
    expectTrue(
        "reconnect clears pressed control",
        shouldClearGunInputOnBleConnectionPhase(
            phase = BleGunConnectionPhase.RECONNECTING,
            gunInputState = GunInputState(pressedControls = setOf("trigger")),
        ),
    )
    expectTrue(
        "error clears non-neutral stick",
        shouldClearGunInputOnBleConnectionPhase(
            phase = BleGunConnectionPhase.ERROR,
            gunInputState = GunInputState(stickAxisX = 1f),
        ),
    )
    expectFalse(
        "neutral disconnected state does no extra work",
        shouldClearGunInputOnBleConnectionPhase(
            phase = BleGunConnectionPhase.STOPPED,
            gunInputState = GunInputState(),
        ),
    )
}

private fun hidOutputCallbackRoutesThroughPhoneHapticExecutorStatus() {
    val haptics = mutableListOf<DesktopHapticCommand>()
    val driver = RecordingHostHidGamepadDriver(
        onOutput = { command ->
            haptics += command
            HapticResult(command.commandId, HapticResultStatus.STARTED, "phone pulse started", 1_000L)
        },
    )
    val controller = HostSessionHidController(driverFactory = { driver })
    controller.startBluetoothGamepad(HostSessionState())

    driver.simulateSetReport(
        reportType = BtGunHidReportTypes.OUTPUT,
        reportId = BtGunHidDescriptor.OUTPUT_REPORT_ID,
        payload = outputPayload(strength = 128, durationMs = 75, ttlMs = 500),
    )
    val state = controller.refreshStatus(HostSessionState())

    expectEquals("haptic routed", 1, haptics.size)
    expectEquals("callback status", BtGunHidOutputCallbackKind.SET_REPORT, state.hidGamepadStatus.lastOutputCallback.kind)
    expectEquals("validation status", BtGunHidOutputValidationState.VALID, state.hidGamepadStatus.lastOutputValidation.state)
    expectEquals("haptic result", HapticResultStatus.STARTED, state.hidGamepadStatus.lastHapticResult?.status)
}

private fun hostProfileRuntimeLoadsActiveProfileAndMapsCurrentInput() {
    val runtime = HostSessionProfileRuntime(
        profileStore = ProfileStore(InMemoryProfilePreferences()),
        mapper = ProfileMapper(),
        elapsedRealtimeNanos = { 10_000_000L },
    )
    val raw = GunInputState(pressedControls = setOf("trigger"), stickAxisX = 0.25f, stickAxisY = -0.5f)

    val state = runtime.loadActiveProfile(HostSessionState(gunInputState = raw))

    expectEquals("default active id", "default_visualizer", state.activeProfileId)
    expectEquals("default active name", "Default Visualizer", state.activeProfileDisplayName)
    expectEquals("default active revision", 1L, state.activeProfileRevision)
    expectEquals("default raw debug", false, state.rawDebugEnabled)
    expectEquals("mapped trigger", setOf("jp_button_r2"), state.mappedControllerState.pressedVirtualControls)
    expectEquals("mapped stick x", 0.25f, state.mappedControllerState.stickAxisX)
    expectEquals("mapped stick y", -0.5f, state.mappedControllerState.stickAxisY)
    expectEquals("validation error clear", null, state.profileValidationError)
}

private fun hostProfileRuntimeReloadsSelectedProfileWithoutRestart() {
    val preferences = InMemoryProfilePreferences()
    val store = ProfileStore(preferences, idFactory = { "user_profile" }, nowEpochMillis = { 100L })
    val runtime = HostSessionProfileRuntime(
        profileStore = store,
        mapper = ProfileMapper(),
        elapsedRealtimeNanos = { 20_000_000L },
    )
    val copy = (store.duplicateProfile("default_visualizer") as SaveProfileResult.Saved)
        .document
        .profiles
        .single { profile -> profile.profileId == "user_profile" }
        .copy(
            displayName = "Reload Test",
            aim = AimMappingSettings(sensitivity = 2f, smoothing = SmoothingMode.OFF),
            buttonMapping = BtGunProfile.defaultButtonMapping() +
                (PhysicalButton.TRIGGER to VirtualButton.B3) +
                (PhysicalButton.BUTTON_X to VirtualButton.R2),
            rawDebugEnabled = true,
        )
    store.saveProfile(copy)
    store.selectProfile("default_visualizer")
    val initial = runtime.loadActiveProfile(
        HostSessionState(gunInputState = GunInputState(pressedControls = setOf("trigger"))),
    )

    store.selectProfile("user_profile")
    val reloaded = runtime.reloadActiveProfile(initial)

    expectEquals("reloaded id", "user_profile", reloaded.activeProfileId)
    expectEquals("reloaded name", "Reload Test", reloaded.activeProfileDisplayName)
    expectEquals("reloaded raw debug", true, reloaded.rawDebugEnabled)
    expectEquals("runtime remapped trigger", setOf("jp_button_b3"), reloaded.mappedControllerState.pressedVirtualControls)
}

private fun hidFanoutUsesMappedStateAfterServiceMapping() {
    val driver = RecordingHostHidGamepadDriver()
    val controller = HostSessionHidController(driverFactory = { driver })
    controller.startBluetoothGamepad(HostSessionState())
    driver.status = driver.status.copy(hostConnection = BtGunHidHostConnectionState.CONNECTED)
    val mapped = defaultMappedState().copy(
        pressedVirtualControls = setOf("jp_button_b3"),
        stickAxisX = 0.5f,
        stickAxisY = -0.25f,
        aimAxisX = 0.125f,
        aimAxisY = -0.75f,
    )

    controller.fanOutLiveInput(HostSessionState(mappedControllerState = mapped))

    expectEquals("one mapped send", 1, driver.sentMappedInputs.size)
    expectEquals("mapped state sent", mapped, driver.sentMappedInputs.single().state)
}

private fun recenterUsesSelectedPhysicalControlWhileVirtualReloadPublishes() {
    val profile = BtGunProfile.defaultVisualizer().copy(
        recenterPhysicalControl = PhysicalButton.BUTTON_A,
        buttonMapping = BtGunProfile.defaultButtonMapping() + (PhysicalButton.BUTTON_A to VirtualButton.L2),
    )
    val rawState = GunInputState(pressedControls = setOf("button_a"))
    val mapper = ProfileMapper()
    val mapped = mapper.map(
        profile = profile,
        gunInputState = rawState,
        motionSample = null,
        nowElapsedNanos = 30_000_000L,
    )

    expectEquals("physical recenter button", true, shouldFeedRecenterHold(profile, "button_a"))
    expectEquals("physical reload not recenter", false, shouldFeedRecenterHold(profile, "reload"))
    expectEquals("button a starts hold", true, mapper.isRecenterPressed(profile, rawState))
    expectEquals("virtual reload still publishes", true, "jp_button_l2" in mapped.pressedVirtualControls)
}

private fun visualizerStatusReflectsRecenterAimZeroAndRawDebug() {
    val profile = BtGunProfile.defaultVisualizer().copy(rawDebugEnabled = true)
    val recenter = statusEnvelope(
        name = ReloadHoldRecenter.RECENTER_EVENT_NAME,
        label = "recenter emitted",
        elapsedNanos = 4_000_000_000L,
    )
    val state = HostSessionState(
        activeProfile = profile,
        lastRecenterStatus = recenter,
        lastMotionSample = motionEnvelope(liveMotionSample()),
        aimBaseline = com.btgun.host.motion.AimBaseline(
            yaw = 1f,
            pitch = 2f,
            roll = 3f,
            elapsedNanos = 4_000_000_000L,
        ),
    )

    val status = hostVisualizerStatusFor(
        state = state,
        androidElapsedNanos = 4_100_000_000L,
        statusSequence = 3L,
    )
    val body = status.toJsonBody()

    expectEquals("raw debug follows profile", true, status.rawDebugEnabled)
    expectEquals("aim-zero ready", VisualizerStatus.AIM_ZERO_READY, status.aimZeroState)
    expectEquals("recentered", VisualizerStatus.RECENTERED, status.recenterState)
    expectEquals("last recenter", 4_000_000_000L, status.lastRecenterElapsedNanos)
    expectEquals("sequence", 3L, status.statusSequence)
    expectFalse("no raw yaw status", body.containsKey("yaw"))
    expectFalse("no raw pitch status", body.containsKey("pitch"))
    expectFalse("no raw roll status", body.containsKey("roll"))
}

private fun visualizerStatusDoesNotReportReadyBeforeLiveMotion() {
    val status = hostVisualizerStatusFor(
        state = HostSessionState(
            aimBaseline = com.btgun.host.motion.AimBaseline(
                yaw = 1f,
                pitch = 2f,
                roll = 3f,
                elapsedNanos = 4_000_000_000L,
            ),
        ),
        androidElapsedNanos = 4_100_000_000L,
        statusSequence = 4L,
    )
    val unavailableMotionStatus = hostVisualizerStatusFor(
        state = HostSessionState(
            lastMotionSample = motionEnvelope(liveMotionSample(provider = MotionProvider.UNAVAILABLE)),
            aimBaseline = com.btgun.host.motion.AimBaseline(
                yaw = 1f,
                pitch = 2f,
                roll = 3f,
                elapsedNanos = 4_000_000_000L,
            ),
        ),
        androidElapsedNanos = 4_200_000_000L,
        statusSequence = 5L,
    )

    expectEquals("baseline alone not ready", VisualizerStatus.AIM_ZERO_UNAVAILABLE, status.aimZeroState)
    expectEquals("unavailable provider not ready", VisualizerStatus.AIM_ZERO_UNAVAILABLE, unavailableMotionStatus.aimZeroState)
}

private fun visualizerStatusReportsHeldEvenAfterPriorRecenter() {
    val recenter = statusEnvelope(
        name = ReloadHoldRecenter.RECENTER_EVENT_NAME,
        label = "recenter emitted",
        elapsedNanos = 4_000_000_000L,
    )
    val status = hostVisualizerStatusFor(
        state = HostSessionState(
            lastRecenterStatus = recenter,
            reloadHoldState = ReloadHoldState(
                isReloadHeld = true,
                pressedElapsedNanos = 6_000_000_000L,
                recenterEmitted = false,
                calibrationEmitted = false,
            ),
            lastMotionSample = motionEnvelope(liveMotionSample()),
            aimBaseline = com.btgun.host.motion.AimBaseline(
                yaw = 1f,
                pitch = 2f,
                roll = 3f,
                elapsedNanos = 4_000_000_000L,
            ),
        ),
        androidElapsedNanos = 6_100_000_000L,
        statusSequence = 6L,
    )

    expectEquals("held state wins over prior recenter", VisualizerStatus.RECENTER_HELD, status.recenterState)
    expectEquals("prior recenter timestamp preserved", 4_000_000_000L, status.lastRecenterElapsedNanos)
}

private fun visualizerStatusNoopsWithoutTrustedDesktopConnection() {
    expectFalse(
        "no desktop no status",
        shouldPublishVisualizerStatus(hasTrustedDesktopConnection = false, meaningfulChange = true),
    )
    expectFalse(
        "no change no status",
        shouldPublishVisualizerStatus(hasTrustedDesktopConnection = true, meaningfulChange = false),
    )
    expectTrue(
        "trusted change publishes",
        shouldPublishVisualizerStatus(hasTrustedDesktopConnection = true, meaningfulChange = true),
    )
}

private fun reloadDownUpEventsStillFanOutAroundRecenterStatus() {
    val recenter = ReloadHoldRecenter()

    val down = recenter.onReload(pressed = true, nowElapsedNanos = 1_000_000_000L)
    val status = recenter.onTick(3_100_000_000L)
    val up = recenter.onReload(pressed = false, nowElapsedNanos = 3_200_000_000L)

    expectEquals("reload down event", "reload", down.single().payload.name)
    expectEquals("reload down pressed", true, down.single().payload.pressed)
    expectEquals("recenter status", ReloadHoldRecenter.RECENTER_EVENT_NAME, status.single().payload.name)
    expectEquals("reload up event", "reload", up.single().payload.name)
    expectEquals("reload up pressed", false, up.single().payload.pressed)
}

private fun statusEnvelope(
    name: String,
    label: String,
    elapsedNanos: Long,
) = com.btgun.host.model.LiveEnvelope(
    stream = com.btgun.host.model.StreamKind.STATUS,
    seq = 1L,
    captureElapsedNanos = elapsedNanos,
    emittedElapsedNanos = elapsedNanos,
    payload = com.btgun.host.model.StatusEvent(
        name = name,
        message = label,
        baselineElapsedNanos = elapsedNanos,
        statusLabel = label,
    ),
)

private fun motionEnvelope(payload: MotionSample) =
    com.btgun.host.model.LiveEnvelope(
        stream = com.btgun.host.model.StreamKind.MOTION,
        seq = 1L,
        captureElapsedNanos = 2_000L,
        emittedElapsedNanos = 2_000L,
        payload = payload,
    )

private fun liveMotionSample(provider: MotionProvider = MotionProvider.ROTATION_VECTOR): MotionSample =
    MotionSample(
        provider = provider,
        sourceSensorElapsedNanos = 2_000L,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        aimX = 0.5f,
        aimY = -0.25f,
    )

private fun outputPayload(strength: Int, durationMs: Int, ttlMs: Int): ByteArray =
    byteArrayOf(
        BtGunHidDescriptor.OUTPUT_REPORT_VERSION.toByte(),
        strength.toByte(),
        (durationMs and 0xff).toByte(),
        ((durationMs ushr 8) and 0xff).toByte(),
        (ttlMs and 0xff).toByte(),
        ((ttlMs ushr 8) and 0xff).toByte(),
        0,
        0,
    )

private class RecordingHostHidGamepadDriver(
    private val onOutput: (DesktopHapticCommand) -> HapticResult? = { null },
) : HostHidGamepadDriver {
    override var status: BtGunHidStatus = BtGunHidStatus()
    var startCount = 0
    var stopCount = 0
    var closeCount = 0
    var openPairingCount = 0
    val sentInputs = mutableListOf<SentInput>()
    val sentMappedInputs = mutableListOf<SentMappedInput>()

    override fun startGamepadMode() {
        startCount += 1
    }

    override fun stopGamepadMode() {
        stopCount += 1
    }

    override fun openPairingWindow(durationSeconds: Int): Boolean {
        openPairingCount += 1
        return true
    }

    override fun sendInput(state: GunInputState, motion: MotionSample?, stale: Boolean): BtGunHidInputSendResult {
        sentInputs += SentInput(state, motion, stale)
        return BtGunHidInputSendResult.SENT
    }

    override fun sendMappedInput(state: MappedControllerState, stale: Boolean): BtGunHidInputSendResult {
        sentMappedInputs += SentMappedInput(state, stale)
        sentInputs += SentInput(
            state = GunInputState(
                pressedControls = state.pressedVirtualControls,
                stickAxisX = state.stickAxisX,
                stickAxisY = state.stickAxisY,
            ),
            motion = MotionSample(
                provider = MotionProvider.ROTATION_VECTOR,
                sourceSensorElapsedNanos = 0L,
                yaw = 0f,
                pitch = 0f,
                roll = 0f,
                aimX = state.aimAxisX,
                aimY = state.aimAxisY,
            ),
            stale = stale,
        )
        return BtGunHidInputSendResult.SENT
    }

    fun simulateSetReport(reportType: Int, reportId: Int, payload: ByteArray) {
        val command = com.btgun.host.hid.BtGunHidOutputReportMapper
            .toHapticCommand(reportId, payload, "hid-output-test")
        if (command is com.btgun.host.hid.BtGunHidOutputReportResult.Valid) {
            val result = onOutput(command.command)
            status = status.copy(
                lastOutputCallback = com.btgun.host.hid.BtGunHidOutputCallbackStatus(
                    kind = BtGunHidOutputCallbackKind.SET_REPORT,
                    reportType = reportType,
                    reportId = reportId,
                    payloadLength = payload.size,
                ),
                lastOutputValidation = com.btgun.host.hid.BtGunHidOutputValidationStatus(
                    state = BtGunHidOutputValidationState.VALID,
                    detail = "valid output report",
                ),
                lastHapticResult = result,
            )
        }
    }

    override fun close() {
        closeCount += 1
    }
}

private data class SentInput(
    val state: GunInputState,
    val motion: MotionSample?,
    val stale: Boolean,
)

private data class SentMappedInput(
    val state: MappedControllerState,
    val stale: Boolean,
)

private fun defaultMappedState(): MappedControllerState =
    ProfileMapper().map(
        profile = BtGunProfile.defaultVisualizer(),
        gunInputState = GunInputState(),
        motionSample = null,
        nowElapsedNanos = 0L,
    )

private class InMemoryProfilePreferences(initialValue: String? = null) : ProfilePreferences {
    private var value: String? = initialValue

    override fun loadProfiles(): String? = value

    override fun saveProfiles(value: String) {
        this.value = value
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError("$label expected true")
    }
}

private fun expectFalse(label: String, actual: Boolean) {
    if (actual) {
        throw AssertionError("$label expected false")
    }
}
