package com.btgun.host.ble

import com.btgun.host.model.GunEvent
import com.btgun.host.model.LiveEnvelope
import com.btgun.host.model.SemanticConfidence
import com.btgun.host.model.StreamKind

private const val FFF3_CHARACTERISTIC_UUID = "0000fff3-0000-1000-8000-00805f9b34fb"

fun main() {
    knownFff3FixturesBecomeGunProductEventsWithProvenance()
    unknownFff3PayloadReturnsDebugStatusOnly()
}

private fun knownFff3FixturesBecomeGunProductEventsWithProvenance() {
    listOf(
        Case("ARGun KeyPressed", "trigger", true, "ARGUN2021-CONTROL-001", "trigger-001", SemanticConfidence.CANDIDATE),
        Case(hex = "00000000000000000000000000000000", "trigger", false, "ARGUN2021-CONTROL-001", "trigger-001", SemanticConfidence.CANDIDATE),
        Case("B8DOWN", "reload", true, "ARGUN2021-CONTROL-001", "reload-001", SemanticConfidence.CONFIRMED),
        Case("B8UP", "reload", false, "ARGUN2021-CONTROL-001", "reload-001", SemanticConfidence.CONFIRMED),
        Case("B6DOWN", "stick_left", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B6UP", "stick_left", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B4DOWN", "stick_right", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B4UP", "stick_right", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B5DOWN", "stick_up", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B5UP", "stick_up", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B7DOWN", "stick_down", true, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("B7UP", "stick_down", false, "ARCHER-INPUT-001", "joystick-001", SemanticConfidence.CONFIRMED),
        Case("BADOWN", "button_x", true, "ARCHER-INPUT-001", "button-x-001", SemanticConfidence.CANDIDATE),
        Case("BAUP", "button_x", false, "ARCHER-INPUT-001", "button-x-001", SemanticConfidence.CANDIDATE),
        Case("B3DOWN", "button_y", true, "ARCHER-INPUT-001", "button-y-001", SemanticConfidence.CANDIDATE),
        Case("B3UP", "button_y", false, "ARCHER-INPUT-001", "button-y-001", SemanticConfidence.CANDIDATE),
        Case("B2DOWN", "button_a", true, "ARCHER-INPUT-001", "button-a-001", SemanticConfidence.CANDIDATE),
        Case("B2UP", "button_a", false, "ARCHER-INPUT-001", "button-a-up-noisy-001", SemanticConfidence.CANDIDATE),
        Case("B9DOWN", "button_b", true, "ARCHER-INPUT-001", "button-b-001", SemanticConfidence.CANDIDATE),
        Case("B9UP", "button_b", false, "ARCHER-INPUT-001", "button-b-001", SemanticConfidence.CANDIDATE),
    ).forEachIndexed { index, expected ->
        val parsed = IpegaPacketParser().parseFff3(
            value = expected.bytes(),
            captureElapsedNanos = 1_000L + index,
            emittedElapsedNanos = 2_000L + index,
        )

        val event = parsed.expectGunEvent("fixture ${expected.eventName}")
        expectEquals("${expected.eventName} stream", StreamKind.GUN, event.stream)
        expectEquals("${expected.eventName} seq", (index + 1).toLong(), event.seq)
        expectEquals("${expected.eventName} capture elapsed", 1_000L + index, event.captureElapsedNanos)
        expectEquals("${expected.eventName} emitted elapsed", 2_000L + index, event.emittedElapsedNanos)
        expectEquals("${expected.eventName} name", expected.eventName, event.payload.name)
        expectEquals("${expected.eventName} pressed", expected.pressed, event.payload.pressed)
        expectEquals("${expected.eventName} raw ascii", expected.rawAscii, event.provenance?.rawAscii)
        expectEquals("${expected.eventName} raw hex", expected.rawHex, event.provenance?.rawHex)
        expectEquals("${expected.eventName} characteristic", FFF3_CHARACTERISTIC_UUID, event.provenance?.bleCharacteristicUuid)
        expectEquals("${expected.eventName} clue", expected.clueId, event.provenance?.clueId)
        expectEquals("${expected.eventName} capture", expected.captureId, event.provenance?.captureId)
        expectEquals("${expected.eventName} confidence", expected.confidence, event.provenance?.semanticConfidence)
    }
}

private fun unknownFff3PayloadReturnsDebugStatusOnly() {
    val parsed = IpegaPacketParser().parseFff3(
        value = "NOISE".encodeToByteArray(),
        captureElapsedNanos = 10L,
        emittedElapsedNanos = 20L,
    )

    if (parsed is ParsedGunPacket.Event) {
        throw AssertionError("unknown payload must not become product GunEvent")
    }
    val unknown = parsed.expectUnknown("NOISE unknown")
    expectEquals("unknown raw ascii", "NOISE", unknown.rawAscii)
    expectEquals("unknown raw hex", "4e4f495345", unknown.rawHex)
    expectEquals("unknown characteristic", FFF3_CHARACTERISTIC_UUID, unknown.bleCharacteristicUuid)
}

private data class Case(
    val rawAscii: String,
    val eventName: String,
    val pressed: Boolean,
    val clueId: String,
    val captureId: String,
    val confidence: SemanticConfidence,
    val rawHex: String = rawAscii.encodeToByteArray().toHex(),
) {
    constructor(
        hex: String,
        eventName: String,
        pressed: Boolean,
        clueId: String,
        captureId: String,
        confidence: SemanticConfidence,
    ) : this("", eventName, pressed, clueId, captureId, confidence, hex)

    fun bytes(): ByteArray = if (rawAscii.isNotEmpty()) rawAscii.encodeToByteArray() else rawHex.hexToBytes()
}

private fun ParsedGunPacket.expectGunEvent(label: String): LiveEnvelope<GunEvent> =
    when (this) {
        is ParsedGunPacket.Event -> envelope
        is UnknownBlePayload -> throw AssertionError("$label expected GunEvent, got UnknownBlePayload")
    }

private fun ParsedGunPacket.expectUnknown(label: String): UnknownBlePayload =
    when (this) {
        is UnknownBlePayload -> this
        is ParsedGunPacket.Event -> throw AssertionError("$label expected UnknownBlePayload, got GunEvent")
    }

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}
