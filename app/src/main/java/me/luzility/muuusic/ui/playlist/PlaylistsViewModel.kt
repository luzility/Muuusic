package me.luzility.muuusic.ui.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.luzility.muuusic.container
import me.luzility.muuusic.data.PlaylistSummary
import me.luzility.muuusic.data.model.CollectionType
import me.luzility.muuusic.data.model.Song

sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    data class Done(val name: String, val count: Int) : ImportState
    data object InvalidLink : ImportState
    data object Failed : ImportState
}

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = app.container.playlists
    private val youtube = app.container.youtube

    val playlists: StateFlow<List<PlaylistSummary>> =
        repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val import = _import.asStateFlow()

    fun create(name: String, songs: List<Song> = emptyList(), onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(repository.create(name, songs)) }
    }

    fun addSongs(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { repository.addSongs(playlistId, songs) }
    }

    fun importFromLink(link: String) {
        val listId = playlistIdOf(link)
        if (listId == null) {
            _import.value = ImportState.InvalidLink
            return
        }
        viewModelScope.launch {
            _import.value = ImportState.Working
            _import.value = runCatching {
                val detail = youtube.collection("https://www.youtube.com/playlist?list=$listId", CollectionType.Playlist)
                repository.create(detail.collection.title, detail.songs)
                ImportState.Done(detail.collection.title, detail.songs.size)
            }.getOrElse { ImportState.Failed }
        }
    }

    fun resetImport() {
        _import.value = ImportState.Idle
    }

    companion object {
        private val listParam = Regex("[?&]list=([A-Za-z0-9_-]+)")

        fun playlistIdOf(link: String): String? = listParam.find(link.trim())?.groupValues?.get(1)
    }
}
