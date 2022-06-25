package maryk.lib.time

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Date by year, month and day. */
object Date {
    val MIN = LocalDate(-999_999, 1, 1)
    val MAX = LocalDate(999_999, 12, 31)
}

fun LocalDate.Companion.nowUTC() = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
