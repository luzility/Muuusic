package me.luzility.muuusic.data.youtube

import android.net.Uri
import me.luzility.muuusic.data.model.ArtistDetail
import me.luzility.muuusic.data.settings.AudioQuality
import me.luzility.muuusic.data.model.CollectionDetail
import me.luzility.muuusic.data.model.CollectionType
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class YouTubeRepository(private val audioQuality: () -> AudioQuality = { AudioQuality.High }) {

    private val service = ServiceList.YouTube
    private val streamUrls = ConcurrentHashMap<String, CachedUrl>()

    private data class CachedUrl(val url: String, val expiresAtMs: Long)

    suspend fun search(query: String, filter: SearchFilter): List<SearchResult> = io {
        val contentFilter = when (filter) {
            SearchFilter.Songs -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            SearchFilter.Videos -> YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
            SearchFilter.Albums -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            SearchFilter.Playlists -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        }
        val extractor = service.getSearchExtractor(query, listOf(contentFilter), "")
        extractor.fetchPage()
        extractor.initialPage.items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> item.toSong()?.let(SearchResult::SongResult)
                is PlaylistInfoItem -> SearchResult.CollectionResult(
                    item.toCollection(
                        if (filter == SearchFilter.Albums) CollectionType.Album else CollectionType.Playlist
                    )
                )
                else -> null
            }
        }
    }

    suspend fun suggestions(query: String): List<String> = io {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    suspend fun collection(url: String, type: CollectionType): CollectionDetail = io {
        val info = PlaylistInfo.getInfo(service, url.forNewPipe())
        val songs = info.relatedItems.mapNotNull { it.toSong() }.toMutableList()
        var page = info.nextPage
        // A few extra pages so long albums are complete, capped.
        var pagesLeft = 5
        while (page != null && pagesLeft-- > 0) {
            val more = PlaylistInfo.getMoreItems(service, url.forNewPipe(), page)
            songs += more.items.mapNotNull { it.toSong() }
            page = more.nextPage
        }
        CollectionDetail(
            collection = MusicCollection(
                url = url,
                title = info.name,
                subtitle = info.uploaderName.orEmpty(),
                thumbnailUrl = info.thumbnails.best() ?: songs.firstOrNull()?.thumbnailUrl,
                songCount = songs.size.toLong(),
                type = type,
            ),
            songs = songs,
        )
    }

    suspend fun radio(videoId: String): List<Song> = io {
        val mix = runCatching {
            PlaylistInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId&list=RDAMVM$videoId")
                .relatedItems.mapNotNull { it.toSong() }
        }.getOrNull()
        if (!mix.isNullOrEmpty()) return@io mix.filter { it.id != videoId }

        StreamInfo.getInfo(service, watchUrl(videoId)).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSong() }
            .filter { it.id != videoId }
    }

    // Topic channels have empty tabs, so the channel only gives images and bio;
    // songs, releases and videos come from music searches filtered to it.
    suspend fun artist(url: String): ArtistDetail = withContext(Dispatchers.IO) {
        val channelUrl = url.forNewPipe()
        val channelId = channelIdOf(channelUrl)
        val info = ChannelInfo.getInfo(service, channelUrl)
        val name = info.name.removeSuffix(" - Topic")

        coroutineScope {
            val songs = async { rawSearch(name, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
            val albums = async { rawSearch(name, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS) }
            val videos = async { rawSearch(name, YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS) }
            val artists = async { rawSearch(name, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS) }

            // A song may point at the artist's video channel while YouTube Music files their music
            // under another ID; accept the artist search hit with the same name as well.
            val artistItems = artists.await().filterIsInstance<ChannelInfoItem>()
            val artistMatch = artistItems.firstOrNull { channelId != null && it.url.contains(channelId) }
                ?: artistItems.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val ids = setOfNotNull(channelId, artistMatch?.url?.let(::channelIdOf))
            fun byArtist(uploaderUrl: String?) = uploaderUrl != null && ids.any { uploaderUrl.contains(it) }

            val songItems = songs.await().filterIsInstance<StreamInfoItem>()
            val topSongs = songItems.filter { byArtist(it.uploaderUrl) }
                // Collaborations can list another channel first; fall back to the name.
                .ifEmpty { songItems.filter { it.uploaderName.orEmpty().contains(name, ignoreCase = true) } }
                .mapNotNull { it.toSong() }
            ArtistDetail(
                url = channelUrl,
                name = name,
                avatarUrl = info.avatars.maxByOrNull { it.height }?.url?.let(::largeAvatar),
                bannerUrl = info.banners.maxByOrNull { it.width }?.url,
                subscribers = artistMatch?.subscriberCount ?: -1,
                description = info.description?.takeIf { it.isNotBlank() },
                topSongs = topSongs,
                releases = albums.await().filterIsInstance<PlaylistInfoItem>()
                    .filter { byArtist(it.uploaderUrl) }
                    .map { it.toCollection(CollectionType.Album) },
                videos = videos.await().filterIsInstance<StreamInfoItem>()
                    .filter { byArtist(it.uploaderUrl) }
                    .mapNotNull { it.toSong() },
            )
        }
    }

    suspend fun findArtistUrl(name: String): String? = withContext(Dispatchers.IO) {
        rawSearch(name, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS)
            .filterIsInstance<ChannelInfoItem>()
            .firstOrNull()?.url?.forNewPipe()
    }

    private fun rawSearch(query: String, filter: String): List<InfoItem> = runCatching {
        val extractor = service.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        extractor.initialPage.items
    }.getOrDefault(emptyList())

    // Blocking; runs on ExoPlayer's loader thread.
    fun resolveStreamUrl(videoId: String): String {
        val quality = audioQuality()
        val key = "$videoId@$quality"
        streamUrls[key]?.let { if (it.expiresAtMs > System.currentTimeMillis()) return it.url }

        val info = StreamInfo.getInfo(service, watchUrl(videoId))
        val streams = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val best = when (quality) {
            AudioQuality.High -> streams.maxByOrNull { it.averageBitrate }
            // Roughly 128 kbps: the best stream that stays at or below it.
            AudioQuality.Normal -> streams.filter { it.averageBitrate in 1..NormalBitrateKbps }.maxByOrNull { it.averageBitrate }
                ?: streams.minByOrNull { it.averageBitrate }
            AudioQuality.Low -> streams.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
                ?: streams.firstOrNull()
        } ?: throw IOException("No playable audio stream for $videoId")

        val url = best.content
        val expire = Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()
        // Refresh a bit before YouTube's expiry; default to 5 hours when the param is missing.
        val expiresAt = expire?.let { it * 1000 - 30 * 60 * 1000 }
            ?: (System.currentTimeMillis() + 5 * 60 * 60 * 1000)
        streamUrls[key] = CachedUrl(url, expiresAt)
        return url
    }

    fun invalidateStreamUrl(videoId: String) {
        streamUrls.keys.removeAll { it.startsWith("$videoId@") }
    }

    private fun StreamInfoItem.toSong(): Song? {
        val id = videoIdOf(url) ?: return null
        return Song(
            id = id,
            title = name,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
            durationSec = duration.coerceAtLeast(0),
            thumbnailUrl = thumbnails.best(),
            artistUrl = uploaderUrl?.forNewPipe(),
        )
    }

    private fun PlaylistInfoItem.toCollection(type: CollectionType) = MusicCollection(
        url = url,
        title = name,
        subtitle = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.best(),
        songCount = streamCount,
        type = type,
    )

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val NormalBitrateKbps = 140

        fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

        // NewPipe fails to recognise music.youtube.com URLs under a Turkish locale (dotless I).
        // www serves the same content.
        fun String.forNewPipe(): String = replace("://music.youtube.com/", "://www.youtube.com/")

        fun channelIdOf(url: String): String? = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.get(1)

        // Channel avatars come as s160; ask for a bigger size.
        private fun largeAvatar(url: String) = url.replace(Regex("=s\\d+"), "=s720")

        private val videoIdPattern = Regex("[?&]v=([A-Za-z0-9_-]{11})")

        fun videoIdOf(url: String): String? =
            videoIdPattern.find(url)?.groupValues?.get(1)
                ?: runCatching { ServiceList.YouTube.streamLHFactory.getId(url.forNewPipe()) }.getOrNull()

        private val sizeParam = Regex("=w\\d+-h\\d+")

        fun List<Image>.best(): String? {
            val url = maxByOrNull { it.height }?.url ?: return null
            return if (url.contains("googleusercontent.com")) url.replace(sizeParam, "=w544-h544") else url
        }
    }
}
