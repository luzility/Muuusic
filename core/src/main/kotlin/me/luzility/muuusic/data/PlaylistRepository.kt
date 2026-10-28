package me.luzility.muuusic.data

import me.luzility.muuusic.data.db.writeTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.luzility.muuusic.data.db.MuuusicDatabase
import me.luzility.muuusic.data.db.PlaylistDao
import me.luzility.muuusic.data.db.PlaylistEntity
import me.luzility.muuusic.data.db.PlaylistEntryEntity
import me.luzility.muuusic.data.db.SongDao
import me.luzility.muuusic.data.db.toEntity
import me.luzility.muuusic.data.model.Song

data class PlaylistSummary(val id: Long, val name: String, val songCount: Int, val thumbnailUrl: String?)

data class PlaylistItem(val entryId: Long, val song: Song)

class PlaylistRepository(
    private val db: MuuusicDatabase,
    private val dao: PlaylistDao,
    private val songDao: SongDao,
) {
    val playlists: Flow<List<PlaylistSummary>> = dao.playlists().map { rows ->
        rows.map { PlaylistSummary(it.id, it.name, it.songCount, it.thumbnailUrl) }
    }

    fun name(id: Long): Flow<String?> = dao.playlist(id).map { it?.name }

    fun items(id: Long): Flow<List<PlaylistItem>> = dao.songs(id).map { rows ->
        rows.map { PlaylistItem(it.entryId, it.song.toSong()) }
    }

    suspend fun create(name: String, songs: List<Song> = emptyList()): Long = db.writeTransaction {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
        addSongsInTransaction(id, songs)
        id
    }

    suspend fun addSongs(id: Long, songs: List<Song>) = db.writeTransaction {
        addSongsInTransaction(id, songs)
        dao.touch(id, System.currentTimeMillis())
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim(), System.currentTimeMillis())

    suspend fun delete(id: Long) = db.writeTransaction {
        // Explicit, rather than relying on SQLite foreign keys being switched on.
        dao.deleteEntriesOf(id)
        dao.delete(id)
    }

    suspend fun remove(entryId: Long) = dao.deleteEntry(entryId)

    suspend fun reorder(id: Long, entryIds: List<Long>) = db.writeTransaction {
        entryIds.forEachIndexed { index, entryId -> dao.setPosition(entryId, index) }
        dao.touch(id, System.currentTimeMillis())
    }

    private suspend fun addSongsInTransaction(id: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        songs.forEach { song ->
            if (songDao.insertIgnore(song.toEntity()) == -1L) {
                songDao.updateMetadata(song.id, song.title, song.artist, song.durationSec, song.thumbnailUrl, song.artistUrl)
            }
        }
        val start = dao.nextPosition(id)
        dao.insertEntries(songs.mapIndexed { i, song -> PlaylistEntryEntity(playlistId = id, songId = song.id, position = start + i) })
    }
}
