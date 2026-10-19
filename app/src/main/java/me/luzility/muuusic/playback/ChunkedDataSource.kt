package me.luzility.muuusic.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.math.min

// googlevideo intermittently 403s unbounded requests (no Range, or `bytes=N-`); small bounded
// ranges work. Total size comes from the URL's `clen` parameter.
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : DataSource {

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkSize)
    }

    private var spec: DataSpec? = null
    private var position = 0L
    private var end = C.LENGTH_UNSET.toLong()
    private var lastChunkShort = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        end = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.position + dataSpec.length
            total != null -> total
            else -> C.LENGTH_UNSET.toLong()
        }
        openChunk()
        return if (end != C.LENGTH_UNSET.toLong()) end - position else C.LENGTH_UNSET.toLong()
    }

    private fun openChunk() {
        val spec = checkNotNull(spec)
        val length = if (end != C.LENGTH_UNSET.toLong()) min(chunkSize, end - position) else chunkSize
        val opened = upstream.open(spec.subrange(position - spec.position, length))
        lastChunkShort = opened != C.LENGTH_UNSET.toLong() && opened < length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (end != C.LENGTH_UNSET.toLong() && position >= end) return C.RESULT_END_OF_INPUT

        var read = upstream.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) {
            upstream.close()
            if (end == C.LENGTH_UNSET.toLong() && lastChunkShort) return C.RESULT_END_OF_INPUT
            openChunk()
            read = upstream.read(buffer, offset, length)
            if (read == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT
        }
        position += read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        upstream.close()
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 1024L * 1024
    }
}
