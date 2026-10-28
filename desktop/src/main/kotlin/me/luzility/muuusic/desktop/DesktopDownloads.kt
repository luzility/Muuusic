package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.model.SongJson
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.data.youtube.YouTubeRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

data class DownloadStatus(val song: Song, val state: State, val percent: Float, val updatedAt: Long) {
    enum class State { Queued, Downloading, Done, Failed }
}

class DesktopDownloads(
    private val dir: File,
    private val client: OkHttpClient,
    private val youtube: YouTubeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val index = File(dir, "index.json")
    private val slots = Semaphore(MAX_PARALLEL)
    private val jobs = HashMap<String, Job>()
    private val saves = Channel<Unit>(Channel.CONFLATED)

    private val _statuses = MutableStateFlow(readIndex())
    val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()

    val songs: Flow<List<Song>> = statuses.map { all ->
        all.values.filter { it.state == DownloadStatus.State.Done }.sortedByDescending { it.updatedAt }.map { it.song }
    }

    init {
        dir.mkdirs()
        scope.launch(Dispatchers.IO) {
            for (unused in saves) writeIndex(_statuses.value)
        }
    }

    fun fileFor(songId: String): File? =
        audioFile(songId).takeIf { _statuses.value[songId]?.state == DownloadStatus.State.Done && it.exists() }

    fun download(songs: List<Song>) {
        songs.filter { _statuses.value[it.id]?.state != DownloadStatus.State.Done && it.id !in jobs }.forEach { song ->
            set(DownloadStatus(song, DownloadStatus.State.Queued, 0f, System.currentTimeMillis()))
            jobs[song.id] = scope.launch {
                val result = runCatching { slots.withPermit { fetch(song) } }
                jobs.remove(song.id)
                if (result.isFailure && song.id in _statuses.value) {
                    partFile(song.id).delete()
                    set(DownloadStatus(song, DownloadStatus.State.Failed, 0f, System.currentTimeMillis()))
                }
            }
        }
    }

    fun remove(songId: String) {
        jobs.remove(songId)?.cancel()
        _statuses.update { it - songId }
        saves.trySend(Unit)
        scope.launch(Dispatchers.IO) {
            audioFile(songId).delete()
            partFile(songId).delete()
        }
    }

    fun removeAll() {
        _statuses.value.keys.toList().forEach(::remove)
    }

    fun bytesUsed(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private suspend fun fetch(song: Song) {
        set(DownloadStatus(song, DownloadStatus.State.Downloading, 0f, System.currentTimeMillis()))
        withContext(Dispatchers.IO) {
            val url = youtube.resolveStreamUrl(song.id)
            val part = partFile(song.id)
            part.delete()
            var total = url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull() ?: -1L
            var position = 0L
            RandomAccessFile(part, "rw").use { out ->
                while (total < 0 || position < total) {
                    ensureActive()
                    val end = position + CHUNK_SIZE - 1
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", NewPipeDownloader.USER_AGENT)
                        .header("Range", "bytes=$position-${if (total > 0) minOf(end, total - 1) else end}")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        if (total < 0) {
                            total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: throw IOException("Unknown size")
                        }
                        val bytes = response.body.bytes()
                        if (bytes.isEmpty()) throw IOException("Empty chunk")
                        out.seek(position)
                        out.write(bytes)
                        position += bytes.size
                    }
                    val percent = position * 100f / total
                    withContext(Dispatchers.Main) {
                        set(DownloadStatus(song, DownloadStatus.State.Downloading, percent, System.currentTimeMillis()))
                    }
                }
            }
            if (!part.renameTo(audioFile(song.id))) throw IOException("Couldn't save ${song.id}")
        }
        set(DownloadStatus(song, DownloadStatus.State.Done, 100f, System.currentTimeMillis()))
    }

    private fun set(status: DownloadStatus) {
        _statuses.update { it + (status.song.id to status) }
        if (status.state == DownloadStatus.State.Done || status.state == DownloadStatus.State.Failed) saves.trySend(Unit)
    }

    private fun audioFile(id: String) = File(dir, "$id.audio")

    private fun partFile(id: String) = File(dir, "$id.part")

    private fun readIndex(): Map<String, DownloadStatus> {
        if (!index.exists()) return emptyMap()
        val array = runCatching { JSONArray(index.readText()) }.getOrNull() ?: return emptyMap()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { entry ->
            val song = runCatching { SongJson.fromJson(entry.getJSONObject("song")) }.getOrNull() ?: return@mapNotNull null
            if (!audioFile(song.id).exists()) return@mapNotNull null
            song.id to DownloadStatus(song, DownloadStatus.State.Done, 100f, entry.optLong("updatedAt"))
        }.toMap()
    }

    private fun writeIndex(all: Map<String, DownloadStatus>) {
        val array = JSONArray()
        all.values.filter { it.state == DownloadStatus.State.Done }.forEach {
            array.put(JSONObject().put("song", SongJson.toJson(it.song)).put("updatedAt", it.updatedAt))
        }
        val temp = File(dir, "index.json.tmp")
        temp.writeText(array.toString())
        temp.renameTo(index)
    }

    private companion object {
        const val MAX_PARALLEL = 2
        const val CHUNK_SIZE = 1024L * 1024
    }
}
