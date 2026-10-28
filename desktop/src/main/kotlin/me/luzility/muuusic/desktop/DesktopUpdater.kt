package me.luzility.muuusic.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.update.AppRelease
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

class DesktopUpdater(private val client: OkHttpClient, private val cacheDir: File) {
    enum class Install { AppImage, Deb, None }

    val install: Install = when {
        System.getenv("APPIMAGE") != null -> Install.AppImage
        launcher()?.startsWith(DEB_ROOT) == true -> Install.Deb
        else -> Install.None
    }

    fun assetFor(release: AppRelease): String? = when (install) {
        Install.AppImage -> release.assets.entries.firstOrNull { it.key.endsWith(".AppImage") }?.value
        Install.Deb -> release.assets.entries.firstOrNull { it.key.endsWith(".deb") }?.value
        Install.None -> null
    }

    suspend fun install(url: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        when (install) {
            Install.AppImage -> {
                val target = File(System.getenv("APPIMAGE"))
                val temp = File(target.parentFile, ".${target.name}.new")
                download(url, temp, onProgress)
                temp.setExecutable(true)
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                true
            }
            Install.Deb -> {
                val file = File(cacheDir, "muuusic-update.deb")
                download(url, file, onProgress)
                val code = ProcessBuilder("pkexec", "apt-get", "install", "-y", "--allow-downgrades", file.absolutePath)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor()
                file.delete()
                code == 0
            }
            Install.None -> false
        }
    }

    fun relaunch() {
        val command = when (install) {
            Install.AppImage -> System.getenv("APPIMAGE")
            Install.Deb -> "$DEB_ROOT/bin/Muuusic"
            Install.None -> return
        }
        ProcessBuilder(command).start()
    }

    private suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        target.parentFile?.mkdirs()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body ?: throw IOException("Empty body")
            val total = body.contentLength().takeIf { length -> length > 0 }
            var done = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
    }

    private fun launcher(): String? = ProcessHandle.current().info().command().orElse(null)

    private companion object {
        const val DEB_ROOT = "/opt/muuusic"
    }
}
