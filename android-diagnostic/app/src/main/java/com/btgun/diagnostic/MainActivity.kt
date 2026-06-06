package com.btgun.diagnostic

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

class MainActivity : Activity() {
    private val schema = "btgun.android_diagnostic.v1"
    private val sessionId = "manual-phase1"
    private lateinit var output: TextView

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            logReport(
                "ble_scan",
                "callback_type" to callbackType,
                "device_name" to safeDeviceName(result.device),
                "device_address" to safeDeviceAddress(result.device),
                "rssi" to result.rssi,
                "service_uuids" to (result.scanRecord?.serviceUuids?.joinToString("|") ?: "")
            )
        }

        override fun onScanFailed(errorCode: Int) {
            logReport("ble_scan", "state" to "failed", "error_code" to errorCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button("Permission State") { reportPermissionState() })
            addView(button("InputDevice Scan") { scanInputDevices() })
            addView(button("BLE Scan Hook") { startBleScan() })
            addView(button("BLE Characteristic Hook") { recordBleCharacteristicTargets() })
            addView(button("Classic Scan Hook") { scanClassicDevices() })
            addView(button("App Frame Marker") { recordAppObservedFrameMarker() })
            addView(button("Rumble Attempt Hook") { recordRumbleAttempt() })
            addView(button("Rumble Observed Marker") { recordRumbleObservedMarker() })
            addView(ScrollView(this@MainActivity).apply { addView(output) })
        }
        setContentView(controls)
        reportPermissionState()
        scanInputDevices()
        recordBleCharacteristicTargets()
        scanClassicDevices()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        logKeyEvent("key_event", event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        logKeyEvent("key_event", event)
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        logMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun reportPermissionState() {
        logReport(
            "permission_state",
            "sdk_int" to Build.VERSION.SDK_INT,
            "bluetooth_enabled" to bluetoothEnabled(),
            "bluetooth_scan" to permissionStatus(Manifest.permission.BLUETOOTH_SCAN),
            "bluetooth_connect" to permissionStatus(Manifest.permission.BLUETOOTH_CONNECT),
            "bluetooth_legacy" to permissionStatus(Manifest.permission.BLUETOOTH),
            "bluetooth_admin" to permissionStatus(Manifest.permission.BLUETOOTH_ADMIN),
            "location_fine" to permissionStatus(Manifest.permission.ACCESS_FINE_LOCATION),
            "location_coarse" to permissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun scanInputDevices() {
        for (deviceId in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            logReport(
                "input_device_scan",
                "device_id" to device.id,
                "name" to device.name,
                "descriptor" to device.descriptor,
                "sources" to "0x${device.sources.toString(16)}",
                "keyboard_type" to device.keyboardType,
                "vendor_id" to if (Build.VERSION.SDK_INT >= 19) device.vendorId else -1,
                "product_id" to if (Build.VERSION.SDK_INT >= 19) device.productId else -1,
                "motion_ranges" to device.motionRanges.joinToString("|") { range ->
                    "axis=${range.axis},source=0x${range.source.toString(16)},min=${range.min},max=${range.max}"
                }
            )
        }
    }

    private fun startBleScan() {
        reportPermissionState()
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            logReport("ble_scan", "state" to "unavailable")
            return
        }
        if (!hasBluetoothScanPermission()) {
            logReport("ble_scan", "state" to "permission_blocked")
            return
        }
        logReport("ble_scan", "state" to "start", "clue_id" to "ARGUN2021-BLE-001")
        try {
            scanner.startScan(bleScanCallback)
        } catch (error: SecurityException) {
            logReport("ble_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
        }
    }

    private fun recordBleCharacteristicTargets() {
        val serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb"
        for ((characteristicUuid, clueId, purpose) in BLE_CHARACTERISTIC_TARGETS) {
            logReport(
                "ble_characteristic",
                "state" to "candidate_recorded",
                "service_uuid" to serviceUuid,
                "characteristic_uuid" to characteristicUuid,
                "properties" to purpose,
                "clue_id" to clueId
            )
        }
    }

    private fun scanClassicDevices() {
        reportPermissionState()
        val bondedDevices = try {
            bluetoothAdapter?.bondedDevices.orEmpty()
        } catch (error: SecurityException) {
            logReport("classic_scan", "state" to "permission_blocked", "error" to error.javaClass.simpleName)
            return
        }
        if (bondedDevices.isEmpty()) {
            logReport("classic_scan", "state" to "no_bonded_devices")
            return
        }
        for (device in bondedDevices) {
            val uuids = try {
                device.uuids?.joinToString("|") { it.uuid.toString() }.orEmpty()
            } catch (error: SecurityException) {
                "permission_blocked"
            }
            logReport(
                "classic_scan",
                "device_name" to safeDeviceName(device),
                "device_address" to safeDeviceAddress(device),
                "bond_state" to safeBondState(device),
                "type" to safeDeviceType(device),
                "uuids" to uuids,
                "clue_id" to "ARCHER-BT-001"
            )
            logReport(
                "classic_socket_observation",
                "state" to "candidate_recorded",
                "uuid" to SPP_UUID.toString(),
                "channel" to 1,
                "device_address" to safeDeviceAddress(device),
                "clue_id" to "ARCHER-BT-001"
            )
        }
    }

    private fun recordAppObservedFrameMarker() {
        logReport(
            "app_observed_frame",
            "state" to "manual_marker",
            "source_path" to "ble_or_classic_callback",
            "payload_excerpt" to "manual_entry_required",
            "capture_ref" to "local://.evidence/phase1/app-logs/",
            "clue_id" to "ARCHER-INPUT-001"
        )
    }

    private fun recordRumbleAttempt() {
        logReport(
            "rumble_attempt",
            "state" to "manual_plan03_required",
            "ble_characteristic_candidate" to "0000fff5-0000-1000-8000-00805f9b34fb",
            "classic_uuid_candidate" to SPP_UUID.toString(),
            "clue_id" to "ARGUN2021-RUMBLE-001"
        )
        logReport(
            "rumble_failed",
            "state" to "no_payload_sent",
            "reason" to "Plan 03 must review hardware workflow before bounded output writes",
            "clue_id" to "ARGUN2021-RUMBLE-001"
        )
    }

    private fun recordRumbleObservedMarker() {
        logReport(
            "rumble_observed",
            "state" to "manual_marker",
            "command_ref" to "local://.evidence/phase1/app-logs/",
            "capture_ref" to "local://.evidence/phase1/hci/",
            "observer_note" to "Use only when physical motor activation is observed in Plan 03",
            "clue_id" to "ARGUN2021-RUMBLE-001"
        )
    }

    private fun logKeyEvent(report: String, event: KeyEvent) {
        logReport(
            report,
            "action" to keyActionName(event.action),
            "key_code" to event.keyCode,
            "scan_code" to event.scanCode,
            "repeat_count" to event.repeatCount,
            "source" to "0x${event.source.toString(16)}",
            "device_id" to event.deviceId,
            "event_time" to event.eventTime
        )
    }

    private fun logMotionEvent(event: MotionEvent) {
        val axes = MOTION_AXES.joinToString("|") { axis ->
            "${MotionEvent.axisToString(axis)}=${event.getAxisValue(axis)}"
        }
        logReport(
            "motion_event",
            "action" to motionActionName(event.actionMasked),
            "source" to "0x${event.source.toString(16)}",
            "device_id" to event.deviceId,
            "pointer_count" to event.pointerCount,
            "axes" to axes,
            "x_precision" to event.xPrecision,
            "y_precision" to event.yPrecision,
            "event_time" to event.eventTime
        )
    }

    private fun logReport(report: String, vararg fields: Pair<String, Any?>) {
        val allFields = linkedMapOf<String, Any?>(
            "schema" to schema,
            "report" to report,
            "ts_elapsed_ms" to SystemClock.elapsedRealtime(),
            "session_id" to sessionId
        )
        fields.forEach { (key, value) -> allFields[key] = value }
        val line = allFields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":${jsonValue(value)}"
        }
        output.append(line)
        output.append("\n")
        android.util.Log.i("BtGunDiagnostic", line)
    }

    private fun jsonValue(value: Any?): String =
        when (value) {
            null -> "null"
            is Number, is Boolean -> value.toString()
            else -> "\"${escape(value.toString())}\""
        }

    private fun escape(value: String): String =
        buildString {
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun permissionStatus(permission: String): String =
        if (Build.VERSION.SDK_INT < 23) {
            "granted_pre_runtime"
        } else if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "granted"
        } else {
            "denied"
        }

    private fun hasBluetoothScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    private fun bluetoothEnabled(): String =
        try {
            when (bluetoothAdapter?.isEnabled) {
                true -> "true"
                false -> "false"
                null -> "unavailable"
            }
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeDeviceName(device: BluetoothDevice?): String =
        try {
            device?.name.orEmpty()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeDeviceAddress(device: BluetoothDevice?): String =
        try {
            device?.address.orEmpty()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeBondState(device: BluetoothDevice): String =
        try {
            device.bondState.toString()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun safeDeviceType(device: BluetoothDevice): String =
        try {
            device.type.toString()
        } catch (_: SecurityException) {
            "permission_blocked"
        }

    private fun keyActionName(action: Int): String =
        when (action) {
            KeyEvent.ACTION_DOWN -> "down"
            KeyEvent.ACTION_UP -> "up"
            KeyEvent.ACTION_MULTIPLE -> "multiple"
            else -> "unknown_$action"
        }

    private fun motionActionName(action: Int): String =
        when (action) {
            MotionEvent.ACTION_DOWN -> "down"
            MotionEvent.ACTION_UP -> "up"
            MotionEvent.ACTION_MOVE -> "move"
            MotionEvent.ACTION_CANCEL -> "cancel"
            else -> "unknown_$action"
        }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val BLE_CHARACTERISTIC_TARGETS = arrayOf(
            Triple("0000fff1-0000-1000-8000-00805f9b34fb", "ARGUN2021-BLE-001", "read_or_notify_candidate"),
            Triple("0000fff3-0000-1000-8000-00805f9b34fb", "ARCHER-BLE-001", "notify_candidate"),
            Triple("0000fff5-0000-1000-8000-00805f9b34fb", "ARGUN2021-RUMBLE-001", "write_candidate")
        )
        private val MOTION_AXES = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS
        )
    }
}
