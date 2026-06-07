package com.btgun.host.permissions

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Vibrator

object HostCapabilityProbe {
    fun evaluate(context: Context): PermissionGateState =
        PermissionGate.evaluate(input(context))

    fun input(context: Context): PermissionGateInput =
        PermissionGateInput(
            sdkInt = Build.VERSION.SDK_INT,
            grantedPermissions = grantedPermissions(context),
            bluetoothEnabled = bluetoothEnabled(context),
            locationServiceAvailable = locationServiceAvailable(context),
            hasGyroscope = hasSensor(context, Sensor.TYPE_GYROSCOPE),
            hasRotationVector = hasSensor(context, Sensor.TYPE_ROTATION_VECTOR),
            hasGameRotationVector = hasSensor(context, Sensor.TYPE_GAME_ROTATION_VECTOR),
            hasAccelerometer = hasSensor(context, Sensor.TYPE_ACCELEROMETER),
            hasGravity = hasSensor(context, Sensor.TYPE_GRAVITY),
            hasVibrator = hasVibrator(context),
            hasNetwork = hasNetwork(context),
        )

    fun runtimePermissionsForHost(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }

    private fun grantedPermissions(context: Context): Set<String> =
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).filter { permission ->
            try {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            } catch (_: RuntimeException) {
                false
            }
        }.toSet()

    private fun bluetoothEnabled(context: Context): Boolean =
        try {
            bluetoothAdapter(context)?.isEnabled == true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    @Suppress("DEPRECATION")
    private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: RuntimeException) {
            null
        }

    private fun locationServiceAvailable(context: Context): Boolean {
        val manager = try {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (_: RuntimeException) {
            null
        } ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                manager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun hasSensor(context: Context, sensorType: Int): Boolean =
        try {
            (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(sensorType) != null
        } catch (_: RuntimeException) {
            false
        }

    @Suppress("DEPRECATION")
    private fun hasVibrator(context: Context): Boolean =
        try {
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    private fun hasNetwork(context: Context): Boolean =
        try {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager != null
        } catch (_: RuntimeException) {
            false
        }
}
