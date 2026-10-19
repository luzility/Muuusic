package me.luzility.muuusic.data.search

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.Locale

class SearchHistory(context: Context) {
    private val store = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    private val _queries = MutableStateFlow(read())
    val queries: StateFlow<List<String>> = _queries.asStateFlow()

    fun add(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        // Same query in different case counts once; Locale.ROOT keeps "I" and "ı" apart properly.
        val key = clean.lowercase(Locale.ROOT)
        save(listOf(clean) + _queries.value.filterNot { it.lowercase(Locale.ROOT) == key }.take(MAX - 1))
    }

    fun remove(query: String) = save(_queries.value - query)

    fun clear() = save(emptyList())

    fun reload() {
        _queries.value = read()
    }

    private fun save(queries: List<String>) {
        _queries.value = queries
        store.edit { putString(KEY, JSONArray(queries).toString()) }
    }

    private fun read(): List<String> = runCatching {
        val array = JSONArray(store.getString(KEY, "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "history"
        const val MAX = 20
    }
}
