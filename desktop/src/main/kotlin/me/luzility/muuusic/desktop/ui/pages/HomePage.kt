package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.data.model.CollectionType
import me.luzility.muuusic.data.model.Mood
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.ui.Artwork
import me.luzility.muuusic.desktop.ui.BrowseShelf
import me.luzility.muuusic.desktop.ui.ErrorBox
import me.luzility.muuusic.desktop.ui.FlexFont
import me.luzility.muuusic.desktop.ui.Loadable
import me.luzility.muuusic.desktop.ui.LoadingBox
import me.luzility.muuusic.desktop.ui.LocalPlayer
import me.luzility.muuusic.desktop.ui.ScrollArrow
import me.luzility.muuusic.desktop.ui.ShelfHeader
import me.luzility.muuusic.desktop.ui.playfulShape
import me.luzility.muuusic.desktop.ui.str

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
private const val GLOBAL_CHART_URL = "https://music.youtube.com/playlist?list=PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"

class HomeModel(private val services: DesktopServices, private val scope: CoroutineScope) {
    var feed by mutableStateOf<Loadable<List<BrowseSection>>>(Loadable.Loading)
        private set
    var moods by mutableStateOf<List<Mood>>(emptyList())
        private set
    var mood by mutableStateOf<Mood?>(null)
        private set
    var personal by mutableStateOf(Personal())
        private set
    var recent by mutableStateOf<List<Song>>(emptyList())
        private set
    var mostPlayed by mutableStateOf<List<Song>>(emptyList())
        private set
    private var job: Job? = null
    private var personalJob: Job? = null
    private val avatars = HashMap<String, String>()

    init {
        scope.launch { services.library.history.collect { recent = it.take(15) } }
        scope.launch { services.library.mostPlayed.collect { mostPlayed = it } }
        load()
    }

    fun selectMood(next: Mood?) {
        mood = next
        load()
    }

    fun load() {
        if (mood == null) loadPersonal()
        job?.cancel()
        job = scope.launch {
            feed = Loadable.Loading
            val selected = mood
            val home = runCatching { services.innerTube.home(selected?.params) }.getOrNull()
            if (home != null && home.sections.isNotEmpty()) {
                if (selected == null) moods = home.moods
                feed = Loadable.Ready(home.sections)
                return@launch
            }
            val picks = runCatching { fallbackQuickPicks() }.getOrDefault(emptyList())
            feed = if (picks.isEmpty()) {
                Loadable.Failed
            } else {
                Loadable.Ready(listOf(BrowseSection(str("quick_picks"), picks.map { BrowseItem.SongItem(it) })))
            }
        }
    }

    private fun loadPersonal() {
        personalJob?.cancel()
        personalJob = scope.launch {
            val history = services.library.history.first()
            if (history.isEmpty()) return@launch
            val favorites = services.library.mostPlayed.first().ifEmpty { history.take(FAVORITE_SEEDS) }
            val heard = history.map { it.id }.toSet()

            val mixes = favorites.distinctBy { primaryArtist(it) }.take(MIX_COUNT).map { seed ->
                BrowseItem.CollectionItem(
                    MusicCollection(
                        url = "https://www.youtube.com/watch?v=${seed.id}&list=RDAMVM${seed.id}",
                        title = str("mix_title", primaryArtist(seed)),
                        subtitle = seed.title,
                        thumbnailUrl = seed.thumbnailUrl,
                        type = CollectionType.Playlist,
                    ),
                )
            }
            personal = personal.copy(mixes = BrowseSection(str("your_mixes"), mixes).takeIf { mixes.isNotEmpty() })

            launch {
                val seed = favorites.take(FAVORITE_SEEDS).random()
                val fresh = runCatching { services.youtube.radio(seed.id) }.getOrDefault(emptyList())
                    .filter { it.id !in heard }
                    .take(FOR_YOU_SIZE)
                if (fresh.isNotEmpty()) {
                    personal = personal.copy(forYou = BrowseSection(str("for_you"), fresh.map { BrowseItem.SongItem(it) }))
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
                    val url = song.artistUrl!!
                    val avatar = avatars[url] ?: withTimeoutOrNull(AVATAR_TIMEOUT_MS) {
                        runCatching { services.youtube.artist(url).avatarUrl }.getOrNull()
                    }?.also { avatars[url] = it }
                    BrowseItem.ArtistItem(primaryArtist(song), url, avatar ?: song.thumbnailUrl, "")
                }
            }.awaitAll()
            personal = personal.copy(artists = BrowseSection(str("favorite_artists"), artists))
        }
    }

    private fun primaryArtist(song: Song) = song.artist.substringBefore(", ").ifBlank { song.artist }

    private suspend fun fallbackQuickPicks(): List<Song> {
        val seed = services.library.lastPlayed()
        val songs = if (seed != null) runCatching { services.youtube.radio(seed.id) }.getOrDefault(emptyList()) else emptyList()
        return songs.ifEmpty {
            runCatching { services.youtube.collection(GLOBAL_CHART_URL, CollectionType.Playlist).songs }.getOrDefault(emptyList())
        }.ifEmpty {
            services.youtube.search("top hits", SearchFilter.Songs).filterIsInstance<SearchResult.SongResult>().map { it.song }
        }.take(20)
    }
}

@Composable
fun HomePage(model: HomeModel) {
    val player = LocalPlayer.current
    val personalOn = model.mood == null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Greeting() }
        if (model.moods.isNotEmpty()) {
            item(key = "moods") {
                val moodState = rememberLazyListState()
                Box(contentAlignment = Alignment.CenterStart) {
                    LazyRow(state = moodState, contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(model.moods, key = { it.params }) { mood ->
                            val selected = mood == model.mood
                            FilterChip(
                                selected = selected,
                                onClick = { model.selectMood(if (selected) null else mood) },
                                label = { Text(mood.title) },
                                shape = CircleShape,
                            )
                        }
                    }
                    if (moodState.canScrollBackward) ScrollArrow(moodState, forward = false, Modifier.padding(start = 4.dp))
                    if (moodState.canScrollForward) ScrollArrow(moodState, forward = true, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
                }
            }
        }
        when (val feed = model.feed) {
            Loadable.Loading -> item(key = "loading") { LoadingBox() }
            Loadable.Failed -> item(key = "error") { ErrorBox(onRetry = model::load) }
            is Loadable.Ready -> {
                val personal = model.personal
                val recent = model.recent
                val mostPlayed = model.mostPlayed
                feed.data.forEachIndexed { index, section ->
                    if (index == 0 && personalOn) {
                        personal.forYou?.let { forYou -> item(key = "forYou") { BrowseShelf(forYou) } }
                    }
                    item(key = "feed-$index-${section.title}") { BrowseShelf(section) }
                    if (index == 0 && recent.isNotEmpty() && personalOn) {
                        item(key = "recent") { RecentCarousel(recent) { i -> player.play(recent, i) } }
                    }
                    if (index == 1 && mostPlayed.isNotEmpty() && personalOn) {
                        item(key = "mostPlayed") { MostPlayedShelf(mostPlayed) { i -> player.play(mostPlayed, i) } }
                    }
                    if (index == 2 && personalOn) {
                        personal.mixes?.let { mixes -> item(key = "mixes") { BrowseShelf(mixes) } }
                    }
                    if (index == 3 && personalOn) {
                        personal.artists?.let { artists -> item(key = "artists") { BrowseShelf(artists) } }
                    }
                }
                if (personalOn && feed.data.size <= 2) {
                    personal.mixes?.let { mixes -> item(key = "mixes") { BrowseShelf(mixes) } }
                }
                if (personalOn && feed.data.size <= 3) {
                    personal.artists?.let { artists -> item(key = "artists") { BrowseShelf(artists) } }
                }
            }
        }
    }
}

@Composable
private fun RecentCarousel(songs: List<Song>, onClick: (Int) -> Unit) {
    val state = rememberCarouselState { songs.size }
    ShelfHeader(str("recently_played"), state, step = { 480f })
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 240.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 32.dp),
        modifier = Modifier.fillMaxWidth().height(260.dp),
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
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
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
private fun MostPlayedShelf(songs: List<Song>, onClick: (Int) -> Unit) {
    val state = rememberLazyListState()
    ShelfHeader(str("most_played"), state)
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            Column(
                Modifier.width(160.dp).clip(MaterialTheme.shapes.large).clickable { onClick(index) }.padding(4.dp),
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
    }
}

@Composable
private fun Greeting() {
    val weight = remember { Animatable(400f) }
    LaunchedEffect(Unit) { weight.animateTo(GREETING_WEIGHT, spring(dampingRatio = 0.6f, stiffness = 120f)) }
    val step = FlexFont.quantize(weight.value)
    Column(Modifier.padding(start = 32.dp, top = 32.dp, bottom = 20.dp)) {
        Text(
            str(greetingKey()),
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = FlexFont.family(step), fontWeight = FontWeight(step)),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(str("home_subtitle"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val GREETING_WEIGHT = 860f

private fun greetingKey(): String = when (LocalTime.now().hour) {
    in 5..11 -> "greeting_morning"
    in 12..17 -> "greeting_afternoon"
    in 18..22 -> "greeting_evening"
    else -> "greeting_night"
}
