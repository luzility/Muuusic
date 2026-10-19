package me.luzility.muuusic.ui.recognition

import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.layout.ContentScale
import coil3.request.ImageRequest
import coil3.imageLoader
import coil3.compose.AsyncImage
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.recognition.RecognizedSong
import me.luzility.muuusic.data.recognition.SongRecognizer
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.components.LabBadge
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.MorphShape

sealed interface RecognitionPhase {
    data object Listening : RecognitionPhase
    data object Searching : RecognitionPhase
    data class Found(val song: RecognizedSong) : RecognitionPhase
    data object NotFound : RecognitionPhase
    data object Failed : RecognitionPhase
    data object NoPermission : RecognitionPhase
}

class RecognitionViewModel(app: Application) : AndroidViewModel(app) {
    private val recognizer = app.container.songRecognizer

    private val _phase = MutableStateFlow<RecognitionPhase>(RecognitionPhase.Listening)
    val phase = _phase.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level = _level.asStateFlow()

    private var job: Job? = null

    fun start() {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _phase.value = RecognitionPhase.NoPermission
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _phase.value = RecognitionPhase.Listening
            // The chime plays first; recording starts once it has faded so it isn't fingerprinted.
            launch { Earcons.playStart() }
            delay(Earcons.START_DURATION_MS)
            @Suppress("MissingPermission") // checked above
            val result = recognizer.recognize(
                onLevel = { _level.value = it },
                onSearching = { _phase.value = RecognitionPhase.Searching },
            )
            _level.value = 0f
            // Load the cover before revealing it, so the morph lands on the artwork, not a placeholder.
            if (result is SongRecognizer.Result.Found) preload(result.song.coverUrl)
            _phase.value = when (result) {
                is SongRecognizer.Result.Found -> RecognitionPhase.Found(result.song)
                SongRecognizer.Result.NotFound -> RecognitionPhase.NotFound
                SongRecognizer.Result.Failed -> RecognitionPhase.Failed
            }
            if (result is SongRecognizer.Result.Found) Earcons.playFound() else Earcons.playNotFound()
        }
    }

    private suspend fun preload(url: String?) {
        url ?: return
        val app = getApplication<Application>()
        withTimeoutOrNull(COVER_PRELOAD_TIMEOUT_MS) {
            app.imageLoader.execute(ImageRequest.Builder(app).data(url).build())
        }
    }

    fun denied() {
        _phase.value = RecognitionPhase.NoPermission
    }
}

private const val COVER_PRELOAD_TIMEOUT_MS = 1_500L

private const val ShowMatchMs = 1_800L

@Composable
fun RecognitionScreen(onClose: () -> Unit, onResult: (String) -> Unit, vm: RecognitionViewModel = viewModel()) {
    val player = LocalPlayer.current
    val context = LocalContext.current
    val phase by vm.phase.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.denied()
    }
    fun begin() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        // Our own music would drown out (or be recognised instead of) what the user wants to identify.
        player.pause()
        begin()
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
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close))
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
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150))
                },
                label = "status",
            ) { current -> Status(current, onRetry = ::begin) }
        }
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

    // Where the orb was when the match arrived; the reveal continues from exactly there.
    val frozen = remember(found != null) { Triple(cycle.toInt() % shapes.size, cycle - cycle.toInt(), spin) }
    val reveal by animateFloatAsState(
        targetValue = if (found != null) 1f else 0f,
        // Slow enough to follow the shape turning into the cover, with a slight settle at the end.
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow),
        label = "reveal",
    )
    val ripplesAlpha by animateFloatAsState(if (listening) 1f else 0f, tween(400), label = "ripplesAlpha")

    val shape: MorphShape
    val rotation: Float
    if (found == null && reveal < 0.001f) {
        shape = MorphShape(morphs[cycle.toInt() % shapes.size], FastOutSlowInEasing.transform(cycle - cycle.toInt()), spin)
        rotation = spin
    } else {
        val (index, startProgress, startSpin) = frozen
        val nearest = if (startProgress < 0.5f) index else (index + 1) % shapes.size
        val r = reveal.coerceIn(0f, 1f)
        // First third: finish the morph that was under way; then morph into the cover square.
        shape = if (r < FinishMorphPart) {
            val t = r / FinishMorphPart
            val target = if (startProgress < 0.5f) 0f else 1f
            MorphShape(morphs[index], FastOutSlowInEasing.transform(startProgress + (target - startProgress) * t), startSpin)
        } else {
            val t = (r - FinishMorphPart) / (1f - FinishMorphPart)
            // Square is symmetric every 90°, so settling there leaves the cover upright.
            val upright = Math.round(startSpin / 90f) * 90f
            MorphShape(toCover[nearest], FastOutSlowInEasing.transform(t), startSpin + (upright - startSpin) * t)
        }
        rotation = 0f
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
                // No placeholder: until the image is there the orb's own colour shows through.
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
                    RecognitionPhase.NoPermission -> Icon(Icons.Rounded.MicOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    RecognitionPhase.Failed -> Icon(Icons.Rounded.WifiOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    else -> Icon(Icons.Rounded.SearchOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
                }
            }
        }
    }
}

private const val FinishMorphPart = 0.3f
private val CoverSize = 240.dp

@Composable
private fun Status(phase: RecognitionPhase, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (title, body) = when (phase) {
            RecognitionPhase.Listening -> R.string.recognize_listening to R.string.recognize_listening_hint
            RecognitionPhase.Searching -> R.string.recognize_searching to null
            is RecognitionPhase.Found -> null to null
            RecognitionPhase.NotFound -> R.string.recognize_not_found to R.string.recognize_not_found_hint
            RecognitionPhase.Failed -> R.string.recognize_failed to R.string.recognize_failed_hint
            RecognitionPhase.NoPermission -> R.string.recognize_no_permission to R.string.recognize_no_permission_hint
        }
        if (phase is RecognitionPhase.Found) {
            Text(phase.song.title, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
            Text(
                phase.song.artist,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        title?.let { Text(stringResource(it), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center) }
        body?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (phase == RecognitionPhase.NotFound || phase == RecognitionPhase.Failed || phase == RecognitionPhase.NoPermission) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (phase == RecognitionPhase.NoPermission) R.string.recognize_allow else R.string.retry))
            }
        }
    }
}
