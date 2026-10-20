package me.luzility.muuusic.ui.player

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import me.luzility.muuusic.container
import android.widget.Toast
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Science
import androidx.compose.ui.platform.LocalContext
import me.luzility.muuusic.playback.audio.Karaoke
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.content.res.Configuration
import me.luzility.muuusic.ui.components.TopBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
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
    var showSpeed by rememberSaveable { mutableStateOf(false) }

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

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val immersive = fullscreen && showLyrics
    LaunchedEffect(showLyrics) { if (!showLyrics) fullscreen = false }
    TopBackHandler(enabled = immersive) { fullscreen = false }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val motion = MaterialTheme.motionScheme

    val topBar: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.collapse))
            }
            Spacer(Modifier.weight(1f))
            SpeedButton(onClick = { showSpeed = true })
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
    }
    val playback: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            SeekBar(state)
            Spacer(Modifier.height(16.dp))
            Controls(state)
            Spacer(Modifier.height(if (landscape) 16.dp else 32.dp))
        }
    }
    val lyricsPanel: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
            AnimatedVisibility(
                visibleState = appear,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(motion.slowEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 12 },
            ) {
                LyricsPanel(state, lyrics)
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KaraokeButton()
                FullscreenButton(fullscreen = immersive, onToggle = { fullscreen = !fullscreen })
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface, colors.surface)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp),
    ) {
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
                    Box(modifier, contentAlignment = Alignment.Center) {
                        LikeButton(isLiked, { player.setLiked(current, it) })
                    }
                }
            }
            val compactHeader: @Composable (Boolean) -> Unit = { showLike ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).zIndex(1f), contentAlignment = Alignment.Center) {
                        artwork(
                            song.thumbnailUrl,
                            state.isPlaying,
                            true,
                            Modifier.wrapContentSize(unbounded = true).animateBounds(scope, Modifier.size(64.dp), boundsTransform),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    title(song, true, Modifier.weight(1f).animateBounds(scope, boundsTransform = boundsTransform))
                    AnimatedVisibility(
                        visible = showLike,
                        enter = scaleIn(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        like(song, liked, Modifier.animateBounds(scope, boundsTransform = boundsTransform))
                    }
                }
            }
            val titleRow: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    title(song, false, Modifier.weight(1f).animateBounds(scope, boundsTransform = boundsTransform))
                    like(song, liked, Modifier.animateBounds(scope, boundsTransform = boundsTransform))
                }
            }

            if (landscape) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sideWidth = maxWidth * 0.45f
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (showLyrics) {
                                Column(Modifier.fillMaxSize()) {
                                    AnimatedVisibility(
                                        visible = immersive,
                                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                                        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                                    ) {
                                        compactHeader(false)
                                    }
                                    lyricsPanel(Modifier.weight(1f).fillMaxWidth())
                                }
                            } else {
                                artwork(
                                    song.thumbnailUrl,
                                    state.isPlaying,
                                    false,
                                    Modifier.animateBounds(scope, Modifier.padding(vertical = 16.dp).fillMaxHeight().aspectRatio(1f), boundsTransform),
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = !immersive,
                            enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                            exit = shrinkHorizontally(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                        ) {
                            Column(Modifier.width(sideWidth).fillMaxHeight().padding(start = 24.dp)) {
                                topBar()
                                Spacer(Modifier.weight(1f))
                                if (showLyrics) compactHeader(true) else titleRow()
                                playback()
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(
                        visible = !immersive,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        topBar()
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (showLyrics) {
                            Column(Modifier.fillMaxSize()) {
                                compactHeader(!immersive)
                                lyricsPanel(Modifier.weight(1f).fillMaxWidth())
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
                                titleRow()
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !immersive,
                        enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        playback()
                    }
                }
            }
        }
    }

    if (showQueue) QueueSheet(state, onDismiss = { showQueue = false })
    if (showLyricsSettings) LyricsSettingsSheet(onDismiss = { showLyricsSettings = false })
    if (showSleepTimer) SleepTimerSheet(onDismiss = { showSleepTimer = false })
    if (showSpeed) SpeedSheet(onDismiss = { showSpeed = false })
}

@Composable
private fun KaraokeButton() {
    val context = LocalContext.current
    val settings = context.container.settings
    val prefs by settings.prefs.collectAsStateWithLifecycle()
    if (!prefs.karaoke) return
    val on by Karaoke.enabled.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (on) colors.secondary else colors.secondaryContainer, label = "karaokeContainer")
    val content by animateColorAsState(if (on) colors.onSecondary else colors.onSecondaryContainer, label = "karaokeContent")
    val corner by animateDpAsState(if (on) 12.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "karaokeCorner")
    val label = stringResource(R.string.karaoke)
    val state = stringResource(if (on) R.string.equalizer_on else R.string.equalizer_summary)

    Box {
        Box(
            Modifier
                .padding(top = 4.dp, end = 4.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .combinedClickable(
                    onClickLabel = label,
                    onClick = {
                        Karaoke.set(!on)
                        if (!on) Toast.makeText(context, R.string.karaoke_on, Toast.LENGTH_SHORT).show()
                    },
                    onLongClick = { menu = true },
                )
                .semantics {
                    contentDescription = label
                    stateDescription = state
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MicExternalOn, null, tint = content)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.tertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Science, null, Modifier.size(10.dp), tint = colors.onTertiary)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
            Text(
                stringResource(R.string.settings_karaoke_strength),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Karaoke.strengths.forEach { value ->
                DropdownMenuItem(
                    text = { Text("%${(value * 100).toInt()}") },
                    leadingIcon = {
                        if (value == prefs.karaokeStrength) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        settings.update { it.copy(karaokeStrength = value) }
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FullscreenButton(fullscreen: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onToggle, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        AnimatedContent(fullscreen, label = "fullscreenIcon") { on ->
            Icon(
                if (on) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                stringResource(if (on) R.string.lyrics_exit_fullscreen else R.string.lyrics_fullscreen),
            )
        }
    }
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
    val shape = MorphShape(morph, progress)
    val artworkModifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    var pagerReady by remember { mutableStateOf(false) }
    LaunchedEffect(compact) {
        pagerReady = false
        if (!compact) {
            delay(PagerSettleMs)
            pagerReady = true
        }
    }
    val showPager = !compact && pagerReady
    Box(modifier) {
        Artwork(
            url = url,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (showPager) 0f else 1f }
                .then(artworkModifier),
            shape = shape,
        )
        if (showPager) ArtworkPager(url, shape, artworkModifier, Modifier.fillMaxSize())
    }
}

@Composable
private fun ArtworkPager(url: String?, shape: Shape, artworkModifier: Modifier, modifier: Modifier) {
    val player = LocalPlayer.current
    val state by player.state.collectAsStateWithLifecycle()
    val order = state.playOrder.filter { it in state.queue.indices }
    val position = order.indexOf(state.currentIndex)
    if (position < 0) {
        Artwork(url = url, modifier = modifier.then(artworkModifier), shape = shape)
        return
    }
    val pager = rememberPagerState(initialPage = position) { order.size }
    val haptics = LocalHapticFeedback.current
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bleed = if (landscape) LandscapeBleed else PagerBleed
    val spacing = if (landscape) LandscapeSpacing else PagerSpacing
    val sideAlpha = if (landscape) 0.45f else 0.6f
    val sideShrink = if (landscape) 0.05f else 0.1f

    LaunchedEffect(position, order.size) {
        if (pager.currentPage == position || pager.isScrollInProgress) return@LaunchedEffect
        if (abs(pager.currentPage - position) > 1) pager.scrollToPage(position) else pager.animateScrollToPage(position, animationSpec = spring)
    }
    val currentOrder by rememberUpdatedState(order)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val index = currentOrder.getOrNull(page) ?: return@collect
            if (index != player.state.value.currentIndex) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                player.skipTo(index)
            }
        }
    }

    HorizontalPager(
        state = pager,
        modifier = modifier.layout { measurable, constraints ->
            val extra = bleed.roundToPx()
            val wide = constraints.copy(
                minWidth = constraints.minWidth + extra * 2,
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra * 2 else constraints.maxWidth,
            )
            val placeable = measurable.measure(wide)
            layout(placeable.width - extra * 2, placeable.height) { placeable.place(-extra, 0) }
        }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edge = (bleed.toPx() / size.width).coerceIn(0f, 0.5f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        edge to Color.Black,
                        1f - edge to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = PaddingValues(horizontal = bleed),
        pageSpacing = spacing,
        key = { page -> order.getOrNull(page)?.let { state.queue[it].id + "@" + it } ?: page },
        beyondViewportPageCount = 1,
    ) { page ->
        val song = state.queue.getOrNull(order[page])
        Artwork(
            url = if (page == position) url ?: song?.thumbnailUrl else song?.thumbnailUrl,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val distance = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val shrink = 1f - distance * sideShrink
                    scaleX = shrink
                    scaleY = shrink
                    alpha = 1f - distance * (1f - sideAlpha)
                }
                .then(artworkModifier),
            shape = shape,
        )
    }
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
    val playCorner by animateDpAsState(if (state.isPlaying) 28.dp else 48.dp, motion.fastSpatialSpec(), label = "playCorner")
    val toggleColors = IconButtonDefaults.filledTonalIconToggleButtonColors(
        containerColor = Color.Transparent,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val sideShapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(24.dp), pressedShape = RoundedCornerShape(14.dp))
    val previousInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val previousWeight = pressWeight(previousInteraction, 1f, playInteraction, nextInteraction)
    val playWeight = pressWeight(playInteraction, 1.6f, previousInteraction, nextInteraction)
    val nextWeight = pressWeight(nextInteraction, 1f, previousInteraction, playInteraction)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconToggleButton(
            checked = state.shuffle,
            onCheckedChange = { player.toggleShuffle() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = toggleColors,
        ) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { player.previous() },
                shapes = sideShapes,
                interactionSource = previousInteraction,
                modifier = Modifier.weight(previousWeight).height(72.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(32.dp))
            }
            FilledIconButton(
                onClick = player::togglePlay,
                shape = RoundedCornerShape(playCorner),
                interactionSource = playInteraction,
                modifier = Modifier.weight(playWeight).height(96.dp),
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
                shapes = sideShapes,
                interactionSource = nextInteraction,
                modifier = Modifier.weight(nextWeight).height(72.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconToggleButton(
            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
            onCheckedChange = { player.cycleRepeat() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = toggleColors,
        ) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                stringResource(R.string.repeat),
            )
        }
    }
}

@Composable
private fun pressWeight(
    own: MutableInteractionSource,
    base: Float,
    vararg others: MutableInteractionSource,
): Float {
    val pressed by own.collectIsPressedAsState()
    val neighbourPressed = others.map { it.collectIsPressedAsState().value }.any { it }
    val target = when {
        pressed -> base * 1.15f
        neighbourPressed -> base * 0.92f
        else -> base
    }
    val weight by animateFloatAsState(target, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pressWeight")
    return weight.coerceAtLeast(0.01f)
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

private val PagerBleed = 48.dp
private const val PagerSettleMs = 500L
private val PagerSpacing = 24.dp
private val LandscapeBleed = 88.dp
private val LandscapeSpacing = 16.dp
