package me.luzility.muuusic.ui.player

import androidx.compose.ui.platform.testTag
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luzility.muuusic.R
import me.luzility.muuusic.data.lyrics.LyricsAlign
import me.luzility.muuusic.data.lyrics.LyricsTextSize
import me.luzility.muuusic.ui.components.LocalPlayer
import java.util.Locale
import kotlin.math.roundToLong

private const val OffsetStepMs = 100L
private const val OffsetRangeMs = 3_000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsButton(checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (checked) 12.dp else 20.dp, motion.fastSpatialSpec(), label = "lyricsCorner")
    val container by animateColorAsState(
        if (checked) colors.secondaryContainer else Color.Transparent,
        motion.defaultEffectsSpec(),
        label = "lyricsContainer",
    )
    Box(
        modifier = Modifier
            .testTag("lyrics_button")
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .combinedClickable(
                role = Role.Switch,
                onClickLabel = stringResource(R.string.lyrics),
                onLongClickLabel = stringResource(R.string.lyrics_settings),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Lyrics,
            contentDescription = stringResource(R.string.lyrics),
            tint = if (checked) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
    }
}

@Composable
fun LyricsSettingsSheet(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val prefs by player.lyricsPrefs.collectAsStateWithLifecycle()
    val offset by player.lyricsOffset.collectAsStateWithLifecycle()

    fun setOffset(value: Long) = player.setLyricsOffset(value.coerceIn(-OffsetRangeMs, OffsetRangeMs))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.lyrics_settings), style = MaterialTheme.typography.titleLargeEmphasized)

            SectionLabel(stringResource(R.string.lyrics_timing))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { setOffset(offset - OffsetStepMs) },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.lyrics_later))
                }
                Text(
                    text = stringResource(R.string.lyrics_timing_value, formatOffset(offset)),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { setOffset(offset + OffsetStepMs) },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.lyrics_earlier))
                }
            }
            Slider(
                value = offset.toFloat(),
                onValueChange = { setOffset((it / OffsetStepMs).roundToLong() * OffsetStepMs) },
                valueRange = -OffsetRangeMs.toFloat()..OffsetRangeMs.toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lyrics_timing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { setOffset(0) }, enabled = offset != 0L) {
                    Text(stringResource(R.string.reset))
                }
            }

            SectionLabel(stringResource(R.string.lyrics_text_size))
            ConnectedChoice(
                options = LyricsTextSize.entries,
                selected = prefs.textSize,
                label = {
                    when (it) {
                        LyricsTextSize.Small -> R.string.size_small
                        LyricsTextSize.Medium -> R.string.size_medium
                        LyricsTextSize.Large -> R.string.size_large
                    }
                },
                onSelect = { size -> player.updateLyricsPrefs { it.copy(textSize = size) } },
            )

            SectionLabel(stringResource(R.string.lyrics_alignment))
            ConnectedChoice(
                options = LyricsAlign.entries,
                selected = prefs.align,
                label = { if (it == LyricsAlign.Start) R.string.align_start else R.string.align_center },
                onSelect = { align -> player.updateLyricsPrefs { it.copy(align = align) } },
            )

            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lyrics_blur), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = prefs.blur, onCheckedChange = { blur -> player.updateLyricsPrefs { it.copy(blur = blur) } })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun <T> ConnectedChoice(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(label(option)))
            }
        }
    }
}

private fun formatOffset(offsetMs: Long): String {
    val seconds = offsetMs / 1000.0
    return if (offsetMs == 0L) String.format(Locale.getDefault(), "%.1f", 0.0)
    else String.format(Locale.getDefault(), "%+.1f", seconds)
}
