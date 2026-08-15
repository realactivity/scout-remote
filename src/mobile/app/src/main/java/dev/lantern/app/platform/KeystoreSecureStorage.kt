package dev.lantern.app.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.lantern.core.platform.SecureStorage
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keystore-backed storage for encrypted local state (spec §10, §16.1).
 * MSAL cache integration remains a release gate.
 */
class KeystoreSecureStorage(context: Context) : SecureStorage {
    private val prefs = context.getSharedPreferences("lantern.secure", Context.MODE_PRIVATE)
    private val secretKey: SecretKey by lazy(::loadOrCreateKey)

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val encoded = prefs.getString(key, null) ?: return@withContext null
        decrypt(key, Base64.getDecoder().decode(encoded))
    }

    override suspend fun set(key: String, value: ByteArray) = withContext(Dispatchers.IO) {
        val encoded = Base64.getEncoder().encodeToString(encrypt(key, value))
        check(prefs.edit().putString(key, encoded).commit()) {
            "Secure storage write failed"
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        check(prefs.edit().remove(key).commit()) { "Secure storage remove failed" }
    }

    private fun encrypt(key: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(FORMAT_VERSION, cipher.iv.size.toByte()) + cipher.iv + ciphertext
    }

    private fun decrypt(key: String, payload: ByteArray): ByteArray {
        require(payload.size >= 2 && payload[0] == FORMAT_VERSION) { "Unsupported secure storage payload" }
        val ivLength = payload[1].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > 2 + ivLength) { "Invalid secure storage payload" }
        val iv = payload.copyOfRange(2, 2 + ivLength)
        val ciphertext = payload.copyOfRange(2 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dev.lantern.secure-storage.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
    }
}
