package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

object Typing {
    @Volatile
    var active = false
}

fun Modifier.typingField(): Modifier = composed {
    val focused = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (focused.value) Typing.active = false }
    }
    onFocusChanged {
        focused.value = it.isFocused
        if (it.isFocused) Typing.active = true else if (Typing.active) Typing.active = false
    }
}

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                val download = downloadStatuses()[song.id]
                if (download != null) {
                    DownloadBadge(download)
                    Spacer(Modifier.width(6.dp))
                }
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
fun SongMenu(song: Song, open: androidx.compose.runtime.MutableState<Boolean>, showButton: Boolean = true) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    if (adding) AddToPlaylistDialog(listOf(song), onDismiss = { adding = false })
    fun report(result: QueueResult, next: Boolean) {
        val message = when (result) {
            QueueResult.AlreadyPlaying -> str("queue_already_playing")
            QueueResult.Moved -> str(if (next) "queue_moved_next" else "queue_moved_end")
            QueueResult.Added -> str(if (next) "queue_added_next" else "queue_added_end")
        }
        scope.launch { snackbar.showSnackbar(message) }
    }
    Box {
        if (showButton) {
            IconButton(onClick = { open.value = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.MoreHoriz, str("more"), modifier = Modifier.size(20.dp))
            }
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
                text = { Text(str("add_to_playlist")) },
                leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) },
                onClick = { adding = true; open.value = false },
            )
            val downloads = LocalServices.current.downloads
            val downloaded = downloadStatuses()[song.id] != null
            DropdownMenuItem(
                text = { Text(str(if (downloaded) "remove_download" else "download")) },
                leadingIcon = { Icon(if (downloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download, null) },
                onClick = {
                    if (downloaded) downloads.remove(song.id) else downloads.download(listOf(song))
                    open.value = false
                },
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
    val listState = rememberLazyListState()
    ShelfHeader(section.title, listState)
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            SongMenu(item.song, menuOpen, showButton = false)
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

@Composable
fun logoPainter(): androidx.compose.ui.graphics.painter.Painter {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val vector = remember(density) {
        LocalSnackbar::class.java.classLoader.getResourceAsStream("drawable/logo.xml")!!.use {
            androidx.compose.ui.res.loadXmlImageVector(org.xml.sax.InputSource(it), density)
        }
    }
    return androidx.compose.ui.graphics.vector.rememberVectorPainter(vector)
}

@Composable
fun ShelfHeader(title: String, state: ScrollableState, step: () -> Float = { (state as? LazyListState)?.layoutInfo?.viewportSize?.width?.times(0.8f) ?: 600f }) {
    Row(verticalAlignment = Alignment.Bottom) {
        SectionTitle(title, Modifier.weight(1f))
        if (state.canScrollBackward || state.canScrollForward) {
            Row(Modifier.padding(end = 32.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrollArrow(state, forward = false, step = step)
                ScrollArrow(state, forward = true, step = step)
            }
        }
    }
}

@Composable
fun ScrollArrow(
    state: ScrollableState,
    forward: Boolean,
    modifier: Modifier = Modifier,
    step: () -> Float = { (state as? LazyListState)?.layoutInfo?.viewportSize?.width?.times(0.8f) ?: 600f },
) {
    val scope = rememberCoroutineScope()
    FilledTonalIconButton(
        onClick = {
            val distance = step()
            scope.launch { state.animateScrollBy(if (forward) distance else -distance) }
        },
        enabled = if (forward) state.canScrollForward else state.canScrollBackward,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            if (forward) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            str(if (forward) "next" else "previous"),
        )
    }
}

private val playfulShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Arch,
    MaterialShapes.Sunny,
    MaterialShapes.Gem,
)

@Composable
fun playfulShape(index: Int): Shape = playfulShapes[index % playfulShapes.size].toShape()
