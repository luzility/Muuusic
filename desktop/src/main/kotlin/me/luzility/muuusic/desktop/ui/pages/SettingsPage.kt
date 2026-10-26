package me.luzility.muuusic.desktop.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.backup.InvalidBackupException
import me.luzility.muuusic.data.lyrics.LyricsAlign
import me.luzility.muuusic.data.lyrics.LyricsTextSize
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.data.settings.AudioQuality
import me.luzility.muuusic.data.settings.ThemeMode
import me.luzility.muuusic.data.update.AppRelease
import me.luzility.muuusic.data.update.UpdateChannel
import me.luzility.muuusic.data.update.Versions
import me.luzility.muuusic.desktop.playback.DesktopEqualizer
import me.luzility.muuusic.desktop.ui.LocalServices
import me.luzility.muuusic.desktop.ui.LocalSnackbar
import me.luzility.muuusic.desktop.ui.logoPainter
import me.luzility.muuusic.desktop.ui.str
import me.luzility.muuusic.playback.audio.Karaoke
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

private enum class Category(val title: String, val summary: String, val icon: ImageVector, val colorIndex: Int) {
    Appearance("settings_appearance", "settings_appearance_summary", Icons.Rounded.Palette, 0),
    Playback("settings_playback", "settings_playback_summary", Icons.Rounded.PlayCircle, 1),
    Sound("settings_sound", "settings_sound_summary", Icons.Rounded.GraphicEq, 2),
    Lyrics("lyrics", "settings_lyrics_summary", Icons.Rounded.Lyrics, 3),
    Storage("settings_storage", "settings_storage_summary", Icons.Rounded.Storage, 4),
    Backup("settings_backup", "settings_backup_summary", Icons.Rounded.Backup, 5),
    Lab("settings_lab", "settings_lab_summary", Icons.Rounded.Science, 7),
    Updates("settings_updates", "settings_updates_summary", Icons.Rounded.SystemUpdate, 8),
    About("settings_about", "settings_about_summary", Icons.Rounded.Info, 9),
}

private val sections = listOf(
    listOf(Category.Appearance, Category.Playback, Category.Sound, Category.Lyrics),
    listOf(Category.Storage, Category.Backup),
    listOf(Category.Lab, Category.Updates, Category.About),
)

private val index = listOf(
    "settings_theme" to Category.Appearance,
    "settings_artwork_colors" to Category.Appearance,
    "settings_pure_black" to Category.Appearance,
    "settings_audio_quality" to Category.Playback,
    "settings_autoplay" to Category.Playback,
    "settings_sponsorblock" to Category.Playback,
    "settings_normalize" to Category.Sound,
    "settings_crossfade" to Category.Sound,
    "settings_link_pitch" to Category.Sound,
    "equalizer" to Category.Sound,
    "lyrics_text_size" to Category.Lyrics,
    "lyrics_alignment" to Category.Lyrics,
    "lyrics_blur" to Category.Lyrics,
    "lyrics_translate" to Category.Lyrics,
    "settings_cache_size" to Category.Storage,
    "settings_clear_cache" to Category.Storage,
    "settings_clear_history" to Category.Storage,
    "settings_backup_create" to Category.Backup,
    "settings_backup_restore" to Category.Backup,
    "settings_karaoke" to Category.Lab,
    "settings_karaoke_strength" to Category.Lab,
    "settings_check_updates" to Category.Updates,
    "settings_update_channel" to Category.Updates,
    "settings_source" to Category.About,
    "settings_report" to Category.About,
    "settings_license" to Category.About,
)

private val CategoryShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.SoftBurst,
)

private val CategoryColors = listOf(
    Color(0xFFFFB1C8) to Color(0xFF5E1133),
    Color(0xFFA8C7FA) to Color(0xFF062E6F),
    Color(0xFFD0BCFF) to Color(0xFF381E72),
    Color(0xFFFFB68A) to Color(0xFF552100),
    Color(0xFFA6D6A8) to Color(0xFF0B3913),
    Color(0xFF80D5D1) to Color(0xFF003735),
    Color(0xFFF6D776) to Color(0xFF3B2F00),
    Color(0xFFC5E29A) to Color(0xFF233600),
    Color(0xFF9ECAFF) to Color(0xFF003258),
    Color(0xFFE0C3F0) to Color(0xFF3E1F4C),
)

private sealed interface Pane {
    data class Section(val category: Category) : Pane
    data object Equalizer : Pane
}

@Composable
fun SettingsPage() {
    var pane by remember { mutableStateOf<Pane>(Pane.Section(Category.Appearance)) }
    var query by remember { mutableStateOf("") }
    val selected = when (val current = pane) {
        is Pane.Section -> current.category
        Pane.Equalizer -> Category.Sound
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(360.dp).fillMaxHeight(), contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp)) {
            item {
                Text(str("settings"), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            }
            item { SettingsSearch(query) { query = it } }
            if (query.isBlank()) {
                sections.forEach { section ->
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsGroup(
                            *section.map { category ->
                                @Composable { CategoryRow(category, selected == category) { pane = Pane.Section(category) } }
                            }.toTypedArray(),
                        )
                    }
                }
            } else {
                item { SearchResults(query) { category -> pane = Pane.Section(category) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(targetState = pane, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "settingsPane") { current ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp, end = 24.dp)) {
                    when (current) {
                        is Pane.Section -> {
                            item {
                                Text(
                                    str(current.category.title),
                                    style = MaterialTheme.typography.headlineMediumEmphasized,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 16.dp),
                                )
                            }
                            item {
                                Box(Modifier.widthIn(max = 760.dp)) {
                                    CategoryContent(current.category, onOpenEqualizer = { pane = Pane.Equalizer })
                                }
                            }
                        }
                        Pane.Equalizer -> item {
                            Box(Modifier.widthIn(max = 760.dp)) { EqualizerPane(onBack = { pane = Pane.Section(Category.Sound) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryContent(category: Category, onOpenEqualizer: () -> Unit) {
    val services = LocalServices.current
    val settings = services.settings
    val prefs by settings.prefs.collectAsState()
    val lyricsPrefs by services.lyricsSettings.prefs.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    when (category) {
        Category.Appearance -> SettingsGroup(
            {
                ChoiceSetting(
                    title = str("settings_theme"),
                    summary = null,
                    options = ThemeMode.entries,
                    selected = prefs.themeMode,
                    label = { str(when (it) { ThemeMode.System -> "theme_system"; ThemeMode.Light -> "theme_light"; ThemeMode.Dark -> "theme_dark" }) },
                    onSelect = { mode -> settings.update { it.copy(themeMode = mode) } },
                )
            },
            { SwitchSetting(str("settings_artwork_colors"), str("settings_artwork_colors_summary"), prefs.artworkColors) { on -> settings.update { it.copy(artworkColors = on) } } },
            { SwitchSetting(str("settings_pure_black"), str("settings_pure_black_summary"), prefs.pureBlack) { on -> settings.update { it.copy(pureBlack = on) } } },
        )
        Category.Playback -> SettingsGroup(
            {
                ChoiceSetting(
                    title = str("settings_audio_quality"),
                    summary = str("settings_audio_quality_summary"),
                    options = AudioQuality.entries,
                    selected = prefs.audioQuality,
                    label = { str(when (it) { AudioQuality.High -> "quality_high"; AudioQuality.Normal -> "quality_normal"; AudioQuality.Low -> "quality_low" }) },
                    onSelect = { quality -> settings.update { it.copy(audioQuality = quality) } },
                )
            },
            { SwitchSetting(str("settings_autoplay"), str("settings_autoplay_summary"), prefs.autoplay) { on -> settings.update { it.copy(autoplay = on) } } },
            { SwitchSetting(str("settings_sponsorblock"), str("settings_sponsorblock_summary"), prefs.sponsorBlock) { on -> settings.update { it.copy(sponsorBlock = on) } } },
        )
        Category.Sound -> SettingsGroup(
            { SwitchSetting(str("settings_normalize"), str("settings_normalize_summary"), prefs.normalizeVolume) { on -> settings.update { it.copy(normalizeVolume = on) } } },
            {
                ChoiceSetting(
                    title = str("settings_crossfade"),
                    summary = str("settings_crossfade_summary"),
                    options = AppSettings.FadeSeconds,
                    selected = prefs.fadeSeconds,
                    label = { s -> if (s == 0) str("off") else str("seconds_short", s) },
                    onSelect = { s -> settings.update { it.copy(fadeSeconds = s) } },
                )
            },
            { SwitchSetting(str("settings_link_pitch"), str("settings_link_pitch_summary"), prefs.linkPitch) { on -> settings.update { it.copy(linkPitch = on) } } },
            { ActionSetting(str("equalizer"), str(if (prefs.equalizerEnabled) "equalizer_on" else "equalizer_summary"), onOpenEqualizer) },
        )
        Category.Lyrics -> SettingsGroup(
            {
                ChoiceSetting(
                    title = str("lyrics_text_size"),
                    summary = null,
                    options = LyricsTextSize.entries,
                    selected = lyricsPrefs.textSize,
                    label = { str(when (it) { LyricsTextSize.Small -> "size_small"; LyricsTextSize.Medium -> "size_medium"; LyricsTextSize.Large -> "size_large" }) },
                    onSelect = { size -> services.lyricsSettings.update { it.copy(textSize = size) } },
                )
            },
            {
                ChoiceSetting(
                    title = str("lyrics_alignment"),
                    summary = null,
                    options = LyricsAlign.entries,
                    selected = lyricsPrefs.align,
                    label = { str(if (it == LyricsAlign.Start) "align_start" else "align_center") },
                    onSelect = { align -> services.lyricsSettings.update { it.copy(align = align) } },
                )
            },
            { SwitchSetting(str("lyrics_blur"), null, lyricsPrefs.blur) { on -> services.lyricsSettings.update { it.copy(blur = on) } } },
            { SwitchSetting(str("lyrics_translate"), str("lyrics_translate_summary"), lyricsPrefs.translate) { on -> services.lyricsSettings.update { it.copy(translate = on) } } },
        )
        Category.Storage -> StorageSettings()
        Category.Backup -> SettingsGroup(
            {
                ActionSetting(str("settings_backup_create"), str("settings_backup_create_summary")) {
                    val file = pickFile(FileDialog.SAVE, "muuusic-${LocalDate.now()}.json") ?: return@ActionSetting
                    scope.launch {
                        val ok = runCatching { withContext(Dispatchers.IO) { file.outputStream().use { services.backup.export(it) } } }.isSuccess
                        snackbar.showSnackbar(str(if (ok) "backup_saved" else "backup_failed"))
                    }
                }
            },
            {
                ActionSetting(str("settings_backup_restore"), str("settings_backup_restore_summary")) {
                    val file = pickFile(FileDialog.LOAD, null) ?: return@ActionSetting
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { file.inputStream().use { services.backup.restore(it) } } }
                        settings.reload()
                        services.lyricsSettings.reload()
                        services.searchHistory.reload()
                        val message = when (result.exceptionOrNull()) {
                            null -> "backup_restored"
                            is InvalidBackupException -> "backup_invalid"
                            else -> "backup_restore_failed"
                        }
                        snackbar.showSnackbar(str(message))
                    }
                }
            },
        )
        Category.Lab -> SettingsGroup(
            { SwitchSetting(str("settings_karaoke"), str("settings_karaoke_summary"), prefs.karaoke) { on -> settings.update { it.copy(karaoke = on) } } },
            {
                ChoiceSetting(
                    title = str("settings_karaoke_strength"),
                    summary = str("settings_karaoke_strength_summary"),
                    options = Karaoke.strengths,
                    selected = prefs.karaokeStrength,
                    label = { "%${(it * 100).toInt()}" },
                    onSelect = { value -> settings.update { it.copy(karaokeStrength = value) } },
                )
            },
        )
        Category.Updates -> UpdateSettings()
        Category.About -> Column {
            AboutCard()
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                { ActionSetting("Luzi", str("settings_developer_summary")) { uri.openUri("https://github.com/luzility") } },
                { ActionSetting(str("settings_source"), "github.com/luzility/Muuusic") { uri.openUri("https://github.com/luzility/Muuusic") } },
                { ActionSetting(str("settings_report"), str("settings_report_summary")) { uri.openUri("https://github.com/luzility/Muuusic/issues/new") } },
                { ActionSetting(str("settings_license"), str("settings_license_summary")) { uri.openUri("https://www.gnu.org/licenses/gpl-3.0.html") } },
            )
        }
    }
}

@Composable
private fun StorageSettings() {
    val services = LocalServices.current
    val settings = services.settings
    val prefs by settings.prefs.collectAsState()
    val scope = rememberCoroutineScope()
    var cacheUsed by remember { mutableLongStateOf(-1L) }
    var confirmHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cacheUsed = withContext(Dispatchers.IO) { services.streamCache.bytesUsed() } }
    SettingsGroup(
        {
            ChoiceSetting(
                title = str("settings_cache_size"),
                summary = str("settings_cache_used", if (cacheUsed >= 0) formatBytes(cacheUsed) else "…"),
                options = AppSettings.CacheSizesMb,
                selected = prefs.cacheSizeMb,
                label = { mb -> if (mb >= 1024) "${mb / 1024} GB" else "$mb MB" },
                onSelect = { mb -> settings.update { it.copy(cacheSizeMb = mb) } },
            )
        },
        {
            ActionSetting(str("settings_clear_cache"), str("settings_clear_cache_summary")) {
                scope.launch {
                    withContext(Dispatchers.IO) { services.streamCache.clear() }
                    cacheUsed = withContext(Dispatchers.IO) { services.streamCache.bytesUsed() }
                }
            }
        },
        { ActionSetting(str("settings_clear_history"), str("settings_clear_history_summary")) { confirmHistory = true } },
    )
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

private sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
}

@Composable
private fun UpdateSettings() {
    val services = LocalServices.current
    val settings = services.settings
    val prefs by settings.prefs.collectAsState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var check by remember { mutableStateOf<UpdateCheck>(UpdateCheck.Idle) }
    SettingsGroup(
        {
            val current = check
            ActionSetting(
                title = str("settings_check_updates"),
                summary = when (current) {
                    UpdateCheck.Checking -> str("update_checking")
                    UpdateCheck.UpToDate -> str("update_up_to_date", AppInfo.versionName)
                    UpdateCheck.Failed -> str("update_failed")
                    is UpdateCheck.Available -> str("update_ready", current.release.version)
                    UpdateCheck.Idle -> str("settings_version", AppInfo.versionName)
                },
            ) {
                if (current is UpdateCheck.Available) {
                    uri.openUri(current.release.pageUrl)
                    return@ActionSetting
                }
                if (current == UpdateCheck.Checking) return@ActionSetting
                check = UpdateCheck.Checking
                scope.launch {
                    check = runCatching { services.updates.newerThan(AppInfo.versionName, prefs.updateChannel) }.fold(
                        onSuccess = { release -> release?.let(UpdateCheck::Available) ?: UpdateCheck.UpToDate },
                        onFailure = { UpdateCheck.Failed },
                    )
                }
            }
        },
        {
            ChoiceSetting(
                title = str("settings_update_channel"),
                summary = str("settings_update_channel_summary"),
                options = UpdateChannel.entries,
                selected = prefs.updateChannel,
                label = { str(if (it == UpdateChannel.Release) "channel_release" else "channel_beta") },
                onSelect = { channel -> settings.update { it.copy(updateChannel = channel) } },
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerPane(onBack: () -> Unit) {
    val settings = LocalServices.current.settings
    val prefs by settings.prefs.collectAsState()
    Column {
        Row(Modifier.padding(start = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("back"))
            }
            Spacer(Modifier.width(12.dp))
            Text(str("equalizer"), style = MaterialTheme.typography.headlineMediumEmphasized)
        }
        Card {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(str("equalizer_enable"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = prefs.equalizerEnabled, onCheckedChange = { on -> settings.update { it.copy(equalizerEnabled = on) } })
            }
        }
        val dim = if (prefs.equalizerEnabled) 1f else 0.5f
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).alpha(dim), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopEqualizer.presets.forEachIndexed { index, (name, levels) ->
                val selected = prefs.equalizerPreset == index
                FilterChip(
                    selected = selected,
                    onClick = { settings.update { it.copy(equalizerBands = levels, equalizerPreset = index, equalizerEnabled = true) } },
                    label = { Text(name) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
        Card(Modifier.alpha(dim)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                DesktopEqualizer.bandFrequenciesHz.forEachIndexed { band, hz ->
                    val level = prefs.equalizerBands.getOrNull(band) ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(frequencyLabel(hz), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { v ->
                                settings.update { current ->
                                    val bands = List(DesktopEqualizer.bandFrequenciesHz.size) { current.equalizerBands.getOrNull(it) ?: 0 }.toMutableList()
                                    bands[band] = v.roundToInt()
                                    current.copy(equalizerBands = bands, equalizerPreset = -1)
                                }
                            },
                            valueRange = DesktopEqualizer.MIN_LEVEL.toFloat()..DesktopEqualizer.MAX_LEVEL.toFloat(),
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
        Card(Modifier.padding(top = 2.dp).alpha(dim)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(str("equalizer_bass_boost"), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = prefs.bassBoost.toFloat(),
                    onValueChange = { v -> settings.update { it.copy(bassBoost = v.roundToInt()) } },
                    valueRange = 0f..1000f,
                )
            }
        }
        TextButton(
            onClick = { settings.update { it.copy(equalizerBands = emptyList(), equalizerPreset = -1, bassBoost = 0) } },
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        ) { Text(str("equalizer_reset")) }
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

@Composable
private fun AboutCard() {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 28.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(Color(0xFF7C4DFF))) {
                Image(
                    logoPainter(),
                    null,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.5f
                        scaleY = 1.5f
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Muuusic", style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(
                str("about_tagline"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                    Text(
                        str("settings_version", AppInfo.versionName),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Surface(shape = CircleShape, color = colors.surface.copy(alpha = 0.5f)) {
                    Text(
                        str(if (Versions.isBeta(AppInfo.versionName)) "channel_beta" else "channel_release"),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSearch(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(str("settings_search")) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, str("clear")) }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SearchResults(query: String, onOpen: (Category) -> Unit) {
    val needle = query.trim().lowercase(Locale.getDefault())
    val results = remember(needle) {
        index.filter { (title, category) ->
            str(title).lowercase(Locale.getDefault()).contains(needle) || str(category.title).lowercase(Locale.getDefault()).contains(needle)
        }
    }
    if (results.isEmpty()) {
        Text(
            str("settings_search_empty"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    Spacer(Modifier.height(8.dp))
    SettingsGroup(
        *results.map { (title, category) ->
            @Composable {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(category) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(category)
                    Spacer(Modifier.width(16.dp))
                    SettingText(str(title), str(category.title), Modifier.weight(1f))
                }
            }
        }.toTypedArray(),
    )
}

@Composable
private fun CategoryIcon(category: Category) {
    val (container, content) = CategoryColors[category.colorIndex % CategoryColors.size]
    val shape = CategoryShapes[category.colorIndex % CategoryShapes.size].toShape()
    Box(Modifier.size(44.dp).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(category.icon, null, tint = content, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CategoryRow(category: Category, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category)
        Spacer(Modifier.width(16.dp))
        SettingText(str(category.title), str(category.summary), Modifier.weight(1f))
    }
}

@Composable
private fun SettingsGroup(vararg items: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, content ->
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == items.lastIndex) 24.dp else 6.dp
            Surface(
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) { content() }
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
private fun SwitchSetting(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(title, summary, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSetting(title: String, summary: String?, onClick: () -> Unit) {
    SettingText(title, summary, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp))
}

@Composable
private fun <T> ChoiceSetting(title: String, summary: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        SettingText(title, summary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
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
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text(label(option), maxLines = 1) }
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
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
