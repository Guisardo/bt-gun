package com.btgun.host

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.btgun.host.haptics.PhoneHapticStatus
import com.btgun.host.haptics.PhoneHaptics
import com.btgun.host.motion.AimBaseline
import com.btgun.host.permissions.CapabilityState
import com.btgun.host.permissions.HostCapabilityProbe
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.ui.AimGraphView
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
    private lateinit var scanDesktopQrAction: Button
    private lateinit var manualDesktopEntryAction: Button
    private lateinit var debugModeAction: Button
    private lateinit var bleDebugAction: Button
    private lateinit var permissionDebugAction: Button
    private lateinit var gattDebugAction: Button
    private lateinit var aimGraph: AimGraphView
    private val fields = mutableMapOf<String, TextView>()
    private var lastPhoneHapticStatus: PhoneHapticStatus = PhoneHapticStatus.available()
    private var localStartError: String? = null
    private var desktopLinkState: DesktopLinkState = DesktopLinkState()
    private var manualEntryVisible: Boolean = false
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
            hapticAction = button("Test local haptic") {
                lastPhoneHapticStatus = phoneHaptics.test()
                renderDashboard()
            }
            addView(primaryAction)
            addView(hapticAction)
        })

        aimGraph = AimGraphView(this)
        root.addView(
            aimGraph,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220),
            ),
        )

        listOf(
            "gun_connection",
            "foreground_service",
            "current_error",
            "last_gun_event",
            "active_controls",
            "motion_provider",
            "motion_capabilities",
            "preview_aim",
            "aim_calibration",
            "recenter_state",
            "desktop_link",
        ).forEach(::addField)

        root.addView(row().apply {
            scanDesktopQrAction = button("Scan desktop QR") { showQrScanState() }
            manualDesktopEntryAction = button("Enter manually") { showManualEntryState() }
            addView(scanDesktopQrAction)
            addView(manualDesktopEntryAction)
        })
        addField("manual_pairing_entry")

        listOf(
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
        val latestServiceState = HostSessionService.latestState
        val serviceState = if (localStartError != null && !latestServiceState.isActive) {
            latestServiceState.copy(phase = HostSessionPhase.ERROR, lastError = localStartError)
        } else {
            latestServiceState
        }
        val dashboard = DashboardState.from(
            permissionGateState = permissionGate,
            hostSessionState = serviceState,
            bleConnectionState = serviceState.lastBleConnectionState,
            phoneHapticStatus = lastPhoneHapticStatus,
            desktopLinkState = desktopLinkState,
            eventMode = eventMode,
            debugExpanded = debugExpansion,
            previewAim = serviceState.lastPreviewAim,
            aimBaseline = serviceState.aimBaseline ?: AimBaseline(0f, 0f, 0f, 0L),
            nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
        )
        aimGraph.render(dashboard.aimGraph)

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
        setField("active_controls", "${dashboard.activeGunControls.label}: ${dashboard.activeGunControls.value}")
        setField("motion_provider", "${dashboard.motionProvider.label}: ${dashboard.motionProvider.value}")
        setField("motion_capabilities", dashboard.motionCapabilities.value)
        setField(
            "preview_aim",
            "${dashboard.previewAim.label}: x=${dashboard.previewAim.x} y=${dashboard.previewAim.y} rawX=${dashboard.previewAim.rawX} rawY=${dashboard.previewAim.rawY} baseline=${dashboard.previewAim.baselineElapsedNanos}ns ${dashboard.previewAim.statusLabel}",
        )
        setField("aim_calibration", "${dashboard.aimCalibration.label}: ${dashboard.aimCalibration.value}")
        setField("recenter_state", "${dashboard.recenterState.label}: ${dashboard.recenterState.value}")
        setField("desktop_link", "${dashboard.placeholders.desktopLink.title}: ${dashboard.placeholders.desktopLink.body}")
        scanDesktopQrAction.text = "Scan desktop QR"
        manualDesktopEntryAction.text = dashboard.placeholders.desktopLink.body
            .substringAfter("manual_action=", "Enter manually")
            .substringBefore(" | ")
            .ifBlank { "Enter manually" }
        setManualEntryField()
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
        if (action == HostSessionService.ACTION_START_SESSION) {
            val gate = permissionGateState()
            if (!gate.canStartSession) {
                localStartError = blockedStartMessage(gate)
                renderDashboard()
                return
            }
        }
        localStartError = null
        val intent = Intent(this, HostSessionService::class.java).setAction(action)
        try {
            if (action == HostSessionService.ACTION_START_SESSION && Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (error: SecurityException) {
            localStartError = "Session start blocked: ${error.javaClass.simpleName}"
        } catch (error: IllegalStateException) {
            localStartError = "Session start blocked: ${error.javaClass.simpleName}"
        }
        renderDashboard()
    }

    private fun requestHostPermissions() {
        requestPermissions(HostCapabilityProbe.runtimePermissionsForHost(), REQUEST_PERMISSIONS)
    }

    private fun showQrScanState() {
        manualEntryVisible = false
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.SCANNING_QR,
            diagnosticText = "Scanning desktop QR. Keep the desktop pairing QR visible.",
        )
        renderDashboard()
    }

    private fun showManualEntryState() {
        manualEntryVisible = true
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.CONNECTING,
            diagnosticText = "Manual entry ready. Enter host/IP, port, and 6-digit code from the desktop.",
        )
        renderDashboard()
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
        HostCapabilityProbe.evaluate(this)

    private fun blockedStartMessage(state: PermissionGateState): String =
        listOf(state.bluetoothScan, state.bluetoothConnect, state.motionSensors)
            .firstOrNull { status -> status.state != CapabilityState.AVAILABLE }
            ?.detail
            ?: "Session permission gate blocked."

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

    private fun setManualEntryField() {
        fields.getValue("manual_pairing_entry").visibility = if (manualEntryVisible) View.VISIBLE else View.GONE
        fields.getValue("manual_pairing_entry").text = listOf(
            "Manual pairing",
            "Host/IP:",
            "Port:",
            "6-digit code:",
            "No network connection is started in this phase.",
        ).joinToString("\n")
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
