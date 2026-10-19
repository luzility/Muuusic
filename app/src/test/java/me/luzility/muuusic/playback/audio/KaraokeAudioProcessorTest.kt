package me.luzility.muuusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

class KaraokeAudioProcessorTest {

    private val rate = 44_100

    @After
    fun reset() {
        Karaoke.set(false)
        Karaoke.setStrength(1f)
    }

    private fun run(on: Boolean, left: (Int) -> Float, right: (Int) -> Float): Pair<FloatArray, FloatArray> {
        Karaoke.set(on)
        val processor = KaraokeAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val frames = rate
        val input = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putFloat(left(it)); input.putFloat(right(it)) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        val produced = output.remaining() / 8
        val outLeft = FloatArray(produced)
        val outRight = FloatArray(produced)
        repeat(produced) { outLeft[it] = output.float; outRight[it] = output.float }
        return outLeft to outRight
    }

    private fun peak(samples: FloatArray) = samples.drop(samples.size / 2).maxOf { abs(it) }

    @Test
    fun centeredVoiceIsRemoved() {
        val voice = { i: Int -> 0.5f * sin(2 * Math.PI * 1_000 * i / rate).toFloat() }
        val (left, _) = run(on = true, voice, voice)
        assertTrue(peak(left) < 0.02f)
    }

    @Test
    fun centeredCymbalsStay() {
        val highs = { i: Int -> 0.5f * sin(2 * Math.PI * 10_000 * i / rate).toFloat() }
        val (left, _) = run(on = true, highs, highs)
        assertTrue(peak(left) > 0.45f)
    }

    @Test
    fun halfStrengthKeepsSomeVoice() {
        Karaoke.setStrength(0.5f)
        val voice = { i: Int -> 0.5f * sin(2 * Math.PI * 1_000 * i / rate).toFloat() }
        val (left, _) = run(on = true, voice, voice)
        Karaoke.setStrength(1f)
        assertEquals(0.25f, peak(left), 0.03f)
    }

    @Test
    fun sidesAndBassStay() {
        val bass = { i: Int -> 0.5f * sin(2 * Math.PI * 50 * i / rate).toFloat() }
        val (bassLeft, _) = run(on = true, bass, bass)
        assertTrue(peak(bassLeft) > 0.4f)

        val wide = { i: Int -> 0.5f * sin(2 * Math.PI * 1_000 * i / rate).toFloat() }
        val (wideLeft, wideRight) = run(on = true, wide) { -wide(it) }
        assertEquals(0.5f, peak(wideLeft), 0.01f)
        assertEquals(0.5f, peak(wideRight), 0.01f)
    }

    @Test
    fun offLeavesAudioUntouched() {
        val voice = { i: Int -> 0.5f * sin(2 * Math.PI * 1_000 * i / rate).toFloat() }
        val (left, right) = run(on = false, voice) { voice(it) * 0.3f }
        assertEquals(0.5f, peak(left), 0.001f)
        assertEquals(0.15f, peak(right), 0.001f)
    }
}
