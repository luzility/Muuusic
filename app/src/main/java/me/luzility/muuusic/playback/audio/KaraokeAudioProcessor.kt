package me.luzility.muuusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class KaraokeAudioProcessor : BaseAudioProcessor() {

    private val window = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * PI * it / SIZE)).toFloat() }
    private val cosTable = FloatArray(SIZE / 2) { cos(2 * PI * it / SIZE).toFloat() }
    private val sinTable = FloatArray(SIZE / 2) { sin(2 * PI * it / SIZE).toFloat() }
    private val bitReverse = IntArray(SIZE) { Integer.reverse(it) ushr (32 - BITS) }

    private val historyLeft = FloatArray(SIZE)
    private val historyRight = FloatArray(SIZE)
    private val pendingLeft = FloatArray(HOP)
    private val pendingRight = FloatArray(HOP)
    private var pending = 0
    private val sumLeft = FloatArray(SIZE)
    private val sumRight = FloatArray(SIZE)
    private val realLeft = FloatArray(SIZE)
    private val imagLeft = FloatArray(SIZE)
    private val realRight = FloatArray(SIZE)
    private val imagRight = FloatArray(SIZE)

    private var lowBin = 0
    private var highBin = 0
    private var amount = 0f
    private var step = 0f
    private var float = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount != 2) return AudioProcessor.AudioFormat.NOT_SET
        lowBin = (VOICE_LOW_HZ * SIZE / inputAudioFormat.sampleRate).roundToInt()
        highBin = (VOICE_HIGH_HZ * SIZE / inputAudioFormat.sampleRate).roundToInt().coerceAtMost(SIZE / 2 - 1)
        step = HOP / (inputAudioFormat.sampleRate * RAMP_SECONDS)
        float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = remaining / inputAudioFormat.bytesPerFrame
        val output = replaceOutputBuffer(((pending + frames) / HOP) * HOP * inputAudioFormat.bytesPerFrame)
            .order(ByteOrder.nativeOrder())
        repeat(frames) {
            pendingLeft[pending] = if (float) input.float else input.short / 32768f
            pendingRight[pending] = if (float) input.float else input.short / 32768f
            pending++
            if (pending == HOP) processHop(output)
        }
        output.flip()
    }

    override fun onQueueEndOfStream() {
        val tail = SIZE - HOP + pending
        val hops = (tail + HOP - 1) / HOP
        val output = replaceOutputBuffer(hops * HOP * inputAudioFormat.bytesPerFrame).order(ByteOrder.nativeOrder())
        repeat(hops) {
            while (pending < HOP) {
                pendingLeft[pending] = 0f
                pendingRight[pending] = 0f
                pending++
            }
            processHop(output)
        }
        output.flip()
    }

    override fun onFlush() {
        historyLeft.fill(0f)
        historyRight.fill(0f)
        sumLeft.fill(0f)
        sumRight.fill(0f)
        pending = 0
        amount = if (Karaoke.enabled.value) 1f else 0f
    }

    override fun onReset() = onFlush()

    private fun processHop(output: ByteBuffer) {
        System.arraycopy(historyLeft, HOP, historyLeft, 0, SIZE - HOP)
        System.arraycopy(historyRight, HOP, historyRight, 0, SIZE - HOP)
        System.arraycopy(pendingLeft, 0, historyLeft, SIZE - HOP, HOP)
        System.arraycopy(pendingRight, 0, historyRight, SIZE - HOP, HOP)
        pending = 0

        val target = if (Karaoke.enabled.value) 1f else 0f
        amount = when {
            amount < target -> (amount + step).coerceAtMost(target)
            amount > target -> (amount - step).coerceAtLeast(target)
            else -> amount
        }
        val removal = amount * Karaoke.strength.value

        if (removal > 0f) {
            for (i in 0 until SIZE) {
                realLeft[i] = historyLeft[i] * window[i]
                imagLeft[i] = 0f
                realRight[i] = historyRight[i] * window[i]
                imagRight[i] = 0f
            }
            fft(realLeft, imagLeft, inverse = false)
            fft(realRight, imagRight, inverse = false)
            for (k in lowBin..highBin) {
                removeCenter(k, removal)
                if (k != 0) removeCenter(SIZE - k, removal)
            }
            fft(realLeft, imagLeft, inverse = true)
            fft(realRight, imagRight, inverse = true)
            for (i in 0 until SIZE) {
                sumLeft[i] += realLeft[i] * window[i] * OVERLAP_GAIN
                sumRight[i] += realRight[i] * window[i] * OVERLAP_GAIN
            }
        } else {
            for (i in 0 until SIZE) {
                val w = window[i] * window[i] * OVERLAP_GAIN
                sumLeft[i] += historyLeft[i] * w
                sumRight[i] += historyRight[i] * w
            }
        }

        for (i in 0 until HOP) {
            write(output, sumLeft[i])
            write(output, sumRight[i])
        }
        System.arraycopy(sumLeft, HOP, sumLeft, 0, SIZE - HOP)
        System.arraycopy(sumRight, HOP, sumRight, 0, SIZE - HOP)
        sumLeft.fill(0f, SIZE - HOP, SIZE)
        sumRight.fill(0f, SIZE - HOP, SIZE)
    }

    private fun removeCenter(k: Int, removal: Float) {
        val lr = realLeft[k]
        val li = imagLeft[k]
        val rr = realRight[k]
        val ri = imagRight[k]
        val dr = lr - rr
        val di = li - ri
        val difference = dr * dr + di * di
        val energy = lr * lr + li * li + rr * rr + ri * ri + 1e-12f
        val centered = (1f - difference / energy * PAN_SENSITIVITY).coerceIn(0f, 1f)
        val keep = 1f - removal * centered
        val midR = (lr + rr) * 0.5f * keep
        val midI = (li + ri) * 0.5f * keep
        val sideR = dr * 0.5f
        val sideI = di * 0.5f
        realLeft[k] = midR + sideR
        imagLeft[k] = midI + sideI
        realRight[k] = midR - sideR
        imagRight[k] = midI - sideI
    }

    private fun write(output: ByteBuffer, sample: Float) {
        if (float) {
            output.putFloat(sample)
        } else {
            output.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun fft(real: FloatArray, imag: FloatArray, inverse: Boolean) {
        for (i in 0 until SIZE) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= SIZE) {
            val half = length / 2
            val stride = SIZE / length
            var start = 0
            while (start < SIZE) {
                for (k in 0 until half) {
                    val wr = cosTable[k * stride]
                    val wi = if (inverse) sinTable[k * stride] else -sinTable[k * stride]
                    val a = start + k
                    val b = a + half
                    val xr = real[b] * wr - imag[b] * wi
                    val xi = real[b] * wi + imag[b] * wr
                    real[b] = real[a] - xr
                    imag[b] = imag[a] - xi
                    real[a] += xr
                    imag[a] += xi
                }
                start += length
            }
            length *= 2
        }
        if (inverse) {
            val scale = 1f / SIZE
            for (i in 0 until SIZE) {
                real[i] *= scale
                imag[i] *= scale
            }
        }
    }

    private companion object {
        const val BITS = 11
        const val SIZE = 1 shl BITS
        const val HOP = SIZE / 4
        const val OVERLAP_GAIN = 2f / 3f
        const val VOICE_LOW_HZ = 150.0
        const val VOICE_HIGH_HZ = 6_000.0
        const val PAN_SENSITIVITY = 1.5f
        const val RAMP_SECONDS = 0.3f
    }
}
