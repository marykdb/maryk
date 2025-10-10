@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package maryk

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileManager

actual fun createFolder(path: String): Boolean = memScoped {
    val errorRef = alloc<ObjCObjectVar<NSError?>>()
    val result = NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = errorRef.ptr
    )
    val error = errorRef.value

    if (error != null) {
        throw Exception(error.localizedDescription)
    }

    result
}
