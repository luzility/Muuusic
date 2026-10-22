package me.luzility.muuusic.data.discord

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

class DiscordTokens(val access: String, val refresh: String, val expiresAt: Long)

class DiscordAuthException(message: String) : IOException(message)

class DiscordAuth(private val client: OkHttpClient) {

    private class Pending(val state: String, val verifier: String, val result: CompletableDeferred<String>)

    @Volatile
    private var pending: Pending? = null

    fun begin(): Pair<String, CompletableDeferred<String>> {
        val verifier = random(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = random(16)
        val result = CompletableDeferred<String>()
        pending?.result?.cancel()
        pending = Pending(state, verifier, result)
        val url = AUTHORIZE.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("scope", SCOPES)
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", challenge)
            .build()
            .toString()
        return url to result
    }

    fun onRedirect(code: String?, state: String?, error: String?) {
        val current = pending ?: return
        when {
            error != null -> current.result.completeExceptionally(DiscordAuthException(error))
            code == null || state != current.state -> current.result.completeExceptionally(DiscordAuthException("state"))
            else -> current.result.complete(code)
        }
    }

    suspend fun exchange(code: String): DiscordTokens {
        val verifier = pending?.verifier ?: throw DiscordAuthException("no pending login")
        pending = null
        return token(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", verifier),
        )
    }

    suspend fun refresh(refreshToken: String): DiscordTokens =
        token(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refreshToken))

    private suspend fun token(form: FormBody.Builder): DiscordTokens = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(TOKEN).post(form.add("client_id", CLIENT_ID).build()).build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw DiscordAuthException("token ${response.code}")
            val json = JSONObject(body)
            DiscordTokens(
                access = json.getString("access_token"),
                refresh = json.optString("refresh_token"),
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 0) * 1000,
            )
        }
    }

    private fun random(bytes: Int): String {
        val data = ByteArray(bytes).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val CLIENT_ID = "1561658936879357994"
        const val REDIRECT_URI = "muuusic://discord"
        private const val SCOPES = "openid sdk.social_layer_presence"
        private const val AUTHORIZE = "https://discord.com/oauth2/authorize"
        private const val TOKEN = "https://discord.com/api/v10/oauth2/token"
    }
}
