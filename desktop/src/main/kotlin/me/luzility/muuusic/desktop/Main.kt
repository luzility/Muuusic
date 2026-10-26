package me.luzility.muuusic.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.model.SearchFilter
import me.luzility.muuusic.data.model.SearchResult
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.desktop.playback.DesktopEqualizer
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.playback.audio.Karaoke
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

fun main() {
    AppInfo.versionName = System.getProperty("muuusic.version") ?: "0.1.0"
    val services = DesktopServices(DesktopServices.defaultDataDir())
    val locale = Locale.getDefault()
    NewPipe.init(NewPipeDownloader(services.okHttp), Localization.fromLocale(locale), ContentCountry(locale.country.ifEmpty { "US" }))

    application {
        val player = remember { DesktopPlayer(services) }
        Window(
            onCloseRequest = {
                player.release()
                services.streamCache.stop()
                exitApplication()
            },
            title = "Muuusic",
            state = rememberWindowState(size = DpSize(1000.dp, 760.dp)),
        ) {
            MaterialExpressiveTheme(colorScheme = rememberDynamicColorScheme(seedColor = Color(0xFF7C4DFF), isDark = isSystemInDarkTheme())) {
                Surface(Modifier.fillMaxSize()) { TestScreen(services, player) }
            }
        }
    }
}

@Composable
private fun TestScreen(services: DesktopServices, player: DesktopPlayer) {
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsState()
    val prefs by services.settings.prefs.collectAsState()
    val karaoke by Karaoke.enabled.collectAsState()
    val volume by player.volume.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var position by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.position()
            delay(250)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                scope.launch {
                    results = runCatching { services.youtube.search(query, SearchFilter.Songs) }.getOrDefault(emptyList())
                        .filterIsInstance<SearchResult.SongResult>().map { it.song }
                }
            }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = prefs.fadeSeconds > 0,
                onClick = { services.settings.update { it.copy(fadeSeconds = if (it.fadeSeconds > 0) 0 else 6) } },
                label = { Text("Crossfade 6s") },
            )
            FilterChip(selected = karaoke, onClick = { Karaoke.set(!karaoke) }, label = { Text("Karaoke") })
            FilterChip(
                selected = prefs.pitchSemitones != 0f,
                onClick = { services.settings.update { it.copy(pitchSemitones = if (it.pitchSemitones != 0f) 0f else 3f, linkPitch = false) } },
                label = { Text("Pitch +3") },
            )
            FilterChip(
                selected = prefs.playbackSpeed != 1f,
                onClick = { services.settings.update { it.copy(playbackSpeed = if (it.playbackSpeed != 1f) 1f else 1.25f) } },
                label = { Text("Speed 1.25") },
            )
            FilterChip(
                selected = prefs.equalizerEnabled,
                onClick = {
                    services.settings.update {
                        it.copy(equalizerEnabled = !it.equalizerEnabled, equalizerBands = DesktopEqualizer.presets[1].second)
                    }
                },
                label = { Text("EQ Bass") },
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(results) { index, song ->
                Text(
                    "${song.title} — ${song.artist}",
                    color = if (song.id == state.current?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { player.play(results, index) }.padding(vertical = 10.dp),
                )
            }
        }
        Text(state.current?.let { "${it.title} — ${it.artist}" } ?: "Nothing playing", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = scrub ?: if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { player.seekTo((it * state.durationMs).toLong()) }
                scrub = null
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${position / 1000}s / ${state.durationMs / 1000}s")
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = player::previous) { Icon(Icons.Rounded.SkipPrevious, null) }
            FilledIconButton(onClick = player::togglePlay, modifier = Modifier.size(52.dp)) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
            }
            IconButton(onClick = player::next) { Icon(Icons.Rounded.SkipNext, null) }
            Spacer(Modifier.width(16.dp))
            Text(if (state.isBuffering) "Loading…" else "")
            Spacer(Modifier.weight(1f))
            Text("Volume")
            Slider(value = volume, onValueChange = player::setVolume, modifier = Modifier.width(160.dp))
        }
    }
}
