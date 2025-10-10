@file:OptIn(ExperimentalForeignApi::class)

package maryk

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.opendir

actual fun doesFolderExist(path: String): Boolean {
    val directory = opendir(path)
    return directory != null
}
