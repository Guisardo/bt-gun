package com.btgun.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.ble.BleGunConnectionState
import com.btgun.host.ble.IpegaBleGunAdapter
import com.btgun.host.model.GunEvent
import com.btgun.host.model.GunInputState
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.StatusEvent
import com.btgun.host.motion.AimBaseline
import com.btgun.host.motion.AimCalibration
import com.btgun.host.motion.AimCalibrationCaptureOutcome
import com.btgun.host.motion.AimCalibrationSession
import com.btgun.host.motion.AimCalibrationState
import com.btgun.host.motion.AimCalibrationStore
import com.btgun.host.motion.DisplayRotationRemap
import com.btgun.host.motion.MotionAimProvider
import com.btgun.host.motion.OrientationAngles
import com.btgun.host.motion.PreviewAim
import com.btgun.host.motion.PreviewAimMapper
import com.btgun.host.motion.RawAimPoint
import com.btgun.host.motion.RawAimTracker
import com.btgun.host.motion.fallbackAim
import com.btgun.host.motion.SelectedMotionProvider
import com.btgun.host.haptics.DesktopHapticCommandExecutor
import com.btgun.host.haptics.PhoneHaptics
import com.btgun.host.hid.AndroidBluetoothHidGamepad
import com.btgun.host.hid.AndroidBtGunHidProfileConnector
import com.btgun.host.hid.BtGunHidHostConnectionState
import com.btgun.host.hid.BtGunHidInputSendResult
import com.btgun.host.hid.BtGunHidStatus
import com.btgun.host.permissions.HostCapabilityProbe
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.recenter.ReloadHoldRecenter
import com.btgun.host.recenter.ReloadHoldState
import com.btgun.host.session.DesktopControlClient
import com.btgun.host.session.DesktopControlConnectResult
import com.btgun.host.session.DesktopControlConnectionRequest
import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.session.DesktopLivenessCoordinator
import com.btgun.host.session.DesktopLivenessUpdate
import com.btgun.host.session.PairingParseResult
import com.btgun.host.session.PairingPayload
import com.btgun.host.session.TrustValidationResult
import com.btgun.host.session.TrustedDesktopMetadata
import com.btgun.host.session.TrustedDesktopStore
import com.btgun.host.transport.AndroidUdpInputSender
import com.btgun.host.transport.InputStreamConfig
import com.btgun.host.transport.InputStreamLifecycleState
import com.btgun.host.util.AndroidLog
import java.security.SecureRandom

internal data class HostDesktopLivenessAction(
    val shouldScheduleUdpDisconnectGrace: Boolean,
    val shouldClearClient: Boolean,
    val shouldCancelLivenessTick: Boolean,
    val shouldCloseClient: Boolean,
    val shouldContinuePolling: Boolean,
)

internal fun hostDesktopLivenessActionFor(update: DesktopLivenessUpdate): HostDesktopLivenessAction =
    HostDesktopLivenessAction(
        shouldScheduleUdpDisconnectGrace = update.shouldClearClient,
        shouldClearClient = update.shouldClearClient,
        shouldCancelLivenessTick = update.shouldClearClient || !update.shouldContinuePolling,
        shouldCloseClient = update.shouldCloseClient,
        shouldContinuePolling = update.shouldContinuePolling,
    )

internal fun hostPacketStreamStateAfterControlDisconnect(
    hasSender: Boolean,
    hasConfig: Boolean,
    controlDisconnectGraceMs: Long?,
): InputStreamLifecycleState =
    when {
        !hasSender || !hasConfig || controlDisconnectGraceMs == null -> InputStreamLifecycleState.STOPPED
        controlDisconnectGraceMs <= 0L -> InputStreamLifecycleState.STALE
        else -> InputStreamLifecycleState.GRACE
    }

internal interface HostHidGamepadDriver : AutoCloseable {
    var status: BtGunHidStatus
    fun startGamepadMode()
    fun stopGamepadMode()
    fun openPairingWindow(durationSeconds: Int): Boolean
    fun sendInput(state: GunInputState, motion: MotionSample?, stale: Boolean): BtGunHidInputSendResult
    override fun close()
}

internal class HostSessionHidController(
    private val driverFactory: () -> HostHidGamepadDriver,
    private val pairingWindowSeconds: Int = DEFAULT_PAIRING_WINDOW_SECONDS,
) : AutoCloseable {
    private var driver: HostHidGamepadDriver? = null

    fun startBluetoothGamepad(state: HostSessionState): HostSessionState {
        val activeDriver = driver ?: driverFactory().also { driver = it }
        activeDriver.startGamepadMode()
        return refreshStatus(state)
    }

    fun openPairingWindow(state: HostSessionState): HostSessionState {
        val activeDriver = driver ?: driverFactory().also { driver = it }
        activeDriver.startGamepadMode()
        activeDriver.openPairingWindow(pairingWindowSeconds)
        return refreshStatus(state)
    }

    fun stopBluetoothGamepad(state: HostSessionState): HostSessionState {
        val activeDriver = driver ?: return state.copy(hidGamepadStatus = BtGunHidStatus())
        activeDriver.stopGamepadMode()
        return refreshStatus(state)
    }

    fun fanOutLiveInput(state: HostSessionState): HostSessionState {
        val activeDriver = driver ?: return state
        if (activeDriver.status.hostConnection != BtGunHidHostConnectionState.CONNECTED) {
            return refreshStatus(state)
        }
        activeDriver.sendInput(
            state = state.gunInputState,
            motion = state.lastMotionSample?.payload,
            stale = false,
        )
        return refreshStatus(state)
    }

    fun refreshStatus(state: HostSessionState): HostSessionState =
        state.copy(hidGamepadStatus = driver?.status ?: state.hidGamepadStatus)

    override fun close() {
        val activeDriver = driver ?: return
        activeDriver.stopGamepadMode()
        activeDriver.close()
        driver = null
    }

    private companion object {
        const val DEFAULT_PAIRING_WINDOW_SECONDS = 120
    }
}

internal class AndroidHostHidGamepadDriver(
    private val gamepad: AndroidBluetoothHidGamepad,
) : HostHidGamepadDriver {
    override var status: BtGunHidStatus
        get() = gamepad.status
        set(value) = Unit

    override fun startGamepadMode() {
        gamepad.startGamepadMode()
    }

    override fun stopGamepadMode() {
        gamepad.stopGamepadMode()
    }

    override fun openPairingWindow(durationSeconds: Int): Boolean =
        gamepad.openPairingWindow(durationSeconds)

    override fun sendInput(state: GunInputState, motion: MotionSample?, stale: Boolean): BtGunHidInputSendResult =
        gamepad.sendInput(state, motion, stale)

    override fun close() {
        gamepad.close()
    }
}

private class UnavailableHostHidGamepadDriver(reason: String) : HostHidGamepadDriver {
    override var status: BtGunHidStatus = BtGunHidStatus(unsupportedReason = reason)

    override fun startGamepadMode() = Unit
    override fun stopGamepadMode() = Unit
    override fun openPairingWindow(durationSeconds: Int): Boolean = false
    override fun sendInput(state: GunInputState, motion: MotionSample?, stale: Boolean): BtGunHidInputSendResult =
        BtGunHidInputSendResult.NO_PROXY

    override fun close() = Unit
}

class HostSessionService : Service() {
    private var adapter: IpegaBleGunAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recenter = ReloadHoldRecenter()
    private var motionAimProvider: MotionAimProvider? = null
    private var selectedMotionProvider: SelectedMotionProvider? = null
    private var activeSensor: Sensor? = null
    private var currentAimBaseline: AimBaseline = AimBaseline(0f, 0f, 0f, 0L)
    private var hasLiveAimBaseline: Boolean = false
    private var currentDisplayRotation: Int = Surface.ROTATION_0
    private val rawAimTracker = RawAimTracker()
    private var currentRawAim: RawAimPoint? = null
    private var currentRawOrigin: RawAimPoint? = null
    private var calibrationBaseRawOrigin: RawAimPoint? = null
    private val aimCalibrationSession = AimCalibrationSession()
    private var activeAimCalibration: AimCalibration? = null
    private val aimCalibrationStore: AimCalibrationStore by lazy { AimCalibrationStore(applicationContext) }
    private val trustedDesktopStore: TrustedDesktopStore by lazy { TrustedDesktopStore(applicationContext) }
    private var desktopControlClient: DesktopControlClient? = null
    private var udpInputSender: AndroidUdpInputSender? = null
    private var udpInputConfig: InputStreamConfig? = null
    private var lastUdpSnapshotSentElapsedNanos: Long? = null
    private val desktopHapticExecutor: DesktopHapticCommandExecutor by lazy {
        DesktopHapticCommandExecutor(
            phone = PhoneHaptics(this),
            elapsedRealtimeNanos = { SystemClock.elapsedRealtimeNanos() },
        )
    }
    private val desktopLivenessCoordinator = DesktopLivenessCoordinator()
    private val nonceRandom = SecureRandom()
    private val hidSessionController: HostSessionHidController by lazy {
        HostSessionHidController(driverFactory = { createBluetoothHidDriver() })
    }

    @Volatile
    private var currentState: HostSessionState = HostSessionState()
        set(value) {
            field = value
            latestState = value
        }

    private val reloadHoldTick = Runnable {
        handleReloadHoldTick()
    }

    private val desktopLivenessTick = Runnable {
        refreshDesktopLiveness()
    }

    private val udpSnapshotTick = object : Runnable {
        override fun run() {
            sendUdpSnapshot()
            scheduleUdpSnapshotTick()
        }
    }

    private val udpDisconnectGraceStop = Runnable {
        markUdpInputStale()
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val provider = motionAimProvider ?: return
            val selection = selectedMotionProvider ?: provider.currentSelection()
            if (!selection.isAvailable) {
                val unavailable = provider.unavailableSample()
                currentState = currentState.copy(lastMotionSample = unavailable)
                return
            }
            val displayRotation = displayRotation()
            val displayRotationChanged = displayRotation != currentDisplayRotation
            if (displayRotationChanged) {
                rawAimTracker.reset()
            }
            val envelope = provider.envelopeForSensorEvent(
                event = event,
                orientation = orientationFrom(event, selection.provider, displayRotation),
                selection = selection,
            )
            val rawAbsolute = rawAimTracker.track(envelope.payload)
            if (!hasLiveAimBaseline || displayRotationChanged) {
                currentAimBaseline = envelope.payload.toAimBaseline(envelope.captureElapsedNanos)
                hasLiveAimBaseline = true
                currentDisplayRotation = displayRotation
                currentRawOrigin = rawAbsolute
            }
            currentRawAim = rawAbsolute
            if (currentRawOrigin == null) {
                currentRawOrigin = rawAbsolute
            }
            val enriched = envelope.withAim(rawAbsolute, currentRawOrigin ?: rawAbsolute)
            val preview = PreviewAimMapper(currentAimBaseline).map(enriched)
            currentState = currentState.copy(
                lastMotionSample = enriched,
                lastPreviewAim = preview,
                aimBaseline = currentAimBaseline,
                aimCalibrationState = aimCalibrationSession.state,
            )
            currentState = hidSessionController.fanOutLiveInput(currentState)
            sendUdpSnapshot(throttled = true)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AndroidLog.i(TAG, "onStartCommand action=${intent?.action ?: "null"} startId=$startId")
        when (intent?.action) {
            ACTION_STOP_SESSION -> stopSession()
            ACTION_START_BLUETOOTH_GAMEPAD -> startBluetoothGamepad()
            ACTION_STOP_BLUETOOTH_GAMEPAD -> stopBluetoothGamepad()
            ACTION_START_HID_PAIRING_WINDOW -> startBluetoothGamepad(openPairingWindow = true)
            ACTION_CONNECT_DESKTOP_QR -> connectDesktopFromQr(intent.getStringExtra(EXTRA_QR_PAYLOAD).orEmpty())
            ACTION_CONNECT_TRUSTED_DESKTOP -> connectTrustedDesktop(intent.getStringExtra(EXTRA_DESKTOP_FINGERPRINT))
            ACTION_CONNECT_MANUAL_DESKTOP -> connectManualDesktop(intent)
            ACTION_STOP_DESKTOP_CONTROL -> stopDesktopControl()
            ACTION_START_SESSION, null -> startSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hidSessionController.close()
        stopSession()
        super.onDestroy()
    }

    private fun startSession() {
        val gate = permissionGateState()
        if (!canStartWithPermissionGate(gate)) {
            currentState = HostSessionState(
                phase = HostSessionPhase.ERROR,
                lastError = blockedPermissionMessage(gate),
            )
            stopSelf()
            return
        }

        currentState = HostSessionState(phase = HostSessionPhase.STARTING)
        if (!startHostForegroundSafely()) {
            stopSelf()
            return
        }
        startMotionCapture()

        val listener = object : IpegaBleGunAdapter.Listener {
            override fun onConnectionState(state: BleGunConnectionState) {
                currentState = currentState.copy(
                    phase = state.phase.toHostSessionPhase(),
                    foregroundActive = currentState.foregroundActive,
                    reconnectAttempt = state.reconnectAttempt,
                    lastError = state.lastError,
                    lastBleConnectionState = state,
                )
            }

            override fun onGunEvent(envelope: LiveEnvelope<GunEvent>) {
                handleGunEvent(envelope)
            }

            override fun onStatus(envelope: LiveEnvelope<StatusEvent>) {
                currentState = currentState.copy(
                    lastStatusEvent = envelope,
                    lastError = envelope.payload.message ?: currentState.lastError,
                )
            }
        }

        adapter = IpegaBleGunAdapter(applicationContext, listener).also { it.startSession() }
        currentState = currentState.copy(phase = HostSessionPhase.SCANNING, foregroundActive = true)
    }

    private fun startHostForegroundSafely(): Boolean =
        try {
            startHostForeground()
            true
        } catch (error: SecurityException) {
            currentState = HostSessionState(
                phase = HostSessionPhase.ERROR,
                lastError = "Foreground service blocked: ${error.javaClass.simpleName}",
            )
            false
        } catch (error: IllegalStateException) {
            currentState = HostSessionState(
                phase = HostSessionPhase.ERROR,
                lastError = "Foreground service blocked: ${error.javaClass.simpleName}",
            )
            false
        }

    private fun stopSession() {
        currentState = currentState.copy(phase = HostSessionPhase.STOPPING)
        hidSessionController.close()
        stopDesktopControl()
        stopUdpInput()
        stopMotionCapture()
        handler.removeCallbacks(reloadHoldTick)
        recenter.onReload(pressed = false, nowElapsedNanos = SystemClock.elapsedRealtimeNanos())
        adapter?.stopSession()
        adapter = null
        currentState = HostSessionState(phase = HostSessionPhase.STOPPED)
        stopForegroundCompat()
        stopSelf()
    }

    private fun startBluetoothGamepad(openPairingWindow: Boolean = false) {
        AndroidLog.i(TAG, "startBluetoothGamepad openPairingWindow=$openPairingWindow foreground=${currentState.foregroundActive}")
        if (!ensureForegroundForHidMode()) {
            AndroidLog.w(TAG, "startBluetoothGamepad blocked: foreground start failed")
            return
        }
        currentState = if (openPairingWindow) {
            hidSessionController.openPairingWindow(currentState)
        } else {
            hidSessionController.startBluetoothGamepad(currentState)
        }
        AndroidLog.i(TAG, "hid status after start=${currentState.hidGamepadStatus}")
    }

    private fun stopBluetoothGamepad() {
        AndroidLog.i(TAG, "stopBluetoothGamepad")
        currentState = hidSessionController.stopBluetoothGamepad(currentState)
        hidSessionController.close()
        currentState = currentState.copy(hidGamepadStatus = BtGunHidStatus())
        if (!currentState.isActive && currentState.desktopLinkState.phase == DesktopLinkPhase.IDLE) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun connectDesktopFromQr(rawPayload: String) {
        if (!ensureForegroundForDesktopControl()) {
            return
        }
        currentState = currentState.copy(
            desktopLinkState = DesktopLinkState(phase = DesktopLinkPhase.CONNECTING),
        )
        when (val parsed = PairingPayload.parseQrUri(rawPayload, nowEpochMillis = System.currentTimeMillis())) {
            is PairingParseResult.Invalid -> {
                currentState = currentState.copy(
                    desktopLinkState = DesktopLinkState(
                        phase = DesktopLinkPhase.DISCONNECTED,
                        lastControlError = parsed.message,
                    ),
                )
            }
            is PairingParseResult.Valid -> {
                val payload = parsed.value
                when (
                    val trust = trustedDesktopStore.validateIdentity(
                        fingerprintSha256 = payload.desktopSpkiSha256,
                        displayName = DesktopControlConnectionRequest.DEFAULT_DESKTOP_DISPLAY_NAME,
                        host = payload.host,
                        port = payload.port,
                    )
                ) {
                    is TrustValidationResult.Mismatch -> {
                        currentState = currentState.copy(
                            desktopLinkState = trustProblemState(trust.stored, payload.desktopSpkiSha256),
                        )
                    }
                    TrustValidationResult.Missing -> {
                        currentState = currentState.copy(
                            desktopLinkState = DesktopLinkState(
                                phase = DesktopLinkPhase.TRUST_PROBLEM,
                                lastControlError = "Desktop identity changed",
                            ),
                        )
                    }
                    is TrustValidationResult.FirstTrust,
                    is TrustValidationResult.Trusted,
                    -> connectDesktopControl(
                        request = DesktopControlConnectionRequest.fromQrPayload(
                            payload = payload,
                            androidNonce = newAndroidNonce(),
                        ),
                        saveOnSuccess = true,
                    )
                }
            }
        }
    }

    private fun connectTrustedDesktop(fingerprint: String?) {
        if (!ensureForegroundForDesktopControl()) {
            return
        }
        val metadata = trustedDesktopStore.loadTrustedDesktops()
            .firstOrNull { desktop -> fingerprint == null || desktop.fingerprintSha256 == fingerprint }
        if (metadata == null) {
            currentState = currentState.copy(
                desktopLinkState = DesktopLinkState(
                    phase = DesktopLinkPhase.DISCONNECTED,
                    lastControlError = "No trusted desktop stored. Scan desktop QR first.",
                ),
            )
            return
        }
        currentState = currentState.copy(
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.DISCONNECTED,
                desktopDisplayName = metadata.displayName,
                fingerprintSuffix = metadata.fingerprintSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
                lastControlError = "Trusted reconnect needs a fresh desktop pairing QR or manual code.",
            ),
        )
    }

    private fun connectManualDesktop(intent: Intent?) {
        if (!ensureForegroundForDesktopControl()) {
            return
        }
        val host = intent?.getStringExtra(EXTRA_MANUAL_HOST).orEmpty()
        val port = intent?.getStringExtra(EXTRA_MANUAL_PORT).orEmpty()
        val code = intent?.getStringExtra(EXTRA_MANUAL_CODE).orEmpty()
        val suffix = intent?.getStringExtra(EXTRA_MANUAL_FINGERPRINT_SUFFIX).orEmpty()
        when (val parsed = PairingPayload.parseManual(host, port, code, suffix)) {
            is PairingParseResult.Invalid -> {
                currentState = currentState.copy(
                    desktopLinkState = DesktopLinkState(
                        phase = DesktopLinkPhase.DISCONNECTED,
                        lastControlError = parsed.message,
                    ),
                )
            }
            is PairingParseResult.Valid -> {
                val matches = trustedDesktopStore.loadTrustedDesktops()
                    .filter { desktop -> desktop.fingerprintSha256.endsWith(parsed.value.desktopSpkiSha256Suffix) }
                val trusted = when (matches.size) {
                    1 -> matches.single()
                    0 -> {
                        currentState = currentState.copy(
                            desktopLinkState = DesktopLinkState(
                                phase = DesktopLinkPhase.TRUST_PROBLEM,
                                lastControlError = "Manual pairing needs a saved trusted desktop fingerprint. Scan desktop QR first.",
                            ),
                        )
                        return
                    }
                    else -> {
                        currentState = currentState.copy(
                            desktopLinkState = DesktopLinkState(
                                phase = DesktopLinkPhase.TRUST_PROBLEM,
                                lastControlError = "Fingerprint suffix matches multiple trusted desktops. Enter more fingerprint characters.",
                            ),
                        )
                        return
                    }
                }
                connectDesktopControl(
                    request = DesktopControlConnectionRequest.fromManualPayload(
                        payload = parsed.value,
                        trustedDesktop = trusted.copy(lastHost = parsed.value.host, lastPort = parsed.value.port),
                        androidNonce = newAndroidNonce(),
                    ),
                    saveOnSuccess = false,
                )
            }
        }
    }

    private fun connectDesktopControl(
        request: DesktopControlConnectionRequest,
        saveOnSuccess: Boolean,
    ) {
        cancelDesktopLivenessTick()
        desktopLivenessCoordinator.stop()
        val previousClient = desktopControlClient
        desktopHapticExecutor
            .onSessionChanged(request.authRequest.expectedSessionId ?: request.config.url)
            ?.let { result -> previousClient?.sendHapticResult(result) }
        previousClient?.close()
        desktopControlClient = null
        stopUdpInput()
        currentState = currentState.copy(
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.PAIRING_PROOF,
                desktopDisplayName = request.displayName,
                fingerprintSuffix = request.config.expectedDesktopSpkiSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
            ),
        )
        val client = DesktopControlClient(request.config)
        when (
            val result = client.connect(
                authRequest = request.authRequest,
                onAuthenticated = {
                    handler.post {
                        if (desktopControlClient !== client) {
                            return@post
                        }
                        if (saveOnSuccess) {
                            trustedDesktopStore.saveTrustedDesktop(request.trustedMetadata(System.currentTimeMillis()))
                        }
                        currentState = currentState.copy(
                            desktopLinkState = desktopLinkStateForRequest(client.currentLinkState(), request),
                        )
                        startDesktopLiveness(client)
                    }
                },
                onConnectionFailure = { reason ->
                    handler.post {
                        if (desktopControlClient !== client) {
                            return@post
                        }
                        cancelDesktopLivenessTick()
                        desktopLivenessCoordinator.stop(client)
                        desktopControlClient = null
                        scheduleUdpDisconnectGraceStop()
                        currentState = currentState.copy(
                            desktopLinkState = desktopLinkStateForRequest(
                                client.currentLinkState().copy(
                                    phase = DesktopLinkPhase.DISCONNECTED,
                                    lastControlError = reason,
                                ),
                                request,
                            ),
                        )
                    }
                },
                onLinkStateChanged = { linkState ->
                    handler.post {
                        if (desktopControlClient !== client) {
                            return@post
                        }
                        currentState = currentState.copy(
                            desktopLinkState = desktopLinkStateForRequest(linkState, request),
                        )
                        if (linkState.phase == DesktopLinkPhase.DISCONNECTED) {
                            cancelDesktopLivenessTick()
                            desktopLivenessCoordinator.stop(client)
                            desktopControlClient = null
                            scheduleUdpDisconnectGraceStop()
                        }
                    }
                },
                onProfileMetadataReceived = { profile ->
                    handler.post {
                        if (desktopControlClient !== client) {
                            return@post
                        }
                        val linkState = currentState.desktopLinkState
                        currentState = currentState.copy(
                            desktopLinkState = linkState.copy(
                                desktopDisplayName = request.displayName,
                                fingerprintSuffix = request.config.expectedDesktopSpkiSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
                                profileDisplayName = profile.displayName,
                                profileRevision = profile.revision,
                            ),
                        )
                    }
                },
                onInputStreamConfigReceived = { streamConfig ->
                    handler.post {
                        if (desktopControlClient !== client) {
                            return@post
                        }
                        startUdpInput(streamConfig)
                    }
                },
                onHapticCommandReceived = { command, receivedElapsedNanos ->
                    desktopHapticExecutor.handle(command, receivedElapsedNanos)
                },
            )
        ) {
            DesktopControlConnectResult.Connecting,
            DesktopControlConnectResult.Connected,
            -> {
                desktopControlClient = client
                currentState = currentState.copy(
                    desktopLinkState = client.currentLinkState().copy(
                        phase = DesktopLinkPhase.PAIRING_PROOF,
                        desktopDisplayName = request.displayName,
                        fingerprintSuffix = request.config.expectedDesktopSpkiSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
                    ),
                )
            }
            is DesktopControlConnectResult.TrustMismatch -> {
                client.close()
                currentState = currentState.copy(
                    desktopLinkState = DesktopLinkState(
                        phase = DesktopLinkPhase.TRUST_PROBLEM,
                        desktopDisplayName = request.displayName,
                        fingerprintSuffix = result.presented.takeLast(FINGERPRINT_SUFFIX_LENGTH),
                        lastControlError = "Desktop identity changed",
                    ),
                )
            }
        }
    }

    private fun desktopLinkStateForRequest(
        linkState: DesktopLinkState,
        request: DesktopControlConnectionRequest,
    ): DesktopLinkState {
        val currentDesktop = currentState.desktopLinkState
        return linkState.copy(
            desktopDisplayName = request.displayName,
            fingerprintSuffix = linkState.fingerprintSuffix
                ?: request.config.expectedDesktopSpkiSha256.takeLast(FINGERPRINT_SUFFIX_LENGTH),
            profileDisplayName = linkState.profileDisplayName ?: currentDesktop.profileDisplayName,
            profileRevision = linkState.profileRevision ?: currentDesktop.profileRevision,
        )
    }

    private fun startDesktopLiveness(client: DesktopControlClient) {
        desktopLivenessCoordinator.start(client)
        scheduleDesktopLivenessTick(client)
    }

    private fun cancelDesktopLivenessTick() {
        handler.removeCallbacks(desktopLivenessTick)
    }

    private fun scheduleDesktopLivenessTick(client: DesktopControlClient) {
        if (desktopControlClient !== client || !desktopLivenessCoordinator.isActiveClient(client)) {
            return
        }
        cancelDesktopLivenessTick()
        handler.postDelayed(desktopLivenessTick, DESKTOP_LIVENESS_POLL_MILLIS)
    }

    private fun refreshDesktopLiveness() {
        val client = desktopControlClient ?: run {
            desktopLivenessCoordinator.stop()
            cancelDesktopLivenessTick()
            return
        }
        val update = desktopLivenessCoordinator.refresh(
            client = client,
            currentState = currentState.desktopLinkState,
            nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
        )
        if (desktopControlClient !== client) {
            return
        }
        currentState = currentState.copy(desktopLinkState = update.linkState)
        val action = hostDesktopLivenessActionFor(update)
        if (action.shouldClearClient) {
            if (action.shouldScheduleUdpDisconnectGrace) {
                scheduleUdpDisconnectGraceStop()
            }
            desktopControlClient = null
            if (action.shouldCancelLivenessTick) {
                cancelDesktopLivenessTick()
            }
            if (action.shouldCloseClient) {
                client.close()
            }
        } else if (action.shouldContinuePolling) {
            scheduleDesktopLivenessTick(client)
        } else {
            cancelDesktopLivenessTick()
        }
    }

    private fun stopDesktopControl() {
        cancelDesktopLivenessTick()
        desktopLivenessCoordinator.stop()
        desktopControlClient?.close()
        desktopControlClient = null
        stopUdpInput()
        currentState = currentState.copy(
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.DISCONNECTED,
                lastControlError = "Desktop control stopped with foreground session.",
            ),
        )
    }

    private fun startUdpInput(config: InputStreamConfig) {
        if (!currentState.foregroundActive || !currentState.isActive) {
            currentState = currentState.copy(lastError = "Input stream config ignored outside active foreground session.")
            return
        }
        stopUdpInput()
        udpInputConfig = config
        udpInputSender = AndroidUdpInputSender(
            elapsedRealtimeNanos = { SystemClock.elapsedRealtimeNanos() },
        ).also { sender ->
            sender.start(config)
        }
        lastUdpSnapshotSentElapsedNanos = null
        currentState = currentState.copy(packetStreamState = InputStreamLifecycleState.ACTIVE)
        scheduleUdpSnapshotTick()
        sendUdpSnapshot()
    }

    private fun stopUdpInput() {
        handler.removeCallbacks(udpSnapshotTick)
        handler.removeCallbacks(udpDisconnectGraceStop)
        udpInputSender?.close()
        udpInputSender = null
        udpInputConfig = null
        lastUdpSnapshotSentElapsedNanos = null
        currentState = currentState.copy(packetStreamState = InputStreamLifecycleState.STOPPED)
    }

    private fun markUdpInputStale() {
        handler.removeCallbacks(udpSnapshotTick)
        handler.removeCallbacks(udpDisconnectGraceStop)
        udpInputSender?.close()
        udpInputSender = null
        udpInputConfig = null
        lastUdpSnapshotSentElapsedNanos = null
        currentState = currentState.copy(packetStreamState = InputStreamLifecycleState.STALE)
    }

    private fun scheduleUdpDisconnectGraceStop() {
        handler.removeCallbacks(udpDisconnectGraceStop)
        val sender = udpInputSender
        val config = udpInputConfig
        when (
            hostPacketStreamStateAfterControlDisconnect(
                hasSender = sender != null,
                hasConfig = config != null,
                controlDisconnectGraceMs = config?.controlDisconnectGraceMs,
            )
        ) {
            InputStreamLifecycleState.STOPPED -> {
                handler.removeCallbacks(udpSnapshotTick)
                sender?.close()
                udpInputSender = null
                udpInputConfig = null
                currentState = currentState.copy(packetStreamState = InputStreamLifecycleState.STOPPED)
            }
            InputStreamLifecycleState.STALE -> markUdpInputStale()
            InputStreamLifecycleState.GRACE -> {
                sender?.onControlDisconnected(SystemClock.elapsedRealtimeNanos())
                currentState = currentState.copy(packetStreamState = InputStreamLifecycleState.GRACE)
                handler.postDelayed(udpDisconnectGraceStop, config!!.controlDisconnectGraceMs)
            }
            InputStreamLifecycleState.ACTIVE -> Unit
        }
    }

    private fun scheduleUdpSnapshotTick() {
        val config = udpInputConfig ?: return
        if (udpInputSender == null || !currentState.foregroundActive || !currentState.isActive) {
            return
        }
        handler.removeCallbacks(udpSnapshotTick)
        handler.postDelayed(udpSnapshotTick, snapshotIntervalMillis(config))
    }

    private fun sendUdpSnapshot(throttled: Boolean = false) {
        val sender = udpInputSender ?: return
        val config = udpInputConfig
        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
        if (throttled && config != null && !canSendThrottledUdpSnapshot(nowElapsedNanos, config)) {
            return
        }
        val result = sender.sendSnapshot(
            state = currentState.gunInputState,
            motion = currentState.lastMotionSample,
        )
        if (result == com.btgun.host.transport.AndroidUdpInputSendResult.SENT) {
            lastUdpSnapshotSentElapsedNanos = nowElapsedNanos
        }
    }

    private fun sendUdpEdge(envelope: LiveEnvelope<GunEvent>, state: GunInputState) {
        val sender = udpInputSender ?: return
        sender.sendEdge(
            event = envelope,
            state = state,
            motion = currentState.lastMotionSample,
        )
    }

    private fun snapshotIntervalMillis(config: InputStreamConfig): Long =
        ((1_000L + config.snapshotHz.toLong() - 1L) / config.snapshotHz.toLong()).coerceAtLeast(1L)

    private fun canSendThrottledUdpSnapshot(nowElapsedNanos: Long, config: InputStreamConfig): Boolean {
        val lastSent = lastUdpSnapshotSentElapsedNanos ?: return true
        val intervalNanos = snapshotIntervalMillis(config) * 1_000_000L
        return nowElapsedNanos - lastSent >= intervalNanos
    }

    private fun ensureForegroundForDesktopControl(): Boolean {
        if (currentState.foregroundActive) {
            return true
        }
        currentState = currentState.copy(phase = HostSessionPhase.STARTING)
        return if (startHostForegroundSafely()) {
            true
        } else {
            stopSelf()
            false
        }
    }

    private fun ensureForegroundForHidMode(): Boolean {
        if (currentState.foregroundActive) {
            return true
        }
        return if (startHostForegroundSafely()) {
            true
        } else {
            stopSelf()
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun createBluetoothHidDriver(): HostHidGamepadDriver {
        val adapter = try {
            (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: RuntimeException) {
            null
        }
        val gamepad = AndroidBluetoothHidGamepad(
            connector = AndroidBtGunHidProfileConnector(
                context = applicationContext,
                adapter = adapter ?: return UnavailableHostHidGamepadDriver("Bluetooth adapter unavailable"),
                executor = { runnable -> handler.post(runnable) },
            ),
            hapticHandler = { command ->
                desktopHapticExecutor.handle(command, SystemClock.elapsedRealtimeNanos())
            },
            onStatusChanged = { status ->
                currentState = currentState.copy(hidGamepadStatus = status)
            },
        )
        return AndroidHostHidGamepadDriver(gamepad)
    }

    private fun trustProblemState(stored: TrustedDesktopMetadata, presentedFingerprint: String): DesktopLinkState =
        DesktopLinkState(
            phase = DesktopLinkPhase.TRUST_PROBLEM,
            desktopDisplayName = stored.displayName,
            fingerprintSuffix = presentedFingerprint.takeLast(FINGERPRINT_SUFFIX_LENGTH),
            lastControlError = "Desktop identity changed",
        )

    private fun newAndroidNonce(): String {
        val bytes = ByteArray(16)
        nonceRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun startHostForeground() {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        currentState = currentState.copy(foregroundActive = true)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_TITLE,
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun permissionGateState(): PermissionGateState =
        HostCapabilityProbe.evaluate(this)

    private fun startMotionCapture() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val provider = MotionAimProvider(
            sensorManager = sensorManager,
            clock = { SystemClock.elapsedRealtimeNanos() },
        )
        val selection = provider.currentSelection()
        motionAimProvider = provider
        selectedMotionProvider = selection
        activeAimCalibration = aimCalibrationStore.load()
        aimCalibrationSession.setActiveCalibration(activeAimCalibration)
        currentAimBaseline = AimBaseline(0f, 0f, 0f, SystemClock.elapsedRealtimeNanos())
        hasLiveAimBaseline = false
        currentDisplayRotation = displayRotation()
        rawAimTracker.reset()
        currentRawAim = null
        currentRawOrigin = null
        calibrationBaseRawOrigin = null
        currentState = currentState.copy(
            aimBaseline = currentAimBaseline,
            aimCalibrationState = aimCalibrationSession.state,
        )

        if (!selection.isAvailable) {
            currentState = currentState.copy(lastMotionSample = provider.unavailableSample())
            return
        }

        activeSensor = sensorForSelection(sensorManager, selection.provider)
        val registered = activeSensor?.let { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        } == true
        if (!registered) {
            currentState = currentState.copy(lastMotionSample = provider.unavailableSample())
        }
    }

    private fun stopMotionCapture() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.unregisterListener(sensorListener)
        activeSensor = null
        motionAimProvider = null
        selectedMotionProvider = null
        hasLiveAimBaseline = false
        currentDisplayRotation = Surface.ROTATION_0
        rawAimTracker.reset()
        currentRawAim = null
        currentRawOrigin = null
        calibrationBaseRawOrigin = null
        aimCalibrationSession.cancelInProgress()
    }

    private fun sensorForSelection(sensorManager: SensorManager, provider: MotionProvider): Sensor? =
        when (provider) {
            MotionProvider.GAME_ROTATION_VECTOR -> sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            MotionProvider.ROTATION_VECTOR -> sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            MotionProvider.GYRO_GRAVITY -> sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            MotionProvider.TILT_FALLBACK -> sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            MotionProvider.UNAVAILABLE -> null
        }

    private fun orientationFrom(
        event: SensorEvent,
        provider: MotionProvider,
        displayRotation: Int,
    ): OrientationAngles =
        when (provider) {
            MotionProvider.GAME_ROTATION_VECTOR,
            MotionProvider.ROTATION_VECTOR,
            -> rotationVectorOrientation(event.values, displayRotation)
            MotionProvider.TILT_FALLBACK -> tiltOrientation(event.values, displayRotation)
            MotionProvider.GYRO_GRAVITY -> gyroGravityOrientation(event.values, displayRotation)
            MotionProvider.UNAVAILABLE -> OrientationAngles(0f, 0f, 0f)
        }

    private fun rotationVectorOrientation(values: FloatArray, displayRotation: Int): OrientationAngles {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(remapRotationMatrix(rotationMatrix, displayRotation), orientation)
        return OrientationAngles(
            yaw = Math.toDegrees(orientation[0].toDouble()).toFloat(),
            pitch = Math.toDegrees(orientation[1].toDouble()).toFloat(),
            roll = Math.toDegrees(orientation[2].toDouble()).toFloat(),
        )
    }

    private fun remapRotationMatrix(rotationMatrix: FloatArray, displayRotation: Int): FloatArray {
        val axes = DisplayRotationRemap.axesFor(displayRotation) ?: return rotationMatrix
        val adjusted = FloatArray(9)
        return if (SensorManager.remapCoordinateSystem(rotationMatrix, axes.x, axes.y, adjusted)) {
            adjusted
        } else {
            rotationMatrix
        }
    }

    private fun tiltOrientation(values: FloatArray, displayRotation: Int): OrientationAngles {
        val (x, y) = DisplayRotationRemap.remapTiltXY(
            rotation = displayRotation,
            x = values.getOrElse(0) { 0f },
            y = values.getOrElse(1) { 0f },
        )
        val z = values.getOrElse(2) { 9.8f }
        return OrientationAngles(
            yaw = 0f,
            pitch = Math.toDegrees(kotlin.math.atan2((-x).toDouble(), z.toDouble())).toFloat(),
            roll = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat(),
        )
    }

    private fun gyroGravityOrientation(values: FloatArray, displayRotation: Int): OrientationAngles {
        val (x, y) = DisplayRotationRemap.remapTiltXY(
            rotation = displayRotation,
            x = values.getOrElse(0) { 0f },
            y = values.getOrElse(1) { 0f },
        )
        return OrientationAngles(
            yaw = values.getOrElse(2) { 0f },
            pitch = x,
            roll = y,
        )
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
            ?: Surface.ROTATION_0

    private fun LiveEnvelope<MotionSample>.withAim(
        rawAbsolute: RawAimPoint,
        rawOrigin: RawAimPoint,
    ): LiveEnvelope<MotionSample> {
        val rawRelative = rawAbsolute - rawOrigin
        val calibration = activeAimCalibration
        val mapped = calibration?.map(rawRelative) ?: fallbackAim(rawRelative)
        val latencyMillis = ((SystemClock.elapsedRealtimeNanos() - captureElapsedNanos).coerceAtLeast(0L) / 1_000_000L)
        return copy(
            payload = payload.copy(
                rawAimX = rawRelative.xDegrees,
                rawAimY = rawRelative.yDegrees,
                aimX = mapped.x,
                aimY = mapped.y,
                aimCalibrated = calibration != null,
                aimCalibrationProvider = calibration?.providerName,
                aimLatencyMillis = latencyMillis,
            ),
        )
    }

    private fun handleReloadHoldTick() {
        recenter.onTick(SystemClock.elapsedRealtimeNanos()).forEach { envelope ->
            handleReloadHoldStatus(envelope)
        }
        scheduleReloadHoldTick()
    }

    private fun handleReloadHoldStatus(envelope: LiveEnvelope<StatusEvent>) {
        var nextState = currentState.copy(
            lastStatusEvent = envelope,
            reloadHoldState = recenter.state,
        )
        when (envelope.payload.name) {
            ReloadHoldRecenter.RECENTER_EVENT_NAME -> {
                val lastMotion = currentState.lastMotionSample?.payload
                if (lastMotion != null) {
                    currentAimBaseline = AimBaseline(
                        yaw = lastMotion.yaw,
                        pitch = lastMotion.pitch,
                        roll = lastMotion.roll,
                        elapsedNanos = envelope.payload.baselineElapsedNanos ?: envelope.captureElapsedNanos,
                    )
                }
                currentRawAim?.let { raw -> currentRawOrigin = raw }
                nextState = nextState.copy(
                    lastRecenterStatus = envelope,
                    aimBaseline = currentAimBaseline,
                )
            }
            ReloadHoldRecenter.AIM_CALIBRATION_EVENT_NAME -> {
                startAimCalibration()
                nextState = nextState.copy(aimCalibrationState = aimCalibrationSession.state)
            }
        }
        currentState = nextState.copy(aimCalibrationState = aimCalibrationSession.state)
    }

    private fun scheduleReloadHoldTick() {
        handler.removeCallbacks(reloadHoldTick)
        val pressedAt = recenter.state.pressedElapsedNanos
        if (!recenter.state.isReloadHeld || pressedAt == null || recenter.state.calibrationEmitted) {
            return
        }
        val targetNanos = if (!recenter.state.recenterEmitted) {
            pressedAt + ReloadHoldRecenter.RELOAD_HOLD_NANOS
        } else {
            pressedAt + ReloadHoldRecenter.AIM_CALIBRATION_HOLD_NANOS
        }
        val remainingNanos = (targetNanos - SystemClock.elapsedRealtimeNanos()).coerceAtLeast(0L)
        val delayMillis = (remainingNanos + 999_999L) / 1_000_000L
        handler.postDelayed(reloadHoldTick, delayMillis)
    }

    private fun startAimCalibration() {
        calibrationBaseRawOrigin = currentRawOrigin ?: currentRawAim
        aimCalibrationSession.start()
    }

    private fun captureAimCalibrationMark(captureElapsedNanos: Long) {
        val rawAim = currentRawAim
        val baseOrigin = calibrationBaseRawOrigin ?: currentRawOrigin ?: rawAim
        if (rawAim == null || baseOrigin == null) {
            currentState = currentState.copy(
                lastError = "Aim calibration needs live motion before capture.",
                aimCalibrationState = aimCalibrationSession.state,
            )
            return
        }
        calibrationBaseRawOrigin = baseOrigin
        val providerName = selectedMotionProvider?.providerName ?: currentState.lastMotionSample?.payload?.providerName ?: "unknown"
        val outcome = aimCalibrationSession.capture(
            providerName = providerName,
            rawPoint = rawAim - baseOrigin,
            elapsedRealtimeNanos = captureElapsedNanos,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        when (outcome) {
            is AimCalibrationCaptureOutcome.Completed -> {
                activeAimCalibration = outcome.calibration
                aimCalibrationStore.save(outcome.calibration)
                currentRawOrigin = baseOrigin + outcome.rawCenter
                calibrationBaseRawOrigin = null
                currentState = currentState.copy(
                    aimCalibrationState = aimCalibrationSession.state,
                    lastError = null,
                )
            }
            is AimCalibrationCaptureOutcome.Failed -> {
                currentState = currentState.copy(
                    aimCalibrationState = aimCalibrationSession.state,
                    lastError = outcome.reason,
                )
            }
            is AimCalibrationCaptureOutcome.Captured,
            AimCalibrationCaptureOutcome.Ignored,
            -> {
                currentState = currentState.copy(aimCalibrationState = aimCalibrationSession.state)
            }
        }
    }

    private fun handleGunEvent(envelope: LiveEnvelope<GunEvent>) {
        if (envelope.payload.name == "trigger" && envelope.payload.pressed != null && aimCalibrationSession.state.isCaptureActive) {
            if (envelope.payload.pressed) {
                captureAimCalibrationMark(envelope.captureElapsedNanos)
            }
            val gunInputState = if (envelope.payload.pressed == false) {
                currentState.gunInputState.apply(envelope.payload)
            } else {
                currentState.gunInputState
            }
            currentState = currentState.copy(
                gunInputState = gunInputState,
                aimCalibrationState = aimCalibrationSession.state,
            )
            currentState = hidSessionController.fanOutLiveInput(currentState)
            sendUdpEdge(envelope, gunInputState)
            return
        }

        val gunInputState = currentState.gunInputState.apply(envelope.payload)
        if (envelope.payload.name == "reload" && envelope.payload.pressed != null) {
            val wasHeld = recenter.state.isReloadHeld
            recenter.onReload(envelope.payload.pressed, envelope.captureElapsedNanos)
            if (envelope.payload.pressed) {
                if (!wasHeld) {
                    scheduleReloadHoldTick()
                }
            } else {
                handler.removeCallbacks(reloadHoldTick)
            }
        }
        currentState = currentState.copy(
            lastGunEvent = envelope,
            gunInputState = gunInputState,
            reloadHoldState = recenter.state,
            aimCalibrationState = aimCalibrationSession.state,
        )
        currentState = hidSessionController.fanOutLiveInput(currentState)
        sendUdpEdge(envelope, gunInputState)
    }

    private fun MotionSample.toAimBaseline(elapsedNanos: Long): AimBaseline =
        AimBaseline(
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            elapsedNanos = elapsedNanos,
        )

    companion object {
        const val ACTION_START_SESSION: String = "com.btgun.host.action.START_SESSION"
        const val ACTION_STOP_SESSION: String = "com.btgun.host.action.STOP_SESSION"
        const val ACTION_START_BLUETOOTH_GAMEPAD: String = "com.btgun.host.action.START_BLUETOOTH_GAMEPAD"
        const val ACTION_STOP_BLUETOOTH_GAMEPAD: String = "com.btgun.host.action.STOP_BLUETOOTH_GAMEPAD"
        const val ACTION_START_HID_PAIRING_WINDOW: String = "com.btgun.host.action.START_HID_PAIRING_WINDOW"
        const val ACTION_CONNECT_DESKTOP_QR: String = "com.btgun.host.action.CONNECT_DESKTOP_QR"
        const val ACTION_CONNECT_TRUSTED_DESKTOP: String = "com.btgun.host.action.CONNECT_TRUSTED_DESKTOP"
        const val ACTION_CONNECT_MANUAL_DESKTOP: String = "com.btgun.host.action.CONNECT_MANUAL_DESKTOP"
        const val ACTION_STOP_DESKTOP_CONTROL: String = "com.btgun.host.action.STOP_DESKTOP_CONTROL"
        const val EXTRA_QR_PAYLOAD: String = "com.btgun.host.extra.QR_PAYLOAD"
        const val EXTRA_DESKTOP_FINGERPRINT: String = "com.btgun.host.extra.DESKTOP_FINGERPRINT"
        const val EXTRA_MANUAL_HOST: String = "com.btgun.host.extra.MANUAL_HOST"
        const val EXTRA_MANUAL_PORT: String = "com.btgun.host.extra.MANUAL_PORT"
        const val EXTRA_MANUAL_CODE: String = "com.btgun.host.extra.MANUAL_CODE"
        const val EXTRA_MANUAL_FINGERPRINT_SUFFIX: String = "com.btgun.host.extra.MANUAL_FINGERPRINT_SUFFIX"
        const val NOTIFICATION_CHANNEL_ID: String = "bt_gun_host_session"
        const val NOTIFICATION_TITLE: String = "BT Gun Host"
        const val NOTIFICATION_TEXT: String = "BT Gun Host running - live input active"
        private const val NOTIFICATION_ID: Int = 1001
        private const val FINGERPRINT_SUFFIX_LENGTH: Int = 8
        private const val DESKTOP_LIVENESS_POLL_MILLIS: Long = 500L
        private const val TAG = "BtGunHostSession"

        @Volatile
        var latestState: HostSessionState = HostSessionState()
            private set

        fun canStartWithPermissionGate(state: PermissionGateState): Boolean =
            state.canStartSession

        private fun blockedPermissionMessage(state: PermissionGateState): String =
            listOf(state.bluetoothScan, state.bluetoothConnect, state.motionSensors)
                .firstOrNull { status -> status.state != com.btgun.host.permissions.CapabilityState.AVAILABLE }
                ?.detail
                ?: "Session permission gate blocked."
    }
}

data class HostSessionState(
    val phase: HostSessionPhase = HostSessionPhase.IDLE,
    val foregroundActive: Boolean = false,
    val reconnectAttempt: Int = 0,
    val lastError: String? = null,
    val lastBleConnectionState: BleGunConnectionState = BleGunConnectionState(),
    val lastGunEvent: LiveEnvelope<GunEvent>? = null,
    val gunInputState: GunInputState = GunInputState(),
    val lastMotionSample: LiveEnvelope<MotionSample>? = null,
    val lastStatusEvent: LiveEnvelope<StatusEvent>? = null,
    val reloadHoldState: ReloadHoldState = ReloadHoldState(),
    val lastRecenterStatus: LiveEnvelope<StatusEvent>? = null,
    val aimBaseline: AimBaseline? = null,
    val lastPreviewAim: PreviewAim? = null,
    val aimCalibrationState: AimCalibrationState = AimCalibrationState(),
    val desktopLinkState: DesktopLinkState = DesktopLinkState(),
    val packetStreamState: InputStreamLifecycleState = InputStreamLifecycleState.STOPPED,
    val hidGamepadStatus: BtGunHidStatus = BtGunHidStatus(),
) {
    val wireState: String = phase.wireName
    val isActive: Boolean =
        phase in setOf(
            HostSessionPhase.STARTING,
            HostSessionPhase.SCANNING,
            HostSessionPhase.CONNECTING,
            HostSessionPhase.CONNECTED,
            HostSessionPhase.RECONNECTING,
        )
}

enum class HostSessionPhase(val wireName: String) {
    IDLE("idle"),
    STARTING("starting"),
    SCANNING("scanning"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    RECONNECTING("reconnecting"),
    STOPPING("stopping"),
    STOPPED("stopped"),
    ERROR("error"),
}

data class ReconnectPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 250L,
    val maxDelayMillis: Long = 2_000L,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be non-negative" }
        require(initialDelayMillis > 0L) { "initialDelayMillis must be positive" }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must be >= initialDelayMillis" }
    }

    fun onDisconnect(activeSession: Boolean, completedAttempts: Int, error: String): ReconnectDecision {
        if (!activeSession) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = completedAttempts,
                delayMillis = null,
                state = HostSessionState(phase = HostSessionPhase.STOPPED, lastError = error),
            )
        }

        if (completedAttempts >= maxAttempts) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = completedAttempts,
                delayMillis = null,
                state = HostSessionState(
                    phase = HostSessionPhase.ERROR,
                    reconnectAttempt = completedAttempts,
                    lastError = error,
                ),
            )
        }

        val nextAttempt = completedAttempts + 1
        return ReconnectDecision(
            shouldReconnect = true,
            nextAttempt = nextAttempt,
            delayMillis = delayMillisForAttempt(nextAttempt),
            state = HostSessionState(
                phase = HostSessionPhase.RECONNECTING,
                foregroundActive = true,
                reconnectAttempt = nextAttempt,
                lastError = error,
            ),
        )
    }

    fun delayMillisForAttempt(attempt: Int): Long {
        require(attempt > 0) { "attempt must start at 1" }
        val uncapped = initialDelayMillis * (1L shl (attempt - 1).coerceAtMost(20))
        return uncapped.coerceAtMost(maxDelayMillis)
    }
}

data class ReconnectDecision(
    val shouldReconnect: Boolean,
    val nextAttempt: Int,
    val delayMillis: Long?,
    val state: HostSessionState,
)

private fun BleGunConnectionPhase.toHostSessionPhase(): HostSessionPhase =
    when (this) {
        BleGunConnectionPhase.IDLE -> HostSessionPhase.IDLE
        BleGunConnectionPhase.SCANNING -> HostSessionPhase.SCANNING
        BleGunConnectionPhase.CONNECTING -> HostSessionPhase.CONNECTING
        BleGunConnectionPhase.CONNECTED -> HostSessionPhase.CONNECTED
        BleGunConnectionPhase.RECONNECTING -> HostSessionPhase.RECONNECTING
        BleGunConnectionPhase.STOPPED -> HostSessionPhase.STOPPED
        BleGunConnectionPhase.ERROR -> HostSessionPhase.ERROR
    }
