package me.luzility.muuusic.data.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.model.ArtistPage
import me.luzility.muuusic.data.model.HomeFeed
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class InnerTubeClient(private val client: OkHttpClient) {

    @Volatile private var clientVersion: String? = null

    @Volatile private var visitorData: String? = null

    suspend fun home(params: String? = null, extraPages: Int = 2): HomeFeed = withContext(Dispatchers.IO) {
        val first = browse(JSONObject().put("browseId", HOME).apply { params?.let { put("params", it) } })
        InnerTubeParser.visitorData(first)?.let { visitorData = it }
        val feed = InnerTubeParser.home(first)
        val sections = feed.sections.toMutableList()
        var token = InnerTubeParser.continuation(first)
        var pages = 0
        while (token != null && pages++ < extraPages) {
            val next = browse(JSONObject().put("continuation", token))
            sections += InnerTubeParser.homeContinuation(next)
            token = InnerTubeParser.continuation(next)
        }
        feed.copy(sections = sections)
    }

    suspend fun artist(channelId: String, url: String): ArtistPage? = withContext(Dispatchers.IO) {
        InnerTubeParser.artist(browse(JSONObject().put("browseId", channelId)), url)
    }

    // Only the iOS client reports loudness without extra verification.
    suspend fun loudnessGainDb(videoId: String): Double? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "IOS")
                        .put("clientVersion", IOS_VERSION)
                        .put("deviceMake", "Apple")
                        .put("deviceModel", "iPhone16,2")
                        .put("osName", "iPhone")
                        .put("osVersion", "18.3.2.22D82")
                        .put("hl", "en")
                        .put("gl", "US"),
                ),
            )
        val request = Request.Builder()
            .url("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", "com.google.ios.youtube/$IOS_VERSION (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val audio = JSONObject(response.body?.string() ?: return@withContext null)
                .optJSONObject("playerConfig")?.optJSONObject("audioConfig") ?: return@withContext null
            val track = audio.optDouble("trackAbsoluteLoudnessLkfs", Double.NaN)
                .takeUnless { it.isNaN() } ?: audio.optDouble("perceptualLoudnessDb", Double.NaN)
            val target = audio.optDouble("loudnessTargetLkfs", -14.0)
            if (track.isNaN()) null else target - track
        }
    }

    private fun browse(body: JSONObject): JSONObject {
        val locale = Locale.getDefault()
        body.put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", CLIENT_NAME)
                    .put("clientVersion", version())
                    .put("hl", locale.language.ifEmpty { "en" })
                    .put("gl", locale.country.ifEmpty { "US" })
                    .apply { visitorData?.let { put("visitorData", it) } },
            ),
        )
        val request = Request.Builder()
            .url("$BASE/youtubei/v1/browse?prettyPrint=false")
            .header("User-Agent", USER_AGENT)
            .header("Origin", BASE)
            .header("X-YouTube-Client-Name", CLIENT_ID)
            .header("X-YouTube-Client-Version", version())
            .apply { visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube ${response.code}")
            return JSONObject(response.body?.string() ?: throw IOException("Empty response"))
        }
    }

    // Old web client versions get rejected, so read the current one from the site once.
    private fun version(): String {
        clientVersion?.let { return it }
        val found = runCatching {
            val request = Request.Builder().url(BASE).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                VERSION_PATTERN.find(response.body?.string().orEmpty())?.groupValues?.get(1)
            }
        }.getOrNull()
        return (found ?: FALLBACK_VERSION).also { clientVersion = it }
    }

    private companion object {
        const val BASE = "https://music.youtube.com"
        const val HOME = "FEmusic_home"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"
        const val FALLBACK_VERSION = "1.20261013.15.00"
        const val IOS_VERSION = "20.10.4"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        val VERSION_PATTERN = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"")
    }
}
