package me.luzility.muuusic.data.account

import java.security.MessageDigest

object YouTubeAuth {
    private val sapisidPattern = Regex("(?:^|;\\s*)(?:__Secure-3PAPISID|SAPISID)=([^;]+)")

    fun headers(cookie: String, origin: String): Map<String, String> {
        val headers = mutableMapOf("Cookie" to cookie, "X-Goog-AuthUser" to "0")
        val sapisid = sapisidPattern.find(cookie)?.groupValues?.get(1) ?: return headers
        val time = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$time $sapisid $origin".toByteArray())
        headers["Authorization"] = "SAPISIDHASH ${time}_${digest.joinToString("") { "%02x".format(it) }}"
        return headers
    }
}
