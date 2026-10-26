package me.luzility.muuusic.data.storage

import android.content.Context
import android.content.SharedPreferences

class AndroidKeyValueStores(private val context: Context) : KeyValueStores {
    override fun open(name: String): KeyValueStore = SharedPreferencesStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
}

private class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override val all: Map<String, Any?> get() = prefs.all
    override fun contains(key: String) = prefs.contains(key)
    override fun getString(key: String, default: String?) = prefs.getString(key, default)
    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = prefs.getStringSet(key, default)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        val editor = prefs.edit()
        object : KeyValueStore.Editor {
            override fun putString(key: String, value: String?) { editor.putString(key, value) }
            override fun putStringSet(key: String, value: Set<String>) { editor.putStringSet(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
            override fun putFloat(key: String, value: Float) { editor.putFloat(key, value) }
            override fun remove(key: String) { editor.remove(key) }
            override fun clear() { editor.clear() }
        }.block()
        if (commit) editor.commit() else editor.apply()
    }
}
