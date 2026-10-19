package me.luzility.muuusic.ui.recognition

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object Earcons {

    internal class Note(val atMs: Int, val hz: Double, val decayMs: Double, val gain: Double)

    internal val start = listOf(
        Note(0, 587.33, 110.0, 0.45),   // D5
        Note(115, 880.00, 160.0, 0.40), // A5
    )

    internal val found = listOf(
        Note(0, 659.26, 120.0, 0.34),    // E5
        Note(85, 830.61, 120.0, 0.32),   // G#5
        Note(170, 987.77, 140.0, 0.30),  // B5
        Note(255, 1318.51, 320.0, 0.30), // E6
    )

    internal val notFound = listOf(
        Note(0, 880.00, 150.0, 0.30),   // A5
        Note(160, 659.26, 260.0, 0.28), // E5
    )

    private val cache = HashMap<List<Note>, ShortArray>()

    suspend fun playStart() = play(start)
    suspend fun playFound() = play(found)
    suspend fun playNotFound() = play(notFound)

    /** Length of [playStart], so recording can begin once the chime has faded. */
    const val START_DURATION_MS = 420L

    private suspend fun play(notes: List<Note>) = withContext(Dispatchers.Default) {
        val pcm = synchronized(cache) { cache.getOrPut(notes) { render(notes) } }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            kotlinx.coroutines.delay(pcm.size * 1000L / SAMPLE_RATE + 50)
        } finally {
            track.release()
        }
    }

    internal fun render(notes: List<Note>): ShortArray {
        val lengthMs = notes.maxOf { it.atMs + it.decayMs * 6 }
        val out = DoubleArray((lengthMs * SAMPLE_RATE / 1000).toInt())
        for (note in notes) {
            val start = note.atMs * SAMPLE_RATE / 1000
            var phase = 0.0
            for (i in start until out.size) {
                val t = (i - start).toDouble() / SAMPLE_RATE
                // Pitch rises ~2% over the first 40 ms.
                val bloom = 0.98 + 0.02 * (t / 0.04).coerceAtMost(1.0)
                phase += 2 * PI * note.hz * bloom / SAMPLE_RATE
                val attack = 1 - exp(-t / 0.004)
                val decay = exp(-t * 1000 / note.decayMs)
                // Fundamental plus a little 2nd and 3rd harmonic.
                val wave = sin(phase) + 0.22 * sin(2 * phase) * decay + 0.06 * sin(3 * phase) * decay * decay
                out[i] += wave * attack * decay * note.gain
            }
        }
        // Short fade at the very end so nothing clicks, then convert to 16-bit.
        val fade = SAMPLE_RATE / 100
        return ShortArray(out.size) { i ->
            val edge = if (i > out.size - fade) (out.size - i).toDouble() / fade else 1.0
            (out[i] * edge * 0.8 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    internal const val SAMPLE_RATE = 44_100
}
