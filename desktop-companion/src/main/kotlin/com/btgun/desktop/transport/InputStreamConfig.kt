package com.btgun.desktop.transport

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
        require(snapshotHz in MIN_SNAPSHOT_HZ..MAX_SNAPSHOT_HZ) { "snapshotHz out of range" }
        require(frameAgeLimitMs in MIN_FRAME_AGE_LIMIT_MS..MAX_FRAME_AGE_LIMIT_MS) { "frameAgeLimitMs out of range" }
        require(streamTimeoutMs in MIN_STREAM_TIMEOUT_MS..MAX_STREAM_TIMEOUT_MS) { "streamTimeoutMs out of range" }
        require(controlDisconnectGraceMs in MIN_CONTROL_DISCONNECT_GRACE_MS..MAX_CONTROL_DISCONNECT_GRACE_MS) {
            "controlDisconnectGraceMs out of range"
        }
        require(hmacKeyBytes().size == 32) { "stream auth secret must be 32 bytes" }
    }

    fun streamSessionIdBytes(): ByteArray = streamSessionIdHex.hexToBytes()

    fun hmacKeyBytes(): ByteArray = Base64.getUrlDecoder().decode(hmacSha256KeyBase64Url)

    companion object {
        const val MIN_SNAPSHOT_HZ = 1
        const val MAX_SNAPSHOT_HZ = 240
        const val MIN_FRAME_AGE_LIMIT_MS = 1L
        const val MAX_FRAME_AGE_LIMIT_MS = 5_000L
        const val MIN_STREAM_TIMEOUT_MS = 1L
        const val MAX_STREAM_TIMEOUT_MS = 10_000L
        const val MIN_CONTROL_DISCONNECT_GRACE_MS = 0L
        const val MAX_CONTROL_DISCONNECT_GRACE_MS = 10_000L
    }
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
