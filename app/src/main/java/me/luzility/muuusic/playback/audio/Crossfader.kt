package me.luzility.muuusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The next song starts on a fresh player that takes the media session; the old player only
// plays out its ending while fading. Nothing moves between players mid-song.
class Crossfader(private val startNext: () -> Outgoing?) {

    class Outgoing(val player: ExoPlayer, val gain: GainAudioProcessor, val fadeStartMs: Long)

    private var outgoing: Outgoing? = null
    private var fadeMs = 0L

    private var fadeInMs = 0L

    fun tick(fadeSeconds: Int, main: ExoPlayer, mainGain: GainAudioProcessor) {
        if (fadeSeconds == 0) {
            release()
            fadeInMs = 0
            mainGain.fade = 1f
            return
        }
        if (outgoing == null && fadeInMs == 0L) fadeMs = fadeSeconds * 1000L

        if (fadeInMs > 0) {
            val progress = (main.currentPosition.toFloat() / fadeInMs).coerceIn(0f, 1f)
            mainGain.fade = sin(progress * PI / 2).toFloat()
            if (progress >= 1f) fadeInMs = 0
        } else {
            mainGain.fade = 1f
        }

        outgoing?.let { fadeOut(it, main) }

        if (shouldStart(main)) {
            release()
            val next = startNext() ?: return
            outgoing = next
            // Overlap is whatever is left of the old song.
            fadeInMs = (next.player.duration - next.fadeStartMs).coerceIn(1, fadeMs)
        }
    }

    fun release() {
        outgoing?.player?.release()
        outgoing = null
    }

    private fun shouldStart(main: ExoPlayer): Boolean {
        val duration = main.duration
        return main.isPlaying &&
            duration != C.TIME_UNSET && duration > fadeMs * 2 &&
            main.currentPosition >= duration - fadeMs &&
            main.hasNextMediaItem() &&
            main.repeatMode != Player.REPEAT_MODE_ONE &&
            // The sleep timer's "end of song" stop wins over crossfading into the next one.
            !main.pauseAtEndOfMediaItems
    }

    private fun fadeOut(tail: Outgoing, main: ExoPlayer) {
        val player = tail.player
        val length = (player.duration - tail.fadeStartMs).coerceAtLeast(1)
        val progress = ((player.currentPosition - tail.fadeStartMs).toFloat() / length).coerceIn(0f, 1f)
        if (progress >= 0.99f || player.playbackState == Player.STATE_ENDED) {
            release()
            return
        }
        // Equal-power curves keep loudness steady through the overlap.
        tail.gain.fade = cos(progress * PI / 2).toFloat()
        // Pausing (or resuming) the new song pauses the ending of the old one with it.
        if (player.playWhenReady != main.playWhenReady) player.playWhenReady = main.playWhenReady
    }
}
