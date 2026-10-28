package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import kotlin.math.abs

class DesktopPresence(scope: CoroutineScope, private val player: DesktopPlayer, private val settings: AppSettings) {
    private val rpc = DiscordRpc(scope)
    private var last: ListeningActivity? = null

    init {
        scope.launch {
            combine(player.state, settings.prefs) { _, _ -> }.collect { refresh() }
        }
        scope.launch {
            while (true) {
                delay(CHECK_MS)
                refresh()
            }
        }
    }

    private fun refresh() {
        val next = current()
        val previous = last
        val changed = when {
            next == null || previous == null -> next != previous
            else -> next.songId != previous.songId ||
                abs(next.startMs - previous.startMs) > DRIFT_MS ||
                (next.endMs == null) != (previous.endMs == null)
        }
        if (!changed) return
        last = next
        rpc.update(next)
    }

    private fun current(): ListeningActivity? {
        val prefs = settings.prefs.value
        val state = player.state.value
        val song = state.current ?: return null
        if (!prefs.discordPresence || !state.isPlaying) return null
        val speed = prefs.playbackSpeed.coerceAtLeast(0.1f)
        val start = (System.currentTimeMillis() - (player.position() / speed).toLong()) / 1000 * 1000
        val duration = state.durationMs.takeIf { it > 0 }
        return ListeningActivity(song.id, song.title, song.artist, song.thumbnailUrl, start, duration?.let { start + (it / speed).toLong() })
    }

    private companion object {
        const val CHECK_MS = 2_000L
        const val DRIFT_MS = 2_000L
    }
}
