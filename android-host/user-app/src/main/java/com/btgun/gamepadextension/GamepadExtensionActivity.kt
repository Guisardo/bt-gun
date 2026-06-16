package com.btgun.gamepadextension

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.btgun.host.HostSessionService
import com.btgun.host.HostSessionPhase
import com.btgun.host.hid.BtGunHidModeState
import com.btgun.host.motion.AimCalibrationMark
import com.btgun.host.motion.AimCalibrationMode
import com.btgun.host.motion.AimCalibrationState
import com.btgun.host.permissions.HostCapabilityProbe
import com.btgun.host.profile.BtGunProfile
import com.btgun.host.profile.PhysicalButton
import com.btgun.host.profile.ProfileStore
import com.btgun.host.profile.SaveProfileResult
import com.btgun.host.profile.SoftControl
import com.btgun.host.profile.VirtualButton
import com.btgun.host.session.DesktopLinkPhase
import com.btgun.host.transport.InputStreamLifecycleState
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class GamepadExtensionActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var textureView: TextureView
    private lateinit var zoomLabel: TextView
    private lateinit var zoomSlider: ZoomSliderView
    private var reticleView: ReticleView? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraOpening: Boolean = false
    private var captureSession: CameraCaptureSession? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSize: Size? = null
    private var cameraId: String? = null
    private var activeArray: Rect? = null
    private var maxZoom: Float = 1f
    private var currentZoom: Float = 1f
    private var hudActive: Boolean = false
    private var cameraPreviewReady: Boolean = false
    private var menuOpen: Boolean = false
    private var hudGatedViews: List<View> = emptyList()
    private var pendingMode: PendingMode? = null
    private var pendingGunStart: Boolean = false
    private val gunLinkPoll = object : Runnable {
        override fun run() {
            val state = HostSessionService.latestState
            when (state.phase) {
                HostSessionPhase.CONNECTED -> showModePicker("Gun connected")
                HostSessionPhase.ERROR -> {
                    status.text = "Gun link failed: ${state.lastError ?: state.lastBleConnectionState.lastError ?: "unknown"}"
                }
                HostSessionPhase.SCANNING,
                HostSessionPhase.CONNECTING,
                HostSessionPhase.RECONNECTING,
                HostSessionPhase.STARTING,
                -> {
                    status.text = "Linking gun: ${state.phase.wireName}"
                    handler.postDelayed(this, 500L)
                }
                else -> handler.postDelayed(this, 500L)
            }
        }
    }
    private val readinessPoll = object : Runnable {
        override fun run() {
            val mode = pendingMode ?: return
            val ready = isRuntimeReadyFor(mode)
            if (ready && !hudActive) {
                enterHud(mode.statusLabel)
                pendingMode = null
            } else if (!hudActive) {
                status.text = "Waiting for ${mode.displayName}: ${readinessDetail(mode)}"
                handler.postDelayed(this, 300L)
            }
        }
    }
    private val calibrationHudPoll = object : Runnable {
        override fun run() {
            if (!hudActive) {
                return
            }
            updateCalibrationHud(HostSessionService.latestState.aimCalibrationState)
            handler.postDelayed(this, 180L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        showGunLinkGate()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (hudActive && hasCameraPermission() && ::textureView.isInitialized && textureView.isAvailable) {
            openCamera()
        }
        if (hudActive) {
            handler.removeCallbacks(calibrationHudPoll)
            handler.post(calibrationHudPoll)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(calibrationHudPoll)
        closeCamera()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
        if (hudActive && ::textureView.isInitialized) {
            configurePreviewTransform(textureView.width, textureView.height)
        }
    }

    private fun showModePicker(message: String = "Ready") {
        if (!isGunConnected()) {
            showGunLinkGate("Connect gun first")
            return
        }
        hudActive = false
        pendingMode = null
        pendingGunStart = false
        handler.removeCallbacks(gunLinkPoll)
        handler.removeCallbacks(readinessPoll)
        handler.removeCallbacks(calibrationHudPoll)
        closeCamera()
        root = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }
        status = label(message, 14f, DIM_GREEN)
        panel.addView(label("Gamepad Extension", 30f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        panel.addView(action("LAN", "Scan desktop QR and enter HUD") { scanLanQr() }, rowParams())
        panel.addView(action("Bluetooth", "Start HID role and enter HUD") { startBluetoothMode() }, rowParams())
        panel.addView(action("Profiles", "Open profile controls") { showProfileMenu() }, rowParams())
        root.addView(panel, centeredPanelParams())
        setContentView(root)
    }

    private fun showGunLinkGate(message: String = "Connect gun") {
        hudActive = false
        pendingMode = null
        handler.removeCallbacks(readinessPoll)
        handler.removeCallbacks(calibrationHudPoll)
        closeCamera()
        if (isGunConnected()) {
            showModePicker("Gun connected")
            return
        }
        root = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }
        status = label(message, 14f, DIM_GREEN)
        panel.addView(label("Gamepad Extension", 30f, PHOSPHOR, bold = true), rowParams())
        panel.addView(label("Gun Link", 18f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        panel.addView(action("Connect gun", "Required before LAN or Bluetooth") { connectGunFirst() }, rowParams())
        root.addView(panel, centeredPanelParams())
        setContentView(root)
        when (HostSessionService.latestState.phase) {
            HostSessionPhase.SCANNING,
            HostSessionPhase.CONNECTING,
            HostSessionPhase.RECONNECTING,
            HostSessionPhase.STARTING,
            -> {
                handler.removeCallbacks(gunLinkPoll)
                handler.post(gunLinkPoll)
            }
            else -> Unit
        }
    }

    private fun enterHud(label: String) {
        hudActive = true
        cameraPreviewReady = false
        menuOpen = false
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        textureView = TextureView(this).apply {
            surfaceTextureListener = previewSurfaceListener
        }
        root.addView(textureView, FrameLayout.LayoutParams(MATCH, MATCH))
        val reticle = ReticleView(this)
        reticleView = reticle
        val softControls = topSoftControls()
        val zoom = zoomRail()
        val menu = slideMenu()
        hudGatedViews = listOf(reticle, softControls, zoom, menu).onEach { it.visibility = View.INVISIBLE }
        root.addView(reticle, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(softControls, topBarParams())
        root.addView(statusStrip(label), statusParams())
        root.addView(zoom, zoomRailParams())
        root.addView(menu, menuParams())
        setContentView(root)
        ensureCameraReady()
        handler.removeCallbacks(calibrationHudPoll)
        handler.post(calibrationHudPoll)
    }

    private fun scanLanQr() {
        if (!isGunConnected()) {
            showGunLinkGate("Connect gun before LAN")
            return
        }
        startOptionalCodeScanner()
    }

    private fun startBluetoothMode() {
        if (!isGunConnected()) {
            showGunLinkGate("Connect gun before Bluetooth")
            return
        }
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_START_BLUETOOTH_GAMEPAD),
        )
        waitForMode(PendingMode.BLUETOOTH)
    }

    private fun showProfileMenu() {
        hudActive = false
        pendingMode = null
        pendingGunStart = false
        handler.removeCallbacks(gunLinkPoll)
        handler.removeCallbacks(readinessPoll)
        handler.removeCallbacks(calibrationHudPoll)
        closeCamera()
        val store = ProfileStore(applicationContext)
        val document = store.load().document
        val active = document.activeProfile()
        root = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 20, 26, 24)
        }
        status = label(
            "${active.displayName}  dz=${"%.2f".format(active.aim.deadZone)}  sens=${"%.1f".format(active.aim.sensitivity)}",
            13f,
            DIM_GREEN,
        )
        panel.addView(label("Profiles", 24f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        panel.addView(profileEditorRow(
            profileAction("Select profile", "Choose active profile") { showProfileSelector() },
            profileAction("Rename active", "Edit profile name") { showRenameProfile() },
        ), profileEditorRowParams())
        panel.addView(profileAction("New copy", "Create editable profile") { duplicateActiveProfile() }, profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Deadzone -", "Tighter aim") { updateAim(deadZoneDelta = -0.01f) },
            profileAction("Deadzone +", "Wider aim") { updateAim(deadZoneDelta = 0.01f) },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Sensitivity -", "Lower gain") { updateAim(sensitivityDelta = -0.1f) },
            profileAction("Sensitivity +", "Raise gain") { updateAim(sensitivityDelta = 0.1f) },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Map trigger", "Now ${mappingLabel(active.buttonMapping[PhysicalButton.TRIGGER])}") {
                showPhysicalMappingChooser(PhysicalButton.TRIGGER)
            },
            profileAction("Map reload", "Now ${mappingLabel(active.buttonMapping[PhysicalButton.RELOAD])}") {
                showPhysicalMappingChooser(PhysicalButton.RELOAD)
            },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Map X", "Now ${mappingLabel(active.buttonMapping[PhysicalButton.BUTTON_X])}") {
                showPhysicalMappingChooser(PhysicalButton.BUTTON_X)
            },
            profileAction("Map Y", "Now ${mappingLabel(active.buttonMapping[PhysicalButton.BUTTON_Y])}") {
                showPhysicalMappingChooser(PhysicalButton.BUTTON_Y)
            },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Map A", "Now ${mappingLabel(active.buttonMapping[PhysicalButton.BUTTON_A])}") {
                showPhysicalMappingChooser(PhysicalButton.BUTTON_A)
            },
            profileAction("Map B", "Now ${mappingLabel(active.buttonMapping[PhysicalButton.BUTTON_B])}") {
                showPhysicalMappingChooser(PhysicalButton.BUTTON_B)
            },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Map Back", "Now ${mappingLabel(active.softControlMapping[SoftControl.BACK])}") {
                showSoftMappingChooser(SoftControl.BACK)
            },
            profileAction("Map Home", "Now ${mappingLabel(active.softControlMapping[SoftControl.HOME])}") {
                showSoftMappingChooser(SoftControl.HOME)
            },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("Map Select", "Now ${mappingLabel(active.softControlMapping[SoftControl.SELECT])}") {
                showSoftMappingChooser(SoftControl.SELECT)
            },
            profileAction("Back", "Mode picker") { showModePicker() },
        ), profileEditorRowParams())
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(panel, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
            post { scrollTo(0, 0) }
        }
        root.addView(scroll, profileEditorParams())
        setContentView(root)
    }

    private fun showPhysicalMappingChooser(button: PhysicalButton) {
        val profile = ensureEditableActiveProfile() ?: return
        showMappingChooser(
            title = button.eventLabel,
            current = profile.buttonMapping[button],
            onSelect = { selected ->
                val saved = saveProfileChange(profile.copy(buttonMapping = profile.buttonMapping + (button to selected))) {
                    "${button.eventLabel} -> ${selected.validationName}"
                }
                if (saved) showProfileMenu()
            },
        )
    }

    private fun showSoftMappingChooser(control: SoftControl) {
        val profile = ensureEditableActiveProfile() ?: return
        showMappingChooser(
            title = control.label,
            current = profile.softControlMapping[control],
            onSelect = { selected ->
                val saved = saveProfileChange(profile.copy(softControlMapping = profile.softControlMapping + (control to selected))) {
                    "${control.label} -> ${selected.validationName}"
                }
                if (saved) showProfileMenu()
            },
        )
    }

    private fun showMappingChooser(title: String, current: VirtualButton?, onSelect: (VirtualButton) -> Unit) {
        root = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 20, 26, 24)
        }
        status = label("Current ${mappingLabel(current)}", 13f, DIM_GREEN)
        panel.addView(label("Map $title", 24f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        VirtualButton.destinationOptions.chunked(4).forEach { row ->
            panel.addView(virtualButtonRow(row, current, onSelect), virtualButtonRowParams())
        }
        panel.addView(profileAction("Back", "Profile editor") { showProfileMenu() }, rowParams())
        root.addView(panel, mappingChooserParams())
        setContentView(root)
    }

    private fun showProfileSelector() {
        hudActive = false
        pendingMode = null
        pendingGunStart = false
        handler.removeCallbacks(readinessPoll)
        handler.removeCallbacks(calibrationHudPoll)
        closeCamera()
        val document = ProfileStore(applicationContext).load().document
        val active = document.activeProfile()
        root = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 20, 26, 24)
        }
        status = label("Active ${active.displayName}", 13f, DIM_GREEN)
        panel.addView(label("Select profile", 24f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        panel.addView(profileAction("New copy", "Clone active profile") { duplicateActiveProfile() }, rowParams())
        document.profiles.forEach { profile ->
            val marker = when {
                profile.profileId == active.profileId -> "Active"
                profile.builtIn -> "Built-in"
                else -> "User"
            }
            panel.addView(
                profileAction(profile.displayName, marker) { selectProfile(profile.profileId) },
                profileEditorRowParams(),
            )
        }
        panel.addView(profileAction("Back", "Profile editor") { showProfileMenu() }, rowParams())
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(panel, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, profileEditorParams())
        setContentView(root)
    }

    private fun showRenameProfile() {
        val profile = editableProfileForRename() ?: return
        root = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 20, 26, 24)
        }
        status = label("Rename ${profile.displayName}", 13f, DIM_GREEN)
        val input = EditText(this).apply {
            setText(profile.displayName)
            setSelectAllOnFocus(true)
            setTextColor(PHOSPHOR)
            setHintTextColor(DIM_GREEN)
            textSize = 18f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(18, 0, 18, 0)
            background = panelDrawable(alpha = 120)
        }
        panel.addView(label("Rename profile", 24f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        panel.addView(input, rowParams())
        panel.addView(profileEditorRow(
            profileAction("Save", "Use new name") { renameProfile(profile.profileId, input.text?.toString().orEmpty()) },
            profileAction("Back", "Profile editor") { showProfileMenu() },
        ), profileEditorRowParams())
        root.addView(panel, profileEditorParams())
        setContentView(root)
        input.requestFocus()
    }

    private fun showDeadzoneMenu() {
        menuOpen = false
        hudActive = false
        pendingMode = null
        pendingGunStart = false
        handler.removeCallbacks(readinessPoll)
        handler.removeCallbacks(calibrationHudPoll)
        closeCamera()
        val active = ProfileStore(applicationContext).load().document.activeProfile()
        root = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 20, 26, 24)
        }
        status = label(deadzoneStatus(active), 13f, DIM_GREEN)
        panel.addView(label("Deadzone", 24f, PHOSPHOR, bold = true), rowParams())
        panel.addView(status, rowParams())
        panel.addView(profileEditorRow(
            profileAction("-0.01", "Tighter aim") {
                updateAim(deadZoneDelta = -0.01f)
                refreshDeadzoneStatus()
            },
            profileAction("+0.01", "Wider aim") {
                updateAim(deadZoneDelta = 0.01f)
                refreshDeadzoneStatus()
            },
        ), profileEditorRowParams())
        panel.addView(profileEditorRow(
            profileAction("-0.05", "Large decrease") {
                updateAim(deadZoneDelta = -0.05f)
                refreshDeadzoneStatus()
            },
            profileAction("+0.05", "Large increase") {
                updateAim(deadZoneDelta = 0.05f)
                refreshDeadzoneStatus()
            },
        ), profileEditorRowParams())
        panel.addView(profileAction("Back", "Mode picker") { showModePicker() }, rowParams())
        root.addView(panel, profileEditorParams())
        setContentView(root)
    }

    private fun connectGunFirst() {
        if (!ensureHostRuntimePermissions()) {
            pendingGunStart = true
            return
        }
        pendingGunStart = false
        startServiceAction(Intent(this, HostSessionService::class.java).setAction(HostSessionService.ACTION_START_SESSION))
        handler.removeCallbacks(gunLinkPoll)
        handler.post(gunLinkPoll)
    }

    private fun isGunConnected(): Boolean =
        HostSessionService.latestState.phase == HostSessionPhase.CONNECTED

    private fun ensureEditableActiveProfile(): BtGunProfile? {
        val store = ProfileStore(applicationContext)
        val document = store.load().document
        val active = document.activeProfile()
        if (!active.builtIn) return active
        return when (val duplicate = store.duplicateProfile(active.profileId)) {
            is SaveProfileResult.Saved -> {
                val copy = duplicate.document.profiles.lastOrNull { !it.builtIn } ?: return null
                store.selectProfile(copy.profileId)
                startServiceAction(Intent(this, HostSessionService::class.java).setAction(HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE))
                copy
            }
            is SaveProfileResult.Rejected -> {
                status.text = "Profile copy failed: ${duplicate.reason}"
                null
            }
        }
    }

    private fun editableProfileForRename(): BtGunProfile? {
        val active = ProfileStore(applicationContext).load().document.activeProfile()
        return if (!active.builtIn) {
            active
        } else {
            duplicateActiveProfile(showEditor = false)
        }
    }

    private fun duplicateActiveProfile(showEditor: Boolean = true): BtGunProfile? {
        val store = ProfileStore(applicationContext)
        val active = store.load().document.activeProfile()
        when (val duplicate = store.duplicateProfile(active.profileId)) {
            is SaveProfileResult.Saved -> {
                val copy = duplicate.document.profiles.lastOrNull() ?: return null
                store.selectProfile(copy.profileId)
                startServiceAction(Intent(this, HostSessionService::class.java).setAction(HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE))
                if (showEditor) {
                    showProfileMenu()
                }
                return copy
            }
            is SaveProfileResult.Rejected -> {
                status.text = "Profile copy failed: ${duplicate.reason}"
                return null
            }
        }
    }

    private fun selectProfile(profileId: String) {
        when (val result = ProfileStore(applicationContext).selectProfile(profileId)) {
            is SaveProfileResult.Saved -> {
                startServiceAction(Intent(this, HostSessionService::class.java).setAction(HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE))
                showProfileMenu()
            }
            is SaveProfileResult.Rejected -> {
                status.text = "Profile select failed: ${result.reason}"
            }
        }
    }

    private fun renameProfile(profileId: String, rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) {
            status.text = "Name required"
            return
        }
        when (val result = ProfileStore(applicationContext).renameProfile(profileId, name)) {
            is SaveProfileResult.Saved -> {
                startServiceAction(Intent(this, HostSessionService::class.java).setAction(HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE))
                showProfileMenu()
            }
            is SaveProfileResult.Rejected -> {
                status.text = "Rename failed: ${result.reason}"
            }
        }
    }

    private fun deadzoneStatus(profile: BtGunProfile): String =
        "${profile.displayName}  dz=${"%.2f".format(profile.aim.deadZone)}"

    private fun refreshDeadzoneStatus() {
        if (::status.isInitialized) {
            status.text = deadzoneStatus(ProfileStore(applicationContext).load().document.activeProfile())
        }
    }

    private fun updateAim(deadZoneDelta: Float = 0f, sensitivityDelta: Float = 0f) {
        val profile = ensureEditableActiveProfile() ?: return
        val nextAim = profile.aim.copy(
            deadZone = (profile.aim.deadZone + deadZoneDelta).coerceIn(0f, 0.5f),
            sensitivity = (profile.aim.sensitivity + sensitivityDelta).coerceIn(0.1f, 5f),
        )
        saveProfileChange(profile.copy(aim = nextAim)) {
            "Saved dz=${"%.2f".format(nextAim.deadZone)} sens=${"%.1f".format(nextAim.sensitivity)}"
        }
    }

    private fun cyclePhysicalMapping(button: PhysicalButton) {
        val profile = ensureEditableActiveProfile() ?: return
        val next = nextVirtual(profile.buttonMapping[button])
        saveProfileChange(profile.copy(buttonMapping = profile.buttonMapping + (button to next))) {
            "${button.eventLabel} -> ${next.validationName}"
        }
    }

    private fun cycleSoftMapping(control: SoftControl) {
        val profile = ensureEditableActiveProfile() ?: return
        val next = nextVirtual(profile.softControlMapping[control])
        saveProfileChange(profile.copy(softControlMapping = profile.softControlMapping + (control to next))) {
            "${control.label} -> ${next.validationName}"
        }
    }

    private fun mappingLabel(button: VirtualButton?): String =
        button?.validationName ?: "unset"

    private fun nextVirtual(current: VirtualButton?): VirtualButton {
        val options = listOf(VirtualButton.S1, VirtualButton.S2, VirtualButton.A1, VirtualButton.A2, VirtualButton.L3, VirtualButton.R3)
        val index = options.indexOf(current).takeIf { it >= 0 } ?: -1
        return options[(index + 1) % options.size]
    }

    private fun saveProfileChange(profile: BtGunProfile, message: () -> String): Boolean =
        when (val result = ProfileStore(applicationContext).saveProfile(profile)) {
            is SaveProfileResult.Saved -> {
                startServiceAction(Intent(this, HostSessionService::class.java).setAction(HostSessionService.ACTION_RELOAD_ACTIVE_PROFILE))
                status.text = message()
                true
            }
            is SaveProfileResult.Rejected -> {
                status.text = "Profile save failed: ${result.reason}"
                false
            }
        }

    private fun handleQrPayload(payload: String?) {
        if (payload.isNullOrBlank()) {
            status.text = "QR empty"
            return
        }
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(HostSessionService.ACTION_CONNECT_DESKTOP_QR)
                .putExtra(HostSessionService.EXTRA_QR_PAYLOAD, payload),
        )
        waitForMode(PendingMode.LAN)
    }

    private fun waitForMode(mode: PendingMode) {
        pendingMode = mode
        if (!hasCameraPermission() && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
        handler.removeCallbacks(readinessPoll)
        status.text = "Waiting for ${mode.displayName}: ${readinessDetail(mode)}"
        handler.post(readinessPoll)
    }

    private fun ensureHostRuntimePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val missing = HostCapabilityProbe.runtimePermissionsForHost()
            .filter { permission -> checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
        if (missing.isEmpty()) return true
        requestPermissions(missing, REQUEST_HOST_PERMISSIONS)
        status.text = "Bluetooth/LAN permissions required"
        return false
    }

    private fun isRuntimeReadyFor(mode: PendingMode): Boolean {
        val state = HostSessionService.latestState
        val cameraReady = hasCameraPermission()
        val gunReady = state.phase == HostSessionPhase.CONNECTED
        val motionReady = state.lastMotionSample?.payload?.providerName != "unavailable"
        val profileReady = state.profileValidationError == null
        val outputReady = when (mode) {
            PendingMode.LAN -> state.desktopLinkState.phase in setOf(DesktopLinkPhase.CONNECTED, DesktopLinkPhase.DEGRADED) &&
                state.packetStreamState == InputStreamLifecycleState.ACTIVE
            PendingMode.BLUETOOTH -> state.hidGamepadStatus.mode == BtGunHidModeState.STARTED
        }
        return cameraReady && gunReady && motionReady && profileReady && outputReady
    }

    private fun readinessDetail(mode: PendingMode): String {
        val state = HostSessionService.latestState
        if (!hasCameraPermission()) return "camera"
        if (state.phase != HostSessionPhase.CONNECTED) return "gun"
        if (state.lastMotionSample?.payload?.providerName == "unavailable") return "motion"
        if (state.profileValidationError != null) return "profile"
        return when (mode) {
            PendingMode.LAN -> if (state.packetStreamState != InputStreamLifecycleState.ACTIVE) "LAN" else "ready"
            PendingMode.BLUETOOTH -> if (state.hidGamepadStatus.mode != BtGunHidModeState.STARTED) "Bluetooth HID" else "ready"
        }
    }

    private fun startOptionalCodeScanner() {
        runCatching {
            val scannerClass = Class.forName("com.google.mlkit.vision.codescanner.GmsBarcodeScanning")
            val successClass = Class.forName("com.google.android.gms.tasks.OnSuccessListener")
            val failureClass = Class.forName("com.google.android.gms.tasks.OnFailureListener")
            val client = scannerClass.getMethod("getClient", android.content.Context::class.java).invoke(null, this)
            val task = client.javaClass.getMethod("startScan").invoke(client)
            val successListener = Proxy.newProxyInstance(successClass.classLoader, arrayOf(successClass)) { _, _, args ->
                val barcode = args?.firstOrNull()
                val rawPayload = barcode?.javaClass?.methods
                    ?.firstOrNull { method -> method.name == "getRawValue" }
                    ?.invoke(barcode) as? String
                handleQrPayload(rawPayload)
                null
            }
            val failureListener = Proxy.newProxyInstance(failureClass.classLoader, arrayOf(failureClass)) { _, _, args ->
                val error = args?.firstOrNull() as? Throwable
                status.text = "Scanner unavailable: ${error?.javaClass?.simpleName ?: "CodeScanner"}"
                null
            }
            task.javaClass.getMethod("addOnSuccessListener", successClass).invoke(task, successListener)
            task.javaClass.getMethod("addOnFailureListener", failureClass).invoke(task, failureListener)
        }.onFailure { error ->
            status.text = "Scanner unavailable: ${error.javaClass.simpleName}"
        }
    }

    private fun ensureCameraReady() {
        if (!hasCameraPermission()) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            }
            showCameraRequired("Camera required for sight background")
            return
        }
        if (::textureView.isInitialized && textureView.isAvailable) {
            openCamera()
        }
    }

    @Suppress("MissingPermission")
    private fun openCamera() {
        if (cameraOpening || cameraDevice != null) {
            return
        }
        val manager = getSystemService(CameraManager::class.java)
        val id = runCatching { chooseBackCamera(manager) }.getOrNull()
        if (id == null) {
            showCameraRequired("No rear camera available")
            return
        }
        cameraId = id
        runCatching {
            val characteristics = manager.getCameraCharacteristics(id)
            activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?.takeIf { it.isFinite() && it > 1f }
                ?: 1f
            previewSize = choosePreviewSize(characteristics, textureView.width, textureView.height)
            cameraOpening = true
            manager.openCamera(id, cameraStateCallback, handler)
        }.onFailure { error ->
            cameraOpening = false
            showCameraRequired("Camera blocked: ${error.javaClass.simpleName}")
        }
    }

    private fun chooseBackCamera(manager: CameraManager): String? =
        manager.cameraIdList.firstOrNull { id ->
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull()

    private fun choosePreviewSize(
        characteristics: CameraCharacteristics,
        viewWidth: Int,
        viewHeight: Int,
    ): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.outputSizesForTextureView().orEmpty()
        if (sizes.isEmpty()) {
            return Size(viewWidth.coerceAtLeast(1), viewHeight.coerceAtLeast(1))
        }
        val targetAspect = viewWidth.coerceAtLeast(1).toFloat() / viewHeight.coerceAtLeast(1).toFloat()
        val targetArea = 1280 * 720
        return sizes
            .filter { size -> size.width >= size.height }
            .ifEmpty { sizes.toList() }
            .minWithOrNull(
                compareBy<Size>(
                    { size -> abs((size.width.toFloat() / size.height.toFloat()) - targetAspect) },
                    { size -> abs((size.width * size.height) - targetArea) },
                ),
            ) ?: sizes.first()
    }

    private fun StreamConfigurationMap.outputSizesForTextureView(): Array<Size> =
        getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpening = false
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpening = false
            captureSession?.close()
            captureSession = null
            camera.close()
            cameraDevice = null
            showCameraRequired("Camera disconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpening = false
            captureSession?.close()
            captureSession = null
            camera.close()
            cameraDevice = null
            showCameraRequired("Camera error $error")
        }
    }

    private fun startPreview() {
        val camera = cameraDevice ?: return
        val texture = textureView.surfaceTexture ?: return
        val size = previewSize ?: Size(textureView.width.coerceAtLeast(1), textureView.height.coerceAtLeast(1))
        texture.setDefaultBufferSize(size.width, size.height)
        configurePreviewTransform(textureView.width, textureView.height)
        val surface = Surface(texture)
        runCatching {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                applyZoom(this)
            }
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        cameraPreviewReady = true
                        hudGatedViews.forEach { view -> view.visibility = View.VISIBLE }
                        updateRepeatingRequest()
                        updateZoomLabel()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        showCameraRequired("Camera preview failed")
                    }
                },
                handler,
            )
        }.onFailure { error ->
            showCameraRequired("Camera preview blocked: ${error.javaClass.simpleName}")
        }
    }

    private fun updateRepeatingRequest() {
        val builder = previewBuilder ?: return
        val session = captureSession ?: return
        applyZoom(builder)
        runCatching {
            session.setRepeatingRequest(builder.build(), null, handler)
        }.onFailure { error ->
            showCameraRequired("Camera request failed: ${error.javaClass.simpleName}")
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder) {
        val sensor = activeArray ?: return
        val zoom = currentZoom.coerceIn(1f, maxZoom.coerceAtLeast(1f))
        val cropWidth = (sensor.width() / zoom).roundToInt().coerceAtLeast(1)
        val cropHeight = (sensor.height() / zoom).roundToInt().coerceAtLeast(1)
        val left = sensor.left + (sensor.width() - cropWidth) / 2
        val top = sensor.top + (sensor.height() - cropHeight) / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + cropWidth, top + cropHeight))
    }

    private fun setZoom(next: Float) {
        currentZoom = next.coerceIn(1f, maxZoom.coerceAtLeast(1f))
        updateZoomLabel()
        updateRepeatingRequest()
    }

    private fun updateZoomLabel() {
        zoomLabel.text = if (maxZoom > 1f) {
            "%.1fx".format(Locale.US, currentZoom)
        } else {
            "1.0x"
        }
        if (::zoomSlider.isInitialized) {
            val fraction = if (maxZoom > 1f) {
                (currentZoom - 1f) / (maxZoom - 1f)
            } else {
                0f
            }
            zoomSlider.setFraction(fraction)
            zoomSlider.isEnabled = maxZoom > 1f
        }
    }

    private val previewSurfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            configurePreviewTransform(width, height)
            ensureCameraReady()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configurePreviewTransform(width, height)
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        cameraOpening = false
        previewBuilder = null
        previewSize = null
        cameraPreviewReady = false
        hudGatedViews.forEach { view -> view.visibility = View.INVISIBLE }
    }

    private fun configurePreviewTransform(viewWidth: Int, viewHeight: Int) {
        if (!::textureView.isInitialized || viewWidth <= 0 || viewHeight <= 0) {
            return
        }
        val rotation = displayRotation()
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val buffer = previewSize
        if (rotation.isQuarterTurn()) {
            val bufferRect = RectF(
                0f,
                0f,
                (buffer?.height ?: viewHeight).toFloat(),
                (buffer?.width ?: viewWidth).toFloat(),
            )
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / (buffer?.height ?: viewHeight).toFloat(),
                viewWidth.toFloat() / (buffer?.width ?: viewWidth).toFloat(),
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90f * (rotation - 2), centerX, centerY)
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, centerX, centerY)
        }
        matrix.postScale(PREVIEW_EDGE_CROP, PREVIEW_EDGE_CROP, centerX, centerY)
        textureView.setTransform(matrix)
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            windowManager.defaultDisplay.rotation
        }

    private fun Int.isQuarterTurn(): Boolean =
        this == Surface.ROTATION_90 || this == Surface.ROTATION_270

    private fun showCameraRequired(message: String) {
        if (::status.isInitialized) {
            status.text = message
        }
    }

    private fun updateCalibrationHud(calibrationState: AimCalibrationState) {
        reticleView?.calibrationState = calibrationState
        if (!::status.isInitialized) {
            return
        }
        val label = when (calibrationState.mode) {
            AimCalibrationMode.IDLE -> null
            AimCalibrationMode.WAITING_FOR_MARK,
            AimCalibrationMode.CAPTURED,
            AimCalibrationMode.VALIDATING,
            AimCalibrationMode.ACTIVE,
            AimCalibrationMode.FAILED,
            -> calibrationState.statusLabel
        }
        if (label != null && status.text != label) {
            status.text = label
        }
    }

    private fun topSoftControls(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(hudControlButton("Back", HudIcon.BACK, "back"), hudControlParams())
            addView(hudControlButton("Home", HudIcon.HOME, "home"), hudControlParams())
            addView(hudControlButton("Select", HudIcon.SELECT, "select"), hudControlParams())
        }

    private fun hudControlParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(190, 164).apply {
            setMargins(12, 0, 12, 0)
        }

    private fun hudControlButton(labelText: String, icon: HudIcon, control: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(14, 12, 14, 12)
            background = hudPanelDrawable()
            addView(HudIconView(context, icon), LinearLayout.LayoutParams(MATCH, 48))
            val text = TextView(context).apply {
                text = labelText
                setTextColor(PHOSPHOR)
                textSize = 14f
                letterSpacing = 0f
                includeFontPadding = true
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                isSingleLine = true
                maxLines = 1
            }
            addView(text, LinearLayout.LayoutParams(MATCH, 42).apply { topMargin = 6 })
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.alpha = 0.82f
                        softControl(control, pressed = true)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> {
                        view.alpha = 1f
                        softControl(control, pressed = false)
                        true
                    }
                    else -> false
                }
            }
        }

    private fun statusStrip(label: String): TextView =
        label(label, 13f, DIM_GREEN).apply {
            setPadding(16, 8, 16, 8)
            background = panelDrawable(alpha = 150)
            status = this
        }

    private fun zoomRail(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, 0)
            background = null
            zoomLabel = label("1.0x", 10f, DIM_GREEN, bold = false)
            zoomLabel.includeFontPadding = false
            zoomLabel.letterSpacing = 0.18f
            addView(zoomLabel, LinearLayout.LayoutParams(MATCH, 26).apply { setMargins(0, 0, 0, 6) })
            addView(
                zoomTextButton("+").apply { setOnClickListener { setZoom(currentZoom + 0.2f) } },
                zoomButtonParams(bottom = 6),
            )
            zoomSlider = ZoomSliderView(context) { fraction ->
                setZoom(1f + (maxZoom - 1f) * fraction)
            }
            addView(zoomSlider, LinearLayout.LayoutParams(44, 154).apply { setMargins(0, 0, 0, 6) })
            addView(
                zoomTextButton("-").apply { setOnClickListener { setZoom(currentZoom - 0.2f) } },
                zoomButtonParams(),
            )
        }

    private fun zoomButtonParams(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(54, 54).apply {
            setMargins(0, 0, 0, bottom)
        }

    private fun profileEditorRow(first: View, second: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(first, LinearLayout.LayoutParams(0, MATCH, 1f).apply { rightMargin = 8 })
            addView(second, LinearLayout.LayoutParams(0, MATCH, 1f).apply { leftMargin = 8 })
        }

    private fun virtualButtonRow(
        options: List<VirtualButton>,
        current: VirtualButton?,
        onSelect: (VirtualButton) -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            repeat(4) { index ->
                val option = options.getOrNull(index)
                val view = if (option == null) {
                    View(context)
                } else {
                    virtualOptionButton(option, selected = option == current) { onSelect(option) }
                }
                addView(view, LinearLayout.LayoutParams(0, MATCH, 1f).apply {
                    leftMargin = if (index == 0) 0 else 6
                    rightMargin = if (index == 3) 0 else 6
                })
            }
        }

    private fun profileEditorRowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, 98).apply {
            setMargins(0, 6, 0, 6)
        }

    private fun virtualButtonRowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, 76).apply {
            setMargins(0, 5, 0, 5)
        }

    private fun slideMenu(): FrameLayout =
        FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            var toggle: HamburgerButtonView? = null
            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(22, 24, 22, 18)
                visibility = View.GONE
                background = menuPanelDrawable()
                addView(label("ARGUN.EXT", 10f, DIM_GREEN).apply {
                    gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                    letterSpacing = 0.12f
                    includeFontPadding = true
                }, LinearLayout.LayoutParams(MATCH, 32))
                addView(label("SETTINGS", 18f, PHOSPHOR, bold = true).apply {
                    gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                    letterSpacing = 0.08f
                    includeFontPadding = true
                }, LinearLayout.LayoutParams(MATCH, 46))
                addView(menuButton("Profile") { showProfileMenu() }, menuItemParams())
                addView(menuButton("Deadzone") { showDeadzoneMenu() }, menuItemParams())
                addView(menuButton("Calibrate") {
                    startServiceAction(Intent(this@GamepadExtensionActivity, HostSessionService::class.java).setAction(HostSessionService.ACTION_START_AIM_CALIBRATION))
                    menuOpen = false
                    visibility = View.GONE
                    toggle?.open = false
                    status.text = "Calibration armed"
                    handler.removeCallbacks(calibrationHudPoll)
                    handler.postDelayed(calibrationHudPoll, 120L)
                }, menuItemParams())
                addView(menuButton("Exit") {
                    startServiceAction(Intent(this@GamepadExtensionActivity, HostSessionService::class.java).setAction(HostSessionService.ACTION_STOP_SESSION))
                    showModePicker("Stopped")
                }, menuItemParams())
            }
            val toggleView = HamburgerButtonView(context).apply {
                setOnClickListener {
                    menuOpen = !menuOpen
                    panel.visibility = if (menuOpen) View.VISIBLE else View.GONE
                    open = menuOpen
                }
            }
            toggle = toggleView
            addView(panel, FrameLayout.LayoutParams(300, MATCH, Gravity.LEFT))
            addView(toggleView, FrameLayout.LayoutParams(50, 50, Gravity.LEFT or Gravity.BOTTOM).apply {
                leftMargin = 20
                bottomMargin = 24
            })
        }

    private fun menuItemParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, 68).apply {
            setMargins(0, 10, 0, 0)
        }

    private fun action(title: String, subtitle: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = "$title\n$subtitle"
            setTextColor(PHOSPHOR)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            background = panelDrawable()
            setOnClickListener { onClick() }
        }

    private fun profileAction(title: String, subtitle: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = "$title\n$subtitle"
            setTextColor(PHOSPHOR)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            includeFontPadding = true
            minHeight = 92
            setLineSpacing(0f, 0.96f)
            setPadding(10, 8, 10, 8)
            background = panelDrawable(alpha = 130)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun virtualOptionButton(button: VirtualButton, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = if (selected) {
                "${button.validationName}\nCURRENT"
            } else {
                "${button.validationName}\n${button.destinationLabel.substringAfter(" - ")}"
            }
            setTextColor(if (selected) PHOSPHOR else DIM_GREEN)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            includeFontPadding = true
            maxLines = 2
            setLineSpacing(0f, 0.92f)
            setPadding(6, 4, 6, 4)
            background = panelDrawable(alpha = if (selected) 165 else 105)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun softButton(textValue: String): Button =
        Button(this).apply {
            text = textValue
            setTextColor(PHOSPHOR)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            minWidth = 0
            minHeight = 0
            setPadding(12, 6, 12, 6)
            background = panelDrawable(alpha = 130)
        }

    private fun zoomTextButton(textValue: String): TextView =
        TextView(this).apply {
            text = textValue
            setTextColor(PHOSPHOR)
            textSize = 18f
            includeFontPadding = false
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = hudPanelDrawable(alpha = 95)
        }

    private fun softControlButton(textValue: String, control: String): Button =
        softButton(textValue).apply {
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        softControl(control, pressed = true)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> {
                        softControl(control, pressed = false)
                        true
                    }
                    else -> false
                }
            }
        }

    private fun menuButton(textValue: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            setTextColor(DIM_GREEN)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            includeFontPadding = true
            minHeight = 68
            setPadding(16, 0, 16, 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(8, 0, 255, 65))
                setStroke(1, Color.argb(16, 0, 255, 65))
                cornerRadius = 6f
            }
            setOnClickListener { onClick() }
        }

    private fun softControl(control: String, pressed: Boolean) {
        startServiceAction(
            Intent(this, HostSessionService::class.java)
                .setAction(if (pressed) HostSessionService.ACTION_SOFT_CONTROL_DOWN else HostSessionService.ACTION_SOFT_CONTROL_UP)
                .putExtra(HostSessionService.EXTRA_SOFT_CONTROL, control),
        )
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

    private fun panelDrawable(alpha: Int = 120): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha, 0, 20, 8))
            setStroke(1, PHOSPHOR)
            cornerRadius = 6f
        }

    private fun hudPanelDrawable(alpha: Int = 90): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha, 0, 0, 0))
            setStroke(1, Color.argb(70, 0, 255, 65))
            cornerRadius = 8f
        }

    private fun menuPanelDrawable(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(225, 2, 10, 2))
            setStroke(1, Color.argb(46, 0, 255, 65))
            cornerRadius = 0f
        }

    private fun startServiceAction(intent: Intent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.onFailure { error ->
            if (::status.isInitialized) {
                status.text = "Service blocked: ${error.javaClass.simpleName}"
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (hudActive && ::textureView.isInitialized && textureView.isAvailable) {
                openCamera()
            }
        } else if (requestCode == REQUEST_CAMERA) {
            showCameraRequired("Camera permission required for game mode")
        } else if (requestCode == REQUEST_HOST_PERMISSIONS) {
            if (pendingGunStart && HostCapabilityProbe.runtimePermissionsForHost().all { permission ->
                    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                connectGunFirst()
            } else {
                showGunLinkGate("Permissions updated")
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun rowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 8, 0, 8)
        }

    private fun centeredPanelParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(420, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)

    private fun profileEditorParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(900, MATCH, Gravity.CENTER).apply {
            topMargin = 24
            bottomMargin = 24
        }

    private fun mappingChooserParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(1180, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)

    private fun topBarParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = 16
        }

    private fun statusParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = 14
        }

    private fun zoomRailParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(78, 330, Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin = 18
        }

    private fun menuParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(MATCH, MATCH, Gravity.LEFT or Gravity.BOTTOM)

    private enum class HudIcon {
        BACK,
        HOME,
        SELECT,
    }

    private class ReticleView(context: android.content.Context) : View(context) {
        var calibrationState: AimCalibrationState = AimCalibrationState()
            set(value) {
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PHOSPHOR
            strokeWidth = 2f
            style = Paint.Style.STROKE
            setShadowLayer(12f, 0f, 0f, PHOSPHOR)
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PHOSPHOR
            strokeWidth = 1f
            alpha = 54
            style = Paint.Style.STROKE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val gridStep = 40
            val leftGridInset = gridStep * 2
            for (x in leftGridInset until width step gridStep) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
            }
            for (y in gridStep until height step gridStep) {
                canvas.drawLine(leftGridInset.toFloat(), y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
            }
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.08f
            paint.alpha = 130
            canvas.drawCircle(cx, cy, radius * 1.85f, paint)
            paint.alpha = 210
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 3f, paint)
            paint.style = Paint.Style.STROKE
            paint.alpha = 255
            canvas.drawLine(cx - radius * 1.6f, cy, cx - radius * 0.45f, cy, paint)
            canvas.drawLine(cx + radius * 0.45f, cy, cx + radius * 1.6f, cy, paint)
            canvas.drawLine(cx, cy - radius * 1.6f, cx, cy - radius * 0.45f, paint)
            canvas.drawLine(cx, cy + radius * 0.45f, cx, cy + radius * 1.6f, paint)
            drawCalibrationMarks(canvas)
            val corner = 22f
            val offset = 8f
            paint.alpha = 150
            canvas.drawLine(offset, offset, offset + corner, offset, paint)
            canvas.drawLine(offset, offset, offset, offset + corner, paint)
            canvas.drawLine(width - offset, offset, width - offset - corner, offset, paint)
            canvas.drawLine(width - offset, offset, width - offset, offset + corner, paint)
            canvas.drawLine(offset, height - offset, offset + corner, height - offset, paint)
            canvas.drawLine(offset, height - offset, offset, height - offset - corner, paint)
            canvas.drawLine(width - offset, height - offset, width - offset - corner, height - offset, paint)
            canvas.drawLine(width - offset, height - offset, width - offset, height - offset - corner, paint)
            paint.alpha = 255
        }

        private fun drawCalibrationMarks(canvas: Canvas) {
            if (calibrationState.mode == AimCalibrationMode.IDLE) {
                return
            }
            val captured = calibrationState.capturedPoints.map { it.mark }.toSet()
            val active = calibrationState.activeMark
            AimCalibrationMark.captureOrder.forEach { mark ->
                val point = mark.toScreenPoint(width.toFloat(), height.toFloat())
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (mark == active) 4f else 2f
                paint.alpha = when {
                    mark == active -> 255
                    mark in captured -> 190
                    else -> 85
                }
                val size = if (mark == active) 34f else 24f
                canvas.drawLine(point.x - size, point.y, point.x + size, point.y, paint)
                canvas.drawLine(point.x, point.y - size, point.x, point.y + size, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(point.x, point.y, if (mark in captured) 5f else 3f, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.alpha = 255
        }

        private fun AimCalibrationMark.toScreenPoint(width: Float, height: Float): android.graphics.PointF {
            val insetX = width * 0.12f
            val insetY = height * 0.18f
            return when (this) {
                AimCalibrationMark.TOP_LEFT -> android.graphics.PointF(insetX, insetY)
                AimCalibrationMark.TOP_RIGHT -> android.graphics.PointF(width - insetX, insetY)
                AimCalibrationMark.BOTTOM_LEFT -> android.graphics.PointF(insetX, height - insetY)
                AimCalibrationMark.BOTTOM_RIGHT -> android.graphics.PointF(width - insetX, height - insetY)
            }
        }
    }

    private class HamburgerButtonView(context: android.content.Context) : View(context) {
        var open: Boolean = false
            set(value) {
                field = value
                invalidate()
            }
        private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 0, 0, 0)
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 255, 65)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PHOSPHOR
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(8f, 0f, 0f, PHOSPHOR)
        }

        init {
            isClickable = true
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = RectF(2f, 2f, width - 2f, height - 2f)
            canvas.drawRoundRect(rect, 8f, 8f, shellPaint)
            canvas.drawRoundRect(rect, 8f, 8f, strokePaint)
            val left = width * 0.28f
            val right = width * 0.72f
            val cy = height / 2f
            if (open) {
                canvas.drawLine(left, cy - 8f, right, cy + 8f, linePaint)
                canvas.drawLine(left, cy + 8f, right, cy - 8f, linePaint)
            } else {
                canvas.drawLine(left, cy - 10f, right, cy - 10f, linePaint)
                canvas.drawLine(left, cy, right, cy, linePaint)
                canvas.drawLine(left, cy + 10f, right, cy + 10f, linePaint)
            }
        }
    }

    private class HudIconView(context: android.content.Context, private val icon: HudIcon) : View(context) {
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PHOSPHOR
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
            setShadowLayer(8f, 0f, 0f, PHOSPHOR)
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            when (icon) {
                HudIcon.BACK -> {
                    canvas.drawLine(cx + 10f, cy - 12f, cx - 8f, cy, iconPaint)
                    canvas.drawLine(cx - 8f, cy, cx + 10f, cy + 12f, iconPaint)
                }
                HudIcon.HOME -> {
                    canvas.drawLine(cx - 16f, cy - 1f, cx, cy - 15f, iconPaint)
                    canvas.drawLine(cx, cy - 15f, cx + 16f, cy - 1f, iconPaint)
                    canvas.drawLine(cx - 12f, cy, cx - 12f, cy + 15f, iconPaint)
                    canvas.drawLine(cx + 12f, cy, cx + 12f, cy + 15f, iconPaint)
                    canvas.drawLine(cx - 12f, cy + 15f, cx + 12f, cy + 15f, iconPaint)
                    canvas.drawLine(cx - 4f, cy + 15f, cx - 4f, cy + 5f, iconPaint)
                    canvas.drawLine(cx + 4f, cy + 15f, cx + 4f, cy + 5f, iconPaint)
                }
                HudIcon.SELECT -> {
                    canvas.drawCircle(cx, cy, 14f, iconPaint)
                    canvas.drawLine(cx, cy - 8f, cx, cy + 8f, iconPaint)
                    canvas.drawLine(cx - 8f, cy, cx + 8f, cy, iconPaint)
                }
            }
        }
    }

    private class ZoomSliderView(
        context: android.content.Context,
        private val onFractionChanged: (Float) -> Unit,
    ) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 255, 65)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PHOSPHOR
            strokeWidth = 6f
            style = Paint.Style.STROKE
            setShadowLayer(8f, 0f, 0f, PHOSPHOR)
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PHOSPHOR
            style = Paint.Style.FILL
            setShadowLayer(10f, 0f, 0f, PHOSPHOR)
        }
        private var fraction = 0f

        init {
            contentDescription = "Camera zoom"
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setFraction(next: Float) {
            fraction = next.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val x = width / 2f
            val top = 18f
            val bottom = height - 18f
            val thumbY = bottom - ((bottom - top) * fraction)
            canvas.drawLine(x, top, x, bottom, trackPaint)
            canvas.drawLine(x, thumbY, x, bottom, fillPaint)
            canvas.drawCircle(x, thumbY, 11f, thumbPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateFromY(event.y)
                    return true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    updateFromY(event.y)
                    return true
                }
            }
            return true
        }

        private fun updateFromY(y: Float) {
            val top = 18f
            val bottom = height - 18f
            val next = ((bottom - y) / (bottom - top)).coerceIn(0f, 1f)
            fraction = next
            invalidate()
            onFractionChanged(next)
        }
    }

    companion object {
        private const val REQUEST_CAMERA = 2001
        private const val REQUEST_HOST_PERMISSIONS = 2002
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val PREVIEW_EDGE_CROP = 1.12f
        private val PHOSPHOR = Color.rgb(0, 255, 65)
        private val DIM_GREEN = Color.rgb(110, 190, 130)
        private val BACKGROUND = Color.rgb(0, 8, 4)
    }

    private enum class PendingMode(val displayName: String, val statusLabel: String) {
        LAN("LAN", "LAN compact v2"),
        BLUETOOTH("Bluetooth", "Bluetooth HID - haptics limited"),
    }
}
