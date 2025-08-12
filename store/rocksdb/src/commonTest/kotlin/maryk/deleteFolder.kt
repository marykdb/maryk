package maryk

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

fun deleteFolder(path: String): Boolean {
    return try {
        val p = path.toPath()
        deleteRecursively(p)
        true
    } catch (_: Throwable) {
        false
    }
}

private fun deleteRecursively(path: Path) {
    val fs = FileSystem.SYSTEM
    val meta = fs.metadataOrNull(path) ?: return
    if (meta.isDirectory) {
        // List children first
        fs.list(path).forEach { child: Path ->
            deleteRecursively(child)
        }
        // Then delete the directory itself
        fs.delete(path, mustExist = false)
    } else {
        fs.delete(path, mustExist = false)
    }
}
