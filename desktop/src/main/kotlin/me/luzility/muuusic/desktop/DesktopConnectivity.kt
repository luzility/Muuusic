package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class DesktopConnectivity(scope: CoroutineScope) {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                _online.value = reachable()
                delay(if (_online.value) ONLINE_CHECK_MS else OFFLINE_CHECK_MS)
            }
        }
    }

    private fun reachable(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("music.youtube.com", 443), TIMEOUT_MS) }
    }.isSuccess

    private companion object {
        const val ONLINE_CHECK_MS = 30_000L
        const val OFFLINE_CHECK_MS = 5_000L
        const val TIMEOUT_MS = 4_000
    }
}
