package me.luzility.muuusic.ui.playlist

import me.luzility.muuusic.ui.components.DownloadAllButton
import android.app.Application
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.PlaylistItem
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.EmptyState
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import me.luzility.muuusic.ui.library.PlayButtons
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class PlaylistViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val repository = app.container.playlists
    val id: Long = checkNotNull(savedState.get<Long>("id"))

    val name: StateFlow<String?> = repository.name(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<PlaylistItem>?> = repository.items(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) = viewModelScope.launch { repository.rename(id, name) }
    fun delete() = viewModelScope.launch { repository.delete(id) }
    fun remove(entryId: Long) = viewModelScope.launch { repository.remove(entryId) }
    fun reorder(entryIds: List<Long>) = viewModelScope.launch { repository.reorder(id, entryIds) }
}

@Composable
fun PlaylistScreen(onBack: () -> Unit, vm: PlaylistViewModel = viewModel()) {
    val player = LocalPlayer.current
    val haptics = LocalHapticFeedback.current
    val name by vm.name.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val currentId = player.state.collectAsStateWithLifecycle().value.current?.id

    // Local copy so a drag moves rows instantly; it is written back once the drag ends.
    var rows by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items, dragging) { if (!dragging) items?.let { rows = it } }

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = rows.indexOfFirst { it.entryId == from.key }
        val toIndex = rows.indexOfFirst { it.entryId == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        rows = rows.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val songs = rows.map { it.song }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerInset.current),
    ) {
        item(key = "top") { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "toolbar") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                }
                Spacer(Modifier.weight(1f))
                if (rows.isNotEmpty()) DownloadAllButton(songs)
                Box {
                    IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        }
        item(key = "header") { PlaylistHeader(name.orEmpty(), rows) }
        item(key = "buttons") {
            if (rows.isNotEmpty()) {
                PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) })
            }
        }

        if (items != null && rows.isEmpty()) {
            item(key = "empty") { EmptyState(Icons.Rounded.QueueMusic, stringResource(R.string.empty_playlist)) }
        }

        itemsIndexed(rows, key = { _, row -> row.entryId }) { index, row ->
            ReorderableItem(reorderState, key = row.entryId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                RemovableRow(onRemove = { vm.remove(row.entryId) }) {
                    Surface(
                        shadowElevation = elevation,
                        shape = MaterialTheme.shapes.large,
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SongListItem(
                                song = row.song,
                                isCurrent = row.song.id == currentId,
                                onClick = { player.play(songs, index) },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle(
                                        onDragStarted = {
                                            dragging = true
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            dragging = false
                                            vm.reorder(rows.map { it.entryId })
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        },
                                    )
                                    .padding(start = 4.dp, end = 16.dp)
                                    .size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(R.string.rename),
            confirmLabel = stringResource(R.string.save),
            initial = name.orEmpty(),
            onConfirm = { vm.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_confirm, name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { vm.delete(); confirmDelete = false; onBack() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RemovableRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onRemove()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        content()
    }
}

@Composable
private fun PlaylistHeader(name: String, rows: List<PlaylistItem>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val covers = rows.mapNotNull { it.song.thumbnailUrl }.distinct()
        Box(Modifier.size(220.dp).clip(MaterialTheme.shapes.extraLarge)) {
            if (covers.size >= 4) {
                Column {
                    for (r in 0..1) Row {
                        for (c in 0..1) Artwork(covers[r * 2 + c], Modifier.weight(1f).aspectRatio(1f), MaterialTheme.shapes.extraSmall)
                    }
                }
            } else {
                Artwork(covers.firstOrNull(), Modifier.fillMaxSize(), MaterialTheme.shapes.extraSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        Text(
            pluralStringResource(R.plurals.song_count, rows.size, rows.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
