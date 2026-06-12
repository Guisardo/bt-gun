package com.btgun.desktop.control

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ProfileMetadata(
    val profileId: String,
    val displayName: String,
    val revision: Long,
    val source: String,
    val rawDebugEnabled: Boolean = false,
)

internal fun profileMetadataFromJsonBody(body: JsonObject): ProfileMetadata? {
    val profileId = body.stringField("profileId")?.takeIf(String::isNotBlank) ?: return null
    val displayName = body.stringField("displayName")?.takeIf(String::isNotBlank) ?: return null
    val revision = body.longField("revision")?.takeIf { it >= 0L } ?: return null
    val source = body.stringField("source")?.takeIf { it == "android" } ?: return null
    return ProfileMetadata(
        profileId = profileId,
        displayName = displayName,
        revision = revision,
        source = source,
        rawDebugEnabled = body.booleanField("rawDebugEnabled") ?: false,
    )
}

private fun JsonObject.stringField(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.longField(name: String): Long? =
    (get(name) as? JsonPrimitive)?.jsonPrimitive?.longOrNull

private fun JsonObject.booleanField(name: String): Boolean? =
    (get(name) as? JsonPrimitive)?.jsonPrimitive?.booleanOrNull
