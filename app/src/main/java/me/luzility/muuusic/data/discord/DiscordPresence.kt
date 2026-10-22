package me.luzility.muuusic.data.discord

import android.util.Log
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ListeningActivity(
    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val startMs: Long,
    val endMs: Long?,
)

class DiscordPresence(context: Context, private val client: OkHttpClient) {
    private val auth = DiscordAuth(client)
    private val tokens = DiscordTokenStore(context)
    private val info = context.getSharedPreferences("discord_account", Context.MODE_PRIVATE)
    private val socketClient = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _signedIn = MutableStateFlow(tokens.load() != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    val loginResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    private val _user = MutableStateFlow(info.getString(KEY_USER, null))
    val user: StateFlow<String?> = _user.asStateFlow()

    private var socket: WebSocket? = null
    private var socketId = 0
    private var ready = false
    private var sequence: Int? = null
    private var heartbeat: Job? = null
    private var reconnect: Job? = null
    private var idle: Job? = null
    private var attempts = 0
    private var wanted: ListeningActivity? = null
    private val assets = HashMap<String, String>()
    private val resolving = HashSet<String>()

    fun loginUrl(): String {
        val (url, code) = auth.begin()
        scope.launch {
            val result = runCatching {
                val tokens = auth.exchange(withTimeout(LOGIN_TIMEOUT_MS) { code.await() })
                this@DiscordPresence.tokens.save(tokens)
            }
            _signedIn.value = result.isSuccess || tokens.load() != null
            loginResults.tryEmit(result.isSuccess)
            if (result.isSuccess && wanted != null) connect()
        }
        return url
    }

    fun onRedirect(code: String?, state: String?, error: String?) = auth.onRedirect(code, state, error)

    fun logout() {
        wanted = null
        disconnect()
        tokens.clear()
        info.edit { remove(KEY_USER) }
        _user.value = null
        _signedIn.value = false
    }

    fun update(activity: ListeningActivity?) {
        if (activity == wanted) return
        wanted = activity
        if (!_signedIn.value) return
        idle?.cancel()
        if (activity == null) {
            if (ready) sendPresence()
            idle = scope.launch {
                delay(IDLE_DISCONNECT_MS)
                disconnect()
            }
            return
        }
        if (ready) sendPresence() else if (socket == null && reconnect?.isActive != true) connect()
    }

    private fun connect() {
        reconnect?.cancel()
        scope.launch {
            val token = freshToken() ?: return@launch
            val id = ++socketId
            socket = socketClient.newWebSocket(Request.Builder().url(GATEWAY).build(), Listener(id, token))
        }
    }

    private fun disconnect() {
        heartbeat?.cancel()
        reconnect?.cancel()
        socketId++
        socket?.close(1000, null)
        socket = null
        ready = false
        sequence = null
    }

    private suspend fun freshToken(): String? {
        val current = tokens.load() ?: return null.also { _signedIn.value = false }
        if (current.expiresAt - System.currentTimeMillis() > REFRESH_MARGIN_MS) return current.access
        return runCatching { auth.refresh(current.refresh) }
            .onSuccess(tokens::save)
            .map { it.access }
            .getOrElse { if (it is DiscordAuthException) logout(); null }
    }

    private fun sendPresence() {
        val activity = wanted
        val activities = JSONArray()
        if (activity != null) {
            val entry = JSONObject()
                .put("name", "Muuusic")
                .put("type", LISTENING)
                .put("details", activity.title.take(128))
                .put("state", activity.artist.ifBlank { "Muuusic" }.take(128))
                .put("timestamps", JSONObject().put("start", activity.startMs).apply { activity.endMs?.let { put("end", it) } })
                .put("buttons", JSONArray().put(BUTTON_LISTEN).put(BUTTON_APP))
                .put(
                    "metadata",
                    JSONObject().put("button_urls", JSONArray().put("https://music.youtube.com/watch?v=${activity.songId}").put(APP_URL)),
                )
            activity.artworkUrl?.let { url ->
                val image = assets[url]
                if (image != null) {
                    entry.put("assets", JSONObject().put("large_image", image).put("large_text", activity.title.take(128)))
                } else {
                    resolveArtwork(url)
                }
            }
            activities.put(entry)
        }
        send(
            JSONObject().put("op", PRESENCE_UPDATE).put(
                "d",
                JSONObject().put("since", 0).put("activities", activities).put("status", "online").put("afk", false),
            ),
        )
    }

    private fun resolveArtwork(url: String) {
        if (!resolving.add(url)) return
        scope.launch {
            val token = freshToken()
            val image = token?.let { mediaProxy(url, it) }
            resolving.remove(url)
            if (image == null) return@launch
            assets[url] = image
            if (assets.size > MAX_ASSETS) assets.remove(assets.keys.first())
            if (ready && wanted?.artworkUrl == url) sendPresence()
        }
    }

    private suspend fun mediaProxy(url: String, token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("urls", JSONArray().put(url)).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://discord.com/api/v10/applications/${DiscordAuth.CLIENT_ID}/external-assets")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "External asset failed: ${response.code}")
                    return@use null
                }
                JSONArray(response.body.string()).optJSONObject(0)?.optString("external_asset_path")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "mp:$it" }
            }
        }.getOrNull()
    }

    private fun send(frame: JSONObject) {
        socket?.send(frame.toString())
    }

    private fun scheduleReconnect() {
        if (wanted == null || !_signedIn.value) return
        val wait = (1_000L shl attempts.coerceAtMost(6)).coerceAtMost(MAX_BACKOFF_MS)
        attempts++
        reconnect = scope.launch {
            delay(wait)
            connect()
        }
    }

    private inner class Listener(private val id: Int, private val token: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                if (id != socketId) return@launch
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return@launch
                if (!json.isNull("s")) sequence = json.optInt("s")
                when (json.optInt("op", -1)) {
                    HELLO -> {
                        val interval = json.optJSONObject("d")?.optLong("heartbeat_interval") ?: DEFAULT_HEARTBEAT_MS
                        startHeartbeat(interval)
                        send(
                            JSONObject().put("op", IDENTIFY).put(
                                "d",
                                JSONObject()
                                    .put("token", "Bearer $token")
                                    .put("intents", 0)
                                    .put("properties", JSONObject().put("os", "android").put("browser", "Muuusic").put("device", "Muuusic")),
                            ),
                        )
                    }
                    DISPATCH -> if (json.optString("t") == "READY") {
                        ready = true
                        attempts = 0
                        json.optJSONObject("d")?.optJSONObject("user")?.let { user ->
                            val name = user.optString("global_name").takeIf { it.isNotBlank() && it != "null" } ?: user.optString("username")
                            if (name.isNotBlank()) {
                                _user.value = name
                                info.edit { putString(KEY_USER, name) }
                            }
                        }
                        sendPresence()
                    }
                    HEARTBEAT -> send(JSONObject().put("op", HEARTBEAT).put("d", sequence ?: JSONObject.NULL))
                    RECONNECT, INVALID_SESSION -> {
                        disconnect()
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed(code)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed(-1)

        private fun closed(code: Int) {
            scope.launch {
                if (id != socketId) return@launch
                heartbeat?.cancel()
                socket = null
                ready = false
                if (code == AUTH_FAILED) {
                    tokens.load()?.let { tokens.save(DiscordTokens(it.access, it.refresh, 0)) }
                }
                if (code in FATAL_CODES) logout() else scheduleReconnect()
            }
        }
    }

    private fun startHeartbeat(interval: Long) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            delay((interval * Math.random()).toLong())
            while (true) {
                send(JSONObject().put("op", HEARTBEAT).put("d", sequence ?: JSONObject.NULL))
                delay(interval)
            }
        }
    }

    private companion object {
        const val GATEWAY = "wss://gateway.discord.gg/?v=10&encoding=json"
        const val APP_URL = "https://github.com/luzility/Muuusic"
        const val BUTTON_LISTEN = "Listen on YouTube Music"
        const val BUTTON_APP = "Get Muuusic"
        const val KEY_USER = "user"
        const val DISPATCH = 0
        const val HEARTBEAT = 1
        const val IDENTIFY = 2
        const val PRESENCE_UPDATE = 3
        const val RECONNECT = 7
        const val INVALID_SESSION = 9
        const val HELLO = 10
        const val LISTENING = 2
        const val AUTH_FAILED = 4004
        val FATAL_CODES = setOf(4010, 4011, 4012, 4013, 4014)
        const val DEFAULT_HEARTBEAT_MS = 41_250L
        const val MAX_BACKOFF_MS = 60_000L
        const val MAX_ASSETS = 64
        const val TAG = "DiscordPresence"
        val JSON = "application/json".toMediaType()
        const val IDLE_DISCONNECT_MS = 5 * 60_000L
        const val REFRESH_MARGIN_MS = 24 * 60 * 60_000L
        const val LOGIN_TIMEOUT_MS = 5 * 60_000L
    }
}
