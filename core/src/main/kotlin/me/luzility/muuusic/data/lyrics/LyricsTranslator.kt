package me.luzility.muuusic.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LyricsTranslator(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    suspend fun translate(lines: List<String>, target: String): Map<String, String> = withContext(Dispatchers.IO) {
        val unique = lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val key = "${unique.hashCode()}@$target"
        cache[key]?.let { return@withContext it }

        val results = mutableListOf<Pair<String, String>>()
        unique.chunkedByLength().forEach { chunk -> results += request(chunk, target) }

        val foreign = results.withIndex().filter { (_, result) -> !result.second.equals(target, ignoreCase = true) }
        val translations = if (foreign.size * 2 < results.size) {
            emptyMap()
        } else {
            foreign.mapNotNull { (index, result) ->
                val original = unique[index]
                result.first.takeIf { !it.equals(original, ignoreCase = true) }?.let { original to it }
            }.toMap()
        }
        cache[key] = translations
        translations
    }

    private fun request(lines: List<String>, target: String): List<Pair<String, String>> {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("client", "dict-chrome-ex")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", target)
            .build()
        val body = FormBody.Builder().apply { lines.forEach { add("q", it) } }.build()
        val response = client.newCall(Request.Builder().url(url).post(body).build()).execute()
        val text = response.use {
            if (!it.isSuccessful) throw IOException("Translate ${it.code}")
            it.body.string()
        }
        val array = JSONArray(text)
        if (array.length() != lines.size) throw IOException("Translate returned ${array.length()} of ${lines.size}")
        return List(array.length()) { i ->
            when (val item = array.get(i)) {
                is JSONArray -> item.optString(0) to item.optString(1)
                else -> item.toString() to ""
            }
        }
    }

    private fun List<String>.chunkedByLength(): List<List<String>> {
        val chunks = mutableListOf<MutableList<String>>()
        var length = 0
        forEach { line ->
            if (chunks.isEmpty() || length + line.length > MAX_CHARS || chunks.last().size >= MAX_LINES) {
                chunks += mutableListOf<String>()
                length = 0
            }
            chunks.last() += line
            length += line.length
        }
        return chunks
    }

    companion object {
        private const val BASE = "https://clients5.google.com/translate_a/t"
        private const val MAX_CHARS = 3_000
        private const val MAX_LINES = 80

        fun targetFor(locale: Locale): String = when (locale.language) {
            "in" -> "id"
            "iw" -> "he"
            "zh" -> if (locale.country == "TW" || locale.country == "HK" || locale.script == "Hant") "zh-TW" else "zh-CN"
            else -> locale.language
        }
    }
}
