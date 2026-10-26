package me.luzility.muuusic.ui.artist

import android.app.Application
import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.model.ArtistDetail
import me.luzility.muuusic.data.model.ArtistPage
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.data.youtube.YouTubeRepository
import me.luzility.muuusic.ui.UiState
import me.luzility.muuusic.ui.browse.BrowseSectionView
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.CenteredLoading
import me.luzility.muuusic.ui.components.ErrorState
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.library.PlayButtons
import java.util.Locale

class ArtistViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val youtube = app.container.youtube
    private val innerTube = app.container.innerTube
    private val url: String? = savedState.get<String>("url")?.takeIf { it.isNotBlank() }
    private val name: String = savedState["name"] ?: ""

    private val _state = MutableStateFlow<UiState<ArtistPage>>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = UiState.of {
                // Songs saved before artist links existed only know the name.
                val resolved = url ?: youtube.findArtistUrl(name) ?: error("Artist not found: $name")
                // YouTube Music's own artist page is the complete one; NewPipe searches are the fallback.
                val channelId = YouTubeRepository.channelIdOf(resolved)
                val page = channelId?.let { runCatching { innerTube.artist(it, resolved) }.getOrNull() }
                page?.takeIf { it.sections.isNotEmpty() } ?: youtube.artist(resolved).toPage()
            }
        }
    }

    private fun ArtistDetail.toPage(): ArtistPage {
        val app = getApplication<Application>()
        return ArtistPage(
            name = name,
            url = url,
            bannerUrl = bannerUrl,
            subscribers = subscribers.takeIf { it >= 0 }?.let(::compactNumber),
            audience = null,
            avatarUrl = avatarUrl,
            description = description,
            sections = listOfNotNull(
                BrowseSection(app.getString(R.string.artist_popular), topSongs.map { BrowseItem.SongItem(it) }),
                BrowseSection(app.getString(R.string.artist_releases), releases.map { BrowseItem.CollectionItem(it) }),
                BrowseSection(app.getString(R.string.filter_videos), videos.map { BrowseItem.SongItem(it, wide = true) }),
            ).filter { it.items.isNotEmpty() },
        )
    }
}

@Composable
fun ArtistScreen(onBack: () -> Unit, vm: ArtistViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAbout) { showAbout = false }
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> CenteredLoading()
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(onRetry = vm::load)
            }
            is UiState.Success -> {
                ArtistContent(s.data, showAbout)
                if (s.data.description != null) {
                    ToggleButton(
                        checked = showAbout,
                        onCheckedChange = { showAbout = it },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 12.dp, top = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Info, null, Modifier.size(ToggleButtonDefaults.IconSize))
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.artist_about))
                    }
                }
            }
        }
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
        }
    }
}

@Composable
private fun ArtistContent(artist: ArtistPage, showAbout: Boolean) {
    val player = LocalPlayer.current
    // The first song shelf (top songs) is what Play and Shuffle play.
    val topSongs = artist.sections.firstOrNull { section ->
        section.items.isNotEmpty() && section.items.all { it is BrowseItem.SongItem && !it.wide }
    }
    val songs = topSongs?.items?.map { (it as BrowseItem.SongItem).song }.orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerInset.current)) {
        item(key = "header") { ArtistHeader(artist) }
        val bio = artist.description
        if (showAbout && bio != null) {
            item(key = "about") { About(bio, Modifier.animateItem()) }
            return@LazyColumn
        }
        if (songs.isNotEmpty()) {
            item(key = "play") {
                Box(Modifier.animateItem()) {
                    PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) })
                }
            }
        }
        artist.sections.forEachIndexed { index, section ->
            item(key = "section-$index") {
                Box(Modifier.animateItem()) { BrowseSectionView(section, songsAsList = section === topSongs) }
            }
        }
    }
}

// Pages from the fallback path also carry a round avatar.
@Composable
private fun ArtistHeader(artist: ArtistPage) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (artist.avatarUrl != null) 340.dp else 300.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(colors.primaryContainer)) {
            if (artist.bannerUrl != null) {
                AsyncImage(
                    model = artist.bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to colors.surface)),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp)) {
            if (artist.avatarUrl != null) {
                Artwork(artist.avatarUrl, Modifier.size(120.dp), MaterialShapes.Cookie12Sided.toShape())
                Spacer(Modifier.height(12.dp))
            }
            Text(artist.name, style = MaterialTheme.typography.displaySmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = artist.audience ?: artist.subscribers?.let { stringResource(R.string.artist_subscribers, it) }
            if (line != null) {
                Text(line, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun About(bio: String, modifier: Modifier = Modifier) {
    Text(
        bio,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

private fun compactNumber(value: Long): String =
    CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(value)
