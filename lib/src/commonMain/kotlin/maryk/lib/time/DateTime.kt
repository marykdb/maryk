package maryk.lib.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime

object DateTime {
    val MIN = Date.MIN.atTime(0, 0)
    val MAX_IN_SECONDS = Date.MAX.atTime(23, 59, 59)
    val MAX_IN_MILLIS = Date.MAX.atTime(23, 59, 59, 999000000)

    /** Create a DateTime by the amount of seconds since 01-01-1970 */
    fun ofEpochSecond(epochInSeconds: Long, milli: Short = 0) =
        Instant.fromEpochSeconds(epochInSeconds, milli * 1000000).toLocalDateTime(UTC)
}

/** Get a new DateTime with the date and time at UTC timezone */
fun LocalDateTime.Companion.nowUTC() = Clock.System.now().toLocalDateTime(UTC)
