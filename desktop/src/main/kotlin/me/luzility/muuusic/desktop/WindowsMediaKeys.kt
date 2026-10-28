package me.luzility.muuusic.desktop

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.luzility.muuusic.desktop.playback.DesktopPlayer

class WindowsMediaKeys(private val scope: CoroutineScope, private val player: DesktopPlayer) {
    @Volatile
    private var threadId = 0

    init {
        Thread(::listen, "media-keys").apply { isDaemon = true }.start()
    }

    fun close() {
        val id = threadId
        if (id != 0) runCatching { User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, WPARAM(0), LPARAM(0)) }
    }

    private fun listen() {
        val user32 = User32.INSTANCE
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        val registered = KEYS.keys.filter { id -> runCatching { user32.RegisterHotKey(null, id, WinUser.MOD_NOREPEAT, KEYS.getValue(id)) }.getOrDefault(false) }
        if (registered.isEmpty()) return
        val message = WinUser.MSG()
        while (user32.GetMessage(message, null, 0, 0) > 0) {
            if (message.message != WinUser.WM_HOTKEY) continue
            val id = message.wParam.toInt()
            scope.launch(Dispatchers.Main) {
                when (id) {
                    PLAY_PAUSE -> player.togglePlay()
                    NEXT -> player.next()
                    PREVIOUS -> player.previous()
                    STOP -> player.pause()
                }
            }
        }
        registered.forEach { user32.UnregisterHotKey(null, it) }
    }

    private companion object {
        const val PLAY_PAUSE = 1
        const val NEXT = 2
        const val PREVIOUS = 3
        const val STOP = 4
        val KEYS = mapOf(PLAY_PAUSE to 0xB3, NEXT to 0xB0, PREVIOUS to 0xB1, STOP to 0xB2)
    }
}
