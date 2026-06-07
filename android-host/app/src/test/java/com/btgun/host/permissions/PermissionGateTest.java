package com.btgun.host.permissions;

import java.util.Set;

public final class PermissionGateTest {
    public static void main(String[] args) {
        android12ScanAndConnectRequireNearbyDevicePermissions();
        legacyScanAcceptsFineOrCoarseLocation();
        sensorCapabilityReportsAvailableAndUnavailableWithoutRuntimePermission();
        vibrationCapabilityReportsHardwareState();
        lanCapabilityReportsNetworkState();
    }

    private static void android12ScanAndConnectRequireNearbyDevicePermissions() {
        PermissionGateState blocked = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectState("android12 scan missing permission", CapabilityState.BLOCKED, blocked.getBluetoothScan().getState());
        expectState("android12 connect missing permission", CapabilityState.BLOCKED, blocked.getBluetoothConnect().getState());
        expectEquals("android12 permission model", BluetoothPermissionModel.ANDROID_12_NEARBY_DEVICES, blocked.getBluetoothPermissionModel());

        PermissionGateState granted = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_CONNECT),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectState("android12 scan granted", CapabilityState.AVAILABLE, granted.getBluetoothScan().getState());
        expectState("android12 connect granted", CapabilityState.AVAILABLE, granted.getBluetoothConnect().getState());
        expectState("android12 location compatibility not required", CapabilityState.AVAILABLE, granted.getLocationScanCompatibility().getState());
    }

    private static void legacyScanAcceptsFineOrCoarseLocation() {
        PermissionGateState coarseOnly = PermissionGate.evaluate(new PermissionGateInput(
                30,
                Set.of(PermissionGate.ACCESS_COARSE_LOCATION),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectEquals("legacy permission model", BluetoothPermissionModel.LEGACY_LOCATION_SCAN, coarseOnly.getBluetoothPermissionModel());
        expectState("legacy coarse scan", CapabilityState.AVAILABLE, coarseOnly.getBluetoothScan().getState());
        expectState("legacy location compatibility coarse", CapabilityState.AVAILABLE, coarseOnly.getLocationScanCompatibility().getState());

        PermissionGateState fineOnly = PermissionGate.evaluate(new PermissionGateInput(
                30,
                Set.of(PermissionGate.ACCESS_FINE_LOCATION),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectState("legacy fine scan", CapabilityState.AVAILABLE, fineOnly.getBluetoothScan().getState());

        PermissionGateState missingLocation = PermissionGate.evaluate(new PermissionGateInput(
                30,
                Set.of(),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectState("legacy missing scan location", CapabilityState.BLOCKED, missingLocation.getBluetoothScan().getState());
    }

    private static void sensorCapabilityReportsAvailableAndUnavailableWithoutRuntimePermission() {
        PermissionGateState motionAvailable = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_CONNECT),
                true,
                true,
                false,
                true,
                false,
                false,
                false,
                true,
                true
        ));

        expectState("rotation vector sensor available", CapabilityState.AVAILABLE, motionAvailable.getMotionSensors().getState());

        PermissionGateState motionUnavailable = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_CONNECT),
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                true
        ));

        expectState("motion sensors unavailable", CapabilityState.UNAVAILABLE, motionUnavailable.getMotionSensors().getState());
    }

    private static void vibrationCapabilityReportsHardwareState() {
        PermissionGateState noVibrator = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_CONNECT),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                true
        ));

        expectState("vibrator absent", CapabilityState.UNAVAILABLE, noVibrator.getVibration().getState());
    }

    private static void lanCapabilityReportsNetworkState() {
        PermissionGateState noNetwork = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_CONNECT),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                false
        ));

        expectState("lan network unavailable", CapabilityState.UNAVAILABLE, noNetwork.getLanNetwork().getState());
    }

    private static void expectState(String label, CapabilityState expected, CapabilityState actual) {
        expectEquals(label, expected, actual);
    }

    private static void expectEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
