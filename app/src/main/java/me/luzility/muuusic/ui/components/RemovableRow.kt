package me.luzility.muuusic.ui.components

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.R

@Composable
fun RemovableRow(onRemove: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * RemoveThreshold })
    var removed by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(1f) }
    val armed by remember {
        derivedStateOf { -(runCatching { state.requireOffset() }.getOrDefault(0f)) > width * RemoveThreshold }
    }

    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart && !removed) {
            removed = true
            onRemove()
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = Modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val background by animateColorAsState(
                if (armed) colors.errorContainer else colors.surfaceContainerHighest,
                motion.defaultEffectsSpec(),
                label = "removeBackground",
            )
            val tint by animateColorAsState(
                if (armed) colors.onErrorContainer else colors.onSurfaceVariant,
                motion.defaultEffectsSpec(),
                label = "removeTint",
            )
            val scale by animateFloatAsState(if (armed) 1.2f else 0.85f, motion.fastSpatialSpec(), label = "removeIcon")
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(background)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    stringResource(R.string.remove),
                    tint = tint,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        },
    ) {
        content()
    }
}

private const val RemoveThreshold = 0.35f
