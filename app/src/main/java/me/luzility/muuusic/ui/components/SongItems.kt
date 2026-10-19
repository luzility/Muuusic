package me.luzility.muuusic.ui.components

import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.R
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.ui.playlist.AddToPlaylistSheet

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
) {
    ListItem(
        modifier = modifier.testTag("song_item").clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        headlineContent = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val duration = if (song.durationSec > 0) " • " + formatDuration(song.durationSec * 1000) else ""
            val download = downloadStatuses()[song.id]
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (download != null) {
                    DownloadBadge(download)
                    Spacer(Modifier.width(6.dp))
                }
                Text(song.artist + duration, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Artwork(song.thumbnailUrl, Modifier.size(52.dp), MaterialTheme.shapes.medium)
                if (isCurrent) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        trailingContent = { SongMenu(song) },
    )
}

@Composable
fun SongMenu(song: Song) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    var open by remember { mutableStateOf(false) }
    var addingToPlaylist by remember { mutableStateOf(false) }
    val liked by remember(song.id) { player.isLiked(song) }.collectAsState(initial = false)

    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.play_next)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = { player.playNext(song); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_queue)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { player.addToQueue(song); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_playlist)) },
                leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) },
                onClick = { addingToPlaylist = true; open = false },
            )
            val downloads = rememberDownloads()
            val downloaded = downloadStatuses()[song.id] != null
            DropdownMenuItem(
                text = { Text(stringResource(if (downloaded) R.string.remove_download else R.string.download)) },
                leadingIcon = { Icon(if (downloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download, null) },
                onClick = {
                    if (downloaded) downloads.remove(song.id) else downloads.download(listOf(song))
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.start_radio)) },
                leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                onClick = { player.startRadio(song); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.go_to_artist)) },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { navigator.openArtist(song.artist, song.artistUrl); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (liked) R.string.unlike else R.string.like)) },
                leadingIcon = {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
                },
                onClick = { player.setLiked(song, !liked); open = false },
            )
        }
    }
    if (addingToPlaylist) AddToPlaylistSheet(listOf(song), onDismiss = { addingToPlaylist = false })
}

@Composable
fun CollectionListItem(collection: MusicCollection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(collection.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(collection.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Artwork(collection.thumbnailUrl, Modifier.size(64.dp), MaterialTheme.shapes.large)
        },
    )
}
