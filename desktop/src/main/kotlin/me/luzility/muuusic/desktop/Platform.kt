package me.luzility.muuusic.desktop

import java.io.File

object Platform {
    private val os = System.getProperty("os.name").lowercase()
    val isWindows = os.contains("win")
    val isMac = os.contains("mac")

    fun bundled(name: String): File? =
        System.getProperty("compose.application.resources.dir")?.let { File(it, name) }?.takeIf { it.exists() }
}
