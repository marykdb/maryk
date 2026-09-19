package maryk

import platform.posix.S_IRWXU
import platform.posix.mkdir

actual fun createFolder(path: String): Boolean =
    when (mkdir(path, S_IRWXU.toUInt())) {
        0 -> true
        else -> false
    }
