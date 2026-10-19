package me.luzility.muuusic.playback.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.compose.runtime.Immutable
import me.luzility.muuusic.data.settings.AppPrefs

@Immutable
data class EqualizerLayout(
    val bandFrequenciesHz: List<Int>,
    val minLevel: Int,
    val maxLevel: Int,
    val presetNames: List<String>,
    val presetLevels: List<List<Int>>,
    val bassBoostSupported: Boolean,
)

class AudioEffects(audioSessionId: Int) {
    private val equalizer = runCatching { Equalizer(0, audioSessionId) }
        .onFailure { Log.w(TAG, "Equalizer unavailable", it) }.getOrNull()
    private val bassBoost = runCatching { BassBoost(0, audioSessionId) }
        .onFailure { Log.w(TAG, "Bass boost unavailable", it) }.getOrNull()

    val layout: EqualizerLayout? = equalizer?.let { eq ->
        runCatching {
            val bands = eq.numberOfBands.toInt()
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            val presetLevels = presets.indices.map { preset ->
                eq.usePreset(preset.toShort())
                (0 until bands).map { eq.getBandLevel(it.toShort()).toInt() }
            }
            EqualizerLayout(
                bandFrequenciesHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                minLevel = eq.bandLevelRange[0].toInt(),
                maxLevel = eq.bandLevelRange[1].toInt(),
                presetNames = presets,
                presetLevels = presetLevels,
                bassBoostSupported = bassBoost?.strengthSupported == true,
            )
        }.getOrNull()
    }

    fun apply(prefs: AppPrefs) {
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = prefs.equalizerEnabled
                val bands = eq.numberOfBands.toInt()
                for (band in 0 until bands) {
                    eq.setBandLevel(band.toShort(), (prefs.equalizerBands.getOrNull(band) ?: 0).toShort())
                }
            }
            bassBoost?.let { bass ->
                bass.enabled = prefs.equalizerEnabled && prefs.bassBoost > 0
                if (bass.strengthSupported) bass.setStrength(prefs.bassBoost.coerceIn(0, 1000).toShort())
            }
        }.onFailure { Log.w(TAG, "Could not apply audio effects", it) }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
