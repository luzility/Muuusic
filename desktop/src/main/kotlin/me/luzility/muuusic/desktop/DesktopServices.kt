package me.luzility.muuusic.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.luzility.muuusic.data.LibraryRepository
import me.luzility.muuusic.data.PlaylistRepository
import me.luzility.muuusic.data.backup.Backup
import me.luzility.muuusic.data.db.MuuusicDatabase
import me.luzility.muuusic.data.innertube.InnerTubeClient
import me.luzility.muuusic.data.lyrics.LyricsRepository
import me.luzility.muuusic.data.lyrics.LyricsSettings
import me.luzility.muuusic.data.lyrics.LyricsTranslator
import me.luzility.muuusic.data.recognition.ShazamClient
import me.luzility.muuusic.data.recognition.SongRecognizer
import me.luzility.muuusic.data.search.SearchHistory
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.data.settings.Hints
import me.luzility.muuusic.data.sponsorblock.SponsorBlock
import me.luzility.muuusic.data.update.Updates
import me.luzility.muuusic.data.youtube.YouTubeRepository
import me.luzility.muuusic.playback.audio.LoudnessStore
import okhttp3.OkHttpClient

class DesktopServices(val dataDir: File) {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val stores = FileKeyValueStores(File(dataDir, "prefs"))

    val settings = AppSettings(stores)
    val connectivity = DesktopConnectivity(scope)
    val youtube = YouTubeRepository(audioQuality = { settings.prefs.value.audioQuality })
    val innerTube by lazy { InnerTubeClient(okHttp) }
    val lyrics by lazy { LyricsRepository(okHttp) }
    val lyricsTranslator by lazy { LyricsTranslator(okHttp) }
    val lyricsSettings by lazy { LyricsSettings(stores) }
    val searchHistory by lazy { SearchHistory(stores) }
    val hints by lazy { Hints(stores) }
    val updates by lazy { Updates(stores, okHttp) }
    val loudness by lazy { LoudnessStore(stores) }
    val sponsorBlock by lazy { SponsorBlock(okHttp) }
    val recognizer by lazy { SongRecognizer(ShazamClient(okHttp)) }
    val updater by lazy { DesktopUpdater(okHttp, File(dataDir, "cache")) }

    private val database = Room.databaseBuilder<MuuusicDatabase>(File(dataDir, "muuusic.db").absolutePath)
        .addMigrations(MuuusicDatabase.MIGRATION_1_2, MuuusicDatabase.MIGRATION_2_3)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    val library = LibraryRepository(database.songDao())
    val playlists = PlaylistRepository(database, database.playlistDao(), database.songDao())
    val backup by lazy { Backup(stores, database, playlists) }

    val streamCache = StreamCache(File(dataDir, "cache"), okHttp, youtube, settings)

    val downloads by lazy { DesktopDownloads(File(dataDir, "downloads"), okHttp, youtube) }

    companion object {
        fun defaultDataDir(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val base = when {
                os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
                os.contains("mac") -> File(home, "Library/Application Support")
                else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(home, ".local/share")
            }
            return File(base, "Muuusic").apply { mkdirs() }
        }
    }
}
