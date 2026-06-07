package com.btgun.host

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.ble.BleGunConnectionState
import com.btgun.host.ble.IpegaBleGunAdapter
import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.model.StatusEvent
import com.btgun.host.motion.AimBaseline
import com.btgun.host.motion.MotionAimProvider
import com.btgun.host.motion.OrientationAngles
import com.btgun.host.motion.PreviewAim
import com.btgun.host.motion.PreviewAimMapper
import com.btgun.host.motion.SelectedMotionProvider
import com.btgun.host.permissions.PermissionGate
import com.btgun.host.permissions.PermissionGateInput
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.recenter.ReloadHoldRecenter
import com.btgun.host.recenter.ReloadHoldState

class HostSessionService : Service() {
    private var adapter: IpegaBleGunAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recenter = ReloadHoldRecenter()
    private var motionAimProvider: MotionAimProvider? = null
    private var selectedMotionProvider: SelectedMotionProvider? = null
    private var activeSensor: Sensor? = null
    private var currentAimBaseline: AimBaseline = AimBaseline(0f, 0f, 0f, 0L)
    private var hasLiveAimBaseline: Boolean = false

    @Volatile
    private var currentState: HostSessionState = HostSessionState()
        set(value) {
            field = value
            latestState = value
        }

    private val recenterTick = Runnable {
        val emitted = recenter.onTick(SystemClock.elapsedRealtimeNanos()).firstOrNull()
        if (emitted != null) {
            val lastMotion = currentState.lastMotionSample?.payload
            if (lastMotion != null) {
                currentAimBaseline = AimBaseline(
                    yaw = lastMotion.yaw,
                    pitch = lastMotion.pitch,
                    roll = lastMotion.roll,
                    elapsedNanos = emitted.payload.baselineElapsedNanos ?: emitted.captureElapsedNanos,
                )
            }
            currentState = currentState.copy(
                reloadHoldState = recenter.state,
                lastRecenterStatus = emitted,
                aimBaseline = currentAimBaseline,
            )
        }
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
            val envelope = provider.envelopeForSensorEvent(
                event = event,
                orientation = orientationFrom(event, selection.provider),
                selection = selection,
            )
            if (!hasLiveAimBaseline) {
                currentAimBaseline = envelope.payload.toAimBaseline(envelope.captureElapsedNanos)
                hasLiveAimBaseline = true
            }
            val preview = PreviewAimMapper(currentAimBaseline).map(envelope)
            currentState = currentState.copy(
                lastMotionSample = envelope,
                lastPreviewAim = preview,
                aimBaseline = currentAimBaseline,
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SESSION -> stopSession()
            ACTION_START_SESSION, null -> startSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
        startHostForeground()
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

    private fun stopSession() {
        currentState = currentState.copy(phase = HostSessionPhase.STOPPING)
        stopMotionCapture()
        handler.removeCallbacks(recenterTick)
        adapter?.stopSession()
        adapter = null
        currentState = HostSessionState(phase = HostSessionPhase.STOPPED)
        stopForegroundCompat()
        stopSelf()
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
        PermissionGate.evaluate(
            PermissionGateInput(
                sdkInt = Build.VERSION.SDK_INT,
                grantedPermissions = grantedPermissions(),
                bluetoothEnabled = bluetoothAdapter()?.isEnabled == true,
                locationServiceAvailable = locationServiceAvailable(),
                hasGyroscope = hasSensor(Sensor.TYPE_GYROSCOPE),
                hasRotationVector = hasSensor(Sensor.TYPE_ROTATION_VECTOR),
                hasGameRotationVector = hasSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
                hasAccelerometer = hasSensor(Sensor.TYPE_ACCELEROMETER),
                hasGravity = hasSensor(Sensor.TYPE_GRAVITY),
                hasVibrator = hasVibrator(),
                hasNetwork = hasNetwork(),
            ),
        )

    private fun grantedPermissions(): Set<String> =
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).filter { permission ->
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }.toSet()

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()

    private fun locationServiceAvailable(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= 28) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun hasSensor(sensorType: Int): Boolean =
        (getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(sensorType) != null

    private fun hasVibrator(): Boolean =
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true

    private fun hasNetwork(): Boolean =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager != null

    private fun startMotionCapture() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val provider = MotionAimProvider(
            sensorManager = sensorManager,
            clock = { SystemClock.elapsedRealtimeNanos() },
        )
        val selection = provider.currentSelection()
        motionAimProvider = provider
        selectedMotionProvider = selection
        currentAimBaseline = AimBaseline(0f, 0f, 0f, SystemClock.elapsedRealtimeNanos())
        hasLiveAimBaseline = false
        currentState = currentState.copy(aimBaseline = currentAimBaseline)

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
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager.unregisterListener(sensorListener)
        activeSensor = null
        motionAimProvider = null
        selectedMotionProvider = null
        hasLiveAimBaseline = false
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

    private fun orientationFrom(event: SensorEvent, provider: MotionProvider): OrientationAngles =
        when (provider) {
            MotionProvider.GAME_ROTATION_VECTOR,
            MotionProvider.ROTATION_VECTOR,
            -> rotationVectorOrientation(event.values)
            MotionProvider.TILT_FALLBACK -> tiltOrientation(event.values)
            MotionProvider.GYRO_GRAVITY -> OrientationAngles(
                yaw = event.values.getOrElse(2) { 0f },
                pitch = event.values.getOrElse(0) { 0f },
                roll = event.values.getOrElse(1) { 0f },
            )
            MotionProvider.UNAVAILABLE -> OrientationAngles(0f, 0f, 0f)
        }

    private fun rotationVectorOrientation(values: FloatArray): OrientationAngles {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        return OrientationAngles(
            yaw = Math.toDegrees(orientation[0].toDouble()).toFloat(),
            pitch = Math.toDegrees(orientation[1].toDouble()).toFloat(),
            roll = Math.toDegrees(orientation[2].toDouble()).toFloat(),
        )
    }

    private fun tiltOrientation(values: FloatArray): OrientationAngles {
        val x = values.getOrElse(0) { 0f }
        val y = values.getOrElse(1) { 0f }
        val z = values.getOrElse(2) { 9.8f }
        return OrientationAngles(
            yaw = 0f,
            pitch = Math.toDegrees(kotlin.math.atan2((-x).toDouble(), z.toDouble())).toFloat(),
            roll = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat(),
        )
    }

    private fun handleGunEvent(envelope: LiveEnvelope<GunEvent>) {
        if (envelope.payload.name == "reload" && envelope.payload.pressed != null) {
            recenter.onReload(envelope.payload.pressed, envelope.captureElapsedNanos)
            if (envelope.payload.pressed) {
                handler.removeCallbacks(recenterTick)
                handler.postDelayed(recenterTick, ReloadHoldRecenter.RELOAD_HOLD_NANOS / 1_000_000L)
            } else {
                handler.removeCallbacks(recenterTick)
            }
        }
        currentState = currentState.copy(
            lastGunEvent = envelope,
            reloadHoldState = recenter.state,
        )
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
        const val NOTIFICATION_CHANNEL_ID: String = "bt_gun_host_session"
        const val NOTIFICATION_TITLE: String = "BT Gun Host"
        const val NOTIFICATION_TEXT: String = "BT Gun Host running - live input active"
        private const val NOTIFICATION_ID: Int = 1001

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
    val lastMotionSample: LiveEnvelope<MotionSample>? = null,
    val lastStatusEvent: LiveEnvelope<StatusEvent>? = null,
    val reloadHoldState: ReloadHoldState = ReloadHoldState(),
    val lastRecenterStatus: LiveEnvelope<StatusEvent>? = null,
    val aimBaseline: AimBaseline? = null,
    val lastPreviewAim: PreviewAim? = null,
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
