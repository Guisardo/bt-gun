package com.btgun.desktop.security

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PairingProof {
    private const val LABEL = "btgun-pair-v1"
    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun transcript(
        sid: String,
        desktopNonce: String,
        androidNonce: String,
        desktopSpkiSha256: String,
        oneTimeMaterial: String,
    ): String =
        listOf(
            LABEL,
            "sid=$sid",
            "desktop_nonce=$desktopNonce",
            "android_nonce=$androidNonce",
            "desktop_spki_sha256=$desktopSpkiSha256",
            "one_time_material=$oneTimeMaterial",
        ).joinToString("\n")

    fun create(
        sid: String,
        desktopNonce: String,
        androidNonce: String,
        desktopSpkiSha256: String,
        oneTimeMaterial: String,
    ): String =
        hmacSha256(
            keyMaterial = oneTimeMaterial,
            message = transcript(
                sid = sid,
                desktopNonce = desktopNonce,
                androidNonce = androidNonce,
                desktopSpkiSha256 = desktopSpkiSha256,
                oneTimeMaterial = oneTimeMaterial,
            ),
        )

    fun hmacSha256(keyMaterial: String, message: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(keyMaterial.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun verify(
        proofHex: String,
        sid: String,
        desktopNonce: String,
        androidNonce: String,
        desktopSpkiSha256: String,
        oneTimeMaterial: String,
    ): Boolean {
        val provided = proofHex.hexToBytes() ?: return false
        val expected = create(
            sid = sid,
            desktopNonce = desktopNonce,
            androidNonce = androidNonce,
            desktopSpkiSha256 = desktopSpkiSha256,
            oneTimeMaterial = oneTimeMaterial,
        ).hexToBytes() ?: return false
        return MessageDigest.isEqual(provided, expected)
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0 || !matches(Regex("[0-9a-fA-F]+"))) {
            return null
        }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
