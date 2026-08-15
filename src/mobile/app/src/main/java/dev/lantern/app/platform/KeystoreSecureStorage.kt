package dev.lantern.app.platform

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.lantern.core.platform.SecureStorage
import java.util.Base64

/**
 * Keystore-backed secure storage for the MSAL token cache (spec §10, §16.1).
 */
class KeystoreSecureStorage(context: Context) : SecureStorage {
    private val prefs = EncryptedSharedPreferences.create(
        "lantern.secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun get(key: String): ByteArray? {
        val encoded = prefs.getString(key, null) ?: return null
        return Base64.getDecoder().decode(encoded)
    }

    override suspend fun set(key: String, value: ByteArray) {
        prefs.edit().putString(key, Base64.getEncoder().encodeToString(value)).apply()
    }

    override suspend fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
