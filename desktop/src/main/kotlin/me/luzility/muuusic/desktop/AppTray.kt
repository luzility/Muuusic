package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

interface AppTray {
    val available: StateFlow<Boolean>
    fun close()

    companion object {
        fun create(scope: CoroutineScope, player: DesktopPlayer, onActivate: () -> Unit, onMenu: (x: Int, y: Int) -> Unit): AppTray =
            if (Platform.isWindows) AwtTray(scope, player, onActivate, onMenu) else StatusNotifierTray(scope, player, onActivate, onMenu)
    }
}

private val trayIconSource: BufferedImage? by lazy {
    AppTray::class.java.classLoader.getResourceAsStream("icon/muuusic.png")?.use { ImageIO.read(it) }
}

fun trayIcon(size: Int): BufferedImage? {
    val source = trayIconSource ?: return null
    return BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(source, 0, 0, size, size, null)
            dispose()
        }
    }
}
