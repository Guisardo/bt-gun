package com.btgun.host

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.btgun.host.haptics.PhoneHapticStatus
import com.btgun.host.haptics.PhoneHaptics
import com.btgun.host.motion.AimBaseline
import com.btgun.host.permissions.PermissionGate
import com.btgun.host.permissions.PermissionGateInput
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.ui.DashboardEventMode
import com.btgun.host.ui.DashboardState
import com.btgun.host.ui.DebugExpansion

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var phoneHaptics: PhoneHaptics
    private lateinit var root: LinearLayout
    private lateinit var primaryAction: Button
    private lateinit var hapticAction: Button
    private lateinit var permissionAction: Button
    private lateinit var debugModeAction: Button
    private lateinit var bleDebugAction: Button
    private lateinit var permissionDebugAction: Button
    private lateinit var gattDebugAction: Button
    private val fields = mutableMapOf<String, TextView>()
    private var lastPhoneHapticStatus: PhoneHapticStatus = PhoneHapticStatus.available()
    private var eventMode: DashboardEventMode = DashboardEventMode.PRODUCT_EVENTS
    private var debugExpansion = DebugExpansion()
    private val refreshRunnable = object : Runnable {
        override fun run() {
            renderDashboard()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phoneHaptics = PhoneHaptics(this)
        lastPhoneHapticStatus = phoneHaptics.currentStatus()
        buildLayout()
        renderDashboard()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun buildLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(247, 248, 246))
        }

        root.addView(TextView(this).apply {
            text = "BT Gun Host"
            textSize = 22f
            setTextColor(Color.rgb(31, 41, 51))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        addField("permission_title")
        addField("permission_body")
        permissionAction = button("Grant permissions") { requestHostPermissions() }
        root.addView(permissionAction)

        root.addView(row().apply {
            primaryAction = button("Start live session") { toggleSession() }
            hapticAction = button("Test phone vibration") {
                lastPhoneHapticStatus = phoneHaptics.test()
                renderDashboard()
            }
            addView(primaryAction)
            addView(hapticAction)
        })

        listOf(
            "gun_connection",
            "foreground_service",
            "current_error",
            "last_gun_event",
            "motion_provider",
            "motion_capabilities",
            "preview_aim",
            "recenter_state",
            "desktop_link",
            "packet_stream",
            "phone_haptic",
        ).forEach(::addField)

        debugModeAction = button("Product events") {
            eventMode = if (eventMode == DashboardEventMode.PRODUCT_EVENTS) {
                DashboardEventMode.DEBUG_PROVENANCE
            } else {
                DashboardEventMode.PRODUCT_EVENTS
            }
            renderDashboard()
        }
        root.addView(debugModeAction)

        bleDebugAction = button("BLE provenance") {
            debugExpansion = debugExpansion.copy(bleProvenance = !debugExpansion.bleProvenance)
            renderDashboard()
        }
        permissionDebugAction = button("Permission state") {
            debugExpansion = debugExpansion.copy(permissionState = !debugExpansion.permissionState)
            renderDashboard()
        }
        gattDebugAction = button("GATT status") {
            debugExpansion = debugExpansion.copy(gattStatus = !debugExpansion.gattStatus)
            renderDashboard()
        }
        root.addView(bleDebugAction)
        addField("ble_debug")
        root.addView(permissionDebugAction)
        addField("permission_debug")
        root.addView(gattDebugAction)
        addField("gatt_debug")

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun renderDashboard() {
        val permissionGate = permissionGateState()
        val serviceState = HostSessionService.latestState
        val dashboard = DashboardState.from(
            permissionGateState = permissionGate,
            hostSessionState = serviceState,
            bleConnectionState = serviceState.lastBleConnectionState,
            phoneHapticStatus = lastPhoneHapticStatus,
            eventMode = eventMode,
            debugExpanded = debugExpansion,
            previewAim = serviceState.lastPreviewAim,
            aimBaseline = serviceState.aimBaseline ?: AimBaseline(0f, 0f, 0f, 0L),
            nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
        )

        setField("permission_title", dashboard.permission.title)
        setField("permission_body", dashboard.permission.body + "\n" + dashboard.permission.details)
        permissionAction.visibility = if (dashboard.permission.visible) View.VISIBLE else View.GONE
        primaryAction.text = dashboard.primaryActionLabel
        hapticAction.text = dashboard.hapticActionLabel
        debugModeAction.text = if (dashboard.eventMode == DashboardEventMode.PRODUCT_EVENTS) {
            "Product events"
        } else {
            "Debug provenance"
        }

        setField("gun_connection", "${dashboard.gunConnection.label}: ${dashboard.gunConnection.value}")
        setField("foreground_service", "${dashboard.foregroundService.label}: ${dashboard.foregroundService.value}")
        setField("current_error", "${dashboard.currentError.label}: ${dashboard.currentError.value}")
        setField("last_gun_event", "${dashboard.lastGunEvent.label}: ${dashboard.lastGunEvent.value}")
        setField("motion_provider", "${dashboard.motionProvider.label}: ${dashboard.motionProvider.value}")
        setField("motion_capabilities", dashboard.motionCapabilities.value)
        setField(
            "preview_aim",
            "${dashboard.previewAim.label}: x=${dashboard.previewAim.x} y=${dashboard.previewAim.y} baseline=${dashboard.previewAim.baselineElapsedNanos}ns ${dashboard.previewAim.statusLabel}",
        )
        setField("recenter_state", "${dashboard.recenterState.label}: ${dashboard.recenterState.value}")
        setField("desktop_link", "${dashboard.placeholders.desktopLink.title}: ${dashboard.placeholders.desktopLink.body}")
        setField("packet_stream", "${dashboard.placeholders.packetStream.title}: ${dashboard.placeholders.packetStream.body}")
        setField("phone_haptic", "${dashboard.phoneHaptic.label}: ${dashboard.phoneHaptic.capability}; ${dashboard.phoneHaptic.lastLocalTest}")

        setDebugField("ble_debug", dashboard.debugPanels.bleProvenance.expanded, dashboard.debugPanels.bleProvenance.body)
        setDebugField("permission_debug", dashboard.debugPanels.permissionState.expanded, dashboard.debugPanels.permissionState.body)
        setDebugField("gatt_debug", dashboard.debugPanels.gattStatus.expanded, dashboard.debugPanels.gattStatus.body)
    }

    private fun toggleSession() {
        val action = if (HostSessionService.latestState.isActive) {
            HostSessionService.ACTION_STOP_SESSION
        } else {
            HostSessionService.ACTION_START_SESSION
        }
        val intent = Intent(this, HostSessionService::class.java).setAction(action)
        if (action == HostSessionService.ACTION_START_SESSION && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        renderDashboard()
    }

    private fun requestHostPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
        requestPermissions(permissions, REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            renderDashboard()
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
                hasVibrator = (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true,
                hasNetwork = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager != null,
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

    private fun addField(key: String) {
        fields[key] = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(31, 41, 51))
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(fields.getValue(key))
    }

    private fun setField(key: String, value: String) {
        fields.getValue(key).text = value
    }

    private fun setDebugField(key: String, expanded: Boolean, value: String) {
        fields.getValue(key).visibility = if (expanded) View.VISIBLE else View.GONE
        fields.getValue(key).text = value
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun row(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REFRESH_INTERVAL_MS = 500L
        private const val REQUEST_PERMISSIONS = 2001
    }
}
