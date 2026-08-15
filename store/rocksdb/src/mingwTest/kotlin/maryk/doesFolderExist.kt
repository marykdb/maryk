@file:OptIn(ExperimentalForeignApi::class)

package maryk

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.closedir
import platform.posix.opendir

actual fun doesFolderExist(path: String): Boolean {
    val directory = opendir(path)
    directory ?: return false
    closedir(directory)
    return true
}
