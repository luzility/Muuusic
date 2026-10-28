package me.luzility.muuusic.data.storage

interface KeyValueStore {
    val all: Map<String, Any?>
    fun contains(key: String): Boolean
    fun getString(key: String, default: String?): String?
    fun getStringSet(key: String, default: Set<String>?): Set<String>?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getFloat(key: String, default: Float): Float
    fun edit(commit: Boolean = false, block: Editor.() -> Unit)

    interface Editor {
        fun putString(key: String, value: String?)
        fun putStringSet(key: String, value: Set<String>)
        fun putBoolean(key: String, value: Boolean)
        fun putInt(key: String, value: Int)
        fun putLong(key: String, value: Long)
        fun putFloat(key: String, value: Float)
        fun remove(key: String)
        fun clear()
    }
}

fun interface KeyValueStores {
    fun open(name: String): KeyValueStore
}
