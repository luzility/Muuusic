package me.luzility.muuusic.playback.audio

import me.luzility.muuusic.data.storage.KeyValueStores

class LoudnessStore(stores: KeyValueStores) {
    private val store = stores.open("loudness")

    fun get(videoId: String): Float? = if (store.contains(videoId)) store.getFloat(videoId, 0f) else null

    fun put(videoId: String, gainDb: Float) {
        store.edit {
            // Plain cap to keep the file small; evicted entries just get refetched.
            if (store.all.size >= MAX_ENTRIES) clear()
            putFloat(videoId, gainDb)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 2_000
    }
}
