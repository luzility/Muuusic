package me.luzility.muuusic.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.Util
import kotlinx.coroutines.flow.StateFlow
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.settings.AppPrefs
import me.luzility.muuusic.playback.audio.AudioEffects
import me.luzility.muuusic.playback.audio.EqualizerLayout
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import java.util.Locale
import kotlin.math.roundToInt

class EqualizerViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = app.container.settings
    val prefs: StateFlow<AppPrefs> = settings.prefs

    // The service publishes the real layout; before playback a throwaway equalizer gives the same info.
    val layout: EqualizerLayout? = app.container.equalizerLayout.value ?: run {
        val probe = AudioEffects(Util.generateAudioSessionIdV21(app))
        probe.layout.also { probe.release() }
    }

    fun update(transform: (AppPrefs) -> AppPrefs) = settings.update(transform)

    fun setBand(band: Int, level: Int, bandCount: Int) = update { prefs ->
        val bands = List(bandCount) { prefs.equalizerBands.getOrNull(it) ?: 0 }.toMutableList()
        bands[band] = level
        prefs.copy(equalizerBands = bands, equalizerPreset = -1)
    }

    fun usePreset(index: Int) {
        val levels = layout?.presetLevels?.getOrNull(index) ?: return
        update { it.copy(equalizerBands = levels, equalizerPreset = index, equalizerEnabled = true) }
    }

    fun reset() = update { it.copy(equalizerBands = emptyList(), equalizerPreset = -1, bassBoost = 0) }
}

@Composable
fun EqualizerScreen(onBack: () -> Unit, vm: EqualizerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val layout = vm.layout

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerInset.current)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
        }
        item {
            Text(
                stringResource(R.string.equalizer),
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        if (layout == null) {
            item {
                Text(
                    stringResource(R.string.equalizer_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            return@LazyColumn
        }

        item {
            Card(Modifier.padding(top = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.equalizer_enable), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = prefs.equalizerEnabled, onCheckedChange = { on -> vm.update { it.copy(equalizerEnabled = on) } })
                }
            }
        }

        val dim = if (prefs.equalizerEnabled) 1f else 0.5f

        if (layout.presetNames.isNotEmpty()) {
            item {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).alpha(dim),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    layout.presetNames.forEachIndexed { index, name ->
                        val selected = prefs.equalizerPreset == index
                        FilterChip(
                            selected = selected,
                            onClick = { vm.usePreset(index) },
                            label = { Text(name) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, null, Modifier.padding(0.dp).width(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.alpha(dim)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    layout.bandFrequenciesHz.forEachIndexed { band, hz ->
                        val level = prefs.equalizerBands.getOrNull(band) ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(frequencyLabel(hz), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { vm.setBand(band, it.roundToInt(), layout.bandFrequenciesHz.size) },
                                valueRange = layout.minLevel.toFloat()..layout.maxLevel.toFloat(),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                String.format(Locale.getDefault(), "%+.1f", level / 100f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(48.dp),
                            )
                        }
                    }
                }
            }
        }

        if (layout.bassBoostSupported) {
            item {
                Card(Modifier.padding(top = 2.dp).alpha(dim)) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(stringResource(R.string.equalizer_bass_boost), style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = prefs.bassBoost.toFloat(),
                            onValueChange = { v -> vm.update { it.copy(bassBoost = v.roundToInt()) } },
                            valueRange = 0f..1000f,
                        )
                    }
                }
            }
        }

        item {
            TextButton(onClick = vm::reset, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                Text(stringResource(R.string.equalizer_reset))
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        content = content,
    )
}

private fun frequencyLabel(hz: Int): String =
    if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz % 1f == 0f) "${khz.toInt()} kHz" else String.format(Locale.getDefault(), "%.1f kHz", khz)
    } else "$hz Hz"
