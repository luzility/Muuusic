package me.luzility.muuusic.data.account

import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AccountLink {
    class Offer(val host: String, val port: Int, val token: String, val key: ByteArray)

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun newOffer(host: String, port: Int): Offer =
        Offer(host, port, encoder.encodeToString(bytes(TOKEN_BYTES)), bytes(KEY_BYTES))

    fun uri(offer: Offer): String =
        "$SCHEME://link?h=${encode(offer.host)}&p=${offer.port}&t=${offer.token}&k=${encoder.encodeToString(offer.key)}"

    fun parse(text: String): Offer? = runCatching {
        val uri = URI(text.trim())
        if (uri.scheme != SCHEME || uri.host != "link") return null
        val query = uri.rawQuery.split('&').associate { part ->
            val (name, value) = part.split('=', limit = 2)
            name to URLDecoder.decode(value, Charsets.UTF_8.name())
        }
        Offer(
            host = query.getValue("h"),
            port = query.getValue("p").toInt(),
            token = query.getValue("t"),
            key = decoder.decode(query.getValue("k")).also { require(it.size == KEY_BYTES) },
        )
    }.getOrNull()

    fun seal(offer: Offer, cookie: String): String {
        val iv = bytes(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(offer.key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(offer.token.toByteArray())
        return JSONObject()
            .put("t", offer.token)
            .put("iv", encoder.encodeToString(iv))
            .put("d", encoder.encodeToString(cipher.doFinal(cookie.toByteArray())))
            .toString()
    }

    fun open(offer: Offer, line: String): String? = runCatching {
        val json = JSONObject(line)
        val token = json.getString("t")
        if (!MessageDigest.isEqual(token.toByteArray(), offer.token.toByteArray())) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(offer.key, "AES"), GCMParameterSpec(TAG_BITS, decoder.decode(json.getString("iv"))))
        cipher.updateAAD(token.toByteArray())
        String(cipher.doFinal(decoder.decode(json.getString("d"))))
    }.getOrNull()

    fun send(offer: Offer, cookie: String): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(offer.host, offer.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.getOutputStream().write("${seal(offer, cookie)}\n".toByteArray())
            socket.getInputStream().bufferedReader().readLine() == ACCEPTED
        }
    }.getOrDefault(false)

    private fun bytes(count: Int) = ByteArray(count).also(random::nextBytes)

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    const val ACCEPTED = "ok"
    private const val SCHEME = "muuusic"
    private const val TOKEN_BYTES = 16
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
}
