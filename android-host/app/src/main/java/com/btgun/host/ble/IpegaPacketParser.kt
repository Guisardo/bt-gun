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
    fun parseFff3(
        value: ByteArray,
        captureElapsedNanos: Long,
        emittedElapsedNanos: Long,
    ): ParsedGunPacket {
        val rawHex = value.toHex()
        val rawAscii = value.toPrintableAscii()
        val mapping = KNOWN_FFF3_PAYLOADS[rawHex]

        if (mapping == null) {
            return UnknownBlePayload(
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
        }

        return ParsedGunPacket.Event(
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
    }

    private data class Mapping(
        val rawAscii: String,
        val eventName: String,
        val pressed: Boolean,
        val clueId: String,
        val captureId: String,
        val confidence: SemanticConfidence,
    )

    companion object {
        const val FFF0_SERVICE_UUID: String = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val FFF3_CHARACTERISTIC_UUID: String = "0000fff3-0000-1000-8000-00805f9b34fb"

        private val KNOWN_FFF3_PAYLOADS: Map<String, Mapping> = listOf(
            Mapping("ARGun KeyPressed", "trigger", true, "ARGUN2021-CONTROL-001", "trigger-001", SemanticConfidence.CANDIDATE),
            Mapping("", "trigger", false, "ARGUN2021-CONTROL-001", "trigger-001", SemanticConfidence.CANDIDATE),
            Mapping("B8DOWN", "reload", true, "ARGUN2021-CONTROL-001", "reload-001", SemanticConfidence.CONFIRMED),
            Mapping("B8UP", "reload", false, "ARGUN2021-CONTROL-001", "reload-001", SemanticConfidence.CONFIRMED),
            Mapping("B6DOWN", "stick_left", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B6UP", "stick_left", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B4DOWN", "stick_right", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B4UP", "stick_right", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B5DOWN", "stick_up", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B5UP", "stick_up", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B7DOWN", "stick_down", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("B7UP", "stick_down", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
            Mapping("BADOWN", "button_x", true, "ARCHER-INPUT-001", "button-x-001", SemanticConfidence.CANDIDATE),
            Mapping("BAUP", "button_x", false, "ARCHER-INPUT-001", "button-x-001", SemanticConfidence.CANDIDATE),
            Mapping("B3DOWN", "button_y", true, "ARCHER-INPUT-001", "button-y-001", SemanticConfidence.CANDIDATE),
            Mapping("B3UP", "button_y", false, "ARCHER-INPUT-001", "button-y-001", SemanticConfidence.CANDIDATE),
            Mapping("B2DOWN", "button_a", true, "ARCHER-INPUT-001", "button-a-001", SemanticConfidence.CANDIDATE),
            Mapping("B2UP", "button_a", false, "ARCHER-INPUT-001", "button-a-up-noisy-001", SemanticConfidence.CANDIDATE),
            Mapping("B9DOWN", "button_b", true, "ARCHER-INPUT-001", "button-b-001", SemanticConfidence.CANDIDATE),
            Mapping("B9UP", "button_b", false, "ARCHER-INPUT-001", "button-b-001", SemanticConfidence.CANDIDATE),
        ).associateBy { mapping ->
            if (mapping.rawAscii.isEmpty()) {
                "00000000000000000000000000000000"
            } else {
                mapping.rawAscii.encodeToByteArray().toHex()
            }
        }
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
