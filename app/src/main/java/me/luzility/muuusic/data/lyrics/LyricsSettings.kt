package me.luzility.muuusic.data.lyrics

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class LyricsTextSize { Small, Medium, Large }

enum class LyricsAlign { Start, Center }

@Immutable
data class LyricsPrefs(
    val textSize: LyricsTextSize = LyricsTextSize.Medium,
    val align: LyricsAlign = LyricsAlign.Start,
    val blur: Boolean = true,
    val translate: Boolean = false,
)

class LyricsSettings(context: Context) {
    private val prefsStore = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)
    private val offsetStore = context.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(readPrefs())
    val prefs: StateFlow<LyricsPrefs> = _prefs.asStateFlow()

    private val offsets = MutableStateFlow(readOffsets())

    // ms added to the playback position; positive shows lyrics earlier.
    fun offsetFor(songId: String): Flow<Long> = offsets.map { it[songId] ?: 0L }.distinctUntilChanged()

    fun setOffset(songId: String, offsetMs: Long) {
        offsets.update { if (offsetMs == 0L) it - songId else it + (songId to offsetMs) }
        offsetStore.edit { if (offsetMs == 0L) remove(songId) else putLong(songId, offsetMs) }
    }

    fun update(transform: (LyricsPrefs) -> LyricsPrefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        prefsStore.edit {
            putString(KEY_SIZE, next.textSize.name)
            putString(KEY_ALIGN, next.align.name)
            putBoolean(KEY_BLUR, next.blur)
            putBoolean(KEY_TRANSLATE, next.translate)
        }
    }

    fun reload() {
        _prefs.value = readPrefs()
        offsets.value = readOffsets()
    }

    private fun readOffsets() = offsetStore.all.mapNotNull { (id, value) -> (value as? Long)?.let { id to it } }.toMap()

    private fun readPrefs() = LyricsPrefs(
        textSize = prefsStore.getString(KEY_SIZE, null)
            ?.let { runCatching { LyricsTextSize.valueOf(it) }.getOrNull() } ?: LyricsTextSize.Medium,
        align = prefsStore.getString(KEY_ALIGN, null)
            ?.let { runCatching { LyricsAlign.valueOf(it) }.getOrNull() } ?: LyricsAlign.Start,
        blur = prefsStore.getBoolean(KEY_BLUR, true),
        translate = prefsStore.getBoolean(KEY_TRANSLATE, false),
    )

    private companion object {
        const val KEY_SIZE = "text_size"
        const val KEY_ALIGN = "align"
        const val KEY_BLUR = "blur"
        const val KEY_TRANSLATE = "translate"
    }
}
