package me.luzility.muuusic.data.account

import kotlinx.coroutines.flow.first
import me.luzility.muuusic.data.LibraryRepository
import me.luzility.muuusic.data.PlaylistRepository
import me.luzility.muuusic.data.innertube.InnerTubeClient

class YouTubeImport(
    private val innerTube: InnerTubeClient,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
) {
    sealed interface Progress {
        data object Likes : Progress
        data class Playlist(val index: Int, val total: Int, val name: String) : Progress
    }

    data class Result(val likes: Int, val playlists: Int, val likedIds: Set<String>)

    class LikeSync(val likedIds: Set<String>, val added: Int)

    suspend fun likedIds(cookie: String): Set<String> = innerTube.likedSongs(cookie).map { it.id }.toSet()

    suspend fun syncLikes(cookie: String, known: Set<String>?): LikeSync {
        val remote = innerTube.likedSongs(cookie)
        val alreadyLiked = library.liked.first().map { it.id }.toSet()
        val fresh = remote.filter { it.id !in alreadyLiked && (known == null || it.id !in known) }
        fresh.asReversed().forEach { library.setLiked(it, true) }
        return LikeSync(remote.map { it.id }.toSet(), fresh.size)
    }

    suspend fun run(cookie: String, onProgress: (Progress) -> Unit = {}): Result {
        onProgress(Progress.Likes)
        val likes = syncLikes(cookie, known = null)

        val existing = playlists.playlists.first().map { it.name.trim().lowercase() }.toSet()
        val remote = innerTube.libraryPlaylists(cookie).filter { it.title.trim().lowercase() !in existing }
        var created = 0
        remote.forEachIndexed { index, playlist ->
            onProgress(Progress.Playlist(index + 1, remote.size, playlist.title))
            val songs = runCatching { innerTube.playlistSongs(cookie, playlist.browseId) }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) {
                playlists.create(playlist.title, songs)
                created++
            }
        }
        return Result(likes.added, created, likes.likedIds)
    }
}
