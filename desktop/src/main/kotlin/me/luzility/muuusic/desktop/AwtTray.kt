package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class AwtTray(
    private val scope: CoroutineScope,
    private val player: DesktopPlayer,
    private val onActivate: () -> Unit,
    private val onMenu: (x: Int, y: Int) -> Unit,
) : AppTray {
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()
    private var icon: TrayIcon? = null

    init {
        scope.launch(Dispatchers.Main) {
            if (!SystemTray.isSupported()) return@launch
            val tray = SystemTray.getSystemTray()
            val image = trayIcon(tray.trayIconSize.width.coerceAtLeast(16)) ?: return@launch
            val trayIcon = TrayIcon(image, "Muuusic").apply {
                isImageAutoSize = true
                addMouseListener(object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent) {
                        when (e.button) {
                            MouseEvent.BUTTON1 -> onActivate()
                            MouseEvent.BUTTON2 -> player.togglePlay()
                            MouseEvent.BUTTON3 -> onMenu(e.xOnScreen, e.yOnScreen)
                        }
                    }
                })
            }
            if (runCatching { tray.add(trayIcon) }.isFailure) return@launch
            icon = trayIcon
            _available.value = true
            player.state.distinctUntilChangedBy { it.current?.id }.collect { state ->
                trayIcon.toolTip = state.current?.let { "${it.title} · ${it.artist}".take(TOOLTIP_LIMIT) } ?: "Muuusic"
            }
        }
    }

    override fun close() {
        _available.value = false
        icon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        icon = null
    }

    private companion object {
        const val TOOLTIP_LIMIT = 127
    }
}
