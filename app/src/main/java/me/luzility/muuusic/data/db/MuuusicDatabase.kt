package me.luzility.muuusic.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.luzility.muuusic.data.model.Song
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val artistUrl: String? = null,
) {
    fun toSong() = Song(id, title, artist, durationSec, thumbnailUrl, artistUrl)
}

fun Song.toEntity() = SongEntity(id, title, artist, durationSec, thumbnailUrl, artistUrl = artistUrl)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun liked(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun history(limit: Int = 200): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 1 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun mostPlayed(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun lastPlayed(): SongEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(song: SongEntity): Long

    // An unknown artist URL never wipes out one we already know.
    @Query("UPDATE songs SET title = :title, artist = :artist, durationSec = CASE WHEN :durationSec > 0 THEN :durationSec ELSE durationSec END, thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl), artistUrl = COALESCE(:artistUrl, artistUrl) WHERE id = :id")
    suspend fun updateMetadata(id: String, title: String, artist: String, durationSec: Long, thumbnailUrl: String?, artistUrl: String?)

    @Query("UPDATE songs SET lastPlayedAt = :now, playCount = playCount + 1 WHERE id = :id")
    suspend fun markPlayed(id: String, now: Long)

    @Query("UPDATE songs SET lastPlayedAt = NULL, playCount = 0")
    suspend fun clearPlayHistory()

    @Query("DELETE FROM songs WHERE liked = 0 AND lastPlayedAt IS NULL AND id NOT IN (SELECT songId FROM playlist_entries)")
    suspend fun deleteUnreferenced()

    @Query("UPDATE songs SET liked = :liked, likedAt = :likedAt WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, likedAt: Long?)

    @Query("SELECT * FROM songs")
    suspend fun all(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(song: SongEntity)
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    indices = [Index("playlistId"), Index("songId")],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: Long,
    val songId: String,
    val position: Int,
)

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val songCount: Int,
    val thumbnailUrl: String?,
)

data class PlaylistSongRow(
    @Embedded val song: SongEntity,
    val entryId: Long,
)

@Dao
interface PlaylistDao {
    @Query(
        """SELECT p.id, p.name,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount,
               (SELECT s.thumbnailUrl FROM playlist_entries e JOIN songs s ON s.id = e.songId
                WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1) AS thumbnailUrl
           FROM playlists p ORDER BY p.updatedAt DESC"""
    )
    fun playlists(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query(
        """SELECT s.*, e.entryId FROM playlist_entries e JOIN songs s ON s.id = e.songId
           WHERE e.playlistId = :id ORDER BY e.position"""
    )
    fun songs(id: Long): Flow<List<PlaylistSongRow>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :id ORDER BY position")
    suspend fun entries(id: Long): List<PlaylistEntryEntity>

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun all(): List<PlaylistEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_entries WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun deleteEntriesOf(id: Long)

    @Query("DELETE FROM playlist_entries WHERE entryId = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("UPDATE playlist_entries SET position = :position WHERE entryId = :entryId")
    suspend fun setPosition(entryId: Long, position: Int)
}

@Database(entities = [SongEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class], version = 3, exportSchema = true)
abstract class MuuusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN artistUrl TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_entries` (`entryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`playlistId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_playlistId` ON `playlist_entries` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_songId` ON `playlist_entries` (`songId`)")
            }
        }
    }
}
