package me.luzility.muuusic.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.util.Locale
import kotlin.system.exitProcess
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.desktop.ui.App
import me.luzility.muuusic.desktop.ui.Navigator
import me.luzility.muuusic.desktop.ui.logoPainter
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

fun main() {
    AppInfo.versionName = System.getProperty("muuusic.version") ?: "0.1.0"
    val services = DesktopServices(DesktopServices.defaultDataDir())
    val locale = Locale.getDefault()
    NewPipe.init(NewPipeDownloader(services.okHttp), Localization.fromLocale(locale), ContentCountry(locale.country.ifEmpty { "US" }))

    application(exitProcessOnExit = false) {
        val player = remember { DesktopPlayer(services, localFile = services.downloads::fileFor) }
        val navigator = remember { Navigator() }
        remember { DesktopPresence(services.scope, player, services.settings) }
        var open by remember { mutableStateOf(true) }
        var frame by remember { mutableStateOf<ComposeWindow?>(null) }
        lateinit var mpris: Mpris
        fun quit(restart: Boolean) {
            if (!open) return
            open = false
            mpris.close()
            player.release()
            Thread {
                Thread({ Thread.sleep(QUIT_TIMEOUT_MS); Runtime.getRuntime().halt(0) }, "quit-watchdog").apply { isDaemon = true }.start()
                services.streamCache.stop()
                player.destroy()
                if (restart) services.updater.relaunch()
                exitProcess(0)
            }.start()
        }
        mpris = remember {
            Mpris(
                services.scope,
                player,
                onRaise = { frame?.apply { isVisible = true; toFront(); requestFocus() } },
                onQuit = { quit(false) },
            )
        }
        if (open) Window(
            onCloseRequest = { quit(false) },
            title = "Muuusic",
            icon = logoPainter(),
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
            onKeyEvent = { event -> event.type == KeyEventType.KeyDown && handleKey(event, player, navigator) },
        ) {
            window.minimumSize = Dimension(900, 600)
            frame = window
            App(services, player, navigator, onRestart = { quit(true) })
        }
    }
}

private const val QUIT_TIMEOUT_MS = 3_000L

private fun handleKey(event: KeyEvent, player: DesktopPlayer, navigator: Navigator): Boolean {
    when {
        event.key == Key.Spacebar || event.key == Key.MediaPlayPause -> player.togglePlay()
        event.key == Key.MediaNext || (event.isCtrlPressed && event.key == Key.DirectionRight) -> player.next()
        event.key == Key.MediaPrevious || (event.isCtrlPressed && event.key == Key.DirectionLeft) -> player.previous()
        event.isAltPressed && event.key == Key.DirectionLeft -> navigator.back()
        else -> return false
    }
    return true
}
