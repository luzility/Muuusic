package me.luzility.muuusic.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.compose.material3.toPath
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import me.luzility.muuusic.data.recognition.RecognizedSong
import me.luzility.muuusic.data.recognition.SongRecognizer
import me.luzility.muuusic.desktop.SystemAudioSource

private sealed interface RecognitionPhase {
    data object Listening : RecognitionPhase
    data object Searching : RecognitionPhase
    data class Found(val song: RecognizedSong) : RecognitionPhase
    data object NotFound : RecognitionPhase
    data object Failed : RecognitionPhase
    data object NoCapture : RecognitionPhase
}

private const val CoverPreloadTimeoutMs = 1_500L
private const val ShowMatchMs = 1_800L
private const val FinishMorphPart = 0.3f
private val CoverSize = 240.dp

@Composable
fun RecognitionOverlay(onClose: () -> Unit, onResult: (String) -> Unit) {
    val services = LocalServices.current
    val player = LocalPlayer.current
    var phase by remember { mutableStateOf<RecognitionPhase>(RecognitionPhase.Listening) }
    var level by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        if (!SystemAudioSource.isSupported) {
            phase = RecognitionPhase.NoCapture
            return@LaunchedEffect
        }
        phase = RecognitionPhase.Listening
        player.pause()
        val result = services.recognizer.recognize(
            open = { SystemAudioSource.open() },
            onLevel = { level = it },
            onSearching = { phase = RecognitionPhase.Searching },
        )
        level = 0f
        if (result is SongRecognizer.Result.Found) preload(result.song.coverUrl)
        phase = when (result) {
            is SongRecognizer.Result.Found -> RecognitionPhase.Found(result.song)
            SongRecognizer.Result.NotFound -> RecognitionPhase.NotFound
            SongRecognizer.Result.Failed -> RecognitionPhase.Failed
        }
    }
    LaunchedEffect(phase) {
        val found = phase as? RecognitionPhase.Found ?: return@LaunchedEffect
        delay(ShowMatchMs)
        onResult("${found.song.title} ${found.song.artist}")
    }

    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Close, str("close"))
            }
            Spacer(Modifier.weight(1f))
            LabBadge()
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                Orb(phase, level)
            }
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = phase,
                transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150)) },
                label = "status",
            ) { current -> Status(current, onRetry = { attempt++ }) }
        }
    }
}

private suspend fun preload(url: String?) {
    url ?: return
    val context = PlatformContext.INSTANCE
    withTimeoutOrNull(CoverPreloadTimeoutMs) {
        SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).build())
    }
}

@Composable
private fun LabBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Science, null, Modifier.size(16.dp))
            Text(str("experimental"), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private class MorphShape(private val morph: Morph, private val progress: Float, private val rotationDegrees: Float = 0f) : Shape {
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

@Composable
private fun Orb(phase: RecognitionPhase, level: Float) {
    val colors = MaterialTheme.colorScheme
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf, MaterialShapes.SoftBurst,
            MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.Flower,
        )
    }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val toCover = remember { shapes.map { Morph(it, MaterialShapes.Square) } }
    val searching = phase == RecognitionPhase.Searching
    val listening = phase == RecognitionPhase.Listening
    val found = phase as? RecognitionPhase.Found
    val idle = !listening && !searching && found == null

    val transition = rememberInfiniteTransition(label = "orb")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = shapes.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(shapes.size * 1_300, easing = LinearEasing)),
        label = "morphCycle",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (searching) 4_000 else 16_000, easing = LinearEasing)),
        label = "rotation",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "ripple",
    )
    val swell by animateFloatAsState(
        targetValue = if (listening) level else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swell",
    )
    val baseSize by animateFloatAsState(
        targetValue = if (idle) 0.72f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "orbSize",
    )
    val frozen = remember(found != null) { Triple(cycle.toInt() % shapes.size, cycle - cycle.toInt(), spin) }
    val reveal by animateFloatAsState(
        targetValue = if (found != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow),
        label = "reveal",
    )
    val ripplesAlpha by animateFloatAsState(if (listening) 1f else 0f, tween(400), label = "ripplesAlpha")

    val shape = if (found == null && reveal < 0.001f) {
        MorphShape(morphs[cycle.toInt() % shapes.size], FastOutSlowInEasing.transform(cycle - cycle.toInt()), spin)
    } else {
        val (index, startProgress, startSpin) = frozen
        val nearest = if (startProgress < 0.5f) index else (index + 1) % shapes.size
        val r = reveal.coerceIn(0f, 1f)
        if (r < FinishMorphPart) {
            val t = r / FinishMorphPart
            val target = if (startProgress < 0.5f) 0f else 1f
            MorphShape(morphs[index], FastOutSlowInEasing.transform(startProgress + (target - startProgress) * t), startSpin)
        } else {
            val t = (r - FinishMorphPart) / (1f - FinishMorphPart)
            val upright = Math.round(startSpin / 90f) * 90f
            MorphShape(toCover[nearest], FastOutSlowInEasing.transform(t), startSpin + (upright - startSpin) * t)
        }
    }

    Box(contentAlignment = Alignment.Center) {
        if (ripplesAlpha > 0f) {
            Canvas(Modifier.size(320.dp)) {
                val base = 85.dp.toPx()
                repeat(3) { i ->
                    val p = (ripple + i / 3f) % 1f
                    drawCircle(
                        color = colors.primary.copy(alpha = (1f - p) * 0.28f * ripplesAlpha),
                        radius = base * (1f + p * (0.75f + swell * 0.35f)),
                    )
                }
            }
        }
        val orbSize = 170.dp + (CoverSize - 170.dp) * reveal
        Box(
            Modifier
                .size(orbSize)
                .graphicsLayer {
                    val scale = baseSize * (1f + swell * 0.22f)
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(if (idle) colors.surfaceContainerHighest else colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (found != null) {
                AsyncImage(
                    model = found.song.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal.coerceIn(0f, 1f) },
                )
            }
        }
        if (found == null) {
            AnimatedContent(targetState = phase, label = "orbIcon") { current ->
                when (current) {
                    RecognitionPhase.Searching -> LoadingIndicator(Modifier.size(72.dp), color = colors.onPrimary)
                    RecognitionPhase.Listening -> Icon(Icons.Rounded.GraphicEq, null, tint = colors.onPrimary, modifier = Modifier.size(64.dp))
                    RecognitionPhase.NoCapture -> Icon(Icons.Rounded.VolumeOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    RecognitionPhase.Failed -> Icon(Icons.Rounded.WifiOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    else -> Icon(Icons.Rounded.SearchOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
                }
            }
        }
    }
}

@Composable
private fun Status(phase: RecognitionPhase, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (title, body) = when (phase) {
            RecognitionPhase.Listening -> "recognize_listening" to "recognize_listening_hint_desktop"
            RecognitionPhase.Searching -> "recognize_searching" to null
            is RecognitionPhase.Found -> null to null
            RecognitionPhase.NotFound -> "recognize_not_found" to "recognize_not_found_hint"
            RecognitionPhase.Failed -> "recognize_failed" to "recognize_failed_hint"
            RecognitionPhase.NoCapture -> "recognize_no_capture" to "recognize_no_capture_hint"
        }
        if (phase is RecognitionPhase.Found) {
            Text(phase.song.title, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
            Text(phase.song.artist, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        title?.let { Text(str(it), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center) }
        body?.let {
            Text(str(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (phase == RecognitionPhase.NotFound || phase == RecognitionPhase.Failed) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(str("retry"))
            }
        }
    }
}
