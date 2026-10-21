package me.luzility.muuusic.ui.player

import me.luzility.muuusic.ui.components.Haptic
import me.luzility.muuusic.ui.components.LocalHaptics
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.R
import me.luzility.muuusic.playback.PlayerUiState
import me.luzility.muuusic.playback.PlayerViewModel
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.LocalPlayer
import kotlinx.coroutines.delay

@Composable
fun rememberPlaybackPosition(player: PlayerViewModel, state: PlayerUiState, intervalMs: Long = 200): State<Long> {
    val position = remember { mutableLongStateOf(player.position()) }
    LaunchedEffect(state.isPlaying, state.current?.id) {
        while (true) {
            position.longValue = player.position()
            delay(if (state.isPlaying) intervalMs else 500)
        }
    }
    return position
}

val MiniPlayerHeight = 64.dp

@Composable
fun MiniPlayerContent(state: PlayerUiState, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val song = state.current ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(song.thumbnailUrl, Modifier.size(48.dp), MaterialTheme.shapes.large)
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiniControls(state)
    }
}

// Play button is wider and squares off while playing, like the full-screen player.
@Composable
private fun MiniControls(state: PlayerUiState) {
    val player = LocalPlayer.current
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(if (state.isPlaying) 12.dp else 20.dp, motion.fastSpatialSpec(), label = "miniPlayCorner")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { player.previous() }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous))
        }
        val haptics = LocalHaptics.current
        FilledIconButton(
            onClick = {
                haptics.perform(Haptic.Heavy)
                player.togglePlay()
            },
            shape = RoundedCornerShape(corner),
            modifier = Modifier.size(width = 52.dp, height = 40.dp),
        ) {
            when {
                state.isBuffering && !state.isPlaying ->
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                state.isPlaying -> Icon(Icons.Rounded.Pause, stringResource(R.string.pause))
                else -> Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play))
            }
        }
        IconButton(onClick = { player.next() }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.next))
        }
    }
}
