package me.luzility.muuusic.data.recognition

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

// Port of SongRec / shazamio: 16 kHz mono PCM, 2048-point FFT every 128 samples,
// peaks picked per band and packed into Shazam's binary signature.
class ShazamSignature {
    private class Peak(val pass: Int, val magnitude: Int, val bin: Int)

    private val samples = ShortArray(WINDOW)
    private var samplePos = 0

    private val fftOutputs = Array(RING) { DoubleArray(BINS) }
    private var fftPos = 0

    private val spreadOutputs = Array(RING) { DoubleArray(BINS) }
    private var spreadPos = 0
    private var spreadWritten = 0

    private val bands = Array(4) { mutableListOf<Peak>() }
    private val fft = Fft(WINDOW)
    private val windowed = DoubleArray(WINDOW)

    var sampleCount = 0
        private set

    // 16 kHz mono 16-bit; trailing samples past a multiple of 128 are dropped.
    fun feed(pcm: ShortArray, length: Int = pcm.size) {
        var offset = 0
        while (offset + CHUNK <= length) {
            pcm.copyInto(samples, samplePos, offset, offset + CHUNK)
            samplePos = (samplePos + CHUNK) % WINDOW
            sampleCount += CHUNK
            doFft()
            doPeakSpreading()
            if (spreadWritten >= 46) doPeakRecognition()
            offset += CHUNK
        }
    }

    private fun doFft() {
        for (i in 0 until WINDOW) windowed[i] = samples[(samplePos + i) % WINDOW] * HANNING[i]
        val out = fftOutputs[fftPos]
        fft.powerSpectrum(windowed, out)
        for (i in 0 until BINS) out[i] = max(out[i] / (1 shl 17), 1e-10)
        fftPos = (fftPos + 1) % RING
    }

    private fun doPeakSpreading() {
        val last = fftOutputs[(fftPos - 1 + RING) % RING]
        val spread = last.copyOf()
        for (position in 0 until BINS) {
            if (position < BINS - 2) {
                spread[position] = max(spread[position], max(spread[position + 1], spread[position + 2]))
            }
            var maxValue = spread[position]
            for (former in FORMER_FFTS) {
                val output = spreadOutputs[(spreadPos + former + RING) % RING]
                maxValue = max(output[position], maxValue)
                output[position] = maxValue
            }
        }
        spread.copyInto(spreadOutputs[spreadPos])
        spreadPos = (spreadPos + 1) % RING
        spreadWritten++
    }

    private fun doPeakRecognition() {
        val fftMinus46 = fftOutputs[(fftPos - 46 + RING) % RING]
        val fftMinus49 = spreadOutputs[(spreadPos - 49 + RING) % RING]

        for (bin in 10 until 1015) {
            val value = fftMinus46[bin]
            if (value < 1.0 / 64 || value < fftMinus49[bin - 1]) continue

            var maxNeighbor = 0.0
            for (offset in NEIGHBOR_OFFSETS) maxNeighbor = max(fftMinus49[bin + offset], maxNeighbor)
            if (value <= maxNeighbor) continue

            var maxOther = maxNeighbor
            for (offset in OTHER_OFFSETS) {
                maxOther = max(spreadOutputs[((spreadPos + offset) % RING + RING) % RING][bin - 1], maxOther)
            }
            if (value <= maxOther) continue

            val pass = spreadWritten - 46
            val magnitude = ln(max(1.0 / 64, value)) * 1477.3 + 6144
            val before = ln(max(1.0 / 64, fftMinus46[bin - 1])) * 1477.3 + 6144
            val after = ln(max(1.0 / 64, fftMinus46[bin + 1])) * 1477.3 + 6144
            val variation1 = magnitude * 2 - before - after
            if (variation1 <= 0) continue
            val variation2 = (after - before) * 32 / variation1
            val correctedBin = bin * 64 + variation2
            val frequency = correctedBin * (SAMPLE_RATE / 2.0 / 1024 / 64)
            val band = when {
                frequency > 250 && frequency < 520 -> 0
                frequency > 520 && frequency < 1450 -> 1
                frequency > 1450 && frequency < 3500 -> 2
                frequency > 3500 && frequency < 5500 -> 3
                else -> continue
            }
            bands[band] += Peak(pass, magnitude.toInt(), correctedBin.toInt())
        }
    }

    // Shazam wants a reasonable number of peaks before a lookup makes sense.
    val peakCount: Int get() = bands.sumOf { it.size }

    fun encode(): ByteArray {
        val contents = ByteArrayOutputStream()
        bands.forEachIndexed { band, peaks ->
            if (peaks.isEmpty()) return@forEachIndexed
            val peakBytes = ByteArrayOutputStream()
            var pass = 0
            for (peak in peaks) {
                if (peak.pass - pass >= 255) {
                    peakBytes.write(0xff)
                    peakBytes.write(le32(peak.pass))
                    pass = peak.pass
                }
                peakBytes.write(peak.pass - pass)
                peakBytes.write(le16(peak.magnitude))
                peakBytes.write(le16(peak.bin))
                pass = peak.pass
            }
            val body = peakBytes.toByteArray()
            contents.write(le32(0x60030040 + band))
            contents.write(le32(body.size))
            contents.write(body)
            contents.write(ByteArray((4 - body.size % 4) % 4))
        }
        val content = contents.toByteArray()

        val buffer = ByteBuffer.allocate(HEADER_SIZE + 8 + content.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0xcafe2580.toInt()) // magic1
        buffer.putInt(0) // crc32, filled in below
        buffer.putInt(content.size + 8) // size minus header
        buffer.putInt(0x94119c00.toInt()) // magic2
        repeat(3) { buffer.putInt(0) }
        buffer.putInt(SAMPLE_RATE_ID shl 27)
        repeat(2) { buffer.putInt(0) }
        buffer.putInt((sampleCount + SAMPLE_RATE * 0.24).toInt())
        buffer.putInt((15 shl 19) + 0x40000)
        buffer.putInt(0x40000000)
        buffer.putInt(content.size + 8)
        buffer.put(content)

        val bytes = buffer.array()
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, crc.toInt())
        return bytes
    }

    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())
    private fun le32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val SAMPLE_RATE_ID = 3 // Shazam's code for 16 kHz
        private const val WINDOW = 2048
        private const val BINS = WINDOW / 2 + 1
        private const val CHUNK = 128
        private const val RING = 256
        private const val HEADER_SIZE = 48
        private val FORMER_FFTS = intArrayOf(-1, -3, -6)
        private val NEIGHBOR_OFFSETS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
        private val OTHER_OFFSETS = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

        /** numpy.hanning(2050)[1:-1] */
        private val HANNING = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * (it + 1) / (WINDOW + 1)) }
    }
}

// In-place radix-2 FFT, returns the power spectrum of real input.
private class Fft(private val n: Int) {
    private val levels = Integer.numberOfTrailingZeros(n)
    private val cosTable = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinTable = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)

    fun powerSpectrum(input: DoubleArray, out: DoubleArray) {
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - levels)
            re[j] = input[i]
            im[j] = 0.0
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val tRe = re[l] * cosTable[k] + im[l] * sinTable[k]
                    val tIm = -re[l] * sinTable[k] + im[l] * cosTable[k]
                    re[l] = re[j] - tRe
                    im[l] = im[j] - tIm
                    re[j] += tRe
                    im[j] += tIm
                    k += step
                }
                start += size
            }
            size *= 2
        }
        for (i in 0..n / 2) out[i] = re[i] * re[i] + im[i] * im[i]
    }
}
