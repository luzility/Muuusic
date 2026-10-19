package me.luzility.muuusic.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.first
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.data.model.Song
import java.util.concurrent.ConcurrentHashMap

// What Android Auto (and other media browsers) can browse. Song ids carry their list so picking a song plays the list from it.
class BrowseTree(private val context: Context) {

    private val container = context.container
    private val lists = ConcurrentHashMap<String, List<Song>>()

    fun root(): MediaItem = folder(ROOT, context.getString(R.string.app_name))

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(
            folder(HOME, context.getString(R.string.quick_picks)),
            folder(RECENT, context.getString(R.string.recently_played)),
            folder(LIKED, context.getString(R.string.liked_songs)),
            folder(PLAYLISTS, context.getString(R.string.playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(DOWNLOADS, context.getString(R.string.downloads)),
        )
        PLAYLISTS -> container.playlists.playlists.first().map {
            folder("$PLAYLIST${it.id}", it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, it.thumbnailUrl)
        }
        else -> songsOf(parentId)?.let { songs -> songs.map { playable(parentId, it) } }.orEmpty()
    }

    suspend fun search(query: String): List<MediaItem> {
        val parent = "$SEARCH$query"
        return songsOf(parent).orEmpty().map { playable(parent, it) }
    }

    suspend fun searchSongs(query: String): List<Song> = songsOf("$SEARCH$query").orEmpty()

    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT) return root()
        val (songs, index) = resolve(mediaId) ?: return null
        return playable(parentOf(mediaId) ?: return null, songs[index])
    }

    /** The list a browsed song belongs to and its position, or null for other ids. */
    suspend fun resolve(mediaId: String): Pair<List<Song>, Int>? {
        val parent = parentOf(mediaId) ?: return null
        val videoId = mediaId.substringAfterLast(SEPARATOR)
        val songs = songsOf(parent) ?: return null
        val index = songs.indexOfFirst { it.id == videoId }
        return if (index >= 0) songs to index else null
    }

    private fun parentOf(mediaId: String): String? =
        if (mediaId.startsWith(ITEM) && SEPARATOR in mediaId) mediaId.removePrefix(ITEM).substringBeforeLast(SEPARATOR) else null

    private suspend fun songsOf(parentId: String): List<Song>? {
        lists[parentId]?.let { return it }
        val songs = when {
            parentId == HOME -> quickPicks()
            parentId == RECENT -> container.library.history.first()
            parentId == LIKED -> container.library.liked.first()
            parentId == DOWNLOADS -> container.downloads.songs.first()
            parentId.startsWith(PLAYLIST) ->
                parentId.removePrefix(PLAYLIST).toLongOrNull()?.let { id -> container.playlists.items(id).first().map { it.song } }
            parentId.startsWith(SEARCH) ->
                container.youtube.search(parentId.removePrefix(SEARCH), SearchFilter.Songs)
                    .filterIsInstance<SearchResult.SongResult>().map { it.song }
            else -> null
        } ?: return null
        // Library lists change, so only remote results are kept; the rest are cheap to read again.
        if (parentId == HOME || parentId.startsWith(SEARCH)) lists[parentId] = songs
        return songs
    }

    private suspend fun quickPicks(): List<Song> =
        runCatching {
            container.innerTube.home().sections.firstNotNullOfOrNull { section ->
                section.items.filterIsInstance<BrowseItem.SongItem>().filterNot { it.wide }.map { it.song }.takeIf { it.size >= 4 }
            }
        }.getOrNull() ?: container.library.mostPlayed.first()

    private fun folder(id: String, title: String, type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, artwork: String? = null) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .setArtworkUri(artwork?.toUri())
                    .build()
            )
            .build()

    private fun playable(parentId: String, song: Song): MediaItem =
        song.toMediaItem().buildUpon().setMediaId("$ITEM$parentId$SEPARATOR${song.id}").build()

    private companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val RECENT = "recent"
        const val LIKED = "liked"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST = "playlist:"
        const val DOWNLOADS = "downloads"
        const val SEARCH = "search:"
        const val ITEM = "item:"
        const val SEPARATOR = '|'
    }
}
