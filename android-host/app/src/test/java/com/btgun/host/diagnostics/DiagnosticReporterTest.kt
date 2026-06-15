package com.btgun.host.diagnostics

import com.btgun.host.HostSessionPhase
import com.btgun.host.HostSessionState
import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.ble.BleGunConnectionState
import com.btgun.host.haptics.HapticResult
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.hid.BtGunHidOutputValidationState
import com.btgun.host.hid.BtGunHidOutputValidationStatus
import com.btgun.host.hid.BtGunHidStatus
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.permissions.BluetoothPermissionModel
import com.btgun.host.permissions.CapabilityState
import com.btgun.host.permissions.CapabilityStatus
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.profile.BtGunProfile
import com.btgun.host.profile.MappedAimStatus
import com.btgun.host.profile.MappedControllerState
import com.btgun.host.transport.InputStreamLifecycleState
import com.btgun.host.ui.DashboardState

fun main() {
    reporterEmitsOneStatusPerAndroidDiagnosticDomain()
    reporterMapsBlockedAndUnsupportedStatesToFixedStatuses()
    dashboardRowsExposeConciseSanitizedDiagnostics()
}

private fun reporterEmitsOneStatusPerAndroidDiagnosticDomain() {
    val snapshot = DiagnosticReporter.snapshot(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(
            phase = HostSessionPhase.CONNECTED,
            packetStreamState = InputStreamLifecycleState.ACTIVE,
            mappedControllerState = mappedState(smoothingMode = "balanced"),
            hidGamepadStatus = BtGunHidStatus(
                lastHapticResult = HapticResult(
                    commandId = "hid-output-safe",
                    status = HapticResultStatus.STARTED,
                    detail = "phone pulse started",
                    observedElapsedNanos = 3_000L,
                ),
            ),
        ),
        bleConnectionState = BleGunConnectionState(phase = BleGunConnectionPhase.CONNECTED),
        lastMotionSample = MotionSample(
            provider = MotionProvider.GAME_ROTATION_VECTOR,
            providerName = "game_rotation_vector",
            sourceSensorElapsedNanos = 1_000L,
            yaw = 0f,
            pitch = 0f,
            roll = 0f,
        ),
        nowElapsedNanos = 5_000L,
    )

    expectEquals("one event per domain", AndroidDiagnosticDomain.entries.size, snapshot.events.size)
    expectEquals("ordered domain rows", AndroidDiagnosticDomain.entries, snapshot.events.map { it.domain })
    snapshot.events.forEach { event ->
        expectEquals("elapsed propagated ${event.domain}", 5_000L, event.tsElapsedNanos)
        expectTrue("reason family ${event.domain}", event.reasonCode.startsWith(event.domain.wireName + "."))
    }
}

private fun reporterMapsBlockedAndUnsupportedStatesToFixedStatuses() {
    val snapshot = DiagnosticReporter.snapshot(
        permissionGateState = permissionGateState(
            bluetoothHidRole = CapabilityStatus(
                CapabilityState.UNAVAILABLE,
                "HID_DEVICE proxy unavailable",
                "This Android build did not expose the Bluetooth HID_DEVICE profile proxy.",
            ),
        ),
        hostSessionState = HostSessionState(
            phase = HostSessionPhase.ERROR,
            lastError = "Bluetooth address AA:BB:CC:DD:EE:FF pairing_proof=secret",
            packetStreamState = InputStreamLifecycleState.STALE,
            profileValidationError = "Unsupported axis mapping",
            mappedControllerState = mappedState(smoothingMode = "low", adaptiveFallback = true),
            hidGamepadStatus = BtGunHidStatus(
                lastOutputValidation = BtGunHidOutputValidationStatus(
                    state = BtGunHidOutputValidationState.UNSUPPORTED,
                    detail = "macOS output callback unsupported",
                ),
                unsupportedReason = "macOS output callback not seen",
            ),
        ),
        bleConnectionState = BleGunConnectionState(
            phase = BleGunConnectionPhase.ERROR,
            lastError = "Bluetooth address AA:BB:CC:DD:EE:FF",
        ),
        lastMotionSample = null,
        nowElapsedNanos = 8_000L,
    )

    expectEquals("gun blocked", AndroidDiagnosticStatus.BLOCKED, snapshot.require(AndroidDiagnosticDomain.GUN_BLE).status)
    expectEquals("sensor blocked", AndroidDiagnosticStatus.BLOCKED, snapshot.require(AndroidDiagnosticDomain.SENSOR_MOTION).status)
    expectEquals("lan degraded", AndroidDiagnosticStatus.DEGRADED, snapshot.require(AndroidDiagnosticDomain.LAN_CONTROL_UDP).status)
    expectEquals("profile degraded", AndroidDiagnosticStatus.DEGRADED, snapshot.require(AndroidDiagnosticDomain.PROFILE_MAPPING).status)
    expectEquals("hid unsupported", AndroidDiagnosticStatus.UNSUPPORTED, snapshot.require(AndroidDiagnosticDomain.HID_BACKEND_HAPTICS).status)

    val encoded = snapshot.events.joinToString("\n") { it.toJsonBody().toString() }
    listOf("AA:BB:CC:DD:EE:FF", "pairing_proof=secret").forEach { forbidden ->
        expectFalse("redacted reporter $forbidden", encoded.contains(forbidden, ignoreCase = true))
    }
}

private fun dashboardRowsExposeConciseSanitizedDiagnostics() {
    val dashboard = DashboardState.from(
        permissionGateState = permissionGateState(),
        hostSessionState = HostSessionState(
            phase = HostSessionPhase.CONNECTED,
            foregroundActive = true,
            packetStreamState = InputStreamLifecycleState.ACTIVE,
            activeProfile = BtGunProfile.defaultVisualizer(),
            mappedControllerState = mappedState(smoothingMode = "balanced"),
        ),
        bleConnectionState = BleGunConnectionState(phase = BleGunConnectionPhase.CONNECTED),
        lastMotionSample = null,
        nowElapsedNanos = 9_000L,
    )

    expectEquals("dashboard diagnostic row count", AndroidDiagnosticDomain.entries.size, dashboard.diagnostics.rows.size)
    val byDomain = dashboard.diagnostics.rows.associateBy { it.domain }
    expectEquals("gun row status", "ok", byDomain.getValue("gun_ble").status)
    expectEquals("sensor row status", "blocked", byDomain.getValue("sensor_motion").status)
    expectEquals("lan row exists", "lan_control_udp", byDomain.getValue("lan_control_udp").domain)
    expectEquals("profile row reason", "profile_mapping.mapped", byDomain.getValue("profile_mapping").reasonCode)
    expectEquals("hid row exists", "hid_backend_haptics", byDomain.getValue("hid_backend_haptics").domain)

    dashboard.diagnostics.rows.forEach { row ->
        expectTrue("concise detail ${row.domain}", row.detail.length <= 96)
        listOf("AA:BB:CC:DD:EE:FF", "pairing_proof", "stream key", "Desktop profile").forEach { forbidden ->
            expectFalse("dashboard hides $forbidden", row.toString().contains(forbidden, ignoreCase = true))
        }
    }
}

private fun AndroidDiagnosticSnapshot.require(domain: AndroidDiagnosticDomain): AndroidDiagnosticEvent =
    events.firstOrNull { it.domain == domain } ?: throw AssertionError("missing domain $domain")

private fun permissionGateState(
    bluetoothHidRole: CapabilityStatus = CapabilityStatus(
        CapabilityState.AVAILABLE,
        "Bluetooth HID role available",
        "HID_DEVICE profile proxy can be requested.",
    ),
): PermissionGateState =
    PermissionGateState(
        bluetoothPermissionModel = BluetoothPermissionModel.ANDROID_12_NEARBY_DEVICES,
        bluetoothScan = CapabilityStatus(CapabilityState.AVAILABLE, "Bluetooth scan available", "Runtime permission granted."),
        bluetoothConnect = CapabilityStatus(CapabilityState.AVAILABLE, "Bluetooth connect available", "Runtime permission granted."),
        locationScanCompatibility = CapabilityStatus(CapabilityState.AVAILABLE, "Location compatibility not required", "Android 12+ scan uses Nearby Devices."),
        motionSensors = CapabilityStatus(CapabilityState.AVAILABLE, "Motion sensors available", "Capability detected."),
        vibration = CapabilityStatus(CapabilityState.AVAILABLE, "Phone vibration available", "Capability detected."),
        lanNetwork = CapabilityStatus(CapabilityState.AVAILABLE, "LAN network available", "Capability detected."),
        bluetoothHidRole = bluetoothHidRole,
    )

private fun mappedState(
    smoothingMode: String,
    adaptiveFallback: Boolean = false,
): MappedControllerState =
    MappedControllerState(
        aimAxisX = 0.1f,
        aimAxisY = -0.2f,
        aimStatus = MappedAimStatus(
            aimSource = "calibrated",
            providerName = "game_rotation_vector",
            smoothingMode = smoothingMode,
            estimatedFilterLagMillis = 24,
            adaptiveFallback = adaptiveFallback,
        ),
        pressedVirtualControls = setOf("trigger"),
        stickAxisX = 0f,
        stickAxisY = 0f,
        recenterPhysicalControl = "reload",
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
