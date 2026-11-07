package me.luzility.muuusic.data.discord

import android.content.Context
import androidx.core.content.edit
import me.luzility.muuusic.data.storage.KeystoreCipher
import org.json.JSONObject

class DiscordTokenStore(context: Context) {
    private val store = context.getSharedPreferences("discord", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(ALIAS)

    fun load(): DiscordTokens? = runCatching {
        val json = JSONObject(cipher.open(store.getString(KEY_TOKENS, null) ?: return null) ?: return null)
        DiscordTokens(json.getString("access"), json.optString("refresh"), json.optLong("expiresAt"))
    }.getOrNull()

    fun save(tokens: DiscordTokens) {
        val json = JSONObject()
            .put("access", tokens.access)
            .put("refresh", tokens.refresh)
            .put("expiresAt", tokens.expiresAt)
            .toString()
        store.edit { putString(KEY_TOKENS, cipher.seal(json)) }
    }

    fun clear() = store.edit { remove(KEY_TOKENS) }

    private companion object {
        const val KEY_TOKENS = "tokens"
        const val ALIAS = "muuusic_discord"
    }
}
