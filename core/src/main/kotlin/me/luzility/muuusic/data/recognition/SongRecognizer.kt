package me.luzility.muuusic.data.recognition

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

interface PcmSource {
    fun read(buffer: ShortArray): Int
    fun close()
}

// Lookups go out after 4, 8 and 12 s of audio; first match wins.
class SongRecognizer(private val client: ShazamClient) {

    sealed interface Result {
        data class Found(val song: RecognizedSong) : Result
        data object NotFound : Result
        data object Failed : Result
    }

    // onLevel gets 0..1 about 15 times a second; onSearching fires when only a lookup is left.
    suspend fun recognize(open: () -> PcmSource?, onLevel: (Float) -> Unit, onSearching: () -> Unit): Result = withContext(Dispatchers.Default) {
        val source = open() ?: return@withContext Result.Failed
        val signature = ShazamSignature()
        val buffer = ShortArray(READ_SIZE)
        try {
            coroutineScope {
                val lookups = mutableListOf<Deferred<kotlin.Result<RecognizedSong?>>>()
                var failures = 0
                var next = 0
                // Recording never pauses for a lookup: gaps in the audio would break the fingerprint.
                while (next < CHECKPOINTS_SECONDS.size) {
                    ensureActive()
                    lookups.filter { it.isCompleted }.forEach { done ->
                        lookups.remove(done)
                        val outcome = done.await()
                        outcome.getOrNull()?.let {
                            lookups.forEach { other -> other.cancel() }
                            return@coroutineScope Result.Found(it)
                        }
                        if (outcome.isFailure) failures++
                    }
                    val read = source.read(buffer)
                    if (read < 0) {
                        lookups.forEach { it.cancel() }
                        return@coroutineScope Result.Failed
                    }
                    if (read == 0) continue
                    signature.feed(buffer, read)
                    onLevel(level(buffer, read))
                    if (signature.sampleCount >= CHECKPOINTS_SECONDS[next] * ShazamSignature.SAMPLE_RATE) {
                        val data = signature.encode()
                        val samples = signature.sampleCount
                        lookups += async(Dispatchers.IO) { runCatching { client.recognize(data, samples) } }
                        next++
                    }
                }
                source.close()
                onSearching()
                for (lookup in lookups) {
                    val outcome = lookup.await()
                    outcome.getOrNull()?.let {
                        lookups.forEach { other -> other.cancel() }
                        return@coroutineScope Result.Found(it)
                    }
                    if (outcome.isFailure) failures++
                }
                // Every lookup failing means a network or service problem, not an unknown song.
                if (failures == CHECKPOINTS_SECONDS.size) Result.Failed else Result.NotFound
            }
        } finally {
            source.close()
        }
    }

    private fun level(buffer: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) sum += buffer[i].toDouble() * buffer[i]
        val rms = sqrt(sum / count)
        val db = 20 * log10(rms.coerceAtLeast(1.0) / 32768.0)
        return ((db + 60) / 50).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        // Multiple of the 128-sample chunk, ~64 ms.
        const val READ_SIZE = 1024
        val CHECKPOINTS_SECONDS = intArrayOf(4, 8, 12)
    }
}
