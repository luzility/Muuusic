package me.luzility.muuusic.playback.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Karaoke {
    val strengths = listOf(0.5f, 0.75f, 1f)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _strength = MutableStateFlow(1f)
    val strength: StateFlow<Float> = _strength.asStateFlow()

    fun set(on: Boolean) {
        _enabled.value = on
    }

    fun setStrength(value: Float) {
        _strength.value = value.coerceIn(0f, 1f)
    }
}
