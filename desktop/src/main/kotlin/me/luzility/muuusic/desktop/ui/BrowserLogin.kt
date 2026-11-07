package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefCookieManager
import java.awt.EventQueue
import java.io.File

object Chromium {
    sealed interface State {
        data object Idle : State
        data class Preparing(val progress: Float?) : State
        data object Ready : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()

    suspend fun prepare(dataDir: File) = lock.withLock {
        if (_state.value == State.Ready) return@withLock
        _state.value = State.Preparing(null)
        withContext(Dispatchers.IO) {
            runCatching {
                val install = File(dataDir, "kcef")
                KCEF.init(
                    builder = {
                        installDir(install)
                        progress {
                            onDownloading { percent -> _state.value = State.Preparing((percent / 100f).coerceIn(0f, 1f)) }
                            onInitialized { _state.value = State.Ready }
                        }
                        settings {
                            cachePath = File(dataDir, "kcef-cache").absolutePath
                            browserSubProcessPath = File(install, "jcef_helper").absolutePath
                            resourcesDirPath = install.absolutePath
                            localesDirPath = File(install, "locales").absolutePath
                        }
                    },
                    onError = { _state.value = State.Failed },
                    onRestartRequired = { _state.value = State.Failed },
                )
            }.onFailure { _state.value = State.Failed }
        }
    }

    fun dispose() {
        if (_state.value == State.Ready) runCatching { KCEF.disposeBlocking() }
    }
}

@Composable
fun BrowserLoginWindow(onSignedIn: (String) -> Unit, onClose: () -> Unit) {
    val services = LocalServices.current
    val state by Chromium.state.collectAsState()
    LaunchedEffect(Unit) { Chromium.prepare(services.dataDir) }
    DialogWindow(
        onCloseRequest = onClose,
        title = str("youtube_sign_in"),
        icon = logoPainter(),
        state = rememberDialogState(size = DpSize(520.dp, 760.dp)),
    ) {
        PlayerWindowTheme(services, LocalPlayer.current) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                when (val current = state) {
                    Chromium.State.Ready -> ChromiumLogin(onSignedIn)
                    Chromium.State.Failed -> Message(str("browser_failed"))
                    is Chromium.State.Preparing -> Preparing(current.progress)
                    Chromium.State.Idle -> Preparing(null)
                }
            }
        }
    }
}

@Composable
private fun Preparing(progress: Float?) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress == null) {
            LoadingIndicator(Modifier.size(56.dp))
            Text(str("browser_preparing"), textAlign = TextAlign.Center)
        } else {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp))
            Text(str("browser_downloading", (progress * 100).toInt()), textAlign = TextAlign.Center)
        }
        Text(str("browser_once"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChromiumLogin(onSignedIn: (String) -> Unit) {
    val signedIn by rememberUpdatedState(onSignedIn)
    val client = remember { KCEF.newClientBlocking() }
    val browser = remember {
        CefCookieManager.getGlobalManager().deleteCookies("", "")
        var done = false
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (done || !frame.isMain || browser.url?.contains("youtube.com") != true) return
                readCookies { cookie ->
                    if (done || "SAPISID=" !in cookie) return@readCookies
                    done = true
                    EventQueue.invokeLater { signedIn(cookie) }
                }
            }
        })
        client.createBrowser(LOGIN_URL, CefRendering.DEFAULT, false)
    }
    DisposableEffect(Unit) {
        onDispose {
            browser.close(true)
            client.dispose()
        }
    }
    SwingPanel(factory = { browser.uiComponent }, modifier = Modifier.fillMaxSize())
}

private fun readCookies(done: (String) -> Unit) {
    val parts = mutableListOf<String>()
    CefCookieManager.getGlobalManager().visitUrlCookies(MUSIC_URL, true) { cookie, count, total, _ ->
        parts += "${cookie.name}=${cookie.value}"
        if (count == total - 1) done(parts.joinToString("; "))
        true
    }
}

private const val MUSIC_URL = "https://music.youtube.com"
private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
