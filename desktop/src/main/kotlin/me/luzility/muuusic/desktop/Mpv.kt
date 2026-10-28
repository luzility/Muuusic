package me.luzility.muuusic.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.DoubleByReference

class Mpv(private val onEvent: (MpvEvent) -> Unit) {
    private val lib = library
    private val handle: Pointer = lib.mpv_create() ?: error("mpv_create failed")
    @Volatile
    private var running = true

    init {
        setOption("vid", "no")
        setOption("video", "no")
        setOption("ytdl", "no")
        setOption("idle", "yes")
        setOption("audio-display", "no")
        setOption("input-default-bindings", "no")
        setOption("terminal", "no")
        check(lib.mpv_initialize(handle) >= 0) { "mpv_initialize failed" }
        observe("pause", FORMAT_FLAG)
        observe("duration", FORMAT_DOUBLE)
        observe("paused-for-cache", FORMAT_FLAG)
        Thread({ loop() }, "mpv-events").apply { isDaemon = true }.start()
    }

    fun setOption(name: String, value: String) {
        lib.mpv_set_option_string(handle, name, value)
    }

    fun set(name: String, value: String): Boolean = lib.mpv_set_property_string(handle, name, value) >= 0

    fun command(vararg args: String): Boolean = lib.mpv_command(handle, StringArray(arrayOf(*args))) >= 0

    fun getDouble(name: String): Double? {
        val value = DoubleByReference()
        return if (lib.mpv_get_property(handle, name, FORMAT_DOUBLE, value.pointer) >= 0) value.value else null
    }

    fun destroy() {
        running = false
        lib.mpv_terminate_destroy(handle)
    }

    private fun observe(name: String, format: Int) {
        lib.mpv_observe_property(handle, 0, name, format)
    }

    private fun loop() {
        while (running) {
            val event = lib.mpv_wait_event(handle, -1.0) ?: continue
            val data = event.getPointer(16)
            when (event.getInt(0)) {
                EVENT_SHUTDOWN -> return
                EVENT_END_FILE -> onEvent(MpvEvent.EndFile(data?.getInt(0) ?: END_ERROR))
                EVENT_FILE_LOADED -> onEvent(MpvEvent.FileLoaded)
                EVENT_PLAYBACK_RESTART -> onEvent(MpvEvent.PlaybackRestart)
                EVENT_PROPERTY_CHANGE -> data?.let { property(it) }?.let(onEvent)
            }
        }
    }

    private fun property(data: Pointer): MpvEvent? {
        val name = data.getPointer(0)?.getString(0) ?: return null
        val format = data.getInt(8)
        val value = data.getPointer(16)
        return when {
            value == null -> MpvEvent.Property(name, null)
            format == FORMAT_FLAG -> MpvEvent.Property(name, value.getInt(0) != 0)
            format == FORMAT_DOUBLE -> MpvEvent.Property(name, value.getDouble(0))
            else -> null
        }
    }

    @Suppress("FunctionName")
    private interface LibMpv : Library {
        fun mpv_create(): Pointer?
        fun mpv_initialize(handle: Pointer): Int
        fun mpv_terminate_destroy(handle: Pointer)
        fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
        fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
        fun mpv_get_property(handle: Pointer, name: String, format: Int, data: Pointer): Int
        fun mpv_command(handle: Pointer, args: StringArray): Int
        fun mpv_observe_property(handle: Pointer, userdata: Long, name: String, format: Int): Int
        fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
    }

    private interface LibC : Library {
        fun setlocale(category: Int, locale: String): Pointer?
    }

    companion object {
        const val END_EOF = 0
        const val END_ERROR = 4

        private const val FORMAT_FLAG = 3
        private const val FORMAT_DOUBLE = 5
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_END_FILE = 7
        private const val EVENT_FILE_LOADED = 8
        private const val EVENT_PLAYBACK_RESTART = 21
        private const val EVENT_PROPERTY_CHANGE = 22
        private const val LC_NUMERIC_LINUX = 1
        private const val LC_NUMERIC_MAC = 4

        private val library: LibMpv by lazy {
            useCNumericLocale()
            listOfNotNull(System.getenv("MUUUSIC_LIBMPV"), "mpv", "mpv-2", "libmpv-2").firstNotNullOfOrNull { name ->
                runCatching { Native.load(name, LibMpv::class.java) }.getOrNull()
            } ?: error("libmpv not found")
        }

        private fun useCNumericLocale() {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) return
            val category = if (os.contains("mac")) LC_NUMERIC_MAC else LC_NUMERIC_LINUX
            runCatching { Native.load("c", LibC::class.java).setlocale(category, "C") }
        }
    }
}

sealed interface MpvEvent {
    data class EndFile(val reason: Int) : MpvEvent
    data object FileLoaded : MpvEvent
    data object PlaybackRestart : MpvEvent
    data class Property(val name: String, val value: Any?) : MpvEvent
}
