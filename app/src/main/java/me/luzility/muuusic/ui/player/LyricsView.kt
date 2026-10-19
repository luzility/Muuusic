package me.luzility.muuusic.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.data.lyrics.LyricsTextSize
import me.luzility.muuusic.data.lyrics.LyricsPrefs
import me.luzility.muuusic.data.lyrics.LyricsAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Column
import me.luzility.muuusic.ui.theme.FlexFont
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import me.luzility.muuusic.R
import me.luzility.muuusic.data.lyrics.LyricLine
import me.luzility.muuusic.data.lyrics.Lyrics
import me.luzility.muuusic.playback.PlayerUiState
import me.luzility.muuusic.ui.UiState
import me.luzility.muuusic.ui.components.EmptyState
import me.luzility.muuusic.ui.components.ErrorState
import me.luzility.muuusic.ui.components.LocalPlayer
import kotlin.math.abs

// Lines light up slightly before their timestamp.
private const val LeadMs = 150L

private const val InterludeMs = 4_000L

// Active line rests a bit above centre, leaving room for upcoming lines.
private const val ActiveLineAnchor = 0.4f

// MaxWeight bounds the spring overshoot and the line breaking.
private const val RestWeight = 420f
private const val ActiveWeight = 760f
private const val MaxWeight = 840

private const val WaveStaggerMs = 45L

@Composable
fun LyricsPanel(state: PlayerUiState, lyrics: UiState<Lyrics?>, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val prefs by player.lyricsPrefs.collectAsStateWithLifecycle()
    val offset by player.lyricsOffset.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            lyrics is UiState.Loading -> LoadingIndicator(Modifier.size(56.dp))
            lyrics is UiState.Error -> ErrorState(onRetry = player::retryLyrics)
            lyrics is UiState.Success && lyrics.data == null ->
                EmptyState(Icons.Rounded.Lyrics, stringResource(R.string.lyrics_not_found))
            lyrics is UiState.Success -> {
                val data = lyrics.data!!
                val synced = data.synced
                if (synced != null) SyncedLyrics(state, synced, prefs, offset) else PlainLyrics(data.plain.orEmpty(), prefs)
            }
        }
    }
}

@Stable
private class LyricsWave {
    private class Kick(val anchor: Int, val shift: Float, val startMs: Long)

    // Critically damped: lines settle straight into place without overshooting.
    private val curve = TargetBasedAnimation(
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        Float.VectorConverter,
        initialValue = 1f,
        targetValue = 0f,
    )
    private val curveMs = curve.durationNanos / 1_000_000
    private val kicks = mutableStateListOf<Kick>()
    private var nowMs by mutableLongStateOf(0L)

    // Waves add up, so a line changing mid-wave continues from where it is.
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

    // Read from a graphics layer, not composition.
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
private fun SyncedLyrics(state: PlayerUiState, source: List<LyricLine>, prefs: LyricsPrefs, offsetMs: Long) {
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
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var browsing by remember { mutableStateOf(false) }
    var resyncing by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) browsing = true }

    LaunchedEffect(current, browsing) {
        if (browsing || current < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        // Item offsets are measured from below the top content padding, not from the viewport edge.
        val padding = -layout.viewportStartOffset
        val anchor = (layout.viewportSize.height * ActiveLineAnchor).toInt() - padding
        val item = layout.visibleItemsInfo.firstOrNull { it.index == current }
        if (item == null) {
            listState.scrollToItem(current, -anchor)
            resyncing = false
            return@LaunchedEffect
        }
        val delta = (item.offset - anchor).toFloat()
        if (abs(delta) < 1f) return@LaunchedEffect
        if (resyncing || abs(delta) > layout.viewportSize.height / 2) {
            listState.animateScrollBy(delta, motion.slowSpatialSpec())
            resyncing = false
        } else {
            wave.start(current, delta)
            listState.scrollBy(delta)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Padding sized to the panel lets the first and last lines reach the anchor too.
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
                Text(stringResource(R.string.lyrics_resync))
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
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
    // No overshoot here: a line that wobbles while its fill runs reads as a glitch.
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
    // Colour changes gradually too; an instant switch flashes white while alpha is still high.
    val color by animateColorAsState(
        targetValue = if (active) colors.primary else colors.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "lineColor",
    )

    // The overshoot of the weight spring is a brief extra-bold pop.
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
        // Bolder text is wider. Breaking lines where the boldest weight would wrap keeps the
        // layout identical through the morph, so nothing reflows mid-animation.
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val boldStyle = baseStyle.copy(fontFamily = FlexFont.family(MaxWeight), fontWeight = FontWeight(MaxWeight))
        val wrapped = remember(text, maxWidth, boldStyle) {
            val layout = measurer.measure(text, boldStyle, constraints = Constraints(maxWidth = maxWidth))
            (0 until layout.lineCount).joinToString("\n") {
                text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim()
            }
        }
        Text(
            text = wrapped,
            style = style,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
                }
                // Blur is a no-op before Android 12, which is fine: alpha still carries the focus.
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.dp) Modifier.blur(blur) else Modifier),
        )
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
private fun PlainLyrics(text: String, prefs: LyricsPrefs) {
    val centered = prefs.align == LyricsAlign.Center
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 48.dp, horizontal = 8.dp),
    ) {
        Text(
            text = text,
            style = lyricsTextStyle(prefs.textSize).copy(
                fontFamily = FlexFont.family(RestWeight.toInt()),
                fontWeight = FontWeight(RestWeight.toInt()),
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        ProviderCredit(prefs.align)
    }
}

@Composable
private fun ProviderCredit(align: LyricsAlign, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.lyrics_source),
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
        // A short line followed by a long silence: start the interlude once the line has had its moment.
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
