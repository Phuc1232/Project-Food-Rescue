package vn.foodrescue.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import vn.foodrescue.app.BuildConfig
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class SessionTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)
interface SessionTokenStore {
    fun read(): SessionTokens?
    fun save(value: SessionTokens)
    fun clear()
}

/** Only encrypted session tokens are persisted. Android Keystore holds the non-exportable AES key. */
@Singleton
class EncryptedSessionTokenStore @Inject constructor(@ApplicationContext context: Context) : SessionTokenStore {
    private val namespace = MessageDigest.getInstance("SHA-256").digest(BuildConfig.API_BASE_URL.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(20)
    private val alias = "foodrescue-session-$namespace"
    private val prefs = context.getSharedPreferences(alias, Context.MODE_PRIVATE)
    private val json = Gson()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized override fun read(): SessionTokens? {
        val encoded = prefs.getString("session", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 28)
            val key = keyStore.getKey(alias, null) as? SecretKey ?: error("Missing session key")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(BuildConfig.API_BASE_URL.toByteArray())
            json.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), SessionTokens::class.java)
                .also { require(!it.accessToken.isNullOrBlank() && !it.refreshToken.isNullOrBlank()) }
        } catch (_: Exception) { clear(); null }
    }
    @Synchronized override fun save(value: SessionTokens) {
        val key = (keyStore.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(BuildConfig.API_BASE_URL.toByteArray())
        val encrypted = cipher.iv + cipher.doFinal(json.toJson(value).toByteArray())
        check(prefs.edit().putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Cannot save session" }
    }
    @Synchronized override fun clear() { check(prefs.edit().remove("session").commit()) { "Cannot clear session" } }
}
