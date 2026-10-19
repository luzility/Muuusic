package me.luzility.muuusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh

// Gain can change at any time (normalization times fade level). Changes ramp over a few ms to
// avoid clicks, and boosts are soft-clipped instead of wrapping.
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile var normalization = 1f

    @Volatile var fade = 1f

    private var current = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val target = normalization * fade
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frames = remaining / inputAudioFormat.bytesPerFrame
        val channels = inputAudioFormat.channelCount
        // Reach the new gain within ~10 ms.
        val step = (target - current) / maxOf(1, minOf(frames, inputAudioFormat.sampleRate / 100))

        repeat(frames) {
            if (abs(target - current) > abs(step)) current += step else current = target
            repeat(channels) {
                if (float) {
                    output.putFloat(softClip(input.float * current))
                } else {
                    val sample = input.short / 32768f * current
                    output.putShort((softClip(sample) * 32767f).toInt().toShort())
                }
            }
        }
        output.flip()
    }

    override fun onFlush() {
        current = normalization * fade
    }

    // Untouched below ~0.9, rounds peaks above that instead of hard clipping.
    private fun softClip(x: Float): Float {
        val knee = 0.9f
        return when {
            x > knee -> knee + (1 - knee) * tanh((x - knee) / (1 - knee))
            x < -knee -> -knee - (1 - knee) * tanh((-x - knee) / (1 - knee))
            else -> x
        }
    }
}
