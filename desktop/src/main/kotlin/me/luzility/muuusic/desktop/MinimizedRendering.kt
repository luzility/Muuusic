package me.luzility.muuusic.desktop

import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun pauseRenderingWhileMinimized(window: Window): () -> Unit {
    val listener = object : WindowAdapter() {
        override fun windowStateChanged(e: WindowEvent) = update()
        override fun windowIconified(e: WindowEvent) = update()
        override fun windowDeiconified(e: WindowEvent) = update()

        fun update() {
            val minimized = (window as? Frame)?.let { it.extendedState and Frame.ICONIFIED != 0 } == true
            skiaLayers(window).forEach { layer -> if (layer.isVisible == minimized) layer.isVisible = !minimized }
        }
    }
    window.addWindowListener(listener)
    window.addWindowStateListener(listener)
    return {
        window.removeWindowListener(listener)
        window.removeWindowStateListener(listener)
    }
}

private fun skiaLayers(container: Container): List<SkiaLayer> = container.components.flatMap { component ->
    when (component) {
        is SkiaLayer -> listOf(component)
        is Container -> skiaLayers(component)
        else -> emptyList()
    }
}
