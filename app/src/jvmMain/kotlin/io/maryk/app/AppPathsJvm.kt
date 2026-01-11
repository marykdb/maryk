package io.maryk.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

actual fun storesFilePath(): String {
    val home = System.getProperty("user.home").orEmpty().ifBlank { "." }
    return "$home/.maryk/app/stores.conf"
}

actual fun ensureParentDirectory(path: String) {
    val parent: Path = Paths.get(path).parent ?: return
    Files.createDirectories(parent)
}
