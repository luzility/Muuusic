package me.luzility.muuusic.ui.player

import me.luzility.muuusic.ui.components.SongMenu
import me.luzility.muuusic.data.settings.Hint
import me.luzility.muuusic.ui.components.HintAnchor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.runtime.movableContentOf
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateBounds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.ui.geometry.Rect
import me.luzility.muuusic.data.model.Song
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import me.luzility.muuusic.R
import me.luzility.muuusic.playback.PlayerUiState
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.LocalNavigator
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.MorphShape
import me.luzility.muuusic.ui.components.SongListItem
import me.luzility.muuusic.ui.components.formatDuration

@Composable
fun NowPlayingScreen(onCollapse: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsStateWithLifecycle()
    val liked by player.currentLiked.collectAsStateWithLifecycle()
    val showLyrics by player.showLyrics.collectAsStateWithLifecycle()
    val lyrics by player.lyrics.collectAsStateWithLifecycle()
    val song = state.current
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showLyricsSettings by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(song == null) { if (song == null) onCollapse() }
    song ?: return

    val colors = MaterialTheme.colorScheme
    // Settles without overshoot: a bouncing artwork while the layout changes reads as a glitch.
    val boundsTransform = remember {
        BoundsTransform { _, _ ->
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = Rect.VisibilityThreshold,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface, colors.surface)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.collapse))
            }
            Spacer(Modifier.weight(1f))
            SleepTimerButton(onClick = { showSleepTimer = true })
            HintAnchor(Hint.Lyrics, R.string.hint_lyrics_title, R.string.hint_lyrics, above = false) {
                LyricsButton(
                    checked = showLyrics,
                    onClick = { player.toggleLyrics() },
                    onLongClick = { showLyricsSettings = true },
                )
            }
            IconButton(onClick = { showQueue = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue))
            }
            state.current?.let { SongMenu(it, nowPlaying = true) }
        }

        // Artwork, title and like button are moved between the two layouts with animateBounds, not recreated.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LookaheadScope {
                val scope = this
                val artwork = remember {
                    movableContentOf { url: String?, playing: Boolean, compact: Boolean, modifier: Modifier ->
                        MorphingArtwork(url, playing, compact, modifier)
                    }
                }
                val title = remember {
                    movableContentOf { current: Song, compact: Boolean, modifier: Modifier ->
                        TitleBlock(current, compact, modifier)
                    }
                }
                val like = remember {
                    movableContentOf { current: Song, isLiked: Boolean, modifier: Modifier ->
                        // animateBounds hands its child exact constraints; the Box relaxes them so
                        // the button keeps its 40dp body inside the 48dp touch target mid-flight.
                        Box(modifier, contentAlignment = Alignment.Center) {
                            LikeButton(isLiked, { player.setLiked(current, it) })
                        }
                    }
                }

                if (showLyrics) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            artwork(song.thumbnailUrl, state.isPlaying, true, Modifier.animateBounds(scope, Modifier.size(64.dp), boundsTransform))
                            Spacer(Modifier.width(16.dp))
                            title(song, true, Modifier.weight(1f).animateBounds(scope, boundsTransform = boundsTransform))
                            like(song, liked, Modifier.animateBounds(scope, boundsTransform = boundsTransform))
                        }
                        val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
                        AnimatedVisibility(
                            visibleState = appear,
                            modifier = Modifier.weight(1f),
                            enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()) +
                                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 12 },
                        ) {
                            LyricsPanel(state, lyrics)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(0.5f))
                        artwork(
                            song.thumbnailUrl,
                            state.isPlaying,
                            false,
                            Modifier.animateBounds(scope, Modifier.fillMaxWidth().aspectRatio(1f), boundsTransform),
                        )
                        Spacer(Modifier.weight(0.5f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            title(song, false, Modifier.weight(1f).animateBounds(scope, boundsTransform = boundsTransform))
                            like(song, liked, Modifier.animateBounds(scope, boundsTransform = boundsTransform))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SeekBar(state)
        Spacer(Modifier.height(16.dp))
        Controls(state)
        Spacer(Modifier.height(32.dp))
    }

    if (showQueue) QueueSheet(state, onDismiss = { showQueue = false })
    if (showLyricsSettings) LyricsSettingsSheet(onDismiss = { showLyricsSettings = false })
    if (showSleepTimer) SleepTimerSheet(onDismiss = { showSleepTimer = false })
}

@Composable
private fun TitleBlock(song: Song, compact: Boolean, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Column(modifier) {
        Text(
            song.title,
            style = if (compact) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.headlineSmallEmphasized,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        Text(
            song.artist,
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { navigator.openArtist(song.artist, song.artistUrl) },
        )
    }
}

@Composable
private fun LikeButton(liked: Boolean, onLikedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilledIconToggleButton(
        checked = liked,
        onCheckedChange = onLikedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier,
    ) {
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            stringResource(if (liked) R.string.unlike else R.string.like),
        )
    }
}

@Composable
private fun MorphingArtwork(url: String?, isPlaying: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Square) }
    val progress by animateFloatAsState(
        targetValue = if (isPlaying || compact) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "artworkMorph",
    )
    val scale by animateFloatAsState(
        targetValue = if (isPlaying || compact) 1f else 0.86f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "artworkScale",
    )
    Artwork(
        url = url,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = MorphShape(morph, progress),
    )
}

@Composable
private fun SeekBar(state: PlayerUiState) {
    val player = LocalPlayer.current
    val position by rememberPlaybackPosition(player, state)
    val duration by rememberUpdatedState(state.durationMs)
    val sliderState = remember { SliderState() }
    // Non-null while the user is scrubbing; playback position is ignored meanwhile.
    var scrub by remember { mutableStateOf<Float?>(null) }
    val fraction = scrub ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    SideEffect { sliderState.value = fraction }

    Column(Modifier.fillMaxWidth()) {
        Slider(
            state = sliderState,
            onValueChange = { scrub = it; sliderState.value = it },
            onValueChangeFinished = {
                scrub?.let { player.seekTo((it * duration).toLong()) }
                scrub = null
            },
            track = { s: SliderState ->
                LinearWavyProgressIndicator(
                    progress = { s.value },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    amplitude = { if (state.isPlaying && scrub == null) 1f else 0f },
                )
            },
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = scrub?.let { (it * duration).toLong() } ?: position
            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Controls(state: PlayerUiState) {
    val player = LocalPlayer.current
    val motion = MaterialTheme.motionScheme
    val playWidth by animateDpAsState(if (state.isPlaying) 128.dp else 96.dp, motion.fastSpatialSpec(), label = "playWidth")
    val playCorner by animateDpAsState(if (state.isPlaying) 28.dp else 48.dp, motion.fastSpatialSpec(), label = "playCorner")

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = state.shuffle,
            onCheckedChange = { player.toggleShuffle() },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle))
        }
        FilledTonalIconButton(
            onClick = { player.previous() },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(32.dp))
        }
        FilledIconButton(
            onClick = player::togglePlay,
            shape = RoundedCornerShape(playCorner),
            modifier = Modifier.size(width = playWidth, height = 96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    state.isBuffering && !state.isPlaying ->
                        LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
                    state.isPlaying -> Icon(Icons.Rounded.Pause, stringResource(R.string.pause), Modifier.size(44.dp))
                    else -> Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), Modifier.size(44.dp))
                }
            }
        }
        FilledTonalIconButton(
            onClick = { player.next() },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(32.dp))
        }
        IconToggleButton(
            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
            onCheckedChange = { player.cycleRepeat() },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                stringResource(R.string.repeat),
            )
        }
    }
}

@Composable
private fun QueueSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            stringResource(R.string.queue),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue) { index, song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SongListItem(
                        song = song,
                        isCurrent = index == state.currentIndex,
                        onClick = { player.skipTo(index) },
                        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.large),
                    )
                    if (index != state.currentIndex) {
                        IconButton(onClick = { player.removeAt(index) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.remove))
                        }
                    }
                }
            }
        }
    }
}
