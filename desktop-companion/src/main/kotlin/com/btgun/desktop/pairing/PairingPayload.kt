package com.btgun.desktop.pairing

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PairingPayloadV1(
    val v: Int = 1,
    val sid: String,
    val host: String,
    val port: Int,
    val expiresAtEpochMillis: Long,
    val desktopSpkiSha256: String,
    val desktopNonce: String,
    val qrSecret: String,
) {
    init {
        require(v == 1) { "v must be 1" }
        require(sid.isNotBlank()) { "sid must not be blank" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65_535) { "port must be between 1 and 65535" }
        require(expiresAtEpochMillis > 0L) { "expiresAtEpochMillis must be positive" }
        require(desktopSpkiSha256.length >= 8) { "desktopSpkiSha256 must include a fingerprint" }
        require(desktopNonce.length >= 32) { "desktopNonce must include enough entropy" }
        require(qrSecret.length >= 32) { "qrSecret must include enough entropy" }
    }

    fun toPairingUri(): String {
        val query = listOf(
            "v" to v.toString(),
            "sid" to sid,
            "host" to host,
            "port" to port.toString(),
            "expires_at_epoch_millis" to expiresAtEpochMillis.toString(),
            "desktop_spki_sha256" to desktopSpkiSha256,
            "desktop_nonce" to desktopNonce,
            "qr_secret" to qrSecret,
        ).joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

        return "btgun://pair?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}

data class ManualPairingPayload(
    val host: String,
    val port: Int,
    val code: String,
    val desktopNonce: String,
    val desktopSpkiSha256Suffix: String,
    val sid: String,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65_535) { "port must be between 1 and 65535" }
        require(code.matches(Regex("\\d{6}"))) { "code must be six digits" }
        require(desktopNonce.length >= 32) { "desktopNonce must include enough entropy" }
        require(desktopSpkiSha256Suffix.length >= 8) { "desktopSpkiSha256Suffix must be visible" }
        require(sid.isNotBlank()) { "sid must not be blank" }
    }
}
