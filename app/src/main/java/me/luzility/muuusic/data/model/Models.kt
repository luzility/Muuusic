package me.luzility.muuusic.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long = 0,
    val thumbnailUrl: String? = null,
    val artistUrl: String? = null,
)

enum class CollectionType { Album, Playlist }

@Immutable
data class MusicCollection(
    val url: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songCount: Long = -1,
    val type: CollectionType,
)

@Immutable
data class CollectionDetail(
    val collection: MusicCollection,
    val songs: List<Song>,
)

sealed interface SearchResult {
    data class SongResult(val song: Song) : SearchResult
    data class CollectionResult(val collection: MusicCollection) : SearchResult
}

enum class SearchFilter { Songs, Videos, Albums, Playlists }

@Immutable
data class ArtistDetail(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** -1 when unknown. */
    val subscribers: Long,
    val description: String?,
    val topSongs: List<Song>,
    val releases: List<MusicCollection>,
    val videos: List<Song>,
)

sealed interface BrowseItem {
    /** [wide] marks music videos, shown with a 16:9 thumbnail. */
    data class SongItem(val song: Song, val wide: Boolean = false) : BrowseItem
    data class CollectionItem(val collection: MusicCollection) : BrowseItem
    data class ArtistItem(val name: String, val url: String, val thumbnailUrl: String?, val subtitle: String) : BrowseItem
}

@Immutable
data class BrowseSection(val title: String, val items: List<BrowseItem>, val more: MusicCollection? = null)

@Immutable
data class Mood(val title: String, val params: String)

@Immutable
data class HomeFeed(val moods: List<Mood>, val sections: List<BrowseSection>)

@Immutable
data class ArtistPage(
    val name: String,
    val url: String,
    val bannerUrl: String?,
    val subscribers: String?,
    val audience: String?,
    /** Round profile picture; YouTube Music pages only have the wide banner. */
    val avatarUrl: String? = null,
    val description: String?,
    val sections: List<BrowseSection>,
)
