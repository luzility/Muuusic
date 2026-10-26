package me.luzility.muuusic.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.data.lyrics.LyricLine
import me.luzility.muuusic.data.lyrics.Lyrics
import me.luzility.muuusic.data.lyrics.LyricsAlign
import me.luzility.muuusic.data.lyrics.LyricsPrefs
import me.luzility.muuusic.data.lyrics.LyricsTextSize
import me.luzility.muuusic.data.lyrics.LyricsTranslator
import me.luzility.muuusic.desktop.playback.PlayerState
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private const val LeadMs = 150L

private const val InterludeMs = 4_000L

private const val ActiveLineAnchor = 0.4f

private const val RestWeight = 420f
private const val ActiveWeight = 760f
private const val MaxWeight = 840

private const val WaveStaggerMs = 45L

@Composable
fun LyricsPanel() {
    val services = LocalServices.current
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val song = state.current ?: return
    val prefs by services.lyricsSettings.prefs.collectAsState()
    val offset by remember(song.id) { services.lyricsSettings.offsetFor(song.id) }.collectAsState(initial = 0L)
    var attempt by remember { mutableIntStateOf(0) }
    val lyrics by produceState<Loadable<Lyrics?>>(Loadable.Loading, song.id, attempt) {
        services.lyrics.cached(song)?.let {
            value = Loadable.Ready(it.takeUnless(Lyrics::isEmpty))
            return@produceState
        }
        value = Loadable.Loading
        val duration = song.durationSec.takeIf { it > 0 } ?: (state.durationMs / 1000)
        value = runCatching { services.lyrics.lyricsFor(song, duration) }
            .fold(onSuccess = { Loadable.Ready(it?.takeUnless(Lyrics::isEmpty)) }, onFailure = { Loadable.Failed })
    }
    val data = (lyrics as? Loadable.Ready)?.data
    val translations by produceState(emptyMap<String, String>(), song.id, prefs.translate, data) {
        value = emptyMap()
        if (!prefs.translate || data == null) return@produceState
        val lines = data.synced?.map { it.text } ?: data.plain.orEmpty().lines()
        value = runCatching { services.lyricsTranslator.translate(lines, LyricsTranslator.targetFor(Locale.getDefault())) }.getOrDefault(emptyMap())
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        when (val current = lyrics) {
            Loadable.Loading -> LoadingIndicator(Modifier.size(56.dp))
            Loadable.Failed -> ErrorBox(onRetry = { attempt++ })
            is Loadable.Ready -> {
                val loaded = current.data
                if (loaded == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Lyrics, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        Text(str("lyrics_not_found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val synced = loaded.synced
                    if (synced != null) {
                        androidx.compose.runtime.key(song.id) {
                            SyncedLyrics(state, synced, prefs, offset, translations)
                        }
                    } else {
                        PlainLyrics(loaded.plain.orEmpty(), prefs, translations)
                    }
                }
            }
        }
    }
}

private class AutoScrollGuard {
    var active = false
        private set

    suspend fun run(scroll: suspend () -> Unit) {
        active = true
        try {
            scroll()
            withFrameMillis { }
        } finally {
            active = false
        }
    }
}

@Stable
private class LyricsWave {
    private class Kick(val anchor: Int, val shift: Float, val startMs: Long)

    private val curve = TargetBasedAnimation(
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        Float.VectorConverter,
        initialValue = 1f,
        targetValue = 0f,
    )
    private val curveMs = curve.durationNanos / 1_000_000
    private val kicks = mutableStateListOf<Kick>()
    private var nowMs by mutableLongStateOf(0L)

    suspend fun start(anchorIndex: Int, shiftPx: Float) {
        val frame = withFrameMillis { it }
        nowMs = frame
        kicks += Kick(anchorIndex, shiftPx, frame)
    }

    suspend fun tick() {
        while (true) {
            snapshotFlow { kicks.isNotEmpty() }.first { it }
            while (kicks.isNotEmpty()) {
                withFrameMillis { now ->
                    nowMs = now
                    kicks.removeAll { now - it.startMs > curveMs + WaveStaggerMs * 10 }
                }
            }
        }
    }

    fun offsetFor(index: Int): Float {
        val now = nowMs
        var offset = 0f
        for (kick in kicks) {
            val delayMs = ((index - kick.anchor + 1).coerceIn(0, 10)) * WaveStaggerMs
            val local = (now - kick.startMs - delayMs).coerceAtLeast(0L)
            if (local < curveMs) offset += kick.shift * curve.getValueFromNanos(local * 1_000_000)
        }
        return offset
    }
}

@Composable
private fun SyncedLyrics(
    state: PlayerState,
    source: List<LyricLine>,
    prefs: LyricsPrefs,
    offsetMs: Long,
    translations: Map<String, String>,
) {
    val player = LocalPlayer.current
    val motion = MaterialTheme.motionScheme
    val positionState = rememberPlaybackPosition(player, state, intervalMs = 80)
    val offsetState = rememberUpdatedState(offsetMs)
    val positionProvider = remember { { positionState.value + offsetState.value } }
    val lines = remember(source) { withInterludes(source) }
    val current by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= positionProvider() + LeadMs } }
    }

    val listState = rememberLazyListState()
    val wave = remember { LyricsWave() }
    LaunchedEffect(wave) { wave.tick() }
    var browsing by remember { mutableStateOf(false) }
    var resyncing by remember { mutableStateOf(false) }
    val autoScroll = remember { AutoScrollGuard() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !autoScroll.active) browsing = true
        }
    }

    LaunchedEffect(current, browsing) {
        if (browsing || current < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val padding = -layout.viewportStartOffset
        val anchor = (layout.viewportSize.height * ActiveLineAnchor).toInt() - padding
        val item = layout.visibleItemsInfo.firstOrNull { it.index == current }
        if (item == null) {
            autoScroll.run { listState.scrollToItem(current, -anchor) }
            resyncing = false
            return@LaunchedEffect
        }
        val delta = (item.offset - anchor).toFloat()
        if (abs(delta) < 1f) return@LaunchedEffect
        if (resyncing || abs(delta) > layout.viewportSize.height / 2) {
            autoScroll.run { listState.animateScrollBy(delta, motion.slowSpatialSpec()) }
            resyncing = false
        } else {
            wave.start(current, delta)
            autoScroll.run { listState.scrollBy(delta) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(),
            contentPadding = PaddingValues(
                top = maxHeight * ActiveLineAnchor,
                bottom = maxHeight * (1f - ActiveLineAnchor),
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(lines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
                val start = line.timeMs
                val end = lines.getOrNull(index + 1)?.timeMs ?: (start + InterludeMs)
                val itemModifier = Modifier.graphicsLayer { translationY = wave.offsetFor(index) }
                if (line.text.isBlank()) {
                    Interlude(
                        active = index == current,
                        align = prefs.align,
                        progress = { ((positionProvider() - start).toFloat() / (end - start)).coerceIn(0f, 1f) },
                        modifier = itemModifier,
                    )
                } else {
                    LyricLineItem(
                        text = line.text,
                        translation = translations[line.text.trim()],
                        active = index == current,
                        passed = index < current,
                        distance = if (current < 0) index + 1 else abs(index - current),
                        browsing = browsing,
                        prefs = prefs,
                        onClick = {
                            player.seekTo((start - offsetMs).coerceAtLeast(0))
                            if (browsing) {
                                resyncing = true
                                browsing = false
                            }
                        },
                        modifier = itemModifier,
                    )
                }
            }
            item(key = "provider") {
                val distance = if (current < 0) lines.size + 1 else lines.size - current
                val alpha by animateFloatAsState(
                    targetValue = if (browsing) 0.65f else (0.55f - distance * 0.06f).coerceAtLeast(0.25f),
                    animationSpec = motion.defaultEffectsSpec(),
                    label = "creditAlpha",
                )
                val blur by animateDpAsState(
                    targetValue = if (browsing || !prefs.blur) 0.dp else (distance.coerceAtMost(4) * 0.8f).dp,
                    animationSpec = motion.slowEffectsSpec(),
                    label = "creditBlur",
                )
                ProviderCredit(
                    prefs.align,
                    Modifier
                        .graphicsLayer {
                            translationY = wave.offsetFor(lines.size)
                            this.alpha = alpha
                        }
                        .blur(blur),
                )
            }
        }

        AnimatedVisibility(
            visible = browsing,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            enter = slideInVertically(motion.defaultSpatialSpec()) { -it * 2 } + fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutVertically(motion.fastSpatialSpec()) { -it * 2 } + fadeOut(motion.fastEffectsSpec()),
        ) {
            FilledTonalButton(
                onClick = {
                    resyncing = true
                    browsing = false
                },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(Icons.Rounded.Sync, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(str("lyrics_resync"))
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    translation: String?,
    active: Boolean,
    passed: Boolean,
    distance: Int,
    browsing: Boolean,
    prefs: LyricsPrefs,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val centered = prefs.align == LyricsAlign.Center
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lineScale",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            active -> 1f
            browsing -> 0.65f
            passed -> 0.32f
            else -> (0.55f - distance * 0.06f).coerceAtLeast(0.25f)
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "lineAlpha",
    )
    val blur by animateDpAsState(
        targetValue = if (active || browsing || !prefs.blur) 0.dp else (distance.coerceAtMost(4) * 0.8f).dp,
        animationSpec = motion.slowEffectsSpec(),
        label = "lineBlur",
    )
    val color by animateColorAsState(
        targetValue = if (active) colors.primary else colors.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "lineColor",
    )

    val weight by animateFloatAsState(
        targetValue = if (active) ActiveWeight else RestWeight,
        animationSpec = motion.defaultSpatialSpec(),
        label = "lineWeight",
    )
    val weightStep = FlexFont.quantize(weight.coerceAtMost(MaxWeight.toFloat()))
    val baseStyle = lyricsTextStyle(prefs.textSize).copy(textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    val style = baseStyle.copy(fontFamily = FlexFont.family(weightStep), fontWeight = FontWeight(weightStep))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val boldStyle = baseStyle.copy(fontFamily = FlexFont.family(MaxWeight), fontWeight = FontWeight(MaxWeight))
        val wrapped = remember(text, maxWidth, boldStyle) {
            val layout = measurer.measure(text, boldStyle, constraints = Constraints(maxWidth = maxWidth))
            (0 until layout.lineCount).joinToString("\n") {
                text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim()
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
                }
                .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
        ) {
            Text(text = wrapped, style = style, color = color, modifier = Modifier.fillMaxWidth())
            TranslationLine(translation, prefs, color)
        }
    }
}

@Composable
private fun Interlude(active: Boolean, align: LyricsAlign, progress: () -> Float, modifier: Modifier = Modifier) {
    val centered = align == LyricsAlign.Center
    val motion = MaterialTheme.motionScheme
    val visibleScale by animateFloatAsState(if (active) 1f else 0.6f, motion.defaultSpatialSpec(), label = "interludeScale")
    val visibleAlpha by animateFloatAsState(if (active) 1f else 0.25f, motion.defaultEffectsSpec(), label = "interludeAlpha")
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "breatheScale",
    )
    val color = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 18.dp)
            .graphicsLayer {
                val s = visibleScale * if (active) breathe else 1f
                scaleX = s
                scaleY = s
                alpha = visibleAlpha
                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        val fill = if (active) (progress() * 3f - i).coerceIn(0f, 1f) else 0f
                        val s = 0.7f + 0.3f * fill
                        scaleX = s
                        scaleY = s
                        alpha = 0.3f + 0.7f * fill
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String, prefs: LyricsPrefs, translations: Map<String, String>) {
    val centered = prefs.align == LyricsAlign.Center
    val style = lyricsTextStyle(prefs.textSize).copy(
        fontFamily = FlexFont.family(RestWeight.toInt()),
        fontWeight = FontWeight(RestWeight.toInt()),
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 48.dp, horizontal = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (translations.isEmpty()) {
                Text(text = text, style = style, color = color, modifier = Modifier.fillMaxWidth())
            } else {
                text.lines().forEach { line ->
                    Text(text = line, style = style, color = color, modifier = Modifier.fillMaxWidth())
                    TranslationLine(translations[line.trim()], prefs, color)
                }
            }
        }
        ProviderCredit(prefs.align)
    }
}

@Composable
private fun TranslationLine(translation: String?, prefs: LyricsPrefs, color: Color) {
    val motion = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(translation) }
    if (translation != null) shown = translation
    AnimatedVisibility(
        visible = translation != null,
        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
    ) {
        Text(
            text = shown.orEmpty(),
            style = translationTextStyle(prefs.textSize).copy(
                fontFamily = FlexFont.family(RestWeight.toInt()),
                fontWeight = FontWeight(RestWeight.toInt()),
                textAlign = if (prefs.align == LyricsAlign.Center) TextAlign.Center else TextAlign.Start,
            ),
            color = color.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun translationTextStyle(size: LyricsTextSize): TextStyle = when (size) {
    LyricsTextSize.Small -> MaterialTheme.typography.bodyLarge
    LyricsTextSize.Medium -> MaterialTheme.typography.titleMedium
    LyricsTextSize.Large -> MaterialTheme.typography.titleLarge
}

@Composable
private fun ProviderCredit(align: LyricsAlign, modifier: Modifier = Modifier) {
    Text(
        text = str("lyrics_source"),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (align == LyricsAlign.Center) TextAlign.Center else TextAlign.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 32.dp),
    )
}

@Composable
private fun lyricsTextStyle(size: LyricsTextSize): TextStyle = when (size) {
    LyricsTextSize.Small -> MaterialTheme.typography.headlineSmallEmphasized
    LyricsTextSize.Medium -> MaterialTheme.typography.headlineMediumEmphasized
    LyricsTextSize.Large -> MaterialTheme.typography.headlineLargeEmphasized
}

private fun withInterludes(lines: List<LyricLine>): List<LyricLine> {
    if (lines.isEmpty()) return lines
    val out = ArrayList<LyricLine>(lines.size + 4)
    if (lines.first().timeMs > InterludeMs) out += LyricLine(0, "")
    lines.forEachIndexed { i, line ->
        out += line
        val next = lines.getOrNull(i + 1) ?: return@forEachIndexed
        if (line.text.isNotBlank() && next.timeMs - line.timeMs > InterludeMs * 2) {
            out += LyricLine(line.timeMs + InterludeMs, "")
        }
    }
    return out
}

private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.12f to Color.Black,
                0.82f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
