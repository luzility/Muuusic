package me.luzility.muuusic.desktop

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import kotlin.system.exitProcess
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.desktop.ui.App
import me.luzility.muuusic.desktop.ui.MiniPlayer
import me.luzility.muuusic.desktop.ui.Navigator
import me.luzility.muuusic.desktop.ui.PlayerWindowTheme
import me.luzility.muuusic.desktop.ui.TrayMenu
import me.luzility.muuusic.desktop.ui.Typing
import me.luzility.muuusic.desktop.ui.WindowCorner
import me.luzility.muuusic.desktop.ui.logoPainter
import me.luzility.muuusic.desktop.ui.nearAnchor
import me.luzility.muuusic.desktop.ui.placeNear
import me.luzility.muuusic.desktop.ui.roundCorners
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
        var windowVisible by remember { mutableStateOf(true) }
        var miniPlayer by remember { mutableStateOf(false) }
        var trayMenu by remember { mutableStateOf<IntOffset?>(null) }
        var frame by remember { mutableStateOf<ComposeWindow?>(null) }
        val miniState = remember { WindowState(size = DpSize(400.dp, 120.dp), position = WindowPosition(Alignment.BottomEnd)) }
        lateinit var mpris: Mpris
        lateinit var tray: Tray
        fun quit(restart: Boolean) {
            if (!open) return
            open = false
            mpris.close()
            tray.close()
            player.release()
            Thread {
                Thread({ Thread.sleep(QUIT_TIMEOUT_MS); Runtime.getRuntime().halt(0) }, "quit-watchdog").apply { isDaemon = true }.start()
                services.streamCache.stop()
                player.destroy()
                if (restart) services.updater.relaunch()
                exitProcess(0)
            }.start()
        }
        fun showWindow() {
            miniPlayer = false
            windowVisible = true
            frame?.apply { isVisible = true; toFront(); requestFocus() }
        }
        fun openMiniPlayer() {
            miniPlayer = true
            windowVisible = false
        }
        fun hideToTray() {
            windowVisible = false
            miniPlayer = false
        }
        fun keepsPlaying() = tray.available.value && services.settings.prefs.value.closeToTray
        mpris = remember {
            Mpris(
                services.scope,
                player,
                onRaise = { showWindow() },
                onQuit = { quit(false) },
            )
        }
        tray = remember {
            Tray(
                services.scope,
                player,
                onActivate = { if (windowVisible && frame?.isActive == true) hideToTray() else showWindow() },
                onMenu = { x, y -> trayMenu = IntOffset(x, y) },
            )
        }
        if (!open) return@application
        Window(
            onCloseRequest = { if (keepsPlaying()) hideToTray() else quit(false) },
            visible = windowVisible,
            title = "Muuusic",
            icon = logoPainter(),
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
            onKeyEvent = { event -> event.type == KeyEventType.KeyDown && handleKey(event, player, navigator) },
        ) {
            window.minimumSize = Dimension(900, 600)
            frame = window
            App(services, player, navigator, onRestart = { quit(true) }, onMiniPlayer = { openMiniPlayer() })
        }
        if (miniPlayer) Window(
            onCloseRequest = { if (keepsPlaying()) hideToTray() else showWindow() },
            state = miniState,
            title = "Muuusic",
            icon = logoPainter(),
            undecorated = true,
            resizable = false,
            alwaysOnTop = true,
            onKeyEvent = { event -> event.type == KeyEventType.KeyDown && handleKey(event, player, navigator) },
        ) {
            DisposableEffect(window) { onDispose(roundCorners(window, WindowCorner)) }
            WindowDraggableArea {
                PlayerWindowTheme(services, player) {
                    MiniPlayer(onExpand = { showWindow() }, onClose = { if (keepsPlaying()) hideToTray() else showWindow() })
                }
            }
        }
        trayMenu?.let { anchor ->
            key(anchor) {
                val dismiss = { trayMenu = null }
                DialogWindow(
                    onCloseRequest = dismiss,
                    state = rememberDialogState(
                        size = DpSize(300.dp, Dp.Unspecified),
                        position = nearAnchor(anchor.x, anchor.y, 300, 280).let { WindowPosition(it.x.dp, it.y.dp) },
                    ),
                    title = "Muuusic",
                    icon = logoPainter(),
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = true,
                    onKeyEvent = { event -> if (event.key == Key.Escape) dismiss().let { true } else false },
                ) {
                    DisposableEffect(window) { onDispose(roundCorners(window, WindowCorner)) }
                    DisposableEffect(window) {
                        val focus = object : WindowAdapter() {
                            override fun windowLostFocus(e: WindowEvent) = dismiss()
                        }
                        val opened = object : WindowAdapter() {
                            override fun windowOpened(e: WindowEvent) = placeNear(window, anchor.x, anchor.y)
                        }
                        window.addWindowFocusListener(focus)
                        window.addWindowListener(opened)
                        onDispose {
                            window.removeWindowFocusListener(focus)
                            window.removeWindowListener(opened)
                        }
                    }
                    LaunchedEffect(window) {
                        placeNear(window, anchor.x, anchor.y)
                        window.toFront()
                        window.requestFocus()
                    }
                    PlayerWindowTheme(services, player) {
                        TrayMenu(
                            windowVisible = windowVisible,
                            miniPlayer = miniPlayer,
                            onToggleWindow = {
                                dismiss()
                                if (windowVisible) hideToTray() else showWindow()
                            },
                            onToggleMiniPlayer = {
                                dismiss()
                                if (miniPlayer) showWindow() else openMiniPlayer()
                            },
                            onQuit = {
                                dismiss()
                                quit(false)
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val QUIT_TIMEOUT_MS = 3_000L

private fun handleKey(event: KeyEvent, player: DesktopPlayer, navigator: Navigator): Boolean {
    val typing = Typing.active
    when {
        (event.key == Key.Spacebar && !typing) || event.key == Key.MediaPlayPause -> player.togglePlay()
        event.key == Key.MediaNext || (!typing && event.isCtrlPressed && event.key == Key.DirectionRight) -> player.next()
        event.key == Key.MediaPrevious || (!typing && event.isCtrlPressed && event.key == Key.DirectionLeft) -> player.previous()
        !typing && event.isAltPressed && event.key == Key.DirectionLeft -> navigator.back()
        else -> return false
    }
    return true
}
