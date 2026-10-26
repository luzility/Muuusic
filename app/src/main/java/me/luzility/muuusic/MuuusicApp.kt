package me.luzility.muuusic

import me.luzility.muuusic.data.discord.DiscordPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.flow.MutableStateFlow
import me.luzility.muuusic.playback.audio.EqualizerLayout
import me.luzility.muuusic.playback.audio.LoudnessStore
import me.luzility.muuusic.data.sponsorblock.SponsorBlock
import me.luzility.muuusic.data.update.Updates
import me.luzility.muuusic.data.settings.Hints
import me.luzility.muuusic.data.recognition.SongRecognizer
import me.luzility.muuusic.data.recognition.ShazamClient
import me.luzility.muuusic.data.search.SearchHistory
import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import me.luzility.muuusic.data.storage.AndroidKeyValueStores
import me.luzility.muuusic.data.LibraryRepository
import me.luzility.muuusic.data.PlaylistRepository
import me.luzility.muuusic.data.backup.Backup
import me.luzility.muuusic.data.network.Connectivity
import me.luzility.muuusic.data.db.MuuusicDatabase
import me.luzility.muuusic.data.download.Downloads
import me.luzility.muuusic.data.innertube.InnerTubeClient
import me.luzility.muuusic.data.lyrics.LyricsRepository
import me.luzility.muuusic.data.lyrics.LyricsTranslator
import me.luzility.muuusic.data.lyrics.LyricsSettings
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.data.youtube.YouTubeRepository
import me.luzility.muuusic.playback.ChunkedDataSource
import me.luzility.muuusic.ui.theme.FlexFont
import me.luzility.muuusic.playback.MediaItems
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class MuuusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppInfo.versionName = BuildConfig.VERSION_NAME
        container = AppContainer(this)
        FlexFont.init(this)
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(container.okHttp),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifEmpty { "US" }),
        )
    }
}

val Context.container: AppContainer get() = (applicationContext as MuuusicApp).container

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val stores = AndroidKeyValueStores(appContext)

    val settings = AppSettings(stores)

    val connectivity = Connectivity(appContext)

    val youtube = YouTubeRepository(audioQuality = { settings.prefs.value.audioQuality })

    val lyrics by lazy { LyricsRepository(okHttp) }

    val lyricsTranslator by lazy { LyricsTranslator(okHttp) }

    val songRecognizer by lazy { SongRecognizer(ShazamClient(okHttp)) }

    val searchHistory by lazy { SearchHistory(stores) }

    val innerTube by lazy { InnerTubeClient(okHttp) }

    val loudness by lazy { LoudnessStore(appContext) }
    val sponsorBlock by lazy { SponsorBlock(okHttp) }
    val discord by lazy { DiscordPresence(appContext, okHttp) }
    val updates by lazy { Updates(stores, okHttp) }
    val hints by lazy { Hints(stores) }

    // Set by the playback service once its equalizer exists; null if the device has none.
    val equalizerLayout = MutableStateFlow<EqualizerLayout?>(null)

    val lyricsSettings by lazy { LyricsSettings(stores) }

    private val database = Room.databaseBuilder(appContext, MuuusicDatabase::class.java, "muuusic.db")
        .addMigrations(MuuusicDatabase.MIGRATION_1_2, MuuusicDatabase.MIGRATION_2_3)
        .setDriver(AndroidSQLiteDriver())
        .build()

    val library = LibraryRepository(database.songDao())

    val playlists = PlaylistRepository(database, database.playlistDao(), database.songDao())

    val backup by lazy { Backup(stores, database, playlists) }

    // One index DB shared by the play cache, download cache and download index.
    private val mediaDatabase by lazy { StandaloneDatabaseProvider(appContext) }

    private val playerCache: SimpleCache by lazy {
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(settings.startupCacheSizeMb.toLong() * 1024 * 1024),
            mediaDatabase,
        )
    }

    /** Created on first use, which is always on the main thread (UI or download service). */
    val downloads: Downloads by lazy {
        Downloads(appContext, mediaDatabase, streamDataSourceFactory(), settings.prefs.value.downloadWifiOnly)
    }

    private val cacheDir = File(appContext.cacheDir, "media")

    // Read from disk: SimpleCache reports 0 until its index finishes loading.
    fun cacheBytesUsed(): Long = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // SimpleCache does disk I/O, so call off the main thread.
    fun clearCache() {
        playerCache.keys.toList().forEach(playerCache::removeResource)
    }

    // Items use `muuusic://song/<videoId>`. The cache keys on the id; the real (expiring)
    // googlevideo URL is only resolved on a cache miss.
    fun playerDataSourceFactory(): DataSource.Factory {
        val network = CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(streamDataSourceFactory())
            .setCacheKeyFactory { spec -> "${spec.key ?: spec.uri}@${settings.prefs.value.audioQuality.name}" }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Downloads first (read-only, so streaming never writes into them), then the play cache,
        // then the network.
        return CacheDataSource.Factory()
            .setCache(downloads.cache)
            .setUpstreamDataSourceFactory(network)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { spec ->
                val id = spec.key ?: spec.uri.toString()
                if (downloads.manager.downloadIndex.getDownload(id)?.state == Download.STATE_COMPLETED) id else "$id#partial"
            }
    }

    private fun streamDataSourceFactory(): DataSource.Factory =
        ResolvingDataSource.Factory(
            ChunkedDataSource.Factory(OkHttpDataSource.Factory(okHttp).setUserAgent(NewPipeDownloader.USER_AGENT)),
        ) { spec ->
            val videoId = MediaItems.videoIdFromUri(spec.uri) ?: return@Factory spec
            spec.withUri(youtube.resolveStreamUrl(videoId).toUri())
        }
}
