package me.luzility.muuusic.desktop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.desktop.playback.PlayerState
import me.luzility.muuusic.desktop.playback.RepeatMode

val PlayerBarHeight = 96.dp

@Composable
fun rememberPlaybackPosition(player: DesktopPlayer, state: PlayerState): State<Long> {
    val position = remember { mutableLongStateOf(player.position()) }
    LaunchedEffect(state.isPlaying, state.current?.id) {
        while (true) {
            position.longValue = player.position()
            delay(if (state.isPlaying) 200 else 500)
        }
    }
    return position
}

@Composable
fun PlayerBar(state: PlayerState, modifier: Modifier = Modifier) {
    val song = state.current ?: return
    val player = LocalPlayer.current
    val services = LocalServices.current
    val navigator = LocalNavigator.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val liked by remember(song.id) { services.library.isLiked(song.id) }.collectAsState(initial = false)

    Surface(color = colors.surfaceContainer, modifier = modifier.fillMaxWidth().height(PlayerBarHeight)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(0.3f), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.thumbnailUrl, Modifier.size(64.dp), MaterialTheme.shapes.large)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable { navigator.openArtist(song.artist, song.artistUrl) },
                    )
                }
                IconButton(onClick = { scope.launch { services.library.setLiked(song, !liked) } }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        str(if (liked) "unlike" else "like"),
                        tint = if (liked) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }

            Column(
                Modifier.weight(0.4f).widthIn(max = 640.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Controls(state)
                Seek(state)
            }

            Row(Modifier.weight(0.3f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(
                    checked = navigator.queueOpen,
                    onCheckedChange = { navigator.queueOpen = it },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, str("queue"))
                }
                Volume()
            }
        }
    }
}

@Composable
private fun Controls(state: PlayerState) {
    val player = LocalPlayer.current
    val corner by animateDpAsState(if (state.isPlaying) 14.dp else 22.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "barPlay")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconToggleButton(
            checked = state.shuffle,
            onCheckedChange = { player.toggleShuffle() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, str("shuffle"))
        }
        IconButton(onClick = player::previous, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.SkipPrevious, str("previous"))
        }
        FilledIconButton(
            onClick = player::togglePlay,
            shape = RoundedCornerShape(corner),
            modifier = Modifier.size(width = 52.dp, height = 40.dp),
        ) {
            when {
                state.isBuffering && !state.isPlaying -> LoadingIndicator(Modifier.size(26.dp), color = MaterialTheme.colorScheme.onPrimary)
                state.isPlaying -> Icon(Icons.Rounded.Pause, str("pause"))
                else -> Icon(Icons.Rounded.PlayArrow, str("play"))
            }
        }
        IconButton(onClick = player::next, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.SkipNext, str("next"))
        }
        IconToggleButton(
            checked = state.repeatMode != RepeatMode.Off,
            onCheckedChange = { player.cycleRepeat() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, str("repeat"))
        }
    }
}

@Composable
private fun Seek(state: PlayerState) {
    val player = LocalPlayer.current
    val position by rememberPlaybackPosition(player, state)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val fraction = scrub ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = scrub?.let { (it * duration).toLong() } ?: position
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 44.dp))
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            SlimSlider(
                value = fraction,
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let { player.seekTo((it * duration).toLong()) }
                    scrub = null
                },
                thumbHeight = 20.dp,
            ) { progress ->
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    amplitude = { if (state.isPlaying && scrub == null) 1f else 0f },
                )
            }
        }
        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 44.dp))
    }
}

@Composable
private fun Volume() {
    val player = LocalPlayer.current
    val volume by player.volume.collectAsState()
    var beforeMute by remember { mutableFloatStateOf(1f) }
    IconButton(
        onClick = {
            if (volume > 0f) {
                beforeMute = volume
                player.setVolume(0f)
            } else {
                player.setVolume(beforeMute.takeIf { it > 0f } ?: 1f)
            }
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            when {
                volume == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            str("volume"),
        )
    }
    Box(Modifier.width(120.dp)) {
        SlimSlider(value = volume, onValueChange = player::setVolume, onValueChangeFinished = {}, thumbHeight = 18.dp) { progress ->
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    thumbHeight: Dp,
    track: @Composable (Float) -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactions,
        thumb = { s -> SliderDefaults.Thumb(interactions, sliderState = s, thumbSize = DpSize(4.dp, thumbHeight)) },
        track = { s -> track(s.value) },
    )
}
