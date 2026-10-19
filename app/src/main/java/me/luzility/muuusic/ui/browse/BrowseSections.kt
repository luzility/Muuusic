package me.luzility.muuusic.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luzility.muuusic.R
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.LocalNavigator
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import me.luzility.muuusic.ui.components.playfulShape

private val CardWidth = 152.dp
private val WideCardWidth = 240.dp
private const val GridRows = 4

@Composable
fun BrowseSectionView(section: BrowseSection, songsAsList: Boolean = false, listLimit: Int = 5) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val currentId = player.state.collectAsStateWithLifecycle().value.current?.id
    val songItems = section.items.filterIsInstance<BrowseItem.SongItem>()
    val songs = songItems.map { it.song }
    if (section.items.isEmpty()) return
    val songShelf = songItems.size == section.items.size && songItems.none { it.wide }

    Column {
        SectionHeader(section.title, onMore = section.more?.let { more -> { navigator.openCollection(more) } })
        when {
            songShelf && songsAsList -> songs.take(listLimit).forEachIndexed { index, song ->
                SongListItem(song, isCurrent = song.id == currentId, onClick = { player.play(songs, index) })
            }
            songShelf -> {
                val rows = minOf(GridRows, songs.size)
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(rows),
                    modifier = Modifier.fillMaxWidth().height(SongRowHeight * rows),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        SongListItem(
                            song = song,
                            isCurrent = song.id == currentId,
                            onClick = { player.play(songs, index) },
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }
            }
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(section.items) { index, item ->
                    BrowseCard(item, index) {
                        when (item) {
                            is BrowseItem.SongItem -> player.play(songs, songs.indexOf(item.song))
                            is BrowseItem.CollectionItem -> navigator.openCollection(item.collection)
                            is BrowseItem.ArtistItem -> navigator.openArtist(item.name, item.url)
                        }
                    }
                }
            }
        }
    }
}

private val SongRowHeight: Dp = 72.dp

@Composable
fun SectionHeader(title: String, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onMore != null) {
            TextButton(onClick = onMore) { Text(stringResource(R.string.show_all)) }
        }
    }
}

@Composable
private fun BrowseCard(item: BrowseItem, index: Int, onClick: () -> Unit) {
    when (item) {
        is BrowseItem.CollectionItem -> SquareCard(
            item.collection.thumbnailUrl, item.collection.title, item.collection.subtitle, onClick,
        )
        is BrowseItem.SongItem -> if (item.wide) {
            Column(Modifier.width(WideCardWidth).clickable(onClick = onClick)) {
                Artwork(item.song.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.shapes.large)
                CardText(item.song.title, item.song.artist)
            }
        } else {
            SquareCard(item.song.thumbnailUrl, item.song.title, item.song.artist, onClick)
        }
        is BrowseItem.ArtistItem -> Column(
            Modifier.width(132.dp).clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(item.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), playfulShape(index))
            Spacer(Modifier.height(8.dp))
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SquareCard(thumbnail: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.width(CardWidth).clickable(onClick = onClick)) {
        Artwork(thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), MaterialTheme.shapes.large)
        CardText(title, subtitle)
    }
}

@Composable
private fun CardText(title: String, subtitle: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle.isNotBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
