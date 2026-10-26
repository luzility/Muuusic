package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.backup.InvalidBackupException
import me.luzility.muuusic.data.lyrics.LyricsTextSize
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.data.settings.AudioQuality
import me.luzility.muuusic.data.settings.ThemeMode
import me.luzility.muuusic.desktop.playback.DesktopEqualizer
import me.luzility.muuusic.desktop.ui.LocalServices
import me.luzility.muuusic.desktop.ui.LocalSnackbar
import me.luzility.muuusic.desktop.ui.str
import me.luzility.muuusic.playback.audio.Karaoke
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsPage() {
    val services = LocalServices.current
    val settings = services.settings
    val prefs by settings.prefs.collectAsState()
    val lyricsPrefs by services.lyricsSettings.prefs.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var cacheUsed by remember { mutableLongStateOf(-1L) }
    var confirmHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cacheUsed = withContext(Dispatchers.IO) { services.streamCache.bytesUsed() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 48.dp)) {
        item {
            Text(str("settings"), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.padding(bottom = 8.dp))
        }

        section("settings_appearance") {
            Choice(
                title = str("settings_theme"),
                options = ThemeMode.entries,
                selected = prefs.themeMode,
                label = { str(when (it) { ThemeMode.System -> "theme_system"; ThemeMode.Light -> "theme_light"; ThemeMode.Dark -> "theme_dark" }) },
                onSelect = { mode -> settings.update { it.copy(themeMode = mode) } },
            )
            Toggle(str("settings_artwork_colors"), str("settings_artwork_colors_summary"), prefs.artworkColors) { on -> settings.update { it.copy(artworkColors = on) } }
            Toggle(str("settings_pure_black"), str("settings_pure_black_summary"), prefs.pureBlack) { on -> settings.update { it.copy(pureBlack = on) } }
        }

        section("settings_playback") {
            Choice(
                title = str("settings_audio_quality"),
                summary = str("settings_audio_quality_summary"),
                options = AudioQuality.entries,
                selected = prefs.audioQuality,
                label = { str(when (it) { AudioQuality.High -> "quality_high"; AudioQuality.Normal -> "quality_normal"; AudioQuality.Low -> "quality_low" }) },
                onSelect = { quality -> settings.update { it.copy(audioQuality = quality) } },
            )
            Toggle(str("settings_autoplay"), str("settings_autoplay_summary"), prefs.autoplay) { on -> settings.update { it.copy(autoplay = on) } }
            Toggle(str("settings_sponsorblock"), str("settings_sponsorblock_summary"), prefs.sponsorBlock) { on -> settings.update { it.copy(sponsorBlock = on) } }
        }

        section("settings_sound") {
            Toggle(str("settings_normalize"), str("settings_normalize_summary"), prefs.normalizeVolume) { on -> settings.update { it.copy(normalizeVolume = on) } }
            Choice(
                title = str("settings_crossfade"),
                summary = str("settings_crossfade_summary"),
                options = AppSettings.FadeSeconds,
                selected = prefs.fadeSeconds,
                label = { s -> if (s == 0) str("off") else str("seconds_short", s) },
                onSelect = { s -> settings.update { it.copy(fadeSeconds = s) } },
            )
            Toggle(str("settings_link_pitch"), str("settings_link_pitch_summary"), prefs.linkPitch) { on -> settings.update { it.copy(linkPitch = on) } }
            Equalizer()
        }

        section("lyrics") {
            Choice(
                title = str("lyrics_text_size"),
                options = LyricsTextSize.entries,
                selected = lyricsPrefs.textSize,
                label = { str(when (it) { LyricsTextSize.Small -> "size_small"; LyricsTextSize.Medium -> "size_medium"; LyricsTextSize.Large -> "size_large" }) },
                onSelect = { size -> services.lyricsSettings.update { it.copy(textSize = size) } },
            )
            Toggle(str("lyrics_translate"), str("lyrics_translate_summary"), lyricsPrefs.translate) { on -> services.lyricsSettings.update { it.copy(translate = on) } }
        }

        section("settings_storage") {
            Choice(
                title = str("settings_cache_size"),
                summary = str("settings_cache_used", if (cacheUsed >= 0) formatBytes(cacheUsed) else "…"),
                options = AppSettings.CacheSizesMb,
                selected = prefs.cacheSizeMb,
                label = { mb -> if (mb >= 1024) "${mb / 1024} GB" else "$mb MB" },
                onSelect = { mb -> settings.update { it.copy(cacheSizeMb = mb) } },
            )
            Action(str("settings_clear_cache"), str("settings_clear_cache_summary")) {
                scope.launch {
                    withContext(Dispatchers.IO) { services.streamCache.clear() }
                    cacheUsed = withContext(Dispatchers.IO) { services.streamCache.bytesUsed() }
                }
            }
            Action(str("settings_clear_history"), str("settings_clear_history_summary")) { confirmHistory = true }
        }

        section("settings_backup") {
            Action(str("settings_backup_create"), str("settings_backup_create_summary")) {
                val file = pickFile(FileDialog.SAVE, "muuusic-${LocalDate.now()}.json") ?: return@Action
                scope.launch {
                    val ok = runCatching { withContext(Dispatchers.IO) { file.outputStream().use { services.backup.export(it) } } }.isSuccess
                    snackbar.showSnackbar(str(if (ok) "backup_saved" else "backup_failed"))
                }
            }
            Action(str("settings_backup_restore"), str("settings_backup_restore_summary")) {
                val file = pickFile(FileDialog.LOAD, null) ?: return@Action
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { file.inputStream().use { services.backup.restore(it) } } }
                    settings.reload()
                    services.lyricsSettings.reload()
                    services.searchHistory.reload()
                    snackbar.showSnackbar(
                        str(
                            when (result.exceptionOrNull()) {
                                null -> "backup_restored"
                                is InvalidBackupException -> "backup_invalid"
                                else -> "backup_restore_failed"
                            },
                        ),
                    )
                }
            }
        }

        section("settings_lab") {
            Toggle(str("settings_karaoke"), str("settings_karaoke_summary"), prefs.karaoke) { on -> settings.update { it.copy(karaoke = on) } }
            Choice(
                title = str("settings_karaoke_strength"),
                summary = str("settings_karaoke_strength_summary"),
                options = Karaoke.strengths,
                selected = prefs.karaokeStrength,
                label = { "%${(it * 100).toInt()}" },
                onSelect = { value -> settings.update { it.copy(karaokeStrength = value) } },
            )
        }

        section("settings_about") {
            Action("Muuusic", str("settings_version", AppInfo.versionName)) {}
            Action("Luzi", str("settings_developer_summary")) { uri.openUri("https://github.com/luzility") }
            Action(str("settings_source"), "github.com/luzility/Muuusic") { uri.openUri("https://github.com/luzility/Muuusic") }
            Action(str("settings_report"), str("settings_report_summary")) { uri.openUri("https://github.com/luzility/Muuusic/issues/new") }
            Action(str("settings_license"), str("settings_license_summary")) { uri.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }
        }
    }

    if (confirmHistory) {
        AlertDialog(
            onDismissRequest = { confirmHistory = false },
            title = { Text(str("settings_clear_history")) },
            text = { Text(str("settings_clear_history_confirm")) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { services.library.clearHistory() }
                    confirmHistory = false
                }) { Text(str("clear")) }
            },
            dismissButton = { TextButton(onClick = { confirmHistory = false }) { Text(str("cancel")) } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(titleKey: String, content: @Composable () -> Unit) {
    item(key = titleKey) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Text(
                str(titleKey),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp),
            )
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(vertical = 6.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SettingText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(title, summary, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Action(title: String, summary: String?, onClick: () -> Unit) {
    SettingText(title, summary, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp))
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, summary: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        SettingText(title, summary)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.widthIn(min = 96.dp),
                ) { Text(label(option), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Equalizer() {
    val settings = LocalServices.current.settings
    val prefs by settings.prefs.collectAsState()
    Toggle(str("equalizer"), str(if (prefs.equalizerEnabled) "equalizer_on" else "equalizer_summary"), prefs.equalizerEnabled) { on ->
        settings.update { it.copy(equalizerEnabled = on) }
    }
    AnimatedVisibility(prefs.equalizerEnabled) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopEqualizer.presets.forEachIndexed { index, (name, levels) ->
                    FilterChip(
                        selected = prefs.equalizerPreset == index,
                        onClick = { settings.update { it.copy(equalizerBands = levels, equalizerPreset = index) } },
                        label = { Text(name) },
                        shape = CircleShape,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DesktopEqualizer.bandFrequenciesHz.forEachIndexed { band, hz ->
                val level = prefs.equalizerBands.getOrNull(band) ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { v ->
                            settings.update { current ->
                                val bands = List(DesktopEqualizer.bandFrequenciesHz.size) { current.equalizerBands.getOrNull(it) ?: 0 }.toMutableList()
                                bands[band] = (v / 50).roundToInt() * 50
                                current.copy(equalizerBands = bands, equalizerPreset = -1)
                            }
                        },
                        valueRange = DesktopEqualizer.MIN_LEVEL.toFloat()..DesktopEqualizer.MAX_LEVEL.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text("%+.1f".format(level / 100f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp).padding(start = 8.dp))
                }
            }
            Text(str("equalizer_bass_boost"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = prefs.bassBoost.toFloat(),
                onValueChange = { v -> settings.update { it.copy(bassBoost = v.roundToInt()) } },
                valueRange = 0f..1000f,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { settings.update { it.copy(equalizerBands = emptyList(), equalizerPreset = -1, bassBoost = 0) } }) {
                    Text(str("equalizer_reset"))
                }
            }
        }
    }
}

private fun pickFile(mode: Int, name: String?): File? {
    val dialog = FileDialog(null as Frame?, "Muuusic", mode)
    if (name != null) dialog.file = name
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    val units: CharacterIterator = StringCharacterIterator("KMGT")
    value /= 1024
    while (value >= 1024 && units.current() != 'T') {
        value /= 1024
        units.next()
    }
    return "%.1f %cB".format(value, units.current())
}
