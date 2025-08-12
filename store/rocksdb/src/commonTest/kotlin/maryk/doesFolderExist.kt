package maryk

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

fun doesFolderExist(path: String): Boolean {
    val meta = FileSystem.SYSTEM.metadataOrNull(path.toPath())
    return meta?.isDirectory == true
}
