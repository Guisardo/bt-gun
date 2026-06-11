package com.btgun.host.permissions

data class PermissionGateInput @JvmOverloads constructor(
    val sdkInt: Int,
    val grantedPermissions: Set<String>,
    val bluetoothEnabled: Boolean,
    val locationServiceAvailable: Boolean,
    val hasGyroscope: Boolean,
    val hasRotationVector: Boolean,
    val hasGameRotationVector: Boolean,
    val hasAccelerometer: Boolean,
    val hasGravity: Boolean,
    val hasVibrator: Boolean,
    val hasNetwork: Boolean,
    val bluetoothHidProfileStatus: AndroidHidProfileStatus = AndroidHidProfileStatus.NOT_PROBED,
    val bluetoothHidRegistrationStatus: AndroidHidRegistrationStatus = AndroidHidRegistrationStatus.NOT_REQUESTED,
    val bluetoothHidHostConnectionStatus: AndroidHidHostConnectionStatus = AndroidHidHostConnectionStatus.NOT_CONNECTED,
)

data class PermissionGateState(
    val bluetoothPermissionModel: BluetoothPermissionModel,
    val bluetoothScan: CapabilityStatus,
    val bluetoothConnect: CapabilityStatus,
    val bluetoothAdvertise: CapabilityStatus = CapabilityStatus(
        CapabilityState.AVAILABLE,
        "Bluetooth advertise available",
        "Runtime permission granted.",
    ),
    val locationScanCompatibility: CapabilityStatus,
    val motionSensors: CapabilityStatus,
    val vibration: CapabilityStatus,
    val lanNetwork: CapabilityStatus,
    val bluetoothHidRole: CapabilityStatus = AndroidHidCapability.notStartedStatus,
) {
    val canStartSession: Boolean =
        bluetoothScan.state == CapabilityState.AVAILABLE &&
            bluetoothConnect.state == CapabilityState.AVAILABLE &&
            motionSensors.state == CapabilityState.AVAILABLE
}

data class CapabilityStatus(
    val state: CapabilityState,
    val label: String,
    val detail: String,
)

enum class CapabilityState {
    AVAILABLE,
    BLOCKED,
    UNAVAILABLE,
}

enum class BluetoothPermissionModel {
    ANDROID_12_NEARBY_DEVICES,
    LEGACY_LOCATION_SCAN,
}

object PermissionGate {
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"

    @JvmStatic
    fun evaluate(input: PermissionGateInput): PermissionGateState {
        val android12OrNewer = input.sdkInt >= 31
        val model = if (android12OrNewer) {
            BluetoothPermissionModel.ANDROID_12_NEARBY_DEVICES
        } else {
            BluetoothPermissionModel.LEGACY_LOCATION_SCAN
        }

        val locationCompatible = locationScanCompatibility(input, android12OrNewer)

        val hidCapability = AndroidHidCapability.evaluate(
            AndroidHidCapabilityInput(
                sdkInt = input.sdkInt,
                bluetoothEnabled = input.bluetoothEnabled,
                bluetoothConnectPermissionGranted = !android12OrNewer ||
                    input.grantedPermissions.contains(BLUETOOTH_CONNECT),
                bluetoothAdvertisePermissionGranted = !android12OrNewer ||
                    input.grantedPermissions.contains(BLUETOOTH_ADVERTISE),
                profileStatus = input.bluetoothHidProfileStatus,
                registrationStatus = input.bluetoothHidRegistrationStatus,
                hostConnectionStatus = input.bluetoothHidHostConnectionStatus,
            ),
        )

        return PermissionGateState(
            bluetoothPermissionModel = model,
            bluetoothScan = bluetoothScan(input, android12OrNewer, locationCompatible),
            bluetoothConnect = bluetoothConnect(input, android12OrNewer),
            bluetoothAdvertise = bluetoothAdvertise(input, android12OrNewer),
            locationScanCompatibility = locationCompatible,
            motionSensors = motionSensors(input),
            vibration = hardwareCapability(
                available = input.hasVibrator,
                availableLabel = "Phone vibration available",
                unavailableLabel = "Phone vibration unavailable",
            ),
            lanNetwork = hardwareCapability(
                available = input.hasNetwork,
                availableLabel = "LAN network available",
                unavailableLabel = "LAN network unavailable",
            ),
            bluetoothHidRole = hidCapability.hidRole,
        )
    }

    private fun bluetoothScan(
        input: PermissionGateInput,
        android12OrNewer: Boolean,
        locationCompatible: CapabilityStatus,
    ): CapabilityStatus {
        if (!input.bluetoothEnabled) {
            return blocked("Bluetooth scan blocked", "Bluetooth is off.")
        }

        return if (android12OrNewer) {
            runtimePermission(
                granted = input.grantedPermissions.contains(BLUETOOTH_SCAN),
                availableLabel = "Bluetooth scan available",
                blockedLabel = "Bluetooth scan permission blocked",
                blockedDetail = "Grant Nearby Devices scan permission.",
            )
        } else if (locationCompatible.state == CapabilityState.AVAILABLE) {
            available("Bluetooth scan available", "Legacy scan location compatibility is satisfied.")
        } else {
            locationCompatible.copy(label = "Bluetooth scan blocked")
        }
    }

    private fun bluetoothConnect(input: PermissionGateInput, android12OrNewer: Boolean): CapabilityStatus {
        if (!input.bluetoothEnabled) {
            return blocked("Bluetooth connect blocked", "Bluetooth is off.")
        }

        return if (android12OrNewer) {
            runtimePermission(
                granted = input.grantedPermissions.contains(BLUETOOTH_CONNECT),
                availableLabel = "Bluetooth connect available",
                blockedLabel = "Bluetooth connect permission blocked",
                blockedDetail = "Grant Nearby Devices connect permission.",
            )
        } else {
            available("Bluetooth connect available", "Legacy Bluetooth connect has no runtime permission.")
        }
    }

    private fun bluetoothAdvertise(input: PermissionGateInput, android12OrNewer: Boolean): CapabilityStatus {
        if (!input.bluetoothEnabled) {
            return blocked("Bluetooth advertise blocked", "Bluetooth is off.")
        }

        return if (android12OrNewer) {
            runtimePermission(
                granted = input.grantedPermissions.contains(BLUETOOTH_ADVERTISE),
                availableLabel = "Bluetooth advertise available",
                blockedLabel = "Bluetooth advertise permission blocked",
                blockedDetail = "Grant Nearby Devices advertise permission before opening HID pairing.",
            )
        } else {
            available("Bluetooth advertise available", "Legacy Bluetooth discoverability has no runtime permission.")
        }
    }

    private fun locationScanCompatibility(input: PermissionGateInput, android12OrNewer: Boolean): CapabilityStatus {
        if (android12OrNewer) {
            return available("Location compatibility not required", "Android 12+ scan uses Nearby Devices.")
        }

        if (!input.locationServiceAvailable) {
            return blocked("Location service blocked", "Enable location services for legacy BLE scan.")
        }

        val hasLocationPermission = input.grantedPermissions.contains(ACCESS_FINE_LOCATION) ||
            input.grantedPermissions.contains(ACCESS_COARSE_LOCATION)

        return runtimePermission(
            granted = hasLocationPermission,
            availableLabel = "Legacy scan location available",
            blockedLabel = "Legacy scan location blocked",
            blockedDetail = "Grant fine or coarse location for legacy BLE scan.",
        )
    }

    private fun motionSensors(input: PermissionGateInput): CapabilityStatus {
        val hasRotationProvider = input.hasGameRotationVector || input.hasRotationVector
        val hasGyroFusion = input.hasGyroscope && (input.hasGravity || input.hasAccelerometer)
        val hasTiltFallback = input.hasGravity || input.hasAccelerometer
        return hardwareCapability(
            available = hasRotationProvider || hasGyroFusion || hasTiltFallback,
            availableLabel = "Motion sensors available",
            unavailableLabel = "Motion sensors unavailable",
        )
    }

    private fun runtimePermission(
        granted: Boolean,
        availableLabel: String,
        blockedLabel: String,
        blockedDetail: String,
    ): CapabilityStatus = if (granted) {
        available(availableLabel, "Runtime permission granted.")
    } else {
        blocked(blockedLabel, blockedDetail)
    }

    private fun hardwareCapability(
        available: Boolean,
        availableLabel: String,
        unavailableLabel: String,
    ): CapabilityStatus = if (available) {
        available(availableLabel, "Capability detected.")
    } else {
        unavailable(unavailableLabel, "Capability not detected on this device.")
    }

    private fun available(label: String, detail: String): CapabilityStatus =
        CapabilityStatus(CapabilityState.AVAILABLE, label, detail)

    private fun blocked(label: String, detail: String): CapabilityStatus =
        CapabilityStatus(CapabilityState.BLOCKED, label, detail)

    private fun unavailable(label: String, detail: String): CapabilityStatus =
        CapabilityStatus(CapabilityState.UNAVAILABLE, label, detail)
}
