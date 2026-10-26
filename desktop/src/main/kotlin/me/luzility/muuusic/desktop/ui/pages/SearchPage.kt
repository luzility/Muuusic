package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.ui.ErrorBox
import me.luzility.muuusic.desktop.ui.Loadable
import me.luzility.muuusic.desktop.ui.LoadingBox
import me.luzility.muuusic.desktop.ui.LocalNavigator
import me.luzility.muuusic.desktop.ui.LocalPlayer
import me.luzility.muuusic.desktop.ui.ShelfCard
import me.luzility.muuusic.desktop.ui.SongRow
import me.luzility.muuusic.desktop.ui.str

class SearchModel(private val services: DesktopServices, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter.Songs)
        private set
    var results by mutableStateOf<Loadable<List<SearchResult>>?>(null)
        private set
    private var job: Job? = null

    fun search() {
        val text = query.trim()
        if (text.isEmpty()) return
        job?.cancel()
        job = scope.launch {
            results = Loadable.Loading
            results = runCatching { services.youtube.search(text, filter) }
                .fold(onSuccess = { Loadable.Ready(it) }, onFailure = { Loadable.Failed })
            services.searchHistory.add(text)
        }
    }

    fun selectFilter(next: SearchFilter) {
        filter = next
        if (results != null) search()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchPage(model: SearchModel) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = model.query,
            onValueChange = { model.query = it },
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp).widthIn(max = 720.dp).fillMaxWidth().focusRequester(focus),
            placeholder = { Text(str("search_hint")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (model.query.isNotEmpty()) IconButton(onClick = { model.query = "" }) { Icon(Icons.Rounded.Close, str("close")) }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { model.search() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Row(Modifier.padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchFilter.entries.forEach { filter ->
                FilterChip(
                    selected = model.filter == filter,
                    onClick = { model.selectFilter(filter) },
                    label = {
                        Text(
                            str(
                                when (filter) {
                                    SearchFilter.Songs -> "filter_songs"
                                    SearchFilter.Videos -> "filter_videos"
                                    SearchFilter.Albums -> "filter_albums"
                                    SearchFilter.Playlists -> "filter_playlists"
                                },
                            ),
                        )
                    },
                    shape = CircleShape,
                )
            }
        }
        when (val results = model.results) {
            null -> Unit
            Loadable.Loading -> LoadingBox()
            Loadable.Failed -> ErrorBox(onRetry = model::search)
            is Loadable.Ready -> {
                val songs = results.data.filterIsInstance<SearchResult.SongResult>().map { it.song }
                val collections = results.data.filterIsInstance<SearchResult.CollectionResult>().map { it.collection }
                if (results.data.isEmpty()) {
                    Text(str("no_results"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
                }
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(song, index, onPlay = { player.play(songs, index) })
                    }
                    if (collections.isNotEmpty()) {
                        item {
                            FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                collections.forEach { collection ->
                                    ShelfCard(
                                        title = collection.title,
                                        subtitle = collection.subtitle,
                                        imageUrl = collection.thumbnailUrl,
                                        onClick = { navigator.openCollection(collection) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
