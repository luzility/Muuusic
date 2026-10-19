package me.luzility.muuusic.data.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class ShazamSignatureTest {
    /** A few seconds of tones with some noise, enough to produce spectral peaks in every band. */
    private fun testAudio(seconds: Int): ShortArray {
        val random = Random(42)
        return ShortArray(seconds * ShazamSignature.SAMPLE_RATE) { i ->
            val t = i.toDouble() / ShazamSignature.SAMPLE_RATE
            val tone = listOf(330.0, 880.0, 2200.0, 4400.0).sumOf { f -> sin(2 * PI * f * t * (1 + 0.1 * sin(t))) }
            (tone * 5000 + random.nextInt(-800, 800)).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun encode(seconds: Int): Pair<ShazamSignature, ByteBuffer> {
        val signature = ShazamSignature().apply { feed(testAudio(seconds)) }
        return signature to ByteBuffer.wrap(signature.encode()).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Test fun headerHasShazamMagicAndSizes() {
        val (signature, buffer) = encode(4)
        assertEquals(0xcafe2580.toInt(), buffer.getInt(0))
        assertEquals(0x94119c00.toInt(), buffer.getInt(12))
        assertEquals(buffer.capacity() - 48, buffer.getInt(8))
        assertEquals(3 shl 27, buffer.getInt(28))
        assertEquals((signature.sampleCount + 16_000 * 0.24).toInt(), buffer.getInt(40))
        assertEquals(0x40000000, buffer.getInt(48))
    }

    @Test fun crcCoversEverythingAfterIt() {
        val (_, buffer) = encode(4)
        val bytes = buffer.array()
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
        assertEquals(crc, buffer.getInt(4))
    }

    @Test fun findsPeaksAndKeepsBlocksAligned() {
        val (signature, buffer) = encode(5)
        assertTrue("expected peaks, got ${signature.peakCount}", signature.peakCount > 50)
        // Walk the band blocks: each is tag, length, payload padded to 4 bytes.
        var offset = 56
        var blocks = 0
        while (offset < buffer.capacity()) {
            val tag = buffer.getInt(offset)
            assertTrue(tag in 0x60030040..0x60030043)
            val length = buffer.getInt(offset + 4)
            offset += 8 + length + (4 - length % 4) % 4
            blocks++
        }
        assertEquals(buffer.capacity(), offset)
        assertTrue(blocks > 0)
    }

    @Test fun dropsSamplesBeyondWholeChunks() {
        val signature = ShazamSignature()
        signature.feed(ShortArray(300))
        assertEquals(256, signature.sampleCount)
    }
}
