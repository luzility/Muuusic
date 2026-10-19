package me.luzility.muuusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpeedPresets = listOf(0.75f, 1f, 1.25f, 1.5f)

private fun speedLabel(speed: Float) =
    String.format(Locale.getDefault(), "%.2f", speed).trimEnd('0').trimEnd('.', ',') + "×"

@Composable
fun SpeedButton(onClick: () -> Unit) {
    val prefs by LocalContext.current.container.settings.prefs.collectAsStateWithLifecycle()
    val changed = abs(prefs.playbackSpeed - 1f) > 0.001f || (!prefs.linkPitch && prefs.pitchSemitones != 0f)

    AnimatedContent(targetState = changed, label = "speedButton") { active ->
        if (!active) {
            IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Speed, stringResource(R.string.speed_pitch))
            }
        } else {
            FilledTonalButton(
                onClick = onClick,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.SmallContentPadding,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Icon(Icons.Rounded.Speed, stringResource(R.string.speed_pitch), Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(speedLabel(prefs.playbackSpeed), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SpeedSheet(onDismiss: () -> Unit) {
    val settings = LocalContext.current.container.settings
    val prefs by settings.prefs.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.speed_pitch), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(16.dp))

            ValueRow(stringResource(R.string.speed), speedLabel(prefs.playbackSpeed))
            Slider(
                value = prefs.playbackSpeed,
                onValueChange = { v -> settings.update { it.copy(playbackSpeed = (v * 20).roundToInt() / 20f) } },
                valueRange = 0.5f..2f,
                steps = 29,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedPresets.forEach { preset ->
                    FilterChip(
                        selected = abs(prefs.playbackSpeed - preset) < 0.001f,
                        onClick = { settings.update { it.copy(playbackSpeed = preset) } },
                        label = { Text(speedLabel(preset)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(visible = !prefs.linkPitch) {
                Column {
                    val semitones = prefs.pitchSemitones.roundToInt()
                    ValueRow(
                        stringResource(R.string.pitch),
                        if (semitones == 0) "0" else stringResource(R.string.pitch_semitones, if (semitones > 0) "+$semitones" else "$semitones"),
                    )
                    Slider(
                        value = prefs.pitchSemitones,
                        onValueChange = { v -> settings.update { it.copy(pitchSemitones = v.roundToInt().toFloat()) } },
                        valueRange = -12f..12f,
                        steps = 23,
                    )
                }
            }
            AnimatedVisibility(visible = prefs.linkPitch) {
                Text(
                    stringResource(R.string.pitch_linked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { settings.update { it.copy(playbackSpeed = 1f, pitchSemitones = 0f) } },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.equalizer_reset))
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
