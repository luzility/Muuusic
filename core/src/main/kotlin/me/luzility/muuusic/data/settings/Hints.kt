package me.luzility.muuusic.data.settings

import me.luzility.muuusic.data.storage.KeyValueStores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Hint { MiniPlayer, Lyrics, Recognition, LyricsShare }

/** One-time tips; each shows until the user dismisses it once. */
class Hints(stores: KeyValueStores) {
    private val store = stores.open("hints")

    private val _seen = MutableStateFlow(store.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    fun markSeen(hint: Hint) {
        if (hint.name in _seen.value) return
        _seen.value += hint.name
        store.edit { putStringSet(KEY, _seen.value) }
    }

    fun reset() {
        _seen.value = emptySet()
        store.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "seen"
    }
}
