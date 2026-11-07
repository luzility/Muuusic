package me.luzility.muuusic.desktop

import me.luzility.muuusic.data.account.SecretStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class FileSecretStore(private val file: File) : SecretStore {
    override fun load(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    override fun save(value: String) {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
            runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        }
        file.writeText(value)
    }

    override fun clear() {
        file.delete()
    }
}
