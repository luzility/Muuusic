package me.luzility.muuusic.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
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
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.desktop.playback.PlayerState
import me.luzility.muuusic.desktop.playback.RepeatMode

private enum class NowPlayingView { Artwork, Lyrics, Queue }

private val MaxArtwork = 480.dp

@Composable
fun NowPlaying(onCollapse: () -> Unit) {
    val player = LocalPlayer.current
    val services = LocalServices.current
    val state by player.state.collectAsState()
    val song = state.current
    LaunchedEffect(song == null) { if (song == null) onCollapse() }
    song ?: return

    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val liked by remember(song.id) { services.library.isLiked(song.id) }.collectAsState(initial = false)
    var view by remember { mutableStateOf(NowPlayingView.Artwork) }
    val toggle = { target: NowPlayingView -> view = if (view == target) NowPlayingView.Artwork else target }
    val motion = MaterialTheme.motionScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface, colors.surface)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = view,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "nowPlayingView",
                ) { current ->
                    when (current) {
                        NowPlayingView.Artwork -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val side = (min(maxWidth, maxHeight) * 0.85f).coerceAtMost(MaxArtwork)
                            MorphingArtwork(song.thumbnailUrl, state.isPlaying, Modifier.size(side))
                        }
                        NowPlayingView.Lyrics -> Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge)) { LyricsPanel() }
                        NowPlayingView.Queue -> Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge)) { QueueList() }
                    }
                }
            }
            Column(Modifier.widthIn(min = 360.dp, max = 520.dp).weight(0.8f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.KeyboardArrowDown, str("collapse"))
                    }
                    Spacer(Modifier.weight(1f))
                    IconToggleButton(
                        checked = view == NowPlayingView.Lyrics,
                        onCheckedChange = { toggle(NowPlayingView.Lyrics) },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(Icons.Rounded.Lyrics, str("lyrics"))
                    }
                    IconToggleButton(
                        checked = view == NowPlayingView.Queue,
                        onCheckedChange = { toggle(NowPlayingView.Queue) },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, str("queue"))
                    }
                    PlayerMenu()
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TitleBlock(song, Modifier.weight(1f))
                    LikeButton(liked) { scope.launch { services.library.setLiked(song, it) } }
                }
                Spacer(Modifier.height(16.dp))
                SeekBar(state)
                Spacer(Modifier.height(16.dp))
                Controls(state)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Volume(compact = false)
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TitleBlock(song: Song, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Column(modifier) {
        Text(song.title, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, modifier = Modifier.basicMarquee())
        Text(
            song.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { navigator.openArtist(song.artist, song.artistUrl) },
        )
    }
}

@Composable
private fun LikeButton(liked: Boolean, onLikedChange: (Boolean) -> Unit) {
    FilledIconToggleButton(checked = liked, onCheckedChange = onLikedChange, shapes = IconButtonDefaults.toggleableShapes()) {
        Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, str(if (liked) "unlike" else "like"))
    }
}

@Composable
private fun MorphingArtwork(url: String?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Square) }
    val progress by animateFloatAsState(if (isPlaying) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "artworkMorph")
    val scale by animateFloatAsState(if (isPlaying) 1f else 0.86f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "artworkScale")
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
private fun SeekBar(state: PlayerState) {
    val player = LocalPlayer.current
    val position by rememberPlaybackPosition(player, state)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val fraction = scrub ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { player.seekTo((it * duration).toLong()) }
                scrub = null
            },
            track = { s ->
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
private fun Controls(state: PlayerState) {
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
            Icon(Icons.Rounded.Shuffle, str("shuffle"))
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = player::previous,
                shapes = sideShapes,
                interactionSource = previousInteraction,
                modifier = Modifier.weight(previousWeight).height(72.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, str("previous"), Modifier.size(32.dp))
            }
            FilledIconButton(
                onClick = player::togglePlay,
                shape = RoundedCornerShape(playCorner),
                interactionSource = playInteraction,
                modifier = Modifier.weight(playWeight).height(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        state.isBuffering && !state.isPlaying -> LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
                        state.isPlaying -> Icon(Icons.Rounded.Pause, str("pause"), Modifier.size(44.dp))
                        else -> Icon(Icons.Rounded.PlayArrow, str("play"), Modifier.size(44.dp))
                    }
                }
            }
            FilledTonalIconButton(
                onClick = player::next,
                shapes = sideShapes,
                interactionSource = nextInteraction,
                modifier = Modifier.weight(nextWeight).height(72.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, str("next"), Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconToggleButton(
            checked = state.repeatMode != RepeatMode.Off,
            onCheckedChange = { player.cycleRepeat() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = toggleColors,
        ) {
            Icon(if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, str("repeat"))
        }
    }
}

@Composable
private fun pressWeight(own: MutableInteractionSource, base: Float, vararg others: MutableInteractionSource): Float {
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
