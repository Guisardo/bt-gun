package com.btgun.host.permissions

data class AndroidHidCapabilityInput(
    val sdkInt: Int,
    val bluetoothEnabled: Boolean,
    val bluetoothConnectPermissionGranted: Boolean,
    val bluetoothAdvertisePermissionGranted: Boolean,
    val profileStatus: AndroidHidProfileStatus,
    val registrationStatus: AndroidHidRegistrationStatus,
    val hostConnectionStatus: AndroidHidHostConnectionStatus,
)

data class AndroidHidCapabilityState(
    val hidRole: CapabilityStatus,
    val canStartBluetoothGamepad: Boolean,
    val hostConnected: Boolean,
)

enum class AndroidHidProfileStatus {
    NOT_PROBED,
    AVAILABLE,
    UNAVAILABLE,
}

enum class AndroidHidRegistrationStatus {
    NOT_REQUESTED,
    REGISTERED,
    FAILED,
}

enum class AndroidHidHostConnectionStatus {
    NOT_CONNECTED,
    CONNECTED,
    DISCONNECTED,
}

object AndroidHidCapability {
    val notStartedStatus: CapabilityStatus =
        blocked(
            "Bluetooth HID role not started",
            "Tap Start Bluetooth gamepad to probe HID_DEVICE support.",
        )

    fun evaluate(input: AndroidHidCapabilityInput): AndroidHidCapabilityState {
        if (!input.bluetoothEnabled) {
            return blockedState("Bluetooth HID role blocked", "Bluetooth is off.", canStart = false)
        }

        if (input.sdkInt >= 31 && !input.bluetoothConnectPermissionGranted) {
            return blockedState(
                "Bluetooth HID permission blocked",
                "Grant Nearby Devices connect permission before starting Bluetooth gamepad.",
                canStart = false,
            )
        }

        if (input.sdkInt >= 31 && !input.bluetoothAdvertisePermissionGranted) {
            return blockedState(
                "Bluetooth HID advertise permission blocked",
                "Grant Nearby Devices advertise permission before opening the HID pairing window.",
                canStart = false,
            )
        }

        if (input.profileStatus == AndroidHidProfileStatus.NOT_PROBED) {
            return AndroidHidCapabilityState(
                hidRole = notStartedStatus,
                canStartBluetoothGamepad = true,
                hostConnected = false,
            )
        }

        if (input.profileStatus == AndroidHidProfileStatus.UNAVAILABLE) {
            return blockedState(
                "HID_DEVICE proxy unavailable",
                "This Android build did not expose the Bluetooth HID_DEVICE profile proxy.",
                canStart = false,
            )
        }

        if (input.registrationStatus == AndroidHidRegistrationStatus.FAILED) {
            return blockedState(
                "Bluetooth HID registration failed",
                "Android rejected or lost HID gamepad app registration.",
                canStart = false,
            )
        }

        if (input.registrationStatus == AndroidHidRegistrationStatus.NOT_REQUESTED) {
            return AndroidHidCapabilityState(
                hidRole = notStartedStatus,
                canStartBluetoothGamepad = true,
                hostConnected = false,
            )
        }

        return when (input.hostConnectionStatus) {
            AndroidHidHostConnectionStatus.NOT_CONNECTED -> blockedState(
                "Bluetooth HID host not connected",
                "Pair macOS while Bluetooth gamepad mode is active.",
                canStart = true,
            )

            AndroidHidHostConnectionStatus.DISCONNECTED -> blockedState(
                "Bluetooth HID host disconnected",
                "Host disconnected; restart pairing or reconnect from macOS Bluetooth.",
                canStart = true,
            )

            AndroidHidHostConnectionStatus.CONNECTED -> AndroidHidCapabilityState(
                hidRole = CapabilityStatus(
                    state = CapabilityState.AVAILABLE,
                    label = "Bluetooth HID host connected",
                    detail = "macOS host is connected to Android Bluetooth gamepad mode.",
                ),
                canStartBluetoothGamepad = true,
                hostConnected = true,
            )
        }
    }

    private fun blockedState(label: String, detail: String, canStart: Boolean): AndroidHidCapabilityState =
        AndroidHidCapabilityState(
            hidRole = blocked(label, detail),
            canStartBluetoothGamepad = canStart,
            hostConnected = false,
        )

    private fun blocked(label: String, detail: String): CapabilityStatus =
        CapabilityStatus(CapabilityState.BLOCKED, label, detail)
}
