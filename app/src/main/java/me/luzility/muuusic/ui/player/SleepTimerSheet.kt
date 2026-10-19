package me.luzility.muuusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.luzility.muuusic.R
import me.luzility.muuusic.playback.SleepTimer
import me.luzility.muuusic.ui.components.formatDuration
import me.luzility.muuusic.ui.components.LocalPlayer

private val Presets = listOf(5, 10, 15, 30, 45, 60)

@Composable
fun SleepTimerButton(onClick: () -> Unit) {
    val player = LocalPlayer.current
    val endsAt by player.sleepEndsAt.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt > 0) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    AnimatedContent(targetState = endsAt != 0L, label = "sleepButton") { active ->
        if (!active) {
            IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Bedtime, stringResource(R.string.sleep_timer))
            }
        } else {
            FilledTonalButton(
                onClick = onClick,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.SmallContentPadding,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Icon(
                    if (endsAt == SleepTimer.ENDS_WITH_SONG) Icons.Rounded.MusicNote else Icons.Rounded.Bedtime,
                    stringResource(R.string.sleep_timer),
                    Modifier.size(ButtonDefaults.IconSize),
                )
                if (endsAt > 0) {
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(formatDuration(endsAt - now), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val endsAt by player.sleepEndsAt.collectAsStateWithLifecycle()

    fun choose(minutes: Int) {
        player.setSleepTimer(minutes)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                stringResource(R.string.sleep_timer_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.forEach { minutes ->
                    OutlinedButton(onClick = { choose(minutes) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.sleep_minutes, minutes))
                    }
                }
                FilledTonalButton(onClick = { choose(SleepTimer.END_OF_SONG) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.sleep_end_of_song))
                }
            }
            if (endsAt != 0L) {
                TextButton(onClick = { choose(0) }) {
                    Text(stringResource(R.string.sleep_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
