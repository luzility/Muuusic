package me.luzility.muuusic.data.innertube

import me.luzility.muuusic.data.model.ArtistPage
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.data.model.CollectionType
import me.luzility.muuusic.data.model.HomeFeed
import me.luzility.muuusic.data.model.Mood
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

object InnerTubeParser {

    fun home(json: JSONObject): HomeFeed {
        val list = sectionList(json)
        val moods = list?.array("header", "chipCloudRenderer", "chips").objects().mapNotNull { chip ->
            val renderer = chip.path("chipCloudChipRenderer") ?: return@mapNotNull null
            val title = renderer.array("text", "runs").firstText() ?: return@mapNotNull null
            val params = renderer.path("navigationEndpoint", "browseEndpoint")?.string("params") ?: return@mapNotNull null
            Mood(title, params)
        }
        return HomeFeed(moods, sections(list?.optJSONArray("contents")))
    }

    fun homeContinuation(json: JSONObject): List<BrowseSection> =
        sections(json.path("continuationContents", "sectionListContinuation")?.optJSONArray("contents"))

    fun continuation(json: JSONObject): String? {
        val list = sectionList(json) ?: json.path("continuationContents", "sectionListContinuation")
        return list?.optJSONArray("continuations").objects().firstOrNull()
            ?.path("nextContinuationData")?.string("continuation")
    }

    fun visitorData(json: JSONObject): String? = json.path("responseContext")?.string("visitorData")

    fun artist(json: JSONObject, url: String): ArtistPage? {
        val header = json.path("header", "musicImmersiveHeaderRenderer") ?: json.path("header", "musicVisualHeaderRenderer")
            ?: return null
        val name = header.array("title", "runs").firstText() ?: return null
        val list = sectionList(json)
        val contents = list?.optJSONArray("contents")
        val description = contents.objects()
            .firstNotNullOfOrNull { it.array("musicDescriptionShelfRenderer", "description", "runs").joinedText() }
            ?: header.array("description", "runs").joinedText()
        return ArtistPage(
            name = name,
            url = url,
            bannerUrl = header.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").lastUrl(square = false),
            subscribers = header.array("subscriptionButton", "subscribeButtonRenderer", "subscriberCountText", "runs").joinedText(),
            audience = header.array("monthlyListenerCount", "runs").joinedText(),
            description = description?.takeIf { it.isNotBlank() },
            sections = sections(contents),
        )
    }

    private fun sectionList(json: JSONObject): JSONObject? =
        json.array("contents", "singleColumnBrowseResultsRenderer", "tabs").objectAt(0)
            ?.path("tabRenderer", "content", "sectionListRenderer")

    private fun sections(contents: JSONArray?): List<BrowseSection> = contents.objects().mapNotNull { section ->
        val carousel = section.path("musicCarouselShelfRenderer")
        val shelf = section.path("musicShelfRenderer")
        val renderer = carousel ?: shelf ?: return@mapNotNull null
        val title = carousel?.array("header", "musicCarouselShelfBasicHeaderRenderer", "title", "runs").firstText()
            ?: shelf?.array("title", "runs").firstText()
            ?: return@mapNotNull null
        val items = renderer.optJSONArray("contents").objects().mapNotNull(::item)
        if (items.isEmpty()) return@mapNotNull null
        val moreId = shelf?.path("bottomEndpoint", "browseEndpoint")?.string("browseId")
        BrowseSection(title, items, more = moreId?.let { playlistOf(it, title) })
    }

    private fun item(entry: JSONObject): BrowseItem? =
        entry.path("musicResponsiveListItemRenderer")?.let(::responsiveItem)
            ?: entry.path("musicTwoRowItemRenderer")?.let(::twoRowItem)

    private fun responsiveItem(item: JSONObject): BrowseItem? {
        val columns = item.optJSONArray("flexColumns").objects()
            .map { it.array("musicResponsiveListItemFlexColumnRenderer", "text", "runs") }
        val videoId = item.path("playlistItemData")?.string("videoId")
            ?: item.path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")
                ?.string("videoId")
            ?: return null
        val title = columns.getOrNull(0).firstText() ?: return null
        val artistRuns = columns.getOrNull(1).objects().filter { it.browseId()?.startsWith("UC") == true }
        val artist = artistRuns.joinToString(", ") { it.string("text").orEmpty() }
            .ifBlank { columns.getOrNull(1).firstText().orEmpty() }
        val duration = item.optJSONArray("fixedColumns").objects()
            .firstNotNullOfOrNull { it.array("musicResponsiveListItemFixedColumnRenderer", "text", "runs").firstText() }
        return BrowseItem.SongItem(
            Song(
                id = videoId,
                title = title,
                artist = artist,
                durationSec = duration?.let(::parseDuration) ?: 0,
                thumbnailUrl = item.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").lastUrl(),
                artistUrl = artistRuns.firstOrNull()?.browseId()?.let(::channelUrl),
            )
        )
    }

    private fun twoRowItem(item: JSONObject): BrowseItem? {
        val title = item.array("title", "runs").firstText() ?: return null
        val subtitleRuns = item.array("subtitle", "runs")
        val subtitle = subtitleRuns.joinedText().orEmpty()
        val thumbnail = item.array("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails").lastUrl()
        val navigation = item.path("navigationEndpoint")

        navigation?.path("watchEndpoint")?.string("videoId")?.let { videoId ->
            val artistRuns = subtitleRuns.objects().filter { it.browseId()?.startsWith("UC") == true }
            return BrowseItem.SongItem(
                Song(
                    id = videoId,
                    title = title,
                    artist = artistRuns.joinToString(", ") { it.string("text").orEmpty() }.ifBlank { subtitle },
                    thumbnailUrl = thumbnail,
                    artistUrl = artistRuns.firstOrNull()?.browseId()?.let(::channelUrl),
                ),
                wide = item.string("aspectRatio")?.contains("16_9") == true,
            )
        }

        val browse = navigation?.path("browseEndpoint") ?: return null
        val browseId = browse.string("browseId") ?: return null
        val pageType = browse.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig")?.string("pageType")
        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") ->
                BrowseItem.ArtistItem(title, channelUrl(browseId), thumbnail, subtitle)
            pageType == "MUSIC_PAGE_TYPE_ALBUM" -> {
                // Albums are opened through the playable playlist behind their play button.
                val playlistId = item.path(
                    "thumbnailOverlay", "musicItemThumbnailOverlayRenderer", "content",
                    "musicPlayButtonRenderer", "playNavigationEndpoint", "watchPlaylistEndpoint",
                )?.string("playlistId") ?: return null
                BrowseItem.CollectionItem(MusicCollection(playlistUrl(playlistId), title, subtitle, thumbnail, type = CollectionType.Album))
            }
            browseId.startsWith("VL") ->
                BrowseItem.CollectionItem(
                    MusicCollection(playlistUrl(browseId.removePrefix("VL")), title, subtitle, thumbnail, type = CollectionType.Playlist)
                )
            else -> null
        }
    }

    private fun playlistOf(browseId: String, title: String): MusicCollection? {
        if (!browseId.startsWith("VL")) return null
        return MusicCollection(playlistUrl(browseId.removePrefix("VL")), title, "", null, type = CollectionType.Playlist)
    }

    private fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"
    private fun channelUrl(id: String) = "https://www.youtube.com/channel/$id"

    internal fun parseDuration(text: String): Long? {
        val parts = text.split(':').map { it.trim().toLongOrNull() ?: return null }
        return parts.fold(0L) { total, part -> total * 60 + part }
    }


    private fun JSONObject.path(vararg keys: String): JSONObject? {
        var current: JSONObject = this
        for (key in keys) current = current.optJSONObject(key) ?: return null
        return current
    }

    private fun JSONObject?.array(first: String, vararg keys: String): JSONArray? {
        this ?: return null
        val all = listOf(first) + keys
        var current: JSONObject = this
        for (key in all.dropLast(1)) current = current.optJSONObject(key) ?: return null
        return current.optJSONArray(all.last())
    }

    private fun JSONObject.string(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
    private fun JSONObject.browseId(): String? = path("navigationEndpoint", "browseEndpoint")?.string("browseId")
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()
    private fun JSONArray?.objectAt(index: Int): JSONObject? = this?.optJSONObject(index)
    private fun JSONArray?.firstText(): String? = objects().firstOrNull()?.string("text")
    private fun JSONArray?.joinedText(): String? = objects().joinToString("") { it.string("text").orEmpty() }.takeIf { it.isNotEmpty() }

    // Largest thumbnail; square art is re-requested from googleusercontent at a larger size.
    private fun JSONArray?.lastUrl(square: Boolean = true): String? {
        val url = objects().lastOrNull()?.string("url") ?: return null
        return if (square && url.contains("googleusercontent.com") && url.contains(Regex("=w\\d+-h\\d+"))) {
            url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
        } else {
            url
        }
    }
}
