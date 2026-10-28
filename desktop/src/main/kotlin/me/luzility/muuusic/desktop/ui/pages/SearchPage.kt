package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.ui.ErrorBox
import me.luzility.muuusic.desktop.ui.Loadable
import me.luzility.muuusic.desktop.ui.LoadingBox
import me.luzility.muuusic.desktop.ui.LocalNavigator
import me.luzility.muuusic.desktop.ui.LocalPlayer
import me.luzility.muuusic.desktop.ui.LocalServices
import me.luzility.muuusic.desktop.ui.ShelfCard
import me.luzility.muuusic.desktop.ui.SongRow
import me.luzility.muuusic.desktop.ui.Strings
import me.luzility.muuusic.desktop.ui.str

class SearchModel(private val services: DesktopServices, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var recognizing by mutableStateOf(false)
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestJob: Job? = null

    fun onQueryChange(text: String) {
        query = text
        results = null
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = scope.launch {
            delay(SUGGEST_DELAY_MS)
            suggestions = runCatching { services.youtube.suggestions(text) }.getOrDefault(emptyList())
        }
    }

    fun search(text: String) {
        query = text
        search()
    }
    var filter by mutableStateOf(SearchFilter.Songs)
        private set
    var results by mutableStateOf<Loadable<List<SearchResult>>?>(null)
        private set
    private var job: Job? = null

    fun search() {
        val text = query.trim()
        if (text.isEmpty()) return
        suggestJob?.cancel()
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

private const val SUGGEST_DELAY_MS = 250L

@Composable
private fun Discover(model: SearchModel) {
    val services = LocalServices.current
    val history by services.searchHistory.queries.collectAsState()
    val genres = remember { Strings.array("search_genres") }
    val colors = MaterialTheme.colorScheme
    val palette = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
    )
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), modifier = Modifier.widthIn(max = 960.dp)) {
        if (history.isNotEmpty()) {
            item(key = "historyHeader") {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(str("recent_searches"), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                    TextButton(onClick = services.searchHistory::clear) { Text(str("clear_all")) }
                }
            }
            items(history.take(8), key = { "h-$it" }) { entry ->
                ListItem(
                    modifier = Modifier.animateItem().clip(MaterialTheme.shapes.medium).clickable { model.search(entry) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    headlineContent = { Text(entry, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { services.searchHistory.remove(entry) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, str("remove"))
                        }
                    },
                )
            }
        }
        item(key = "genresHeader") {
            Text(str("browse"), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
        }
        items(genres.chunked(4), key = { "g-${it.first()}" }) { row ->
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { genre ->
                    val index = genres.indexOf(genre)
                    val (container, content) = palette[index % palette.size]
                    GenreCard(genre, container, content, PlayfulShapes[index % PlayfulShapes.size], Modifier.weight(1f)) { model.search(genre) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val PlayfulShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Arch,
    MaterialShapes.Sunny,
    MaterialShapes.Gem,
)

@Composable
private fun GenreCard(label: String, container: Color, content: Color, shape: RoundedPolygon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = container, contentColor = content, modifier = modifier.height(88.dp)) {
        Box {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 18.dp, y = 18.dp)
                    .size(72.dp)
                    .graphicsLayer { rotationZ = -18f; alpha = 0.35f }
                    .clip(shape.toShape())
                    .background(content),
            )
            Text(label, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchPage(model: SearchModel) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val recognition by LocalServices.current.settings.prefs.collectAsState().let { prefs -> remember { derivedStateOf { prefs.value.songRecognition } } }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = model.query,
            onValueChange = model::onQueryChange,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp).widthIn(max = 720.dp).fillMaxWidth().focusRequester(focus),
            placeholder = { Text(str("search_hint")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (model.query.isNotEmpty()) {
                    IconButton(onClick = { model.onQueryChange("") }) { Icon(Icons.Rounded.Close, str("close")) }
                } else if (recognition) {
                    IconButton(onClick = { model.recognizing = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(end = 4.dp)) {
                        BadgedBox(badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                Icon(Icons.Rounded.Science, null, Modifier.size(10.dp))
                            }
                        }) {
                            Icon(Icons.Rounded.GraphicEq, str("recognize_song"))
                        }
                    }
                }
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
            null -> if (model.query.isBlank()) {
                Discover(model)
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), modifier = Modifier.widthIn(max = 760.dp)) {
                    items(model.suggestions) { suggestion ->
                        ListItem(
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { model.search(suggestion) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Rounded.Search, null) },
                            headlineContent = { Text(suggestion) },
                            trailingContent = {
                                IconButton(onClick = { model.onQueryChange("$suggestion ") }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.NorthWest, str("fill_query"))
                                }
                            },
                        )
                    }
                }
            }
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
