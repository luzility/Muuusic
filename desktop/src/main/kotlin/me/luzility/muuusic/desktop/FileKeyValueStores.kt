package me.luzility.muuusic.desktop

import me.luzility.muuusic.data.storage.KeyValueStore
import me.luzility.muuusic.data.storage.KeyValueStores
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileKeyValueStores(private val dir: File) : KeyValueStores {
    private val opened = HashMap<String, KeyValueStore>()

    override fun open(name: String): KeyValueStore = synchronized(opened) {
        opened.getOrPut(name) { JsonFileStore(File(dir, "$name.json")) }
    }
}

private class JsonFileStore(private val file: File) : KeyValueStore {
    private val values: MutableMap<String, Any?> = load()

    override val all: Map<String, Any?> get() = synchronized(values) { values.toMap() }
    override fun contains(key: String) = synchronized(values) { key in values }
    override fun getString(key: String, default: String?) = read(key) as? String ?: default
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, default: Set<String>?) = read(key) as? Set<String> ?: default
    override fun getBoolean(key: String, default: Boolean) = read(key) as? Boolean ?: default
    override fun getInt(key: String, default: Int) = (read(key) as? Number)?.toInt() ?: default
    override fun getLong(key: String, default: Long) = (read(key) as? Number)?.toLong() ?: default
    override fun getFloat(key: String, default: Float) = (read(key) as? Number)?.toFloat() ?: default

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        synchronized(values) {
            object : KeyValueStore.Editor {
                override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
                override fun putStringSet(key: String, value: Set<String>) { values[key] = value.toSet() }
                override fun putBoolean(key: String, value: Boolean) { values[key] = value }
                override fun putInt(key: String, value: Int) { values[key] = value }
                override fun putLong(key: String, value: Long) { values[key] = value }
                override fun putFloat(key: String, value: Float) { values[key] = value }
                override fun remove(key: String) { values.remove(key) }
                override fun clear() { values.clear() }
            }.block()
            save()
        }
    }

    private fun read(key: String): Any? = synchronized(values) { values[key] }

    private fun load(): MutableMap<String, Any?> {
        if (!file.exists()) return HashMap()
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return HashMap()
        val map = HashMap<String, Any?>()
        json.keys().forEach { key ->
            val entry = json.optJSONObject(key) ?: return@forEach
            map[key] = when (entry.optString("t")) {
                "s" -> entry.getString("v")
                "b" -> entry.getBoolean("v")
                "i" -> entry.getInt("v")
                "l" -> entry.getLong("v")
                "f" -> entry.getDouble("v").toFloat()
                "set" -> entry.getJSONArray("v").let { array -> List(array.length()) { array.getString(it) }.toSet() }
                else -> return@forEach
            }
        }
        return map
    }

    private fun save() {
        val json = JSONObject()
        values.forEach { (key, value) ->
            val (type, stored) = when (value) {
                is String -> "s" to value
                is Boolean -> "b" to value
                is Int -> "i" to value
                is Long -> "l" to value
                is Float -> "f" to value.toDouble()
                is Set<*> -> "set" to JSONArray(value.toList())
                else -> return@forEach
            }
            json.put(key, JSONObject().put("t", type).put("v", stored))
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }
}
