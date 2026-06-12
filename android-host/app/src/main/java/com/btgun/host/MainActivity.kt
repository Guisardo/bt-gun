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
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.btgun.host.haptics.PhoneHapticStatus
import com.btgun.host.haptics.PhoneHaptics
import com.btgun.host.hid.BtGunHidHostConnectionState
import com.btgun.host.hid.BtGunHidProxyState
import com.btgun.host.hid.BtGunHidRegistrationState
import com.btgun.host.motion.AimBaseline
import com.btgun.host.permissions.CapabilityState
import com.btgun.host.permissions.HostCapabilityProbe
import com.btgun.host.permissions.AndroidHidHostConnectionStatus
import com.btgun.host.permissions.AndroidHidProfileStatus
import com.btgun.host.permissions.AndroidHidRegistrationStatus
import com.btgun.host.permissions.PermissionGate
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.profile.AimMappingSettings
import com.btgun.host.profile.AimProviderKey
import com.btgun.host.profile.BtGunProfile
import com.btgun.host.profile.DEFAULT_VISUALIZER_PROFILE_ID
import com.btgun.host.profile.PhysicalButton
import com.btgun.host.profile.ProfileStore
import com.btgun.host.profile.ProfileValidator
import com.btgun.host.profile.ProviderAimOverrides
import com.btgun.host.profile.SaveProfileResult
import com.btgun.host.profile.SmoothingMode
import com.btgun.host.profile.VirtualButton
import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.session.DesktopLinkState
import com.btgun.host.session.TrustedDesktopMetadata
import com.btgun.host.session.TrustedDesktopStore
import com.btgun.host.ui.AimGraphView
import com.btgun.host.ui.DashboardEventMode
import com.btgun.host.ui.DashboardState
import com.btgun.host.ui.DebugExpansion
import com.btgun.host.util.AndroidLog
import java.lang.reflect.Proxy

private data class AimSettingControls(
    val sensitivity: EditText,
    val invertX: CheckBox,
    val invertY: CheckBox,
    val deadZone: EditText,
    val smoothing: Spinner,
)

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var phoneHaptics: PhoneHaptics
    private lateinit var trustedDesktopStore: TrustedDesktopStore
    private lateinit var profileStore: ProfileStore
    private lateinit var root: LinearLayout
    private lateinit var primaryAction: Button
    private lateinit var hapticAction: Button
    private lateinit var editProfilesAction: Button
    private lateinit var profileListGroup: LinearLayout
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
    private var profileListVisible: Boolean = false
    private var profileSurfaceDirty: Boolean = true
    private var editingProfileId: String? = null
    private var profileActionStatus: String? = null
    private var eventMode: DashboardEventMode = DashboardEventMode.PRODUCT_EVENTS
    private var debugExpansion = DebugExpansion()
    private var profileNameInput: EditText? = null
    private var sharedAimControls: AimSettingControls? = null
    private val providerUseSharedInputs = mutableMapOf<AimProviderKey, CheckBox>()
    private val providerAimControls = mutableMapOf<AimProviderKey, AimSettingControls>()
    private val buttonMappingInputs = mutableMapOf<PhysicalButton, Spinner>()
    private var recenterButtonInput: Spinner? = null
    private var rawDebugInput: CheckBox? = null
    private var profileValidationText: TextView? = null
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
        profileStore = ProfileStore(this)
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
        addActionGroup(permissionAction)

        primaryAction = button("Start live session") { toggleSession() }
        hapticAction = button("Test local haptic") {
            lastPhoneHapticStatus = phoneHaptics.test()
            renderDashboard()
        }
        addActionGroup(primaryAction, hapticAction)

        listOf(
            "active_profile",
            "profile_mapping",
            "recenter_control",
            "raw_debug_stream",
            "profile_error",
        ).forEach(::addField)
        editProfilesAction = button("Edit profiles") {
            profileListVisible = !profileListVisible
            profileSurfaceDirty = true
            renderDashboard()
        }
        addActionGroup(editProfilesAction)
        profileListGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(profileListGroup)

        startBluetoothGamepadAction = button("Start Bluetooth gamepad") { startBluetoothGamepad() }
        stopBluetoothGamepadAction = button("Stop Bluetooth gamepad") { stopBluetoothGamepad() }
        addActionGroup(startBluetoothGamepadAction, stopBluetoothGamepadAction)
        openHidPairingWindowAction = button("Open pairing window") { openHidPairingWindow() }
        addActionGroup(openHidPairingWindowAction)

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

        scanDesktopQrAction = button("Scan desktop QR") { scanDesktopQr() }
        manualDesktopEntryAction = button("Enter manually") { showManualEntryState() }
        trustedDesktopAction = button("Use trusted desktop") { useTrustedDesktop() }
        addActionGroup(scanDesktopQrAction, manualDesktopEntryAction, trustedDesktopAction)
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
        addActionGroup(debugModeAction)

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
        addActionGroup(bleDebugAction)
        addField("ble_debug")
        addActionGroup(permissionDebugAction)
        addField("permission_debug")
        addActionGroup(gattDebugAction)
        addField("gatt_debug")

        setContentView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    private fun renderDashboard() {
        val latestServiceState = HostSessionService.latestState
        val permissionGate = permissionGateState(latestServiceState)
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
        setField("active_profile", dashboard.profile.activeProfile.value)
        setField("profile_mapping", dashboard.profile.profileMapping.value)
        setField("recenter_control", dashboard.profile.recenterControl.value)
        setField("raw_debug_stream", dashboard.profile.rawDebugStream.value)
        setField("profile_error", dashboard.profile.profileError.value)
        editProfilesAction.text = "Edit profiles"
        renderProfileList()

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

    private fun renderProfileList() {
        profileListGroup.visibility = if (profileListVisible) View.VISIBLE else View.GONE
        if (!profileListVisible) {
            editingProfileId = null
            return
        }
        if (!profileSurfaceDirty) {
            return
        }
        profileSurfaceDirty = false
        profileListGroup.removeAllViews()

        profileActionStatus?.let { status ->
            profileListGroup.addView(profileText(status, bold = false))
        }

        val document = profileStore.load().document
        val editingId = editingProfileId
        if (editingId != null) {
            val editingProfile = document.profiles.firstOrNull { profile -> profile.profileId == editingId }
            if (editingProfile != null && !editingProfile.builtIn) {
                addProfileEditor(editingProfile)
                return
            }
            editingProfileId = null
            profileActionStatus = "Edit profile blocked: built_in_immutable"
        }

        val defaultProfile = document.profiles.firstOrNull { profile ->
            profile.profileId == DEFAULT_VISUALIZER_PROFILE_ID
        } ?: BtGunProfile.defaultVisualizer()
        addProfileRow(defaultProfile, activeProfileId = document.activeProfileId)

        val userProfiles = document.profiles.filterNot { profile ->
            profile.profileId == DEFAULT_VISUALIZER_PROFILE_ID || profile.builtIn
        }
        userProfiles.forEach { profile ->
            addProfileRow(profile, activeProfileId = document.activeProfileId)
        }

        if (userProfiles.isEmpty()) {
            profileListGroup.addView(profileText("No user profiles", bold = true))
            profileListGroup.addView(profileText("Duplicate Default Visualizer to create an editable profile.", bold = false))
        }
    }

    private fun addProfileRow(profile: BtGunProfile, activeProfileId: String) {
        val builtIn = profile.builtIn || profile.profileId == DEFAULT_VISUALIZER_PROFILE_ID
        val labels = buildList {
            add(profile.displayName)
            if (builtIn) add("Built-in")
            if (profile.profileId == activeProfileId) add("Active")
            add("rev=${profile.revision}")
        }
        profileListGroup.addView(profileText(labels.joinToString(" | "), bold = true))

        val useProfile = button("Use profile") {
            applyProfileStoreResult(profileStore.selectProfile(profile.profileId), "Use profile")
        }
        val duplicateProfile = button("Duplicate profile") {
            applyProfileStoreResult(profileStore.duplicateProfile(profile.profileId), "Duplicate profile")
        }
        if (builtIn) {
            addProfileActionGroup(useProfile, duplicateProfile)
            return
        }

        val editProfile = button("Edit profile") {
            editingProfileId = profile.profileId
            profileSurfaceDirty = true
            profileActionStatus = "Edit profile"
            renderDashboard()
        }
        val deleteProfile = button("Delete profile") {
            applyProfileStoreResult(profileStore.deleteProfile(profile.profileId), "Delete profile")
        }
        addProfileActionGroup(useProfile, editProfile, duplicateProfile, deleteProfile)
    }

    private fun applyProfileStoreResult(result: SaveProfileResult, action: String) {
        profileSurfaceDirty = true
        profileActionStatus = when (result) {
            is SaveProfileResult.Saved -> "$action saved"
            is SaveProfileResult.Rejected -> "$action blocked: ${result.reason}"
        }
        if (result is SaveProfileResult.Saved) {
            startServiceAction(
                Intent(this, HostSessionService::class.java)
                    .setAction(HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE),
            )
        } else {
            renderDashboard()
        }
    }

    private fun addProfileEditor(profile: BtGunProfile) {
        clearProfileEditorInputs()
        profileListGroup.addView(profileText("Edit profile", bold = true))
        profileNameInput = editText("Profile name").apply {
            setText(profile.displayName)
        }.also(profileListGroup::addView)

        profileListGroup.addView(profileText("Shared aim settings", bold = true))
        sharedAimControls = addAimSettingControls(profile.aim)

        addProviderOverrideGroup(
            key = AimProviderKey.CALIBRATED_FUSED_ROTATION,
            label = "Calibrated and fused rotation",
            override = profile.providerOverrides[AimProviderKey.CALIBRATED_FUSED_ROTATION] ?: ProviderAimOverrides(),
        )
        addProviderOverrideGroup(
            key = AimProviderKey.GYRO_RAW_AIM,
            label = "Gyro and raw aim",
            override = profile.providerOverrides[AimProviderKey.GYRO_RAW_AIM] ?: ProviderAimOverrides(),
        )
        addProviderOverrideGroup(
            key = AimProviderKey.TILT_FALLBACK,
            label = "Accelerometer and gravity tilt fallback",
            override = profile.providerOverrides[AimProviderKey.TILT_FALLBACK] ?: ProviderAimOverrides(),
        )

        profileListGroup.addView(profileText("Button mapping", bold = true))
        PhysicalButton.defaultOrder.forEach { physical ->
            profileListGroup.addView(profileText("${physical.id} output", bold = false))
            val spinner = spinner(
                values = VirtualButton.requiredOutputs.map { output -> output.id },
                selected = profile.buttonMapping[physical]?.id ?: VirtualButton.TRIGGER.id,
            )
            buttonMappingInputs[physical] = spinner
            profileListGroup.addView(spinner)
        }

        profileListGroup.addView(profileText("Hold-to-recenter button", bold = true))
        recenterButtonInput = spinner(
            values = PhysicalButton.defaultOrder.map { button -> button.id },
            selected = profile.recenterPhysicalControl?.id ?: PhysicalButton.RELOAD.id,
        ).also(profileListGroup::addView)

        rawDebugInput = CheckBox(this).apply {
            text = "Send raw debug data"
            textSize = 14f
            minHeight = dp(48)
            isChecked = profile.rawDebugEnabled
        }.also(profileListGroup::addView)
        profileListGroup.addView(
            profileText(
                "Adds provider and raw motion fields to the debug stream for this Android session only.",
                bold = false,
            ),
        )

        profileValidationText = profileText("Save blocked", bold = false).also { label ->
            label.visibility = View.GONE
            profileListGroup.addView(label)
        }
        val saveProfile = button("Save profile") { saveProfileEditor(profile) }
        val resetProfile = button("Reset profile") { resetProfileEditor(profile) }
        val cancelEdit = button("Use profile list") {
            editingProfileId = null
            profileSurfaceDirty = true
            renderDashboard()
        }
        addProfileActionGroup(saveProfile, resetProfile, cancelEdit)
    }

    private fun addProviderOverrideGroup(
        key: AimProviderKey,
        label: String,
        override: ProviderAimOverrides,
    ) {
        profileListGroup.addView(profileText(label, bold = true))
        val useShared = CheckBox(this).apply {
            text = "Use shared settings"
            textSize = 14f
            minHeight = dp(48)
            isChecked = override.useSharedSettings
        }
        providerUseSharedInputs[key] = useShared
        profileListGroup.addView(useShared)
        providerAimControls[key] = addAimSettingControls(override.settings)
    }

    private fun addAimSettingControls(settings: AimMappingSettings): AimSettingControls {
        val sensitivity = editText("Sensitivity").apply {
            setText(formatEditorFloat(settings.sensitivity))
        }
        val invertX = CheckBox(this).apply {
            text = "Invert X"
            textSize = 14f
            minHeight = dp(48)
            isChecked = settings.invertX
        }
        val invertY = CheckBox(this).apply {
            text = "Invert Y"
            textSize = 14f
            minHeight = dp(48)
            isChecked = settings.invertY
        }
        val deadZone = editText("Dead zone").apply {
            setText(formatEditorFloat(settings.deadZone))
        }
        val smoothing = spinner(
            values = SmoothingMode.entries.map { mode -> mode.id },
            selected = settings.smoothing.id,
        )
        listOf<View>(sensitivity, invertX, invertY, deadZone, smoothing).forEach(profileListGroup::addView)
        return AimSettingControls(
            sensitivity = sensitivity,
            invertX = invertX,
            invertY = invertY,
            deadZone = deadZone,
            smoothing = smoothing,
        )
    }

    private fun saveProfileEditor(existing: BtGunProfile) {
        val draft = profileDraftFromEditor(existing)
        val validationErrors = ProfileValidator.validate(draft)
        if (validationErrors.isNotEmpty()) {
            profileValidationText?.apply {
                visibility = View.VISIBLE
                text = listOf(
                    "Save blocked",
                    "Profile has invalid mappings. Fix the highlighted controls before saving.",
                    validationErrors.joinToString(", ") { error -> error.label },
                ).joinToString("\n")
            }
            profileActionStatus = "Save blocked"
            return
        }
        applyProfileStoreResult(profileStore.saveProfile(draft), "Save profile")
    }

    private fun resetProfileEditor(existing: BtGunProfile) {
        val resetProfile = existing.copy(
            aim = AimMappingSettings.defaults(),
            providerOverrides = BtGunProfile.defaultProviderOverrides(),
            buttonMapping = BtGunProfile.defaultButtonMapping(),
            recenterPhysicalControl = PhysicalButton.RELOAD,
            rawDebugEnabled = false,
        )
        applyProfileStoreResult(profileStore.saveProfile(resetProfile), "Reset profile")
    }

    private fun profileDraftFromEditor(existing: BtGunProfile): BtGunProfile =
        existing.copy(
            displayName = profileNameInput?.text?.toString().orEmpty(),
            aim = sharedAimControls?.toAimSettings() ?: existing.aim,
            providerOverrides = AimProviderKey.defaultOrder.associateWith { key ->
                ProviderAimOverrides(
                    useSharedSettings = providerUseSharedInputs[key]?.isChecked ?: true,
                    settings = providerAimControls[key]?.toAimSettings() ?: AimMappingSettings.defaults(),
                )
            },
            buttonMapping = PhysicalButton.defaultOrder.associateWith { physical ->
                VirtualButton.fromId(buttonMappingInputs[physical]?.selectedItem?.toString().orEmpty())
                    ?: VirtualButton.TRIGGER
            },
            recenterPhysicalControl = PhysicalButton.fromId(recenterButtonInput?.selectedItem?.toString().orEmpty()),
            rawDebugEnabled = rawDebugInput?.isChecked ?: false,
        )

    private fun AimSettingControls.toAimSettings(): AimMappingSettings =
        AimMappingSettings(
            sensitivity = sensitivity.text.toString().toFloatOrNull() ?: Float.NaN,
            invertX = invertX.isChecked,
            invertY = invertY.isChecked,
            deadZone = deadZone.text.toString().toFloatOrNull() ?: Float.NaN,
            smoothing = SmoothingMode.fromId(smoothing.selectedItem?.toString().orEmpty()) ?: SmoothingMode.LOW,
        )

    private fun spinner(values: List<String>, selected: String): Spinner =
        Spinner(this).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                values,
            )
            setSelection(values.indexOf(selected).coerceAtLeast(0))
        }

    private fun clearProfileEditorInputs() {
        profileNameInput = null
        sharedAimControls = null
        providerUseSharedInputs.clear()
        providerAimControls.clear()
        buttonMappingInputs.clear()
        recenterButtonInput = null
        rawDebugInput = null
        profileValidationText = null
    }

    private fun formatEditorFloat(value: Float): String =
        java.lang.String.format(java.util.Locale.US, "%.2f", value).let { text ->
            when {
                text.endsWith(".00") -> text.dropLast(1)
                text.endsWith("0") -> text.dropLast(1)
                else -> text
            }
        }

    private fun profileText(value: String, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.rgb(31, 41, 51))
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(4))
            if (bold) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }

    private fun addProfileActionGroup(vararg buttons: Button) {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        buttons.forEach { action ->
            group.addView(
                action,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        profileListGroup.addView(group)
    }

    private fun startBluetoothGamepad() {
        AndroidLog.i(TAG, "Start Bluetooth gamepad tapped")
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_START_BLUETOOTH_GAMEPAD),
        )
    }

    private fun stopBluetoothGamepad() {
        AndroidLog.i(TAG, "Stop Bluetooth gamepad tapped")
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_STOP_BLUETOOTH_GAMEPAD),
        )
    }

    private fun openHidPairingWindow() {
        AndroidLog.i(TAG, "Open HID pairing window tapped")
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
            AndroidLog.w(TAG, "Service action blocked by security", error)
            desktopLinkState = DesktopLinkState(
                phase = DesktopLinkPhase.DISCONNECTED,
                lastControlError = "Desktop session blocked: ${error.javaClass.simpleName}",
            )
        } catch (error: IllegalStateException) {
            AndroidLog.w(TAG, "Service action blocked by state", error)
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

    private fun permissionGateState(
        serviceState: HostSessionState = HostSessionService.latestState,
    ): PermissionGateState {
        val hidStatus = serviceState.hidGamepadStatus
        return PermissionGate.evaluate(
            HostCapabilityProbe.input(this).copy(
                bluetoothHidProfileStatus = hidStatus.proxy.toAndroidHidProfileStatus(),
                bluetoothHidRegistrationStatus = hidStatus.registration.toAndroidHidRegistrationStatus(),
                bluetoothHidHostConnectionStatus = hidStatus.hostConnection.toAndroidHidHostConnectionStatus(),
            ),
        )
    }

    private fun BtGunHidProxyState.toAndroidHidProfileStatus(): AndroidHidProfileStatus =
        when (this) {
            BtGunHidProxyState.AVAILABLE -> AndroidHidProfileStatus.AVAILABLE
            BtGunHidProxyState.UNAVAILABLE,
            BtGunHidProxyState.CLOSED,
            -> AndroidHidProfileStatus.UNAVAILABLE
            BtGunHidProxyState.NOT_REQUESTED,
            BtGunHidProxyState.REQUESTING,
            -> AndroidHidProfileStatus.NOT_PROBED
        }

    private fun BtGunHidRegistrationState.toAndroidHidRegistrationStatus(): AndroidHidRegistrationStatus =
        when (this) {
            BtGunHidRegistrationState.REGISTERED -> AndroidHidRegistrationStatus.REGISTERED
            BtGunHidRegistrationState.FAILED -> AndroidHidRegistrationStatus.FAILED
            BtGunHidRegistrationState.NOT_REGISTERED,
            BtGunHidRegistrationState.REGISTERING,
            -> AndroidHidRegistrationStatus.NOT_REQUESTED
        }

    private fun BtGunHidHostConnectionState.toAndroidHidHostConnectionStatus(): AndroidHidHostConnectionStatus =
        when (this) {
            BtGunHidHostConnectionState.CONNECTED -> AndroidHidHostConnectionStatus.CONNECTED
            BtGunHidHostConnectionState.DISCONNECTED -> AndroidHidHostConnectionStatus.DISCONNECTED
            BtGunHidHostConnectionState.NOT_CONNECTED -> AndroidHidHostConnectionStatus.NOT_CONNECTED
        }

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
            setAllCaps(false)
            textSize = 14f
            minHeight = dp(48)
            minimumWidth = 0
            maxLines = 2
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
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

    private fun addActionGroup(vararg buttons: Button) {
        val horizontal = resources.configuration.screenWidthDp >= ACTION_GROUP_HORIZONTAL_MIN_DP
        val group = LinearLayout(this).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        buttons.forEachIndexed { index, action ->
            val params = LinearLayout.LayoutParams(
                if (horizontal) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            if (horizontal) {
                params.weight = 1f
            }
            params.setMargins(
                0,
                dp(4),
                if (horizontal && index < buttons.lastIndex) dp(8) else 0,
                dp(4),
            )
            group.addView(action, params)
        }
        root.addView(group)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "BtGunMain"
        private const val REFRESH_INTERVAL_MS = 500L
        private const val REQUEST_PERMISSIONS = 2001
        private const val ACTION_GROUP_HORIZONTAL_MIN_DP = 600
    }
}
