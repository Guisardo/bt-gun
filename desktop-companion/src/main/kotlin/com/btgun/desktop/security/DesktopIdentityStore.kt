package com.btgun.desktop.security

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class DesktopIdentity(
    val desktopSpkiSha256: String,
    val publicKey: PublicKey? = null,
    val privateKey: PrivateKey? = null,
)

interface DesktopIdentityStore {
    fun loadOrCreateIdentity(): DesktopIdentity

    companion object {
        fun default(): DesktopIdentityStore =
            FileDesktopIdentityStore(
                path = Paths.get(
                    System.getProperty("user.home"),
                    ".bt-gun",
                    "desktop-identity.p12",
                ),
            )
    }
}

class FileDesktopIdentityStore(
    private val path: Path,
    private val password: CharArray = DEFAULT_PASSWORD,
) : DesktopIdentityStore {
    override fun loadOrCreateIdentity(): DesktopIdentity {
        val keyStore = KeyStore.getInstance("JCEKS")
        if (Files.exists(path)) {
            Files.newInputStream(path).use { input -> keyStore.load(input, password) }
            val publicKey = loadSecretKey(keyStore, PUBLIC_ALIAS)
                ?.encoded
                ?.let { bytes -> KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(bytes)) }
            val privateKey = loadSecretKey(keyStore, PRIVATE_ALIAS)
                ?.encoded
                ?.let { bytes -> KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(bytes)) }
            if (publicKey != null && privateKey != null) {
                return DesktopIdentity(
                    desktopSpkiSha256 = spkiSha256(publicKey),
                    publicKey = publicKey,
                    privateKey = privateKey,
                )
            }
        } else {
            keyStore.load(null, password)
        }

        val keyPair = KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
            initialize(2048)
        }.generateKeyPair()
        keyStore.setEntry(
            PUBLIC_ALIAS,
            KeyStore.SecretKeyEntry(SecretKeySpec(keyPair.public.encoded, SECRET_KEY_ALGORITHM)),
            KeyStore.PasswordProtection(password),
        )
        keyStore.setEntry(
            PRIVATE_ALIAS,
            KeyStore.SecretKeyEntry(SecretKeySpec(keyPair.private.encoded, SECRET_KEY_ALGORITHM)),
            KeyStore.PasswordProtection(password),
        )
        Files.createDirectories(path.parent)
        Files.newOutputStream(path).use { output -> keyStore.store(output, password) }

        return DesktopIdentity(
            desktopSpkiSha256 = spkiSha256(keyPair.public),
            publicKey = keyPair.public,
            privateKey = keyPair.private,
        )
    }

    private fun loadSecretKey(keyStore: KeyStore, alias: String): SecretKey? =
        (keyStore.getEntry(alias, KeyStore.PasswordProtection(password)) as? KeyStore.SecretKeyEntry)?.secretKey

    companion object {
        private const val KEY_ALGORITHM = "RSA"
        private const val SECRET_KEY_ALGORITHM = "RAW"
        private const val PUBLIC_ALIAS = "btgun-desktop-public"
        private const val PRIVATE_ALIAS = "btgun-desktop-private"
        private val DEFAULT_PASSWORD = "bt-gun-desktop-local-identity".toCharArray()

        fun spkiSha256(publicKey: PublicKey): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
