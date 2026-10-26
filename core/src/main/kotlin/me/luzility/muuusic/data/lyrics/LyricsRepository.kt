package me.luzility.muuusic.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.model.Song
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class LyricsRepository(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, Lyrics>()

    fun cached(song: Song): Lyrics? = cache[song.id]

    suspend fun lyricsFor(song: Song, durationSec: Long): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it.takeUnless(Lyrics::isEmpty) }

        val query = TitleCleaner.clean(song.title, song.artist)
        val attempts = listOf(
            { exactMatch(query, durationSec) },
            { pickBest(search { addQueryParameter("track_name", query.title).addQueryParameter("artist_name", query.artist) }, durationSec) },
            { pickBest(search { addQueryParameter("q", "${query.artist} ${query.title}") }, durationSec) },
        )
        // Each lookup is a fallback for the previous one; a failing step only matters if all fail.
        var best: JSONObject? = null
        var failure: Exception? = null
        for (attempt in attempts) {
            try {
                best = attempt()
            } catch (e: java.io.IOException) {
                failure = e
            }
            if (best != null) break
        }
        // "Not found" is only trustworthy (and cacheable) when every lookup actually answered.
        if (best == null) failure?.let { throw it }

        val lyrics = best?.toLyrics() ?: Lyrics(null, null)
        cache[song.id] = lyrics
        lyrics.takeUnless(Lyrics::isEmpty)
    }

    private fun exactMatch(query: TitleCleaner.Query, durationSec: Long): JSONObject? {
        if (durationSec <= 0 || query.artist.isBlank()) return null
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", query.title)
            .addQueryParameter("artist_name", query.artist)
            .addQueryParameter("duration", durationSec.toString())
            .build()
        return fetch(url)?.let(::JSONObject)
    }

    private fun search(params: HttpUrl.Builder.() -> Unit): List<JSONObject> {
        val url = "$BASE/search".toHttpUrl().newBuilder().apply(params).build()
        val body = fetch(url) ?: return emptyList()
        val array = JSONArray(body)
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun pickBest(results: List<JSONObject>, durationSec: Long): JSONObject? {
        val usable = results.filter { !it.optBoolean("instrumental") && (it.hasText("syncedLyrics") || it.hasText("plainLyrics")) }
        fun durationOk(o: JSONObject) = durationSec <= 0 || abs(o.optDouble("duration", 0.0) - durationSec) <= 4
        return usable.firstOrNull { durationOk(it) && it.hasText("syncedLyrics") }
            ?: usable.firstOrNull { durationOk(it) }
            ?: usable.firstOrNull { it.hasText("syncedLyrics") }
            ?: usable.firstOrNull()
    }

    private fun JSONObject.toLyrics(): Lyrics {
        if (optBoolean("instrumental")) return Lyrics(null, null)
        val synced = optNullableString("syncedLyrics")?.let(LrcParser::parse)?.takeIf { it.isNotEmpty() }
        return Lyrics(synced, optNullableString("plainLyrics"))
    }

    // Null on 404; LRCLIB sheds load with 503/429, so those are retried with backoff.
    private fun fetch(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        var attempt = 0
        while (true) {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> return null
                    response.isSuccessful -> return response.body?.string()
                    (response.code == 503 || response.code == 429) && attempt < RETRY_DELAYS_MS.size -> Unit
                    else -> throw java.io.IOException("LRCLIB ${response.code}")
                }
            }
            Thread.sleep(RETRY_DELAYS_MS[attempt++])
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.hasText(key: String) = optNullableString(key) != null

    private companion object {
        const val BASE = "https://lrclib.net/api"
        val RETRY_DELAYS_MS = longArrayOf(400, 1_200, 2_500)
        // LRCLIB asks clients to identify themselves.
        val USER_AGENT get() = "Muuusic ${AppInfo.versionName} (open-source music player)"
    }
}
