package com.btgun.host.ble

import android.content.Context
import com.btgun.host.ReconnectPolicy
import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.StatusEvent

class IpegaBleGunAdapter(
    private val context: Context,
    private val listener: Listener,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
) {
    interface Listener {
        fun onConnectionState(state: BleGunConnectionState)
        fun onGunEvent(envelope: LiveEnvelope<GunEvent>)
        fun onStatus(envelope: LiveEnvelope<StatusEvent>)
    }

    fun startSession() {
        listener.onConnectionState(BleGunConnectionState(phase = BleGunConnectionPhase.SCANNING))
    }

    fun stopSession() {
        listener.onConnectionState(BleGunConnectionState(phase = BleGunConnectionPhase.STOPPED))
    }

    companion object {
        const val ARGUN_DEVICE_NAME: String = "ARGunGame"
        const val BLE_SERVICE_UUID: String = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val FFF3_CHARACTERISTIC_UUID: String = "0000fff3-0000-1000-8000-00805f9b34fb"
        const val CCCD_UUID: String = "00002902-0000-1000-8000-00805f9b34fb"
        private const val ENABLE_NOTIFICATION_VALUE_HEX: String = "0100"

        fun scanFilterSpec(): BleScanFilterSpec =
            BleScanFilterSpec(
                deviceName = ARGUN_DEVICE_NAME,
                serviceUuid = BLE_SERVICE_UUID,
                scanMode = "low_latency",
            )

        fun notificationSubscriptionSpec(): NotificationSubscriptionSpec =
            NotificationSubscriptionSpec(
                serviceUuid = BLE_SERVICE_UUID,
                characteristicUuid = FFF3_CHARACTERISTIC_UUID,
                descriptorUuid = CCCD_UUID,
                enableNotificationValueHex = ENABLE_NOTIFICATION_VALUE_HEX,
            )

        fun matchesTargetAdvertisement(deviceName: String?, advertisedServiceUuids: Set<String>): Boolean {
            val serviceMatch = advertisedServiceUuids.any { uuid ->
                uuid.equals(BLE_SERVICE_UUID, ignoreCase = true)
            }
            val nameMatch = deviceName.equals(ARGUN_DEVICE_NAME, ignoreCase = true)
            return serviceMatch && nameMatch
        }
    }
}

data class BleScanFilterSpec(
    val deviceName: String,
    val serviceUuid: String,
    val scanMode: String,
)

data class NotificationSubscriptionSpec(
    val serviceUuid: String,
    val characteristicUuid: String,
    val descriptorUuid: String,
    val enableNotificationValueHex: String,
)

data class BleGunConnectionState(
    val phase: BleGunConnectionPhase = BleGunConnectionPhase.IDLE,
    val reconnectAttempt: Int = 0,
    val lastError: String? = null,
)

enum class BleGunConnectionPhase {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    STOPPED,
    ERROR,
}
