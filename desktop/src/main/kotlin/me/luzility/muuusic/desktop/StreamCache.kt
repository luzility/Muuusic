package me.luzility.muuusic.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.luzility.muuusic.data.settings.AppSettings
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.data.youtube.YouTubeRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class StreamCache(
    private val dir: File,
    private val client: OkHttpClient,
    private val youtube: YouTubeRepository,
    private val settings: AppSettings,
) {
    private val writers = ConcurrentHashMap.newKeySet<String>()
    private val open = ConcurrentHashMap<String, Int>()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        executor = Executors.newVirtualThreadPerTaskExecutor()
        createContext("/song/") { exchange -> runCatching { serve(exchange) }; exchange.close() }
        start()
    }

    init {
        dir.mkdirs()
    }

    fun urlFor(videoId: String): String = "http://127.0.0.1:${server.address.port}/song/$videoId"

    fun bytesUsed(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    fun clear() {
        dir.listFiles().orEmpty().filter { !open.containsKey(keyOf(it)) }.forEach { it.delete() }
    }

    fun stop() = server.stop(0)

    private fun serve(exchange: HttpExchange) {
        val videoId = exchange.requestURI.path.removePrefix("/song/").takeIf { it.matches(VIDEO_ID) }
            ?: return exchange.sendResponseHeaders(404, -1)
        val key = "${videoId}_${settings.prefs.value.audioQuality.name}"
        open.merge(key, 1, Int::plus)
        try {
            val complete = File(dir, "$key.audio")
            val range = parseRange(exchange.requestHeaders.getFirst("Range"))
            if (complete.exists()) {
                complete.setLastModified(System.currentTimeMillis())
                serveFile(exchange, complete, range)
            } else {
                serveStream(exchange, videoId, key, complete, range)
            }
        } finally {
            open.computeIfPresent(key) { _, count -> (count - 1).takeIf { it > 0 } }
        }
    }

    private fun serveFile(exchange: HttpExchange, file: File, range: LongRange?) {
        val total = file.length()
        val start = range?.first ?: 0
        val end = (range?.last?.takeIf { it >= 0 } ?: (total - 1)).coerceAtMost(total - 1)
        if (start > end) return exchange.sendResponseHeaders(416, -1)
        sendHeaders(exchange, start, end, total, partial = range != null)
        RandomAccessFile(file, "r").use { input ->
            input.seek(start)
            copy(input, exchange.responseBody, end - start + 1)
        }
    }

    private fun serveStream(exchange: HttpExchange, videoId: String, key: String, complete: File, range: LongRange?) {
        var url = youtube.resolveStreamUrl(videoId)
        val total = url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull() ?: probeSize(url)
            ?: return exchange.sendResponseHeaders(502, -1)
        val start = range?.first ?: 0
        val end = (range?.last?.takeIf { it >= 0 } ?: (total - 1)).coerceAtMost(total - 1)
        if (start > end) return exchange.sendResponseHeaders(416, -1)
        sendHeaders(exchange, start, end, total, partial = range != null)

        val part = File(dir, "$key.part")
        val body = exchange.responseBody
        var position = start
        val cached = part.length()
        if (position < cached) {
            RandomAccessFile(part, "r").use { input ->
                input.seek(position)
                val count = minOf(cached, end + 1) - position
                copy(input, body, count)
                position += count
            }
        }
        val writing = position == part.length() && writers.add(key)
        try {
            val sink = if (writing) FileOutputStream(part, true) else null
            sink.use {
                var retried = false
                while (position <= end) {
                    val chunkEnd = minOf(position + CHUNK_SIZE - 1, end)
                    val bytes = try {
                        fetch(url, position, chunkEnd)
                    } catch (e: IOException) {
                        if (retried) throw e
                        retried = true
                        youtube.invalidateStreamUrl(videoId)
                        url = youtube.resolveStreamUrl(videoId)
                        continue
                    }
                    sink?.write(bytes)
                    body.write(bytes)
                    position += bytes.size
                }
            }
            if (writing && part.length() == total && part.renameTo(complete)) evict()
        } finally {
            if (writing) writers.remove(key)
        }
    }

    private fun fetch(url: String, from: Long, to: Long): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Range", "bytes=$from-$to")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body.bytes().also { if (it.isEmpty()) throw IOException("Empty chunk") }
        }
    }

    private fun probeSize(url: String): Long? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", NewPipeDownloader.USER_AGENT).header("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { it.header("Content-Range")?.substringAfter('/')?.toLongOrNull() }
    }.getOrNull()

    private fun evict() {
        val limit = settings.prefs.value.cacheSizeMb.toLong() * 1024 * 1024
        val files = dir.listFiles().orEmpty().sortedBy { it.lastModified() }
        var used = files.sumOf { it.length() }
        for (file in files) {
            if (used <= limit) break
            if (open.containsKey(keyOf(file)) || keyOf(file) in writers) continue
            used -= file.length()
            file.delete()
        }
    }

    private fun sendHeaders(exchange: HttpExchange, start: Long, end: Long, total: Long, partial: Boolean) {
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        if (partial) exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$total")
        exchange.sendResponseHeaders(if (partial) 206 else 200, end - start + 1)
    }

    private fun copy(input: RandomAccessFile, output: OutputStream, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            left -= read
        }
    }

    private fun parseRange(header: String?): LongRange? {
        val match = header?.let { RANGE.find(it) } ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: -1
        return start..end
    }

    private fun keyOf(file: File) = file.name.substringBeforeLast('.')

    private companion object {
        const val CHUNK_SIZE = 512L * 1024
        val RANGE = Regex("bytes=(\\d+)-(\\d*)")
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
