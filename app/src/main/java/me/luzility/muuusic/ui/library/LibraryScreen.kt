package me.luzility.muuusic.ui.library

import me.luzility.muuusic.ui.components.downloadStatuses
import me.luzility.muuusic.data.download.DownloadStatus
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.LocalNavigator
import me.luzility.muuusic.ui.playlist.ImportPlaylistDialog
import me.luzility.muuusic.ui.playlist.PlaylistNameDialog
import me.luzility.muuusic.ui.playlist.PlaylistsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import me.luzility.muuusic.R
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.container
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.ui.components.EmptyState
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val library = app.container.library
    val liked: StateFlow<List<Song>> =
        library.liked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<Song>> =
        library.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private const val TabLiked = 0
private const val TabHistory = 1
private const val TabPlaylists = 2

@Composable
fun LibraryScreen(
    vm: LibraryViewModel = viewModel(),
    playlistsVm: PlaylistsViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val liked by vm.liked.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val playlists by playlistsVm.playlists.collectAsStateWithLifecycle()
    val currentId = player.state.collectAsStateWithLifecycle().value.current?.id
    var tab by rememberSaveable { mutableIntStateOf(TabLiked) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    val songs = if (tab == TabLiked) liked else history

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerInset.current)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Text(
                stringResource(R.string.nav_library),
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                listOf(
                    Triple(R.string.liked_songs, Icons.Rounded.Favorite, ButtonGroupDefaults.connectedLeadingButtonShapes()),
                    Triple(R.string.history, Icons.Rounded.History, ButtonGroupDefaults.connectedMiddleButtonShapes()),
                    Triple(R.string.playlists, Icons.AutoMirrored.Rounded.QueueMusic, ButtonGroupDefaults.connectedTrailingButtonShapes()),
                ).forEachIndexed { index, (label, icon, shapes) ->
                    val weight by animateFloatAsState(
                        if (tab == index) 1.3f else 1f,
                        MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "tabWeight",
                    )
                    ToggleButton(
                        checked = tab == index,
                        onCheckedChange = { tab = index },
                        shapes = shapes,
                        modifier = Modifier.weight(weight),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        // Three labels share one row; only the selected tab spends space on its icon.
                        AnimatedVisibility(
                            visible = tab == index,
                            enter = expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                        ) {
                            Row {
                                Icon(icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
                                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                            }
                        }
                        Text(stringResource(label), maxLines = 1)
                    }
                }
            }
        }

        if (tab == TabPlaylists) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = { creating = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.new_playlist), maxLines = 1)
                    }
                    OutlinedButton(onClick = { importing = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.import_action), maxLines = 1)
                    }
                }
            }
            item(key = "downloads") {
                val downloaded = downloadStatuses().values.count { it.state == DownloadStatus.State.Done }
                ListItem(
                    modifier = Modifier.clickable { navigator.openDownloads() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Box(
                            Modifier.size(64.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.DownloadDone, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.downloads)) },
                    supportingContent = { Text(pluralStringResource(R.plurals.song_count, downloaded, downloaded)) },
                )
            }
            if (playlists.isEmpty()) {
                item { EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.empty_playlists)) }
            }
            items(playlists, key = { "playlist-${it.id}" }) { playlist ->
                ListItem(
                    modifier = Modifier.clickable { navigator.openPlaylist(playlist.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Artwork(playlist.thumbnailUrl, Modifier.size(64.dp), MaterialTheme.shapes.large) },
                    headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount)) },
                )
            }
        } else if (songs.isEmpty()) {
            item {
                EmptyState(
                    icon = if (tab == TabLiked) Icons.Rounded.Favorite else Icons.Rounded.History,
                    text = stringResource(if (tab == TabLiked) R.string.empty_liked else R.string.empty_history),
                )
            }
        } else {
            item { PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) }) }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongListItem(song, isCurrent = song.id == currentId, onClick = { player.play(songs, index) })
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                creating = false
                playlistsVm.create(name) { id -> navigator.openPlaylist(id) }
            },
            onDismiss = { creating = false },
        )
    }
    if (importing) ImportPlaylistDialog(onDismiss = { importing = false }, vm = playlistsVm)
}

@Composable
fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPlay,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.play_all), style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalButton(
            onClick = onShuffle,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.shuffle), style = MaterialTheme.typography.titleMedium)
        }
    }
}
