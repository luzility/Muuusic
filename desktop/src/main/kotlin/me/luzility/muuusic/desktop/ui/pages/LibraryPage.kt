package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.desktop.ui.Artwork
import me.luzility.muuusic.desktop.ui.LocalNavigator
import me.luzility.muuusic.desktop.ui.LocalPlayer
import me.luzility.muuusic.desktop.ui.LocalServices
import me.luzility.muuusic.desktop.ui.PlayButtons
import me.luzility.muuusic.desktop.ui.Route
import me.luzility.muuusic.desktop.ui.ShelfCard
import me.luzility.muuusic.desktop.ui.SongRow
import me.luzility.muuusic.desktop.ui.Strings
import me.luzility.muuusic.desktop.ui.str

private val tabs = listOf("liked_songs", "history", "playlists")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryPage() {
    val services = LocalServices.current
    val navigator = LocalNavigator.current
    var tab by remember { mutableIntStateOf(0) }
    val liked by services.library.liked.collectAsState(initial = emptyList())
    val history by services.library.history.collectAsState(initial = emptyList())
    val playlists by services.playlists.playlists.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Text(str("nav_library"), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.padding(start = 32.dp, top = 32.dp, bottom = 16.dp))
        Row(Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            tabs.forEachIndexed { index, key ->
                ToggleButton(
                    checked = tab == index,
                    onCheckedChange = { tab = index },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) { Text(str(key)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> SongList(liked, empty = str("empty_liked"))
            1 -> SongList(history, empty = str("empty_history"))
            else -> if (playlists.isEmpty()) {
                Text(str("empty_playlists"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
            } else {
                FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    playlists.forEach { playlist ->
                        ShelfCard(
                            title = playlist.name,
                            subtitle = Strings.plural("song_count", playlist.songCount),
                            imageUrl = playlist.thumbnailUrl,
                            onClick = { navigator.open(Route.Playlist(playlist.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, empty: String) {
    val player = LocalPlayer.current
    if (songs.isEmpty()) {
        Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.padding(start = 12.dp, bottom = 12.dp)) {
                PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) })
            }
        }
        itemsIndexed(songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
            SongRow(song, index, onPlay = { player.play(songs, index) })
        }
    }
}

@Composable
fun PlaylistPage(id: Long) {
    val services = LocalServices.current
    val player = LocalPlayer.current
    val name by remember(id) { services.playlists.name(id) }.collectAsState(initial = null)
    val items by remember(id) { services.playlists.items(id) }.collectAsState(initial = emptyList())
    val songs = items.map { it.song }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.padding(start = 12.dp, top = 32.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                Artwork(songs.firstOrNull()?.thumbnailUrl, Modifier.size(220.dp), MaterialTheme.shapes.extraLarge)
                Spacer(Modifier.width(28.dp))
                Column {
                    Text(name.orEmpty(), style = MaterialTheme.typography.displaySmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Strings.plural("song_count", songs.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (songs.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) })
                    }
                }
            }
        }
        if (songs.isEmpty()) item { Text(str("empty_playlist"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
        itemsIndexed(items, key = { _, item -> item.entryId }) { index, item ->
            SongRow(item.song, index, onPlay = { player.play(songs, index) })
        }
    }
}
