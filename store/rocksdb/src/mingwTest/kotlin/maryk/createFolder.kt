package maryk

import platform.posix.mkdir

actual fun createFolder(path: String): Boolean =
    when (mkdir(path)) {
        0 -> true
        else -> false
    }
