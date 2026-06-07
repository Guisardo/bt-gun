package com.btgun.host.session

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

data class DesktopControlClientConfig(
    val url: String,
    val expectedDesktopSpkiSha256: String,
    val maxMessageBytes: Int = 16 * 1024,
)

data class ControlProofRequest(
    val sid: String,
    val androidNonce: String,
    val desktopSpkiSha256: String,
    val proofHex: String,
)

interface DesktopControlSocket {
    fun send(text: String): Boolean
    fun close()
}

class DesktopControlClient(
    private val config: DesktopControlClientConfig,
    private val socketFactory: (Request, WebSocketListener) -> DesktopControlSocket = ::defaultSocket,
) {
    private var socket: DesktopControlSocket? = null

    fun connect(proofRequest: ControlProofRequest): DesktopControlConnectResult {
        val trust = verifyPresentedFingerprint(proofRequest.desktopSpkiSha256)
        if (trust is DesktopControlConnectResult.TrustMismatch) {
            return trust
        }
        val request = Request.Builder()
            .url(config.url)
            .header("X-BT-Gun-Desktop-Fingerprint", config.expectedDesktopSpkiSha256)
            .header("X-BT-Gun-Session", proofRequest.sid)
            .build()

        socket = socketFactory(request, object : WebSocketListener() {})
        return DesktopControlConnectResult.Connected
    }

    fun send(envelope: ControlEnvelope): DesktopControlSendResult {
        val encoded = ControlEnvelopeCodec.encode(envelope)
        return when (val decoded = ControlEnvelopeCodec.decode(encoded, maxBytes = Int.MAX_VALUE)) {
            is ControlDecodeResult.Rejected -> DesktopControlSendResult.Rejected(decoded.error)
            is ControlDecodeResult.Accepted -> {
                if (encoded.toByteArray(Charsets.UTF_8).size > config.maxMessageBytes) {
                    return DesktopControlSendResult.Rejected(ControlEnvelopeError.OVERSIZED)
                }
                val activeSocket = socket ?: return DesktopControlSendResult.NotConnected
                if (activeSocket.send(encoded)) {
                    DesktopControlSendResult.Sent
                } else {
                    DesktopControlSendResult.Failed("socket rejected message")
                }
            }
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }

    fun verifyPresentedFingerprint(presented: String): DesktopControlConnectResult =
        if (presented.equals(config.expectedDesktopSpkiSha256, ignoreCase = true)) {
            DesktopControlConnectResult.Connected
        } else {
            DesktopControlConnectResult.TrustMismatch(
                expected = config.expectedDesktopSpkiSha256,
                presented = presented,
            )
        }

    fun certificatePin(): String =
        "sha256/${config.expectedDesktopSpkiSha256.hexToBytes().toByteString().base64()}"

    private companion object {
        fun defaultSocket(request: Request, listener: WebSocketListener): DesktopControlSocket {
            val host = request.url.host
            val pin = request.header("X-BT-Gun-Desktop-Fingerprint")
                ?: throw IllegalArgumentException("missing expected desktop fingerprint")
            val client = OkHttpClient.Builder()
                .certificatePinner(
                    CertificatePinner.Builder()
                        .add(host, "sha256/${pin.hexToBytes().toByteString().base64()}")
                        .build(),
                )
                .build()
            return OkHttpDesktopControlSocket(client.newWebSocket(request, listener))
        }
    }
}

sealed interface DesktopControlConnectResult {
    data object Connected : DesktopControlConnectResult
    data class TrustMismatch(val expected: String, val presented: String) : DesktopControlConnectResult
}

sealed interface DesktopControlSendResult {
    data object Sent : DesktopControlSendResult
    data object NotConnected : DesktopControlSendResult
    data class Rejected(val error: ControlEnvelopeError) : DesktopControlSendResult
    data class Failed(val reason: String) : DesktopControlSendResult
}

private class OkHttpDesktopControlSocket(
    private val webSocket: WebSocket,
) : DesktopControlSocket {
    override fun send(text: String): Boolean = webSocket.send(text)

    override fun close() {
        webSocket.close(1000, "client closed")
    }
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && matches(Regex("[0-9a-fA-F]+"))) {
        "fingerprint must be hex"
    }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
