package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MicExternalOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.luzility.muuusic.playback.SleepTimer
import me.luzility.muuusic.playback.audio.Karaoke
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpeedPresets = listOf(0.75f, 1f, 1.25f, 1.5f)
private val SleepPresets = listOf(5, 10, 15, 30, 45, 60)

private fun speedLabel(speed: Float) = String.format(Locale.getDefault(), "%.2f", speed).trimEnd('0').trimEnd('.', ',') + "×"

@Composable
fun KaraokeButton() {
    val services = LocalServices.current
    val prefs by services.settings.prefs.collectAsState()
    val on by Karaoke.enabled.collectAsState()
    if (!prefs.karaoke) return
    IconToggleButton(checked = on, onCheckedChange = { Karaoke.set(it) }, shapes = IconButtonDefaults.toggleableShapes()) {
        Icon(Icons.Rounded.MicExternalOff, str("karaoke"))
    }
}

@Composable
fun SpeedButton() {
    val settings = LocalServices.current.settings
    val prefs by settings.prefs.collectAsState()
    var open by remember { mutableStateOf(false) }
    val changed = abs(prefs.playbackSpeed - 1f) > 0.001f || (!prefs.linkPitch && prefs.pitchSemitones != 0f)
    Box {
        TextButton(onClick = { open = true }) {
            Text(speedLabel(prefs.playbackSpeed), color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.width(320.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(str("speed_pitch"), style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.height(12.dp))
                ValueRow(str("speed"), speedLabel(prefs.playbackSpeed))
                Slider(
                    value = prefs.playbackSpeed,
                    onValueChange = { v -> settings.update { it.copy(playbackSpeed = (v * 20).roundToInt() / 20f) } },
                    valueRange = 0.5f..2f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpeedPresets.forEach { preset ->
                        FilterChip(
                            selected = abs(prefs.playbackSpeed - preset) < 0.001f,
                            onClick = { settings.update { it.copy(playbackSpeed = preset) } },
                            label = { Text(speedLabel(preset)) },
                            shape = CircleShape,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (prefs.linkPitch) {
                    Text(str("pitch_linked"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val semitones = prefs.pitchSemitones
                    val label = (if (semitones > 0) "+" else "") + String.format(Locale.getDefault(), "%.1f", semitones).removeSuffix(",0").removeSuffix(".0")
                    ValueRow(str("pitch"), Strings.plural("pitch_semitones", abs(semitones).roundToInt(), label))
                    Slider(
                        value = semitones,
                        onValueChange = { v -> settings.update { it.copy(pitchSemitones = (v * 2).roundToInt() / 2f) } },
                        valueRange = -12f..12f,
                    )
                }
                TextButton(onClick = { settings.update { it.copy(playbackSpeed = 1f, pitchSemitones = 0f) } }, modifier = Modifier.align(Alignment.End)) {
                    Text(str("reset"))
                }
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SleepTimerButton() {
    val player = LocalPlayer.current
    val endsAt by player.sleepEndsAt.collectAsState()
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt > 0) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Box {
        if (endsAt > 0) {
            TextButton(onClick = { open = true }) {
                Icon(Icons.Rounded.Bedtime, null, Modifier.padding(end = 6.dp))
                Text(if (endsAt == SleepTimer.ENDS_WITH_SONG) "♪" else formatDuration(endsAt - now))
            }
        } else {
            IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Bedtime, str("sleep_timer"))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            Text(str("sleep_timer"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SleepPresets.forEach { minutes ->
                DropdownMenuItem(text = { Text(str("sleep_minutes", minutes)) }, onClick = { player.setSleepTimer(minutes); open = false })
            }
            DropdownMenuItem(
                text = { Text(str("sleep_end_of_song")) },
                trailingIcon = { if (endsAt == SleepTimer.ENDS_WITH_SONG) Icon(Icons.Rounded.Check, null) },
                onClick = { player.setSleepTimer(SleepTimer.END_OF_SONG); open = false },
            )
            if (endsAt != 0L) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(str("sleep_cancel")) }, onClick = { player.setSleepTimer(0); open = false })
            }
        }
    }
}
