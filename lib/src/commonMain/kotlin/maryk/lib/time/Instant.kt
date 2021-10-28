package maryk.lib.time

import kotlinx.datetime.Clock

/** Defines the current Instant time */
object Instant {
    /** get the current epoch time in milliseconds since 1-1-1970 */
    fun getCurrentEpochTimeInMillis() = Clock.System.now().toEpochMilliseconds()
}
