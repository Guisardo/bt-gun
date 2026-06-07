package com.btgun.host.ble

import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.Provenance
import com.btgun.host.model.SemanticConfidence
import com.btgun.host.model.StatusEvent
import com.btgun.host.model.StreamKind
import com.btgun.host.model.StreamSequencer

class IpegaPacketParser(
    private val sequencer: StreamSequencer = StreamSequencer(),
) {
    private val joystickState = JoystickSwitchState()

    fun reset() {
        joystickState.clear()
    }

    fun parseFff3(
        value: ByteArray,
        captureElapsedNanos: Long,
        emittedElapsedNanos: Long,
    ): ParsedGunPacket {
        val rawHex = value.toHex()
        val rawAscii = value.toPrintableAscii()
        val joystickMapping = JOYSTICK_FFF3_PAYLOADS[rawHex]
        if (joystickMapping != null) {
            return joystickEvent(joystickMapping, rawHex, captureElapsedNanos, emittedElapsedNanos)
        }

        val mapping = KNOWN_FFF3_PAYLOADS[rawHex]

        if (mapping == null) {
            return unknownPayload(rawAscii, rawHex, captureElapsedNanos, emittedElapsedNanos)
        }

        return gunEvent(mapping, rawHex, captureElapsedNanos, emittedElapsedNanos)
    }

    private fun unknownPayload(
        rawAscii: String,
        rawHex: String,
        captureElapsedNanos: Long,
        emittedElapsedNanos: Long,
    ): UnknownBlePayload =
        UnknownBlePayload(
            rawAscii = rawAscii,
            rawHex = rawHex,
            bleServiceUuid = FFF0_SERVICE_UUID,
            bleCharacteristicUuid = FFF3_CHARACTERISTIC_UUID,
            envelope = LiveEnvelope(
                stream = StreamKind.STATUS,
                seq = sequencer.next(StreamKind.STATUS),
                captureElapsedNanos = captureElapsedNanos,
                emittedElapsedNanos = emittedElapsedNanos,
                payload = StatusEvent(
                    name = "unknown_fff3_payload",
                    message = rawAscii.ifEmpty { rawHex },
                ),
                provenance = Provenance(
                    rawAscii = rawAscii,
                    rawHex = rawHex,
                    bleServiceUuid = FFF0_SERVICE_UUID,
                    bleCharacteristicUuid = FFF3_CHARACTERISTIC_UUID,
                    semanticConfidence = SemanticConfidence.UNKNOWN,
                ),
            ),
        )

    private fun joystickEvent(
        mapping: JoystickMapping,
        rawHex: String,
        captureElapsedNanos: Long,
        emittedElapsedNanos: Long,
    ): ParsedGunPacket.Event {
        val axis = joystickState.apply(mapping.direction, mapping.pressed)
        return ParsedGunPacket.Event(
            envelope = LiveEnvelope(
                stream = StreamKind.GUN,
                seq = sequencer.next(StreamKind.GUN),
                captureElapsedNanos = captureElapsedNanos,
                emittedElapsedNanos = emittedElapsedNanos,
                payload = GunEvent(
                    name = "stick",
                    axisX = axis.x,
                    axisY = axis.y,
                ),
                provenance = Provenance(
                    rawAscii = mapping.rawAscii,
                    rawHex = rawHex,
                    bleServiceUuid = FFF0_SERVICE_UUID,
                    bleCharacteristicUuid = FFF3_CHARACTERISTIC_UUID,
                    clueId = mapping.clueId,
                    captureId = mapping.captureId,
                    semanticConfidence = SemanticConfidence.CONFIRMED,
                ),
            ),
        )
    }

    private fun gunEvent(
        mapping: Mapping,
        rawHex: String,
        captureElapsedNanos: Long,
        emittedElapsedNanos: Long,
    ): ParsedGunPacket.Event =
        ParsedGunPacket.Event(
            envelope = LiveEnvelope(
                stream = StreamKind.GUN,
                seq = sequencer.next(StreamKind.GUN),
                captureElapsedNanos = captureElapsedNanos,
                emittedElapsedNanos = emittedElapsedNanos,
                payload = GunEvent(
                    name = mapping.eventName,
                    pressed = mapping.pressed,
                ),
                provenance = Provenance(
                    rawAscii = mapping.rawAscii,
                    rawHex = rawHex,
                    bleServiceUuid = FFF0_SERVICE_UUID,
                    bleCharacteristicUuid = FFF3_CHARACTERISTIC_UUID,
                    clueId = mapping.clueId,
                    captureId = mapping.captureId,
                    semanticConfidence = mapping.confidence,
                ),
            ),
        )

    private data class Mapping(
        val rawAscii: String,
        val eventName: String,
        val pressed: Boolean,
        val clueId: String,
        val captureId: String,
        val confidence: SemanticConfidence,
    ) {
        fun rawHexKey(): String =
            if (rawAscii.isEmpty()) {
                "00000000000000000000000000000000"
            } else {
                rawAscii.encodeToByteArray().toHex()
            }
    }

    private enum class JoystickDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN,
    }

    private data class JoystickAxis(val x: Float, val y: Float)

    private data class JoystickMapping(
        val rawAscii: String,
        val direction: JoystickDirection,
        val pressed: Boolean,
        val clueId: String = "ARCHER-INPUT-001",
        val captureId: String = "joystick-sweep-001",
    ) {
        fun rawHexKey(): String = rawAscii.encodeToByteArray().toHex()
    }

    private class JoystickSwitchState {
        private val activeDirections = mutableSetOf<JoystickDirection>()

        fun clear() {
            activeDirections.clear()
        }

        fun apply(direction: JoystickDirection, pressed: Boolean): JoystickAxis {
            if (pressed) {
                activeDirections.add(direction)
            } else {
                activeDirections.remove(direction)
            }
            val x = activeDirections.axis(JoystickDirection.RIGHT, JoystickDirection.LEFT)
            val y = activeDirections.axis(JoystickDirection.UP, JoystickDirection.DOWN)
            return JoystickAxis(x, y)
        }

        private fun Set<JoystickDirection>.axis(
            positive: JoystickDirection,
            negative: JoystickDirection,
        ): Float =
            when {
                positive in this && negative !in this -> 1f
                negative in this && positive !in this -> -1f
                else -> 0f
            }
    }

    companion object {
        const val FFF0_SERVICE_UUID: String = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val FFF3_CHARACTERISTIC_UUID: String = "0000fff3-0000-1000-8000-00805f9b34fb"

        private val JOYSTICK_FFF3_PAYLOADS: Map<String, JoystickMapping> = listOf(
            JoystickMapping("B6DOWN", JoystickDirection.LEFT, true),
            JoystickMapping("B6UP", JoystickDirection.LEFT, false),
            JoystickMapping("B4DOWN", JoystickDirection.RIGHT, true),
            JoystickMapping("B4UP", JoystickDirection.RIGHT, false),
            JoystickMapping("B5DOWN", JoystickDirection.UP, true),
            JoystickMapping("B5UP", JoystickDirection.UP, false),
            JoystickMapping("B7DOWN", JoystickDirection.DOWN, true),
            JoystickMapping("B7UP", JoystickDirection.DOWN, false),
        ).associateBy(JoystickMapping::rawHexKey)

        private val KNOWN_FFF3_PAYLOADS: Map<String, Mapping> = listOf(
            Mapping("ARGun KeyPressed", "trigger", true, "ARGUN2021-CONTROL-001", "trigger-001", SemanticConfidence.CANDIDATE),
            Mapping("", "trigger", false, "ARGUN2021-CONTROL-001", "trigger-001", SemanticConfidence.CANDIDATE),
            Mapping("B8DOWN", "reload", true, "ARGUN2021-CONTROL-001", "reload-001", SemanticConfidence.CONFIRMED),
            Mapping("B8UP", "reload", false, "ARGUN2021-CONTROL-001", "reload-001", SemanticConfidence.CONFIRMED),
            Mapping("BADOWN", "button_x", true, "ARCHER-INPUT-001", "button-x-001", SemanticConfidence.CANDIDATE),
            Mapping("BAUP", "button_x", false, "ARCHER-INPUT-001", "button-x-001", SemanticConfidence.CANDIDATE),
            Mapping("B3DOWN", "button_y", true, "ARCHER-INPUT-001", "button-y-001", SemanticConfidence.CANDIDATE),
            Mapping("B3UP", "button_y", false, "ARCHER-INPUT-001", "button-y-001", SemanticConfidence.CANDIDATE),
            Mapping("B2DOWN", "button_a", true, "ARCHER-INPUT-001", "button-a-001", SemanticConfidence.CANDIDATE),
            Mapping("B2UP", "button_a", false, "ARCHER-INPUT-001", "button-a-up-noisy-001", SemanticConfidence.CANDIDATE),
            Mapping("B9DOWN", "button_b", true, "ARCHER-INPUT-001", "button-b-001", SemanticConfidence.CANDIDATE),
            Mapping("B9UP", "button_b", false, "ARCHER-INPUT-001", "button-b-001", SemanticConfidence.CANDIDATE),
        ).associateBy(Mapping::rawHexKey)
    }
}

sealed interface ParsedGunPacket {
    data class Event(val envelope: LiveEnvelope<GunEvent>) : ParsedGunPacket
}

data class UnknownBlePayload(
    val rawAscii: String,
    val rawHex: String,
    val bleServiceUuid: String,
    val bleCharacteristicUuid: String,
    val envelope: LiveEnvelope<StatusEvent>,
) : ParsedGunPacket

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun ByteArray.toPrintableAscii(): String =
    if (all { byte -> byte in 0x20..0x7e }) {
        decodeToString()
    } else {
        ""
    }
