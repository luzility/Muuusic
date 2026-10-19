package me.luzility.muuusic.playback

import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.model.SongJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class QueueSnapshot(
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

class QueueStore(private val file: File) {

    fun load(): QueueSnapshot? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val songs = json.getJSONArray("songs").let { array ->
            List(array.length()) { SongJson.fromJson(array.getJSONObject(it)) }
        }
        if (songs.isEmpty()) return null
        QueueSnapshot(
            songs = songs,
            index = json.optInt("index").coerceIn(0, songs.lastIndex),
            positionMs = json.optLong("position").coerceAtLeast(0),
            shuffle = json.optBoolean("shuffle"),
            repeatMode = json.optInt("repeat"),
        )
    }.getOrNull()

    fun save(snapshot: QueueSnapshot) {
        val json = JSONObject()
            .put("songs", JSONArray().apply { snapshot.songs.forEach { put(SongJson.toJson(it)) } })
            .put("index", snapshot.index)
            .put("position", snapshot.positionMs)
            .put("shuffle", snapshot.shuffle)
            .put("repeat", snapshot.repeatMode)
        // Write to a temp file and rename, so a crash can't leave a partial queue.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
    }
}
