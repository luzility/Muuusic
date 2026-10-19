package me.luzility.muuusic.ui.player

import me.luzility.muuusic.R
import me.luzility.muuusic.data.settings.Hint
import me.luzility.muuusic.ui.components.HintAnchor
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import me.luzility.muuusic.playback.PlayerUiState
import kotlin.math.roundToInt

private val SideMargin = 12.dp
private val BottomGap = 8.dp
private val MiniCorner = 28.dp

// Fling speed (px/s) that decides direction regardless of drag distance.
private const val FlingVelocity = 1200f

// Fraction of travel a drag must cover to commit.
private const val CommitFraction = 0.1f

// Fraction of bar height the bar must be pulled down to dismiss playback.
private const val DismissFraction = 0.6f

// progress: 0 = mini bar, 1 = full screen.
@Composable
fun PlayerSheet(
    state: PlayerUiState,
    progress: Animatable<Float, AnimationVector1D>,
    bottomInset: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        val miniTop = fullHeight - bottomInset - BottomGap - MiniPlayerHeight
        val travelPx = with(density) { miniTop.toPx() }.coerceAtLeast(1f)

        fun animateTo(target: Float, velocity: Float = 0f) {
            scope.launch { progress.animateTo(target, spring, initialVelocity = velocity) }
        }

        // Pulling the collapsed bar down slides it away; far enough, and playback is dismissed.
        val dismissOffset = remember { Animatable(0f) }
        val miniHeightPx = with(density) { MiniPlayerHeight.toPx() }
        val offscreenPx = with(density) { (bottomInset + BottomGap + MiniPlayerHeight).toPx() }

        var dragStart by remember { mutableFloatStateOf(0f) }
        val dragState = rememberDraggableState { delta ->
            scope.launch {
                val fromBar = dragStart < 0.01f
                if (fromBar && (dismissOffset.value > 0f || (delta > 0f && progress.value <= 0f))) {
                    val next = dismissOffset.value + delta
                    dismissOffset.snapTo(next.coerceAtLeast(0f))
                    // Changing direction past the resting point turns the gesture into an expand.
                    if (next < 0f) progress.snapTo((-next / travelPx).coerceIn(0f, 1f))
                } else {
                    progress.snapTo((progress.value - delta / travelPx).coerceIn(0f, 1f))
                }
            }
        }

        // The expressive spring overshoots past 0 and 1; geometry may stretch a little with it,
        // but corner radius and colour must stay in range.
        val p = progress.value
        val pc = p.coerceIn(0f, 1f)
        Box(
            Modifier
                .offset { IntOffset(lerp(SideMargin, 0.dp, p).roundToPx(), lerp(miniTop, 0.dp, p).roundToPx()) }
                .size(
                    width = lerp(fullWidth - SideMargin * 2, fullWidth, p),
                    height = lerp(MiniPlayerHeight, fullHeight, p),
                )
                .graphicsLayer {
                    translationY = dismissOffset.value
                    alpha = (1f - dismissOffset.value / (miniHeightPx * 2f)).coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(lerp(MiniCorner, 0.dp, pc)))
                .background(lerp(colors.secondaryContainer, colors.surface, pc))
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragStart = progress.value },
                    onDragStopped = { velocity ->
                        if (dismissOffset.value > 0f) {
                            val dismissed = dismissOffset.value > miniHeightPx * DismissFraction || velocity > FlingVelocity
                            scope.launch {
                                if (dismissed) {
                                    dismissOffset.animateTo(offscreenPx, spring, initialVelocity = velocity)
                                    onDismiss()
                                } else {
                                    dismissOffset.animateTo(0f, spring, initialVelocity = velocity)
                                }
                            }
                            return@draggable
                        }
                        val moved = progress.value - dragStart
                        val target = when {
                            velocity < -FlingVelocity -> 1f
                            velocity > FlingVelocity -> 0f
                            moved > CommitFraction -> 1f
                            moved < -CommitFraction -> 0f
                            else -> if (progress.value > 0.5f) 1f else 0f
                        }
                        animateTo(target, -velocity / travelPx)
                    },
                )
                .testTag("player_sheet")
                // Always enabled: toggling `enabled` mid-drag rebuilds the modifier and cancels the gesture.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (progress.value < 0.01f) animateTo(1f) },
        ) {
            if (p < 0.4f) {
                HintAnchor(Hint.MiniPlayer, R.string.hint_mini_player_title, R.string.hint_mini_player, enabled = p < 0.01f) {
                    MiniPlayerContent(
                        state = state,
                        modifier = Modifier.graphicsLayer { alpha = (1f - p * 3f).coerceIn(0f, 1f) },
                    )
                }
            }
            if (p > 0.02f) {
                // Laid out at full-screen size from the start, so the growing container reveals it
                // instead of squeezing it through every intermediate size.
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(fullWidth, fullHeight)
                        .graphicsLayer { alpha = ((p - 0.2f) / 0.6f).coerceIn(0f, 1f) },
                ) {
                    NowPlayingScreen(onCollapse = { animateTo(0f) })
                }
            }
        }
    }
}
