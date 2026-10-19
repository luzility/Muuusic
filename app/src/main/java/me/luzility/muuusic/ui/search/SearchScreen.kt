package me.luzility.muuusic.ui.search

import me.luzility.muuusic.data.settings.Hint
import me.luzility.muuusic.ui.components.HintAnchor
import me.luzility.muuusic.ui.components.playfulShapes
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.toShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.speech.RecognizerIntent
import android.content.Intent
import android.content.ActivityNotFoundException
import android.app.Activity
import androidx.compose.ui.platform.testTag
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import me.luzility.muuusic.R
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.container
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.ui.UiState
import me.luzility.muuusic.ui.components.CenteredLoading
import me.luzility.muuusic.ui.components.CollectionListItem
import me.luzility.muuusic.ui.components.EmptyState
import me.luzility.muuusic.ui.components.ErrorState
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val youtube = app.container.youtube
    private val searchHistory = app.container.searchHistory

    val history: StateFlow<List<String>> = searchHistory.queries
    val recognitionEnabled: StateFlow<Boolean> = app.container.settings.prefs
        .map { it.songRecognition }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.Songs)
    val filter = _filter.asStateFlow()

    // null = suggestions, otherwise results for the submitted query.
    private val _results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    val results = _results.asStateFlow()

    val suggestions = _query
        .debounce(250)
        .distinctUntilChanged()
        .mapLatest { q -> if (q.isBlank()) emptyList() else youtube.suggestions(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null
    private var submitted: String? = null

    fun onQueryChange(value: String) {
        _query.value = value
        if (value != submitted) _results.value = null
    }

    fun setFilter(value: SearchFilter) {
        _filter.value = value
        if (submitted != null) search(submitted!!)
    }

    fun search(text: String = _query.value) {
        if (text.isBlank()) return
        _query.value = text
        submitted = text
        searchHistory.add(text)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _results.value = UiState.Loading
            _results.value = UiState.of { youtube.search(text, _filter.value) }
        }
    }

    fun removeFromHistory(query: String) = searchHistory.remove(query)

    fun clearHistory() = searchHistory.clear()
}

@Composable
fun SearchScreen(
    onOpenCollection: (MusicCollection) -> Unit,
    onRecognize: () -> Unit,
    recognizedQuery: String?,
    onRecognizedConsumed: () -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val context = LocalContext.current
    val history by vm.history.collectAsStateWithLifecycle()
    val recognitionEnabled by vm.recognitionEnabled.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val currentId = player.state.collectAsStateWithLifecycle().value.current?.id
    val focus = LocalFocusManager.current

    // A song identified on the recognition screen comes back here as a search.
    LaunchedEffect(recognizedQuery) {
        if (recognizedQuery != null) {
            vm.search(recognizedQuery)
            onRecognizedConsumed()
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            vm.search(spoken)
            focus.clearFocus()
        }
    }
    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.search_hint))
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("search_field"),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                AnimatedContent(targetState = query.isNotEmpty(), label = "searchTrailing") { hasText ->
                    if (hasText) {
                        IconButton(onClick = { vm.onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                        }
                    } else {
                        Row(Modifier.padding(end = 4.dp)) {
                            IconButton(onClick = ::startVoiceSearch, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Mic, stringResource(R.string.voice_search))
                            }
                            if (recognitionEnabled) {
                                HintAnchor(Hint.Recognition, R.string.hint_recognition_title, R.string.hint_recognition, above = false) {
                                    IconButton(
                                        onClick = onRecognize,
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier.testTag("recognize_button"),
                                    ) {
                                        BadgedBox(badge = {
                                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                                Icon(Icons.Rounded.Science, null, Modifier.size(10.dp))
                                            }
                                        }) {
                                            Icon(Icons.Rounded.GraphicEq, stringResource(R.string.recognize_song))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(); focus.clearFocus() }),
        )

        FilterButtons(selected = filter, onSelect = vm::setFilter)

        when (val r = results) {
            null -> if (query.isBlank()) {
                Discover(
                    history = history,
                    onSearch = { vm.search(it); focus.clearFocus() },
                    onRemove = vm::removeFromHistory,
                    onClear = vm::clearHistory,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)) {
                    items(suggestions) { suggestion ->
                        ListItem(
                            modifier = Modifier.testTag("search_suggestion").clickable { vm.search(suggestion); focus.clearFocus() },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Rounded.Search, null) },
                            headlineContent = { Text(suggestion) },
                            trailingContent = {
                                IconButton(onClick = { vm.onQueryChange("$suggestion ") }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.NorthWest, stringResource(R.string.fill_query))
                                }
                            },
                        )
                    }
                }
            }
            UiState.Loading -> CenteredLoading()
            is UiState.Error -> ErrorState(onRetry = { vm.search() })
            is UiState.Success -> {
                if (r.data.isEmpty()) {
                    EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.no_results))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerInset.current)) {
                        itemsIndexed(r.data) { _, item ->
                            when (item) {
                                is SearchResult.SongResult -> SongListItem(
                                    song = item.song,
                                    isCurrent = item.song.id == currentId,
                                    // A search hit starts a radio from that song, like YouTube Music.
                                    onClick = { player.startRadio(item.song) },
                                )
                                is SearchResult.CollectionResult -> CollectionListItem(
                                    collection = item.collection,
                                    onClick = { onOpenCollection(item.collection) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButtons(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val labels = mapOf(
        SearchFilter.Songs to R.string.filter_songs,
        SearchFilter.Videos to R.string.filter_videos,
        SearchFilter.Albums to R.string.filter_albums,
        SearchFilter.Playlists to R.string.filter_playlists,
    )
    val entries = SearchFilter.entries
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        entries.forEachIndexed { index, f ->
            ToggleButton(
                checked = f == selected,
                onCheckedChange = { onSelect(f) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(stringResource(labels.getValue(f)))
            }
        }
    }
}

@Composable
private fun Discover(
    history: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val genres = stringArrayResource(R.array.search_genres)
    val colors = MaterialTheme.colorScheme
    val palette = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerInset.current)) {
        if (history.isNotEmpty()) {
            item(key = "historyHeader") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.recent_searches), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear_all)) }
                }
            }
            items(history.take(8), key = { "h-$it" }) { entry ->
                ListItem(
                    modifier = Modifier.animateItem().clickable { onSearch(entry) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    headlineContent = { Text(entry, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(entry) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.remove))
                        }
                    },
                )
            }
        }
        item(key = "genresHeader") {
            Text(
                stringResource(R.string.browse),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        items(genres.toList().chunked(2), key = { "g-${it.first()}" }) { pair ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { genre ->
                    val index = genres.indexOf(genre)
                    val (container, content) = palette[index % palette.size]
                    GenreCard(genre, container, content, playfulShapes[index % playfulShapes.size], Modifier.weight(1f)) { onSearch(genre) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GenreCard(
    label: String,
    container: Color,
    content: Color,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            Text(
                label,
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
        }
    }
}
