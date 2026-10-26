package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.font.FontWeight
import me.luzility.muuusic.desktop.ui.FlexFont
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.ui.BrowseShelf
import me.luzility.muuusic.desktop.ui.ErrorBox
import me.luzility.muuusic.desktop.ui.Loadable
import me.luzility.muuusic.desktop.ui.LoadingBox
import me.luzility.muuusic.desktop.ui.str
import java.time.LocalTime

class HomeModel(private val services: DesktopServices, private val scope: CoroutineScope) {
    var feed by mutableStateOf<Loadable<List<BrowseSection>>>(Loadable.Loading)
        private set
    var moods by mutableStateOf<List<Mood>>(emptyList())
        private set
    var mood by mutableStateOf<Mood?>(null)
        private set
    var personal by mutableStateOf<List<BrowseSection>>(emptyList())
        private set
    private var job: Job? = null

    init {
        load()
        loadPersonal()
    }

    fun selectMood(next: Mood?) {
        mood = next
        load()
    }

    fun load() {
        job?.cancel()
        job = scope.launch {
            feed = Loadable.Loading
            val home = runCatching { services.innerTube.home(mood?.params) }.getOrNull()
            if (home == null || home.sections.isEmpty()) {
                feed = Loadable.Failed
                return@launch
            }
            if (mood == null) moods = home.moods
            feed = Loadable.Ready(home.sections)
        }
    }

    private fun loadPersonal() {
        scope.launch {
            val history = services.library.history.first()
            if (history.isEmpty()) return@launch
            val favorites = services.library.mostPlayed.first().ifEmpty { history.take(5) }
            val heard = history.map { it.id }.toSet()
            val sections = mutableListOf<BrowseSection>()
            sections += BrowseSection(str("recently_played"), history.take(15).map { BrowseItem.SongItem(it) })
            val mixes = favorites.distinctBy { primaryArtist(it) }.take(6).map { seed ->
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
            if (mixes.isNotEmpty()) sections += BrowseSection(str("your_mixes"), mixes)
            personal = sections.toList()

            launch {
                val seed = favorites.take(5).random()
                val fresh = runCatching { services.youtube.radio(seed.id) }.getOrDefault(emptyList()).filter { it.id !in heard }.take(20)
                if (fresh.isNotEmpty()) personal = listOf(BrowseSection(str("for_you"), fresh.map { BrowseItem.SongItem(it) })) + personal
            }

            val scores = HashMap<String, Int>()
            val sample = HashMap<String, Song>()
            favorites.forEachIndexed { rank, song -> scores.merge(primaryArtist(song), favorites.size - rank + 1, Int::plus) }
            history.forEach { scores.merge(primaryArtist(it), 1, Int::plus) }
            (favorites + history).forEach { song -> if (song.artistUrl != null) sample.putIfAbsent(primaryArtist(song), song) }
            val top = scores.entries.filter { it.key in sample }.sortedByDescending { it.value }.take(8).map { sample.getValue(it.key) }
            if (top.size < 3) return@launch
            val artists = top.map { song ->
                async {
                    val url = song.artistUrl!!
                    val avatar = withTimeoutOrNull(8_000) { runCatching { services.youtube.artist(url).avatarUrl }.getOrNull() }
                    BrowseItem.ArtistItem(primaryArtist(song), url, avatar ?: song.thumbnailUrl, "")
                }
            }.awaitAll()
            personal = personal + BrowseSection(str("favorite_artists"), artists)
        }
    }

    private fun primaryArtist(song: Song) = song.artist.substringBefore(", ").ifBlank { song.artist }
}

@Composable
fun HomePage(model: HomeModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Greeting() }
        if (model.moods.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
        if (model.mood == null) {
            model.personal.forEach { section -> item(key = "personal-${section.title}") { BrowseShelf(section) } }
        }
        when (val feed = model.feed) {
            Loadable.Loading -> item { LoadingBox() }
            Loadable.Failed -> item { ErrorBox(onRetry = model::load) }
            is Loadable.Ready -> feed.data.forEachIndexed { index, section ->
                item(key = "feed-$index-${section.title}") { BrowseShelf(section) }
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
