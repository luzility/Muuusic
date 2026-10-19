package me.luzility.muuusic.ui.collection

import androidx.compose.foundation.layout.width
import me.luzility.muuusic.ui.components.DownloadAllButton
import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.luzility.muuusic.ui.playlist.PlaylistsViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import me.luzility.muuusic.R
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.container
import me.luzility.muuusic.data.model.CollectionDetail
import me.luzility.muuusic.data.model.CollectionType
import me.luzility.muuusic.ui.UiState
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.CenteredLoading
import me.luzility.muuusic.ui.components.ErrorState
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import me.luzility.muuusic.ui.library.PlayButtons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CollectionViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val youtube = app.container.youtube
    private val url: String = checkNotNull(savedState["url"])
    private val type = CollectionType.valueOf(savedState["type"] ?: CollectionType.Playlist.name)

    private val _state = MutableStateFlow<UiState<CollectionDetail>>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = UiState.of { youtube.collection(url, type) }
        }
    }
}

@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    vm: CollectionViewModel = viewModel(),
    playlistsVm: PlaylistsViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val currentId = player.state.collectAsStateWithLifecycle().value.current?.id

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
            }
            Spacer(Modifier.weight(1f))
            val loaded = (state as? UiState.Success)?.data
            if (loaded != null && loaded.songs.isNotEmpty()) {
                DownloadAllButton(loaded.songs)
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = {
                        playlistsVm.create(loaded.collection.title, loaded.songs)
                        Toast.makeText(context, context.getString(R.string.saved_to_library), Toast.LENGTH_SHORT).show()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.LibraryAdd, stringResource(R.string.save_to_library))
                }
            }
        }

        when (val s = state) {
            UiState.Loading -> CenteredLoading()
            is UiState.Error -> ErrorState(onRetry = vm::load)
            is UiState.Success -> {
                val detail = s.data
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerInset.current)) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Artwork(detail.collection.thumbnailUrl, Modifier.size(240.dp), MaterialTheme.shapes.extraLarge)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                detail.collection.title,
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                detail.collection.subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                pluralStringResource(R.plurals.song_count, detail.songs.size, detail.songs.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        PlayButtons(
                            onPlay = { player.play(detail.songs) },
                            onShuffle = { player.play(detail.songs, shuffle = true) },
                        )
                    }
                    itemsIndexed(detail.songs) { index, song ->
                        SongListItem(song, isCurrent = song.id == currentId, onClick = { player.play(detail.songs, index) })
                    }
                }
            }
        }
    }
}
