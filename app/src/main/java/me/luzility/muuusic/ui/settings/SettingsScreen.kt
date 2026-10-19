package me.luzility.muuusic.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Science
import android.app.Application
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.luzility.muuusic.BuildConfig
import me.luzility.muuusic.R
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.container
import me.luzility.muuusic.data.settings.AppPrefs
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.data.settings.AudioQuality
import me.luzility.muuusic.data.settings.ThemeMode

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val settings = container.settings
    val prefs: StateFlow<AppPrefs> = settings.prefs
    val startupCacheSizeMb = settings.startupCacheSizeMb

    private val _cacheUsed = MutableStateFlow(-1L)
    val cacheUsed = _cacheUsed.asStateFlow()

    private val _downloadsUsed = MutableStateFlow(-1L)
    val downloadsUsed = _downloadsUsed.asStateFlow()

    init {
        refreshCacheUsage()
    }

    fun update(transform: (AppPrefs) -> AppPrefs) = settings.update(transform)

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.clearCache() }
            refreshCacheUsage()
        }
    }

    fun setDownloadWifiOnly(wifiOnly: Boolean) {
        settings.update { it.copy(downloadWifiOnly = wifiOnly) }
        container.downloads.setWifiOnly(wifiOnly)
    }

    fun removeAllDownloads() {
        container.downloads.removeAll()
        _downloadsUsed.value = 0
    }

    fun clearHistory() {
        viewModelScope.launch { container.library.clearHistory() }
    }

    private fun refreshCacheUsage() {
        viewModelScope.launch {
            _cacheUsed.value = withContext(Dispatchers.IO) { container.cacheBytesUsed() }
            _downloadsUsed.value = withContext(Dispatchers.IO) { container.downloads.bytesUsed() }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenEqualizer: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cacheUsed by vm.cacheUsed.collectAsStateWithLifecycle()
    val downloadsUsed by vm.downloadsUsed.collectAsStateWithLifecycle()
    var confirmDownloads by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var confirmHistory by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerInset.current)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
            }
        }
        item {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        item { GroupTitle(stringResource(R.string.settings_playback)) }
        item {
            SettingsGroup(
                {
                    ChoiceSetting(
                        title = stringResource(R.string.settings_audio_quality),
                        summary = stringResource(R.string.settings_audio_quality_summary),
                        options = AudioQuality.entries,
                        selected = prefs.audioQuality,
                        label = {
                            when (it) {
                                AudioQuality.High -> R.string.quality_high
                                AudioQuality.Normal -> R.string.quality_normal
                                AudioQuality.Low -> R.string.quality_low
                            }
                        },
                        onSelect = { q -> vm.update { it.copy(audioQuality = q) } },
                    )
                },
                {
                    SwitchSetting(
                        title = stringResource(R.string.settings_autoplay),
                        summary = stringResource(R.string.settings_autoplay_summary),
                        checked = prefs.autoplay,
                        onCheckedChange = { on -> vm.update { it.copy(autoplay = on) } },
                    )
                },
            )
        }

        item { GroupTitle(stringResource(R.string.settings_sound)) }
        item {
            SettingsGroup(
                {
                    SwitchSetting(
                        title = stringResource(R.string.settings_normalize),
                        summary = stringResource(R.string.settings_normalize_summary),
                        checked = prefs.normalizeVolume,
                        onCheckedChange = { on -> vm.update { it.copy(normalizeVolume = on) } },
                    )
                },
                {
                    ChoiceSetting(
                        title = stringResource(R.string.settings_crossfade),
                        summary = stringResource(R.string.settings_crossfade_summary),
                        options = AppSettings.FadeSeconds,
                        selected = prefs.fadeSeconds,
                        labelText = { s -> if (s == 0) context.getString(R.string.off) else context.getString(R.string.seconds_short, s) },
                        onSelect = { s -> vm.update { it.copy(fadeSeconds = s) } },
                    )
                },
                {
                    ActionSetting(
                        title = stringResource(R.string.equalizer),
                        summary = stringResource(if (prefs.equalizerEnabled) R.string.equalizer_on else R.string.equalizer_summary),
                        onClick = onOpenEqualizer,
                    )
                },
            )
        }

        item { GroupTitle(stringResource(R.string.settings_appearance)) }
        item {
            SettingsGroup(
                {
                    ChoiceSetting(
                        title = stringResource(R.string.settings_theme),
                        summary = null,
                        options = ThemeMode.entries,
                        selected = prefs.themeMode,
                        label = {
                            when (it) {
                                ThemeMode.System -> R.string.theme_system
                                ThemeMode.Light -> R.string.theme_light
                                ThemeMode.Dark -> R.string.theme_dark
                            }
                        },
                        onSelect = { mode -> vm.update { it.copy(themeMode = mode) } },
                    )
                },
                {
                    SwitchSetting(
                        title = stringResource(R.string.settings_artwork_colors),
                        summary = stringResource(R.string.settings_artwork_colors_summary),
                        checked = prefs.artworkColors,
                        onCheckedChange = { on -> vm.update { it.copy(artworkColors = on) } },
                    )
                },
                {
                    SwitchSetting(
                        title = stringResource(R.string.settings_pure_black),
                        summary = stringResource(R.string.settings_pure_black_summary),
                        checked = prefs.pureBlack,
                        onCheckedChange = { on -> vm.update { it.copy(pureBlack = on) } },
                    )
                },
            )
        }

        item { GroupTitle(stringResource(R.string.settings_storage)) }
        item {
            SettingsGroup(
                {
                    val used = if (cacheUsed >= 0) Formatter.formatShortFileSize(context, cacheUsed) else "…"
                    val pending = prefs.cacheSizeMb != vm.startupCacheSizeMb
                    ChoiceSetting(
                        title = stringResource(R.string.settings_cache_size),
                        summary = stringResource(R.string.settings_cache_used, used) +
                            if (pending) "\n" + stringResource(R.string.settings_cache_restart) else "",
                        options = AppSettings.CacheSizesMb,
                        selected = prefs.cacheSizeMb,
                        labelText = { mb -> if (mb >= 1024) "${mb / 1024} GB" else "$mb MB" },
                        onSelect = { mb -> vm.update { it.copy(cacheSizeMb = mb) } },
                    )
                },
                {
                    ActionSetting(
                        title = stringResource(R.string.settings_clear_cache),
                        summary = stringResource(R.string.settings_clear_cache_summary),
                        onClick = vm::clearCache,
                    )
                },
                {
                    SwitchSetting(
                        title = stringResource(R.string.settings_download_wifi),
                        summary = stringResource(R.string.settings_download_wifi_summary),
                        checked = prefs.downloadWifiOnly,
                        onCheckedChange = vm::setDownloadWifiOnly,
                    )
                },
                {
                    val used = if (downloadsUsed >= 0) Formatter.formatShortFileSize(context, downloadsUsed) else "…"
                    ActionSetting(
                        title = stringResource(R.string.settings_remove_downloads),
                        summary = stringResource(R.string.settings_downloads_used, used) + "\n" +
                            stringResource(R.string.settings_remove_downloads_summary),
                        onClick = { confirmDownloads = true },
                    )
                },
                {
                    ActionSetting(
                        title = stringResource(R.string.settings_clear_history),
                        summary = stringResource(R.string.settings_clear_history_summary),
                        onClick = { confirmHistory = true },
                    )
                },
            )
        }

        item {
            Row(
                Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_lab), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            SettingsGroup(
                {
                    SwitchSetting(
                        title = stringResource(R.string.settings_song_recognition),
                        summary = stringResource(R.string.settings_song_recognition_summary),
                        checked = prefs.songRecognition,
                        onCheckedChange = { on -> vm.update { it.copy(songRecognition = on) } },
                    )
                },
            )
        }

        item { GroupTitle(stringResource(R.string.settings_about)) }
        item {
            SettingsGroup(
                { InfoSetting(stringResource(R.string.app_name), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                { InfoSetting(stringResource(R.string.settings_license), "GNU GPL-3.0") },
                { InfoSetting(stringResource(R.string.settings_credits), stringResource(R.string.settings_credits_summary)) },
            )
        }
    }

    if (confirmDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDownloads = false },
            title = { Text(stringResource(R.string.settings_remove_downloads)) },
            text = { Text(stringResource(R.string.settings_remove_downloads_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.removeAllDownloads(); confirmDownloads = false }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloads = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmHistory) {
        AlertDialog(
            onDismissRequest = { confirmHistory = false },
            title = { Text(stringResource(R.string.settings_clear_history)) },
            text = { Text(stringResource(R.string.settings_clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmHistory = false }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmHistory = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(vararg items: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, content ->
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == items.lastIndex) 24.dp else 6.dp
            Surface(
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchSetting(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(title, summary, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSetting(title: String, summary: String?, onClick: () -> Unit) {
    SettingText(
        title,
        summary,
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun InfoSetting(title: String, summary: String) {
    SettingText(title, summary, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp))
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    summary: String?,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: ((T) -> Int)? = null,
    labelText: ((T) -> String)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        SettingText(title, summary)
        Spacer(Modifier.height(12.dp))
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
                    // Four options share one row; trim the side padding so labels like "512 MB" fit.
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        labelText?.invoke(option) ?: stringResource(label!!.invoke(option)),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
