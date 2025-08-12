package maryk

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

fun createFolder(path: String): Boolean {
    return try {
        val p = path.toPath()
        // If it already exists and is a directory, return true
        val meta = FileSystem.SYSTEM.metadataOrNull(p)
        if (meta?.isDirectory == true) return true
        FileSystem.SYSTEM.createDirectories(p)
        true
    } catch (_: Throwable) {
        false
    }
}
