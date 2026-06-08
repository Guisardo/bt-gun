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
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
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
    private val passwordProvider: DesktopIdentityPasswordProvider = SidecarDesktopIdentityPasswordProvider(
        path.resolveSibling("${path.fileName}.key"),
    ),
) : DesktopIdentityStore {
    constructor(path: Path, password: CharArray) : this(path, StaticDesktopIdentityPasswordProvider(password))

    override fun loadOrCreateIdentity(): DesktopIdentity {
        val password = passwordProvider.loadOrCreatePassword()
        if (Files.exists(path)) {
            loadIdentity(password)?.let { return it }
            loadIdentity(LEGACY_PASSWORD)?.let { identity ->
                storeIdentity(identity.publicKey ?: return identity, identity.privateKey ?: return identity, password)
                return identity
            }
        }

        val keyPair = KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
            initialize(2048)
        }.generateKeyPair()
        storeIdentity(keyPair.public, keyPair.private, password)

        return DesktopIdentity(
            desktopSpkiSha256 = spkiSha256(keyPair.public),
            publicKey = keyPair.public,
            privateKey = keyPair.private,
        )
    }

    private fun loadIdentity(password: CharArray): DesktopIdentity? =
        runCatching {
            val keyStore = KeyStore.getInstance("JCEKS")
            Files.newInputStream(path).use { input -> keyStore.load(input, password) }
            val publicKey = loadSecretKey(keyStore, PUBLIC_ALIAS, password)
                ?.encoded
                ?.let { bytes -> KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(bytes)) }
            val privateKey = loadSecretKey(keyStore, PRIVATE_ALIAS, password)
                ?.encoded
                ?.let { bytes -> KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(bytes)) }
            if (publicKey != null && privateKey != null) {
                DesktopIdentity(
                    desktopSpkiSha256 = spkiSha256(publicKey),
                    publicKey = publicKey,
                    privateKey = privateKey,
                )
            } else {
                null
            }
        }.getOrNull()

    private fun storeIdentity(publicKey: PublicKey, privateKey: PrivateKey, password: CharArray) {
        val keyStore = KeyStore.getInstance("JCEKS").apply {
            load(null, password)
        }
        keyStore.setEntry(
            PUBLIC_ALIAS,
            KeyStore.SecretKeyEntry(SecretKeySpec(publicKey.encoded, SECRET_KEY_ALGORITHM)),
            KeyStore.PasswordProtection(password),
        )
        keyStore.setEntry(
            PRIVATE_ALIAS,
            KeyStore.SecretKeyEntry(SecretKeySpec(privateKey.encoded, SECRET_KEY_ALGORITHM)),
            KeyStore.PasswordProtection(password),
        )
        Files.createDirectories(path.parent)
        Files.newOutputStream(path).use { output -> keyStore.store(output, password) }
    }

    private fun loadSecretKey(keyStore: KeyStore, alias: String, password: CharArray): SecretKey? =
        (keyStore.getEntry(alias, KeyStore.PasswordProtection(password)) as? KeyStore.SecretKeyEntry)?.secretKey

    companion object {
        private const val KEY_ALGORITHM = "RSA"
        private const val SECRET_KEY_ALGORITHM = "RAW"
        private const val PUBLIC_ALIAS = "btgun-desktop-public"
        private const val PRIVATE_ALIAS = "btgun-desktop-private"
        private val LEGACY_PASSWORD = "bt-gun-desktop-local-identity".toCharArray()

        fun spkiSha256(publicKey: PublicKey): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

interface DesktopIdentityPasswordProvider {
    fun loadOrCreatePassword(): CharArray
}

private class StaticDesktopIdentityPasswordProvider(
    private val password: CharArray,
) : DesktopIdentityPasswordProvider {
    override fun loadOrCreatePassword(): CharArray = password
}

private class SidecarDesktopIdentityPasswordProvider(
    private val path: Path,
    private val random: SecureRandom = SecureRandom(),
) : DesktopIdentityPasswordProvider {
    override fun loadOrCreatePassword(): CharArray {
        if (Files.exists(path)) {
            return Files.readString(path).trim().toCharArray()
        }
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        Files.createDirectories(path.parent)
        Files.writeString(path, password)
        path.toFile().apply {
            setReadable(false, false)
            setWritable(false, false)
            setReadable(true, true)
            setWritable(true, true)
        }
        return password.toCharArray()
    }
}
