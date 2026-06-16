package com.btgun.host.diagnostics

import com.btgun.host.HostSessionPhase
import com.btgun.host.HostSessionState
import com.btgun.host.ble.BleGunConnectionPhase
import com.btgun.host.ble.BleGunConnectionState
import com.btgun.host.haptics.HapticResultStatus
import com.btgun.host.hid.BtGunHidOutputValidationState
import com.btgun.host.model.MotionProvider
import com.btgun.host.model.MotionSample
import com.btgun.host.permissions.CapabilityState
import com.btgun.host.permissions.PermissionGateState
import com.btgun.host.transport.InputStreamLifecycleState
import com.btgun.host.util.AndroidLog

object DiagnosticReporter {
    private const val TAG = "BtGunDiagnostics"

    fun snapshot(
        permissionGateState: PermissionGateState,
        hostSessionState: HostSessionState,
        bleConnectionState: BleGunConnectionState = hostSessionState.lastBleConnectionState,
        lastMotionSample: MotionSample? = hostSessionState.lastMotionSample?.payload,
        nowElapsedNanos: Long = 0L,
        log: Boolean = false,
    ): AndroidDiagnosticSnapshot {
        val safeElapsed = nowElapsedNanos.coerceAtLeast(0L)
        val events = listOf(
            gunEvent(permissionGateState, hostSessionState, bleConnectionState, safeElapsed),
            sensorEvent(permissionGateState, lastMotionSample, safeElapsed),
            lanEvent(hostSessionState, safeElapsed),
            profileEvent(hostSessionState, safeElapsed),
            hidEvent(permissionGateState, hostSessionState, safeElapsed),
        )
        if (log) {
            events.forEach { event ->
                AndroidLog.i(TAG, "${event.domain.wireName}:${event.status.wireName}:${event.reasonCode}")
            }
        }
        return AndroidDiagnosticSnapshot(events)
    }

    private fun gunEvent(
        permissionGateState: PermissionGateState,
        hostSessionState: HostSessionState,
        bleConnectionState: BleGunConnectionState,
        tsElapsed: Long,
    ): AndroidDiagnosticEvent {
        val connectBlocked = permissionGateState.bluetoothConnect.state != CapabilityState.AVAILABLE
        return when {
            connectBlocked -> event(
                tsElapsed,
                AndroidDiagnosticDomain.GUN_BLE,
                AndroidDiagnosticStatus.BLOCKED,
                "gun_ble.permission_blocked",
                permissionGateState.bluetoothConnect.detail,
            )
            bleConnectionState.phase == BleGunConnectionPhase.CONNECTED ||
                hostSessionState.phase == HostSessionPhase.CONNECTED -> event(
                tsElapsed,
                AndroidDiagnosticDomain.GUN_BLE,
                AndroidDiagnosticStatus.OK,
                "gun_ble.connected",
                "fff3 notifications active",
            )
            bleConnectionState.phase == BleGunConnectionPhase.ERROR ||
                hostSessionState.phase == HostSessionPhase.ERROR -> event(
                tsElapsed,
                AndroidDiagnosticDomain.GUN_BLE,
                AndroidDiagnosticStatus.BLOCKED,
                "gun_ble.connection_error",
                hostSessionState.lastError ?: bleConnectionState.lastError ?: "connection error",
            )
            hostSessionState.phase in setOf(
                HostSessionPhase.STARTING,
                HostSessionPhase.SCANNING,
                HostSessionPhase.CONNECTING,
                HostSessionPhase.RECONNECTING,
            ) -> event(
                tsElapsed,
                AndroidDiagnosticDomain.GUN_BLE,
                AndroidDiagnosticStatus.DEGRADED,
                "gun_ble.connecting",
                hostSessionState.phase.wireName,
            )
            else -> event(
                tsElapsed,
                AndroidDiagnosticDomain.GUN_BLE,
                AndroidDiagnosticStatus.UNKNOWN,
                "gun_ble.not_started",
                "session not started",
            )
        }
    }

    private fun sensorEvent(
        permissionGateState: PermissionGateState,
        lastMotionSample: MotionSample?,
        tsElapsed: Long,
    ): AndroidDiagnosticEvent =
        when {
            permissionGateState.motionSensors.state != CapabilityState.AVAILABLE -> event(
                tsElapsed,
                AndroidDiagnosticDomain.SENSOR_MOTION,
                AndroidDiagnosticStatus.BLOCKED,
                "sensor_motion.permission_blocked",
                permissionGateState.motionSensors.detail,
            )
            lastMotionSample == null || lastMotionSample.provider == MotionProvider.UNAVAILABLE -> event(
                tsElapsed,
                AndroidDiagnosticDomain.SENSOR_MOTION,
                AndroidDiagnosticStatus.BLOCKED,
                "sensor_motion.unavailable",
                "no motion sample",
            )
            else -> event(
                tsElapsed,
                AndroidDiagnosticDomain.SENSOR_MOTION,
                AndroidDiagnosticStatus.OK,
                "sensor_motion.provider_active",
                lastMotionSample.providerName,
                context = mapOf("provider" to lastMotionSample.provider.wireName),
            )
        }

    private fun lanEvent(
        hostSessionState: HostSessionState,
        tsElapsed: Long,
    ): AndroidDiagnosticEvent =
        when (hostSessionState.packetStreamState) {
            InputStreamLifecycleState.ACTIVE -> event(
                tsElapsed,
                AndroidDiagnosticDomain.LAN_CONTROL_UDP,
                AndroidDiagnosticStatus.OK,
                "lan_control_udp.active",
                "input stream active",
            )
            InputStreamLifecycleState.GRACE -> event(
                tsElapsed,
                AndroidDiagnosticDomain.LAN_CONTROL_UDP,
                AndroidDiagnosticStatus.DEGRADED,
                "lan_control_udp.grace",
                "control disconnect grace",
            )
            InputStreamLifecycleState.STALE -> event(
                tsElapsed,
                AndroidDiagnosticDomain.LAN_CONTROL_UDP,
                AndroidDiagnosticStatus.DEGRADED,
                "lan_control_udp.stale",
                "input stream stale",
            )
            InputStreamLifecycleState.STOPPED -> event(
                tsElapsed,
                AndroidDiagnosticDomain.LAN_CONTROL_UDP,
                AndroidDiagnosticStatus.UNKNOWN,
                "lan_control_udp.stopped",
                "input stream stopped",
            )
        }

    private fun profileEvent(
        hostSessionState: HostSessionState,
        tsElapsed: Long,
    ): AndroidDiagnosticEvent =
        when {
            hostSessionState.profileValidationError != null -> event(
                tsElapsed,
                AndroidDiagnosticDomain.PROFILE_MAPPING,
                AndroidDiagnosticStatus.DEGRADED,
                "profile_mapping.validation_warning",
                hostSessionState.profileValidationError,
            )
            hostSessionState.mappedControllerState.aimStatus.adaptiveFallback -> event(
                tsElapsed,
                AndroidDiagnosticDomain.PROFILE_MAPPING,
                AndroidDiagnosticStatus.DEGRADED,
                "profile_mapping.adaptive_fallback",
                "adaptive smoothing fallback",
            )
            else -> event(
                tsElapsed,
                AndroidDiagnosticDomain.PROFILE_MAPPING,
                AndroidDiagnosticStatus.OK,
                "profile_mapping.mapped",
                "profile mapped",
                context = mapOf(
                    "profile_id" to hostSessionState.activeProfile.profileId,
                    "aim_source" to hostSessionState.mappedControllerState.aimStatus.aimSource,
                ),
            )
        }

    private fun hidEvent(
        permissionGateState: PermissionGateState,
        hostSessionState: HostSessionState,
        tsElapsed: Long,
    ): AndroidDiagnosticEvent {
        val hid = hostSessionState.hidGamepadStatus
        return when {
            hid.lastOutputValidation.state == BtGunHidOutputValidationState.UNSUPPORTED -> event(
                tsElapsed,
                AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
                AndroidDiagnosticStatus.UNSUPPORTED,
                "hid_backend_haptics.output_unsupported",
                hid.lastOutputValidation.detail,
            )
            hid.lastHapticResult?.status == HapticResultStatus.UNSUPPORTED -> event(
                tsElapsed,
                AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
                AndroidDiagnosticStatus.UNSUPPORTED,
                "hid_backend_haptics.output_unsupported",
                hid.lastHapticResult.detail,
            )
            permissionGateState.bluetoothHidRole.state == CapabilityState.UNAVAILABLE -> event(
                tsElapsed,
                AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
                AndroidDiagnosticStatus.UNSUPPORTED,
                "hid_backend_haptics.role_unsupported",
                permissionGateState.bluetoothHidRole.detail,
            )
            permissionGateState.bluetoothHidRole.state == CapabilityState.BLOCKED -> event(
                tsElapsed,
                AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
                AndroidDiagnosticStatus.BLOCKED,
                "hid_backend_haptics.role_blocked",
                permissionGateState.bluetoothHidRole.detail,
            )
            hid.lastHapticResult?.status == HapticResultStatus.STARTED -> event(
                tsElapsed,
                AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
                AndroidDiagnosticStatus.OK,
                "hid_backend_haptics.phone_started",
                hid.lastHapticResult.detail,
            )
            else -> event(
                tsElapsed,
                AndroidDiagnosticDomain.HID_BACKEND_HAPTICS,
                AndroidDiagnosticStatus.UNKNOWN,
                "hid_backend_haptics.not_seen",
                hid.unsupportedReason ?: "no output callback observed",
            )
        }
    }

    private fun event(
        tsElapsed: Long,
        domain: AndroidDiagnosticDomain,
        status: AndroidDiagnosticStatus,
        reasonCode: String,
        detail: String,
        sessionRefs: Map<String, String> = emptyMap(),
        context: Map<String, String> = emptyMap(),
    ): AndroidDiagnosticEvent =
        AndroidDiagnosticEvent(
            tsElapsedNanos = tsElapsed,
            domain = domain,
            status = status,
            reasonCode = reasonCode,
            detail = DiagnosticSanitizer.redact(detail),
            sessionRefs = sessionRefs,
            context = context,
        )
}
