package me.luzility.muuusic.desktop.playback

import me.luzility.muuusic.data.settings.AppPrefs
import java.util.Locale

object DesktopEqualizer {
    val bandFrequenciesHz = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val MIN_LEVEL = -1200
    const val MAX_LEVEL = 1200

    val presets = listOf(
        "Normal" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Bass" to listOf(600, 500, 400, 200, 0, 0, 0, 0, 0, 0),
        "Treble" to listOf(0, 0, 0, 0, 0, 100, 200, 400, 500, 600),
        "Rock" to listOf(500, 300, 100, -100, -200, 0, 200, 400, 500, 500),
        "Pop" to listOf(-100, 0, 200, 400, 500, 400, 200, 0, -100, -100),
        "Jazz" to listOf(300, 200, 100, 200, -100, -100, 0, 100, 200, 300),
        "Classical" to listOf(400, 300, 200, 100, 0, 0, 0, 200, 300, 400),
        "Dance" to listOf(600, 400, 100, 0, 0, -200, -100, 200, 400, 500),
        "Vocal" to listOf(-200, -200, -100, 100, 300, 400, 300, 100, 0, -100),
    )

    fun filters(prefs: AppPrefs): List<String> {
        if (!prefs.equalizerEnabled) return emptyList()
        val bands = bandFrequenciesHz.mapIndexedNotNull { band, hz ->
            val level = prefs.equalizerBands.getOrNull(band) ?: 0
            if (level == 0) null else "equalizer=f=$hz:t=o:w=1:g=${decibels(level)}"
        }
        val bass = prefs.bassBoost.takeIf { it > 0 }?.let { "bass=g=${decibels(it * MAX_BASS_MB / 1000)}" }
        return bands + listOfNotNull(bass)
    }

    private fun decibels(millibels: Int) = String.format(Locale.ROOT, "%.2f", millibels / 100.0)

    private const val MAX_BASS_MB = 1200
}
