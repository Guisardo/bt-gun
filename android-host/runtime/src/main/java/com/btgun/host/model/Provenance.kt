package com.btgun.host.model

data class Provenance(
    val rawAscii: String? = null,
    val rawHex: String? = null,
    val bleServiceUuid: String? = null,
    val bleCharacteristicUuid: String? = null,
    val clueId: String? = null,
    val captureId: String? = null,
    val semanticConfidence: SemanticConfidence = SemanticConfidence.UNKNOWN,
)

enum class SemanticConfidence {
    CONFIRMED,
    CANDIDATE,
    UNKNOWN,
}
