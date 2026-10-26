package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.BrowseSection
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.desktop.playback.QueueResult
import androidx.compose.material3.SnackbarHostState

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier {
    val current by rememberUpdatedState(action)
    return pointerInput(Unit) {
        awaitEachGesture {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                current()
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    index: Int?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val player = LocalPlayer.current
    val services = LocalServices.current
    val state by player.state.collectAsState()
    val current = state.current?.id == song.id
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val menuOpen = remember { mutableStateOf(false) }
    val liked by remember(song.id) { services.library.isLiked(song.id) }.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (current) colors.secondaryContainer else if (hovered) colors.surfaceContainerHigh else colors.surface.copy(alpha = 0f))
            .hoverable(interactions)
            .onSecondaryClick { menuOpen.value = true }
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                when {
                    current -> Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    hovered -> Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    else -> Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Artwork(song.thumbnailUrl, Modifier.size(44.dp), MaterialTheme.shapes.small)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = { scope.launch { services.library.setLiked(song, !liked) } },
            modifier = Modifier.size(36.dp),
        ) {
            if (liked || hovered) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    str(if (liked) "unlike" else "like"),
                    tint = if (liked) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            if (song.durationSec > 0) formatDuration(song.durationSec * 1000) else "",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(56.dp).padding(start = 8.dp),
        )
        trailing?.invoke()
        SongMenu(song, menuOpen)
    }
}

@Composable
fun SongMenu(song: Song, open: androidx.compose.runtime.MutableState<Boolean>) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    fun report(result: QueueResult, next: Boolean) {
        val message = when (result) {
            QueueResult.AlreadyPlaying -> str("queue_already_playing")
            QueueResult.Moved -> str(if (next) "queue_moved_next" else "queue_moved_end")
            QueueResult.Added -> str(if (next) "queue_added_next" else "queue_added_end")
        }
        scope.launch { snackbar.showSnackbar(message) }
    }
    Box {
        IconButton(onClick = { open.value = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.MoreHoriz, str("more"), modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open.value, onDismissRequest = { open.value = false }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(str("play_next")) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = { report(player.enqueue(song, next = true), next = true); open.value = false },
            )
            DropdownMenuItem(
                text = { Text(str("add_to_queue")) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { report(player.enqueue(song, next = false), next = false); open.value = false },
            )
            DropdownMenuItem(
                text = { Text(str("start_radio")) },
                leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                onClick = { player.startRadio(song); open.value = false },
            )
            DropdownMenuItem(
                text = { Text(str("go_to_artist")) },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { navigator.openArtist(song.artist, song.artistUrl); open.value = false },
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 12.dp),
    )
}

@Composable
fun ShelfCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    shape: Shape = MaterialTheme.shapes.large,
    onSecondaryClick: (() -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Column(
        Modifier
            .width(176.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface.copy(alpha = 0f))
            .hoverable(interactions)
            .then(if (onSecondaryClick != null) Modifier.onSecondaryClick(onSecondaryClick) else Modifier)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Artwork(imageUrl, Modifier.fillMaxWidth().aspectRatio(1f), shape)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BrowseShelf(section: BrowseSection) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val songs = section.items.filterIsInstance<BrowseItem.SongItem>().map { it.song }
    SectionTitle(section.title)
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(section.items) { _, item ->
            when (item) {
                is BrowseItem.SongItem -> {
                    val menuOpen = remember { mutableStateOf(false) }
                    Box {
                        ShelfCard(
                            title = item.song.title,
                            subtitle = item.song.artist,
                            imageUrl = item.song.thumbnailUrl,
                            onClick = { player.play(songs, songs.indexOf(item.song)) },
                            onSecondaryClick = { menuOpen.value = true },
                        )
                        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                            if (menuOpen.value) SongMenu(item.song, menuOpen)
                        }
                    }
                }
                is BrowseItem.CollectionItem -> ShelfCard(
                    title = item.collection.title,
                    subtitle = item.collection.subtitle,
                    imageUrl = item.collection.thumbnailUrl,
                    onClick = { navigator.openCollection(item.collection) },
                )
                is BrowseItem.ArtistItem -> ShelfCard(
                    title = item.name,
                    subtitle = item.subtitle,
                    imageUrl = item.thumbnailUrl,
                    onClick = { navigator.openArtist(item.name, item.url) },
                    shape = CircleShape,
                )
            }
        }
    }
}

@Composable
fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPlay, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(str("play_all"))
        }
        FilledTonalButton(onClick = onShuffle, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(8.dp))
            Text(str("shuffle"))
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
fun ErrorBox(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(str("error_generic"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(str("retry")) }
    }
}

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data object Failed : Loadable<Nothing>
    data class Ready<T>(val data: T) : Loadable<T>
}
