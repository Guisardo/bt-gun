package com.btgun.host.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun main() {
    visualizerStatusBodyHasStableSanitizedFields()
    visualizerStatusNormalizesInvalidAndBlankValues()
    visualizerStatusSerializationOmitsForbiddenMaterial()
}

private fun visualizerStatusBodyHasStableSanitizedFields() {
    val status = VisualizerStatus(
        rawDebugEnabled = true,
        aimZeroState = "ready",
        recenterState = "recentered",
        lastRecenterElapsedNanos = 2_000_000_000L,
        androidElapsedNanos = 2_050_000_000L,
        captureElapsedNanos = 2_010_000_000L,
        sendElapsedNanos = 2_040_000_000L,
        statusSequence = 7L,
        recenterLabel = "reload hold recentered",
        aimZeroLabel = "aim baseline ready",
    )

    val body = status.toJsonBody()

    expectEquals(
        "stable fields",
        listOf(
            "rawDebugEnabled",
            "aimZeroState",
            "recenterState",
            "lastRecenterElapsedNanos",
            "androidElapsedNanos",
            "captureElapsedNanos",
            "sendElapsedNanos",
            "statusSequence",
            "recenterLabel",
            "aimZeroLabel",
        ),
        body.keys.toList(),
    )
    expectEquals("raw debug", "true", body["rawDebugEnabled"]?.jsonPrimitive?.content)
    expectEquals("aim zero", "ready", body["aimZeroState"]?.jsonPrimitive?.content)
    expectEquals("recenter", "recentered", body["recenterState"]?.jsonPrimitive?.content)
    expectEquals("last recenter", "2000000000", body["lastRecenterElapsedNanos"]?.jsonPrimitive?.content)
    expectEquals("android elapsed", "2050000000", body["androidElapsedNanos"]?.jsonPrimitive?.content)
}

private fun visualizerStatusNormalizesInvalidAndBlankValues() {
    val status = VisualizerStatus(
        rawDebugEnabled = false,
        aimZeroState = " ",
        recenterState = "has spaces and punctuation!",
        lastRecenterElapsedNanos = -1L,
        androidElapsedNanos = -10L,
        captureElapsedNanos = -2L,
        sendElapsedNanos = -3L,
        statusSequence = -4L,
        recenterLabel = "",
        aimZeroLabel = "this label is far too long for a compact diagnostic status row",
    )

    val body = status.toJsonBody()

    expectEquals("blank aim zero unavailable", "unavailable", body["aimZeroState"]?.jsonPrimitive?.content)
    expectEquals("invalid recenter unavailable", "unavailable", body["recenterState"]?.jsonPrimitive?.content)
    expectFalse("negative recenter omitted", body.containsKey("lastRecenterElapsedNanos"))
    expectEquals("android elapsed clamped", "0", body["androidElapsedNanos"]?.jsonPrimitive?.content)
    expectFalse("negative capture omitted", body.containsKey("captureElapsedNanos"))
    expectFalse("negative send omitted", body.containsKey("sendElapsedNanos"))
    expectFalse("negative sequence omitted", body.containsKey("statusSequence"))
    expectEquals("blank recenter label unavailable", "unavailable", body["recenterLabel"]?.jsonPrimitive?.content)
    expectEquals("long aim label capped", 48, body["aimZeroLabel"]?.jsonPrimitive?.content?.length)
}

private fun visualizerStatusSerializationOmitsForbiddenMaterial() {
    val body = VisualizerStatus(
        rawDebugEnabled = true,
        aimZeroState = "ready",
        recenterState = "idle",
        androidElapsedNanos = 1L,
        recenterLabel = "ready",
        aimZeroLabel = "ready",
    ).toJsonBody()
    val encoded = body.toString()

    listOf(
        "pairing",
        "secret",
        "hmac",
        "privateKey",
        "rawPacket",
        "packetBytes",
        "deviceId",
        "screenshot",
        "rawLog",
        "macAddress",
    ).forEach { token ->
        expectFalse("forbidden token $token", encoded.contains(token, ignoreCase = true))
        expectFalse("forbidden field $token", body.containsKey(token))
    }
}

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError("$label expected false")
    }
}
