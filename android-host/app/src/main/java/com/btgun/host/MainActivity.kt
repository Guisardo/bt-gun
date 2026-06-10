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
import android.widget.EditText
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
import com.btgun.host.session.TrustedDesktopMetadata
import com.btgun.host.session.TrustedDesktopStore
import com.btgun.host.ui.AimGraphView
import com.btgun.host.ui.DashboardEventMode
import com.btgun.host.ui.DashboardState
import com.btgun.host.ui.DebugExpansion
import java.lang.reflect.Proxy

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var phoneHaptics: PhoneHaptics
    private lateinit var trustedDesktopStore: TrustedDesktopStore
    private lateinit var root: LinearLayout
    private lateinit var primaryAction: Button
    private lateinit var hapticAction: Button
    private lateinit var startBluetoothGamepadAction: Button
    private lateinit var stopBluetoothGamepadAction: Button
    private lateinit var openHidPairingWindowAction: Button
    private lateinit var permissionAction: Button
    private lateinit var scanDesktopQrAction: Button
    private lateinit var manualDesktopEntryAction: Button
    private lateinit var trustedDesktopAction: Button
    private lateinit var manualPairAction: Button
    private lateinit var manualHostInput: EditText
    private lateinit var manualPortInput: EditText
    private lateinit var manualCodeInput: EditText
    private lateinit var manualFingerprintSuffixInput: EditText
    private lateinit var manualEntryGroup: LinearLayout
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
        trustedDesktopStore = TrustedDesktopStore(this)
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

        root.addView(row().apply {
            startBluetoothGamepadAction = button("Start Bluetooth gamepad") { startBluetoothGamepad() }
            stopBluetoothGamepadAction = button("Stop Bluetooth gamepad") { stopBluetoothGamepad() }
            openHidPairingWindowAction = button("Open pairing window") { openHidPairingWindow() }
            addView(startBluetoothGamepadAction)
            addView(stopBluetoothGamepadAction)
            addView(openHidPairingWindowAction)
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
            "hid_role",
            "hid_registration",
            "hid_pairing",
            "hid_host",
            "hid_input",
            "hid_output_callback",
            "hid_output_validation",
            "hid_output_haptic",
            "hid_fallback",
            "desktop_link",
        ).forEach(::addField)

        root.addView(row().apply {
            scanDesktopQrAction = button("Scan desktop QR") { scanDesktopQr() }
            manualDesktopEntryAction = button("Enter manually") { showManualEntryState() }
            trustedDesktopAction = button("Use trusted desktop") { useTrustedDesktop() }
            addView(scanDesktopQrAction)
            addView(manualDesktopEntryAction)
            addView(trustedDesktopAction)
        })
        buildManualEntryGroup()

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
            desktopLinkState = serviceState.desktopLinkState.takeIf { it.phase != DesktopLinkPhase.IDLE } ?: desktopLinkState,
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
        setField("hid_role", "${dashboard.hidGamepad.role}: ${dashboard.hidGamepad.roleCapability.label}; ${dashboard.hidGamepad.roleCapability.value}")
        setField("hid_registration", "${dashboard.hidGamepad.registration.label}: ${dashboard.hidGamepad.registration.value}")
        setField("hid_pairing", "${dashboard.hidGamepad.pairingWindow.label}: ${dashboard.hidGamepad.pairingWindow.value}")
        setField("hid_host", "${dashboard.hidGamepad.hostConnection.label}: ${dashboard.hidGamepad.hostConnection.value}")
        setField("hid_input", "${dashboard.hidGamepad.lastInputReport.label}: ${dashboard.hidGamepad.lastInputReport.value}")
        setField("hid_output_callback", "${dashboard.hidGamepad.outputCallback.label}: ${dashboard.hidGamepad.outputCallback.value}")
        setField("hid_output_validation", "${dashboard.hidGamepad.outputValidation.label}: ${dashboard.hidGamepad.outputValidation.value}")
        setField("hid_output_haptic", "${dashboard.hidGamepad.outputHaptic.label}: ${dashboard.hidGamepad.outputHaptic.value}")
        setField("hid_fallback", "${dashboard.hidGamepad.fallback.label}: ${dashboard.hidGamepad.fallback.value}")
        setField("desktop_link", "${dashboard.placeholders.desktopLink.title}: ${dashboard.placeholders.desktopLink.body}")
        scanDesktopQrAction.text = "Scan desktop QR"
        trustedDesktopAction.visibility = if (firstTrustedDesktop() != null) View.VISIBLE else View.GONE
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

    private fun startBluetoothGamepad() {
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_START_BLUETOOTH_GAMEPAD),
        )
    }

    private fun stopBluetoothGamepad() {
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_STOP_BLUETOOTH_GAMEPAD),
        )
    }

    private fun openHidPairingWindow() {
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_START_HID_PAIRING_WINDOW),
        )
    }

    private fun scanDesktopQr() {
        manualEntryVisible = false
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.SCANNING_QR,
            diagnosticTextOverride = "Scanning desktop QR. Keep the desktop pairing QR visible.",
        )
        renderDashboard()
        startOptionalCodeScanner()
    }

    private fun showManualEntryState() {
        manualEntryVisible = true
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.CONNECTING,
            diagnosticTextOverride = "Manual entry ready. Enter host/IP, port, 6-digit code, and trusted desktop fingerprint suffix.",
        )
        renderDashboard()
    }

    private fun connectManualEntry() {
        manualEntryVisible = true
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.CONNECTING,
            diagnosticTextOverride = "Connecting with manual host/IP, port, code, and trusted desktop fingerprint suffix.",
        )
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_CONNECT_MANUAL_DESKTOP)
                .putExtra(HostSessionService.EXTRA_MANUAL_HOST, manualHostInput.text.toString())
                .putExtra(HostSessionService.EXTRA_MANUAL_PORT, manualPortInput.text.toString())
                .putExtra(HostSessionService.EXTRA_MANUAL_CODE, manualCodeInput.text.toString())
                .putExtra(
                    HostSessionService.EXTRA_MANUAL_FINGERPRINT_SUFFIX,
                    manualFingerprintSuffixInput.text.toString(),
                ),
        )
    }

    private fun useTrustedDesktop() {
        val trusted = firstTrustedDesktop()
        if (trusted == null) {
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.DISCONNECTED,
                lastControlError = "No trusted desktop stored. Scan desktop QR first.",
            )
            renderDashboard()
            return
        }
        manualEntryVisible = false
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.CONNECTING,
            desktopDisplayName = trusted.displayName,
            fingerprintSuffix = trusted.fingerprintSha256.takeLast(8),
            diagnosticTextOverride = "Trusted desktop selected. Start pairing on desktop, then scan QR or enter manual code.",
        )
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_CONNECT_TRUSTED_DESKTOP)
                .putExtra(HostSessionService.EXTRA_DESKTOP_FINGERPRINT, trusted.fingerprintSha256),
        )
    }

    private fun scannerFailed(message: String) {
        manualEntryVisible = true
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.DISCONNECTED,
            lastControlError = message,
        )
        renderDashboard()
    }

    private fun startOptionalCodeScanner() {
        runCatching {
            val scannerClass = Class.forName("com.google.mlkit.vision.codescanner.GmsBarcodeScanning")
            val successClass = Class.forName("com.google.android.gms.tasks.OnSuccessListener")
            val failureClass = Class.forName("com.google.android.gms.tasks.OnFailureListener")
            val client = scannerClass.getMethod("getClient", android.content.Context::class.java).invoke(null, this)
            val task = client.javaClass.getMethod("startScan").invoke(client)
            val successListener = Proxy.newProxyInstance(
                successClass.classLoader,
                arrayOf(successClass),
            ) { _, _, args ->
                val barcode = args?.firstOrNull()
                val rawPayload = barcode?.javaClass?.methods
                    ?.firstOrNull { method -> method.name == "getRawValue" }
                    ?.invoke(barcode) as? String
                handleScannedPayload(rawPayload)
                null
            }
            val failureListener = Proxy.newProxyInstance(
                failureClass.classLoader,
                arrayOf(failureClass),
            ) { _, _, args ->
                val error = args?.firstOrNull() as? Throwable
                scannerFailed("Scanner unavailable: ${error?.javaClass?.simpleName ?: "CodeScanner"}. Enter manually.")
                null
            }
            task.javaClass.getMethod("addOnSuccessListener", successClass).invoke(task, successListener)
            task.javaClass.getMethod("addOnFailureListener", failureClass).invoke(task, failureListener)
        }.onFailure { error ->
            scannerFailed("Scanner unavailable: ${error.javaClass.simpleName}. Enter manually.")
        }
    }

    private fun handleScannedPayload(rawPayload: String?) {
        if (rawPayload.isNullOrBlank()) {
            scannerFailed("Scanner returned an empty QR payload.")
            return
        }
        desktopLinkState = DesktopLinkState(
            phase = DesktopLinkPhase.CONNECTING,
            diagnosticTextOverride = "Connecting to desktop endpoint from QR payload.",
        )
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_CONNECT_DESKTOP_QR)
                .putExtra(HostSessionService.EXTRA_QR_PAYLOAD, rawPayload),
        )
    }

    private fun startServiceAction(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (error: SecurityException) {
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.DISCONNECTED,
                lastControlError = "Desktop session blocked: ${error.javaClass.simpleName}",
            )
        } catch (error: IllegalStateException) {
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.DISCONNECTED,
                lastControlError = "Desktop session blocked: ${error.javaClass.simpleName}",
            )
        }
        renderDashboard()
    }

    private fun firstTrustedDesktop(): TrustedDesktopMetadata? =
        trustedDesktopStore.loadTrustedDesktops().firstOrNull()

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

    private fun buildManualEntryGroup() {
        manualEntryGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        manualEntryGroup.addView(TextView(this).apply {
            text = "Manual pairing"
            textSize = 14f
            setTextColor(Color.rgb(31, 41, 51))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        manualHostInput = editText("Host/IP")
        manualPortInput = editText("Port")
        manualCodeInput = editText("6-digit code")
        manualFingerprintSuffixInput = editText("Trusted desktop fingerprint suffix")
        manualPairAction = button("Connect manually") { connectManualEntry() }
        listOf(
            manualHostInput,
            manualPortInput,
            manualCodeInput,
            manualFingerprintSuffixInput,
            manualPairAction,
        ).forEach(manualEntryGroup::addView)
        root.addView(manualEntryGroup)
    }

    private fun setManualEntryField() {
        manualEntryGroup.visibility = if (manualEntryVisible) View.VISIBLE else View.GONE
        val trusted = firstTrustedDesktop()
        if (trusted != null && manualFingerprintSuffixInput.text.isBlank()) {
            manualFingerprintSuffixInput.setText(trusted.fingerprintSha256.takeLast(8))
        }
        manualPairAction.text = "Connect manually"
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

    private fun editText(hintText: String): EditText =
        EditText(this).apply {
            hint = hintText
            textSize = 14f
            setSingleLine(true)
            minHeight = dp(48)
            setTextColor(Color.rgb(31, 41, 51))
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
