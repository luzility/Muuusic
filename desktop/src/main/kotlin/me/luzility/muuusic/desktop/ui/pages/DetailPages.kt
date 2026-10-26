package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.luzility.muuusic.data.model.ArtistDetail
import me.luzility.muuusic.data.model.ArtistPage
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.data.model.CollectionDetail
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.youtube.YouTubeRepository
import me.luzility.muuusic.desktop.ui.Artwork
import me.luzility.muuusic.desktop.ui.BrowseShelf
import me.luzility.muuusic.desktop.ui.ErrorBox
import me.luzility.muuusic.desktop.ui.Loadable
import me.luzility.muuusic.desktop.ui.LoadingBox
import me.luzility.muuusic.desktop.ui.LocalPlayer
import me.luzility.muuusic.desktop.ui.LocalServices
import me.luzility.muuusic.desktop.ui.PlayButtons
import me.luzility.muuusic.desktop.ui.SectionTitle
import me.luzility.muuusic.desktop.ui.SongRow
import me.luzility.muuusic.desktop.ui.Strings
import me.luzility.muuusic.desktop.ui.str

@Composable
fun CollectionPage(collection: MusicCollection) {
    val services = LocalServices.current
    val player = LocalPlayer.current
    var attempt by remember { mutableIntStateOf(0) }
    val detail by produceState<Loadable<CollectionDetail>>(Loadable.Loading, collection.url, attempt) {
        value = Loadable.Loading
        value = runCatching { services.youtube.collection(collection.url, collection.type) }
            .fold(onSuccess = { Loadable.Ready(it) }, onFailure = { Loadable.Failed })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        val loaded = (detail as? Loadable.Ready)?.data
        val info = loaded?.collection ?: collection
        val songs = loaded?.songs.orEmpty()
        item {
            Row(Modifier.padding(start = 12.dp, top = 32.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                Artwork(info.thumbnailUrl, Modifier.size(220.dp), MaterialTheme.shapes.extraLarge)
                Spacer(Modifier.width(28.dp))
                Column {
                    Text(info.title, style = MaterialTheme.typography.displaySmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (info.subtitle.isNotBlank()) {
                        Text(info.subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (songs.isNotEmpty()) {
                        Text(Strings.plural("song_count", songs.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) })
                    }
                }
            }
        }
        when (detail) {
            Loadable.Loading -> item { LoadingBox() }
            Loadable.Failed -> item { ErrorBox(onRetry = { attempt++ }) }
            is Loadable.Ready -> itemsIndexed(songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                SongRow(song, index, onPlay = { player.play(songs, index) })
            }
        }
    }
}

@Composable
fun ArtistPage(name: String, url: String?) {
    val services = LocalServices.current
    val player = LocalPlayer.current
    var attempt by remember { mutableIntStateOf(0) }
    val page by produceState<Loadable<ArtistPage>>(Loadable.Loading, name, url, attempt) {
        value = Loadable.Loading
        value = runCatching {
            val resolved = url ?: services.youtube.findArtistUrl(name) ?: error("Artist not found: $name")
            val channelId = YouTubeRepository.channelIdOf(resolved)
            val full = channelId?.let { runCatching { services.innerTube.artist(it, resolved) }.getOrNull() }
            full?.takeIf { it.sections.isNotEmpty() } ?: services.youtube.artist(resolved).toPage()
        }.fold(onSuccess = { Loadable.Ready(it) }, onFailure = { Loadable.Failed })
    }
    when (val state = page) {
        Loadable.Loading -> LoadingBox()
        Loadable.Failed -> ErrorBox(onRetry = { attempt++ })
        is Loadable.Ready -> {
            val artist = state.data
            val topSongs = artist.sections.firstOrNull { section ->
                section.items.isNotEmpty() && section.items.all { it is BrowseItem.SongItem && !it.wide }
            }
            val songs = topSongs?.items?.map { (it as BrowseItem.SongItem).song }.orEmpty()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { ArtistHeader(artist) }
                if (songs.isNotEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
                            PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) })
                        }
                    }
                }
                artist.sections.forEachIndexed { index, section ->
                    if (section === topSongs) {
                        item(key = "top") { SectionTitle(section.title) }
                        itemsIndexed(songs.take(10), key = { i, song -> "top-$i-${song.id}" }) { i, song ->
                            Box(Modifier.padding(horizontal = 20.dp)) { SongRow(song, i, onPlay = { player.play(songs, i) }) }
                        }
                    } else {
                        item(key = "section-$index") { BrowseShelf(section) }
                    }
                }
                artist.description?.let { bio ->
                    item {
                        SectionTitle(str("artist_about"))
                        Text(
                            bio,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(artist: ArtistPage) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        if (artist.bannerUrl != null) {
            AsyncImage(model = artist.bannerUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Color.Transparent, 1f to colors.surface)))
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
            if (artist.avatarUrl != null) {
                Artwork(artist.avatarUrl, Modifier.size(140.dp), MaterialShapes.Cookie12Sided.toShape())
                Spacer(Modifier.width(24.dp))
            }
            Column {
                Text(artist.name, style = MaterialTheme.typography.displayMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val line = artist.audience ?: artist.subscribers?.let { str("artist_subscribers", it) }
                if (line != null) Text(line, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

private fun ArtistDetail.toPage() = ArtistPage(
    name = name,
    url = url,
    bannerUrl = bannerUrl,
    subscribers = subscribers.takeIf { it >= 0 }?.toString(),
    audience = null,
    avatarUrl = avatarUrl,
    description = description,
    sections = listOf(
        BrowseSection(str("artist_popular"), topSongs.map { BrowseItem.SongItem(it) }),
        BrowseSection(str("artist_releases"), releases.map { BrowseItem.CollectionItem(it) }),
        BrowseSection(str("filter_videos"), videos.map { BrowseItem.SongItem(it, wide = true) }),
    ).filter { it.items.isNotEmpty() },
)
