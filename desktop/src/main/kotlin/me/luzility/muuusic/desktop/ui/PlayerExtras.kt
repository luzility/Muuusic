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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MicExternalOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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

private enum class MenuPage { Main, Speed, Sleep }

@Composable
fun PlayerMenu() {
    val player = LocalPlayer.current
    val settings = LocalServices.current.settings
    val prefs by settings.prefs.collectAsState()
    val karaoke by Karaoke.enabled.collectAsState()
    val endsAt by player.sleepEndsAt.collectAsState()
    val openMiniPlayer = LocalMiniPlayer.current
    var open by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(MenuPage.Main) }
    val speedChanged = abs(prefs.playbackSpeed - 1f) > 0.001f || (!prefs.linkPitch && prefs.pitchSemitones != 0f)
    val karaokeOn = prefs.karaoke && karaoke
    val close = { open = false }
    Box {
        IconButton(onClick = { page = MenuPage.Main; open = true }, shapes = IconButtonDefaults.shapes()) {
            BadgedBox(badge = { if (speedChanged || karaokeOn || endsAt != 0L) Badge() }) {
                Icon(Icons.Rounded.MoreVert, str("more"))
            }
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = close) {
            when (page) {
                MenuPage.Main -> {
                    val count = if (prefs.karaoke) 4 else 3
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                        DropdownMenuItem(
                            selected = false,
                            onClick = { page = MenuPage.Speed },
                            text = { Text(str("speed_pitch")) },
                            shapes = MenuDefaults.itemShape(0, count),
                            leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                            trailingIcon = { Text(speedLabel(prefs.playbackSpeed), color = if (speedChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                        if (prefs.karaoke) {
                            DropdownMenuItem(
                                checked = karaoke,
                                onCheckedChange = { Karaoke.set(it) },
                                text = { Text(str("karaoke")) },
                                shapes = MenuDefaults.itemShape(1, count),
                                leadingIcon = { Icon(Icons.Rounded.MicExternalOff, null) },
                            )
                        }
                        DropdownMenuItem(
                            selected = false,
                            onClick = { page = MenuPage.Sleep },
                            text = { Text(str("sleep_timer")) },
                            shapes = MenuDefaults.itemShape(count - 2, count),
                            leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                            trailingIcon = { if (endsAt != 0L) SleepRemaining(endsAt) },
                        )
                        DropdownMenuItem(
                            selected = false,
                            onClick = { close(); openMiniPlayer() },
                            text = { Text(str("mini_player")) },
                            shapes = MenuDefaults.itemShape(count - 1, count),
                            leadingIcon = { Icon(Icons.Rounded.PictureInPictureAlt, null) },
                        )
                    }
                    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                        DropdownMenuItem(
                            selected = false,
                            onClick = { close(); player.dismiss() },
                            text = { Text(str("close_player")) },
                            shapes = MenuDefaults.itemShape(0, 1),
                            leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        )
                    }
                }
                MenuPage.Speed -> DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                    MenuPageHeader(str("speed_pitch")) { page = MenuPage.Main }
                    SpeedPitchControls()
                }
                MenuPage.Sleep -> DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                    MenuPageHeader(str("sleep_timer")) { page = MenuPage.Main }
                    val options = SleepPresets + SleepTimer.END_OF_SONG
                    val count = options.size + if (endsAt != 0L) 1 else 0
                    options.forEachIndexed { index, minutes ->
                        DropdownMenuItem(
                            selected = minutes == SleepTimer.END_OF_SONG && endsAt == SleepTimer.ENDS_WITH_SONG,
                            onClick = { player.setSleepTimer(minutes); close() },
                            text = { Text(if (minutes == SleepTimer.END_OF_SONG) str("sleep_end_of_song") else str("sleep_minutes", minutes)) },
                            shapes = MenuDefaults.itemShape(index, count),
                        )
                    }
                    if (endsAt != 0L) {
                        DropdownMenuItem(
                            selected = false,
                            onClick = { player.setSleepTimer(0); close() },
                            text = { Text(str("sleep_cancel")) },
                            shapes = MenuDefaults.itemShape(count - 1, count),
                            leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("back"))
        }
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun SleepRemaining(endsAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        if (endsAt == SleepTimer.ENDS_WITH_SONG) "♪" else formatDuration(endsAt - now),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SpeedPitchControls() {
    val settings = LocalServices.current.settings
    val prefs by settings.prefs.collectAsState()
    Column(Modifier.width(320.dp).padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
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

@Composable
private fun ValueRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
