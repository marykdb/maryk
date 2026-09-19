@file:OptIn(ExperimentalForeignApi::class)

package maryk

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager

actual fun doesFolderExist(path: String): Boolean = memScoped {
    val isDir = alloc<BooleanVar>()
    val fileManager = NSFileManager.defaultManager
    val exists = fileManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
    exists && isDir.value
}
