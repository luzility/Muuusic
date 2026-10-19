package me.luzility.muuusic.data.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioQuality { High, Normal, Low }

enum class ThemeMode { System, Light, Dark }

@Immutable
data class AppPrefs(
    val audioQuality: AudioQuality = AudioQuality.High,
    val autoplay: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val artworkColors: Boolean = true,
    val pureBlack: Boolean = false,
    val cacheSizeMb: Int = 512,
    val downloadWifiOnly: Boolean = true,
    val songRecognition: Boolean = true,
    val normalizeVolume: Boolean = true,
    val fadeSeconds: Int = 0,
    val equalizerEnabled: Boolean = false,
    // millibels per band; empty is flat
    val equalizerBands: List<Int> = emptyList(),
    /** Index of the device preset in use, or -1 for custom levels. */
    val equalizerPreset: Int = -1,
    /** Bass boost strength, 0..1000. */
    val bassBoost: Int = 0,
)

class AppSettings(context: Context) {
    private val store = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    /** Cache size the app was started with; a new size only takes effect after a restart. */
    val startupCacheSizeMb: Int = _prefs.value.cacheSizeMb

    fun update(transform: (AppPrefs) -> AppPrefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        store.edit {
            putString(KEY_QUALITY, next.audioQuality.name)
            putBoolean(KEY_AUTOPLAY, next.autoplay)
            putString(KEY_THEME, next.themeMode.name)
            putBoolean(KEY_ARTWORK_COLORS, next.artworkColors)
            putBoolean(KEY_PURE_BLACK, next.pureBlack)
            putInt(KEY_CACHE_MB, next.cacheSizeMb)
            putBoolean(KEY_DOWNLOAD_WIFI_ONLY, next.downloadWifiOnly)
            putBoolean(KEY_SONG_RECOGNITION, next.songRecognition)
            putBoolean(KEY_NORMALIZE, next.normalizeVolume)
            putInt(KEY_FADE, next.fadeSeconds)
            putBoolean(KEY_EQ_ENABLED, next.equalizerEnabled)
            putString(KEY_EQ_BANDS, next.equalizerBands.joinToString(","))
            putInt(KEY_EQ_PRESET, next.equalizerPreset)
            putInt(KEY_BASS, next.bassBoost)
        }
    }

    private fun read() = AppPrefs(
        audioQuality = enumOrNull<AudioQuality>(store.getString(KEY_QUALITY, null)) ?: AudioQuality.High,
        autoplay = store.getBoolean(KEY_AUTOPLAY, true),
        themeMode = enumOrNull<ThemeMode>(store.getString(KEY_THEME, null)) ?: ThemeMode.System,
        artworkColors = store.getBoolean(KEY_ARTWORK_COLORS, true),
        pureBlack = store.getBoolean(KEY_PURE_BLACK, false),
        cacheSizeMb = store.getInt(KEY_CACHE_MB, 512),
        downloadWifiOnly = store.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true),
        songRecognition = store.getBoolean(KEY_SONG_RECOGNITION, true),
        normalizeVolume = store.getBoolean(KEY_NORMALIZE, true),
        fadeSeconds = store.getInt(KEY_FADE, 0),
        equalizerEnabled = store.getBoolean(KEY_EQ_ENABLED, false),
        equalizerBands = store.getString(KEY_EQ_BANDS, "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
        equalizerPreset = store.getInt(KEY_EQ_PRESET, -1),
        bassBoost = store.getInt(KEY_BASS, 0),
    )

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    companion object {
        val CacheSizesMb = listOf(256, 512, 1024, 2048)
        val FadeSeconds = listOf(0, 3, 6, 10)

        private const val KEY_QUALITY = "audio_quality"
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ARTWORK_COLORS = "artwork_colors"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_CACHE_MB = "cache_size_mb"
        private const val KEY_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
        private const val KEY_SONG_RECOGNITION = "song_recognition"
        private const val KEY_NORMALIZE = "normalize_volume"
        private const val KEY_FADE = "fade_seconds"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_BANDS = "eq_bands"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_BASS = "bass_boost"
    }
}
