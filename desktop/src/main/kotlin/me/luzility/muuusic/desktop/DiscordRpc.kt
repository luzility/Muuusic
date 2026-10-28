package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.util.UUID

data class ListeningActivity(
    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val startMs: Long,
    val endMs: Long?,
)

class DiscordRpc(scope: CoroutineScope) {
    private val changes = Channel<Unit>(Channel.CONFLATED)
    @Volatile
    private var wanted: ListeningActivity? = null
    private var connection: IpcConnection? = null
    private var sent: ListeningActivity? = null

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                withTimeoutOrNull(RETRY_MS) { changes.receive() }
                val activity = wanted
                if (activity == sent && (connection != null || activity == null)) continue
                runCatching {
                    val ipc = connection ?: connect()?.also { connection = it } ?: return@runCatching
                    ipc.send(OP_FRAME, setActivity(activity))
                    ipc.receive()
                    sent = activity
                }.onFailure {
                    connection?.close()
                    connection = null
                    sent = null
                }
            }
        }
    }

    fun update(activity: ListeningActivity?) {
        wanted = activity
        changes.trySend(Unit)
    }

    private fun connect(): IpcConnection? {
        val ipc = candidates().firstNotNullOfOrNull { runCatching { open(it) }.getOrNull() } ?: return null
        return runCatching {
            ipc.send(OP_HANDSHAKE, JSONObject().put("v", 1).put("client_id", CLIENT_ID))
            ipc.receive()
            ipc
        }.getOrElse {
            ipc.close()
            null
        }
    }

    private fun setActivity(activity: ListeningActivity?): JSONObject {
        val args = JSONObject().put("pid", ProcessHandle.current().pid())
        if (activity != null) {
            val entry = JSONObject()
                .put("type", LISTENING)
                .put("status_display_type", STATUS_SHOWS_DETAILS)
                .put("details", activity.title.take(128))
                .put("state", activity.artist.ifBlank { "Muuusic" }.take(128))
                .put("timestamps", JSONObject().put("start", activity.startMs).apply { activity.endMs?.let { put("end", it) } })
                .put(
                    "buttons",
                    JSONArray()
                        .put(JSONObject().put("label", BUTTON_LISTEN).put("url", "https://music.youtube.com/watch?v=${activity.songId}"))
                        .put(JSONObject().put("label", BUTTON_APP).put("url", APP_URL)),
                )
            activity.artworkUrl?.let { url ->
                entry.put("assets", JSONObject().put("large_image", url).put("large_text", activity.title.take(128)))
            }
            args.put("activity", entry)
        }
        return JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", UUID.randomUUID().toString())
    }

    private fun candidates(): List<String> {
        if (Platform.isWindows) return (0..9).map { "\\\\.\\pipe\\discord-ipc-$it" }
        val bases = listOfNotNull(System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), "/tmp").distinct()
        val folders = bases.flatMap { base ->
            listOf(base, "$base/app/com.discordapp.Discord", "$base/app/com.discordapp.DiscordCanary", "$base/snap.discord", "$base/.flatpak/dev.vencord.Vesktop/xdg-run")
        }
        return folders.flatMap { folder -> (0..9).map { "$folder/discord-ipc-$it" } }.filter { File(it).exists() }
    }

    private fun open(path: String): IpcConnection = if (Platform.isWindows) PipeConnection(path) else SocketConnection(path)

    private interface IpcConnection : Closeable {
        fun write(bytes: ByteArray)
        fun read(count: Int): ByteArray

        fun send(op: Int, payload: JSONObject) {
            val body = payload.toString().toByteArray()
            val frame = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).put(body)
            write(frame.array())
        }

        fun receive(): JSONObject {
            val header = ByteBuffer.wrap(read(8)).order(ByteOrder.LITTLE_ENDIAN)
            val op = header.getInt()
            val size = header.getInt()
            val body = JSONObject(String(read(size)))
            if (op == OP_CLOSE) throw IOException("Discord closed the connection")
            return body
        }
    }

    private class SocketConnection(path: String) : IpcConnection {
        private val channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(path)) }

        override fun write(bytes: ByteArray) {
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
        }

        override fun read(count: Int): ByteArray {
            val buffer = ByteBuffer.allocate(count)
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw IOException("Discord went away")
            return buffer.array()
        }

        override fun close() = channel.close()
    }

    private class PipeConnection(path: String) : IpcConnection {
        private val pipe = RandomAccessFile(path, "rw")

        override fun write(bytes: ByteArray) = pipe.write(bytes)

        override fun read(count: Int): ByteArray = ByteArray(count).also { pipe.readFully(it) }

        override fun close() = pipe.close()
    }

    private companion object {
        const val CLIENT_ID = "1561658936879357994"
        const val OP_HANDSHAKE = 0
        const val OP_FRAME = 1
        const val OP_CLOSE = 2
        const val LISTENING = 2
        const val STATUS_SHOWS_DETAILS = 2
        const val RETRY_MS = 15_000L
        const val APP_URL = "https://github.com/luzility/Muuusic"
        const val BUTTON_LISTEN = "Listen on YouTube Music"
        const val BUTTON_APP = "Get Muuusic"
    }
}
