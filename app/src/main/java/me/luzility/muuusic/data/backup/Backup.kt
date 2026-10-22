package me.luzility.muuusic.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.BuildConfig
import me.luzility.muuusic.data.PlaylistRepository
import me.luzility.muuusic.data.db.MuuusicDatabase
import me.luzility.muuusic.data.db.SongEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class RestoreResult(val songs: Int, val playlists: Int)

class InvalidBackupException : Exception()

class Backup(
    private val context: Context,
    private val db: MuuusicDatabase,
    private val playlists: PlaylistRepository,
) {
    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()

    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val songs = JSONArray()
        songDao.all().forEach { songs.put(it.toJson()) }

        val lists = JSONArray()
        playlistDao.all().forEach { playlist ->
            val ids = JSONArray()
            playlistDao.entries(playlist.id).forEach { ids.put(it.songId) }
            lists.put(
                JSONObject()
                    .put("name", playlist.name)
                    .put("createdAt", playlist.createdAt)
                    .put("songs", ids)
            )
        }

        val preferences = JSONObject()
        PREFERENCE_FILES.forEach { name -> preferences.put(name, store(name).toJson()) }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("createdAt", System.currentTimeMillis())
            .put("songs", songs)
            .put("playlists", lists)
            .put("preferences", preferences)
        out.bufferedWriter().use { it.write(root.toString()) }
    }

    suspend fun restore(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val root = runCatching { JSONObject(input.bufferedReader().use { it.readText() }) }.getOrNull()
        if (root == null || root.optString("format") != FORMAT || root.optInt("version") > VERSION) throw InvalidBackupException()

        val songs = root.optJSONArray("songs").objects().filter { it.has("id") }.map { it.toSongEntity() }
        val byId = songs.associateBy { it.id }

        val addedPlaylists = db.withTransaction {
            songs.forEach { incoming ->
                val existing = songDao.get(incoming.id)
                songDao.put(if (existing == null) incoming else merge(existing, incoming))
            }

            var added = 0
            val current = playlistDao.all().map { it.name to playlistDao.entries(it.id).map { e -> e.songId } }.toSet()
            root.optJSONArray("playlists").objects().forEach { playlist ->
                val name = playlist.optString("name").ifBlank { return@forEach }
                val ids = playlist.optJSONArray("songs").strings().filter { it in byId || songDao.get(it) != null }
                if ((name to ids) in current) return@forEach
                playlists.create(name, ids.mapNotNull { id -> (byId[id] ?: songDao.get(id))?.toSong() })
                added++
            }
            added
        }

        root.optJSONObject("preferences")?.let { preferences ->
            PREFERENCE_FILES.forEach { name -> preferences.optJSONObject(name)?.let { store(name).replaceWith(it) } }
        }

        RestoreResult(songs.size, addedPlaylists)
    }

    private fun merge(old: SongEntity, new: SongEntity) = old.copy(
        title = new.title.ifBlank { old.title },
        artist = new.artist.ifBlank { old.artist },
        durationSec = if (new.durationSec > 0) new.durationSec else old.durationSec,
        thumbnailUrl = new.thumbnailUrl ?: old.thumbnailUrl,
        artistUrl = new.artistUrl ?: old.artistUrl,
        liked = old.liked || new.liked,
        likedAt = listOfNotNull(old.likedAt, new.likedAt).maxOrNull(),
        lastPlayedAt = listOfNotNull(old.lastPlayedAt, new.lastPlayedAt).maxOrNull(),
        playCount = maxOf(old.playCount, new.playCount),
    )

    private fun store(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun SharedPreferences.toJson(): JSONObject {
        val json = JSONObject()
        all.forEach { (key, value) ->
            val typed = when (value) {
                is Boolean -> JSONObject().put("b", value)
                is Int -> JSONObject().put("i", value)
                is Long -> JSONObject().put("l", value)
                is Float -> JSONObject().put("f", value.toDouble())
                is String -> JSONObject().put("s", value)
                else -> null
            }
            typed?.let { json.put(key, it) }
        }
        return json
    }

    private fun SharedPreferences.replaceWith(json: JSONObject) = edit(commit = true) {
        clear()
        json.keys().forEach { key ->
            val value = json.optJSONObject(key) ?: return@forEach
            when {
                value.has("b") -> putBoolean(key, value.getBoolean("b"))
                value.has("i") -> putInt(key, value.getInt("i"))
                value.has("l") -> putLong(key, value.getLong("l"))
                value.has("f") -> putFloat(key, value.getDouble("f").toFloat())
                value.has("s") -> putString(key, value.getString("s"))
            }
        }
    }

    private fun SongEntity.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("duration", durationSec)
        .putOpt("thumbnail", thumbnailUrl)
        .putOpt("artistUrl", artistUrl)
        .put("liked", liked)
        .putOpt("likedAt", likedAt)
        .putOpt("lastPlayedAt", lastPlayedAt)
        .put("playCount", playCount)

    private fun JSONObject.toSongEntity() = SongEntity(
        id = getString("id"),
        title = optString("title"),
        artist = optString("artist"),
        durationSec = optLong("duration"),
        thumbnailUrl = optStringOrNull("thumbnail"),
        liked = optBoolean("liked"),
        likedAt = optLongOrNull("likedAt"),
        lastPlayedAt = optLongOrNull("lastPlayedAt"),
        playCount = optInt("playCount"),
        artistUrl = optStringOrNull("artistUrl"),
    )

    private fun JSONObject.optStringOrNull(key: String) = if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String) = if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull().filter { it.has("id") || it.has("name") }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

    private companion object {
        const val FORMAT = "muuusic-backup"
        const val VERSION = 1
        val PREFERENCE_FILES = listOf("settings", "lyrics", "lyrics_offsets", "search")
    }
}
