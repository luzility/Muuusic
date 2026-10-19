package me.luzility.muuusic.data.update

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

enum class UpdateChannel { Release, Beta }

data class AppRelease(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?, val prerelease: Boolean)

object Versions {
    private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$""")

    // Same ordering as the versionCode in app/build.gradle.kts; null if the tag doesn't follow the scheme.
    fun code(version: String): Long? {
        val (major, minor, patch, beta) = pattern.matchEntire(version.trim())?.destructured ?: return null
        return ((major.toLong() * 100 + minor.toLong()) * 100 + patch.toLong()) * 100 + (beta.toLongOrNull() ?: 99)
    }

    fun isBeta(version: String) = "-beta." in version
}

class Updates(context: Context, private val client: OkHttpClient) {

    private val store = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** Version the user chose to skip in the startup prompt. */
    var skipped: String?
        get() = store.getString(KEY_SKIPPED, null)
        set(value) = store.edit { putString(KEY_SKIPPED, value) }

    fun dueForAutoCheck(now: Long = System.currentTimeMillis()) =
        now - store.getLong(KEY_LAST_CHECK, 0) > AUTO_CHECK_INTERVAL_MS

    /** The newest release on [channel] if it is newer than [current], otherwise null. */
    suspend fun newerThan(current: String, channel: UpdateChannel): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub ${response.code}")
            response.body?.string().orEmpty()
        }
        store.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        val latest = parse(body).filter { channel == UpdateChannel.Beta || !it.prerelease }
            .maxByOrNull { Versions.code(it.version) ?: -1 }
        val currentCode = Versions.code(current) ?: 0
        latest?.takeIf { (Versions.code(it.version) ?: -1) > currentCode }
    }

    companion object {
        const val REPO = "luzility/Muuusic"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_LAST_CHECK = "last_check"
        private const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

        // GitHub notes are Markdown; the app shows plain text, without the compare link at the end.
        internal fun cleanNotes(body: String): String =
            body.lines()
                .filterNot { it.startsWith("**Changes:**") || it.startsWith("**Full Changelog**") }
                .joinToString("\n") { it.replace("**", "").replace(Regex("^[-*] "), "• ") }
                .trim()

        internal fun parse(json: String): List<AppRelease> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                val release = array.getJSONObject(i)
                if (release.optBoolean("draft")) return@mapNotNull null
                val tag = release.optString("tag_name").removePrefix("v")
                if (Versions.code(tag) == null) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: JSONArray()
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
                AppRelease(
                    version = tag,
                    notes = cleanNotes(release.optString("body")),
                    pageUrl = release.optString("html_url"),
                    apkUrl = apk,
                    prerelease = release.optBoolean("prerelease"),
                )
            }
        }
    }
}
