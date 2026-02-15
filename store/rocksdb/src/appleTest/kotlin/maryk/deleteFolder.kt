@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, UnsafeNumber::class)
@file:Suppress("RemoveRedundantCallsOfConversionMethods")

package maryk

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSCocoaErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileNoSuchFileError
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSThread
import platform.posix.EBUSY

actual fun deleteFolder(path: String): Boolean = memScoped {
    val fileManager = NSFileManager.defaultManager
    var lastError: NSError? = null

    repeat(5) { attempt ->
        val errorRef = alloc<ObjCObjectVar<NSError?>>()
        val result = fileManager.removeItemAtPath(
            path = path,
            error = errorRef.ptr
        )
        val error = errorRef.value

        if (result) {
            return@memScoped true
        }

        if (error == null) {
            return@memScoped result
        }

        val domain = error.domain
        val code = error.code.toInt()

        when {
            domain == NSCocoaErrorDomain && code == NSFileNoSuchFileError.toInt() -> {
                return@memScoped true
            }
            domain == NSPOSIXErrorDomain && code == EBUSY && attempt < 4 -> {
                lastError = error
                NSThread.sleepForTimeInterval(0.1)
            }
            else -> throw IllegalStateException(error.localizedDescription)
        }
    }

    lastError?.let { throw IllegalStateException(it.localizedDescription) }

    false
}
