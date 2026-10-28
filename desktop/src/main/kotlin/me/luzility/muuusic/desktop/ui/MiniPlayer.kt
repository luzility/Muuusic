package me.luzility.muuusic.desktop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.desktop.playback.PlayerState
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.RoundRectangle2D

const val WindowCorner = 24
private val MenuPadding = 6.dp
private val MenuOuterCorner = (WindowCorner - 6).dp
private val MenuInnerCorner = 4.dp
private val TopGroup = RoundedCornerShape(topStart = MenuOuterCorner, topEnd = MenuOuterCorner, bottomStart = MenuInnerCorner, bottomEnd = MenuInnerCorner)
private val BottomGroup = RoundedCornerShape(topStart = MenuInnerCorner, topEnd = MenuInnerCorner, bottomStart = MenuOuterCorner, bottomEnd = MenuOuterCorner)

@Composable
fun PlayerWindowTheme(services: DesktopServices, player: DesktopPlayer, content: @Composable () -> Unit) {
    val state by player.state.collectAsState()
    val prefs by services.settings.prefs.collectAsState()
    MuuusicDesktopTheme(artworkUrl = state.current?.thumbnailUrl, prefs = prefs) {
        CompositionLocalProvider(LocalServices provides services, LocalPlayer provides player, content = content)
    }
}

@Composable
fun MiniPlayer(onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RectangleShape,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            val song = state.current
            if (song == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(str("nothing_playing"), style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Artwork(song.thumbnailUrl, Modifier.size(56.dp), MaterialTheme.shapes.large)
                    SongTitle(song.title, song.artist, Modifier.weight(1f))
                    MiniControls(state)
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(end = 4.dp)) {
                    if (song != null) MiniSeek(state)
                }
                IconButton(onClick = onExpand, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.OpenInFull, str("show_window"), Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, str("close"), Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun TrayMenu(
    windowVisible: Boolean,
    miniPlayer: Boolean,
    onToggleWindow: () -> Unit,
    onToggleMiniPlayer: () -> Unit,
    onQuit: () -> Unit,
) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RectangleShape,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(MenuPadding), verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing)) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes(TopGroup, TopGroup), containerColor = colors.secondaryContainer) {
                val song = state.current
                if (song == null) {
                    Text(
                        str("nothing_playing"),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                } else {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(song.thumbnailUrl, Modifier.size(48.dp), MaterialTheme.shapes.large)
                            SongTitle(song.title, song.artist, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        MiniControls(state)
                    }
                }
            }
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes(BottomGroup, BottomGroup)) {
                DropdownMenuItem(
                    selected = false,
                    onClick = onToggleWindow,
                    text = { Text(str(if (windowVisible) "hide_window" else "show_window")) },
                    shapes = MenuDefaults.itemShape(0, 3),
                    leadingIcon = { Icon(if (windowVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.OpenInFull, null) },
                )
                DropdownMenuItem(
                    checked = miniPlayer,
                    onCheckedChange = { onToggleMiniPlayer() },
                    text = { Text(str("mini_player")) },
                    shapes = MenuDefaults.itemShape(1, 3),
                    leadingIcon = { Icon(Icons.Rounded.PictureInPictureAlt, null) },
                )
                DropdownMenuItem(
                    selected = false,
                    onClick = onQuit,
                    text = { Text(str("quit")) },
                    shapes = MenuDefaults.itemShape(2, 3),
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null) },
                )
            }
        }
    }
}

@Composable
private fun SongTitle(title: String, artist: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.basicMarquee())
        Text(
            artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniControls(state: PlayerState) {
    val player = LocalPlayer.current
    val corner by animateDpAsState(if (state.isPlaying) 12.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "miniPlayCorner")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = player::previous, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.SkipPrevious, str("previous"))
        }
        FilledIconButton(
            onClick = player::togglePlay,
            shape = RoundedCornerShape(corner),
            modifier = Modifier.size(width = 52.dp, height = 40.dp),
        ) {
            when {
                state.isBuffering && !state.isPlaying -> LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                state.isPlaying -> Icon(Icons.Rounded.Pause, str("pause"))
                else -> Icon(Icons.Rounded.PlayArrow, str("play"))
            }
        }
        IconButton(onClick = player::next, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.SkipNext, str("next"))
        }
    }
}

@Composable
private fun MiniSeek(state: PlayerState) {
    val player = LocalPlayer.current
    val position by rememberPlaybackPosition(player, state)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val fraction = scrub ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    SlimSlider(
        value = fraction,
        onValueChange = { scrub = it },
        onValueChangeFinished = {
            scrub?.let { player.seekTo((it * duration).toLong()) }
            scrub = null
        },
        thumbHeight = 18.dp,
    ) { progress ->
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            amplitude = { if (state.isPlaying && scrub == null) 1f else 0f },
            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
        )
    }
}

fun nearAnchor(x: Int, y: Int, width: Int, height: Int): Point {
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    val screen = screens.firstOrNull { it.contains(x, y) } ?: screens.firstOrNull() ?: Rectangle(0, 0, 1920, 1080)
    val margin = 8
    val left = (x - width / 2).coerceIn(screen.x + margin, screen.x + screen.width - width - margin)
    val top = if (y + height + margin > screen.y + screen.height) y - height - margin else y + margin
    return Point(left, top.coerceIn(screen.y + margin, screen.y + screen.height - height - margin))
}

fun placeNear(window: Window, x: Int, y: Int) {
    window.location = nearAnchor(x, y, window.width, window.height)
}

fun roundCorners(window: Window, radius: Int): () -> Unit {
    fun apply() {
        window.shape = RoundRectangle2D.Double(0.0, 0.0, window.width.toDouble(), window.height.toDouble(), radius * 2.0, radius * 2.0)
    }
    val listener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) = apply()
    }
    window.addComponentListener(listener)
    apply()
    return { window.removeComponentListener(listener) }
}
