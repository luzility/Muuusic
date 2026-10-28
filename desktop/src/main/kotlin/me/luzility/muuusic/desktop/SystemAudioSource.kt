package me.luzility.muuusic.desktop

import me.luzility.muuusic.data.recognition.PcmSource
import me.luzility.muuusic.data.recognition.ShazamSignature
import java.io.File
import java.io.InputStream

class SystemAudioSource private constructor(private val process: Process) : PcmSource {
    private val input: InputStream = process.inputStream
    private var bytes = ByteArray(0)
    private var carry = -1

    override fun read(buffer: ShortArray): Int {
        if (bytes.size < buffer.size * 2) bytes = ByteArray(buffer.size * 2)
        var offset = 0
        if (carry >= 0) {
            bytes[0] = carry.toByte()
            offset = 1
            carry = -1
        }
        val read = input.read(bytes, offset, buffer.size * 2 - offset)
        if (read < 0) return -1
        val total = offset + read
        val samples = total / 2
        for (i in 0 until samples) {
            buffer[i] = ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        }
        if (total % 2 == 1) carry = bytes[total - 1].toInt() and 0xFF
        return samples
    }

    override fun close() {
        process.destroy()
    }

    companion object {
        private val rate = ShazamSignature.SAMPLE_RATE.toString()

        private val commands = listOf(
            listOf("parec", "--device=@DEFAULT_MONITOR@", "--latency-msec=50", "--format=s16le", "--rate=$rate", "--channels=1", "--raw"),
            listOf("pw-record", "-P", "{ stream.capture.sink=true }", "--rate", rate, "--channels", "1", "--format", "s16", "-"),
        )

        val isSupported: Boolean by lazy { commands.any { onPath(it.first()) } }

        fun open(): SystemAudioSource? {
            val command = commands.firstOrNull { onPath(it.first()) } ?: return null
            val process = runCatching {
                ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull() ?: return null
            return SystemAudioSource(process)
        }

        private fun onPath(name: String): Boolean =
            System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, name).canExecute() }
    }
}
