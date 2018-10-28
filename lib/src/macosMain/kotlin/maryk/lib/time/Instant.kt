package maryk.lib.time

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.timeval

/** Defines the current Instant time */
actual object Instant {
    /** get the current epoch time in milliseconds since 1-1-1970 */
    actual fun getCurrentEpochTimeInMillis(): Long {
        memScoped {
            val now = alloc<timeval>()
            gettimeofday(now.ptr, null)

            return (now.tv_sec * 1000) + (now.tv_usec.toLong() / 1000)
        }
    }
}
