package me.luzility.muuusic.data.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.model.ArtistPage
import me.luzility.muuusic.data.model.HomeFeed
import me.luzility.muuusic.data.model.Song
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
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

    suspend fun accountName(cookie: String): String? = withContext(Dispatchers.IO) {
        InnerTubeParser.accountName(post("account/account_menu", JSONObject(), cookie))
    }

    suspend fun likedSongs(cookie: String): List<Song> = withContext(Dispatchers.IO) {
        pages(cookie, JSONObject().put("browseId", InnerTubeParser.LIKED_PLAYLIST), InnerTubeParser::librarySongs).distinctBy { it.id }
    }

    suspend fun libraryPlaylists(cookie: String): List<LibraryPlaylist> = withContext(Dispatchers.IO) {
        pages(cookie, JSONObject().put("browseId", LIBRARY_PLAYLISTS), InnerTubeParser::libraryPlaylists).distinctBy { it.browseId }
    }

    suspend fun playlistSongs(cookie: String, browseId: String): List<Song> = withContext(Dispatchers.IO) {
        pages(cookie, JSONObject().put("browseId", browseId), InnerTubeParser::librarySongs).distinctBy { it.id }
    }

    private fun <T> pages(cookie: String, first: JSONObject, parse: (JSONObject) -> List<T>): List<T> {
        val found = mutableListOf<T>()
        var page = browse(first, cookie)
        repeat(MAX_PAGES) {
            val items = parse(page)
            found += items
            val token = InnerTubeParser.nextPage(page)
            if (token == null || items.isEmpty()) return found
            page = browse(JSONObject().put("continuation", token), cookie)
        }
        return found
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

    private fun browse(body: JSONObject, cookie: String? = null): JSONObject = post("browse", body, cookie)

    private fun post(endpoint: String, body: JSONObject, cookie: String?): JSONObject {
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
                    .apply { visitorData?.takeIf { cookie == null }?.let { put("visitorData", it) } },
            ),
        )
        val request = Request.Builder()
            .url("$BASE/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", USER_AGENT)
            .header("Origin", BASE)
            .header("X-YouTube-Client-Name", CLIENT_ID)
            .header("X-YouTube-Client-Version", version())
            .apply { visitorData?.takeIf { cookie == null }?.let { header("X-Goog-Visitor-Id", it) } }
            .apply { cookie?.let { signIn(it) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube ${response.code}")
            return JSONObject(response.body?.string() ?: throw IOException("Empty response"))
        }
    }

    private fun Request.Builder.signIn(cookie: String) {
        header("Cookie", cookie)
        header("X-Goog-AuthUser", "0")
        val sapisid = SAPISID_PATTERN.find(cookie)?.groupValues?.get(1) ?: return
        val time = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$time $sapisid $BASE".toByteArray())
        header("Authorization", "SAPISIDHASH ${time}_${digest.joinToString("") { "%02x".format(it) }}")
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
        const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
        const val MAX_PAGES = 100
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"
        const val FALLBACK_VERSION = "1.20261013.15.00"
        const val IOS_VERSION = "20.10.4"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        val VERSION_PATTERN = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"")
        val SAPISID_PATTERN = Regex("(?:^|;\\s*)(?:__Secure-3PAPISID|SAPISID)=([^;]+)")
    }
}
