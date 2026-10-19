package me.luzility.muuusic.playback.audio

import android.content.Context
import androidx.core.content.edit

class LoudnessStore(context: Context) {
    private val store = context.getSharedPreferences("loudness", Context.MODE_PRIVATE)

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
