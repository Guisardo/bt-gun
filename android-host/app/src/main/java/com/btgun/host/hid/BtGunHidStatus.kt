package com.btgun.host.hid

import com.btgun.host.haptics.HapticResult

data class BtGunHidStatus(
    val proxy: BtGunHidProxyState = BtGunHidProxyState.NOT_REQUESTED,
    val registration: BtGunHidRegistrationState = BtGunHidRegistrationState.NOT_REGISTERED,
    val hostConnection: BtGunHidHostConnectionState = BtGunHidHostConnectionState.NOT_CONNECTED,
    val pairingWindow: BtGunHidPairingWindowStatus = BtGunHidPairingWindowStatus(),
    val lastInputReport: BtGunHidInputReportStatus = BtGunHidInputReportStatus(),
    val lastOutputCallback: BtGunHidOutputCallbackStatus = BtGunHidOutputCallbackStatus(),
    val lastOutputValidation: BtGunHidOutputValidationStatus = BtGunHidOutputValidationStatus(),
    val lastHapticResult: HapticResult? = null,
    val unsupportedReason: String? = null,
)

enum class BtGunHidProxyState {
    NOT_REQUESTED,
    REQUESTING,
    AVAILABLE,
    UNAVAILABLE,
    CLOSED,
}

enum class BtGunHidRegistrationState {
    NOT_REGISTERED,
    REGISTERING,
    REGISTERED,
    FAILED,
}

enum class BtGunHidHostConnectionState {
    NOT_CONNECTED,
    CONNECTED,
    DISCONNECTED,
}

enum class BtGunHidInputSendResult {
    SENT,
    NO_PROXY,
    NOT_REGISTERED,
    NO_HOST,
    FAILED,
}

data class BtGunHidPairingWindowStatus(
    val open: Boolean = false,
    val durationSeconds: Int = 0,
    val detail: String = "not opened",
)

data class BtGunHidInputReportStatus(
    val result: BtGunHidInputSendResult? = null,
    val reportId: Int? = null,
    val payloadLength: Int = 0,
    val aimSource: String? = null,
    val stale: Boolean = false,
)

enum class BtGunHidOutputCallbackKind {
    NONE,
    GET_REPORT,
    SET_REPORT,
    INTERRUPT_DATA,
    VIRTUAL_CABLE_UNPLUG,
}

data class BtGunHidOutputCallbackStatus(
    val kind: BtGunHidOutputCallbackKind = BtGunHidOutputCallbackKind.NONE,
    val reportType: Int? = null,
    val reportId: Int? = null,
    val payloadLength: Int = 0,
)

enum class BtGunHidOutputValidationState {
    NONE,
    VALID,
    INVALID,
    UNSUPPORTED,
}

data class BtGunHidOutputValidationStatus(
    val state: BtGunHidOutputValidationState = BtGunHidOutputValidationState.NONE,
    val detail: String = "not seen",
)

object BtGunHidReportTypes {
    const val INPUT: Int = 1
    const val OUTPUT: Int = 2
    const val FEATURE: Int = 3
}

object BtGunHidConnectionStates {
    const val DISCONNECTED: Int = 0
    const val CONNECTED: Int = 2
}

object BtGunHidErrorResponses {
    const val INVALID_REPORT_ID: Byte = 0x02
    const val INVALID_PARAMETER: Byte = 0x04
    const val UNSUPPORTED_REQUEST: Byte = 0x03
}
