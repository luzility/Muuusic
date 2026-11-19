package me.luzility.muuusic.data.youtube

import me.luzility.muuusic.data.account.YouTubeAuth
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloader(
    private val client: OkHttpClient,
    private val session: () -> String? = { null },
) : Downloader() {

    override fun execute(request: Request): Response {
        val cookie = session()?.takeIf { signable(request.url()) }
        if (cookie != null) {
            val signed = send(request, cookie)
            if (signed.responseCode() != 401 && signed.responseCode() != 403) return signed
        }
        return send(request, null)
    }

    private fun send(request: Request, cookie: String?): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (cookie != null) {
            val url = request.url().toHttpUrl()
            YouTubeAuth.headers(cookie, "${url.scheme}://${url.host}").forEach { (name, value) -> builder.header(name, value) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    private fun signable(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.host != "music.youtube.com" && parsed.host != "www.youtube.com") return false
        val endpoint = parsed.encodedPath.removePrefix("/youtubei/v1/").takeIf { it != parsed.encodedPath } ?: return false
        return endpoint in SIGNED_ENDPOINTS
    }

    companion object {
        private val SIGNED_ENDPOINTS = setOf("browse", "search", "next", "music/get_search_suggestions")

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
