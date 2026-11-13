package co.topflighthealth.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The tokens the auth server hands back after sign-in or a refresh. */
data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    /** Epoch millis when the access token stops working, if known. */
    val expiresAtMillis: Long?,
)

/**
 * Keeps the sign-in tokens on the phone, encrypted with a key held in the
 * Android Keystore (the Android equivalent of the iOS Keychain). The key never
 * leaves the device, so a copied preferences file is useless on its own.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun load(): Tokens? {
        val access = decrypt(prefs.getString(KEY_ACCESS, null)) ?: return null
        return Tokens(
            accessToken = access,
            refreshToken = decrypt(prefs.getString(KEY_REFRESH, null)),
            expiresAtMillis = prefs.getLong(KEY_EXPIRES, 0L).takeIf { it > 0L },
        )
    }

    fun save(tokens: Tokens) {
        prefs.edit()
            .putString(KEY_ACCESS, encrypt(tokens.accessToken))
            .apply {
                if (tokens.refreshToken != null) putString(KEY_REFRESH, encrypt(tokens.refreshToken))
                if (tokens.expiresAtMillis != null) putLong(KEY_EXPIRES, tokens.expiresAtMillis) else remove(KEY_EXPIRES)
            }
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /** Returns null for a missing value or one this phone's key can't open. */
    private fun decrypt(stored: String?): String? {
        if (stored == null) return null
        return try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tfh_auth_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_EXPIRES = "expires"
    }
}
