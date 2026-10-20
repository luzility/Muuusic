package me.luzility.muuusic.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedCallback
import me.luzility.muuusic.container
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Science
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.compose.AsyncImage
import me.luzility.muuusic.R
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.playback.PlayerViewModel
import androidx.compose.material3.toPath

val LocalPlayer = staticCompositionLocalOf<PlayerViewModel> { error("PlayerViewModel not provided") }

class AppNavigator(
    val openArtist: (name: String, url: String?) -> Unit,
    val openCollection: (MusicCollection) -> Unit,
    val openPlaylist: (id: Long) -> Unit,
    val openDownloads: () -> Unit,
    val openRecognition: () -> Unit,
)

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("AppNavigator not provided") }

// Lists add this to their bottom padding so they can scroll under the mini player.
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    // Outline only, so clipped content (artwork) stays upright.
    private val rotationDegrees: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress)
        if (rotationDegrees != 0f) {
            path.translate(Offset(-0.5f, -0.5f))
            path.transform(Matrix().apply { rotateZ(rotationDegrees) })
            path.translate(Offset(0.5f, 0.5f))
        }
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

val playfulShapes = listOf(
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

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, offerDownloads: Boolean = true) {
    val online by LocalContext.current.container.connectivity.online.collectAsStateWithLifecycle()
    val retry by rememberUpdatedState(onRetry)
    var wasOffline by remember { mutableStateOf(!online) }
    LaunchedEffect(online) {
        if (online && wasOffline) retry()
        wasOffline = !online
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!online) {
            Icon(Icons.Rounded.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(if (online) R.string.error_generic else R.string.error_offline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!online && offerDownloads) {
            val navigator = LocalNavigator.current
            Button(onClick = navigator.openDownloads, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.DownloadDone, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.go_to_downloads))
            }
            FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.retry))
            }
        } else {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun LabBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Science, null, Modifier.size(16.dp))
            Text(stringResource(R.string.experimental), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun TopBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(enabled, dispatcher, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose {}
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = currentOnBack()
        }
        dispatcher.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}
