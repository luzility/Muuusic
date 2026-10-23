package me.luzility.muuusic.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import me.luzility.muuusic.ui.theme.FlexFont
import me.luzility.muuusic.ui.browse.BrowseSectionView
import me.luzility.muuusic.data.model.Mood
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.data.model.BrowseItem
import kotlinx.coroutines.Job
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import me.luzility.muuusic.R
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.container
import me.luzility.muuusic.data.model.CollectionType
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.ui.UiState
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.ErrorState
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import me.luzility.muuusic.ui.components.playfulShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.luzility.muuusic.data.model.MusicCollection
import java.time.LocalTime

// Top 100 Global chart, used until there is listening history.
data class Personal(
    val forYou: BrowseSection? = null,
    val mixes: BrowseSection? = null,
    val artists: BrowseSection? = null,
)

private const val FAVORITE_SEEDS = 5
private const val MIX_COUNT = 6
private const val FOR_YOU_SIZE = 20
private const val ARTIST_COUNT = 8
private const val MIN_ARTISTS = 3
private const val HISTORY_WEIGHT = 1
private const val AVATAR_TIMEOUT_MS = 8_000L
private val avatars = java.util.concurrent.ConcurrentHashMap<String, String>()

private const val GLOBAL_CHART_URL = "https://music.youtube.com/playlist?list=PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val youtube = app.container.youtube
    private val innerTube = app.container.innerTube
    private val library = app.container.library

    val recent: StateFlow<List<Song>> = library.history.map { it.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mostPlayed: StateFlow<List<Song>> = library.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _feed = MutableStateFlow<UiState<List<BrowseSection>>>(UiState.Loading)
    val feed = _feed.asStateFlow()

    // Kept on screen while a mood is selected.
    private val _moods = MutableStateFlow<List<Mood>>(emptyList())
    val moods = _moods.asStateFlow()

    private val _mood = MutableStateFlow<Mood?>(null)
    val mood = _mood.asStateFlow()

    private val _personal = MutableStateFlow(Personal())
    val personal = _personal.asStateFlow()

    private var loadJob: Job? = null
    private var personalJob: Job? = null

    init {
        load()
    }

    fun selectMood(mood: Mood?) {
        _mood.value = mood
        load()
    }

    // Falls back to quick picks from the last song's radio, or popular songs on first launch.
    fun load() {
        if (_mood.value == null) loadPersonal()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _feed.value = UiState.Loading
            val mood = _mood.value
            val home = runCatching { innerTube.home(mood?.params) }.getOrNull()
            if (home != null && home.sections.isNotEmpty()) {
                if (mood == null) _moods.value = home.moods
                _feed.value = UiState.Success(home.sections)
                return@launch
            }
            _feed.value = UiState.of {
                listOf(BrowseSection(getApplication<Application>().getString(R.string.quick_picks), fallbackQuickPicks().map { BrowseItem.SongItem(it) }))
            }
        }
    }

    private fun loadPersonal() {
        personalJob?.cancel()
        personalJob = viewModelScope.launch {
            val history = library.history.first()
            if (history.isEmpty()) return@launch
            val favorites = library.mostPlayed.first().ifEmpty { history.take(FAVORITE_SEEDS) }
            val app = getApplication<Application>()
            val heard = history.map { it.id }.toSet()

            val mixes = favorites.distinctBy { primaryArtist(it) }.take(MIX_COUNT).map { seed ->
                BrowseItem.CollectionItem(
                    MusicCollection(
                        url = "https://www.youtube.com/watch?v=${seed.id}&list=RDAMVM${seed.id}",
                        title = app.getString(R.string.mix_title, primaryArtist(seed)),
                        subtitle = seed.title,
                        thumbnailUrl = seed.thumbnailUrl,
                        type = CollectionType.Playlist,
                    ),
                )
            }
            _personal.value = _personal.value.copy(
                mixes = BrowseSection(app.getString(R.string.your_mixes), mixes).takeIf { mixes.isNotEmpty() },
            )

            launch {
                val seed = favorites.take(FAVORITE_SEEDS).random()
                val fresh = runCatching { youtube.radio(seed.id) }.getOrDefault(emptyList())
                    .filter { it.id !in heard }
                    .take(FOR_YOU_SIZE)
                if (fresh.isNotEmpty()) {
                    _personal.value = _personal.value.copy(
                        forYou = BrowseSection(app.getString(R.string.for_you), fresh.map { BrowseItem.SongItem(it) }),
                    )
                }
            }

            val scores = HashMap<String, Int>()
            val sample = HashMap<String, Song>()
            favorites.forEachIndexed { rank, song -> scores.merge(primaryArtist(song), favorites.size - rank + HISTORY_WEIGHT, Int::plus) }
            history.forEach { scores.merge(primaryArtist(it), HISTORY_WEIGHT, Int::plus) }
            (favorites + history).forEach { song -> if (song.artistUrl != null) sample.putIfAbsent(primaryArtist(song), song) }
            val top = scores.entries.filter { it.key in sample }.sortedByDescending { it.value }.take(ARTIST_COUNT).map { sample.getValue(it.key) }
            if (top.size < MIN_ARTISTS) return@launch
            val artists = top.map { song ->
                async {
                    val name = primaryArtist(song)
                    val url = song.artistUrl!!
                    val avatar = avatars[url] ?: withTimeoutOrNull(AVATAR_TIMEOUT_MS) {
                        runCatching { youtube.artist(url).avatarUrl }.getOrNull()
                    }?.also { avatars[url] = it }
                    BrowseItem.ArtistItem(name, url, avatar ?: song.thumbnailUrl, "")
                }
            }.awaitAll()
            _personal.value = _personal.value.copy(artists = BrowseSection(app.getString(R.string.favorite_artists), artists))
        }
    }

    private fun primaryArtist(song: Song) = song.artist.substringBefore(", ").ifBlank { song.artist }

    private suspend fun fallbackQuickPicks(): List<Song> {
        val seed = library.lastPlayed()
        val songs = if (seed != null) runCatching { youtube.radio(seed.id) }.getOrDefault(emptyList()) else emptyList()
        return songs.ifEmpty {
            runCatching { youtube.collection(GLOBAL_CHART_URL, CollectionType.Playlist).songs }.getOrDefault(emptyList())
        }.ifEmpty {
            youtube.search("top hits", SearchFilter.Songs).filterIsInstance<SearchResult.SongResult>().map { it.song }
        }.take(20)
    }
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, vm: HomeViewModel = viewModel()) {
    val player = LocalPlayer.current
    val recent by vm.recent.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val moods by vm.moods.collectAsStateWithLifecycle()
    val selectedMood by vm.mood.collectAsStateWithLifecycle()
    val personal by vm.personal.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_list"),
        contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerInset.current),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { Greeting(onOpenSettings) }

        if (moods.isNotEmpty()) {
            item(key = "moods") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(moods, key = { it.params }) { mood ->
                        val selected = mood == selectedMood
                        FilterChip(
                            selected = selected,
                            onClick = { vm.selectMood(if (selected) null else mood) },
                            label = { Text(mood.title) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        when (val state = feed) {
            UiState.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(320.dp), Alignment.Center) { LoadingIndicator() }
            }
            is UiState.Error -> item(key = "error") { ErrorState(onRetry = vm::load) }
            is UiState.Success -> {
            state.data.forEachIndexed { index, section ->
                if (index == 0 && selectedMood == null) {
                    personal.forYou?.let { forYou -> item(key = "forYou") { BrowseSectionView(forYou) } }
                }
                item(key = "section-$index-${section.title}") { BrowseSectionView(section) }
                // The user's own listening sits between YouTube Music's first shelves.
                if (index == 0 && recent.isNotEmpty() && selectedMood == null) {
                    item(key = "recent") {
                        SectionTitle(stringResource(R.string.recently_played))
                        RecentCarousel(recent) { i -> player.play(recent, i) }
                    }
                }
                if (index == 2 && selectedMood == null) {
                    personal.mixes?.let { mixes -> item(key = "mixes") { BrowseSectionView(mixes) } }
                }
                if (index == 3 && selectedMood == null) {
                    personal.artists?.let { artists -> item(key = "artists") { BrowseSectionView(artists) } }
                }
                if (index == 1 && mostPlayed.isNotEmpty() && selectedMood == null) {
                    item(key = "mostPlayed") {
                        SectionTitle(stringResource(R.string.most_played))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(mostPlayed, key = { _, s -> s.id }) { i, song ->
                                ShapedSongCard(song, i) { player.play(mostPlayed, i) }
                            }
                        }
                    }
                }
            }
            if (selectedMood == null && state.data.size <= 2) {
                personal.mixes?.let { mixes -> item(key = "mixes") { BrowseSectionView(mixes) } }
            }
            if (selectedMood == null && state.data.size <= 3) {
                personal.artists?.let { artists -> item(key = "artists") { BrowseSectionView(artists) } }
            }
            }
        }
    }
}

private const val GREETING_WEIGHT = 860f

@Composable
private fun Greeting(onOpenSettings: () -> Unit) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
    Row(
        Modifier.padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            // Grows from regular to heavy when the page appears.
            val weight = remember { Animatable(400f) }
            LaunchedEffect(Unit) { weight.animateTo(GREETING_WEIGHT, spring(dampingRatio = 0.6f, stiffness = 120f)) }
            Text(
                stringResource(greeting),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FlexFont.family(FlexFont.quantize(weight.value)),
                    fontWeight = FontWeight(FlexFont.quantize(weight.value)),
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun RecentCarousel(songs: List<Song>, onClick: (Int) -> Unit) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { songs.size },
        preferredItemWidth = 200.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(220.dp),
    ) { index ->
        val song = songs[index]
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable { onClick(index) },
        ) {
            Artwork(song.thumbnailUrl, Modifier.fillMaxSize(), MaterialTheme.shapes.extraSmall)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShapedSongCard(song: Song, index: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(132.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(song.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), playfulShape(index))
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

