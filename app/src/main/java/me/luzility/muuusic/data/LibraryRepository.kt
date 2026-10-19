package me.luzility.muuusic.data

import me.luzility.muuusic.data.db.SongDao
import me.luzility.muuusic.data.db.toEntity
import me.luzility.muuusic.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val dao: SongDao) {

    val liked: Flow<List<Song>> = dao.liked().map { list -> list.map { it.toSong() } }
    val history: Flow<List<Song>> = dao.history().map { list -> list.map { it.toSong() } }
    val mostPlayed: Flow<List<Song>> = dao.mostPlayed().map { list -> list.map { it.toSong() } }

    fun isLiked(id: String): Flow<Boolean> = dao.isLiked(id).map { it == true }

    suspend fun lastPlayed(): Song? = dao.lastPlayed()?.toSong()

    suspend fun recordPlay(song: Song) {
        upsert(song)
        dao.markPlayed(song.id, System.currentTimeMillis())
    }

    suspend fun clearHistory() {
        dao.clearPlayHistory()
        dao.deleteUnreferenced()
    }

    suspend fun setLiked(song: Song, liked: Boolean) {
        upsert(song)
        dao.setLiked(song.id, liked, if (liked) System.currentTimeMillis() else null)
    }

    private suspend fun upsert(song: Song) {
        if (dao.insertIgnore(song.toEntity()) == -1L) {
            dao.updateMetadata(song.id, song.title, song.artist, song.durationSec, song.thumbnailUrl, song.artistUrl)
        }
    }
}
