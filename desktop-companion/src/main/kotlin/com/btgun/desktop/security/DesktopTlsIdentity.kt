package com.btgun.desktop.security

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DesktopTlsIdentity {
    private const val TLS_ALIAS = "btgun-desktop-tls"
    private val TLS_PASSWORD = "bt-gun-desktop-tls".toCharArray()

    fun keyStoreFor(identity: DesktopIdentity, certificateHost: String): DesktopTlsKeyStore {
        val privateKey = requireNotNull(identity.privateKey) { "desktop identity is missing private key" }
        val publicKey = requireNotNull(identity.publicKey) { "desktop identity is missing public key" }
        val certificate = SelfSignedCertificate.create(
            publicKey = publicKey,
            privateKey = privateKey,
            host = certificateHost,
        )
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, TLS_PASSWORD)
            setKeyEntry(TLS_ALIAS, privateKey, TLS_PASSWORD, arrayOf(certificate))
        }
        return DesktopTlsKeyStore(
            keyStore = keyStore,
            keyAlias = TLS_ALIAS,
            password = TLS_PASSWORD,
            certificate = certificate,
        )
    }
}

data class DesktopTlsKeyStore(
    val keyStore: KeyStore,
    val keyAlias: String,
    val password: CharArray,
    val certificate: X509Certificate,
)

private object SelfSignedCertificate {
    private const val COMMON_NAME = "BT Gun Desktop"
    private val random = SecureRandom()
    private val utcFormatter = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun create(publicKey: PublicKey, privateKey: PrivateKey, host: String): X509Certificate {
        val now = Instant.now()
        val tbs = tbsCertificate(
            publicKey = publicKey,
            host = host,
            serial = positiveSerial(),
            notBefore = now.minusSeconds(60),
            notAfter = now.plusSeconds(366L * 24L * 60L * 60L * 10L),
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbs)
            sign()
        }
        val certificateDer = Der.sequence(
            tbs,
            algorithmIdentifier(),
            Der.bitString(signature),
        )
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateDer)) as X509Certificate
    }

    private fun tbsCertificate(
        publicKey: PublicKey,
        host: String,
        serial: BigInteger,
        notBefore: Instant,
        notAfter: Instant,
    ): ByteArray =
        Der.sequence(
            Der.explicit(0, Der.integer(BigInteger.valueOf(2L))),
            Der.integer(serial),
            algorithmIdentifier(),
            name(),
            Der.sequence(Der.utcTime(notBefore), Der.utcTime(notAfter)),
            name(),
            publicKey.encoded,
            Der.explicit(3, extensions(host)),
        )

    private fun extensions(host: String): ByteArray =
        Der.sequence(
            Der.sequence(
                Der.oid("2.5.29.17"),
                Der.octetString(
                    Der.sequence(generalName(host)),
                ),
            ),
        )

    private fun generalName(host: String): ByteArray {
        val trimmed = host.trim()
        val ipBytes = if (trimmed.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) || trimmed.contains(":")) {
            runCatching { InetAddress.getByName(trimmed).address }.getOrNull()
        } else {
            null
        }
        return if (ipBytes != null) {
            Der.contextPrimitive(7, ipBytes)
        } else {
            Der.contextPrimitive(2, trimmed.toByteArray(Charsets.US_ASCII))
        }
    }

    private fun name(): ByteArray =
        Der.sequence(
            Der.set(
                Der.sequence(
                    Der.oid("2.5.4.3"),
                    Der.utf8String(COMMON_NAME),
                ),
            ),
        )

    private fun algorithmIdentifier(): ByteArray =
        Der.sequence(Der.oid("1.2.840.113549.1.1.11"), Der.nullValue())

    private fun positiveSerial(): BigInteger {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        bytes[0] = (bytes[0].toInt() and 0x7f).toByte()
        return BigInteger(1, bytes)
    }

    private object Der {
        fun sequence(vararg values: ByteArray): ByteArray = tagged(0x30, values.concat())

        fun set(vararg values: ByteArray): ByteArray = tagged(0x31, values.concat())

        fun explicit(tag: Int, value: ByteArray): ByteArray = tagged(0xa0 + tag, value)

        fun contextPrimitive(tag: Int, value: ByteArray): ByteArray = tagged(0x80 + tag, value)

        fun integer(value: BigInteger): ByteArray = tagged(0x02, value.toByteArray())

        fun oid(value: String): ByteArray {
            val parts = value.split(".").map(String::toLong)
            val body = mutableListOf<Byte>()
            body += (parts[0] * 40 + parts[1]).toByte()
            parts.drop(2).forEach { part ->
                val encoded = mutableListOf<Byte>((part and 0x7f).toByte())
                var next = part ushr 7
                while (next > 0) {
                    encoded += ((next and 0x7f) or 0x80).toByte()
                    next = next ushr 7
                }
                body += encoded.asReversed()
            }
            return tagged(0x06, body.toByteArray())
        }

        fun nullValue(): ByteArray = tagged(0x05, byteArrayOf())

        fun utf8String(value: String): ByteArray = tagged(0x0c, value.toByteArray(Charsets.UTF_8))

        fun utcTime(value: Instant): ByteArray =
            tagged(0x17, utcFormatter.format(value).toByteArray(Charsets.US_ASCII))

        fun bitString(value: ByteArray): ByteArray = tagged(0x03, byteArrayOf(0) + value)

        fun octetString(value: ByteArray): ByteArray = tagged(0x04, value)

        private fun tagged(tag: Int, value: ByteArray): ByteArray =
            byteArrayOf(tag.toByte()) + length(value.size) + value

        private fun length(size: Int): ByteArray =
            if (size < 128) {
                byteArrayOf(size.toByte())
            } else {
                val bytes = BigInteger.valueOf(size.toLong()).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
                byteArrayOf((0x80 or bytes.size).toByte()) + bytes
            }

        private fun Array<out ByteArray>.concat(): ByteArray {
            val output = ByteArray(sumOf { it.size })
            var offset = 0
            forEach { value ->
                value.copyInto(output, offset)
                offset += value.size
            }
            return output
        }
    }
}
