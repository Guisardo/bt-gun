package com.btgun.host.permissions;

import com.btgun.host.model.GunEvent;
import com.btgun.host.model.LiveEnvelope;
import com.btgun.host.model.Provenance;
import com.btgun.host.model.SemanticConfidence;
import com.btgun.host.model.StreamKind;
import com.btgun.host.model.StreamSequencer;

import java.util.Set;

public final class PermissionGateTest {
    public static void main(String[] args) {
        android12ScanAndConnectRequireNearbyDevicePermissions();
        android12BluetoothOffBlocksScanAndConnect();
        legacyScanAcceptsFineOrCoarseLocation();
        legacyLocationServiceOffBlocksScan();
        sensorCapabilityReportsAvailableAndUnavailableWithoutRuntimePermission();
        vibrationCapabilityReportsHardwareState();
        lanCapabilityReportsNetworkState();
        envelopeSequencesAreIndependentPerStream();
        envelopeCarriesOptionalDebugProvenance();
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

    private static void android12BluetoothOffBlocksScanAndConnect() {
        PermissionGateState bluetoothOff = PermissionGate.evaluate(new PermissionGateInput(
                35,
                Set.of(PermissionGate.BLUETOOTH_SCAN, PermissionGate.BLUETOOTH_CONNECT),
                false,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectState("bluetooth off scan", CapabilityState.BLOCKED, bluetoothOff.getBluetoothScan().getState());
        expectState("bluetooth off connect", CapabilityState.BLOCKED, bluetoothOff.getBluetoothConnect().getState());
        expectEquals("bluetooth off scan detail", "Bluetooth is off.", bluetoothOff.getBluetoothScan().getDetail());
        expectEquals("bluetooth off cannot start", false, bluetoothOff.getCanStartSession());
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

    private static void legacyLocationServiceOffBlocksScan() {
        PermissionGateState locationOff = PermissionGate.evaluate(new PermissionGateInput(
                30,
                Set.of(PermissionGate.ACCESS_FINE_LOCATION),
                true,
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true
        ));

        expectState("legacy location off compatibility", CapabilityState.BLOCKED, locationOff.getLocationScanCompatibility().getState());
        expectState("legacy location off scan", CapabilityState.BLOCKED, locationOff.getBluetoothScan().getState());
        expectEquals("legacy location off scan detail", "Enable location services for legacy BLE scan.", locationOff.getBluetoothScan().getDetail());
        expectEquals("legacy location off cannot start", false, locationOff.getCanStartSession());
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

    private static void envelopeSequencesAreIndependentPerStream() {
        StreamSequencer sequencer = new StreamSequencer();

        expectEquals("gun seq 1", 1L, sequencer.next(StreamKind.GUN));
        expectEquals("gun seq 2", 2L, sequencer.next(StreamKind.GUN));
        expectEquals("motion seq 1", 1L, sequencer.next(StreamKind.MOTION));
        expectEquals("status seq 1", 1L, sequencer.next(StreamKind.STATUS));
    }

    private static void envelopeCarriesOptionalDebugProvenance() {
        Provenance provenance = new Provenance(
                "T",
                "54",
                "0000fff0-0000-1000-8000-00805f9b34fb",
                "0000fff3-0000-1000-8000-00805f9b34fb",
                "input-trigger-001",
                "phase1-trigger-capture",
                SemanticConfidence.CONFIRMED
        );
        LiveEnvelope<GunEvent> envelope = new LiveEnvelope<>(
                StreamKind.GUN,
                1L,
                100L,
                150L,
                new GunEvent("trigger", true, null, null),
                provenance
        );

        expectEquals("stream wire name", "gun", envelope.getStream().getWireName());
        expectEquals("capture elapsed", 100L, envelope.getCaptureElapsedNanos());
        expectEquals("emitted elapsed", 150L, envelope.getEmittedElapsedNanos());
        expectEquals("provenance characteristic", "0000fff3-0000-1000-8000-00805f9b34fb", envelope.getProvenance().getBleCharacteristicUuid());
        expectEquals("semantic confidence", SemanticConfidence.CONFIRMED, envelope.getProvenance().getSemanticConfidence());
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
