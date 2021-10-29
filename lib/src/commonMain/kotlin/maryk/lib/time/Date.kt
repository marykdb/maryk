package maryk.lib.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import maryk.lib.exceptions.ParseException

private val epochStart = LocalDate(1970, 1, 1)

/** Date by year, month and day. */
object Date : IsTemporalObject<LocalDate>() {
    val MIN = LocalDate(-999_999, 1, 1)
    val MAX = LocalDate(999_999, 12, 31)

    /** Get a date by the amount of days since 01-01-1970 */
    fun ofEpochDay(epochDay: Int) =
        epochStart.plus(epochDay, DateTimeUnit.DAY)

    override fun parse(value: String) = try {
        LocalDate.parse(value)
    } catch (e: IllegalArgumentException) {
        throw ParseException(value, e)
    }

    /** Get the current date at UTC timezone */
    override fun nowUTC() =
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}

val LocalDate.epochDay get() = epochStart.daysUntil(this)
