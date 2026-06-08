package com.btgun.host.transport

import java.util.Base64

data class InputStreamConfig(
    val streamSessionIdHex: String,
    val udpHost: String,
    val udpPort: Int,
    val hmacSha256KeyBase64Url: String,
    val snapshotHz: Int,
    val frameAgeLimitMs: Long,
    val streamTimeoutMs: Long,
    val controlDisconnectGraceMs: Long,
) {
    init {
        require(streamSessionIdHex.length == 32 && streamSessionIdHex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "streamSessionIdHex must be 16 lowercase hex bytes"
        }
        require(udpHost.isNotBlank()) { "udpHost must not be blank" }
        require(udpPort in 1..65_535) { "udpPort must be in 1..65535" }
        require(snapshotHz > 0) { "snapshotHz must be positive" }
        require(frameAgeLimitMs > 0L) { "frameAgeLimitMs must be positive" }
        require(streamTimeoutMs > 0L) { "streamTimeoutMs must be positive" }
        require(controlDisconnectGraceMs >= 0L) { "controlDisconnectGraceMs must be non-negative" }
        require(hmacKeyBytes().size == 32) { "stream auth secret must be 32 bytes" }
    }

    fun streamSessionIdBytes(): ByteArray = streamSessionIdHex.hexToBytes()

    fun hmacKeyBytes(): ByteArray = Base64.getUrlDecoder().decode(hmacSha256KeyBase64Url)
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
