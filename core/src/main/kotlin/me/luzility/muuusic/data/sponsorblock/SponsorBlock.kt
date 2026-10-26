package me.luzility.muuusic.data.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class Segment(val startMs: Long, val endMs: Long, val category: String)

class SponsorBlock(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, List<Segment>>()

    fun cached(videoId: String): List<Segment>? = cache[videoId]

    suspend fun segments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        cache[videoId]?.let { return@withContext it }
        // Ask by hash prefix so the server doesn't learn which video is playing.
        val prefix = sha256(videoId).take(4)
        val url = "$API/skipSegments/$prefix".toHttpUrl().newBuilder()
            .addQueryParameter("categories", JSONArray(CATEGORIES).toString())
            .addQueryParameter("actionType", "skip")
            .build()
        val segments = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            when {
                response.code == 404 -> emptyList()
                !response.isSuccessful -> return@withContext emptyList()
                else -> parse(response.body?.string().orEmpty(), videoId)
            }
        }
        cache[videoId] = segments
        segments
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val API = "https://sponsor.ajay.app/api"
        private val CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "music_offtopic")
        private const val MIN_LENGTH_MS = 1_000

        internal fun parse(json: String, videoId: String): List<Segment> {
            val videos = JSONArray(json)
            for (i in 0 until videos.length()) {
                val video = videos.getJSONObject(i)
                if (video.optString("videoID") != videoId) continue
                val list = video.optJSONArray("segments") ?: return emptyList()
                return (0 until list.length()).mapNotNull { j ->
                    val item = list.getJSONObject(j)
                    if (item.optString("actionType", "skip") != "skip") return@mapNotNull null
                    val range = item.optJSONArray("segment") ?: return@mapNotNull null
                    val start = (range.optDouble(0) * 1000).toLong()
                    val end = (range.optDouble(1) * 1000).toLong()
                    if (end - start < MIN_LENGTH_MS) null else Segment(start, end, item.optString("category"))
                }.sortedBy { it.startMs }
            }
            return emptyList()
        }
    }
}
