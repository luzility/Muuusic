package me.luzility.muuusic.desktop.playback

import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.model.SongJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class QueueSnapshot(
    val queue: List<Song>,
    val order: List<Int>,
    val current: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: RepeatMode,
)

class QueueStore(private val file: File) {
    fun load(): QueueSnapshot? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val songs = json.getJSONArray("queue").let { array -> List(array.length()) { SongJson.fromJson(array.getJSONObject(it)) } }
        val order = json.getJSONArray("order").let { array -> List(array.length()) { array.getInt(it) } }
        if (songs.isEmpty() || order.any { it !in songs.indices }) return null
        QueueSnapshot(
            queue = songs,
            order = order,
            current = json.getInt("current").coerceIn(songs.indices),
            positionMs = json.optLong("position"),
            shuffle = json.optBoolean("shuffle"),
            repeat = runCatching { RepeatMode.valueOf(json.optString("repeat")) }.getOrDefault(RepeatMode.Off),
        )
    }.getOrNull()

    fun save(snapshot: QueueSnapshot?) {
        if (snapshot == null || snapshot.queue.isEmpty()) {
            file.delete()
            return
        }
        val json = JSONObject()
            .put("queue", JSONArray().apply { snapshot.queue.forEach { put(SongJson.toJson(it)) } })
            .put("order", JSONArray(snapshot.order))
            .put("current", snapshot.current)
            .put("position", snapshot.positionMs)
            .put("shuffle", snapshot.shuffle)
            .put("repeat", snapshot.repeat.name)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }
}
