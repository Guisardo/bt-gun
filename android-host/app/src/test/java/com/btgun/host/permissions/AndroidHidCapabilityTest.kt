package com.btgun.host.permissions

fun main() {
    bluetoothOffBlocksHidRole()
    missingConnectPermissionBlocksHidRoleOnAndroid12()
    notYetProbedRequiresExplicitStartAction()
    proxyUnavailableHasDistinctBlockedRow()
    registrationFailureHasDistinctBlockedRow()
    noHostConnectedHasDistinctBlockedRow()
    hostDisconnectedHasDistinctBlockedRow()
    connectedHostIsAvailable()
}

private fun bluetoothOffBlocksHidRole() {
    val status = AndroidHidCapability.evaluate(
        input(
            bluetoothEnabled = false,
            connectPermissionGranted = true,
            profile = AndroidHidProfileStatus.NOT_PROBED,
        ),
    )

    expectState("bluetooth off role", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("bluetooth off label", "Bluetooth HID role blocked", status.hidRole.label)
    expectEquals("bluetooth off detail", "Bluetooth is off.", status.hidRole.detail)
    expectFalse("bluetooth off cannot start hid", status.canStartBluetoothGamepad)
}

private fun missingConnectPermissionBlocksHidRoleOnAndroid12() {
    val status = AndroidHidCapability.evaluate(
        input(
            sdkInt = 35,
            bluetoothEnabled = true,
            connectPermissionGranted = false,
        ),
    )

    expectState("missing connect permission", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("permission label", "Bluetooth HID permission blocked", status.hidRole.label)
    expectEquals(
        "permission detail",
        "Grant Nearby Devices connect permission before starting Bluetooth gamepad.",
        status.hidRole.detail,
    )
    expectFalse("permission cannot start hid", status.canStartBluetoothGamepad)
}

private fun notYetProbedRequiresExplicitStartAction() {
    val status = AndroidHidCapability.evaluate(
        input(
            profile = AndroidHidProfileStatus.NOT_PROBED,
            registration = AndroidHidRegistrationStatus.NOT_REQUESTED,
            host = AndroidHidHostConnectionStatus.NOT_CONNECTED,
        ),
    )

    expectState("not probed role", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("not probed label", "Bluetooth HID role not started", status.hidRole.label)
    expectEquals(
        "not probed detail",
        "Tap Start Bluetooth gamepad to probe HID_DEVICE support.",
        status.hidRole.detail,
    )
    expectTrue("not probed may start hid", status.canStartBluetoothGamepad)
}

private fun proxyUnavailableHasDistinctBlockedRow() {
    val status = AndroidHidCapability.evaluate(input(profile = AndroidHidProfileStatus.UNAVAILABLE))

    expectState("proxy unavailable role", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("proxy unavailable label", "HID_DEVICE proxy unavailable", status.hidRole.label)
    expectEquals(
        "proxy unavailable detail",
        "This Android build did not expose the Bluetooth HID_DEVICE profile proxy.",
        status.hidRole.detail,
    )
    expectFalse("proxy unavailable cannot start hid", status.canStartBluetoothGamepad)
}

private fun registrationFailureHasDistinctBlockedRow() {
    val status = AndroidHidCapability.evaluate(
        input(
            profile = AndroidHidProfileStatus.AVAILABLE,
            registration = AndroidHidRegistrationStatus.FAILED,
        ),
    )

    expectState("registration failed role", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("registration failed label", "Bluetooth HID registration failed", status.hidRole.label)
    expectEquals(
        "registration failed detail",
        "Android rejected or lost HID gamepad app registration.",
        status.hidRole.detail,
    )
}

private fun noHostConnectedHasDistinctBlockedRow() {
    val status = AndroidHidCapability.evaluate(
        input(
            profile = AndroidHidProfileStatus.AVAILABLE,
            registration = AndroidHidRegistrationStatus.REGISTERED,
            host = AndroidHidHostConnectionStatus.NOT_CONNECTED,
        ),
    )

    expectState("no host role", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("no host label", "Bluetooth HID host not connected", status.hidRole.label)
    expectEquals("no host detail", "Pair macOS while Bluetooth gamepad mode is active.", status.hidRole.detail)
}

private fun hostDisconnectedHasDistinctBlockedRow() {
    val status = AndroidHidCapability.evaluate(
        input(
            profile = AndroidHidProfileStatus.AVAILABLE,
            registration = AndroidHidRegistrationStatus.REGISTERED,
            host = AndroidHidHostConnectionStatus.DISCONNECTED,
        ),
    )

    expectState("host disconnected role", CapabilityState.BLOCKED, status.hidRole.state)
    expectEquals("host disconnected label", "Bluetooth HID host disconnected", status.hidRole.label)
    expectEquals(
        "host disconnected detail",
        "Host disconnected; restart pairing or reconnect from macOS Bluetooth.",
        status.hidRole.detail,
    )
}

private fun connectedHostIsAvailable() {
    val status = AndroidHidCapability.evaluate(
        input(
            profile = AndroidHidProfileStatus.AVAILABLE,
            registration = AndroidHidRegistrationStatus.REGISTERED,
            host = AndroidHidHostConnectionStatus.CONNECTED,
        ),
    )

    expectState("connected role", CapabilityState.AVAILABLE, status.hidRole.state)
    expectEquals("connected label", "Bluetooth HID host connected", status.hidRole.label)
    expectEquals("connected detail", "macOS host is connected to Android Bluetooth gamepad mode.", status.hidRole.detail)
    expectTrue("connected host", status.hostConnected)
}

private fun input(
    sdkInt: Int = 35,
    bluetoothEnabled: Boolean = true,
    connectPermissionGranted: Boolean = true,
    profile: AndroidHidProfileStatus = AndroidHidProfileStatus.NOT_PROBED,
    registration: AndroidHidRegistrationStatus = AndroidHidRegistrationStatus.NOT_REQUESTED,
    host: AndroidHidHostConnectionStatus = AndroidHidHostConnectionStatus.NOT_CONNECTED,
): AndroidHidCapabilityInput =
    AndroidHidCapabilityInput(
        sdkInt = sdkInt,
        bluetoothEnabled = bluetoothEnabled,
        bluetoothConnectPermissionGranted = connectPermissionGranted,
        profileStatus = profile,
        registrationStatus = registration,
        hostConnectionStatus = host,
    )

private fun expectState(label: String, expected: CapabilityState, actual: CapabilityState) {
    expectEquals(label, expected, actual)
}

private fun expectTrue(label: String, actual: Boolean) {
    expectEquals(label, true, actual)
}

private fun expectFalse(label: String, actual: Boolean) {
    expectEquals(label, false, actual)
}

private fun expectEquals(label: String, expected: Any, actual: Any) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
