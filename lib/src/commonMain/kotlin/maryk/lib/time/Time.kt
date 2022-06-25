package maryk.lib.time

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object Time {
    val MIN = LocalTime(0, 0, 0)
    val MAX_IN_SECONDS = LocalTime(23, 59, 59)
    val MAX_IN_MILLIS = LocalTime(23, 59, 59, 999_000_000)
    val MIDNIGHT = LocalTime(0, 0, 0, 0)
}

fun LocalTime.Companion.nowUTC() = Clock.System.now().toLocalDateTime(TimeZone.UTC).time
