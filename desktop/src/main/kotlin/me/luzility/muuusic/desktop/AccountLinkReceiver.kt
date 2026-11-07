package me.luzility.muuusic.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.account.AccountLink
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException

class AccountLinkReceiver {
    private val server = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0)) }
    val offer: AccountLink.Offer? = lanAddress()?.let { AccountLink.newOffer(it, server.localPort) }

    suspend fun await(): String? = withContext(Dispatchers.IO) {
        val current = offer ?: return@withContext null
        server.soTimeout = TIMEOUT_MS
        while (true) {
            val socket = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                return@withContext null
            } catch (_: SocketException) {
                return@withContext null
            }
            socket.use {
                it.soTimeout = READ_TIMEOUT_MS
                val line = runCatching { it.getInputStream().bufferedReader().readLine() }.getOrNull()
                val cookie = line?.let { text -> AccountLink.open(current, text) }
                if (cookie != null) {
                    runCatching { it.getOutputStream().write("${AccountLink.ACCEPTED}\n".toByteArray()) }
                    return@withContext cookie
                }
            }
        }
        null
    }

    fun close() = runCatching { server.close() }

    private fun lanAddress(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress

    private companion object {
        const val TIMEOUT_MS = 5 * 60_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
