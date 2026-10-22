package me.luzility.muuusic.data.discord

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DiscordTokenStore(context: Context) {
    private val store = context.getSharedPreferences("discord", Context.MODE_PRIVATE)

    fun load(): DiscordTokens? = runCatching {
        val sealed = store.getString(KEY_TOKENS, null) ?: return null
        val parts = sealed.split(':')
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))))
        DiscordTokens(json.getString("access"), json.optString("refresh"), json.optLong("expiresAt"))
    }.getOrNull()

    fun save(tokens: DiscordTokens) {
        val json = JSONObject()
            .put("access", tokens.access)
            .put("refresh", tokens.refresh)
            .put("expiresAt", tokens.expiresAt)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(json.toByteArray()), Base64.NO_WRAP)
        store.edit { putString(KEY_TOKENS, sealed) }
    }

    fun clear() = store.edit { remove(KEY_TOKENS) }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_TOKENS = "tokens"
        const val ALIAS = "muuusic_discord"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
