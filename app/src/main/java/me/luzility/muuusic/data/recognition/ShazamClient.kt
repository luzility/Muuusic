package me.luzility.muuusic.data.recognition

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.TimeZone
import java.util.UUID

data class RecognizedSong(val title: String, val artist: String, val coverUrl: String?)

// Unofficial endpoint, same one SongRec and shazamio use. Can break at any time.
class ShazamClient(private val client: OkHttpClient) {

    fun recognize(signature: ShazamSignature): RecognizedSong? = recognize(signature.encode(), signature.sampleCount)

    fun recognize(data: ByteArray, sampleCount: Int): RecognizedSong? {
        val body = JSONObject()
            .put("timezone", TimeZone.getDefault().id)
            .put(
                "signature",
                JSONObject()
                    .put("uri", "data:audio/vnd.shazam.sig;base64," + Base64.getEncoder().encodeToString(data))
                    .put("samplems", sampleCount * 1000L / ShazamSignature.SAMPLE_RATE),
            )
            .put("timestamp", System.currentTimeMillis())
            .put("context", JSONObject())
            .put("geolocation", JSONObject())

        val id1 = UUID.randomUUID().toString().uppercase()
        val id2 = UUID.randomUUID().toString().uppercase()
        val request = Request.Builder()
            .url(
                "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$id1/$id2" +
                    "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"
            )
            .header("User-Agent", USER_AGENT)
            .header("Content-Language", "en_US")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Shazam ${response.code}")
            val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
            val track = json.optJSONObject("track") ?: return null
            val images = track.optJSONObject("images")
            return RecognizedSong(
                title = track.optString("title"),
                artist = track.optString("subtitle"),
                coverUrl = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                    ?: images?.optString("coverart")?.takeIf { it.isNotBlank() },
            )
        }
    }

    private companion object {
        const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7 Build/TQ3A.230805.001)"
    }
}
