package com.btgun.host.ble

import com.btgun.host.HostSessionPhase
import com.btgun.host.HostSessionService
import com.btgun.host.ReconnectPolicy

fun main() {
    foregroundNotificationCopyIsExact()
    scanFilterTargetsArgunGameAndFff0()
    connectionTimeoutsAreBounded()
    notificationSetupWritesFff3Cccd()
    disconnectWhileActiveSchedulesBoundedReconnectWithLastError()
    disconnectWhileStoppedDoesNotReconnect()
    gattQueueRunsOneOperationAtATime()
}

private fun foregroundNotificationCopyIsExact() {
    expectEquals("notification channel", "bt_gun_host_session", HostSessionService.NOTIFICATION_CHANNEL_ID)
    expectEquals("notification title", "BT Gun Host", HostSessionService.NOTIFICATION_TITLE)
    expectEquals(
        "notification text",
        "BT Gun Host running - live input active",
        HostSessionService.NOTIFICATION_TEXT,
    )
}

private fun scanFilterTargetsArgunGameAndFff0() {
    val spec = IpegaBleGunAdapter.scanFilterSpec()

    expectEquals("device name", "ARGunGame", spec.deviceName)
    expectEquals("service uuid", "0000fff0-0000-1000-8000-00805f9b34fb", spec.serviceUuid)
    expectEquals("scan mode", "low_latency", spec.scanMode)
    expectTrue(
        "scan match accepts ARGunGame/fff0",
        IpegaBleGunAdapter.matchesTargetAdvertisement(
            deviceName = "ARGunGame",
            advertisedServiceUuids = setOf("0000fff0-0000-1000-8000-00805f9b34fb"),
        ),
    )
    expectTrue(
        "scan match accepts ARGunGame name only",
        IpegaBleGunAdapter.matchesTargetAdvertisement(
            deviceName = "ARGunGame",
            advertisedServiceUuids = emptySet(),
        ),
    )
    expectTrue(
        "scan match accepts fff0 service only",
        IpegaBleGunAdapter.matchesTargetAdvertisement(
            deviceName = null,
            advertisedServiceUuids = setOf("0000fff0-0000-1000-8000-00805f9b34fb"),
        ),
    )
    expectFalse(
        "scan match rejects non-target",
        IpegaBleGunAdapter.matchesTargetAdvertisement(
            deviceName = "Other",
            advertisedServiceUuids = setOf("0000180f-0000-1000-8000-00805f9b34fb"),
        ),
    )
}

private fun connectionTimeoutsAreBounded() {
    expectEquals("scan timeout", 12_000L, IpegaBleGunAdapter.BLE_SCAN_TIMEOUT_MILLIS)
    expectEquals("connect timeout", 8_000L, IpegaBleGunAdapter.GATT_CONNECT_TIMEOUT_MILLIS)
    expectEquals("discovery timeout", 8_000L, IpegaBleGunAdapter.GATT_DISCOVERY_TIMEOUT_MILLIS)
    expectEquals("notification timeout", 8_000L, IpegaBleGunAdapter.NOTIFICATION_SETUP_TIMEOUT_MILLIS)
}

private fun notificationSetupWritesFff3Cccd() {
    val spec = IpegaBleGunAdapter.notificationSubscriptionSpec()

    expectEquals("notify service", "0000fff0-0000-1000-8000-00805f9b34fb", spec.serviceUuid)
    expectEquals("notify characteristic", "0000fff3-0000-1000-8000-00805f9b34fb", spec.characteristicUuid)
    expectEquals("notify cccd", "00002902-0000-1000-8000-00805f9b34fb", spec.descriptorUuid)
    expectEquals("notify descriptor value", "0100", spec.enableNotificationValueHex)
}

private fun disconnectWhileActiveSchedulesBoundedReconnectWithLastError() {
    val policy = ReconnectPolicy(maxAttempts = 3, initialDelayMillis = 250L, maxDelayMillis = 1_000L)

    val first = policy.onDisconnect(
        activeSession = true,
        completedAttempts = 0,
        error = "gatt disconnected status=133",
    )
    expectTrue("first reconnect", first.shouldReconnect)
    expectEquals("first attempt", 1, first.nextAttempt)
    expectEquals("first delay", 250L, first.delayMillis)
    expectEquals("first visible state", HostSessionPhase.RECONNECTING, first.state.phase)
    expectEquals("first visible error", "gatt disconnected status=133", first.state.lastError)

    val capped = policy.onDisconnect(
        activeSession = true,
        completedAttempts = 3,
        error = "gatt disconnected status=8",
    )
    expectFalse("bounded reconnect stops", capped.shouldReconnect)
    expectEquals("bounded state", HostSessionPhase.ERROR, capped.state.phase)
    expectEquals("bounded visible error", "gatt disconnected status=8", capped.state.lastError)
}

private fun disconnectWhileStoppedDoesNotReconnect() {
    val stopped = ReconnectPolicy(maxAttempts = 3).onDisconnect(
        activeSession = false,
        completedAttempts = 0,
        error = "explicit stop",
    )

    expectFalse("stopped no reconnect", stopped.shouldReconnect)
    expectEquals("stopped phase", HostSessionPhase.STOPPED, stopped.state.phase)
    expectEquals("stopped error visible", "explicit stop", stopped.state.lastError)
}

private fun gattQueueRunsOneOperationAtATime() {
    val started = mutableListOf<String>()
    val events = mutableListOf<String>()
    val queue = GattOperationQueue { event ->
        events.add("${event.state}:${event.operationName ?: event.callbackName}")
    }

    queue.enqueue("read:fff3") {
        started.add("read:fff3")
        true
    }
    queue.enqueue("write_cccd:fff3") {
        started.add("write_cccd:fff3")
        true
    }

    expectEquals("only first operation started", listOf("read:fff3"), started)
    expectEquals("in-flight operation", "read:fff3", queue.inFlightOperationName)

    queue.completeCharacteristicRead(status = 0)
    expectEquals("second starts after completion", listOf("read:fff3", "write_cccd:fff3"), started)
    expectEquals("second in-flight", "write_cccd:fff3", queue.inFlightOperationName)

    queue.completeDescriptorWrite(status = 0)
    expectEquals("queue idle", null, queue.inFlightOperationName)
    expectEquals(
        "queue events",
        listOf(
            "started:read:fff3",
            "completed:characteristic_read",
            "started:write_cccd:fff3",
            "completed:descriptor_write",
        ),
        events,
    )
}

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
    if (condition) {
        throw AssertionError(label)
    }
}
