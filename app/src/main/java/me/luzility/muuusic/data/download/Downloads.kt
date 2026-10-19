package me.luzility.muuusic.data.download

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.model.SongJson
import me.luzility.muuusic.playback.MediaItems
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

@Immutable
data class DownloadStatus(val song: Song, val state: State, val percent: Float, val updatedAt: Long) {
    enum class State { Queued, Downloading, Done, Failed }
}

// Separate from the play cache and never evicted. Metadata rides in the download request so
// the list works offline. Main thread only, like DownloadManager.
class Downloads(
    private val context: Context,
    databaseProvider: DatabaseProvider,
    upstream: DataSource.Factory,
    wifiOnly: Boolean,
) {
    private val dir = File(context.filesDir, "downloads")

    val cache = SimpleCache(dir, NoOpCacheEvictor(), databaseProvider)

    val manager = DownloadManager(context, databaseProvider, cache, upstream, Executors.newFixedThreadPool(2)).apply {
        maxParallelDownloads = 2
        requirements = requirementsFor(wifiOnly)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())

    val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()

    val songs: Flow<List<Song>> = statuses.map { all ->
        all.values.filter { it.state == DownloadStatus.State.Done }.sortedByDescending { it.updatedAt }.map { it.song }
    }

    init {
        manager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = refresh()
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) = refresh()
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh()
        })
        scope.launch {
            while (true) {
                refreshRequests.receive()
                val all = readIndex().toMutableMap()
                manager.currentDownloads.forEach { d -> d.toStatus()?.let { all[it.song.id] = it } }
                _statuses.value = all
                if (manager.currentDownloads.any { it.state == Download.STATE_DOWNLOADING }) pollProgress()
            }
        }
        refresh()
    }

    fun download(songs: List<Song>) {
        songs.filter { _statuses.value[it.id]?.state != DownloadStatus.State.Done }.forEach { song ->
            val request = DownloadRequest.Builder(song.id, MediaItems.uriFor(song.id))
                .setCustomCacheKey(song.id)
                .setData(SongJson.toJson(song).toString().toByteArray())
                .build()
            DownloadService.sendAddDownload(context, MuuusicDownloadService::class.java, request, false)
        }
    }

    fun remove(songId: String) =
        DownloadService.sendRemoveDownload(context, MuuusicDownloadService::class.java, songId, false)

    fun removeAll() = DownloadService.sendRemoveAllDownloads(context, MuuusicDownloadService::class.java, false)

    fun setWifiOnly(wifiOnly: Boolean) =
        DownloadService.sendSetRequirements(context, MuuusicDownloadService::class.java, requirementsFor(wifiOnly), false)

    fun bytesUsed(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun refresh() {
        refreshRequests.trySend(Unit)
    }

    private suspend fun readIndex(): Map<String, DownloadStatus> = withContext(Dispatchers.IO) {
        val all = mutableMapOf<String, DownloadStatus>()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) cursor.download.toStatus()?.let { all[it.song.id] = it }
        }
        all
    }

    private fun pollProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && manager.currentDownloads.any { it.state == Download.STATE_DOWNLOADING }) {
                _statuses.value = _statuses.value + manager.currentDownloads.mapNotNull { it.toStatus() }.associateBy { it.song.id }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun Download.toStatus(): DownloadStatus? {
        val song = runCatching { SongJson.fromJson(JSONObject(String(request.data))) }.getOrNull() ?: return null
        val state = when (state) {
            Download.STATE_COMPLETED -> DownloadStatus.State.Done
            Download.STATE_DOWNLOADING -> DownloadStatus.State.Downloading
            Download.STATE_FAILED -> DownloadStatus.State.Failed
            Download.STATE_REMOVING -> return null
            else -> DownloadStatus.State.Queued
        }
        return DownloadStatus(song, state, percentDownloaded.coerceIn(0f, 100f), updateTimeMs)
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L

        fun requirementsFor(wifiOnly: Boolean) =
            Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
    }
}
