package maryk.lib.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val epochStart = LocalDate(1970, 1, 1)

/** Date by year, month and day. */
object Date {
    val MIN = LocalDate(-999_999, 1, 1)
    val MAX = LocalDate(999_999, 12, 31)

    /** Get a date by the amount of days since 01-01-1970 */
    fun ofEpochDay(epochDay: Int) = epochStart.plus(epochDay, DateTimeUnit.DAY)
}

val LocalDate.epochDay get() = epochStart.daysUntil(this)
fun LocalDate.Companion.nowUTC() = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
