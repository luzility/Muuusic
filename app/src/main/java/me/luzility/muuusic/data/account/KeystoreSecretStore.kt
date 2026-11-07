package me.luzility.muuusic.data.account

import android.content.Context
import androidx.core.content.edit
import me.luzility.muuusic.data.storage.KeystoreCipher

class KeystoreSecretStore(context: Context, file: String, alias: String) : SecretStore {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(alias)

    override fun load(): String? = prefs.getString(KEY, null)?.let(cipher::open)

    override fun save(value: String) = prefs.edit { putString(KEY, cipher.seal(value)) }

    override fun clear() = prefs.edit { remove(KEY) }

    private companion object {
        const val KEY = "account"
    }
}
